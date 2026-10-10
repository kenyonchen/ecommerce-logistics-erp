#!/usr/bin/env bash
# ISS-121 / ISS-122 真机探针：/api/platform-integrations 按 ID 的写操作是否校验租户归属。
#
# 背景：platform:manage 这个点不只发给平台管理员，租户角色同样可以被授予（库里 d880b38c 的
# 「test」角色实测就带这个点），而 @PreAuthorize 只看"有没有点"、不看"这行是谁的"，
# 所以租户用户拿到别人的集成 ID 就能改/删/同步，连带读走该店铺的对接配置。
# 本探针用「租户角色 + platform:manage」这一组合复现，并给一条正例保证守卫不是空判。
#
# 本探针自带脚手架：攻击者与靶子租户都是本轮现场建的（不依赖手工 fixture），
# 收尾按 API 删除租户并按 DB 复查残留（探针造成的数据必须由探针自己复原）。
set -uo pipefail

BASE=${SMOKE_BASE:-http://localhost:8082}
RUN_ID="i122$(date +%s)"
MYSQL="docker exec mysql mysql -uroot -p123456 -N -B ecommerce_logistics_erp -e"

pass=0
fail=0
ok() { echo "  ✅ $1"; pass=$((pass + 1)); }
ko() { echo "  ❌ $1"; fail=$((fail + 1)); }

login() {
  curl -s -m 10 -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" |
    node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{try{const j=JSON.parse(d);process.stdout.write(j?.data?.token??j?.data?.accessToken??"")}catch(e){process.stdout.write("")}})'
}
code_of() { echo "$1" | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{try{process.stdout.write(String(JSON.parse(d)?.code))}catch(e){process.stdout.write("<parse-error>")}})'; }
jget() { echo "$1" | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{try{const p=process.argv[1].split(".");let v=JSON.parse(d);for(const k of p)v=v?.[k];process.stdout.write(String(v??""))}catch(e){process.stdout.write("")}})' "$2"; }
id_of() { jget "$1" data.id; }

# 集成行按 storeUrl 回读（URL 全 ASCII，避免中文列值在 shell 里往返出错）
db_field() { $MYSQL "SELECT $2 FROM platform_integrations WHERE store_url='$1' LIMIT 1" 2>/dev/null | head -1; }
db_count_by_url() { $MYSQL "SELECT COUNT(*) FROM platform_integrations WHERE store_url='$1'" 2>/dev/null | head -1; }
db_count_of_tenant() { $MYSQL "SELECT COUNT(*) FROM platform_integrations WHERE tenant_id='$1'" 2>/dev/null | head -1; }
phantom_count() { $MYSQL "SELECT COUNT(*) FROM platform_integrations WHERE tenant_id='default-tenant' OR tenant_id IS NULL OR tenant_id=''" 2>/dev/null | head -1; }

VICTIM_URL="https://iss122-victim-$RUN_ID.myshopify.com"
OWN_URL="https://iss122-own-$RUN_ID.myshopify.com"

# 实验卫生：中途失败退出（exit 2）也必须把临时挂上去的 platform:manage 和两个探针租户摘掉
TENANT_ADMIN_ROLE=""
revoke_point() {
  [ -n "$TENANT_ADMIN_ROLE" ] && $MYSQL "DELETE FROM role_jpa_entity_permissions WHERE role_jpa_entity_id='$TENANT_ADMIN_ROLE' AND permission='platform:manage'" 2>/dev/null
  return 0
}
trap revoke_point EXIT

body_for() { printf '{"tenantId":"%s","platformName":"SHOPIFY","authType":"SHOPIFY","storeUrl":"%s","accessToken":"shpat_%s","apiKey":"ak-%s","apiSecret":"sk-%s","status":"INACTIVE"}' "$1" "$2" "$RUN_ID" "$RUN_ID" "$RUN_ID"; }

create_tenant() {
  curl -s -m 15 -X POST "$BASE/api/tenants" -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' \
    -d "{\"name\":\"集成归属探针租户-$1\",\"description\":\"ISS-122 探针自建\",\"contactEmail\":\"i122-$1@example.com\",\"adminUsername\":\"i122a$1\",\"adminPassword\":\"Probe#12345\",\"adminEmail\":\"i122a$1@example.com\",\"adminFirstName\":\"I122\",\"adminLastName\":\"Probe\"}"
}

echo "== 0. 前置：幻影桶计数 + 现场建两个租户（攻击者租户 + 靶子租户）"
PHANTOM_BEFORE=$(phantom_count)
echo "  幻影行数（default-tenant / NULL / 空串）= $PHANTOM_BEFORE"
[ "$PHANTOM_BEFORE" = "0" ] && ok "起点幻影桶干净" || ko "起点有 $PHANTOM_BEFORE 条幻影行，归因会被污染"
ATOK=$(login admin 123456)
[ -n "$ATOK" ] && ok "平台管理员登录成功" || { ko "平台管理员登录失败，无法继续"; echo; echo "结果：$pass 通过 / $fail 失败"; exit 2; }
b=$(create_tenant "$RUN_ID")
PROBE_TENANT=$(id_of "$b")
[ -n "$PROBE_TENANT" ] && ok "攻击者租户已建 $PROBE_TENANT" || { ko "攻击者租户建不出来：$(printf '%.200s' "$b")"; echo; echo "结果：$pass 通过 / $fail 失败"; exit 2; }
b=$(create_tenant "v$RUN_ID")
VICTIM_TENANT=$(id_of "$b")
[ -n "$VICTIM_TENANT" ] && ok "靶子租户已建 $VICTIM_TENANT" || { ko "靶子租户建不出来：$(printf '%.200s' "$b")"; echo; echo "结果：$pass 通过 / $fail 失败"; exit 2; }
TENANT_ADMIN_ROLE=$($MYSQL "SELECT id FROM roles WHERE tenant_id='$PROBE_TENANT' AND name='TENANT_ADMIN' LIMIT 1" 2>/dev/null | head -1)
PERM_BEFORE=$($MYSQL "SELECT COUNT(*) FROM role_jpa_entity_permissions WHERE role_jpa_entity_id='$TENANT_ADMIN_ROLE'" 2>/dev/null | head -1)
HAS_POINT=$($MYSQL "SELECT COUNT(*) FROM role_jpa_entity_permissions WHERE role_jpa_entity_id='$TENANT_ADMIN_ROLE' AND permission='platform:manage'" 2>/dev/null | head -1)
echo "  探针租户 TENANT_ADMIN 角色=$TENANT_ADMIN_ROLE 权限点数=$PERM_BEFORE（本轮临时加 platform:manage，收尾必复原）"
[ -n "$TENANT_ADMIN_ROLE" ] && ok "定位到本轮自建的 TENANT_ADMIN 角色" || { ko "探针角色不存在，无法继续"; echo; echo "结果：$pass 通过 / $fail 失败"; exit 2; }
[ "$HAS_POINT" = "0" ] && ok "起点：该角色本来不带 platform:manage（授权由本探针自己施加并复原）" || ko "起点已带 platform:manage，复原口径不准"

echo "== 1. 给探针租户角色挂上 platform:manage（复现库里已存在的真实授权形态）"
$MYSQL "INSERT INTO role_jpa_entity_permissions (role_jpa_entity_id, permission) VALUES ('$TENANT_ADMIN_ROLE','platform:manage')" 2>/dev/null
TTOK=$(login "i122a$RUN_ID" 'Probe#12345')
[ -n "$TTOK" ] && ok "租户用户登录成功" || { ko "租户用户登录失败，无法继续"; echo; echo "结果：$pass 通过 / $fail 失败"; exit 2; }

echo "== 2. 正例（前置）：租户用户在本租户创建集成——证明 @PreAuthorize 已放行，后面的 404 才是归属守卫的功劳"
resp=$(curl -s -m 15 -w '\n%{http_code}' -X POST "$BASE/api/platform-integrations" \
  -H "Authorization: Bearer $TTOK" -H 'Content-Type: application/json' \
  -d "$(body_for default-tenant "$OWN_URL")")
http=$(echo "$resp" | tail -1); body=$(echo "$resp" | sed '$d')
OWN_ID=$(id_of "$body")
echo "  HTTP=$http code=$(code_of "$body") id=$OWN_ID"
[ "$http" = "200" ] && [ -n "$OWN_ID" ] && ok "租户用户带平台权限可写本租户（权限点确实生效）" || ko "本租户创建失败：$(printf '%.200s' "$body")"
ownt=$(db_field "$OWN_URL" tenant_id)
echo "  DB 回读 store_url=$OWN_URL → tenant_id=$ownt"
[ "$ownt" = "$PROBE_TENANT" ] && ok "ISS-121：字面量 tenantId 的创建落登录租户，没进幻影桶" || ko "ISS-121 回归：归属=$ownt"

echo "== 3. 造一条他租户（靶子租户）的集成作为靶子"
resp=$(curl -s -m 15 -w '\n%{http_code}' -X POST "$BASE/api/platform-integrations" \
  -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' \
  -d "$(body_for "$VICTIM_TENANT" "$VICTIM_URL")")
http=$(echo "$resp" | tail -1); body=$(echo "$resp" | sed '$d')
VICTIM_ID=$(id_of "$body")
echo "  HTTP=$http code=$(code_of "$body") id=$VICTIM_ID"
[ -n "$VICTIM_ID" ] && ok "靶子集成已建（$VICTIM_ID @ $VICTIM_TENANT）" || { ko "靶子建不出来，负例无意义"; echo; echo "结果：$pass 通过 / $fail 失败"; exit 2; }
[ "$(db_field "$VICTIM_URL" tenant_id)" = "$VICTIM_TENANT" ] && ok "平台管理员代客写入靶子租户成功（R-30 第三段未误伤）" || ko "靶子归属不是 $VICTIM_TENANT"

echo "== 4. 负例：租户用户拿他租户的集成 ID 做四种写操作，必须全部 404（跨租户与不存在同码）"
declare -a NEG_NAMES=(PUT DELETE 激活 停用)
declare -a NEG_HTTPS=()
h=$(curl -s -m 15 -o /tmp/.i122n0 -w '%{http_code}' -X PUT "$BASE/api/platform-integrations/$VICTIM_ID" \
  -H "Authorization: Bearer $TTOK" -H 'Content-Type: application/json' \
  -d "$(body_for "$VICTIM_TENANT" "https://hijacked-$RUN_ID.myshopify.com")"); NEG_HTTPS+=("$h")
h=$(curl -s -m 15 -o /tmp/.i122n1 -w '%{http_code}' -X DELETE "$BASE/api/platform-integrations/$VICTIM_ID" \
  -H "Authorization: Bearer $TTOK"); NEG_HTTPS+=("$h")
h=$(curl -s -m 15 -o /tmp/.i122n2 -w '%{http_code}' -X POST "$BASE/api/platform-integrations/$VICTIM_ID/activate" \
  -H "Authorization: Bearer $TTOK"); NEG_HTTPS+=("$h")
h=$(curl -s -m 15 -o /tmp/.i122n3 -w '%{http_code}' -X POST "$BASE/api/platform-integrations/$VICTIM_ID/deactivate" \
  -H "Authorization: Bearer $TTOK"); NEG_HTTPS+=("$h")
idx=0
for name in "${NEG_NAMES[@]}"; do
  printf '  %-4s HTTP=%s body=%s\n' "$name" "${NEG_HTTPS[$idx]}" "$(head -c 120 "/tmp/.i122n$idx")"
  idx=$((idx + 1))
done
all404=1
for h in "${NEG_HTTPS[@]}"; do [ "$h" = "404" ] || all404=0; done
[ "$all404" = "1" ] && ok "四种按 ID 写操作全部 404" || ko "有非 404 的响应：${NEG_HTTPS[*]}（横向越权未被拦住）"

echo "== 5. 副作用回读：被拒之后靶子必须原样还在（只看状态码不算验证）"
vt=$(db_field "$VICTIM_URL" tenant_id)
vc=$(db_count_by_url "$VICTIM_URL")
echo "  DB 回读靶子行数=$vc tenant_id=$vt"
[ "$vc" = "1" ] && ok "靶子没被删掉" || ko "靶子行数=$vc，删除已生效（越权落地）"
[ "$vt" = "$VICTIM_TENANT" ] && ok "靶子归属没被改走" || ko "靶子归属变成 $vt"
HIJACKED=$(db_count_by_url "https://hijacked-$RUN_ID.myshopify.com")
[ "$HIJACKED" = "0" ] && ok "靶子内容没被改写（库里不存在被劫持后的 store_url 行，PUT 未落库）" || ko "越权更新落地：hijacked URL 行数=$HIJACKED"

echo "== 6. 读路径：租户用户的列表里不得出现他租户集成"
resp=$(curl -s -m 15 "$BASE/api/platform-integrations" -H "Authorization: Bearer $TTOK")
stats=$(printf '%s' "$resp" | PROBE_TENANT="$PROBE_TENANT" node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{try{const j=JSON.parse(d);const l=Array.isArray(j.data)?j.data:[];const foreign=l.filter(r=>r.tenantId&&r.tenantId!==process.env.PROBE_TENANT).length;process.stdout.write(l.length+"/"+foreign)}catch(e){process.stdout.write("<err>")}})')
echo "  条数/他租户条数=$stats"
[ "${stats#*/}" = "0" ] && ok "列表收敛在本租户" || ko "列表含 ${stats#*/} 条他租户集成"

echo "== 7. 正例收尾：本租户的按 ID 写操作照常放行（守卫没有把功能一起关掉）"
h=$(curl -s -m 15 -o /tmp/.i122ownact -w '%{http_code}' -X POST "$BASE/api/platform-integrations/$OWN_ID/activate" -H "Authorization: Bearer $TTOK")
[ "$h" = "200" ] && ok "本租户激活 200（响应 $(code_of "$(cat /tmp/.i122ownact)")）" || ko "本租户激活变成 $h，守卫过严"
# 同步端点同样要有归属守卫（它会把店铺侧数据拉进本租户，是最贵的一条）
h=$(curl -s -m 15 -o /tmp/.i122nsync -w '%{http_code}' -X POST "$BASE/api/platform-integrations/$VICTIM_ID/sync-orders" -H "Authorization: Bearer $TTOK")
[ "$h" = "404" ] && ok "跨租户同步仍 404（sync-* 也过归属校验）" || ko "跨租户同步 HTTP=$h，未按 404 收敛"

echo "== 8. 复原（探针造成的数据与授权必须由探针自己清掉）"
deleted_ok() { [ "$1" = "200" ] || [ "$1" = "204" ]; }
h=$(curl -s -m 15 -o /dev/null -w '%{http_code}' -X DELETE "$BASE/api/platform-integrations/$OWN_ID" -H "Authorization: Bearer $TTOK")
deleted_ok "$h" && ok "自建的集成已用 API 删除（HTTP=$h）" || ko "自建集成删除 HTTP=$h"
h=$(curl -s -m 15 -o /dev/null -w '%{http_code}' -X DELETE "$BASE/api/platform-integrations/$VICTIM_ID" -H "Authorization: Bearer $ATOK")
deleted_ok "$h" && ok "靶子集成已删除（HTTP=$h）" || ko "靶子集成删除 HTTP=$h"
$MYSQL "DELETE FROM role_jpa_entity_permissions WHERE role_jpa_entity_id='$TENANT_ADMIN_ROLE' AND permission='platform:manage'" 2>/dev/null
PERM_AFTER=$($MYSQL "SELECT COUNT(*) FROM role_jpa_entity_permissions WHERE role_jpa_entity_id='$TENANT_ADMIN_ROLE'" 2>/dev/null | head -1)
[ "$PERM_AFTER" = "$PERM_BEFORE" ] && ok "探针角色权限点数回到 $PERM_AFTER" || ko "权限点数 $PERM_BEFORE → $PERM_AFTER，platform:manage 未复原"
LEFT=$(db_count_by_url "$OWN_URL"); LEFTV=$(db_count_by_url "$VICTIM_URL")
[ "$LEFT" = "0" ] && [ "$LEFTV" = "0" ] && ok "本轮两条集成行均已清（0/0）" || ko "残留集成行：own=$LEFT victim=$LEFTV"
for t in "$PROBE_TENANT" "$VICTIM_TENANT"; do
  h=$(curl -s -m 15 -o /tmp/.i122del -w '%{http_code}' -X DELETE "$BASE/api/tenants/$t" -H "Authorization: Bearer $ATOK")
  deleted_ok "$h" && ok "探针租户 $t 已删（HTTP=$h）" || ko "探针租户 $t 删除 HTTP=$h body=$(head -c 160 /tmp/.i122del)"
  [ "$(db_count_of_tenant "$t")" = "0" ] && ok "租户 $t 的集成行残留 0" || ko "租户 $t 仍留集成行（$(db_count_of_tenant "$t")）"
done
PHANTOM_AFTER=$(phantom_count)
echo "  探针后幻影行数 = $PHANTOM_AFTER"
[ "$PHANTOM_AFTER" = "0" ] && ok "0 幻影写入" || ko "新增 $((PHANTOM_AFTER - PHANTOM_BEFORE)) 条幻影行"

echo
echo "结果：$pass 通过 / $fail 失败"
exit $((fail > 0 ? 1 : 0))
