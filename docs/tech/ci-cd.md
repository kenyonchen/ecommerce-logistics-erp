# CI/CD 流水线与质量门禁

> 定位：本项目**当前没有任何 CI/CD**，本文给出可一次性搭起来的最小流水线与门禁标准，并明确「先报告、后阻断」的接入顺序（避免第一天全红灯被整体关掉）。
> 相关事实来源：[testing.md §1/§3](../testing.md)（测试水位与必然失败原因）、[deployment.md §7](../deployment.md)（缺失能力）、[backend-standards.md §11](backend-standards.md)（提交自检）。
> 事实基线：2026-01-25 工作树。

## 1. 现状

| 项 | 事实 |
|---|---|
| CI 配置 | **无**：`.github/`、`Jenkinsfile`、`.gitlab-ci.yml`、`azure-pipelines.yml`、pre-commit hook 全部不存在（实测） |
| 构建可复现性 | ⚠️ **无 Maven Wrapper**（`mvnw`），依赖本机 Maven 版本；前端无 `engines` 约束 |
| 后端 lint | **无**（无 checkstyle/spotless/.editorconfig/PMD） |
| 前端 lint | 有：ESLint 9 flat config，`npm run lint`，两 app 配置逐字节相同；⚠️ 是否当前为绿**未验证**（node_modules 未安装） |
| 测试 | 后端 9 类 / 48 `@Test`，**`mvn test` 大概率红灯**（5 类系统性问题：`Result.code` 是 String 而断言写 int、缺 `@Mock`、matcher 非法混用、stub 与 controller 默认 `tenantId` 不一致、必填参数缺失——见 [testing.md §3](../testing.md)）；前端 0 测试 |
| 覆盖率 | 无 JaCoCo，无任何覆盖率数字 |
| 集成/E2E | 无 `@SpringBootTest`、无 H2/Testcontainers、无 Playwright；`src/test/resources` 不存在 |
| 制品 | 无 Dockerfile、无镜像仓库定义、无 release 流程 |
| 密钥 | `application.yml` 明文含 DB 口令与 `jwt.secret`（S-04）——**在 CI 里等于把密钥暴露给构建日志的风险面** |
| 文档漂移 | 靠人工：改实体要手删/手同步 `docs/db/schema.sql`（[docs/README §4.3](../README.md)） |

**结论**：所有「质量」目前只存在于人的自觉。CI 的价值不是优雅，而是**让已知红灯无法被合入**。

## 2. 前置整改（没有这些，流水线第一天就废）

| # | 事项 | 说明 |
|---|---|---|
| C-1 | 加 Maven Wrapper | `mvn wrapper:wrapper -Dmaven=3.9.6` → 提交 `mvnw`/`.mvn/`；CI 与本地一致 |
| C-2 | 加 `.editorconfig` + Spotless（或 checkstyle） | 只做格式化与 import 顺序，不引入风格争论；`mvn spotless:check` 作为门禁 |
| C-3 | 加 `src/test/resources/application-test.yml` + `@ActiveProfiles("test")` | 把测试配置从 `localhost:3306` 的 dev 配置解耦（现在只有一个配置文件，测试若起上下文会连开发库） |
| C-4 | 修 [testing.md §4](../testing.md) 的 5 类测试缺陷 | 让 `mvn test` 先变绿；**否则只能把单测设为 non-blocking，门禁失去意义** |
| C-5 | 密钥从 yml 移出（`${DB_PASSWORD}` 等占位） | [ADR-0009](../adr/0009-config-externalization.md)；CI 用 secrets 注入 |
| C-6 | `.gitignore` 补 `target/`、`dist/`、`.env*`、`node_modules/` | 防制品与本地配置入库 |

## 3. 流水线阶段（PR 与合并后）

```mermaid
graph LR
  A[PR 触发] --> B[依赖与格式<br/>mvn verify / npm lint]
  B --> C[单元 + 架构测试<br/>JUnit + ArchUnit + JaCoCo]
  C --> D[契约测试<br/>MockMvc / OpenAPI diff]
  D --> E[集成测试<br/>Testcontainers MySQL + Flyway migrate]
  E --> F[前端构建<br/>vite build x2]
  F --> G[安全扫描<br/>secret + dependency]
  G --> H[制品构建<br/>jar / 镜像 + SBOM]
  H --> I[main 分支：部署 dev]
  I --> J[冒烟<br/>qa/smoke 关键 curl]
```

| 阶段 | 命令 | 门禁 | 说明 |
|---|---|---|---|
| Lint | `mvn -f backend/pom.xml spotless:check`；`npm run lint`（两 app） | **阻断** | 成本极低，最先启用 |
| 单测 | `./mvnw -f backend/pom.xml test` | **阻断**（C-4 完成后） | 无需 MySQL/MQ（standalone MockMvc + mock） |
| 架构 | `./mvnw -f backend/pom.xml test -Dtest=ArchitectureRulesTest` | **阻断** | 分层四条规则，见 [backend-standards.md §1](backend-standards.md) |
| 覆盖率 | JaCoCo `check` | 报告 → 阻断（阈值渐进） | 起步 line ≥ 25%（现状有效断言约 40/48，全域 0 覆盖），目标 domain+application ≥ 60%（[roadmap §6](../product/07-roadmap.md)） |
| 集成 | `./mvnw verify -Dgroups=integration`（Testcontainers MySQL 8 + Flyway） | 阻断（合并后必跑） | 同时验证 [migration-plan.md §8](../db/migration-plan.md) 的「空库可全量迁移」 |
| 前端构建 | `npm ci && npm run build`（两 app） | **阻断** | 当前 `npm install` 与 lockfile 有历史漂移（lock 顶层 name 为 `frontend`、registry 两个不同源）→ CI 必须 `npm ci` |
| 密钥扫描 | gitleaks / trufflehog | **阻断** | 已有明文口令入库（S-04），扫描器要允许历史豁免、拒绝新增 |
| 依赖扫描 | `mvn org.owasp:dependency-check-maven:check`、`npm audit --omit=dev` | 报告 → High 阻断 | 现状 pom 有冗余声明（`junit-jupiter`/`mockito-core` 已由 starter-test 传递引入；`mapstruct-processor` 双处声明）与死属性 `spring.kafka.version` |
| 文档漂移 | 自研脚本（§6） | 阻断 | 本项目最特殊的门禁 |
| 性能 | k6 冒烟（P95 预算） | 报告（合并后） | [performance-capacity.md §10](performance-capacity.md) |

## 4. GitHub Actions 参考实现

```yaml
# .github/workflows/ci.yml
name: CI
on:
  pull_request:
  push: { branches: [main] }
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: maven }
      - name: Format
        run: ./mvnw -f backend/pom.xml -q spotless:check
      - name: Unit + Architecture + Coverage
        run: ./mvnw -f backend/pom.xml -B verify
      - name: Upload report
        if: always()
        uses: actions/upload-artifact@v4
        with: { name: jacoco, path: backend/target/site/jacoco }

  frontend:
    runs-on: ubuntu-latest
    strategy:
      matrix: { app: [user-web, admin-web] }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20', cache: npm, cache-dependency-path: frontend/${{ matrix.app }}/package-lock.json }
      - name: Install
        run: npm ci
        working-directory: frontend/${{ matrix.app }}
      - name: Lint
        run: npm run lint
        working-directory: frontend/${{ matrix.app }}
      - name: Build
        run: npm run build
        working-directory: frontend/${{ matrix.app }}

  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - name: Secret scan
        uses: gitleaks/gitleaks-action@v2
        env: { GITLEAKS_LICENSE: ${{ secrets.GITLEAKS_LICENSE } }
```

```yaml
# .github/workflows/integration.yml（合并到 main 后跑，需要 Docker provider）
name: Integration
on: { push: { branches: [main] } }
jobs:
  it:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: maven }
      - run: ./mvnw -f backend/pom.xml -B verify -Dgroups=integration
        env:
          DB_PASSWORD: ${{ secrets.TEST_DB_PASSWORD }}   # 不落 yml 明文
          JWT_SECRET: ${{ secrets.TEST_JWT_SECRET }}
```

要点：`setup-java` 用 **temurin 17**（与 `java.version=17` 对齐）；前端 `cache-dependency-path` 必须分别指向两个 app 的 lockfile（无 workspace，见 [frontend-architecture.md §1](frontend-architecture.md)）；集成测试用 `services:` 或 Testcontainers 起 MySQL 8（`MySQL8Dialect`），**不要**用 H2 代替（[data-dictionary §4](../db/data-dictionary.md) 的枚举/类型口径差异 H2 测不出来）。

## 5. 分支、版本与发布

| 项 | 约定 |
|---|---|
| 分支 | `main`（可发布）+ `feature/*` 短生命周期 + `hotfix/*`；不引入 GitFlow（项目规模不需要） |
| PR 要求 | 至少 1 名 reviewer；涉及 JPA 实体/端点/状态机的 PR 必须同时改对应文档（§6 自动检查） |
| 版本 | 后端 `pom.xml` 当前 `1.0.0-SNAPSHOT`，但 [product/07-roadmap.md §1](../product/07-roadmap.md) 走 `v0.0.1 → v0.1 → v0.2 → v0.3 → v1.0`。**两侧不一致**：建议 `pom` 版本对齐里程碑（如 `0.2.0-SNAPSHOT`），作为 ADR 决策记录 |
| 制品 | `mvn package` → `target/*.jar`；镜像 `eclipse-temurin:17-jre` + jar（Dockerfile 见 [deployment-architecture.md §3](deployment-architecture.md)）；前端 `dist/` → Nginx 镜像 |
| Tag | `v0.2.0`、`v0.2.1`；打 tag 触发 release workflow（构建镜像 + 生成 CHANGELOG + 冒烟） |
| 环境 | dev（每次合并 main 自动）→ staging（tag 触发）→ prod（人工审批）。当前**三环境定义都不存在**（无 profile、无编排），先用 compose 单机分目录实现 |
| 回滚 | 制品按 tag 保留；DB 按 [migration-plan.md §6](../db/migration-plan.md) 的向前修复策略；**应用回滚 ≠ 库结构回滚**（`ddl-auto: update` 现状下更是如此） |

## 6. 文档漂移检查（本项目最需要的自动化）

「代码改了文档没改」是本仓已发生过的真实问题（根 README 曾声称后端"仅搭基础结构"、MySQL"计划中"）。用 3 条脚本把文档变成可验证契约：

| 检查 | 实现 | 失败条件 |
|---|---|---|
| 端点 ↔ `api.md` | 从 `/v3/api-docs`（或 javaparser 扫 `@*Mapping`）提取端点集合，与 `docs/api/api.md` §4 表格比对 | 数量或路径不一致（当前基线：~~**100 个端点**、13 控制器~~ → **2026-01-25 重测 196 个端点、20 控制器**，口径见 [api.md §4](../api/api.md)；旧文档写 83、再改 100，正是这种漂移。⚠️ 复算方式：`grep -cE "@(Get\|Post\|Put\|Delete\|Patch)Mapping"` 逐控制器求和，**不含 `@RequestMapping` 类前缀**） |
| 实体 ↔ `schema.sql` | `git diff --name-only` 命中 `**/jpa/entity/*JpaEntity.java` 时，要求同一 PR 修改 `docs/db/schema.sql` | 命中实体但未改快照（快照基线已于 **ISS-038** 补齐：42 张表） |
| OpenAPI 注解 | 统计 `@Tag`（类）与 `@Operation`（方法）覆盖率 | < 100%（~~当前实测 13/13、100/100，守住即可~~ → **2026-01-25 实测 20 个控制器 / 20 个 `@Tag`，方法级 `@Operation` 亦全覆盖**；新增控制器时记得补 `@Tag`，否则该门禁会拦） |

## 7. 接入顺序（务实版）

| 周 | 动作 | 阻断范围 |
|---|---|---|
| W1 | C-1/C-2/C-6 + workflow 骨架（lint + build） | 格式与构建失败即阻断（低风险，先建立信任） |
| W2 | C-4 修测试 + 单测/架构测试阻断 | 单测 |
| W3 | JaCoCo 报告（阈值 25% 起步，不设阻断）+ secret/依赖扫描 | 仅密钥扫描阻断 |
| W4 | Testcontainers 集成测试 + Flyway 空库迁移校验（C-3 前置） | 集成测试 |
| W5 | §6 文档漂移三条检查 | 文档检查 |
| W6 | 制品与镜像构建、dev 自动部署（依赖 [deployment-architecture.md](deployment-architecture.md) 的 Dockerfile） | — |

**反模式提醒**：不要在 W1 就把覆盖率或性能设成硬门禁——现状 ~~domain 有效测试为 0（唯一领域测试全是 `assertTrue(true)`）~~ → **2026-01-25：`assertTrue(true)` 全仓已清零，`domain` 有 1 个有效测试类（`OrderProcessingServiceTest` 14 例 / 49 断言 / 6 `verify()`）+ `FinancialTransactionTest`(5)**，但 9 个领域模块里仍只有 2 个被摸到，**硬阈值依然只会逼人把测试注释掉**。真正的教训来自本轮：**164 例全绿的同时，管理端整块功能是坏的（ISS-061 让 `/api/tenants|users|roles` 恒定 403）**——因为这些用例一个都不起 Spring 上下文、一个都不连库。**优先级应是加 1 个 `@SpringBootTest` 冒烟（登录 → 打 3 个受 `@PreAuthorize` 保护的端点 → 断 200），而不是给现有单测加覆盖率门槛。**

## 8. 本地等效门禁

CI 之前，本地要能跑同一套：

```bash
# ⚠️ 本仓没有 ./mvnw（实测 backend/ 下无 wrapper 脚本），且本机 mvn 不在 PATH：
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home   # 🔴 必须：默认 java 是 JDK 25，lombok 1.18.34 会直接编译失败
MVN=/Users/kenyon/Tools/apache-maven-3.9.16/bin/mvn
$MVN -o -f backend/pom.xml verify                 # 后端全量（含架构测试）—— `-o` 离线，内网仓库不可达时必加
(cd frontend/user-web  && npm run lint && npm run build)
(cd frontend/admin-web && npm run lint && npm run build)
```

> ⚠️ 本地跑 `verify` 前须知（本轮实测）：`backend/pom.xml` 挂了 `maven-compiler-plugin` 的 lombok + mapstruct `annotationProcessorPaths`，**换 JDK 后必须先 `clean`**，否则增量编译会留下「lombok 方法找不到」的假错误；另外 `spring-boot-devtools` 在类路径上，**若本机已有实例在跑，`mvn compile` 会触发它约 5 秒自动重启**（冷启动约 55 秒），别以为是编译卡住。
> ⚠️ `mvn surefire:test` **不重新编译**，改完代码要跑测试必须用 `mvn test`。

可选 pre-commit（husky / 原生 hook）：只跑「密钥模式匹配 + ~~`System.out` 新增检测（现状有 5 处 `System.out.println`，不允许再增）~~ → **2026-01-25 实测后端主源码已 0 处，这条门禁现在可以从「不允许再增」升级为「必须为 0」**；⚠️ 前端仍有 `console.log`，例如 `admin-web/src/services/api.js:138` 的 `saveSystemConfig`，若要一起管需另加 JS 规则」。pre-commit 不宜重，否则会被 `--no-verify` 绕过。
