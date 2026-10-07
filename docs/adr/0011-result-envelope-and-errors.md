# ADR-0011：统一 `Result<T>` 响应信封 + 全局异常处理

- 状态：Accepted（**控制器侧使用方式三种并存，一致性未达成**）
- 日期记录：2026-01-25
- 相关：[api.md §2/§7](../api/api.md)、[testing.md §3](../testing.md)、[security-findings.md M-08](../security-findings.md)

## 背景

前后端分离 + 双 SPA 需要一套统一的响应结构，让前端拦截器能集中判成败、集中提示错误；同时 `spring-boot-starter-web` 默认的 `ProblemDetail`/白页错误对前端不友好。

## 决策

1. 自定义信封 `interfaces/common/Result<T>`：仅 `code:String`、`message:String`、`data:T` 三字段；工厂方法 `success/success()/fail/error/error(code,msg)/error(code,msg,data)`，默认 code 为字符串 `"200"/"400"/"500"`。
2. `GlobalExceptionHandler`（`@ControllerAdvice + @ResponseBody`）把异常映射为信封 + HTTP 状态：`EntityNotFoundException→404/ENTITY_NOT_FOUND`、`InvalidOperationException→400/INVALID_OPERATION`、`ValidationException|BadCredentialsException→400/"400"`、`MethodArgumentNotValidException→400/VALIDATION_ERROR`（`data` 为 `{field:message}`）、兜底 `Exception→500/SYSTEM_ERROR`。
3. 前端在 `user-web/src/services/api.js` 的响应拦截器里解包：`code === "200"` 视为成功并 `return response.data.data`，否则按 code 映射中文文案并弹 toast；HTTP 401 → 清登录态并跳 `/login`。

## 后果

**正面**：契约简单，前端有一处集中的错误处理入口。

**代价 / 已确证的三处不一致**
1. **三种控制器写法并存**：`ResponseEntity<Result<?>>`（Auth/User/Role/Tenant）、`ResponseEntity<Result<T>>` + 方法内 try/catch（Customer/PlatformIntegration）、**裸 `Result<T>`**（Finance/Logistics/Order/Product/Purchase/Warehouse）。第三种导致 **HTTP 恒 200，即使业务失败**；Finance/Purchase 还把 `e.getMessage()` 拼进响应（M-08）。
2. **无错误码字典**：`code` 混用数字串（`"200"/"400"/"500"`）与语义串（`ENTITY_NOT_FOUND`/`VALIDATION_ERROR`/`SYSTEM_ERROR`），没有 enum/常量类；`user-web` 的 `ERROR_MESSAGES` 与 `errorHandler.jsx` 的 `getErrorMessageByCode` **重复定义**了两套映射，且后端实际只会产出 `"500"/"400"`，语义码映射基本不命中。
3. **异常体系未被使用**：⚠️ 2026-01-25 更正——**已在使用**：`throw new ValidationException()` **116** 处、`throw new BusinessException()` **14** 处、`throw new InvalidOperationException()` **6** 处、`throw new EntityNotFoundException()` **3** 处（复核口径修正：上一版本记录前用了宽 grep，把 `@ExceptionHandler` 声明、import 行与 javadoc 提及一并计入，故数字偏高），且 `GlobalExceptionHandler`（`interfaces/handler/GlobalExceptionHandler.java`）已为 `BusinessException`/`EntityNotFoundException`/`InvalidOperationException`/`ValidationException`+`BadCredentialsException`/`IllegalArgumentException`+`IllegalStateException`/`MethodArgumentNotValidException` 分别注册 `@ExceptionHandler`（ISS-037 已收敛）。<br>**残留**：领域层仍有 **32 处 `throw new IllegalArgumentException` + 3 处 `IllegalStateException`**，它们现在被合并映射到同一个 handler，语义上仍应改用 `ValidationException`/`InvalidOperationException` 才能区分 400 与 409。
4. `code` 是 **String**，而 controller 测试类都断言 `jsonPath("$.code").value(200)`（int）。⚠️ 2026-01-25 更正：**这不是测试失败的原因**——`JsonPathExpectationsHelper` 会把期望值折算成实际值类型，实测 26 类 / 164 用例全绿（见 [testing.md §3](../testing.md)）。但 String/int 的跨端隐式约定仍然存在（前端 axios 拦截器按字符串 `"500"` 判失败），改 `Result.code` 类型必须同步动前端。
5. **无 `traceId`**（`Result` 只有三字段）+ 无 MDC ⇒ 前端报错无法与后端日志串联。

## 建议

- 固定为一种写法：`ResponseEntity<Result<T>>` + **控制器内不做 try/catch**，全部交给 `GlobalExceptionHandler`；业务失败用状态码表达，`code` 只作业务码。
- 引入 `ErrorCode` 枚举（语义串 code，如 `ORDER_NOT_FOUND`），异常构造必须携带；把 `Result.code` 与 HTTP status 解耦并写清对应表。
- 让领域层抛 `BusinessException` 家族而非 JDK 异常（与 [ADR-0012](./0012-ddd-single-module-layering.md) 的领域纯净度一致，且不引入 Web 依赖）。
- 加 `traceId`（过滤器生成 → MDC → 回填 `Result`），前端错误提示附带可复制的 traceId。
- 打开 Bean Validation（`@Valid` + `@NotNull/@Size`），让 `VALIDATION_ERROR` 分支从死代码变为实际生效路径。
