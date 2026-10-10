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

🔄 **2026-10-09 订正**：本节此前让人手工 `mysql -e "CREATE DATABASE …"`，并说"业务表结构由 Hibernate 建"——库这一步现在由 compose 做了（`scripts/docker/mysql/init/01-init.sql` 在容器**首次**启动时建库 + 建 `root@'%'` 授权；不补授权的话宿主机 Spring Boot 连入会在握手阶段被断，表现是 `Communications link failure`）。

```bash
# 1) 拉起依赖（MySQL + RocketMQ 三件套，后台）
cd scripts/docker && ./erp-services.sh start
./erp-services.sh status     # 期望 MySQL 健康: healthy

# 2) 后端编译
cd ../../backend && mvn clean compile

# 3) 前端依赖（两个 app 是彼此独立的 npm 项目，无 workspace）
cd ../frontend/user-web  && npm install
cd ../frontend/admin-web && npm install
```

`erp-services.sh` 的子命令：`start` / `stop`（保留数据）/ `restart` / `status` / `ps` / `logs [mysql|rmqnamesrv|rmqbroker]` / `down`（删容器保留卷）/ `reset`（**清空所有持久化数据，慎用**）。

> ⚠️ **端口冲突的处理方式**：compose 里 MySQL 的宿主机端口是可覆盖的（`ERP_MYSQL_HOST_PORT`，默认 3306）。本机若已被别的项目占了 3306，用 `ERP_MYSQL_HOST_PORT=3307 ./erp-services.sh start` 并存，后端侧对应 `SPRING_DATASOURCE_URL` 或同名 `ERP_MYSQL_HOST_PORT`（`application.yml:26`）——**不要**为此停掉无关容器，也**不要**改 `application.yml` 里的默认值。
> ⚠️ `ERP_MYSQL_HOST_PORT` 只在**容器创建时**生效一次；数据卷已初始化后改 `MYSQL_ROOT_PASSWORD`/`MYSQL_ROOT_HOST` 会被 MySQL 入口脚本忽略，需 `reset` 重来或手工授权（见 `01-init.sql` 注释）。

两个 lockfile 的历史遗留问题（不影响安装，但会影响你的 diff 噪音）：`user-web` 与 `admin-web` 的 `package-lock.json` 顶层 `"name"` 都写成 `frontend`（历史上在 `frontend/` 根目录生成），且 registry 分别指向 `r.cnpmjs.org` 与 `mirrors.tencent.com`。**换源后请整体重新生成 lock，不要只改一两行。**

## 3. 日常启动（顺序有意义）

```bash
# 后端（需 MySQL 已就绪）
cd backend && mvn spring-boot:run        # http://localhost:8082

# 前端（另开终端；端口在 vite.config.js 里写死，不是 Vite 默认的 5173）
cd frontend/user-web  && npm run dev     # http://localhost:5120
cd frontend/admin-web && npm run dev     # http://localhost:5110
```

> 🔑 **平台凭据主密钥（涉及店铺凭据时必须注入）**：`PLATFORM_CREDENTIAL_MASTER_KEY` 是 AES-256-GCM 的主密钥（`application.yml:66` → `CredentialCipher:40-44`）。**未注入时后端照常启动并 WARN，但平台凭据以明文入库**（即 ISS-011 未关闭）。本机开发可以用任意口令派生（非 32 字节 Base64 时按 SHA-256 派生，`CredentialCipher:120`），生产环境必须注入随机 32 字节的 Base64：
> ```bash
> PLATFORM_CREDENTIAL_MASTER_KEY="$(openssl rand -base64 32)" mvn spring-boot:run
> ```
> 密钥**不要**写进 `application.yml`，也不要提交；换密钥会让已存密文解不开（`CredentialCipher:83` 直接抛 `IllegalStateException`）。

首次启动后端时，`DataInitializationConfig`（`CommandLineRunner`）会在**租户表为空**时创建默认租户和管理员账号；对**已存在的库**它还会再做两件幂等的事——建/补齐系统角色 `PLATFORM_ADMIN`（`ensurePlatformAdminRole`，挂 26 个权限点，补齐条件按集合 `containsAll` 而非数量）和把该角色分给 `admin`（`ensureAdminHasPlatformAdminRole`）：

| 用户名 | 口令 | 来源 |
|---|---|---|
| `admin` | `app.seed.default-tenant-admin-password`，未注入时退到 `123456` | `DataInitializationConfig.java:51`。🔄 启动日志**不再打印口令明文**，`:226` 只打「口令来源: app.seed.default-tenant-admin-password（生产环境请立即改密）」 |

> ⚠️ **种子租户的 ID 是随机 UUID，不是字符串 `default-tenant`**。照旧文档抄 `?tenantId=default-tenant` 去列表接口会拿到空结果或误路由租户作用域——带租户参数的探针要么先查真实 ID，要么**不传**（登录租户即作用域，`GET /api/warehouses` 这类列表在缺省时收敛到登录租户）。

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
curl -s "http://localhost:8082/api/products?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
# 🔄 2026-10-09 订正：旧示例带 &tenantId=default-tenant，该值不存在（种子租户是 UUID），
# 照抄会查不到数据。列表端点在缺省 tenantId 时收敛到登录租户（ISS-092/050 的收口）。
```

Swagger UI：`http://localhost:8082/swagger-ui.html`，OpenAPI JSON：`/v3/api-docs`（分组 `public-api`）。⚠️ `SecurityConfig:39-42` 没有显式放行这两个路径、也没有 `.anyRequest()` 兜底（L-01），**能否打开取决于实际安全链，未运行验证过**；打不开时先查这里，不要怀疑 springdoc 依赖（依赖是齐的，**20 个控制器 `@Tag`、196 个方法 `@Operation`**，覆盖率 100%——实测 `@Operation` 与 `@(Get|Post|Put|Delete|Patch)Mapping` 数量相等）。

## 4. RocketMQ（可选项，但别误解它）

```bash
cd scripts/docker && ./erp-services.sh start   # 或 docker compose up -d
# MySQL 3306（可 ERP_MYSQL_HOST_PORT 覆盖）/ namesrv:9876
# broker:10909,10911,10912 + 8080,8081（RocketMQ 5 的 gRPC/proxy 端口，实测 docker ps 口径）
```

🔄 **2026-10-09 订正**：`scripts/docker/docker-compose.yml` 现在定义 **`mysql` + `rmqnamesrv` + `rmqbroker` 三个服务**（旧文写"只定义 RocketMQ 三件套、不含 MySQL"已过期）；仍**不含应用容器**，后端要本机 `mvn spring-boot:run` 起。MySQL 服务带 `mysql-data` 命名卷与 `./mysql/init` 初始化目录、`mysqladmin ping` 健康检查，所以 §2 不必再手工建库。

- 不启动 MQ 也能开发：发送失败只记 error 日志（`DomainEventPublisher:74-76`），主流程不受影响。
- **但不要因为 compose 起来了就以为拥有异步解耦**（2026-01-25 更新：装配层已修，链路仍不通）。已修：`RocketMQConfig.java:24-27` 现为 `@ConditionalOnProperty(prefix="rocketmq", name="enabled")` + `@Import(RocketMQAutoConfiguration.class)`——该 starter 2.2.3 只把自动配置写在 `META-INF/spring.factories`，Boot 3 不再读该文件，不显式 `@Import` 就**静默不装配**；配置已归位顶层 `rocketmq.`（`application.yml:36-49`）；4 个消费者已是独立顶层类（ISS-034）。仍卡：事件无 `@JsonCreator` → 消费端反序列化失败（ISS-052）；与 `EventHandler` 双通道重复副作用（ISS-054）；且 `rocketmq.enabled=true` 时**没有 broker 就起不来**，本机纯跑代码请置 `false`。详见 [architecture.md §3](../architecture.md)。

## 5. 测试与静态检查

| 命令 | 结果预期 | 说明 |
|---|---|---|
| `cd backend && mvn test` | ✅ **实测绿灯** | 🔄 **2026-10-09 复测：`BUILD SUCCESS`，80 个测试类 / 656 例 / 0 failures / 0 errors / 0 skipped**（历史链：26 类/164 例 → 31/211 → 69/575 → 75/628 → 77/638 → 78/643 → 79/650 → **80/656**）。⚠️ **必须 `cd backend` 后运行**，且汇总口径按 `target/surefire-reports/*.xml` 逐文件统计（脚本 `scripts/smoke/sum-surefire.js`）——`mvn … test \| tail` 会把退出码吞掉（管道取的是最后一个命令的码），出现过"构建实际失败但 EXIT=0"的假绿。旧「5 类系统性问题」需就地更正：int/String 那一条并不导致失败——JsonPath 会把期望值强转成实际值类型，故 `value(200)` 对 `code="200"` 同样通过。⚠️ 真正的局限仍是层次：单测大部分不起 Spring 上下文、不连库，历轮真实缺陷（ISS-047/048/049、以及本轮的 ISS-114/115/116）都是**连库跑 HTTP 才暴露**。逐项见 [testing.md §3](../testing.md) |
| `node scripts/smoke/sum-surefire.js` | 汇总上一轮 `mvn test` | 输出形如 `classes=80 tests=656 failures=0 errors=0 skipped=0`；这是在 `backend/` 目录下跑 |
| `scripts/smoke/smoke.sh` | ✅ **198 通过 / 0 失败 / 0 跳过** | 对 **live 后端**（8082 + 真实 MySQL）跑的业务冒烟，不是单测；判据口径见 [smoke-test-checklist.md §6](smoke-test-checklist.md) |
| `scripts/smoke/security-slice-smoke.sh` | ✅ **43 通过 / 0 失败** | 安全切片（配置往返、JWT 有效期、登录锁定、审计双向留痕、跨租户列表、收尾复原） |
| `mvn -Dtest=OrderControllerTest test` | 单类调试 | standalone MockMvc，不起 Spring 上下文、不连库。⚠️ 多类用**逗号**分隔（`-Dtest='A,B'`），写成 `A+B` 会让 surefire 报 "No tests matching pattern" 并 BUILD FAILURE |
| `cd frontend/user-web && npx eslint .` | ✅ **实测：0 error / 8 warning，退出码 0** | 🔄 2026-10-09 复测（原记"待实测"）。8 条**全是** `react-hooks/exhaustive-deps`（如 `WarehouseList.jsx:46` 的 `pagination`），属提示性而非缺陷；ESLint 9 flat config，两个 app 的 `eslint.config.js` 实测**逐字节相同**（`cmp -s` 通过），覆盖 `**/*.{js,jsx}` |
| `cd frontend/admin-web && npx eslint .` | ✅ **实测：无任何输出，退出码 0** | admin-web 比 user-web 更干净：0 error 0 warning |
| `cd frontend/*-web && npm run build` | 输出 `dist/` | `vite.config.js` 只有 `plugins:[react()]`，**无任何 build 配置** |
| 前端 `npm test` | **不存在** | 两个 app 均无 vitest/jest/testing-library；接入方案见 [testing.md §6](../testing.md) |

**新人常见误判**：`mvn test` 红了不要先去改业务代码。当前红灯多为测试自身写法错误，不是产品缺陷。判断顺序：① 是否 §testing.md 已列的 5 类问题 → ② 才是真实回归。

## 6. 配置怎么改

`backend/src/main/resources/application.yml` 是**全仓唯一配置文件**——没有 `application-dev.yml`、没有任何 profile（`ls src/main/resources` 只有 `application.yml`）。

🔄 **2026-10-09 订正**：旧文断言"没有任何 `${VAR:default}` 占位"已过期——ADR-0009 的外置化已落了 **6 个占位**，都带可用默认值，所以本机什么都不注入也能起：

| 环境变量 | 位置 | 默认值 | 备注 |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | `:26` | `jdbc:mysql://localhost:3306/ecommerce_logistics_erp?...` | 整串可覆盖 |
| `ERP_MYSQL_HOST_PORT` | `:26` 内嵌 | `3306` | 与 compose 的同名变量配对使用（3306 被占时两处一起改） |
| `ERP_DB_USERNAME` / `ERP_DB_PASSWORD` | `:27,:28` | `root` / `123456` | ⚠️ 默认值是开发口令 |
| `PLATFORM_CREDENTIAL_MASTER_KEY` | `:66` | **空** | 空 ⇒ 平台凭据明文入库并 WARN（见 §3 的🔑框），涉及店铺凭据必须注入 |
| `SHOPIFY_API_VERSION` | `:70` | 空 | |
| `JWT_SECRET` | `:119` | 一串 256 位以上的内联默认密钥 | 🔴 **这就是 ISS-021 的登记项**：默认密钥随仓库公开，等于任何拿到源码的人都能签出合法令牌。生产**必须**注入，且本仓已把它保留为默认值这件事记在 [known-issues.md](../qa/known-issues.md) / [security-findings.md](../security-findings.md)，不要误读成"已收口" |

| 想做的事 | 当前做法（临时） | 正确做法（目标态） |
|---|---|---|
| 换数据库地址/口令 | 🔄 **不用改 yml 了**：`ERP_DB_*` / `SPRING_DATASOURCE_URL` 直接注入 | 部署侧全部环境变量注入，见 [ADR-0009](../adr/0009-config-externalization.md) |
| 本地不自动改表结构 | 改 `ddl-auto: update`→`none`（会立刻看不到新字段） | 迁移工具，见 [migration-plan.md](../db/migration-plan.md)（按决策当前仍无 Flyway） |
| 关掉刷屏 SQL | 改 `show-sql: false` | 日志 profile，见 [observability.md](observability.md) |
| 前端指向联调环境 | 改 `api.js` 的 baseURL（**硬编码**，两 app 均无 `.env`）：`user-web/src/services/api.js:48`、`admin-web/src/services/api.js:6`，两处值都是 `http://localhost:8082/api` | `import.meta.env.VITE_API_BASE_URL`，见 [frontend-architecture.md §4](frontend-architecture.md) |

⚠️ 提交纪律：**不要把 `jwt.secret`、DB 口令、指向个人环境的 URL 提交进 Git**。这三项已经在本仓发生过（S-04），不要加重。

⚠️ 提交纪律：**不要把 `jwt.secret`、DB 口令、指向个人环境的 URL 提交进 Git**。这三项已经在本仓发生过（S-04），不要加重。

## 7. 已知坑（实测存在于代码，不是猜测）

| # | 坑 | 现象 | 证据 / 规避 |
|---|---|---|---|
| 1 | ~~**前端登录写不进 token**~~ → ✅ 已修 | ~~页面点登录「没反应」~~ | 原双重解包问题已消除：拦截器 `return response.data.data`（`user-web/src/services/api.js:42`）之后，`AuthContext.jsx` 直接按 `responseData.token/refreshToken/username/tenantId/roles` 取值并落 localStorage（`:74-84`），登录后再 `refreshProfile()` 拉 `/auth/me` 补权限明细（`:93`）。2026-01-25 实测：`admin/123456` 登录后 `roles=[ADMIN, PLATFORM_ADMIN]`、22 个权限点齐 |
| 2 | ~~**列表页数据是假数据**~~ → 🟡 大幅收敛 | 只剩 3 个页面是假数据 | ~~34 个页面中仅 9 个有真实 `*Api.` 调用；25 个用 `Array.from({length:20})+Math.random()`~~ → ~~2026-01-25 逐页复测：45 个页面里 42 个直调 `*Api.*`；未接的 3 个是 `pages/home/Home.jsx`（`:34-59` 硬编码 KPI）、`pages/platform/PlatformDashboard.jsx`（`:20,64`）、`pages/platform/SystemConfig.jsx`（`:3` import 了 `platformApi` 却不调用，`:16-41` 自拼 `mockConfig`）~~ → 🔄 **2026-10-10 复测：46 个页面文件全部直调 `*Api.*`，无写死页面**。逐页清单见 [frontend-architecture.md §7](frontend-architecture.md) |
| 3 | ~~**admin-web 完全不联后端**~~ → ✅ 已修（一处除外） | 3/5 页面走真实接口 | ~~`admin-web/src/services/api.js` 不 import axios；`authApi` 写死 `admin/admin123` 返回 `'mock-jwt-token'`（`:165-185`）~~ → 现为 axios 实例 + 5 个模块 33 个方法（`authApi:69`、`tenantApi:77`、`userApi:90`、`roleApi:102`、`platformApi:116-143`），Login/TenantList/UserList 真实调用，退出也已实调（`AuthContext.jsx:72-81` → `POST /auth/logout`）。⚠️ **唯一残留的桩是 `platformApi` 本身**（`:116-143` 硬编码返回 `siteName: '物流ERP系统'` 等），所以 `SystemConfig.jsx` 看着"有数据"也是假的（ISS-026） |
| 4 | ~~**admin-web 联调必 403**~~ → ✅ CORS 已放行 | 5110/5120 可用 | ~~`SecurityConfig.java:31` 只放行 `http://localhost:5173`~~ → 🔄 2026-10-09 实测 `corsConfigurationSource`（`SecurityConfig:60-67`）放行的是 **`http://localhost:5110` 与 `http://localhost:5120`**，与两个 `vite.config.js` 的 `server.port` 严格对齐（旧文记的 5173/5174 是改名前的历史端口，照它配前端会真的被 CORS 挡）。仍 ⚠️：**没有同源反代**——`vite.config.js` 只有 `plugins` + `server.port/strictPort`，无 `proxy` 段，所以是浏览器跨域直连 8082，`baseURL` 仍写死（见 ISS-030） |
| 5 | ~~**端口不是配出来的**~~ → ✅ 已固定 | 端口被占时 Vite **直接失败**而不是换端口 | ~~无 `server.port` 配置，5173/5174 是默认端口被占后自动递增的结果，所以"有时前端起在 5175"~~ → 现两个 `vite.config.js` 都写了 `port` + **`strictPort: true`**（user-web 5120 / admin-web 5110）。这是有意的：CORS 白名单是按端口匹配的，静默递增会让页面能打开但所有请求 403。端口冲突时报错，请释放端口或同时改 `SecurityConfig:63`，**别只改一端** |
| 6 | ~~**鉴权形同虚设**~~ → ✅ 已修 | ~~随便塞个 `Authorization: Bearer xxx` 也能调~~ | ~~`validateToken()` 恒 `true`（`:126-128`）、`getUsernameFromToken()` 恒 `""`、`getTenantIdFromToken()` 恒 `"default-tenant"`~~ → `validateToken` 现为 `parseClaims(token) != null`（`JwtTokenProvider.java:189-191`），两个 getter 解析失败返回 null（`:130-133,141-144`）。2026-01-25 实测：伪造 token 与不带 token 都被拒（**403 空 body**，注意不是 401——`SecurityConfig` 没配 `exceptionHandling()`，见 ISS-032/ISS-058） |
| 7 | ~~**`/api/tenants/**` 恒 403**~~ → ✅ 已修（两段根因） | 租户管理可用 | ① authorities 恒空（角色填充被注释）已由 ISS-041 修复：token 携带 `roles`/`permissions` claim，`JwtAuthenticationFilter` 构造注入并按 `ROLE_` 前缀重建（`:59-60`）；② 2026-01-25 又查出第二段——`admin` 其实**从未被分配 `PLATFORM_ADMIN` 角色**，因为种子初始化在无会话线程触碰 `@ElementCollection` 懒集合抛 `LazyInitializationException` 而被 `catch` 吞掉（**ISS-061**，已修）。实测 `GET /api/tenants` 现 **200** |
| 8 | ~~**`tenantId` 由客户端传**~~ → 🟡 写路径已收口（2026-10-09，ISS-118 / R-30） | ~~可为任意租户写数据~~ → 归属现在由**服务端**裁决：应用服务统一走 `TenantAccessGuard.resolveWriteTenant`（显式传参优先，但**租户用户传他租户直接拒** ⇒ `TENANT_ACCESS_DENIED`；缺省取登录租户；只有完全没有会话时才落历史默认桶）。平台管理员仍可显式指定他租户（代客操作，与 R-25 同口径） | 旧证据本轮实测**已不成立**：控制器里不再有 `@RequestParam(defaultValue="default-tenant")`（全控制器的 `default-tenant` 只剩注释，以及 5 个 `resolveTenant` 各自那句"入参 → JWT 上下文 → 历史默认桶"的**末位兜底**：写端点全部要求认证，走到那一档时必然有租户上下文，故实测不可达），`LogisticsOrderMapper` 已无硬编码。**残留（不藏）**：`WarehouseMapper:60` 是 JPA→聚合的读方向归一化（历史 null 行会读成 `default-tenant`；实测 `customers/financial_transactions/warehouses/orders/logistics_orders` 五表的 `tenant_id IS NULL OR ''` 均为 **0** 行，故暂无实际命中路径）、`EventHandler:101` 与两个事件消费者的无上下文兜底。守卫：`TenantWriteFallbackGuardTest`（扫 `application/service/**`，绕过入口即红）。详见 [known-issues.md §13 R-30 / ISS-118](../qa/known-issues.md) |
| 9 | ~~**多表写入不原子**~~ → ✅ 已修 | ~~全仓 `@Transactional` 0 处（S-06）~~ | 2026-01-25 实测 **110 处**（application 90 / infrastructure 20 / domain 0 / interfaces 0）。⚠️ 遗留：`rollbackFor` **0 处**（checked 异常不回滚）、`readOnly` 只有 **7 处**（在 `UserRepositoryImpl`/`RoleRepositoryImpl`，为 ISS-061 补的）→ 见 ISS-036/ISS-042 |
| 10 | ~~**无参构造的 `new JwtTokenProvider()`**~~ → ✅ 已修 | ~~改 `jwt.secret` 不生效~~ | `JwtAuthenticationFilter` 改为构造注入（`:31,33`），不再有自建实例；`SecurityConfig` 也不再 `@Bean` new 一次（M-01 已收敛）。密钥本身仍明文在 `application.yml`（ISS-021，按边界保持未修） |

## 8. 端口与路径速查

| 服务 | 地址 | 配置位置 |
|---|---|---|
| 后端 API | `http://localhost:8082/api/**` | `application.yml` `server.port=8082`，无 `context-path` |
| Swagger | `http://localhost:8082/swagger-ui.html` | `application.yml:41-52` |
| user-web | `http://localhost:5120` | 🔄 `vite.config.js:9` **显式配了** `server.port: 5120` + `strictPort: true`（旧文"Vite 默认 5173"已过期） |
| admin-web | `http://localhost:5110` | 🔄 `vite.config.js` 显式 `server.port: 5110` + `strictPort: true`（旧文"5174 递增"已过期） |
| MySQL | `localhost:3306/ecommerce_logistics_erp`（宿主机发布端口，可 `ERP_MYSQL_HOST_PORT` 覆盖；容器内仍 3306） | `application.yml:26` + `scripts/docker/docker-compose.yml` |
| RocketMQ | nameServer `localhost:9876` | compose + `application.yml`（`rocketmq.*`）；⚠️ `rocketmq.enabled=true` 且无 broker 时**应用起不来**，本机纯跑代码置 `false` |

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
