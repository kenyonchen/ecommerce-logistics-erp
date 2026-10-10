# 功能规格 · 物流管理（核心域）

| 项 | 内容 |
|---|---|
| 子域 | 核心域（Logistics Bounded Context） |
| 聚合根 | `LogisticsOrder` |
| 实体 | `LogisticsChannel` |
| 值对象 | `LogisticsStatus`、`LogisticsLabel`、`LogisticsFee`、`PriceRule` |
| 领域服务 | `LogisticsOrderProcessingService`、`LogisticsTrackingService`、`LogisticsChannelService` |
| 数据表 | `logistics_orders`、`logistics_channels`、`logistics_labels`、`logistics_fees` |
| 前端 | `frontend/user-web/src/pages/logistics/{LogisticsList,LogisticsDetail,CreateLogistics}.jsx` |

---

## 1. 模块定位

物流直接决定客户体验与毛利。本上下文负责：

1. 为一个订单生成一张**物流单**（运单），绑定渠道、面单、运费、追踪号；
2. 维护**物流渠道档案与价格规则**，为比价提供基础；
3. 维护**物流状态**，为客服与买家提供轨迹。

> 当前的价值缺口：渠道无法在系统内维护（无 REST 端点）、面单是真桩、没有轨迹明细。因此物流模块目前只能演示"一张运单的状态变更"。

---

## 2. 概念模型

```mermaid
erDiagram
    logistics_channels ||--o{ logistics_orders : "logistics_channel_id"
    logistics_labels   ||--o| logistics_orders : "logistics_label_id"
    logistics_fees     ||--o| logistics_orders : "logistics_fee_id"
    orders             ..> logistics_orders   : "order_id 裸引用"
```

| 概念 | 说明 | 关键字段 |
|---|---|---|
| 物流单 | 一次发货的载体 | `logisticsOrderNumber`(唯一)、`orderId`、`status`、`trackingNumber` |
| 物流渠道 | 承运方案 | `channelName`(唯一)、`channelCode`(唯一)、`active`、`basePrice`、`pricePerGram` |
| 面单/标签 | 贴包裹的运单标签 | `labelData`(TEXT)、`labelUrl`、`labelFormat` |
| 运费 | 一次发货的费用明细 | `baseFee`、`weightFee`、`totalFee`、`currency`（**2026-10-10**：在**创建物流单**时按渠道口径预估并落 `logistics_fees`，由 `logistics_orders.logistics_fee_id` 指向；发货/状态推进不重算，见 BR-04 与 [../qa/known-issues.md](../qa/known-issues.md) R-55） |
| 价格规则 | 计费参数 | `basePrice`、`pricePerGram` |

---

## 3. 状态机

```mermaid
stateDiagram-v2
    [*] --> PENDING: 创建物流单
    PENDING --> LABEL_GENERATED: 生成面单
    LABEL_GENERATED --> COLLECTED: 物流商揽收
    PENDING --> COLLECTED: 揽收
    COLLECTED --> IN_TRANSIT: 起运(含清关/干线)
    IN_TRANSIT --> DELIVERING: 到达目的国开始派送
    DELIVERING --> DELIVERED: 签收完成
    DELIVERING --> FAILED: 派送失败(拒收/地址错)
    IN_TRANSIT --> FAILED: 运输异常(丢失/退回)
    FAILED --> CANCELLED: 确认退件并作废
    DELIVERED --> [*]
    CANCELLED --> [*]
```

| 枚举名 | 存储值 | 中文 | 业务含义 |
|---|---|---|---|
| `PENDING` | `pending` | 待处理 | 运单已创建，等待打单 |
| `LABEL_GENERATED` | `generated` | 已打单 | 面单已生成，等待揽收 |
| `COLLECTED` | `collected` | 已揽收 | 物流商已取件 |
| `IN_TRANSIT` | `in_transit` | 运输中 | 干线段（跨境场景常见于干线+清关） |
| `DELIVERING` | `delivering` | 派送中 | 尾程派送 |
| `DELIVERED` | `delivered` | 已送达 | 买家签收 |
| `FAILED` | `failed` | 配送失败 | 需要人工介入：重发/退款 |
| `CANCELLED` | `cancelled` | 已取消 | 退件或作废 |

> ⚠️ **无状态迁移校验**：`PUT /api/logistics/{id}/status` 可把任意状态改为任意状态（含"已送达→待处理"）。建议补迁移白名单（ISS-005）。

---

## 4. 业务规则

| 编号 | 规则 | 状态 | 说明 |
|---|---|---|---|
| BR-01 | 一张物流单必须绑定一个物流渠道（`nullable=false`） | ✅ | `logistics_orders.logistics_channel_id` |
| BR-02 | 物流单号 `logisticsOrderNumber` 必须唯一 | ✅ | 列上有唯一约束（但需在领域层生成，注意别重蹈订单号 ISS-001 的覆辙） |
| BR-03 | 物流单通过 `orderId` 关联订单（裸引用，无物理外键） | ✅ | 见数据字典 R-01 |
| BR-04 | 运费 = 基础费 + 重量费 | ✅ | **2026-10-10 收口（原 🟡"有模型未落库"＝ISS-137）**：公式收敛为 `LogisticsChannel.estimateFee(weightInGrams)` 单一来源，试算端点 `GET /api/logistics-channels/{id}/fee` 转调同一函数；**创建物流单时**按 `basePrice + pricePerGram × Σ(SKU 克重 × 件数)` 预估并随单落 `logistics_fees`，`logistics_orders.logistics_fee_id` 回填。口径见 [../qa/known-issues.md](../qa/known-issues.md) **R-55~R-57**：发货/打单/状态推进不重算不回填，平台导入回传的费用不覆盖，SKU 缺重量按 0 克（只收首重、不拒建），预估不做 `supportsCountry` 校验 |
| BR-05 | 状态迁移必须符合 3 图的边 | ⚪ | 接口无校验（ISS-005） |
| BR-06 | 每次状态变更应产生一条轨迹明细（时间 + 节点 + 操作来源） | ⚪ | 无轨迹表，见 US-202 |
| BR-07 | 生成面单后回写 `trackingNumber` 到订单/物流单 | 🟡 | 字段存在，生成逻辑是桩 |
| BR-08 | 渠道停用后不可被新运单选择 | ⚪ | 渠道无维护端点 |
| BR-09 | 计费重取实重与抛重较大者 | 🟡 | **2026-10-10 改判（原 ⚪"无抛重公式"）**：**实重这一半已接通**——计费重＝`Σ(SKU 克重 × 件数)` 并真正参与落库（BR-04）。**抛重仍缺**：无体积重公式（长×宽×高/除数），`ProductSku` 也没有尺寸字段（只有 `volumeInCubicCentimeters` 未被任何计费路径读取），所以"较大者"的比较无从发生。另注意库里 `skus.weight` 现 **0/88 行有值**，续重在现有数据上恒为 `0.00`——这是**录入缺口不是代码缺口**（接口与表单都有该字段，见 [product.md](product.md) BR-06） |
| BR-10 | 物流费用自动驱动财务应付流水 | ~~⚪~~ → ✅ | **2026-10-10 收口（ISS-138 / R-58、R-59，见 [../qa/known-issues.md](../qa/known-issues.md) §22）**：**入账时点＝「货已交出」**（物流单进入 `PICKED_UP`／`IN_TRANSIT`／`DELIVERING`／`DELIVERED`／`DELIVERY_FAILED` 之一），**建单与打单不入账**；金额**只读已在库的 `logistics_fees.total_fee`/`currency`，不重算**（渠道改价不拖动历史运费）；幂等判据＝`(reference_id=物流单 id, reference_type='LOGISTICS_ORDER', type=EXPENSE)`，同一张单重复发货/重复推状态仍是 1 条、金额不变；入账即挂 `pending`（待对账后由 `PUT /api/finance/transactions/{id}/complete` 转 `success`）。四条门＝手工揽收 `ship()` / 批量发货 `shipSingle()` / 建单即发货 `createLogisticsOrder()` / 状态推进 `persistStatusChange()`（`LogisticsApplicationService:256/413/138/440`）。**删除侧同步收口（R-59）**：删物流单或订单级联删除会撤销名下运费流水——已过账 `success` **挡住删除并回 HTTP 400 + 可读原因**，挂账中 `pending` **置 `cancelled` 留痕而不删行**。⚠️ 三条实测口径：`type`/`status` 在库里是**枚举 code**（`expense`/`pending`），JSON 侧给**枚举名**（`EXPENSE`/`PENDING`），前端按名过滤；`PageResult` 的字段名是 `content` 不是 `records`；历史无费用行的单子**宁可台账少一行也不猜金额**。常驻位＝`FreightPostingGuardTest` 8 例 + `LogisticsFreightPostingTest` 13 例 + `FreightExpenseReversalTest` 4 例 + 探针 `scripts/smoke/iss138-freight-posting-probe.sh` **45 条断言**（2026-10-10 15:40 复跑 45/45、`RC=0`）。仍缺的一半＝平台物流导入回传的那条路径不入账（口径待定，见 §8） |
| BR-11 | 配送失败需生成待办，提醒客服跟进 | ⚪ | 无待办机制 |

---

## 5. 功能清单

| 功能 | 前端 | 后端 | 状态 |
|---|---|---|---|
| 创建物流单 | ~~`CreateLogistics.jsx`（仅弹提示，未提交）~~ → 🔄 **2026-03-22 复测：已真实提交**（`CreateLogistics.jsx:87` `logisticsApi.createLogistics(...)` → `:95` `message.success` → `:96` `navigate(/logistics/${id})`；另 `:47` 拉渠道、`:64` 按订单号查单） | `POST /api/logistics`（`@RequestBody CreateLogisticsRequest`，`LogisticsController:67-68`） | ~~🟡~~ → 🟢 |
| 物流单列表 | `LogisticsList.jsx`~~（mock）~~ | `GET /api/logistics` | ~~🟡 无分页无筛选~~ → 🟡 **2026-01-25 逐页复测**（判据：页面内 `*Api.` 调用点数）：**后端已改为分页检索**（`LogisticsController.java:73`起，返回 `PageResult<LogisticsOrderDto>`，支持 `trackingNumber`/`orderId`/`channelId`(或渠道编码)/`status`/`keyword`/`startDate`/`endDate`/分页；前端入口 `api.js:177` 的 `logisticsApi.getLogisticsOrders(params)`），该页有 6 个调用点 |
| 物流单详情 | `LogisticsDetail.jsx`~~（mock）~~ | `GET /api/logistics/{id}`（`api.js:178`）、`GET /api/logistics/order/{orderId}` | ~~🟡~~ → 🟡 **2026-01-25 逐页复测**（判据：页面内 `*Api.` 调用点数）：8 个调用点，已联网。⚠️ 状态流转仍只挡终态回退、中间态可乱跳（ISS-005） |
| 按订单查物流单 | ~~未使用~~ | `GET /api/logistics/order/{orderId}`（`api.js:179`） | ~~🟡~~ → 🟢 2026-03-22：入口已接 |
| 变更物流状态 | ~~无入口~~ → 🔄 `LogisticsDetail.jsx:84` `updateStatus` | `PUT /api/logistics/{id}/status` | ~~🟡 无迁移校验~~ → 🟡 **2026-03-22 复测：仍只挡终态回退**（ISS-005 未修）；本轮另加 `POST /{id}/ship`（`:123`）、`POST /{id}/cancel`（`:132`，`LogisticsApplicationService:205+` 已拒"已揽收/在途/已送达"的取消）、`POST /batch-ship`（`:158`）、`DELETE /{id}`（`:169`）、`GET /statuses` 字典（`:90`） |
| **物流渠道管理** | ~~无页面~~ → 🔄 **`pages/logistics/LogisticsChannels.jsx`**（`:131` 调 `calculateFee`） | ~~**无端点**~~ → 🔄 **2026-03-22 复测：孤儿聚合已接上，`LogisticsChannelController` 6 个端点**（`GET /api/logistics-channels`:45、`GET /{id}`:57、`POST`:65、`PUT /{id}`:77、`PUT /{id}/status`:100、`DELETE /{id}`:108；前端 `api.js:200-208` 六个方法一一对应） | ~~⚪ **孤儿聚合**~~ → 🟢 **已解除**；⚠️ 代价：`GET` 是 `Result<List<LogisticsChannelDto>>` **全量返回、无分页**（M-07），且该类**无方法级 `@PreAuthorize`**（M-06 的 10 个之一） |
| **面单生成/打印** | ~~无入口~~ → 🔄 `LogisticsDetail.jsx:167`、`LogisticsList.jsx:220` 均有「生成面单」按钮 | ~~无端点~~ → 🔄 **`POST /api/logistics/{id}/label`**（`LogisticsController:138-139` → `LogisticsApplicationService:171-187 generateLabel`，`api.js:186`） | ~~⚪ 桩~~ → 🟡 **端点已真实但内容仍是半桩**：`generateLabel` 组的是 `LogisticsLabel.builder()`（`trackingNumber` + `buildRecipientInfo(order)` + **空的 `PackageInfo.builder().build()`** + `labelFormat("PDF")`），**没有任何 PDF 字节流、条码生成、渠道回传**；`logistics_labels` 表存在（`docs/db/schema.sql:217`）；已发 `LogisticsLabelGeneratedEvent`，但因 ISS-062（`:185-187` 发布的是 `save()` 副本）该事件到不了发布器 |
| **轨迹明细** | ~~无~~ → 🔄 `LogisticsDetail.jsx:96` `addTrace` | ~~无表无端点~~ → 🔄 **2026-03-22 复测：有端点、有存储**——`POST /api/logistics/{id}/track`（`:144`，拉取刷新）与 `POST /{id}/traces`（`:150`，`AddTraceRequest`/`LogisticsTraceDto`），存法不是独立表而是 `logistics_orders.tracking_traces` 列（`LogisticsOrderJpaEntity:90` `@Column(length=4000)`，`LogisticsOrderMapper:64/94` 用 JSON 序列化往返，聚合内 `LogisticsOrder:324-330` **上限 100 条**，超出从头截断） | ~~⚪~~ → 🟡 **无真实渠道回传**（`track` 是本地追加而非调承运商），且 4000 字符 + 100 条的截断意味着长轨迹会静默丢历史 |
| **费用试算与比价** | ~~无~~ → 🔄 `LogisticsChannels.jsx:131` | ~~无端点~~ → 🔄 **`GET /api/logistics-channels/{id}/fee?weight=&country=`**（`LogisticsChannelController:115-116`，返回 `Result<LogisticsFee>`；前端 `api.js:207-208`） | ~~⚪~~ → 🟡 **单渠道试算可跑，但"比价"仍不存在**（没有跨渠道择优端点）；⚠️ `LogisticsFee` 是值对象却直接做出参（ISS-063 契约泄漏，域内唯一一处） |

> **"孤儿聚合"** 指领域对象与数据表存在、但没有任何 REST 入口的情况。这类设计会导致数据只能靠初始化脚本或手工 SQL 写入，业务上等于不可用。

---

## 6. 接口对照

| 场景 | 方法 | 路径 | 备注 |
|---|---|---|---|
| 创建物流单 | POST | `/api/logistics` | `tenantId` 通过 `@RequestParam` 传入，未做租户校验 |
| 按 ID 查询 | GET | `/api/logistics/{id}` | — |
| 按订单查询 | GET | `/api/logistics/order/{orderId}` | 支持一个订单多运单（拆单） |
| 全量查询 | GET | `/api/logistics` | 无分页 |
| 变更状态 | PUT | `/api/logistics/{id}/status` | 无迁移校验 |

---

## 7. 领域事件

| 事件 | 期望下游 | 现状 |
|---|---|---|
| `LogisticsOrderCreatedEvent` | 生成运费应付流水 | ⚪ **且判读已改（2026-10-10 R-58）**：这一行原本把"建单"当作入账触发点，与 BR-10 的收口口径不一致——**建单不入账**（货还在自己仓里，记应付＝提前确认负债）。运费实际在"货已交出"那一组状态上入账 |
| `LogisticsStatusChangedEvent` | 写入轨迹明细；`DELIVERED` 时推动订单完成 | ⚪ |
| `LogisticsLabelGeneratedEvent` | 通知仓库打印；回写面单 URL | ⚪ |
| `LogisticsCompletedEvent` | 触发收入确认、结清运费 | 🟡 **2026-10-10 部分达成，且走的是另一条通道**：运费应付流水**不由这个事件驱动**，而是由发货编排在事务内直接调用 `postFreightExpense`（`LogisticsApplicationService:138/256/413/440`）——理由是同步入账不留"已发货但台账还没记"的最终一致窗口，且金额必须取已在库的费用行。`LogisticsCompletedEventConsumer:27-32` 至今仍是只打一行"记账规则未定义"的空壳，**"收入确认"这一半仍 ⚪**（属 finance 侧 BR-04 的缺口）。见 [../qa/known-issues.md](../qa/known-issues.md) §22 |

---

## 8. 现状缺口与待办

| 优先级 | 缺口 | 建议 |
|---|---|---|
| **P1** | 物流渠道与价格规则无 REST 端点 | 新增 `/api/logistics/channels`、`/api/logistics/channels/{id}/price-rules` 完整 CRUD |
| **P1** | 缺轨迹明细 | 新增 `logistics_traces(物流单ID, 状态, 描述, 节点, 时间)` 表 + `GET /api/logistics/{id}/traces` |
| **P1** | 状态迁移无校验 | 在 `LogisticsOrder` 内实现 `changeTo(LogisticsStatus)` 白名单校验 |
| ~~**P1**~~ → ✅ 已完成 | ~~前端三个页面全部 mock~~ → **2026-01-25 逐页复测**（判据：页面内 `*Api.` 调用点数）：`LogisticsList.jsx`(6)、`LogisticsDetail.jsx`(8)、`CreateLogistics.jsx`(2)、`BatchShipModal.jsx`(2)、`LogisticsChannels.jsx`(≥1) 均已调真实接口 | ~~补齐 `logisticsApi` 并接入真实接口~~（`api.js:174-197` 的 `logisticsApi` 已到位） |
| **P2** | 面单是桩 | 引入 PDF 生成（如 iText / flying-saucer），并定义渠道面单模板 |
| **P1** | 平台物流导入链路不入账（BR-10 的已知边界）：`LogisticsImportApplicationService.processRow` 自己 `logisticsOrderRepository.save(target)`（`:403`）并用 `updateStatus(...)` 直接置状态（`:399-401`），**不经过** `LogisticsApplicationService` 的四个发货门 ⇒ 平台行带 `totalFee` 时会落一行 `logistics_fees`（`:378-383`，只有总额没有首重/续重拆分）却**永远 0 笔 `expense` 流水** | 先定口径再改：导入是"补录历史事实"（应一次性按导入行入账）还是"外部系统只读镜像"（不该在本系统入账）。前者给导入编排加一个显式入账步骤复用 `postFreightExpense`，后者在文档与页面明确标注"导入单不入账" |
| ~~**P2**~~ → 🟡 部分完成 | ~~无计费重/分区/时效~~ **首重续重已在 2026-10-10 生效并落库**（BR-04 / R-55~R-57，公式 `LogisticsChannel.estimateFee`）；仍缺：目的国分区费率、抛重系数、时效 | 扩展 `PriceRule`：目的国分区、抛重系数、时效（首重续重一项可划掉） |
| **P2** | 无外部承运商 API 对接 | 定义 `CarrierClient` 接口（下单/取号/取面单/查轨迹），先实现 1 家做样板 |
| **P3** | 异常件无处理流 | 新增重发/退回/理赔子流程 |

---

## 9. 验收要点

1. 创建物流单必须选择有效渠道；渠道不存在时报错。
2. 物流单号唯一；同一订单可存在多张运单（拆单场景）。
3. 非法状态迁移（如"已送达→待处理"）必须被拒绝。
4. 每次状态变更后，轨迹明细应新增一条记录（当前无此能力，作为 v0.2 验收项）。
5. 运费 = 基础费 + 重量费，金额与币种字段完整。**2026-10-10 实测达成**（探针 `scripts/smoke/iss137-freight-persistence-probe.sh` 23 条断言）：以 `首重 8.40 / 每克 0.0050` 的渠道 + 500 克 ×1 的订单项建单 ⇒ `baseFee 8.40`、`weightFee 2.50`、`totalFee 10.90`、`currency CNY`，且**响应、二次 GET 回读、`logistics_fees` 库行三处逐位一致**；三条创建路径（HTTP 建单 / 下单事件自动建单 / 批量发货自建）同结果；负例＝`ship` 之后三值与全库费用行数都不变（不重算不重复落库）、无重量 SKU 仍建单成功且只收首重。界面侧（R-18 真浏览器实测，user-web 5120 + 真实后端，**13:30/13:31 复验**）：详情页 `运费` 描述项渲染 `CNY ¥15.00(首重 ¥10.00 + 续重 ¥5.00)`、列表页同单那行运费列为 `CNY ¥15.00`（`LG202610101331233944431`）；列表里两条**修复前**落下的历史单（`LG202610092345234275853`、`LG202610091805212321219`）仍为 `-` —— 那是 R-55"不回填"的界面后果，不是渲染缺陷。场景数据实测后由 API 删除自清场，回读 `lo_rows=2 / fees=0 / 孤儿=0 / SKU 残留=0 / 审计残留=0`。
6. 运费在"货已交出"时自动产生一笔 `expense` 流水（BR-10 / R-58，**2026-10-10 实测达成**）。探针 `scripts/smoke/iss138-freight-posting-probe.sh` **11 节 / 45 条断言 / 0 失败**（15:40 复跑，`RC=0`），按"每一类入口一条常驻位"铺开（R-44）：**正向四门**＝门 A `POST /api/logistics/{id}/ship`（手工揽收）、门 B `POST /api/logistics/batch-ship`（批量发货，走"复用下单事件自动派生的 PENDING 单"那条分支）、门 C `POST /api/logistics` + `shipImmediately=true`（建单即发货）、门 D `PUT /api/logistics/{id}/status` 推进到「运输中」；判据一律取**库行 + 财务接口回读**而不是写端点的 `Result.code`——门 A 的 `SELECT COUNT(*),amount,currency,status,tenant_id FROM financial_transactions WHERE reference_id=<物流单id> AND reference_type='LOGISTICS_ORDER' AND type='expense'` 返回 **`1 | 13.40 | CNY | pending | <与物流单同租户>`**，且 `GET /api/finance/transactions/by-reference` 回读同值 `1|13.40`、`GET /api/finance/transactions?type=EXPENSE&referenceType=LOGISTICS_ORDER` 也能查到（`首重 8.40 / 每克 0.0050` × 500 克 ×2 ⇒ `13.40`，金额直接读 `logistics_fees.total_fee` 落库值，**不重算**）。**幂等一节**＝同一张单再发一次货 + 再推一次状态，分录仍**只有 1 条、金额不变**。**负例三门**＝门 E 建单只预估运费不入账、门 G 历史无费用行的单发货不入账（不凭空编金额）、门 H `PUT /orders/{id}/status` 订单侧发货不产生运费流水；另有门 D 内的"已打单（LABELED）不入账"半门。**撤销门 I（ISS-139 / R-59）**＝删单据要一起撤掉名下运费分录：挂账中 `pending` 被置 `cancelled` 且**行保留**（审计留痕，非删除），已过账 `success` **挡住删除**并回 HTTP 400 + 中文原因（`PUT .../complete` 后再删的实测路径）。界面侧 R-18 真浏览器实测（**user-web 财务台账 `/finance`，5120 + 真实后端 8082**，15:51；⚠️ 本轮订正：财务台账只在 user-web，`admin-web` 没有 finance 路由——`grep -n finance frontend/admin-web/src/routes.jsx` 0 命中，此前写成"admin-web"是错的）：列表行 `TXN-7cf91fcb | 支出 | ¥13.40 | CNY | 673b5d85-…（LOGISTICS_ORDER） | 待处理 | 2026/10/10 15:44:33`，详情页 9 个描述项齐备（交易ID／交易类型／交易金额／币种／交易状态／关联单据／创建时间／更新时间／交易描述），且详情页下方「关联流水」面板按 `673b5d85… / LOGISTICS_ORDER` 回读出**同一条**（`by-reference` 的 UI 侧复测）；console 全程只有已登记的 3 条 antd 废弃告警（`Statistic valueStyle`、`List` 废弃、`Space direction`，均在登录后的首页触发，`/finance` 列表与详情页 0 条新增）。⚠️ 同一次列表回读还暴露 **5 条 `留痕供应商` 的采购应付**（每轮冒烟留 1 条、无法经接口清）——登记为 [../qa/known-issues.md](../qa/known-issues.md) 的 ISS-142。场景数据由探针与 teardown 自清场（R-39），10 个计数器全 0（本轮另加：R-18 的浏览器场景由 `r18-purge.sh` 自行清场，15:56 逐条回读订单／物流单／本品流水／商品／渠道／仓／审计／孤儿费用行／全库活孤儿分录／幻影桶流水 **均为 0**、`PURGE_RC=0`），且**全库**"活孤儿运费分录"（`reference_type='LOGISTICS_ORDER'` 而源单已不存在且 `status<>'cancelled'`）实测 **0 行**。仍开放：平台物流导入链路不入账（见 §8）。
