# 功能规格 · 客户管理（支撑域）

| 项 | 内容 |
|---|---|
| 子域 | 支撑域（Customer Bounded Context） |
| 聚合根 | `Customer`、`CustomerImportJob`（导入任务） |
| 值对象 | `CustomerStatus`、`CustomerImportTargetField`、`ImportedCustomerRow` |
| 实体 | `CustomerFieldMapping`（映射模板行）、`CustomerImportError`（行级错误） |
| 领域服务 | `CustomerService`、`CustomerFieldMappingService` |
| 应用服务 | `CustomerApplicationService`、`CustomerImportApplicationService` |
| 数据表 | `customers`、`customer_import_jobs`、`customer_import_job_errors`、`customer_field_mappings` |
| 前端 | `frontend/user-web/src/pages/customer/{CustomerList,CustomerDetail,CreateCustomer,CustomerImport}.jsx` |

> 本文件按代码实测更新（2026-01-25）。接口明细见 [api.md §4.5/§4.14](../api/api.md)。

---

## 1. 模块定位

跨境业务里，"客户"就是平台上的买家。客户上下文的职责：

1. 沉淀买家档案（含来源平台与平台买家ID），支撑客服查询与二次触达；
2. 从第三方电商平台**批量导入客户**：支持平台买家接口直拉、从平台订单数据萃取买家、文件上传（JSON/CSV/xlsx）三种数据获取方式；
3. 关联客户的历史订单，形成"买家视角的时间线"。

原审计结论"全系统最薄弱模块（仅 3 个接口、前端全 mock）"已不成立：核心 CRUD + 分页搜索 + 状态机 + 软删除 + 完整导入管线均已落地，前后端契约对齐并有单测覆盖（`CustomerControllerTest` 5 例、`CustomerImportPipelineTest` 5 例）。

---

## 2. 概念模型

### 2.1 Customer 聚合

| 字段 | 列 | 说明 |
|---|---|---|
| `customerId` | `customers.id` VARCHAR(64) PK | 字符串业务ID：`"CUST-" + UUID前8位`（应用层生成，非 `@GeneratedValue`） |
| `tenantId` | `tenant_id` | 缺省 `default-tenant` |
| `customerName` | `customer_name` | 必填 |
| `email` | `email` | 可选；**小写归一 + 全局唯一**（正则校验在领域服务层）；多个空值归一为 `NULL` 避免唯一约束冲突 |
| `phone` / `address` / `company` | 同名列 | 可选，`trimToNull`；`company` 贯通导入映射→预览→落库→展示全链路 |
| `sourcePlatform` | `source_platform` | 来源平台名（如 `SHOPIFY`），手动创建为 `NULL` |
| `externalBuyerId` | `external_buyer_id` | 平台侧买家ID，与 `(tenantId, sourcePlatform)` 一起构成导入幂等匹配键 |
| `status` | `status` | `@Enumerated(STRING)`，落库存枚举名 `ACTIVE/INACTIVE/SUSPENDED/DELETED` |
| `createdAt` / `updatedAt` | 同名列 | 聚合根内赋值（应用层保证，无 JPA 自动填充） |

### 2.2 导入侧模型

- `CustomerImportJob`：一次批量导入的任务聚合，记录 `totalCount/successCount/failureCount/skippedCount`、创建与更新的客户ID清单、行级错误集合；状态复用 `ImportJobStatus`（`PENDING/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED`）。
- `CustomerFieldMapping`：按 `(tenantId, platform)` 保存的「平台原始字段 → 标准目标字段」模板，可整体覆盖。
- `CustomerImportError`：行级失败/跳过明细（行号、买家ID、字段、消息）。

---

## 3. 状态机

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: 创建/导入
    ACTIVE --> INACTIVE: PUT /{id}/deactivate
    ACTIVE --> SUSPENDED: PUT /{id}/suspend
    SUSPENDED --> ACTIVE: PUT /{id}/activate
    INACTIVE --> ACTIVE: PUT /{id}/activate
    ACTIVE --> DELETED: DELETE /{id}（软删除）
    INACTIVE --> DELETED: DELETE /{id}
    SUSPENDED --> DELETED: DELETE /{id}
    DELETED --> [*]
```

- 删除为**软删除**（置 `DELETED`），历史订单关联保留可查。
- 客户是**全仓唯一**采用软删除的业务聚合，因此"`DELETE /api/tenants/{id}` 前置检查"必须把 `DELETED` 墓碑排除在"仍有业务数据"之外（`TenantRepositoryImpl.INVISIBLE_ROW_PREDICATES`，R-31 / ISS-119）：否则"把这个租户的客户删干净"这个动作本身就让该租户永远删不掉，而挡它的那条行用户在列表里根本看不见。代价是删租户后其墓碑成为不可达行，不级联删除是明确接受的取舍。
- `DELETED` 为终态：`changeStatus` 与 `updateProfile` 均 `assertNotDeleted`，再操作抛 `InvalidOperationException`（HTTP 400）。
- 状态未变化时为空操作，不刷新 `updatedAt`、不发事件。
- 尚无「按最近下单时间自动刷新 ACTIVE/INACTIVE」的定时任务（BR-11 仍未实现；全仓 `@Scheduled`/`@EnableScheduling` 实测为 0 处）。

---

## 4. 业务规则

| 编号 | 规则 | 状态 | 说明 |
|---|---|---|---|
| BR-01 | 邮箱格式合法且全局唯一 | ✅ | 领域服务层正则 + `findByEmail` 查重；创建/更新/导入三条路径一致 |
| BR-02 | 创建/更新时间必须赋值 | ✅ | 聚合 `create/restore` 内赋值，不依赖 JPA 回调 |
| BR-03 | 删除采用软删除 | ✅ | `status=DELETED`，终态不可复活 |
| BR-04 | 分页搜索（ID/名称/邮箱/电话/状态/平台/关键字/注册时间区间） | ✅ | `GET /api/customers` + `CustomerQuery`/Specification |
| BR-05 | 导入幂等：同租户+同平台+同买家ID 视为同一客户 | ✅ | 命中 `mergeImportedProfile`，未命中再按邮箱兜底匹配 |
| BR-06 | 导入合并只补空、不覆盖已有值 | ✅ | `mergeImportedProfile` 仅填空白字段；前端文案已对齐（"合并补全缺失字段"） |
| BR-07 | 行级失败不阻断批次 | ✅ | 逐行 try/catch，失败明细落库可回溯 |
| BR-08 | 一个客户多个收货地址（地址簿） | ⚪ | 未实现，地址为单一字符串列 |
| BR-09 | 沟通记录 | ⚪ | 未实现（见 §8） |
| BR-10 | 客户分层/标签 | ⚪ | 未实现（见 §8） |
| BR-11 | 状态由购买行为自动更新 | ⚪ | 无定时任务 |

---

## 5. 平台客户导入（核心链路）

### 5.1 数据获取方式

| 通道 | 入口 | 行为 |
|---|---|---|
| 平台买家接口 | `sourceType=CUSTOMERS` | 按 `integrationId` 取集成的 `storeUrl/apiKey/apiSecret`，`GET {storeUrl}/customers`（`X-API-Key`/`X-API-Secret` 头），支持 `keyword` 过滤、`limit/page` 分页 |
| 平台订单萃取 | `sourceType=ORDERS` | `GET {storeUrl}/orders`（支持 `orderNumber`、增量 `updatedFrom/updatedTo`），再按「买家ID→邮箱→名称」优先级分组去重，每单行合并为买家行 |
| 文件上传 | `/upload` | JSON / CSV / xlsx（`ImportSourceParser` + `ExcelSourceParser`），首行为表头 |
| 示例数据 | `useSampleData=true` | 各平台形状有意的差异样本（AMAZON 无电话地址、SHOPIFY 无买家ID 走邮箱兜底、个别行缺邮箱），用于未联调真实接口时验证全链路 |

未配置 `storeUrl` 且未勾选示例数据 → 直接 400，并提示替代路径。

### 5.2 字段映射规则

- 标准目标字段 9 个：`EXTERNAL_BUYER_ID / CUSTOMER_NAME / CUSTOMER_EMAIL / CUSTOMER_PHONE / COMPANY / COUNTRY / PROVINCE / CITY / ADDRESS`，各带中英文别名表。
- 自动识别：`detectByAlias`（忽略大小写、连字符、下划线精确匹配）→ 未命中再 `detectFuzzy`（包含式兜底）；仍无法识别的列不进映射。
- 已保存模板（`customer_field_mappings`）优先于自动识别；预览请求携带 `mappings` 时以请求为准（支持 UI 手工调整后重预览）。
- 转换规则复用 `FieldTransform`（`NONE/WEIGHT_KG_TO_G/BOOLEAN_FROM_YES_NO/DEFAULT_VALUE`），`DEFAULT_VALUE` 在源值缺失时填默认值。
- 省州/市/国家与详细地址在行模型内拼接为 `fullAddress`（`ImportedCustomerRow.fullAddress()`），落库到 `address` 列。

### 5.3 批量导入流程

1. `POST /preview`：获取→映射→逐行校验，返回预览行 + 逐行 errors + warnings，不写库。
2. `POST /api/customers/import`：带 `rows` 直接导入（rows 为空则服务端重走「获取+映射」）；创建 `CustomerImportJob`（`start`→逐行 `processRow`→`complete`），起止时间、计数、错误明细全程落库。
3. 行处理：行内已有校验错误 → 记失败；名称为空/邮箱非法 → 失败；幂等命中 → 合并（`updateExisting=false` 时改判跳过并记录原因）；未命中 → `createCustomerFromPlatform`。
4. 一键同步：`POST /api/platform-integrations/{id}/sync-customers`（内部固定 `updateExisting=true`、`enableIdempotency=true`）。

### 5.4 异常处理与结果反馈

- 行级 try/catch：单行异常（含领域校验、唯一冲突、数据库异常）只影响该行；错误消息截断 300 字符后随任务持久化。
- 反馈：`CustomerImportJobDto` 含 `status/statusLabel/totalCount/successCount/failureCount/skippedCount/message/startedAt/finishedAt/errors[]/createdCustomerIds[]/updatedCustomerIds[]`；任务列表与详情可事后回溯（`GET /jobs`、`GET /jobs/{jobId}`）。
- 前端 `CustomerImport.jsx`：三个 Tab（平台拉取导入 / 文件上传导入 / 导入任务），导入完成弹窗展示计数与失败行明细（行号/买家ID/字段/原因）。

---

## 6. 功能清单（前后端对照）

| 功能 | 前端 | 后端 | 状态 |
|---|---|---|---|
| 客户列表/搜索/分页 | `CustomerList.jsx`（真实接口） | `GET /api/customers` | ✅ |
| 创建客户 | `CreateCustomer.jsx` | `POST /api/customers` | ✅ |
| 客户详情 + 历史订单 | `CustomerDetail.jsx`（按客户ID与买家ID双查合并） | `GET /{id}` + `GET /api/orders/customer/{id}` | ✅ |
| 更新资料 | 详情页编辑弹窗 | `PUT /{id}` | ✅ |
| 状态变更 | 激活/置非活跃/暂停按钮 | `PUT /{id}/activate`｜`deactivate`｜`suspend` | ✅ |
| 软删除 | Popconfirm | `DELETE /{id}` | ✅ |
| 平台客户导入（拉取/萃取/上传/映射/预览/任务） | `CustomerImport.jsx` | `/api/customers/import/*` 10 端点 | ✅ |
| 平台集成一键同步客户 | 集成列表/详情「同步客户」 | `POST /{id}/sync-customers` | ✅ |
| 沟通记录 | — | — | ⚪ 未实现（README 曾承诺，建议 P1） |
| 客户分类/标签 | — | — | ⚪ 未实现（P1） |
| 收货地址簿 | — | — | ⚪ 未实现（P2） |
| 状态自动刷新 | — | 无定时任务 | ⚪ 未实现（P2） |
| 真实平台 SDK/OAuth | — | 仅通用 HTTP + 别名映射 | 🟡 对外服务需按平台适配 |

---

## 7. 领域事件

| 事件 | 发布点 | 现状 |
|---|---|---|
| `CustomerCreatedEvent` | 创建/导入成功后经 `DomainEventPublisher` 发布 | ✅ 有发布链路；🟡 无订阅方（RocketMQ 通道未接） |
| `CustomerStatusChangedEvent` | `changeStatus` 且状态确有变化时 | ✅ 同上 |

---

## 8. 遗留缺口（按优先级）

| 优先级 | 缺口 | 建议 |
|---|---|---|
| P1 | 沟通记录实体/接口/时间线 | 新增 `customer_communications` + 详情页时间线 |
| P1 | 客户标签/分层 | `customer_tags` + 关系表 |
| P2 | 地址簿多地址 | `customer_addresses`，下单可选 |
| P2 | 状态自动刷新任务 | 定时按最近下单时间切换 ACTIVE/INACTIVE |
| P2 | 平台真实凭证对接 | OAuth/签名适配层 + 密钥加密存储（当前 apiKey/apiSecret 明文收发） |
| P2 | 导入异步化 | 大样本改后台任务 + 轮询（当前同步执行、请求内完成） |

---

## 9. 验收要点

1. 重复邮箱创建/导入必须拒绝（导入按行记失败，不阻断批次）。
2. 同一 `(tenant, platform, externalBuyerId)` 重复导入不产生新客户，且**已有字段不被空值覆盖**。
3. 软删除客户再更新/变更状态 → 400 `InvalidOperationException`；历史订单仍可查询。
4. `useSampleData` 三平台样本全链路（预览→导入→任务回溯）可跑通，缺邮箱行走邮箱兜底逻辑。
5. 前端列表筛选（状态/时间区间/关键字）与后端 `CustomerQuery` 语义一致；`Result.code` 为字符串 `"200"`。
