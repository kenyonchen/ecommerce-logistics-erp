# 0002 · 使用 Hibernate `ddl-auto=update` 自动维护表结构

| 项 | 内容 |
|---|---|
| 状态 | 已采纳（**附带强制同步义务**） |
| 日期 | 2026-01-25 |
| 决策者 | 项目维护者 |
| 相关文档 | [`../db/data-dictionary.md`](../db/data-dictionary.md)、[`../db/schema.sql`](../db/schema.sql)、[`../qa/known-issues.md`](../qa/known-issues.md) ISS-020 |

---

> ⚠️ 本文件被 `docs/db/data-dictionary.md` 直接引用。任何重命名必须同步修改该引用。

## 背景

项目启动阶段的核心诉求是**快速推进领域模型**：24 张表、12 个 MapStruct 转换器、15 套仓储。在此阶段，领域模型与表结构变动频繁（几乎每周都在加字段/改关系）。

同时团队规模小，没有专职 DBA，也不希望每次改字段都要手写并执行迁移脚本。

## 候选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| **A. Flyway / Liquibase 管理版本化迁移** | Schema 可评审、可回滚、生产可控 | 每次模型变更需额外写脚本；与 `ddl-auto` 冲突时需先统一出口；增加协作摩擦 |
| **B. `ddl-auto=update`（自动生成/追加）** | 零迁移成本；改完实体重启即生效；适合早期探索 | **只增不删**：不删列、不改类型、不删表；结构无版本；生产环境不可控；不同环境可能漂移 |
| C. `ddl-auto=none` + 手工维护 SQL | 完全可控 | 维护成本最高，且容易出现"代码与库不一致" |

## 决策

当前阶段采用**选项 B**：`spring.jpa.hibernate.ddl-auto: update`，由 Hibernate 依据 `@Entity` 注解自动生成/追加表结构。

**附带义务（不可省略）**：

1. **JPA 实体是表结构的唯一权威来源**；
2. `docs/db/schema.sql` 是**快照**而非执行入口，仅用于评审、ER 对照与未来迁移基线；
3. 任何实体变更后，必须把等价变更手工同步进 `schema.sql` 并更新 `data-dictionary.md` 的风险清单；
4. 在进入准生产（或首次真实客户上线）之前，**必须切换到 Flyway**，切换路径见下文。

## 理由

- 原型阶段的模型改动频率极高，版本化迁移会成为明显瓶颈；
- `show-sql: true` 已开启，Hibernate 生成的 DDL 在控制台可见，可作为评审输入；
- 保留了切换点：先把所有结构沉淀到 `schema.sql` 快照，再由此生成 Flyway 的 `V1__baseline.sql`。

## 后果

### 正面

- 迭代速度快：加一个字段只改实体、重启即可；
- 本地/演示环境零配置，clone 后只要有 MySQL 库即可跑起来。

### 负面 / 需要承担的成本

- **结构不可逆**：删除实体里的字段不会删除数据库里的列，历史冗余列会长期残留；改列类型（如 `DOUBLE` → `DECIMAL(19,2)`）不会被自动应用，必须手工处理；
- **无版本、无审计**：无法回答"上周表结构是什么样的"；
- **环境漂移风险**：不同环境的库是不同时间点自动生成的，结构可能不一致；
- **评审困难**：Code Review 时看到的是实体注解而不是 DDL，容易被忽略（因此需要 `schema.sql` 快照辅助）。

### 必须遵守的约束与待办

| # | 待办 | 优先级 | 关联 |
|---|---|---|---|
| 1 | 改结构顺序：改实体 → 本地重启看 Hibernate 输出 → 同步 `schema.sql` 与 `data-dictionary.md` | 强制 | — |
| 2 | 引入 Flyway：以 `schema.sql` 生成 `V1__baseline.sql`，然后改为 `ddl-auto=validate` | **准生产前必须完成** | 见下方「迁移路径」 |
| 3 | 纠错 `DOUBLE` → `DECIMAL(19,2)`（金额类字段）时需要显式迁移脚本 | P2 | ISS-023 |
| 4 | 补缺失的唯一约束（`sku_code+warehouse_id`、`warehouse_id+location_code`）与索引 | P0 | 数据字典 R-05 |
| 5 | `scripts/docker` 增加 MySQL 容器编排，消除"环境自己准备"的漂移 | P2 | ISS-020 |

## 迁移路径（替代方案落地步骤）

```text
1) 冻结实体改动，导出当前 Hibernate 生成的 DDL（show-sql 输出）
2) 与 docs/db/schema.sql 逐行比对，修正差异，使快照成为真值的可读版本
3) 新增依赖 flyway-core + flyway-mysql，配置 spring.flyway.enabled=true
4) 由快照生成 V1__baseline.sql（含 CREATE TABLE / 唯一键 / 外键）
5) application.yml 改为 spring.jpa.hibernate.ddl-auto: validate
   —— 此时若出现 "Schema-validation: missing column" 说明代码与迁移不一致，必须先修复
6) 后续所有变更走 V2__xxx.sql / V3__xxx.sql
7) CI 中加入 flyway migrate 校验，禁止 merge 未带迁移的结构变更
```

## 复审触发条件

- 首次接入真实客户数据（必须在此之前完成 Flyway 切换）；
- 出现无法用 `update` 表达的结构变更（删列、字段类型收窄、表重命名、数据回填）；
- 需要多环境结构一致性审计。


## 更新（2026-01-25 按工作树复测）

本 ADR 的决策**未变**（仍为 `ddl-auto: update`，按本轮边界不引入 Flyway），但正文里的两处事实需就地更正/补充：

- 规模数字：Context 段的「24 张表」已过期，实测为 **40 个 `@Table` 实体 + 2 张 `@ElementCollection` 集合表 = 运行时库 42 张**；MapStruct 转换器 12 个未变，仓储为 26 领域接口 / 26 `JpaRepository` / 26 `RepositoryImpl`。
- **`update` 静默漏建表的实测证据（ISS-049）**：6 张 `*_import_job_errors` 的行号列名为 `row_number`，而 `ROW_NUMBER` 是 MySQL 8 保留字，Hibernate 生成的 DDL 未加引号 → `create table` 报 `ERROR 1064`，随后 `alter table add constraint FK…` 又报「表不存在」。Hibernate **只打 WARN，启动照样成功**，于是这 6 张表在库里根本不存在、导入错误明细写入功能整体不可用。实体侧已把物理列改名为 `line_number`（Java 字段名不变）。
  这正是本 ADR「后果」段预言的失效模式第一次真实发生：**结构问题不会以失败告终，而是以"表不存在但系统在跑"告终**。若走 Flyway，1064 会在迁移阶段直接中断。
- 因此「复审触发条件」第 2 条（无法用 `update` 表达的结构变更）已部分触达——本次是靠**改列名绕开**，代价是零迁移风险（表本就从未建成），但下次遇到"已存在的表要改类型"（如 ISS-023 的 `DOUBLE`→`DECIMAL`）就没有这条退路。
