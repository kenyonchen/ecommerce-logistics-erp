# 技术文档索引

> 定位：**工程线（技术）文档的统一入口**。产品层文档回答「做什么、给谁做」，本目录回答「怎么实现、实现到什么程度、要先修什么才能继续往前走」。
> 事实基线：**2026-03-08 工作树**（20 控制器 / **198** 端点 / 40 个 `@Table` + 2 张集合表 = 运行时库 42 张 / 22 领域事件 / **463** 主源码文件 / **31** 测试类 **211** 个 `@Test`，`mvn -o test` 全绿）。上一档基线 **2026-01-25** =「20 / 196 / 40 / 22 / 444 / 26 类 164 例」；再早的「13 / 100 / 24 / 266 / 10-53」已全部过期，逐条见 [tech/traceability.md §6](traceability.md)。
> ⚠️ 本目录全部结论按文件逐个核对写成；与代码不一致时**以代码为准**，并回来修正本文。重算命令见 [traceability.md §5](./traceability.md)。
> 🔄 **2026-03-22 复测更正**：上面那行 2026-03-08 基线已再次落后——当日工作树实测为 **20 控制器 / 202 端点 / 513 主源文件 / 54 测试类 494 个 `@Test`（全绿，surefire 计时合计 10.4s）/ 运行时库 42 表 421 列**。逐条数字与可下钻明细见 [project-statistics-report.md](./project-statistics-report.md) 与 [inventory/](./inventory/)。另：本文与 [api/error-codes.md](../api/error-codes.md) 引用的 **ADR-0014 目前在 `adr/` 下不存在**。🔄 编号复核（2026-03-22）：`docs/adr/` 实测 **13 个文件 = ADR-0001…0012 + README**，`adr/README.md` 索引亦只登记到 0012，因此 ~~**0013 尚未被占用，新裁决请从 0013 起编号**~~（本文此前「实际只有 0001~0013、新裁决从 0015 起」的表述有误，已更正）。→ 🔄 **2026-10-06 再更**：0013 已落地为 [adr/0013-shop-entity-and-platform-connection-split.md](../adr/0013-shop-entity-and-platform-connection-split.md)，`adr/` 现为 **13 个 ADR + README = 14 个文件**，新裁决从 **0014** 起。

## 1. 这套文档想解决什么

产品文档（[`../product/`](../product/)、[`../spec/`](../spec/)）已经界定了目标与验收口径。技术侧的缺口不是「没人写文档」，而是：

1. **只有 As-Is 描述，没有 To-Be 方案** —— 现有 [architecture.md](../architecture.md)/[deployment.md](../deployment.md)/[testing.md](../testing.md) 精确记录了现状与缺陷，但没有「该怎么修、按什么顺序修」的设计；
2. **横切能力（可观测、性能、集成、CI）此前完全没有技术方案**；
3. **需求 ↔ 实现 ↔ 文档之间没有追溯关系**，改一处会漏改五处。

本目录补的正是这三类：**规范（怎么写的约定）→ 设计（要落成什么）→ 追溯（谁依赖谁）**。

## 2. 文档清单

| 文档 | 层次 | 回答的问题 | 主要上游依据 |
|---|---|---|---|
| [dev-guide.md](./dev-guide.md) | 规范 | 新人第一天怎么把环境跑起来、哪些坑是实测踩过的 | [deployment.md](../deployment.md)、[testing.md](../testing.md) |
| [backend-standards.md](./backend-standards.md) | 规范 | 分层依赖/命名/事务/事件/异常/持久化的**硬性约定**与 ArchUnit 门禁 | [architecture.md §7](../architecture.md)、[ADR-0012](../adr/0012-ddd-single-module-layering.md) |
| [frontend-architecture.md](./frontend-architecture.md) | 规范+设计 | 两个 app 的工程形态、API 层契约、认证态、去 mock 路线 | [product/04](../product/04-information-architecture.md)、[ADR-0008](../adr/0008-frontend-user-admin-split.md) |
| [security-design.md](./security-design.md) | 设计 | 认证/授权/租户隔离/凭据保护**该改成什么样**、修复顺序 | [security-findings.md](../security-findings.md)、[ADR-0003/0004/0007](../adr/0007-jwt-auth-and-tenant-context.md) |
| [observability.md](./observability.md) | 设计 | 日志/MDC/指标/追踪/告警的最小可用集 | [deployment.md §5](../deployment.md) |
| [performance-capacity.md](./performance-capacity.md) | 设计 | 容量假设、瓶颈 P-1~P-8、分页/索引/缓存/连接池取舍 | [data-dictionary.md §6](../db/data-dictionary.md)、[ISS-007~010](../qa/known-issues.md) |
| [integration-design.md](./integration-design.md) | 设计 | 外部平台（Amazon/eBay/Shopify）适配层 SPI、凭据、限流退避、同步任务表 | [spec/platform-integration.md](../spec/platform-integration.md) BR-01~BR-11 |
| [ci-cd.md](./ci-cd.md) | 设计 | 流水线阶段与门禁、前置整改 C-1~C-6、分支与版本策略 | [testing.md §3](../testing.md)、[ADR-0011](../adr/0011-result-envelope-and-errors.md) |
| [deployment-architecture.md](./deployment-architecture.md) | 设计 | To-Be 拓扑（单机 compose → 双副本+LB → K8s）、发布流程、Runbook、备份/RPO/RTO | [deployment.md §7/§8](../deployment.md) |
| [traceability.md](./traceability.md) | 追溯 | 需求/规格 BR ↔ 接口/领域/表/前端/缺陷 ↔ 技术文档 的映射矩阵与反向清单 | 上述全部 |
| [project-statistics-report.md](./project-statistics-report.md) | **底账** | 十章统计（规模/技术栈/模块依赖/API/数据库/前端/构建部署/测试/质量）与技术债 TD-01~TD-16 | 2026-03-22 工作树逐文件实测 |
| [inventory/](./inventory/) | **底账数据** | 15 张 CSV：202 端点逐条、42 表逐条、89 条 import 边、54 测试类、21 个 errorCode、技术债登记表 | 同上，报告内每张表都可下钻 |
| [../api/error-codes.md](../api/error-codes.md) | 设计 | 错误码字典与信封扩展（`traceId`）、HTTP 状态码一致性 | [ADR-0011](../adr/0011-result-envelope-and-errors.md) |
| [../db/migration-plan.md](../db/migration-plan.md) | 设计 | Flyway 基线 M0~M4、`tenant_id` 迁移、不可逆脚本的影子列策略 | [ADR-0002](../adr/0002-hibernate-ddl-auto.md) |

## 3. 按角色的阅读顺序

| 你是 | 顺序 |
|---|---|
| **新加入的后端** | dev-guide → backend-standards → architecture.md → 你要改的模块的 `../spec/*.md` → traceability |
| **新加入的前端** | dev-guide §7（坑清单）→ frontend-architecture → [api/README](../api/README.md) → [api.md](../api/api.md) |
| **要动表结构** | migration-plan → data-dictionary → backend-standards §7 → performance-capacity §5（索引由查询驱动） |
| **要修安全项** | security-findings（证据）→ security-design（方案与顺序）→ ci-cd §2（门禁，防止修完又退回去） |
| **要接平台** | integration-design → spec/platform-integration → api.md §4.12/§4.13 |
| **要做发布/运维** | deployment.md（As-Is）→ deployment-architecture（To-Be）→ observability → ci-cd |
| **评审 / 把关** | traceability §3（反向清单）→ ci-cd §3（门禁）→ qa/known-issues |

## 4. 单一权威边界（避免同一事实两处漂移）

| 主题 | 唯一权威 | 本目录的做法 |
|---|---|---|
| 端点契约 | [api/api.md](../api/api.md) | 只引用小节号，不重述端点表 |
| 错误码 | [api/error-codes.md](../api/error-codes.md) | 其它文档只写「按 error-codes §n」 |
| 数据模型与数据风险 | [db/data-dictionary.md](../db/data-dictionary.md)（`R-01~R-12`）+ [db/migration-plan.md](../db/migration-plan.md) | 索引/列定义不再复述 |
| 安全事实（带行号） | [security-findings.md](../security-findings.md)（`S/M/L-xx`） | security-design 只写**方案**，事实引用编号 |
| 缺陷立项 | [qa/known-issues.md](../qa/known-issues.md)（`ISS-xxx`） | 各文档发现的问题**必须登记 ISS 编号**才算立项 |
| 业务规则 | [spec/*.md](../spec/)（`BR-xx`） | 技术文档给「技术落点」，不改写规则语义 |

## 5. 编号命名空间（本目录新增）

| 前缀 | 含义 | 出处 |
|---|---|---|
| `P-n` | 性能瓶颈 | [performance-capacity.md §2](./performance-capacity.md) |
| `C-n` | CI 接入前的阻塞项 | [ci-cd.md §2](./ci-cd.md) |
| `O-n` | 可观测性实施阶段 | [observability.md §10](./observability.md) |
| `I-n` | 平台集成实施阶段 | [integration-design.md §9](./integration-design.md) |
| `M-n`（Flyway 里程碑 M0~M4） | 迁移阶段 | [migration-plan.md §3](../db/migration-plan.md) |

⚠️ **撞名警告**：`M-xx` 同时被 [security-findings.md](../security-findings.md) 用作**风险等级**（M-01~M-10）。两者字母相同、语义无关。引用时必须带文档名，如「security M-04」/「migration M3」；`traceability.md §4` 已登记该冲突，新增条目不要再往 `M-` 上叠加第三种含义。

## 6. 这些文档自身的已知局限

诚实列出来，别让「文档看着完整」被误读成「事情做完了」：

1. **全部 To-Be 内容未经运行验证**。本目录未启动过应用，所有「实际能否访问 / 实际耗时 / 实际是否注册」类判断都标注了需要以启动日志或压测为准。
2. **数字会漂**。基线数字随 product 导入等功能持续增长；引用时带上时点，或按 [traceability.md §5](./traceability.md) 的命令重算。
3. **门禁尚未存在**。[backend-standards.md](./backend-standards.md) 的 ArchUnit、[ci-cd.md](./ci-cd.md) 的流水线是**待接入**项；当前违反规范不会有任何自动化拦截。
4. **修复顺序建立在「先让信号可信」之上**：[testing.md §4](../testing.md) 的最小修复没做完之前，本文任何「已通过测试」的表述都没有支撑。
