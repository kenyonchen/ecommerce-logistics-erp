# 测试文档

> 核对自 `backend/src/test/`（~~**52 个测试类 / 456 个 `@Test`**，2026-03-22 工作树当日第二档；同日第一档 31 类 / 211 例，上一档 2026-01-25 = 26 类 / 164 例，历史口径 9→10 / 48→53 均已过期~~ → 🔄 **2026-03-22 第三档：54 个测试类 / 494 个 `@Test`**，分层 application 7 类/42 + domain 5 类/45 + infrastructure 32 类/320 + interfaces 10 类/87 = 54/494）与两个前端工程（无任何测试配置）。
> ⚠️ 本文档的首要目的：**给出真实的测试水位**。根 README 早期版本把测试列为「后续规划」，同时又将后端描述为「仅搭建基础结构」，两端都不准确——实际既有 494 个 `@Test` 方法，但**断言强度分布极不均**：实测 ~~`assert*` 调用点 1698 处、`verify(` 31 处~~ → 🔄 2026-03-22 **`assert*` 1761 处 / `verify(` 55 处（分布在 9 个类）**、`assertTrue(true)` = **0**，~~52~~ **54** 个类**每一类**都含 ≥3 处断言点（`assert*`/`verify(`/`andExpect`）其中 `infrastructure/platform/**` 的 25 个类就占了同一口径下的 1278 处调用点（本轮未变），剩下的旧 controller 用例里仍有「只验 `$.code==200`、不 stub 不 `verify()`」的弱断言（§2 逐条标注）。
> ✅ 2026-01-25 实测：`mvn test` **BUILD SUCCESS，26 类 / 164 用例 / 0 failures / 0 errors / 0 skipped**（数字取自 `target/surefire-reports/*.txt` 汇总，不采信管道退出码）。§3「大概率不能通过」的推断已被证伪，原因见该节。
> ✅ **2026-03-08 复测（当日第一档）**：`mvn -o test` **BUILD SUCCESS，`Tests run: 211, Failures: 0, Errors: 0, Skipped: 0`**。增量 = **5 个新测试类 / +47 例**，全部落在平台对接这一轮：`ShopifyAdapterMappingTest` 17、`ShopifyAdminApiContractTest` 10、`CredentialCipherTest` 9、`PlatformFetcherRegistryTest` 7、`PlatformIntegrationServiceImplTest` 4（其余 26 类的例数一律未变，逐项相加 31+28+71+81 = 211 可复核）。
> ✅ **2026-03-08 复测（当日第二档，本文件当前口径）**：`mvn -o test` **BUILD SUCCESS，`Tests run: 456, Failures: 0, Errors: 0, Skipped: 0`**。增量 = **21 个新测试类 / +245 例**，**每一例都在平台域**：七家新平台适配器的 fixture 契约测试 + 令牌刷新回归。分层实测 application 6 类/31、domain 4 类/28、infrastructure 32 类/316、interfaces 10 类/81；`infrastructure/platform/**` 从 4 类/38 扩到 **25 类/283**（aliexpress 2/17、amazon 2/30、ebay 2/26、magento 4/52、mercado 2/29、oauth 1/7、ozon 4/44、shopee 4/40、shopify 2/27、平台根包 2/11）。⚠️ 两档合起来 292 例**全是本地 `com.sun.net.httpserver.HttpServer` 桩 + 官方形状 fixture**，没有一例打到真实平台，因此 §3/§4 关于"不起上下文、不连库"的结论对新增部分同样成立，ISS-064「未经真实平台验证」更不能靠测试数量抵销。⚠️ 另一个新出现的空白：本轮把 `PlatformCredentials` 注入进了 **8 个应用服务**（10 处外呼取凭据点），而**全仓仍无 `@SpringBootTest`** ⇒ 这张变大的 bean 图没有任何装配级验证，构造器参数错位之外的装配问题（bean 缺失、循环依赖）只有启动才暴露。

> ✅ **2026-03-22 复测（第三档，本文件当前口径）**：`mvn -o test` **BUILD SUCCESS，`Tests run: 494, Failures: 0, Errors: 0, Skipped: 0`**（数字取 `target/surefire-reports/`，54 个 `.txt`）。增量 = **+2 个测试类 / +38 例**，**全部落在采购这一轮**（工作树 mtime 2026-03-08 22:22–23:19）：新增 `domain/purchase/aggregate/PurchaseOrderTest` **17 例**（真状态机测试：`pendingShouldOnlyBeApprovableRejectionOrCancellable`、`approvedShouldOnlyShipOrCancel`、`inTransitShouldOnlyArriveAndCannotBeCancelledAnymore`、`terminalStatusesAllowNoFurtherTransition`、`illegalTransitionShouldCarveItsOwnErrorCodeSoUiShowsTheReason`、`everyTransitionShouldRegisterStatusChangedEvent`、`happyPathShouldWalkTheWholeStateMachine` + 明细行不变式 6 例，24 处断言）与 `application/service/purchase/PurchaseApplicationServiceTest` **11 例**（到货入库 + 应付挂账编排、跨租户仓库拒建、一次调用持久化明细并只发一次事件、日期区间按整天展开、`statusChangesShouldPublishAggregateEvents`）；扩写 `PurchaseControllerTest` 9→**17**、`PurchaseOrderRepositoryImplTest` 3→**7**。

> 🔄 **2026-10-10 复测（当前口径）**：`src/test/java` 实测 **102 个测试类 / 801 个 `@Test`**（surefire 报告 102 个 `.txt`）。本文件此前停留在 2026-03-22 的 54 类 / 494 例，已严重滞后；§1 表与 §2 逐类评估须按 2026-10-10 重测值重写（分层分布、覆盖面矩阵中的控制器/应用服务/领域模块计数均待刷新）。断言强度分布、`assertTrue(true)=0`、纯单测不起上下文等定性结论仍成立。⚠️ **这三条改变了 §2 覆盖面矩阵的两处旧结论**：「`PurchaseOrder` 迁移表无测」不再成立（改由 `PurchaseOrderTest` 覆盖）、「persistence 1 类 3 例」的例数已翻牌。⚠️ **但水位判断不变**：新增 38 例仍是纯单测（`openMocks` + mock 仓储，不起上下文、不连库），ISS-062 那条"真实调用链上事件是否落到发布器"仍无任何测试断言，`@SpringBootTest` 仍为 **0**。

## 1. 测试水位速览

| 项 | 数值 | 说明 |
|---|---|---|
| 后端测试类 | ~~**52**~~ → ~~**54**~~ → **102**（2026-10-10；2026-03-08 同日第一档 31；2026-01-25 为 26） | `backend/src/test/java/site/hexaarch/ecommerce/logistics/`：分层分布与逐类清单见 §2（2026-10-10 重测值，此前 54 类口径已过期） |
| 后端 `@Test` 方法 | ~~**456**~~ → ~~**494**~~ → **801**（2026-10-10；同日第一档 211；2026-01-25 为 164） | 全部 JUnit 5 + Mockito 纯单测；分层相加（2026-10-10 待重测，此前 494 档 = application 42 + domain 45 + infrastructure 320 + interfaces 87） |
| 有效断言的测试 | ~~约 40~~ → ~~**54/54**~~ → **102/102 类都有真断言**（2026-10-10） | 断言点分布：~~`assert*` 调用点 1698 处 / `verify(` 31 处~~ → 🔄 2026-03-22 **`assert*` 1761 处 / `verify(` 55 处（9 个类）** / `assertTrue(true)` 0 处；弱断言（只验 `code==200`）集中在旧 controller 用例（`assert*` 总量 2026-10-10 待重测） |
| 框架 | JUnit Jupiter + Mockito（`MockitoAnnotations.openMocks(this)`） | **无** `@ExtendWith(MockitoExtension.class)` |
| Web 层测试方式 | `MockMvcBuilders.standaloneSetup(controller)` | **不起 Spring 上下文**、不加载 `SecurityConfig`、不连 DB |
| `@SpringBootTest` / 集成测试 / E2E | **0** | 全仓 grep 无命中 |
| `src/test/resources` / `application-test.yml` / `@ActiveProfiles` | **不存在** | |
| Testcontainers / 内存库（H2） | **未引入** | |
| 测试覆盖率工具（JaCoCo） | **未引入** | 无任何覆盖率数字 |
| 前端测试 | **0** | 两个 app 均无 vitest/jest/@testing-library/playwright/cypress，无 `test` 脚本 |
| CI | **不存在** | 无 `.github/`、`Jenkinsfile`、`.gitlab-ci.yml`、pre-commit、`mvnw` |

**结论：~~456~~ → ~~494~~ → **801** 个用例全绿（2026-10-10 实测 102 类；同日第一档 211、2026-01-25 那档 164），但它们全是脱离 Spring 上下文、脱离数据库的纯单测——自动化集成/端到层测试仍为 0，覆盖率门禁仍为 0。** 领域层已从「有效 0」补到 `FinancialTransactionTest`(5)、`OrderFieldMappingServiceTest`(5)、`OrderProcessingServiceTest`(14)、🔄 2026-03-22 新增 `PurchaseOrderTest`(17，**本仓第一份真正的聚合状态机测试**：非法迁移自带错误码、每次迁移注册 `PurchaseOrderStatusChangedEvent`、终态无后续迁移、明细行大小写去重与金额重算)；~~新增的 `PurchaseOrderRepositoryImplTest`(3)~~ → 🔄 现 **(7)**，仍是本仓第一个**用 ArgumentCaptor 断言 JPA 关系回填**的持久层测试（ISS-047 回归）；同轮补上 `PurchaseApplicationServiceTest`(11)——它是第一个**断言"应用服务在真实聚合实例上发布事件"**的测试（`statusChangesShouldPublishAggregateEvents`），但用的是 mock 仓储 + 入参实例，因此仍不能证伪 ISS-062（真实链路上交出去的是仓储映射副本）。<br>⚠️ **两档合计新增的 292 例没有改变这个水位**：它们把"平台适配器的请求形状与解析逻辑"测到了单元级（每家用本地 `HttpServer` 桩回放官方形状 fixture，逐家例数见上），但**没有一例打到真实平台**，所以 ISS-064 那条"未经真实平台验证"不能靠测试数量抵销；而且新平台的响应键大多是**候选键探测**（ISS-065 ⑩），fixture 绿只证明"按我们猜的键名解析得通"。§3/§4 正文里的 164 是**带时点的历史实测记录**，未改写。

## 2. 逐类评估

| 测试类 | 方法数 | 被测 | 断言质量 | 关键问题 |
|---|---|---|---|---|
| `domain/order/service/OrderProcessingServiceTest` | 3 | `OrderProcessingService` | ❌ **完全空洞** | 三个方法全是 `assertTrue(true)`（`:30/:36/:42`，注释「简化测试，直接通过」）；`@Mock OrderRepository` 构造后从未使用；全文无 `verify()`。**覆盖 = 0** |
| `interfaces/controller/OrderControllerTest` | 4 | `OrderController` | ⚠️ 真断言但预期失败 | `@Mock OrderMapper` 未 stub `toDto` → 返回 null → `$.data.orderId` 断言失败；未验证 controller 硬编码的 `"default-tenant"` |
| `application/service/ProductImportPipelineTest` | 5 | 导入流水线（CSV 解析→字段映射→行级校验→任务状态） | ✅ **本仓第一批高质量应用层测试**：`assertEquals` 逐字段断言映射结果、缺必填字段产生行级错误、`PARTIAL_SUCCESS` 计数（total/success/failure/skipped） | 无 `verify()`；只覆盖 product 一个上下文 |
| `interfaces/controller/ProductControllerTest` | 5 | `ProductController` | ❌ **写法错误** | `:54-58` 在同一 `when()` 里混用 3 个 `eq()` matcher 与 1 个裸字符串 → 运行时 `InvalidUseOfMatchersException`；且**未声明 `@Mock ProductMapper`**（controller 构造需要它）→ `toDto` NPE |
| `interfaces/controller/CustomerControllerTest` | 3 | `CustomerController` | ⚠️ 混合 | 前两个是真断言；`testDeleteCustomer`（`:83-89`）只验 `$.code==200`、不 stub 不 verify —— controller 的 try/catch 吞掉一切也照样通过。**未覆盖 404/异常分支** |
| `interfaces/controller/FinanceControllerTest` | 4 | `FinanceController` | ⚠️ 真断言但预期失败 | 未声明 `@Mock FinancialTransactionMapper`（构造需要）→ create 用例 NPE 被吞成 `code "500"` |
| `interfaces/controller/LogisticsControllerTest` | 4 | `LogisticsController` | ⚠️ 真断言但预期失败 | `testCreateLogisticsOrder` 只传 `orderId/logisticsChannelId`，缺 **必填** `tenantId` → 实际 400；stub 用 `eq("TEST_TENANT")` 对不上。**未覆盖 `GET /api/logistics`** |
| `interfaces/controller/PurchaseControllerTest` | ~~9~~ → **17** | `PurchaseController` | ~~⚠️ 真断言，1 处必失败~~ → ✅ **本仓断言最强的 controller 测试** | ~~create 用例 stub `eq("TEST_TENANT")`，而 controller 默认 `default-tenant` 且测试未传 → stub 不命中；无任何 `verify()`~~ → 🔄 2026-03-22 复测：该缺陷已修（ISS-043/051 回归），**17 例全部通过**；现在 **5 处 `verify()`**（`:149` `never()).findPurchaseOrderById`、`:227/:246/:261` `approvePurchaseOrder(TENANT, …)` 逐参数、`:276` `never()).rejectPurchaseOrder`），每个用例都显式 `.param("tenantId", TENANT)`；`:102-112` 另有一条**专门测缺省租户**的用例（stub `eq("default-tenant")` + 断言 `$.data.tenantId == "default-tenant"`），即"默认值"本身也被测到了，不再是漏洞 |
| `interfaces/controller/WarehouseControllerTest` | 6 | `WarehouseController` | ⚠️ 真断言但预期失败 | stub `eq("123")` 作 tenantId，controller 传 `default-tenant` → 不命中 |
| `interfaces/controller/PlatformIntegrationControllerTest` | 10 | `PlatformIntegrationController` | ⚠️ 混合 | 7 个真断言；**3 个 sync 用例（`:171-195`）不 stub service，只验 `code==200`** —— 而 sync 本身是桩实现，测的是「桩没报错」 |

> 🔄 2026-01-25 更正（针对上表逐类评估）：上表的方法数与「关键问题」是 2026-01-25 时点的快照，以下几条已不成立——
> · `OrderProcessingServiceTest` 从 3 个 `assertTrue(true)` 扩到 **14 个用例 / 49 处断言 / 6 处 `verify()`**，空断言已清零（**全仓 `assertTrue(true)` = 0**）。
> · `ProductControllerTest` 的 matcher 非法混用与缺 `@Mock` 已修，现为 6 个用例（`:103`、`:136` 用 `eq()/any()` 规范配对）。
> · `PurchaseControllerTest` 9→~~**11**~~ → **17**（10-02 那轮新增「不带 status 必须查全部」与「缺省租户」两条，正是 ISS-043 的回归；10-03 采购轮再加 6 条覆盖审批/在途/到货/取消/明细删除与日期区间，见上表）；`FinanceControllerTest` 4→**10**；`LogisticsChannelControllerTest` **11**（该测试类为新增）；`CustomerControllerTest` 3→**5**；`LogisticsControllerTest` 4→**8**；`WarehouseControllerTest` 6→**8**；`LogisticsImportControllerTest` **8**（10-03 新增，含 `verify()` 1 处）；`ProductImportPipelineTest` 5→**4**，另新增 `CustomerImportPipelineTest`(5)/`FinanceImportPipelineTest`(9)/`LogisticsImportPipelineTest`(6)/`ShopWarehouseImportPipelineTest`(5)/`ProductImportApplicationServiceTest`(2)。
> · 仍成立的：controller 测试全是 `standaloneSetup`，不加载上下文；`src/test/resources` 仍不存在；~~全仓仅 **7 个类**用到 `verify()`~~ → 🔄 2026-03-22：**9 个类 / 55 处**（新增 `PurchaseApplicationServiceTest` 19、`PurchaseOrderRepositoryImplTest` 6、`PurchaseControllerTest` 5）。
> · 新增的非 controller 覆盖：`JwtTokenProviderTest`(4)/`JwtAuthenticationFilterTest`(4)（ISS-041 回归）、`DomainEventPublisherTest`(6)、`RocketMQConfigTest`(5)、`HttpClientConfigTest`(2)（ISS-044 超时）、`HttpPlatformLogisticsFetcherTest`(4)、`FinancialTransactionTest`(5)、`OrderFieldMappingServiceTest`(5)、`PurchaseOrderRepositoryImplTest` ~~(3)~~ → **(7)**；🔄 2026-03-22 再补 `PurchaseOrderTest`(17，聚合状态机)、`PurchaseApplicationServiceTest`(11，到货入库+挂应付编排)、`PlatformIntegrationServiceImplTest`(4)、`PlatformFetcherRegistryTest`(7)、`CredentialCipherTest`(9)、`ShopifyAdapterMappingTest`(17)、`ShopifyAdminApiContractTest`(10) 以及 7 家新平台的 fixture 契约测试（`infrastructure/platform/**` 合计 **25 类 / 283 例**）。

### 覆盖面矩阵

| 维度 | 已覆盖 | 未覆盖 |
|---|---|---|
| Controller（~~13~~ → **实测 20**） | ~~8~~ → ~~**12**：`Order`/`Product`/`Customer`/`Finance`/`Logistics`/`Purchase`/`Warehouse`/`PlatformIntegration`/`LogisticsChannel` + 各 `*ImportController` 中被 pipeline 测试覆盖的部分~~ → 🔄 **2026-03-22 口径收紧：真正的 controller 级测试类只有 10 个 / 10 个控制器**（`Order` 4、`Product` 6、`Customer` 5、`Finance` 10、`Logistics` 8、`Purchase` **17**、`Warehouse` 8、`PlatformIntegration` 10、`LogisticsChannel` 11、`LogisticsImport` 8），相加 87 例 = interfaces 层总数；原写 12 是把 5 个 `*ImportPipelineTest`（**应用层**测试）算成了 controller 覆盖 | **`AuthController`、`TenantController`、`UserController`、`RoleController`、`ShopWarehouseController`、`WarehouseImportController`、`OrderImportController`、`ProductImportController`、`CustomerImportController`、`FinanceImportController`**（10 个）—— **零 controller 级测试**。⚠️ 这正是 ISS-061 逃逸的原因：`admin` 拿不到 `PLATFORM_ADMIN` 让 `/api/tenants/**`、`/api/users/**`、`/api/roles/**` 全部恒定 403，而 164 个单测当时**全绿**——不起上下文、不连库的测试摸不到这条链路（见 §3 结论与 [tech/ci-cd.md](tech/ci-cd.md) 的冒烟门禁）；🔄 2026-03-22 补一条同类缺口：**`@PreAuthorize` 在这 10 个 `standaloneSetup` 测试里根本不生效**（不加载 `MethodSecurityInterceptor`），所以 M-06 说的"10 个控制器无方法级鉴权"没有任何测试能暴露 |
| 领域模块（9） | 形式上 1（order），~~**有效 0**~~ → ~~**有效 1**（`OrderProcessingServiceTest` 14 例真断言 + 6 处 `verify()`；另有 `FinancialTransactionTest` 5 例）~~ → 🔄 2026-03-22 **有效 3**：`OrderProcessingServiceTest`(14)、`FinancialTransactionTest`(5)、**`PurchaseOrderTest`(17，采购聚合状态机 + 明细不变式)**，另有 `OrderFieldMappingServiceTest`(5)、`PlatformIntegrationServiceImplTest`(4)——domain 层实测 **5 个测试类 / 45 例**；应用层 `ProductImportPipelineTest` 等 5 个 pipeline 测试 | ~~其余模块的聚合不变式与状态机（`Warehouse`/`LogisticsOrder`/`PurchaseOrder`/`Customer` 的迁移表）仍无测~~ → 🔄 **`PurchaseOrder` 已从该清单移出**（由 `PurchaseOrderTest` 覆盖）；仍无测的是 **`Warehouse`/`LogisticsOrder`/`Customer`/`Order`/`Product` 的聚合迁移表与不变式**，以及 `Role`/`Tenant` 聚合 |
| 应用服务（~~11~~ → 🔄 2026-03-22 实测 **21 个类**：15 个 `*ApplicationService` + `BusinessProcessOrchestrationService`/`CustomUserDetailsService`/`LogisticsOrderResolver` + `User/Role/TenantServiceImpl`） | 作为 mock 出现 8 个（并未被测）；🔄 2026-03-22 **`PurchaseApplicationService` 已转为被测**（11 例，application 层实测 7 类 / 42 例） | `BusinessProcessOrchestrationService`、`CustomUserDetailsService`（🔄 2026-03-22 **上一轮的删除线是错的，此处恢复**：全仓 54 个测试类里没有一处引用该类，连 mock 都没有，而它的 authorities 组装正是 ISS-041/061 的关键路径）、`OrderCommandHandler`、`OrderQueryHandler`、`UserServiceImpl/RoleServiceImpl/TenantServiceImpl` 连 mock 引用都没有；🔄 2026-03-22 复测新增的空白：**`WarehouseApplicationService`（ISS-059/060 的修法处在其体内）、`LogisticsApplicationService`（10 处发布点，最多）、`OrderApplicationService`、`CustomerApplicationService`、`FinanceApplicationService`、`ProductApplicationService`(仅 `ProductImportApplicationServiceTest` 测其导入侧)、`ShopWarehouseApplicationService`、`LogisticsOrderResolver` 等仍只有 mock 身份或完全未测** |
| 基础设施 | ~~0~~ → ~~已有 6 类~~ → **实测 32 类**：platform **25**（`infrastructure/platform/**`，含 7 家新平台的契约测试 + `PlatformOAuthClientTest` 7）、security 3（`JwtTokenProviderTest` 4、`JwtAuthenticationFilterTest` 4、`CredentialCipherTest` 9）、messaging 2（`DomainEventPublisherTest` 6、`RocketMQConfigTest` 5）、config 1（`HttpClientConfigTest` 2）、persistence 1（`PurchaseOrderRepositoryImplTest` ~~3~~ → **7**）；platform 根包另有 `HttpPlatformLogisticsFetcherTest`(4)、`PlatformFetcherRegistryTest`(7)（ISS-041/044 的回归 + 本轮平台地基） | ⚠️ **本轮三个新固化点仍零测试**：① `UserRepositoryImpl`/`RoleRepositoryImpl` 的 `@Transactional(readOnly=true)`（ISS-061 的修法）——它只在**非 Web 线程**才暴露，纯单测永远测不出，必须靠连库/起上下文的集成测试；② `WarehouseMapper` 的 `@AfterMapping` 空值守卫与 `Warehouse.findInventoryRecord` 的聚合定位口径（ISS-059/060 的修法），同理需要真实 JPA 会话；③ **`PlatformCredentials` 的刷新→回写路径**：它有测试（`PlatformOAuthClientTest` 7 例测的是解析），但 8 个注入它的服务里只有 3 个（`FinanceImportPipelineTest:97`、`ProductImportApplicationServiceTest:75`、`CustomerImportPipelineTest:96`）在构造时传入了真实实例，而且传的是**空令牌交换注册表**（`List.of()`）⇒ 临期刷新与新令牌落库那两条分支在测试里从未走到。**这三条是引入第一个 `@SpringBootTest`（或 Testcontainers + MySQL）最直接的理由。** 其余：**12 个 `*Mapper`**（`Customer/FinancialTransaction/LogisticsChannel/LogisticsOrder/Order/PlatformIntegration/Product/PurchaseOrder/Role/Tenant/User/Warehouse`）、`TenantIsolationAspect`、MQ 消费者仍无测 |

## 3. `mvn test` 能否通过？——✅ 2026-01-25 实测：**能，全绿**

⚠️ 本节原标题「大概率不能」及其 5 条失败推断已在 2026-01-25 被实测证伪：`mvn test` 输出 `BUILD SUCCESS`，`Tests run: 164, Failures: 0, Errors: 0, Skipped: 0`。逐条对账：

1. ~~必填参数缺失~~ → 不再成立：controller 侧的租户参数与测试 stub 已对齐（`PurchaseControllerTest` 显式覆盖「传 `tenantId`」与「走 `default-tenant` 缺省」两条路径）。
2. ~~stub 与实现不一致~~ → 已修，Purchase/Warehouse 用例通过。
3. ~~matcher 非法混用~~ → `ProductControllerTest` 已改为 `eq()/any()` 全配对。
4. ~~缺少 `@Mock` 依赖~~ → 已补声明，注入不再为 null。
5. ~~`Result.code` 类型口径冲突导致断言失败~~ → **前提部分成立、结论不成立**：`interfaces/common/Result.java:18` 的 `code` 至今仍是 `String`（成功固定 `"200"`），但 `jsonPath("$.code").value(200)` 在 Spring 的 `JsonPathExpectationsHelper` 下会做期望值到实际值类型的折算，因此断言实测通过。**这条「测试会大面积失败」的推断是错的，但 String/int 的契约不一致本身仍然存在**——前端 axios 拦截器正是按字符串 `"500"` 判断业务失败并弹 `data.message`，属于跨端隐式约定，改动 `Result.code` 类型必须同步改前端（见 [../adr/0011-result-envelope-and-errors.md](../adr/0011-result-envelope-and-errors.md)）。

**仍然为真的限制**：这 164 个用例全部不起 Spring 上下文、不连数据库，所以它们证明的是「单元级逻辑与 mock 契约」，**不能证明任何落库/事务/租户隔离/JPA 映射行为正确**。本轮三个真实缺陷（ISS-047 采购项外键、ISS-048 `created_at` 被刷新、ISS-049 保留字建表失败）都是靠**连真实 MySQL 跑起来**才发现的，纯单测当时全部是绿的——这正是下面 §4 缺口的实证。

```bash
# 复现（需本机 Maven + JDK17，无需 DB/MQ）
cd backend && mvn -q test
```

> 建议先做第 4 节的最小修复让绿灯成为可信信号，再谈补测试。**当前即使有测试通过，也不代表功能正确**（~~`OrderProcessingServiceTest` 就是典型：它永远通过~~ → 🔄 2026-03-22：**这一类"空壳用例"已不存在**——该文件现为 14 例真断言 + 6 处 `verify()`，`assertTrue(true)` 全仓 **0** 处；逐类扫描 54 个文件后，controller 用例里也没有"只验 `$.code==200`"的空壳了（`OrderControllerTest` 现在断到 `$.data.orderId`/`statusLabel`，`CustomerControllerTest` 断到 `$.data.content[0].customerId`，§2 上表那批"预期失败/弱断言"是 2026-01-25 的快照）。**当前的"永远通过"风险换了形态，是环境性的**：`standaloneSetup` 不加载 `SecurityConfig` 与 `MethodSecurityInterceptor` ⇒ **29 处 `@PreAuthorize` 在任何测试里都不生效**；也不接全局异常出口——10 个 controller 测试类里只有 **2 个**手工 `.setControllerAdvice(new GlobalExceptionHandler())`（`PurchaseControllerTest:62`、`LogisticsChannelControllerTest:63`），其余 **8 个** 走裸 `standaloneSetup` ⇒ `BusinessException`/`EntityNotFoundException` 的映射根本不被执行；更不连 DB（H2/Testcontainers 均未引入）⇒ 唯一断言"异常→错误码"的用例是 `PurchaseOrderTest:83`（聚合层抛错自带错误码）。于是鉴权 403、异常 500 这类"应该失败"的路径**没有断言会失败**。这正是 ISS-061（`/api/tenants` 恒 403）能在 164 例全绿下逃逸的机制。）

## 4. 让测试重新可信的最小修复

按顺序，成本从低到高：

1. **统一 `code` 口径断言**：`jsonPath("$.code").value("200")`（或把 `Result.code` 改为 int，二者只能选其一并写进 [api.md](api/api.md)）。🔄 2026-03-22：**这一条已出现分裂的实证**——同一断言在 `OrderControllerTest:118` 写成整数 `value(200)`、在 `CustomerControllerTest:65` 写成字符串 `value("200")`，两者都通过（Spring 侧做了类型折算），所以它**不是失败点而是可读性/契约隐患**；`Result.code` 至今仍是 `String`。
2. ~~**补齐缺失的 `@Mock`**：`ProductControllerTest` → `ProductMapper`；`FinanceControllerTest` → `FinancialTransactionMapper`；并对 `toDto(...)` 做 stub。~~ → 🔄 2026-03-22 **已完成**：两个类的用例现在都真断言到 `$.data.*` 字段。
3. ~~**修正参数与 stub 一致性**：测试要么显式传 `tenantId`，要么按 controller 的 `defaultValue` 写 stub；`LogisticsControllerTest` 补上必填 `tenantId`。~~ → 🔄 **已完成且方式变了**：controller 侧不再依赖 `@RequestParam(defaultValue=…)`，而是统一走 `resolveTenant(...)`（`PurchaseController:264-272`），测试逐例显式传 `tenantId`，并由 `PurchaseControllerTest:99` 专门覆盖缺省路径。
4. ~~**删除或改写 `OrderProcessingServiceTest`**：`assertTrue(true)` 必须替换为真实断言~~ → 🔄 **已完成**：现为 14 例真断言 + 6 处 `verify()`，全仓 `assertTrue(true)` = **0**。
5. ~~**给 sync / delete 类用例补 `verify()`**，让「只验 code==200」的空壳变成行为断言。~~ → 🔄 **部分完成**：`verify()` 从 7 个类 / 31 处扩到 **9 个类 / 55 处**（新增 `PurchaseApplicationServiceTest` 19、`PurchaseOrderRepositoryImplTest` 6、`PurchaseControllerTest` 5）；**仍为 0 处 `verify()` 的是 `OrderControllerTest`、`CustomerControllerTest`、`ProductControllerTest`、`WarehouseControllerTest`、`FinanceControllerTest`、`PlatformIntegrationControllerTest`**——这几类的 delete/sync 用例依旧只看出参。

## 5. 应当补的测试（优先级）

| P | 目标 | 理由 | 需要的基建 |
|---|---|---|---|
| P0 | 多租户/认证域：~~`JwtTokenProvider`、`JwtAuthenticationFilter`、~~`AuthController`、`TenantController`（+ `UserController`/`RoleController`） | ~~鉴权链路当前被桩化（`validateToken` 恒 true），修复时必须有回归网~~ → 桩化已于 2026-01-25 修掉（ISS-007/008），🔄 2026-03-22：**前两个目标已经补上了**——`JwtTokenProviderTest`(4)、`JwtAuthenticationFilterTest`(4)（ISS-041 回归）、`CredentialCipherTest`(9)；**但缺的是"出口"而不是"部件"**：401/403 的实际出口、refresh 轮换、跨租户拒绝仍无任何测试（这 4 个控制器零测试 + standalone 不加载 `SecurityConfig`，实测未认证只有 403 + 空 body，ISS-032/058）。⚠️ 口径要精确：过滤器**自身**在无头/坏 token 时的行为是有测试的（`JwtAuthenticationFilterTest:79 anonymousWhenNoHeaderOrBadToken` 断言不建立认证、`:69 tenantContextIsClearedAfterRequest` 断言 ThreadLocal 已清理），缺的是**这些条件在真实 `SecurityFilterChain` 下产出的 HTTP 状态码与 body**——即"过滤器放行/不放行"已锁住，"Web 端点最终返回 401 还是 403"没有。 | 纯单测可覆盖 filter/provider；**401/403 出口必须起上下文**（`@SpringBootTest` 或 `@AutoConfigureMockMvc` + 真实 `SecurityFilterChain`） |
| P0 | 事务边界相关：一次「建订单+订单项+事件」的原子性 | ~~全仓 `@Transactional` 为 0，当前无测试能暴露该问题~~ → 注解数已不是 0（~~2026-01-25 实测 **110 处**，application 90 / infrastructure 20~~ → 🔄 2026-03-22 **116 处 / 27 个文件**，application 96 / infrastructure 20，`readOnly` 9 / `rollbackFor` **0**，ISS-036），**但"无测试暴露"仍然成立**：`@Transactional` 是否真的生效、`readOnly`/`rollbackFor` 缺失、以及**事件在事务里到底发没发出去**都无人验证。<br>⚠️ **本行判据的措辞在二次实测里被订正**（原写"没有任何测试断言 `DomainEventPublisher` 收到过非空事件列表"——不成立）：`infrastructure/messaging/DomainEventPublisherTest.java` 确实存在，6 个 `@Test` 覆盖 topic 路由（`verify(rocketMQTemplate, times(8)).convertAndSend(...)`）、无模板跳过、`shouldClearPublishedEvents`、未知包不投 MQ、MQ 故障不影响进程内通道。**但它喂给发布器的是测试自己造的替身**（`static class EventCarrier { final List<Object> domainEvents = new ArrayList<>(); }`，`carrierOf(...)` 手工 `addAll`）⇒ 验证的是发布器**内部**逻辑，**没有一条测试走过"真实聚合 + 仓储 + 应用服务"这条链**，也就是没有一个测试断言"业务方法执行完后，交给 `publishEventsFrom` 的那个对象上 `domainEvents` 非空"。⇒ **ISS-062 正是从这个缺口里长出来的**：发布器单测全绿，5/8 聚合的事件在真实调用链上一次也没到达发布器。<br>🔄 2026-03-22 补一句：**最接近该判据的测试已经出现但仍不够**——`PurchaseApplicationServiceTest:209 statusChangesShouldPublishAggregateEvents` 用 `verify(domainEventPublisher).publishEventsFrom(approved)`（`:225-228` 四条逐实例）断言了"交出去的是本地聚合而不是副本"（`:166` 另有 `times(1)`、`:88`/`:182` 同形），这正是 ISS-062 的判别式；**但 `purchaseOrderService` 是 mock**，副本来自真实 `PurchaseOrderRepositoryImpl.save()` 的那一环没有被执行 ⇒ 采购域靠"领域服务返回入参实例"侥幸成立，而 Order/Logistics/Warehouse/Customer/PlatformIntegration 五个域要等第一个 `@SpringBootTest` 才测得出来 | 首个 `@SpringBootTest` + H2 或 Testcontainers MySQL，并补一条"调用真实应用服务后事件确实被发布"的断言 |
| P1 | ~~聚合状态机与不变式（9 个上下文）：非法状态迁移应抛异常~~ → 🔄 2026-03-22：**采购上下文已完成**（`PurchaseOrderTest` 17 例，含 `illegalTransitionShouldCarveItsOwnErrorCodeSoUiShowsTheReason:83`、`everyTransitionShouldRegisterStatusChangedEvent:124`）；**剩余待补 = `Order`/`LogisticsOrder`/`Warehouse`/`Customer`/`Product`** 五个聚合的迁移表与不变式 | 这是 DDD 分层里最有价值、也最易测的部分（`PurchaseOrderTest` 就是现成样板：纯单测、不 mock 仓储、17 例只用 24 处断言就把状态机 + 明细不变式全覆盖） | 纯单测 |
| P1 | ~~`GET /api/purchases` 不带 status 的语义（当前回退成 PENDING）~~ → 🔄 **已知语义 bug 已修并被锁住**：`PurchaseController:85` 现为 `@RequestParam(required = false) PurchaseStatus status`，一路透传到 `PurchaseApplicationService:138-142` → `PurchaseOrderSpecification`（仅在 `hasText` 时才加租户/状态谓词）；回归用例是 `PurchaseControllerTest:116`（`testSearchPurchaseOrdersPassesFiltersAndPaging`，status 传 `isNull()`）与 `:140`（`testSearchUsesPageOneByDefault`） | — | 已覆盖 |
| P1 | ~~把 `ProductImportPipelineTest` 的写法复制到其余 8 个上下文的导入/编排路径，并给 `OrderProcessingServiceTest` 换成同样口径的真断言~~ → 🔄 **导入侧已铺完、编排侧只铺了采购**：现有 **6 个 pipeline/service 测试**（`ProductImportPipelineTest` 4、`CustomerImportPipelineTest` 5、`FinanceImportPipelineTest` 9、`LogisticsImportPipelineTest` 6、`ShopWarehouseImportPipelineTest` 5、`ProductImportApplicationServiceTest` 2）+ `PurchaseApplicationServiceTest` 11；`OrderProcessingServiceTest` 已换真断言（14 例）。**仍缺编排测试的上下文**：`LogisticsApplicationService`（10 处发布点）、`WarehouseApplicationService`（库存四路径 + ISS-059/060 修法）、`OrderApplicationService`、`CustomerApplicationService`、`FinanceApplicationService` | ~~全仓唯一有真断言的应用层测试就是它~~ → 它已不是唯一，但它仍是模板 | 纯单测 |
| P2 | 12 个 MapStruct `*Mapper` 的领域↔JPA 往返（尤其枚举三套口径） | 枚举名称漂移（`CON_FIRMED`↔`CONFIRMED` 等）最易出错 | 纯单测 |
| P2 | MQ 消费者与 `EventHandler` 的重复副作用 | 双通道发布会重复建物流单 | `@SpringBootTest` + Testcontainers RocketMQ |
| P2 | 前端：`AuthContext` 登录/登出、`api.js` 拦截器解包逻辑（~~已知双重解包 bug~~ → 🔄 **该 bug 已修**：`user-web/src/context/AuthContext.jsx:71-72` 现在直接读已解包的 `responseData?.token`，不再二次 `.data`；`api.js:46` 单点解包，`:42` 另加 blob 分支放行文件下载；admin-web 也补上了同名拦截器 + 401 跳登录） | 这是打通后端的关键路径；🔄 2026-03-22 复测：两个 app **仍零测试文件**（`package.json` 无 `test` 脚本、无 vitest/jest/@testing-library/playwright/cypress 依赖），拦截器的三种出口（`code:"200"` / 业务错误码 / HTTP 401-403-404-500）没有任何回归网 | 见 §6 |

## 6. 前端测试最小接入方案

两个 app 目前**零测试基建**。以 `user-web` 为例：

```bash
cd frontend/user-web
npm i -D vitest @testing-library/react @testing-library/jest-dom jsdom
```

```js
// vite.config.js 中追加
test: { environment: 'jsdom', globals: true, setupFiles: './src/test/setup.js' }
```

```json
// package.json
{ "scripts": { "test": "vitest run", "test:watch": "vitest" } }
```

首批建议用例：① `api.js` 响应拦截器在 `code:"200"` / `"500"` / HTTP 401 三种情形下的行为；② `AuthContext.login` 是否正确取到 token（~~当前存在**双重解包 bug**：拦截器已 `return response.data.data`，`AuthContext.jsx:32` 又读 `responseData.data?.token` → token 取到 `undefined`~~ → 🔄 2026-03-22 **该 bug 已修**：`AuthContext.jsx:71-72` 现为 `const token = responseData?.token`，与拦截器的单点解包对齐；但**它仍然是零测试的高危路径**——登录是整条认证链的入口，且现在多了 `refreshToken`/`roles`/`tenantId` 四项写入 `localStorage`（`:77-88`），以及 `:56-64` 的"有 token 就拉取当前用户校验会话"分支）；③ `AuthGuard` 未登录重定向。
E2E（Playwright）在当前无 CI 的情况下优先级更低，先建立单测与拦截器契约测试。

## 7. 测试环境与命令

| 命令 | 说明 |
|---|---|
| `cd backend && mvn test` | 后端单测；**不需要** MySQL / RocketMQ（standalone MockMvc + mock） |
| `cd backend && mvn -Dtest=OrderControllerTest test` | 单类调试 |
| `cd frontend/user-web && npm run lint` | ESLint 9 flat config，覆盖 `**/*.{js,jsx}`（react-hooks、react-refresh 规则）；admin-web 同名脚本 |
| `cd frontend/*-web && npm run build` | Vite 构建（默认输出 `dist/`） |
| 前端 `npm test` | **不存在**，需按 §6 接入 |

注意：应用**本体**启动才需要 MySQL(`localhost:3306`) 与 RocketMQ(`localhost:9876`)；`scripts/docker/docker-compose.yml` 只起 RocketMQ 三件套，**不含 MySQL、不含测试服务**。

## 8. 建议的测试策略（目标态）

| 层 | 手段 | 占比目标 |
|---|---|---|
| 领域（聚合不变式、状态机） | JUnit5 + Mockito 纯单测 | 50% |
| 应用服务（编排、事件发布、事务） | `@SpringBootTest` + H2/Testcontainers | 25% |
| 接口（契约、状态码、信封） | `@WebMvcTest` + `@MockBean`（当前一律 standalone，Security 与全局异常**从未被测**） | 15% |
| E2E（关键业务闭环：下单→物流→库存→财务） | Playwright + 真实后端 | 10% |

配套引入：JaCoCo（覆盖率报告与门禁）、GitHub Actions（`mvn test` + `npm run lint && npm run build`）、`application-test.yml` + `@ActiveProfiles("test")`（把测试配置从 `localhost:3306` 的 dev 配置里解耦）。
