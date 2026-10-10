#!/usr/bin/env bash
#
# 跨境大卖ERP —— 审计留痕 / 平台系统配置 / 登录锁定 的端到端实测（真实 HTTP，非 Mock）
#
# 覆盖 docs/tech/security-design.md §5（登录失败锁定）、§7（审计留痕）、§9 P1-a/P1-c
# 与 docs/spec/tenant-and-iam.md BR-12 的「配置项存了要真生效」口径。
#
# 与 smoke.sh 的分工：smoke.sh 盯主链路（下单→发货→扣库存）与既有 ISS 回归；
# 本脚本盯这三块新增能力，两者互相不重复计数。
#
# 用法：
#   (cd backend && mvn -o spring-boot:run) &     # 后端需已起
#   ./scripts/smoke/security-slice-smoke.sh
#
# 环境变量：SMOKE_BASE（默认 http://localhost:8082）、SMOKE_USER/SMOKE_PASS（默认 admin/123456）
# 退出码：0 全部通过；1 存在失败项。
set -uo pipefail

BASE="${SMOKE_BASE:-http://localhost:8082}"
API="$BASE/api"
USER_NAME="${SMOKE_USER:-admin}"
PASS="${SMOKE_PASS:-123456}"
RUN_ID="$(date +%s)$$"
LOCK_USER="locktest$RUN_ID"
STRONG_PASS='Smoke#Strong123'
WEAK_PASS='1234567'

PASS_CNT=0
FAIL_CNT=0
FAILED_ITEMS=()

ok()   { PASS_CNT=$((PASS_CNT+1)); printf "  \033[32mPASS\033[0m %s\n" "$1"; }
bad()  { FAIL_CNT=$((FAIL_CNT+1)); FAILED_ITEMS+=("$1"); printf "  \033[31mFAIL\033[0m %s\n       期望: %s\n       实际: %s\n" "$1" "$2" "$3"; }
head2() { printf "\n\033[36m== %s ==\n\033[0m" "$1"; }

TMP="$(mktemp -t erp-sec-body.XXXXXX)"
trap 'rm -f "$TMP"' EXIT

jget() { node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const p=process.argv[1].split(".");let v=o;for(const k of p){if(v==null)break;v=v[k]}process.stdout.write(v==null?"":(typeof v==="object"?JSON.stringify(v):String(v)))}catch(e){process.stdout.write("")}})' "$1"; }

# JWT 有效期（秒）：exp - iat
jwt_ttl() { node -e 'const p=process.argv[1].split(".")[1]||"";try{const j=JSON.parse(Buffer.from(p,"base64url").toString());process.stdout.write(String((j.exp||0)-(j.iat||0)))}catch(e){process.stdout.write("-1")}' "$1"; }

req() {
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    HTTP_CODE=$(curl -sS -m 25 -o "$TMP" -w '%{http_code}' -X "$method" "$API$path" \
      -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$body" 2>/dev/null)
  else
    HTTP_CODE=$(curl -sS -m 25 -o "$TMP" -w '%{http_code}' -X "$method" "$API$path" \
      -H "Authorization: Bearer $TOKEN" 2>/dev/null)
  fi
  BODY="$(cat "$TMP" 2>/dev/null)"
}

req_anon() {
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    HTTP_CODE=$(curl -sS -m 25 -o "$TMP" -w '%{http_code}' -X "$method" "$API$path" \
      -H 'Content-Type: application/json' -d "$body" 2>/dev/null)
  else
    HTTP_CODE=$(curl -sS -m 25 -o "$TMP" -w '%{http_code}' -X "$method" "$API$path" 2>/dev/null)
  fi
  BODY="$(cat "$TMP" 2>/dev/null)"
}

expect() {
  local label="$1" want="$2" got="$3"
  if [ "$got" = "$want" ]; then ok "$label"; else bad "$label" "HTTP $want" "HTTP $got"; fi
}

# login <user> <pass> -> 设置 LOGIN_CODE / LOGIN_TOKEN
# 刻意不写 TOKEN：失败登录会把 TOKEN 置空，后续用平台管理员令牌的断言就会全变成 401，
# 看起来像「鉴权坏了」而实际只是脚本自己把令牌擦掉了。
login() {
  req_anon POST /auth/login "{\"username\":\"$1\",\"password\":\"$2\"}"
  LOGIN_CODE="$HTTP_CODE"
  LOGIN_TOKEN="$(printf '%s' "$BODY" | jget data.token)"
}

# ---------------------------------------------------------------- 0. 登录
head2 "0. 前置：平台管理员登录"
login "$USER_NAME" "$PASS"
expect "0.1 管理员登录" 200 "$LOGIN_CODE"
TOKEN="$LOGIN_TOKEN"
if [ -z "$TOKEN" ]; then
  bad "0.2 取到访问令牌" "非空" "空（后续断言无法进行）"
  printf "\n  通过 %d ｜ 失败 %d\n" "$PASS_CNT" "$FAIL_CNT"
  exit 1
fi
ok "0.2 取到访问令牌"

req GET /auth/me
TENANT_ID="$(printf '%s' "$BODY" | jget data.tenantId)"
if [ -n "$TENANT_ID" ]; then ok "0.3 管理员租户ID=$TENANT_ID"; else bad "0.3 管理员租户ID" "非空" "空"; fi

# ---------------------------------------------------------------- 1. 系统配置读写
head2 "1. GET/PUT /api/system-config"
req GET /system-config
expect "1.1 读取系统配置" 200 "$HTTP_CODE"
SITE_NAME="$(printf '%s' "$BODY" | jget data.general.siteName)"
TIMEOUT="$(printf '%s' "$BODY" | jget data.security.sessionTimeout)"
MAX_ATTEMPTS="$(printf '%s' "$BODY" | jget data.security.maxLoginAttempts)"
if [ -n "$SITE_NAME" ] && [ "$TIMEOUT" != "" ]; then
  ok "1.2 分三组回显（siteName=$SITE_NAME sessionTimeout=$TIMEOUT maxLoginAttempts=$MAX_ATTEMPTS）"
else
  bad "1.2 分三组回显" "general.siteName 与 security.sessionTimeout 均有值" "$BODY"
fi

# 先原样留存运行前的配置快照：收尾要把会话超时真正还原成这个值，
# 而不是还原成本脚本写入的 3600，否则多次运行会把环境越拖越低
CFG_ORIGINAL="$(node -e 'process.stdout.write(JSON.stringify(JSON.parse(process.argv[1]).data))' "$BODY")"
CFG_FULL="$(node -e '
const v=JSON.parse(process.argv[1]);
v.security.sessionTimeout=3600; v.security.maxLoginAttempts=5; v.security.twoFactorAuthRequired=false;
process.stdout.write(JSON.stringify(v));' "$CFG_ORIGINAL")"
req PUT /system-config "$CFG_FULL"
expect "1.3 保存会话超时=3600" 200 "$HTTP_CODE"
SAVED_TIMEOUT="$(printf '%s' "$BODY" | jget data.security.sessionTimeout)"
if [ "$SAVED_TIMEOUT" = "3600" ]; then ok "1.4 保存后回显 3600"; else bad "1.4 保存后回显" "3600" "$SAVED_TIMEOUT"; fi

head2 "2. 配置真的生效：会话超时注入 JWT 有效期"
login "$USER_NAME" "$PASS"
TTL="$(jwt_ttl "$LOGIN_TOKEN")"
if [ "$TTL" = "3600" ]; then ok "2.1 新签发的令牌有效期 = 配置的 3600 秒"; else bad "2.1 令牌有效期跟着配置走" "3600" "$TTL"; fi

head2 "3. 非法配置被拒且不落库"
BAD_2FA="$(node -e 'const v=JSON.parse(process.argv[1]);v.security.twoFactorAuthRequired=true;process.stdout.write(JSON.stringify(v))' "$CFG_FULL")"
req PUT /system-config "$BAD_2FA"
if [ "$HTTP_CODE" = "400" ] && printf '%s' "$BODY" | grep -q "TOTP"; then
  ok "3.1 开启双因素被明确拒绝（后端无 TOTP 链路，不能给假安全感）"
else
  bad "3.1 开启双因素被拒绝" "400 + 提到 TOTP" "HTTP $HTTP_CODE ${BODY:0:160}"
fi

SHORT_TIMEOUT="$(node -e 'const v=JSON.parse(process.argv[1]);v.security.sessionTimeout=10;process.stdout.write(JSON.stringify(v))' "$CFG_FULL")"
req PUT /system-config "$SHORT_TIMEOUT"
expect "3.2 会话超时低于 300 秒被拒" 400 "$HTTP_CODE"

BAD_EMAIL="$(node -e 'const v=JSON.parse(process.argv[1]);v.general.contactEmail="not-an-email";process.stdout.write(JSON.stringify(v))' "$CFG_FULL")"
req PUT /system-config "$BAD_EMAIL"
expect "3.3 非法邮箱被拒" 400 "$HTTP_CODE"

req GET /system-config
STILL="$(printf '%s' "$BODY" | jget data.security.sessionTimeout)"
if [ "$STILL" = "3600" ]; then ok "3.4 被拒的保存没有改动已生效值（校验先于写入）"; else bad "3.4 被拒的保存不应落库" "3600" "$STILL"; fi

# ---------------------------------------------------------------- 4. 密码策略生效
head2 "4. 密码策略（security.passwordPolicyEnabled）真正拦住弱口令"
req POST /users "{\"tenantId\":\"$TENANT_ID\",\"username\":\"weakpw$RUN_ID\",\"password\":\"$WEAK_PASS\"}"
if [ "$HTTP_CODE" = "400" ] && printf '%s' "$BODY" | grep -q "8 位"; then
  ok "4.1 7 位口令被拒（400 + 提示至少 8 位）"
else
  bad "4.1 弱口令被拒" "400 + 至少 8 位" "HTTP $HTTP_CODE ${BODY:0:160}"
fi

req POST /users "{\"tenantId\":\"$TENANT_ID\",\"username\":\"$LOCK_USER\",\"password\":\"$STRONG_PASS\",\"firstName\":\"锁定\",\"lastName\":\"测试\"}"
expect "4.2 强口令用户创建成功" 200 "$HTTP_CODE"
LOCK_USER_ID="$(printf '%s' "$BODY" | jget data.id)"
req PUT "/users/$LOCK_USER_ID/activate"
expect "4.3 激活该测试用户" 200 "$HTTP_CODE"

# ---------------------------------------------------------------- 5. 登录失败锁定
ADMIN_TOKEN="$TOKEN"
head2 "5. 登录失败锁定（security.maxLoginAttempts=5）"
for i in 1 2 3 4 5; do
  login "$LOCK_USER" "WrongPass#$i"
  expect "5.$i 第 $i 次错密码 → 401" 401 "$LOGIN_CODE"
done
login "$LOCK_USER" "$STRONG_PASS"
if [ "$LOGIN_CODE" = "400" ] && printf '%s' "$BODY" | grep -q "TOO_MANY_LOGIN_ATTEMPTS"; then
  ok "5.6 达到阈值后连正确口令也被挡（锁定先于认证）"
else
  bad "5.6 锁定生效" "400 + TOO_MANY_LOGIN_ATTEMPTS" "HTTP $LOGIN_CODE ${BODY:0:160}"
fi

head2 "6. 关闭锁定后同样口令可正常登录"
OFF="$(node -e 'const v=JSON.parse(process.argv[1]);v.security.maxLoginAttempts=0;process.stdout.write(JSON.stringify(v))' "$CFG_FULL")"
req PUT /system-config "$OFF"
expect "6.1 把 maxLoginAttempts 配成 0（关闭锁定）" 200 "$HTTP_CODE"
# 重新取该用户自己的 token 前先确认阈值确实读回 0
req GET /system-config
READ_ZERO="$(printf '%s' "$BODY" | jget data.security.maxLoginAttempts)"
if [ "$READ_ZERO" = "0" ]; then ok "6.2 阈值回读为 0"; else bad "6.2 阈值回读" "0" "$READ_ZERO"; fi
login "$LOCK_USER" "$STRONG_PASS"
expect "6.3 关闭锁定后正确口令立即恢复登录（历史失败不再卡人）" 200 "$LOGIN_CODE"
[ -n "$LOGIN_TOKEN" ] && ok "6.4 锁定解除后拿到了新令牌"

# ---------------------------------------------------------------- 7. 审计留痕
head2 "7. 审计留痕（audit_logs）"
req GET "/audit-logs/recent?limit=20"
expect "7.1 平台管理员可读最近审计" 200 "$HTTP_CODE"
RECENT_LEN="$(printf '%s' "$BODY" | jget data | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{process.stdout.write(String(JSON.parse(s).length))}catch(e){process.stdout.write("0")}})')"
if [ "${RECENT_LEN:-0}" -ge 10 ]; then ok "7.2 最近活动有 $RECENT_LEN 条（登录/建用户/改配置都进了）"; else bad "7.2 最近活动条数" ">=10" "$RECENT_LEN"; fi

req GET "/audit-logs?tenantId=$TENANT_ID&action=USER_CREATE&result=SUCCESS&size=50"
CREATE_TOTAL="$(printf '%s' "$BODY" | jget data.total)"
if [ "${CREATE_TOTAL:-0}" -ge 1 ]; then ok "7.3 按 租户+动作+结果 检索命中 $CREATE_TOTAL 条 USER_CREATE/SUCCESS"; else bad "7.3 USER_CREATE 检索" ">=1" "$CREATE_TOTAL"; fi

req GET "/audit-logs?result=FAILURE&action=AUTH_LOGIN&size=100"
FAILURE_COUNT="$(printf '%s' "$BODY" | jget data.total)"
if [ "${FAILURE_COUNT:-0}" -ge 5 ]; then ok "7.4 登录失败也留痕（AUTH_LOGIN/FAILURE 共 $FAILURE_COUNT 条）"; else bad "7.4 失败留痕" ">=5" "$FAILURE_COUNT"; fi

FIRST_FAIL="$(printf '%s' "$BODY" | jget data.content.0.errorMessage)"
if printf '%s' "$FIRST_FAIL" | grep -q "BadCredentialsException"; then
  ok "7.5 失败原因入库（errorMessage=$FIRST_FAIL）"
else
  bad "7.5 失败原因入库" "含 BadCredentialsException" "$FIRST_FAIL"
fi

if printf '%s' "$BODY" | grep -qi "$STRONG_PASS\|WrongPass"; then
  bad "7.6 审计内容不含明文口令" "不含口令字面量" "出现了明文口令"
else
  ok "7.6 审计响应里没有任何明文口令"
fi

req GET "/audit-logs?result=MAYBE"
expect "7.7 非法 result 取值被拒" 400 "$HTTP_CODE"

req GET "/audit-logs?tenantId=00000000-0000-0000-0000-000000000000&action=SYSTEM_CONFIG_UPDATE"
NOTHERE="$(printf '%s' "$BODY" | jget data.total)"
if [ "$NOTHERE" = "0" ]; then ok "7.8 按不存在租户过滤返回 0 条（过滤真实生效，不是忽略条件）"; else bad "7.8 租户过滤" "0" "$NOTHERE"; fi

head2 "8. 审计读权限只给平台管理员"
# 该测试用户没有被分配任何角色，正好用来验证「非平台管理员读不到审计与配置」
login "$LOCK_USER" "$STRONG_PASS"
if [ "$LOGIN_CODE" != "200" ] || [ -z "$LOGIN_TOKEN" ]; then
  bad "8.1 测试用户可登录" "200 + 令牌" "HTTP $LOGIN_CODE，无法验证越权读取"
else
  ok "8.1 测试用户（无角色）可登录"
  TOKEN="$LOGIN_TOKEN"
  req GET "/audit-logs?tenantId=$TENANT_ID"
  expect "8.2 非平台管理员读审计被拒" 403 "$HTTP_CODE"
  req GET /system-config
  expect "8.3 非平台管理员读系统配置被拒" 403 "$HTTP_CODE"
  TOKEN="$ADMIN_TOKEN"
  req GET /system-config
  expect "8.4 平台管理员令牌未被上面的切换污染" 200 "$HTTP_CODE"
fi

# ---------------------------------------------------------------- 9. 跨租户用户列表
head2 "9. 平台管理员跨租户用户列表（避免看板 N+1）"
req GET /users
ALL_USERS_LEN="$(printf '%s' "$BODY" | jget data | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{process.stdout.write(String(JSON.parse(s).length))}catch(e){process.stdout.write("0")}})')"
req GET "/users?tenantId=$TENANT_ID"
ONE_TENANT_LEN="$(printf '%s' "$BODY" | jget data | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{process.stdout.write(String(JSON.parse(s).length))}catch(e){process.stdout.write("0")}})')"
# 口径：全平台列表要比单租户多，且多出来的必须是别的租户——原来的 ">=9" 测的是历史残留账号数，
# 冒烟不再残留租户后那个阈值只会恒红（2026-10-09 R-25）
if [ "${ALL_USERS_LEN:-0}" -ge 2 ] && [ "${ALL_USERS_LEN:-0}" -gt "${ONE_TENANT_LEN:-0}" ]; then
  ok "9.1 不传 tenantId 返回全平台 $ALL_USERS_LEN 个用户（本租户 $ONE_TENANT_LEN）"
else
  bad "9.1 全平台用户列表" ">=2 且多于单租户" "全平台=$ALL_USERS_LEN 单租户=$ONE_TENANT_LEN"
fi
if [ "${ONE_TENANT_LEN:-0}" -gt 0 ] && [ "${ALL_USERS_LEN:-0}" -gt "${ONE_TENANT_LEN:-0}" ]; then
  ok "9.2 带 tenantId 时收敛到本租户（$ONE_TENANT_LEN < $ALL_USERS_LEN）"
else
  bad "9.2 单租户收敛" "少于全平台且非空" "$ONE_TENANT_LEN"
fi

# ---------------------------------------------------------------- 10. 收尾复原
head2 "10. 清理与复原"
req DELETE "/users/$LOCK_USER_ID"
expect "10.1 删除锁定测试用户" 200 "$HTTP_CODE"
RESTORE="$CFG_ORIGINAL"
req PUT /system-config "$RESTORE"
expect "10.2 会话超时复原为运行前的值" 200 "$HTTP_CODE"
req GET /system-config
RESTORED_TIMEOUT="$(printf '%s' "$BODY" | jget data.security.sessionTimeout)"
if [ "$RESTORED_TIMEOUT" = "$TIMEOUT" ]; then ok "10.2b 已还原为运行前的 sessionTimeout=$TIMEOUT"; else bad "10.2b 还原 sessionTimeout" "$TIMEOUT" "$RESTORED_TIMEOUT"; fi
RESTORED_ATTEMPTS="$(printf '%s' "$BODY" | jget data.security.maxLoginAttempts)"
if [ "$RESTORED_ATTEMPTS" = "$MAX_ATTEMPTS" ]; then ok "10.3 锁定阈值复原为运行前的 $MAX_ATTEMPTS"; else bad "10.3 阈值复原" "$MAX_ATTEMPTS" "$RESTORED_ATTEMPTS"; fi

printf "\n\033[36m== 汇总 ==\n\033[0m"
printf "  通过 %d ｜ 失败 %d\n" "$PASS_CNT" "$FAIL_CNT"
if [ "$FAIL_CNT" -gt 0 ]; then
  printf "  失败项：\n"; for item in "${FAILED_ITEMS[@]}"; do printf "    - %s\n" "$item"; done
  exit 1
fi
printf "  全部通过\n"
exit 0
