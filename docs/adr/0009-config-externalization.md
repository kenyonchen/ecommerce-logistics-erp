# ADR-0009：配置与凭据管理（单文件、无 profile、无外部化）

- 状态：Accepted（**列为待整改，优先级高**）
- 日期记录：2026-01-25
- 相关：[deployment.md §3](../deployment.md)、[security-findings.md S-04](../security-findings.md)

> 🔄 **2026-10-21 复核**：标题里的「无外部化」已不再字面成立——yml 现有 6 行 / 7 处 `${VAR:default}` 占位（见决策 2、3），但**默认值仍是仓库里的字面量**、仍无 profile、仍无密钥管理，所以「凭据随源码进入 Git」的结论不变。整改项从「全部未做」收窄为「profiles、Flyway、默认值去字面量、CORS 外部化」四项未做。

## 背景

后端只有一份配置文件 `backend/src/main/resources/application.yml`，其中同时包含框架配置、数据源、消息、文档、日志与 JWT 参数。项目从"能跑起来"阶段起步，未预设多环境。

## 决策（现状事实）

1. **单文件**：无 `application-dev/test/prod.yml`，无 `@ActiveProfiles`。
2. ~~**全部硬编码**~~ → 🔄 **2026-10-21：数据源与 JWT 已改成占位符，但默认值仍是仓库字面量**。~~`spring.datasource.url/username=root/password=123456`（`application.yml:24-26`）、`jwt.secret=mySecretKeyMust...12345!`（`:79`）~~ ⇒ 现在为 `url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:${ERP_MYSQL_HOST_PORT:3306}/ecommerce_logistics_erp?...}`（`:26`）、`username: ${ERP_DB_USERNAME:root}`（`:27`）、`password: ${ERP_DB_PASSWORD:123456}`（`:28`）、`secret: ${JWT_SECRET:mySecretKeyMust...12345!}`（`:119`）——**可被环境变量覆盖，但不注入时行为与旧版完全相同，字面量依旧在 Git 里**。~~`spring.rocketmq.name-server=localhost:9876`~~ → 前缀已于 2026-01-25 纠正为**顶层 `rocketmq:`**（`:39-49`，ISS-042 同轮）→ 🔄 2026-10-21 实测整块在 `:41-55`（含 `enabled: true` `:42`、`internal-consumers.enabled: false` `:45-46`），`name-server` 仍是硬编码 `localhost:9876`（`:47`，无占位符）。
3. ~~**无任何 `${VAR:default}` 占位**（grep `resources` 目录 `\${` 零命中）；`JwtTokenProvider` 里的 `${jwt.secret:...}` 默认值写在**代码**里而非 yml。~~ → 🟡 **2026-01-25 复核：yml 里依然 0 个 `${}`**（本条对配置文件成立）；但"默认值写在代码里"的点**从 1 处增加到 3 处**：`JwtTokenProvider` 的 `${jwt.secret:...}`/`${jwt.expiration:...}`、以及本轮新增的 `infrastructure/config/HttpClientConfig.java:27-28` `${platform.http.connect-timeout:5s}` / `${platform.http.read-timeout:15s}`——后者是**用 `@Value` 读 yml 值、但兜底默认写在代码**，所以 yml 一改即生效。新增这类键必须同步登记到 [deployment.md §3](../deployment.md)（已登记）。<br>🔄 **2026-10-21 复测：yml 里已有 7 处 `${}`、分布在 6 行（`:26`、`:27`、`:28`、`:66`、`:70`、`:119`），本条前半句「0 个」作废**。两类要分开看：<br>① **带默认值**（不注入也能跑）：`SPRING_DATASOURCE_URL`、`ERP_DB_USERNAME`、`ERP_DB_PASSWORD`、`JWT_SECRET`；<br>② **无默认值**（不注入即关闭对应能力）：`PLATFORM_CREDENTIAL_MASTER_KEY`（`:66`，空默认 ⇒ 凭据加密关闭，ISS-011 只有部署侧注入后才算关闭）、`SHOPIFY_API_VERSION`（`:70`）。<br>「默认值写在代码里」仍是 3 处（`JwtTokenProvider:39`、`:42`、`:45`），其中 **`:45` 的 refresh 默认 604800000（7 天）与 yml `:121-122` 的 86400000（24 小时）不一致**，只在 yml 缺键时暴露。`HttpClientConfig:27-28` 的 `:5s`/`:15s` 也仍是代码兜底，但 yml `:60-61` 已显式给值，正常启动不再依赖代码默认值。
4. **代码内硬编码的行为配置**：CORS 允许的 origin（~~`SecurityConfig.java:31` 只有 `http://localhost:5173`~~ → ~~实测在 `:50`~~ → 🔄 2026-10-10：在 `SecurityConfig:63`，值为 `http://localhost:5110` / `http://localhost:5120`（与两个 `vite.config.js:9` 的固定端口一致），**未外部化的结论不变**，~~且已扩为 `List.of("http://localhost:5173","http://localhost:5174")`~~ ⇒ 🔄 **2026-10-21 复核：`:63` 就是 `List.of("http://localhost:5110", "http://localhost:5120")`，上一版补记的 5173/5174 是错的，已作废；承载方法为 `SecurityConfig:60-67`，仍无占位符、无 profile 区分**）、前端 `baseURL`（两端 `services/api.js:6` 各一份，均 `http://localhost:8082/api`，且两个 `vite.config.js` 都无 proxy → 无法同源）、启动种子账号（~~`admin/123456`（`DataInitializationConfig.java:64` → 实测编码点 `:134`、日志明文打印在 `:145`，**按本轮边界未修**）~~ → 🔄 **2026-10-21：口令已改成可注入**——`${app.seed.default-tenant-admin-password:123456}`（`DataInitializationConfig:51`），启动日志 `:226` 只说明来源、不再打印口令；**默认值仍是 `123456`**，不注入即与旧行为一致）、~~`OrderController.java:45` 的 `"default-tenant"`~~ → 🔄 **2026-01-25 订正**：该处已改为从 `TenantContext` 取值再回落，但「回落」点扩散成了 ~~**10 个 `DEFAULT_TENANT_ID` 常量声明 + 15 处引用**~~ → 🔄 **2026-10-21 实测：14 处 `DEFAULT_TENANT_ID` 声明（13 处赋字面量 + `CustomerImportApplicationService:65` 1 处别名）+ 36 次引用 / 21 个文件；裸 `"default-tenant"` 字面量共 18 处**。controller 侧兜底已只剩 `OrderController:192` 的 `resolveTenant()` 末级回落（`CustomerController:76-79` 改为交给 `TenantAccessGuard`），domain 层仍是 `FinancialTransactionServiceImpl:26`；完整清单见 [security-findings.md M-05](../security-findings.md)。
5. `ddl-auto: update` + `show-sql: true` + `spring-boot-devtools` 启用 —— 三项都属于开发期开关，但无法按环境关闭。<br>⚠️ **本轮明确边界**：不引入 Flyway（ISS-020 保留）、不做凭据外部化（S-04/ISS-021 保留）、不加密平台密钥（ISS-011 保留）。所以本 ADR 的「建议的目标态」**本轮未执行**，仍作为待办保留；实际只做了第 3 条里 MQ 前缀与平台超时两处"让配置真正生效"的修正。<br>🔄 **2026-10-21 复核：开发期三项开关仍未按环境隔离**——`application.yml:35` `ddl-auto: update`、`:36` `show-sql: true`、`pom.xml:79` `spring-boot-devtools`（`runtime` / `optional`），且 `resources` 下只有这一份 `application.yml`，没有 `application-dev/test/prod.yml`。<br>但「本轮边界」已被后续工作**部分突破**：① 凭据外部化做了「占位符版」（决策 2/3），默认值仍留在库；② 平台密钥加密已落地（`PLATFORM_CREDENTIAL_MASTER_KEY` `:66` + `CredentialCipher`，键缺失时降级明文并 WARN，ISS-011 只在生产注入后关闭）；③ Flyway 仍未引入，`backend/target/classes/db/migration/` 下的 V7 脚本没有进 Git（`git ls-files` = 0，见 [ADR-0002](./0002-hibernate-ddl-auto.md)）。

## 后果

- **凭据进入 Git 历史**：`jwt.secret` 已随源码公开 ⇒ 任何该密钥签发的令牌都可被伪造；换密钥需要轮换并通知所有环境。🔄 2026-10-21：现在可用 `JWT_SECRET` / `ERP_DB_PASSWORD` 覆盖，但**默认字面量仍在 Git**，不注入时结论不变。
- **换环境 = 改文件**：不可能有干净的 dev/test/prod 差异，也不可能在 CI 里用独立库跑集成测试（无 `application-test.yml`，见 [testing.md §7](../testing.md)）。🔄 2026-10-21 部分改善：DB/JWT 可用环境变量覆盖（`application.yml:26-28`、`:119`），但**仍无 profile、无 `application-test.yml`**，`ddl-auto`/`show-sql` 只能改文件。
- **运维参数不可调**：~~连接池、超时、线程池全部取默认值，且无法在不重新打包的前提下调整。~~ → 🟡 **2026-01-25 部分改善**：**平台外呼超时**现在可配（`platform.http.connect-timeout` / `read-timeout`，yml `:58-61`（`:60`/`:61`），由 `HttpClientConfig:27-28` 读取，ISS-044），**MQ 生产端参数**现在真正生效（顶层 `rocketmq:` 前缀纠正，含 `send-message-timeout: 3000`、`retry-times-when-send-failed: 2` 等 7 项）。<br>❌ 仍未取默认值且无处可调：HikariCP 连接池大小、Tomcat 线程池、JPA batch、`spring.mvc.async` 超时；且这些新键依旧是 yml 字面值（无 `${VAR}`），改环境仍要改文件。
- **开发期开关可能带进生产**：`update`/`show-sql`/devtools 无环境隔离手段。

## 建议的目标态

```yaml
# application.yml —— 只放公共默认值
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
---
# application-dev.yml：ddl-auto: update, show-sql: true, devtools on
# application-test.yml：H2 或 Testcontainers 库，DDL 由 Flyway 接管
# application-prod.yml：
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    ddl-auto: none
    show-sql: false
jwt:
  secret: ${JWT_SECRET}        # 由密钥管理服务下发，禁止入库
  expiration: ${JWT_EXPIRATION:3600000}
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS}
```

配套动作：
1. 从版本库**移除**现有 `jwt.secret` 与 DB 口令，并轮换；若已推送到远端，需按密钥泄漏流程处理（改历史或视为已泄漏）。
2. 引入 `.gitignore` 的 `.env*` 与本地 `.env.example`（前端同理：`VITE_API_BASE_URL`，配合 Vite proxy 后可不需要）。
3. `DataInitializationConfig` 的默认口令改为「首次启动随机生成并**只打印一次**」或直接用环境变量注入，禁止明文写死。→ 🟡 2026-10-21：已做「环境变量注入 + 默认值」半套（`DataInitializationConfig:51`，默认仍是 `123456`）；**未做**随机生成 / 一次性打印。
4. `OrderController`/各服务的 `"default-tenant"` 全部改为从令牌上下文取值。→ ❌ 2026-10-21：仍未做；入口侧只取消了 `@RequestParam(defaultValue)`，常量兜底仍是 14 处声明 / 36 次引用（决策 4）。

## 复核对账（2026-10-21）

| 整改项 | 现状 | 证据 |
|---|---|---|
| 多 profile 拆分 | ❌ 未做 | `resources` 下仅 `application.yml`；无 `@ActiveProfiles` |
| 占位符外部化 | 🟡 半套 | 7 处 `${}` / 6 行；DB/JWT 4 个占位符带默认值，默认字面量仍在 Git |
| 密钥管理 | 🟡 半套 | `JWT_SECRET`、`PLATFORM_CREDENTIAL_MASTER_KEY` 可注入；JWT 默认值仍是公开串 |
| CORS 外部化 | ❌ 未做 | `SecurityConfig:63` `List.of` 硬编码 5110/5120 |
| 种子口令 | 🟡 半套 | `DataInitializationConfig:51` 可注入，默认仍是 `123456` |
| Flyway | ❌ 未做 | V7 脚本只在 `backend/target/classes/db/migration/`，未进 Git（ADR-0002） |
| devtools / show-sql 环境隔离 | ❌ 未做 | `pom.xml:79`、`application.yml:35-36` |
