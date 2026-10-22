# 安全与权限技术设计

> 定位：把散在 [security-findings.md](../security-findings.md)（事实与行号）、[ADR-0004](../adr/0004-jwt-authentication.md)/[ADR-0007](../adr/0007-jwt-auth-and-tenant-context.md)（选型与差距）、[spec/tenant-and-iam.md §5](../spec/tenant-and-iam.md)（目标时序）里的内容，收敛成**一份可执行的技术实施方案**：改哪个类、按什么顺序、如何验收。
> 时序图与角色权限的业务定义不在此重复（单一权威：`spec/tenant-and-iam.md`）。
> 事实基线：2026-01-25 工作树。

## 1. 威胁模型（本项目实际面临的，按可能性排序）

| 威胁 | 当前可利用性 | 证据 |
|---|---|---|
| **无凭证访问受保护接口** | ~~**可直接利用**~~ → **✅ 2026-01-25 已不可利用** | ~~`JwtTokenProvider.validateToken()` 恒 `true`（`:126-128`）、`getUsernameFromToken()` 恒 `""`（`:106-108`）→ 任意 `Authorization: Bearer <任意串>` 都算已认证~~ → 现为 `parseClaims(token) != null`（`:189-191`），`parseClaims` 走 `Jwts.parser()` 验签 + 过期校验、空串先拒（`:197`），两个 getter 从 claims 读。**实测口径**：不带令牌 / 伪造令牌访问 `/api/orders/{id}` → **HTTP 403（且响应体为空）**，不再是"通过"。<br>⚠️ 但**没有 401 出口**（`SecurityConfig:59-68` 未配 `httpBasic`/`formLogin`/`exceptionHandling`）→ 前端按 401 跳登录的分支不可达（ISS-017/ISS-032/ISS-058） |
| **跨租户读写** | 🟡 写路径已挡，**读路径基本可越权** | 2026-01-25 复测：`TenantIsolationAspect` 已启用——`@Before` 比对入参 `tenantId` 与 `TenantContext`，不一致抛 `BusinessException("TENANT_ACCESS_DENIED")`（`:55-86`），`PLATFORM_ADMIN` 放行（`:99-105`，**注意：`admin` 现在真的持有该角色，见 M-06/ISS-061，所以这条放行对默认账号是生效的**）；ThreadLocal 已在 `JwtAuthenticationFilter` 的 `finally` 中 `clearTenantId()`（`:82`）。<br>❌ 但仓储层显式不过滤（`:91-97`），凡入参不含 `tenantId` 的读方法即无隔离——采购域 `findById`/`findAll`/`findByStatus`/`findBySupplierId`/`findByWarehouseId` 全中（**ISS-050**）。⚠️ 更根本的一条：**切面只在入参出现 `tenantId` 时才生效**（`:57-60` 无该参数即放行），所以「按 ID 直读」这类端点从设计上不在它的射程内。`default-tenant` 缺省值只剩 `PurchaseController.java:47` 一处（M-05 / ISS-051）；租户列覆盖 **42 张里 23 张**、仍缺 19 张（M-04） |
| **提权（自授角色/改他人密码）** | 🟡 **2026-01-25 大幅收窄但未清零** | ~~`POST /api/users/{id}/roles`、`PUT /api/roles/{id}/permissions`、`PUT /api/users/{id}/password`、`POST /api/auth/refresh` 全部无方法级鉴权（M-06）~~ → `UserController:31` 已加类级 `@PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage')")`、`RoleController:31` 同理（`role:manage`），所以**用户/角色的写操作现在需要平台管理员或对应权限点**。<br>❌ 仍敞开的是：`POST /api/auth/refresh`（`permitAll` + 只凭明文 username 换令牌，`AuthController:60-65`）与 11 个无方法级鉴权控制器里的业务写端点（`PUT /api/orders/{id}/status`、`DELETE /api/orders/{id}`、`POST /api/purchases/{id}/approve`…）——**任何已认证用户都能调**。⚠️ 附带发现：类级 `@PreAuthorize` 会让**未认证的** `/api/roles/nonexistent` 返回 **403 空 body 而不是 404**（鉴权先于方法执行），这是"越权探测"与"资源不存在"在前端无法区分的又一个来源 |
| **第三方平台凭据外泄** | 已泄漏 | `platform_integrations.api_key/api_secret` 明文入库且 `PlatformIntegrationController` 直接序列化领域聚合原样返回（S-05）；`OrderCreatedEvent` 内嵌 `PaymentInfo`/`ShippingAddress` 经 MQ 外发（`:23-25`） |
| **令牌伪造** | 密钥已进 Git | `application.yml` 的 `jwt.secret` 明文（S-04）；无 `jti`、无黑名单，签发后不可撤销 |
| **数据批量拖库** | 可利用 | 11/13 控制器列表端点无分页、无权限过滤（M-07）；无 `@Valid`（M-10） |
| **内部信息泄漏** | 可利用 | `Finance/PurchaseController` 把 `e.getMessage()` 拼进响应（M-08）；`show-sql: true`；启动日志打印默认管理员口令（~~`DataInitializationConfig:75`~~ → 2026-01-25 复测订正锚点：编码点在 `:134`，`logger.info("默认密码: 123456")` 在 `:145`） |
| 管理端未认证 | 尚未暴露（因 admin-web 不联后端） | admin-web 无 `context/`、无守卫、mock `authApi` 返回 `'mock-jwt-token'`（M-09） |

## 2. 设计原则

1. **令牌是唯一可信的租户与身份来源**——任何 `tenantId` 都不得来自请求参数。
2. **授权判定在两个层面同时存在**：URL 级（`SecurityConfig`）+ 方法级（`@PreAuthorize`）。URL 规则覆盖不到的动作型端点（`/api/users/{id}/roles`）必须由方法级兜住。
3. **默认拒绝**：`.anyRequest().authenticated()` 收尾；新增非 `/api` 路径不会静默无规则（现状无兜底，L-01）。
4. **敏感数据不进入出参与事件**：DTO 白名单化 + 事件载荷最小化。
5. **凭据不落在仓库里**：环境变量 / 密钥管理，且**已入库的密钥视为泄漏，必须轮换**。

## 3. 认证链路修复（第一优先，其余都依赖它）

~~现状 4 个缺陷必须**同一批**修（分开修会出现「修了校验但 filter 仍用自建实例」的假绿）：~~

> ✅ **2026-01-25：这 4 条已按"同一批"的要求全部修完（ISS-041/007/008），下表保留为修法记录。** 关键提醒仍然成立——下表任一条单独修都会出现假绿，而本轮实测**补上了第 5 个同批项**：只有 `@PreAuthorize` 与令牌 authorities 都到位还不够，**播种账号必须真的拿到角色**（ISS-061：`DataInitializationConfig` 的角色分配在非 Web 线程读懒加载集合 → `LazyInitializationException` 被吞 → 前 4 条全修完仍然表现为"管理端恒定 403"）。判据只有一条：**启动日志出现「已为用户 admin 分配角色 PLATFORM_ADMIN」，且 `GET /api/tenants` 返回 200。**

| # | 缺陷 | 位置 | 修复 |
|---|---|---|---|
| 1 | ~~`validateToken` 恒 true、两个 getter 恒返回桩值~~ ✅ | `JwtTokenProvider:106-128` → 现 `:189-191` + `:196` | ✅ 已按此实现：JJWT `parseSignedClaims` 验签 + 过期判断，非法返回 null；`getUsernameFromToken`/`getTenantIdFromToken`（`:141-144`）从 claims 读 |
| 2 | ~~filter 内 `new JwtTokenProvider()`，`@Value` 不生效~~ ✅ | `JwtAuthenticationFilter:28-30` | ✅ 已改构造器注入（`:31-35`），类上是唯一一份 `@Component`，`SecurityConfig:40-44` 注入过滤器、无重复 `@Bean`（M-01 已修） |
| 3 | ~~authorities 恒空 + 角色提取被注释~~ ✅ | `JwtAuthenticationFilter:46-48`、`CustomUserDetailsService:40` | ✅ 令牌写 4 个 claim（`username`/`tenantId`/`roles`/`permissions`），过滤器 `:46-72` 逐条映射为 `ROLE_*` 与权限点；`CustomUserDetailsService:57-73` 同口径。<br>⚠️ **同批必做的第 5 件事**：保证权限点能**活着到达领域对象**——`UserRepositoryImpl`/`RoleRepositoryImpl` 的 find 方法要 `@Transactional(readOnly = true)`（非 Web 线程无 OSIV），且 `RoleMapper` 必须把 `PersistentSet` **拷成普通 `HashSet`** 再交给领域（否则会话关闭后读取抛异常、且领域侧改动会绕过脏检查写回库表） |
| 4 | ~~`/api/platform/**` 规则与真实前缀不符~~ ✅ | `SecurityConfig:40` vs `PlatformIntegrationController:30` | ✅ 错规则已删；现为 `permitAll(login/refresh/logout)` → `/api/auth/me authenticated()` → `/api/tenants/** hasRole(PLATFORM_ADMIN)` → **`/api/** authenticated()` 兜底**（`:63-67`）。⚠️ 仍**没有字面 `.anyRequest()`**（L-01），非 `/api` 路径靠默认规则 |

✅ 已核实无需修复的一项：`Bearer ` 前缀剥离**代码里是做了的**（`JwtAuthenticationFilter:66-67` `startsWith("Bearer ")` + `substring(7)`）。[security-findings.md M-02](../security-findings.md) 的「未 `substring(7)`」描述与当前代码不符，应更正。

### 3.1 令牌设计（目标）

| 项 | 现状 | 目标 |
|---|---|---|
| claims | `username` + `roles`（`generateToken(Authentication)` `:43-56`）；带 `tenantId` 的重载 `:66-79` **全仓无调用点** | 必含 `sub`(userId)、`username`、`tenantId`、`roles`、`jti`、`iat`、`exp`；`generateToken(Authentication)` 不再丢 tenantId |
| 算法/密钥 | `Keys.hmacShaKeyFor(jwtSecret.getBytes())`（`:32-35`），算法由密钥长度推断（注释声称 HS512） | 显式指定算法并要求 ≥ 512 bit 密钥；生产用 RSA/EC 或 KMS 托管，便于多实例共享与轮换 |
| access 有效期 | `86400000`（24h，`application.yml`） | 15–30 分钟 |
| refresh | `jwt.refresh.expiration` 同为 86400000（与代码注释「默认 7 天」矛盾）；`/api/auth/refresh` 入参是**明文 username**，不校验旧令牌 | refresh token 7–14 天、带 `token-type=refresh` claim、服务端存储与轮换；refresh 必须校验旧 refresh token 有效且未撤销 |
| 登出 | `AuthController:70-76` 只 `SecurityContextHolder.clearContext()`（占位） | 引入撤销表 / Redis 黑名单（按 `jti`，TTL = 剩余有效期）；无黑名单时**不能声称「已登出」** |
| 未使用代码 | 三参重载 `generateToken(String,String,String[])` 无调用点 | 修 claims 时一并删除或接上（不要留「看起来能用其实没接线」的入口） |

### 3.2 角色模型的真实落地

关键事实：~~领域 `User` 有 `Set<Role> roles`（`domain/tenant/entity/User.java:34`），但 **`UserJpaEntity` 无角色字段/关联**（`:21-50` 内无 role）→ 角色不持久化、不参与认证，`roles` 表与用户-角色关系实际是空的（er-diagram §8 也标注缺 `user_roles`）。因此：~~

> ✅ **2026-01-25：本节第 1 条已完成，第 2、3 条同时落地。** 实测现状：
> - `UserJpaEntity:64-67` 已有 `@ElementCollection @CollectionTable(name = "user_roles") @Column(name = "role_id")  Set<String> roleIds`，运行时库**确实存在 `user_roles` 表**（旧口径「无此表」作废），当前 **2 行**，均属于 `admin`，值是**角色 UUID**（`roles.id`）：`42d1896d-…`（= `ADMIN`，属于默认租户）与 `a88fb4a9-…`（= `PLATFORM_ADMIN`，`tenant_id = SYSTEM`）。
> - 权限点从 `RoleJpaEntity` 的 `@ElementCollection` 桥表 `role_jpa_entity_permissions` 读出 → `RoleMapper` **拷成普通 `HashSet`** 交给领域 → `CustomUserDetailsService:57-73` 组 `ROLE_*` + 权限点 → 签进令牌的 `roles`/`permissions` claim → `JwtAuthenticationFilter:46-72` 重建 authorities。整条链已通，`GET /api/tenants` 实测 **200**。
> - ⚠️ **两个未收口的语义缺口**：① `user_roles.role_id` 存的是**角色 UUID**（指向 `roles.id`），但它是 `@ElementCollection` 桥表、**没有外键约束**，所以"删角色"不会级联清理，也可能留下指向已删角色的悬挂 id；② `users` 的唯一性是**全局** `username`/`email` 唯一约束（ISS-024），角色也没有 `(tenant_id, name)` 复合唯一（BR-09）。
> - 🔴 **本节第 2 条新增的硬约束**：这条链**每一步都读懒加载集合**，因此 `UserRepositoryImpl`/`RoleRepositoryImpl` 的 find 方法必须自带 `@Transactional(readOnly = true)`——OSIV 只覆盖 Web 请求线程，`CommandLineRunner` 里没有它（ISS-061 的根因，见 [backend-standards §5](backend-standards.md)）。

1. ~~补 `UserJpaEntity` 的 `@ManyToMany`（或独立 `user_roles` 关联表）+ 迁移脚本~~ ✅（用了 `@ElementCollection` + `user_roles` 桥表这条路；**注意本仓不引入 Flyway，列与表仍由 `ddl-auto: update` 生成**，见 [ADR-0002](../adr/0002-hibernate-ddl-auto.md)）；
2. `RoleJpaEntity` 已有 `tenant_id`（`:27-28`）与 `@ElementCollection` 桥表 `role_jpa_entity_permissions`，权限点应从此处读取并进入 authorities；✅ **已按此实现**
3. 角色命名与 `SecurityConfig:40-41` 的 `hasRole("PLATFORM_ADMIN")` 对齐（`PLATFORM_ADMIN` / `TENANT_ADMIN` / 业务角色），前端守卫按同一集合；~~`AuthGuard.jsx:44-53` 目前把角色检查注释掉了~~ → **✅ 前端守卫已实现**：`user-web/src/components/Auth/AuthGuard.jsx:9` 收 `requiredRoles`，`:33-43` 角色不匹配渲染 403 `Result`（不再放行），同文件 `:51-53` `PlatformAdminGuard` 要求 `PLATFORM_ADMIN`、`:58-60` `TenantAdminGuard` 要求 `TENANT_ADMIN`/`PLATFORM_ADMIN`——**与后端同一套角色名，口径已对齐**。<br>⚠️ 但**仅 user-web 有这两个守卫组件**（admin-web 用 `routes.jsx` 自己的判断），且**前端守卫只是体验层**：真正的门槛是后端的 ~~17 处~~ → 🔄 **2026-10-21：130 处真实 `@PreAuthorize`（9 类级 + 121 方法级，覆盖 22/24 个控制器）** + `SecurityConfig` 路径规则（M-06）。

## 4. 多租户隔离落地（依赖 [migration-plan.md M3](../db/migration-plan.md)）

顺序不能颠倒——**先有列，再过滤，最后去掉客户端传参**：

1. **库层**：18 张业务表加 `tenant_id`（`V4/V5/V6`），唯一键从全局改租户内（`users.username/email`）。<br>🔎 **现状（2026-01-25 查库）：本步骤未完成。** 运行时 42 张表里 23 张有 `tenant_id`、19 张没有（M-04）；`SHOW INDEX FROM users` 的 `username`/`email` 两条唯一索引**仍是单列全局唯一**，未改成 `(tenant_id, username)`。本行是**待办**，不要当成已落地事实引用（另见 [spec/tenant-and-iam.md BR-09](../spec/tenant-and-iam.md)）。<br>🔄 **2026-10-21 实库复测：45 张表 / 25 张有 `tenant_id` / 20 张没有**（`shops`、`audit_logs` 有列，`system_configs` 无列）；**`users.username`/`email` 仍是单列全局唯一**，本步骤仍未完成。
2. **入口层**：`JwtAuthenticationFilter:51` 已有 `TenantContext.setTenantId(tenantId)`，~~但**全仓 `clearTenantId()` 调用点为 0** → 在 `finally` 中清理，否则线程池复用会串租户（M-03）~~ → **✅ 2026-01-25 已在 `finally` 补上 `clearTenantId()`（`:82`），M-03 的 ThreadLocal 串租户风险解除。**
   - 🔄 **本步骤的"读侧"仍未收口，这里是 `TenantContext` 全部 8 个调用点的实测清单**：`JwtAuthenticationFilter:51`（写）、`:82`（清）、`TenantIsolationAspect:57,62`（断言入参与上下文一致）、`:93,95`（仓储切面**只打 trace**）、`OrderController:167,168`（**唯一一个从上下文取值去用的地方**，且只是 `resolveTenantId` 的中间优先级，取不到仍回落 `DEFAULT_TENANT_ID`）。
   - ~~**也就是说：全仓没有任何一条查询从 `TenantContext` 取租户去过滤数据**（这正是 ISS-050 的精确定义）。~~ → 🔄 **2026-10-21 复测：这句话已不成立。** `TenantAccessGuard.scopedTenantId()`（`application/service/support/TenantAccessGuard.java:50-63`）与 `requireOwned()` 直接读 `TenantContext` 的登录租户，并被各应用服务调用；采购仓储还做了 fail-closed 的租户条件（`PurchaseOrderRepositoryImpl:68-75`）。**但收敛仍是逐聚合的，没有结构性保证**：`requireOwned` 只在 28 处 / 8 个文件出现，订单/客户等读路径仍靠控制器传参 + 常量兜底（M-05），本轮另登记 ISS-146/147 两处未接守卫的入口。
3. **持久层**：两种方案择一——
   - A（推荐）：Spring Data `Specification`/`@Query` 显式带 `tenant_id` + Hibernate `@TenantId`/`@FilterDef` 全局过滤；
   - B：`TenantIsolationAspect` 只做**断言**（仓储方法入参未携带当前租户则抛 `C00003`），过滤仍由查询承担。现状 AOP 是空壳，走 B 的成本更低。
4. **收口**：删除所有 `@RequestParam tenantId`；清掉硬编码——~~`LogisticsOrderMapper:52-54`、`WarehouseMapper:54-56`（反射注入 `"default-tenant"`）、`RocketMQEventConsumer:54,115`、`EventHandler:100`（`"DEFAULT_TENANT"`，大小写两种写法并存）~~ → **2026-01-25 重测的完整清单**（`RocketMQEventConsumer` 已随 MQ 重构不存在）：
   - 字面量 `"default-tenant"` 主源码 **17 处** = **10 个 `DEFAULT_TENANT_ID` 常量声明**（`OrderApplicationService:31`、`ProductApplicationService:42`、`CustomerApplicationService:23`、`ShopWarehouseApplicationService:36`、`domain/finance/service/FinancialTransactionServiceImpl:26`、5 个 `*ImportApplicationService`）+ 若干文档注释 + `PurchaseController:47`；
   - 该常量**引用 15 处**，形态一律是 `StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID`（含 **controller 侧兜底** `OrderController:170`、`CustomerController:68`）；
   - **异步与映射链路**：`EventHandler:100`、`OrderCreatedEventConsumer:38`、`InventoryShortageEventConsumer:37`、`BusinessProcessOrchestrationService:48,58`、`WarehouseMapper:59-60`（反射注入）；
   - 第三种形态：`LogisticsOrderMapper` **完全不处理 `tenantId`**（0 命中 → 从 JPA 读出的聚合该字段为 null），这不是"硬编码兜底"而是"根本没映射"，比兜底更难发现。
   > ⚠️ 结论：本步骤的工作量不是"删一个 `@RequestParam`"，而是**把 25+ 个兜底点改为「从 `TenantContext`/令牌取，取不到就拒绝」**。建议与 §4 步骤 3 的持久层过滤同批做，否则删掉兜底只会让现有单租户流程大面积报错。
5. **消费者场景**：MQ 消费者无 HTTP 上下文，必须从事件载荷取 `tenantId` 并显式设置/传递（`DefaultResourceListener` 风格），不能靠 ThreadLocal 继承。
6. **前端**：`UserList.jsx:27,40` 的 `'current-tenant-id'` 改自 `AuthContext`。

⚠️ 现实提醒：~~当前只有 3 张表有 `tenant_id`（`ProductJpaEntity:38-39`、`UserJpaEntity:26-27`、`RoleJpaEntity:27-28`）~~ → **2026-01-25 实测已扩到运行时 42 张表里 23 张有 / 19 张没有**（M-04）；且**没有任何查询从 `TenantContext` 读取 tenantId**（调用点仅 filter 的写/清 + aspect 的断言/trace + `OrderController:167-168` 一处取值，见上一步清单）。所以「多租户已实现」的说法**只在写路径入参校验这一层成立**，读路径与仓储过滤仍未实现（**ISS-050**）。

## 5. 授权与提权面

| 端点 | 风险 | 处置 |
|---|---|---|
| `POST /api/users/{id}/roles` | 自授角色 | `@PreAuthorize("hasRole('PLATFORM_ADMIN') and #id == authentication.name")` 类规则；~~开启 `@EnableMethodSecurity`（现状全仓 `@Enable` 只有 `@EnableWebSecurity`，方法级注解 0 处）~~ → **✅ 2026-01-25 已开启**：`SecurityConfig:35` `@EnableMethodSecurity`（全仓 `@Enable` 现共 2 个，另一处是 `:34` `@EnableWebSecurity`），**`@PreAuthorize` 实测 17 处 / 9 个控制器**（M-06）。⚠️ 但 `UserController:31` 是**类级** `hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage')`，**没有 `#id == authentication.name` 这类自身校验**——所以"改他人密码"这一半风险仍在（任何持 `user:manage` 者可为任意 id 改密），只是不再是"任何已认证用户" |
| `PUT /api/roles/{id}/permissions` | 改权限点 | 仅平台管理员 |
| `PUT /api/users/{id}/password` | 改他人密码 | 拆成「自助改密（校验旧密码）」与「管理员重置」两个端点，后者需审计日志 |
| `POST /api/auth/refresh` | 凭 username 换令牌 | 见 §3.1；改完后必须校验旧 refresh token |
| ~~列表端点（11 个无分页）~~ → **口径已翻转**：20 个控制器里 **11 个已返回 `PageResult`**（`Order`/`Finance`/`Product`/`Customer`/`Logistics` 5 个主控制器 + 6 个 `*ImportController` 里的 5 个，`WarehouseImportController` 虽引用 `PageResult` 但也无注解） | 批量拖库 | 强制分页 + `size` 上限 200，见 [performance-capacity.md §4](performance-capacity.md)。<br>❌ 仍无界的列表：`PurchaseController`（全量数组）、`WarehouseController`、`ShopWarehouseController`、`LogisticsChannelController`、`PlatformIntegrationController`、`TenantController`；且这 6 个里除 `TenantController`（靠 `/api/tenants/** hasRole` 路径规则）外**都没有方法级鉴权**，任何已认证用户可全量拉取 |

🔴 **权限点的"种子数"与"生效数"（2026-01-25 实测，做冒烟用例前必读）**：库中 PLATFORM_ADMIN 持有 **22** 个权限点，代码里被 `hasAuthority()` 检查的只有 **10** 个——`finance:read`/`finance:write`/`finance:import`/`user:manage`/`role:manage`/`platform:manage`/`order:import`/`logistics:import`/`product:import`/`customer:import`。其余 **12 个（`order`/`product`/`customer`/`warehouse`/`purchase`/`logistics` 的 `:read` 与 `:write`）只是数据种上了，没有任何端点检查它们**。因此：

- ~~写冒烟用例时**不要**假设"访问 `GET /api/warehouses` 需要 `warehouse:read`"——实际门槛只是 `/api/** authenticated()`；只有 `finance:*`、`user:manage`、`role:manage`、`platform:manage`、5 个 `*:import` 端点带权限点门槛。~~ → **2026-10-09 口径翻转（ISS-112）**：26 个权限点全部有端点消费，`GET /api/warehouses` 确实要 `warehouse:read`；写探针时"只认证不带权限点"应当预期 **403**，判据见 `smoke.sh` §11（24 条）与 `PermissionPointCoverageGuardTest`。
- 17 处注解一律是 `hasRole('PLATFORM_ADMIN') or hasAuthority(...)` 的**或**关系：平台管理员永远不看权限点，权限点只对"非平台管理员但被单独授权"的用户有意义——而当前库里只有 `admin` 一个用户且它是平台管理员，**这条或分支从未被真实验证过**。⚠️ OR 结构本身仍在（口径见 [known-issues.md §9](../qa/known-issues.md)），但"从未被真实验证"已不成立：本轮用只读子账号 + TENANT_ADMIN（25 点，无 `platform:manage`）两种非平台身份跑过 403/200 双向探针。
- 领域层没有任何权限判定入口：`Role.hasPermission(String)`（`domain/tenant/entity/Role.java:131`）**调用点 0**，无 `PermissionService.java` → 按钮级/字段级权限未实现，前端只能靠 `AuthGuard` 的**角色**名（`frontend/user-web/src/components/Auth/AuthGuard.jsx:34` 判角色、`:35-43` 渲染 403）粗粒度控制。→ **2026-10-09 前端半边已补（ISS-112）**：`AuthContext` 新增 `can()` 与 `permissionsLoaded`，`AuthGuard.jsx` 新增 `PermissionGuard`（按权限点渲染 403），`menus.js` 每个叶子挂权限点，写操作入口用 `components/Auth/Can.jsx` 包裹。`Role.hasPermission` 在后端领域层仍是 0 调用点（服务端一律走 `@PreAuthorize` 的 authorities），字段级权限仍未实现。

🔄 **2026-10-09 复测（本轮新增的执行点，逐项带实测证据）**：

- **登录失败锁定已真实落地**（§5 提权面的第一道）：`AuthController` 在**认证之前**调用 `LoginSecurityService`，用 `audit_logs` 里 15 分钟窗口内的 `AUTH_LOGIN/FAILURE` 计数判定锁定，达阈值即对**正确口令**也返回 `400 TOO_MANY_LOGIN_ATTEMPTS`；`security.maxLoginAttempts<=0` 表示关闭锁定。证据：`security-slice-smoke.sh` §4–§6（5 次错口令 → 第 6 次正确口令被拒；改 0 后立即放行）+ `LoginSecurityServiceTest` 12 例。
- **会话超时不再只是表单数字**：`security.sessionTimeout` 注入 JWT `exp`（`JwtTokenProvider` 5 参重载）。证据：配置 3600 → 解出的 token `exp-iat=3600`；复原 86400 → 同一账号新 token `ttl=86400`（冒烟 2.1 / 10.2b）。
- **`security.passwordPolicyEnabled` 有执行点**：建租户 / 建租户管理员入口的 `requireAcceptablePassword`（开关关闭时不拦）。⚠️ 边界：只覆盖**创建**侧口令，登录侧不改密码强度策略。
- **审计面从 0 到 1**（§7「审计」行的目标态）：`audit_logs`（actor / tenant / action / target / target_type / detail / ip / traceId / result / created_at）+ `@Auditable` + `AuditAspect`（`REQUIRES_NEW` 写，成功与失败都留痕，写审计失败只 WARN 不影响业务），登录/刷新因**认证前拿不到 actor** 而显式记录 `AUTH_LOGIN`/`AUTH_REFRESH`；读端点 `GET /api/audit-logs` 类级 `PLATFORM_ADMIN`。
- **审计覆盖从"身份面"扩到"业务单据面"（2026-10-09，ISS-114）**：上一轮只把身份/权限/租户/凭据/店铺接了审计，**订单、库存、财务、物流、仓库这 5 个控制器里的 35 个写端点全部零留痕**——金额改动、库存出入、单据状态流转在 `audit_logs` 里查不到任何一行，等于合规上无法回答"谁在什么时候把这单改成已发货"。现共 **70 个 `@Auditable` / 12 个控制器**全覆盖，并让 `AuditAspect` 识别 `Result.code != 200` 这种"HTTP 200 但业务失败"的响形状记为 `FAILURE`（此前只按异常判失败）。口径边界见 [known-issues.md §13 R-27](../qa/known-issues.md)：~~**入审计的是金额/库存/单据状态写操作，主数据 CRUD（仓库/商品/渠道的增改）不入**，避免审计流被台账维护噪声淹没~~ → 🔄 **2026-10-09 后半句已被 R-37 推翻**（见下一条）：默认全留痕，噪声用 `action` 过滤解决。⚠️ ~~一处已登记未修的语义缺口：**批量发货**的行级失败被吞进 HTTP 200 + `SUCCESS` 审计（ISS-117），改响应形状需与前端联动，故本轮只做登记。~~ → 🔄 **同轮已修（R-36）**：响应契约不动，改由 `AuditAspect.describePartialBatch` 在 `failureCount + skippedCount > 0` 时记 FAILURE 并写逐行统计。
- **审计覆盖从"业务单据面"扩到"全部写端点"（2026-10-09，ISS-123 / R-37 / R-38）**：ISS-114 的守卫清单是手工维护的 12 个控制器，**清单之外的 10 个控制器 / 48 个写端点对它不可见**——客户 CRUD（PII）、商品与 SKU（重量直接决定运费与报关数据）、物流渠道、店铺仓库绑定、平台同步/探活/发货回写、6 条导入线的执行端点与字段映射保存、以及登出，全部零留痕。用源码扫描脚本 `scripts/smoke/audit-coverage-scan.js` 对表才发现，现口径为 **126 个写端点 = 118 个 `@Auditable` + 8 个带理由豁免**（登录/刷新由 `LoginSecurityService` 显式记录两侧；6 个 `*ImportController#preview` 只解析不落库），`AuditActions` 常量 **94 个**（本轮 +30），守卫清单扩到 **22 个控制器**且**豁免只可收缩**。**教训成文：覆盖面守卫的分母必须来自源码扫描，不能来自手工类清单。** `AuditAspect` 的"业务失败"判定同步补齐**四类形状**（R-38）：抛异常、`Result.code ≠ 200`（含被 `ResponseEntity` 包壳的 4xx）、批量/作业**行级**未生效、平台同步 `Map.failures` 非空；后三类在 HTTP 与 `code` 都是 200 时仍记 FAILURE，`error_message` 带逐行统计——**HTTP 状态码不是业务结果的证据**。活证据：`smoke.sh` 12.156–12.158（商品导入 `PARTIAL_SUCCESS`、店铺仓库同步有行被跳过、回写不存在物流单的 400 被包壳）三条都断言 `audit_logs` 里是 FAILURE。
- **权限点的"种子数 vs 生效数"更新**：PLATFORM_ADMIN 现持 **26** 个权限点（不是旧文的 22），静态可扫到 `hasAuthority()` 检查的 **14** 个，仍未被任何端点检查的 **12** 个 = `customer:read/write`、`logistics:read/write`、`order:read/write`、`product:read/write`、`warehouse:read/write`、`shop:credential_write`、`shop:sync`（`@PreAuthorize` 实测 **56 处 / 17 个文件**，旧文 17 处已过期）。⚠️ ~~结论不变：这 12 个只是数据种上了，**按钮级/字段级权限仍未实现**，`Role.hasPermission(String)` 调用点依旧为 0。~~ → **2026-10-09 清零（ISS-112）**：这 12 个点已全部接到端点上，服务端目录 **26/26 有消费方**（`PermissionPointCoverageGuardTest` 逐个点名断言，新增点若不接线即测试失败；`shop:sync` 挂在 `POST /api/warehouses/shop/sync/{integrationId}`）；前端同步落地菜单/路由/按钮三层绑定。**字段级权限仍未实现**，且 `Role.hasPermission(String)` 在后端仍是 0 调用点（服务端只认 authorities）。
- **权限点接线轮的另一处实测发现（ISS-113）**：`DELETE /api/orders/{id}` 只删订单，不删下单时自动派生的 `logistics_orders` 行，留下的孤儿行会让「删除租户」的业务数据守卫一直报 `400 仍有 N 条业务数据`。修复：`OrderApplicationService.deleteOrder` 先 `logisticsOrderRepository.findByOrderId(...)` 逐个删再删订单（同一 `@Transactional`）。证据：`OrderApplicationServiceTest` 2 例 + `smoke.sh` 3.15~3.18 + DB 复查 `logistics_orders LEFT JOIN orders` 孤儿行数 **0**。
- **写路径的租户归属只有一个入口**：`TenantAccessGuard.resolveWriteTenant(显式传参, 历史默认桶)` —— 入参优先但**租户用户显式传他租户即拒**（`TENANT_ACCESS_DENIED`），缺省取登录租户，只有完全没有会话（内部调用、事件消费）时才落 `default-tenant`。此前 12 个应用服务各写了一遍「空值兜底成 `default-tenant`」，而 `default-tenant` 在 `tenants` 表里没有行、读侧又按登录租户收敛，写进去的数据对谁都不可见（接口 200、导入报成功、列表为空 —— [known-issues.md §13 ISS-118 / R-30](../qa/known-issues.md)）。**守卫**：`TenantWriteFallbackGuardTest` 扫 `application/service/**` 源码，绕过该入口自己兜底即红，并反向断言入口调用数 `>= 15`（实测 17），防止删空兜底骗过前者。
- **哨兵字面量的归一化是租户判定的一部分，不是兼容性取巧（2026-10-09，ISS-120 / R-32）**：`default-tenant` 在 `tenants` 表里没有行，客户端却把它当"缺省租户"硬编码传上来（历史文档示例 + user-web 常量）。定标为**一个事实源 + 三种语义**：`TenantContext.normalizeRequested()` 把 `null`/空白/该字面量（含首尾空格）统一判为「未指定」，**显式传真实他租户照旧 400**；写＝落登录租户，读＝按会话收敛（平台管理员＝跨租户全量），Role/User/Shop 上平台管理员带字面量则 400「必须显式指定 tenantId」而不是给空列表。归一化必须同时落在 `TenantIsolationAspect` 里，因为它 `@Before` 跑在服务体之前，只改服务层会先把合法请求判成越权。近似值（`Default-tenant`、`default-tenant-2`、`default`）**不算**哨兵，仍按显式租户处理。
- **按 ID 的写操作必须显式判归属，权限点判定不能替代（2026-10-09，ISS-122 / R-34，P0）**：`platform:manage` 实测发在一个**租户**角色上（`role_jpa_entity_permissions`），而 `@PreAuthorize` 只回答"能不能做这类操作"、不回答"这一行是不是他的"；`/api/platform-integrations` 的 12 个按 ID 写端点因此可被租户用户拿去改/删/同步他租户的集成（含密文凭据）。修复是控制器入口 `requireOwnedIntegration(UUID)`（`TenantAccessGuard.requireOwned`），**跨租户与查无此 ID 同码 404 `ENTITY_NOT_FOUND`**（不用状态码差异确认他租户资源存在性，与 `ShopController` 一致），且校验要放在方法体 `try` **之前**——这些控制器的 `catch (Exception)` 会把 404 压成 400/500。证据含一次变异检查：撤掉那一行后 PUT/DELETE 真 200 且靶子行数 1 → 0。
- **凭据类聚合的归属只能由会话或既有行决定（2026-10-09，ISS-121 / R-33）**：`PlatformIntegrationSaveRequest.toAggregate()` 原本直接吃请求体的 `tenantId`，而这一族既不被切面覆盖（形参是聚合不是 `tenantId`）、也不被源码扫描守卫覆盖（兜底不在 `application/service/**`），实测攒出 **35 条** `tenant_id='default-tenant'` 的集成行（每条都带 `enc:v1:` 密文凭据，谁也查不出来谁也删不掉）。现在创建走 `resolveWriteTenant`、PUT 把归属钉回既有行，并把幻影桶巡检常驻成 `smoke.sh` §13 的 **22 张业务表**断言（清单与租户删除守卫同源，新增表自动纳入）。
- **审计覆盖有守卫**：`AuditCoverageGuardTest` 要求写端点「有 `@Auditable` 或在白名单里写明理由」，白名单键指向的代码消失即报错（口径见 [known-issues.md §9 R-12](../qa/known-issues.md)）。🔄 2026-10-09（ISS-114）：守卫范围从身份/权限类扩到 **12 个控制器**（含订单、库存、财务、物流、仓库的业务写端点），并加了「注解总数 `>= 25`」的下限断言，防止批量丢注解后测试仍静默通过。🔄 2026-10-09（ISS-123 / R-37）：清单扩到 **22 个控制器**，豁免从 15 条收到 **8 条**（7 条平台集成豁免删除，因为那些端点已改为真留痕），并新增**自检负例**——夹具控制器里 3 个漏标注解的写端点必须被逐个报出来、已标注的 DELETE 与只读的 GET 不得混进缺口；没有这条，判定逻辑失效时"全绿"与"空转"无法区分。守卫之外另有**源码扫描门**（`audit-coverage-scan.js --strict`，挂在 `smoke.sh` 12.161），分母来自全量扫描而不是手工清单。
- **租户状态真正参与令牌签发**（本轮补上的最大一处空缺，见 [known-issues.md §11 R-20/R-21](../qa/known-issues.md)）：`AccountLoginGuard.isUsable` = 账号 `ACTIVE` **且** 所属租户存在且 `ACTIVE`；认证链路（`CustomUserDetailsService`）用它判定 `enabled`，续期链路（`POST /api/auth/refresh`，过去直接查库重签、连 `users.status` 都不看）在重签前调用同一个守卫。效果：暂停/注销租户 → 其账号新登录 **401 `ACCOUNT_DISABLED`**、既有刷新令牌续期 **400**；重新激活立即恢复。**平台管理员例外**（它挂在自己管的租户上，连带拒绝等于把运营方锁死、现场无人能恢复）。⚠️ 固有边界：无状态 JWT 在 `exp` 到期前仍然可用，要收窄请调 `security.sessionTimeout`（当前目录默认 86400）。证据：`AccountLoginGuardTest` 6 例 + `smoke.sh` 10.1~10.7 真实 HTTP 往返。
- **删除租户 = 身份级联 + 业务守卫**（R-19/R-20/R-31）：`TenantServiceImpl.deleteTenant` 在同一事务内先拒绝「含平台管理员的租户」与「仍有业务数据的租户」（`TenantRepository.countBusinessRecords` 扫 22 张租户维度业务表并回条数），再逐个删除该租户的账号与角色、最后删租户。修掉的是真实越权面：`users/roles.tenant_id` 没有指向 `tenants` 的外键，旧实现只删租户行，孤儿账号实测仍可登录并拿到 25 个权限点的令牌。`audit_logs` 既不级联也不阻断（留痕是合规资产）。**守卫的判据是"看得见"的业务数据**（R-31 / ISS-119）：客户是全仓唯一软删除的聚合，`DELETE /api/customers/{id}` 留下 `status='DELETED'` 的墓碑行且列表与详情都不显示它，若把它算成"仍有业务数据"，则"把租户下的客户删干净"这个动作本身就让该租户永久删不掉（实测 400 的原因正是那条用户看不见的行）；`TenantRepositoryImpl.INVISIBLE_ROW_PREDICATES` 只给 `customers` 挂 `AND status <> 'DELETED'`，其余聚合是硬删除，**不给它们加状态条件**（那会让真实数据对守卫隐身），也**不级联删历史行**——代价是删租户后其客户墓碑成为孤儿行，这是该裁决明确接受的取舍。证据：`TenantServiceImplDeleteTest` 4 例 + `TenantRepositoryImplBusinessCountTest` 5 例（含"只剩墓碑时计数为 0"与"只有 customers 带排除条件"两侧，并用撤掉修复转红的变异检查实证非空测试）+ `smoke.sh` 10.8~10.12 与 10.9b/10.9c/10.10（建活跃客户时删租户**仍 400**、软删后 200、修复前 400 的同一租户重放现为 200）。
- **停用/锁定不再伪装成服务端故障**：`DisabledException`/`LockedException` 新增专用出口 → **401 + `Result` 信封**（`ACCOUNT_DISABLED` / `ACCOUNT_LOCKED`），不再落兜底 handler 变成 `500 SYSTEM_ERROR`；按 ID 读不存在的租户同轮从 500 改为 **404 `ENTITY_NOT_FOUND`**（`getTenantById` 改抛 `EntityNotFoundException`）。
- **冒烟可重复运行**：`smoke.sh` 新增 §10 收尾删除本次建的租户并断言列表不再含本次 `RUN_ID`；历史残留（6 个冒烟租户及其账号/角色）已用新级联删除清理，`orphan_users`/`orphan_roles` 归 0。
- **聚合的读路径重建必须走 `restore`，`created_at` 的真实性是取证链的地基（2026-10-10，ISS-125 / R-41）**：`User`/`Role`/`Tenant` 的 `createdAt` 是 `final` 字段、只在私有构造里赋值，而三个 mapper 的 `toDomainEntity()` 图省事复用了 `create()` 工厂——工厂的语义是"这条记录刚诞生"，于是**每一次读都把记录的诞生时刻重置为本次请求**；更要紧的是本项目的写路径是 `toDomainEntity()` → 改字段 → `toJpaEntity()` → Spring Data `merge`，**被伪造的时间会随下一次 `save()` 原样写回库**：一个只改姓名的 `PUT /api/users/{id}` 实测把 `users.created_at` 覆盖成当前时间，响应 200、业务字段完全正确，没有任何失败信号。审计与合规回答的"谁在什么时候建的这个账号"依赖的正是这一列，所以它按 **P1 数据真实性**登记而不是显示问题。修复口径三条：① 每个聚合加 `restore(...)` 静态工厂 + 只接 `createdAt`/`updatedAt` 的私有构造，字段**直接赋值**、不经过 `activate()/deactivate()/lock()` 这类状态突变器（旧实现走突变器，连 `updatedAt` 一起丢）；② 重建路径要守的领域不变式在 `restore` 里**显式重述**（`Role.restore` 仍调 `validateName`），而不是靠"顺便经过工厂"；顺带修掉 `createSystemRole` 在重建时把 `tenantId` 强制改写成系统租户的副作用；③ **第二个重建点容易被漏**——`UserRepositoryImpl.toDomainRole()` 独立用 `create()` 重建内嵌角色，只改 mapper 修不掉 `GET /api/users/{id}` 里 `roles[].createdAt` 的伪造。回归位：`TenantDomainRestoreTest` 4 例（含"负例"：`create()` 仍必须打当前时间，防止把新建一起改掉）+ 常驻探针 `scripts/smoke/iss125-created-at-probe.sh` **9/9**（**修复前同一脚本 3 处失败**；判据用"库里挪 3650 天 ⇒ 接口同向挪 3650 天"的**位移相等**，并先用 app 自己写入的一行标定本机时区偏移，见下条）。⚠️ **既成损害不可恢复**：旧代码已经发生过的覆盖找不回来（库里无历史快照），本轮只保证此后不再污染，并如实登记而非假装数据已修好。
- **时间戳的时区口径是本项目的已知债（2026-10-10 登记为 ISS-126，未修）**：`application.yml:26` 的 JDBC URL 带 `serverTimezone=UTC`，领域代码却用 naive `LocalDateTime.now()`，本机（JVM +8）实测 **API 值 = DB 值 + 28800 s**。应用自写自读的行读写自洽，但**原始 SQL 写入的值读回来差 8 小时**，且同一份代码部署到 UTC 主机（生产容器常态）后偏移变 0 而历史数据仍是 +8h 写进去的——**这一列的含义随部署环境漂移，等于没有稳定契约**；文档里把 `audit_logs.created_at` 说成 UTC 也不准确。**任何一侧改齐都会改变全部已存行的解释**，属于需要迁移方案的口径决定，故本轮不动，只把判据改成"位移相等"（见上条探针）。⚠️ 写给这个库的时间断言**禁止**用「API 字符串 == SQL 字符串」。
- **管理端路由守卫的 403 只能靠植入令牌证明（2026-10-10 浏览器实测口径）**：`AdminGuard`（`routes.jsx:13-32`）要求 `isAuthenticated` **且** `hasRole('PLATFORM_ADMIN')`，实测形态是**客户端 403 兜底页**（无后端请求发出）。但正常登录走不到这一分支——非平台管理员在**登录页**就被「该账号不是平台管理员，无法登录平台管理端」挡住。所以要证明这条守卫真的在拦，必须在 localStorage 里**植入一个 TENANT_ADMIN 令牌**再访问 `/platform/*`；同时 `AuthContext` 的 `init` 会调 `/api/auth/me` 并**从响应重新派生 roles**，所以只种 `token` 一项即可（种 `roles` 反而测不到派生逻辑）。另记一条**误报撤回**：admin-web 登录页看到的 `admin/123456` 是**浏览器自动填充**，`Login.jsx` 里 grep `initialValues|setFieldsValue|123456` 0 命中，不是代码预填。

## 6. 管理端（admin-web）接入前置条件

admin-web 一旦接后端就会「无认证 + CORS 拒绝」同时暴露（M-09）。接入前必须完成：

> 🔄 **2026-01-25 复测：本节四条前置条件已完成 2.5 条，admin-web 实际已接入后端。** 逐条对账：
> 1. 🟡 **半完成**：~~允许来源已加入 admin 源（实测 `SecurityConfig:50` `setAllowedOrigins(List.of("http://localhost:5173","http://localhost:5174"))`（`:31` 是更旧行号）~~ → 🔄 **2026-10-10 复测订正**：白名单已跟着真实端口改成 `SecurityConfig:63` `List.of("http://localhost:5110", "http://localhost:5120")`，与两个 `vite.config.js:9` 的 `server.port`（admin **5110** / user **5120**）一致——旧文档写的 5173/5174 是 vite 默认端口，早已不是本项目的端口，按它验证会误判成 CORS 失败。**仍未完成的是那半条**： origins **依然硬编码在 `List.of` 里、没有外部化**（属 S-04 边界，本轮明确不改）；两个 `vite.config.js` 也没有 proxy，所以同源方案没做、目前靠 CORS 白名单跑通（`api.js:6` / `api.js:48` 的 `baseURL` 直接指向 `http://localhost:8082/api`）。
> 2. ❌ **未做**：admin-web 有**自己一份** `context/AuthContext.jsx`（与 user-web 重复实现，非复用、也未抽公共包），这是 [frontend-architecture.md §10](frontend-architecture.md) 记的重复项。
> 3. ✅ 已做且形态正确：`UserController:31`、`RoleController:31`、`PlatformIntegrationController:46` 都是**类级 `@PreAuthorize`**（不依赖 URL 前缀）；⚠️ 唯一仍靠前缀规则的是 `TenantController`——它**没有任何注解**，只由 `SecurityConfig:66` 的 `/api/tenants/** → hasRole(PLATFORM_ADMIN)` 兜住，这正是本节原本要求避免的形态。
> 4. ✅ **已完成**：~~删除 mock `authApi`（`admin-web/src/services/api.js:165-185`，写死 `admin/admin123`）~~ → 该文件现在**只有 143 行**，mock `authApi` 已不存在；`authApi:69`、`tenantApi:77`、`userApi:90`、`roleApi:102` 均为真实 HTTP。<br>~~❌ 但**冒名换了一个对象**：`platformApi:116-143` 是**硬编码桩**（`getSystemConfig` 直接返回写死的对象，`saveSystemConfig:137` 只 `console.log(:138)`）——admin-web 5 页里 `Login/TenantList/UserList` 3 页真实、`PlatformDashboard`（无 import）与 `SystemConfig`（有 import、**0 个调用点**）2 页仍假，即 ISS-026 的「3 真 2 假」。~~ → 🔄 **2026-10-10 复测：这个桩对象已整体消失**。`admin-web/src/services/api.js` 现 **128 行**，导出 `authApi:69`/`tenantApi:77`/`userApi:90`/`roleApi:103`/**`auditApi:117`**/**`systemConfigApi:123`**（`GET /system-config` + `PUT /system-config` 真往返），全仓 grep `platformApi` 在两个前端各 **0 命中**；admin-web `pages/` 下 **6 个页面 6 个都走真实接口**，`mock` 0 命中（ISS-026 收口，见 [known-issues.md §16](../qa/known-issues.md)）。浏览器实测：配置保存 → 刷新复验值仍在 → 按原值还原；看板「总用户数」与用户列表行数一致（**5 == 5**，此前不一致是 ISS-127 的前端契约漂移）。

## 7. 数据保护

| 项 | 现状 | 目标 |
|---|---|---|
| 平台凭据 | `api_key`/`api_secret` 明文入库 + 原样返回（S-05） | 信封加密（KMS/主密钥）存 `*_enc`；出参永不返回 secret，只返回 `hasCredential: true` 与掩码 `ak_****1234`；轮换与撤销接口 |
| 支付/地址信息 | 事件载荷内嵌（`OrderCreatedEvent:23-25`）、实体直接序列化 | 出参 DTO 白名单；事件只带 ID 与必要字段；`payment_infos.transaction_id` 等按需脱敏日志 |
| 口令 | `BCryptPasswordEncoder`（~~`SecurityConfig:57-58`~~ → 实测在 `SecurityConfig:82-83`）✅ 正确 | 保持；`DataInitializationConfig:134`（~~`:64`~~）的 `123456` 改为**首次登录强制改密**或随机口令+一次性输出 |
| 默认数据 | 无租户时建默认租户 + admin（`initializeDefaultTenantAndUser`，实测 `:115-140`，入口在 `:106`；~~`:48-50` 幂等~~） | 保留初始化但**禁止打印口令**（实测 `:145` `logger.info("默认密码: 123456")` + `:146`，~~`:75`~~），并加环境变量开关 `app.init.demo-data=false` |
| 异常信息 | `e.getMessage()` 拼进响应（M-08） | 对外只给 `code` + `traceId`，堆栈仅落日志，见 [error-codes.md §5](../api/error-codes.md) |
| 审计 | 无操作审计（只有 `createdAt/updatedAt`，且 `updated_at` 不会自动刷新，R-04） | 新增 `audit_logs`（actor、tenant、action、target、ip、traceId、结果），覆盖 §5 全部提权面端点 |
🔄 **2026-10-09 复测：本表三行已推进**：
> - **平台凭据**：`api_key`/`api_secret`/`access_token`/`refresh_token` 已改为 **AES-256-GCM 密文列**入库，出参只给掩码 + `hasCredential` 位（ISS-011 已修，但主密钥仍需部署侧用环境变量注入，未注入时用开发默认值 —— 见 §8 与 ISS-021）。凭据写入唯一入口 `PUT /api/shops/{id}/credentials`，任何写入把授权状态打回 `PENDING`。
> - **口令 / 默认数据**：种子口令改为 `app.seed.default-tenant-admin-password` 注入，**启动日志不再打印口令明文**（旧文实测的 `logger.info("默认密码: 123456")` 已消失，现为「口令来源: 配置项」一行提示）。⚠️ 「首次登录强制改密」仍未做。
> - **审计**：见 §5 复测块的「审计面从 0 到 1」与「审计覆盖从身份面扩到业务单据面」「扩到全部写端点」；`audit_logs` 已在真实库运行验证，且**每类动作码都有按码回读的实测断言**（`smoke.sh` 12.50–12.84 逐动作码验 SUCCESS、12.85/12.86 验 FAILURE、**12.128–12.155 再验本轮新增的 28 个动作码**、12.156–12.158 验"HTTP 200 但业务失败必须记 FAILURE"），不再只是"有行数"。覆盖门：`audit-coverage-scan.js --strict` → **126 个写端点 / 118 注解 / 8 带理由豁免 / 缺口 0**。
> - ⚠️ **仍未推进的两项**（避免本节被误读为全绿）：`general.*`（站点名/联系电话等）与 `email.*`（SMTP）**目前只有存储与校验，没有消费方** —— 发信链路未接，站点名也未注入前端标题；`security.twoFactorAuthRequired` 因 TOTP 后端未实现而被**显式拒绝开启**（ISS-102），不是"已支持"。


## 8. 密钥与配置外部化

- 现状：`application.yml` 是全仓唯一配置文件，**无 profile、无 `${VAR:default}` 占位**，DB 口令 `123456` 与 `jwt.secret` 明文（S-04）。
- 目标：`application.yml`（通用）+ `application-dev/test/prod.yml`（环境）+ 全部敏感项走 `${ENV}` 或密钥管理；`[SEC-*]` 密钥一旦入库即视为泄漏，**轮换而非删除**。
- 方案细节见 [ADR-0009](../adr/0009-config-externalization.md)、[deployment-architecture.md §5](deployment-architecture.md)。

## 9. 安全开发检查清单（评审用）

新增/修改接口时逐项确认：

1. [ ] 是否可从令牌推导 `tenantId`，且不接收客户端传入的租户标识？
2. [ ] 是否声明了方法级权限（而不是只靠 `SecurityConfig` 的 URL 前缀）？
3. [ ] 路径规则与控制器 `@RequestMapping` 实际前缀一致？（`S-03` 的成因就是不一致）
4. [ ] 响应 DTO 是否排除凭据、支付、地址、口令散列等字段？
5. [ ] 列表端点是否有分页上限与租户过滤？
6. [ ] 入参是否有 Bean Validation（而非仅 `@RequestParam(required=true)`）？
7. [ ] 失败响应是否只回 `code`+`traceId`，不回内部异常信息？
8. [x] 该操作是否需要审计？（~~身份/权限/金额/库存/单据状态变更一律审计；主数据台账 CRUD 不入~~ → 🔄 **2026-10-09 起口径改为「写端点一律留痕，豁免必须写明理由且只可收缩」，R-37**：客户是 PII、商品重量决定运费与报关、一次导入改写上百行，这三类恰是纠纷最先要查的；噪声来自**读端点**，仍不入审计，用 `action` 过滤而不是"少记一点"来治。金额/库存/单据状态必须审计这半句不变）—— **2026-10-09 起有机器守卫**：`AuditCoverageGuardTest` 覆盖 ~~12~~ **22 个控制器**（含自检负例，防止判定逻辑空转），会在写端点缺 `@Auditable` 且不在白名单（或白名单理由失效、注解总数异常下滑）时失败；另有源码扫描门 `scripts/smoke/audit-coverage-scan.js --strict`（现报 **118/126，豁免 8，缺口 0**），分母来自全量扫描而不是手工清单，不必靠评审记忆
9. [ ] 是否引入新的硬编码密钥、账号、URL？（提交前 `git diff` 自查）
10. [ ] CORS 与安全规则是否覆盖新前端的来源？（dev 端口是 **5110 / 5120**，不是 vite 默认 5173/5174；`SecurityConfig:63` 与两个 `vite.config.js:9` 必须同口径）
11. [ ] 聚合从持久化数据重建时是否走 `restore(...)` 而不是 `create(...)`？（**2026-10-10 新增，R-41 / ISS-125**：`create()` 的语义含"这条记录刚诞生"，重建复用它会把 `createdAt` 伪造成请求时刻，并且随下一次 `save()` 覆盖库里的真实创建时间——审计依赖的正是这一列。 mapper 的 `toDomainEntity` 与仓储里任何"手工再建一个聚合"的地方（已知的第二处是 `UserRepositoryImpl.toDomainRole`）都要逐个检查；回归位 = `TenantDomainRestoreTest` + 常驻探针 `iss125-created-at-probe.sh`）
12. [ ] 涉及时间的断言是否用了**位移相等**判据而不是字符串裸比？（**2026-10-10 新增，ISS-126**：`serverTimezone=UTC` 与 naive `LocalDateTime.now()` 混用，本机实测 API 值 = DB 值 + 28800 s；先拿应用自己写入的一行标定偏移再断言）

## 10. 整改顺序（与安全发现表的对应）

| 阶段 | 内容 | 覆盖编号 |
|---|---|---|
| P0-a | §3 认证链路四合一修复 + 单测（`JwtTokenProvider`/`JwtAuthenticationFilter`/`AuthController`） | S-01、S-02、M-01、M-02（更正）、L-01 |
| P0-b | 凭据外部化 + 密钥轮换 + 去掉口令日志 | S-04、L-06 |
| P0-c | 平台凭据加密与出参剔除 | S-05 |
| P1-a | 事务边界补齐（多表写入原子性） | S-06 |
| P1-b | [migration-plan.md M3](../db/migration-plan.md) → §4 隔离落地 → 删客户端 `tenantId` | M-03、M-04、M-05 |
| P1-c | 方法级鉴权 + refresh 收紧 + 审计日志 | M-06、M-08 |
| P2 | 分页上限、`@Valid` 全量启用、admin-web 接入前置 | M-07、M-10、M-09 |

每完成一项：在 [qa/known-issues.md](../qa/known-issues.md) 关闭对应 `ISS-xxx`，并回改 [security-findings.md](../security-findings.md) 的严重度（不要留下「已修复但仍标 S 级」的过期证据）。
