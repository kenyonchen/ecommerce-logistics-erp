# 项目统计报告 / 技术底账 —— ecommerce-logistics-erp

> 用途：① 项目归档与技术底账；② 技术债评估与风险识别。
> **复测日期：2026-03-22，全部数字由当日工作树实测得出**（非引用既有文档口径）。
> 计量口径：行数 = 文件字节流换行数（末行无换行符仍计 1 行）；排除 `target/`、`node_modules/`、`dist/`、`.git/`、`.idea/`。
> 数据底账（本报告每个表格都可下钻）：[`inventory/`](inventory/)，共 15 张 CSV、含 202 条端点与 42 张表的逐条明细。

## 0. 底账文件索引

15 张 CSV 位于 [`inventory/`](inventory/)，行数如下（不含表头）：

| CSV | 数据行 | 内容 |
|---|---|---|
| [code-scale-by-language.csv](inventory/code-scale-by-language.csv) | 16 | 按扩展名的文件数/行数/占比（含 TOTAL 行） |
| [code-scale-by-directory.csv](inventory/code-scale-by-directory.csv) | 15 | 按目录的文件数/行数/主扩展名 |
| [backend-layer-size.csv](inventory/backend-layer-size.csv) | 26 | 后端层与限界上下文的文件数/行数/均值/Spring 构造型 |
| [module-dependencies.csv](inventory/module-dependencies.csv) | 89 | 跨层/跨包 import 边及权重（全量 **1,530** 条边 / 592 个不同源-目标对；🔴 **本 CSV 是权重降序的前 89 对，自身求和只有 1,103，不要当全量下钻用**——2026-03-22 逐行复算发现，其余 14 张 CSV 的求和都与本文表格严格相符） |
| [api-endpoints.csv](inventory/api-endpoints.csv) | 202 | 每个端点：控制器/动词/路径/方法/返回类型/是否鉴权/是否类级 |
| [api-endpoints-by-controller.csv](inventory/api-endpoints-by-controller.csv) | 21 | 每控制器端点数、动词分布、鉴权覆盖率（含 TOTAL 行） |
| [database-tables.csv](inventory/database-tables.csv) | 42 | 每表列数/索引数/外键数/实际行数（来自运行库） |
| [frontend-files.csv](inventory/frontend-files.csv) | 69 | 两端每个 JS/JSX 的类别、所属模块、路径、行数 |
| [frontend-page-modules.csv](inventory/frontend-page-modules.csv) | 18 | 页面按 app × 业务模块的文件数与行数 |
| [frontend-api-clients.csv](inventory/frontend-api-clients.csv) | 26 | 每个 api 分组的方法数、HTTP 调用数、tenantId 提及数 |
| [dependencies.csv](inventory/dependencies.csv) | 52 | 后端 19 个 Maven 依赖 + 4 个版本属性 + 前端 30 个 npm 依赖 |
| [tests-by-class.csv](inventory/tests-by-class.csv) | 54 | 每个测试类的 @Test/@Mock/when()/断言数与行数 |
| [error-codes.csv](inventory/error-codes.csv) | 51 | 代码中实际抛出的 errorCode、抛出点数、是否已入文档（🔄 2026-03-22 由 21 行重测重生成：51 个字面量码 / 99 个字面量抛出点，另有 1 个变量码与 1 个拼接码无法逐字列出） |
| [quality-indicators.csv](inventory/quality-indicators.csv) | 19 | 质量指标实测值（后端主源/测试/前端；🔄 2026-03-22 新增「断言与交互校验」一行，并订正 @PreAuthorize 29、@Transactional 116、readOnly 9、tenantId 83 四处） |
| [tech-debt-register.csv](inventory/tech-debt-register.csv) | 16 | 技术债登记表 TD-01…TD-16，含实测证据、影响、关联文档与处置建议 |

---

## 1. 项目概述

多租户 SaaS 形态的**跨境电商订单-仓储-物流 ERP**，DDD 单体分层后端 + 双前端 SPA + 8 个电商平台适配层。

| 项 | 内容 |
|---|---|
| 仓库 | `ecommerce-logistics-erp`，分支 `main` |
| 提交基线 | `bfbce09 分离用户端和管理端`（其下为 `7a237ab init`，共 2 个提交） |
| 工作树状态 | **大量未提交改动**（后端 60+ 文件、前端、docs），统计以工作树而非 `HEAD` 为准 |
| 结构 | `backend/`（单 Maven 模块）、`frontend/user-web`、`frontend/admin-web`、`docs/`、`scripts/` |
| 规模 | ~~744 个文件 / 105,902 行~~ ~~744 个文件 / 105,946 行~~ ~~744 个文件 / 105,983 行~~ **744 个文件 / 105,999 行**（🔄 2026-03-22 末次复测：本次一致性核对累计写入 +97 行 ＝ Markdown +66 ＋ inventory CSV +31，文件数未变；其中 +16 行来自仓库/采购章节收尾时的三处补写：`docs/spec` +12、`docs/api` +3、`docs/product` +1）；生产代码 72,161 行（后端 54,858 行 Java + 前端 src 内 17,303 行 JS/JSX） |
| 业务上下文 | 9 个限界上下文 + 共享内核：order / logistics / warehouse / product / customer / finance / **purchase** / tenant / platform / common |
| 运行形态 | 后端 `:8082` 单进程；MySQL 8.0 + RocketMQ 5.3.2 由 docker-compose 提供；前端 Vite dev / 静态构建 |
| 交付判断 | **仍不可对外提供服务**（凭据明文、读路径租户隔离、事件链路三处 P1 未闭环，见 §10） |

本轮已完成的采购管理模块补全（P1 档：正确性 + 集成）已计入以下所有统计：12 个端点全量 REST 化、审批字段与状态机白名单、到货同事务入库 + 采购应付编排、前端契约对齐与服务端分页。

## 2. 技术栈清单

### 2.1 后端（19 个声明依赖，全部走 spring-boot-starter-parent 版本治理）

| 类别 | 选型 | 版本 |
|---|---|---|
| 语言 / 运行时 | Java | 17 |
| 框架 | Spring Boot | **3.5.9**（parent） |
| Web / 持久化 / 安全 / AOP | spring-boot-starter-web / data-jpa / security / aop | managed |
| ORM | Hibernate + MySQL8Dialect | managed，`ddl-auto: update`（ADR-0002） |
| 数据库 | MySQL | 8.0（compose 镜像 `mysql:8.0`） |
| 消息 | rocketmq-spring-boot-starter | 2.2.3（broker `apache/rocketmq:5.3.2`） |
| 认证 | jjwt-api / -impl / -jackson | 0.12.5 |
| API 文档 | springdoc-openapi-starter-webmvc-ui | 2.6.0（`/swagger-ui.html`、`/v3/api-docs`） |
| 对象映射 | MapStruct + mapstruct-processor | 1.5.5.Final（12 个 `@Mapper`，ADR-0010） |
| 样板代码 | Lombok | 1.18.34（**278** 个文件引用） |
| Excel 导入 | poi-ooxml | 5.2.5 |
| 测试 | junit-jupiter / mockito-core / spring-boot-starter-test | managed |
| 开发期 | spring-boot-devtools | managed（runtime） |

**缺失但文档声称存在**：`spring-boot-starter-validation`（→ `@Valid` 0 处，TD-03）。
**陈旧配置**：pom 属性 `spring.kafka.version=3.4.2` 无对应依赖，应删除。

### 2.2 前端（两端依赖集完全一致）

React **19.2** + Vite **7.2.4** + antd **6.2** + `@ant-design/icons` 6.1 + react-router-dom **7.12** + axios **1.13.2**；开发期 ESLint 9 + `@vitejs/plugin-react` 5.1.1。脚本 4 个：`dev / build / lint / preview`。**无状态管理库**（无 Pinia/Redux/Zustand，状态靠 hooks + Context），**无 UI 测试框架**，**无 TypeScript**。

## 3. 代码规模统计

### 3.1 按语言

| 扩展名 | 文件 | 行 | 占比 |
|---|---|---|---|
| java | 567 | 68,007 | 64.2% |
| jsx | 61 | 16,127 | 15.2% |
| md | 59 | ~~9,628~~ ~~**9,641**~~ ~~**9,678**~~ **9,694** | 9.1%（🔄 2026-03-22 末次复测：本次一致性核对自身的写入量 +66 行，主要集中在 `docs/spec/purchase.md`、`docs/spec/warehouse.md`、`docs/api/api.md` §4.10/§4.11 与 `docs/tech/inventory/*`） |
| json | 4 | 8,502 | 8.0%（含 `package-lock.json`，非手写） |
| js | 12 | 1,248 | 1.2% |
| sql | 2 | 799 | 0.8% |
| csv | 15 | ~~700~~ **731** | 0.7%（本报告底账；🔄 +31 行来自 `error-codes.csv` 由 21 行重写为 51 行、`quality-indicators.csv` 增行） |
| xml / yml / css / sh / noext / conf / html / svg | 24 | 891 | 0.8% |
| **合计** | **744** | ~~**105,902**~~ ~~**105,946**~~ ~~**105,983**~~ **105,999** | 100%（🔄 末次复测勾稽：`567+61+59+4+12+2+15+24 = 744`、`68,007+16,127+9,694+8,502+1,248+799+731+891 = 105,999`，逐扩展名与总量自洽） |

### 3.2 按目录

| 目录 | 文件 | 行 | 说明 |
|---|---|---|---|
| backend/src/main/java | 513 | 54,858 | 生产代码，均值 **106.9 行/类** |
| frontend/user-web | 70 | 20,268 | 含配置与静态资源；src 内 62 文件 / 15,842 行 |
| backend/src/test/java | 54 | 13,149 | 测试，**test : main = 1 : 4.2** |
| frontend/admin-web | 21 | 6,012 | src 内 13 文件 / 1,620 行 |
| docs/tech | 27 | ~~3,042~~ **3,075** | 12 个 Markdown（含本报告）+ 15 张 inventory CSV |
| docs/spec | 10 | ~~2,011~~ ~~**2,029**~~ **2,041** | 10 个模块规格（🔄 2026-03-22 收尾：`warehouse.md` +12） |
| docs/product | 8 | ~~1,442~~ **1,443** | 🔄 2026-03-22 收尾：`07-roadmap.md` 验收行 +1 |
| docs/db | 4 | ~~1,317~~ **1,409** | 3 个 Markdown + schema.sql（42 张表）🔄 2026-10-06：`schema.sql` 的 `platform_integrations` 块按运行库实测重写、`migration-plan.md` 追加 §9 |
| docs/adr | ~~13~~ **14** | ~~927~~ **1,226** | ~~**ADR-0001–0013** + README~~ → ~~**ADR-0001–0012（12 个）+ README** = 13 个文件；0013 未占用~~ → 🔄 **2026-10-06：ADR-0001–0013（13 个）+ README = 14 个文件**，0013 已由本轮店铺分表裁决占用，下一个可用编号是 0014；见 §10.4 |
| docs/api | 3 | ~~904~~ ~~**924**~~ **927** | api.md / error-codes.md / 1 个补充（🔄 2026-03-22 收尾 +3） |
| docs（根）+ docs/qa | 7 | ~~1,016~~ **1,026** | known-issues.md 65 个 ISS 条目 |
| backend/other | 2 | 293 | pom.xml（197）+ application.yml（~~95~~ **96**） |
| scripts | 7 | 287 | docker-compose、运维脚本、broker.conf、init.sql |
| ROOT | 5 | 376 | README、LICENSE、COPYRIGHT、.gitignore |
| **docs 合计** | **72** | ~~**10,659**~~ ~~**10,740**~~ **10,756** | ~~Markdown 59 / 9,628 行~~ ~~**56 个 / 9,226 行**~~ → 🔄 docs 内 Markdown **56 个 / 9,242 行**（另 15 CSV / 731 行 + `docs/db/schema.sql` 783 行）；全仓 Markdown 才是 59 个 / 9,678 行 |

**生产代码 : Markdown ≈ 72,161 : ~~9,628~~ ~~9,678~~ 9,694 ≈ 7.4 : 1**，文档占比在同规模业务系统里显著偏高（且为逐条实测型文档，也因此更容易与代码漂移，见 §10.4）。

## 4. 模块划分与依赖关系

### 4.1 后端层规模（Top，完整见 backend-layer-size.csv）

| 层 / 上下文 | 文件 | 行 | 均值行 | 主要构造型 |
|---|---|---|---|---|
| infrastructure/platform | 60 | 11,123 | 185.4 | @Component 53 |
| infrastructure/persistence | 115 | 8,506 | 74.0 | @Repository 42、@Entity 40 |
| application/service | 21 | 6,952 | 331.0 | @Service 21 |
| interfaces/controller | 20 | 4,290 | 214.5 | @RestController 20 |
| interfaces/dto | 77 | 3,427 | 44.5 | — |
| domain/logistics | 23 | 3,091 | — | — |
| domain/warehouse | 23 | 2,770 | — | — |
| domain/order | 21 | 2,608 | — | — |
| domain/product | 23 | 2,203 | — | — |
| domain/finance | 16 | 1,806 | — | — |
| domain/platform | 35 | 1,762 | — | — |
| domain/customer | 16 | 1,508 | — | — |
| **domain/purchase** | **9** | **1,213** | 134.8 | 本轮补全后的完整聚合 |
| domain/tenant | 11 | 890 | — | — |
| domain/common | 9 | 293 | — | 异常家族 + PageResult |

平台适配层（infrastructure/platform）是全仓最大单一子系统：**10 个包 / 60 文件 / 11,123 行**，占后端生产代码 **20.3%**，规模排序 magento 1,356 > ozon 1,197 > shopify 1,160 > shopee 1,099 > ebay 941 > mercado 835 > aliexpress 811 > amazon 676 > common 446 > oauth 326。**domain 十个上下文合计 20,144 行（含 common 293），平台适配层一个包就抵到它的 55%**——这是本项目最不对称的投入结构。

### 4.2 层间依赖（import 边，全量 **1,530** 条 / 102 个源-目标组对；CSV 只存了权重降序的前 89 对 = 1,103 条，见 §0 提示）

```
interfaces/controller ──23──▶ application/service ──270──▶ domain
        │58                        │69(⚠)
        ▼                          ▼
  interfaces/dto(76)        interfaces/dto   ← 反向依赖
infrastructure ──373──▶ domain        domain ──1──▶ infrastructure  ← 唯一一次上穿
```

（🔄 2026-03-22 逐 import 复算校正：`interfaces → application` **23** = controller→service 19 + controller→command 4；`application → domain` **270** = service 264 + CQRS 骨架（`application/command`+`application/query`）6，故图上标注 270 的口径是"整个 application 层"而非仅 `application/service`；`interfaces → domain` 原写 **89** 是**误把 CSV 行数当边权**，实测 **58**。）

| 边 | 权重 | 判定 |
|---|---|---|
| infrastructure → domain | 373 | 正常（依赖倒置实现） |
| domain → domain | 297 | 上下文内部聚合；其中**跨上下文仅 17**（§4.3） |
| application → domain | 270 | 正常 |
| interfaces → domain | ~~89~~ → **58**（2026-03-22 复算） | ⚠️ **一半是真问题**：29 条指向 `domain/common`（`PageResult` + 4 个异常家族，可接受），另 **29 条指向 9 个业务聚合**（tenant 6、logistics 4、platform 4、customer 3、finance 3、warehouse 3、order 2、product 2、purchase 2）= 聚合根直接当 REST 出参（ISS-063） |
| **application → interfaces/dto** | **69** | **违规**：6 个导入应用服务各 9–10 处（合计 56 处 = 81%），🔄 2026-03-22 补记：另 **13 处**在 3 个非导入应用服务（`LogisticsApplicationService` 6、`PlatformIntegrationApplicationService` 5、`ShopWarehouseApplicationService` 2），故"集中在 6 个"≠"只在 6 个"（TD-11） |
| **domain → infrastructure** | **1** | **违规**：`PlatformIntegrationServiceImpl` 直接 import `infrastructure.config.HttpClientConfig`（TD-11） |

### 4.3 上下文耦合

跨上下文 import 仅 **17 条 / 6 条边**：`order → product`(3)、`finance → product`(3)、`warehouse → product`(3)、`logistics → product`(3)、`customer → product`(3)、`platform → warehouse`(2)。**`product` 是事实上的共享上下文**，其余上下文两两零直接依赖——限界上下文切分是全仓最健康的一环。

## 5. API 接口统计

### 5.1 总量

| 指标 | 实测 |
|---|---|
| 控制器 | **20** 个 `@RestController` |
| 端点 | **202** 个（`@GetMapping`82 / `@PostMapping`64 / `@PutMapping`41 / `@DeleteMapping`15，注解级计数与逐条枚举一致） |
| 有 `@PreAuthorize` | **111（55.0%）**，其余 91 个无 |
| 类级注解覆盖的端点 | 90（占已鉴权端点的 81%） |
| 自描述覆盖 | 202/202 带 `@Operation`（springdoc 全量） |
| 文档口径 | `docs/qa/known-issues.md`（2026-03-08 档）记 198，实测 202；差额主要来自采购接口 REST 化（`/statuses`、明细 PUT/DELETE）等增量 |

### 5.2 每控制器（完整见 api-endpoints-by-controller.csv）

| 控制器 | 端点 | 已鉴权 | 覆盖率 | 备注 |
|---|---|---|---|---|
| PlatformIntegrationController | 15 | 15 | 100% | 类级；23 处 catch 吞异常（TD-07） |
| LogisticsController | 13 | 0 | **0%** | 读路径无租户条件（ISS-050） |
| WarehouseController | 13 | 0 | **0%** | |
| LogisticsImportController | 12 | 12 | 100% | 类级 |
| ProductController | 12 | 0 | **0%** | |
| **PurchaseController** | **12** | **12** | **100%** | **方法级，本轮全量 REST 化** |
| FinanceImportController / OrderImportController / UserController | 11 | 11 | 100% | 类级 |
| CustomerImport / ProductImport / RoleController | 10 | 10 | 100% | 类级 |
| WarehouseImportController | 10 | 0 | **0%** | 导入类里唯一未加闸 |
| Customer / Finance / Order / TenantController | 9 | 0/9/0/0 | — | Finance 方法级 100%，其余 0% |
| LogisticsChannelController | 7 | 0 | 0% | |
| ShopWarehouseController | 5 | 0 | 0% | |
| AuthController | 4 | 0 | 0% | 设计上应公开 |

**结构判读**：鉴权呈"新模块类级全覆盖、老控制器零覆盖"的二元分布，`AuthController` 之外的 90 个未鉴权端点集中在 order/logistics/product/warehouse/customer/tenant 六个主业务域——这是 TD-01，也是读路径越权的直接成因。

### 5.3 错误契约

- `Result<T>` 信封：`String code` / `String message` / `T data`，**无 success 布尔、无 traceId**。
- `GlobalExceptionHandler`：**204 行 / 9 个 `@ExceptionHandler`**，覆盖 BusinessException、EntityNotFound、InvalidOperation、Validation+BadCredentials、IllegalArgument/IllegalState、MethodArgumentNotValid、AccessDenied、HttpMessageNotReadable/TypeMismatch/MissingParam、Exception 兜底。
- 抛出分布（`throw new X`，共 325 处）：ValidationException **132** > BusinessException **105** > IllegalArgumentException **27** > IllegalStateException 9 > InvalidOperationException 6 > EntityNotFoundException 4，另有 7 个平台专用异常家族（PlatformProductFetch/FinanceFetch/…）。
- **errorCode 实测**：~~111 处 `new BusinessException` 仅 **42 处（21 个不同码）**传码，其余落默认 `BUSINESS_ERROR`；`docs/api/error-codes.md` 记录了这 21 个里的 **1 个**（TENANT_ACCESS_DENIED）~~ → 🔄 2026-03-22 重测：**101 处传码 / 10 处落默认 `BUSINESS_ERROR`**；不同码 **51 个字面量 + 1 个变量 + 1 个拼接式**（99 个字面量抛出点），高度偏斜（`PLATFORM_CREDENTIAL_REQUIRED` 19、`PLATFORM_STORE_URL_INVALID` 16；40 个码只出现 1 次）。`error-codes.md` 现记录 5 个，全量清单在 [inventory/error-codes.csv](inventory/error-codes.csv)（51 行，本轮重生成）。**仍无常量类/枚举**，文档 §3 的目标码表（`A00001`/`B00001`…）**未落地**。
- Bean Validation：`@Valid` 0 / `@NotNull` 0 → handler 的 `MethodArgumentNotValidException` 分支是**不可达死代码**。

## 6. 数据库设计分析

口径：运行库 `ecommerce_logistics_erp`（docker `erp-mysql`）实测，逐表见 database-tables.csv。

| 指标 | 值 |
|---|---|
| 表 | **42** 张（全部 InnoDB + utf8mb4） |
| 映射来源 | 40 个 `@Entity`（均有显式 `@Table`）+ 2 张 `@ManyToMany` 隐式关联表（`user_roles`、`role_jpa_entity_permissions`） |
| 列 | **421**（均值 10.0 / 最大 19） |
| 索引 | **110**（含 **26** 个唯一索引） |
| 外键约束 | **19** |
| 空表 | **23 / 42（55%）** |

**最宽表**：logistics_orders 19、platform_integrations 19、shop_warehouses 16、6 张 `*_import_jobs` 各 15、orders 14、purchase_orders 12。

### 6.1 风险判读

1. **无迁移工具**：`ddl-auto: update` + 手工快照 `docs/db/schema.sql`（42 个 `CREATE TABLE`，与运行库表数一致）。`scripts/docker/mysql/init/01-init.sql` **只有 1 条 INSERT、0 条 CREATE TABLE**——新环境完全靠 Hibernate 启动建表。已知历史事故：6 张表曾因保留字从未被建出（ISS-049）。→ **TD-05**。
2. **外键覆盖率 4.5%（19/421 列）**：引用完整性主要由应用层保证，跨表脏数据无库级拦截；带 FK 的表集中在 order(2)、inventory_records(2)、user_roles(1) 等少数。
3. **索引分布极不均**：orders 9、purchase_orders 4、logistics_channels 3、inventory_records 3，而 23 张表 ≤1（仅主键）；多租户查询依赖 `(tenant_id, …)` 复合索引，缺表的查询会随数据量线性劣化。
4. **行数分布显示这是演示库而非业务库**：role_jpa_entity_permissions 22、logistics_channels 8、inventory_records 7、purchase_orders 4、purchase_order_items 4（本轮 e2e 产物）、financial_transactions 3、orders 3，其余多为 0/1。任何容量结论都不可从此库推出。
5. **敏感列明文**：`platform_integrations.api_key / api_secret` 明文存储并原样出参（ISS-011，本轮按指示未改动）。

## 7. 前端组件统计

| 指标 | user-web | admin-web |
|---|---|---|
| src 文件 / 行 | 62 / 15,842 | 13 / 1,620 |
| JSX 文件 / 行 | 52 / 14,761 | 9 / 1,366 |
| 路由 | **42** | **7** |
| 页面文件 | **45** | **5** |
| 复用组件 | **2**（Layout、AuthGuard） | **0** |
| api 分组 / HTTP 调用 | 21 / **199** | 5 / **31** |
| >500 行文件 | 6 | 0 |
| console.* / localStorage.* | 46 / 20 | 3 / 11 |

### 7.1 页面按模块

user-web：product 6、logistics 6、order 4、finance 4、warehouse 4、customer 4、tenant 3、user 3、**purchase 3**、platform-integration 3、platform 2、auth/home/role 各 1。
admin-web：platform 2、tenant 1、user 1、Login 1。

### 7.2 判读

1. **组件复用几乎为零**：45 个页面对 2 个共享组件，页面各自直调 antd 与 `api.js`；6 个 >500 行页面（LogisticsImport 824、OrderImport 794、FinanceImport 794、ProductImport 725、CustomerImport 722、ShopWarehouseSync 532）5 个是导入页，前端导入页两两相似度 **0.30–0.87**（Finance↔Order 0.87、Customer↔Product 0.81）。→ **TD-10**。
2. **`platformApi` 是空壳**：~~`api.js:486` 起 0 个方法，仅注释「根据实际后端API补充」，而 `platformIntegrationApi` 已有 9 个方法~~ → 🔄 2026-03-22 复测：空壳仍在（`user-web/src/services/api.js:486-488`），`platformIntegrationApi` 已增到 **14 个方法**（新增 `testConnection`、`pushFulfillment`），落差扩大。**更重的问题在页面侧**：`user-web` 与 `admin-web` 的 `pages/platform/SystemConfig.jsx` 是**逐字节相同的两份副本**（各 174 行），`fetchSystemConfig` 在函数体内现造 mockConfig（站点名/SMTP/密码策略全是假值），`onFinish` 只 `console.log` + `message.success('系统配置保存成功')`，**零网络请求**；两端 `SystemConfig.jsx:3` 那行 `import { platformApi }` 全文件再无第二处引用（死导入）。`admin-web/src/services/api.js:116-141` 另有一份等价桩（`getSystemConfig` 返回硬编码、`saveSystemConfig` 只 `console.log` 后 `return true`），同样无人调用。→ **TD-12（建议升 P1：用户看到"保存成功"但什么都没发生）**。
3. **租户 ID 由前端下发**：`api.js` 中 `tenantId` 出现 ~~78~~ → **79**（user-web）+ 4（admin-web）= 83 次；`DEFAULT_TENANT_ID` 被 23 个文件引用 78 次（复测一致），且在 `constants/order.js:7` 与 `constants/product.js:7` **重复定义两份**（另有 `logistics.js:7`/`purchase.js:7`/`finance.js:7` 三处 re-export），取值 `localStorage.getItem('tenantId')` 而登录流程**从未写入该键** → 恒为 `'default-tenant'`。**本轮已在采购模块（PurchaseList / CreatePurchase）移除该下发并实测验证**，其余 18 个页面仍在下发。→ **TD-04（P1）**。
4. **鉴权模型**：`AuthGuard` / `PlatformAdminGuard` / `TenantAdminGuard` 三个守卫仅在 `routes.jsx` 做路由级拦截，与后端 91 个未鉴权端点不构成一致边界。
5. **两端重复**：`admin-web/src/services/api.js` 133 个非空行中 **86 行（65%）与 user-web 逐字节相同**；依赖集、脚本、`baseURL http://localhost:8082/api` 硬编码全部一致，无共享包。→ ADR-0005/0008 完成了拆分但未沉淀共享层。

## 8. 构建与部署配置

| 项 | 实测 |
|---|---|
| 后端构建 | Maven 单模块；3 个插件：spring-boot-maven-plugin、maven-compiler-plugin 3.11.0（`-parameters`，租户切面依赖此项）、maven-surefire-plugin 3.2.5；**无 JaCoCo / Checkstyle / SpotBugs** |
| 配置 | **单个 `backend/src/main/resources/application.yml`（95 行），无 profile 拆分**（无 dev/test/prod 区分） |
| 关键值 | `server.port=8082`、`ddl-auto: update`、`show-sql: true`、multipart 20MB、devtools 热重启开启、`rocketmq.enabled: true`、springdoc 单分组 `public-api`、日志四级 INFO |
| 密钥处理 | 数据源口令（`:26`）与 `jwt.secret`（`:93`）**硬编码明文**（本报告不复述其值）；`platform.credential.master-key` 已改 `${PLATFORM_CREDENTIAL_MASTER_KEY:}` 占位——同一文件内两种标准并存 |
| 基础设施 | `scripts/docker/docker-compose.yml`：3 个服务（`mysql:8.0` → erp-mysql、`rmqnamesrv`、`rmqbroker`，均 `apache/rocketmq:5.3.2`）+ `erp-net` + `mysql-data` 卷；`broker.conf`；`mysql/init/01-init.sql`；运维脚本 `erp-services.sh`（151 行） |
| CI/CD | **不存在**：无 `.github/`、无 **任何 Dockerfile**、无 Jenkins/GitLab CI 文件，而 `docs/tech/ci-cd.md` 描述了完整流水线（→ **TD-09**） |
| 前端构建 | Vite `build`，无环境变量注入（`baseURL` 源码硬编码），无 Docker 化静态托管配置 |

**判读**：可交付性完全绑定"能连到本机的 MySQL + 手动 `mvn spring-boot:run`"。没有镜像、没有流水线、没有 profile，意味着 §6 的 DDL 风险与 §5.3 的错误契约风险都无法通过发布流程兜住。

## 9. 测试现状分析

| 指标 | 实测 |
|---|---|
| 测试文件 / 测试类 | 54 / 54（每个测试文件都是一个含 `@Test` 的真实测试类，无纯 helper 文件） |
| `@Test` 方法 | **494**（无 `@ParameterizedTest`） |
| 结果 | **494 通过 / 0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS**（2026-03-22 复跑 `mvn -o test-compile` + `surefire:test`；54 个 surefire 报告计时合计 **10.4s**） |
| 断言密度 | `when()` 154、`@Mock` 37、~~断言与 verify 合计 1,776~~ → **`assert*` 1,776 + `verify(` 55 = 1,831**（2026-03-22 复测：1,776 是 `assert[A-Za-z]*(` 的单独计数；`verify(` 55 处集中在 **9 个类**，见 quality-indicators.csv；54 个测试类**全部 ≥3 个断言点**、`assertTrue(true)` 0 处） |
| 主类 : 测试类 | 513 : 54（**9.5 : 1**）；按同名匹配仅 **28** 个主类有对应测试类（5.5%） |
| 覆盖率工具 | **无**（TD-08） |

### 9.1 分布（完整见 tests-by-class.csv）

| 包 | @Test | 判读 |
|---|---|---|
| interfaces.controller | 87（**10** 个测试类） | 20 个控制器中 **10 个**有测试；**PurchaseControllerTest 17 例覆盖本轮 12 端点**；无测试的 10 个：Auth/Tenant/User/Role/ShopWarehouse + 5 个导入控制器 |
| infrastructure.platform.* | 283（19 类，占 **57.3%**） | magento 52、ozon 44、shopee 40、amazon 30、mercado 29、shopify 27、ebay 26、core 11、aliexpress 17、oauth 7 |
| application.service（含 purchase/finance/platform/customer） | 42（7 类） | application.service.purchase **11** |
| domain.* | 45（5 类） | order.service 19、**purchase.aggregate 17**、finance.aggregate 5、platform.service 4 |
| infrastructure.security | 17 | JWT + 租户切面 |
| persistence.repository / messaging(+config) / infrastructure.config | 20 | 7 / 11 / 2 |

**按层汇总**：平台适配 283（57.3%）> 控制器 87（17.6%）> 领域 45 > 应用 42 > 持久化/消息/配置 20 > 安全 17，合计 494。

### 9.2 判读

1. **测试资产投向与业务风险错位**：**57.3%** 的测试（283/494）在平台适配层，且全部是本地 `HttpServer` 桩 + 官方形状 fixture，无任一真实沙箱凭据（ISS-064）；而承载资金与库存的 `domain.*` 只有 **45 例（9.1%）**，其中采购独占 17 例（本轮补全）。
2. **完全没有集成级测试**：`@SpringBootTest` **0**、`@WebMvcTest` **0**、`@DataJpaTest` **0**、Testcontainers **0**。控制器测试用 `MockMvcBuilders.standaloneSetup`（10 个类 / 11 处），**不经 Spring 过滤链 → 202 端点上那 111 个 `@PreAuthorize` 在测试里全部不生效**；仅 2 个测试类接入了 `GlobalExceptionHandler`。建表风险（ISS-049）也不在任何测试保护范围内。
3. **无前端测试**：两端 devDeps 无任何测试框架，45 个页面 0 覆盖；本轮采购 UI 全链路仅能靠浏览器驱动人工验证。
4. **文档口径漂移**：`docs/qa/known-issues.md` 2026-03-08 档记为「31 测试类 211 @Test」，与当日工作树实测（54 类 / 494 例）不符。以本报告与 CSV 为准。

## 10. 代码质量评估

### 10.1 正面指标（实测）

| 指标 | 值 |
|---|---|
| TODO / FIXME / printStackTrace / System.out（主源码） | **0 / 0 / 0 / 0** |
| 空 catch 块 | **0**（252 个 catch 全部有处理） |
| Javadoc 块 | **2,185**（≈4.0 块/百行，中文，含 `@author`） |
| 注入风格 | `@Autowired` 字段注入 **11** 处 vs `private final` 构造注入 **647** 处 |
| 事务边界 | `@Transactional` ~~115~~ → **116** 处（2026-03-22 复测：application 96 / infrastructure 20 / domain 0 / interfaces 0。⚠️ `readOnly` ~~仅 7 处~~ → **9 处**、`rollbackFor` **0** 处——默认不回滚 checked 异常，见 quality-indicators.csv） |
| 上下文切分 | 跨上下文 import 仅 17 条 / 6 条边（§4.3） |
| API 自描述 | 202/202 端点带 `@Operation` |

卫生程度高于同规模业务系统常态：**没有调试残留、没有静默吞异常、没有字段注入泛滥**。

### 10.2 负面指标（实测）

| 指标 | 值 |
|---|---|
| >500 行的类 | **8**（LogisticsImport 688、OrderImport 630、CustomerImport 619、FinanceImport 584、ProductImport 583、LogisticsOrder 563、Order 524、WarehouseImport 506）；>300 行 34 个 |
| 重复实现 | 6 个后端导入服务 ~~3,343~~ → **3,610** 行（2026-03-22 复测：688+630+619+584+583+506）、两两相似 0.46–0.57（该比值属一次性脚本产物，本轮未复算）；5 个前端导入页 ~~3,594~~ → **3,859** 行（824+794+794+725+722），两两 0.30–0.87；两端 api.js 65%（86/133 非空行）逐行相同——**这一项复测仍成立** |
| 绕过异常家族 | `new RuntimeException` 8 处（6 处表达 not-found，会被 handler 归为 400 INVALID_OPERATION 而非 404） |
| 分层违规 | 2 类（domain→infrastructure 1 处、application→interfaces/dto 69 处） |
| 前端卫生 | console.* 49、localStorage.* 31，无 ESLint 规则收紧（无 no-console） |
| 契约完整性 | **9 个控制器**仍有 `catch → Result.error`，共 **45** 处：PlatformIntegration 23、Finance 8、LogisticsImport 3、CustomerImport/FinanceImport/OrderImport/ProductImport/WarehouseImport 各 2、Logistics 1；采购与其余 11 个控制器本轮已清零（`PurchaseController` 0 catch）。🔄 2026-03-22 订正口径：~~"把失败压成 HTTP 200 + code 500"~~ 对**其中 22 处**成立（`FinanceController` 8 处 = HTTP 200 + 数字串 `"500"`；6 个导入控制器 13 处 + `LogisticsController:164` 1 处 = HTTP 200 但码已是语义串）；`PlatformIntegrationController` 那 23 处包在 `ResponseEntity` 里（14×500 + 9×400），状态码正确，**残留问题是 45 处全部把 `e.getMessage()` 原文写进响应体**（信息泄漏，M-08）。另有 148 个端点返回类型是裸 `Result`（9 个控制器 89 个端点完全无 `ResponseEntity`），这部分即使无 catch 也给不出正确状态码 |

### 10.3 技术债登记表

16 条，逐条含实测证据、影响、关联文档与处置建议 → **[tech-debt-register.csv](inventory/tech-debt-register.csv)**。按严重度：

- **P1（阻断上线，9 条）**：TD-01 91 端点无鉴权 · TD-02 错误码无字典 · TD-03 Bean Validation 缺位 · TD-04 前端租户硬编码 · TD-05 无 DDL 迁移 · TD-06 凭据明文 · TD-08 无覆盖率/静态检查 · TD-10 导入子系统三处平行实现 · TD-14 事件链路只有部分域接通
- **P2（影响稳定集成，5 条）**：TD-07 控制器吞异常 · TD-09 无 CI 与镜像 · TD-11 分层方向违规 · TD-12 前端空壳客户端 · TD-16 文档口径漂移
- **P3（可维护性，2 条）**：TD-13 超大类 · TD-15 前端调试残留

### 10.4 与既有文档的一致性提示

- ~~`docs/qa/known-issues.md`（65 个 ISS 条目，001–065，多轮复测标注）与代码存在口径漂移：端点 198 vs 实测 **202**、测试 31 类/211 例 vs 实测 **54 类/494 例**。~~ → 🔄 **2026-03-22 本轮已在该文件内修复**：`known-issues.md:10` 追加了「2026-03-22 全量复测」计量口径行（513 主源 / 202 端点 / 54 类 494 例 / `@Transactional` 116 / `@PreAuthorize` 29），并在 `:9` 原 10-03 档上标注其已过期，198 与 31/211 作为历史档位保留。
- 该文档另引用 **ADR-0014**，但 ~~`docs/adr/` 实际只有 **0001–0013**（+README）——编号已占用但文件不存在，后续新裁决请从 **0015** 起~~。→ 🔄 **2026-03-22 复测更正**：`docs/adr/` 实测 **13 个文件 = ADR-0001…0012 + README**，**0013 与 0014 都不存在**。因此 0013 是**下一个可用编号**（不是已占用），新裁决应从 **0013** 起；`docs/tech/README.md` 与 `docs/api/error-codes.md` 此前沿用了「实际有 0001–0013 / 从 0015 起」的错误结论，已同步更正。
- 本文与 inventory CSV 为当日实测，可独立作为底账引用；`docs/spec/purchase.md`、`docs/api/api.md`、`docs/db/*` 的采购章节尚待按本轮改动同步（进行中任务）。

### 10.5 结论与建议顺序

**当前不可对外提供服务**。按投入产出排序的最小闭环路径：

1. TD-04（前端租户）→ 一次性收掉余下 18 个页面，采购模块的做法可直接复制；
2. TD-01 + TD-02/03（鉴权 + 错误契约）→ 6 个主业务域控制器补类级 `@PreAuthorize`，errorCode 收常量并落文档 §3 码表；
3. TD-06 + TD-05（密钥 + DDL）→ 环境变量注入与轮换，schema 快照加 CI 校验；
4. TD-14（事件链路）→ 统一仓储 `save()` 语义，把"下单→物流""到货→入库/应付"变成可验证的横切能力；
5. TD-09 + TD-08（CI + 覆盖率基线）→ 1/2/3 步的回归保护；
6. TD-10 / TD-13（导入子系统收敛）→ 收益最大但改动面最广，放在契约稳定之后。

---

*报告生成：2026-03-22，全部数字来自当次工作树实测；数据底账见 `docs/tech/inventory/`。*
