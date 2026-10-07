# 架构决策记录（ADR）

> ADR（Architecture Decision Record）用于记录**重要的、难以逆转的**架构决策：背景、决策、理由与后果。
> 目的不是事后解释代码，而是让后来者知道"为什么当初这样选"，避免重复讨论。

---

## 索引

| 编号 | 决策 | 状态 | 日期 |
|---|---|---|---|
| [0001](./0001-domain-driven-design.md) | 采用 DDD 四层架构与 9 个限界上下文 | 已采纳 | 2026-01-25 |
| [0002](./0002-hibernate-ddl-auto.md) | 使用 Hibernate `ddl-auto=update` 自动维护表结构 | 已采纳（附带迁移义务） | 2026-01-25 |
| [0003](./0003-multi-tenancy-isolation.md) | 多租户隔离：共享库 + 应用层租户上下文 | 部分落地（实现待完成） | 2026-01-25 |
| [0004](./0004-jwt-authentication.md) | JWT + Spring Security 无状态鉴权 | 已采纳（核心逻辑待修复） | 2026-01-25 |
| [0005](./0005-dual-frontend-split.md) | 前后端分离，前端拆分为 user-web / admin-web | 已采纳（admin-web 待重构） | 2026-01-25 |

**状态含义**：已采纳 / 部分落地 / 已废弃 / 已取代（注明被谁取代）。

> 本索引由产品线与工程线共同维护，「决策 vs 实现差距」的权威划分见 [`../README.md`](../README.md) §4。
> 上面 5 条是**产品线决策**（回答"为什么这么选"）；下面 7 条是**工程线决策**（回答"怎么落地、现状与差距"）。

**工程线决策（0002 · 0006~0012）**

| 编号 | 文件 | 主题 | 产品线如何引用 |
|---|---|---|---|
| 0002 | [`0002-hibernate-ddl-auto.md`](./0002-hibernate-ddl-auto.md) | Hibernate 自动建表 + Flyway 迁移路径 | 数据层唯一约定；改实体必须同步 `db/schema.sql` |
| 0006 | [`0006-rocketmq-domain-events.md`](./0006-rocketmq-domain-events.md) | RocketMQ 领域事件与双通道发布 | 产品 flow F1~F8 的事件消费是否落地的依据。<br>⚠️ 2026-01-25 二次实测：F1~F8 的判断**要先过 ISS-062 这一关**——8 个注册事件的聚合里只有采购、财务、商品 3 个真的把事件交给过发布器，另外 5 个（订单/客户/物流/仓储/平台）的事件恒空，所以"消费是否落地"目前无意义 |
| 0007 | [`0007-jwt-auth-and-tenant-context.md`](./0007-jwt-auth-and-tenant-context.md) | JWT 实现现状与差距 | **0004 的实现差距**：~~`validateToken` 恒真、authorities 恒空~~ → **2026-01-25 两条差距都已修**（ISS-007 真验签 `JwtTokenProvider.java:189-191`+`:201-205`；ISS-008/041 roles+permissions 写入 authorities，实测 JWT 里带 22 个权限点）。🔄 **2026-10-07 复测：`admin` 的 JWT 实测 26 个权限点**（22 是当时 catalog 的全量，不是缺陷；ADR-0013 新增的 `shop:*` 四点在 2026-10-06~07 间因 catalog 点名与 `Role` 校验正则漂移而**整批未落库**，实测仍是 22，修好后才是 26——见 ISS-090）。**新差距换成出口问题**：未认证是 403 + 0 字节 body、401 通道不可达（ISS-032/058），refresh 不轮换且与 access 同为 24h |
| 0008 | [`0008-frontend-user-admin-split.md`](./0008-frontend-user-admin-split.md) | 双前端实现现状与差距 | **0005 的实现差距**：CORS、认证缺失、页面重复 |
| 0009 | [`0009-config-externalization.md`](./0009-config-externalization.md) | 配置与凭据外部化 | 交付 checklist 的依赖（见 `product/07-roadmap.md` v1.0） |
| 0010 | [`0010-mapstruct-domain-jpa-bridge.md`](./0010-mapstruct-domain-jpa-bridge.md) | MapStruct 领域↔JPA 桥接 | 规格文档"概念模型"章节的技术背景 |
| 0011 | [`0011-result-envelope-and-errors.md`](./0011-result-envelope-and-errors.md) | `Result` 信封与错误处理 | [`../api/README.md`](../api/README.md) §3.2 错误码口径来源 |
| 0012 | [`0012-ddd-single-module-layering.md`](./0012-ddd-single-module-layering.md) | DDD 单模块分包实现细节与偏离 | **0001 的实现差距**：10 条分层偏离与修复顺序 |
| 0013 | [`0013-shop-entity-and-platform-connection-split.md`](./0013-shop-entity-and-platform-connection-split.md) | 店铺实体与平台对接元信息分表（站点粒度） | **状态：部分实现（2026-10-07）**——`shops` 切片与四个租户级权限点已落地且实测种子生效（PLATFORM_ADMIN 26 / TENANT_ADMIN 23），`orders.shop_id`、`platform_integrations` 收敛、两层回写标记、显式 DDL 与前端门控仍在 §6 待办里。点名定为 `shop:credential_write`（**不是** `credential:write`——`Role.PERMISSION_PATTERN` 只允许一个冒号，放宽校验会放进无人消费的死权限，理由见该 ADR §4.1）。含 §4.3 三道隔离守卫（其中一条看守的是 `-parameters` 编译标志——关掉它 `TenantIsolationAspect` 会静默失效，2026-10-06 实测当前生效） |

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
