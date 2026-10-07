# 错误码字典与响应规范

> 定位：`code` 字段的**唯一权威定义**。[api.md §2/§7](api.md) 记录信封事实与处理器映射，[ADR-0011](../adr/0011-result-envelope-and-errors.md) 记录决策，本文给出**可直接引用的码表 + 目标态设计 + 迁移步骤**。
> 前端消费方：`user-web/src/services/api.js:29-35` 与 `services/errorHandler.jsx:53-59` 各有一份映射（重复，见 [frontend-architecture.md §9](../tech/frontend-architecture.md)），本文是它们的对齐目标。
> 事实基线：2026-01-25 工作树。

## 1. 当前实际存在的 code 取值（穷举，无遗漏）

`Result`（`interfaces/common/Result.java:18`）只有三字段：`String code` / `String message` / `T data`。**没有 `success` 布尔、没有 `traceId`**（`:12-28`）。

| `code` 值 | 类型口径 | 产生位置 | HTTP 状态 | 触发条件 |
|---|---|---|---|---|
| `"200"` | 数字字符串 | `Result.success(data)` `:50-52`、`success()` `:60-62` | 200 | 成功 |
| `"400"` | 数字字符串 | `Result.fail(message)` `:107-109`；`GlobalExceptionHandler:85-92` | 400 | `ValidationException`、`BadCredentialsException` |
| `"500"` | 数字字符串 | `Result.error(message)` `:83-85` | ⚠️ **200**（裸 `Result<T>` 写法的控制器） | ~~`PurchaseController`(9) / `FinanceController`(8) 方法内 try/catch 吞异常，共 17 处~~ → 🔄 **2026-03-22 复测：单参 `Result.error(msg)`（即真正产出 `"500"` 的形态）只剩 8 处，全部在 `FinanceController`（`:70,:112,:129,:146,:165,:180,:195,:210`）**；`PurchaseController` 的 9 处 catch **已全部删除**（现 0 catch，类注释 `:38-39` 明写"业务失败不再在本类捕获"，改由 `GlobalExceptionHandler` 出口）；`LogisticsController:164` 那一处已升级为双参形态（码正确、HTTP 仍 200），不再产出 `"500"`。<br>**2026-01-25 更正**：`PlatformIntegrationController` 的 17 处 catch 已改用 `ResponseEntity.status(500)` → 状态码正确、但 message 原文外泄，不再属于本行形态（M-08）。🔄 2026-03-22：**"产出 `"500"` 数字串"与"HTTP 恒 200"是两条不同的账，别混**——单参 `Result.error(msg)` 现存 **22** 处：`FinanceController` 8 处（`:70,:112,:129,:146,:165,:180,:195,:210`，HTTP 也是 200，双错）+ `PlatformIntegrationController` 14 处（`:77,:94,:108,:131,:143,:160,:177,:202,:225,:242,:256,:280,:296,:315`，包在 `ResponseEntity.status(500)` 里，所以状态码自洽、只是码值仍是数字串且拼进了 `e.getMessage()`）。本行「HTTP 200 + `"500"`」这一形态只有 **8 处**（原文写的 17 处含 `PurchaseController` 9 处，该类 catch 现已全部删除，见 M-08）。`PurchaseController` 的 9 处 catch **已全部删除**（现 0 catch，类注释 `:38-39` 明写"业务失败不再在本类捕获"，改由 `GlobalExceptionHandler` 出口）；`LogisticsController:164` 那一处已升级为双参形态（码正确、HTTP 仍 200），不再产出 `"500"`。<br>**2026-01-25 更正**：`PlatformIntegrationController` 的 17 处 catch 已改用 `ResponseEntity.status(500)` → 状态码正确、但 message 原文外泄，不再属于本行形态（M-08）。🔄 2026-03-22：该类现有 **23 处 catch**——14 处 `status(500)` + 单参（见上），9 处 `status(400)` + 双参 `Result.error(e.getErrorCode(), msg)`（`:74,:128,:199,:222,:239,:253,:277,:293,:312`，形态正确），另有 4 处 `status(404)` + `Result.fail("平台集成不存在")`（`:90,:124,:156,:173`，码值又是数字串 `"400"`——`Result.fail` 单参写死 `"400"`，与 404 状态不一致） |
| `"ENTITY_NOT_FOUND"` | 语义串 | `EntityNotFoundException` 构造器（`:9,13,17`）设 `errorCode`；handler `:53-61` | 404 | 实体不存在（~~**2026-01-25 实测 3 处抛出**：`OrderApplicationService:100`、`ProductApplicationService:279,348`；其余模块仍用 `ValidationException`/`catch → Result.error` 表达「找不到」~~ → 🔄 **2026-03-22 复测：该码已铺开——实测 54 处 `new EntityNotFoundException(...)`，分布在 22 个文件**（application 46 处 / domain 11 处 / interfaces 2 处，其中 `LogisticsOrderResolver` 6、`InventoryManagementService` 4、`LogisticsChannelService` 4、`OrderApplicationService` 4）。⚠️ 计数口径要说清：`grep "throw new EntityNotFoundException"` **只有 4 处**，因为其余 50 处写在 `orElseThrow(() -> new EntityNotFoundException(...))` 里；三个构造器重载都硬编码 `"ENTITY_NOT_FOUND"`，所以这 50 处**不需要**调用方传码。<br>🔄 剩余用 `ValidationException`/`catch → Result.error` 表达"找不到"的地方明显变少，但**6 个导入应用服务**与 `CustomerImportApplicationService` 的混合口径仍在） |
| `"INVALID_OPERATION"` | 语义串 | `InvalidOperationException`（`:9,13`）；handler `:69-77` | 400 | 非法状态/操作（实测 `throw new` **6** 处，2026-03-22 复测未变；⚠️ 采购域新写的非法迁移用的是 `BusinessException` + `INVALID_STATUS_TRANSITION`（`PurchaseOrderTest:83` 锁住），**同一个语义分到了两个码**） |
| `"VALIDATION_ERROR"` | 语义串 | `ValidationException`（`:9,13`）；~~handler `:100-113`~~ → **2026-01-25 行号更正**：`MethodArgumentNotValidException` 分支在 handler ~~`:120-131`~~ **`:124-140`**（同码，`data` 为 `{field:message}`），而 ~~`:104-110`~~ **`:108-118`** 是新增的 `IllegalArgumentException`/`IllegalStateException` → 400 分支（ISS-037）；~~`:141-143`~~ **`:197-203`** 才是 `SYSTEM_ERROR` 兜底（本轮加了两个分支，行号整体后移）。⚠️ 注意 `ValidationException` 本体**不走这个码**——它由 `:89-95` 捕获后经 `Result.fail` 返回数字串 `"400"` | 400 | 校验失败 |
| `"SYSTEM_ERROR"` | 语义串 | handler 兜底 ~~`:121-127`~~ ~~`:141-143`~~ → **2026-01-25 再更正**：新增两个分支后兜底已移到 `:197-203`（`@ExceptionHandler(Exception.class)` + `@ResponseStatus(INTERNAL_SERVER_ERROR)`），message「系统内部错误，请稍后重试」 | 500 | 任何未被上面捕获的异常 |
| `"ACCESS_DENIED"` | 语义串 | **2026-01-25 本轮新增**：`GlobalExceptionHandler.java:149-156` 捕 `AccessDeniedException` → `Result.error("ACCESS_DENIED","没有权限执行该操作")` + `HttpStatus.FORBIDDEN`（此前该异常无人捕获，落 500 兜底，见 ISS-058） | 403 | `@PreAuthorize` 拒绝（方法级安全）。⚠️ **只对进入 `DispatcherServlet` 之后抛出的拒绝有效**；在过滤器链上就被挡下的请求（未认证、或 `SecurityConfig.java:64-67` 的 `hasRole(PLATFORM_ADMIN)` 未满足）不经过本 handler，实测是 **403 + 0 字节空 body**（没有 `httpBasic`/`formLogin`/`exceptionHandling` 配置），前端拿不到任何信封 → 见 ISS-032/ISS-017 |
| `"VALIDATION_ERROR"`（新增触发点） | 语义串 | **2026-01-25 本轮新增**：`GlobalExceptionHandler.java:171-188` 把 `HttpMessageNotReadableException` / `MethodArgumentTypeMismatchException` / `MissingServletRequestParameterException` 统一映射为 400 `VALIDATION_ERROR`，文案按异常类型分两支：缺参/取值不合法给「缺少必填参数 {name} 或参数取值不合法」，请求体不合法给「请求体格式不正确，请检查必填字段与取值」。此前这三类全部落 500 兜底且把原始异常文本回给调用方（ISS-057） | 400 | 实测两例：`GET /api/users`（不带 `tenantId`）→ 400；`GET /api/finance/transactions/by-type?type=income`（枚举要写 `INCOME`）→ 400。⚠️ 与 §1 上面那行的 `"VALIDATION_ERROR"` 是**同一个字符串**、不同分支（`:124-140` 的 Bean Validation 分支仍因 `@Valid` 0 处而不可达） |
| **任意自由字符串** | — | `BusinessException(message, errorCode)`（`:18,31,44`）——`errorCode` 由抛出方随便传 | 400 | 领域层抛 `BusinessException` 时。🔄 **2026-03-22 实测规模**：全仓 `new BusinessException(...)` **111 处**（其中紧跟 `throw` 的 105 处，另 6 处写法不同：`AuthController:65,109,155` 在 `orElseThrow(() -> …)` 里，`PlatformHttpExchange:175`、`ShopifyAdminApi:282,284` 是先赋值给局部变量/直接 `return` 再抛），**101 处传了 `errorCode`、10 处只传 message 而落到默认 `"BUSINESS_ERROR"`**（`WarehouseManagementService:38,70,102` + `AuthController:65,97,100,105,109,151,155`）；传出去的码共 **51 个不同的字面量 + 1 个变量 + 1 个拼接式**（`"PLATFORM_" + toSnakeCase(field) + "_REQUIRED"`），**没有一个常量类或枚举**。高度集中：`PLATFORM_CREDENTIAL_REQUIRED` 19 处、`PLATFORM_STORE_URL_INVALID` 16 处，前 11 个码占 61 处；剩下 40 个码各只出现 1 次 ⇒ **码空间随适配器数量线性膨胀**，这正是 §3 要收编的对象 |

**结论：码空间是「数字字符串 + 语义串 + 自由串」三套混用**，调用方无法枚举，前端只能特殊-case 判断。这是本文要消除的核心问题。

## 2. 为什么大多数失败现在落到 `SYSTEM_ERROR`

> 🔄 **2026-01-25 实测重写本节**（原口径「异常家族几乎没被使用」已过期，原文保留在下方便于对照）。按 `grep -c "throw new X"` 逐类实测：**`ValidationException` 116 处**、`IllegalArgumentException` 32、`BusinessException` 14、`InvalidOperationException` 6、`IllegalStateException` 3、`EntityNotFoundException` 3、`UnsupportedOperationException` **0**。按层分布：application 94 / domain 66 / infrastructure 35 / **interfaces 19**（原写「interfaces 0 处」已不成立——`*ImportController` 与 `CustomerController:160,178` 直接抛 `ValidationException`，`AuthController:97,100` 抛 `BusinessException`）。
> 🔄 **2026-03-22 三次复测（同一口径 `grep "throw new X("`，数字已再次过期，以本行为准）**：**`ValidationException` 132**（+16）、**`BusinessException` 105**（+91，几乎全部来自平台适配器这一轮：`PLATFORM_*`/`SHOPIFY_*`/`EBAY_*`/`MAGENTO_*`/`OZON_*` 共 40 多个码）、`IllegalArgumentException` **27**（−5）、`IllegalStateException` **9**（+6）、**`EntityNotFoundException` 仍 4**（但 `orElseThrow` 形态另有 50 处，合计 54，见 §1）、`InvalidOperationException` **6**（未变）、`UnsupportedOperationException` **0**（未变）。⚠️ 上一行的"按层分布 application 94 / domain 66 / infrastructure 35 / interfaces 19"**已失效**——按 `new BusinessException(` 重测的分布是 **`infrastructure/platform/**` 87 处（占 78%）/ domain 16 / interfaces 7 / infrastructure 其它 1 / application 0**，大头整体搬到了平台适配器里；重算前请勿引用旧拆分。
> 因此**「绝大多数失败落 500」这一条已不成立**：`ValidationException` → HTTP 400（`GlobalExceptionHandler.java:85-92`，走 `Result.fail`，`code` 为数字串 `"400"`），`BusinessException` → 400 + 自由 `errorCode`（`:37-43`），`IllegalArgument`/`IllegalState` 也已被 `:104-110` 捕获映射 400（ISS-037 的修法）。真正落 `SYSTEM_ERROR` 的只剩未预期异常。
> **仍然成立的核心问题**：`code` 没有字典——~~116 处 `ValidationException` 共用一个 `"400"`~~ → 🔄 现为 **132 处**，仍是同一个 `"400"`，前端无法按语义分支；`BusinessException` 的 `errorCode` 由抛出方**自由传字符串**（本轮新增的 `TENANT_ACCESS_DENIED` 就是这类；🔄 2026-03-22 已扩到 **51 个字面量码 + 1 变量 + 1 拼接式**，见 §1 最后一行）；`Result.fail` 的数字串码与语义串码混用未消除（§1 结论不变）。
> 原写法：实测异常抛出分布（`grep -c "throw new"`）：`domain` 24 处、`application` 2 处、`interfaces` **0** 处。其中 `new BusinessException/InvalidOperationException/ValidationException` 直接构造 **0 处**、`new EntityNotFoundException` 仅 2 处；领域层真实抛的多为 `IllegalArgumentException` / `IllegalStateException`；`application` 层还有 `UnsupportedOperationException`（`WarehouseApplicationService:177`）、`IllegalArgumentException`（`:179`）——绕过家族体系；桩实现（`executeInventoryCount` 返回 `List.of()`、`PlatformIntegrationServiceImpl:96,104,112` 只 `System.out`）根本不抛。
> ~~所以：**同一个「实体不存在」在不同模块可能表现为 500 / 404 / HTTP 200+`code "500"` 三种形态**~~ → 现为 400/404 两态，~~`Purchase/FinanceController` 的 17 处 `catch → Result.error` 仍会产生 HTTP 200 + `code "500"`（M-08）~~ → 🔄 **2026-03-22 复测：缩到 8 处**（`FinanceController`，`PurchaseController` 已清零）；另有 **14 处** `catch → Result.error(errorCode, message)`（13 处分布在 6 个 `*ImportController`，1 处在 `LogisticsController:164`）虽然带上了语义码，但 **HTTP 仍是 200**——合计 **22 处"裸 `Result.error` 出口"**（口径见 M-08）。⚠️ 别把这 22 处与"产出 `"500"` 数字串的 22 处"当成同一批：后者 = `FinanceController` 8 + `PlatformIntegrationController` 14，那 14 处 HTTP 是 500（正确），只是码值仍是数字串。本行这 22 处的共同特征是 **HTTP 200**。加上 Bean Validation 未启用（2026-01-25 复测：`@Valid` **0**、`@NotNull` **0**，pom 仍无 `spring-boot-starter-validation`；🔄 2026-03-22 三测：`@Valid`/`@NotBlank`/`@Size` **仍全为 0**），`MethodArgumentNotValidException` 分支**仍是不可达死代码**。

## 3. 目标码表（建议直接采用）

规则：**6 位字符串**，`A` 类保留给系统、`B` 类给业务、`C` 类给校验/鉴权；`code` 一律字符串（不与 HTTP 状态码同型，避免与测试断言 int 的老冲突，见 [testing.md §3](../testing.md)）。

| code | 语义 | HTTP | message 模板 | 对应异常 |
|---|---|---|---|---|
| `000000` | 成功 | 200 | `success` | — |
| `A00001` | 系统内部错误 | 500 | 系统繁忙，请稍后重试 | `Exception` 兜底 |
| `A00002` | 依赖服务不可用（DB/MQ） | 503 | 服务暂不可用 | `DataAccessException` 等 |
| `A00003` | 功能未实现（显式桩） | 501 | 该能力尚未开放 | `NotImplementedException` |
| `A00004` | 请求方法/路径不支持 | 405/404 | 接口不存在 | `HttpRequestMethodNotSupportedException` |
| `B00001` | 资源不存在 | 404 | `{entity}` 不存在：{id} | `EntityNotFoundException` |
| `B00002` | 状态不允许该操作 | 400 | 当前状态 {from} 不能执行 {action} | `InvalidOperationException` |
| `B00003` | 业务规则冲突 | 400 | 具体 `BR-xx` 文案 | `BusinessException` |
| `B00004` | 并发冲突（乐观锁） | 409 | 数据已被他人修改，请刷新重试 | `OptimisticLockingFailureException` |
| `B00005` | 重复提交 / 唯一约束冲突 | 409 | {field} 已存在 | `DataIntegrityViolationException` |
| `B00006` | 库存/额度不足 | 400 | 可用量 {available} 不足以满足 {required} | `BusinessException` |
| `B001xx` | 订单域专用 | 400 | 按模块细分 | `BusinessException` |
| `B002xx` | 物流域 / `B003xx` 仓储 / `B004xx` 产品 / `B005xx` 采购 / `B006xx` 财务 / `B007xx` 客户 / `B008xx` 平台 / `B009xx` 租户与权限 | — | — | — |
| `C00001` | 未认证 / 令牌无效或过期 | 401 | 登录已过期，请重新登录 | `BadCredentialsException`、`AuthenticationException` |
| `C00002` | 无权限 | 403 | 无权执行该操作 | `AccessDeniedException` |
| `C00003` | 租户上下文缺失 | 403 | 缺少租户信息 | `TenantMismatchException` |
| `C00004` | 参数校验失败 | 400 | 见 `data.fields` | `MethodArgumentNotValidException` |
| `C00005` | 请求体格式错误 | 400 | 请求格式不正确 | `HttpMessageNotReadableException` |
| `C00006` | 限流 / 超配额 | 429 | 操作过于频繁 | （待引入） |

模块段位与 `docs/spec/{module}.md` 的 `BR-xx` 一一对应：`B001xx` 的规则编号应能在 `spec/order.md` 找到同序条目（可追溯性见 [tech/traceability.md](../tech/traceability.md)）。

## 4. 目标信封

```java
public record ApiResult<T>(String code, String message, T data, String traceId, Instant timestamp) {
    public static <T> ApiResult<T> ok(T data) { ... }              // code = "000000"
    public static ApiResult<Void> fail(ErrorCode ec, Object... args) { ... }
}
```

```json
{ "code": "B00002", "message": "当前状态 PENDING 不能执行 SHIP", "data": null,
  "traceId": "6f2c1a…", "timestamp": "2026-01-25T03:12:44Z" }
```

- 新增 `traceId`：与 [observability.md §4](../tech/observability.md) 的 MDC 同源；缺了它，用户报「页面报错」时无法定位日志。
- `ErrorCode` 为实现 `getCode()/getMessageTemplate()/getHttpStatus()` 的枚举，**取代 `BusinessException(String, String)` 的自由字符串**（`Result.java:72-98` 的 `error(code,msg)` 与 `error(code,msg,data)` 两个透传重载应废弃）。
- 校验失败固定 `data = { "fields": { "fieldName": "message" } }`，前端按此渲染字段错误。

## 5. HTTP 状态与信封必须一致

现状最严重的一条：~~裸 `Result<T>` 返回的 6 个控制器（Finance、Logistics、Order、Product、Purchase、Warehouse）HTTP 恒 200，即使业务失败~~ → **2026-03-22 实测：名单变了，且影响面更大**。按「方法返回类型全是 `Result<...>`、0 处 `ResponseEntity`」判定，恒 200 的控制器有 **9 个**：`Customer`(9) / `Finance`(9) / `LogisticsChannel`(7) / `Logistics`(13) / `Order`(9) / `Product`(12) / `Purchase`(12) / `ShopWarehouse`(5) / `Warehouse`(13)——括号内为该控制器裸 `Result` 端点数，9 个合计 **89** 个端点（恰好等于这 9 个控制器的全部端点，因为其中没有任何 `ResponseEntity` 签名）。原文漏了 Customer、LogisticsChannel、ShopWarehouse 三个，却多算了 Purchase：**Purchase 的方法签名仍是裸 `Result`（所以 HTTP 恒 200 这条对它成立），但它已 0 处 try/catch**，业务失败改为让异常上抛（类注释 `PurchaseController.java:38-39`「业务失败不再在本类捕获」），所以它落在「状态码错、但错误码/文案对」这一档。全仓返回类型为裸 `Result` 的端点共 **148** 个（另外 41 个分布在 6 个 `*ImportController` 与 `PlatformIntegrationController:60`，这些控制器混用两种签名——如 `PlatformIntegrationController` 14 处 `ResponseEntity` + 1 处裸 `Result`）。（[api.md §2](api.md)）规范要求：

| 失败类别 | HTTP | 说明 |
|---|---|---|
| 认证/授权 | 401 / 403 | ~~由 Spring Security 出口，不进 `GlobalExceptionHandler`~~ → **2026-01-25 实测订正，分两条路**：① `@PreAuthorize` 抛的 `AccessDeniedException` 在 DispatcherServlet 之后，**会**进 handler（`:149-156`）→ 403 + `ACCESS_DENIED` 信封；② 未认证 / `SecurityConfig.java:64-67` 的 `hasRole(PLATFORM_ADMIN)` 在过滤器链上就被拒，**不进 handler**，实测 **403 + 空 body**（没配 `exceptionHandling()`，也没有任何 401 出口）。所以"401"在当前实现里**永远不会出现**，前端 `api.js:63-66` 按 401 跳登录的分支不可达（ISS-032） |
| 参数与业务校验 | 400 | `C00004` / `B0000x` |
| 资源不存在 | 404 | `B00001` |
| 状态冲突 / 唯一冲突 / 乐观锁 | 409 | `B00004` / `B00005` |
| 未实现 | 501 | 显式桩，禁止伪装成成功 |
| 系统异常 | 500 | 对外只给 `traceId`，不回栈信息 |

同时**禁止**：控制器方法内 try/catch 后返回 200；把 `e.getMessage()` 拼进响应文案（~~现状 `Finance/PurchaseController`，M-08~~ → **2026-03-22 复测：`PurchaseController` 已完全收口（0 处 catch、0 处 `getMessage()`），原文外泄的账转移到了别处，但总量没降**：全控制器 **45 处** `Result.error(...)` 全部把异常原文写进响应体——① **23 处**双参 `Result.error(e.getErrorCode(), e.getMessage())`（`PlatformIntegrationController` 9：`:74,:128,:199,:222,:239,:253,:277,:293,:312`；6 个 `*ImportController` 13：`CustomerImport:99,:149`、`FinanceImport:105,:152`、`OrderImport:105,:152`、`ProductImport:98,:144`、`WarehouseImport:97,:145`、`LogisticsImport:106,:150,:170`；`LogisticsController:164` 1）——码正确、但 HTTP 200 且原文外泄；② **22 处**单参拼接（`FinanceController` 8 + `PlatformIntegrationController` 14，见 §1 第一行）——码是数字串 `"500"`。另有 6 处 `throw new ValidationException("读取上传文件失败: " + e.getMessage())`（各 `*ImportController` 的 `downloadFile` 分支，如 `OrderImportController:193`），经 handler `:89-95` 后同样把原文带给调用方。M-08 仍然成立，只是收敛点从 Purchase 变成了它自己。）

## 6. 落地顺序（不做大爆炸替换）

1. **先定码表再改代码**：新增 `interfaces/common/ErrorCode.java` 枚举（本文 §3 全量），并在 [ADR-0011](../adr/0011-result-envelope-and-errors.md) 追加一条「码表冻结」说明。
2. `GlobalExceptionHandler` 逐分支替换字面量为枚举引用；给 `BusinessException` 增加 `ErrorCode` 字段并保留旧构造器（`@Deprecated`），使 ~~24 处 domain 抛出点~~ → 🔄 **2026-03-22 实测：`new BusinessException(` 全仓 111 处**，按层是 infrastructure/platform **87**、domain **16**、interfaces **7**、infrastructure 其它 **1**、application **0**——可分批迁移的口径不变，但**主战场在平台外呼层而不是领域层**（其中 10 处还是无码单参构造：`WarehouseManagementService:38,70,102` + `AuthController:65,97,100,105,109,151,155`）。
3. 领域层把 `IllegalArgumentException`/`IllegalStateException` 换成带码的 `BusinessException` 家族——**这是消除「同错三形态」的关键一步**，收益大于加 `traceId`。（🔄 2026-03-22 实测规模：`new IllegalArgumentException(` **40** 处 = `throw new` 27 + `orElseThrow(() -> new …)` 13，其中 domain **36** 处；`new IllegalStateException(` **11** 处 = throw 9，domain 3 / infrastructure 8。注意 orElseThrow 那 13 处语义上是「找不到实体」，应迁 `EntityNotFoundException`/`B00001` 而非业务码。）
4. 引入 `spring-boot-starter-validation`（pom 当前无此依赖），DTO/`*Request` 补 `@NotBlank/@NotNull/@Size/@DecimalMin`，控制器加 `@Valid`，让 `C00004` 分支从死代码变成真实路径。
5. 统一控制器返回为 `ResponseEntity<Result<T>>` 并去掉方法内 try/catch——~~**先改 6 个裸 `Result<T>` 控制器**（HTTP 语义修复），再改 2 个自吞异常的（Customer、PlatformIntegration）~~ → 🔄 **2026-03-22：按 §5 的实测名单，恒 200 的是 9 个控制器 / 89 个端点**（Customer、Finance、LogisticsChannel、Logistics、Order、Product、Purchase、ShopWarehouse、Warehouse），另有 6 个 `*ImportController` + `PlatformIntegrationController:60` 混用两种签名（全仓裸 `Result` 端点 148 个）。"自吞异常"与"裸返回"也不再是两批人：`CustomerController` 有 3 处 catch 但签名全裸，`PurchaseController` 签名全裸却已 0 catch——**排序应按「HTTP 语义」单维走**，先 `Purchase`(12) / `LogisticsChannel`(7) / `ShopWarehouse`(5)（**9 个里有 6 个已是 0 catch**：`Purchase`、`LogisticsChannel`、`ShopWarehouse`、`Order`、`Product`、`Warehouse`，这批改签名即可、风险最低），再 `Finance`(9)（必须同时删 8 处 catch）与 `Customer`(9，3 处 catch)、`Logistics`(13，1 处 catch)。
6. 前端：`api.js` 拦截器把 `code !== "000000"` 统一交给 `errorHandler`，两份 `ERROR_MESSAGES` 合并为一份并引用本文码表；同时修 ~~[frontend-architecture.md §9](../tech/frontend-architecture.md) 的 P0 双重解包~~ → 🔄 **2026-03-22 复测：双重解包已修**（`user-web/src/services/api.js:46` 仍是 `return response.data.data` 单层解包，`AuthContext.jsx:69-88` 已按解包后的 `responseData?.token` 取值，两处不再互相抵消），本条只剩**两份 `ERROR_MESSAGES` 合并**（`user-web/src/services/api.js:29-34` 与 `user-web/src/services/errorHandler.jsx:53-58` 的 `getErrorMessageByCode` 内联表——实测**两份内容逐字相同**，且第二份的唯一调用点是 `errorHandler.jsx:25` 的 `handleApiError`，而 `handleApiError` 全前端无人调用（`errorHandler.jsx` 只被 `main.jsx:5` 用 `ErrorBoundary`、`pages/order/CreateOrder.jsx:6` 用 `handleFormError`），所以"合并"实际是**删掉一份死表**，成本比想象低）。该表另含一个不存在的码 `'ERROR'`，而 §1 实测的 6 个真实码里 `ACCESS_DENIED` / `TENANT_ACCESS_DENIED` **两份表都没有** → 权限类失败在前端退化为兜底文案。`code` 判等口径仍是数字串（`api.js:45` 比较 `=== "200"`）。
7. 后端测试断言统一 `jsonPath("$.code").value("B00001")` 形态，消除 String/int 口径冲突（[testing.md §4](../testing.md)）。

## 7. 验收要点

- 任一失败响应：HTTP 状态 ∈ {400,401,403,404,409,500,501,503}，且 `code` 在 §3 表内可枚举。
  - ⚠️ 2026-01-25 实测差距：**401 在当前实现里不可达**（无 `httpBasic`/`formLogin`/`exceptionHandling`，未认证一律 403），403 有两种形状（过滤器链上空 body / 方法级安全有 `ACCESS_DENIED` 信封），`code` 空间除 §1 所列还多出本轮新用的 `TENANT_ACCESS_DENIED`（ISS-009）、`ACCESS_DENIED`（ISS-058）与两个来源不同的 `VALIDATION_ERROR`（ISS-057）。验收时应把"403 必带信封"作为一条独立要求写进 `SecurityConfig`，否则前端永远无法区分"没登录"和"没权限"。
- 全仓无裸字符串 `code` 字面量（除枚举定义处）；`grep -rn 'Result.error("' backend/src/main` 应为 0（~~**2026-01-25 基线：`Result.error(` 仍有 54 处、`Result.fail(` 5 处**，绝大多数集中在 `PurchaseController`/`FinanceController`/`PlatformIntegrationController` 的 catch 里~~ → **2026-03-22 基线：`Result.error(` 53 处（较上次 −1），其中 45 处在控制器、8 处在别处；`Result.fail(` 仍 5 处（`GlobalExceptionHandler:92` 1 处 + `PlatformIntegrationController:90,:124,:156,:173` 4 处）。按形态拆：**单参 `Result.error(msg)`（写死数字串 `"500"`，`Result.java:83-85`）22 处** = `FinanceController` 8 + `PlatformIntegrationController` 14；**双参 `Result.error(code,msg)`（`Result.java:72-74`）30 处** = 控制器 23（全部传 `e.getErrorCode()`）+ handler 7；**三参 `Result.error(code,msg,data)`（`:96`）1 处** = handler `:133`。22 + 30 + 1 = 53 ✓。**PurchaseController 已从本条除名**（0 处 catch）——见 M-08。`grep -rn 'Result\.error("' backend/src/main` 当前 **27** 处：22 处在控制器文案拼接（Finance 8 + PlatformIntegration 14），5 处在 `GlobalExceptionHandler.java:112,:133,:153,:182,:202`（这 5 处是码表落地时要换成 `ErrorCode` 枚举的合法改造点，不是缺陷）。
- 每个 `B00*` 码能在对应 `spec/*.md` 找到 `BR-xx` 依据。
- OpenAPI 里每个端点声明其可能返回的 `code`（~~现状 100 个方法都有 `@Operation`~~ → ~~**2026-01-25 实测：20 个控制器 / 196 个端点，`@Operation` 恰好 196 处（1:1 覆盖），`@ApiResponse` 0 处**~~ → 🔄 **2026-03-22 复测：20 个控制器 / 202 个端点（`@Get/Post/Put/Patch/DeleteMapping` 合计 202），`@Operation` 恰好 202 处（1:1 覆盖保持），`@ApiResponse` 仍 0 处**——增量 6 个端点来自平台域（`test-connection`、`push-fulfillment` 等），补 `@ApiResponse` 的成本确实只在「新增」，不在「补齐」）。
