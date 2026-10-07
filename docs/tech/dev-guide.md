# 开发上手指南（Onboarding）

> 定位：**第一天就能跑起来**。面向新加入的后端/前端/全栈同学，只讲「怎么搭、怎么跑、怎么验、会踩什么坑」。
> 与其他文档的分工：架构为什么长这样看 [architecture.md](../architecture.md)；写代码要守的规矩看 [backend-standards.md](backend-standards.md)；生产化与运维看 [deployment.md](../deployment.md) 和 [deployment-architecture.md](deployment-architecture.md)。
> 事实基线：2026-01-25 工作树。所有版本号、命令、端口、坑位均按代码实测。

## 1. 工具链要求

| 工具 | 版本 | 校验 | 说明 |
|---|---|---|---|
| JDK | **17** | `java -version` | `backend/pom.xml` `java.version=17`；用 21 会因 source/target 17 编译但运行期差异产生误导 |
| Maven | 3.6+ | `mvn -v` | ⚠️ **无 `mvnw`**（全仓 find 无 wrapper），必须本机安装；版本随人走是本项目的隐性风险 |
| MySQL | 8.x | `mysql --version` | `MySQL8Dialect`（`application.yml`）；库名 `ecommerce_logistics_erp` |
| Node.js | ≥ 18 | `node -v` | Vite 7 要求；实测 v24 可用 |
| npm | 随 Node | `npm -v` | 两个前端各有 `package-lock.json`（lockfileVersion 3） |
| Docker | 20.10+ | `docker -v` | 仅为跑 RocketMQ；不跑也能开发（见 §4） |

IDE 建议：IntelliJ IDEA（打开 `backend/` 为 Maven 项目，**不要**把仓库根当 Maven 工程导入——根目录无 `pom.xml`）；前端用 VS Code / WebStorm。仓库当前**没有** `.editorconfig`、没有 `.vscode/`、没有 git hook，格式一致靠自觉。

## 2. 一次性准备

```bash
# 1) 建库（不需要手工建表）
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS ecommerce_logistics_erp \
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 2) 后端编译
cd backend && mvn clean compile

# 3) 前端依赖（两个 app 是彼此独立的 npm 项目，无 workspace）
cd ../frontend/user-web  && npm install
cd ../frontend/admin-web && npm install
```

两个 lockfile 的历史遗留问题（不影响安装，但会影响你的 diff 噪音）：`user-web` 与 `admin-web` 的 `package-lock.json` 顶层 `"name"` 都写成 `frontend`（历史上在 `frontend/` 根目录生成），且 registry 分别指向 `r.cnpmjs.org` 与 `mirrors.tencent.com`。**换源后请整体重新生成 lock，不要只改一两行。**

## 3. 日常启动（顺序有意义）

```bash
# 后端（需 MySQL 已就绪）
cd backend && mvn spring-boot:run        # http://localhost:8082

# 前端（另开终端）
cd frontend/user-web && npm run dev      # http://localhost:5173
```

首次启动后端时，`DataInitializationConfig`（`CommandLineRunner`）会在**租户表为空**时创建默认租户和管理员账号；对**已存在的库**它还会再做两件幂等的事——建/补齐系统角色 `PLATFORM_ADMIN`（`ensurePlatformAdminRole`，`:159-177`，挂 26 个权限点，2026-10-07 实测；补齐条件已改为按集合 `containsAll` 而非数量）和把该角色分给 `admin`（`ensureAdminHasPlatformAdminRole`，`:182-201`）：

| 用户名 | 口令 | 来源 |
|---|---|---|
| `admin` | `123456` | `DataInitializationConfig.java:64,67`（且 `:75` 在启动日志明文打印密码） |

> ⚠️ 2026-01-25 实测教训（**ISS-061**）：上面那两个后步骤此前**每次都静默失败**——它们在没有会话的线程上触碰 `@ElementCollection` 懒集合（`RoleJpaEntity.permissions`、`UserJpaEntity.roleIds`；OSIV 只对 Web 请求生效，`CommandLineRunner` 不在范围内），抛 `LazyInitializationException` 后被 `catch (Exception e) { logger.error(...) }` 吞掉，启动日志看着一切正常，但 `admin` 从此没有 `PLATFORM_ADMIN`，于是 `hasRole(PLATFORM_ADMIN)` 的接口全 403、admin-web 的 `AdminGuard` 也把人挡在外面。
> **自检方式**：启动日志必须出现「已为用户 admin 分配角色 PLATFORM_ADMIN」；或解 JWT 看 `roles` 是否含 `PLATFORM_ADMIN`（2026-10-07 实测为 `[ADMIN, PLATFORM_ADMIN]` + **26** 个权限点，其中 `shop:read`/`shop:write`/`shop:credential_write`/`shop:sync` 四项来自 ADR-0013）。修法见 `UserRepositoryImpl`/`RoleRepositoryImpl` 读方法的 `@Transactional(readOnly = true)` 与 `RoleMapper` 的防御性拷贝。
> ⚠️ 同族的第二个静默失败形态（**ISS-090**，2026-10-07）：`Role.PERMISSION_PATTERN`（`domain/tenant/entity/Role.java:45`）只允许**一个冒号**，往 `PermissionPoints` 里加 `shop:credential:write` 这种三段名会让 `resetPermissions()` 整批回滚，同样被那个 `catch (Exception e)` 吞成一条 ERROR 日志——启动照常、测试全绿，但所有角色停在旧清单、新权限点恒 403。**加权限点后必须解一次 JWT 数权限条数**，或跑 `TenantParameterGuardTest`（它断言 catalog 每项都过 `Role.validatePermission()`，且 `@PreAuthorize` 引用的每点在 catalog 里）。

该初始化是**幂等**的（`:48-50` 判 `findAll()` 为空才建），已有数据时不会重置密码——想恢复默认口令需手工删租户或改库。

自检（不依赖前端，绕开 §7 的登录坑）：

```bash
curl -s http://localhost:8082/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
# 期望：{"code":"200","message":"success","data":{"token":"eyJ..."}}
# 注意 code 是字符串 "200"，不是数字 —— 写断言/前端判定时别搞错（Result.java:18）
```

带令牌调用：

```bash
TOKEN=$(curl -s http://localhost:8082/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s "http://localhost:8082/api/products?page=0&size=20&tenantId=default-tenant" \
  -H "Authorization: Bearer $TOKEN"
```

Swagger UI：`http://localhost:8082/swagger-ui.html`，OpenAPI JSON：`/v3/api-docs`（分组 `public-api`）。⚠️ `SecurityConfig:39-42` 没有显式放行这两个路径、也没有 `.anyRequest()` 兜底（L-01），**能否打开取决于实际安全链，未运行验证过**；打不开时先查这里，不要怀疑 springdoc 依赖（依赖是齐的，**20 个控制器 `@Tag`、196 个方法 `@Operation`**，覆盖率 100%——实测 `@Operation` 与 `@(Get|Post|Put|Delete|Patch)Mapping` 数量相等）。

## 4. RocketMQ（可选项，但别误解它）

```bash
cd scripts/docker && docker compose up -d   # namesrv:9876 / broker:10909,10911,10912 / proxy:8088,8089
```

`scripts/docker/docker-compose.yml` **只定义 RocketMQ 三件套**，不含 MySQL、不含应用容器。

- 不启动 MQ 也能开发：发送失败只记 error 日志（`DomainEventPublisher:74-76`），主流程不受影响。
- **但不要因为 compose 起来了就以为拥有异步解耦**（2026-01-25 更新：装配层已修，链路仍不通）。已修：`RocketMQConfig.java:24-27` 现为 `@ConditionalOnProperty(prefix="rocketmq", name="enabled")` + `@Import(RocketMQAutoConfiguration.class)`——该 starter 2.2.3 只把自动配置写在 `META-INF/spring.factories`，Boot 3 不再读该文件，不显式 `@Import` 就**静默不装配**；配置已归位顶层 `rocketmq.`（`application.yml:36-49`）；4 个消费者已是独立顶层类（ISS-034）。仍卡：事件无 `@JsonCreator` → 消费端反序列化失败（ISS-052）；与 `EventHandler` 双通道重复副作用（ISS-054）；且 `rocketmq.enabled=true` 时**没有 broker 就起不来**，本机纯跑代码请置 `false`。详见 [architecture.md §3](../architecture.md)。

## 5. 测试与静态检查

| 命令 | 结果预期 | 说明 |
|---|---|---|
| `cd backend && mvn test` | ✅ **实测绿灯** | 2026-03-08 复测：`BUILD SUCCESS`，**31 个测试类 / 211 个 `@Test` / 0 failures / 0 errors / 0 skipped**（2026-01-25 那档为 26 类 / 164 例）。旧「5 类系统性问题」需就地更正：int/String 那一条并不导致失败——JsonPath 会把期望值强转成实际值类型，故 `value(200)` 对 `code="200"` 同样通过。⚠️ 真正的局限是层次：164 例全部不起 Spring 上下文、不连库，本轮 3 个真实缺陷（ISS-047/048/049）恰是**连库才暴露、纯单测当时全绿**。逐项见 [testing.md §3](../testing.md) |
| `mvn -Dtest=OrderControllerTest test` | 单类调试 | standalone MockMvc，不起 Spring 上下文、不连库 |
| `cd frontend/user-web && npm run lint` | 待实测 | ESLint 9 flat config（两 app 配置逐字节相同），覆盖 `**/*.{js,jsx}` |
| `cd frontend/*-web && npm run build` | 输出 `dist/` | `vite.config.js` 只有 `plugins:[react()]`，**无任何 build 配置** |
| 前端 `npm test` | **不存在** | 两个 app 均无 vitest/jest/testing-library；接入方案见 [testing.md §6](../testing.md) |

**新人常见误判**：`mvn test` 红了不要先去改业务代码。当前红灯多为测试自身写法错误，不是产品缺陷。判断顺序：① 是否 §testing.md 已列的 5 类问题 → ② 才是真实回归。

## 6. 配置怎么改

`backend/src/main/resources/application.yml` 是**全仓唯一配置文件**——没有 `application-dev.yml`、没有 profile、没有任何 `${VAR:default}` 占位。

| 想做的事 | 当前做法（临时） | 正确做法（目标态） |
|---|---|---|
| 换数据库地址/口令 | 直接改 yml（不要提交） | 环境变量注入，见 [ADR-0009](../adr/0009-config-externalization.md) |
| 本地不自动改表结构 | 改 `ddl-auto: update`→`none`（会立刻看不到新字段） | 迁移工具，见 [migration-plan.md](../db/migration-plan.md) |
| 关掉刷屏 SQL | 改 `show-sql: false` | 日志 profile，见 [observability.md](observability.md) |
| 前端指向联调环境 | 改 `src/services/api.js:6` 的 baseURL（**硬编码**，无 `.env`） | `import.meta.env.VITE_API_BASE_URL`，见 [frontend-architecture.md §4](frontend-architecture.md) |

⚠️ 提交纪律：**不要把 `jwt.secret`、DB 口令、指向个人环境的 URL 提交进 Git**。这三项已经在本仓发生过（S-04），不要加重。

## 7. 已知坑（实测存在于代码，不是猜测）

| # | 坑 | 现象 | 证据 / 规避 |
|---|---|---|---|
| 1 | ~~**前端登录写不进 token**~~ → ✅ 已修 | ~~页面点登录「没反应」~~ | 原双重解包问题已消除：拦截器 `return response.data.data`（`user-web/src/services/api.js:42`）之后，`AuthContext.jsx` 直接按 `responseData.token/refreshToken/username/tenantId/roles` 取值并落 localStorage（`:74-84`），登录后再 `refreshProfile()` 拉 `/auth/me` 补权限明细（`:93`）。2026-01-25 实测：`admin/123456` 登录后 `roles=[ADMIN, PLATFORM_ADMIN]`、22 个权限点齐 |
| 2 | ~~**列表页数据是假数据**~~ → 🟡 大幅收敛 | 只剩 3 个页面是假数据 | ~~34 个页面中仅 9 个有真实 `*Api.` 调用；25 个用 `Array.from({length:20})+Math.random()`~~ → 2026-01-25 逐页复测：**45 个页面里 42 个**直调 `*Api.*`；未接的 3 个是 `pages/home/Home.jsx`（`:34-59` 硬编码 KPI）、`pages/platform/PlatformDashboard.jsx`（`:20,64`）、`pages/platform/SystemConfig.jsx`（`:3` import 了 `platformApi` 却不调用，`:16-41` 自拼 `mockConfig`）。逐页清单见 [frontend-architecture.md §7](frontend-architecture.md) |
| 3 | ~~**admin-web 完全不联后端**~~ → ✅ 已修（一处除外） | 3/5 页面走真实接口 | ~~`admin-web/src/services/api.js` 不 import axios；`authApi` 写死 `admin/admin123` 返回 `'mock-jwt-token'`（`:165-185`）~~ → 现为 axios 实例 + 5 个模块 33 个方法（`authApi:69`、`tenantApi:77`、`userApi:90`、`roleApi:102`、`platformApi:116-143`），Login/TenantList/UserList 真实调用，退出也已实调（`AuthContext.jsx:72-81` → `POST /auth/logout`）。⚠️ **唯一残留的桩是 `platformApi` 本身**（`:116-143` 硬编码返回 `siteName: '物流ERP系统'` 等），所以 `SystemConfig.jsx` 看着"有数据"也是假的（ISS-026） |
| 4 | ~~**admin-web 联调必 403**~~ → ✅ CORS 已放行 | 5174 可用 | ~~`SecurityConfig.java:31` 只放行 `http://localhost:5173`~~ → 现 `:47-55` 同时允许 5173 与 5174。⚠️ 两个 `vite.config.js` **仍然没有 proxy**（2026-01-25 复测：两份配置里没有 `server:` 段），所以是"跨域直连 8082"而不是同源反代——`baseURL` 仍写死（见 ISS-030） |
| 5 | **端口不是配出来的** | 有时前端起在 5175 | 无 `server.port` 配置，5173/5174 是 Vite 默认端口被占后自动递增的结果 |
| 6 | ~~**鉴权形同虚设**~~ → ✅ 已修 | ~~随便塞个 `Authorization: Bearer xxx` 也能调~~ | ~~`validateToken()` 恒 `true`（`:126-128`）、`getUsernameFromToken()` 恒 `""`、`getTenantIdFromToken()` 恒 `"default-tenant"`~~ → `validateToken` 现为 `parseClaims(token) != null`（`JwtTokenProvider.java:189-191`），两个 getter 解析失败返回 null（`:130-133,141-144`）。2026-01-25 实测：伪造 token 与不带 token 都被拒（**403 空 body**，注意不是 401——`SecurityConfig` 没配 `exceptionHandling()`，见 ISS-032/ISS-058） |
| 7 | ~~**`/api/tenants/**` 恒 403**~~ → ✅ 已修（两段根因） | 租户管理可用 | ① authorities 恒空（角色填充被注释）已由 ISS-041 修复：token 携带 `roles`/`permissions` claim，`JwtAuthenticationFilter` 构造注入并按 `ROLE_` 前缀重建（`:59-60`）；② 2026-01-25 又查出第二段——`admin` 其实**从未被分配 `PLATFORM_ADMIN` 角色**，因为种子初始化在无会话线程触碰 `@ElementCollection` 懒集合抛 `LazyInitializationException` 而被 `catch` 吞掉（**ISS-061**，已修）。实测 `GET /api/tenants` 现 **200** |
| 8 | **`tenantId` 由客户端传** | 可为任意租户写数据 | `OrderController:45` 等 `@RequestParam(defaultValue="default-tenant")`；`LogisticsOrderMapper:52-54`、`WarehouseMapper:54-56` 反射硬编码 `"default-tenant"` |
| 9 | ~~**多表写入不原子**~~ → ✅ 已修 | ~~全仓 `@Transactional` 0 处（S-06）~~ | 2026-01-25 实测 **110 处**（application 90 / infrastructure 20 / domain 0 / interfaces 0）。⚠️ 遗留：`rollbackFor` **0 处**（checked 异常不回滚）、`readOnly` 只有 **7 处**（在 `UserRepositoryImpl`/`RoleRepositoryImpl`，为 ISS-061 补的）→ 见 ISS-036/ISS-042 |
| 10 | ~~**无参构造的 `new JwtTokenProvider()`**~~ → ✅ 已修 | ~~改 `jwt.secret` 不生效~~ | `JwtAuthenticationFilter` 改为构造注入（`:31,33`），不再有自建实例；`SecurityConfig` 也不再 `@Bean` new 一次（M-01 已收敛）。密钥本身仍明文在 `application.yml`（ISS-021，按边界保持未修） |

## 8. 端口与路径速查

| 服务 | 地址 | 配置位置 |
|---|---|---|
| 后端 API | `http://localhost:8082/api/**` | `application.yml` `server.port=8082`，无 `context-path` |
| Swagger | `http://localhost:8082/swagger-ui.html` | `application.yml:41-52` |
| user-web | `http://localhost:5173` | Vite 默认（未显式配置） |
| admin-web | `http://localhost:5174` | Vite 默认递增（未显式配置） |
| MySQL | `localhost:3306/ecommerce_logistics_erp`（宿主机发布端口，容器内仍为 3306） | `application.yml` |
| RocketMQ | nameServer `localhost:9876` | compose + `application.yml:29-39`（死配置） |

## 9. 改代码的正确姿势

1. **读规矩**：后端 [backend-standards.md](backend-standards.md)，前端 [frontend-architecture.md §9](frontend-architecture.md)。
2. **找现状**：模块规格在 `docs/spec/{module}.md`（含状态机与 `BR-xx` 业务规则）；端点契约以 [api/api.md](../api/api.md) 为唯一权威；表语义以 [db/data-dictionary.md](../db/data-dictionary.md) 为唯一权威。
3. **改代码**：改 JPA 实体必须同步 `docs/db/schema.sql`（schema 由 `ddl-auto: update` 生成，无迁移工具，文档是唯一评审载体）。
4. **同步文档**：新增/变更端点 → 更新 `api/api.md` 端点表 + 对应 `spec/*.md` 的接口对照；发现缺陷 → 在 [qa/known-issues.md](../qa/known-issues.md) 立项 `ISS-xxx`。触发时机见 [docs/README §4](../README.md)。
5. **提 PR 前**：过 [backend-standards.md §11](backend-standards.md) 自检清单。
6. **重大技术选择**：写 ADR（模板在 [adr/README.md](../adr/README.md)）。编号请顺延 `0014+`（🔄 2026-10-06：`0013-shop-entity-and-platform-connection-split.md` 已落地，0013 已占用）——`0001~0005` 是产品线选型决策、`0002`/`0006~0012` 是工程线现状与差距，两套编号已在同一目录共存，新增时**先查 [adr/README.md](../adr/README.md) 索引避免撞号**。

## 10. 求助路径

| 问题类型 | 先看 |
|---|---|
| 「这个项目到底做到哪了」 | [product/03-feature-map.md](../product/03-feature-map.md) 状态矩阵、[product/05-core-business-flows.md](../product/05-core-business-flows.md) 流程断点 |
| 「这个接口应该返回什么」 | [api/api.md](../api/api.md) §2 信封 + §4 端点表 |
| 「为什么登录/鉴权不生效」 | [security-design.md](security-design.md) + [security-findings.md](../security-findings.md) S-01/S-02/M-01/M-02 |
| 「这字段为什么是这个类型」 | [db/data-dictionary.md](../db/data-dictionary.md) §3/§4（枚举三套口径） |
| 「能不能演示给客户看」 | [qa/smoke-test-checklist.md](../qa/smoke-test-checklist.md) §6 三档判定 |
| 「需求什么时候做」 | [product/07-roadmap.md](../product/07-roadmap.md)（`R-1xx/2xx/3xx` 编号） |
