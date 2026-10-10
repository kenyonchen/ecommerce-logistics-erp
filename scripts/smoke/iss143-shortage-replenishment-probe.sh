#!/usr/bin/env bash
# ISS-143 探针：缺货自动单必须带明细行，并且这张单真的能走完 BR-09 的到货入库。
#
# 修复前的实况（证据）：EventHandler:102 与 MQ 消费端都调 4 参重载 createPurchaseOrder(tenant, supplier,
# name, warehouseId)，只落单头。18:06 复算 purchase_orders：共 539 行、supplier_id='DEFAULT_SUPPLIER' 366 行、
# 其中 366 行的 purchase_order_items 计数为 0 —— 与 DEFAULT_SUPPLIER 行数完全相等，不是抽样结论。
# 后果：BR-09 的到货入库是逐行 inventoryIn，空单到货等于一行都入不了，"缺货→自动补货"这条链不可用。
#
# 判定口径（回读副作用，不看 HTTP 码）：
#   ① 出库到 0 ⇒ 自动单必须有 1 行明细，且 sku_code/数量/单价/总额逐位等于「阈值-现存量 × SKU 成本价」；
#      HTTP GET 回读与 DB 行两处一致（响应形状与库里存的是同一件事）。
#   ② 负例：SKU 没有成本价 ⇒ 只发预警、一张单都不建（幻影桶反模式 R-58 的同一条口径 ——
#      绝不拿售价或猜数当采购单价，否则会造出一张能过审批却金额虚假的账，到货还按假金额记应付）。
#   ③ 端到端：批准 → 运输中 → 已送达之后，缺货的那条 SKU 库存真的回到 10，并且落一条 66.60 的采购应付。
#      这一条才是"可用"的判据：明细行不是为了好看，是为了入库与入账能对上。
#   ④ 常驻位：探针仓名下无采购域残留 + 全库断链采购单不超过开跑基线（ISS-142 同款，卫生 ㉒）。
# 探针自建的商品/SKU/仓/单由探针自己删除（R-39：只认自己的 skuCode / warehouseCode）。
set -uo pipefail
BASE=http://localhost:8082
RUN="$(date +%s)$$"
SKU="ISS143-SKU-$RUN"
NOSKU="ISS143-NOCOST-$RUN"
COST="6.66"
P=0; F=0
ok(){ echo "  ✅ $1"; P=$((P+1)); }
bad(){ echo "  ❌ $1"; F=$((F+1)); }
# 合并 stderr：MySQL 报错必须被看见（mysql -e 从首条失败语句起整批不执行，静默失败过一次）
dbq(){ docker exec mysql mysql -uroot -p123456 -N -D ecommerce_logistics_erp --default-character-set=utf8mb4 -e "$1" 2>&1 | grep -v "Using a password"; }
# ISS-142 基线：开跑前全库"指向不存在的仓"的采购单行数，收尾拿它比对（见脚本尾部清场段）
DANGLING_T0=$(dbq "SELECT COUNT(*) FROM purchase_orders p LEFT JOIN warehouses w ON w.id = p.warehouse_id WHERE p.warehouse_id IS NOT NULL AND w.id IS NULL")

TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("token",""))')
[ -n "$TOKEN" ] || { echo "登录失败，探针无法执行"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"
JH='Content-Type: application/json'

mkwh(){ curl -s -X POST $BASE/api/warehouses -H "$AUTH" -H "$JH" -d "{
  \"warehouseName\":\"ISS143探针仓$1-$RUN\",\"warehouseCode\":\"ISS143-$1-$RUN\",\"address\":\"探针地址\",\"capacity\":100}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("warehouseId",""))'; }
mkproduct(){ curl -s -X POST $BASE/api/products -H "$AUTH" -H "$JH" -d "{
  \"name\":\"ISS143探针商品$1-$RUN\",\"description\":\"ISS-143\",\"categoryName\":\"ISS143\",\"active\":true,
  \"skus\":[{\"skuCode\":\"$2\",\"skuName\":\"ISS143 SKU$1\",\"sellingPrice\":9.90,\"currency\":\"CNY\"$3}]}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("id",""))'; }
inv(){ curl -s -X POST "$BASE/api/warehouses/$1/inventory/$2" -H "$AUTH" -H "$JH" \
  -d "{\"sku\":\"$3\",\"productId\":\"$3\",\"warehouseId\":\"$1\",\"quantity\":$4,\"movementType\":\"$( [ "$2" = in ] && echo INBOUND || echo OUTBOUND )\"}"; }
# 某个仓名下的自动单（supplier_id 恒为 DEFAULT_SUPPLIER，按仓隔离计数）
posof(){ dbq "SELECT IFNULL(GROUP_CONCAT(purchase_order_id),'') FROM purchase_orders WHERE warehouse_id='$1' AND supplier_id='DEFAULT_SUPPLIER';"; }
# 明细行四元组：sku_code|数量|单价|单据总额（库里 DECIMAL 原样输出，逐位比较）
dbof(){ dbq "SELECT CONCAT(IFNULL(i.sku_code,'~'),'|',IFNULL(i.quantity,'~'),'|',IFNULL(i.unit_price,'~'),'|',IFNULL(p.total_amount,'~'))
  FROM purchase_orders p LEFT JOIN purchase_order_items i ON i.purchase_order_id=p.purchase_order_id
  WHERE p.purchase_order_id='$1';"; }

echo "== 1. 建两个探针仓 + 两个商品（A 带成本价 $COST，B 只有售价）=="
WA=$(mkwh A); [ -n "$WA" ] && ok "探针仓 A $WA（有成本价商品）" || { bad "创建探针仓 A 失败"; exit 1; }
WB=$(mkwh B); [ -n "$WB" ] && ok "探针仓 B $WB（无成本价商品）" || bad "创建探针仓 B 失败"
PA=$(mkproduct P "$SKU" ",\"costPrice\":$COST")
[ -n "$PA" ] && ok "有成本价商品 $PA（SKU $SKU cost_price=$COST）" || { bad "创建有成本价商品失败"; exit 1; }
PB=$(mkproduct N "$NOSKU" '')
[ -n "$PB" ] && ok "无成本价商品 $PB（SKU $NOSKU cost_price=NULL）" || bad "创建无成本价商品失败"

echo "== 2. 各入库 10 再各出库 10 ⇒ 库存 0（OUT_OF_STOCK）⇒ 缺货事件 =="
inv "$WA" in "$SKU" 10 >/dev/null; inv "$WB" in "$NOSKU" 10 >/dev/null
QA=$(dbq "SELECT quantity FROM inventory_records WHERE warehouse_id='$WA' AND sku_code='$SKU';")
QB=$(dbq "SELECT quantity FROM inventory_records WHERE warehouse_id='$WB' AND sku_code='$NOSKU';")
[ "$QA" = "10" ] && ok "入库到位：仓 A 库存 10" || bad "入库后仓 A 库存=$QA（期望 10），出库判定无法成立"
[ "$QB" = "10" ] && ok "入库到位：仓 B 库存 10" || bad "入库后仓 B 库存=$QB（期望 10）"
inv "$WA" out "$SKU" 10 >/dev/null; inv "$WB" out "$NOSKU" 10 >/dev/null
SA=$(dbq "SELECT status FROM inventory_records WHERE warehouse_id='$WA' AND sku_code='$SKU';")
[ "$SA" = "OUT_OF_STOCK" ] && ok "出库到 0：库存状态 $SA（缺货事件已发布）" || bad "出库后状态=$SA（期望 OUT_OF_STOCK）"

echo "== 3. 判定 ①：自动单必须带 1 行明细，数量=阈值10-现存0=10、单价=成本价 $COST =="
POA=$(posof "$WA")
PO_NUM=$(printf '%s' "$POA" | tr ',' '\n' | grep -c '^PO')
if [ "$PO_NUM" = "1" ]; then ok "仓 A 名下自动单 1 张：$POA"; else bad "仓 A 名下自动单 $PO_NUM 张（期望 1 张）：$POA"; fi
ROW=$(dbof "$POA")
# 期望：SKU|10|6.66|66.60（总额 = 单价 × 数量）
[ "$ROW" = "$SKU|10|$COST|66.60" ] && ok "明细行逐位正确：$ROW" || bad "明细行不符：实际[$ROW] 期望[$SKU|10|$COST|66.60]"
ITEMS=$(dbq "SELECT COUNT(*) FROM purchase_order_items WHERE purchase_order_id='$POA';")
[ "$ITEMS" = "1" ] && ok "purchase_order_items 计数 1（修复前这里是 0）" || bad "明细行计数=$ITEMS（期望 1）：空单进不了 BR-09 的逐行入库"

echo "== 4. 判定 ①b：HTTP GET 回读与库里是同一件事（响应形状不是只给单头）=="
curl -s "$BASE/api/purchases/$POA" -H "$AUTH" | python3 -c "
import sys,json
from decimal import Decimal
d=(json.load(sys.stdin,parse_float=Decimal).get('data') or {})
items=d.get('items') or []
one=items[0] if len(items)==1 else {}
got='%s|%s|%s|%s' % (one.get('skuCode','~'),one.get('quantity','~'),one.get('unitPrice','~'),d.get('totalAmount','~'))
print(got)" > /tmp/iss143-dto.txt 2>/dev/null
DTO=$(cat /tmp/iss143-dto.txt 2>/dev/null)
[ "$DTO" = "$SKU|10|$COST|66.60" ] && ok "GET /api/purchases/$POA 回读一致：$DTO" || bad "HTTP 回读不符：实际[$DTO] 期望[$SKU|10|$COST|66.60]"

echo "== 5. 判定 ②（负例）：无成本价 ⇒ 只发预警，一张单都不建 =="
POB=$(posof "$WB")
if [ -z "$POB" ]; then ok "仓 B（SKU 无成本价）名下自动单 0 张 —— 没有拿售价 9.90 或猜数顶替采购单价"; else bad "仓 B 建出了单：$POB（无成本价必须只发预警不建单，R-58）"; fi

echo "== 6. 判定 ③（端到端）：批准 → 运输中 → 已送达 ⇒ 库存回到 10 且落一条 66.60 应付 =="
if [ "$PO_NUM" = "1" ]; then
  for STEP in approve in-transit delivered; do
    RESP=$(curl -s -X PUT "$BASE/api/purchases/$POA/$STEP" -H "$AUTH" -H "$JH" -d '{"comment":"ISS-143 探针"}')
    ST=$(printf '%s' "$RESP" | python3 -c 'import sys,json;d=json.load(sys.stdin);print((d.get("data") or {}).get("status",d.get("message","?")))')
    [ "$ST" != "?" ] && ok "$STEP → status=$ST" || bad "$STEP 失败：$RESP"
  done
  Q1=$(dbq "SELECT quantity FROM inventory_records WHERE warehouse_id='$WA' AND sku_code='$SKU';")
  [ "$Q1" = "10" ] && ok "BR-09 到货入库生效：库存 0 → $Q1（修复前空单到货这一步入不了任何库存）" || bad "到货后库存=$Q1（期望 10）：明细行没被逐行入库"
  PAY=$(dbq "SELECT CONCAT(status,'|',amount) FROM financial_transactions WHERE reference_type='PURCHASE_ORDER' AND reference_id='$POA';")
  # 落库是 pending：分录要 PUT /api/finance/transactions/{id}/complete 才置 success（docs/spec/finance.md BR-05 口径①，
  # 与 iss138 的运费分录同一形态）。这里判"有没有按明细金额挂上应付"，不替用户做对账决定。
  [ "$PAY" = "pending|66.60" ] && ok "BR-10 采购应付挂账：$PAY（金额逐位等于明细行 10 × 6.66）" || bad "采购应付不符：实际[$PAY] 期望[pending|66.60]"
else
  bad "没有可走完流程的自动单，端到端判定跳过"
fi

echo "== 7. 清场（R-39：只删探针自己建的数据，采购域按子表先删）=="
dbq "DELETE ft FROM financial_transactions ft JOIN purchase_orders po ON po.purchase_order_id = ft.reference_id
  WHERE ft.reference_type='PURCHASE_ORDER' AND po.warehouse_id IN ('$WA','$WB');
  DELETE i FROM purchase_order_items i JOIN purchase_orders po ON po.purchase_order_id=i.purchase_order_id
  WHERE po.warehouse_id IN ('$WA','$WB');
  DELETE FROM purchase_orders WHERE warehouse_id IN ('$WA','$WB');
  DELETE ir FROM inventory_records ir JOIN warehouses w ON ir.warehouse_id=w.id WHERE w.warehouse_code LIKE 'ISS143-%';
  DELETE l FROM locations l JOIN warehouses w ON l.warehouse_id=w.id WHERE w.warehouse_code LIKE 'ISS143-%';
  DELETE FROM warehouses WHERE warehouse_code LIKE 'ISS143-%';
  DELETE s FROM skus s JOIN products p ON s.product_id=p.id WHERE p.name LIKE 'ISS143探针商品%';
  DELETE FROM products WHERE name LIKE 'ISS143探针商品%';" >/dev/null
LEFT_PO=$(dbq "SELECT COUNT(*) FROM purchase_orders WHERE warehouse_id IN ('$WA','$WB');")
[ "$LEFT_PO" = "0" ] && ok "ISS-143 探针仓名下无采购单残留" || {
  dbq "DELETE i FROM purchase_order_items i JOIN purchase_orders po ON po.purchase_order_id=i.purchase_order_id
  WHERE po.warehouse_id IN ('$WA','$WB');
  DELETE FROM purchase_orders WHERE warehouse_id IN ('$WA','$WB');" >/dev/null
  LEFT_PO=$(dbq "SELECT COUNT(*) FROM purchase_orders WHERE warehouse_id IN ('$WA','$WB');")
  [ "$LEFT_PO" = "0" ] && ok "ISS-143 探针仓名下无采购单残留（补删一次后为 0）" || bad "ISS-143 探针仓名下仍有 $LEFT_PO 张采购单残留（删仓没带走名下采购单：采购域清理失效）"
}
DANGLING_NOW=$(dbq "SELECT COUNT(*) FROM purchase_orders p LEFT JOIN warehouses w ON w.id = p.warehouse_id WHERE p.warehouse_id IS NOT NULL AND w.id IS NULL")
[ "$DANGLING_NOW" = "$DANGLING_T0" ] && ok "ISS-142 全库断链采购单未新增（基线 $DANGLING_T0 → 清场后 $DANGLING_NOW）" || bad "全库断链采购单 $DANGLING_T0 → $DANGLING_NOW：探针删仓没按子表先删，采购域被掏成断链"
LEFT_SKU=$(dbq "SELECT COUNT(*) FROM skus WHERE sku_code LIKE 'ISS143-%';")
[ "$LEFT_SKU" = "0" ] && ok "探针 SKU 已清（残留 $LEFT_SKU）" || bad "探针 SKU 残留 $LEFT_SKU 行"

echo "通过 $P / 失败 $F"
[ "$F" -eq 0 ]
