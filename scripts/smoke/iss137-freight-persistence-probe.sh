#!/usr/bin/env bash
# ISS-137 探针：运费必须在「创建物流单」时按渠道计费口径真实落库，三条创建路径同结果，且发货不重算。
#
# 修复前的实况（证据）：logistics_fees 全表 0 行、logistics_orders.logistics_fee_id 全 NULL，
# 唯一写 setLogisticsFee 的调用方是「平台导入回传」，正常业务链路从不写 ⇒
# 前端物流详情页「运费」列永远是 '-'，spec 里 BR-04「运费预估」只剩试算端点那一半。
#
# 三条创建路径（R-55：只在创建时预估，发货/打单/状态推进不重算不回填）：
#   A) POST /api/logistics                       —— 手工建单，用探针渠道（费率自己定 ⇒ 期望值可逐位硬编码）
#   B) 下单事件自动建单（DEFAULT_CHANNEL）        —— EventHandler.handleOrderCreatedEvent
#   C) POST /api/logistics/batch-ship            —— 批量发货自建/复用物流单
#
# 判定口径（回读副作用，不看 HTTP 码）：
#   ① 响应、二次 GET 回读、DB 行三处逐位一致（DECIMAL(19,2) 写读链路上的 8.40/5.00/13.40）；
#   ② 试算端点 GET /logistics-channels/{id}/fee 与建单落库同值 —— 证明「全仓唯一公式」在运行时成立；
#   ③ 负例：ship 之后运费三值不变、该订单的 logistics_fees 行数不增加（不得重算/重复落库）；
#   ④ 负例：SKU 无重量 ⇒ 只收首重（weightFee 0.00）且建单不得因此被拒；
#   ⑤ 单位陷阱：0.0050 × 1000 克 = 5.00。若误用面单包裹重（double，单位 kg）当计费重，
#      这里会得到 0.01 —— 所以 5.00 逐位相等本身就是「按克计费」的证据。
# 探针自建的商品/SKU/仓/渠道/订单由探针自己删除（R-39：只认自己的 skuCode / warehouseCode / channelCode）。
set -uo pipefail
BASE=http://localhost:8082
RUN="$(date +%s)$$"
CH="CHAN-ISS137-$RUN"
SKU="ISS137-SKU-$RUN"
NOWSKU="ISS137-NOHW-$RUN"
P=0; F=0
ok(){ echo "  ✅ $1"; P=$((P+1)); }
bad(){ echo "  ❌ $1"; F=$((F+1)); }
# 合并 stderr：MySQL 报错必须被看见（hygiene：mysql -e 从首条失败语句起整批不执行）
dbq(){ docker exec mysql mysql -uroot -p123456 -N -D ecommerce_logistics_erp -e "$1" 2>&1 | grep -v "Using a password"; }
# ISS-142 基线：开跑前全库"指向不存在的仓"的采购单行数，收尾拿它比对（见脚本尾部清场段）
DANGLING_T0=$(dbq "SELECT COUNT(*) FROM purchase_orders p LEFT JOIN warehouses w ON w.id = p.warehouse_id WHERE p.warehouse_id IS NOT NULL AND w.id IS NULL")

TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("token",""))')
[ -n "$TOKEN" ] || { echo "登录失败，探针无法执行"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"
JH='Content-Type: application/json'

echo "== 1. 建探针渠道（首重 8.40 + 每克 0.0050）与两个探针商品（500g / 无重量）=="
CHID=$(curl -s -X POST $BASE/api/logistics-channels -H "$AUTH" -H "$JH" \
  -d "{\"channelCode\":\"$CH\",\"channelName\":\"ISS137探针渠道-$RUN\",\"description\":\"ISS-137\",\"countries\":[\"CN\"],\"basePrice\":8.40,\"pricePerGram\":0.0050,\"currency\":\"CNY\",\"active\":true}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("channelId",""))')
[ -n "$CHID" ] && ok "探针渠道 $CHID" || { bad "创建探针渠道失败"; exit 1; }

mkproduct(){ curl -s -X POST $BASE/api/products -H "$AUTH" -H "$JH" -d "{
  \"name\":\"ISS137探针商品$1-$RUN\",\"description\":\"ISS-137\",\"categoryName\":\"ISS137\",\"active\":true,
  \"skus\":[{\"skuCode\":\"$2\",\"skuName\":\"ISS137 SKU$1\",\"sellingPrice\":9.90,\"currency\":\"CNY\"$3}]}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("id",""))'; }
PROD=$(mkproduct W "$SKU" ',"weightInGrams":500')
[ -n "$PROD" ] && ok "带重量商品 $PROD（SKU $SKU = 500 克）" || { bad "创建带重量商品失败"; exit 1; }
NOPROD=$(mkproduct N "$NOWSKU" '')
[ -n "$NOPROD" ] && ok "无重量商品 $NOPROD（SKU $NOWSKU weight=NULL）" || bad "创建无重量商品失败"

WID=$(curl -s -X POST $BASE/api/warehouses -H "$AUTH" -H "$JH" \
  -d "{\"warehouseName\":\"ISS137探针仓-$RUN\",\"warehouseCode\":\"ISS137-$RUN\",\"address\":\"探针地址\",\"capacity\":100}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("warehouseId",""))')
[ -n "$WID" ] && ok "探针仓 $WID" || bad "创建探针仓失败（路径 C 需库存才能出库）"
[ -n "$WID" ] && curl -s -X POST "$BASE/api/warehouses/$WID/inventory/in" -H "$AUTH" -H "$JH" \
  -d "{\"sku\":\"$SKU\",\"productId\":\"$SKU\",\"warehouseId\":\"$WID\",\"quantity\":10,\"movementType\":\"INBOUND\"}" >/dev/null

# 期望值：探针渠道 8.40 + 0.0050×(500g×2)=5.00 ⇒ 13.40
EXPECT_A="8.40|5.00|13.40|CNY"

mkorder(){ curl -s -X POST $BASE/api/orders -H "$AUTH" -H "$JH" -d "{
  \"orderNumber\":\"ISS137$1$RUN\",\"customerId\":\"ISS137-CUST-$1\",\"customerName\":\"ISS137 客户\",
  \"customerEmail\":\"iss137$1@example.com\",
  \"orderItems\":[{\"productName\":\"ISS137 商品\",\"skuCode\":\"$2\",\"quantity\":$3,\"unitPrice\":9.90,\"totalPrice\":9.90}],
  \"paymentInfo\":{\"amount\":9.90,\"paymentMethod\":\"CREDIT_CARD\",\"paymentStatus\":\"PAID\",\"transactionId\":\"ISS137$1-$RUN\"},
  \"shippingAddress\":{\"country\":\"CN\",\"province\":\"广东\",\"city\":\"深圳\",\"street\":\"探针路 1 号\",\"recipient\":\"探针收件人\",\"phone\":\"13900000000\",\"zipCode\":\"518000\"}}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("orderId",""))'; }

# 从物流单 DTO 取运费四元组（缺失按空串，逐位比较）
# parse_float=Decimal 是必需的：默认 json 把 8.40 解析成 float 8.4，逐位断言会被解析器先抹掉尾零
feeofo(){ curl -s "$BASE/api/logistics/$1" -H "$AUTH" | python3 -c "
import sys,json
from decimal import Decimal
d=(json.load(sys.stdin,parse_float=Decimal).get('data') or {})
g=lambda k:('' if d.get(k) is None else str(d.get(k)))
print('%s|%s|%s|%s' % (g('baseFee'),g('weightFee'),g('totalFee'),g('currency')))"; }
# DB 侧同一单的四元组（DECIMAL 列原样输出）
dbfeeofo(){ dbq "SELECT CONCAT(IFNULL(f.base_fee,'~'),'|',IFNULL(f.weight_fee,'~'),'|',IFNULL(f.total_fee,'~'),'|',IFNULL(f.currency,'~'))
  FROM logistics_orders l JOIN logistics_fees f ON f.id=l.logistics_fee_id WHERE l.id='$1';"; }
loof(){ curl -s "$BASE/api/logistics/order/$1" -H "$AUTH" | python3 -c "
import sys,json
a=(json.load(sys.stdin).get('data') or [])
print(str(a[0].get('logisticsOrderId')) if a else '')"; }
feeidof(){ dbq "SELECT IFNULL(logistics_fee_id,'NULL') FROM logistics_orders WHERE id='$1';"; }
chrate(){ dbq "SELECT CONCAT(base_price,'|',price_per_gram) FROM logistics_channels WHERE id='$1';"; }
# 建一次物流单，一次拿到 "id|baseFee|weightFee|totalFee|currency"（只发一次请求，避免同单多张物流单干扰计数）
mklogistics(){ curl -s -X POST $BASE/api/logistics -H "$AUTH" -H "$JH" -d "{\"orderId\":\"$1\",\"channelCode\":\"$2\"}" | python3 -c "
import sys,json
from decimal import Decimal
d=(json.load(sys.stdin,parse_float=Decimal).get('data') or {})
g=lambda k:('' if d.get(k) is None else str(d.get(k)))
print('%s|%s|%s|%s|%s' % (g('logisticsOrderId'),g('baseFee'),g('weightFee'),g('totalFee'),g('currency')))"; }

echo "== 2. 路径 A：POST /api/logistics（探针渠道，1000 克 ⇒ 8.40 + 5.00 = 13.40）=="
OA=$(mkorder A "$SKU" 2); [ -n "$OA" ] || { bad "A 建单失败"; exit 1; }
LINEA=$(mklogistics "$OA" "$CH")
LOA=$(printf '%s' "$LINEA" | cut -d'|' -f1)
RA=$(printf '%s' "$LINEA" | cut -d'|' -f2-)
[ "$RA" = "$EXPECT_A" ] && ok "A 建单响应逐位 $EXPECT_A" || bad "A 建单响应应为 $EXPECT_A，实际 ${RA:-（空，HTTP 层就失败了）}"
if [ -n "$LOA" ]; then
  ok "A 物流单 $LOA"
  GA=$(feeofo "$LOA")
  [ "$GA" = "$EXPECT_A" ] && ok "A 回读 GET 逐位 $EXPECT_A（BigDecimal→JSON 无精度损失）" || bad "A 回读应为 $EXPECT_A，实际 $GA"
  DA=$(dbfeeofo "$LOA")
  [ "$DA" = "$EXPECT_A" ] && ok "A 落库 logistics_fees 逐位 $EXPECT_A（DECIMAL(19,2) 写读一致）" || bad "A 落库应为 $EXPECT_A，实际 ${DA:-查不到费用行}"
  FIDA=$(feeidof "$LOA")
  [ "$FIDA" != "NULL" ] && [ -n "$FIDA" ] && ok "A logistics_fee_id 已回填（$FIDA）" || bad "A logistics_fee_id 仍为 NULL——运费没和外键接上"
  # 单位陷阱证据：若把面单包裹重（kg）当克数，1000g 会得到 weightFee 0.01 而不是 5.00
  case "$RA" in *"|5.00|"*) ok "A 计费重按克（5.00 = 0.0050×1000g；误用 kg 会是 0.01）" ;;
    *) bad "A 计费重不是按克（weightFee 应为 5.00，实际 $(printf '%s' "$RA" | cut -d'|' -f2)）" ;; esac
else
  bad "A 未拿到物流单 id，跳过回读断言"; bad "A 未拿到物流单 id，跳过落库断言"
  bad "A 未拿到物流单 id，跳过外键断言"; bad "A 未拿到物流单 id，跳过单位断言"
fi

echo "== 3. 试算端点与建单落库同值（全仓唯一公式的运行时证据）=="
TRIAL=$(curl -s "$BASE/api/logistics-channels/$CHID/fee?weight=1000&country=CN" -H "$AUTH" | python3 -c "
import sys,json
from decimal import Decimal
d=(json.load(sys.stdin,parse_float=Decimal).get('data') or {})
g=lambda k:('' if d.get(k) is None else str(d.get(k)))
print('%s|%s|%s|%s' % (g('baseFee'),g('weightFee'),g('totalFee'),g('currency')))")
[ "$TRIAL" = "$EXPECT_A" ] && ok "试算 GET 逐位 $EXPECT_A，与建单落库同一口径" || bad "试算应为 $EXPECT_A，实际 $TRIAL —— 两处公式已经分叉"

echo "== 4. 路径 B：下单事件自动建单（DEFAULT_CHANNEL，按该渠道在库里的真实费率独立算期望）=="
# 渠道三列 -> 「base|rate|cur」，期望值由探针按 spec 口径（首重 + 每克×克数，2 位 HALF_UP）独立算出
chcols(){ dbq "SELECT CONCAT(c.base_price,'|',c.price_per_gram,'|',c.currency) FROM logistics_orders l JOIN logistics_channels c ON c.id=l.logistics_channel_id WHERE l.id='$1';"; }
expectfee(){ python3 -c "
from decimal import Decimal, ROUND_HALF_UP
b,r,cur=('${1:-0|0|CNY}'.split('|')+['CNY'])[:3]
b,r=[Decimal(b),Decimal(r)]; w=Decimal($2)
q=lambda x: str(x.quantize(Decimal('0.01'),ROUND_HALF_UP))
print('%s|%s|%s|%s' % (q(b),q(r*w),q(b+r*w),cur))"; }
OB=$(mkorder B "$SKU" 3); [ -n "$OB" ] || bad "B 建单失败"
LOB=$(loof "$OB")
[ -n "$LOB" ] && ok "B 自动派生物流单 $LOB" || bad "B 没有自动派生物流单——事件链路未接通"
if [ -n "$LOB" ]; then
  CHROW=$(chcols "$LOB"); EXPB=$(expectfee "$CHROW" $((500*3))); DBB=$(dbfeeofo "$LOB")
  echo "       B 渠道 $CHROW，期望 $EXPB，落库 ${DBB:-无}"
  [ "$DBB" = "$EXPB" ] && ok "B 自动建单按渠道口径落库 $EXPB（500g×3 件）" || bad "B 自动建单落库应为 $EXPB，实际 ${DBB:-无费用行}"
  FIDB=$(feeidof "$LOB")
  [ "$FIDB" != "NULL" ] && [ -n "$FIDB" ] && ok "B logistics_fee_id 已回填（$FIDB）" || bad "B logistics_fee_id 仍为 NULL"
else
  bad "B 无物流单，跳过落库断言"; bad "B 无物流单，跳过外键断言"
fi

echo "== 5. 路径 C：POST /logistics/batch-ship 自建物流单（第三个调用点）=="
OC=$(mkorder C "$SKU" 1); [ -n "$OC" ] && curl -s -X PUT "$BASE/api/orders/$OC/status?status=PROCESSING" -H "$AUTH" >/dev/null || bad "C 建单失败"
# 下单事件已经自动派生过一张 PENDING 单，批量发货会复用它 ⇒ 打到的是「已有费用不覆盖」分支。
# 为了真正打到 shipSingle 里的自建分支，先把那张自动单连同它的费用行摘掉（探针自建，自己删）。
LOC0=$(loof "$OC")
if [ -n "$LOC0" ]; then
  LFEE0=$(dbq "SELECT IFNULL(logistics_fee_id,'') FROM logistics_orders WHERE id='$LOC0';")
  dbq "DELETE FROM logistics_orders WHERE id='$LOC0'" >/dev/null
  [ -n "$LFEE0" ] && dbq "DELETE FROM logistics_fees WHERE id='$LFEE0'" >/dev/null
  ok "已摘掉自动派生的 $LOC0 以便批量发货自建"
fi
BRES=$(curl -s -X POST "$BASE/api/logistics/batch-ship" -H "$AUTH" -H "$JH" \
  -d "{\"orderIds\":[\"$OC\"],\"logisticsChannelId\":\"$CHID\",\"updateOrderStatus\":true}" | python3 -c "
import sys,json
d=(json.load(sys.stdin).get('data') or {})
print('total=%s success=%s failure=%s skipped=%s' % (d.get('totalCount'),d.get('successCount'),d.get('failureCount'),d.get('skippedCount')))")
echo "       批量发货结果：$BRES"
LOC=$(loof "$OC")
if [ -n "$LOC" ]; then
  CHROWC=$(chcols "$LOC"); EXPC=$(expectfee "$CHROWC" $((500*1))); DBC=$(dbfeeofo "$LOC")
  echo "       C 批量发货新建物流单 $LOC 渠道 $CHROWC，期望 $EXPC，落库 ${DBC:-无}"
  case "$CHROWC" in "8.40|0.0050|CNY"*) ok "C 自建单用的就是探针渠道（请求里的 logisticsChannelId 生效）" ;;
    *) bad "C 自建单没用探针渠道，实际渠道费率行为 $CHROWC" ;; esac
  [ "$DBC" = "$EXPC" ] && ok "C 批量发货自建单按渠道口径落库 $EXPC（500g×1 件）" || bad "C 自建单落库应为 $EXPC，实际 ${DBC:-无费用行}"
else
  bad "C 批量发货后仍没有物流单，路径 C 未被打到：$BRES"
  bad "C 无物流单，跳过探针渠道断言"
fi

echo "== 6. 负例：ship 之后不得重算运费、不得多落一行 =="
if [ -n "$LOA" ]; then
  N0=$(dbq "SELECT COUNT(*) FROM logistics_fees;")
  FID0=$(feeidof "$LOA")
  curl -s -X POST "$BASE/api/logistics/$LOA/ship?trackingNumber=ISS137-$RUN-S" -H "$AUTH" >/dev/null
  G1=$(feeofo "$LOA")
  N1=$(dbq "SELECT COUNT(*) FROM logistics_fees;")
  FID1=$(feeidof "$LOA")
  [ "$G1" = "$EXPECT_A" ] && ok "发货后运费仍为 $EXPECT_A（渠道改价不拖动历史运费）" || bad "发货把运费改了：期望 $EXPECT_A，实际 $G1"
  if [ "$N1" = "$N0" ] && [ "$FID1" = "$FID0" ] && [ -n "$FID0" ]; then
    ok "发货后全库费用行数不变（$N0）且外键仍指原行 $FID0（未重算/未重复落库）"
  else
    bad "发货后费用行数 $N0 → $N1，外键 $FID0 → $FID1（期望都不变）"
  fi
else
  bad "无 A 物流单，跳过重算负例"; bad "无 A 物流单，跳过行数负例"
fi

echo "== 7. 负例：SKU 无重量 ⇒ 只收首重，且建单不得被拒 =="
OD=$(mkorder D "$NOWSKU" 1); [ -n "$OD" ] || bad "D 建单失败"
HTTPD=$(curl -s -o /dev/null -w '%{http_code}' -X POST $BASE/api/logistics -H "$AUTH" -H "$JH" -d "{\"orderId\":\"$OD\",\"channelCode\":\"$CH\"}")
[ "$HTTPD" = "200" ] && ok "无重量 SKU 建单仍 HTTP 200（没有新增拒绝路径）" || bad "无重量 SKU 建单被拒（HTTP $HTTPD）"
DBD=""; LOD=""
if [ "$HTTPD" = "200" ]; then
  # 上一步只取 HTTP 码；按渠道 id 回查那张单，不再重发建单请求（避免同单两张物流单）
  LOD=$(dbq "SELECT id FROM logistics_orders WHERE order_id='$OD' AND logistics_channel_id='$CHID' LIMIT 1;")
  [ -n "$LOD" ] && ok "D 物流单 $LOD" || bad "D 建单返回 200 却查不到物流单"
  DBD=$(dbfeeofo "$LOD")
fi
[ "$DBD" = "8.40|0.00|8.40|CNY" ] && ok "无重量按 0 克只收首重：$DBD" || bad "无重量期望 8.40|0.00|8.40|CNY，实际 ${DBD:-无}"

echo "== 8. 清场：只删探针自己建的数据（先取引用 id，再按 FK 方向 子表 → 父表 → 孤儿）=="
IDS="'${OA:-none}','${OB:-none}','${OC:-none}','${OD:-none}'"
PI_IDS=$(dbq "SELECT GROUP_CONCAT(payment_info_id) FROM orders WHERE id IN ($IDS)")
SA_IDS=$(dbq "SELECT GROUP_CONCAT(shipping_address_id) FROM orders WHERE id IN ($IDS)")
FEE_IDS=$(dbq "SELECT GROUP_CONCAT(logistics_fee_id) FROM logistics_orders WHERE order_id IN ($IDS)")
LBL_IDS=$(dbq "SELECT GROUP_CONCAT(logistics_label_id) FROM logistics_orders WHERE order_id IN ($IDS)")
LO_IDS=$(dbq "SELECT GROUP_CONCAT(id) FROM logistics_orders WHERE order_id IN ($IDS)")
TARGETS="$IDS,'$CHID','$PROD','$NOPROD'"
[ -n "$LO_IDS" ] && [ "$LO_IDS" != "NULL" ] && TARGETS="$TARGETS,'$(printf '%s' "$LO_IDS" | sed "s/,/','/g")'"
inlist(){ printf '%s' "$1" | sed "s/,/','/g"; }
# BR-10 之后「发货」会写一条运费流水（§6 的 ship 负例就会打到这里）。
# 流水没有外键指向物流单（R-05 的缺索引同款），删物流单行不会带走它 ⇒ 探针自己引发的副作用必须由探针复原（R-39）。
TXN_WHERE=""
[ -n "$LO_IDS" ] && [ "$LO_IDS" != "NULL" ] && TXN_WHERE="reference_type='LOGISTICS_ORDER' AND reference_id IN ('$(inlist "$LO_IDS")')"
for STMT in \
  "${TXN_WHERE:+DELETE FROM financial_transactions WHERE $TXN_WHERE}" \
  "DELETE FROM audit_logs WHERE target_id IN ($TARGETS)" \
  "DELETE FROM logistics_orders WHERE order_id IN ($IDS)" \
  "DELETE FROM order_items WHERE order_id IN ($IDS)" \
  "DELETE FROM orders WHERE id IN ($IDS)"; do
  ERR=$(dbq "$STMT"); [ -n "$ERR" ] && echo "    （清理：$ERR ← $STMT）"
done
for PAIR in "payment_infos:$PI_IDS" "shipping_addresses:$SA_IDS" "logistics_fees:$FEE_IDS" "logistics_labels:$LBL_IDS"; do
  T="${PAIR%%:*}"; V="${PAIR#*:}"
  [ -z "$V" ] || [ "$V" = "NULL" ] && continue
  ERR=$(dbq "DELETE FROM $T WHERE id IN ('$(inlist "$V")')")
  [ -n "$ERR" ] && echo "    （$T：$ERR）"
done
# ISS-142：warehouses 被 SQL 直接删掉时，指向它的采购单不会跟着走（purchase_orders.warehouse_id 是无外键
# 的普通 varchar），于是探针自己的清场把采购单掏成断链——实测 530 → 535 那 5 行就是本探针与 iss072 的
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
if [ -n "$WID" ]; then
  for STMT in \
    "DELETE FROM inventory_records WHERE warehouse_id='$WID'" \
    "DELETE FROM locations WHERE warehouse_id='$WID'" \
    "DELETE FROM warehouses WHERE id='$WID'"; do
    ERR=$(dbq "$STMT"); [ -n "$ERR" ] && echo "    （清理：$ERR ← $STMT）"
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
fi
for STMT in \
  "DELETE FROM skus WHERE product_id IN ('$PROD','${NOPROD:-none}')" \
  "DELETE FROM products WHERE id IN ('$PROD','${NOPROD:-none}')" \
  "DELETE FROM logistics_channels WHERE id='$CHID'"; do
  ERR=$(dbq "$STMT"); [ -n "$ERR" ] && echo "    （清理：$ERR ← $STMT）"
done
LEFT_O=$(dbq "SELECT COUNT(*) FROM orders WHERE order_number LIKE 'ISS137%$RUN'")
LEFT_L=$(dbq "SELECT COUNT(*) FROM logistics_orders WHERE order_id IN ($IDS)")
LEFT_F=$(dbq "SELECT COUNT(*) FROM logistics_orders WHERE order_id IN ($IDS) AND logistics_fee_id IS NOT NULL")
# 孤儿费用行：所有 logistics_fees 都必须被某张物流单引用（探针自清场，也给 ISS-067 单持久化留证据）
ORPHAN_F=$(dbq "SELECT COUNT(*) FROM logistics_fees f LEFT JOIN logistics_orders l ON l.logistics_fee_id=f.id WHERE l.id IS NULL")
LEFT_P=$(dbq "SELECT COUNT(*) FROM products WHERE name LIKE 'ISS137探针商品%'")
LEFT_S=$(dbq "SELECT COUNT(*) FROM skus WHERE sku_code IN ('$SKU','$NOWSKU')")
LEFT_C=$(dbq "SELECT COUNT(*) FROM logistics_channels WHERE channel_code='$CH'")
LEFT_W=$(dbq "SELECT COUNT(*) FROM warehouses WHERE warehouse_code='ISS137-$RUN'")
# 本探针留下的运费流水 + 全库孤儿流水（运费流水只由物流单产生，指向已不存在的物流单即残留）
LEFT_T=$([ -n "$TXN_WHERE" ] && dbq "SELECT COUNT(*) FROM financial_transactions WHERE $TXN_WHERE" || echo 0)
# 孤儿流水巡检要放过 status='cancelled'：ISS-139 之后"删物流单"会把它的应付流水置成已取消留痕，
# 那是应用有意保留的审计行，不是残留（已确认 SUCCESS 的孤儿则由应用侧拒绝删除挡掉）。
ORPHAN_T=$(dbq "SELECT COUNT(*) FROM financial_transactions t WHERE t.reference_type='LOGISTICS_ORDER' AND t.reference_id NOT IN (SELECT id FROM logistics_orders) AND t.status <> 'cancelled'")
if [ "$LEFT_O" = "0" ] && [ "$LEFT_L" = "0" ] && [ "$LEFT_F" = "0" ] && [ "$ORPHAN_F" = "0" ] && [ "$LEFT_P" = "0" ] && [ "$LEFT_S" = "0" ] && [ "$LEFT_C" = "0" ] && [ "$LEFT_W" = "0" ] && [ "${LEFT_T:-0}" = "0" ] && [ "$ORPHAN_T" = "0" ]; then
  ok "探针数据已清空（orders=$LEFT_O logistics=$LEFT_L 挂费用的单=$LEFT_F 全库孤儿费用行=$ORPHAN_F products=$LEFT_P skus=$LEFT_S channels=$LEFT_C warehouses=$LEFT_W 运费流水=$LEFT_T 全库孤儿流水=$ORPHAN_T）"
else
  bad "残留 orders=$LEFT_O logistics=$LEFT_L 挂费用的单=$LEFT_F 孤儿费用行=$ORPHAN_F products=$LEFT_P skus=$LEFT_S channels=$LEFT_C warehouses=$LEFT_W 运费流水=${LEFT_T:-?} 孤儿流水=$ORPHAN_T"
fi

echo
echo "结果：$P 通过 / $F 失败"
[ "$F" = "0" ] || exit 1
