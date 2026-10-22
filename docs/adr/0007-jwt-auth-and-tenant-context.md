# ADR-0007：JWT 无状态认证 + ThreadLocal 多租户上下文

- 状态：Accepted（~~**校验与鉴权实现被桩化，当前不成立**~~ → 🟡 **2026-01-25 复测：认证与鉴权链路已成立，"不成立"的范围收窄到只剩读路径隔离**。逐条对账见文末「复测对账」节）
- 日期记录：2026-01-25
- 相关：[api.md §6](../api/api.md)、[security-findings.md S-01/S-02/S-03/M-01~M-06](../security-findings.md)

## 背景

前后端分离 + 双 SPA（user-web / admin-web），需要无会话的水平扩展能力；同时系统按「租户」组织数据（`tenants`/`users.tenant_id`/`roles.tenant_id`），要求每个请求都知道自己属于哪个租户，并据此隔离数据。

## 决策

1. 令牌：JJWT 0.12.5，HS512，`Authorization: Bearer <token>`；secret/expiration 在 `application.yml`（`jwt.expiration=86400000`，`jwt.refresh.expiration=86400000`）。
2. 认证：`JwtAuthenticationFilter` + `JwtTokenProvider`；登出仅 `SecurityContextHolder.clearContext()`（**无服务端黑名单**）。
3. 授权：`SecurityConfig` URL 级规则 —— `/api/auth/**` permitAll、`/api/platform/**` 与 `/api/tenants/**` 需 `PLATFORM_ADMIN`、`/api/**` 需 `authenticated`。
4. 租户：`TenantContext`（ThreadLocal）由 filter 写入，`TenantIsolationAspect` 拦截 `domain..*Repository.*` 与 `application.service..*` 做隔离。

## 后果：设计可用，但实现有 6 处使保护失效

| # | 事实 | 直接后果 |
|---|---|---|
| 1 | `JwtTokenProvider.validateToken()` 恒 `true`、`getUsernameFromToken()` 恒 `""`、`getTenantIdFromToken()` 恒 `"default-tenant"` | 任意 `Authorization` 串即"已认证"；`/api/**` 保护被绕过 |
| 2 | `CustomUserDetailsService` 权限集合恒空 + filter 中角色提取被注释 | 任何 `hasRole` 永不满足 ⇒ `/api/tenants/**` 恒 403，角色模型整体失效 |
| 3 | 规则写 `/api/platform/**`，真实路径 `/api/platform-integrations/**` | 平台管理降级为 `authenticated()`（叠加 #1 ⇒ 实际公开） |
| 4 | filter 内 `new JwtTokenProvider()` 绕过注入；类又标 `@Component`、`SecurityConfig` 还 `@Bean` new 一次 | 多实例并存，yml 里的 secret 在实际校验路径上不生效 |
| 5 | 读取 header 后未 `substring(7)` 去掉 `Bearer ` | 令牌解析口径错误（被 #1 掩盖） |
| 6 | `TenantIsolationAspect` 过滤逻辑全部注释、仅 `System.out`，且从不 `clearTenantId` | 隔离未实现；线程池下有串租户风险 |
| — | 全仓无 `@PreAuthorize`/`@Secured`/`@EnableMethodSecurity`；业务表**无 `tenant_id` 列** | 授权只有 URL 一层；库层面无法兜底越权 |

另外三点属于设计层面的取舍，需要显式承认：
- **登出不使令牌失效**：24h 内旧令牌仍可被使用（无黑名单/无 jti 撤销表）。
- **`POST /api/auth/refresh` 只凭 username 换发**，不校验旧令牌 ⇒ 等同于「知道用户名即可取新令牌」。
- **access 与 refresh 同为 24h**，无短期 access + 长期 refresh 的分层。

## 建议

按顺序：先修 #1~#5（同一链路，一起修才有意义）→ 打开 `@EnableMethodSecurity` 并为提权面（`/users/{id}/roles`、`/roles/{id}/permissions`、`/users/{id}/password`）加 `@PreAuthorize` → 业务表补 `tenant_id` 后启用真实的 `TenantIsolationAspect`（并在 filter `finally` 中 `clear`）→ `SecurityConfig` 升级到 `authorizeHttpRequests` 且加 `.anyRequest().denyAll()` 兜底 → 引入令牌撤销（jti 黑名单或短期 access + 轮换 refresh）。

---

## 复测对账（2026-01-25，逐条按当前工作树实测）

本 ADR 上表的 6 个失效点 + 3 个设计取舍，现状如下：

| 本 ADR 条目 | 现状 | 证据 |
|---|---|---|
| #1 校验恒真 | ✅ 已修 | `JwtTokenProvider:189-191` = `parseClaims(token) != null`；`getTenantIdFromToken` 从 claim 取（`:141-144`）（ISS-007） |
| #2 authorities 恒空 | ✅ 已修 | 令牌带 `roles`/`permissions` 两个 claim，过滤器 `:46-72` 重建 authorities；`CustomUserDetailsService:57-73` 口径一致（ISS-041）。<br>⚠️ 但**修复后一度仍恒 403**，根因是种子初始化在非 Web 线程读 `@ElementCollection` 被 `catch` 吞掉（**ISS-061**），不是本表任何一条 |
| #3 `/api/platform/**` 规则写错 | ✅ 已删该规则 | `SecurityConfig:64-67` 现为 login/refresh/logout `permitAll` → `/api/auth/me` `authenticated` → `/api/tenants/**` `hasRole(PLATFORM_ADMIN)` → `/api/**` `authenticated`；平台集成改由**类级 `@PreAuthorize`** 保护（`PlatformIntegrationController:46`）（S-03 已修） |
| #4 filter 内 new + 双实例 | 🟡 部分 | 过滤器已改**构造注入**（`:31-35`，M-01 已修）。<br>❌ 但「同一类型两个 Bean 定义」的形态在**仓储层**反而增加了：`User/Role/Tenant/Product` 的 `*RepositoryImpl` 既标 `@Repository` 又被 `PersistenceConfig` `@Bean`（9 个 `@Bean`，其中 `productRepository:86-88` 只是转发别名）→ 靠注入点参数名消歧，见 [tech/backend-standards.md §8](../tech/backend-standards.md) |
| #5 未剥 `Bearer ` 前缀 | ✅ **核实为误报** | `getJwtFromRequest` 一直有 `startsWith("Bearer ")` + `substring(7)`（M-02） |
| #6 切面全注释 + 不 clear | 🟡 一半 | 切面已落地（`TenantIsolationAspect:55-86`，不一致抛 `TENANT_ACCESS_DENIED`，`PLATFORM_ADMIN` 放行）、`clearTenantId()` 已在 `finally`（filter `:82`）。<br>❌ **「隔离未实现」对读路径仍成立**：`:91-97` 的仓储切面只打 trace，注释写明"隔离由服务层参数校验保障" → **ISS-050** |
| 表末「无 `@PreAuthorize`／业务表无 `tenant_id`」 | 🟡 大幅推进但未收口 | `@EnableMethodSecurity` 已开（`SecurityConfig:35`），`@PreAuthorize` **17 处 / 9 个控制器**（另 11 个控制器仍无）；`tenant_id` 从 2 张 → **23/42 张**。⚠️ 22 个种子权限点只有 **10 个**真被检查 |
| 取舍① 登出不使令牌失效 | ❌ 未改 | `AuthController:137-139` 仍只有 `clearContext()`，无 jti 黑名单 |
| 取舍② refresh 只凭 username | ✅ 已修 | `AuthController:93-129`：`:99` 校验旧 refresh token、`:103` 取 username、`:108` 重载用户、`:126` 重签 roles/permissions<br>❌ 但 `:128` **把同一个 refreshToken 原样回给客户端** → 无轮换、无一次性使用，旧 refresh 在 24h 内始终可复用 |
| 取舍③ access/refresh 同 24h | ❌ 未改 | `application.yml:80-82` 均为 `86400000` |

📌 **本 ADR 的决策本身仍然成立**（无状态 JWT + ThreadLocal 租户上下文），"不成立"的只剩读路径隔离与令牌生命周期治理。本节的「建议」段第 1、2、3、4 步已完成，**第 5 步（令牌撤销/轮换）与 `.anyRequest().denyAll()` 兜底未做**；新增的未完成项是 ISS-050（让查询真正带租户条件）与 ISS-051（把 `tenantId` 从客户端传参改为令牌来源，涉及 10 个 `DEFAULT_TENANT_ID` 常量 + 15 处兜底）。

## 复测（2026-10-21 按工作树）

只订正上表已过期的行号与计数，结论方向不变：

1. **鉴权规模**：`@EnableMethodSecurity` 现行 `SecurityConfig:48`（~~`:35`~~）；`authorizeHttpRequests` `:77`，`permitAll` = `:78` 三个认证端点 + `:81` swagger，`/api/tenants/**` `hasRole` `:83`，`anyRequest().authenticated()` `:85`（**仍不是 `denyAll()`**）。`@PreAuthorize` ~~实测 **136 处 / 26 个文件**~~ → **真注解 130 处 / 22 个文件**（`grep -rn "@PreAuthorize"` 的 136/26 含 6 处 Javadoc 提及，多出的 4 个文件也是 Javadoc 所在文件；口径与 [architecture.md §9](../architecture.md) 一致），24 个控制器里 **22 个**已挂；只有 `AuthController`（permitAll 登录口）与 `TenantController`（靠 `:83` 的 URL 规则 + 切面）没有——上表「17 处 / 9 个控制器」与「另 11 个控制器仍无」均已过期。
2. **租户列与权限点**：`tenant_id` 从 **23/42** 到 **25/45**（20 张有索引）；权限点 catalog 现为 **26 个**（25 租户级 + 1 平台级），实测全部被 `hasAuthority` 引用、无孤儿，`RoleList.jsx` 的 `PERMISSION_PRESETS` 与后端逐名对齐——上表「22 个种子点只有 10 个被检查」不再成立。
3. **切面（#6）现行坐标**：`TenantIsolationAspect:77-130`（~~`:55-86`~~），仓储 advice `beforeRepositoryMethod:157-162` 仍只打 trace；新增两道门——入参先 `TenantContext.normalizeRequested`（`:101`，空串/历史默认桶一律视为"未指定"），平台管理员跨租户**写**必须 `TenantAccessGuard.requireExistingTenant`（`:113-121`，ISS-135 幻影桶补丁），拒绝仍在 `:128`。**读路径仍是「逐聚合手工接线」**：27 个仓储接口 / 50 个带 `tenantId` 形参的方法、`requireOwned` 28 处 / 8 个文件，采购域 0 处（它把隔离放进仓储 `PurchaseOrderRepositoryImpl:68-75`）⇒ ISS-050 的残留形态是「没有结构性保证」，不再是「仓储完全不看租户」。
4. **ISS-051 入口侧收口**：控制器里已无 `@RequestParam(defaultValue = "default-tenant")`；`PurchaseController:83` 改为 `required = false` + `resolveTenant():275-284`。`DEFAULT_TENANT_ID` 符号 **36 次引用 / 21 个文件**，其中 **14 处是常量声明**（13 处赋字面量 + `CustomerImportApplicationService:65` 1 处别名）；裸 `"default-tenant"` 字面量 **18 处** = 13 声明 + `TenantContext.LEGACY_TENANT_ID:14` + `WarehouseMapper:60` + `OrderCreatedEventConsumer:41` + `CreateFinancialTransactionRequest:36`（`@Schema` 示例）+ `FinanceController:125`。上表末段「10 个常量 + 15 处兜底」的数字按此更新；另两处行号已漂：controller 侧兜底只剩 `OrderController:192`（`CustomerController:76-79` 已交给 `TenantAccessGuard`）。
5. **令牌生命周期三问（复核结论未变，行号已漂）**：① 登出仍只 `SecurityContextHolder.clearContext()`（`AuthController:174-178`），全仓 `blacklist`/`jti`/`revoke` **0 命中**；② `POST /api/auth/refresh` 在 `AuthController:121-163`，`:162` 仍把**同一个 refreshToken 原样返回**，无轮换/无一次性使用；③ access 与 refresh 仍同为 `86400000`，位置从 ~~`application.yml:80-82`~~ → 现行 `:120-122`（secret 在 `:119`，默认值仍受版本控制，S-04 的残留见 [architecture.md §9](../architecture.md)）。
