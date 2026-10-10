#!/usr/bin/env bash
# ISS-125 探针：读路径重建聚合时 createdAt/updatedAt 被"本次请求时刻"伪造。
#
# 三条断言：
#   A) 读回来的 createdAt 必须来自库里那一行（而不是 now()）；
#   B) PUT 更新不得把库里 created_at 覆盖成当前时间（这是真实数据丢失，不只是显示错）；
#   C) 用户详情里内嵌的 roles[].createdAt 走的是另一处重建点（UserRepositoryImpl.toDomainRole），
#      同一根因，必须一起验。
#
# 时区口径（实测确认，不在断言里硬编码）：JDBC URL 带 serverTimezone=UTC，而领域侧用的是
# 无时区的 LocalDateTime.now()，于是"接口值 = 库里值 + 进程本地偏移"（本机 +8h）。
# 这个偏移对本机是常量，所以 A 不断言"接口值 == 库里值"，而是
# **先测出该 app 自己写入行的偏移，再断言把库里时间整体前挪 3650 天后接口值同步前挪 3650 天**——
# 修复前接口值纹丝不动（因为它是 now()），修复后必须与库里的挪动一致。
# 偏移本身的语义问题另立 ISS-126 登记，不在本探针范围内。
#
# 探针自建的行由探针自己删除（R-39）。
set -uo pipefail
BASE=http://localhost:8082
TID=d0861d6f-7d51-4ec5-a733-fda0a0058176
BACK=3650   # 前挪天数
P=0; F=0
ok(){ echo "  ✅ $1"; P=$((P+1)); }
bad(){ echo "  ❌ $1"; F=$((F+1)); }
dbq(){ docker exec mysql mysql -uroot -p123456 -N -e "USE ecommerce_logistics_erp; $1" 2>/dev/null; }
iso2epoch(){ date -j -f '%Y-%m-%dT%H:%M:%S' "$1" +%s 2>/dev/null || date -d "$1" +%s; }

TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
[ -n "$TOKEN" ] || { echo "登录失败"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

# 只清上一次异常中断留下的行：按探针自己的用户名/角色名认（R-39）
PRE=$(dbq "SELECT COUNT(*) FROM users WHERE username='iss125probe'")
if [ "$PRE" != "0" ]; then
  PID=$(dbq "SELECT id FROM users WHERE username='iss125probe'")
  dbq "DELETE FROM user_roles WHERE user_id='$PID'; DELETE FROM users WHERE id='$PID';"
fi

echo "== 1. 建探针用户 =="
UID_=$(curl -s -X POST $BASE/api/users -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"tenantId\":\"$TID\",\"username\":\"iss125probe\",\"password\":\"Probe#12345\",\"email\":\"iss125probe@example.com\",\"firstName\":\"Probe\",\"lastName\":\"ISS125\"}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("id",""))')
[ -n "$UID_" ] && ok "创建成功 id=$UID_" || { bad "创建失败"; exit 1; }

echo "== 2. 标定 app 自身写入行的时区偏移 =="
A0=$(curl -s $BASE/api/users/$UID_ -H "$AUTH" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(((d.get("data") or {}).get("createdAt") or "")[:19])')
D0=$(dbq "SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s') FROM users WHERE id='$UID_'")
OFFSET=$(( $(iso2epoch "$A0") - $(iso2epoch "$D0") ))
echo "  API=$A0 DB=$D0 偏移=${OFFSET}s"
if [ "$OFFSET" -ge 0 ] && [ "$OFFSET" -le 86400 ]; then ok "偏移标定完成（${OFFSET}s，本机 +8h 属正常）"; else bad "偏移异常（${OFFSET}s），探针前提不成立"; exit 1; fi

echo "== 3. 断言 A：读回的 createdAt 必须随库里那一行挪动（修复前它恒等于 now()）=="
dbq "UPDATE users SET created_at=DATE_SUB(created_at, INTERVAL $BACK DAY), updated_at=DATE_SUB(updated_at, INTERVAL $BACK DAY) WHERE id='$UID_'"
A1=$(curl -s $BASE/api/users/$UID_ -H "$AUTH" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(((d.get("data") or {}).get("createdAt") or "")[:19])')
D1=$(dbq "SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s') FROM users WHERE id='$UID_'")
EXPECT=$(( $(iso2epoch "$A0") - BACK*86400 ))
GOT=$(( $(iso2epoch "$A1") ))
echo "  库里挪动后=$D1，接口读回=$A1"
if [ "$GOT" = "$EXPECT" ]; then ok "接口值精确前挪 ${BACK} 天（$A0 → $A1）"; else bad "接口值没跟着库里挪动（期望 $A0-${BACK}d / 实际 $A1）"; fi

echo "== 4. 断言 B：PUT 更新不得覆盖库里 created_at =="
curl -s -X PUT $BASE/api/users/$UID_ -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"email":"iss125probe@example.com","phone":"13800001234","firstName":"ProbeRenamed","lastName":"ISS125"}' >/dev/null
D2=$(dbq "SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s') FROM users WHERE id='$UID_'")
if [ "$D2" = "$D1" ]; then ok "created_at 未被写路径覆盖（仍为 $D2）"; else bad "PUT 把库里 created_at 覆盖了（$D1 → $D2）"; fi
A2=$(curl -s $BASE/api/users/$UID_ -H "$AUTH" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(((d.get("data") or {}).get("firstName") or ""))')
[ "$A2" = "ProbeRenamed" ] && ok "PUT 本身确实生效（firstName=$A2）" || bad "PUT 未生效，本轮断言无意义（firstName=$A2）"

echo "== 5. 断言 C：内嵌 roles[].createdAt（第二处重建点 UserRepositoryImpl.toDomainRole）=="
jrole(){ python3 -c "
import sys,json
d=json.load(sys.stdin)
roles=(d.get('data') or {}).get('roles') or []
print(next((r.get('createdAt','')[:19] for r in roles if r.get('id')=='$RID'),''))"; }
RID=$(curl -s -X POST $BASE/api/roles -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"tenantId\":\"$TID\",\"name\":\"iss125role\",\"description\":\"ISS-125 探针角色\"}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("id",""))')
if [ -z "$RID" ]; then
  bad "探针角色创建失败，C 分支未执行"
else
  curl -s -X POST $BASE/api/users/$UID_/roles -H "$AUTH" -H 'Content-Type: application/json' -d "{\"roleId\":\"$RID\"}" >/dev/null
  RA0=$(curl -s $BASE/api/users/$UID_ -H "$AUTH" | jrole)
  dbq "UPDATE roles SET created_at=DATE_SUB(created_at, INTERVAL $BACK DAY) WHERE id='$RID'"
  RA1=$(curl -s $BASE/api/users/$UID_ -H "$AUTH" | jrole)
  RB1=$(curl -s $BASE/api/roles/$RID -H "$AUTH" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(((d.get("data") or {}).get("createdAt") or "")[:19])')
  RD1=$(dbq "SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s') FROM roles WHERE id='$RID'")
  echo "  挪动前接口=$RA0 / 挪动后接口=$RA1 / GET /api/roles 接口=$RB1 / 库里=$RD1"
  if [ -z "$RA0" ] || [ -z "$RA1" ]; then
    bad "没能取到内嵌角色的 createdAt（响应里 roles 为空？）"
  elif [ "$RA1" = "$RA0" ]; then
    bad "内嵌角色 createdAt 不随库里挪动（$RA0 与 $RA1 相同，仍是 now()）"
  else
    ok "内嵌角色 createdAt 已随库里挪动（$RA0 → $RA1）"
  fi
  if [ "$RB1" = "$RA1" ]; then ok "GET /api/roles/{id} 与内嵌视图一致（$RB1）"; else bad "两处角色读法不一致（/api/roles=$RB1 vs 内嵌=$RA1）"; fi
  curl -s -X PUT $BASE/api/users/$UID_ -H "$AUTH" -H 'Content-Type: application/json' \
    -d '{"email":"iss125probe@example.com","phone":"13800001234","firstName":"ProbeRenamed2","lastName":"ISS125"}' >/dev/null
  RD2=$(dbq "SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s') FROM roles WHERE id='$RID'")
  if [ "$RD2" = "$RD1" ]; then ok "PUT 用户未连带覆盖角色 created_at"; else bad "PUT 用户把角色 created_at 覆盖了（$RD1 → $RD2）"; fi
fi

echo "== 6. 清场：删探针用户与角色并复查无残留（先解绑再删角色）=="
dbq "DELETE FROM user_roles WHERE user_id='$UID_'"
curl -s -X DELETE $BASE/api/users/$UID_ -H "$AUTH" >/dev/null
[ -n "${RID:-}" ] && curl -s -X DELETE $BASE/api/roles/$RID -H "$AUTH" >/dev/null
[ -n "${RID:-}" ] && dbq "DELETE FROM role_jpa_entity_permissions WHERE role_id='$RID'; DELETE FROM roles WHERE id='$RID';"
LEFT=$(dbq "SELECT COUNT(*) FROM users WHERE username='iss125probe'")
BIND=$(dbq "SELECT COUNT(*) FROM user_roles WHERE user_id='$UID_'")
RLEFT=$(dbq "SELECT COUNT(*) FROM roles WHERE name='iss125role'")
if [ "$LEFT" = "0" ] && [ "$BIND" = "0" ] && [ "$RLEFT" = "0" ]; then
  ok "探针数据已清空（users=$LEFT user_roles=$BIND roles=$RLEFT）"
else
  bad "残留 users=$LEFT user_roles=$BIND roles=$RLEFT"
fi
dbq "DELETE FROM audit_logs WHERE target_id IN ('$UID_','${RID:-}')" >/dev/null
echo "  已删除本次探针留下的审计行（target_id=${UID_} / ${RID:-—}）"

echo
echo "结果：$P 通过 / $F 失败"
[ "$F" = "0" ] || exit 1
