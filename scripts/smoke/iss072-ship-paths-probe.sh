#!/usr/bin/env bash
# ISS-072 探针：三条"发货"路径必须得到同一个业务结果——订单推进为 SHIPPED 且按订单行出库一次。
#
# 三条路径（都是接口真实会走的入口）：
#   A) PUT /api/orders/{id}/status?status=SHIPPED      —— 参照实现：出库唯一的落点（BR-04）
#   B) POST /api/logistics/batch-ship                  —— 修复前自己改订单状态，绕开扣减
#   C) POST /api/logistics/{id}/ship                   —— 修复前只动物流单，既不推进订单也不出库
#      （该端点的 @Auditable label 一直写着「标记发货并扣减库存」，属于对用户的谎报）
#
# 判定口径（回读副作用，不看 HTTP 码）：
#   ① 订单状态 == SHIPPED；② 该 SKU 在该仓的余量比发货前少（发货数量）；
#   ③ 负例：对已发货订单重复发货，余量不得再少一次（扣减必须幂等）；
#   ④ 负例：库存不足时发货必须被拒，且不得留下「订单已发货、库存没扣」或
#      「物流已揽收、订单没发」的半态（物流单状态也要回读）。
# 探针自建行由探针自己删除（R-39：只认自己的 orderNumber / warehouseCode / sku 标记）。
set -uo pipefail
BASE=http://localhost:8082
RUN="$(date +%s)$$"
SKU="ISS072-SKU-$RUN"
P=0; F=0
ok(){ echo "  ✅ $1"; P=$((P+1)); }
bad(){ echo "  ❌ $1"; F=$((F+1)); }
# 合并 stderr：MySQL 报错必须被看见（ hygiene：mysql -e 从首条失败语句起整批不执行）
dbq(){ docker exec mysql mysql -uroot -p123456 -N -D ecommerce_logistics_erp -e "$1" 2>&1 | grep -v "Using a password"; }
# ISS-142 基线：开跑前全库"指向不存在的仓"的采购单行数，收尾拿它比对（见脚本尾部清场段）
DANGLING_T0=$(dbq "SELECT COUNT(*) FROM purchase_orders p LEFT JOIN warehouses w ON w.id = p.warehouse_id WHERE p.warehouse_id IS NOT NULL AND w.id IS NULL")

TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("token",""))')
[ -n "$TOKEN" ] || { echo "登录失败，探针无法执行"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

echo "== 1. 建探针仓并入库 3 件 =="
WID=$(curl -s -X POST $BASE/api/warehouses -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"warehouseName\":\"ISS072探针仓-$RUN\",\"warehouseCode\":\"ISS072-$RUN\",\"address\":\"探针地址\",\"capacity\":100}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("warehouseId",""))')
[ -n "$WID" ] && ok "探针仓 $WID" || { bad "创建仓库失败：$(dbq "SELECT 1")"; exit 1; }
curl -s -X POST "$BASE/api/warehouses/$WID/inventory/in" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"sku\":\"$SKU\",\"productId\":\"$SKU\",\"warehouseId\":\"$WID\",\"quantity\":3,\"movementType\":\"INBOUND\"}" >/dev/null

stock(){ curl -s "$BASE/api/warehouses/$WID/inventory" -H "$AUTH" | python3 -c "
import sys,json
d=json.load(sys.stdin)
rows=d.get('data') or []
print(sum((r.get('quantity') or 0) for r in rows if (r.get('sku') or r.get('productId'))=='$SKU'))"; }
CH=$(curl -s "$BASE/api/logistics-channels" -H "$AUTH" | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(next((str(c['channelId']) for c in (d.get('data') or []) if c.get('channelCode')=='DEFAULT_CHANNEL'),''))")
[ -n "$CH" ] && ok "物流渠道 DEFAULT_CHANNEL=$CH" || bad "取不到物流渠道，路径 B/C 无法建单"
S0=$(stock)
[ "$S0" = "3" ] && ok "发货前余量=3" || { bad "发货前余量应为 3，实际 $S0（探针前提不成立）"; exit 1; }

mkorder(){ curl -s -X POST $BASE/api/orders -H "$AUTH" -H 'Content-Type: application/json' -d "{
  \"orderNumber\":\"ISS072$1$RUN\",\"customerId\":\"ISS072-CUST-$1\",\"customerName\":\"ISS072 客户\",
  \"customerEmail\":\"iss072$1@example.com\",
  \"orderItems\":[{\"productName\":\"ISS072 商品\",\"skuCode\":\"$SKU\",\"quantity\":1,\"unitPrice\":9.9,\"totalPrice\":9.9}],
  \"paymentInfo\":{\"amount\":9.9,\"paymentMethod\":\"CREDIT_CARD\",\"paymentStatus\":\"PAID\",\"transactionId\":\"ISS072$1-$RUN\"},
  \"shippingAddress\":{\"country\":\"CN\",\"province\":\"广东\",\"city\":\"深圳\",\"street\":\"探针路 1 号\",\"recipient\":\"探针收件人\",\"phone\":\"13900000000\",\"zipCode\":\"518000\"}}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("orderId",""))'; }
statusof(){ curl -s "$BASE/api/orders/$1" -H "$AUTH" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(((d.get("data") or {}).get("status") or ""))'; }
lostatusof(){ curl -s "$BASE/api/logistics/$1" -H "$AUTH" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(((d.get("data") or {}).get("status") or ""))'; }
to_processing(){ curl -s -X PUT "$BASE/api/orders/$1/status?status=PROCESSING" -H "$AUTH" >/dev/null; }
lo_of(){ curl -s "$BASE/api/logistics/order/$1" -H "$AUTH" | python3 -c "
import sys,json
d=json.load(sys.stdin)
a=d.get('data') or [{}]
print(str(a[0].get('logisticsOrderId','')) if a else '')"; }

echo "== 2. 路径 A：PUT /orders/{id}/status?status=SHIPPED（参照实现，必须扣减）=="
OA=$(mkorder A); [ -n "$OA" ] && to_processing "$OA" || bad "A 建单失败"
curl -s -X PUT "$BASE/api/orders/$OA/status?status=SHIPPED" -H "$AUTH" >/dev/null
SA=$(stock); KA=$(statusof "$OA")
[ "$KA" = "SHIPPED" ] && ok "A 订单状态=SHIPPED" || bad "A 订单状态应为 SHIPPED，实际 $KA"
[ "$SA" = "2" ] && ok "A 余量 3→2（扣减 1 件）" || bad "A 余量应扣到 2，实际 $SA"

echo "== 3. 路径 B：POST /logistics/batch-ship（必须与 A 同结果）=="
OB=$(mkorder B); [ -n "$OB" ] && to_processing "$OB" || bad "B 建单失败"
SB0=$(stock)
BRES=$(curl -s -X POST "$BASE/api/logistics/batch-ship" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"orderIds\":[\"$OB\"],\"logisticsChannelId\":\"$CH\",\"updateOrderStatus\":true}" | python3 -c "
import sys,json
d=(json.load(sys.stdin).get('data') or {})
print('total=%s success=%s failure=%s skipped=%s' % (d.get('totalCount'),d.get('successCount'),d.get('failureCount'),d.get('skippedCount')))")
echo "       批量发货结果：$BRES"
SB=$(stock); KB=$(statusof "$OB")
[ "$KB" = "SHIPPED" ] && ok "B 订单状态=SHIPPED" || bad "B 订单状态应为 SHIPPED，实际 $KB（$BRES）"
[ "$SB" = "$((SB0-1))" ] && ok "B 余量 $SB0→$SB（扣减 1 件）" \
  || bad "B 余量应从 $SB0 扣到 $((SB0-1))，实际 $SB —— 批量发货绕开了 BR-04 出库"

echo "== 4. 路径 C：POST /logistics/{id}/ship（必须与 A 同结果）=="
OC=$(mkorder C); [ -n "$OC" ] && to_processing "$OC" || bad "C 建单失败"
LO=""; [ -n "$OC" ] && LO=$(lo_of "$OC")
[ -n "$LO" ] && ok "C 的派生物流单 $LO" || bad "C 没有派生物流单，路径 C 未被打到"
SC0=$(stock)
[ -n "$LO" ] && curl -s -X POST "$BASE/api/logistics/$LO/ship?trackingNumber=ISS072-$RUN" -H "$AUTH" >/dev/null
SC=$(stock); KC=$(statusof "$OC")
[ "$KC" = "SHIPPED" ] && ok "C 订单状态=SHIPPED" \
  || bad "C 订单状态应为 SHIPPED，实际 $KC —— 手工标记发货完全不推进订单"
[ "$SC" = "$((SC0-1))" ] && ok "C 余量 $SC0→$SC（扣减 1 件）" \
  || bad "C 余量应从 $SC0 扣到 $((SC0-1))，实际 $SC —— 端点的审计标签承诺「扣减库存」但没扣"

echo "== 5. 负例：对已发货订单重复标记发货，不得二次扣减 =="
S1=$(stock)
[ -n "$LO" ] && curl -s -X POST "$BASE/api/logistics/$LO/ship?trackingNumber=ISS072-$RUN-R" -H "$AUTH" >/dev/null
S2=$(stock)
[ "$S2" = "$S1" ] && ok "重复发货未二次扣减（余量仍为 $S2）" \
  || bad "重复发货把余量又扣了一次（$S1 → $S2）—— 扣减必须幂等"

echo "== 6. 负例：库存不足时发货必须被拒，不得留下「已发货但没出库」的半态 =="
OD=$(mkorder D); [ -n "$OD" ] && to_processing "$OD" || bad "D 建单失败"
LOD=""; [ -n "$OD" ] && LOD=$(lo_of "$OD")
RES=$(stock)
if [ "$RES" != "0" ]; then
  curl -s -X POST "$BASE/api/warehouses/$WID/inventory/out" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"sku\":\"$SKU\",\"productId\":\"$SKU\",\"warehouseId\":\"$WID\",\"quantity\":$RES,\"movementType\":\"OUTBOUND\"}" >/dev/null
fi
HD=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/logistics/$LOD/ship?trackingNumber=ISS072-$RUN-D" -H "$AUTH")
KD=$(statusof "$OD")
LD=$(lostatusof "$LOD")
echo "       库存不足时 ship 的 HTTP=$HD，订单状态=$KD，物流单状态=$LD，余量=$(stock)"
if [ "$HD" = "200" ] || [ "$KD" = "SHIPPED" ]; then
  bad "库存不足仍把发货判成成功（HTTP $HD / 状态 $KD）—— 出库失败必须阻断发货"
else
  ok "库存不足被拒且订单未推进（HTTP $HD / 状态 $KD）"
fi
if [ "$LD" = "COLLECTED" ]; then
  bad "库存不足却把物流单标成已揽收——半态：物流已揽收、订单未发货、库存未扣"
else
  ok "库存不足时物流单未被推进（状态 $LD）"
fi

echo "== 7. 清场：只删探针自己建的数据（先取引用 id，再按 FK 方向 子表 → 父表 → 孤儿）=="
IDS="'${OA:-none}','${OB:-none}','${OC:-none}','${OD:-none}'"
# orders.payment_info_id / shipping_address_id、logistics_orders.logistics_fee_id / logistics_label_id
# 都是"父表持子表外键"，所以必须在删父表行之前把这些 id 取出来，删完父表再清孤儿。
PI_IDS=$(dbq "SELECT GROUP_CONCAT(payment_info_id) FROM orders WHERE id IN ($IDS)")
SA_IDS=$(dbq "SELECT GROUP_CONCAT(shipping_address_id) FROM orders WHERE id IN ($IDS)")
FEE_IDS=$(dbq "SELECT GROUP_CONCAT(logistics_fee_id) FROM logistics_orders WHERE order_id IN ($IDS)")
LBL_IDS=$(dbq "SELECT GROUP_CONCAT(logistics_label_id) FROM logistics_orders WHERE order_id IN ($IDS)")
# BR-10：发货现在会写一条运费流水（reference_type='LOGISTICS_ORDER'，reference_id＝物流单 id）。
# 探针自己引发的副作用必须由探针复原（R-39），所以这里要在删物流单之前把 id 名单取出来。
LO_IDS=$(dbq "SELECT GROUP_CONCAT(id) FROM logistics_orders WHERE order_id IN ($IDS)")
TXN_WHERE=""
[ -n "$LO_IDS" ] && [ "$LO_IDS" != "NULL" ] && TXN_WHERE="reference_type='LOGISTICS_ORDER' AND reference_id IN ('$(printf '%s' "$LO_IDS" | sed "s/,/','/g")')"
TARGETS="$IDS,'$WID'"
[ -n "$LO" ] && TARGETS="$TARGETS,'$LO'"
[ -n "$LOD" ] && TARGETS="$TARGETS,'$LOD'"
inlist(){ printf '%s' "$1" | sed "s/,/','/g"; }
for STMT in \
  "${TXN_WHERE:+DELETE FROM financial_transactions WHERE $TXN_WHERE}" \
  "DELETE FROM audit_logs WHERE target_id IN (SELECT id FROM logistics_orders WHERE order_id IN ($IDS))" \
  "DELETE FROM audit_logs WHERE target_id IN ($TARGETS)" \
  "DELETE FROM logistics_orders WHERE order_id IN ($IDS)" \
  "DELETE FROM order_items WHERE order_id IN ($IDS)" \
  "DELETE FROM orders WHERE id IN ($IDS)"; do
  ERR=$(dbq "$STMT")
  [ -n "$ERR" ] && echo "    （清理：$ERR ← $STMT）"
done
for PAIR in "payment_infos:$PI_IDS" "shipping_addresses:$SA_IDS" "logistics_fees:$FEE_IDS" "logistics_labels:$LBL_IDS"; do
  T="${PAIR%%:*}"; V="${PAIR#*:}"
  [ -z "$V" ] || [ "$V" = "NULL" ] && continue
  ERR=$(dbq "DELETE FROM $T WHERE id IN ('$(inlist "$V")')")
  [ -n "$ERR" ] && echo "    （$T：$ERR）"
done
# ISS-142：warehouses 被 SQL 直接删掉时，指向它的采购单不会跟着走（purchase_orders.warehouse_id 是无外键
# 的普通 varchar），于是探针自己的清场把采购单掏成断链——实测 530 → 535 那 5 行就是本探针与 iss137 的
# "出库到 0 → 同步生成缺货自动单 → DELETE FROM warehouses"三步留下的。缺货单走同步事件链
# （EventHandler 用 @EventListener；MQ 那条 InventoryShortageEventConsumer 按 application.yml 是关的），
# 所以按仓 id 先删子表就能确定性地清干净；删完再复查一遍，防的是以后这条链被改成异步。
sweep_po_domain(){
  local S ERR
  for S in \
    "DELETE ft FROM financial_transactions ft JOIN purchase_orders po ON po.purchase_order_id = ft.reference_id WHERE ft.reference_type='PURCHASE_ORDER' AND po.warehouse_id='$WID'" \
    "DELETE poi FROM purchase_order_items poi JOIN purchase_orders po ON po.purchase_order_id = poi.purchase_order_id WHERE po.warehouse_id='$WID'" \
    "DELETE FROM purchase_orders WHERE warehouse_id='$WID'"; do
    ERR=$(dbq "$S")
    [ -n "$ERR" ] && echo "    （采购域清理：$ERR ← $S）"
  done
}
if [ -n "$WID" ]; then sweep_po_domain; fi
for STMT in \
  "DELETE FROM inventory_records WHERE warehouse_id='$WID'" \
  "DELETE FROM locations WHERE warehouse_id='$WID'" \
  "DELETE FROM warehouses WHERE id='$WID'"; do
  ERR=$(dbq "$STMT")
  [ -n "$ERR" ] && echo "    （清理：$ERR ← $STMT）"
done
# 常驻回归位（R-44）：仓行已经没了，此时仍指向本探针仓的采购单＝迟到的异步单，再扫一遍并判定
LEFT_PO=$(dbq "SELECT COUNT(*) FROM purchase_orders WHERE warehouse_id='$WID'")
if [ "${LEFT_PO:-0}" != "0" ]; then sweep_po_domain; LEFT_PO=$(dbq "SELECT COUNT(*) FROM purchase_orders WHERE warehouse_id='$WID'"); fi
if [ "${LEFT_PO:-?}" = "0" ]; then
  ok "ISS-142 探针仓名下无采购单残留"
else
  bad "ISS-142 探针仓名下无采购单残留：期望 0 张，实际 ${LEFT_PO} 张（删仓没带走名下采购单：采购域清理失效）"
fi
DANGLING_NOW=$(dbq "SELECT COUNT(*) FROM purchase_orders p LEFT JOIN warehouses w ON w.id = p.warehouse_id WHERE p.warehouse_id IS NOT NULL AND w.id IS NULL")
if [[ -n "$DANGLING_T0" && -n "$DANGLING_NOW" ]] && [ "$DANGLING_NOW" -le "$DANGLING_T0" ]; then
  ok "ISS-142 全库断链采购单未新增（基线 $DANGLING_T0 → 清场后 $DANGLING_NOW）"
else
  bad "ISS-142 全库断链采购单未新增：期望 ≤ 基线 ${DANGLING_T0:-取数失败}，实际 ${DANGLING_NOW:-取数失败}"
fi
LEFT_O=$(dbq "SELECT COUNT(*) FROM orders WHERE order_number LIKE 'ISS072%$RUN'")
LEFT_W=$(dbq "SELECT COUNT(*) FROM warehouses WHERE warehouse_code='ISS072-$RUN'")
LEFT_I=$(dbq "SELECT COUNT(*) FROM inventory_records WHERE sku_code='$SKU'")
LEFT_L=$(dbq "SELECT COUNT(*) FROM logistics_orders WHERE order_id IN ($IDS)")
LEFT_TXN=$([ -n "$TXN_WHERE" ] && dbq "SELECT COUNT(*) FROM financial_transactions WHERE $TXN_WHERE" || echo 0)
PHANTOM=$(dbq "SELECT COUNT(*) FROM orders WHERE tenant_id='default-tenant' OR tenant_id='' OR tenant_id IS NULL")
if [ "$LEFT_O" = "0" ] && [ "$LEFT_W" = "0" ] && [ "$LEFT_I" = "0" ] && [ "$LEFT_L" = "0" ] && [ "${LEFT_TXN:-0}" = "0" ]; then
  ok "探针数据已清空（orders=$LEFT_O warehouses=$LEFT_W inventory=$LEFT_I logistics=$LEFT_L 运费流水=$LEFT_TXN，全库幻影订单=$PHANTOM）"
else
  bad "残留 orders=$LEFT_O warehouses=$LEFT_W inventory=$LEFT_I logistics=$LEFT_L 运费流水=${LEFT_TXN:-查询失败}"
fi

echo
echo "结果：$P 通过 / $F 失败"
[ "$F" = "0" ] || exit 1
