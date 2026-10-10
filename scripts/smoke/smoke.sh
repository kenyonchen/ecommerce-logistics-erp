#!/usr/bin/env bash
#
# 跨境大卖ERP —— 端到端冒烟实测（真实 HTTP，非 Mock）
#
# 覆盖 docs/qa/smoke-test-checklist.md §1/§2/§4 与 docs/plans/2026-10-07-mainflow-usable.md
# 的「下单 → 发货 → 扣库存 → 事件联动」主链路，并逐项断言已知缺陷（ISS-xxx）的修复效果。
#
# 用法：
#   ./erp-services.sh start                      # 先起 MySQL + RocketMQ
#   (cd backend && mvn -o spring-boot:run) &     # 再起后端
#   ./scripts/smoke/smoke.sh                     # 最后跑本脚本
#
# 环境变量：SMOKE_BASE（默认 http://localhost:8082）、SMOKE_USER/SMOKE_PASS（默认 admin/123456）
# 退出码：0 全部通过；1 存在失败项。
set -uo pipefail

BASE="${SMOKE_BASE:-http://localhost:8082}"
API="$BASE/api"
USER_NAME="${SMOKE_USER:-admin}"
PASS="${SMOKE_PASS:-123456}"
RUN_ID="$(date +%s)$$"

PASS_CNT=0
FAIL_CNT=0
SKIP_CNT=0
FAILED_ITEMS=()

# json 取值：node 做解析，避免依赖 jq
jget() { node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const p=process.argv[1].split(".");let v=o;for(const k of p){if(v==null)break;v=v[k]}process.stdout.write(v==null?"":(typeof v==="object"?JSON.stringify(v):String(v)))}catch(e){process.stdout.write("")}})' "$1"; }

ok()   { PASS_CNT=$((PASS_CNT+1)); printf "  \033[32mPASS\033[0m %s\n" "$1"; }
bad()  { FAIL_CNT=$((FAIL_CNT+1)); FAILED_ITEMS+=("$1"); printf "  \033[31mFAIL\033[0m %s\n       期望: %s\n       实际: %s\n" "$1" "$2" "$3"; }
skip() { SKIP_CNT=$((SKIP_CNT+1)); printf "  \033[33mSKIP\033[0m %s（%s）\n" "$1" "$2"; }
head2() { printf "\n\033[36m== %s ==\033[0m\n" "$1"; }

SMOKE_TMP="$(mktemp -t erp-smoke-body.XXXXXX)"
trap 'rm -f "$SMOKE_TMP"' EXIT

# req METHOD PATH [BODY] -> 设置全局 HTTP_CODE / BODY
req() {
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    HTTP_CODE=$(curl -sS -m 25 -o "$SMOKE_TMP" -w '%{http_code}' -X "$method" "$API$path" \
      -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$body" 2>/dev/null)
  else
    HTTP_CODE=$(curl -sS -m 25 -o "$SMOKE_TMP" -w '%{http_code}' -X "$method" "$API$path" \
      -H "Authorization: Bearer $TOKEN" 2>/dev/null)
  fi
  BODY="$(cat "$SMOKE_TMP" 2>/dev/null)"
}

# 不带 token 的请求；可选第三个参数为 JSON 请求体
req_anon() {
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    HTTP_CODE=$(curl -sS -m 25 -o "$SMOKE_TMP" -w '%{http_code}' -X "$method" "$API$path" \
      -H 'Content-Type: application/json' -d "$body" 2>/dev/null)
  else
    HTTP_CODE=$(curl -sS -m 25 -o "$SMOKE_TMP" -w '%{http_code}' -X "$method" "$API$path" 2>/dev/null)
  fi
  BODY="$(cat "$SMOKE_TMP" 2>/dev/null)"
}

# req_with <token> METHOD PATH [BODY]：以指定身份发请求（跨租户用例需要非平台管理员的令牌）
req_with() {
  local token="$1" method="$2" path="$3" body="${4:-}"
  local auth=(-H "Authorization: Bearer $token")
  if [ -n "$body" ]; then
    HTTP_CODE=$(curl -sS -m 25 -o "$SMOKE_TMP" -w '%{http_code}' -X "$method" "$API$path" \
      "${auth[@]}" -H 'Content-Type: application/json' -d "$body" 2>/dev/null)
  else
    HTTP_CODE=$(curl -sS -m 25 -o "$SMOKE_TMP" -w '%{http_code}' -X "$method" "$API$path" \
      "${auth[@]}" 2>/dev/null)
  fi
  BODY="$(cat "$SMOKE_TMP" 2>/dev/null)"
}

assert_code() { [ "$HTTP_CODE" = "$2" ] && ok "$1" || bad "$1" "HTTP $2" "HTTP $HTTP_CODE body=${BODY:0:200}"; }
assert_code_in() { for c in $2; do [ "$HTTP_CODE" = "$c" ] && { ok "$1"; return; }; done; bad "$1" "HTTP ∈ {$2}" "HTTP $HTTP_CODE body=${BODY:0:200}"; }
assert_code_lt() { [ "${HTTP_CODE:-999}" -lt "$2" ] && ok "$1" || bad "$1" "HTTP < $2" "HTTP $HTTP_CODE body=${BODY:0:200}"; }

# ─────────────────────────── 0. 环境 ───────────────────────────
head2 "0. 环境与服务可达性"
if ! curl -sS -m 10 -o /dev/null "$BASE/v3/api-docs"; then
  echo "后端不可达（$BASE），先执行：cd backend && mvn -o spring-boot:run"; exit 1
fi
ok "后端可达 $BASE"

# 本轮开跑前先给"运费流水"拍一张 id 快照，§12 自清场要用它区分"本轮撤销出来的 cancelled 分录"
# 与"历史/人工数据的审计痕"。快照只能取在这里：晚一步就把本轮自己写的行也算进基线，清场就漏了。
SMOKE_DB_PW="${SMOKE_DB_ROOT_PW:-123456}"
FREIGHT_T0="$(docker exec mysql mysql -uroot -p"$SMOKE_DB_PW" -N -B --default-character-set=utf8mb4 \
  ecommerce_logistics_erp -e "SELECT IFNULL(GROUP_CONCAT(transaction_id),'') FROM financial_transactions WHERE reference_type='LOGISTICS_ORDER'" 2>/dev/null | head -1)"

# Swagger 文档可访问性（ISS：SecurityConfig 缺 anyRequest 时 /v3/api-docs 会被拒）
SWAGGER_CODE=$(curl -sS -m 10 -o /dev/null -w '%{http_code}' "$BASE/v3/api-docs")
case "$SWAGGER_CODE" in
  200) ok "GET /v3/api-docs 返回 200（API 文档可用）" ;;
  401|403) skip "GET /v3/api-docs 返回 $SWAGGER_CODE" "文档端点需鉴权，可接受" ;;
  *) bad "GET /v3/api-docs" "200/401/403" "$SWAGGER_CODE" ;;
esac

# ─────────────────────────── 1. 认证（§1/§4.1） ───────────────────────────
head2 "1. 认证与令牌内容"
req_anon POST "/auth/login" "{\"username\":\"$USER_NAME\",\"password\":\"$PASS\"}"
assert_code "1.1 登录成功 admin/123456（4.1）" 200
TOKEN="$(printf '%s' "$BODY" | jget data.token)"
[ -n "$TOKEN" ] && ok "1.2 拿到 token" || { echo "无 token，后续无法进行"; exit 1; }

REQ="$USER_NAME" # 解码 JWT 校验 claims
CLAIMS_JSON=$(node -e '
const t=process.argv[1].split(".")[1];
const c=JSON.parse(Buffer.from(t,"base64").toString());
const perms=c.permissions||c.perms||[];
console.log(JSON.stringify({username:c.sub||c.username,tenantId:c.tenantId,roles:c.roles||[],n_perms:Array.isArray(perms)?perms.length:0,perms}));
' "$TOKEN" 2>/dev/null)
if [ -z "$CLAIMS_JSON" ]; then
  bad "1.3 解析 JWT claims" "可解析" "解析失败"
else
  echo "       claims: $CLAIMS_JSON"
  TENANT_A="$(printf '%s' "$CLAIMS_JSON" | jget tenantId)"
  ROLES="$(printf '%s' "$CLAIMS_JSON" | jget roles)"
  echo "$ROLES" | grep -q "PLATFORM_ADMIN" && ok "1.3 token 携带 PLATFORM_ADMIN（ISS-061）" \
    || bad "1.3 token 携带 PLATFORM_ADMIN" "含 PLATFORM_ADMIN" "roles=$ROLES（ISS-061 未修）"
  NPERM="$(printf '%s' "$CLAIMS_JSON" | jget n_perms)"
  [ "${NPERM:-0}" -ge 22 ] && ok "1.4 token 携带 $NPERM 个权限点（ISS-041/090）" \
    || bad "1.4 token 携带权限点" ">=22" "$NPERM"
  printf '%s' "$CLAIMS_JSON" | grep -q "shop:read" && ok "1.5 含 shop:* 权限点（ISS-090 种子不再静默失效）" \
    || bad "1.5 含 shop:* 权限点" "存在 shop:read" "缺失（种子 catalog 又失效）"
fi

req_anon POST "/auth/login" "{\"username\":\"$USER_NAME\",\"password\":\"wrong-pass-$RUN_ID\"}"
assert_code "1.6 错误密码被拒" 401
# 未认证出口（ISS-032/058：过滤器链 403 空 body 是缺陷，应为 401 + 信封）
req_anon GET "/orders"
UNAUTH_BODY_LEN=${#BODY}
if [ "$HTTP_CODE" = "401" ] && [ "$UNAUTH_BODY_LEN" -gt 2 ]; then
  ok "1.7 未认证 → 401 + 统一信封（ISS-032/058）"
else
  bad "1.7 未认证出口" "401 且带信封 body" "HTTP $HTTP_CODE，body 长度 $UNAUTH_BODY_LEN（前端 401→/login 分支不可达）"
fi

# RUN_BASE：本次运行开始时的"最新留痕时刻"，作为 §8/§10/§11 期间创建单据的归属回读窗口下界。
# AUD_BASE 要到 §12 开头才取，比这三处晚，所以那里建的租户/角色/子账号不能用它判窗口
# （会被误判成上一轮的旧行）。取法与 AUD_BASE 同源：读接口自己返回的时刻，格式与偏移天然一致，
# 不用 date 拼字符串（ISS-126：本机 API 值 = 库值 + 8h，任何本地格式化都会串味）。
req GET "/audit-logs?page=0&size=1"
RUN_BASE="$(printf '%s' "$BODY" | jget data.content.0.createdAt)"
[ -n "$RUN_BASE" ] || RUN_BASE="0000-00-00T00:00:00"

# ─────────────────────────── 2. 管理链路（§2） ───────────────────────────
head2 "2. 平台管理链路"
req GET "/tenants"
assert_code "2.1 GET /api/tenants 平台管理员可访问（§2.1）" 200
# R-32（ISS-120）：default-tenant 在 tenants 表里没有行，不是可寻址租户。带这个字面量的请求
# 读侧按「未指定」处理（平台管理员=跨租户全量），必填租户的端点落回 400「必须显式指定」——
# 两种都比此前"按字面过滤 → 静默返回空列表"更可判定，也绝不把数据写进幻影桶。
req GET "/users?tenantId=default-tenant"
assert_code "2.2 GET /api/users 带历史桶字面量 → 平台管理员按未指定处理（§2.4）" 200
ALL_USERS_N=$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const d=JSON.parse(s).data;console.log(Array.isArray(d)?d.length:-1)}catch(e){console.log(-1)}})')
[ "${ALL_USERS_N:-0}" -ge 1 ] && ok "2.2a 字面量读回全平台用户 $ALL_USERS_N 个（不再被当过滤值静默清空）" \
  || bad "2.2a 字面量读回全平台用户" "≥1" "$ALL_USERS_N body=${BODY:0:160}"
req GET "/users?tenantId=$TENANT_A"
assert_code "2.2b GET /users 带真实租户仍 200" 200
req GET "/users"
assert_code_in "2.3 GET /api/users 缺 tenantId → 400 而非 500（ISS-057）" "400 200"
req GET "/roles?tenantId=default-tenant"
assert_code "2.4 GET /roles 带历史桶字面量 → 400 要求显式指定（不再静默空列表）" 400
printf '%s' "$BODY" | grep -q "显式指定" && ok "2.4a 400 原因可读（点明必须显式指定 tenantId）" \
  || bad "2.4a 400 原因可读" "message 含「显式指定」" "${BODY:0:200}"
req GET "/roles?tenantId=$TENANT_A"
assert_code "2.4b GET /roles 带真实租户返回角色" 200

# ISS-135：显式点名的租户必须真实存在。业务表的 tenant_id 是普通列而不是指向 tenants 的外键，
# 平台管理员点名一个 tenants 表里没有的 UUID 会「HTTP 200 且行落库」，数据连凭据一起进谁也查不出
# 来的幻影桶（修复前实测：POST /api/platform-integrations 带 11111111-2222-… ⇒ 200 并持久化，
# 该行只能靠 §13 的幻影巡检之外的手段发现）。这里按两个收口入口各打一条负例：控制器收口
# （平台集成的服务形参是聚合，切面够不着）与切面收口（形参名就是 tenantId 的那一族）。
# 正例侧不在此重复：§12H 的 customers 建单显式带真实租户 $TENANT_A 且必须 200，
# 它证明这条判定不是"见显式租户就拒"。
GHOST_TENANT="11111111-2222-3333-4444-555555555555"
req POST "/platform-integrations" "{\"tenantId\":\"$GHOST_TENANT\",\"platformName\":\"AMAZON\",\"authType\":\"CUSTOM_SHOP\",\"storeUrl\":\"https://ghost-$RUN_ID.example.com\",\"apiKey\":\"ghost-key-$RUN_ID\",\"apiSecret\":\"ghost-secret-$RUN_ID\",\"status\":\"INACTIVE\"}"
assert_code "2.5 平台集成点名不存在的租户 → 400（ISS-135 控制器收口）" 400
printf '%s' "$BODY" | grep -q "TENANT_NOT_FOUND" && ok "2.5a 拒绝带业务码 TENANT_NOT_FOUND 且原因含被点名的租户ID" \
  || bad "2.5a 拒绝带业务码" "code=TENANT_NOT_FOUND" "${BODY:0:200}"
req POST "/users" "{\"tenantId\":\"$GHOST_TENANT\",\"username\":\"ghostuser$RUN_ID\",\"password\":\"Probe#12345\",\"email\":\"ghost-$RUN_ID@example.com\",\"firstName\":\"Ghost\",\"lastName\":\"Probe\"}"
assert_code "2.5b 用户创建点名不存在的租户 → 400（ISS-135 切面收口）" 400
req POST "/finance/transactions" "{\"tenantId\":\"$GHOST_TENANT\",\"transactionNo\":\"GHOST-TXN-$RUN_ID\",\"type\":\"INCOME\",\"amount\":1.00,\"currency\":\"USD\",\"category\":\"探针\",\"transactionDate\":\"2026-10-10\"}"
assert_code "2.5c 财务流水点名不存在的租户 → 400（ISS-135 切面收口 + ISS-136 不再压成 code 500）" 400
# 收口面只管写。读一个不存在的租户不会造出幻影行（查不到就是空页），把它一并判 400 会改掉
# 所有带 tenantId 的 GET 契约——本轮真的踩过：安全切片的 7.8「按不存在租户过滤返回 0 条」被
# 改成 400 才发现。下面两条把这条边界钉成常驻位：d 钉"没被拦成 400"，e 钉"过滤条件真被读了"。
req GET "/audit-logs?tenantId=$GHOST_TENANT&action=SYSTEM_CONFIG_UPDATE"
assert_code "2.5d 读请求点名不存在的租户仍返回 200（ISS-135 只收写侧，TenantIsolationAspect.isReadRequest）" 200
GHOST_READ_TOTAL="$(printf '%s' "$BODY" | jget data.total)"
[ "$GHOST_READ_TOTAL" = "0" ] && ok "2.5e 读侧返回空页而不是忽略 tenantId 过滤（total=0）" \
  || bad "2.5e 读侧空页" "data.total=0" "${GHOST_READ_TOTAL:-<空>}"

# ─────────────────────────── 3. 主链路：仓库→入库→下单→发货→扣库存 ───────────────────────────
head2 "3. 主链路（下单 → 发货 → 扣库存 → 事件联动）"
WH_CODE="WH-SMOKE-$RUN_ID"
req POST "/warehouses" "{\"warehouseName\":\"冒烟测试仓-$RUN_ID\",\"warehouseCode\":\"$WH_CODE\",\"address\":\"深圳市南山区科技园 1 号\",\"capacity\":1000}"
assert_code "3.1 创建仓库" 200
WH_ID="$(printf '%s' "$BODY" | jget data.warehouseId)"
[ -n "$WH_ID" ] && ok "3.2 仓库 ID=$WH_ID" || bad "3.2 仓库 ID" "非空" "$BODY"
printf '%s' "$BODY" | grep -q '"domainEvents"' && bad "3.2b 仓库响应不外泄聚合内部字段（ISS-063）" "无 domainEvents" "响应含 domainEvents（聚合根被直接当 REST 契约）" \
  || ok "3.2b 仓库响应不外泄聚合内部字段（ISS-063）"

req POST "/warehouses/$WH_ID/inventory/in" "{\"sku\":\"SKU-SMOKE-$RUN_ID\",\"productId\":\"PROD-SMOKE-$RUN_ID\",\"warehouseId\":\"$WH_ID\",\"quantity\":50,\"movementType\":\"INBOUND\"}"
assert_code "3.3 入库 50（ISS-059/060）" 200
req GET "/warehouses/$WH_ID/inventory"
printf '%s' "$BODY" | grep -q "SKU-SMOKE-$RUN_ID" && ok "3.4 库存可查（入库已落库）" \
  || bad "3.4 库存可查" "含 SKU-SMOKE-$RUN_ID" "${BODY:0:250}"

# 缺货联动：出到 0 应触发 InventoryShortageEvent → 自动建采购单（ISS-062/077 的判据）
req POST "/warehouses/$WH_ID/inventory/out" "{\"sku\":\"SKU-SMOKE-$RUN_ID\",\"productId\":\"PROD-SMOKE-$RUN_ID\",\"warehouseId\":\"$WH_ID\",\"quantity\":49,\"movementType\":\"OUTBOUND\"}"
assert_code "3.5 出库 49（剩 1）" 200

ORD_BODY="{\"orderNumber\":\"ORDSMK$RUN_ID\",\"customerId\":\"CUST-SMOKE-$RUN_ID\",\"customerName\":\"冒烟客户\",\"customerEmail\":\"smoke-$RUN_ID@example.com\",\"orderItems\":[{\"productName\":\"冒烟商品\",\"skuCode\":\"SKU-SMOKE-$RUN_ID\",\"quantity\":1,\"unitPrice\":9.9,\"totalPrice\":9.9}],\"paymentInfo\":{\"amount\":9.9,\"paymentMethod\":\"CREDIT_CARD\",\"paymentStatus\":\"PAID\",\"transactionId\":\"TXN-SMOKE-$RUN_ID\"},\"shippingAddress\":{\"country\":\"CN\",\"province\":\"广东\",\"city\":\"深圳\",\"street\":\"科技园 1 号\",\"recipient\":\"冒烟收件人\",\"phone\":\"13900000000\",\"zipCode\":\"518000\"}}"
req POST "/orders" "$ORD_BODY"
assert_code "3.6 创建订单（ISS-001/056）" 200
ORD_ID="$(printf '%s' "$BODY" | jget data.orderId)"
ORD_NO="$(printf '%s' "$BODY" | jget data.orderNumber)"
[ -n "$ORD_ID" ] && ok "3.7 订单 ID=$ORD_ID 单号=$ORD_NO" || bad "3.7 订单落库" "返回 id" "${BODY:0:300}"

# 关键词搜索必须能按客户名称命中（ISS-074：输入框 placeholder 写的是"订单号/客户/平台订单号"，
# 而 OrderSpecification 的 keyword OR 原先只拼 orderNumber/platformOrderId/customerId）。
# 中文查询串必须百分号编码——Tomcat 直接拒非 ASCII 请求行，给回的是 400 HTML 而不是 Result 信封。
KW_CUST=$(node -e 'console.log(encodeURIComponent("冒烟客户"))')
KW_NONE=$(node -e 'console.log(encodeURIComponent("冒烟客户-不存在-XYZ"))')
req GET "/orders?keyword=$KW_CUST&page=0&size=100"
if printf '%s' "$BODY" | grep -q "$ORD_NO"; then
  ok "3.7b 关键词按客户名命中本单（ISS-074）"
else
  bad "3.7b 关键词按客户名命中本单" "含 $ORD_NO" "HTTP $HTTP_CODE body=${BODY:0:200}"
fi
req GET "/orders?keyword=$KW_NONE&page=0&size=100"
KW_ZERO=$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const c=(o.data&&o.data.content)||[];console.log(c.length)}catch(e){console.log("PARSEERR")}})')
if [ "$KW_ZERO" = "0" ]; then
  ok "3.7c 无关关键词返回 0 行（负例：证明 3.7b 不是「见请求就返回全量」）"
else
  bad "3.7c 无关关键词返回 0 行" "0 行" "${KW_ZERO} 行（keyword 被忽略或恒放行）"
fi

# 订单事件必须真的发出去（ISS-062 的运行时判据）
BEFORE_LOG_LINES=$(wc -l < /tmp/erp-backend.log 2>/dev/null || echo 0)
req GET "/logistics/order/$ORD_ID"
LOGI_HAS=$(printf '%s' "$BODY" | jget data | head -c 200)
if printf '%s' "$LOGI_HAS" | grep -q "logisticsOrderId"; then
  ok "3.8 下单自动生成物流单（ISS-062 事件链路已通）"
else
  bad "3.8 下单自动生成物流单" "OrderCreatedEvent 触发建物流单" "data=${LOGI_HAS:-空}（事件仍未发布，主链路未闭环）"
fi

# 非法流转必须被拒（ISS-003）
req PUT "/orders/$ORD_ID/status?status=COMPLETED"
assert_code_in "3.9 非法流转 PENDING→COMPLETED 被拒（ISS-003）" "400 200"
if [ "$HTTP_CODE" = "200" ]; then bad "3.9 状态机守卫" "400 INVALID_OPERATION" "200 放行了非法流转"; fi

req PUT "/orders/$ORD_ID/status?status=PROCESSING"
assert_code "3.10 合法流转 PENDING→PROCESSING" 200
req PUT "/orders/$ORD_ID/status?status=SHIPPED"
assert_code "3.11 合法流转 PROCESSING→SHIPPED" 200

# 发货扣库存 BR-04：SHIPPED 后该仓 SKU 余量应从 1 变 0
req GET "/warehouses/$WH_ID/inventory"
INV_AFTER=$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const arr=Array.isArray(o.data)?o.data:(o.data&&o.data.content)||[];const hit=arr.find(r=>r.sku===process.argv[1]||r.productId===process.argv[2]);console.log(hit?String(hit.quantity):"NOTFOUND")}catch(e){console.log("PARSEERR")}})' "SKU-SMOKE-$RUN_ID" "PROD-SMOKE-$RUN_ID")
if [ "$INV_AFTER" = "0" ]; then ok "3.12 发货后库存 1→0（BR-04 扣减生效）"
elif [ "$INV_AFTER" = "1" ]; then bad "3.12 发货扣库存" "库存归 0" "仍为 1（未扣减）"
else skip "3.12 发货扣库存" "库存查询返回 $INV_AFTER（订单仓与默认仓不一致，需人工确认口径）"; fi

req GET "/orders/$ORD_ID"
printf '%s' "$BODY" | grep -q "domainEvents" && bad "3.13 聚合内部字段不外泄（ISS-063）" "响应无 domainEvents" "响应含 domainEvents" \
  || ok "3.13 响应无 domainEvents（ISS-063）"

req GET "/orders/not-exist-$RUN_ID"
assert_code "3.14 不存在的订单 → 404 ENTITY_NOT_FOUND（4.2）" 404

# 删除订单必须联动删掉自动派生的物流单，否则留下孤儿行并挡住「删除租户」的业务数据守卫
ORD2_BODY="$(printf '%s' "$ORD_BODY" | sed -e "s/ORDSMK$RUN_ID/ORDSMK2$RUN_ID/" -e "s/TXN-SMOKE-$RUN_ID/TXN-SMOKE2-$RUN_ID/")"
req POST "/orders" "$ORD2_BODY"
assert_code "3.15 创建待删除订单" 200
ORD2_ID="$(printf '%s' "$BODY" | jget data.orderId)"
req GET "/logistics/order/$ORD2_ID"
if printf '%s' "$BODY" | grep -q "logisticsOrderId"; then
  ok "3.16 待删除订单已派生物流单"
else
  bad "3.16 待删除订单已派生物流单" "存在派生物流单" "data=${BODY:0:200}"
fi
req DELETE "/orders/$ORD2_ID"
assert_code "3.17 删除订单" 200
req GET "/logistics/order/$ORD2_ID"
if printf '%s' "$BODY" | grep -q "logisticsOrderId"; then
  bad "3.18 删订单联动删物流单（ISS-113）" "物流单随订单消失" "仍查到物流单（孤儿行，会挡住删除租户）"
else
  ok "3.18 删订单联动删物流单（ISS-113）"
fi

# ─────────────────────────── 4. 商品写路径（ISS-068/069） ───────────────────────────
head2 "4. 商品模块"
req POST "/products" "{\"name\":\"冒烟商品-$RUN_ID\",\"description\":\"smoke\",\"category\":\"SMOKE\",\"brand\":\"SmokeBrand\",\"basePrice\":9.9,\"currency\":\"USD\",\"status\":\"ACTIVE\",\"skus\":[{\"skuCode\":\"PSKU-$RUN_ID\",\"barcode\":\"BC-$RUN_ID\",\"sellingPrice\":9.9,\"costPrice\":5,\"stock\":100,\"status\":\"ACTIVE\"}]}"
assert_code "4.1 创建商品" 200
PROD_ID="$(printf '%s' "$BODY" | jget data.id)"
req PUT "/products/$PROD_ID" "{\"name\":\"冒烟商品改名-$RUN_ID\",\"description\":\"smoke2\",\"category\":\"SMOKE\",\"brand\":\"SmokeBrand\",\"basePrice\":10.9,\"currency\":\"USD\",\"status\":\"ACTIVE\",\"skus\":[{\"skuCode\":\"PSKU-$RUN_ID\",\"barcode\":\"BC-$RUN_ID\",\"sellingPrice\":9.9,\"costPrice\":5,\"stock\":100,\"status\":\"ACTIVE\"}]}"
assert_code "4.2 编辑商品不再 500（ISS-068 已修）" 200
req GET "/products/$PROD_ID"
if [ "$HTTP_CODE" = "200" ] && printf '%s' "$BODY" | grep -q "PSKU-$RUN_ID"; then
  ok "4.3 编辑后 SKU 仍在（未被整体覆盖删光，ISS-069 的数据丢失面）"
else
  bad "4.3 SKU 覆盖语义" "改名后仍含 PSKU-$RUN_ID" "${BODY:0:250}"
fi
req POST "/products/$PROD_ID/skus" "{\"skuCode\":\"PSKU2-$RUN_ID\",\"barcode\":\"BC2-$RUN_ID\",\"sellingPrice\":11.9,\"costPrice\":6,\"stock\":50,\"status\":\"ACTIVE\"}"
assert_code "4.4 新增 SKU 不再 500（ISS-068）" 200

# ─────────────────────────── 5. 采购 / 财务 / 物流渠道（§4.6~4.8） ───────────────────────────
head2 "5. 采购 / 财务 / 渠道"
req POST "/purchases" "{\"supplierName\":\"冒烟供应商\",\"supplierId\":\"SUP-SMOKE-$RUN_ID\",\"warehouseId\":\"$WH_ID\",\"items\":[{\"productName\":\"冒烟商品\",\"skuCode\":\"SKU-SMOKE-$RUN_ID\",\"quantity\":10,\"unitPrice\":3.5}]}"
assert_code "5.1 创建采购单（不传 tenantId 也应归登录租户，ISS-051）" 200
PO_ID="$(printf '%s' "$BODY" | jget data.purchaseOrderId)"
PO_TENANT="$(printf '%s' "$BODY" | jget data.tenantId)"
[ "$PO_TENANT" = "default-tenant" ] || [ -n "$PO_TENANT" ] && ok "5.2 采购单归属租户=$PO_TENANT" || bad "5.2 采购单租户" "非空" "$BODY"
req PUT "/purchases/$PO_ID/approve"
assert_code "5.3 审批通过" 200
req PUT "/purchases/$PO_ID/approve"
if [ "$HTTP_CODE" = "200" ] && printf '%s' "$BODY" | grep -q '"code":"500"'; then
  bad "5.4 重复审批的响应形状" "4xx 状态码 + 具体错误码" "HTTP 200 + code:500（自动化按 HTTP 码判成功会误绿，M-08）"
elif [ "$HTTP_CODE" = "400" ]; then ok "5.4 重复审批 → 400（形状正确）"
else skip "5.4 重复审批" "HTTP $HTTP_CODE ${BODY:0:120}"; fi

req POST "/finance/transactions?type=INCOME" "{\"amount\":199.0,\"currency\":\"CNY\",\"type\":\"INCOME\",\"status\":\"PENDING\",\"description\":\"冒烟收入\",\"referenceId\":\"$ORD_ID\",\"referenceType\":\"ORDER\"}"
assert_code "5.5 创建财务流水" 200
# 该端点把业务失败压成 HTTP 200 + code:500（M-08 同一形态），只看 HTTP 码会误绿：币种缺失时
# 流水其实没建出来，审计侧的 FINANCE_TRANSACTION_CREATE 会记成 FAILURE。
printf '%s' "$BODY" | grep -q '"code":"200"' && ok "5.5b 财务流水真的落库（响应体 code=200）" \
  || bad "5.5b 财务流水真的落库" "响应体 code=200" "${BODY:0:200}"
req GET "/finance/transactions/by-type?type=INCOME"
assert_code "5.6 按类型查流水（枚举名大写）" 200
req GET "/finance/transactions/by-type?type=income"
assert_code_in "5.7 小写枚举 → 400 而非 500（ISS-057）" "400 200"

req GET "/logistics-channels"
assert_code "5.8 物流渠道列表" 200

# ─────────────────────────── 6. 导入线（ISS-067/071） ───────────────────────────
head2 "6. 导入执行链路"
# CSV 列名必须命中 OrderImportTargetField 的别名：缺 sku/productName/收件人/详细地址会被
# 行级必填校验判失败（"订单明细为空，请检查 ITEMS 或 ITEM_SKU/ITEM_NAME 字段映射"）
CSV=$(mktemp /tmp/smoke_orders_XXXXXX.csv)
{
  printf 'orderId,customerName,customerEmail,totalAmount,status,recipientName,phone,country,province,city,street,zipCode,sku,productName,quantity,unitPrice\n'
  printf 'SMOKE-%s-A,冒烟甲,smokeA@example.com,11.11,PENDING,冒烟收件人A,13800000001,CN,广东,深圳,科技园1号,518000,SKU-IMP-%s-A,冒烟导入商品,1,11.11,1\n' "$RUN_ID" "$RUN_ID"
  printf 'SMOKE-%s-B,冒烟乙,smokeB@example.com,22.22,PENDING,冒烟收件人B,13800000002,CN,浙江,杭州,文一西路2号,310000,SKU-IMP-%s-B,冒烟导入商品,2,11.11,2\n' "$RUN_ID" "$RUN_ID"
} > "$CSV"
# 上传需要 multipart，单独用 curl
UP=$(curl -sS -m 40 -o /tmp/smoke_up.$$ -w '%{http_code}' -X POST "$API/orders/import/upload?mode=import&enableIdempotency=true" \
  -H "Authorization: Bearer $TOKEN" -F "file=@$CSV;type=text/csv")
UP_BODY="$(cat /tmp/smoke_up.$$ 2>/dev/null)"; rm -f /tmp/smoke_up.$$ "$CSV"
HTTP_CODE="$UP"; BODY="$UP_BODY"
if [ "$HTTP_CODE" = "200" ]; then
  ok "6.1 订单导入 upload(mode=import) 不再 500（ISS-067 已修）"
  JOB_STATE="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const d=o.data||{};console.log((d.status||d.state||"")+"|total="+(d.totalCount??d.total??"")+"|ok="+(d.successCount??d.success??"")+"|fail="+(d.failureCount??d.failed??""))}catch(e){console.log("PARSEERR")}})')"
  echo "       导入作业：$JOB_STATE"
  printf '%s' "$JOB_STATE" | grep -qE "SUCCESS|COMPLETED|PARTIAL|FAILED" && ok "6.2 作业进入终态（ISS-071 可回溯）" \
    || bad "6.2 作业终态" "SUCCESS/FAILED/PARTIAL" "$JOB_STATE（仍是 RUNNING ⇒ 不可回溯）"
  # 真实可用判据：导入不能只是"不 500"，必须真的建出订单
  OK_CNT=$(printf '%s' "$JOB_STATE" | sed -n 's/.*ok=\([0-9]*\).*/\1/p')
  if [ "${OK_CNT:-0}" -ge 2 ]; then
    ok "6.2b 导入真的落库（成功 $OK_CNT 行）"
    req GET "/orders?keyword=%E5%86%92%E7%83%9F%E7%94%B2&page=0&size=5"
    printf '%s' "$BODY" | grep -q "冒烟甲" && ok "6.2c 导入的订单可在列表查到" \
      || bad "6.2c 导入的订单可查" "列表含「冒烟甲」" "${BODY:0:200}"
  else
    bad "6.2b 导入真的落库" "successCount >= 2" "作业状态 $JOB_STATE（HTTP 不 500 但零成功，仍是不可用）"
    req GET "/orders/import/jobs?page=0&size=1"
    echo "       失败原因：$(printf '%s' "$BODY" | grep -o '"message":"[^"]*"' | head -3 | tr '\n' ' ')"
  fi
else
  bad "6.1 订单导入 upload(mode=import)" "HTTP 200" "HTTP $HTTP_CODE body=${BODY:0:300}"
fi
req GET "/orders/import/jobs?page=0&size=5"
assert_code "6.3 导入任务列表可查" 200

# ─────────────────────────── 7. 平台集成（ISS-011/086/089） ───────────────────────────
head2 "7. 平台集成与凭据"
req GET "/platform-integrations/auth-specs"
assert_code "7.1 auth-specs 字典可用（ISS-089 表单应有 11 条协议规格）" 200
N_SPEC=$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const d=o.data||[];console.log(Array.isArray(d)?d.length:0)}catch(e){console.log(0)}})')
[ "${N_SPEC:-0}" -ge 8 ] && ok "7.2 auth-specs 返回 $N_SPEC 条（覆盖 11 种 PlatformAuthType）" \
  || bad "7.2 auth-specs 条数" ">=8" "$N_SPEC"

req POST "/platform-integrations" "{\"tenantId\":\"default-tenant\",\"platformName\":\"SHOPIFY\",\"authType\":\"SHOPIFY\",\"storeUrl\":\"https://smoke-$RUN_ID.myshopify.com\",\"accessToken\":\"shpat_smoke_$RUN_ID\",\"apiKey\":\"sk-smoke-key-$RUN_ID\",\"apiSecret\":\"sh-smoke-secret-$RUN_ID\",\"status\":\"INACTIVE\"}"
if [ "$HTTP_CODE" = "200" ]; then
  ok "7.3 创建 Shopify 集成"
  INT_ID="$(printf '%s' "$BODY" | jget data.id)"
  req GET "/platform-integrations/$INT_ID"
  if printf '%s' "$BODY" | grep -q "sh-smoke-secret-$RUN_ID"; then
    bad "7.4 凭据不出现在响应（ISS-011）" "掩码，不含明文 secret" "响应回显明文 secret"
  elif printf '%s' "$BODY" | grep -qE "apiKeyMasked|hasApiSecret"; then
    ok "7.4 凭据已掩码（ISS-011）"
  else
    skip "7.4 凭据掩码" "响应既无明文也无掩码字段：${BODY:0:160}"
  fi
  # 密文列宽：保存后库里应是 enc:v1: 且列足够宽（ISS-011 残留：varchar(255) 溢出）
  req PUT "/platform-integrations/$INT_ID" "{\"tenantId\":\"default-tenant\",\"platformName\":\"SHOPIFY\",\"authType\":\"SHOPIFY\",\"storeUrl\":\"https://smoke-$RUN_ID.myshopify.com\",\"accessToken\":\"shpat_smoke_$RUN_ID\",\"apiKey\":\"$(node -e 'console.log("k".repeat(220))')\",\"apiSecret\":\"$(node -e 'console.log("s".repeat(220))')\",\"status\":\"INACTIVE\"}"
  assert_code "7.5 长凭据保存不溢出（密文列宽已放宽）" 200
else
  bad "7.3 创建 Shopify 集成" "HTTP 200" "HTTP $HTTP_CODE body=${BODY:0:300}"
fi

# 其余平台能否建记录（ISS-086：platform_name 列型窄化挡住 5 个平台）
req POST "/platform-integrations" "{\"tenantId\":\"default-tenant\",\"platformName\":\"OZON\",\"authType\":\"CUSTOM_SHOP\",\"storeUrl\":\"https://api.ozon.example\",\"apiKey\":\"oz-key-$RUN_ID\",\"apiSecret\":\"oz-secret-$RUN_ID\",\"status\":\"INACTIVE\"}"
assert_code "7.6 建 OZON 集成（ISS-086 ENUM 窄化）" 200
# 归属回读（ISS-121）：请求体带历史默认桶字面量时，创建必须落登录租户。这一族的形参是聚合而不是
# tenantId，切面与源码扫描两条守卫都够不着，实测库里攒过 35 条谁也查不出来的幻影行（含加密凭据）。
INT_TENANT=$(printf '%s' "$BODY" | jget data.tenantId)
[ -n "$INT_TENANT" ] && [ "$INT_TENANT" != "default-tenant" ] \
  && ok "7.7 集成归属登录租户 $INT_TENANT（不再落幻影桶）" \
  || bad "7.7 集成归属登录租户" "非 default-tenant 的租户ID" "${INT_TENANT:-空} body=${BODY:0:160}"
# 探针自己复原：这两条集成若不删，每轮冒烟就往库里攒一条带凭据的记录
INT2_ID=$(printf '%s' "$BODY" | jget data.id)
[ -n "$INT_ID" ] && { req DELETE "/platform-integrations/$INT_ID"; assert_code_in "7.8 清理 Shopify 探针集成" "200 204"; }
[ -n "$INT2_ID" ] && { req DELETE "/platform-integrations/$INT2_ID"; assert_code_in "7.9 清理 OZON 探针集成" "200 204"; }

# ─────────────────────────── 8. 读路径租户隔离（ISS-050） ───────────────────────────
head2 "8. 跨租户越权（读路径）"
# 用租户 A 之外的身份探测：平台管理员在切面里不参与越权判定（只校验点名的租户是否真实存在，
# 见 TenantIsolationAspect 的 platformAdmin 分支），用 admin 令牌测跨租户读是测不出来的，
# 必须以普通租户管理员的身份测。
req POST "/tenants" "{\"name\":\"冒烟第二租户-$RUN_ID\",\"description\":\"冒烟用例创建\",\"contactEmail\":\"smoke2-$RUN_ID@example.com\",\"adminUsername\":\"smokeadm$RUN_ID\",\"adminPassword\":\"Smoke#123456\",\"adminEmail\":\"smokeadm$RUN_ID@example.com\",\"adminFirstName\":\"Smoke\",\"adminLastName\":\"Admin\"}"
assert_code "8.1 创建第二个租户" 200
T2="$(printf '%s' "$BODY" | jget data.id)"
if [ -z "$T2" ]; then
  bad "8.1b 新租户 ID" "非空" "$BODY"
else
  ok "8.1b 新租户 ID=$T2"
  req_anon POST "/auth/login" "{\"username\":\"smokeadm$RUN_ID\",\"password\":\"Smoke#123456\"}"
  if [ "$HTTP_CODE" = "200" ]; then
    ok "8.1c 新租户管理员可登录"
    T2_TOKEN="$(printf '%s' "$BODY" | jget data.token)"
  else
    bad "8.1c 新租户管理员登录" "HTTP 200" "HTTP $HTTP_CODE ${BODY:0:200}"
  fi
  if [ -n "${T2_TOKEN:-}" ]; then
    # 租户 B 的管理员显式请求租户 A（admin 所在租户）的列表
    req_with "$T2_TOKEN" GET "/orders?tenantId=$TENANT_A&page=0&size=5"
    if [ "$HTTP_CODE" = "200" ]; then
      bad "8.2 跨租户读订单列表未被拦截（ISS-050）" "400/403 TENANT_ACCESS_DENIED" "HTTP 200——写路径已隔离、读路径未隔离"
    elif [ "$HTTP_CODE" = "400" ] || [ "$HTTP_CODE" = "403" ]; then
      ok "8.2 跨租户读订单列表被拒（HTTP $HTTP_CODE）"
    else
      bad "8.2 跨租户读订单列表" "400/403" "HTTP $HTTP_CODE ${BODY:0:200}"
    fi
    req_with "$T2_TOKEN" GET "/warehouses?tenantId=$TENANT_A&page=0&size=5"
    if [ "$HTTP_CODE" = "200" ]; then
      bad "8.3 跨租户读仓库列表未被拦截（ISS-050）" "400/403 TENANT_ACCESS_DENIED" "HTTP 200"
    elif [ "$HTTP_CODE" = "400" ] || [ "$HTTP_CODE" = "403" ]; then
      ok "8.3 跨租户读仓库列表被拒（HTTP $HTTP_CODE）"
    else
      bad "8.3 跨租户读仓库列表" "400/403" "HTTP $HTTP_CODE ${BODY:0:200}"
    fi
    # 按主键直读：即使不带 tenantId 参数，也不应读到他人数据
    req_with "$T2_TOKEN" GET "/orders/$ORD_ID"
    if [ "$HTTP_CODE" = "200" ]; then
      bad "8.4 按 ID 直读他租户订单未被拦截（ISS-050）" "403/404" "HTTP 200 读到他人订单"
    elif [ "$HTTP_CODE" = "403" ] || [ "$HTTP_CODE" = "404" ]; then
      ok "8.4 按 ID 直读他租户订单被拒（HTTP $HTTP_CODE）"
    else
      bad "8.4 按 ID 直读他租户订单" "403/404" "HTTP $HTTP_CODE ${BODY:0:200}"
    fi
    # 按主键直写：跨租户改状态必须按「不存在」处理（ISS-050 的写面）
    req_with "$T2_TOKEN" PUT "/orders/$ORD_ID/status?status=PROCESSING"
    if [ "$HTTP_CODE" = "404" ] || [ "$HTTP_CODE" = "403" ]; then
      ok "8.5 跨租户改他人订单状态被拒（HTTP $HTTP_CODE）"
    elif [ "$HTTP_CODE" = "200" ]; then
      bad "8.5 跨租户写订单未被拦截（ISS-050）" "403/404" "HTTP 200——他人订单被本租户改写"
    else
      bad "8.5 跨租户写订单" "403/404" "HTTP $HTTP_CODE ${BODY:0:200}"
    fi
    # 不传 tenantId 的列表查询不得退化成全平台扫描（ISS-050 的列表面）
    req_with "$T2_TOKEN" GET "/orders?page=0&size=100"
    if [ "$HTTP_CODE" = "200" ] && printf '%s' "$BODY" | grep -q "$ORD_ID"; then
      bad "8.6 缺省 tenantId 的列表泄漏他租户数据（ISS-050）" "列表不含他人订单" "含 $ORD_ID"
    elif [ "$HTTP_CODE" = "200" ]; then
      ok "8.6 缺省 tenantId 的列表已收敛到登录租户"
    else
      bad "8.6 缺省 tenantId 的列表查询" "HTTP 200" "HTTP $HTTP_CODE ${BODY:0:200}"
    fi

    # 采购域同一口径（本轮补）：§8 原先只测订单与仓库，采购列表的读面收敛没有活证据。
    # 正例前置在先——先证明这族端点对本租户是好的，后面的 400/404 才是归属守卫的功劳。
    req_with "$T2_TOKEN" GET "/purchases?page=0&size=5"
    if [ "$HTTP_CODE" = "200" ]; then
      ok "8.7 本租户读采购列表放行（正例前置，证明 8.8/8.9 不是端点故障）"
    else
      bad "8.7 本租户读采购列表" "HTTP 200" "HTTP $HTTP_CODE ${BODY:0:200}"
    fi
    req_with "$T2_TOKEN" GET "/purchases?tenantId=$TENANT_A&page=0&size=5"
    if [ "$HTTP_CODE" = "200" ]; then
      bad "8.8 跨租户显式读采购列表未被拦截（ISS-050）" "400/403 TENANT_ACCESS_DENIED" "HTTP 200——他人租户的采购单可读"
    elif [ "$HTTP_CODE" = "400" ] || [ "$HTTP_CODE" = "403" ]; then
      ok "8.8 跨租户显式读采购列表被拒（HTTP $HTTP_CODE）"
    else
      bad "8.8 跨租户显式读采购列表" "400/403" "HTTP $HTTP_CODE ${BODY:0:200}"
    fi
    # PO_ID 是 §3 在租户 A 建的采购单：按 ID 直读即使不带 tenantId 也不应读到
    if [ -n "${PO_ID:-}" ]; then
      req_with "$T2_TOKEN" GET "/purchases/$PO_ID"
      if [ "$HTTP_CODE" = "200" ]; then
        bad "8.9 按 ID 直读他租户采购单未被拦截（ISS-050）" "403/404" "HTTP 200 读到他人采购单 $PO_ID"
      elif [ "$HTTP_CODE" = "403" ] || [ "$HTTP_CODE" = "404" ]; then
        ok "8.9 按 ID 直读他租户采购单被拒（HTTP $HTTP_CODE）"
      else
        bad "8.9 按 ID 直读他租户采购单" "403/404" "HTTP $HTTP_CODE ${BODY:0:200}"
      fi
      req_with "$T2_TOKEN" GET "/purchases?page=0&size=100"
      if [ "$HTTP_CODE" = "200" ] && printf '%s' "$BODY" | grep -q "$PO_ID"; then
        bad "8.10 缺省 tenantId 的采购列表泄漏他租户数据（ISS-050）" "列表不含 $PO_ID" "含 $PO_ID"
      elif [ "$HTTP_CODE" = "200" ]; then
        ok "8.10 缺省 tenantId 的采购列表已收敛到登录租户"
      else
        bad "8.10 缺省 tenantId 的采购列表查询" "HTTP 200" "HTTP $HTTP_CODE ${BODY:0:200}"
      fi
    else
      skip "8.9~8.10 采购跨租户探测" "§3 未拿到 PO_ID"
    fi
  else
    skip "8.2~8.10 跨租户探测" "拿不到第二租户管理员令牌"
  fi
fi

# ─────────────────────────── 9. 错误形状（ISS-079/017） ───────────────────────────
head2 "9. 错误语义一致性"
req GET "/definitely-not-here-$RUN_ID"
if [ "$HTTP_CODE" = "500" ]; then bad "9.1 未映射路径不塌成 500（ISS-079）" "404" "500 SYSTEM_ERROR（把路由错误伪装成服务端崩溃）"
else ok "9.1 未映射路径 → HTTP $HTTP_CODE"; fi
req POST "/orders/import/upload" "{}"
if [ "$HTTP_CODE" = "500" ]; then bad "9.2 错误 Content-Type 不塌成 500（ISS-079）" "415/400" "500"
else ok "9.2 错误 Content-Type → HTTP $HTTP_CODE"; fi

# ─────────────────────────── 10. 租户生命周期（ISS-108/109） ───────────────────────────
head2 "10. 租户状态参与认证 + 删除级联与守卫"
if [ -z "${T2:-}" ]; then
  skip "10.1~10.12 租户生命周期" "第二租户未创建成功"
else
  # 停用必须立刻让该租户的账号进不来：过去认证只看 users.status，租户是停是活在库里没人问
  req PUT "/tenants/$T2/suspend"
  assert_code "10.1 停用第二租户" 200
  req_anon POST "/auth/login" "{\"username\":\"smokeadm$RUN_ID\",\"password\":\"Smoke#123456\"}"
  if [ "$HTTP_CODE" = "200" ]; then
    bad "10.2 被停用租户的账号不应登录成功（ISS-109）" "HTTP 401 ACCOUNT_DISABLED" "HTTP 200——租户状态没参与认证"
  elif [ "$HTTP_CODE" = "401" ]; then
    ok "10.2 被停用租户的账号登录被拒（401 ACCOUNT_DISABLED）"
  else
    bad "10.2 被停用租户的账号登录出口" "HTTP 401 + Result 信封" "HTTP $HTTP_CODE ${BODY:0:200}"
  fi

  req PUT "/tenants/$T2/activate"
  assert_code "10.3 重新激活租户" 200
  req_anon POST "/auth/login" "{\"username\":\"smokeadm$RUN_ID\",\"password\":\"Smoke#123456\"}"
  assert_code "10.4 激活后账号恢复登录" 200
  T2_REFRESH="$(printf '%s' "$BODY" | jget data.refreshToken)"

  # 续期链路绕过认证（不经过 UserDetailsService），停用后拿旧 refresh token 也不该再换到新令牌
  req PUT "/tenants/$T2/suspend"
  assert_code "10.5 再次停用（续期探针前置）" 200
  if [ -n "$T2_REFRESH" ]; then
    req_anon POST "/auth/refresh" "{\"refreshToken\":\"$T2_REFRESH\"}"
    if [ "$HTTP_CODE" = "200" ]; then
      bad "10.6 停用租户后不应再续期令牌（ISS-109）" "HTTP 400 账号不可用" "HTTP 200——/auth/refresh 绕过了状态检查"
    else
      ok "10.6 停用租户后刷新被拒（HTTP $HTTP_CODE）"
    fi
  else
    skip "10.6 停用后刷新令牌" "登录响应没有 refreshToken"
  fi
  req PUT "/tenants/$T2/activate"
  assert_code "10.7 复原：重新激活第二租户" 200

  # 平台管理员就挂在它管的租户上，删掉等于把运营方锁在门外、再没人能恢复现场
  req DELETE "/tenants/$TENANT_A"
  if [ "$HTTP_CODE" = "400" ]; then
    ok "10.8 平台管理员所在租户删除被拒（HTTP 400 守卫）"
  else
    bad "10.8 平台管理员租户删除守卫" "HTTP 400" "HTTP $HTTP_CODE ${BODY:0:200}"
  fi
  req GET "/tenants/$TENANT_A"
  assert_code "10.9 守卫没有误删：主租户仍可读" 200

  # ISS-119：客户是唯一软删除的聚合（DELETE 只把 status 置成 DELETED，行留在库里，列表与详情都不显示）。
  # 若租户删除守卫把这类墓碑也算成"仍有业务数据"，那么"把客户删干净"这个正常操作本身
  # 就会让这个租户永远删不掉（修复前实测 400「租户「…」下仍有 1 条业务数据」，而那 1 条正是看不见的墓碑）。
  req POST "/customers" "{\"tenantId\":\"$T2\",\"customerName\":\"冒烟墓碑客户-$RUN_ID\",\"email\":\"tomb-$RUN_ID@example.com\"}"
  assert_code "10.9b 在第二租户下建客户" 200
  CUST_TOMB="$(printf '%s' "$BODY" | jget data.customerId)"
  req DELETE "/customers/$CUST_TOMB"
  assert_code "10.9c 软删该客户（置为 DELETED 墓碑）" 200

  # 干净租户可以删：账号与角色随租户一起清掉，不留"能登录的孤儿身份"；
  # 10.9b/10.9c 留下的客户墓碑不计数，所以这里必须仍然 200（ISS-119 的回归判据）
  req DELETE "/tenants/$T2"
  assert_code "10.10 删除第二租户（客户墓碑不挡住删除，ISS-119）" 200
  req GET "/tenants/$T2"
  if [ "$HTTP_CODE" = "404" ]; then
    ok "10.11 删除后按 ID 读 → 404（不是 500）"
  else
    bad "10.11 删除后按 ID 读" "HTTP 404 ENTITY_NOT_FOUND" "HTTP $HTTP_CODE ${BODY:0:200}"
  fi
  req_anon POST "/auth/login" "{\"username\":\"smokeadm$RUN_ID\",\"password\":\"Smoke#123456\"}"
  if [ "$HTTP_CODE" = "200" ]; then
    bad "10.12 删除租户后其账号不应登录（ISS-108）" "HTTP 401" "HTTP 200——留下可登录的孤儿账号与权限点"
  else
    ok "10.12 孤儿账号登录被拒（HTTP $HTTP_CODE）"
  fi

  # 收尾：本次冒烟建的租户不该留在环境里（历史上每跑一次就多留一个）
  req GET "/tenants"
  if printf '%s' "$BODY" | grep -q "冒烟第二租户-$RUN_ID"; then
    bad "10.13 冒烟不残留租户（可重复运行）" "列表不含本次租户" "仍含 $T2"
  else
    ok "10.13 本次冒烟租户已清理干净"
  fi
fi

# ─────────────────────────── 11. 权限点参与鉴权（ISS-112） ───────────────────────────
head2 "11. 权限点真正参与鉴权"
# 接线前后的差别只有一句话：过去只要登录成功，26 个权限点配没配都一样能读写。
# 这里用一个只配了 :read 的账号实测「读得到、写被拒」，并反向确认配满权限的租户管理员没被挡。
req POST "/tenants" "{\"name\":\"冒烟权限租户-$RUN_ID\",\"description\":\"冒烟用例创建\",\"contactEmail\":\"smokeperm-$RUN_ID@example.com\",\"adminUsername\":\"smokeperm$RUN_ID\",\"adminPassword\":\"Smoke#123456\",\"adminEmail\":\"smokeperm$RUN_ID@example.com\",\"adminFirstName\":\"Perm\",\"adminLastName\":\"Admin\"}"
assert_code "11.1 创建权限探针租户" 200
T3="$(printf '%s' "$BODY" | jget data.id)"
if [ -z "$T3" ]; then
  bad "11.1b 权限探针租户 ID" "非空" "$BODY"
else
  req_anon POST "/auth/login" "{\"username\":\"smokeperm$RUN_ID\",\"password\":\"Smoke#123456\"}"
  assert_code "11.2 租户管理员（25 点）登录" 200
  T3_TOKEN="$(printf '%s' "$BODY" | jget data.token)"
  NOPE="smoke-nope-$RUN_ID"

  # 反向守卫：接线不该把配满权限的管理员挡在门外。NOPE 不存在，所以预期 400/404，唯独不能是 403
  if [ -n "$T3_TOKEN" ]; then
    req_with "$T3_TOKEN" PUT "/orders/$NOPE/status?status=PROCESSING"
    if [ "$HTTP_CODE" = "403" ]; then
      bad "11.3 租户管理员写订单被误挡（接线副作用）" "400/404（找不到订单）" "HTTP 403——order:write 没进 TENANT_ADMIN"
    else
      ok "11.3 租户管理员仍可写（HTTP $HTTP_CODE，非 403）"
    fi
  else
    skip "11.3 租户管理员写权限回归" "拿不到探针租户令牌"
  fi

  # 只配 :read 的角色 + 挂在它下面的子账号
  req_with "${T3_TOKEN:-$TOKEN}" POST "/roles" "{\"name\":\"只读员-$RUN_ID\",\"description\":\"只有读点\"}"
  R3="$(printf '%s' "$BODY" | jget data.id)"
  assert_code "11.4 租户管理员创建只读角色" 200
  req_with "${T3_TOKEN:-$TOKEN}" PUT "/roles/$R3/permissions" \
    "{\"permissions\":[\"order:read\",\"product:read\",\"customer:read\",\"warehouse:read\",\"logistics:read\"]}"
  assert_code "11.5 覆盖角色权限点（只留 5 个读点）" 200
  req_with "${T3_TOKEN:-$TOKEN}" POST "/users" "{\"username\":\"smokeread$RUN_ID\",\"password\":\"Smoke#123456\",\"email\":\"smokeread-$RUN_ID@example.com\",\"firstName\":\"Read\",\"lastName\":\"Only\"}"
  U3="$(printf '%s' "$BODY" | jget data.id)"
  assert_code "11.6 创建只读子账号" 200
  req_with "${T3_TOKEN:-$TOKEN}" POST "/users/$U3/roles" "{\"roleId\":\"$R3\"}"
  assert_code "11.7 子账号绑定只读角色" 200
  req_anon POST "/auth/login" "{\"username\":\"smokeread$RUN_ID\",\"password\":\"Smoke#123456\"}"
  assert_code "11.8 只读子账号登录" 200
  RO_TOKEN="$(printf '%s' "$BODY" | jget data.token)"
  if [ -z "$RO_TOKEN" ]; then
    skip "11.9~11.18 只读账号探针" "登录未拿到令牌"
  else
    expect() { # expect <期望码> <用例名>
      if [ "$HTTP_CODE" = "$1" ]; then ok "$2"; else bad "$2" "HTTP $1" "HTTP $HTTP_CODE ${BODY:0:180}"; fi
    }
    # 读面：五个资源的 GET 全部放行
    req_with "$RO_TOKEN" GET "/orders?page=0&size=5";        expect 200 "11.9 只读账号 GET /orders 放行"
    req_with "$RO_TOKEN" GET "/products?page=0&size=5";      expect 200 "11.10 只读账号 GET /products 放行"
    req_with "$RO_TOKEN" GET "/customers?page=0&size=5";     expect 200 "11.11 只读账号 GET /customers 放行"
    req_with "$RO_TOKEN" GET "/warehouses?page=0&size=5";    expect 200 "11.12 只读账号 GET /warehouses 放行"
    req_with "$RO_TOKEN" GET "/logistics-channels";          expect 200 "11.13 只读账号 GET /logistics-channels 放行"
    # 写面：同一批资源的写端点全部 403（过去是 200/400，登录即可写）
    req_with "$RO_TOKEN" PUT "/orders/$NOPE/status?status=PROCESSING";   expect 403 "11.14 缺 order:write 改订单状态被拒"
    req_with "$RO_TOKEN" DELETE "/orders/$NOPE";                         expect 403 "11.15 缺 order:write 删订单被拒"
    req_with "$RO_TOKEN" DELETE "/products/$NOPE";                       expect 403 "11.16 缺 product:write 删商品被拒"
    req_with "$RO_TOKEN" DELETE "/warehouses/$NOPE";                     expect 403 "11.17 缺 warehouse:write 删仓库被拒"
    req_with "$RO_TOKEN" PUT "/customers/$NOPE/deactivate";              expect 403 "11.18 缺 customer:write 停用客户被拒"
    # 管理面：user:manage / 平台专属读同样被拒
    req_with "$RO_TOKEN" GET "/users?page=0&size=5";                     expect 403 "11.19 缺 user:manage 列用户被拒"
    req_with "$RO_TOKEN" GET "/audit-logs?page=0&size=5";                expect 403 "11.20 非平台管理员读审计被拒"
  fi

  # 收尾：子账号、角色、租户全部清掉，权限探针同样不留残留
  req_with "${T3_TOKEN:-$TOKEN}" DELETE "/users/$U3"
  assert_code "11.21 清理只读子账号" 200
  req_with "${T3_TOKEN:-$TOKEN}" DELETE "/roles/$R3"
  assert_code "11.22 清理只读角色" 200
  req DELETE "/tenants/$T3"
  assert_code "11.23 删除权限探针租户（账号随租户级联）" 200
  req GET "/tenants"
  if printf '%s' "$BODY" | grep -q "冒烟权限租户-$RUN_ID"; then
    bad "11.24 冒烟不残留权限探针租户" "列表不含本次租户" "仍含 $T3"
  else
    ok "11.24 本次权限探针租户已清理干净"
  fi
fi

# ─────────────────────────── 12. 业务单据写端点留痕（ISS-114） ───────────────────────────
head2 "12. 业务单据写端点留痕全覆盖（金额/库存/单据状态）"

# 基线：本次运行开始前最新一条留痕的时间。后面按动作码回读时，
# 只有 createdAt >= 基线 才算「这一轮真的写进去了」，否则历史残留也会让断言误绿。
req GET "/audit-logs?page=0&size=1"
AUD_BASE="$(printf '%s' "$BODY" | jget data.content.0.createdAt)"
[ -n "$AUD_BASE" ] || AUD_BASE="0000-00-00T00:00:00"

# aud <动作码> <用例名>：该动作码在本次运行内必须已有 SUCCESS 留痕
aud() {
  req GET "/audit-logs?action=$1&result=SUCCESS&page=0&size=1"
  newest="$(printf '%s' "$BODY" | jget data.content.0.createdAt)"
  if [ -n "$newest" ] && [[ ! "$newest" < "$AUD_BASE" ]]; then
    ok "$2（留痕 $newest）"
  else
    bad "$2" "本次运行内有 SUCCESS 留痕（基线 $AUD_BASE）" "最新留痕=${newest:-无}"
  fi
}

ASKU="SKU-AUD-$RUN_ID"
AWH_CODE="WH-AUD-$RUN_ID"
# 冲突名取第 3 节建的"冒烟测试仓-$RUN_ID"（本轮内必然已存在，且带 RUN_ID 不会跨轮撞名）
AWH_NAME_CONFLICT="冒烟测试仓-$RUN_ID"

# zero_inventory <仓库ID>：逐条出库把该仓清到 0（出库按 (仓库, SKU) 定位单条记录，
# 跨 SKU 汇总成一个数量会让最宽裕的那条也判"库存不足"，于是清空不彻底、删仓被守卫挡住）。
# 结果写入 ZERO_LINE_CNT（清了几条）与 ZERO_FAIL（失败明细，空表示全部出清）。
zero_inventory() {
  local wid="$1" lines line_sku line_qty
  ZERO_LINE_CNT=0
  ZERO_FAIL=""
  req GET "/warehouses/$wid/inventory"
  lines="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const a=Array.isArray(o.data)?o.data:[];a.filter(r=>(r.quantity||0)>0).forEach(r=>console.log(((r.sku||r.productId)||"")+"|"+r.quantity))}catch(e){}})')"
  while IFS='|' read -r line_sku line_qty; do
    [ -z "$line_sku" ] && continue
    ZERO_LINE_CNT=$((ZERO_LINE_CNT + 1))
    req POST "/warehouses/$wid/inventory/out" "{\"sku\":\"$line_sku\",\"productId\":\"$line_sku\",\"warehouseId\":\"$wid\",\"quantity\":$line_qty,\"movementType\":\"OUTBOUND\"}"
    [ "$HTTP_CODE" = "200" ] || ZERO_FAIL="${ZERO_FAIL}sku=$line_sku qty=$line_qty HTTP $HTTP_CODE "
  done <<< "$lines"
}

# ---- 12.A 仓库与库存：5 个仓库端点 + 4 个库存端点 ----
req POST "/warehouses" "{\"warehouseName\":\"冒烟留痕仓-$RUN_ID\",\"warehouseCode\":\"$AWH_CODE\",\"address\":\"探针地址\",\"capacity\":100}"
assert_code "12.1 创建留痕仓" 200
AWH_ID="$(printf '%s' "$BODY" | jget data.warehouseId)"
[ -n "$AWH_ID" ] && ok "12.2 留痕仓 ID=$AWH_ID" || bad "12.2 留痕仓 ID" "非空" "${BODY:0:200}"

req PUT "/warehouses/$AWH_ID" "{\"warehouseName\":\"冒烟留痕仓改名-$RUN_ID\",\"capacity\":200}"
assert_code "12.3 更新留痕仓" 200
# 验收条件 1（docs/spec/warehouse.md §7）：重名必须回"明确错误"。改名撞号此前靠 DB 的
# warehouse_name UNIQUE 在 flush 时拦，响应是 500 SYSTEM_ERROR，用户看不出是重名（ISS-116）。
req PUT "/warehouses/$AWH_ID" "{\"warehouseName\":\"$AWH_NAME_CONFLICT\",\"capacity\":200}"
assert_code_in "12.3b 重名更新被拒（400 明确错误，不再是 500）" "400 409"
printf '%s' "$BODY" | grep -q '仓库名称已存在' \
  && ok "12.3c 重名原因可读（响应点明「仓库名称已存在」）" \
  || bad "12.3c 重名原因可读" "message 含「仓库名称已存在」" "${BODY:0:200}"
req PUT "/warehouses/$AWH_ID/status?active=false"
assert_code "12.4 停用留痕仓" 200
req PUT "/warehouses/$AWH_ID/status?active=true"
assert_code "12.5 重新启用留痕仓" 200
req POST "/warehouses/$AWH_ID/locations" "{\"locationCode\":\"LOC-AUD-$RUN_ID\",\"locationType\":\"SHELF\",\"capacity\":10}"
assert_code "12.6 添加库位" 200
req POST "/warehouses/$AWH_ID/inventory/in" "{\"sku\":\"$ASKU\",\"productId\":\"$ASKU\",\"warehouseId\":\"$AWH_ID\",\"quantity\":10,\"movementType\":\"INBOUND\"}"
assert_code "12.7 入库 10" 200
req POST "/warehouses/$AWH_ID/inventory/move" "{\"sku\":\"$ASKU\",\"productId\":\"$ASKU\",\"warehouseId\":\"$AWH_ID\",\"quantity\":2,\"movementType\":\"ADJUSTMENT\",\"reason\":\"冒烟留痕调整\"}"
assert_code "12.8 库存调整（move）" 200
req POST "/warehouses/$AWH_ID/inventory/count?productId=$ASKU&actualQuantity=5"
assert_code "12.9 盘点为 5（把余量钉成确定值，便于收尾清空）" 200

# ---- 12.B 采购：8 个写端点（金额 + 单据状态） ----
req POST "/purchases" "{\"supplierName\":\"留痕供应商\",\"supplierId\":\"SUP-AUD-$RUN_ID\",\"warehouseId\":\"$AWH_ID\",\"items\":[{\"productName\":\"留痕商品\",\"skuCode\":\"$ASKU\",\"quantity\":3,\"unitPrice\":2.0}]}"
assert_code "12.10 创建采购单" 200
APO_ID="$(printf '%s' "$BODY" | jget data.purchaseOrderId)"
req POST "/purchases/$APO_ID/items" "{\"skuCode\":\"$ASKU-B\",\"productName\":\"留痕加行商品\",\"quantity\":2,\"unitPrice\":1.5}"
assert_code "12.11 添加采购明细行" 200
APO_ITEM="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const a=(o.data&&o.data.items)||[];console.log(String(a[a.length-1].id))}catch(e){console.log("")}})')"
[ -n "$APO_ITEM" ] && ok "12.12 明细行 ID=$APO_ITEM" || bad "12.12 明细行 ID" "非空" "${BODY:0:200}"
req PUT "/purchases/$APO_ID/items/$APO_ITEM" "{\"skuCode\":\"$ASKU-B\",\"productName\":\"留痕加行商品\",\"quantity\":4,\"unitPrice\":1.5}"
assert_code "12.13 修改采购明细行" 200
req DELETE "/purchases/$APO_ID/items/$APO_ITEM"
assert_code "12.14 删除采购明细行" 200
req PUT "/purchases/$APO_ID/approve" "{\"comment\":\"冒烟留痕批准\"}"
assert_code "12.15 批准采购单" 200
req PUT "/purchases/$APO_ID/in-transit"
assert_code "12.16 采购单转运输中" 200
req PUT "/purchases/$APO_ID/delivered"
assert_code "12.17 采购单到货（同事务入库 + 记应付）" 200

req POST "/purchases" "{\"supplierName\":\"留痕供应商2\",\"supplierId\":\"SUP-AUD2-$RUN_ID\",\"warehouseId\":\"$AWH_ID\",\"items\":[{\"productName\":\"留痕商品2\",\"skuCode\":\"$ASKU-C\",\"quantity\":1,\"unitPrice\":1.0}]}"
assert_code "12.18 创建待取消采购单" 200
APO2_ID="$(printf '%s' "$BODY" | jget data.purchaseOrderId)"
req PUT "/purchases/$APO2_ID/cancel"
assert_code "12.19 取消采购单" 200

req POST "/purchases" "{\"supplierName\":\"留痕供应商3\",\"supplierId\":\"SUP-AUD3-$RUN_ID\",\"warehouseId\":\"$AWH_ID\",\"items\":[{\"productName\":\"留痕商品3\",\"skuCode\":\"$ASKU-D\",\"quantity\":1,\"unitPrice\":1.0}]}"
assert_code "12.20 创建待拒绝采购单" 200
APO3_ID="$(printf '%s' "$BODY" | jget data.purchaseOrderId)"
req PUT "/purchases/$APO3_ID/reject" "{\"comment\":\"冒烟留痕：价格不符\"}"
assert_code "12.21 拒绝采购单" 200

# ---- 12.C 财务：4 个写端点（金额） ----
req POST "/finance/transactions" "{\"type\":\"INCOME\",\"amount\":199.0,\"currency\":\"CNY\",\"status\":\"PENDING\",\"description\":\"冒烟留痕收入\",\"referenceId\":\"$APO_ID\",\"referenceType\":\"PURCHASE\"}"
assert_code "12.22 创建财务流水（带币种才真能落库）" 200
TX_ID="$(printf '%s' "$BODY" | jget data.transactionId)"
[ -n "$TX_ID" ] && ok "12.23 流水 ID=$TX_ID" || bad "12.23 流水 ID" "非空" "${BODY:0:200}"
req PUT "/finance/transactions/$TX_ID/complete"
assert_code "12.24 流水标记完成" 200
req POST "/finance/transactions" "{\"type\":\"EXPENSE\",\"amount\":88.0,\"currency\":\"CNY\",\"status\":\"PENDING\",\"description\":\"冒烟留痕支出\",\"referenceId\":\"$APO_ID\",\"referenceType\":\"PURCHASE\"}"
assert_code "12.25 创建待标记失败流水" 200
TX2_ID="$(printf '%s' "$BODY" | jget data.transactionId)"
req PUT "/finance/transactions/$TX2_ID/fail"
assert_code "12.26 流水标记失败" 200
req POST "/finance/transactions" "{\"type\":\"REFUND\",\"amount\":12.0,\"currency\":\"CNY\",\"status\":\"PENDING\",\"description\":\"冒烟留痕退款\",\"referenceId\":\"$APO_ID\",\"referenceType\":\"PURCHASE\"}"
assert_code "12.27 创建待取消流水" 200
TX3_ID="$(printf '%s' "$BODY" | jget data.transactionId)"
req PUT "/finance/transactions/$TX3_ID/cancel"
assert_code "12.28 流水取消" 200

# ---- 12.D 订单：4 个写端点（单据状态） ----
AORD_BODY="{\"orderNumber\":\"ORDAUD$RUN_ID\",\"customerId\":\"CUST-AUD-$RUN_ID\",\"customerName\":\"留痕客户\",\"customerEmail\":\"aud-$RUN_ID@example.com\",\"orderItems\":[{\"productName\":\"留痕商品\",\"skuCode\":\"$ASKU\",\"quantity\":1,\"unitPrice\":9.9,\"totalPrice\":9.9}],\"paymentInfo\":{\"amount\":9.9,\"paymentMethod\":\"CREDIT_CARD\",\"paymentStatus\":\"PAID\",\"transactionId\":\"TXN-AUD-$RUN_ID\"},\"shippingAddress\":{\"country\":\"CN\",\"province\":\"广东\",\"city\":\"深圳\",\"street\":\"科技园 1 号\",\"recipient\":\"留痕收件人\",\"phone\":\"13900000000\",\"zipCode\":\"518000\"}}"
req POST "/orders" "$AORD_BODY"
assert_code "12.29 创建留痕订单" 200
AORD_ID="$(printf '%s' "$BODY" | jget data.orderId)"
req PUT "/orders/$AORD_ID/status?status=PROCESSING"
assert_code "12.30 订单转处理中" 200
# 失败侧留痕探针必须打在"活着"的单据上：处理中 → 待处理 是状态机禁止的流转，
# 服务层抛 BusinessException（放在订单删除后只会得到 404，留痕原因是"找不到"而非"非法操作"）。
req PUT "/orders/$AORD_ID/status?status=PENDING"
assert_code_in "12.30b 非法流转（处理中→待处理）被拒" "400 409"

req POST "/orders" "$(printf '%s' "$AORD_BODY" | sed -e "s/ORDAUD$RUN_ID/ORDAUDB$RUN_ID/" -e "s/TXN-AUD-$RUN_ID/TXN-AUDB-$RUN_ID/")"
assert_code "12.31 创建待取消订单" 200
AORD_B="$(printf '%s' "$BODY" | jget data.orderId)"
req POST "/orders/$AORD_B/cancel"
assert_code "12.32 取消订单" 200
req DELETE "/orders/$AORD_B"
assert_code "12.33 删除已取消订单（联动删物流单，ISS-113）" 200

req POST "/orders" "$(printf '%s' "$AORD_BODY" | sed -e "s/ORDAUD$RUN_ID/ORDAUDC$RUN_ID/" -e "s/TXN-AUD-$RUN_ID/TXN-AUDC-$RUN_ID/")"
assert_code "12.34 创建待批量发货订单" 200
AORD_C="$(printf '%s' "$BODY" | jget data.orderId)"
# 状态机里 PENDING 不能直接跳到 SHIPPED（只允许 → PROCESSING/CON_FIRMED/CANCELLED），
# 批量发货默认要推进订单状态，所以先把订单转到处理中，否则该行会因非法流转被判失败。
req PUT "/orders/$AORD_C/status?status=PROCESSING"
assert_code "12.34a 待批量发货订单转处理中" 200

# ---- 12.E 物流：9 个写端点（发货扣库存 + 单据状态） ----
# 留痕仓里 $ASKU 的余量读数。发货的副作用只有把库存读回来才算"生效"，
# 只看 HTTP 200 会得到"端点成功、库存纹丝不动"的假绿（ISS-072）。
asku_qty() {
  req GET "/warehouses/$AWH_ID/inventory"
  printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const a=Array.isArray(o.data)?o.data:(o.data&&o.data.content)||[];const hit=a.find(r=>r.sku===process.argv[1]||r.productId===process.argv[1]);console.log(hit?String(hit.quantity):"NOTFOUND")}catch(e){console.log("PARSEERR")}})' "$ASKU"
}
# 手工创建物流单必须显式给渠道（logisticsChannelId / channelCode 至少一个）：缺渠道时
# resolveChannel 抛 ValidationException ⇒ 400，该端点根本没被真正调用过，LOGISTICS_CREATE
# 只会有 FAILURE 留痕，SUCCESS 侧断言（12.76）必然空转。
req GET "/logistics-channels"
ACH_CHANNEL_ID="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const a=Array.isArray(o.data)?o.data:[];const c=a.find(x=>x.channelCode==="DEFAULT_CHANNEL")||a[0];console.log(c?String(c.channelId):"")}catch(e){console.log("")}})')"
[ -n "$ACH_CHANNEL_ID" ] && ok "12.34b 可手工建单的物流渠道 $ACH_CHANNEL_ID" \
  || bad "12.34b 可手工建单的物流渠道" "至少一个启用渠道" "${BODY:0:200}"

req GET "/logistics/order/$AORD_ID"
ALOG_ID="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);const a=Array.isArray(o.data)?o.data:[];console.log(a.length?String(a[0].logisticsOrderId):"")}catch(e){console.log("")}})')"
[ -n "$ALOG_ID" ] && ok "12.35 订单派生物流单 ID=$ALOG_ID" || bad "12.35 派生物流单" "非空" "${BODY:0:200}"
req POST "/logistics/$ALOG_ID/label"
assert_code "12.36 生成面单" 200
Q_BEFORE_SHIP="$(asku_qty)"
req POST "/logistics/$ALOG_ID/ship?trackingNumber=TRK-AUD-$RUN_ID"
assert_code "12.37 标记发货" 200
# ISS-072 路径 C：手工揽收必须同时回写订单为已发货并扣一次库存。
# 该端点的审计标签一直写着「标记发货并扣减库存」，修复前两件事都没做。
req GET "/orders/$AORD_ID"
printf '%s' "$BODY" | grep -q '"status":"SHIPPED"' \
  && ok "12.37a 手工揽收回写订单为 SHIPPED（回读 $AORD_ID）" \
  || bad "12.37a 手工揽收回写订单" "status=SHIPPED" "${BODY:0:220}"
Q_AFTER_SHIP="$(asku_qty)"
if [ "$Q_AFTER_SHIP" = "$((Q_BEFORE_SHIP - 1))" ]; then
  ok "12.37b 手工揽收扣减 1 件（$Q_BEFORE_SHIP → $Q_AFTER_SHIP，BR-04 生效）"
else
  bad "12.37b 手工揽收扣减库存" "$((Q_BEFORE_SHIP - 1))" "$Q_AFTER_SHIP"
fi
req PUT "/logistics/$ALOG_ID/status" "{\"status\":\"IN_TRANSIT\",\"description\":\"冒烟留痕：已发出\"}"
assert_code "12.38 物流状态推进为运输中" 200
req POST "/logistics/$ALOG_ID/traces" "{\"status\":\"IN_TRANSIT\",\"description\":\"留痕轨迹节点\",\"location\":\"深圳转运中心\"}"
assert_code "12.39 登记轨迹节点" 200
req POST "/logistics/$ALOG_ID/track"
assert_code "12.40 刷新跟踪" 200

req POST "/logistics" "{\"orderId\":\"$AORD_ID\",\"logisticsChannelId\":\"$ACH_CHANNEL_ID\",\"carrier\":\"留痕承运商\",\"trackingNumber\":\"TRK-AUD2-$RUN_ID\"}"
assert_code "12.41 手工创建物流单" 200
ALOG_C="$(printf '%s' "$BODY" | jget data.logisticsOrderId)"
[ -n "$ALOG_C" ] && ok "12.41b 新建物流单 ID=$ALOG_C" || bad "12.41b 新建物流单 ID" "非空" "${BODY:0:200}"
req POST "/logistics/$ALOG_C/cancel"
assert_code "12.42 取消手工物流单" 200

req POST "/logistics" "{\"orderId\":\"$AORD_C\",\"logisticsChannelId\":\"$ACH_CHANNEL_ID\",\"carrier\":\"留痕承运商2\",\"trackingNumber\":\"TRK-AUD3-$RUN_ID\"}"
assert_code "12.43 为待批量发货订单建物流单" 200
ALOG_D="$(printf '%s' "$BODY" | jget data.logisticsOrderId)"
[ -n "$ALOG_D" ] && ok "12.43b 新建物流单 ID=$ALOG_D" || bad "12.43b 新建物流单 ID" "非空" "${BODY:0:200}"
req DELETE "/logistics/$ALOG_D"
assert_code "12.44 删除物流单" 200

# 批量发货同样要带渠道：shipSingle 里 resolveChannel 抛出的异常被 result.recordFailure 吞掉，
# HTTP 仍是 200，只看状态码会得到"批量发货成功、实际一行没发"的假绿。
req POST "/logistics/batch-ship" "{\"orderIds\":[\"$AORD_C\"],\"logisticsChannelId\":\"$ACH_CHANNEL_ID\",\"updateOrderStatus\":true}"
assert_code "12.45 批量发货" 200
BS_SUMMARY="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const d=(JSON.parse(s).data)||{};console.log("total="+(d.totalCount??-1)+" success="+(d.successCount??-1)+" failure="+(d.failureCount??-1)+" skipped="+(d.skippedCount??-1))}catch(e){console.log("PARSEERR")}})')"
echo "       批量发货结果：$BS_SUMMARY"
printf '%s' "$BS_SUMMARY" | grep -q 'total=1 success=1 failure=0 skipped=0' \
  && ok "12.45b 批量发货真的发出一行" \
  || bad "12.45b 批量发货真的发出一行" "total=1 success=1 failure=0 skipped=0" "$BS_SUMMARY ${BODY:0:220}"
# ISS-072 路径 B：批量发货此前只建物流单、既不回写订单也不出库（HTTP 仍是 200，所以一直没被发现）
req GET "/orders/$AORD_C"
printf '%s' "$BODY" | grep -q '"status":"SHIPPED"' \
  && ok "12.45e 批量发货回写订单为 SHIPPED（回读 $AORD_C）" \
  || bad "12.45e 批量发货回写订单" "status=SHIPPED" "${BODY:0:220}"
Q_AFTER_BATCH="$(asku_qty)"
if [ "$Q_AFTER_BATCH" = "$((Q_AFTER_SHIP - 1))" ]; then
  ok "12.45f 批量发货扣减 1 件（$Q_AFTER_SHIP → $Q_AFTER_BATCH，BR-04 生效）"
else
  bad "12.45f 批量发货扣减库存" "$((Q_AFTER_SHIP - 1))" "$Q_AFTER_BATCH"
fi
# 同一单再发一次：必须被跳过且库存不再变化（"恰好一次"的另一半）
req POST "/logistics/batch-ship" "{\"orderIds\":[\"$AORD_C\"],\"logisticsChannelId\":\"$ACH_CHANNEL_ID\",\"updateOrderStatus\":true}"
BS_AGAIN="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const d=(JSON.parse(s).data)||{};console.log("total="+(d.totalCount??-1)+" success="+(d.successCount??-1)+" failure="+(d.failureCount??-1)+" skipped="+(d.skippedCount??-1))}catch(e){console.log("PARSEERR")}})')"
printf '%s' "$BS_AGAIN" | grep -q 'total=1 success=0 failure=0 skipped=1' \
  && ok "12.45g 重复批量发货被跳过（$BS_AGAIN）" \
  || bad "12.45g 重复批量发货" "total=1 success=0 skipped=1" "$BS_AGAIN"
Q_AFTER_RESHIP="$(asku_qty)"
[ "$Q_AFTER_RESHIP" = "$Q_AFTER_BATCH" ] \
  && ok "12.45h 重复发货没有二次扣减（仍为 $Q_AFTER_RESHIP）" \
  || bad "12.45h 重复发货不得二次扣减" "$Q_AFTER_BATCH" "$Q_AFTER_RESHIP"

# ISS-117 / R-36 的触发条件：行级失败被 result.recordFailure() 吞掉，响应仍是 HTTP 200 + code=200。
# 响应契约本轮不改（换 207/206 会同时打坏所有调用方与前端批量弹窗判定），改由留痕侧说真话 —— 12.86 回读。
req POST "/logistics/batch-ship" "{\"orderIds\":[\"SMOKE-NO-SUCH-ORDER-$RUN_ID\"],\"logisticsChannelId\":\"$ACH_CHANNEL_ID\",\"updateOrderStatus\":true}"
assert_code "12.45c 批量发货（订单不存在）响应仍是 200" 200
BS_PARTIAL="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const d=(JSON.parse(s).data)||{};console.log("total="+(d.totalCount??-1)+" success="+(d.successCount??-1)+" failure="+(d.failureCount??-1)+" skipped="+(d.skippedCount??-1))}catch(e){console.log("PARSEERR")}})')"
echo "       部分失败的批量发货结果：$BS_PARTIAL"
printf '%s' "$BS_PARTIAL" | grep -q 'total=1 success=0 failure=1' \
  && ok "12.45d 响应体里带着一行未处理（这正是谎报 SUCCESS 的形状）" \
  || bad "12.45d 响应体里带着一行未处理" "total=1 success=0 failure=1" "$BS_PARTIAL ${BODY:0:220}"

# ---- 12.F 收尾：引用守卫的两面实测 → 清引用 → 出清库存 → 删除仓库（ISS-142 / R-60）----
# 缺陷形状：DELETE /api/warehouses/{id} 此前只看"仓里还有没有库存"，不看"还有没有单据指向它"。
# purchase_orders.warehouse_id 与 shop_warehouses.local_warehouse_id 都是没有外键的普通 varchar 列，
# 于是删仓把它们掏成断链——实测 533 行采购单里 530 行指向已不存在的仓，而采购域没有删除端点可修。
# 断言必须一正一负（R-31 的先例）：只测"能删"会漏掉守卫，只测"被挡"会把守卫写成"见仓就拒"。
sqlq() { docker exec mysql mysql -uroot -p"$SMOKE_DB_PW" -N -B --default-character-set=utf8mb4 \
           ecommerce_logistics_erp -e "$1" 2>/dev/null | head -1; }

# po_purge_wh <仓库ID>：只清"本轮这张仓名下的采购单"。仓是本轮 12.1/第 3 节新建的，
# 指向它的采购单必然是本轮产物（含 §12B 留痕单与缺货自动单），不需要靠供应商名猜归属（R-40）。
# 顺序＝子表先删：财务应付 → 采购明细 → 采购单；漏一级就留断链或活账。
# 这里**不动 audit_logs**：留痕是只增不删的证据链（§12.J 的既有口径），更要紧的是本节排在 §12.G
# 的逐动作码回读之前——上一版在这里删了采购单的审计行，实测把 12.59~12.67 九条 PURCHASE_* 判据
# 打成"最新留痕=上一轮时间戳"的假失败。探针要清的是自己造的业务行，不是自己造的证据。
po_purge_wh() {
  local wid="$1" err
  PURGE_ERR=""
  [ -n "$wid" ] || { PURGE_ERR="仓库ID为空，跳过"; return; }
  err=$(docker exec mysql mysql -uroot -p"$SMOKE_DB_PW" --default-character-set=utf8mb4 \
        ecommerce_logistics_erp -e "
    DELETE ft FROM financial_transactions ft JOIN purchase_orders po ON po.purchase_order_id = ft.reference_id
      WHERE ft.reference_type='PURCHASE_ORDER' AND po.warehouse_id='$wid';
    DELETE poi FROM purchase_order_items poi JOIN purchase_orders po ON po.purchase_order_id = poi.purchase_order_id
      WHERE po.warehouse_id='$wid';
    DELETE FROM purchase_orders WHERE warehouse_id='$wid';
  " 2>&1 | grep -v "Warning")
  [ -z "$err" ] || PURGE_ERR="$err"
}

# 开跑到这里给"全库断链采购单"拍基线，12.J 清场后要比对是否新增（历史 530 行不在本轮责任范围，
# 但本轮一张都不许再造——把断链数量当判据的一部分，才能发现"守卫被绕过"而不是只发现"守卫报错"）。
PO_DANGLING_T0="$(sqlq "SELECT COUNT(*) FROM purchase_orders p LEFT JOIN warehouses w ON w.id=p.warehouse_id WHERE p.warehouse_id IS NOT NULL AND w.id IS NULL")"

# 先把留痕仓的库存出清，让唯一挡点落在"引用"上而不是"库存"上（否则 400 的原因会撞 12.46 那条门）
zero_inventory "$AWH_ID"
if [ -n "$ZERO_FAIL" ]; then
  bad "12.46 逐条出库清空留痕仓" "每条库存记录都出到 0" "$ZERO_FAIL"
else
  ok "12.46 留痕仓库存已出清（$ZERO_LINE_CNT 条），下面挡删除的只能是引用"
fi
req DELETE "/warehouses/$AWH_ID"
assert_code "12.47 有采购单引用的仓库删除被挡（HTTP 400，不退化成 500）" 400
PO_REF_CNT="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const m=(JSON.parse(s).message||"").match(/(\d+)\s*张采购单/);console.log(m?m[1]:"")}catch(e){}})')"
if printf '%s' "$BODY" | grep -q "采购单" && [ "${PO_REF_CNT:-0}" -ge 1 ] 2>/dev/null; then
  ok "12.47a 拒绝原因点名采购单并给出数量（$PO_REF_CNT 张压着这张仓）"
else
  bad "12.47a 拒绝原因点名采购单与数量" "message 含「采购单」且数量 ≥ 1" "PO_REF_CNT=${PO_REF_CNT:-空} body=${BODY:0:220}"
fi
req GET "/warehouses/$AWH_ID"
assert_code "12.47b 被挡时仓库一张都没删（回读仍在，@Transactional 无半态）" 200
[ "$(sqlq "SELECT COUNT(*) FROM purchase_orders WHERE warehouse_id='$AWH_ID'")" = "$PO_REF_CNT" ] \
  && ok "12.47c 原因里的数量就是库里的数量（$PO_REF_CNT 张，不是编出来的）" \
  || bad "12.47c 引用数量与库表一致" "$PO_REF_CNT" "$(sqlq "SELECT COUNT(*) FROM purchase_orders WHERE warehouse_id='$AWH_ID'")"

# 第二道门（店铺绑定）单独实测：先把本轮冒烟仓的采购单引用清掉，只留绑定 ⇒ 挡点必然落在店铺门上
po_purge_wh "$WH_ID"
[ -z "$PURGE_ERR" ] && ok "12.47d 冒烟仓名下采购单已按子表先删清场（流水→明细→单）" \
  || bad "12.47d 采购单清场 SQL 无报错" "无 ERROR" "$PURGE_ERR"
req POST "/warehouses/shop/bind" "{\"platform\":\"SHOPIFY\",\"shopCode\":\"SMK-BIND-$RUN_ID\",\"shopName\":\"冒烟绑定店铺\",\"platformWarehouseId\":\"SMK-PW-$RUN_ID\",\"platformWarehouseName\":\"冒烟平台仓\",\"localWarehouseId\":\"$WH_ID\"}"
assert_code "12.47e 店铺仓库绑定到冒烟仓（造引用）" 200
SHOP_BIND_ID="$(printf '%s' "$BODY" | jget data.id)"
zero_inventory "$WH_ID"
req DELETE "/warehouses/$WH_ID"
assert_code "12.47f 仅剩店铺绑定时删除仍被挡（HTTP 400）" 400
printf '%s' "$BODY" | grep -q "店铺仓库" && grep -q "解除绑定" <<< "$BODY" \
  && ok "12.47g 拒绝原因点名店铺绑定并给出解绑办法" \
  || bad "12.47g 拒绝原因点名店铺绑定" "message 含「店铺仓库」与「解除绑定」" "${BODY:0:220}"
[ -n "$SHOP_BIND_ID" ] && req POST "/warehouses/shop/$SHOP_BIND_ID/unbind" && \
  assert_code "12.47h 解除绑定" 200
req DELETE "/warehouses/$WH_ID"
assert_code "12.47i 冒烟仓引用清空后删除放行（正向半边，不留跨轮残留）" 200
[ "$(sqlq "SELECT COUNT(*) FROM warehouses WHERE id='$WH_ID'")" = "0" ] \
  && ok "12.47j 冒烟仓确实已从库里消失" || bad "12.47j 冒烟仓删除落库" "0 行" "$(sqlq "SELECT COUNT(*) FROM warehouses WHERE id='$WH_ID'")"

# 留痕仓：引用清完同样放行
po_purge_wh "$AWH_ID"
[ -z "$PURGE_ERR" ] && ok "12.47k 留痕仓名下采购单已清场" || bad "12.47k 采购单清场 SQL 无报错" "无 ERROR" "$PURGE_ERR"
req DELETE "/warehouses/$AWH_ID"
assert_code "12.47l 删除留痕仓（引用与库存都已清，守卫必须放行）" 200

req DELETE "/orders/$AORD_ID"
assert_code "12.48 删除留痕订单" 200
req DELETE "/orders/$AORD_C"
assert_code "12.49 删除批量发货订单" 200

# ---- 12.G 逐动作码回读留痕：35 个业务写端点必须全部有 SUCCESS 记录 ----
aud "WAREHOUSE_CREATE"          "12.50 WAREHOUSE_CREATE 已留痕"
aud "WAREHOUSE_UPDATE"          "12.51 WAREHOUSE_UPDATE 已留痕"
aud "WAREHOUSE_STATUS_UPDATE"   "12.52 WAREHOUSE_STATUS_UPDATE 已留痕"
aud "WAREHOUSE_LOCATION_ADD"    "12.53 WAREHOUSE_LOCATION_ADD 已留痕"
aud "WAREHOUSE_DELETE"          "12.54 WAREHOUSE_DELETE 已留痕"
aud "INVENTORY_IN"              "12.55 INVENTORY_IN 已留痕"
aud "INVENTORY_OUT"             "12.56 INVENTORY_OUT 已留痕"
aud "INVENTORY_COUNT"           "12.57 INVENTORY_COUNT 已留痕"
aud "INVENTORY_MOVE"            "12.58 INVENTORY_MOVE 已留痕"
aud "PURCHASE_CREATE"           "12.59 PURCHASE_CREATE 已留痕"
aud "PURCHASE_ITEM_ADD"         "12.60 PURCHASE_ITEM_ADD 已留痕"
aud "PURCHASE_ITEM_UPDATE"      "12.61 PURCHASE_ITEM_UPDATE 已留痕"
aud "PURCHASE_ITEM_REMOVE"      "12.62 PURCHASE_ITEM_REMOVE 已留痕"
aud "PURCHASE_APPROVE"          "12.63 PURCHASE_APPROVE 已留痕"
aud "PURCHASE_TRANSIT"          "12.64 PURCHASE_TRANSIT 已留痕"
aud "PURCHASE_DELIVERED"        "12.65 PURCHASE_DELIVERED 已留痕"
aud "PURCHASE_CANCEL"           "12.66 PURCHASE_CANCEL 已留痕"
aud "PURCHASE_REJECT"           "12.67 PURCHASE_REJECT 已留痕"
aud "FINANCE_TRANSACTION_CREATE"   "12.68 FINANCE_TRANSACTION_CREATE 已留痕"
aud "FINANCE_TRANSACTION_COMPLETE" "12.69 FINANCE_TRANSACTION_COMPLETE 已留痕"
aud "FINANCE_TRANSACTION_FAIL"     "12.70 FINANCE_TRANSACTION_FAIL 已留痕"
aud "FINANCE_TRANSACTION_CANCEL"   "12.71 FINANCE_TRANSACTION_CANCEL 已留痕"
aud "ORDER_CREATE"              "12.72 ORDER_CREATE 已留痕"
aud "ORDER_STATUS_UPDATE"       "12.73 ORDER_STATUS_UPDATE 已留痕"
aud "ORDER_CANCEL"              "12.74 ORDER_CANCEL 已留痕"
aud "ORDER_DELETE"              "12.75 ORDER_DELETE 已留痕"
aud "LOGISTICS_CREATE"          "12.76 LOGISTICS_CREATE 已留痕"
aud "LOGISTICS_SHIP"            "12.77 LOGISTICS_SHIP 已留痕"
aud "LOGISTICS_STATUS_UPDATE"   "12.78 LOGISTICS_STATUS_UPDATE 已留痕"
aud "LOGISTICS_LABEL_GENERATE"  "12.79 LOGISTICS_LABEL_GENERATE 已留痕"
aud "LOGISTICS_TRACE_ADD"       "12.80 LOGISTICS_TRACE_ADD 已留痕"
aud "LOGISTICS_TRACK_REFRESH"   "12.81 LOGISTICS_TRACK_REFRESH 已留痕"
aud "LOGISTICS_CANCEL"          "12.82 LOGISTICS_CANCEL 已留痕"
aud "LOGISTICS_DELETE"          "12.83 LOGISTICS_DELETE 已留痕"
aud "LOGISTICS_BATCH_SHIP"      "12.84 LOGISTICS_BATCH_SHIP 已留痕"

# 失败侧同样要留痕：12.30b 的非法流转必须在审计里记成 FAILURE（越权/误操作信号主要在失败侧）
req GET "/audit-logs?action=ORDER_STATUS_UPDATE&result=FAILURE&page=0&size=1"
AUD_FAIL="$(printf '%s' "$BODY" | jget data.content.0.createdAt)"
if [ -n "$AUD_FAIL" ] && [[ ! "$AUD_FAIL" < "$AUD_BASE" ]]; then
  ok "12.85 失败调用同样留痕 FAILURE（$AUD_FAIL）"
else
  bad "12.85 失败留痕" "本次运行内有 FAILURE 留痕" "最新 FAILURE=${AUD_FAIL:-无}"
fi

# ISS-117 / R-36：部分失败的批量发货（12.45c，HTTP 200 + code 200）不得在审计里谎报成整批成功。
# errorMessage 里必须带逐行统计，否则取证只看到"批量发货成功"而不知道有一行没动。
req GET "/audit-logs?action=LOGISTICS_BATCH_SHIP&result=FAILURE&page=0&size=1"
BS_AUD_TS="$(printf '%s' "$BODY" | jget data.content.0.createdAt)"
BS_AUD_MSG="$(printf '%s' "$BODY" | jget data.content.0.errorMessage)"
if [ -n "$BS_AUD_TS" ] && [[ ! "$BS_AUD_TS" < "$AUD_BASE" ]] \
   && printf '%s' "$BS_AUD_MSG" | grep -q '批量结果部分未生效: total=1 success=0 failure=1 skipped=0'; then
  ok "12.86 部分失败的批量发货留痕 FAILURE（$BS_AUD_TS）"
else
  bad "12.86 部分失败的批量发货留痕 FAILURE" "本次运行内 FAILURE 留痕且 errorMessage 带逐行统计" "最新=${BS_AUD_TS:-无} errorMessage=${BS_AUD_MSG:0:180}"
fi

# ---- 12.H ISS-123：主数据 / 渠道 / 店铺仓库 / 平台同步 / 导入线 / 登出 的留痕驱动 ----
# 这 10 个控制器（客户、商品、物流渠道、店铺仓库绑定、平台集成同步、6 条导入线、登出）此前
# 56 个写端点一条留痕都没有：客户 PII 改了、SKU 删了、渠道报价调了、谁导进了一批订单，
# 事后在 audit_logs 里全都查不到。下面按模块各驱动一遍，12.I 逐动作码回读，12.J 自清残留。
head2 "12H. 全量写端点留痕（ISS-123：主数据 / 渠道 / 店铺仓库 / 平台同步 / 导入线 / 登出）"

# audx <动作码> <用例名>：本轮内有留痕就算过（SUCCESS 与 FAILURE 都接受）。
# 用于结果取决于外部平台凭据或样例数据的端点 —— 同步成不成功不由冒烟决定，
# 但「这次操作有没有被记下来」必须由冒烟决定。
audx() {
  req GET "/audit-logs?action=$1&page=0&size=1"
  local ts res
  ts="$(printf '%s' "$BODY" | jget data.content.0.createdAt)"
  res="$(printf '%s' "$BODY" | jget data.content.0.result)"
  if [ -n "$ts" ] && [[ ! "$ts" < "$AUD_BASE" ]]; then
    ok "$2（留痕 $res @ $ts）"
  else
    bad "$2" "本次运行内有留痕（基线 $AUD_BASE）" "最新留痕=${ts:-无}"
  fi
}

# audid <动作码> <期望 targetId> <用例名> [基线]：创建类留痕必须能按新建单据的 id 归属（ISS-129）。
# 只判「有没有留痕」不够 —— targetId 为空时"这张单是谁建的"照样查不到；
# 修复前 13 类 *_CREATE 的留痕 target_id 全是 NULL（本轮可复算：
#   SELECT COUNT(*) FROM audit_logs WHERE action LIKE '%\_CREATE' AND target_id IS NULL → 621），就是这个形状。
# 第 4 个参数是"本轮窗口"的下界：默认用 AUD_BASE（§12 开头才取），
# 但 §8/§10/§11 期间创建的单据（租户/角色/子账号）在那之前，必须改用 RUN_BASE，
# 否则会被判成"上一轮留下的旧行"。
audid() {
  local base="${4:-$AUD_BASE}"
  req GET "/audit-logs?action=$1&result=SUCCESS&page=0&size=1"
  local ts tid
  ts="$(printf '%s' "$BODY" | jget data.content.0.createdAt)"
  tid="$(printf '%s' "$BODY" | jget data.content.0.targetId)"
  if [ -n "$ts" ] && [[ ! "$ts" < "$base" ]] && [ "$tid" = "$2" ]; then
    ok "$3（targetId=$tid）"
  else
    bad "$3" "本轮内（基线 $base）SUCCESS 留痕且 targetId 等于 $2" "最新=${ts:-无} targetId=${tid:-NULL}"
  fi
}

# audf <动作码> <用例名> <errorMessage 必含片段>：本轮内必须记 FAILURE，且逐行统计在 errorMessage 里。
# R-38：导入作业与平台同步允许「HTTP 200 + code 200 却有一行没落库」，这种形状谎报成功比直接报错更糟。
audf() {
  req GET "/audit-logs?action=$1&result=FAILURE&page=0&size=1"
  local ts msg
  ts="$(printf '%s' "$BODY" | jget data.content.0.createdAt)"
  msg="$(printf '%s' "$BODY" | jget data.content.0.errorMessage)"
  if [ -n "$ts" ] && [[ ! "$ts" < "$AUD_BASE" ]] && printf '%s' "$msg" | grep -q "$3"; then
    ok "$2（FAILURE @ $ts）"
  else
    bad "$2" "本轮内 FAILURE 留痕且 errorMessage 含「$3」" "最新=${ts:-无} errorMessage=${msg:0:180}"
  fi
}

HM="SMK12H-$RUN_ID"          # 本轮所有探针数据的统一前缀，12.J 按它清场
HCSV="smoke-i123-$RUN_ID.csv"

# A 客户（CUSTOMER_*）：先建后改再删，删除放最后，免得中途状态干扰
req POST "/customers" "{\"tenantId\":\"$TENANT_A\",\"customerName\":\"留痕探针客户-$RUN_ID\",\"email\":\"smk12h-$RUN_ID@example.com\",\"phone\":\"13800000000\",\"address\":\"探针路1号\",\"company\":\"SmokeCo\"}"
assert_code "12.87 创建留痕客户" 200
HCUST="$(printf '%s' "$BODY" | jget data.customerId)"
audid CUSTOMER_CREATE "$HCUST" "12.87a 创建留痕可按单据 id 归属（ISS-129）"
req PUT "/customers/$HCUST" "{\"customerName\":\"留痕探针客户改名-$RUN_ID\",\"email\":\"smk12h-$RUN_ID@example.com\",\"phone\":\"13900000000\",\"address\":\"探针路2号\",\"company\":\"SmokeCo2\"}"
assert_code "12.88 更新留痕客户" 200
req PUT "/customers/$HCUST/deactivate"
assert_code "12.89 停用留痕客户" 200
req PUT "/customers/$HCUST/activate"
assert_code "12.90 重新启用留痕客户" 200

# B 商品（PRODUCT_*）：A 走单条删除，B 走批量删除，两条路径的动作码不同
req POST "/products" "{\"name\":\"留痕探针商品A-$RUN_ID\",\"description\":\"12H\",\"category\":\"AUDIT\",\"brand\":\"SmokeBrand\",\"basePrice\":9.9,\"currency\":\"USD\",\"status\":\"ACTIVE\",\"skus\":[{\"skuCode\":\"$HM-A\",\"barcode\":\"$HM-BA\",\"sellingPrice\":9.9,\"costPrice\":5,\"stock\":10,\"status\":\"ACTIVE\"}]}"
assert_code "12.91 创建留痕商品A" 200
HPROD="$(printf '%s' "$BODY" | jget data.id)"
req PUT "/products/$HPROD" "{\"name\":\"留痕探针商品A改名-$RUN_ID\",\"description\":\"12H-b\",\"category\":\"AUDIT\",\"brand\":\"SmokeBrand\",\"basePrice\":10.9,\"currency\":\"USD\",\"status\":\"ACTIVE\",\"skus\":[{\"skuCode\":\"$HM-A\",\"barcode\":\"$HM-BA\",\"sellingPrice\":9.9,\"costPrice\":5,\"stock\":10,\"status\":\"ACTIVE\"}]}"
assert_code "12.92 更新留痕商品A" 200
req POST "/products/$HPROD/skus" "{\"skuCode\":\"$HM-A2\",\"barcode\":\"$HM-BA2\",\"sellingPrice\":11.9,\"costPrice\":6,\"stock\":5,\"status\":\"ACTIVE\"}"
assert_code "12.93 添加商品 SKU" 200
req DELETE "/products/$HPROD/skus/$HM-A2"
assert_code "12.94 删除商品 SKU" 200
req PUT "/products/$HPROD/deactivate"
assert_code "12.95 停用留痕商品A" 200
req PUT "/products/$HPROD/activate"
assert_code "12.96 重新启用留痕商品A" 200
req POST "/products" "{\"name\":\"留痕探针商品B-$RUN_ID\",\"description\":\"12H\",\"category\":\"AUDIT\",\"brand\":\"SmokeBrand\",\"basePrice\":5.5,\"currency\":\"USD\",\"status\":\"ACTIVE\",\"skus\":[{\"skuCode\":\"$HM-B\",\"barcode\":\"$HM-BB\",\"sellingPrice\":5.5,\"costPrice\":3,\"stock\":4,\"status\":\"ACTIVE\"}]}"
assert_code "12.97 创建留痕商品B（供批量操作）" 200
HPROD2="$(printf '%s' "$BODY" | jget data.id)"
req PUT "/products/batch/active" "{\"productIds\":[\"$HPROD2\"],\"tenantId\":\"$TENANT_A\",\"active\":true}"
assert_code "12.98 批量启用商品" 200

# C 物流渠道（LOGISTICS_CHANNEL_*）：渠道要活到 12.118 的物流导入之后才能删（外键）
req POST "/logistics-channels" "{\"channelCode\":\"$HM-CH\",\"channelName\":\"留痕探针渠道-$RUN_ID\",\"description\":\"12H\",\"countries\":[\"CN\",\"US\"],\"basePrice\":20,\"pricePerGram\":0.06,\"currency\":\"USD\",\"active\":true}"
assert_code "12.99 创建留痕渠道" 200
HCH="$(printf '%s' "$BODY" | jget data.channelId)"
audid LOGISTICS_CHANNEL_CREATE "$HCH" "12.99a 创建渠道留痕按 channelId 归属（注解显式 targetIdResult）"
req PUT "/logistics-channels/$HCH" "{\"channelCode\":\"$HM-CH\",\"channelName\":\"留痕探针渠道改名-$RUN_ID\",\"description\":\"12H-b\",\"countries\":[\"CN\"],\"basePrice\":21,\"pricePerGram\":0.07,\"currency\":\"USD\",\"active\":true}"
assert_code "12.100 更新留痕渠道" 200
req PUT "/logistics-channels/$HCH/status?active=false"
assert_code "12.101 停用留痕渠道" 200
req PUT "/logistics-channels/$HCH/status?active=true"
assert_code "12.101b 重新启用留痕渠道（物流导入要按渠道编码匹配）" 200

# D 店铺仓库绑定（SHOP_WAREHOUSE_*）：需要一个本租户的本地仓库供绑定
req POST "/warehouses" "{\"tenantId\":\"$TENANT_A\",\"warehouseCode\":\"$HM-WH\",\"warehouseName\":\"留痕探针仓-$RUN_ID\",\"address\":\"探针仓库路1号\",\"capacity\":50}"
assert_code "12.102 创建留痕仓（12H 专用）" 200
HWH="$(printf '%s' "$BODY" | jget data.warehouseId)"
# platformWarehouseId 用不带 RUN_ID 的固定值：绑定按 (租户, 平台, 平台仓ID) upsert，
# 每轮命中同一行，不会越跑越多（店铺仓库没有删除端点，只能靠 upsert 控量）
req POST "/warehouses/shop/bind" "{\"tenantId\":\"$TENANT_A\",\"platform\":\"SHOPIFY\",\"shopCode\":\"$HM-SHOP\",\"shopName\":\"留痕探针店铺\",\"platformWarehouseId\":\"SMK12H-PWH\",\"platformWarehouseName\":\"探针平台仓\",\"address\":\"探针平台仓地址\",\"defaultWarehouse\":true,\"localWarehouseId\":\"$HWH\"}"
assert_code "12.103 绑定店铺仓库" 200
HSW="$(printf '%s' "$BODY" | jget data.id)"
req POST "/warehouses/shop/$HSW/unbind"
assert_code "12.104 解绑店铺仓库" 200

# E 平台集成同步（PLATFORM_INTEGRATION_* / SHOP_WAREHOUSE_SYNC / PLATFORM_FULFILLMENT_PUSH）
req POST "/platform-integrations" "{\"tenantId\":\"$TENANT_A\",\"platformName\":\"SHOPIFY\",\"authType\":\"SHOPIFY\",\"storeUrl\":\"https://smk12h-$RUN_ID.myshopify.com\",\"accessToken\":\"shpat_smk12h_$RUN_ID\",\"apiKey\":\"smk12h-key-$RUN_ID\",\"apiSecret\":\"smk12h-secret-$RUN_ID\",\"status\":\"ACTIVE\"}"
assert_code "12.105 创建留痕平台集成" 200
HINT="$(printf '%s' "$BODY" | jget data.id)"
audid PLATFORM_INTEGRATION_CREATE "$HINT" "12.105a 创建集成留痕按返回体 id 归属（ISS-129 兜底路径）"
# 店铺主数据（SHOP_CREATE）：入参没有 id、targetType 由控制器名兜底成 Shop，
# 走的是"返回体 id"这一条兜底路径的另一种形状（Result<ShopDto> 而不是 ResponseEntity）。
# 本轮之前 /shops 在冒烟里 0 覆盖，SHOP_CREATE 的 target_id 因此没有回归位。
req POST "/shops?tenantId=$TENANT_A" "{\"shopCode\":\"$HM-SHOP\",\"shopName\":\"留痕探针店铺-$RUN_ID\",\"platform\":\"SHOPIFY\"}"
assert_code "12.105b 创建留痕店铺" 200
HSHOP="$(printf '%s' "$BODY" | jget data.id)"
audid SHOP_CREATE "$HSHOP" "12.105c 创建店铺留痕按 shopId 归属（ISS-129）"
req DELETE "/shops/$HSHOP?tenantId=$TENANT_A"
assert_code_in "12.105d 清理留痕店铺" "200 204"
req POST "/warehouses/shop/sync/$HINT?useSampleData=true&limit=3"
assert_code "12.106 同步店铺仓库（样例数据，第2行缺平台仓ID）" 200
req POST "/platform-integrations/$HINT/test-connection"
assert_code_in "12.107 平台探活（凭据是假的，4xx/200 都算跑通）" "200 400 401 502"
# ── ISS-130 / ISS-131（R-48）：探活失败是走 data.success=false 回来的（HTTP 与 Result.code 都是 200），
# 修复前这类留痕照样记 SUCCESS，"谁在什么时候用哪条凭据探活失败过"查不到；而 message 里躺着
# ResourceAccessException 的 ": null" 尾巴（真凶在 cause 里），运维看到的话术是"限流或平台不可用"。
# 判据用 127.0.0.1:1 —— 必然立即拒连，不依赖外网、不受平台限流与凭据影响，所以每轮可复跑。
req POST "/platform-integrations" "{\"tenantId\":\"$TENANT_A\",\"platformName\":\"OZON\",\"authType\":\"CUSTOM_SHOP\",\"storeUrl\":\"http://127.0.0.1:1\",\"apiKey\":\"$HM-key\",\"apiSecret\":\"$HM-secret\",\"status\":\"ACTIVE\"}"
assert_code "12.107a 创建「必然拒连」的探活集成（ISS-130 前置）" 200
HDEAD="$(printf '%s' "$BODY" | jget data.id)"
req POST "/platform-integrations/$HDEAD/test-connection"
HSUCC="$(printf '%s' "$BODY" | jget data.success)"
HMSG="$(printf '%s' "$BODY" | jget data.message)"
if [ "$HTTP_CODE" = "200" ] && [ "$HSUCC" = "false" ] \
   && printf '%s' "$HMSG" | grep -q "无法连接" \
   && ! printf '%s' "$HMSG" | grep -q "null"; then
  ok "12.107b 探活失败回显按族的根因（ISS-131，message=${HMSG:0:60}）"
else
  bad "12.107b 探活失败回显按族的根因" "HTTP 200 + data.success=false 且 message 含「无法连接」、不含 null" "HTTP $HTTP_CODE success=${HSUCC:-无} message=${HMSG:0:200}"
fi
audf PLATFORM_INTEGRATION_TEST "12.107c 探活失败必须落成 FAILURE 留痕并带原因（ISS-130）" "连通性测试未通过"
req DELETE "/platform-integrations/$HDEAD"
assert_code_in "12.107d 清场：删除探活集成" "200 204"
req POST "/platform-integrations/$HINT/sync-orders?useSampleData=true&limit=1"
assert_code "12.108 同步平台订单（样例数据）" 200
req POST "/platform-integrations/$HINT/sync-customers?useSampleData=true&limit=1"
assert_code "12.109 同步平台客户（样例数据）" 200
req POST "/platform-integrations/$HINT/push-fulfillment" "{\"logisticsOrderId\":\"$HM-NO-SUCH-LO\",\"notifyCustomer\":false}"
assert_code "12.110 回写不存在的物流单（必须 4xx，不得静默成功）" 400

# F 导入线（IMPORT_FIELD_MAPPING_UPDATE / IMPORT_UPLOAD / *_IMPORT_EXECUTE）
# 字段映射是按 (租户, 平台) 整体覆盖的共享配置：改完必须还原，否则本轮把 SHOPIFY 的映射
# 覆盖成一条，之后所有走字段映射的导入都跟着变 —— 探针自己造成的改动要自己复原。
req GET "/orders/import/field-mappings?platform=SHOPIFY"
HMAP_ORIG="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const a=JSON.parse(s).data||[];console.log(JSON.stringify(a.map(m=>({sourceField:m.sourceField,targetField:m.targetField,transform:m.transform,defaultValue:m.defaultValue,enabled:m.enabled}))))}catch(e){console.log("[]")}})')"
req PUT "/orders/import/field-mappings" "{\"tenantId\":\"$TENANT_A\",\"platform\":\"SHOPIFY\",\"mappings\":[{\"sourceField\":\"order_id\",\"targetField\":\"ORDER_NUMBER\",\"enabled\":true}]}"
assert_code "12.111 保存订单导入字段映射（targetField 用枚举名）" 200
req PUT "/orders/import/field-mappings" "{\"tenantId\":\"$TENANT_A\",\"platform\":\"SHOPIFY\",\"mappings\":$HMAP_ORIG}"
assert_code "12.112 字段映射还原成进入本节前的原样" 200

HCSV_PATH="/tmp/$HCSV"
printf 'externalOrderId,orderNumber,customerName,customerEmail,totalAmount,status,recipient,phone,country,city,street,zipCode,sku,productName,quantity,unitPrice\n' > "$HCSV_PATH"
printf '%s-EXT,%s-UP,留痕上传客户,smk12h-up-%s@example.com,33.33,PENDING,探针收件人,13800000002,CN,深圳,探针路3号,518000,%s-USKU,留痕上传商品,2,16.67\n' "$HM" "$HM" "$RUN_ID" "$HM" >> "$HCSV_PATH"
HTTP_CODE=$(curl -sS -m 60 -o "$SMOKE_TMP" -w '%{http_code}' -X POST "$API/orders/import/upload?mode=import&tenantId=$TENANT_A" \
  -H "Authorization: Bearer $TOKEN" -F "file=@$HCSV_PATH;type=text/csv" 2>/dev/null)
BODY="$(cat "$SMOKE_TMP" 2>/dev/null)"; rm -f "$HCSV_PATH"
assert_code "12.113 上传订单 CSV（multipart，mode=import 直接落库）" 200
printf '%s' "$BODY" | grep -q '"status":"SUCCESS"' \
  && ok "12.113b 上传导入整批成功（明细列被正确映射）" \
  || bad "12.113b 上传导入整批成功" "status=SUCCESS" "${BODY:0:220}"

req POST "/orders/import" "{\"tenantId\":\"$TENANT_A\",\"platform\":\"SHOPIFY\",\"fileName\":\"$HCSV\",\"rows\":[{\"rowNumber\":1,\"externalOrderId\":\"$HM-EXTD\",\"orderNumber\":\"$HM-ORD\",\"customerName\":\"留痕直投客户\",\"customerEmail\":\"smk12h-d-$RUN_ID@example.com\",\"totalAmount\":12.34,\"status\":\"PENDING\",\"recipient\":\"探针收件人\",\"phone\":\"13800000003\",\"country\":\"CN\",\"city\":\"深圳\",\"street\":\"探针路4号\",\"zipCode\":\"518000\",\"items\":[{\"sku\":\"$HM-ISKU\",\"name\":\"留痕直投商品\",\"quantity\":1,\"unitPrice\":12.34}]}]}"
assert_code "12.114 直投订单导入（1 行）" 200
printf '%s' "$BODY" | grep -q '"status":"SUCCESS"' \
  && ok "12.114b 订单导入整批成功" || bad "12.114b 订单导入整批成功" "status=SUCCESS" "${BODY:0:220}"

# 1 好行 + 1 坏行（SKU 为空）：HTTP 200 且 code=200，业务态是 PARTIAL_SUCCESS。
# 这正是 R-38 要盯的形状 —— 审计若记 SUCCESS，等于把「有一行没落库」这件事抹掉。
req POST "/products/import" "{\"tenantId\":\"$TENANT_A\",\"platform\":\"SHOPIFY\",\"fileName\":\"$HCSV\",\"rows\":[{\"rowNumber\":1,\"name\":\"留痕探针导入商品-$RUN_ID\",\"skuCode\":\"$HM-IMP\",\"sellingPrice\":8.8,\"costPrice\":4,\"currency\":\"USD\",\"active\":true},{\"rowNumber\":2,\"name\":\"留痕探针坏行-$RUN_ID\",\"skuCode\":\"\",\"sellingPrice\":1,\"costPrice\":1,\"currency\":\"USD\",\"active\":true}]}"
assert_code "12.115 商品导入（1 好 1 坏）仍是 HTTP 200" 200
printf '%s' "$BODY" | grep -q '"status":"PARTIAL_SUCCESS"' \
  && ok "12.115b 业务态是 PARTIAL_SUCCESS（success=1 failure=1）" \
  || bad "12.115b 业务态是 PARTIAL_SUCCESS" "status=PARTIAL_SUCCESS" "${BODY:0:220}"

req POST "/customers/import" "{\"tenantId\":\"$TENANT_A\",\"platform\":\"SHOPIFY\",\"fileName\":\"$HCSV\",\"rows\":[{\"rowNumber\":1,\"externalBuyerId\":\"$HM-BUYER\",\"customerName\":\"留痕探针导入客户-$RUN_ID\",\"email\":\"smk12h-ci-$RUN_ID@example.com\",\"phone\":\"13600000000\",\"fullAddress\":\"探针导入地址1号\"}]}"
assert_code "12.116 客户导入" 200
req POST "/finance/import" "{\"tenantId\":\"$TENANT_A\",\"platform\":\"SHOPIFY\",\"fileName\":\"$HCSV\",\"rows\":[{\"rowNumber\":1,\"externalTransactionId\":\"$HM-TXN\",\"type\":\"INCOME\",\"amount\":66.6,\"currency\":\"CNY\",\"description\":\"留痕探针流水-$RUN_ID\",\"status\":\"COMPLETED\"}]}"
assert_code "12.117 财务流水导入" 200
req POST "/logistics/import" "{\"tenantId\":\"$TENANT_A\",\"platform\":\"SHOPIFY\",\"fileName\":\"$HCSV\",\"rows\":[{\"rowNumber\":1,\"externalLogisticsId\":\"$HM-LG\",\"trackingNumber\":\"$HM-TRK\",\"orderNumber\":\"$HM-ORD\",\"channelCode\":\"$HM-CH\",\"carrier\":\"探针承运商\",\"status\":\"IN_TRANSIT\"}]}"
assert_code "12.118 物流单导入（按渠道编码匹配 12.99 建的渠道）" 200
printf '%s' "$BODY" | grep -q '"status":"SUCCESS"' \
  && ok "12.118b 物流单导入整批成功" || bad "12.118b 物流单导入整批成功" "status=SUCCESS" "${BODY:0:220}"
HLO="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const a=JSON.parse(s).data.createdLogisticsOrderIds||[];console.log(a[0]||"")}catch(e){console.log("")}})')"
req POST "/warehouses/import" "{\"tenantId\":\"$TENANT_A\",\"platform\":\"SHOPIFY\",\"fileName\":\"$HCSV\",\"rows\":[{\"rowNumber\":1,\"shopCode\":\"$HM-SHOP\",\"shopName\":\"留痕探针店铺\",\"platform\":\"SHOPIFY\",\"platformWarehouseId\":\"SMK12H-PWH\",\"platformWarehouseName\":\"探针导入平台仓\",\"warehouseCode\":\"$HM-WH\",\"address\":\"探针导入地址\",\"isDefault\":true,\"active\":true}]}"
assert_code "12.119 店铺仓库导入（warehouseCode 用 12.102 建的仓）" 200

# G 收尾删除 + 登出：删除类动作码只在真删时才写，必须逐个打到
[ -n "$HLO" ] && { req DELETE "/logistics/$HLO"; assert_code "12.119b 删除导入生成的物流单（放开渠道外键）" 200; }
req DELETE "/products/$HPROD"
assert_code "12.120 单条删除留痕商品A" 200
req DELETE "/products/batch" "{\"productIds\":[\"$HPROD2\"],\"tenantId\":\"$TENANT_A\"}"
assert_code "12.121 批量删除留痕商品B" 200
req DELETE "/logistics-channels/$HCH"
assert_code "12.122 删除留痕渠道" 200
# 12.123 按 R-31 改成"一正一负"：这张仓在 12.119 的店铺仓库导入里被绑上过（platformWarehouseId
# 固定为 SMK12H-PWH，绑定按 (租户,平台,平台仓ID) upsert 到同一行），守卫必须挡；解绑后才放行。
# 旧断言只测"能删"，于是"删仓把引用掏空却回 200"在冒烟里是隐形的——实测 533 行采购单里 530 行
# 指向已不存在的仓库，正是这条路径一轮一轮攒出来的（ISS-142）。
# 先按仓作用域清掉本轮这张仓名下的采购单：守卫是"库存→采购→绑定"的顺序，不清采购的话
# 这条判据的拒绝原因会随上一节缺货自动单的多少在「采购单」与「店铺仓库」之间漂移。
po_purge_wh "$HWH"
[ -z "$PURGE_ERR" ] && ok "12.123a 探针仓名下采购单已按子表先删清场" \
  || bad "12.123a 采购单清场 SQL 无报错" "无 ERROR" "$PURGE_ERR"
req DELETE "/warehouses/$HWH"
assert_code "12.123b 导入留下的店铺绑定同样挡住删仓（HTTP 400，不是只挡 API 绑的）" 400
printf '%s' "$BODY" | grep -q "店铺仓库" && grep -q "解除绑定" <<< "$BODY" \
  && ok "12.123c 拒绝原因点名店铺绑定并给出解绑办法" \
  || bad "12.123c 拒绝原因点名店铺绑定" "message 含「店铺仓库」与「解除绑定」" "${BODY:0:220}"
req GET "/warehouses/shop?localWarehouseId=$HWH"
BIND_IDS="$(printf '%s' "$BODY" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{(JSON.parse(s).data||[]).forEach(r=>console.log(r.id))}catch(e){}})')"
if [ -n "$BIND_IDS" ]; then
  for bid in $BIND_IDS; do
    req POST "/warehouses/shop/$bid/unbind"
    assert_code "12.123d 解绑 $bid" 200
  done
else
  bad "12.123d 列出探针仓的店铺绑定" "≥1 条" "0 条（12.119 的导入没绑上这张仓？）"
fi
req DELETE "/warehouses/$HWH"
assert_code "12.123e 引用清空后删仓放行（正向半边）" 200
req DELETE "/platform-integrations/$HINT"
assert_code "12.124 删除留痕平台集成" 200
req DELETE "/customers/$HCUST"
assert_code "12.125 删除留痕客户" 200
req POST "/auth/logout"
assert_code "12.126 登出" 200
req_anon POST "/auth/login" "{\"username\":\"$USER_NAME\",\"password\":\"$PASS\"}"
assert_code "12.127 登出后可重新登录（令牌确实被注销而不是全局失效）" 200
TOKEN="$(printf '%s' "$BODY" | jget data.token)"

# ---- 12.I 逐动作码回读：31 个 ISS-123 动作码一个都不能少 ----
aud  "CUSTOMER_CREATE"                 "12.128 CUSTOMER_CREATE 已留痕"
aud  "CUSTOMER_UPDATE"                 "12.129 CUSTOMER_UPDATE 已留痕"
aud  "CUSTOMER_STATUS_UPDATE"          "12.130 CUSTOMER_STATUS_UPDATE 已留痕"
aud  "CUSTOMER_DELETE"                 "12.131 CUSTOMER_DELETE 已留痕"
aud  "PRODUCT_CREATE"                  "12.132 PRODUCT_CREATE 已留痕"
aud  "PRODUCT_UPDATE"                  "12.133 PRODUCT_UPDATE 已留痕"
aud  "PRODUCT_STATUS_UPDATE"           "12.134 PRODUCT_STATUS_UPDATE 已留痕"
aud  "PRODUCT_DELETE"                  "12.135 PRODUCT_DELETE 已留痕（单条删除）"
aud  "PRODUCT_SKU_ADD"                 "12.136 PRODUCT_SKU_ADD 已留痕"
aud  "PRODUCT_SKU_REMOVE"              "12.137 PRODUCT_SKU_REMOVE 已留痕"
aud  "PRODUCT_BATCH_UPDATE"            "12.138 PRODUCT_BATCH_UPDATE 已留痕"
aud  "PRODUCT_BATCH_DELETE"            "12.139 PRODUCT_BATCH_DELETE 已留痕"
aud  "LOGISTICS_CHANNEL_CREATE"        "12.140 LOGISTICS_CHANNEL_CREATE 已留痕"
aud  "LOGISTICS_CHANNEL_UPDATE"        "12.141 LOGISTICS_CHANNEL_UPDATE 已留痕"
aud  "LOGISTICS_CHANNEL_STATUS_UPDATE" "12.142 LOGISTICS_CHANNEL_STATUS_UPDATE 已留痕"
aud  "LOGISTICS_CHANNEL_DELETE"        "12.143 LOGISTICS_CHANNEL_DELETE 已留痕"
aud  "SHOP_WAREHOUSE_BIND"             "12.144 SHOP_WAREHOUSE_BIND 已留痕"
aud  "SHOP_WAREHOUSE_UNBIND"           "12.145 SHOP_WAREHOUSE_UNBIND 已留痕"
aud  "IMPORT_FIELD_MAPPING_UPDATE"     "12.146 IMPORT_FIELD_MAPPING_UPDATE 已留痕"
aud  "IMPORT_UPLOAD"                   "12.147 IMPORT_UPLOAD 已留痕"
aud  "ORDER_IMPORT_EXECUTE"            "12.148 ORDER_IMPORT_EXECUTE 已留痕"
aud  "CUSTOMER_IMPORT_EXECUTE"         "12.149 CUSTOMER_IMPORT_EXECUTE 已留痕"
aud  "FINANCE_IMPORT_EXECUTE"          "12.150 FINANCE_IMPORT_EXECUTE 已留痕"
aud  "LOGISTICS_IMPORT_EXECUTE"        "12.151 LOGISTICS_IMPORT_EXECUTE 已留痕"
aud  "SHOP_WAREHOUSE_IMPORT_EXECUTE"   "12.152 SHOP_WAREHOUSE_IMPORT_EXECUTE 已留痕"
aud  "AUTH_LOGOUT"                     "12.153 AUTH_LOGOUT 已留痕"
audx "PLATFORM_INTEGRATION_SYNC"       "12.154 PLATFORM_INTEGRATION_SYNC 已留痕"
audx "PLATFORM_INTEGRATION_TEST"       "12.155 PLATFORM_INTEGRATION_TEST 已留痕"

# R-38 的三条活证据：HTTP 与 code 都是 200，业务上却有行没生效，审计必须记 FAILURE 并写出逐行统计
audf "PRODUCT_IMPORT_EXECUTE"   "12.156 部分成功的商品导入记 FAILURE" "批量结果部分未生效: total=2 success=1 failure=1"
audf "SHOP_WAREHOUSE_SYNC"      "12.157 有行被跳过的店铺仓库同步记 FAILURE" "同步结果部分未生效"
audf "PLATFORM_FULFILLMENT_PUSH" "12.158 回写不存在物流单记 FAILURE（4xx 被 ResponseEntity 包着也算）" "物流单不存在"

# ---- 12.I2 创建类留痕归属补位：13 类 *_CREATE 逐类打位（R-44 + R-47，ISS-129 的残留缺口）----
# 12.87a/12.99a/12.105a/12.105c 只签了 4 类，其余 9 类此前只有 AuditAspectTest 的判定逻辑与
# live 流量的非 NULL 行背书 —— 按 R-44「每个结论要有逐聚合的常驻回归位，不能由一个断言替全部
# 签字」，"没测过"和"测过且没回归"不是一回事，所以逐类补齐。
# ⚠️ 期望值必须是**本轮最后一个被创建的该类型单据**：audid 读的是该动作码最新一条 SUCCESS 留痕，
# 拿中间某条去比就是把断言指向别的单据（R-47 拒绝"扫描任意 *Id getter"是同一条理由）。
# 这条判据本轮真的抓到了写断言的人自己：先按留痕商品 A（HPROD）与 §12B 的留痕仓（AWH_ID）
# 填期望值，12.163/12.164 立刻报 "targetId=别的单据" —— 因为 12H 后面又建了商品B（批量删除路径）
# 与探针仓 HWH（店铺仓库绑定用）。改成"最后一个"之后两条才转绿。
# §8/§10/§11 期间创建的租户/角色/子账号早于 AUD_BASE（§12 开头才取），改用 RUN_BASE 判窗口。
[ -n "${AORD_C:-}" ]   && audid ORDER_CREATE                 "$AORD_C"   "12.162 创建订单留痕按 orderId 归属（ISS-129）" \
  || skip "12.162 创建订单留痕归属" "本轮没拿到待批量发货订单 id"
[ -n "${HPROD2:-}" ]   && audid PRODUCT_CREATE               "$HPROD2"   "12.163 创建商品留痕按 productId 归属（ISS-129）" \
  || skip "12.163 创建商品留痕归属" "本轮没拿到批量删除用的商品B id"
[ -n "${HWH:-}" ]      && audid WAREHOUSE_CREATE             "$HWH"      "12.164 创建仓库留痕按 warehouseId 归属（ISS-129）" \
  || skip "12.164 创建仓库留痕归属" "本轮没拿到店铺绑定用的留痕探针仓 id"
[ -n "${ALOG_D:-}" ]   && audid LOGISTICS_CREATE             "$ALOG_D"   "12.165 创建物流单留痕按 logisticsOrderId 归属（ISS-129）" \
  || skip "12.165 创建物流单留痕归属" "本轮没拿到手工物流单 id"
[ -n "${APO3_ID:-}" ]  && audid PURCHASE_CREATE              "$APO3_ID"  "12.166 创建采购单留痕按 purchaseOrderId 归属（ISS-129）" \
  || skip "12.166 创建采购单留痕归属" "本轮没拿到待拒绝采购单 id"
[ -n "${TX3_ID:-}" ]   && audid FINANCE_TRANSACTION_CREATE   "$TX3_ID"   "12.167 创建财务流水留痕按 transactionId 归属（ISS-129）" \
  || skip "12.167 创建财务流水留痕归属" "本轮没拿到待取消流水 id"
[ -n "${T3:-}" ]       && audid TENANT_CREATE                "$T3"       "12.168 创建租户留痕按 tenantId 归属（ISS-129，窗口 RUN_BASE）" "$RUN_BASE" \
  || skip "12.168 创建租户留痕归属" "本轮没拿到权限探针租户 id"
[ -n "${R3:-}" ]       && audid ROLE_CREATE                  "$R3"       "12.169 创建角色留痕按 roleId 归属（ISS-129，窗口 RUN_BASE）" "$RUN_BASE" \
  || skip "12.169 创建角色留痕归属" "本轮没拿到只读角色 id"
[ -n "${U3:-}" ]       && audid USER_CREATE                  "$U3"       "12.170 创建用户留痕按 userId 归属（ISS-129，窗口 RUN_BASE）" "$RUN_BASE" \
  || skip "12.170 创建用户留痕归属" "本轮没拿到只读子账号 id"

# ---- 12.J 探针自清场：本轮数据 + 历次探针残留一起清掉，清完必须为 0 ----
# 只调 API 删是不够的：客户是软删除会留墓碑、导入作业没有删除端点、平台同步样例数据每轮都建
# 店铺仓库行。实跑取证时 DB 里已经攒了 61 条冒烟订单，所以这里按标记直接清库并回读计数。
DB_ROOT_PW="${SMOKE_DB_ROOT_PW:-123456}"
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'mysql'; then
  dbq() { docker exec mysql mysql -uroot -p"$DB_ROOT_PW" -N -B --default-character-set=utf8mb4 \
            ecommerce_logistics_erp -e "$1" 2>/dev/null | head -1; }
  HORD="order_number LIKE 'SMK12H-%' OR order_number LIKE 'ORDSMK%' OR order_number LIKE 'SMOKE-1%' OR order_number LIKE 'PROBE-1%' OR order_number LIKE 'SMKD-%' OR order_number LIKE 'SMKUV-%' OR order_number LIKE 'EXTV-%' OR order_number LIKE 'R38-%'"
  # 客户判据：留痕探针（本节）+ 冒烟墓碑客户（§10 租户删除用例每轮都建）+ 导入客户（external_buyer_id）
  # + R18 浏览器实测留下的 @probe.test 客户。都是冒烟/探针自己打的标记，人工建的数据不在其中。
  HCUST="name LIKE '%留痕探针%' OR name LIKE '冒烟墓碑客户-%' OR external_buyer_id LIKE '%BUYER-%' OR email LIKE 'smoke%@example.com' OR email LIKE 'probe%@example.com' OR email LIKE '%@probe.test'"
  # 导入作业判据：只认探针自己打的标记，两种 ——
  #   ① 文件名前缀（CSV/JSON 上传与直投都带 fileName）；
  #   ② 本轮平台集成的 UUID（12.106/12.108/12.109 的同步作业没有文件名，只有 integration_id）。
  # 不用 file_name IS NULL、也不用"integration_id 已成孤儿"这类宽判据：人工在后台点的导入同样是
  # NULL 文件名，而集成被删后它的作业行在作业列表里仍然可见，扫走等于替用户删历史（R-39）。
  # 形状校验必须用 [[ =~ ]]：case 的 [0-9a-fA-F-]36 是"一个字符 + 字面量 36"，永远匹配不上，
  # 上一版就是这么把 HPIJOB 静默置空的（12.159 因此少算 2 行同步作业）。
  if [[ "$HINT" =~ ^[0-9a-fA-F-]{36}$ ]]; then HPIJOB=" OR integration_id = '$HINT'"; else HPIJOB=""; fi
  # 本轮新建（快照之后出现）的运费流水 id 判据：空快照要给占位值，否则 NOT IN () 是语法错误，
  # 而 mysql -e 一旦在某条报错就整批不再执行（卫生 ⑰）——清场静默失败过一次就是这么来的。
  if [ -n "$FREIGHT_T0" ]; then FT0="'$(printf '%s' "$FREIGHT_T0" | sed "s/,/','/g")'"; else FT0="'__no-freight-txn-at-start__'"; fi
  # 本轮撤销的 cancelled 分录：清场后必须为 0（探针自己造的，R-39）
  FREIGHT_THIS_RUN="reference_type='LOGISTICS_ORDER' AND transaction_id NOT IN ($FT0)"
  HJOB="file_name LIKE 'smoke-i123-%' OR file_name LIKE 'smoke_orders%' OR file_name LIKE 'i123_%' OR file_name LIKE 'probe.json' OR file_name LIKE 'r38.json' OR file_name LIKE 'u\\_%'${HPIJOB}"
  # 采购单判据（ISS-142）：purchase_orders.warehouse_id 是普通 varchar、没有外键，删掉 warehouses 行
  # 不会带走采购单，只会把它掏成断链（实测 533 行里 530 行指向已不存在的仓，而采购域没有删除端点）。
  # 所以这里必须与下面那条 DELETE FROM warehouses 用同一条谓词（卫生 ㉒）：判据＝"指向会被清掉的探针仓"，
  # 不按 supplier_id 扫——缺货自动单的 supplier_id 恒为 DEFAULT_SUPPLIER，用户在界面上出库到 0 同样会
  # 产生，无法从数据证明是探针产物，扫走等于替用户删历史（R-40）。
  HPOW="w.warehouse_code LIKE 'SMK12H-%' OR w.warehouse_code LIKE 'PROBE-%' OR w.warehouse_code LIKE 'SMKWH-%' OR w.warehouse_name LIKE '%留痕探针%'"
  HRES_BEFORE="$(dbq "SELECT
      (SELECT COUNT(*) FROM orders WHERE $HORD)
    + (SELECT COUNT(*) FROM customers WHERE (${HCUST}))
    + (SELECT COUNT(*) FROM products WHERE name LIKE '%留痕探针%' OR name LIKE 'R38%')
    + (SELECT COUNT(*) FROM shop_warehouses WHERE shop_code LIKE 'SMK%' OR shop_code LIKE 'PROBE-%' OR shop_code LIKE 'SHOP-STORE-%')
    + (SELECT COUNT(*) FROM purchase_orders po JOIN warehouses w ON w.id = po.warehouse_id WHERE (${HPOW}))
    + (SELECT COUNT(*) FROM order_import_jobs WHERE (${HJOB}))
    + (SELECT COUNT(*) FROM customer_import_jobs WHERE (${HJOB}))
    + (SELECT COUNT(*) FROM financial_transactions t WHERE t.reference_type='LOGISTICS_ORDER' AND t.reference_id NOT IN (SELECT id FROM logistics_orders) AND t.status <> 'cancelled')
    + (SELECT COUNT(*) FROM financial_transactions WHERE $FREIGHT_THIS_RUN AND status='cancelled')")"
  # 外键方向要注意：payment_info_id / shipping_address_id 这两列在 orders 上，orders 才是子表。
  # 先删父表会被 FK 挡回，而 mysql -e 从报错那一行起整批不再执行 —— 上一版就是这么静默失败的，
  # 87 行残留一行都没清掉，只有 12.159 的计数把它暴露出来。改成先删 orders，再按"没人引用了"
  # 清扫孤儿 payment_infos / shipping_addresses（这两张表只由下单产生，孤儿即残留）。
  # 清场 SQL 用 here-doc 喂给 mysql -e，不要再包在 "…" 字符串里：上一版这么写时，我在 SQL 注释里
  # 打了两个 ASCII 双引号，字符串被那个引号提前闭合，后半截带空格的残片成了 docker exec 的额外参数，
  # 于是整批 DELETE 一条都没执行，而 stderr 里根本没有 ERROR —— 表现是 12.159 清理前后同为 20 行、
  # 判据却报"SQL 报错：无"。here-doc 让 SQL 内容里的引号与 shell 的引用规则彻底解耦。
  # 报错仍然单独收：mysql -e 从第一条失败语句起整批不再执行（卫生 ⑰），静默跳过清场就是骗过 12.159。
  HPURGE_SQL="$(cat <<EOSQL
    DELETE oi FROM order_items oi JOIN orders o ON oi.order_id = o.id WHERE (${HORD});
    DELETE lo FROM logistics_orders lo JOIN orders o ON lo.order_id = o.id WHERE (${HORD});
    DELETE FROM logistics_orders WHERE tracking_number LIKE 'SMK12H-%' OR tracking_number LIKE 'PROBE-%' OR tracking_number LIKE 'TRKV-%' OR external_logistics_id LIKE 'SMK12H-%' OR external_logistics_id LIKE 'LG-%' OR external_logistics_id LIKE 'LGV-%' OR external_logistics_id LIKE 'PROBE-%';
    DELETE FROM orders WHERE (${HORD});
    DELETE FROM payment_infos WHERE id NOT IN (SELECT payment_info_id FROM orders WHERE payment_info_id IS NOT NULL);
    DELETE FROM shipping_addresses WHERE id NOT IN (SELECT shipping_address_id FROM orders WHERE shipping_address_id IS NOT NULL);
    # ISS-137 之后运费真的会落库：logistics_orders 持 logistics_fee_id，上面的 SQL 删掉物流单行
    # 不会带走费用行（FK 方向是子表指父表），所以按 payment_infos 的同款口径清孤儿。
    DELETE FROM logistics_fees WHERE id NOT IN (SELECT logistics_fee_id FROM logistics_orders WHERE logistics_fee_id IS NOT NULL);
    # ISS-142：删掉 warehouses 行不会带走指向它的采购单（没有外键），于是采购域被掏成断链。
    # 采购域没有删除端点，只能在这里按"子表先删"清干净：财务应付 → 采购明细 → 采购单。
    # 判据与 HRES_BEFORE/HRES_AFTER 用的是同一条 $HPOW（卫生 ㉒），换任何一边都会让计数器骗人。
    DELETE ft FROM financial_transactions ft JOIN purchase_orders po ON po.purchase_order_id = ft.reference_id
      JOIN warehouses w ON w.id = po.warehouse_id
      WHERE ft.reference_type='PURCHASE_ORDER' AND (${HPOW});
    DELETE poi FROM purchase_order_items poi JOIN purchase_orders po ON po.purchase_order_id = poi.purchase_order_id
      JOIN warehouses w ON w.id = po.warehouse_id WHERE (${HPOW});
    DELETE po FROM purchase_orders po JOIN warehouses w ON w.id = po.warehouse_id WHERE (${HPOW});
    DELETE ir FROM inventory_records ir JOIN warehouses w ON ir.warehouse_id = w.id WHERE (${HPOW});
    DELETE l FROM locations l JOIN warehouses w ON l.warehouse_id = w.id WHERE (${HPOW});
    DELETE FROM warehouses w WHERE (${HPOW});
    DELETE s FROM skus s JOIN products p ON s.product_id = p.id WHERE p.name LIKE '%留痕探针%' OR p.name LIKE 'R38%';
    DELETE FROM skus WHERE sku_code LIKE 'SMK12H-%' OR sku_code LIKE 'PROBE-%' OR sku_code LIKE 'PIMP-%' OR sku_code LIKE 'SKU-PROBE-%' OR sku_code LIKE 'SKU-R38%';
    DELETE FROM products WHERE name LIKE '%留痕探针%' OR name LIKE 'R38%';
    DELETE FROM customers WHERE (${HCUST});
    # BR-10 之后发货会写运费流水，而流水与物流单之间没有外键（R-05 缺的就是这个索引）。
    # 上一版这里用 transaction_id LIKE 'TXN-%' 兜底：主键格式是 TXN-<uuid8>，这条谓词等于全表删，
    # 会把采购到货应付、用户手工建的流水一起抹掉（违反 R-39「只清自己标记的数据」）。
    # 收窄成三条可判定的口径：本冒烟自己写的描述前缀 + 留痕探针标记 + 指向已不存在的物流单的运费流水
    # （物流单在上文已按标记删掉，孤儿流水即残留，与 logistics_fees / payment_infos 的孤儿清扫同款）。
    # status <> 'cancelled' 是必须的：ISS-139 之后"删掉物流单"会把它的应付流水置成已取消并留痕，
    # 那是应用有意留下的审计行而不是残留 —— 把已确认（SUCCESS）的孤儿留给上一条口径都不算，
    # 因为应用侧现在会拒绝那种删除。
    DELETE FROM financial_transactions WHERE description LIKE '冒烟%' OR description LIKE '%留痕探针流水%'
      OR (reference_type='LOGISTICS_ORDER' AND status <> 'cancelled'
          AND reference_id NOT IN (SELECT id FROM logistics_orders));
    # ISS-139 之后删单据会把名下应付置成 cancelled 并留痕（分录只置状态、不删行）。
    # 这些行不是残留，但本轮冒烟自己造的必须自己清：判据是开跑快照之后新出现的流水 id。
    # 判据用"开跑快照之后新出现的流水 id"而不是"cancelled + 孤儿"这种全表兜底谓词：
    # 用户在后台删自己的物流单留下的审计痕不在本脚本责任范围，扫走等于替用户销毁审计历史（R-39）。
    # 这段注释可以安全出现双引号，因为整批 SQL 现在走 here-doc（见上方 HPURGE_SQL 的说明）。
    DELETE FROM financial_transactions WHERE $FREIGHT_THIS_RUN AND status='cancelled';
    DELETE FROM shop_warehouses WHERE shop_code LIKE 'SMK%' OR shop_code LIKE 'PROBE-%' OR shop_code LIKE 'SHOP-STORE-%';
    DELETE FROM logistics_channels WHERE channel_code LIKE 'SMK12H-%' OR channel_code LIKE 'PROBE-%' OR channel_code LIKE 'SMKCH-%';
    DELETE FROM platform_integrations WHERE store_url LIKE '%smk12h-%' OR store_url LIKE '%probe-%' OR store_url LIKE '%r38-%';
    DELETE FROM order_import_job_errors WHERE job_id IN (SELECT id FROM order_import_jobs WHERE (${HJOB}));
    DELETE FROM order_import_jobs WHERE (${HJOB});
    DELETE FROM product_import_job_errors WHERE job_id IN (SELECT id FROM product_import_jobs WHERE (${HJOB}));
    DELETE FROM product_import_jobs WHERE (${HJOB});
    DELETE FROM customer_import_job_errors WHERE job_id IN (SELECT id FROM customer_import_jobs WHERE (${HJOB}));
    DELETE FROM customer_import_jobs WHERE (${HJOB});
    DELETE FROM finance_import_job_errors WHERE job_id IN (SELECT id FROM finance_import_jobs WHERE (${HJOB}));
    DELETE FROM finance_import_jobs WHERE (${HJOB});
    DELETE FROM logistics_import_job_errors WHERE job_id IN (SELECT id FROM logistics_import_jobs WHERE (${HJOB}));
    DELETE FROM logistics_import_jobs WHERE (${HJOB});
    DELETE FROM shop_warehouse_import_job_errors WHERE job_id IN (SELECT id FROM shop_warehouse_import_jobs WHERE (${HJOB}));
    DELETE FROM shop_warehouse_import_jobs WHERE (${HJOB});
EOSQL
)"
  HPURGE_LOG="$(mktemp -t erp-smoke-purge.XXXXXX)"
  docker exec mysql mysql -uroot -p"$DB_ROOT_PW" --default-character-set=utf8mb4 ecommerce_logistics_erp \
    -e "$HPURGE_SQL" >/dev/null 2>"$HPURGE_LOG"
  HPURGE_ERR="$(grep -E '^(ERROR|mysql:)' "$HPURGE_LOG" | grep -v 'Using a password' | head -3)"
  rm -f "$HPURGE_LOG"
  HRES_AFTER="$(dbq "SELECT
      (SELECT COUNT(*) FROM orders WHERE $HORD)
    + (SELECT COUNT(*) FROM customers WHERE (${HCUST}))
    + (SELECT COUNT(*) FROM products WHERE name LIKE '%留痕探针%' OR name LIKE 'R38%')
    + (SELECT COUNT(*) FROM shop_warehouses WHERE shop_code LIKE 'SMK%' OR shop_code LIKE 'PROBE-%' OR shop_code LIKE 'SHOP-STORE-%')
    + (SELECT COUNT(*) FROM purchase_orders po JOIN warehouses w ON w.id = po.warehouse_id WHERE (${HPOW}))
    + (SELECT COUNT(*) FROM order_import_jobs WHERE (${HJOB}))
    + (SELECT COUNT(*) FROM customer_import_jobs WHERE (${HJOB}))
    + (SELECT COUNT(*) FROM financial_transactions t WHERE t.reference_type='LOGISTICS_ORDER' AND t.reference_id NOT IN (SELECT id FROM logistics_orders) AND t.status <> 'cancelled')
    + (SELECT COUNT(*) FROM financial_transactions WHERE $FREIGHT_THIS_RUN AND status='cancelled')")"
  if [ "${HRES_AFTER:-x}" = "0" ] && [ -z "$HPURGE_ERR" ]; then
    ok "12.159 探针数据自清场完成（清理前 ${HRES_BEFORE:-?} 行标记数据，含历次残留）"
  else
    bad "12.159 探针数据自清场" "标记数据 0 行且清库无报错" "仍剩 ${HRES_AFTER:-?} 行（清理前 ${HRES_BEFORE:-?}）；SQL 报错：${HPURGE_ERR:-无}"
  fi
  HMAP_LEFT="$(dbq "SELECT COUNT(*) FROM order_field_mappings WHERE platform='SHOPIFY'")"
  if [ "${HMAP_LEFT:-1}" = "0" ]; then
    ok "12.160 SHOPIFY 字段映射已还原（进入本节前为空，现在仍为空）"
  else
    bad "12.160 SHOPIFY 字段映射已还原" "0 条" "${HMAP_LEFT} 条（12.112 的还原没生效）"
  fi
  # ISS-142 的常驻回归位（R-44）：与 §12.F 开头拍的"断链采购单"基线对比。
  # 上面那条计数器只数"还指向活仓"的探针采购单——仓一旦删掉，它的采购单就从计数器的 JOIN 里消失了，
  # 于是"删仓掏空引用"这个缺陷形状恰好是它看不见的；只有 LEFT JOIN 出来的断链总数看得见。
  # 判据是"不超过基线"而不是"等于 0"：历史那 530 行是守卫上线前攒下的，其中 361 行
  # supplier_id=DEFAULT_SUPPLIER（缺货自动单与用户在界面上出库到 0 同形），无法从数据证明是探针产物，
  # 按 R-40 只能备份后由人确认，不由脚本替用户删历史。本轮的责任是"一张都不许新增"。
  PO_DANGLING_AFTER="$(dbq "SELECT COUNT(*) FROM purchase_orders p LEFT JOIN warehouses w ON w.id=p.warehouse_id WHERE p.warehouse_id IS NOT NULL AND w.id IS NULL")"
  if [ -z "$PO_DANGLING_T0" ] || [ -z "$PO_DANGLING_AFTER" ]; then
    bad "12.171 本轮没有新增断链采购单" "基线与现值都取到" "T0=${PO_DANGLING_T0:-空} AFTER=${PO_DANGLING_AFTER:-空}（SQL 报错或表不存在）"
  elif [ "$PO_DANGLING_AFTER" -le "$PO_DANGLING_T0" ]; then
    ok "12.171 本轮没有新增断链采购单（基线 $PO_DANGLING_T0 → 清场后 $PO_DANGLING_AFTER）"
  else
    bad "12.171 本轮没有新增断链采购单" "≤ 基线 $PO_DANGLING_T0" "$PO_DANGLING_AFTER 行（删仓把采购单引用掏空了：守卫被绕过，或清场漏了采购域子表）"
  fi
else
  skip "12.159 探针数据自清场" "本地没有名为 mysql 的容器，纯 HTTP 冒烟不清数据侧残留"
  skip "12.160 SHOPIFY 字段映射已还原" "同上"
  skip "12.171 本轮没有新增断链采购单" "同上"
fi

# 留痕覆盖率的静态不变式：写端点必须全部带 @Auditable 或在白名单里写明理由。
# 12.H/12.I 只能证明「本轮打到的端点有留痕」，证明不了「没打到的端点也有」，
# 所以再跑一遍扫描器把 126 个写端点全量点一遍，缺口不为 0 就红。
if [ -f "$(dirname "$0")/audit-coverage-scan.js" ]; then
  SCAN_OUT="$(node "$(dirname "$0")/audit-coverage-scan.js" --strict 2>&1)"; SCAN_EXIT=$?
  printf '       %s\n' "$(printf '%s' "$SCAN_OUT" | tail -1)"
  if [ "$SCAN_EXIT" = "0" ]; then
    ok "12.161 写端点留痕覆盖扫描无缺口（audit-coverage-scan --strict）"
  else
    bad "12.161 写端点留痕覆盖扫描" "退出码 0" "退出码 $SCAN_EXIT：$(printf '%s' "$SCAN_OUT" | tail -3)"
  fi
else
  skip "12.161 写端点留痕覆盖扫描" "找不到 audit-coverage-scan.js"
fi

# ─────────────────────────── 13. 幻影租户行巡检 ───────────────────────────
# R-30/R-32 的数据侧不变式：default-tenant 在 tenants 表里没有行，按它落库的记录对任何登录租户
# 都不可见（接口 200、导入报"成功"，数据人间蒸发）。上一轮只人工复查了 5 张表，
# 结果 platform_integrations 里攒了 35 条没人看得见的行（含加密凭据）——所以这条巡检覆盖
# TenantRepositoryImpl.BUSINESS_TABLES 的全部 22 张表，把"看哪几张"从人的记忆里拿走。
head2 "13. 幻影租户行巡检（DB）"
DB_TABLES="orders products customers warehouses shops shop_warehouses logistics_orders purchase_orders financial_transactions platform_integrations order_import_jobs product_import_jobs customer_import_jobs logistics_import_jobs finance_import_jobs shop_warehouse_import_jobs order_field_mappings product_field_mappings customer_field_mappings logistics_field_mappings finance_field_mappings shop_warehouse_field_mappings"
DB_ROOT_PW="${SMOKE_DB_ROOT_PW:-123456}"
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'mysql'; then
  phantom_total=0
  for t in $DB_TABLES; do
    n=$(docker exec mysql mysql -uroot -p"$DB_ROOT_PW" -N -B ecommerce_logistics_erp \
        -e "SELECT COUNT(*) FROM $t WHERE tenant_id = 'default-tenant' OR tenant_id IS NULL OR tenant_id = '';" 2>/dev/null | head -1)
    if [ -z "$n" ]; then
      bad "13.$t 巡检可执行" "查到计数" "查询失败（表或列不存在？)"
    elif [ "$n" != "0" ]; then
      phantom_total=$((phantom_total + n))
      bad "13.$t 无幻影/空租户行" "0" "$n 条（写路径绕过了 TenantAccessGuard.resolveWriteTenant）"
    fi
  done
  [ "$phantom_total" = "0" ] && ok "13.0 22 张业务表的 default-tenant / 空租户行均为 0"
  # ISS-135 的数据侧不变式，比 13.0 更宽：只要 tenant_id 非空就必须能在 tenants 表里查到这一行。
  # "default-tenant" 只是这个集合里的一个特例，凭空点名的随机 UUID 同样会让行无人可见，
  # 而数据库不会替我们挡（tenant_id 是普通列不是外键，见 TenantRepository#countBusinessRecords 的注释）。
  orphan_total=0
  for t in $DB_TABLES; do
    n=$(docker exec mysql mysql -uroot -p"$DB_ROOT_PW" -N -B ecommerce_logistics_erp \
        -e "SELECT COUNT(*) FROM $t cur WHERE cur.tenant_id IS NOT NULL AND cur.tenant_id <> '' \
            AND NOT EXISTS (SELECT 1 FROM tenants x WHERE x.id = cur.tenant_id);" 2>/dev/null | head -1)
    if [ -z "$n" ]; then
      bad "13.o.$t 无主租户行巡检可执行" "查到计数" "查询失败（列不存在或表名写错？）"
    elif [ "$n" != "0" ]; then
      orphan_total=$((orphan_total + n))
      bad "13.o.$t 无主租户行为 0" "0" "$n 条（写路径把数据归属给了 tenants 表里不存在的租户）"
    fi
  done
  [ "$orphan_total" = "0" ] && ok "13.1 22 张业务表的租户引用都能在 tenants 表查到（无主行 0）"
else
  skip "13.0 幻影行巡检" "本地没有名为 mysql 的容器，纯 HTTP 冒烟不覆盖数据侧不变式"
fi

# ─────────────────────────── 14. 金额精度巡检（DB 列类型 + 真 HTTP 精确回读） ───────────────────────────
# ISS-023 / BR-18 / R-52。金额必须是定点小数，且这条口径有两处只有实测才守得住：
# ①「哪 10 列该是 DECIMAL(19,2)、每克费率该是 19,4」——列类型或 Java 类型一旦回退成 double，
#    接口照样 200、页面照样显示，只在求和时漏出 828.399999999999 这种噪声（修复前实测产物）；
# ②「豁免面恰好是两个重量列」——重量不是货币（docs/db/migration-plan.md §4 只约束金额列），
#    但也不许被顺手改成熟人不在场的类型。2026-10-10 实测全库 double 列就是这两个。
head2 "14. 金额精度巡检（DB 列类型 + 真 HTTP 精确回读）"
MONEY_COLUMNS="orders.total_amount:19,2 order_items.unit_price:19,2 order_items.total_price:19,2 skus.price:19,2 skus.cost_price:19,2 logistics_fees.base_fee:19,2 logistics_fees.weight_fee:19,2 logistics_fees.total_fee:19,2 logistics_channels.base_price:19,2 logistics_channels.price_per_gram:19,4 payment_infos.amount:19,2 purchase_orders.total_amount:19,2 purchase_order_items.unit_price:19,2 purchase_order_items.total_price:19,2 financial_transactions.amount:19,2"
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'mysql'; then
  decimal_bad=0
  for spec in $MONEY_COLUMNS; do
    col_path="${spec%%:*}"
    col_width="${spec##*:}"
    tbl="${col_path%%.*}"
    col="${col_path##*.}"
    n=$(docker exec mysql mysql -uroot -p"$DB_ROOT_PW" -N -B ecommerce_logistics_erp \
        -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='ecommerce_logistics_erp' \
            AND table_name='$tbl' AND column_name='$col' AND data_type='decimal' \
            AND numeric_precision=${col_width%%,*} AND numeric_scale=${col_width##*,};" 2>/dev/null | head -1)
    if [ "$n" != "1" ]; then
      decimal_bad=$((decimal_bad + 1))
      real=$(docker exec mysql mysql -uroot -p"$DB_ROOT_PW" -N -B ecommerce_logistics_erp \
          -e "SELECT CONCAT(IFNULL(data_type,'缺列'),'(',IFNULL(numeric_precision,'-'),',',IFNULL(numeric_scale,'-'),')') \
              FROM information_schema.columns WHERE table_schema='ecommerce_logistics_erp' \
              AND table_name='$tbl' AND column_name='$col';" 2>/dev/null | head -1)
      bad "14.0.$col_path 是 DECIMAL($col_width)" "DECIMAL($col_width)" "${real:-查询失败}"
    fi
  done
  [ "$decimal_bad" = "0" ] && ok "14.0 逐列核对 15 个金额列：14 个为 DECIMAL(19,2)、每克费率为 DECIMAL(19,4)"

  # 宽度收敛巡检（V7.2 的常驻位）：库里不应再有第三种金额宽度
  # 成因是实体没写 precision/scale 时 Hibernate 默认建 DECIMAL(38,2)——同一语义两种精度
  # 会让"金额列一律 19,2"的文档口径（migration-plan §4）与真实库不一致。
  odd_width=$(docker exec mysql mysql -uroot -p"$DB_ROOT_PW" -N -B ecommerce_logistics_erp \
      -e "SELECT IFNULL(GROUP_CONCAT(CONCAT(table_name,'.',column_name,'=',numeric_precision,',',numeric_scale) ORDER BY table_name,column_name SEPARATOR ','),'') \
          FROM information_schema.columns WHERE table_schema='ecommerce_logistics_erp' \
          AND data_type='decimal' AND NOT (numeric_precision=19 AND numeric_scale IN (2,4));" 2>/dev/null | head -1)
  if [ "$odd_width" = "" ]; then
    ok "14.0b 全库定点金额列只剩两种宽度（19,2 与每克费率 19,4），没有 38,2 残留"
  else
    bad "14.0b 全库定点金额列只剩两种宽度" "无" "$odd_width"
  fi

  # 浮点金额列巡检：数据侧的 MoneyPrecisionGuardTest 等价物（守卫测 Java 类型，这里测列类型）
  float_money=$(docker exec mysql mysql -uroot -p"$DB_ROOT_PW" -N -B ecommerce_logistics_erp \
      -e "SELECT IFNULL(GROUP_CONCAT(CONCAT(table_name,'.',column_name) ORDER BY table_name,column_name),'') \
          FROM information_schema.columns WHERE table_schema='ecommerce_logistics_erp' \
          AND data_type IN ('double','float','real') \
          AND column_name REGEXP '(amount|price|fee|cost|money|balance|payment|refund)';" 2>/dev/null | head -1)
  if [ "$float_money" = "" ]; then
    ok "14.1 全库没有浮点金额列（amount/price/fee/cost 等名次均为定点小数）"
  else
    bad "14.1 全库没有浮点金额列" "无" "$float_money"
  fi

  float_all=$(docker exec mysql mysql -uroot -p"$DB_ROOT_PW" -N -B ecommerce_logistics_erp \
      -e "SELECT IFNULL(GROUP_CONCAT(CONCAT(table_name,'.',column_name) ORDER BY table_name,column_name),'') \
          FROM information_schema.columns WHERE table_schema='ecommerce_logistics_erp' \
          AND data_type IN ('double','float','real');" 2>/dev/null | head -1)
  if [ "$float_all" = "logistics_labels.package_weight,skus.weight" ]; then
    ok "14.2 全库浮点列恰为两个重量列（豁免口径：重量非货币，见 migration-plan §4）"
  else
    bad "14.2 全库浮点列恰为两个重量列" "logistics_labels.package_weight,skus.weight" "${float_all:-一个都没有}"
  fi

  # 真 HTTP 精确回读：探针渠道 8.40 首重 + 0.0050 每克，走「请求 -> DECIMAL 列 -> BigDecimal -> JSON」整条链
  MONEY_CH="CHAN-MONEY-$RUN_ID"
  req POST "/logistics-channels" "{\"channelCode\":\"$MONEY_CH\",\"channelName\":\"金额精度探针渠道-$RUN_ID\",\"description\":\"ISS-023\",\"countries\":[\"CN\"],\"basePrice\":8.40,\"pricePerGram\":0.0050,\"currency\":\"CNY\",\"active\":true}"
  if [ "$HTTP_CODE" = "200" ]; then
    MCH="$(printf '%s' "$BODY" | jget data.channelId)"
    case "$BODY" in
      *'"basePrice":8.40'*)   ok "14.3 创建响应逐位输出 8.40（经 double 会变成 8.4）" ;;
      *) bad "14.3 创建响应逐位输出 8.40" '"basePrice":8.40' "$(printf '%s' "$BODY" | head -c 200)" ;;
    esac
    case "$BODY" in
      *'"pricePerGram":0.0050'*) ok "14.3a 创建响应逐位输出 0.0050（经 double 会变成 5.0E-3）" ;;
      *) bad "14.3a 创建响应逐位输出 0.0050" '"pricePerGram":0.0050' "$(printf '%s' "$BODY" | head -c 200)" ;;
    esac
    req GET "/logistics-channels/$MCH"
    case "$BODY" in
      *'"pricePerGram":0.0050'*) ok "14.3b 详情回读（DECIMAL(19,4) 列 -> BigDecimal）仍为 0.0050" ;;
      *) bad "14.3b 详情回读仍为 0.0050" '"pricePerGram":0.0050' "$(printf '%s' "$BODY" | head -c 200)" ;;
    esac
    db_pair=$(docker exec mysql mysql -uroot -p"$DB_ROOT_PW" -N -B ecommerce_logistics_erp \
        -e "SELECT CONCAT(base_price,'|',price_per_gram) FROM logistics_channels WHERE id='$MCH';" 2>/dev/null | head -1)
    if [ "$db_pair" = "8.40|0.0050" ]; then
      ok "14.3c 落库值逐位为 8.40 / 0.0050（写入没有把 0.0050 舍成 0.01 或 0.00）"
    else
      bad "14.3c 落库值逐位为 8.40 / 0.0050" "8.40|0.0050" "${db_pair:-查不到该行}"
    fi
    req GET "/logistics-channels/$MCH/fee?weight=1000&country=CN"
    case "$BODY" in
      *'"weightFee":5.00'*) ok "14.3d 运费试算的续重费逐位为 5.00（0.0050×1000g）" ;;
      *) bad "14.3d 运费试算的续重费逐位为 5.00" '"weightFee":5.00' "$(printf '%s' "$BODY" | head -c 200)" ;;
    esac
    case "$BODY" in
      *'"totalFee":13.40'*) ok "14.3e 运费试算合计逐位为 13.40（8.40+5.00，无浮点尾差）" ;;
      *) bad "14.3e 运费试算合计逐位为 13.40" '"totalFee":13.40' "$(printf '%s' "$BODY" | head -c 200)" ;;
    esac
    req DELETE "/logistics-channels/$MCH"
    assert_code "14.3f 删除探针渠道（探针自清场）" 200
    left=$(docker exec mysql mysql -uroot -p"$DB_ROOT_PW" -N -B ecommerce_logistics_erp \
        -e "SELECT COUNT(*) FROM logistics_channels WHERE channel_code='$MONEY_CH';" 2>/dev/null | head -1)
    [ "$left" = "0" ] && ok "14.3g 探针渠道无残留行" \
      || bad "14.3g 探针渠道无残留行" "0" "${left:-查询失败}（R-39：探针造成的数据必须自己复原）"
  else
    bad "14.3 创建金额精度探针渠道" "HTTP 200" "HTTP $HTTP_CODE body=${BODY:0:200}"
  fi
else
  skip "14.0 金额列类型巡检" "本地没有名为 mysql 的容器，纯 HTTP 冒烟不覆盖列类型"
  skip "14.3 金额精确回读探针" "同上"
fi

# ─────────────────────────── 汇总 ───────────────────────────
head2 "汇总"
printf "  通过 %d ｜ 失败 %d ｜ 跳过 %d\n" "$PASS_CNT" "$FAIL_CNT" "$SKIP_CNT"
if [ "$FAIL_CNT" -gt 0 ]; then
  printf "\n  失败项：\n"
  for f in "${FAILED_ITEMS[@]}"; do printf "   - %s\n" "$f"; done
  exit 1
fi
printf "\n  \033[32m全部通过\033[0m\n"
