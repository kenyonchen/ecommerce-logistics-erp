# 架构决策记录（ADR）

> ADR（Architecture Decision Record）用于记录**重要的、难以逆转的**架构决策：背景、决策、理由与后果。
> 目的不是事后解释代码，而是让后来者知道"为什么当初这样选"，避免重复讨论。

---

## 索引

| 编号 | 决策 | 状态 | 日期 |
|---|---|---|---|
| [0001](./0001-domain-driven-design.md) | 采用 DDD 四层架构与 ~~9 个~~ 限界上下文 | 已采纳 | 2026-01-25 |
| [0002](./0002-hibernate-ddl-auto.md) | 使用 Hibernate `ddl-auto=update` 自动维护表结构 | 已采纳（附带迁移义务） | 2026-01-25 |
| [0003](./0003-multi-tenancy-isolation.md) | 多租户隔离：共享库 + 应用层租户上下文 | ~~部分落地（实现待完成）~~ → 🔄 2026-10-21 写读两侧均已收口 | 2026-01-25 |
| [0004](./0004-jwt-authentication.md) | JWT + Spring Security 无状态鉴权 | ~~已采纳（核心逻辑待修复）~~ → 🔄 2026-10-21 核心逻辑已落地，差距只剩密钥外化与令牌轮换 | 2026-01-25 |
| [0005](./0005-dual-frontend-split.md) | 前后端分离，前端拆分为 user-web / admin-web | 已采纳（~~admin-web 待重构~~ → 🔄 2026-10-21 已全量接入后端） | 2026-01-25 |

**状态含义**：已采纳 / 部分落地 / 已废弃 / 已取代（注明被谁取代）。

> 本索引由产品线与工程线共同维护，「决策 vs 实现差距」的权威划分见 [`../README.md`](../README.md) §4。
> 上面 5 条是**产品线决策**（回答"为什么这么选"）；下面 ~~7~~ → **9** 条是**工程线决策**（回答"怎么落地、现状与差距"）。
> 🔄 **2026-10-21 索引校准**：0001 的"9 个限界上下文"是**决策当时的划分**，正文不改标题；今天实测是 **13 个上下文 + `common`**（`domain/` 下 14 个包：`order`、`logistics`、`warehouse`、`product`、`purchase`、`finance`、`customer`、`platform`、`tenant`、`shop`、`audit`、`config`、`overview`、`common`），逐包与偏离说明见 [0001 的状态补充](./0001-domain-driven-design.md)。新增的 `audit`/`config`/`overview` 只有实体 + 仓储（读模型形状），没有聚合，是这条决策的**已知偏离**而不是新决策。

**工程线决策（0002 · 0006~0013）**

| 编号 | 文件 | 主题 | 产品线如何引用 |
|---|---|---|---|
| 0002 | [`0002-hibernate-ddl-auto.md`](./0002-hibernate-ddl-auto.md) | Hibernate 自动建表 + Flyway 迁移路径 | 数据层唯一约定；改实体必须同步 `db/schema.sql` |
| 0006 | [`0006-rocketmq-domain-events.md`](./0006-rocketmq-domain-events.md) | RocketMQ 领域事件与双通道发布 | 产品 flow F1~F8 的事件消费是否落地的依据。<br>⚠️ ~~2026-01-25 二次实测：F1~F8 的判断**要先过 ISS-062 这一关**——8 个注册事件的聚合里只有采购、财务、商品 3 个真的把事件交给过发布器，另外 5 个（订单/客户/物流/仓储/平台）的事件恒空，所以"消费是否落地"目前无意义~~ → ✅ **2026-10-21 复测：ISS-062 已闭环，F1~F8 的判断重新有意义**。`domain/common/BaseAggregateRoot.carryOverEvents`（`:48-63`）在 5 个仓储 `save()` 里把副本丢掉的事件补回（`Order:44`、`PlatformIntegration:69`、`Warehouse:36`、`LogisticsOrder:47`、`Customer:50`），加上本就返回 void 的采购/财务与商品的应用层 `add`，**8 个聚合的事件现在都能到发布器**；进程内「下单→生成物流单」「缺货→生成采购单」实测各执行一次。机制成因（Mapper 重建聚合时清空 `domainEvents`）**一行没改**，详见 [ADR-0010 后果 7 的复测段](./0010-mapstruct-domain-jpa-bridge.md)。<br>🔴 **新的口径**：`rocketmq.internal-consumers.enabled` 默认 `false`（`application.yml:45-46`），所以**MQ 消费端不装配**，跨聚合联动目前只在进程内发生；且 24 个事件类里只有 4 个补了 `@JsonCreator`，其余 20 个一旦真开 MQ 反序列化仍会失败（ISS-052 半修）。 |
| 0007 | [`0007-jwt-auth-and-tenant-context.md`](./0007-jwt-auth-and-tenant-context.md) | JWT 实现现状与差距 | **0004 的实现差距**：~~`validateToken` 恒真、authorities 恒空~~ → **2026-01-25 两条差距都已修**（ISS-007 真验签 `JwtTokenProvider.java:189-191`+`:201-205`；ISS-008/041 roles+permissions 写入 authorities，实测 JWT 里带 22 个权限点）。🔄 **2026-10-07 复测：`admin` 的 JWT 实测 26 个权限点**（22 是当时 catalog 的全量，不是缺陷；ADR-0013 新增的 `shop:*` 四点在 2026-10-06~07 间因 catalog 点名与 `Role` 校验正则漂移而**整批未落库**，实测仍是 22，修好后才是 26——见 ISS-090）。~~**新差距换成出口问题**：未认证是 403 + 0 字节 body、401 通道不可达（ISS-032/058）~~ → ✅ 2026-10-09 已修（ISS-098，401/403/404/405/415 语义全部对齐）。**仍然成立的差距**：`jwt.expiration` 与 `jwt.refresh.expiration` 都是 `86400000`（`application.yml:120`、`:122`，access 与 refresh 同为 24h），且 `/api/auth/refresh` 在 `AuthController.java:162` **原样回传同一个 `refreshToken`**（不轮换）。🔄 2026-10-21 同轮补记：续期链路已加两道门——`AccountLoginGuard.isUsable`（`AuthController:140`，挡住已停用账号/租户继续续期）与 `loginSecurityService.recordRefreshAttempt`（`:165`，失败续期进登录安全留痕）。 |
| 0008 | [`0008-frontend-user-admin-split.md`](./0008-frontend-user-admin-split.md) | 双前端实现现状与差距 | **0005 的实现差距**：~~CORS、认证缺失、页面重复~~ → 🔄 **2026-10-21 复测：三条差距里"admin-web 无认证/页面用 mock"两条已消除**——admin-web 全量接入后端（`api.js` 128 行 / 6 个 api 模块 / 6 个页面，`pages/` 下 grep `mock` = 0 命中，硬编码桩 `platformApi` 已删，ISS-026 关闭），两端登录都走真实 `/api/auth/login`。**只剩 CORS**：`SecurityConfig.java:63` 仍把来源白名单写死在 Java 里——`setAllowedOrigins(List.of("http://localhost:5110", "http://localhost:5120"))`，只有这 **2 个** dev 端口（上一档记录的 4 个含 5173/5174，是前端端口迁移前的口径），生产同源部署时还得再加，改端口必须改代码重新编译，属 [0009 配置外部化](./0009-config-externalization.md) 未收口的一项（ISS-021 只登记了密钥，不含 CORS）。 |
| 0009 | [`0009-config-externalization.md`](./0009-config-externalization.md) | 配置与凭据外部化 | 交付 checklist 的依赖（见 `product/07-roadmap.md` v1.0）。🔄 2026-10-21：只做到「占位符半套」——DB/JWT/主密钥共 7 处 `${}`，默认字面量仍在 Git，profile/Flyway/CORS 外部化未做 |
| 0010 | [`0010-mapstruct-domain-jpa-bridge.md`](./0010-mapstruct-domain-jpa-bridge.md) | MapStruct 领域↔JPA 桥接 | 规格文档"概念模型"章节的技术背景 |
| 0011 | [`0011-result-envelope-and-errors.md`](./0011-result-envelope-and-errors.md) | `Result` 信封与错误处理 | [`../api/README.md`](../api/README.md) §3.2 错误码口径来源 |
| 0012 | [`0012-ddd-single-module-layering.md`](./0012-ddd-single-module-layering.md) | DDD 单模块分包实现细节与偏离 | **0001 的实现差距**：10 条分层偏离与修复顺序 |
| 0013 | [`0013-shop-entity-and-platform-connection-split.md`](./0013-shop-entity-and-platform-connection-split.md) | 店铺实体与平台对接元信息分表（站点粒度） | **状态：部分实现（2026-10-07）**——`shops` 切片与四个租户级权限点已落地且实测种子生效（~~PLATFORM_ADMIN 26 / TENANT_ADMIN 23~~ → 🔄 **2026-10-21 复测：PLATFORM_ADMIN 26 / TENANT_ADMIN 25**；catalog 现在由 `domain/tenant/PermissionPoints` 单点定义，`TENANT_SCOPED_PERMISSIONS` 25 项 + `PLATFORM_SCOPED_PERMISSIONS` 1 项，种子处 `DataInitializationConfig.java:167,172,250` 与 `TenantServiceImpl.java:180` 都调 `tenantAdminPermissions()`/`platformAdminPermissions()`，**不再手写清单**），`orders.shop_id`、`platform_integrations` 收敛、两层回写标记、显式 DDL 与前端门控仍在 §6 待办里。点名定为 `shop:credential_write`（**不是** `credential:write`——`Role.PERMISSION_PATTERN` 只允许一个冒号，放宽校验会放进无人消费的死权限，理由见该 ADR §4.1）。含 §4.3 三道隔离守卫（其中一条看守的是 `-parameters` 编译标志——关掉它 `TenantIsolationAspect` 会静默失效，2026-10-06 实测当前生效） |

---

## 何时需要新增 ADR

满足任一条件即可：

1. 影响到**多个限界上下文**或**跨前后端**的技术选型；
2. 决策一旦落地，替换成本很高（如数据源类型、ORM、鉴权方案、隔离模型）；
3. 团队在两个方案间反复讨论却没有结论；
4. 引入了新的**非显然约定**（如"表结构不由 SQL 文件决定"）。

反例（不需要写 ADR）：页面内的组件拆分、单个字段命名、短期临时方案。

---

## 模板

```markdown
# NNNN · 决策标题

| 项 | 内容 |
|---|---|
| 状态 | 已采纳 / 部分落地 / 已废弃 |
| 日期 | YYYY-MM-DD |
| 决策者 | — |
| 相关文档 | — |

## 背景
（业务/技术现状与问题，包含促使决策的约束条件）

## 候选方案
| 方案 | 优点 | 缺点 |
|---|---|---|

## 决策
（明确写出选了什么）

## 理由
（为什么这个方案在当前约束下最优）

## 后果
### 正面
### 负面 / 需要承担的成本
### 必须遵守的约束与待办

## 复审触发条件
（什么情况下需要重新讨论这项决策）
```
