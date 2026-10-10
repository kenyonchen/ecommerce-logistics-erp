#!/usr/bin/env bash
# BR-10 探针（ISS-138）：发货必须把已落库的运费记成一条财务应付流水，四个入账门都要真跑到，且不重复记。
#
# 修复前的实况（证据）：logistics_fees 有行（ISS-137 已修）但 financial_transactions 里
# 没有任何物流来源的流水 —— LogisticsCompletedEventConsumer:27-32 只打一行"记账规则未定义"的日志，
# spec/logistics.md 的 BR-10「运费入账与财务流水联动」一直挂 ⚪。
# 表现是"台账少一行"：接口全 200、页面全正常，人眼看不出来，所以必须回读 DB 与财务列表接口。
#
# 四个入账门（R-58，与 LogisticsApplicationService 里的 postFreightExpense 调用点一一对应）：
#   A) POST /api/logistics/{id}/ship        —— 手工揽收（同时回写订单 SHIPPED + 出库）
#   B) POST /api/logistics/batch-ship       —— 批量发货（复用自动派生的 PENDING 单那条分支）
#   C) POST /api/logistics + shipImmediately —— 建单即发货（ISS-072 之后必须同语义）
#   D) PUT  /api/logistics/{id}/status       —— 状态推进到"货已交出"（已揽收/运输中/派送中/已送达/配送失败）
# 三个不入账的负例（同样要真跑，否则"只预估不入账"这条口径守不住）：
#   E) 建单不发货                            —— 只写 logistics_fees，不写流水
#   F) 状态推进到「已打单」LABEL_GENERATED    —— 货没交出去
#   G) 历史无费用行的单据发货                 —— 宁可台账少一行，也不凭猜测编金额
#   H) 只改订单状态 PUT /orders/{id}/status   —— 物流单没记录揽收事实，不入账（R-58 明写的口径）
# 一条撤销门（ISS-139 / R-59，"发货有门就要有对应的撤门"）：
#   I1) 挂账中（pending）的运费流水 + API 删物流单 —— 单据可删，分录只能置 cancelled 不能删行，
#       且删完不能再留下"指向不存在的单、却还能付款/能对账"的活应付
#   I2) 已过账（success）的运费流水 + API 删物流单 —— 已过账的分录不能凭空消失，
#       必须回业务拒绝（HTTP 400 + 可读原因）且单据一张都没删（不许半态）
#
# 判定口径：金额逐位（DECIMAL(19,2) 写读链路上 8.40/5.00/13.40）、类型 EXPENSE、
# reference_type='LOGISTICS_ORDER'、reference_id＝物流单 id、租户与物流单同租户（ISS-118 口径）、
# 状态 PENDING（入账即挂账，待对账后 complete）、备注含物流单号与费用构成（可追溯）；
# 幂等用"同一张单再发一次货 + 再推一次状态"实测，流水行数必须仍为 1。
# 探针自建的商品/SKU/仓/渠道/订单/流水由探针自己删除（R-39）。
set -uo pipefail
BASE=http://localhost:8082
RUN="$(date +%s)$$"
CH="CHAN-BR10-$RUN"
SKU="BR10-SKU-$RUN"
P=0; F=0
ok(){ echo "  ✅ $1"; P=$((P+1)); }
bad(){ echo "  ❌ $1"; F=$((F+1)); }
# 合并 stderr：MySQL 报错必须被看见（hygiene ⑰：mysql -e 从首条失败语句起整批不执行）
dbq(){ docker exec mysql mysql -uroot -p123456 -N -D ecommerce_logistics_erp -e "$1" 2>&1 | grep -v "Using a password"; }
# ISS-142 基线：开跑前全库"指向不存在的仓"的采购单行数，收尾拿它比对（见脚本尾部清场段）
DANGLING_T0=$(dbq "SELECT COUNT(*) FROM purchase_orders p LEFT JOIN warehouses w ON w.id = p.warehouse_id WHERE p.warehouse_id IS NOT NULL AND w.id IS NULL")

TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("token",""))')
[ -n "$TOKEN" ] || { echo "登录失败，探针无法执行"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"
JH='Content-Type: application/json'

echo "== 1. 建探针渠道（首重 8.40 + 每克 0.0050）、500 克商品与探针仓（库存 20）=="
CHID=$(curl -s -X POST $BASE/api/logistics-channels -H "$AUTH" -H "$JH" \
  -d "{\"channelCode\":\"$CH\",\"channelName\":\"BR10探针渠道-$RUN\",\"description\":\"ISS-138\",\"countries\":[\"CN\"],\"basePrice\":8.40,\"pricePerGram\":0.0050,\"currency\":\"CNY\",\"active\":true}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("channelId",""))')
[ -n "$CHID" ] && ok "探针渠道 $CHID" || { bad "创建探针渠道失败"; exit 1; }
PROD=$(curl -s -X POST $BASE/api/products -H "$AUTH" -H "$JH" -d "{
  \"name\":\"BR10探针商品-$RUN\",\"description\":\"ISS-138\",\"categoryName\":\"BR10\",\"active\":true,
  \"skus\":[{\"skuCode\":\"$SKU\",\"skuName\":\"BR10 SKU\",\"sellingPrice\":9.90,\"currency\":\"CNY\",\"weightInGrams\":500}]}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("id",""))')
[ -n "$PROD" ] && ok "带重量商品 $PROD（SKU $SKU = 500 克）" || { bad "创建探针商品失败"; exit 1; }
WID=$(curl -s -X POST $BASE/api/warehouses -H "$AUTH" -H "$JH" \
  -d "{\"warehouseName\":\"BR10探针仓-$RUN\",\"warehouseCode\":\"BR10-$RUN\",\"address\":\"探针地址\",\"capacity\":100}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("warehouseId",""))')
[ -n "$WID" ] && ok "探针仓 $WID" || { bad "创建探针仓失败（发货要出库）"; exit 1; }
curl -s -X POST "$BASE/api/warehouses/$WID/inventory/in" -H "$AUTH" -H "$JH" \
  -d "{\"sku\":\"$SKU\",\"productId\":\"$SKU\",\"warehouseId\":\"$WID\",\"quantity\":20,\"movementType\":\"INBOUND\"}" >/dev/null
STOCK0=$(dbq "SELECT IFNULL(SUM(quantity),0) FROM inventory_records WHERE sku_code='$SKU'")
[ "$STOCK0" != "0" ] && ok "探针仓已备货（$STOCK0 件）" || bad "备货失败，发货会被库存不足拒掉"

# 期望值：8.40 + 0.0050×(500g×n)，两位 HALF_UP（与 iss137 同一口径）
expectfee(){ python3 -c "
from decimal import Decimal, ROUND_HALF_UP
q=lambda x: str(x.quantize(Decimal('0.01'),ROUND_HALF_UP))
b=Decimal('8.40'); r=Decimal('0.0050'); w=Decimal($1)
print('%s|%s|%s' % (q(b),q(r*w),q(b+r*w)))"; }

mkorder(){ curl -s -X POST $BASE/api/orders -H "$AUTH" -H "$JH" -d "{
  \"orderNumber\":\"BR10$1$RUN\",\"customerId\":\"BR10-CUST-$1\",\"customerName\":\"BR10 客户\",
  \"customerEmail\":\"br10$1@example.com\",
  \"orderItems\":[{\"productName\":\"BR10 商品\",\"skuCode\":\"$SKU\",\"quantity\":$2,\"unitPrice\":9.90,\"totalPrice\":9.90}],
  \"paymentInfo\":{\"amount\":9.90,\"paymentMethod\":\"CREDIT_CARD\",\"paymentStatus\":\"PAID\",\"transactionId\":\"BR10$1-$RUN\"},
  \"shippingAddress\":{\"country\":\"CN\",\"province\":\"广东\",\"city\":\"深圳\",\"street\":\"探针路 1 号\",\"recipient\":\"探针收件人\",\"phone\":\"13900000000\",\"zipCode\":\"518000\"}}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("orderId",""))'; }
# 按探针渠道取那张物流单（下单事件会先自动派生一张 DEFAULT_CHANNEL 的单，不能只取第一条）
lofoc(){ dbq "SELECT id FROM logistics_orders WHERE order_id='$1' AND logistics_channel_id='$CHID' LIMIT 1;"; }
loauto(){ dbq "SELECT id FROM logistics_orders WHERE order_id='$1' AND logistics_channel_id!='$CHID' LIMIT 1;"; }
# 流水五元组「条数|金额|币种|状态|租户」（逐位比较；无行时为空）
txnof(){ dbq "SELECT CONCAT(COUNT(*),'|',IFNULL(MAX(t.amount),'-'),'|',IFNULL(MAX(t.currency),'-'),'|',IFNULL(MAX(t.status),'-'),'|',IFNULL(MAX(t.tenant_id),'-'))
  FROM financial_transactions t WHERE t.reference_type='LOGISTICS_ORDER' AND t.reference_id='$1';"; }
txnso(){ dbq "SELECT COUNT(*) FROM financial_transactions WHERE reference_type='LOGISTICS_ORDER' AND reference_id='$1';"; }
txndesc(){ dbq "SELECT IFNULL(MAX(description),'-') FROM financial_transactions WHERE reference_type='LOGISTICS_ORDER' AND reference_id='$1';"; }
lodesc(){ dbq "SELECT CONCAT(tenant_id,'|',IFNULL(logistics_order_number,'-')) FROM logistics_orders WHERE id='$1';"; }
feerow(){ dbq "SELECT COUNT(*) FROM logistics_orders WHERE id='$1' AND logistics_fee_id IS NOT NULL;"; }
mklogistics(){ curl -s -X POST $BASE/api/logistics -H "$AUTH" -H "$JH" -d "$1" | python3 -c "
import sys,json
from decimal import Decimal
d=(json.load(sys.stdin,parse_float=Decimal).get('data') or {})
g=lambda k:('' if d.get(k) is None else str(d.get(k)))
print('%s|%s|%s|%s|%s' % (g('logisticsOrderId'),g('baseFee'),g('weightFee'),g('totalFee'),g('currency')))"; }
http(){ curl -s -o /dev/null -w '%{http_code}' "$@"; }
# 订单状态列名是 status（不是 order_status），落库值是枚举名 PENDING/PROCESSING/SHIPPED
statusof(){ dbq "SELECT status FROM orders WHERE id='$1';"; }

echo "== 2. 门 E（负例）：建单只预估运费，不入账 =="
OE=$(mkorder E 2); [ -n "$OE" ] || { bad "E 建单失败"; exit 1; }
LINEE=$(mklogistics "{\"orderId\":\"$OE\",\"channelCode\":\"$CH\"}")
LOE=$(printf '%s' "$LINEE" | cut -d'|' -f1)
FEE_E=$(expectfee $((500*2)))
RESP_E=$(printf '%s' "$LINEE" | cut -d'|' -f2-)
# 期望「首重|续重|合计|币种」：2 件 ×500g = 1000g ⇒ 8.40 + 5.00 = 13.40
[ "$RESP_E" = "${FEE_E}|CNY" ] && ok "E 建单响应运费逐位 ${FEE_E} CNY（门 E 的前置：费用已落库）" || bad "E 建单运费应为 ${FEE_E}|CNY，实际 ${RESP_E:-（空）}"
[ -n "$LOE" ] || { bad "E 未拿到物流单 id，后续断言无从执行"; }
[ "${LOE:-}" != "" ] && {
  N=$(dbq "SELECT COUNT(*) FROM logistics_orders WHERE id='$LOE' AND logistics_fee_id IS NOT NULL;")
  [ "$N" = "1" ] && ok "E logistics_fees 已挂行（费用存在）" || bad "E 建单后没有费用行"
  [ "$(txnso "$LOE")" = "0" ] && ok "E 建单不入账：该单 EXPENSE 流水 0 条（货没交出去就不该有应付）" || bad "E 建单就入账了（流水 $(txnso "$LOE") 条）"
}

echo "== 3. 门 A：POST /logistics/{id}/ship 手工揽收 ⇒ 恰好一条 EXPENSE =="
if [ -n "${LOE:-}" ]; then
  # 订单要先可发：待处理订单按状态机不可直发 SHIPPED（ISS-072 的口径）
  curl -s -X PUT "$BASE/api/orders/$OE/status?status=PROCESSING" -H "$AUTH" >/dev/null
  SHIP_CODE=$(http -X POST "$BASE/api/logistics/$LOE/ship?trackingNumber=BR10-$RUN-A" -H "$AUTH")
  [ "$SHIP_CODE" = "200" ] && ok "A 揽收 HTTP 200" || bad "A 揽收返回 HTTP $SHIP_CODE"
  OS=$(statusof "$OE")
  [ "$OS" = "SHIPPED" ] || [ "$OS" = "shipped" ] && ok "A 揽收把订单回写为已发货（$OS，出库随那次迁移发生）" || bad "A 揽收后订单状态仍是 $OS"
  TUPLE=$(txnof "$LOE")
  [ "$(printf '%s' "$TUPLE" | cut -d'|' -f1)" = "1" ] && ok "A 揽收后流水恰好 1 条" || bad "A 揽收后流水应为 1 条，实际整个元组：${TUPLE:-无}"
  # 「条数|金额|币种|状态|租户」。
  # 状态/类型这两列存的是枚举 code（FinancialTransactionMapper 用 getCode() 写库，
  # 读回时 stringToXxx 只按 code 精确匹配），所以 DB 侧是 expense/pending；
  # 枚举名 PENDING/EXPENSE 只出现在 JSON 侧，两条都要各测一次（见下面的 API 断言）。
  LOMETA=$(lodesc "$LOE")
  EXPECT_TUPLE="1|13.40|CNY|pending|$(printf '%s' "$LOMETA" | cut -d'|' -f1)"
  [ "$TUPLE" = "$EXPECT_TUPLE" ] && ok "A 流水五元组逐位 $EXPECT_TUPLE（金额/币种/挂账状态/同租户）" || bad "A 流水应为 $EXPECT_TUPLE，实际 ${TUPLE:-无}"
  TYPE=$(dbq "SELECT IFNULL(MAX(type),'-') FROM financial_transactions WHERE reference_type='LOGISTICS_ORDER' AND reference_id='$LOE';")
  [ "$TYPE" = "expense" ] && ok "A 交易类型落库为 expense code（BR-10 的应付，与采购应付同构同列形）" || bad "A 交易类型应为 expense（枚举 code），实际 $TYPE"
  DESC=$(txndesc "$LOE")
  NUMBER=$(printf '%s' "$LOMETA" | cut -d'|' -f2)
  case "$DESC" in *"$NUMBER"*"8.40"*"5.00"*) ok "A 备注含物流单号 $NUMBER 与费用构成（可追溯）" ;;
    *) bad "A 备注应含单号与首重/续重，实际：$DESC" ;; esac
  # API 侧可见性：只查得到 DB 行不算联动完成，财务列表必须能按关联单据查出来
  APICODE=$(curl -s "$BASE/api/finance/transactions/by-reference?referenceId=$LOE&referenceType=LOGISTICS_ORDER" -H "$AUTH")
  APIHIT=$(printf '%s' "$APICODE" | python3 -c "
import sys,json
from decimal import Decimal
d=json.load(sys.stdin,parse_float=Decimal).get('data') or []
print('%s|%s' % (len(d), (str(d[0].get('amount')) if d else '-')))")
  [ "$APIHIT" = "1|13.40" ] && ok "A GET /finance/transactions/by-reference 回读 1 条 13.40（台账接口侧同值）" || bad "A 财务接口回读应为 1|13.40，实际 $APIHIT"
  # JSON 侧走的是枚举名（DTO 字段是 TransactionType/TransactionStatus），前端按名过滤/展示；
  # 这一条把「DB 存 code、线上给 name」的双形约定钉住，否则改 mapper 会静默把财务页筛选项打空。
  APINAME=$(printf '%s' "$APICODE" | python3 -c "
import sys,json
d=(json.load(sys.stdin).get('data') or [{}])[0]
print('%s|%s' % (d.get('type'), d.get('status')))")
  [ "$APINAME" = "EXPENSE|PENDING" ] && ok "A 台账接口回读 type/status 为枚举名 EXPENSE|PENDING（前端契约）" || bad "A 接口应回读 EXPENSE|PENDING，实际 $APINAME"
  SEARCH=$(curl -s "$BASE/api/finance/transactions?type=EXPENSE&referenceType=LOGISTICS_ORDER" -H "$AUTH" | python3 -c "
import sys,json
d=(json.load(sys.stdin).get('data') or {})
# PageResult 的字段名是 content（不是 records），按 records 取会永远得到 0 ⇒ 误判成接口查不到
print(d.get('total') or len(d.get('content') or []))")
  [ "${SEARCH:-0}" != "0" ] && ok "A 财务分页检索（type=EXPENSE + referenceType）能查到运费流水（total=$SEARCH）" || bad "A 财务分页检索查不到运费流水"
else
  bad "无 E 物流单，跳过门 A 的 7 条断言"; bad "无 E 物流单，跳过备注断言"; bad "无 E 物流单，跳过接口回读"
fi

echo "== 4. 幂等：同单再发一次货 + 再推一次状态，流水必须仍是 1 条 =="
if [ -n "${LOE:-}" ]; then
  curl -s -X POST "$BASE/api/logistics/$LOE/ship?trackingNumber=BR10-$RUN-A2" -H "$AUTH" >/dev/null
  curl -s -X PUT "$BASE/api/logistics/$LOE/status" -H "$AUTH" -H "$JH" -d '{"status":"IN_TRANSIT","description":"BR10 幂等复推"}' >/dev/null
  N2=$(txnso "$LOE")
  [ "$N2" = "1" ] && ok "重复揽收 + 推进运输中后流水仍为 1 条（BR-03 幂等判据生效）" || bad "重复发货把运费记成了 $N2 笔"
  AMT2=$(dbq "SELECT IFNULL(MAX(amount),'-') FROM financial_transactions WHERE reference_type='LOGISTICS_ORDER' AND reference_id='$LOE';")
  [ "$AMT2" = "13.40" ] && ok "复推状态没有把金额改成别的值（仍 13.40，入账不重算）" || bad "复推后金额变成 $AMT2"
else
  bad "无 E 物流单，跳过幂等断言"; bad "无 E 物流单，跳过金额断言"
fi

echo "== 5. 门 B：POST /logistics/batch-ship（复用自动派生的 PENDING 单那条分支）=="
OB=$(mkorder B 1)
[ -n "$OB" ] || bad "B 建单失败"
PCodeB=$(http -X PUT "$BASE/api/orders/$OB/status?status=PROCESSING" -H "$AUTH")
[ "$PCodeB" = "200" ] && ok "B 订单已推进为 PROCESSING（HTTP 200，批量发货要求可发货的订单）" || bad "B 订单推进 PROCESSING 返回 HTTP $PCodeB，批量发货会被状态机拒掉"
LOB=$(loauto "$OB")
[ -n "$LOB" ] && ok "B 下单事件自动派生物流单 $LOB" || bad "B 没有自动派生物流单，门 B 无从执行"
if [ -n "$LOB" ]; then
  # batchShip 的入参契约要求「logisticsChannelId / channelCode 至少给一个」
  # （LogisticsOrderResolver.resolveChannel 在方法开头无条件解析，实测 HTTP 200 但该行 failureCount=1，
  # 原因串见下面的打印）。所以这一门带探针渠道调用，但断言它走的仍是「复用已有 PENDING 单」那条分支：
  # 物流单 id 不变 + 金额取自动渠道那行（15.00），若被换成自建单则会是探针渠道的 10.90。
  BRES=$(curl -s -X POST "$BASE/api/logistics/batch-ship" -H "$AUTH" -H "$JH" \
    -d "{\"orderIds\":[\"$OB\"],\"logisticsChannelId\":\"$CHID\",\"updateOrderStatus\":true}" | python3 -c "
import sys,json
d=(json.load(sys.stdin).get('data') or {})
# 失败原因必须打出来：只报 success=0 的话，下一轮排查还得重跑一遍才知道是被什么拒的
fails=' ; '.join('%s:%s' % (i.get('orderNumber') or i.get('rowNumber'), i.get('reason'))
                 for i in (d.get('failures') or []))
print('total=%s success=%s failure=%s skipped=%s%s' % (d.get('totalCount'), d.get('successCount'),
      d.get('failureCount'), d.get('skippedCount'), (' 原因[' + fails + ']') if fails else ''))")
  echo "       批量发货结果：$BRES"
  NOW_B=$(dbq "SELECT GROUP_CONCAT(id) FROM logistics_orders WHERE order_id='$OB';")
  [ "$NOW_B" = "$LOB" ] && ok "B 批量发货复用的就是自动单（没有另建一张：$NOW_B）" || bad "B 批量发货把自动单换成了 $NOW_B（原 $LOB），复用分支没打到"
  # 复用自动单 ⇒ 入账金额取该单已在库里的费用行（渠道改价不拖动历史运费）。
  # 币种同样从费用行读：自动单用的是 DEFAULT_CHANNEL，它的币种不一定是 CNY，硬编码会把绿探针变红。
  DBFEE=$(dbq "SELECT CONCAT(IFNULL(f.total_fee,'-'),'|',IFNULL(f.currency,'-')) FROM logistics_orders l JOIN logistics_fees f ON f.id=l.logistics_fee_id WHERE l.id='$LOB';")
  TUPLEB=$(txnof "$LOB")
  EXPECTB="1|$(printf '%s' "$DBFEE" | cut -d'|' -f1)|$(printf '%s' "$DBFEE" | cut -d'|' -f2)|pending|$(printf '%s' "$(lodesc "$LOB")" | cut -d'|' -f1)"
  case "$DBFEE" in "-|"*|"") bad "B 自动派生的单没有费用行，门 B 无从判定（ISS-137 的口径回归了）" ;;
    *) [ "$TUPLEB" = "$EXPECTB" ] && ok "B 批量发货入账五元组逐位 $EXPECTB（金额/币种取自费用行 $DBFEE）" || bad "B 批量发货应为 $EXPECTB，实际 ${TUPLEB:-无流水}" ;;
  esac
else
  bad "B 无自动物流单，跳过门 B 断言"; bad "B 无自动物流单，跳过复用断言"
fi

echo "== 6. 门 C：POST /api/logistics + shipImmediately=true（建单即发货）=="
OC=$(mkorder C 2); [ -n "$OC" ] && curl -s -X PUT "$BASE/api/orders/$OC/status?status=PROCESSING" -H "$AUTH" >/dev/null || bad "C 建单失败"
LINEC=$(mklogistics "{\"orderId\":\"$OC\",\"channelCode\":\"$CH\",\"shipImmediately\":true}")
LOC=$(printf '%s' "$LINEC" | cut -d'|' -f1)
RESPC=$(printf '%s' "$LINEC" | cut -d'|' -f2-)
if [ -n "$LOC" ]; then
  [ "$RESPC" = "${FEE_E}|CNY" ] && ok "C 建单即发货的运费仍按同口径落库 ${FEE_E}" || bad "C 运费应为 ${FEE_E}|CNY，实际 $RESPC"
  [ "$(printf '%s' "$(txnof "$LOC")" | cut -d'|' -f1)" = "1" ] && ok "C 建单即发货入账 1 条（第四条门补上，不再产生「已揽收却没记账」的半态）" || bad "C 建单即发货未入账：$(txnof "$LOC")"
  [ "$(statusof "$OC")" != "PENDING" ] && ok "C 建单即发货同步将订单推进（状态 $(statusof "$OC")）" || bad "C 建单即发货没回写订单状态（仍 PENDING，ISS-072 的第四条门又漏了）"
else
  bad "C 未拿到物流单 id，跳过门 C 断言"; bad "C 未拿到物流单 id，跳过订单回写断言"
fi

echo "== 7. 门 D：PUT /logistics/{id}/status 推进到「运输中」也要入账；「已打单」不入账 =="
OF=$(mkorder F 1); [ -n "$OF" ] || bad "F 建单失败"
LOF=$(mklogistics "{\"orderId\":\"$OF\",\"channelCode\":\"$CH\"}" | cut -d'|' -f1)
OG=$(mkorder G 1); [ -n "$OG" ] || bad "G 建单失败"
LOG2=$(mklogistics "{\"orderId\":\"$OG\",\"channelCode\":\"$CH\"}" | cut -d'|' -f1)
if [ -n "$LOF" ]; then
  curl -s -X PUT "$BASE/api/logistics/$LOF/status" -H "$AUTH" -H "$JH" -d '{"status":"LABEL_GENERATED","description":"BR10 已打单"}' >/dev/null
  [ "$(txnso "$LOF")" = "0" ] && ok "D1 推进到已打单不入账（货还在自己仓里）" || bad "D1 只打单就记了应付（$(txnso "$LOF") 条）"
  curl -s -X PUT "$BASE/api/logistics/$LOF/status" -H "$AUTH" -H "$JH" -d '{"status":"IN_TRANSIT","description":"BR10 运输中"}' >/dev/null
  TUPLED=$(txnof "$LOF")
  [ "$(printf '%s' "$TUPLED" | cut -d'|' -f1)" = "1" ] && ok "D2 推进到运输中入账 1 条：$TUPLED" || bad "D2 推进到运输中未入账（元组 ${TUPLED:-无}）"
  OSF=$(statusof "$OF")
  [ "$OSF" = "PENDING" ] && ok "D3 状态推进端点只记承运商事实，不动订单状态（仍 $OSF）—— R-58 明写的口径" || bad "D3 状态推进端点把订单也推成了 $OSF（与 R-58 记的口径不符，文档要一起改）"
else
  bad "D 未拿到物流单 id，跳过门 D 的 3 条断言"; bad "D 未拿到物流单 id，跳过断言"; bad "D 未拿到物流单 id，跳过断言"
fi

echo "== 8. 门 G（负例）：历史无费用行的物流单发货 ⇒ 不入账，也不凭空编金额 =="
if [ -n "$LOG2" ]; then
  # 模拟 ISS-137 之前建的老单：把费用行摘掉（探针自建，自己删）
  FEEG=$(dbq "SELECT IFNULL(logistics_fee_id,'') FROM logistics_orders WHERE id='$LOG2';")
  dbq "UPDATE logistics_orders SET logistics_fee_id=NULL WHERE id='$LOG2'" >/dev/null
  [ -n "$FEEG" ] && dbq "DELETE FROM logistics_fees WHERE id='$FEEG'" >/dev/null
  curl -s -X PUT "$BASE/api/orders/$OG/status?status=PROCESSING" -H "$AUTH" >/dev/null
  SHIPG=$(http -X POST "$BASE/api/logistics/$LOG2/ship?trackingNumber=BR10-$RUN-G" -H "$AUTH")
  [ "$SHIPG" = "200" ] && ok "G 无费用行的单子仍能发货（HTTP 200，入账没有新增拒绝路径）" || bad "G 发货被拒（HTTP $SHIPG）"
  [ "$(txnso "$LOG2")" = "0" ] && ok "G 无费用行不入账：宁可台账少一行，也不猜金额" || bad "G 给从没算过运费的单编了一条应付（$(txnso "$LOG2") 条）"
else
  bad "G 未拿到物流单 id，跳过门 G 断言"; bad "G 未拿到物流单 id，跳过断言"
fi

echo "== 9. 门 H（负例）：只改订单状态 PUT /orders/{id}/status 不产生运费流水 =="
OH=$(mkorder H 1); [ -n "$OH" ] && curl -s -X PUT "$BASE/api/orders/$OH/status?status=PROCESSING" -H "$AUTH" >/dev/null || bad "H 建单失败"
LOH=$(lofoc "$OH")
[ -z "$LOH" ] && LOH=$(loauto "$OH")
if [ -n "$OH" ]; then
  curl -s -X PUT "$BASE/api/orders/$OH/status?status=SHIPPED" -H "$AUTH" >/dev/null
  CNT=0
  for ID in $(dbq "SELECT id FROM logistics_orders WHERE order_id='$OH';"); do
    CNT=$((CNT + $(txnso "$ID")))
  done
  [ "$CNT" = "0" ] && ok "H 订单侧发货不记运费（该订单的 $(dbq "SELECT COUNT(*) FROM logistics_orders WHERE order_id='$OH';") 张物流单都没记录揽收事实，流水 0 条）" || bad "H 订单侧发货记了 $CNT 条流水（门 H 不该入账，R-58 口径变了要同步改文档）"
else
  bad "H 建单失败，跳过门 H 断言"
fi

echo "== 10. 门 I（ISS-139 / R-59）：删单据要一起撤掉运费分录，已过账的要挡住删除 =="
# 修复前实测：DELETE /api/logistics/7d47777b-… 回 HTTP 200、logistics_orders 行消失，
# 但 financial_transactions 里那条 expense/pending 原样留着（AFTER_ORPHAN_TXN=1）。
# 台账上因此多出一条"能付款、能对账"的应付，付款就是凭空多付一笔钱。
OI1=$(mkorder I1 1); [ -n "$OI1" ] || bad "I1 建单失败"
OI2=$(mkorder I2 1); [ -n "$OI2" ] || bad "I2 建单失败"
DEL_LO_IDS=""
FEE_I=$(expectfee $((500*1)) | cut -d'|' -f3)
if [ -n "$OI1" ] && [ -n "$OI2" ]; then
  curl -s -X PUT "$BASE/api/orders/$OI1/status?status=PROCESSING" -H "$AUTH" >/dev/null
  curl -s -X PUT "$BASE/api/orders/$OI2/status?status=PROCESSING" -H "$AUTH" >/dev/null
  LOI1=$(mklogistics "{\"orderId\":\"$OI1\",\"channelCode\":\"$CH\",\"shipImmediately\":true}" | cut -d'|' -f1)
  LOI2=$(mklogistics "{\"orderId\":\"$OI2\",\"channelCode\":\"$CH\",\"shipImmediately\":true}" | cut -d'|' -f1)
  [ -n "$LOI1" ] && [ -n "$LOI2" ] && ok "I 拿到两张已入账的单子 $LOI1 / $LOI2（各 $FEE_I）" || bad "I 未拿到物流单 id，门 I 无从执行"

  TXN1=$(dbq "SELECT IFNULL(MAX(transaction_id),'-') FROM financial_transactions WHERE reference_type='LOGISTICS_ORDER' AND reference_id='$LOI1'")
  FEEID1=$(dbq "SELECT IFNULL(logistics_fee_id,'') FROM logistics_orders WHERE id='$LOI1'")
  # 单请求同时拿 body 与状态码：BusinessException 走 HTTP 400 + Result.message，
  # 只看 curl 退出码或只看 HTTP 200 都会把"接口报 200 但业务被拒"读成成功（ISS-136 的同款形状）
  RAW_I1=$(curl -s -X DELETE "$BASE/api/logistics/$LOI1" -H "$AUTH" -w '\n%{http_code}')
  CODE_I1=$(printf '%s' "$RAW_I1" | tail -1)
  ST1=$(dbq "SELECT IFNULL(MAX(status),'-') FROM financial_transactions WHERE transaction_id='$TXN1'")
  AM1=$(dbq "SELECT IFNULL(MAX(amount),'-') FROM financial_transactions WHERE transaction_id='$TXN1'")
  [ "$CODE_I1" = "200" ] && ok "I1 只有挂账分录时允许删单（HTTP 200，没有把删除整个挡死）" || bad "I1 删单返回 HTTP $CODE_I1（应 200）"
  [ "$(dbq "SELECT COUNT(*) FROM logistics_orders WHERE id='$LOI1'")" = "0" ] && ok "I1 物流单已删除" || bad "I1 物流单仍在库里"
  [ "$(dbq "SELECT COUNT(*) FROM financial_transactions WHERE transaction_id='$TXN1'")" = "1" ] \
    && ok "I1 分录行没被删（撤的是状态不是行，审计链保持完整）" || bad "I1 分录行被删掉了：财务台账不该有删除口"
  [ "$ST1" = "cancelled" ] && ok "I1 派生分录随单据撤成 cancelled" || bad "I1 删单后分录状态仍是 $ST1（应为 cancelled）"
  [ "$AM1" = "$FEE_I" ] && ok "I1 撤销只动状态不动金额（仍 $AM1）" || bad "I1 撤销把金额 $FEE_I 改成了 $AM1"
  LIVE1=$(dbq "SELECT COUNT(*) FROM financial_transactions WHERE transaction_id='$TXN1' AND status NOT IN ('cancelled','failed')")
  [ "$LIVE1" = "0" ] && ok "I1 不再留下「指向已删单据却还能付款」的活应付（R-59 判定口径）" || bad "I1 删单后仍留有 $LIVE1 条活应付指向不存在的物流单"
  if [ -n "$FEEID1" ]; then
    [ "$(dbq "SELECT COUNT(*) FROM logistics_fees WHERE id='$FEEID1'")" = "0" ] \
      && ok "I1 费用行随单据一起回收（无孤儿 logistics_fees）" || bad "I1 费用行 $FEEID1 成了孤儿"
  else
    bad "I1 建单即发货却没费用行（ISS-137 口径回归）"
  fi
  DEL_LO_IDS=""
  [ -n "${LOI1:-}" ] && DEL_LO_IDS="$LOI1"
  # LOI2 在修复后的代码里删不掉（会被挡回来），它仍在 logistics_orders 里；
  # 但一旦撤销口被摘掉（变异/回归），它就被真删了、名下分录不在 LO_LIST 里 ——
  # 把它一并点名，探针在任何代码形状下都能自清场（R-39）
  [ -n "${LOI2:-}" ] && DEL_LO_IDS="${DEL_LO_IDS:+$DEL_LO_IDS,}$LOI2"

  TXN2=$(dbq "SELECT IFNULL(MAX(transaction_id),'-') FROM financial_transactions WHERE reference_type='LOGISTICS_ORDER' AND reference_id='$LOI2'")
  CMP2=$(curl -s -X PUT "$BASE/api/finance/transactions/$TXN2/complete" -H "$AUTH" -w '\n%{http_code}' | tail -1)
  ST2=$(dbq "SELECT IFNULL(MAX(status),'-') FROM financial_transactions WHERE transaction_id='$TXN2'")
  [ "$CMP2" = "200" ] && [ "$ST2" = "success" ] \
    && ok "I2 前置：过账成功 $TXN2（PUT /finance/transactions/{id}/complete ⇒ success）" || bad "I2 过账失败（HTTP $CMP2，状态 $ST2）"
  RAW_I2=$(curl -s -X DELETE "$BASE/api/logistics/$LOI2" -H "$AUTH" -w '\n%{http_code}')
  CODE_I2=$(printf '%s' "$RAW_I2" | tail -1)
  MSG_I2=$(printf '%s' "$RAW_I2" | sed '$d' | python3 -c 'import sys,json;print((json.load(sys.stdin) or {}).get("message",""))' 2>/dev/null)
  case "$MSG_I2" in
    *"不能连同单据一起删除"*) ok "I2 已过账分录挡住删单并回可读原因：$MSG_I2" ;;
    *) bad "I2 应回业务拒绝原因，实际 HTTP $CODE_I2 message=${MSG_I2:-（响应体解析失败）}" ;;
  esac
  [ "$CODE_I2" = "400" ] && ok "I2 业务拒绝走 HTTP 400（没退化成 500，ISS-136 口径守住）" || bad "I2 拒绝返回 HTTP $CODE_I2（应 400）"
  [ "$(dbq "SELECT COUNT(*) FROM logistics_orders WHERE id='$LOI2'")" = "1" ] \
    && ok "I2 单据一张都没删（@Transactional 回滚，没有删一半）" || bad "I2 拒绝了删除却把物流单删掉了（半态）"
  [ "$(dbq "SELECT CONCAT(COUNT(*),'|',IFNULL(MAX(status),'-')) FROM financial_transactions WHERE reference_type='LOGISTICS_ORDER' AND reference_id='$LOI2'")" = "1|success" ] \
    && ok "I2 已过账分录原样保留（既没被改成 cancelled 也没多记第二笔）" || bad "I2 拒绝删除时动了分录：$(txnof "$LOI2")"
else
  bad "I 建单失败，跳过门 I 的 8 条断言"; bad "I 建单失败，跳过门 I 断言"; bad "I 建单失败，跳过门 I 断言"
fi

echo "== 11. 清场：流水 → 物流单 → 费用行 → 订单 → 库存/仓 → 商品/SKU → 渠道 → 审计 =="
IDS="'none'"
for X in "$OE" "$OB" "$OC" "$OF" "$OG" "$OH" "$OI1" "$OI2"; do [ -n "${X:-}" ] && IDS="$IDS,'$X'"; done
LO_LIST=$(dbq "SELECT GROUP_CONCAT(id) FROM logistics_orders WHERE order_id IN ($IDS)")
[ "$LO_LIST" = "NULL" ] && LO_LIST=""
# LOI1 已被接口删掉，不再出现在 logistics_orders 里，但它那条 cancelled 分录必须由探针自己清（R-39）：
# 这类"单据没了、分录还在"的行正是本门要验的形状，清场漏了它就等于把脏数据留给下一轮
ALL_LO="$LO_LIST"
[ -n "$DEL_LO_IDS" ] && ALL_LO="${ALL_LO:+$ALL_LO,}$DEL_LO_IDS"
PI_IDS=$(dbq "SELECT GROUP_CONCAT(payment_info_id) FROM orders WHERE id IN ($IDS)")
SA_IDS=$(dbq "SELECT GROUP_CONCAT(shipping_address_id) FROM orders WHERE id IN ($IDS)")
FEE_IDS=$(dbq "SELECT GROUP_CONCAT(logistics_fee_id) FROM logistics_orders WHERE order_id IN ($IDS)")
LBL_IDS=$(dbq "SELECT GROUP_CONCAT(logistics_label_id) FROM logistics_orders WHERE order_id IN ($IDS)")
TARGETS="$IDS,'$CHID','$PROD','$WID'"
[ -n "$ALL_LO" ] && TARGETS="$TARGETS,'$(printf '%s' "$ALL_LO" | sed "s/,/','/g")'"
inlist(){ printf '%s' "$1" | sed "s/,/','/g"; }
TXN_WHERE=""
[ -n "$ALL_LO" ] && TXN_WHERE="reference_type='LOGISTICS_ORDER' AND reference_id IN ('$(inlist "$ALL_LO")')"

for STMT in \
  "${TXN_WHERE:+DELETE FROM financial_transactions WHERE $TXN_WHERE}" \
  "DELETE FROM audit_logs WHERE target_id IN ($TARGETS)" \
  "DELETE FROM audit_logs WHERE target_id IN (SELECT id FROM logistics_orders WHERE order_id IN ($IDS))" \
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
# 的普通 varchar），于是探针自己的清场把采购单掏成断链——实测 530 → 535 那 5 行就是本探针与 iss072/iss137
# 的"出库到 0 → 同步生成缺货自动单 → DELETE FROM warehouses"三步留下的。缺货单走同步事件链
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
  "DELETE FROM warehouses WHERE id='$WID'" \
  "DELETE FROM skus WHERE product_id='$PROD'" \
  "DELETE FROM products WHERE id='$PROD'" \
  "DELETE FROM logistics_channels WHERE id='$CHID'"; do
  ERR=$(dbq "$STMT"); [ -n "$ERR" ] && echo "    （清理：$ERR ← $STMT）"
done
# 常驻回归位（R-44）：仓行已经没了，此时仍指向本探针仓的采购单＝迟到的异步单，再扫一遍并判定
if [ -n "$WID" ]; then
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
LEFT_O=$(dbq "SELECT COUNT(*) FROM orders WHERE order_number LIKE 'BR10%$RUN'")
LEFT_L=$(dbq "SELECT COUNT(*) FROM logistics_orders WHERE order_id IN ($IDS)")
LEFT_T=$([ -n "$TXN_WHERE" ] && dbq "SELECT COUNT(*) FROM financial_transactions WHERE $TXN_WHERE" || echo 0)
LEFT_ALL_T=$(dbq "SELECT COUNT(*) FROM financial_transactions WHERE reference_type='LOGISTICS_ORDER'")
ORPHAN_F=$(dbq "SELECT COUNT(*) FROM logistics_fees f LEFT JOIN logistics_orders l ON l.logistics_fee_id=f.id WHERE l.id IS NULL")
# 全库口径（卫生 ⑱）：一旦有别的例程（清场块、级联删单）绕过 R-59 删掉物流单，
# 这里就会亮红并把脏行点名打出来 —— cancelled 是应用有意留的撤销痕，不算孤儿
ORPHAN_LIVE=$(dbq "SELECT COUNT(*) FROM financial_transactions t LEFT JOIN logistics_orders l ON l.id=t.reference_id
  WHERE t.reference_type='LOGISTICS_ORDER' AND l.id IS NULL AND t.status<>'cancelled'")
[ "$ORPHAN_LIVE" != "0" ] && echo "    活孤儿分录：$(dbq "SELECT GROUP_CONCAT(CONCAT(t.transaction_id,'/',t.status,'/',t.reference_id)) FROM financial_transactions t LEFT JOIN logistics_orders l ON l.id=t.reference_id WHERE t.reference_type='LOGISTICS_ORDER' AND l.id IS NULL AND t.status<>'cancelled'")"
LEFT_P=$(dbq "SELECT COUNT(*) FROM products WHERE id='$PROD'")
LEFT_C=$(dbq "SELECT COUNT(*) FROM logistics_channels WHERE channel_code='$CH'")
LEFT_W=$(dbq "SELECT COUNT(*) FROM warehouses WHERE warehouse_code='BR10-$RUN'")
LEFT_A=$(dbq "SELECT COUNT(*) FROM audit_logs WHERE target_id IN ($TARGETS)")
if [ "$LEFT_O" = "0" ] && [ "$LEFT_L" = "0" ] && [ "${LEFT_T:-0}" = "0" ] && [ "$LEFT_P" = "0" ] \
   && [ "$LEFT_C" = "0" ] && [ "$LEFT_W" = "0" ] && [ "$ORPHAN_F" = "0" ] && [ "$LEFT_A" = "0" ] \
   && [ "$ORPHAN_LIVE" = "0" ]; then
  ok "探针已自清场（orders=$LEFT_O logistics=$LEFT_L 本品流水=$LEFT_T 孤儿费用=$ORPHAN_F 活孤儿分录=$ORPHAN_LIVE products=$LEFT_P channels=$LEFT_C warehouses=$LEFT_W 审计=$LEFT_A；全库 LOGISTICS_ORDER 流水剩 $LEFT_ALL_T 条，含门 I 撤销后按单据名下的 cancelled 痕）"
else
  bad "残留 orders=$LEFT_O logistics=$LEFT_L 本品流水=${LEFT_T:-?} 孤儿费用=$ORPHAN_F 活孤儿分录=$ORPHAN_LIVE products=$LEFT_P channels=$LEFT_C warehouses=$LEFT_W 审计=$LEFT_A"
fi

echo
echo "结果：$P 通过 / $F 失败"
[ "$F" = "0" ] || exit 1
