# 部署与运维文档

> 核对自 `backend/src/main/resources/application.yml`、`backend/pom.xml`、`scripts/docker/docker-compose.yml`、两个 `vite.config.js`。
> ⚠️ 本文描述的是**开发环境（local dev）启动方式**。项目当前没有预发/生产部署定义：无 Dockerfile、无 CI、无 K8s/编排清单、无 profile 文件、无 `docker-compose` 应用服务。

## 1. 环境依赖矩阵

| 组件 | 版本要求 | 用途 | 是否必需 | 校验命令 |
|---|---|---|---|---|
| JDK | **17**（`pom.xml` `java.version=17`） | 后端编译运行 | 必需 | `java -version` |
| Maven | 3.6+（无 wrapper，需系统安装） | 构建 | 必需 | `mvn -v` |
| MySQL | 8.x（`MySQL8Dialect`） | 主存储 | 必需 | `mysql --version` |
| RocketMQ | **5.3.2**（compose 固定镜像） | 领域事件总线 | 可选（发送失败仅记 error 日志） | `docker compose ps` |
| Node.js | ≥ 18（Vite 7 要求；实测 v24 可用） | 前端构建 | 必需 | `node -v` |
| npm | 随 Node | 依赖管理 | 必需 | `npm -v` |

未引入但常被误认为存在：Spring Boot Actuator（**无** `/actuator/health`）、Flyway、Redis、Nginx、Kafka。

## 2. 本地启动步骤（顺序重要）

### 2.1 准备 MySQL

```bash
# application.yml 指向 localhost:3306，库名 ecommerce_logistics_erp
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS ecommerce_logistics_erp \
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
```

- **无需手工建表**：`ddl-auto: update` 会在首次启动时按 JPA 实体自动建表（详见 [db/data-dictionary.md §1](db/data-dictionary.md#1-schema-的真实来源务必先读)）。
- 想以评审过的结构为准，可手工执行 `docs/db/schema.sql`（注意它是快照，且**包含物理外键**，与 Hibernate 生成的结果不完全相同；两者不要混用于同一库）。

### 2.2 （可选）启动 RocketMQ

```bash
cd scripts/docker
docker compose up -d          # namesrv:9876, broker:10909/10911/10912, proxy:8088/8089
docker compose ps
```

- 该 compose **只定义 RocketMQ**，不含 MySQL、不含应用容器。
- ⚠️ 当前代码里消息链路本身是失效的（~~`RocketMQTemplate` 被手工裸 new、`spring.rocketmq.*` 无人读取、监听器可能未注册，见 [architecture.md §3](architecture.md#3-领域事件与消息)~~）。不启动 RocketMQ 不影响主流程，但**也不要以为启动它就获得了异步解耦**。
- 🔧 **2026-01-25 二次实测更正**：上面那三条旧因**都已经不成立**（ISS-034 已修）——模板由 starter 装配（`RocketMQConfig.java:22-25` 是 `@Configuration` + `@ConditionalOnProperty(rocketmq.enabled=true)` + `@Import(RocketMQAutoConfiguration.class)`，注释 `:11-15` 说明为什么必须手工 `@Import`：starter 2.2.3 只把自动配置登记在 `spring.factories`，Boot 3.x 不读；真实路径是 `infrastructure/messaging/config/RocketMQConfig.java`，不是 `infrastructure/config/`）；配置在顶层 `rocketmq:`（`application.yml:39-49`，`enabled: true`、`name-server: localhost:9876`）；4 个监听器已拆成顶层类并**实测全部注册成功**（启动日志出现 4 个 `DefaultRocketMQListenerContainer ... running`，`selectorType=TAG`、`selectorExpression='*'`）。**但这一节的结论仍然要保留，只是理由换了**：链路事实上还是不通，真因是 ① **ISS-062**——8 个会注册事件的聚合里，订单、客户、物流、仓储、平台这 5 个的事件根本没进发布器，所以 `order-events` / `inventory-events` / `logistics-events` 这些 topic 上今天**没有消息**（实测只有 `purchase-events`、`finance-events`、`product-events` 收到过）；② **ISS-052**——已经发出去的消息，消费端因事件类无 `@JsonCreator` 而无法反序列化。见 [architecture.md §3](architecture.md#3-领域事件与消息) 与 [qa/known-issues.md ISS-062](qa/known-issues.md)。

### 2.3 启动后端

```bash
cd backend
mvn clean compile
mvn spring-boot:run           # http://localhost:8082
```

自检：

```bash
curl -s http://localhost:8082/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | head -c 200
# 期望：{"code":"200","message":"success","data":{"token":"eyJ..."}}
```

首次启动会由 `DataInitializationConfig` 自动创建默认租户与管理员账号 **`admin` / `123456`**，并在日志明文打印该密码（见 [security-findings.md](security-findings.md) S-05）。

API 文档：`http://localhost:8082/swagger-ui.html`，OpenAPI JSON：`/v3/api-docs`（分组 `public-api`）。
⚠️ `SecurityConfig` 未显式放行这两个路径、也没有 `.anyRequest()` 兜底，实际能否访问需运行确认。

### 2.4 启动前端

```bash
cd frontend/user-web  && npm install && npm run dev   # http://localhost:5173
cd frontend/admin-web && npm install && npm run dev   # http://localhost:5174
```

- 两个 `vite.config.js` 都没有 `server.port` 与 `proxy`：**5173/5174 是 Vite 默认端口占用后自动递增的结果**，不是配置出来的。若固定端口需自行加 `server: { port }`。
- 前端 `baseURL` 硬编码 `http://localhost:8082/api`（`user-web/src/services/api.js:6`），跨域直连后端 → 依赖后端 CORS 白名单。
- **admin-web 当前完全不联后端**（其 `src/services/api.js` 是纯 mock 模块，不 import axios）。

## 3. 配置面清单（`application.yml`，全仓唯一配置文件）

| 键 | 当前值 | 说明 / 风险 |
|---|---|---|
| `server.port` | `8082` | 无 `context-path`，API 直接挂 `/api/**` |
| `spring.application.name` | `ecommerce-logistics-erp` | |
| `spring.devtools.restart.*` | `enabled: true` + 若干 exclude | **不应出现在生产** |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/ecommerce_logistics_erp?...` | 地址硬编码 |
| `spring.datasource.username` / `password` | `root` / **`123456`** | 明文凭据，见 S-01 |
| `spring.jpa.database-platform` | `MySQL8Dialect` | |
| `spring.jpa.hibernate.ddl-auto` | **`update`** | 生产风险：自动改表、不删列、无评审 |
| `spring.jpa.show-sql` | `true` | 性能与 SQL 泄漏 |
| ~~`spring.rocketmq.*`~~ | name-server `localhost:9876`、producer group `ecommerce-logistics-producer-group` 等 7 项 | ~~**死配置**，无代码读取；starter 期望的前缀是顶层 `rocketmq.`~~ → ✅ **2026-01-25 已修（ISS-042 同轮）**：前缀纠正为**顶层 `rocketmq:`**（`application.yml:39-49`），并在 `:37-38` 写明「必须是顶层前缀，写在 `spring.rocketmq.` 下不生效」以及 `enabled=false` 的降级语义（不装配 `RocketMQTemplate` 与消费者容器，事件只走进程内 `EventHandler`）。现共 **9 个键**：`enabled: true`、`name-server`、`producer.group`、`send-message-timeout: 3000`、`retry-times-when-send-failed: 2`、`retry-times-when-send-async-failed: 2`、`compress-message-body-threshold: 4096`、`max-message-size: 4194304`、`retry-next-server: true`。<br>⚠️ 仍待办的是 MQ **行为**而非配置：ISS-052（事件类无 `@JsonCreator`/空参构造 → 反序列化失败）、ISS-053（`AFTER_ROLLBACK` 只打日志）、ISS-054（进程内 + MQ 双通道重复副作用） |
| `spring.servlet.multipart.*` | `enabled: true`、`max-file-size: 20MB`、`max-request-size: 20MB` | 批量导入上传的文件上限；**导入链路无 `@Async`**，大文件仍是同步长请求（ISS-040） |
| `platform.http.connect-timeout` / `read-timeout` | `5s` / `15s` | **2026-01-25 新增（ISS-044）**，由 `infrastructure/config/HttpClientConfig.java:27-34` 以 `@Value` 读取并装进 `RestClient` 的 `JdkClientHttpRequestFactory`。此前 JDK HttpClient 默认**不超时**，平台挂起会占住导入线程与数据库连接。⚠️ 按 [tech/backend-standards.md §9](tech/backend-standards.md) 要求登记于此；仍是字面值，未走 `${VAR:default}` |
| `springdoc.swagger-ui.path` / `api-docs.path` / `group-configs` | `/swagger-ui.html`、`/v3/api-docs`、`public-api` → `/api/**` | |
| `logging.level.*` | 四组 INFO | 无 `logback-spring.xml`，无文件/JSON appender |
| `jwt.secret` | **`mySecretKeyMustBeAtLeast256Bits...12345!`** | 明文签名密钥，见 S-02 |
| `jwt.expiration` / `jwt.refresh.expiration` | `86400000` / `86400000`（各 24h） | refresh 与 access 同寿命；`JwtTokenProvider.java:29` 注释写「默认 7 天」已被此配置覆盖 |

**无任何 profile 文件**（无 `application-dev/test/prod.yml`），**无任何 `${VAR:default}` 环境变量占位** → 想切换环境只能改文件（意味着密钥必然进入 Git）。这是最需要先解决的一项，见 [adr/0006](adr/0009-config-externalization.md)。

## 4. CORS 与网络

| 来源 | 状态 |
|---|---|
| `http://localhost:5173`（user-web） | ✅ 在 `SecurityConfig.java:50` 白名单内（~~`:31`~~ 为旧行号，`corsConfigurationSource()` 现在 `:47-56`） |
| `http://localhost:5174`（admin-web） | ~~❌ **不在白名单**（目前因 admin-web 不联后端而未暴露）~~ → ✅ **2026-01-25 已在白名单内**：`:50` 为 `List.of("http://localhost:5173", "http://localhost:5174")`，且 admin-web 已接后端（M-09 已修） |
| 其他任意来源 / 生产域名 | ❌ 无配置 |

生产化时必须把允许的 origin 外部化（当前是代码里硬编码的单值），或改为通过 Vite/Nginx 同源代理 `/api` 从而绕开 CORS。

## 5. 健康检查、监控与日志

- **无 Actuator**：没有 `/actuator/health`、`/metrics`；进程编排与反向代理无法做就绪探测。
- **无链路追踪**：`Result` 没有 `traceId`，日志无 MDC/traceId，跨服务（前端→后端→MQ）问题无法串联。
- **日志**：仅 4 组 INFO 级别 + `show-sql`；默认控制台输出，无滚动文件、无 JSON 结构化、无告警。
- 建议最小集：引入 `spring-boot-starter-actuator`（只暴露 `health,info`）、`logstash-logback-encoder` 或 `logback-spring.xml` 文件 appender、`Result` 增加 `traceId`。

## 6. 备份与回滚

- 无备份策略定义；数据完全在单实例 MySQL。
- **schema 回滚尤其危险**：`ddl-auto: update` 只加不改不删，实体里删掉一个字段不会回收列，改字段类型则可能启动失败或静默残留旧列。回滚一次实体变更 ≠ 回滚一次库结构。
- 一旦切换到 Flyway（[adr/0002](adr/0002-hibernate-ddl-auto.md)），回滚 = 反向迁移脚本，可评审可复现。

## 7. 当前不存在的能力（明确列出，避免误判）

| 缺失 | 影响 |
|---|---|
| Dockerfile / 应用镜像 | 无法容器化部署后端 |
| CI/CD（无 `.github/`、`Jenkinsfile`、`.gitlab-ci.yml`） | 无自动构建、无自动测试门禁，`mvn test` 靠人跑 |
| 环境 profile / 密钥外部化 | dev 与 prod 只能靠手改文件，密钥入库 |
| 数据库迁移工具 | schema 变更不可评审、不可复现（见 ADR-0002） |
| 反向代理 / 网关 | 无 TLS 终结、无统一鉴权前置 |
| 定时任务 / 补偿机制 | 桩化的平台同步、对账无触发入口 |
| Maven Wrapper（`mvnw`） | 构建依赖本机 Maven 版本 |

## 8. 上生产前的最小整改清单

1. `ddl-auto: none` + Flyway 迁移；`show-sql: false`；移除 `spring-boot-devtools`。
2. 所有凭据与地址改为环境变量/密钥管理注入：DB 账密、`jwt.secret`、默认管理员口令、CORS origin。
3. 修复鉴权链路（见 [security-findings.md](security-findings.md) 的 S-01/S-03/S-04），补 Actuator 健康检查与 `anyRequest()` 规则。
4. 引入 CI：`mvn -f backend/pom.xml test` + `npm run lint && npm run build`（两个前端）。
5. 加 `.env`/profile 机制与 Dockerfile，让 `docker compose` 能拉起 MySQL + RocketMQ + backend + 前端一站启动。
