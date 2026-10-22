# ADR-0013 · 店铺实体与平台对接元信息分表

- **状态**：部分实现（2026-10-07）——§5 决策 1 与决策 4 已落地：`shops` 聚合/仓储/应用服务/控制器与脱敏出参已上线，`shop:*` 四个权限点已进 catalog 且**实测种子生效**（运行库 PLATFORM_ADMIN=26、TENANT_ADMIN=23），§4.3 锁 1/锁 2 的守卫测试已建（`TenantParameterGuardTest`）。**未落地**：决策 2（`platform_integrations` 收敛为对接定义）、决策 3（`orders.shop_id`）、决策 5（发货回写两层标记）、§6 步骤 2 的显式 DDL（当前 `shops` 仍由 `ddl-auto=update` 按实体建表，`auth_status`/`push_back_policy` 用 `varchar` 列而非 ENUM，故未触发 ADR-0002 的窄化，但列宽与字符集尚未按 migration-plan §4 固化）、§6 步骤 5 的外呼凭据切流、§6 步骤 7 的前端门控。
- **日期**：2026-10-06（状态更新 2026-10-07）
- **取代/关联**：扩展 [ADR-0003 多租户隔离](./0003-multi-tenancy-isolation.md) 的隔离面；依赖 [ADR-0001 DDD](./0001-domain-driven-design.md)、[ADR-0010 MapStruct 桥接](./0010-mapstruct-domain-jpa-bridge.md)、[ADR-0012 单模块分层](./0012-ddd-single-module-layering.md)；与 [ADR-0002 ddl-auto](./0002-hibernate-ddl-auto.md) 的迁移义务直接冲突，见 §6
- **动因**：新商家（跨境卖家）入驻推演 + 对标店小秘的「注册 → 加店铺 → 首单履约」路径审计

---

## 1. 背景

### 1.1 领域事实：归属层级是三层，今天压平在一张表里

```
开发者应用（LWA client_id/client_secret）    归属「我们 + 区域」
  └── 卖家授权（refresh_token）              归属「某卖家账号在某区域」，一个 token 覆盖其全部已授权站点
        └── 站点（marketplace_id）           US/DE/JP…，订单、库存、发货回写都发生在这一层
```

`platform_integrations` 现在同时装着这三层的属性，外加「属于哪个租户」。后果是**最底下那层（站点）在数据模型里不存在**：

| 实测事实 | 证据 |
|---|---|
| `orders` 14 列，只有 `platform` 与 `platform_order_id`，**无店铺/站点维度** | 运行库 `information_schema` 逐列核对 |
| `AmazonOrderFetcher` 把集成上所有站点一次性塞进 `MarketplaceIds` | `infrastructure/platform/amazon/AmazonOrderFetcher.java:105` |
| 抓回的每单自带的 `MarketplaceId` **从不回读**，`toRow` 丢弃 | `AmazonOrderFetcher.java:154-167` |
| `marketplace_ids` 是 `varchar(512)` 逗号串，无法承载站点级水位与状态 | `PlatformIntegrationJpaEntity.java:97` |

⇒ 一个同时开美国站、德国站、日本站的卖家，订单在**拉回来的那一刻**就混成一锅，无法按店筛选、无法按站点回写发货、无法按站点归集财务。这不是缺功能，是缺一个实体。

### 1.2 迁移红利：现库无存量，可以一刀切

本轮直读 `ecommerce_logistics_erp` 实测：`platform_integrations` **0 行**、`shop_warehouses` **0 行**、`orders` 63 行且平台分布只有 `FILE`(57) + `MANUAL`(6)，**没有任何一条来自平台对接**。

⇒ 本决策不需要数据迁移、不需要回填策略、不需要兼容旧数据。用户 2026-10-06 明确：**系统未上线，一刀切重建，把基础数据初始化好即可**。

### 1.3 一个必须先修的结构性阻塞

运行库 `platform_integrations.platform_name` 是 MySQL **ENUM，只有 7 个值**，而 `PlatformName` 有 12 个。回滚式插探针复现：

```
INSERT ... platform_name='OZON'  →  ERROR 1265 (01000): Data truncated for column 'platform_name'
（sql_mode 含 STRICT_TRANS_TABLES）
```

`auth_type` 的 ENUM 则是完整的 11 个值，两者不对称。后果：Shopee / Ozon / Mercado Libre / TikTok Shop / Temu 五家**建不出集成记录**（`PlatformName` 12 值 − ENUM 7 值），其中 **Shopee、Ozon、Mercado 三家已有适配器**（`docs/spec/platform-integration.md` §7.1：SHOPEE 6 类、OZON 5 类、MERCADO_LIBRE 4 类），也就是说能对外呼的代码已经写好、却在建记录这一步被列型挡住。`ddl-auto=update` 既不会补 ENUM 值也不会改类型，所以这只能靠显式 DDL 解决——它也是本 ADR 的前置项（§6 步骤 1）。

同轮实测的另一处失真：`id` 在运行库是 **`binary(16)`**（Hibernate 6 把 UUID 存成 16 字节原生二进制，不是字符列），`docs/db/schema.sql` 原记 `CHAR(36)`；`schema.sql` 还整块漏记 `region`/`external_shop_id`/`marketplace_ids` 三列。已于 2026-10-06 就地修正。

⚠️ 顺带量到的更大面：**全库 38 张表的 `id` 有 4 种类型**——`varchar(255)` 35 张、`varchar(64)` 1 张（`customers`）、`binary(16)` 1 张（`platform_integrations`）、`bigint` 1 张（`purchase_order_items`）。这不是洁癖问题：`shops.integration_id` 要指向 `platform_integrations.id`，照现状就是「字符列外键指向二进制列」，比较与隐式转换行为不一致；所以 §6 步骤 1 的重建口径必须是**统一 `VARCHAR(36)`**（UUID 字符串），把这几张异构表一起收敛，而不是只新建 `shops`。

---

## 2. 决策

1. **新增 `shops` 聚合，粒度取「站点」**（一站点一行）。这是租户的资产表，`tenant_id NOT NULL`。
2. **`platform_integrations` 收敛为「对接定义」**：只留平台/协议、我们自己的 app 级凭据、接口版本默认值、区域与限流。**去掉 `tenant_id`，去掉卖家级凭据列**。
3. **`orders` 只加 `shop_id` 一列**，不加 `integration_id`。归属只需到站点粒度，连接是店铺的属性；两列并存迟早出现互相矛盾的行。
4. **权限新增 4 个租户级点**（`shop:read` / `shop:write` / `shop:credential_write` / `shop:sync`），并把隔离要求做成**可执行守卫测试**（§5 三道锁）。
5. **发货回写拆两层**：`shops.push_back_policy`（店铺级策略开关）+ 物流单上的 `fulfillment_push_status`（单级事实）。
6. **一刀切重建数据库**，不写迁移脚本；重建后由 `DataInitializationConfig` 保证基础数据齐备。

### 2.1 为什么粒度选「站点」而不是「卖家账号 + 站点子表」

| | **（A）站点粒度 ← 采纳** | （B）账号粒度 + `shop_marketplaces` 子表 |
|---|---|---|
| 凭据 | 同卖家多站点各存一份 token | 只存一份 |
| 令牌刷新 | N 个站点 = N 次 LWA 换发（有 5 分钟临期判断兜底，`PlatformCredentials.java:40,98`） | 1 次 |
| 订单归属 | `orders.shop_id` 直达站点 | 要多跳一次到 marketplace 子行 |
| 改造半径 | 小：`PlatformCredential.from(integration)` → `from(shop)` | 需新增聚合与仓储 |
| 与店小秘一致 | 是（一站点一店铺、各自授权） | 否 |

选 (A) 的理由：零存量红利下先拿到「订单能按站点分回来」这个**正确性**，代价（token 冗余、多次刷新）都是可容忍的浪费，而 (B) 的收益（少一次刷新）解决的是效率问题。**将来成为 SP-API public developer 后需要重估**，届时只是把凭据列抽走，`shops` 语义与 `orders.shop_id` 都不用动——这条写在 §8 复审触发条件里。

### 2.2 表结构

```sql
CREATE TABLE shops (
  id                      VARCHAR(36)  NOT NULL,        -- 显式 varchar，绝不重演 ENUM/binary(16) 的窄化
  tenant_id               VARCHAR(64)  NOT NULL,        -- 必填，不给「空=全局」这种一列两语义
  integration_id          VARCHAR(36),                  -- 指向对接定义，可空（自授权模式为空）
  platform                VARCHAR(32)  NOT NULL,        -- 与 orders.platform 同宽
  shop_code               VARCHAR(128) NOT NULL,        -- 卖家自定店铺编码，租户内唯一，见 §2.3
  shop_name               VARCHAR(128) NOT NULL,
  marketplace_id          VARCHAR(64),                  -- ATVPDKIKX0DER；无市场概念的平台为 NULL
  region                  VARCHAR(32),                  -- NA/EU/FE，保存时按 marketplace_id 反推校验
  external_shop_id        VARCHAR(128),                 -- shop_id / shop_cipher / seller user_id
  store_url               VARCHAR(255),                 -- Shopify 域、CUSTOM_SHOP 基址
  api_key                 VARCHAR(512),                 -- 密文；宽度较旧表翻倍，见下方说明
  api_secret              VARCHAR(512),
  access_token            VARCHAR(2048),
  refresh_token           VARCHAR(2048),
  token_expires_at        DATETIME(6),
  api_version             VARCHAR(32),
  auth_status             VARCHAR(24)  NOT NULL,        -- PENDING/ACTIVE/ERROR/EXPIRED
  push_back_policy        VARCHAR(16)  NOT NULL,        -- AUTO/MANUAL/OFF
  active                  BIT          NOT NULL,
  last_orders_synced_at   DATETIME(6),                  -- 站点级水位
  last_products_synced_at DATETIME(6),
  created_at              DATETIME(6)  NOT NULL,
  updated_at              DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shops_tenant_code (tenant_id, shop_code),
  UNIQUE KEY uk_shops_marketplace (tenant_id, platform, marketplace_id),
  KEY idx_shops_tenant (tenant_id),
  KEY idx_shops_integration (integration_id)
) ENGINE = InnoDB;

ALTER TABLE orders ADD COLUMN shop_id VARCHAR(36);
CREATE INDEX idx_orders_shop ON orders (shop_id);
```

密文列宽度是顺带修的债：`CredentialCipher` 的 `enc:v1:` + Base64 让密文比原文长约 `7 + 4/3 × (原文 + 28)`，旧表 255 只能容纳约 180 字符原文，eBay 的 user access token 已记录可能超 1024。新表一次给到 512/2048，比事后 `ALTER` 便宜。

`uk_shops_marketplace` 的作用是禁止把同一个亚马逊站点绑两次；MySQL 唯一索引不约束 NULL，所以无站点的平台天然不受它限制，这正是我们要的行为。

### 2.3 `shop_code` 取代 COALESCE 规则（对草案的一处修正）

草案曾取 `shop_key = COALESCE(marketplace_id, external_shop_id, store_url)`。逐协议核对 `PlatformAuthCatalog` 的必填/选填字段后发现它**容不下「凭据即店铺」的平台**：

| 协议 | 必填字段（catalog 行号） | 店铺身份来源 | COALESCE 是否有值 |
|---|---|---|---|
| `AMAZON_SP_API` | `apiKey/apiSecret/refreshToken/marketplaceIds`（:68） | `marketplace_id` | ✅ |
| `SHOPIFY` | `storeUrl/accessToken`（:35） | `store_url`（店铺域） | ✅ |
| `CUSTOM_SHOP` | `storeUrl/apiKey/apiSecret`（:23） | `store_url` | ✅ |
| `MAGENTO` | `storeUrl/apiKey/apiSecret`（:50），scope 见 `default/website/store`（:53） | `store_url` + 站点码 | ⚠️ 多 website 会塌成一行 |
| `EBAY_SELL` | `apiKey/apiSecret/refreshToken`（:84），`marketplaceIds` 仅选填（:85） | 卖家身份在 token 内 | ❌ 无 |
| `SHOPEE` | 含 `externalShopId`（:106） | `shop_id` | ✅ |
| `TIKTOK_SHOP` | 含 `externalShopId`（:125） | `shop_cipher` | ✅ |
| `MERCADO_LIBRE` | `apiKey/apiSecret/refreshToken`（:159），`externalShopId` 选填但调用期必需（`MercadoApi.java:226`） | 卖家 `user_id` | ⚠️ |
| `ALIEXPRESS` | `apiKey/apiSecret/accessToken`（:142） | 卖家身份在 session 内 | ❌ 无 |
| `OZON` | `apiKey/apiSecret`（:176） | `Client-Id` 即店铺 | ❌ 无 |
| `TEMU` | `apiKey/apiSecret/accessToken`（:191） | 无独立标识字段 | ❌ 无 |

三个 ❌ 会让 `shop_code` 取到 NULL，而 MySQL 唯一键不管 NULL ⇒ 「一个店都没有」和「无限个同名店」同时成立。三个 ⚠️ 会把本应分开的店铺塌成一行。

改为：**`shop_code` 由卖家必填**（对标店小秘添加店铺时的「店铺名称/编码」），租户内唯一，作为主身份；`marketplace_id` 降为**属性**并配一条辅助唯一键。这一版对 11 个协议全部成立，且不依赖任何平台恰好提供标识字段。`EBAY_SELL`/`ALIEXPRESS`/`OZON`/`TEMU` 这类「凭据即店铺」的平台，一个 token 一行 `shops`，语义也正确。

### 2.4 回写标记两层，不合列

- `shops.push_back_policy`：`AUTO`（发货即推平台）/ `MANUAL`（人工点推）/ `OFF`。这是**店铺策略**。
- 物流单上 `fulfillment_push_status`：`NONE/PENDING/PUSHED/FAILED` + `pushed_at` + `push_error`。这是**单级事实**。

不合列的理由：合成一列后，改开关等于重写了历史语义，事后查不出「这单当时到底推没推」。业务目标 O1/O5 要求可追溯，这一层不能省。

现状缺的是执行方：`PlatformFulfillmentPusher` 只有 Shopify/Magento/eBay/Ozon 四家（`infrastructure/platform/*/*FulfillmentPusher.java`），**Amazon 无实现**，`POST /{id}/push-fulfillment` 对亚马逊直接抛 `PLATFORM_ADAPTER_NOT_FOUND`（`PlatformFetcherRegistry.java:128-140`）。本决策只定义字段与语义，pusher 实现是 §7 待办。

---

## 3. 主流平台适配自检

`shop_code` 方案下，11 个协议都能落成 `shops` 行，且每条的必填项与 catalog 一致。补三个约束以免日后失真：

1. `marketplace_id` 与 `region` 的合法性由服务层校验（亚马逊 NA/EU/FE 三区的站点归属表内置），选错区域时报**指向字段**的错误而非泛化 500。今天 `AmazonSpApi.java:194` 已经是这个形状，沿用。
2. `MAGENTO` 的多 website：把站点码写进 `shop_code`（如 `mage-main-com`），不要指望 `store_url` 区分。
3. 一次只接一个站点，**不再有 50 站点上限的逗号串**。`AmazonSpApi.java:151` 的 `MAX_MARKETPLACE_IDS` 校验降级为「单值非空」，翻页逻辑不变。

---

## 4. 权限模型（本 ADR 的核心）

### 4.1 新增 4 个租户级权限点

`domain/tenant/PermissionPoints.java` 的 `TENANT_SCOPED_PERMISSIONS` 从 19 项增至 23 项：

| 权限点 | 覆盖动作 | 为什么要单独一点 |
|---|---|---|
| `shop:read` | 店铺列表/详情（脱敏出参） | 运营、仓管、财务都要按店筛选订单，都需要读，但不该能改 |
| `shop:write` | 建店、改 `shop_code`/`shop_name`、`active` 启停、`push_back_policy` | 改回写开关会改变发货行为，改店名不会 |
| `shop:credential_write` | 写凭据列（`api_key`/`api_secret`/`access_token`/`refresh_token`/`marketplace_id`/`store_url`）、`auth_status` | **最高危**：把已有店铺指到攻击者的 token，就能用受害者配额拉回攻击者店铺的数据并污染订单归属 |
| `shop:sync` | 触发拉单/拉商品、`test-connection` | 消耗平台限流配额，且把买家 PII（姓名/地址/邮箱）拉进系统 |

分授的现实形态：老板给运营 `shop:read + shop:sync`（不给凭据），给技术对接人 `shop:credential_write`（不给业务写）。合并成两点会让运营能换 token，所以宁可 catalog 多两行。

**为什么是 `credential_write` 而不是 `credential:write`（2026-10-07 实现轮定稿）**：`domain/tenant/entity/Role.java:45` 的 `PERMISSION_PATTERN = ^[a-z][a-z0-9_]*:[a-z0-9_*-]+$` 只允许**一个冒号**（模块:动作）。三段名会让 `addPermission()` 抛 `PERMISSION_INVALID`，`resetPermissions()` 整批回滚，而种子逻辑 `DataInitializationConfig` 的 `catch (Exception)` 把它压成一条启动日志——结果是**所有角色的权限点停在旧清单、新点恒 403、测试全绿**（实测见 ISS-090）。这里的取舍是**改点名而不是放宽校验**：放宽正则等于允许任何三段字符串进角色，那些点没有任何注解消费，会变成「配了却不生效」的死权限，比命名不整齐更糟。代价由 `TenantParameterGuardTest` 兜住：它断言 catalog 每一项都能过 `Role.validatePermission()`，并反向断言 `@PreAuthorize` 里引用的每个点都在 catalog 中。

**不下沉的三项不变**：`platform:manage`、`user:manage`、`role:manage` 仍是平台专属（既有决策，理由见 `PermissionPoints.java:42-46` 的注释——平台管理员在隔离切面里是旁路的，下沉即跨租户泄漏）。`platform_integrations` 的写继续由 `platform:manage` 守着，因为它含**我们自己的** app 凭据。

### 4.2 端点 × 权限矩阵

| 端点 | 注解 | 隔离手段 |
|---|---|---|
| `GET /api/shops` | `hasRole('PLATFORM_ADMIN') or hasAuthority('shop:read')` | 服务签名 `listShops(tenantId)`，且 **tenantId 必填非空**（平台管理员也必须显式指定租户，见 §4.4） |
| `GET /api/shops/{id}` | 同上 | `findByIdAndTenantId` |
| `POST /api/shops` | `… or hasAuthority('shop:write')` | 主数据创建，凭据列为空 |
| `PUT /api/shops/{id}` | `… or hasAuthority('shop:write')` | **不接受任何凭据字段**（DTO 层剥离，见 §4.5） |
| `PUT /api/shops/{id}/credentials` | `… or hasAuthority('shop:credential_write')` | 专用端点，不与主数据共用 |
| `POST /api/shops/{id}/test-connection` | `… or hasAuthority('shop:sync')` | 出连 |
| `POST /api/shops/{id}/sync-orders` | `… or hasAuthority('shop:sync')` | 出连 + 写订单，落库时校验 `shop_id` 与 `tenantId` 一致 |
| `POST /api/shops/{id}/push-fulfillment` | `… or hasAuthority('shop:sync')` | 出连 + 回写 |
| `PUT /api/shops/{id}/push-back-policy` | `… or hasAuthority('shop:write')` | 改策略 |
| `DELETE /api/shops/{id}` | `… or hasAuthority('shop:write')` | 有订单引用时拒绝删除（返回业务错误码，不静默级联） |

`platform_integrations` 侧不变：读放开、写仍 `platform:manage`。

### 4.3 三道锁：把约定变成能失败的测试

隔离机制的真实形状（`infrastructure/aop/TenantIsolationAspect.java:58-88`）必须被正确理解，否则守卫会假绿：

- 它是 `@Before`，**不校验返回值、不过滤查询结果**，只做一件事：若服务方法有一个**形参名恰好叫 `tenantId`** 的 String 参数且与上下文不等，抛 `TENANT_ACCESS_DENIED`。
- **PLATFORM_ADMIN 直接 return**（:64-66），旁路。
- **`parameterNames == null` 时直接 return**（:70-72）。参数名依赖 `-parameters` 编译标志。本轮实测编译产物 `RoleServiceImpl.class` 含 12 处 `MethodParameters` 属性，说明当前生效（`spring-boot-starter-parent` 的 `pluginManagement` 配了 `<parameters>true</parameters>`，`backend/pom.xml:170-174` 自声明 compiler 插件时未覆盖它）。**但没有任何东西防止有人日后清理 pom 把它关掉**——一旦关掉，整套越权校验静默失效，且不报错。这是本系统权限的单点故障。

三道锁因此是：

1. **`TenantParameterGuardTest`**：反射遍历 `application.service..` 的所有 public 方法，断言参数名集合含 `tenantId`。同时断言 `method.getParameters()[i].getName()` 不等于 `arg0` 形状（即 `-parameters` 未失效）。这条测试让 §4.2 的「服务签名」从口头约定变成 CI 门禁，也是 ISS-050 那类「读路径无租户过滤」缺陷的根治手段。
2. **仓储只提供带租户的方法**：`ShopRepository.findByIdAndTenantId(...)`，不提供裸 `findById`。ADR-0010 的 MapStruct 桥接层同样不暴露无租户查询，让「忘了过滤」在编译期不可表达。
3. **跨实体引用校验**：`orders.shop_id`、`shop_warehouses.shop_id`、各 `*ImportJob.shop_id` 在写入前必须校验目标店铺属于同一租户。现状是 `shop_warehouses` 用裸字符串 `shop_code` 引用、无外键（文档自标 R-01），本次一并改成 `shop_id`。

### 4.4 平台管理员旁路的收口

`isPlatformAdmin()` 旁路 + 读路径无过滤 = 今天 ISS-050 的形态（A 租户能看到 B 的数据）。`shops` 不留这个口子：**平台管理员调用店铺读接口时 `tenantId` 仍为必填**，因为该参数只在「与上下文不等」时才报错，而平台管理员走的是切面提前 return 的路径，参数校验必须由服务层自己做（`StringUtils.hasText(tenantId)` 非空断言）。跨租户浏览不在 user-web 提供，走 admin-web 的租户维度页面。

> 反例警告，禁止照抄：`PlatformIntegrationController.getAllPlatformIntegrations()`（:98-110）不接受 `tenantId`、直接委托无过滤的服务（`PlatformIntegrationApplicationService.java:95-97`）。任何「先放开注解」的改动即跨租户泄漏（ISS-070 已就此明确禁止）。`shops` 从第一版就要带租户参数，不要复制这个形状。

### 4.5 凭据出口纪律

复用 `PlatformIntegrationDto` 的既有形状（:85 `mask()`、:64 只出 `apiKeyMasked`、`hasApiSecret/hasAccessToken/hasRefreshToken` 三个布尔、注释声明 BR-02 不再回传 `apiSecret`/`accessToken`）。本轮实测该 DTO **不含** `refreshToken` 字段，形状是干净的，`ShopDto` 照此实现并增加：

- 写入接口**不回显提交值**，只回 `credentialConfigured` + 掩码 + `tokenExpiresAt`；
- 日志侧沿用 `ApiLoggingFilter.maskSensitive()`（:220），并补 `refreshToken` 到脱敏字段名清单（确认项，勿假设已有）；
- 凭据写入与 `push_back_policy` 变更需要落审计——**当前库无任何 `*audit*` 表**（`SHOW TABLES LIKE '%audit%'` 空），这是 §7 待办而非既有能力。

### 4.6 前端门控与权限点必须同源

- `constants/menus.js:70-78` 今天只给「租户管理」加了 `roles` 门，「平台集成/用户管理/角色管理」对 TENANT_ADMIN 无条件可见——实测三个入口 GET 全 403，形成「菜单骗人」。`/shops` 上线时必须带 `permission: 'shop:read'` 门控，且这轮一并修掉既存的三条。
- `pages/role/RoleList.jsx:12-37` 的 `PERMISSION_PRESETS` 是后端 catalog 的**硬编码副本**（`PermissionPoints.java:13-16` 自陈漂移风险）。新增 4 点若只改后端，界面就配不出来。解法：开 `GET /api/permissions` 由后端下发 catalog，前端不再抄表。
- 403 必须带响应体：`/api/tenants` 的 403 今天是 0 字节（在过滤器层被拒），被 axios 拦截器误报成「网络错误，请检查网络连接」（ISS-058/032）。店铺域新端点不得重演——`AuthenticationEntryPoint`/`AccessDeniedHandler` 要产出统一信封。

---

## 5. 与业务目标的对齐

对照 `docs/product/01-product-overview.md` §4：

| 目标 | 本决策如何推进 | 不推进的部分 |
|---|---|---|
| 北极星：日均系统内闭环履约订单数（待处理→已完成 + 生成财务流水） | 站点归属是「闭环」的前提：回写要知道往哪个站点推，流水要能按站点/币种归集 | 闭环本身仍缺库存预留、出库、流水生成（ISS-077） |
| O1 统一多平台订单入口（基线 0%） | `shops` 让按店拉单成为可能，是 0% → 可用的第一块地基 | 无调度器，仍需人工点同步（`@Scheduled` 全仓 0 命中） |
| O2 履约过程可视化 | 订单可按店铺维度筛，回写状态首次可见 | 轨迹查询仍是桩（桩在 `domain/logistics/service/LogisticsTrackingService.java:30-32`：注释「这里应该调用物流渠道的API获取最新的物流状态 / 简化处理，直接返回当前状态」；调用方 `application/service/LogisticsApplicationService.java:226-234` 把返回的原状态记成一条「已查询物流跟踪信息」轨迹） |
| O3 库存可信 | 不解决 | 超卖风险见 `BusinessProcessOrchestrationService.java:75-81` 空实现 |
| O5 财务自动闭环 | 站点维度是汇率/平台费归集的必要键 | 流水生成逻辑本身缺失 |
| 健康度：订单查询 < 1s（1 万单量级） | `idx_orders_shop` + `idx_shops_tenant` 支撑按店筛选 | 注意 `shop_id` 无索引会让按店筛选全表扫 |

判断：**本决策是必要不充分条件**。做完它，O1 才可能脱 0；但若不配调度器、SKU 映射、库存预留、Amazon pusher，闭环仍不成立。所以 §7 把它们列成同批待办，而不是留到「以后」。

---

## 6. 一刀切重建（不做迁移）

用户 2026-10-06 明确不需要迁移。因此本决策的落地路径是**改结构 + 重建库**，不是 `ALTER`。顺序不可乱：

1. **先修列型阻塞**：新建库时 `platform_name` 用 `varchar(32)` 而非 ENUM，避免 §1.3 的 1265 截断。这是所有平台的前置。
2. **`shops` 建表用显式 DDL**，不让 `ddl-auto=update` 按实体自己建——否则 `@Enumerated(STRING)` 会重演 ENUM 窄化（ADR-0002 的既有教训）。列宽/字符集遵循 `docs/db/migration-plan.md` §4 的新表强制约定。
3. **`orders` 加 `shop_id`**，`shop_warehouses.shop_code` 裸引用换成 `shop_id`。
4. **`platform_integrations` 去掉卖家级凭据列与 `tenant_id`**，只留对接定义。
5. **代码切流**：`PlatformCredential.from(integration)` → `from(shop)`；`AmazonOrderFetcher` 改为按站点逐次调用、`toRow` 回读 `MarketplaceId`；导入落库写 `shop_id`。
6. **重建库 + 初始化基础数据**，见 §6.1。
7. **守卫测试**（§4.3 锁 1）与权限 catalog、前端门控同步上线。

### 6.1 重建后必须齐备的基础数据

沿用 `DataInitializationConfig`（现有种子：DB 无租户时建默认租户 + `admin/123456`；补挂 `PLATFORM_ADMIN` 系统角色；`ensureTenantAdminPermissions()` 幂等对齐 TENANT_ADMIN 权限；8 个内置物流渠道 :46-63,:299-317）。本次要**新增/变更**的只有两项：

- 权限 catalog 从 22 项增至 26 项（19+4 租户级、3 平台级、`shop:*` 4 项计入前者 ⇒ 实际 `TENANT_SCOPED` 23 项、`ALL` 26 项）。TENANT_ADMIN 由 `tenantAdminPermissions()` 自动获得 23 项，`ensureTenantAdminPermissions()` 的幂等补齐会让**存量租户无需人工授权即上线生效**——这是期望行为，但必须在发布说明里写清。
- 前端 `PERMISSION_PRESETS` 副本同步（§4.6），否则界面配不出新点。

需要确认的一项判断（不属本 ADR 决定范围，但重建会连带处置）：现库 63 条订单、2 商品、2 仓库、5 财务流水是历轮验证攒下的**测试残留**，重建即丢失。若需要可复现的演示数据，应在 `DataInitializationConfig` 或 `scripts/` 下加一个显式 demo 数据集（目前 `scripts/` 只有 `docker/`，没有灌数据脚本），而不是依赖手工导入的痕迹。

---

## 7. 后果

### 正面
- 订单获得站点归属，多站点卖家从「不可用」变成「可用」；回写、库存、财务都有了必要键。
- 凭据归属层级与实体层级对齐，`platform_integrations` 退化成真正可全局共享的对接定义。
- 隔离要求从口头约定变成 CI 可失败测试（§4.3 锁 1），这是本系统第一次给「权限不出错」装上机制而非意愿。
- 顺带清掉密文列宽、`shop_warehouses` 无外键、列型 ENUM 三笔债。

### 负面 / 需承担的成本
- 多一张表和一层聚合，新增一个限界上下文（或并入 `platform`，需在实现时定并同步 ADR-0012 的分包偏离清单）。
- 站点粒度下同卖家多站点各存 token：LWA 换发次数 ×站点数，且 token 冗余 N 份。刷新失败的局部状态会不一致（某站点 `EXPIRED`、另一站点仍 `ACTIVE`），需在 UI 明示而非聚合展示。
- `orders.shop_id` 允许 NULL，`FILE`/`MANUAL` 来源的订单永远没有店铺归属 → 订单列表要区分「平台单/非平台单」，否则「未分配店铺」筛查看起来像数据坏了。
- 权限点 22 → 26 使所有写「19 项 / 22 项」的文档、种子逻辑断言、测试常量都要改（`docs/spec/tenant-and-iam.md`、`docs/product/*`、`RoleList.jsx`）。

### 必须遵守的约束与待办
- **约束 1**：`shops` 的服务方法必须有名为 `tenantId` 的形参，且守卫测试覆盖。新模块照此办理。
- **约束 2**：`-parameters` 不得被关闭；守卫测试断言参数名非 `arg0` 形状。
- **约束 3**：任何出参 DTO 不得含凭据明文/密文，只有 `has*` 布尔 + 掩码。
- **约束 4**：不得为了「让租户自助绑店」而放宽 `platform:manage`。自助能力靠 `shop:*` 新点提供，不是靠降级平台级点。
- **待办（同批，不在本 ADR 内实现）**：`AmazonFulfillmentPusher`；`@Scheduled` 增量拉单；平台 SKU ↔ 本地 SKU 映射表（`skus` 实测无外部 SKU 列，导入把平台 SKU 原样落 `order_items.sku_code`，空则 `UNKNOWN-SKU`，`OrderImportApplicationService.java:393-409`）；库存预留（`BusinessProcessOrchestrationService.java:75-81` 空循环）；`order_number` 全局唯一键与 `uk_orders_platform_order(tenant_id,…)` 口径不一致；审计表。

---

## 8. 复审触发条件

1. **成为 SP-API public developer**（拿到在线授权跳转资质）：授权模型从「卖家自建 private app」变成「我们的 app + 卖家 OAuth 回调」，届时 app 凭据真正全局化，需要重估 §2.1 的 (A)/(B) 选择与 `platform_integrations` 的凭据去留。
2. 出现「一个卖家账号需要按站点差异化配置」（不同站点不同仓库/不同物流模板）且 `shops` 行数失控时：引入账号级父实体，即形态 (B)。
3. 单站点订单量或店铺数超过 `idx_orders_shop` 的合理基数（10 万单级）：重新评估查询与索引。
4. 任一租户发生跨店数据可见事故：立即复审 §4.3 三道锁的覆盖率。

---

## 9. 复测（2026-10-21 按活库 + 工作树）

本节只订正上面已被超越的事实，决策不变：

1. **§1.3 的结构性阻塞已修**：`platform_integrations.platform_name` 的 MySQL ENUM 现在是**完整 12 个标签**（`SHOW CREATE TABLE` 实测与 `PlatformName.java:9-20` 一一对应，含 `WISH`/`OTHER`），`auth_type` 11 个、`status` 4 个（`ACTIVE/DISCONNECTED/ERROR/INACTIVE`）。原文「ENUM 只有 7 个值 ⇒ Shopee/Ozon/Mercado/TikTok/Temu 建不出集成记录」不再成立，插 `OZON` 不会再 1265。⚠️ 但机制警告仍有效：这是靠显式 DDL 补的，`ddl-auto: update` 不扩 ENUM 标签（见 [ADR-0002 末段](./0002-hibernate-ddl-auto.md)），下次给 `PlatformName`/`PlatformAuthType` 加值仍要手工 `ALTER`。
2. **§1.2 的数据基线变了，但「无存量」结论更强**：活库 `orders` 从 **63 行降到 4 行**（`MANUAL` 3 + `SHOPIFY` 1，已有一条平台来源的单），`platform_integrations`、`shops`、`shop_warehouses` **仍都是 0 行**；`orders` 仍无 `shop_id`/`integration_id` 两列 ⇒ 决策 3 未落地，§1.2「不需要迁移/回填」的前提继续成立。
3. **§1.3 的 id 类型普查过期**：全库现 **45 张基表**（原文 38），其中 40 张有 `id` 列——`varchar(255)` 35、**`varchar(36)` 2（`audit_logs`、新增 `shops`）**、`varchar(64)` 1（`customers`）、`binary(16)` 1（`platform_integrations`）、`bigint` 1（`purchase_order_items`）；另 5 张没有 `id`（`purchase_orders.purchase_order_id`、`financial_transactions.transaction_id`、`system_configs.config_key`、复合主键 `user_roles`、无主键 `role_jpa_entity_permissions`）。**原文预言的异构外键问题已经现实化**：`shops.integration_id varchar(36)` 指向 `platform_integrations.id binary(16)`，字符列存二进制主键（当前无外键、0 行所以未爆，§6 步骤 1 的「统一 `VARCHAR(36)`」仍是必要前置）。
4. **`shops` 实际列型**（回应状态行「未按 migration-plan §4 固化」）：`auth_status varchar(24)`、`push_back_policy varchar(16)` 确为 varchar 而非 ENUM；凭据列为 `api_key/api_secret varchar(512)`、`access_token/refresh_token varchar(2048)`（比 `platform_integrations` 的 1024 更宽），`marketplace_id varchar(64)` 是**单数**（与集成表的 `marketplace_ids varchar(512)` 逗号串不同口径），`id varchar(36)`、`integration_id varchar(36)`、`tenant_id varchar(64) NOT NULL`。这些宽度/命名仍未进显式 DDL 基线，`schema.sql` 侧不在本 ADR 处理。
5. **决策 2 未落地、且离目标更远**：`platform_integrations` 仍保留 `tenant_id` 与四列卖家级凭据（`api_key`/`api_secret`/`access_token`/`refresh_token`，均 `varchar(1024)`），与 §2 决策 2「去掉 `tenant_id`、去掉卖家级凭据」相反；状态行已记「未落地」，本轮确认无变化。
