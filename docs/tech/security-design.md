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
3. 角色命名与 `SecurityConfig:40-41` 的 `hasRole("PLATFORM_ADMIN")` 对齐（`PLATFORM_ADMIN` / `TENANT_ADMIN` / 业务角色），前端守卫按同一集合；~~`AuthGuard.jsx:44-53` 目前把角色检查注释掉了~~ → **✅ 前端守卫已实现**：`user-web/src/components/Auth/AuthGuard.jsx:9` 收 `requiredRoles`，`:33-43` 角色不匹配渲染 403 `Result`（不再放行），同文件 `:51-53` `PlatformAdminGuard` 要求 `PLATFORM_ADMIN`、`:58-60` `TenantAdminGuard` 要求 `TENANT_ADMIN`/`PLATFORM_ADMIN`——**与后端同一套角色名，口径已对齐**。<br>⚠️ 但**仅 user-web 有这两个守卫组件**（admin-web 用 `routes.jsx` 自己的判断），且**前端守卫只是体验层**：真正的门槛是后端的 17 处 `@PreAuthorize` + `SecurityConfig` 路径规则（M-06）。

## 4. 多租户隔离落地（依赖 [migration-plan.md M3](../db/migration-plan.md)）

顺序不能颠倒——**先有列，再过滤，最后去掉客户端传参**：

1. **库层**：18 张业务表加 `tenant_id`（`V4/V5/V6`），唯一键从全局改租户内（`users.username/email`）。<br>🔎 **现状（2026-01-25 查库）：本步骤未完成。** 运行时 42 张表里 23 张有 `tenant_id`、19 张没有（M-04）；`SHOW INDEX FROM users` 的 `username`/`email` 两条唯一索引**仍是单列全局唯一**，未改成 `(tenant_id, username)`。本行是**待办**，不要当成已落地事实引用（另见 [spec/tenant-and-iam.md BR-09](../spec/tenant-and-iam.md)）。
2. **入口层**：`JwtAuthenticationFilter:51` 已有 `TenantContext.setTenantId(tenantId)`，~~但**全仓 `clearTenantId()` 调用点为 0** → 在 `finally` 中清理，否则线程池复用会串租户（M-03）~~ → **✅ 2026-01-25 已在 `finally` 补上 `clearTenantId()`（`:82`），M-03 的 ThreadLocal 串租户风险解除。**
   - 🔄 **本步骤的"读侧"仍未收口，这里是 `TenantContext` 全部 8 个调用点的实测清单**：`JwtAuthenticationFilter:51`（写）、`:82`（清）、`TenantIsolationAspect:57,62`（断言入参与上下文一致）、`:93,95`（仓储切面**只打 trace**）、`OrderController:167,168`（**唯一一个从上下文取值去用的地方**，且只是 `resolveTenantId` 的中间优先级，取不到仍回落 `DEFAULT_TENANT_ID`）。
   - **也就是说：全仓没有任何一条查询从 `TenantContext` 取租户去过滤数据**（这正是 ISS-050 的精确定义）。
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

- 写冒烟用例时**不要**假设"访问 `GET /api/warehouses` 需要 `warehouse:read`"——实际门槛只是 `/api/** authenticated()`；只有 `finance:*`、`user:manage`、`role:manage`、`platform:manage`、5 个 `*:import` 端点带权限点门槛。
- 17 处注解一律是 `hasRole('PLATFORM_ADMIN') or hasAuthority(...)` 的**或**关系：平台管理员永远不看权限点，权限点只对"非平台管理员但被单独授权"的用户有意义——而当前库里只有 `admin` 一个用户且它是平台管理员，**这条或分支从未被真实验证过**。
- 领域层没有任何权限判定入口：`Role.hasPermission(String)`（`domain/tenant/entity/Role.java:131`）**调用点 0**，无 `PermissionService.java` → 按钮级/字段级权限未实现，前端只能靠 `AuthGuard` 的**角色**名（`frontend/user-web/src/components/Auth/AuthGuard.jsx:34` 判角色、`:35-43` 渲染 403）粗粒度控制。

## 6. 管理端（admin-web）接入前置条件

admin-web 一旦接后端就会「无认证 + CORS 拒绝」同时暴露（M-09）。接入前必须完成：

> 🔄 **2026-01-25 复测：本节四条前置条件已完成 2.5 条，admin-web 实际已接入后端。** 逐条对账：
> 1. 🟡 **半完成**：允许来源已加入 admin 源（实测 `SecurityConfig:50` `setAllowedOrigins(List.of("http://localhost:5173","http://localhost:5174"))`，~~`:31`~~ 是旧行号），但**仍是硬编码 `List.of`、没有外部化**（属 S-04 边界，本轮明确不改）；两个 `vite.config.js` 依然**没有 proxy**，所以同源方案没做、目前靠 CORS 白名单跑通。
> 2. ❌ **未做**：admin-web 有**自己一份** `context/AuthContext.jsx`（与 user-web 重复实现，非复用、也未抽公共包），这是 [frontend-architecture.md §10](frontend-architecture.md) 记的重复项。
> 3. ✅ 已做且形态正确：`UserController:31`、`RoleController:31`、`PlatformIntegrationController:46` 都是**类级 `@PreAuthorize`**（不依赖 URL 前缀）；⚠️ 唯一仍靠前缀规则的是 `TenantController`——它**没有任何注解**，只由 `SecurityConfig:66` 的 `/api/tenants/** → hasRole(PLATFORM_ADMIN)` 兜住，这正是本节原本要求避免的形态。
> 4. ✅ **已完成**：~~删除 mock `authApi`（`admin-web/src/services/api.js:165-185`，写死 `admin/admin123`）~~ → 该文件现在**只有 143 行**，mock `authApi` 已不存在；`authApi:69`、`tenantApi:77`、`userApi:90`、`roleApi:102` 均为真实 HTTP。<br>❌ 但**冒名换了一个对象**：`platformApi:116-143` 是**硬编码桩**（`getSystemConfig` 直接返回写死的对象，`saveSystemConfig:137` 只 `console.log(:138)`）——admin-web 5 页里 `Login/TenantList/UserList` 3 页真实、`PlatformDashboard`（无 import）与 `SystemConfig`（有 import、**0 个调用点**）2 页仍假，即 ISS-026 的「3 真 2 假」。

## 7. 数据保护

| 项 | 现状 | 目标 |
|---|---|---|
| 平台凭据 | `api_key`/`api_secret` 明文入库 + 原样返回（S-05） | 信封加密（KMS/主密钥）存 `*_enc`；出参永不返回 secret，只返回 `hasCredential: true` 与掩码 `ak_****1234`；轮换与撤销接口 |
| 支付/地址信息 | 事件载荷内嵌（`OrderCreatedEvent:23-25`）、实体直接序列化 | 出参 DTO 白名单；事件只带 ID 与必要字段；`payment_infos.transaction_id` 等按需脱敏日志 |
| 口令 | `BCryptPasswordEncoder`（~~`SecurityConfig:57-58`~~ → 实测在 `SecurityConfig:82-83`）✅ 正确 | 保持；`DataInitializationConfig:134`（~~`:64`~~）的 `123456` 改为**首次登录强制改密**或随机口令+一次性输出 |
| 默认数据 | 无租户时建默认租户 + admin（`initializeDefaultTenantAndUser`，实测 `:115-140`，入口在 `:106`；~~`:48-50` 幂等~~） | 保留初始化但**禁止打印口令**（实测 `:145` `logger.info("默认密码: 123456")` + `:146`，~~`:75`~~），并加环境变量开关 `app.init.demo-data=false` |
| 异常信息 | `e.getMessage()` 拼进响应（M-08） | 对外只给 `code` + `traceId`，堆栈仅落日志，见 [error-codes.md §5](../api/error-codes.md) |
| 审计 | 无操作审计（只有 `createdAt/updatedAt`，且 `updated_at` 不会自动刷新，R-04） | 新增 `audit_logs`（actor、tenant、action、target、ip、traceId、结果），覆盖 §5 全部提权面端点 |

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
8. [ ] 该操作是否需要审计？（身份/权限/金额/凭据变更一律审计）
9. [ ] 是否引入新的硬编码密钥、账号、URL？（提交前 `git diff` 自查）
10. [ ] CORS 与安全规则是否覆盖新前端的来源？

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
