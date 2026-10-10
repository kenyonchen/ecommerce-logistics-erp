#!/usr/bin/env bash
# R-32 / ISS-120 真机探针：客户端把历史默认桶字面量当显式租户传上来时，
# 服务端入口必须把它归一化成"由会话决定归属"，而不是判越权(400)或写进幻影桶。
#
# 本探针自带脚手架：两个租户都是本轮现场建的（不依赖手工 fixture），
# 收尾按 API 删除 + DB 复查残留，跑完库里不积累行（探针造成的数据必须由探针自己复原）。
set -uo pipefail

BASE=${SMOKE_BASE:-http://localhost:8082}
SEED_TENANT=d0861d6f-7d51-4ec5-a733-fda0a0058176
RUN_ID="r32$(date +%s)"
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
jget() { echo "$1" | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{try{const p=process.argv[1].split(".");let v=JSON.parse(d);for(const k of p)v=v?.[k];process.stdout.write(String(v??""))}catch(e){process.stdout.write("")}})' "$2"; }
code_of() { jget "$1" code; }
tenant_of() { jget "$1" data.tenantId; }

db_tenant_of() { $MYSQL "SELECT tenant_id FROM customers WHERE email='$1'" 2>/dev/null | head -1; }
phantom_count() { $MYSQL "SELECT COUNT(*) FROM customers WHERE tenant_id='default-tenant' OR tenant_id IS NULL OR tenant_id=''" 2>/dev/null | head -1; }
rows_of_tenant() { $MYSQL "SELECT COUNT(*) FROM customers WHERE tenant_id='$1'" 2>/dev/null | head -1; }

echo "== 0. 前置：幻影桶残留计数"
before=$(phantom_count)
echo "  customers 表幻影/空租户行数 = $before"
[ "$before" = "0" ] && ok "起点干净（历史幻影行已清零）" || ko "起点有 $before 条幻影行，探针结论无法归因"

echo "== 1. 现场建两个租户（本租户 + 用作负例的「他租户」），不依赖手工账号"
create_tenant() {
  curl -s -m 15 -X POST "$BASE/api/tenants" -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' \
    -d "{\"name\":\"哨兵探针租户-$1\",\"description\":\"R-32 探针自建\",\"contactEmail\":\"r32-$1@example.com\",\"adminUsername\":\"r32a$1\",\"adminPassword\":\"Probe#12345\",\"adminEmail\":\"r32a$1@example.com\",\"adminFirstName\":\"R32\",\"adminLastName\":\"Probe\"}"
}
ATOK=$(login admin 123456)
[ -n "$ATOK" ] && ok "平台管理员登录成功" || { ko "平台管理员登录失败，无法继续"; echo; echo "结果：$pass 通过 / $fail 失败"; exit 2; }
b=$(create_tenant "$RUN_ID")
PROBE_TENANT=$(jget "$b" data.id)
[ -n "$PROBE_TENANT" ] && ok "本租户已建 $PROBE_TENANT" || { ko "本租户建不出来：$(printf '%.200s' "$b")"; echo; echo "结果：$pass 通过 / $fail 失败"; exit 2; }
b=$(create_tenant "o$RUN_ID")
OTHER_TENANT=$(jget "$b" data.id)
[ -n "$OTHER_TENANT" ] && ok "他租户已建 $OTHER_TENANT（负例用）" || ko "他租户建不出来：$(printf '%.200s' "$b")"
TTOK=$(login "r32a$RUN_ID" 'Probe#12345')
[ -n "$TTOK" ] && ok "租户管理员登录成功" || { ko "租户管理员登录失败，无法继续"; echo; echo "结果：$pass 通过 / $fail 失败"; exit 2; }

echo "== 2. 租户用户 POST /api/customers 带 tenantId=default-tenant（修复前实测 400 TENANT_ACCESS_DENIED）"
NAME="R32探针客户-$RUN_ID"
EMAIL="r32-$RUN_ID@probe.test"
resp=$(curl -s -m 15 -w '\n%{http_code}' -X POST "$BASE/api/customers" \
  -H "Authorization: Bearer $TTOK" -H 'Content-Type: application/json' \
  -d "{\"tenantId\":\"default-tenant\",\"customerName\":\"$NAME\",\"email\":\"$EMAIL\",\"phone\":\"13800000001\"}")
http=$(echo "$resp" | tail -1); body=$(echo "$resp" | sed '$d')
CUST_ID=$(jget "$body" data.customerId)
echo "  HTTP=$http code=$(code_of "$body") data.tenantId=$(tenant_of "$body") id=$CUST_ID"
[ "$http" = "200" ] && [ "$(code_of "$body")" = "200" ] && ok "写请求不再被判越权" || ko "写请求失败：$(printf '%.200s' "$body")"
[ "$(tenant_of "$body")" = "$PROBE_TENANT" ] && ok "响应归属 = 登录租户" || ko "响应归属 = $(tenant_of "$body")，应为 $PROBE_TENANT"
dbt=$(db_tenant_of "$EMAIL")
echo "  DB 回读 tenant_id = $dbt"
[ "$dbt" = "$PROBE_TENANT" ] && ok "落库行归属 = 登录租户" || ko "落库行归属 = $dbt（幻影桶/空值即为回归）"

echo "== 3. 同一字面量的读路径：GET /api/customers?tenantId=default-tenant（page 从 0 起，见 docs/api/api.md）"
resp=$(curl -s -m 15 -w '\n%{http_code}' "$BASE/api/customers?page=0&size=50&tenantId=default-tenant" -H "Authorization: Bearer $TTOK")
http=$(echo "$resp" | tail -1); body=$(echo "$resp" | sed '$d')
stats=$(printf '%s' "$body" | EXPECT_NAME="$NAME" PROBE_TENANT="$PROBE_TENANT" node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{try{const j=JSON.parse(d);const l=(j.data&&j.data.content)||[];const mine=l.filter(r=>r.customerName===process.env.EXPECT_NAME).length;const foreign=l.filter(r=>r.tenantId&&r.tenantId!==process.env.PROBE_TENANT).length;process.stdout.write(l.length+"/"+mine+"/"+foreign)}catch(e){process.stdout.write("<err>")}})')
echo "  HTTP=$http 本页条数/命中本轮新建/他租户条数=$stats"
[ "$http" = "200" ] && ok "列表不再 400" || ko "列表 HTTP=$http"
[ "$(echo "$stats" | cut -d/ -f2)" -ge 1 ] && ok "正向回读：本轮新建客户出现在自己的列表里（否则就是「接口 200 但数据看不见」的形状）" || ko "正向回读失败：列表里没有本轮新建客户（$stats）"
[ "$(echo "$stats" | cut -d/ -f3)" = "0" ] && ok "结果全部收敛在本租户" || ko "结果含 $(echo "$stats" | cut -d/ -f3) 条他租户数据（越权）"

echo "== 4. 负例：显式传真实他租户必须仍然被拒（归一化没有把守卫清空）"
http=$(curl -s -m 15 -o /tmp/.r32neg1 -w '%{http_code}' "$BASE/api/customers?page=0&size=5&tenantId=$OTHER_TENANT" -H "Authorization: Bearer $TTOK")
echo "  读他租户 HTTP=$http body=$(head -c 160 /tmp/.r32neg1)"
[ "$http" = "400" ] && ok "跨租户读仍 400" || ko "跨租户读变成 $http，守卫被削弱"
http=$(curl -s -m 15 -o /tmp/.r32neg2 -w '%{http_code}' -X POST "$BASE/api/customers" \
  -H "Authorization: Bearer $TTOK" -H 'Content-Type: application/json' \
  -d "{\"tenantId\":\"$OTHER_TENANT\",\"customerName\":\"R32越权-$RUN_ID\",\"email\":\"neg-$RUN_ID@probe.test\"}")
echo "  写他租户 HTTP=$http body=$(head -c 160 /tmp/.r32neg2)"
[ "$http" = "400" ] && ok "跨租户写仍 400" || ko "跨租户写变成 $http"

echo "== 5. 平台管理员带字面量：不得写进幻影桶"
NAME2="R32平台探针客户-$RUN_ID"
EMAIL2="r32p-$RUN_ID@probe.test"
resp=$(curl -s -m 15 -w '\n%{http_code}' -X POST "$BASE/api/customers" \
  -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' \
  -d "{\"tenantId\":\"default-tenant\",\"customerName\":\"$NAME2\",\"email\":\"$EMAIL2\",\"phone\":\"13800000002\"}")
http=$(echo "$resp" | tail -1); body=$(echo "$resp" | sed '$d')
PCUST_ID=$(jget "$body" data.customerId)
dbt=$(db_tenant_of "$EMAIL2")
echo "  HTTP=$http code=$(code_of "$body") 响应 tenantId=$(tenant_of "$body") DB=$dbt"
[ "$http" = "200" ] && ok "平台管理员请求不因字面量报错" || ko "平台管理员 HTTP=$http"
[ "$dbt" != "default-tenant" ] && [ -n "$dbt" ] && ok "未落入幻影桶（$dbt）" || ko "仍写入幻影桶"
[ "$dbt" = "$SEED_TENANT" ] && ok "平台管理员的写入落在其登录租户 $SEED_TENANT" || ko "归属=$dbt，不是平台管理员所在租户"

echo "== 6. 探针后幻影桶复查"
after=$(phantom_count)
echo "  customers 表幻影/空租户行数 = $after"
[ "$after" = "0" ] && ok "本轮探针 0 幻影写入" || ko "新增 $((after - before)) 条幻影行"

echo "== 7. 复原（按 API 删除，租户随探针一起清掉）"
for cid in "$CUST_ID" "$PCUST_ID"; do
  [ -n "$cid" ] && {
    h=$(curl -s -m 15 -o /dev/null -w '%{http_code}' -X DELETE "$BASE/api/customers/$cid" -H "Authorization: Bearer $ATOK")
    [ "$h" = "200" ] && ok "客户 $cid 已删（HTTP=$h）" || ko "客户 $cid 删除 HTTP=$h"
  }
done
for tid in "$PROBE_TENANT" "$OTHER_TENANT"; do
  [ -n "$tid" ] && {
    h=$(curl -s -m 15 -o /tmp/.r32del -w '%{http_code}' -X DELETE "$BASE/api/tenants/$tid" -H "Authorization: Bearer $ATOK")
    [ "$h" = "200" ] && ok "租户 $tid 已删（HTTP=$h，墓碑不挡住删除＝R-31 回归位）" || ko "租户 $tid 删除 HTTP=$h body=$(head -c 160 /tmp/.r32del)"
  }
done
$MYSQL "DELETE FROM customers WHERE tenant_id='$PROBE_TENANT' OR tenant_id='$OTHER_TENANT'" 2>/dev/null
LEFT_P=$(rows_of_tenant "$PROBE_TENANT"); LEFT_O=$(rows_of_tenant "$OTHER_TENANT")
[ "${LEFT_P:-0}" = "0" ] && [ "${LEFT_O:-0}" = "0" ] && ok "两个探针租户的客户行（含墓碑）均为 0" || ko "残留客户行 probe=$LEFT_P other=$LEFT_O"
LIST=$(curl -s -m 15 "$BASE/api/tenants?page=0&size=200" -H "Authorization: Bearer $ATOK")
echo "$LIST" | grep -q "$RUN_ID" && ko "租户列表仍含本轮 RUN_ID（删除未生效）" || ok "租户列表已无本轮探针租户"
[ "$(phantom_count)" = "0" ] && ok "收尾后幻影桶计数仍为 0" || ko "收尾后幻影桶又攒出行"

echo
echo "结果：$pass 通过 / $fail 失败"
exit $((fail > 0 ? 1 : 0))
