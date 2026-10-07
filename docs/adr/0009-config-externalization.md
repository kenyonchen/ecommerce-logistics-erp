# ADR-0009：配置与凭据管理（单文件、无 profile、无外部化）

- 状态：Accepted（**列为待整改，优先级高**）
- 日期记录：2026-01-25
- 相关：[deployment.md §3](../deployment.md)、[security-findings.md S-04](../security-findings.md)

## 背景

后端只有一份配置文件 `backend/src/main/resources/application.yml`，其中同时包含框架配置、数据源、消息、文档、日志与 JWT 参数。项目从"能跑起来"阶段起步，未预设多环境。

## 决策（现状事实）

1. **单文件**：无 `application-dev/test/prod.yml`，无 `@ActiveProfiles`。
2. **全部硬编码**：`spring.datasource.url/username=root/password=123456`（`application.yml:24-26`）、`jwt.secret=mySecretKeyMust...12345!`（`:79`）、~~`spring.rocketmq.name-server=localhost:9876`~~ → 前缀已于 2026-01-25 纠正为**顶层 `rocketmq:`**（`:39-49`，ISS-042 同轮），但值仍硬编码 `localhost:9876`。
3. ~~**无任何 `${VAR:default}` 占位**（grep `resources` 目录 `\${` 零命中）；`JwtTokenProvider` 里的 `${jwt.secret:...}` 默认值写在**代码**里而非 yml。~~ → 🟡 **2026-01-25 复核：yml 里依然 0 个 `${}`**（本条对配置文件成立）；但"默认值写在代码里"的点**从 1 处增加到 3 处**：`JwtTokenProvider` 的 `${jwt.secret:...}`/`${jwt.expiration:...}`、以及本轮新增的 `infrastructure/config/HttpClientConfig.java:27-28` `${platform.http.connect-timeout:5s}` / `${platform.http.read-timeout:15s}`——后者是**用 `@Value` 读 yml 值、但兜底默认写在代码**，所以 yml 一改即生效。新增这类键必须同步登记到 [deployment.md §3](../deployment.md)（已登记）。
4. **代码内硬编码的行为配置**：CORS 允许的 origin（~~`SecurityConfig.java:31` 只有 `http://localhost:5173`~~ → 实测在 `:50`，且已扩为 `List.of("http://localhost:5173","http://localhost:5174")`，**仍是 `List.of` 硬编码**）、前端 `baseURL`（两端 `services/api.js:6` 各一份，均 `http://localhost:8082/api`，且两个 `vite.config.js` 都无 proxy → 无法同源）、启动种子账号 `admin/123456`（~~`DataInitializationConfig.java:64`~~ → 实测编码点 `:134`、日志明文打印在 `:145`，**按本轮边界未修**）、~~`OrderController.java:45` 的 `"default-tenant"`~~ → 🔄 **2026-01-25 订正**：该处已改为从 `TenantContext` 取值再回落，但"回落"点扩散成了 **10 个 `DEFAULT_TENANT_ID` 常量声明 + 15 处引用**（含 controller 侧 `OrderController:170`、`CustomerController:68`，以及 domain 层的 `FinancialTransactionServiceImpl:26`），完整清单见 [security-findings.md M-05](../security-findings.md)。
5. `ddl-auto: update` + `show-sql: true` + `spring-boot-devtools` 启用 —— 三项都属于开发期开关，但无法按环境关闭。<br>⚠️ **本轮明确边界**：不引入 Flyway（ISS-020 保留）、不做凭据外部化（S-04/ISS-021 保留）、不加密平台密钥（ISS-011 保留）。所以本 ADR 的「建议的目标态」**本轮未执行**，仍作为待办保留；实际只做了第 3 条里 MQ 前缀与平台超时两处"让配置真正生效"的修正。

## 后果

- **凭据进入 Git 历史**：`jwt.secret` 已随源码公开 ⇒ 任何该密钥签发的令牌都可被伪造；换密钥需要轮换并通知所有环境。
- **换环境 = 改文件**：不可能有干净的 dev/test/prod 差异，也不可能在 CI 里用独立库跑集成测试（无 `application-test.yml`，见 [testing.md §7](../testing.md)）。
- **运维参数不可调**：~~连接池、超时、线程池全部取默认值，且无法在不重新打包的前提下调整。~~ → 🟡 **2026-01-25 部分改善**：**平台外呼超时**现在可配（`platform.http.connect-timeout` / `read-timeout`，yml `:52-55`，由 `HttpClientConfig:27-34` 读取，ISS-044），**MQ 生产端参数**现在真正生效（顶层 `rocketmq:` 前缀纠正，含 `send-message-timeout: 3000`、`retry-times-when-send-failed: 2` 等 7 项）。<br>❌ 仍未取默认值且无处可调：HikariCP 连接池大小、Tomcat 线程池、JPA batch、`spring.mvc.async` 超时；且这些新键依旧是 yml 字面值（无 `${VAR}`），改环境仍要改文件。
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
3. `DataInitializationConfig` 的默认口令改为「首次启动随机生成并**只打印一次**」或直接用环境变量注入，禁止明文写死。
4. `OrderController`/各服务的 `"default-tenant"` 全部改为从令牌上下文取值。
