# 第三方平台接口对接规范（核实版）

> 核对日期：2026-03-08 ｜ 核对方式：逐条从**公开可读的官方文档页**或**官方端点实测响应**取证
> 纪律：本文只记录有出处的结论。凡公开渠道读不到的，一律写在「未核实」里并给出需要谁来补，
> **不允许**用行业惯例或历史记忆填补端点名、参数名、签名串——参数名错了适配器就是废的。
> 计量口径：本文覆盖 11 种对接协议（`PlatformAuthType` 的 11 个枚举值，含本仓库自用的 `CUSTOM_SHOP`）的**规范核实**。
> 适配器实现另算，实况见 §1 的表——核实到规范 ≠ 写好了适配器。
> 🔄 **2026-10-21 复核**：`PlatformAuthType` 仍是 11 个值（`domain/platform/valueobject/PlatformAuthType.java:17-58`：`CUSTOM_SHOP/SHOPIFY/MAGENTO/AMAZON_SP_API/EBAY_SELL/TEMU/TIKTOK_SHOP/ALIEXPRESS/SHOPEE/MERCADO_LIBRE/OZON`），口径未漂；兄弟枚举 `PlatformName`（业务侧平台标识，不参与选适配器）现为 **12 个值**——`PlatformName.java:9-20` 的 11 家 + `OTHER`，其中 `WISH` 只在 `PlatformName` 里存在、`PlatformAuthType` 没有对应协议。两枚枚举不是同一套清单，写校验时别拿一边的成员去收另一边的值。

## 0. 为什么要这份文档

`docs/spec/platform-integration.md` 记录的是「已完成 Shopify 端到端 + 地基」，`docs/qa/known-issues.md` 的
**ISS-064** 记录了那轮交付的证据边界：所有「已完成」都建立在本地 fixture 上，没有一次真实平台调用。
本文是那轮的后续：按用户指示「先继续完成其他平台代码开发和接口的适配，后面我再统一来填写相关的密钥来进行接口的测试」，
把范围从 Shopify 扩到 **Amazon SP-API、eBay、Temu、TikTok Shop、速卖通、Shopee、Mercado Libre、Ozon、Magento** 共 9 家。

三家平台的核实结果**推翻了实现前的预设**，这三处如果不核实就会直接写出错的适配器：

| 预设 | 核实结论 | 影响 |
| --- | --- | --- |
| Amazon LWA 令牌端点是 `api.amazon.com/auth/o2/token` | 官方页写的是 **`www.amazon.com/ap/oath/v1/token`**（另有 `amazon.ru`/`amazon.in` 变体） | 用错 host 换不到令牌 |
| Magento 商品列表可用 `searchCriteria[filter_groups]...field=name&value=xxx` | 官方原文：**`like` 条件要求通配符 `*` 或 `%`**，裸词不匹配；且现版文档已把 admin 取令牌端点改成 2FA | 拉不到商品；换令牌要按实例二选一 |
| eBay 订单列表用 `order_status=`/`creation_date_range=` 独立参数、且没有 `offset` | 官方 OpenAPI spec（`fulfillment_api.json` v1.20.6）里 `getOrders` **只有** `fieldGroups\|filter\|limit\|offset\|orderIds` 五个查询参数，`getAllOrders` 已不存在，过滤全部走单个 `filter=` 参数 | 散文页写的参数名发出去会被忽略或报错，增量拉取直接失效 |

## 1. 平台清单与对接状态

两列口径分开，因为它们经常被人混着说：

- **规范**：这家平台的端点/认证/签名/分页是否已从公开官方出处核实。🟢 全部核实 ｜ 🟡 主体核实、有明确的未核实项（ISS-065）｜ ⚪ 核实不到
- **适配器**：`backend/.../infrastructure/platform/` 下是否真有按 `PlatformAuthType` 注册的实现。🟢 已实现并有 fixture 契约测试 ｜ ⚪ 无适配器

适配器一列是 **2026-03-08 深夜重测**的——口径是**实现了某个数据线端口、且 `type()` 返回该协议键**的类数
（不是引用该枚举的文件数，实测引用文件数普遍更高：`SHOPEE` 5 个文件引用但只有 3 个适配器类）：
`CUSTOM_SHOP` 7、`SHOPIFY` 4、`OZON` 4、`MAGENTO` 4、`EBAY_SELL` 3、`SHOPEE` 3、`AMAZON_SP_API` 2、
`MERCADO_LIBRE` 2、`ALIEXPRESS` 2，`TIKTOK_SHOP` 与 `TEMU` **0**；另有 `SAMPLE` 6（只有 6 个拉取端口，无发货回写、无连通性）。
**合计 37 个适配器类。**
> ⚠️ 本节此前写的是「合计 41 个 bean」，那是把**另一个注册表**的 4 个 `PlatformTokenExchange` 也计进了适配器数
> （eBay/Shopee/Amazon/Mercado 各 1 个），于是这 4 家的协议计数各自虚高 1。37 + 4 = 41，两个注册表必须分开报。

令牌交换是独立 registry（`PlatformCredentials` 内部按协议索引，与 `PlatformFetcherRegistry` 无关），
全仓**只有 4 家注册**：Amazon LWA、eBay OAuth、Shopee、Mercado。即只有这 4 个协议能在令牌临期时自动刷新并回写；
`SHOPIFY/OZON/MAGENTO/ALIEXPRESS/CUSTOM_SHOP` 走静态令牌，过期只能操作员重填。
七家新平台共 33 个主源文件 + 20 个契约测试类、238 个 `@Test`。

> 🔄 **2026-10-21 复测：本节数字逐条命中，无需订正**。`infrastructure/platform` 下共 **61 个主源文件**、`implements Platform*` 的类 **41 个**——其中 **4 个是 `PlatformTokenExchange`**（`amazon/AmazonLwaTokenExchange`、`ebay/EbayOAuthTokenExchange`、`shopee/ShopeeTokenExchange`、`mercado/MercadoTokenExchange`），减掉正好 37 个适配器类；按协议数与原文一致：`CUSTOM_SHOP` 7（`HttpPlatform*` 七件套）、`SAMPLE` 6、`SHOPIFY` 4、`OZON` 4、`MAGENTO` 4、`EBAY_SELL` 3、`SHOPEE` 3、`AMAZON_SP_API` 2、`MERCADO_LIBRE` 2、`ALIEXPRESS` 2、`TIKTOK_SHOP`/`TEMU` 0（`infrastructure/platform/` 下根本没有 `temu/`、`tiktok/` 包）。七家新平台的 33 个主源文件 = aliexpress 3 + amazon 4 + ebay 5 + magento 6 + mercado 4 + ozon 5 + shopee 6；测试 20 个类 / **238 个 `@Test`**（aliexpress 17、amazon 30、ebay 26、magento 52、mercado 29、ozon 44、shopee 40）也逐项对得上。**唯一变动在总量**：platform 测试包现在是 **26 类 / 291 例**（`shopify/` 29、`oauth/PlatformOAuthClientTest` 7、根级共用件 17、七家新平台 238）；其中能与原文逐项对上的只有 Shopify 一项——原文 27 例，现为 **29 例**（`ShopifyAdapterMappingTest` 17 + `ShopifyAdminApiContractTest` 12）。§12 标准 5 的「456 个 `@Test`」即由此过期，已就地订正。

| # | 平台 | `PlatformAuthType` | 认证形状 | 规范 | 订单 | 商品 | 发货回写 | 连通性测试 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 通用自建店铺 | `CUSTOM_SHOP` | `X-API-Key`/`X-API-Secret` 头 | 🟢（本仓库自有约定） | 🟢 | 🟢 | — | 🟢 |
| 2 | Shopify | `SHOPIFY` | `X-Shopify-Access-Token` 静态令牌 | 🟢 | 🟢 | 🟢 | 🟢 | 🟢 |
| 3 | Amazon SP-API | `AMAZON_SP_API` | LWA 刷新令牌 → 1h Bearer | 🟡 | 🟢 | ⚪ | ⚪ | 🟢 |
| 4 | eBay Sell | `EBAY_SELL` | OAuth2 刷新令牌 → 2h Bearer | 🟡 | 🟢 | ⚪ | 🟢 | 🟢 |
| 5 | 速卖通 | `ALIEXPRESS` | TOP 网关签名 + `session` | 🟡 | 🟢 | ⚪ | ⚪ | 🟢 |
| 6 | Shopee | `SHOPEE` | 签名 + OAuth2 刷新（轮换） | 🟢 | 🟢 | 🟢 | ⚪ | 🟢 |
| 7 | TikTok Shop | `TIKTOK_SHOP` | 签名 + OAuth2 刷新（轮换） | 🟡 公开文档是 202212（v1_deprecated）代 | ⚪ | ⚪ | ⚪ | ⚪ |
| 8 | Mercado Libre | `MERCADO_LIBRE` | OAuth2 刷新（轮换） | 🟡 | 🟢 | ⚪ | ⚪ | 🟢 |
| 9 | Ozon | `OZON` | `Client-Id` + `Api-Key` 静态头 | 🟢 | 🟢 | 🟢 | 🟢 | 🟢 |
| 10 | Magento | `MAGENTO` | 用户名口令换 Bearer / 直填集成令牌 | 🟡 | 🟢 | 🟢 | 🟢 | 🟢 |
| 11 | Temu | `TEMU`（枚举有值） | 签名（算法未核实） | ⚪ | ⚪ | ⚪ | ⚪ | ⚪ |

第 3–10 行的「规范」列与「适配器」列现在都成立了，但 🟢 只代表 **fixture 契约测试通过**，不代表过一次真实调用
（ISS-064）；每家探测不到的响应键都逐条记在 §3.4/§4.3/§5.2/§6/§7/§8/§9 与 ISS-065。

有意留白（不是遗漏，是无法核实的部分被显式省略而不是编造）：Amazon 无商品/发货端口，eBay 与 Mercado 无商品端口，
Mercado/AliExpress/Shopee 无发货回写端口，`PlatformCustomerFetcher`/`PlatformLogisticsFetcher`/
`PlatformWarehouseFetcher`/`PlatformFinanceFetcher` 九家新平台全部没有实现（`CUSTOM_SHOP` 与 `SAMPLE` 有）。
注册表缺适配器时抛 `PLATFORM_ADAPTER_NOT_FOUND`，不会静默返回空集，因此不是隐性故障。

## 2. 共用地基

### 2.1 凭据字段与列
`platform_integrations` 本轮新增 3 列（`ddl-auto=update` 只会 ADD，符合 ADR-0002）：

| 列 | 领域字段 | 用途 | 敏感 |
| --- | --- | --- | --- |
| `region` | `region` | 选 host：Amazon `NA/EU/FE`、Magento `store_view_code` | 否 |
| `external_shop_id` | `externalShopId` | Shopee/TikTok `shop_id`/`shop_cipher`、Mercado `user_id` | 否 |
| `marketplace_ids` | `marketplaceIds` | Amazon `marketplaceIds`、eBay `marketplace_id` | 否 |

`api_key`/`api_secret`/`access_token`/`refresh_token` 四列在仓储边界加解密（`CredentialCipher`，AES-256-GCM，
前缀 `enc:v1:`，主密钥只从 `PLATFORM_CREDENTIAL_MASTER_KEY` 读，ADR-0009）。非敏感标识明文存，
~~否则会占掉 255 字节的 `api_key` 列预算（密文比明文长 ~128 字节）~~ → 🔄 **2026-10-21 复测：这个理由已随列加宽失效**——四列现在都是 **`varchar(1024)`**（`PlatformIntegrationJpaEntity:69`、`:72`、`:78`、`:84`，实体注释写明是为「冒烟 7.5」的长令牌 `Data too long` 才放宽），活库 `SHOW CREATE TABLE` 一致；三条非敏感列也实测在位：`region varchar(32)`、`external_shop_id varchar(128)`、`marketplace_ids varchar(512)`。**但「非敏感标识明文存」的策略本身没变**，变的是它不再受 255 列宽约束，别再引用「128 字节预算」做论证。

> 🔄 **2026-10-21 新发现（结构事实，影响 docs/db 与 ADR-0002）**：活库里 `platform_integrations` 的 `platform_name`、`auth_type`、`status` 三列都是 **MySQL 原生 `ENUM`**（标签与 Java 枚举常量一一对应，`platform_name` 12 个标签含 `OTHER`/`WISH`，`auth_type` 11 个），主键 `id` 是 **`binary(16)`**；本表由 Hibernate 6.6（Spring Boot 3.5.9）在 `ddl-auto: update` 下生成，这代 Hibernate 对 `@Enumerated(STRING)` 直接落原生 ENUM。后果有两条：①新增枚举常量时 `ddl-auto: update` **不会**扩展既有列的 ENUM 标签（它只做建表/加列），严格模式下写入新值会报 `1265 Data truncated`，必须手工 `ALTER TABLE … MODIFY`——这与 R-53「库结构可复现」的假设相冲突；②这是**模块级差异**：同样叫 `status`，本表有 DB 级约束，而 `purchase_orders.status` 是裸 `varchar(255)`（见 `purchase.md` 命名陷阱与 §6），`orders.status` 又是 ENUM——数据字典不能一律写成 varchar。全库现有 **26 个原生 ENUM 列**分布在 **18 张表**（6 张 `*_field_mappings` 各 2 个 = 12、6 张 `*_import_jobs` 各 1 个 = 6、`audit_logs`/`customers`/`inventory_records`/`logistics_orders`/`orders` 各 1 个 = 5、本表 3 个），仅 `platform_integrations` 用 `binary(16)` 主键。

### 2.2 令牌刷新（本轮补上的真实缺口）
`refresh_token` 列一直存在，但**此前从未被用于任何令牌端点调用**——Shopify 是长期令牌所以没暴露问题，
Amazon SP-API 只有刷新令牌、没有长期令牌，不刷新就完全无法外呼。

新增：`PlatformOAuthClient`（表单/JSON 两种令牌请求 + 容错解析 `expires_in`/`expire_time`/`expires_at`）
+ `PlatformTokenExchange` SPI（按协议注册，`rotatesRefreshToken()` 声明是否轮换）
+ `PlatformCredentials.resolve()`（临期→刷新→`applyToken`→`repository.save` 落库）。

刷新结果**必须**落库：不落库则每次外呼都重新换令牌（Amazon 令牌端点限流 1 次/秒会立刻被打爆），
且 Shopee/Mercado/TikTok 的刷新令牌是一次性的，不回写新值等于下次刷新必然失败。

> 🔄 **2026-10-21 复测：上面这条「必须」只是设计意图，代码里没有强制执行**。`PlatformCredentials.java:77-84` 对 `repository.save(integration)` 的异常是 **catch 后 `log.error` 继续返回本次新令牌**，注释理由（`:80-81`）是「落库失败不该作废这次调用」。后果正是上一段所担心的：数据库瞬时故障时，Shopee/Mercado/TikTok 这类**一次性刷新令牌**已经作废、新值又没落库，下一次外呼只能拿旧 refresh token 去换，必然失败且只能重填凭据；Amazon 则会退化成每次外呼都刷新（限流风险）。要堵住得改成「落库失败即让本次调用失败并在错误信息里要求重填」，或在 outbox 里重试——目前两者都没有，记为 §12 标准 4 的残留缺口。

各协议是否轮换刷新令牌（这决定 `rotatesRefreshToken()` 取值）：

| 协议 | 轮换 | 依据 |
| --- | --- | --- |
| Amazon SP-API | 否 | 官方「Don't store the refresh token... it does not expire」口径 |
| eBay | 否 | 文档未述及轮换，实测以响应为准 |
| Shopee | **是** | `refresh_token` "only used once" |
| Mercado Libre | **是** | 「REFRESH_TOKEN 只能使用一次」 |
| TikTok Shop | **是** | 6 天周期换新，新旧并行一年 |

### 2.3 共用传输
- `PlatformHttpExchange`：超时 + 429 按 `Retry-After` 退避 + 5xx/网络异常重试 + 401/403 立即失败并给可读原因。
  任何方法不把请求头写进日志或异常消息（凭据只可能出现在头里）。
  Shopify 例外：保留自己的 `ShopifyAdminApi`（`Link` 头游标语义特殊且已有 27 例契约测试覆盖）。
- `PlatformSignatures`：MD5 / HMAC-MD5 / HMAC-SHA256 的 hex 原语 + TOP 系「字典序拼 k+v」工具。
  **大小写显式分两个方法**：TOP 要 32 位大写，Shopee/TikTok 要小写 hex。
- `PlatformJson`：从 `ShopifyJson` 提升为跨平台通用件（多平台响应信封形态不同但取集合/按路径取值同构）。

## 3. Amazon Selling Partner API

### 3.1 host 与区域
- 生产：`https://sellingpartnerapi-na.amazon.com` / `-eu.` / `-fe.`（三套独立 host）
- 沙箱：同 host 加 `/sandbox` 路径前缀（**不是** `sandbox.sellingpartnerapi-*`，后者是已弃用口径），
  且「即使开发者资料尚在审核中也可用沙箱」
- 区域由 `region` 选；`storeUrl` 不用填（`hostFromConfig=true`）

### 3.2 认证
`POST https://www.amazon.com/ap/oath/v1/token`（`amazon.ru`/`amazon.in` 为对应区域变体），
`application/x-www-form-urlencoded`：`grant_type=refresh_token&refresh_token=&client_id=&client_secret=`。
响应 `access_token` 有效期 **1 小时**、`expires_in=3600`。请求头 `x-amz-access-token`，**没有 SigV4 步骤**
（官方示例明确「no signing information」）。

grantless（`grant_type=client_credentials`）的 scope 形如
`sellingpartnerapi::notifications`、`...:client_credential:rotation`、`...shipments:track`。

### 3.3 接口
- 订单（现行）：`GET /orders/2026-01-01/orders`（`searchOrders`），参数 `lastUpdateStart`/`lastUpdateEnd`、
  `marketplaceIds`、`nextToken`。**PII 改为角色权限**，不再是 RDT 受限数据令牌。
- 订单（v0，仍广泛在用）：`GET /orders/v0/orders`，PascalCase 参数
  `CreatedAfter`/`LastUpdatedAfter`（二选一必填）、`MarketplaceIds`（必填，≤50）、
  `OrderStatuses`、`MaxResultsPerPage`（1-100）、翻页 `NextToken`。
  速率 0.0167 rps / burst 20；v2026-01-01 为 0.0056 rps / burst 20。
- 受限数据令牌：`POST /tokens/2021-03-01/restrictedDataToken`，
  `restrictedResources[]{method,path,dataElements}` ≤50。
- 商品：SP-API 没有「卖家商品列表」这类同步 REST 接口，目录侧只有
  `CATALOG_2022_04_01`/`CATALOG_2020_12_01` 的 `searchCatalogItems`（按 ASIN/关键词搜平台目录，不是卖家自有 SKU 清单）；
  卖家自有列表要走 `LISTINGS_2021_08_01`。因此本仓库的商品拉取走 **Reports** 异步通道。
- 报表：`POST /reports/2021-06-30/reports` → 轮询 `GET /reports/2021-06-30/reports/{reportId}`
  到 `DONE`（`processingStatus` ∈ IN_QUEUE/IN_PROGRESS/CANCELLED/DONE/FATAL）→
  `GET /reports/2021-06-30/documents/{reportDocumentId}` 拿预签名 URL（**5 分钟过期**）→ 直接 HTTP GET 下载。
  当前 `FBASTOCK` 模板 `dataElements` 仅 `["all"]`。
  官方文档**没有** AES/INITVEC 解密步骤（历史上部分报表要解密，现文档不再述及；实现按不解密处理，
  真实环境若拿到密文需回头补，见 ISS-065）。

### 3.4 未核实（需要用户或平台方补）
- 发货回写：FBA 发货走 `FBY_OFFER_PREVIEW` 报表、MFN 走 `Merchant Fulfillment` 相关接口，
  公开文档未核实到可直接 POST 运单号的端点 → **没有实现 pusher**。
- 7 项 NOT-FOUND 记录在 ISS-065。

## 4. eBay Sell APIs

### 4.1 认证
`POST https://api.ebay.com/identity/v1/oauth2/token`（官方 OpenAPI spec 的 servers 现为
`https://apiz.ebay.com/sell/fulfillment/v1`，沙箱 **`https://apiz.sandbox.ebay.com`**，与生产同为 `apiz` 前缀）。
请求头 `Authorization: Basic base64(client_id:client_secret)`，表单体
`grant_type=refresh_token&refresh_token=&scope=https://api.ebay.com/oauth/api_scope/sell.fulfillment https://api.ebay.com/oauth/api_scope/sell.inventory`。
用户令牌 TTL **7200s**，刷新令牌 47304000s（≈18 个月）且**不轮换**。
scope 字面值（取自官方 spec `security`）：`.../sell.fulfillment`、`.../sell.fulfillment.readonly`。

### 4.2 接口
- 订单列表：`GET /sell/fulfillment/v1/order`（`getOrders`）。spec 的 parameters **只有**
  `fieldGroups`、`filter`、`limit`、`offset`、`orderIds` 五个；`getAllOrders` 端点在当前 spec 里已不存在，
  散文页里的 `order_status=`、`creation_date_range=`、`modification_date_range=` 这些**独立参数键**
  不在 spec 的 parameters 列表内，按它们发请求等于发不存在的参数。
  时间窗与状态一律写进 `filter`：区间语法 `creationdate:[<ISO>.. <ISO>]`、`lastmodifieddate:[…]`，
  枚举集合语法 `orderfulfillmentstatus:{…}`。
  **多个 `filter` 条件之间怎么组合（分号、竖线还是重复参数）spec 正文没给** → 实现只带一个条件，
  增量窗口用 `lastmodifieddate`（ISS-065）。
  `limit` ≤ **200**、默认 50。翻页**跟随响应体的 `href`/`next`**：`offset` 到底是「页码」还是「跳过的记录数」
  在 fulfillment spec 正文里没写明（同族的 `sell/inventory` 明确是页码、从 1 起），
  所以适配器不自己算 `offset`，只跟 `next`（ISS-065）。
- 商品：`sell/fulfillment/v1` 域**没有**商品/库存列表。商品在 `sell/inventory` 域：
  `GET /inventory_item`（`limit` 1–200、`offset` 是页码、从 1 起）与 `GET /offer`（`limit` ≤100），
  两者都要 `marketplace_id`，且需要 `sell.inventory` scope（与 fulfillment scope 不同一把）。
  → 本轮适配器只接 fulfillment 域，商品端口报明确错误码 `PLATFORM_PRODUCT_LIST_UNSUPPORTED`。
- 发货回写：`POST /sell/fulfillment/v1/order/{orderId}/shipping_fulfillment`，
  现版 schema `ShippingFulfillmentDetails` **required `lineItems`**（元素 `{id, quantity}`），
  `trackingNumber` 与 `shippingCarrierCode` 是 **body 字段**（「If you include one, you must also include the other」），
  官方 OpenAPI 标注 `readOnly: true, default: CREATED`（状态不可由卖家指定）。
  `shipTo` 已不在此版 schema；spec 中不存在 `fulfillmentProfile`/`sellerStories` 端点。
- 限流：429（带 `Retry-After`）、401 令牌过期；OAuth 配额 10k/50k 每次刷新。

### 4.3 未核实
- 「各市场共用 `api.ebay.com`」官方页未明文声明；spec 无 `marketplaceId` 路径参数。
- spec 已改版（`apiz.ebay.com`、去 `shipTo`、`trackingNumber` 移入 body），与旧版博客/SDK 记忆冲突
  ——编码前需与所连 eBay 应用的实际 revision 对齐（ISS-065）。

## 5. 全球速卖通（TOP 同源网关）

### 5.1 网关与协议
- 生产：`http://gw.api.taobao.com/router/rest`、`https://eco.taobao.com/router/rest`；
  海外：`https://api.taobao.com/router/rest`。默认取 `gw.api.taobao.com`，`storeUrl` 可覆盖。
- 公共参数：`method`、`app_key`、`session`（= 卖家授权 `access_token`，可选）、`timestamp`
  （`yyyy-MM-dd HH:mm:ss`）、`v=2.0`、`sign_method`、`sign`、`format`。
- 签名：三种 `md5` / `hmac`(HMAC-MD5) / `hmac-sha256`。`sign` 自身不参与，
  「除去sign参数和byte[]类型的参数」；按 key 字典序拼 `k+v`；
  `md5(secret + 拼接串 + secret)` 取 **32 位大写**；`hmac` 分支**不包 secret**（secret 是 HMAC 密钥）。
  `hmac-sha256` 的拼接细则公开页未展开 → 沿用 HMAC 分支口径（大写、不包 secret），标注待实测。
- GET 仅用于调试，**正式调用必须 POST**，业务参数 URL 编码后放 body。
- 响应：成功 `{"aliexpress_trade_seller_orderlist_get_response":{…}}`，
  失败 `{"error_response":{"msg":"Remote service error","code":50,"sub_msg":"非法参数","sub_code":"isv.invalid-parameter"}}`。

### 5.2 接口
- 订单列表：`aliexpress.trade.seller.orderlist.get`（**不是** `sellers.orders.list`）。
  入参对象 `param_aeop_order_query{create_date_start/end, modified_date_start/end, current_page, page_size}`，
  **`current_page`/`page_size` 必填且从 1 起**（与 TOP 通用文档说的 0 起不同）。`total_page` 判翻页。
- 订单详情：`aliexpress.trade.trade.get.single.result`（apiId=19620）取收件人与明细。
- 发货回写：`aliexpress.logistics.order.shipment`（apiId=58036）；
  备选 `aliexpress.solution.order.fulfill`（必填 `_service_name`/`_out_ref`/`_send_type`/`_logistics_no`）。
- 商品列表：公开可核实的只有**联盟**接口 `aliexpress.traffic.product.search`（需单独权限包，
  返回平台推荐商品而非卖家自有商品），卖家自有商品列表方法要登录 `open.aliexpress.com` → 🟡。

## 6. Shopee Open Platform v2

- host：生产 `https://partner.shopeemobile.com`（另有 `openplatform.shopee.cn`、
  `openplatform.shopee.com.br`），沙箱 `https://openplatform.sandbox.test-stable.shopee.sg/`。
  **沙箱与生产共用同一 `access_token`**，靠请求头 **`x-env-type`**（`sandbox`/`production`）切换。
- 签名：**`base_string = partner_id + apiPath + timestamp`**，Shop 级接口再拼 `access_token` + `shop_id`
  （Merchant 级拼 `merchant_id`；Public 级不拼 token），
  `sign = HMAC_SHA256(key = partner_key, base_string)` → **hex 全小写**。
  注意拼接口径与常见「path+partner_id+timestamp」写法**顺序不同**。
- 公共参数放 query（含 `access_token`、`shop_id`、`partner_id`、`timestamp`、`sign`），业务参数放 JSON body。
- 授权：**不是标准 OAuth2，没有 `grant_type`**。授权页 `https://open.shopee.com/auth`
  （参数 `partner_id`、`auth_type=seller`、`redirect_uri`、`response_type=code`）；
  换令牌 `POST /api/v2/auth/token/get` body `{code, partner_id, shop_id|main_account_id}`；
  刷新 `POST /api/v2/auth/access_token/get`。`access_token` 4 小时，`refresh_token` 30 天且**一次性**。
- 订单：`POST /api/v2/order/get_order_list`，必填 `time_range_field`(create_time/update_time)、
  `time_from`、`time_to`（**跨度上限 15 天**）、`page_size`；可选 `cursor`、`order_status`、
  `response_optional_fields`。翻页看响应 `more` + `next_cursor`。
  取件人明细要再调 `POST /api/v2/order/get_order_detail`（`order_sn_list[]`，须显式列
  `response_optional_fields` 如 `shipping,buyer,invoice,item_list` 才返回）。
- 商品：`POST /api/v2/product/get_item_list`（**不在 `item/` 下**），必填 `offset`、`page_size`(≤100)、
  `item_status[]`，翻页看 `has_next_page` + `next_offset`。
- 发货：`POST /api/v2/logistics/ship_order`（**不是 `shipment/ship`**），必填 `order_sn`，
  按 `get_shipping_parameter` 的 `info_needed` 决定 `pickup`/`dropoff`/`non_integrated` 哪组必填。
- 响应外层：`{request_id, error, message, warning, response}`，`error` 为空即成功。

## 7. Mercado Libre

- 令牌：`POST https://api.mercadolibre.com/oauth/token`，`grant_type` ∈ `client_credentials` /
  `authorization_code` / `refresh_token`。授权页官方**强制** `https://auth.mercadolibre.com/authorization`
  （明确禁止 `mp.aliexpress.com` 一类老域名，「用户将不再能够授权您的应用」）。
  `access_token` 6 小时（示例给 `expires_in:10800`，实现**以响应 `expires_in` 为准**），
  刷新令牌约 6 个月且**一次性、会轮换**。
- 认证头：`Authorization: Bearer <token>`。
- 错误信封（官方端点实测）：`{"message":"invalid client_id or client_secret","error":"invalid_client","status":400,"cause":[]}`。
- 订单（CBT 跨境口径）：`GET /marketplace/orders/search`，
  参数 `seller.id`、`limit`、`offset`、`date_created.gte|lt`、`last_updated.gte|lt`（**ISO-8601 带偏移字符串**）、
  `mediations.stage`、`sort` + `order`。响应 `{results[], paging{total,offset,limit}}`。
  国内版 `seller_id`/`from`/`to`/`status` 形式正文未核实到（ISS-065）。
- 商品：`GET /users/{user_id}/items/search`，`offset` 只到约 1000，更多要用 `search_type=scan` + `scroll_id`。
  `limit` 上限未核实。
- 发货：只核实到读端点 `GET /marketplace/shipments/{id}`（须带 `x-format-new: true`）与
  `GET /shipments/{id}/orders`；状态枚举 pending/ready_to_ship/shipped/delivered/cancelled。
  **卖家自发货上传运单号的写端点未核实** → 无 pusher。
- 限流：HTTP 429 `local_rate_limited`，配额按站点（国家）独立；具体数值未公开。

## 8. Ozon Seller API

- host：只有 `https://api-seller.ozon.ru`，**强制 https**。没有 OAuth、没有刷新、**没有签名**。
- 认证头：`Client-Id`（→ `apiKey`）+ `Api-Key`（→ `apiSecret`），缺一即 401。
  401 响应体 `{code:<int>, message:"…"}`（`code` 是数字不是字符串）。
- 时间：**所有 `*At` 字段是带时区偏移的 RFC3339**（`2025-07-08T00:00:00+08:00`）。
  裸 `LocalDateTime` 会被判 `bad time`——这是实现最容易踩的坑。
- 订单（FBS，卖家自发货）：`POST /v3/posting/fbs/list`
  body `{dir, sort, filter:{since, until, limit, offset, status?}, with:{...}}`，`filter.limit` 上限 1000。
  响应 `{result:{posts[], has_more}, has_more}`。
  **列表不返回明细**，须逐单 `POST /v2/posting/fbs/get`（body `{posting_number, language?}`）。
  FBO 用 `/v3/posting/fbo/list`，无 `/v4/posting/fbo/list`。
- 商品：**没有商品列表接口**。`POST /v3/product/list`（`filter{visibility}` + `last_id` + `limit`）只给 `product_id[]`，
  再 `POST /v4/product/info/attributes`（`filter{product_id[], visibility}`，`limit` ≤1000、默认 100，
  `last_id` 翻页、返回 `id` 而非 `product_id`）取属性。
- 店铺仓库：`POST /v2/warehouse/list`（**不是 `/v1`**）。
- 发货回写：`POST /v2/posts/add`，body `{status:"awaiting_delivery", tracking_number, posts:[{posting_number, tracking_status}]}`。
  不是 `/v4/...`、不是 `tracing_number`。
- 没有公开沙箱/联调环境（仅 `client-id=215000371` 一处示例）。错误码 429 可能存在但未文档化；
  Api-Key 过期返回 500。

## 9. Magento / Adobe Commerce REST

- URL 形态：`https://<host>/rest/<store-view-code>/<V1 endpoint>`；`store-view-code` 缺省即 `default`。
- 换令牌：`PUT /rest/V1/integration/admin/token`，body `{"username":…, "password":…}`，
  **成功响应是纯字符串令牌**（示例 `asdf3hjklp5iuytre`），不是 JSON 对象。
  ⚠️ 现版文档里 admin 取令牌教程已改用 2FA 端点
  （`POST /V1/tfa/provider/google/authenticate`，payload 含 `otp`），
  `integration/admin/token` 字面路径在当前 developer.adobe.com 检索不到（仅 ≤2.3 版本文档出现）
  → 适配器同时支持「用户名口令换令牌」与「直接粘贴 `accessToken`」（Commerce Cloud/SaaS 只能走后者）。
  有效期：Admin 默认 4 小时、Customer 1 小时。
- 订单：`GET /rest/<store>/V1/orders`，`searchCriteria[filter_groups][0][filters][0][field|value|condition_type]`、
  `searchCriteria[sortOrders][0][field|direction]`、`searchCriteria[pageSize]`、`searchCriteria[currentPage]`。
  ⚠️ **`like` 必须自带通配符 `*` 或 `%`**。`condition_type` 全集含 eq/gt/in/like/nlike/null/from/to/finset。
  `pageSize` 不给则返回全部匹配（无官方默认值/上限）。响应外层 `{items[], search_criteria, total_count}`。
  字段白名单：`field[]=status&field[]=created_at`，列表里 `status` 是**标签字符串**。
- 商品：`GET /rest/<store>/V1/products`，同 `searchCriteria` 形态。
- 发货：现役教程用 `POST /rest/<store>/V1/order/<orderId>/ship`，
  body `items[{order_item_id, qty}]`、`notify`、`tracks[{track_number, title, carrier_code}]`。
  （`POST /V1/shipment/` + `entity_id` 形态只在 adobe-commerce.redoc.ly 可查，域名不在本次允许清单内。）
- 搜索参数是**大小写敏感**的数组语法，`filter_groups` 之间是 AND、组内 `filters` 之间是 OR。

## 10. 落不了地的两家：Temu 与 TikTok Shop

官方网关实测（无凭证逐级探测，2026-03-08）已证实的部分：
- CN `https://openapi.kuajingmaihuo.com/openapi/router`、US `https://openapi-b-us.temu.com/openapi/router`、
  EU/GLOBAL `openapi-b-eu.temu.com` / `openapi-b-global.temu.com`；
  测试环境 `https://openapi-b-us.temudemo.com/openapi/router`（`requestId` 前缀 `ust-`）。
- 信封：接口名放 body 的 **`type`** 字段；必填 `app_key`、`timestamp`、`access_token`、`sign`。
- 失败响应：`{"success":false,"requestId":"cn-…","errorCode":7000000,"errorMsg":"there is no type in body."}`
  （US 网关同结构、`errorCode:3000002`）。

**没有适配器的原因**：签名算法（MD5/HMAC、参数拼接序、secret 位置）、订单/商品/发货三类接口的 `type` 字符串、
`timestamp` 单位、成功态响应结构，全部只记载于需登录卖家账号的文档站
（`seller.kuajingmaihuo.com/sop/view/634117628601810731`、`partner.kuajingmaihuo.com/document`，
均为 JS 壳，正文接口 `POST /pelargir/api/sop/queryContent` 无会话时返回 `{"error_code":405}`；
`open.temu.com` / `developer.temu.com` DNS 不可达）。
乱猜 `type` 值只会命中 `access_token not exists`，无法枚举。**需要业务方用卖家账号导出上述文档页作为唯一依据。**

### 10.1 TikTok Shop —— 规范能核实到的是平台自己标注已弃用的那一代

公开出处能证实的部分（由前一轮核实后写进 `PlatformAuthCatalog` 的 `TIKTOK_SHOP` note，本文不重复宣称）：
`app_key`/`app_secret` 对应 `apiKey`/`apiSecret`；授权码换 `access_token`、每 7 天用 `refresh_token` 续期
且刷新令牌会轮换；`externalShopId` 填 `shop_cipher`；签名与 OAuth 形状按公开文档核实过。

**没有适配器的原因**：公开文档描述的接口是 **202212 代**（`POST /api/orders/search` 一类），平台自己在页面上标
`v1_deprecated`；现役的 **202309 代**接口路径与 `X-Tts-*` 请求头规范只有登录 Partner Center 才读得到。
2026-03-08 复核时，两次公开检索都没能回到可访问的官方规范页，`developers.tiktok.com` 直接抓取失败
（`fetch failed`），因此没有任何可以据实取数的出处。

在 `v1_deprecated` 上写适配器会给出「已对接」的假信号（真填钥匙大概率 404），所以与 Temu 同样处理：显式留白，
注册表按 `PLATFORM_ADAPTER_NOT_FOUND` 喊出来。要落地需要业务方从 Partner Center 导出 202309 代的
订单/商品/发货接口页与签名规范。

## 11. 填钥匙的操作顺序

1. 配好主密钥：`export PLATFORM_CREDENTIAL_MASTER_KEY=<32字节随机密钥的Base64>`。
   **不设的话 `CredentialCipher` 只打 WARN 然后静默关闭加密**（ISS-011 未关）。
   🔄 **2026-10-21 补测**：`application.yml:66` 的默认值是空串（`${PLATFORM_CREDENTIAL_MASTER_KEY:}`），`scripts/docker/docker-compose.yml` 与 `scripts/docker/mysql/init/01-init.sql` 都没有注入这个变量，全仓也搜不到其它注入点——**本机这套栈当前处于「加密关闭」状态**。另有一条原文没写的分支：密钥填了但**不是 32 字节 Base64** 时，`CredentialCipher.java:120` 会按 SHA-256 口令派生并再打一条 WARN，口径是「能跑但不合生产要求」。好消息是活库 `platform_integrations` **0 行**，即目前不存在已落库的明文凭据；风险窗口从「第一次按第 3 步建集成」开始。
2. 起后端 → 打开配置页（或直接 `GET /api/platform-integrations/auth-specs`）看每家要填什么。
3. 建集成：`POST /api/platform-integrations`，`authType` 选协议，按 `requiredFields` 填。
4. `POST /api/platform-integrations/{id}/test-connection` —— 这一步是 ISS-064 一直缺的「真实一次」；
   它打真实令牌/只读端点，成功即证明凭据、host、区域三者都对。
5. 通了再激活、再同步；同步结果里的错误码要对着本文各家的「未核实」看，
   未核实项失败不代表实现有错。

## 12. 完成标准（本轮）

| # | 标准 | 状态 |
| --- | --- | --- |
| 1 | 九家平台的端点/认证/签名/分页都有公开官方出处 | 🟢 8 家成立；Temu 明确不成立并记为 ⚪ |
| 2 | 每家都有按 `PlatformAuthType` 注册的适配器，缺端口报明确错误码 | 🟡 7 家（Amazon/eBay/速卖通/Shopee/Mercado/Ozon/Magento）已注册并有契约测试；TikTok Shop 与 Temu 仍为 0，两家都卡在「当前代接口规范只在登录后的卖家/Partner Center 里」（§10、§10.1）；缺端口一律 `PLATFORM_ADAPTER_NOT_FOUND` |
| 3 | 凭据不落明文、不出现在出参与日志 | 🟢（`PlatformToken.toString` 亦掩码） |
| 4 | `refresh_token` 真正被使用且轮换值回写 | 🟢 `PlatformCredentials.resolve` 已接进 8 个应用服务的 10 处取凭据点（拉单/商品/仓库/客户/财务/物流/连通性测试/发货回写），OAuth 协议在令牌缺失或临期时先换再回填落库。真实一次成功调用仍归标准 6。🔄 **2026-10-21 逐处复核仍成立**：`OrderImportApplicationService:439`、`PlatformIntegrationApplicationService:213,256`、`ProductImportApplicationService:418`、`FinanceImportApplicationService:421`、`ShopWarehouseApplicationService:127`、`WarehouseImportApplicationService:345`、`LogisticsImportApplicationService:492`、`CustomerImportApplicationService:384,400` = 8 个类 / 10 处；`resolve()` 内的回填在 `PlatformCredentials:75-78`（`applyToken` 后 `repository.save`，仅轮换协议带新 refresh token） |
| 5 | 有 fixture 契约测试断言签名可复算、请求形状、翻页推进、429 退避 | 🟢 ~~全仓 456 个 `@Test` 全绿~~ → 🔄 **2026-10-21 实测：全仓 801 个 `@Test`，最近一次 `mvn -o test` 为 102 个测试类 / 801 例 / 0 失败**（与 `@Test` 计数 801 完全吻合，无 `@ParameterizedTest`）；七家新平台 238 例 + Shopify ~~27~~ → 🔄 **29 例**（`ShopifyAdapterMappingTest` 17 + `ShopifyAdminApiContractTest` 12）+ 共用令牌解析 7 例（`PlatformOAuthClientTest`）。429 退避在 `PlatformHttpExchange` 一处覆盖，各家契约测试复用 |
| 6 | 真实平台一次成功调用 | 🔴 仍未做（ISS-064），要等用户填钥匙。🔄 **2026-10-21 补证：`platform_integrations` 活库 0 行**——不只是「没做过真实调用」，本环境连一条集成记录都还没有，§11 的第 3 步起从未执行过 |
| 7 | 配置界面能自助回答「这家要填哪几把钥匙」 | 🟢 `GET /api/platform-integrations/auth-specs` |

标准 6 没做就不要再说「已对接完成」——这正是 ISS-064 立 issue 的理由，本轮不改变那个结论。
标准 2 的另一半（TikTok/Temu）不是工作量问题：没有可核实的官方规范，写出来的适配器就是凭想象编程，
所以按 §10/§11 的口径显式留白并等业务方导出文档。
