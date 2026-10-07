# 跨境大卖ERP系统

基于领域驱动设计（DDD）的跨境电商 ERP，为卖家提供订单、物流、仓储、产品、采购、财务、客户与平台集成的一体化管理。前后端分离：React + Vite 前端，Java 17 + Spring Boot 3.5 后端，MySQL 8 存储，RocketMQ 用于领域事件。

> **文档索引**
>
> 产品与功能文档体系（产品定位 / 角色画像 / 功能全景 / 9 份模块规格 / API 总览 / 已知缺陷台账 / 冒烟清单 / 路线图）集中在 **[文档中心 `docs/README.md`](docs/README.md)**，按角色给了阅读路径；其 §4 定义**单一权威划分**与**编号命名空间**（`ISS-`/`S-M-L-`/`R-`/`US-`/`BR-`）。
>
> 工程文档（本仓库工程线所写）：
> - [技术架构设计](docs/architecture.md) — 分层、限界上下文、领域事件、事务、命名约定、技术债
> - [接口设计](docs/api/api.md) — 100 个端点清单、响应信封、错误处理、鉴权现状
> - [数据库设计](docs/db/data-dictionary.md) · [ER 图](docs/db/er-diagram.md) · [schema 快照](docs/db/schema.sql)
> - [测试文档](docs/testing.md) — 真实测试水位与修复路径
> - [部署与运维](docs/deployment.md) — 依赖矩阵、启动顺序、配置面、缺失能力
> - [安全与风险发现](docs/security-findings.md) — 逐条带行号的代码级事实清单
> - [架构决策记录](docs/adr/README.md) — ADR-0001 ~ ADR-0012（0001~0005 与 0002 为选型决策，0006~0012 为工程侧实现差距与技术债）

## 项目现状（2026-01-25 逐文件核对）

| 指标 | 实测值 |
|---|---|
| 后端主源码 | 266 个 `.java`（`domain` 117 / `infrastructure` 88 / `interfaces` 34 / `application` 26） |
| 控制器 / 端点 | 13 个 `@RestController` / **100 个端点**（已全部实现路由与方法体） |
| 持久化 | 24 张表（`@Entity`）+ 15 领域仓储接口 + 15 `*RepositoryImpl` + 15 Spring Data 接口 + 12 MapStruct Mapper |
| 领域事件 | 22 个 `*Event`，双通道发布（进程内 + RocketMQ） |
| 后端测试 | 10 个测试类 / 53 个 `@Test`（**多数存在失败或空断言问题**，见 [testing.md](docs/testing.md)） |
| 前端 | user-web 34 个页面（37 个 API 调用已打通后端）；admin-web 4 个页面（**尚未接入后端**） |
| CI / 迁移工具 / 覆盖率 | 均**无** |

**需要知道的三个前提**（不是待办清单，是当前事实）：

1. **鉴权链路被桩化**：`JwtTokenProvider.validateToken()` 恒返回 `true`、用户权限集合恒为空 → `/api/**` 的保护实际可绕过，而 `/api/tenants/**` 因 `hasRole` 永不满足而恒 403。修复顺序见 [security-findings.md](docs/security-findings.md)。
2. **事务覆盖不均**：客户模块应用服务已加 `@Transactional`，其余多数模块的多表写入仍无事务边界（历史"全仓 0 处"已不成立）。
3. **平台对接只有 Shopify 一家按官方协议实现，且未经真实平台验证**：分派已改为按认证协议的适配器注册表（`PlatformFetcherRegistry`），Shopify 走 `X-Shopify-Access-Token` + `Link`/`page_info` 游标 + 429 退避，覆盖增量拉单 / 商品 / 发货回写 / `test-connection`；Amazon / eBay / Magento / AliExpress / Wish 仍是**零适配器**（落到 `CUSTOM_SHOP` 的自建店铺约定：对 `storeUrl` 调 `/customers|/orders|/products`）。⚠️ **没有任何平台沙箱凭据**，正确性证据只有本地 fixture 契约测试（ISS-064，P0）；未联调环境可靠 `useSampleData` 示例链路。

## 设计理念

### 领域识别与划分

| 子域类型 | 子域名称 | 描述 | 重要性 |
|---------|---------|------|--------|
| 核心域 | 订单管理 | 处理订单生命周期，是系统的核心价值 | 高 |
| 核心域 | 物流管理 | 管理物流渠道和物流状态，直接影响客户体验 | 高 |
| 支撑域 | 仓储管理 | 支持订单和物流的执行，管理库存 | 中 |
| 支撑域 | 产品管理 | 管理商品信息，为订单和仓储提供基础数据 | 中 |
| 支撑域 | 采购管理 | 保证库存充足，支持销售业务 | 中 |
| 支撑域 | 财务管理 | 处理财务核算，为决策提供数据 | 中 |
| 支撑域 | 客户管理 | 管理客户信息，提升服务质量 | 中 |
| 通用域 | 平台集成 | 与外部电商平台对接，获取订单数据 | 低 |
| 通用域 | 用户与租户管理 | 多租户、用户、角色、认证授权 | 低 |

### 限界上下文

9 个上下文已在 `domain/` 下落地为包结构（含共享内核 `domain/common`）：
订单、物流、仓储、产品、采购、财务、客户、平台集成、租户（tenant：租户/用户/角色）。

每个上下文内部结构为 `aggregate / entity / valueobject / service / event / repository`，详见 [architecture.md §2](docs/architecture.md#2-分层与依赖方向)。

### 架构分层

```
interfaces  →  application  →  domain  ←  infrastructure
   19            21             102            77
```

单 Maven 模块内以包实现分层（[ADR-0012](docs/adr/0012-ddd-single-module-layering.md)），领域模型为纯 POJO，JPA 实体与领域模型经 MapStruct 双向隔离（[ADR-0010](docs/adr/0010-mapstruct-domain-jpa-bridge.md)）。

## 项目结构

```
ecommerce-logistics-erp/
├── backend/                     # Spring Boot 单 Maven 模块
│   ├── pom.xml
│   └── src/
│       ├── main/java/site/hexaarch/ecommerce/logistics/
│       │   ├── interfaces/      # 12 Controller · Result 信封 · GlobalExceptionHandler · dto
│       │   ├── application/     # 12 应用服务 · command/query（骨架，未接线）
│       │   ├── domain/          # 9 上下文 + common（聚合/值对象/领域服务/事件/仓储接口）
│       │   └── infrastructure/  # persistence(jpa+mapper+repository) · security · messaging · aop · config
│       ├── main/resources/application.yml   # 唯一配置文件（无 profile）
│       └── test/java/…                       # 10 个测试类
├── frontend/
│   ├── user-web/                # :5173 卖家业务端（34 页面）
│   └── admin-web/               # :5174 平台管理端（4 页面）
├── scripts/docker/docker-compose.yml   # 仅 RocketMQ（namesrv/broker/proxy）
├── docs/                        # 架构 / API / DB / 测试 / 部署 / ADR / 安全
├── LICENSE   COPYRIGHT   README.md
```

## 功能实现状态

图例：✅ 后端 API + 前端页面均已打通 · 🟡 后端有 API，前端仍是模拟数据 · ⚪ 尚未实现

### 1. 订单管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 创建订单 | 🟡 | `POST /api/orders`（tenant 目前硬编码 `default-tenant`） |
| 订单详情 | 🟡 | `GET /api/orders/{id}` |
| 按客户查订单 | 🟡 | `GET /api/orders/customer/{customerId}` |
| 订单状态变更 | 🟡 | `PUT /api/orders/{id}/status` |
| 订单列表 | ✅ | ~~无 list-all 端点~~ → `GET /api/orders`（`OrderController.java:64`，分页 + 9 个筛选条件），前端 `OrderList` 已去 mock |
| 订单取消 | ⚪ | 无专用端点（可用 status 变通） |
| 订单同步（平台） | 🟡 | ~~`OrderSyncService` 为桩实现~~（该类已不存在）→ `POST /api/platform-integrations/{id}/sync-orders` 走 `PlatformFetcherRegistry` 分派：`CUSTOM_SHOP` 真实 HTTP、`SHOPIFY` 按官方 Admin REST、`SAMPLE` 示例数据；结果落 `order_import_jobs`/`_errors`。⚠️ **未经真实平台验证**（ISS-064），且**无定时调度**（只能人工触发，`R-204`） |

### 2. 物流管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 物流单创建/详情/按订单查/状态变更 | 🟡 | 4 个端点 |
| 物流单列表 | ⚪ | 有 `GET /api/logistics`（全量无分页），前端未接 |
| **物流渠道管理** | ⚪ | 有表与仓储，**无 API** |
| **物流标签生成** | ⚪ | 无端点，状态可置 `LABEL_GENERATED` 但不产面单 |

### 3. 仓储管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 仓库创建/详情/列表 | 🟡 | 3 个端点 |
| 库存记录查询 | 🟡 | `GET /api/warehouses/{id}/inventory` |
| 库存盘点 | 🟡 | `POST .../inventory/count`（应用层为简化实现） |
| 库存移动 | ⚪ | `POST .../inventory/move` 因 `InventoryMovement` 无 Jackson 反序列化能力，**大概率 400** |
| **库位管理** | ⚪ | 有 `locations` 表，**无 API** |

### 4. 产品管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 创建产品/详情/按 SKU 查/上下架 | 🟡 | 5 个端点 |
| **产品列表** | ⚪ | 无 list 端点 |
| **SKU 管理** | ⚪ | 仅只读查询，无增删改 |
| **产品分类** | ⚪ | 无 `categories` 表、无端点（`category_id` 是现场拼的 UUID） |
| **产品属性管理** | ⚪ | 仅领域值对象 `ProductAttribute` |

### 5. 采购管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 采购单创建/详情/列表/加项 | 🟡 | 4 个端点（⚠️ 不带 status 时回退查 `PENDING`，非"全部"） |
| 采购审批（批准/拒绝） | 🟡 | `PUT .../approve`、`.../reject` |
| 运输中/已送达/取消 | 🟡 | 3 个端点 |
| **采购入库** | ⚪ | 无专用端点 |
| **供应商主数据** | ⚪ | 无 `suppliers` 表 |

### 6. 财务管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 交易创建/详情/按状态/按类型/按关联查 | 🟡 | 5 个端点 |
| 完成/失败/取消 | 🟡 | 3 个端点 |
| **订单对账** | ⚪ | 无端点 |
| **费用核算** | ⚪ | 有 `logistics_fees` 表，无核算逻辑 |
| **报表生成** | ⚪ | 无端点 |

### 7. 客户管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 创建/详情/更新/删除 | ✅ | 软删除；删除后不可再变更（`DELETED` 终态） |
| 客户列表 | ✅ | 分页 + ID/名称/邮箱/电话/状态/平台/关键字/注册时间区间筛选 |
| 状态变更 | ✅ | activate/deactivate/suspend 端点 + 前端按钮 |
| **平台客户导入** | ✅ | `/api/customers/import` 10 端点 + `CustomerImport.jsx`：平台买家接口直拉 / 从平台订单萃取买家 / 文件上传（JSON/CSV/xlsx），字段映射模板、预览、幂等 upsert、行级异常、任务化结果反馈 |
| 平台一键同步客户 | ✅ | `POST /api/platform-integrations/{id}/sync-customers` |
| **客户沟通记录** | ⚪ | 无模型无端点（P1） |
| **客户分类/标签** | ⚪ | 仅 `CustomerStatus` 枚举（P1） |

### 8. 平台集成
> 🔄 **2026-03-08 更新**（详见 [docs/spec/platform-integration.md](docs/spec/platform-integration.md) 与 [docs/tech/integration-design.md](docs/tech/integration-design.md)）：

| 功能 | 状态 | 说明 |
|---|---|---|
| 集成配置 CRUD/启停 | ✅ | 14 个端点。**出入参已改 DTO**（`PlatformIntegrationSaveRequest` / `PlatformIntegrationDto`，出参 `apiKeyMasked` + `hasApiSecret`/`hasAccessToken` 布尔，不再回传任何凭据）；落库为 AES-256-GCM 密文（`enc:v1:` 前缀）。⚠️ 前提是环境变量 `PLATFORM_CREDENTIAL_MASTER_KEY` 已注入，缺键时加密静默关闭（ISS-011 门槛 / ISS-064 ③） |
| 订单/客户/产品/库存/物流同步 | ✅ | 14 端点，走 `PlatformFetcherRegistry` 按**认证协议**分派；`useSampleData=true` 时走示例链路 |
| **按认证协议分派适配器** | ✅ | ~~`useSample ? sample : http` 布尔三元~~ → 注册表按 `PlatformAuthType` 分派。新增一家平台 = 注册一个 `type()` 返回该协议名的适配器，不改导入服务 |
| **Shopify（首家按官方协议对接）** | 🟡 | **代码符合官方文档规范、经 fixture 验证，但未经真实平台验证**：`X-Shopify-Access-Token` + `Link` 头 `page_info` 游标（从不发 `page`）+ 250 上限 + 429 退避；订单增量拉单 / 商品拉取 / 发货回写 / `test-connection`。**库存推送未实现**（需 `inventory_item_id`/`location_id` 主数据映射）。无沙箱凭据，27 例契约测试全是本地 `HttpServer` 桩 + 手写 fixture ⇒ **ISS-064（P0）** |
| Amazon / eBay / Magento / AliExpress / Wish | ⚪ | **零适配器**（运行时只会落 `CUSTOM_SHOP` 那条自建店铺约定通道）。卡点在**平台侧准入**不在代码：开发者账号、APP 审核、OAuth/LWA + RDT、scope 申请。逐家前置见 [integration-design §11](docs/tech/integration-design.md) |
| 定时调度 / 对账 | ⚪ | 无 `@Scheduled`，同步只能人工点（`R-204`）；`syncFinanceFromPlatform` 是**无入口的死方法** |

### 9. 租户 / 用户 / 角色（README 早期未列，代码已实现）
| 功能 | 状态 | 说明 |
|---|---|---|
| 登录 / 刷新 / 登出 | ✅ | 前端 `AuthContext` + 3 个端点（⚠️ 前端存在 token 双重解包 bug） |
| 租户管理 9 端点 | 🟡 | 前端已实现 9 个调用，但**后端因角色判断恒 403** |
| 用户管理 11 端点 | ✅ | 前后端已打通 |
| 角色与权限管理 10 端点 | ✅ | 前后端已打通（权限点是自由字符串，无字典） |

## 技术栈

### 后端
Java 17 · Spring Boot **3.5.9** · Spring MVC · Spring Data JPA / Hibernate 6（`MySQL8Dialect`）· Spring Security + JJWT 0.12.5（HS512）· MySQL Connector/J · RocketMQ `rocketmq-spring-boot-starter` 2.2.3（服务端 5.3.2）· springdoc-openapi 2.6.0 · MapStruct 1.5.5.Final · Lombok 1.18.34 · Maven（无 wrapper）

### 前端（两个独立工程）
React 19.2 · Vite 7.2 · Ant Design 6.2 + `@ant-design/icons` 6.1 · React Router DOM 7.12 · axios 1.13 · JavaScript/JSX（无 TypeScript）· ESLint 9 flat config

## 如何运行

依赖版本与完整说明见 [docs/deployment.md](docs/deployment.md)。

### 前置
```bash
# 1) 建库（表结构由 Hibernate 自动创建，无需手工建表）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS ecommerce_logistics_erp \
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 2) 一键拉起数据库与消息中间件（MySQL 8.0 + RocketMQ 5.3.2）
cd scripts/docker && ./erp-services.sh start
#   其他管理命令：stop / restart / status / logs / ps / down / reset
#   端口映射：MySQL 宿主机 3306（容器内 3306）· RocketMQ namesrv 9876 · broker 10909/10911/10912 · broker 内嵌 proxy 8080/8081
```

> 数据库默认密码 `123456`，库名 `ecommerce_logistics_erp`，字符集 `utf8mb4`，
> 均与 `backend/src/main/resources/application.yml` 的 `spring.datasource` 一致。
> 数据通过 Docker 命名卷持久化，重启容器不丢数据。
>
> RocketMQ 虽然随服务一起启动，但 `application.yml` 中 `rocketmq.enabled=false`，
> 领域事件当前只走进程内通道；若需真正启用 MQ 发布，请置 `rocketmq.enabled=true`
> 后重启后端（broker 地址 `localhost:9876`，已自动建 Topic）。

### 后端
```bash
cd backend
mvn clean compile
mvn spring-boot:run          # http://localhost:8082
```
首次启动会创建默认租户与管理员账号 **`admin` / `123456`**（口令硬编码在 `DataInitializationConfig`，见 [security-findings.md S-04](docs/security-findings.md)）。
API 文档：`http://localhost:8082/swagger-ui.html`。

### 前端
```bash
cd frontend/user-web  && npm install && npm run dev   # http://localhost:5173
cd frontend/admin-web && npm install && npm run dev   # http://localhost:5174
```
两个工程都没有配置 Vite proxy，前端直连 `http://localhost:8082/api`，依赖后端 CORS（当前仅放行 5173）。

### 测试与静态检查
```bash
cd backend          && mvn test        # 53 个用例；当前预计不通过，原因见 docs/testing.md §3
cd frontend/user-web  && npm run lint
cd frontend/admin-web && npm run lint
```

## 已知问题与改进路线

按影响排序，详细论证见对应文档。

| 优先级 | 事项 | 出处 |
|---|---|---|
| P0 | 修复认证链路（`validateToken` 恒真、authorities 恒空、CORS/路径规则不匹配、filter 绕过注入） | [security-findings.md](docs/security-findings.md) S-01~S-03、[ADR-0007](docs/adr/0007-jwt-auth-and-tenant-context.md) |
| P0 | 凭据与配置外部化（DB 口令、`jwt.secret`、CORS origin、前端 baseURL），引入 profile | [ADR-0009](docs/adr/0009-config-externalization.md) |
| P0 | 补齐声明式事务边界（下单、采购审批、库存移动三条主链路） | [architecture.md §4](docs/architecture.md#4-事务边界关键缺口) |
| P1 | 让 `mvn test` 通过并替换 `assertTrue(true)` 空断言；建立 CI | [testing.md §3/§4](docs/testing.md) |
| P1 | 统一响应封装与错误码字典；停止直接序列化领域聚合（剔除 `apiSecret`） | [ADR-0011](docs/adr/0011-result-envelope-and-errors.md) |
| P1 | 业务表补 `tenant_id`，`tenantId` 一律从令牌取 | [ADR-0007](docs/adr/0007-jwt-auth-and-tenant-context.md) |
| P2 | 引入分页/排序约定（列表接口目前无界返回） | [api.md §1](docs/api/api.md) |
| P2 | 切换 Flyway（`ddl-auto: none`），补唯一约束与索引 | [ADR-0002](docs/adr/0002-hibernate-ddl-auto.md) |
| P2 | 补齐缺失读接口（订单/客户/产品列表、物流渠道、库位、SKU/分类） | [api.md §10](docs/api/api.md) |
| P3 | 决策消息链路：单体阶段只留进程内事件，或按 ADR-0006 修通 MQ（当前双通道会重复副作用） | [ADR-0006](docs/adr/0006-rocketmq-domain-events.md) |
| P3 | admin-web 接入后端（认证 + CORS + API 层）；前端共享代码抽取 | [ADR-0008](docs/adr/0008-frontend-user-admin-split.md) |

## 产品与功能文档

上一节是**技术视角**的文档；下面这些是**产品与功能视角**的补充文档，入口同样是 [`docs/README.md`](docs/README.md)。

| 分类 | 内容 |
|---|---|
| 产品层 [`docs/product/`](docs/product/) | 产品概览与成功指标、6 类用户画像与场景、功能全景**实现状态矩阵**、信息架构与页面清单、8 条端到端业务流程、术语表、用户故事与验收标准、版本路线图 |
| 规格层 [`docs/spec/`](docs/spec/) | 9 个模块的功能规格：状态机、业务规则 BR-xx、接口对照、领域事件、缺口与验收要点 |
| 质量层 [`docs/qa/`](docs/qa/) | 已知缺陷清单（ISS-xxx，含用户可感知影响与规避）、冒烟测试清单 |

> ⚠️ 提示：本节「功能实现状态」表格是**代码视角**的功能/端点清单；若要判断"用户能否真的用它完成工作"，请对照
> [`docs/product/03-feature-map.md`](docs/product/03-feature-map.md) 的端到端可用性分级（A/B/C/D 四级）。

## 贡献指南

1. 分支：从 `main` 切出 `feat/<scope>-<summary>` 或 `fix/<scope>-<summary>`。
2. 提交信息：中文或英文均可，但需说明**动机**而非仅"改了什么"；一次提交只做一件事。
3. 变更表结构 → 改 JPA 实体后**必须同步 `docs/db/schema.sql`**（当前 schema 由 Hibernate 生成，文档是唯一评审载体，见 [ADR-0002](docs/adr/0002-hibernate-ddl-auto.md)）。
4. 新增/变更端点 → 同步更新 [docs/api/api.md](docs/api/api.md) 的端点清单，并补 `@Operation`/`@Schema`。
5. 提交前本地执行：`mvn -f backend/pom.xml test`、两个前端的 `npm run lint` 与 `npm run build`。
6. 涉及架构取舍（新增依赖、改变分层边界、引入中间件）→ 在 `docs/adr/` 新增一条 ADR。

## 许可证

本项目采用研究与商业授权双轨制许可证，详细条款见 [LICENSE](./LICENSE)。简要说明：
- 个人研究和学习使用：允许自由使用、修改和分发
- 商业使用：需获得书面授权
- 禁止将本项目代码用于盈利性或商业化活动

## 联系方式

如有问题或建议，请联系项目维护者。
