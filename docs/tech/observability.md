# 可观测性设计（日志 / 指标 / 链路 / 健康）

> 定位：本项目当前**零可观测能力**，本文给出从「一次 `System.out`」到「可排障、可告警」的分阶段设计。
> 与其他文档分工：现状事实见 [architecture.md §5/§10](../architecture.md) 与 [deployment.md §5](../deployment.md)；本文讲目标设计与落地步骤。错误响应里的 `traceId` 字段定义见 [api/error-codes.md §4](../api/error-codes.md)。
> 事实基线：2026-01-25 工作树。

## 1. 现状（实测）

| 维度 | 事实 |
|---|---|
| 日志门面 | `@Slf4j` ~~**仅 1 个类**（`BusinessProcessOrchestrationService`）~~ → **2026-01-25 实测 35 个类**；`LoggerFactory.getLogger` **9 个类**（~~`RocketMQEventConsumer`~~ → 该类已随 ISS-034 删除；2026-01-25 复测的 9 个是 `DataInitializationConfig`、`DomainEventPublisher`、`EventHandler`、`GlobalExceptionHandler`、`PlatformIntegrationApplicationService` + `infrastructure/messaging/consumer/` 下的 4 个监听器）；444 个主源文件里合计 **44 个类有 logger**，即**多数类仍无 logger** |
| 标准输出 | ~~`System.out.println` 5 处：`TenantIsolationAspect:41`、`OrderSyncService:31`、`PlatformIntegrationServiceImpl:96,104,112`~~ → **2026-01-25 实测 `backend/src/main/java` 下 0 处**：这 5 处正是上面三行点名的位置，随 ISS-009（切面改 `@Slf4j`）、`OrderSyncService` 整类删除、ISS-013（`sync-*` 改真实编排）一并消失。⚠️ 仍待清理的是 **`DataInitializationConfig` 用日志明文打印默认口令**（~~`:75`~~ → 复测锚点为 `:145`；S-04 边界内未修） |
| 日志配置 | 无 `logback-spring.xml`（find 实测 0）；只有 `application.yml:55-60` 的 4 组包级 `INFO`；控制台输出，无文件/滚动/JSON |
| SQL | `spring.jpa.show-sql: true`（`application.yml:27`）——SQL 打到 stdout，不进日志框架，无法分级/采样/脱敏 |
| 链路 | `Result` 无 `traceId`（`Result.java:18-28`）；全仓 `MDC`/`traceId` **0 命中** |
| 指标 | pom **无** `spring-boot-starter-actuator`、无 Micrometer → 无 `/actuator/health`、无 `/metrics` |
| 追踪 | 无 Micrometer Tracing / Sleuth 后继、无 OTLP、~~全仓 `@Enable` 只有 `@EnableWebSecurity`~~ → **2026-01-25：`@Enable` 共 2 个，且都在 `SecurityConfig`——`:34` `@EnableWebSecurity` + `:35` `@EnableMethodSecurity`（ISS-041 一并开启，使 17 处 `@PreAuthorize` 真正生效）**。观测侧仍是 0：没有任何 `@Enable` 与 tracing/metrics 相关 |
| 告警 | 无。MQ 发送失败仅记 error 不重试（`DomainEventPublisher:74-76`，L-04）；反射取不到 `domainEvents` 时走 debug 静默忽略（`:52-54`）→ **事件丢失至今无人可知** |
| 前端 | 无 Sentry/自建上报；错误只弹 `message.error`（`api.js`/`errorHandler.jsx`） |

**可排障性结论**：用户报「下单失败」时，无法把前端一次点击对应到后端一条日志（无 traceId），也无法确认事件是否投递成功（无指标、debug 级静默）。这是本设计要解决的第一问题，优先级高于任何 dashboard。

## 2. 设计目标

| 目标 | 验收 |
|---|---|
| 一次请求端到端可串联 | 前端错误提示里的 `traceId` 能在日志中检索到该请求全部记录（含 MQ 消费侧） |
| 关键业务链路可量化 | 下单量、事件发布成功率、MQ 积压、平台同步失败率有指标与告警 |
| 结构可编排 | JSON 日志 + 分级 profile，生产无 `show-sql`、无 `devtools` |
| 失败不静默 | 事件投递失败、消费者异常必须计数并可告警（当前 `catch` 后仅 error 日志的三处都要有指标） |

## 3. 日志规范

### 3.1 MDC 上下文键（固定四个）

| key | 来源 | 用途 |
|---|---|---|
| `traceId` | 入口 `Filter`：优先取 `X-Trace-Id` 请求头，否则生成（32 hex） | 跨前端→后端→MQ 串联 |
| `spanId` | 同上 | 与 tracing 对齐 |
| `tenantId` | `TenantContext`（`JwtAuthenticationFilter:43` 已写入） | 多租户排障与越权审计 |
| `userId` | 认证主体（~~当前恒空，需先修 [security-design.md §3](security-design.md)~~ → **2026-01-25 前置已解除**：ISS-041/008 修好后 `SecurityContextHolder` 里有真实 principal + `ROLE_*`/权限点，可直接进 MDC；但 MDC 里**至今仍没有** `userId`/`tenantId`——`TraceIdFilter` 尚未实现（O1 待做），别把"能取到"当成"已经打了"） | 行为追溯 |

实现：新增 `TraceIdFilter`（`@Order` 早于 `JwtAuthenticationFilter`）写入 MDC，并把 `traceId` 放入响应头与 `Result.traceId`；**`finally` 清理 MDC**（与 `TenantContext.clearTenantId()` 同一处收口，二者都有泄漏问题）。MQ 消费侧：`DomainEventPublisher` 发送时把 `traceId` 放进消息 header/user property，消费者入口恢复 MDC——否则事件链路天然断链。

### 3.2 输出格式

```
%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] %logger{36} - traceId=%X{traceId} tenantId=%X{tenantId} userId=%X{userId} | %msg %ex{full}
```

生产改 JSON（`logstash-logback-encoder` 或 Logback `JsonEncoder`），字段与 §3.1 对齐；保留 `message`/`level`/`logger`/`stack_trace`。

### 3.3 分级与必打点

| 场景 | 级别 | 必含字段 |
|---|---|---|
| 用例进入/完成 | `INFO` | 用例名、tenantId、资源 ID、耗时 ms |
| 业务失败（`BusinessException` 家族） | `WARN` | code、message、资源 ID——**不打堆栈**（噪声） |
| 系统异常 | `ERROR` | 完整堆栈 + traceId |
| 外部平台调用 | `INFO`/`ERROR` | 平台名、耗时、HTTP 码、**凭据掩码**；禁止打印 `api_secret`（S-05） |
| MQ 发布/消费 | `INFO`/`ERROR` | topic、consumerGroup、eventId（`BaseDomainEvent` 有 `eventId`，但现状仅 8/22 事件继承它——先补齐才能对齐） |
| SQL | 由 `show-sql` 迁到 `hibernate SQL 日志组` + 慢查询阈值 | 见 §6 |

禁止：~~`System.out`（5 处待清）~~（**2026-01-25 实测已清零，见 §1 标准输出行**）、明文口令（`DataInitializationConfig:75`，**仍在，按本轮边界未修**）、把异常信息拼进 HTTP 响应（M-08，仍在：`PurchaseController`/`FinanceController` 的 catch 把 `e.getMessage()` 拼进文案）。

### 3.4 logback profile 骨架

```xml
<!-- backend/src/main/resources/logback-spring.xml -->
<springProfile name="dev">   <root level="INFO"><appender-ref ref="CONSOLE"/></root> </springProfile>
<springProfile name="prod">  <root level="INFO"><appender-ref ref="JSON_FILE"/><appender-ref ref="CONSOLE"/></root> </springProfile>
```

前置依赖：当前无 profile 文件（`application.yml` 是唯一配置），必须先做 [ADR-0009](../adr/0009-config-externalization.md) 的 profile 拆分，否则 `springProfile` 无从生效。滚动策略：按天 + 按大小（建议 100MB/文件、保留 14 天、总量上限 2GB）。

## 4. 健康检查与探针

引入 `spring-boot-starter-actuator`，暴露面最小化：

| 端点 | 用途 | 暴露 |
|---|---|---|
| `/actuator/health/liveness` | K8s livenessProbe | 是（无鉴权或独立端口） |
| `/actuator/health/readiness` | readinessProbe（含 DB、MQ 连通） | 是 |
| `/actuator/metrics` | 指标抓取 | **仅内网**，或 `management.server.port=9090` 独立端口 + `SecurityConfig` 放行规则同步（现状只放行 5173 且无 `anyRequest()` 兜底，L-01） |
| `/actuator/env`、`/configprops`、`/beans`、`/heapdump` | — | **禁止暴露**（会泄漏 `jwt.secret`、DB 口令——两者现在是明文） |

```yaml
management:
  endpoints.web.exposure.include: health,info,metrics,prometheus
  endpoint.health.probes.enabled: true
  server.port: 9090
```

## 5. 指标清单（Micrometer + Prometheus）

命名规范：`erp_<domain>_<metric>_<unit>`；标签基数受控（`tenant` 标签需白名单或改直方图，避免无界）。

### 5.1 系统

| 指标 | 类型 | 标签 | 告警阈值（起步） |
|---|---|---|---|
| `http_server_requests_seconds` | Timer | uri,status,method | P99 > 1s 持续 5min（v1.0 目标：1 万单量级列表 <1s，[roadmap §6](../product/07-roadmap.md)） |
| `jvm_memory_used_bytes` / `gc_pause_seconds` | Gauge/Summary | area/pool | 老年代 > 80%、GC 停顿 > 200ms |
| `hikaricp_connections_active` / `_pending` | Gauge | pool | pending > 0 持续 1min（连接池打满前预警） |
| `erp_event_publish_total` | Counter | topic,result | `result=failure` 速率 > 0 → **立刻告警**（现状失败仅 error 日志） |
| `erp_event_reflect_miss_total` | Counter | aggregate | > 0 → 说明 `getDeclaredField("domainEvents")` 对仅继承基类的聚合失效（`DomainEventPublisher:37-39,52-54`），这是**必须可见**的静默丢失 |
| `erp_mq_consumer_lag` | Gauge | group | > 1 分钟或持续增长 |
| `erp_platform_sync_failures` | Counter | platform | > 0（~~平台对接当前是 `System.out` 桩，接入真实调用后启用~~ → **2026-01-25 已具备接入条件**：`sync-*` 已是真实编排并抛 `BusinessException`（ISS-013 已修），`infrastructure/platform/` 有 6 个 `HttpPlatform*Fetcher` 带 5s/15s 超时（ISS-044）；该指标现在就可以埋，缺的只是 actuator + Micrometer 依赖） |

### 5.2 业务（服务 SLO 与产品指标对齐）

| 指标 | 说明 |
|---|---|
| `erp_order_created_total{tenant,platform}` | 下单量；对应北极星指标的过程观测 |
| `erp_order_status_transition_total{from,to}` | 状态机流转分布，异常流转（如回退）可发现逻辑漏洞 |
| `erp_inventory_shortage_total{warehouse}` | 缺货事件量，触发采购链路健康度 |
| `erp_logistics_created_from_order_seconds` | 「订单创建→物流单生成」端到端时延（事件驱动链路真实水位） |
| `erp_api_unauthorized_total{uri}` | 越权尝试检测——在 §7 修复前，该指标会暴露「任意令牌都能通过」的规模 |

## 6. 慢查询与 N+1

- 关闭 `show-sql`，改用 `logging.level.org.hibernate.SQL=DEBUG` + `org.hibernate.orm.jdbc.bind=TRACE`（仅 dev profile）。
- 接入 `datasource-proxy` 或 Hibernate `Statistics`（`spring.jpa.properties.hibernate.generate_statistics=true` 仅测试环境），输出：执行次数、缓存命中、**`PrepareStatement` 批量情况**。
- 已知 N+1 形态：4 个 `@OneToMany` 集合（`ProductJpaEntity:65`、`OrderJpaEntity:50`、`WarehouseJpaEntity:50,53`、`PurchaseOrderJpaEntity:51`）+ `ProductRepositoryImpl:74-77` 逐条转聚合。指标：`erp_load_collection_seconds_max`；优化路径见 [performance-capacity.md §5](performance-capacity.md)。
- 慢 SQL 阈值建议 200ms（dev）/ 500ms（prod），超阈值 `WARN` 并带 traceId。

## 7. 链路追踪

选型：**Micrometer Tracing + OTLP → 后端（Tempo/Jaeger/Zipkin 任一）**，Spring Boot 3.5 原生支持，成本低于自建。前置：actuator 与 profile 已就位。采样起步 10%（`management.tracing.sampling.probability: 0.1`），异常链路强制采样。跨 MQ 传播靠消息 header（与 §3.1 的 traceId 恢复同一实现）。

**不要**在没有 traceId 之前先上 APM——当前 `Result` 无 `traceId`、前端也无上报，APM 只能看到断链的片段。顺序：MDC traceId（3.1）→ actuator 健康（4）→ 指标（5）→ tracing（7）。

## 8. 前端可观测

| 项 | 现状 | 目标 |
|---|---|---|
| 错误上报 | 无（`ErrorBoundary` 只渲染兜底 UI；`App.jsx:9-44` 与 `services/errorHandler.jsx` 还有重复实现） | Sentry 或自建 `/api/client-logs`，携带 `traceId` |
| 请求失败可见性 | 只弹 `message.error`（`api.js:38-76`），401 直接 `window.location.href='/login'`（`:62`） | 失败上报含 uri/status/code/traceId；401 改 SPA 跳转 |
| 白屏/性能 | 无 | Web Vitals（LCP/INP/CLS）+ 首屏包体积（34 页全静态 import，无 lazy） |
| 环境标识 | `baseURL` 硬编码（`api.js:6`） | 构建注入 `VITE_*`，日志带版本号与环境 |

## 9. Dashboard 与告警（最小集）

1. **服务健康**：QPS、P50/P95/P99、错误率、HikariCP、GC。
2. **事件链路**：`erp_event_publish_total` 成功率、consumer lag、`erp_logistics_created_from_order_seconds`（这条最能暴露「双通道 + 重复副作用 + 消费者可能未注册」的真实状况，见 [architecture.md §3](../architecture.md)）。
3. **安全**：`erp_api_unauthorized_total`、401/403 比率、越权写入尝试。
4. **业务**：下单量、缺货量、平台同步失败量。

告警起步 4 条（避免告警疲劳）：① 事件发布失败 > 0；② P99 > 1s 持续 5min；③ readiness 非 UP；④ consumer lag > 5min。每条都要有 Runbook 链接（[deployment-architecture.md §7](deployment-architecture.md)）。

## 10. 落地阶段

| 阶段 | 内容 | 依赖 |
|---|---|---|
| O1 | `TraceIdFilter` + MDC（含 `finally` 清理）+ `Result.traceId` + 日志 pattern；~~清 5 处 `System.out`~~（**2026-01-25 已完成，实测 0 处**）；统一 `@Slf4j`（**已推进到 35 个类，仍缺多数**） | 无（可立即做）。📌 本轮 **ISS-061 是本节最强的论据**：`DataInitializationConfig:169` 等处抛出的 `LazyInitializationException` 被初始化代码的 `catch` 吞成一行 error 日志，结果表现为「管理端恒定 403」而不是「启动报错」，排查耗时远超一次真实异常告警。当前唯一可用的定位手段是 `spring.jpa.show-sql: true` 的 stdout（`application.yml:30`）——**它不进日志框架、无级别、无 traceId**，正是 §1 描述的局面 |
| O2 | profile 拆分 + `logback-spring.xml` + 滚动文件 + 关 `show-sql` | [ADR-0009](../adr/0009-config-externalization.md) |
| O3 | actuator（health/metrics/prometheus，独立端口）+ 4 条告警 | O2 |
| O4 | 业务与事件指标埋点（§5.2 + `erp_event_reflect_miss_total`） | O3；建议与事务边界修复（`@Transactional` 0 处）同期，因为「发布时机」会从直发改为 `AFTER_COMMIT` |
| O5 | Micrometer Tracing + OTLP + MQ header 传播 | O1/O3 |
| O6 | 前端上报（错误 + Web Vitals） | [error-codes.md](../api/error-codes.md) 的 `code` 口径先统一 |
