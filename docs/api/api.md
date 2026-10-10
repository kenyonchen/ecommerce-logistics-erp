# 接口设计文档

> 来源：逐文件核对 `backend/src/main/java/.../interfaces/controller/` 下的 `@RestController`。
> 🔄 **2026-01-25 按工作树重测**：**20 个控制器 / 196 个端点**；**2026-03-08 复测 = 20 个控制器 / 198 个端点**（增量来自 `PlatformIntegrationController` 的 `test-connection` 与 `push-fulfillment`）；**同日三测 = 20 个控制器 / 199 个端点**（再加 `PlatformIntegrationController` 的 `GET /auth-specs`，`PlatformIntegrationController.java:59`）；**🔄 2026-03-22 复测 = 20 个控制器 / 202 个端点**（增量 3 条全在采购域：`GET /api/purchases/statuses`、`PUT /api/purchases/{id}/items/{itemId}`、`DELETE /api/purchases/{id}/items/{itemId}`）。旧口径「12 个 / 83 个」「13 个 / 100 个」均已过期。
> §4.1–§4.14 是较早基线的手写摘要（含风险注记）。~~仍逐条对得上代码~~ → 🔄 **2026-03-22 复核：§4.10 PurchaseController 一节整体已过期并已重写**（端点 9→12、入参 query→body、列表 全量数组→服务端分页、「不带 status 就回退查 PENDING」的语义 bug 已修）；其余 §4.x 抽核未见新偏差。**[§4B 全量端点清单](#4b-全量端点清单2026-01-25-由注解扫描生成)** 是由注解扫描生成的完整清单（10-02 生成时 196 条 → 10-03 追加平台 3 条 = 199 条 → **10-04 重写采购块 9→12 = 202 条**）。🔄 **本轮已用脚本把 §4B 的 20 个块逐控制器与源码注解扫描对账：doc 202 ↔ code 202，逐控制器 0 差集**，**计数与路径以 §4B 为准**。
> 本文描述的是**代码当前真实暴露的契约**，含其中的不一致与缺陷——这些不一致必须被记录，否则前端只能靠试错来发现。
>
> 交互式文档：`http://localhost:8082/swagger-ui.html`（`server.port: 8082`；springdoc 分组 `public-api`，`paths-to-match: /api/**`，OpenAPI JSON 在 `/v3/api-docs`）。
> 20/20 控制器都有 `@Tag`，**端点与 `@Operation` 数量相等**（2026-01-25 为 196/196；2026-03-08 复测 198/198；**同日三测 199/199**，实测 `grep -c '@Operation'` = 199 与 `grep -cE "@(Get|Post|Put|Delete|Patch)Mapping"` = 199 相等），即接口已自描述；本文补充的是自描述覆盖不到的横切约定与坑。
>
> **分工**：面向前端/联调的**入口导读**在产品线 [`README.md`](./README.md)（怎么读、按业务场景导航、响应信封与错误码的产品视角摘要、需求侧诉求清单）；该文件**不重述端点表**。
> 本文是**逐端点的权威契约清单**（参数载体、载荷类型、每个端点的缺陷与不一致）。任何端点信息与本文冲突时，**以本文为准**并回改导读。

## 1. 通用约定

| 项 | 值 |
|---|---|
| Base URL | `http://localhost:8082`（`server.port=8082`，**无 context-path**） |
| 路径前缀 | 全部 `/api/**` |
| Content-Type | `application/json`（但**大量写接口实际接收 query/form 参数**，见 §3） |
| 字符/时间 | 未配置全局时区与日期格式；epoch-millis 与 ISO 字符串并存 |
| 版本化 | **无**（无 `/v1`、无 Header 版本协商） |
| 分页/排序/筛选 | ~~**完全没有**：全仓无 `Pageable`/`Page`/`page`/`size`/`sort`，列表接口返回无界 `List<T>`~~ → 🔄 **2026-10-09 复测已失效**：客户/订单/商品/物流/财务/仓储/采购等列表均带 `page`/`size` 查询参数，出参形状 `data.{content,total,page,size,totalPages}`。**`page` 从 0 起**（`docs/tech/backend-standards.md` §107「从 0 起，size 上限 200」，`docs/api/api.md` 各端点表里的「默认 `0`」与此一致；user-web 在请求前做 `page - 1`，所以传 `page=1` 拿到的是**第 2 页**，写探针时不要按 1 起算） |
| `tenantId` 参数语义 | 🔄 **2026-10-09（ISS-120 / R-32）**：`tenantId` 缺省时一律取登录会话的租户；字面量 **`default-tenant` 被服务端归一化为「未指定」**（该值在 `tenants` 表里没有行，是历史幻影桶），等价于不传。**写**＝落登录租户；**读**＝按登录租户收敛，平台管理员不带该参数＝跨租户全量。**显式传真实他租户**仍 `400 TENANT_ACCESS_DENIED`；平台管理员在 Role/User/Shop 上带该字面量得到 `400「必须显式指定 tenantId」`而不是空列表 |

## 2. 响应信封 `Result<T>`

`site.hexaarch.ecommerce.logistics.interfaces.common.Result<T>`，字段仅三个：

```json
{ "code": "200", "message": "success", "data": { } }
```

- `code` 是**字符串**，不是数字。工厂方法：`success(data)`→`"200"`、`success()`→`"200"`+`data:null`、`fail(msg)`→`"400"`、`error(msg)`→`"500"`、`error(code,msg)`、`error(code,msg,data)`。
- **没有** `success: boolean`、**没有** `traceId`。前端只能靠 `code === "200"` 判成功（`user-web/src/services/api.js` 的响应拦截器正是这么做的）。
- ⚠️ 后端单测普遍断言 `jsonPath("$.code").value(200)`（int），与 String `"200"` 口径冲突——见 [testing.md](../testing.md)。

### 三种封装写法并存（同一系统内不一致）

| 写法 | 控制器 | HTTP 状态 |
|---|---|---|
| `ResponseEntity<Result<?>>` + 无 try/catch | Auth、User、Role、Tenant | 由代码/全局处理器决定 |
| `ResponseEntity<Result<T>>` + 方法内 try/catch 手工映射状态码 | PlatformIntegration | 200 / 400 / 404 / 500 |
| **裸 `Result<T>`**（不包 `ResponseEntity`） | Customer、Finance、Logistics、Order、Product、Purchase、Warehouse | **HTTP 恒 200，即使业务失败**（Customer 模块 404/400 由 `GlobalExceptionHandler` 兜底，业务错误经 `ValidationException`/`EntityNotFoundException` 正确映射） |

后果：调用方不能只看 HTTP 状态判断成败，必须解析 `code`；而第三种写法里 ~~Finance/Purchase 还把异常吞成 `HTTP 200 + code "500"`，并把 `e.getMessage()` 直接拼进响应（内部信息泄漏）~~ → 🔄 **2026-03-22 复测**：**`PurchaseController` 已 0 处 try/catch**（`:37-40` 类注释记明改由 `GlobalExceptionHandler` 映射 400/404），该形态只剩 **`FinanceController` 的 8 处**（`:70,112,129,146,165,180,195,210` → `Result.error("…: " + e.getMessage())` → HTTP 200 + `code "500"`）；全仓 9 个控制器共 **45 处** catch 仍在把 `e.getMessage()` 拼进响应体，逐处行号见 [error-codes.md §5](error-codes.md)。

### `data` 载荷类型不一致

多数接口直接序列化**领域聚合/实体**（~~`User`/`Role`/`Tenant`/`PlatformIntegration`/`LogisticsOrder`/`FinancialTransaction`/`PurchaseOrder`/`Warehouse`/`InventoryRecord`~~），仅 `OrderDto`、`ProductDto`、`CustomerDto`（Customer 模块全量 DTO 化）、~~`FinancialTransactionDto`（create）~~与三类导入的 `*JobDto/*PreviewResponse` 是 DTO。~~Finance 同一个控制器内 create 返 DTO、get 返领域对象~~。直接序列化聚合根存在懒加载序列化与内部字段泄漏风险。<br>🔄 **2026-03-22 逐控制器复测（按 `public Result<…>` 返回类型实数）**：**财务 9/9、物流 13/13、采购 12/12 已全量 DTO 化**（`FinancialTransactionDto` / `LogisticsOrderDto` / `PurchaseOrderDto`，采购另有 `TargetFieldDto` 状态字典）；`Order`/`Product`/`Customer`/`ShopWarehouse` 亦为 DTO。**仍直接出领域对象的只剩 6 类**：`User`、`Role`、`Tenant`、`PlatformIntegration`、`Warehouse`、`InventoryRecord`（`WarehouseController:73,85,92,99,105,128,136` 出 `Warehouse`，`:145,152,162,172,181` 出 `InventoryRecord`）。本文「仅 …… 是 DTO」的原枚举已漏掉本轮新增的三个域，故整体重写。

## 3. 请求参数承载方式不统一

| 方式 | 端点 |
|---|---|
| JSON `@RequestBody` | `POST /api/orders`、`POST`/`PUT /api/platform-integrations`、`POST /api/warehouses/{id}/inventory/move`、`POST /api/users`、`PUT /api/users/{id}`、`POST /api/roles`、`PUT /api/roles/{id}/permissions`、`POST /api/tenants`、`PUT /api/tenants/{id}`、`POST /api/tenants/{id}/admin`、`/api/auth/*`、🔄 **2026-03-22 由下方 query 行迁入 7 条，另加本轮新增的 2 类 body 端点**：`POST /api/purchases`（`PurchaseController:65`）、`POST /api/purchases/{id}/items`（`:130`）、`PUT`/`DELETE /api/purchases/{id}/items/{itemId}`（`:146`，新增）、`POST /api/purchases/{id}/approve\|reject`（`:180,:196`）、`POST /api/finance/transactions`（`FinanceController:58`）、`POST /api/logistics`（`LogisticsController:67`）、`PUT /api/logistics/{id}/status`（`:117`）、`POST /api/products`（`ProductController:82`）、`POST /api/warehouses`（`WarehouseController:72`） |
| **query/form 参数**（`@RequestParam` 或裸参数） | ~~`POST /api/finance/transactions`、`POST /api/logistics`、`PUT /api/logistics/{id}/status`、`POST /api/products`、`POST /api/purchases`、`POST /api/purchases/{id}/items`、`GET /api/purchases?status=`、`POST /api/warehouses`、`POST /api/warehouses/{id}/inventory/count`、`PUT /api/orders/{id}/status`、`GET /api/users?tenantId=`、`GET /api/roles?tenantId=`~~ → 🔄 **2026-03-22 复测：12 条里只剩 5 条成立**——`POST /api/warehouses/{id}/inventory/count`（`:171`）、`PUT /api/orders/{id}/status`（`:110`）、`GET /api/users?tenantId=`、`GET /api/roles?tenantId=`、以及各 `GET` 列表的查询串（含 `GET /api/purchases`，`:80-90` 全为可选 query）。其余 7 条已改为 `@RequestBody`，见上一行。 |

约定：**状态变更类操作用 `PUT` + 动词子路径**（`/activate`、`/suspend`、`/approve`、`/cancel`、`/lock`），而非 PATCH 或 body 内命令字段；平台集成的状态变更却用 `POST`（`/activate`、`/deactivate`）——动词子路径的 HTTP 方法本身也不统一。

## 4. 端点清单

> ⚠️ **2026-01-25 计数对账**：下列 §4.1–§4.14 各节标题的计数来自较早基线，实测已变的以括注为准（10-02 合计 196 → 10-03 合计 198 → 10-03 三测合计 199 → **🔄 10-04 合计 202**，全量见 [§4B](#4b-全量端点清单2026-01-25-由注解扫描生成)）：
> Auth `3→4`、Finance `8→9`、Logistics `5→13`（另有独立的 `LogisticsChannelController` 7）、Order `4→9`、Warehouse `6→13`（另有 `ShopWarehouseController` 5）、PlatformIntegration `10→12→14→15`（2026-03-08 新增 `test-connection`、`push-fulfillment`、`GET /auth-specs`）；
> **完全未收录的 6 个控制器**（~~合计 57 个端点~~ → 复算 **56**：11+12+11+10+7+5，原 57 为加法笔误）：OrderImport 11、LogisticsImport 12、FinanceImport 11、WarehouseImport 10、LogisticsChannel 7、ShopWarehouse 5（其中前四个是导入上下文，形态与已收录的 `ProductImportController`/`CustomerImportController` 一致）。
> 🔎 **2026-01-25 逐文件计数对账**：20 个控制器的 `@*Mapping` 注解数 = **196**，与 §4.1–§4.14 已收录的 14 个（**140**）+ 上述 6 个（**56**）之和一致；各节标题计数（Auth 4 / User 11 / Role 10 / Tenant 9 / Customer 9 / Finance 9 / Logistics 13 / Order 9 / Product 12 / Purchase 9 / Warehouse 13 / PlatformIntegration ~~12~~ **14** / ProductImport 10 / CustomerImport 10）经复测**全部与代码一致**。<br>🔄 **2026-03-08 复算**：注解数 198 = §4.1–§4.14 收录 14 个（142）+ 未收录 6 个（56）；当时的增量来自 `PlatformIntegrationController` 的 2 个新端点（`test-connection`、`push-fulfillment`）。<br>🔄 **2026-03-08 三测**：注解数 **199** = §4.1–§4.14 收录 14 个（**143**）+ 未收录 6 个（**56**），第 3 项增量是 `PlatformIntegrationController.java:59` 的 `GET /auth-specs`（`@Operation(summary = "对接协议凭据要求")`）。

标注：🔒 = 受 `/api/** authenticated()` 保护（但见 §6，该保护当前实际无效）；🌐 = permitAll；📱 = user-web 前端已调用。

### 4.1 AuthController `/api/auth`（3 → 实测 4）

| 方法 | 路径 | 入参 | data | 说明 |
|---|---|---|---|---|
| POST 🌐 | `/api/auth/login` | body `{username,password}` | `{token}` | 走 `AuthenticationManager`，签发 JJWT |
| ~~POST 🌐 | `/api/auth/refresh` | body `{username}` | `{token}` | ⚠️ 用 **username** 换新令牌，不校验旧令牌~~ |
| POST 🌐 | `/api/auth/refresh` | body `{refreshToken}` | `{token,refreshToken,username,tenantId,roles}` | ~~用 username 换新令牌、不校验旧令牌~~ **2026-01-25 已改**：`AuthController.java:95-101` 先取 `refreshToken` 并 `validateToken()` 验签+验过期，不接受裸 username（`:205-212` 的 `RefreshTokenRequest`）。⚠️ 残留 2 点：①**不轮换**——`:128` 把**同一个** refreshToken 原样回传，服务端无撤销表；② access 与 refresh **同为 24h**（`application.yml:80`/`:82`），没有短期 access。另：用户名换出的新令牌会**重新读库**取当前角色/权限（`:108-127`），所以改授权后刷新即生效。 |
| POST 🌐 | `/api/auth/logout` | — | `"登出成功"` | 仅 `SecurityContextHolder.clearContext()`（`:137-139`）；JWT 无黑名单，客户端仍持有有效令牌 |
| GET 🔒 | `/api/auth/me` | — | 当前用户资料 + `tenantId` + 角色/权限 | `:146` 从 `SecurityContextHolder` 取用户名再查库；未认证时抛 `BusinessException("未登录或会话已失效")`（`:151`）→ 由路径规则 `authenticated()`（`SecurityConfig.java:65`）先拦为 403 |

✅ **2026-01-25 补：本节此前漏计 `/api/auth/me`**（标题写「实测 4」却只列 3 行）；[§4B](#4b-全量端点清单2026-01-25-由注解扫描生成) 第 265-268 行的 4 行才是完整口径。`user-web` 侧 `authApi` 也正好 4 个方法（`api.js:83-88`，含 `getMe`）。

### 4.2 UserController `/api/users`（11）🔒

| 方法 | 路径 | 入参 | 说明 |
|---|---|---|---|
| POST 📱 | `/api/users` | body `{tenantId,username,password,email,phone,firstName,lastName}` | 建用户 |
| GET 📱 | `/api/users?tenantId=` | query **必填** | 按租户列用户 |
| GET 📱 | `/api/users/{id}` | path | 详情 |
| PUT 📱 | `/api/users/{id}` | body `{email,phone,firstName,lastName}` | 更新 |
| PUT 📱 | `/api/users/{id}/password` | body `{password}` | ⚠️ 改他人密码无需验证旧密码、无权限校验 |
| PUT 📱 | `/api/users/{id}/activate` · `/deactivate` · `/lock` | path | 状态变更 |
| DELETE 📱 | `/api/users/{id}` | path | 硬删除 |
| POST 📱 | `/api/users/{id}/roles` | body `{roleId}` | ⚠️ 提权面：无权限校验 |
| DELETE 📱 | `/api/users/{id}/roles/{roleId}` | path | 移除角色 |

### 4.3 RoleController `/api/roles`（10）🔒

| 方法 | 路径 | 入参 |
|---|---|---|
| POST | `/api/roles` | body `{tenantId,name,description}` |
| POST | `/api/roles/system` | body `{name,description}` |
| GET | `/api/roles?tenantId=` / `/api/roles/system` / `/api/roles/{id}` | — |
| PUT · DELETE | `/api/roles/{id}` | body `{name,description}` |
| POST · DELETE · PUT | `/api/roles/{id}/permissions` | body `{permission}` / path `{permission}` / body `{permissions:[...]}` |

权限点是**自由字符串**，无字典、无校验。

### 4.4 TenantController `/api/tenants`（9）🔒 + `hasRole("PLATFORM_ADMIN")`

`POST /api/tenants`、`GET /api/tenants`（列全部）、`GET/PUT/DELETE /api/tenants/{id}`、`PUT .../activate|suspend|deactivate`、`POST /api/tenants/{id}/admin`（body 同 CreateUserRequest）。

~~⚠️ **当前整块不可用**：`JwtAuthenticationFilter` 构造认证对象时 authorities 恒为空列表，`hasRole("PLATFORM_ADMIN")` 永不满足 → `/api/tenants/**` 恒定 403（见 §6）。~~
✅ **2026-01-25 实测可用**：`GET /api/tenants` 返回 **200** + 全部租户（仍**无分页**，是 `List`）。要跑通这块有两个前提，都已修：① authorities 不再恒空（ISS-041——token 带 `roles`/`permissions` claim，`JwtAuthenticationFilter.java:59-60` 按 `ROLE_` 前缀重建）；② `admin` 真正持有 `PLATFORM_ADMIN` 角色（**ISS-061**——种子初始化此前静默失败，所以即便 ① 修好，`admin` 依旧 403）。
⚠️ 未认证/角色不符时的出口形态：**403 + 空 body**（`SecurityConfig.java:62-67` 没有 `exceptionHandling()`），不是 401、也没有统一信封 → 前端按 `code` 判定的分支在这里全部失效（ISS-032/ISS-017/ISS-058）。

### 4.5 CustomerController `/api/customers`（9）🔒 · 裸 `Result<T>` 📱

| 方法 | 路径 | 入参 | data |
|---|---|---|---|
| GET 📱 | `/api/customers` | query 可选 `tenantId,customerId,customerName(模糊),email(模糊),phone(模糊),status,sourcePlatform,keyword,createdFrom,createdTo,page(0起),size(默认10),sort(默认 createdAt,desc)` | `PageResult<CustomerDto>` |
| GET 📱 | `/api/customers/status-options` | — | `List<{code,label}>`（状态字典） |
| GET 📱 | `/api/customers/{id}` | path（字符串业务 ID `CUST-xxxxxxxx`） | `CustomerDto`；不存在走 `GlobalExceptionHandler` → 404 |
| POST 📱 | `/api/customers` | body `CustomerCreateRequest{tenantId,customerName(必填),email(可选,合法且唯一),phone,address,company}` | `CustomerDto` |
| PUT 📱 | `/api/customers/{id}` | body `CustomerUpdateRequest{customerName,email,phone,address,company}`，空字段保持原值 | `CustomerDto` |
| PUT 📱 | `/api/customers/{id}/activate` · `/deactivate` · `/suspend` | path | `CustomerDto` |
| DELETE 📱 | `/api/customers/{id}` | path | `CustomerDto`（`status=DELETED`，**软删除**） |

响应统一 `CustomerDto`（不直接序列化聚合根）。校验在应用/领域服务层（项目未启用 Bean Validation）：客户名必填、邮箱正则校验+全局唯一（小写归一）。「沟通记录」「客户分类」「地址簿」仍无接口（见 [spec/customer.md](../spec/customer.md)）。

### 4.6 FinanceController `/api/finance/transactions`（8 → 实测 9）🔒 · 裸 `Result`

| 方法 | 路径 | 入参 |
|---|---|---|
| POST | `/api/finance/transactions` | query 必填 `type,amount,currency,referenceId,referenceType`，可选 `description` |
| GET | `/api/finance/transactions/{transactionId}` | path |
| GET | `/api/finance/transactions/by-status?status=` · `/by-type?type=` · `/by-reference?referenceId=&referenceType=` | query 必填 |
| PUT | `/api/finance/transactions/{transactionId}/complete\|fail\|cancel` | path |

⚠️ 无「删除/修改交易」、无「对账」「费用核算」「报表生成」端点（README 声称有）。

### 4.7 LogisticsController `/api/logistics`（5 → 实测 13）🔒 · 裸 `Result`

| 方法 | 路径 | 入参 |
|---|---|---|
| POST | `/api/logistics` | query 必填 `tenantId,orderId,logisticsChannelId` |
| GET | `/api/logistics/{id}` · `/api/logistics/order/{orderId}` · `/api/logistics`（**全部、跨租户**） | path/— |
| PUT | `/api/logistics/{id}/status?status=` | query 必填（领域 `LogisticsStatus`） |

⚠️ 「物流渠道管理」「物流标签生成」无端点——渠道只有表和仓储，没有 API；面单只能通过 `/status` 置为 `LABEL_GENERATED`，并不产出面单内容。

### 4.8 OrderController `/api/orders`（4 → 实测 9）🔒 · 裸 `Result<OrderDto>` 📱

| 方法 | 路径 | 入参 |
|---|---|---|
| POST 📱 | `/api/orders` | body `CreateOrderDto{orderNumber,customerId,customerName,customerEmail,shippingAddress,paymentInfo,orderItems[]}` |
| GET 📱 | `/api/orders/{id}` | path |
| PUT 📱 | `/api/orders/{id}/status?status=` | query 必填 |
| GET 📱 | `/api/orders/customer/{customerId}` | path |
| GET 📱 | `/api/orders` | query `tenantId`,`orderNumber`(模糊),`customerId`,`customerName`(模糊),`status`,`platform`,`keyword`,`startDate`,`endDate`,`page`(0起),`size`(默认10) → `PageResult<OrderDto>`（`:64-81`；共 11 个 query 参数，其中 9 个筛选条件 + `page/size`） |
| GET 📱 | `/api/orders/statuses` | — → `List<TargetFieldDto>`（`:84`，`OrderStatus` 全量枚举 + 中文 label，前端下拉字典） |
| GET 📱 | `/api/orders/number/{orderNumber}` | path（`:94`；**不按租户过滤**，见下） |
| POST 📱 | `/api/orders/{id}/cancel` | path（`:119`；聚合内校验「已发货/已完成不可取消」） |
| DELETE 📱 | `/api/orders/{id}` | path（`:127`，返回 `Result<Void>`） |

> 上表后 6 行为 2026-01-25 补录（原标题「4 → 实测 9」，此前只列了 4 行）。`/api/orders/{id}` 详情端点见第 2 行，它与 `/number/{orderNumber}` 同样**不接受 `tenantId` 入参**。

~~⚠️ `createOrder` 在 `OrderController.java:45` **硬编码 tenant `"default-tenant"`**，忽略令牌与调用方；`CreateOrderDto` 的 `orderNumber/customerName/customerEmail` 三字段未被使用。~~
✅ **`POST /api/orders` 原先无法落库的缺陷已修（ISS-001）**：`Order.create`（`Order.java:128/162/505`）改为 `hasText(orderNumber) ? orderNumber.trim() : generateOrderNumber()`，编号在聚合内部生成，不再依赖调用方传入；`OrderJpaEntity` 上的 `order_number unique/nullable=false` 约束因此可满足。2026-01-25 在真实 MySQL 上跑通建单链路（编号规则的产品口径见 [`../qa/known-issues.md`](../qa/known-issues.md) ISS-001）。
✅ **2026-01-25 复测：该处已改为上下文解析**。`createOrder` 现在是 `resolveTenant(null)`（`:51`），`resolveTenant`（`:163-171`）按「请求参数 → `TenantContext` → `OrderApplicationService.DEFAULT_TENANT_ID`」三级回退，`default-tenant` 字面量已从控制器源码消失（`interfaces` 层只剩 ~~2 处：`PurchaseController.java:47` 的 `@RequestParam(defaultValue=...)` 与 `CreateFinancialTransactionRequest.java:36` 的 `@Schema` 示例~~ **0 处行为点**，🔄 2026-03-22 复测：剩下的 3 处全是 DTO 里的文案，见下）。🔄 2026-03-22 复测：采购侧那处 `defaultValue` 已删除，另有 3 处 `default-tenant` 只出现在 DTO 的 Javadoc/`@Schema` 文案里（`CreateProductRequest:12`、`CustomerCreateRequest:13`、`CreateFinancialTransactionRequest:36`），不构成行为。`orderNumber` 也不再必填（见 ISS-001，聚合内生成）。⚠️ 残留：`customerName/customerEmail` 仍只是透传、不参与任何校验或去重；**列表/详情/按单号查询三处**若入参不带 `tenantId` 且令牌无租户 claim，仍会落到 `default-tenant` 全集（ISS-050 读路径未隔离）。
⚠️ 无「订单列表」端点（既无 list-all 也无分页），无 `/cancel`（只能 `PUT /status?status=CANCELLED`），无订单同步端点。**→ 2026-01-25 三条全部失效**：列表见上（`:64` 分页 + 9 个筛选）、`POST /api/orders/{id}/cancel` 已存在（`:119`）、订单同步在 `PlatformIntegrationController.java:182` 的 `sync-orders`（另有 OrderImport 11 个端点，见 [§4B](#4b-全量端点清单2026-01-25-由注解扫描生成)）。本行与 [§9](#9-前后端契约对齐) 的「`GET /api/orders` 现已存在」口径一致（此前两处互相矛盾，以本行更正后为准）。

### 4.9 ProductController `/api/products`（12）🔒

| 方法 | 路径 | 入参 |
|---|---|---|
| GET | `/api/products` | query `tenantId`,`keyword`,`categoryName`,`active`,`sourcePlatform`,`skuCode`,`page`(0起),`size`(默认10),`sort`(默认 `createdAt,desc`) → `PageResult<ProductDto>` |
| POST · PUT `/{id}` | `/api/products` | **body** `CreateProductRequest` / `UpdateProductRequest`（含嵌套 SKU、属性） |
| GET | `/api/products/{id}` · `/api/products/sku/{skuCode}` | path（`tenantId` 可选 query） |
| PUT · DELETE | `/api/products/{id}` | body 更新体 / query `tenantId` |
| PUT | `/api/products/{id}/activate` · `/deactivate` | path |
| POST · DELETE | `/api/products/{id}/skus` · `/{id}/skus/{skuCode}` | body `SkuRequestDto` |
| PUT · DELETE | `/api/products/batch/active` · `/api/products/batch` | body `BatchActiveRequest` / `BatchDeleteRequest` |

✅ 商品列表分页、SKU 增删、批量改状态/批量删除已落地（本节旧版记录的「无列表、无 SKU 端点、出参裸 `Result<ProductDto>`」已不成立）。
⚠️ `size` 未见上限校验；`sort` 直传 `Sort.by()` 且字段名不做白名单 → 非法列会落到 500（目标态见 [performance-capacity.md §4](../tech/performance-capacity.md)）。
⚠️ `category` 仍由 `categoryName` 现场 `UUID` 拼装，无分类管理端点。

### 4.10 PurchaseController `/api/purchases`（~~9~~ **12**）🔒 · 裸 `Result`

🔄 **2026-03-22 复测：本节整体重写。** 实测 **12 个端点 / 12 处方法级 `@PreAuthorize`**（`purchase:read` 3 + `purchase:write` 9）、**0 处 try/catch**（类注释 `:37-40` 记明了改由 `GlobalExceptionHandler` 映射 400/404）、创建与加项**已改 JSON 请求体**、列表**已改服务端分页**。原表口径全部过期：

| 方法 | 路径 | 入参 | 权限点 |
|---|---|---|---|
| POST | `/api/purchases` | **body** `PurchaseCreateRequest{tenantId,supplierId,supplierName,warehouseId,items[]}`（单头+明细一次提交，`:65`） | `purchase:write` |
| GET | `/api/purchases` | query 全部可选：`tenantId`,`purchaseOrderId`(模糊),`supplierId`,`supplierName`(模糊),`warehouseId`,`status`,`minTotalAmount`,`startDate`,`endDate`,`page`(0起),`size`(默认10) → `PageResult<PurchaseOrderDto>`（`:79-95`） | `purchase:read` |
| GET | `/api/purchases/statuses` | — → `List<TargetFieldDto>` 状态字典（`:103`，新增） | `purchase:read` |
| GET | `/api/purchases/{purchaseOrderId}` | query `tenantId` 可选 + path | `purchase:read` |
| POST | `/api/purchases/{purchaseOrderId}/items` | query `tenantId` 可选 + **body** `PurchaseItemRequest{skuCode,productName,quantity,unitPrice}`（`:130-138`） | `purchase:write` |
| PUT | `/api/purchases/{purchaseOrderId}/items/{itemId}` | path ×2 + query `tenantId` + **body** `PurchaseItemRequest`（`:146`，新增：服务端重算小计与总额） | `purchase:write` |
| DELETE | `/api/purchases/{purchaseOrderId}/items/{itemId}` | path ×2 + query `tenantId`（`:162`，新增） | `purchase:write` |
| PUT | `/api/purchases/{id}/approve\|reject` | query `tenantId` 可选 + **body**（`required=false`）`PurchaseApprovalRequest{approvedBy,comment}`（`:180,:196`）：`approvedBy` 缺省时 `resolveApprover`（`:277-287`）取登录账号、匿名记 `SYSTEM`；**拒绝必须填 `comment`**（`:199-201` 抛 `ValidationException`） | `purchase:write` |
| PUT | `/api/purchases/{id}/in-transit\|delivered\|cancel` | path + query `tenantId` 可选 | `purchase:write` |

- ~~⚠️ **语义 bug**：`GET /api/purchases` 不带 `status` 时不返回全部，而是回退到查 `PENDING`（源码注释「简化处理」）。~~ → 🔄 **2026-03-22 已修**：改为 `searchPurchaseOrders`（`PurchaseApplicationService.java:136-144`，`readOnly`）→ `PurchaseOrderSpecification.withQuery`，条件按 `hasText`/非空逐项拼装，`status` 为空即不加该谓词、返回全集。
- ~~⚠️ 仍成立：**无「采购入库」专用端点**。到货只把状态推到 `DELIVERED` 并登记 `PurchaseOrderStatusChangedEvent`（`PurchaseOrder.java:211`）——**仓内没有 `PurchaseOrderDeliveredEvent` 这个类**，采购域一共只有 2 个事件类型（Created / StatusChanged）。该事件经 `DomainEventPublisher.java:123` 外发到 `purchase-events` topic，但**本仓库没有任何消费者**（4 个 `RocketMQMessageListener` 分别订阅 `order-events`/`inventory-events`/`logistics-events`，`EventHandler` 的 4 个 `@EventListener` 也不接采购事件）⇒ 到货不会自动入库。见 [../tech/inventory/tech-debt-register.csv](../tech/inventory/tech-debt-register.csv) TD-14 与 [../qa/known-issues.md](../qa/known-issues.md) ISS-062。~~ → 🔄 **2026-03-22 同日四测：本条前半段成立、结论段已失效**。**仍然没有**独立的「采购入库」端点、**仍然没有** `PurchaseOrderDeliveredEvent` 类、`purchase-events` topic **仍然无仓内消费者**——但「⇒ 到货不会自动入库」错了：`PUT /api/purchases/{id}/delivered`（`PurchaseController:226-233`）现在在**同一事务里同步编排**入库与应付：`PurchaseApplicationService.markPurchaseOrderAsDelivered()`（`:256-262`）→ `receiveIntoWarehouse()` 逐行 `inventoryIn`（`:281-293`）→ `createPurchasePayable()` 记 `EXPENSE` + `reference_type=PURCHASE_ORDER`（`:299-310`）。`@Operation` 描述（`:223-224`）与类注释（`:30-32`）都写明了这一点，并声明事件「只作对外订阅通道，仓内不重复消费」。测试锚点 `PurchaseApplicationServiceTest:74-113`（3 个 `delivered*` 用例）。**副作用面**：财务流水因此多了第三个写入方（此前只有手工 + 平台导入），且该编排**没有幂等/乐观锁保护**（全仓 `@Version` 0 处，`financial_transactions` 无唯一键）——见 [../spec/purchase.md §8](../spec/purchase.md)。TD-14/ISS-062 的口径以各自文件为准。
- 租户解析：`resolveTenant`（`:264-272`）＝「请求参数 → `TenantContext` → `PurchaseApplicationService.DEFAULT_TENANT_ID`」三级回退，**与 `OrderController` 同构**；`@RequestParam(defaultValue="default-tenant")` 字面量已消失。前端 `PurchaseList`/`CreatePurchase` 本轮已停止下发 `tenantId`（TD-04 的做法模板）。

### 4.11 WarehouseController `/api/warehouses`（6 → 实测 13）· 裸 `Result` · ⚠️ **无鉴权**

🔄 **2026-03-22 复测：本节原记的「🔒」与入参形态均已过期。** 实测 13 个端点、**0 处 `@PreAuthorize`**（全类只有 `@RequestMapping("/api/warehouses")`（`:31`）；它属于 §8 说的「10 个未覆盖控制器」之一，任何登录用户都能建/删仓库与改库存）。入参已改为**嵌套静态类请求体**（不是独立 DTO 文件）：`CreateWarehouseRequest`（`:38`，`POST` 用 `:73`）、`UpdateWarehouseRequest`（`:47`）、`AddLocationRequest`（`:55`）、`InventoryMoveRequest`（`:62`）。

| 方法 | 路径 | 入参 |
|---|---|---|
| POST | `/api/warehouses` | ~~query `tenantId`(默认),`warehouseName`,`warehouseCode`,`address`,`capacity:int`~~ → **body** `CreateWarehouseRequest`（`:73`） |
| GET | `/api/warehouses/{id}` · `/api/warehouses`（全部） · `/api/warehouses/code/{code}` | path（`/code/{code}` 见 `:91`，旧表未列） |
| PUT | `/api/warehouses/{id}` · `/{id}/status` | path + **body** `UpdateWarehouseRequest`（`:105-107`）；`/{id}/status` 则是 query `active:boolean`（`:130`，**不是** body） |
| DELETE | `/api/warehouses/{id}` | path（`:120`） |
| POST | `/api/warehouses/{id}/locations` | path + **body** `AddLocationRequest`（`:136`）——~~"无库位（locations）管理端点"~~ → 🔄 **该句已失效**，新增库位端点已存在；仍缺的是库位的改/删与查询 |
| GET | `/api/warehouses/{warehouseId}/inventory` | path（`:145`） |
| POST | `/api/warehouses/{warehouseId}/inventory/in` · `/out` | path + **body** `InventoryMoveRequest`（`:152`、`:162`，旧表未列这两个端点；取 `sku` 优先、回落 `productId`，再交 `inventoryIn/inventoryOut`） |
| POST | `/api/warehouses/{warehouseId}/inventory/count` | path + query `productId`,`actualQuantity:int`（`:172-175`，**仍是 query**） |
| POST | `/api/warehouses/{warehouseId}/inventory/move` | path + **body `InventoryMovement`**（`:181-183`） |

~~⚠️ `InventoryMovement` 是私有构造 + Builder、全 final 字段、无无参构造、无 `@JsonCreator` 的值对象（`domain/warehouse/valueobject/InventoryMovement.java:40,68` 两个构造器均 `private`）→ **Jackson 无法反序列化，该端点大概率返回 400**。同一次复测确认：`/inventory/in|out` 之所以能用，正是因为换了专门的 `InventoryMoveRequest` DTO，而 `move` 端点没换 ⇒ 四个移动类型（INBOUND/OUTBOUND/ADJUSTMENT/TRANSFER）在 HTTP 层仍不可达，只能在服务内/测试里调（ISS-012/059/060 的修复与实测都发生在应用服务层）。~~

🔄 **2026-03-22 三次复测：上面这段结论作废，方向反了。** `InventoryMovement` 现在**带 `@JsonCreator`**——`domain/warehouse/valueobject/InventoryMovement.java:39` 的私有构造标注了 `@com.fasterxml.jackson.annotation.JsonCreator`，9 个参数逐个标 `@JsonProperty`（`:41-49`），这是**全仓唯一**一处 `@JsonCreator`（`grep -rn JsonCreator backend/src/main/java` = 1，ISS-052 记的也正是这一处，所以 ISS-052「事件类没有」的结论不受影响）。⇒ `POST /{warehouseId}/inventory/move` **在 HTTP 层可达**，四个移动类型都能从 curl 走通；`docs/qa/smoke-test-checklist.md` 4.5 的「实测 200」与此自洽，旧文案里「端点可达但 400/打不通」是本节此前唯一的错处。三条真实约束（照做即可）：① body **必须含 `warehouseId`**（构造里 `Objects.requireNonNull`，`:51`；`productId`/`sku`/`movementType` 同样必填（`:53-56`）），但它**不参与定位**——实际按路径变量执行，两者不一致时静默以路径为准（`WarehouseApplicationService:201-232`）；② `movementType=TRANSFER` 时 `targetWarehouseId` 必填且不能等于源仓（`:57-61` 抛（`:58` requireNonNull、`:59-60` 源目标同仓抛 `IllegalArgumentException`） → 400）；③ 响应只回目标仓入库那条 `InventoryRecord`。
出参仍直接序列化领域对象：6 个 `Result<Warehouse>`（`:73,:85,:92,:105,:128,:136`）+ 4 个 `Result<InventoryRecord>`（`:152,:162,:172,:181`），另有 `Result<List<Warehouse>>`（`:99`）与 `Result<List<InventoryRecord>>`（`:145`）、`Result<Void>`（`:120`）——ISS-063 未收口的域之一。

### 4.12 PlatformIntegrationController `/api/platform-integrations`（10 → 2026-01-25 实测 12 → 本轮实测 14 → **同日三测 15**）🔒

> 口径 **2026-03-08 工作树**。本节早期版本的两条 ⚠️ 已失效：① ~~"请求体与响应体直接使用领域聚合 `PlatformIntegration`，会把 `apiKey`、`apiSecret` 原样收发"~~ → **已改 DTO**；② ~~"没有按平台的真实 SDK/OAuth 对接，平台差异只体现在示例数据形状与字段别名表"~~ → **Shopify 已按官方 Admin REST 协议对接**（`X-Shopify-Access-Token` + `Link` 游标），按 `PlatformAuthType` 注册表分派。

| 方法 | 路径 | 入参 | `data` |
|---|---|---|---|
| GET | `/api/platform-integrations/auth-specs` 🆕 | — | `List<PlatformAuthSpecDto>`（字段 `authType/label/requiredFields/optionalFields/hostFromConfig/regionOptions/tokenGuide/docUrls/verified/note`；`verified=false` 表示规范未能从公开官方文档核实、因此没有适配器） |
| POST | `/api/platform-integrations` | body `PlatformIntegrationSaveRequest{tenantId,platformName,authType,apiKey,apiSecret,accessToken,refreshToken,tokenExpiresAt,apiVersion,storeUrl,status}` | `PlatformIntegrationDto`（**掩码**） |
| GET | `/api/platform-integrations/{id}` | path `id`（**UUID**） | `PlatformIntegrationDto` |
| GET | `/api/platform-integrations` | — | `List<PlatformIntegrationDto>`（全量数组，**无分页**） |
| PUT | `/api/platform-integrations/{id}` | body 同 POST | `PlatformIntegrationDto` |
| DELETE | `/api/platform-integrations/{id}` | path | `null` |
| POST | `/api/platform-integrations/{id}/activate` · `/deactivate` | path | `PlatformIntegrationDto` |
| POST | `/api/platform-integrations/{id}/sync-orders` | query `tenantId`,`orderNumber`,`updatedFrom`,`updatedTo`,`limit`,`useSampleData` | `OrderImportJobDto` |
| POST | `/api/platform-integrations/{id}/sync-customers` | query `tenantId`,`sourceType`(`CUSTOMERS`\|`ORDERS`),`keyword`,`limit`,`useSampleData` | `CustomerImportJobDto` |
| POST | `/api/platform-integrations/{id}/sync-products` | query `tenantId` | `ProductImportJobDto` |
| POST | `/api/platform-integrations/{id}/sync-logistics` | query `tenantId`,`orderNumber`,`trackingNumber`,`shippedFrom`,`shippedTo`,`limit`,`useSampleData` | `LogisticsImportJobDto` |
| POST | `/api/platform-integrations/{id}/sync-inventory` | — | `InventorySyncResult`（`Result<Object>` 弱类型） |
| **POST** | `/api/platform-integrations/{id}/test-connection` 🆕 | — | `PlatformConnectionTestResult{success,adapterType,latencyMs,message}` |
| **POST** | `/api/platform-integrations/{id}/push-fulfillment` 🆕 | body `PlatformFulfillmentPushRequest{logisticsOrderId,notifyCustomer}` | `PlatformFulfillmentResult{platformFulfillmentId,...}` |

实现要点与坑（核对自 `PlatformIntegrationController` / `PlatformIntegrationApplicationService`）：

- ✅ **出参不再含凭据明文**：`PlatformIntegrationDto` 只有 `apiKeyMasked`（前 3+`****`+后 4，≤7 位整体 `****`）+ `hasApiSecret`/`hasAccessToken` + `tokenExpiresAt`；`apiSecret`/`accessToken`/`refreshToken` **字段本身不在 DTO 上**。入参仍收明文（一次性提交后加密落库），`PUT` 时**留空 = 保留库里原值**（否则"不回显"会导致"改个字段丢密钥"）。
- ⚠️ **`test-connection` 失败也返回 200**（`data.success=false`），前端要读 `data` 而不是只看 HTTP 状态；它**不改 `status`**，也不参与 `activate` 门禁。
- ⚠️ **`sync-*` 是同步阻塞**：单请求内完成"外呼 + 映射 + 落库"，带重试的 Shopify 翻页可超过前端 10s 超时；⚠️ 客户端超时**不会回滚**服务端事务（数据可能已入库而用户看到失败）。
- ⚠️ **`useSampleData` 是客户端可控参数**：生产传 `true` 会导入示例数据。
- ⚠️ 错误口径三分：**未找到** → 404 + `Result.fail`；**`BusinessException`** → 400 + `Result.error(errorCode,msg)`；**其余 `Exception`** → 500 且把 `e.getMessage()` 拼进消息（M-08 泄漏面）。
- 🟡 **能力按协议不齐**（调用前先看 [spec §7.1](../spec/platform-integration.md)）：2026-03-08 深夜按端口重测的**适配器类**数为 `CUSTOM_SHOP` 7、`SHOPIFY`/`OZON`/`MAGENTO` 各 4、`EBAY_SELL`/`SHOPEE` 各 3、`AMAZON_SP_API`/`MERCADO_LIBRE`/`ALIEXPRESS` 各 2、`TIKTOK_SHOP`/`TEMU` **0**，另有 `SAMPLE` 6，**合计 37**（此前本节写的 41 把 4 个 `PlatformTokenExchange` 也算成了适配器，已在 spec §1 更正）。**客户 / 物流 / 店铺仓 / 财务四个端口只有 `CUSTOM_SHOP` 与 `SAMPLE` 有实现**，九家公开平台全无 ⇒ 对它们调 `sync-customers`、`sync-logistics` 会 `PLATFORM_ADAPTER_NOT_FOUND`。店铺仓与财务**在本控制器上没有同步端点**（本控制器只有 `sync-orders`/`sync-customers`/`sync-products`/`sync-inventory`/`sync-logistics` 五个，外加 `test-connection`/`push-fulfillment`），这两类数据只能走各自 `*/import` 端点的「按 `integrationId` 拉取」分支（`WarehouseImportApplicationService.java:345`、`FinanceImportApplicationService.java:418`），而那两条端口同样只有 `CUSTOM_SHOP`/`SAMPLE` 有实现 ⇒ 传真实平台的集成 ID 一样会 `PLATFORM_ADAPTER_NOT_FOUND`。Shopify 的客户数据只能走 `sourceType=ORDERS` 从订单地址萃取。`sync-inventory` 只对 `CUSTOM_SHOP` 可用（`PlatformIntegrationServiceImpl.java:181-185`），其余协议一律 `PLATFORM_INVENTORY_PUSH_UNSUPPORTED`。
- 🆕 **`GET /auth-specs` 是配置界面的契约**：由 `PlatformAuthCatalog.all()` 直接映射（`PlatformIntegrationController.java:59-62`，**不经应用服务、不读库**），给出每个 `authType` 的 `requiredFields`/`optionalFields`/`regionOptions`/`tokenGuide`/`docUrls`/`verified`/`note`。**它受类级 `@PreAuthorize` 保护**（`platform:manage` 或 `PLATFORM_ADMIN`），所以只读账号拿不到；`verified=false` 的两项（`TIKTOK_SHOP`、`TEMU`）是在明确告诉前端"填了也没有适配器"。
- 🔴 权限：类级 `@PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage')")`；`tenantId` 是**可选入参**，切面只对"形参名为 `tenantId` 且与登录租户不一致"生效（见 §4.0 与 ISS-050）。



### 4.13 ProductImportController `/api/products/import`（10）🔒

第三方平台商品导入（拉取 / 上传 → 字段映射 → 预览 → 批量导入 → 任务回溯）。

| 方法 | 路径 | 入参 | data |
|---|---|---|---|
| GET | `/api/products/import/target-fields` | — | `List<TargetFieldDto>`（14 个标准目标字段） |
| GET | `/api/products/import/transforms` | — | `List<TargetFieldDto>`（4 个转换规则） |
| GET | `/api/products/import/field-mappings` | query `platform`(**必填**)、`tenantId`(可选) | `List<FieldMappingDto>` |
| PUT | `/api/products/import/field-mappings` | body `FieldMappingSaveRequest` | `List<FieldMappingDto>`（按租户+平台整体覆盖保存） |
| POST | `/api/products/import/preview` | body `ProductImportPreviewRequest{tenantId,platform,content,fileName}` | `ProductImportPreviewResponse`（含逐行错误） |
| POST | `/api/products/import/upload` | **multipart** `file` + query `mode`(默认 `preview`，`import` 直接导入)、`tenantId`、`platform`、`updateExisting` | `Object`（preview → 预览体；import → `ProductImportJobDto`） |
| POST | `/api/products/import` | body `ProductImportRequest{tenantId,platform,rows,updateExisting,fileName}` | `ProductImportJobDto` |
| GET | `/api/products/import/jobs` | query `tenantId`、`page`(0起)、`size`(默认10) | `PageResult<ProductImportJobDto>` |
| GET | `/api/products/import/jobs/{jobId}` | path | `ProductImportJobDto`（含失败行明细） |
| GET | `/api/products/import/template` | — | **`text/csv`** + `Content-Disposition: attachment`（唯一非 `Result` 信封的端点，直接返回文件字节） |

实现要点与坑（核对自 `ProductImportController` / `ProductImportApplicationService`）：

- ⚠️ `preview` 与 `import` 捕获 `BusinessException` 后返回 `Result.error(e.getErrorCode(), ...)`，HTTP 仍 200；`upload`/`jobs`/`field-mappings` **不捕获** → 同类异常走 `GlobalExceptionHandler`。同一模块内两套错误路径，`code` 口径不一致（见 [error-codes.md §5](./error-codes.md)）。
- ⚠️ `upload` 里 `mode=import` 是「先 `preview()` 再 `importProducts()`」**两次调用**，`tenantId` 由客户端传参、`platform` 缺省落到 `FILE_PLATFORM` 常量；无幂等键，重复提交会建两个任务。
- ⚠️ `readContent` 把整个文件读进内存再按 UTF-8 解码 → 大文件与非法编码都会放大失败面；无大小上限、无类型白名单（仅按内容判断 JSON/CSV）。
- ⚠️ `Result<Object>`（`upload`）是弱类型出参，前端需按 `mode` 分支解析；`PageResult<T>` 首次进入响应信封，`data` 结构从「数组/对象」变成 `{content,total,page,size}`。
- ✅ `GET /template` 走 `ResponseEntity<ByteArrayResource>`，绕开 `Result` 是**正确**做法（文件下载不该被信封包裹），可作为其余导出端点的范式。

### 4.14 CustomerImportController `/api/customers/import`（10）🔒 · 裸 `Result<T>` 📱

第三方电商平台客户（买家）导入：客户/订单数据获取 → 字段映射 → 预览 → 批量导入 → 行级异常 → 任务化结果反馈。与 §4.13 商品导入同构，复用 `FieldMappingDto`/`TargetFieldDto`/`ImportJobStatus`。

| 方法 | 路径 | 入参 | data |
|---|---|---|---|
| GET 📱 | `/api/customers/import/target-fields` | — | `List<TargetFieldDto>`（9 个标准目标字段：`EXTERNAL_BUYER_ID/CUSTOMER_NAME/CUSTOMER_EMAIL/CUSTOMER_PHONE/COMPANY/COUNTRY/PROVINCE/CITY/ADDRESS`） |
| GET 📱 | `/api/customers/import/transforms` | — | `List<TargetFieldDto>`（4 个转换规则，与商品共用 `FieldTransform`） |
| GET 📱 | `/api/customers/import/field-mappings` | query `platform`、`tenantId`(可选) | `List<FieldMappingDto>` |
| PUT 📱 | `/api/customers/import/field-mappings` | body `FieldMappingSaveRequest` | `List<FieldMappingDto>`（按租户+平台整体覆盖） |
| POST 📱 | `/api/customers/import/preview` | body `CustomerImportPreviewRequest{tenantId,integrationId,platform,sourceType(CUSTOMERS\|ORDERS),keyword,orderNumber,updatedFrom,updatedTo,limit(默认50),page(默认1),useSampleData,content,rawRows,mappings,fileName}` | `CustomerImportPreviewResponse`（`sourceFields/mappings/rows(含逐行 errors)/rowErrors/warnings`） |
| POST 📱 | `/api/customers/import/upload` | **multipart** `file`(JSON/CSV/xlsx) + query `mode`(默认 `preview`；`import` 先预览再导入)、`tenantId`、`platform`、`sourceType`、`updateExisting` | preview → 预览体；import → `CustomerImportJobDto` |
| POST 📱 | `/api/customers/import` | body `CustomerImportRequest`（含预览行 `rows`；`rows` 为空则服务端重新走「拉取+映射」） | `CustomerImportJobDto` |
| GET 📱 | `/api/customers/import/jobs` | query `tenantId`、`page`(0起)、`size` | `PageResult<CustomerImportJobDto>` |
| GET 📱 | `/api/customers/import/jobs/{jobId}` | path | `CustomerImportJobDto`（含失败/跳过行明细） |
| GET 📱 | `/api/customers/import/template` | — | `text/csv` 模板（同样绕开 `Result` 信封） |

实现要点（核对自 `CustomerImportApplicationService` / `CustomerImportController`）：

- 数据获取双通道：`useSampleData=false` 时按 `integrationId` 解析集成，取 `storeUrl/apiKey/apiSecret` 发真实 HTTP（`GET {storeUrl}/customers`；`sourceType=ORDERS` 时 `GET {storeUrl}/orders` 再按「买家ID→邮箱→名称」分组去重萃取买家）；`useSampleData=true` 走各平台形状差异的示例数据。未配置 `storeUrl` 直接 HTTP 拉取会 400 并提示改用示例数据或文件上传。
- 字段映射：`CustomerImportTargetField.detectByAlias`（忽略大小写/连字符/下划线的别名精确匹配）自动识别 + 可按租户+平台保存/覆盖模板；`targetField` 为空 = 忽略该列；无法识别时还有 `detectFuzzy` 包含式兜底。
- 幂等 upsert：先按 `(tenantId, platform, externalBuyerId)` 匹配，未命中再按邮箱匹配；命中后 `mergeImportedProfile` **只补空不覆盖**，`updateExisting=false` 时改判为跳过（跳过行保留原因明细）。
- 异常处理为行级 try/catch：单行失败（名称为空、邮箱非法、邮箱冲突等）不中断批次；错误明细（行号、买家ID、字段、消息，消息截断 300 字符）落 `customer_import_job_errors`。
- ⚠️ `preview`/`import` 捕获 `BusinessException` 返回 `Result.error(code,msg)`（HTTP 200），与 `upload`/`jobs` 走全局处理器的口径与 §4.13 相同。
- ⚠️ `syncFromPlatform`（平台一键同步 `POST /api/platform-integrations/{id}/sync-customers`）内部 `updateExisting`/`enableIdempotency` 固定为 `true`。

## 4B. 全量端点清单（2026-01-25 由注解扫描生成）

> 生成方式：扫描 `interfaces/controller/*.java` 的 `@(Get|Post|Put|Delete|Patch)Mapping` + `@Operation(summary)` + 参数注解；
> 共 196 条 / 20 个控制器（2026-01-25 生成时）→ 2026-03-08 追加平台 2 条 = 198 条 → **同日三测补 `GET /auth-specs` = 199 条**，与 `grep -c '@Operation'`（199）及 `grep -cE "@(Get|Post|Put|Delete|Patch)Mapping"`（199）一致（两次独立测量相等；并且已用脚本把 199 行的「方法 + 路径」与代码逐条取差集，**两个方向都是空集**）。
> 「鉴权」列取方法级或类级 `@PreAuthorize`，为空即只受 `/api/** authenticated()` 路径规则保护。
> ⚠️ 本清单由脚本生成、可重跑校验，**不要手工编辑**；语义性坑点仍看 §4.1–§4.14 的手写注记。
> 🔄 **2026-03-08 例外说明**：`PlatformIntegrationController` 的 **3** 行（`GET /auth-specs`、`test-connection`、`push-fulfillment`）与 `POST` 那行的 `body` 类型（`PlatformIntegrationSaveRequest`，原写 `PlatformIntegration`）是**手工补的**，逐字核对自 `PlatformIntegrationController.java:57-62`（`auth-specs`）、`:284-298`（`test-connection`）、`:300-317`（`push-fulfillment`）的 `@Operation(summary)` 与 `@RequestBody` 形参；下次重跑注解扫描应得到同样的 **199** 行。

**AuthController**（4）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| POST | `/api/auth/login` | body `LoginRequest` | 用户登录 | `authenticated()` |
| POST | `/api/auth/refresh` | body `RefreshTokenRequest` | 刷新令牌 | `authenticated()` |
| POST | `/api/auth/logout` | — | 用户登出 | `authenticated()` |
| GET | `/api/auth/me` | — | 当前用户信息 | `authenticated()` |

**CustomerController**（9）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/customers` | query `tenantId:String`（选填） · query `customerId:String`（选填） · query `customerName:String`（选填） · query `email:String`（选填） · query `phone:String`（选填） · query `status:String`（选填） · query `sourcePlatform:String`（选填） · query `keyword:String`（选填） · query `createdFrom:String`（选填） · query `createdTo:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） · query `sort:String`（默认 `createdAt,desc`） | 分页查询客户 | `authenticated()` |
| GET | `/api/customers/status-options` | — | 客户状态下拉字典 | `authenticated()` |
| GET | `/api/customers/{id}` | path `id` | 根据ID获取客户信息 | `authenticated()` |
| POST | `/api/customers` | body `CustomerCreateRequest` | 创建客户信息 | `authenticated()` |
| PUT | `/api/customers/{id}` | body `CustomerUpdateRequest` · path `id` | 更新客户资料 | `authenticated()` |
| PUT | `/api/customers/{id}/activate` | path `id` | 激活客户 | `authenticated()` |
| PUT | `/api/customers/{id}/deactivate` | path `id` | 停用客户 | `authenticated()` |
| PUT | `/api/customers/{id}/suspend` | path `id` | 暂停客户 | `authenticated()` |
| DELETE | `/api/customers/{id}` | path `id` | 根据ID删除客户信息 | `authenticated()` |

**CustomerImportController**（10）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/customers/import/target-fields` | — | 标准目标字段列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import') |
| GET | `/api/customers/import/transforms` | — | 字段转换规则列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import') |
| GET | `/api/customers/import/field-mappings` | query `tenantId:String`（选填） · query `platform:String`（**必填**） | 查询字段映射 | hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import') |
| PUT | `/api/customers/import/field-mappings` | body `FieldMappingSaveRequest` | 保存字段映射 | hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import') |
| POST | `/api/customers/import/preview` | body `CustomerImportPreviewRequest` | 客户导入预览 | hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import') |
| POST | `/api/customers/import/upload` | multipart `MultipartFile` · query `mode:String`（默认 `preview`） · query `tenantId:String`（选填） · query `platform:String`（选填） · query `sourceType:String`（选填） · query `updateExisting:Boolean`（选填） | 上传文件导入 | hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import') |
| POST | `/api/customers/import` | body `CustomerImportRequest` | 批量导入客户 | hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import') |
| GET | `/api/customers/import/jobs` | query `tenantId:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） | 查询导入任务列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import') |
| GET | `/api/customers/import/jobs/{jobId}` | path `jobId` | 查询导入任务详情 | hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import') |
| GET | `/api/customers/import/template` | — | 下载CSV导入模板 | hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import') |

**FinanceController**（9）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| POST | `/api/finance/transactions` | body `CreateFinancialTransactionRequest` | 创建财务交易 | `authenticated()` |
| GET | `/api/finance/transactions` | query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `20`） · query `type:TransactionType`（选填） · query `status:TransactionStatus`（选填） · query `referenceId:String`（选填） · query `referenceType:String`（选填） · query `from:LocalDateTime`（选填） · query `to:LocalDateTime`（选填） · query `tenantId:String`（选填） | 分页查询财务交易 | `authenticated()` |
| GET | `/api/finance/transactions/{transactionId}` | path `transactionId` | 根据交易ID查找财务交易 | `authenticated()` |
| GET | `/api/finance/transactions/by-status` | query `status:TransactionStatus`（**必填**） | 根据状态查找财务交易 | `authenticated()` |
| GET | `/api/finance/transactions/by-type` | query `type:TransactionType`（**必填**） | 根据类型查找财务交易 | `authenticated()` |
| GET | `/api/finance/transactions/by-reference` | query `referenceId:String`（**必填**） · query `referenceType:String`（**必填**） | 根据关联ID和类型查找财务交易 | `authenticated()` |
| PUT | `/api/finance/transactions/{transactionId}/complete` | path `transactionId` | 根据交易ID完成财务交易 | `authenticated()` |
| PUT | `/api/finance/transactions/{transactionId}/fail` | path `transactionId` | 根据交易ID标记财务交易为失败 | `authenticated()` |
| PUT | `/api/finance/transactions/{transactionId}/cancel` | path `transactionId` | 取消财务交易 | `authenticated()` |

**FinanceImportController**（11）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/finance/import/target-fields` | — | 标准目标字段列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |
| GET | `/api/finance/import/transforms` | — | 字段转换规则列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |
| GET | `/api/finance/import/transaction-types` | — | 交易类型字典 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |
| GET | `/api/finance/import/field-mappings` | query `tenantId:String`（选填） · query `platform:String`（**必填**） | 查询字段映射 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |
| PUT | `/api/finance/import/field-mappings` | body `FieldMappingSaveRequest` | 保存字段映射 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |
| POST | `/api/finance/import/preview` | body `FinanceImportPreviewRequest` | 财务数据导入预览 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |
| POST | `/api/finance/import/upload` | multipart `MultipartFile` · query `mode:String`（默认 `preview`） · query `tenantId:String`（选填） · query `platform:String`（选填） · query `updateExisting:Boolean`（选填） | 上传文件导入 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |
| POST | `/api/finance/import` | body `FinanceImportRequest` | 批量导入财务数据 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |
| GET | `/api/finance/import/jobs` | query `tenantId:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） | 查询导入任务列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |
| GET | `/api/finance/import/jobs/{jobId}` | path `jobId` | 查询导入任务详情 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |
| GET | `/api/finance/import/template` | — | 下载CSV导入模板 | hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import') |

**LogisticsChannelController**（7）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/logistics-channels` | query `country:String`（选填） · query `onlyActive:Boolean`（默认 `false`） | 查询渠道列表 | `authenticated()` |
| GET | `/api/logistics-channels/{id}` | path `id` | 根据ID获取渠道 | `authenticated()` |
| POST | `/api/logistics-channels` | body `LogisticsChannelDto` | 创建渠道 | `authenticated()` |
| PUT | `/api/logistics-channels/{id}` | body `LogisticsChannelDto` · path `id` | 更新渠道 | `authenticated()` |
| PUT | `/api/logistics-channels/{id}/status` | path `id` · query `active:boolean`（**必填**） | 启用/停用渠道 | `authenticated()` |
| DELETE | `/api/logistics-channels/{id}` | path `id` | 删除渠道 | `authenticated()` |
| GET | `/api/logistics-channels/{id}/fee` | path `id` · query `country:String`（选填） · query `weight:BigDecimal`（**必填**） | 运费试算 | `authenticated()` |

**LogisticsController**（13）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| POST | `/api/logistics` | body `CreateLogisticsRequest` | 创建物流单 | `authenticated()` |
| GET | `/api/logistics` | query `tenantId:String`（选填） · query `trackingNumber:String`（选填） · query `orderId:String`（选填） · query `channelId:String`（选填） · query `status:String`（选填） · query `keyword:String`（选填） · query `startDate:String`（选填） · query `endDate:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `20`） | 条件分页查询物流单 | `authenticated()` |
| GET | `/api/logistics/statuses` | — | 物流状态字典 | `authenticated()` |
| GET | `/api/logistics/{id}` | path `id` | 根据ID获取物流单 | `authenticated()` |
| GET | `/api/logistics/order/{orderId}` | path `orderId` | 获取订单的物流单 | `authenticated()` |
| PUT | `/api/logistics/{id}/status` | body `UpdateLogisticsStatusRequest` · path `id` | 更新物流状态 | `authenticated()` |
| POST | `/api/logistics/{id}/ship` | path `id` · query `shippedAt:String`（选填） · query `trackingNumber:String`（选填） | 标记发货 | `authenticated()` |
| POST | `/api/logistics/{id}/cancel` | path `id` | 取消物流单 | `authenticated()` |
| POST | `/api/logistics/{id}/label` | path `id` | 生成物流面单 | `authenticated()` |
| POST | `/api/logistics/{id}/track` | path `id` | 刷新物流跟踪 | `authenticated()` |
| POST | `/api/logistics/{id}/traces` | body `AddTraceRequest` · path `id` | 登记物流轨迹 | `authenticated()` |
| POST | `/api/logistics/batch-ship` | body `BatchShipRequest` | 批量发货 | `authenticated()` |
| DELETE | `/api/logistics/{id}` | path `id` | 删除物流单 | `authenticated()` |

**LogisticsImportController**（12）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/logistics/import/target-fields` | — | 标准目标字段列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| GET | `/api/logistics/import/transforms` | — | 字段转换规则列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| GET | `/api/logistics/import/logistics-statuses` | — | 物流状态字典 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| GET | `/api/logistics/import/field-mappings` | query `tenantId:String`（选填） · query `platform:String`（**必填**） | 查询字段映射 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| PUT | `/api/logistics/import/field-mappings` | body `FieldMappingSaveRequest` | 保存字段映射 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| POST | `/api/logistics/import/preview` | body `LogisticsImportPreviewRequest` | 物流导入预览 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| POST | `/api/logistics/import/upload` | multipart `MultipartFile` · query `mode:String`（默认 `preview`） · query `tenantId:String`（选填） · query `platform:String`（选填） · query `updateExisting:Boolean`（选填） | 上传文件导入 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| POST | `/api/logistics/import` | body `LogisticsImportRequest` | 批量导入物流数据 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| POST | `/api/logistics/import/sync` | query `integrationId:String`（选填） · query `tenantId:String`（选填） · query `orderNumber:String`（选填） · query `trackingNumber:String`（选填） · query `shippedFrom:String`（选填） · query `shippedTo:String`（选填） · query `limit:Integer`（选填） · query `useSampleData:Boolean`（选填） | 从平台同步物流数据 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| GET | `/api/logistics/import/jobs` | query `tenantId:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） | 查询导入任务列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| GET | `/api/logistics/import/jobs/{jobId}` | path `jobId` | 查询导入任务详情 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |
| GET | `/api/logistics/import/template` | — | 下载CSV导入模板 | hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import') |

**OrderController**（9）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| POST | `/api/orders` | body `CreateOrderDto` | 创建订单 | `authenticated()` |
| GET | `/api/orders` | query `tenantId:String`（选填） · query `orderNumber:String`（选填） · query `customerId:String`（选填） · query `customerName:String`（选填） · query `status:OrderStatus`（选填） · query `platform:String`（选填） · query `keyword:String`（选填） · query `startDate:String`（选填） · query `endDate:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） | 分页查询订单 | `authenticated()` |
| GET | `/api/orders/statuses` | — | 订单状态字典 | `authenticated()` |
| GET | `/api/orders/number/{orderNumber}` | path `orderNumber` | 根据订单号获取订单 | `authenticated()` |
| GET | `/api/orders/{id}` | path `id` | 根据ID获取订单 | `authenticated()` |
| PUT | `/api/orders/{id}/status` | path `id` · query `status:OrderStatus`（**必填**） | 更新订单状态 | `authenticated()` |
| POST | `/api/orders/{id}/cancel` | path `id` | 取消订单 | `authenticated()` |
| DELETE | `/api/orders/{id}` | path `id` | 删除订单 | `authenticated()` |
| GET | `/api/orders/customer/{customerId}` | path `customerId` | 获取客户的所有订单 | `authenticated()` |

**OrderImportController**（11）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/orders/import/target-fields` | — | 标准目标字段列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |
| GET | `/api/orders/import/transforms` | — | 字段转换规则列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |
| GET | `/api/orders/import/order-statuses` | — | 订单状态字典 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |
| GET | `/api/orders/import/field-mappings` | query `tenantId:String`（选填） · query `platform:String`（**必填**） | 查询字段映射 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |
| PUT | `/api/orders/import/field-mappings` | body `FieldMappingSaveRequest` | 保存字段映射 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |
| POST | `/api/orders/import/preview` | body `OrderImportPreviewRequest` | 订单导入预览 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |
| POST | `/api/orders/import/upload` | multipart `MultipartFile` · query `mode:String`（默认 `preview`） · query `tenantId:String`（选填） · query `platform:String`（选填） · query `updateExisting:Boolean`（选填） | 上传文件导入 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |
| POST | `/api/orders/import` | body `OrderImportRequest` | 批量导入订单 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |
| GET | `/api/orders/import/jobs` | query `tenantId:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） | 查询导入任务列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |
| GET | `/api/orders/import/jobs/{jobId}` | path `jobId` | 查询导入任务详情 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |
| GET | `/api/orders/import/template` | — | 下载CSV导入模板 | hasRole('PLATFORM_ADMIN') or hasAuthority('order:import') |

**PlatformIntegrationController**（12 → 2026-03-08 实测 14 → **同日再测 15**）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/platform-integrations/auth-specs` | — | 对接协议凭据要求 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| POST | `/api/platform-integrations` | body `PlatformIntegrationSaveRequest` | 创建平台集成 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| GET | `/api/platform-integrations/{id}` | path `id` | 根据ID获取平台集成 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| GET | `/api/platform-integrations` | — | 获取所有平台集成 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| PUT | `/api/platform-integrations/{id}` | body `PlatformIntegrationSaveRequest` · path `id` | 更新平台集成 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| DELETE | `/api/platform-integrations/{id}` | path `id` | 删除平台集成 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| POST | `/api/platform-integrations/{id}/activate` | path `id` | 激活平台集成 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| POST | `/api/platform-integrations/{id}/deactivate` | path `id` | 停用平台集成 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| POST | `/api/platform-integrations/{id}/sync-orders` | path `id` · query `tenantId:String`（选填） · query `orderNumber:String`（选填） · query `updatedFrom:String`（选填） · query `updatedTo:String`（选填） · query `limit:Integer`（选填） · query `useSampleData:Boolean`（选填） | 同步订单 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| POST | `/api/platform-integrations/{id}/sync-customers` | path `id` · query `tenantId:String`（选填） · query `sourceType:String`（选填） · query `keyword:String`（选填） · query `limit:Integer`（选填） · query `useSampleData:Boolean`（选填） | 同步客户（买家） | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| POST | `/api/platform-integrations/{id}/sync-products` | path `id` · query `tenantId:String`（选填） | 同步产品 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| POST | `/api/platform-integrations/{id}/sync-inventory` | path `id` | 同步库存 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| POST | `/api/platform-integrations/{id}/sync-logistics` | path `id` · query `tenantId:String`（选填） · query `orderNumber:String`（选填） · query `trackingNumber:String`（选填） · query `shippedFrom:String`（选填） · query `shippedTo:String`（选填） · query `limit:Integer`（选填） · query `useSampleData:Boolean`（选填） | 同步物流 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| POST | `/api/platform-integrations/{id}/test-connection` | path `id` | 连通性测试 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |
| POST | `/api/platform-integrations/{id}/push-fulfillment` | path `id` · body `PlatformFulfillmentPushRequest` | 发货回写 | hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage') |

**ProductController**（12）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/products` | query `tenantId:String`（选填） · query `keyword:String`（选填） · query `categoryName:String`（选填） · query `active:Boolean`（选填） · query `sourcePlatform:String`（选填） · query `skuCode:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） · query `sort:String`（默认 `createdAt,desc`） | 分页查询产品 | `authenticated()` |
| POST | `/api/products` | body `CreateProductRequest` | 创建产品 | `authenticated()` |
| PUT | `/api/products/{id}` | body `UpdateProductRequest` · path `id` | 更新产品 | `authenticated()` |
| GET | `/api/products/{id}` | path `id` | 根据ID获取产品 | `authenticated()` |
| GET | `/api/products/sku/{skuCode}` | path `skuCode` | 根据SKU编码获取产品 | `authenticated()` |
| PUT | `/api/products/{id}/activate` | path `id` | 激活产品 | `authenticated()` |
| PUT | `/api/products/{id}/deactivate` | path `id` | 停用产品 | `authenticated()` |
| DELETE | `/api/products/{id}` | path `id` · query `tenantId:String`（选填） | 删除产品 | `authenticated()` |
| POST | `/api/products/{id}/skus` | body `SkuRequestDto` · path `id` | 新增SKU | `authenticated()` |
| DELETE | `/api/products/{id}/skus/{skuCode}` | path `id` · path `skuCode` · query `tenantId:String`（选填） | 删除SKU | `authenticated()` |
| PUT | `/api/products/batch/active` | body `BatchActiveRequest` | 批量启用/停用产品 | `authenticated()` |
| DELETE | `/api/products/batch` | body `BatchDeleteRequest` | 批量删除产品 | `authenticated()` |

**ProductImportController**（10）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/products/import/target-fields` | — | 标准目标字段列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('product:import') |
| GET | `/api/products/import/transforms` | — | 字段转换规则列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('product:import') |
| GET | `/api/products/import/field-mappings` | query `tenantId:String`（选填） · query `platform:String`（**必填**） | 查询字段映射 | hasRole('PLATFORM_ADMIN') or hasAuthority('product:import') |
| PUT | `/api/products/import/field-mappings` | body `FieldMappingSaveRequest` | 保存字段映射 | hasRole('PLATFORM_ADMIN') or hasAuthority('product:import') |
| POST | `/api/products/import/preview` | body `ProductImportPreviewRequest` | 商品导入预览 | hasRole('PLATFORM_ADMIN') or hasAuthority('product:import') |
| POST | `/api/products/import/upload` | multipart `MultipartFile` · query `mode:String`（默认 `preview`） · query `tenantId:String`（选填） · query `platform:String`（选填） · query `updateExisting:Boolean`（选填） | 上传文件导入 | hasRole('PLATFORM_ADMIN') or hasAuthority('product:import') |
| POST | `/api/products/import` | body `ProductImportRequest` | 批量导入商品 | hasRole('PLATFORM_ADMIN') or hasAuthority('product:import') |
| GET | `/api/products/import/jobs` | query `tenantId:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） | 查询导入任务列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('product:import') |
| GET | `/api/products/import/jobs/{jobId}` | path `jobId` | 查询导入任务详情 | hasRole('PLATFORM_ADMIN') or hasAuthority('product:import') |
| GET | `/api/products/import/template` | — | 下载CSV导入模板 | hasRole('PLATFORM_ADMIN') or hasAuthority('product:import') |

**PurchaseController**（~~9~~ **12**）🔄 2026-03-22 按注解扫描重写，逐条风险注记见 §4.10

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| POST | `/api/purchases` | body `PurchaseCreateRequest` | 创建采购单（单头+明细一次提交） | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write') |
| GET | `/api/purchases` | query `tenantId:String`（选填） · query `purchaseOrderId:String`（选填） · query `supplierId:String`（选填） · query `supplierName:String`（选填） · query `warehouseId:String`（选填） · query `status:PurchaseStatus`（选填） · query `minTotalAmount:BigDecimal`（选填） · query `startDate:String`（选填） · query `endDate:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） | 分页查询采购单 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:read') |
| GET | `/api/purchases/statuses` | — | 采购状态字典 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:read') |
| GET | `/api/purchases/{purchaseOrderId}` | path `purchaseOrderId` · query `tenantId:String`（选填） | 根据ID获取采购单 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:read') |
| POST | `/api/purchases/{purchaseOrderId}/items` | path `purchaseOrderId` · query `tenantId:String`（选填） · body `PurchaseItemRequest` | 添加采购项 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write') |
| PUT | `/api/purchases/{purchaseOrderId}/items/{itemId}` | path ×2 · query `tenantId:String`（选填） · body `PurchaseItemRequest` | 修改采购项 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write') |
| DELETE | `/api/purchases/{purchaseOrderId}/items/{itemId}` | path ×2 · query `tenantId:String`（选填） | 删除采购项 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write') |
| PUT | `/api/purchases/{purchaseOrderId}/approve` | path `purchaseOrderId` · query `tenantId:String`（选填） · body `PurchaseApprovalRequest`（可空） | 批准采购单 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write') |
| PUT | `/api/purchases/{purchaseOrderId}/reject` | path `purchaseOrderId` · query `tenantId:String`（选填） · body `PurchaseApprovalRequest`（`comment` **必填**） | 拒绝采购单 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write') |
| PUT | `/api/purchases/{purchaseOrderId}/in-transit` | path `purchaseOrderId` · query `tenantId:String`（选填） | 标记采购单为运输中 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write') |
| PUT | `/api/purchases/{purchaseOrderId}/delivered` | path `purchaseOrderId` · query `tenantId:String`（选填） | 标记采购单为已送达 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write') |
| PUT | `/api/purchases/{purchaseOrderId}/cancel` | path `purchaseOrderId` · query `tenantId:String`（选填） | 取消采购单 | hasRole('PLATFORM_ADMIN') or hasAuthority('purchase:write') |

**RoleController**（10）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| POST | `/api/roles` | body `CreateRoleRequest` | 创建角色 | hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage') |
| POST | `/api/roles/system` | body `CreateRoleRequest` | 创建系统角色 | hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage') |
| GET | `/api/roles` | query `tenantId:String`（**必填**） | 获取角色列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage') |
| GET | `/api/roles/system` | — | 获取系统角色列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage') |
| GET | `/api/roles/{id}` | path `id` | 获取角色详情 | hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage') |
| PUT | `/api/roles/{id}` | body `UpdateRoleRequest` · path `id` | 更新角色 | hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage') |
| DELETE | `/api/roles/{id}` | path `id` | 删除角色 | hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage') |
| POST | `/api/roles/{id}/permissions` | body `AddPermissionRequest` · path `id` | 为角色添加权限 | hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage') |
| DELETE | `/api/roles/{id}/permissions/{permission}` | path `id` · path `permission` | 从角色移除权限 | hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage') |
| PUT | `/api/roles/{id}/permissions` | body `SetPermissionsRequest` · path `id` | 为角色设置权限集合 | hasRole('PLATFORM_ADMIN') or hasAuthority('role:manage') |

**ShopWarehouseController**（5）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/warehouses/shop` | query `tenantId:String`（选填） · query `platform:String`（选填） · query `localWarehouseId:String`（选填） | 查询店铺仓库列表 | `authenticated()` |
| GET | `/api/warehouses/shop/{id}` | path `id` | 查询店铺仓库详情 | `authenticated()` |
| POST | `/api/warehouses/shop/bind` | body `BindShopWarehouseRequest` | 绑定本地仓库 | `authenticated()` |
| POST | `/api/warehouses/shop/{id}/unbind` | path `id` | 解绑本地仓库 | `authenticated()` |
| POST | `/api/warehouses/shop/sync/{integrationId}` | path `integrationId` · query `tenantId:String`（选填） · query `useSampleData:Boolean`（选填） · query `keyword:String`（选填） · query `limit:Integer`（选填） | 从平台同步店铺仓库 | `authenticated()` |

**TenantController**（9）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| POST | `/api/tenants` | body `CreateTenantRequest` | 创建租户 | `authenticated()` |
| GET | `/api/tenants` | — | 获取租户列表 | `authenticated()` |
| GET | `/api/tenants/{id}` | path `id` | 获取租户详情 | `authenticated()` |
| PUT | `/api/tenants/{id}` | body `UpdateTenantRequest` · path `id` | 更新租户 | `authenticated()` |
| PUT | `/api/tenants/{id}/activate` | path `id` | 激活租户 | `authenticated()` |
| PUT | `/api/tenants/{id}/suspend` | path `id` | 暂停租户 | `authenticated()` |
| PUT | `/api/tenants/{id}/deactivate` | path `id` | 注销租户 | `authenticated()` |
| DELETE | `/api/tenants/{id}` | path `id` | 删除租户 | `authenticated()` |
| POST | `/api/tenants/{id}/admin` | body `CreateUserRequest` · path `id` | 为租户创建管理员 | `authenticated()` |

**UserController**（11）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| POST | `/api/users` | body `CreateUserRequest` | 创建用户 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |
| GET | `/api/users` | query `tenantId:String`（**必填**） | 获取用户列表 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |
| GET | `/api/users/{id}` | path `id` | 获取用户详情 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |
| PUT | `/api/users/{id}` | body `UpdateUserRequest` · path `id` | 更新用户 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |
| PUT | `/api/users/{id}/password` | body `UpdatePasswordRequest` · path `id` | 更新用户密码 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |
| PUT | `/api/users/{id}/activate` | path `id` | 激活用户 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |
| PUT | `/api/users/{id}/deactivate` | path `id` | 禁用用户 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |
| PUT | `/api/users/{id}/lock` | path `id` | 锁定用户 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |
| DELETE | `/api/users/{id}` | path `id` | 删除用户 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |
| POST | `/api/users/{id}/roles` | body `AssignRoleRequest` · path `id` | 为用户分配角色 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |
| DELETE | `/api/users/{id}/roles/{roleId}` | path `id` · path `roleId` | 从用户移除角色 | hasRole('PLATFORM_ADMIN') or hasAuthority('user:manage') |

**WarehouseController**（13）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| POST | `/api/warehouses` | body `CreateWarehouseRequest` | 创建仓库 | `authenticated()` |
| GET | `/api/warehouses/{id}` | path `id` | 根据ID获取仓库 | `authenticated()` |
| GET | `/api/warehouses/code/{code}` | path `code` | 根据编码获取仓库 | `authenticated()` |
| GET | `/api/warehouses` | query `tenantId:String`（选填，缺省取 JWT 上下文租户） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） | 🔄 **2026-10-07 改为分页检索**：按租户过滤分页查询仓库，返回 `PageResult<Warehouse>`（ISS-050 仓库段修复） | `authenticated()` |
| PUT | `/api/warehouses/{id}` | body `UpdateWarehouseRequest` · path `id` | 更新仓库 | `authenticated()` |
| DELETE | `/api/warehouses/{id}` | path `id` | 删除仓库 | `authenticated()` |
| PUT | `/api/warehouses/{id}/status` | path `id` · query `active:boolean`（**必填**） | 变更仓库状态 | `authenticated()` |
| POST | `/api/warehouses/{id}/locations` | body `AddLocationRequest` · path `id` | 添加库位 | `authenticated()` |
| GET | `/api/warehouses/{warehouseId}/inventory` | path `warehouseId` | 根据仓库ID获取库存记录 | `authenticated()` |
| POST | `/api/warehouses/{warehouseId}/inventory/in` | body `InventoryMoveRequest` · path `warehouseId` | 库存入库 | `authenticated()` |
| POST | `/api/warehouses/{warehouseId}/inventory/out` | body `InventoryMoveRequest` · path `warehouseId` | 库存出库 | `authenticated()` |
| POST | `/api/warehouses/{warehouseId}/inventory/count` | path `warehouseId` · query `productId:String`（**必填**） · query `actualQuantity:int`（**必填**） | 执行库存盘点 | `authenticated()` |
| POST | `/api/warehouses/{warehouseId}/inventory/move` | body `InventoryMovement` · path `warehouseId` | 执行库存移动 | `authenticated()` |

**WarehouseImportController**（10）

| 方法 | 路径 | 入参 | 说明 | 鉴权 |
|---|---|---|---|---|
| GET | `/api/warehouses/import/target-fields` | — | 标准目标字段列表 | `authenticated()` |
| GET | `/api/warehouses/import/transforms` | — | 字段转换规则列表 | `authenticated()` |
| GET | `/api/warehouses/import/field-mappings` | query `tenantId:String`（选填） · query `platform:String`（**必填**） | 查询字段映射 | `authenticated()` |
| PUT | `/api/warehouses/import/field-mappings` | body `ShopWarehouseFieldMappingSaveRequest` | 保存字段映射 | `authenticated()` |
| POST | `/api/warehouses/import/preview` | body `ShopWarehouseImportPreviewRequest` | 店铺仓库导入预览 | `authenticated()` |
| POST | `/api/warehouses/import/upload` | multipart `MultipartFile` · query `mode:String`（默认 `preview`） · query `tenantId:String`（选填） · query `platform:String`（选填） · query `updateExisting:Boolean`（选填） | 上传文件导入 | `authenticated()` |
| POST | `/api/warehouses/import` | body `ShopWarehouseImportRequest` | 批量导入店铺仓库 | `authenticated()` |
| GET | `/api/warehouses/import/jobs` | query `tenantId:String`（选填） · query `page:Integer`（默认 `0`） · query `size:Integer`（默认 `10`） | 查询导入任务列表 | `authenticated()` |
| GET | `/api/warehouses/import/jobs/{jobId}` | path `jobId` | 查询导入任务详情 | `authenticated()` |
| GET | `/api/warehouses/import/template` | — | 下载CSV导入模板 | `authenticated()` |

## 4C. 端点清单复测（2026-10-09，按注解扫描）

> ⚠️ **§4B 已过期**：它是 2026-01-25 由注解扫描生成的，只有 20 个控制器，**不含 `AuditLogController` 与 `SystemConfigController`**（BR-12 审计切片与系统配置切片之后才落地）。本节是同一次扫描的当前口径，§4B 保留作历史链。
> 计数口径：方法级 `@GetMapping/@PostMapping/@PutMapping/@PatchMapping/@DeleteMapping` 之和；`@PreAuthorize` 与 `@Auditable` 含类级注解，故不等于"逐端点都有"。写端点的逐条留痕口径见 [../tech/security-design.md §5](../tech/security-design.md)（**126 个写端点 = 118 注解 + 8 带理由豁免**）与 [../qa/known-issues.md §15 R-37](../qa/known-issues.md)。

| 控制器 | 基路径 | 端点 | `@PreAuthorize` | `@Auditable` |
|---|---|---:|---:|---:|
| `AuthController` | `/api/auth` | 4 | 0（放行端点，登录/刷新由 `LoginSecurityService` 显式留痕） | 1 |
| `UserController` | `/api/users` | 11 | 1（类级） | 9 |
| `RoleController` | `/api/roles` | 10 | 3 | 7 |
| `TenantController` | `/api/tenants` | 9 | 0（类级 `hasRole("PLATFORM_ADMIN")` 写在 `SecurityConfig` 侧） | 7 |
| `AuditLogController` | `/api/audit-logs` | 2 | 1（类级 `PLATFORM_ADMIN`） | 0（只读） |
| `SystemConfigController` | `/api/system-config` | 2 | 1 | 1 |
| `OverviewController` | `/api/overview` | 1 | 1 | 0（只读） |
| `CustomerController` | `/api/customers` | 9 | 9 | 6 |
| `ProductController` | `/api/products` | 12 | 12 | 9 |
| `OrderController` | `/api/orders` | 9 | 9 | 4 |
| `FinanceController` | `/api/finance/transactions` | 9 | 9 | 4 |
| `PurchaseController` | `/api/purchases` | 12 | 12 | 9 |
| `LogisticsController` | `/api/logistics` | 13 | 13 | 9 |
| `LogisticsChannelController` | `/api/logistics-channels` | 7 | 7 | 4 |
| `WarehouseController` | `/api/warehouses` | 13 | 13 | 9 |
| `ShopWarehouseController` | `/api/warehouses/shop` | 5 | 5 | 3 |
| `ShopController` | `/api/shops` | 7 | 7 | 5 |
| `PlatformIntegrationController` | `/api/platform-integrations` | 15 | 14 | 12 |
| `OrderImportController` | `/api/orders/import` | 11 | 1（类级） | 3 |
| `ProductImportController` | `/api/products/import` | 10 | 1（类级） | 3 |
| `CustomerImportController` | `/api/customers/import` | 10 | 1（类级） | 3 |
| `FinanceImportController` | `/api/finance/import` | 11 | 1（类级） | 3 |
| `LogisticsImportController` | `/api/logistics/import` | 12 | 1（类级） | 4 |
| `WarehouseImportController` | `/api/warehouses/import` | 10 | 10 | 3 |
| **合计** | **24 个控制器** | **214** | **132** | **118** |

> `@Auditable` 合计 **118** 与 `scripts/smoke/audit-coverage-scan.js --strict` 的输出一致（该脚本另给出写端点分母 **126** 与 **8** 条带理由豁免，缺口 0）。6 个 `*ImportController` 的 `preview` 端点是其中 6 条豁免（只解析文件返回预览行，不落库），另 2 条是 `AuthController#login`/`#refreshToken`（认证前拿不到 actor，由 `LoginSecurityService` 记成功与失败两侧）。

## 5. 枚举取值

查询参数/请求体按**领域枚举常量名**绑定（不是数据库落库值）：

| 枚举 | 可选值 |
|---|---|
| `OrderStatus` | `PENDING` \| `PROCESSING` \| `CON_FIRMED` \| `SHIPPED` \| `COMPLETED` \| `CANCELLED` |
| `LogisticsStatus` | `PENDING` \| `COLLECTED` \| `LABEL_GENERATED` \| `IN_TRANSIT` \| `DELIVERING` \| `DELIVERED` \| `FAILED` \| `CANCELLED` |
| `TransactionType` | `INCOME` \| `EXPENSE` \| `REFUND` \| `ADJUSTMENT` |
| `TransactionStatus` | `PENDING` \| `SUCCESS` \| `FAILED` \| `CANCELLED` |
| `PurchaseStatus` | `PENDING` \| `APPROVED` \| `REJECTED` \| `IN_TRANSIT` \| `DELIVERED` \| `CANCELLED` |
| `InventoryMovement.MovementType` | `INBOUND` \| `OUTBOUND` \| `ADJUSTMENT` \| `TRANSFER`（~~`TRANSFER` 在应用层直接 `throw UnsupportedOperationException`~~ → 🔄 2026-03-22 复测：**全仓 `UnsupportedOperationException` 0 处**，`TRANSFER` 走 `WarehouseApplicationService.transferInventory:237-252` 的双写；未支持类型才在 `:230` 抛 `ValidationException` → 400。⚠️ HTTP 入参按**枚举名**（大写）传，领域 `code` 是小写且从不落库，见 [../spec/warehouse.md §3.2](../spec/warehouse.md)） |

注意 `CON_FIRMED`（领域）↔ `CONFIRMED`（落库）、`COLLECTED`↔`PICKED_UP`、`DELIVERING`↔`OUT_FOR_DELIVERY`、`FAILED`↔`EXCEPTION` 的映射差异，见 [data-dictionary.md §4](../db/data-dictionary.md#4-枚举存储口径三套并存--最需要统一的部分)。

## 6. 认证与鉴权（现状：链路已通，隔离只做了一半）

- 令牌：`Authorization: Bearer <JWT>`，HS512（JJWT 0.12.5），密钥与过期在 `application.yml`（`jwt.secret` 明文、`jwt.expiration=86400000`、`jwt.refresh.expiration=86400000`）。
- 🔄 2026-01-25：`SecurityConfig.java:63` 已改用 `authorizeHttpRequests`（不再用废弃的 `authorizeRequests`）；现规则为 `/api/auth/login|refresh|logout` permitAll（`:64`）→ `/api/auth/me` `authenticated()`（`:65`）→ `/api/tenants/**` `hasRole(PLATFORM_ADMIN)`（`:66`）→ `/api/**` `authenticated()`（`:67`）；已无 `/api/platform/**` 这条与真实路径不匹配的规则。~~CORS 白名单已含 `:5173` 与 `:5174`（`:49-50`）~~ → 🔄 **2026-10-10**：白名单现为 `:5110`（admin-web）与 `:5120`（user-web）（`SecurityConfig.java:63`，`corsConfigurationSource()` 在 `:59-68`），两端 `vite.config.js:9` 已把 `server.port` 写死并配 `strictPort: true`；旧的 Vite 默认端口 `:5173/:5174` **已不在白名单**，按旧文档起 dev server 会在浏览器里得到 CORS 错误而不是业务错误。
  ⚠️ 仍**无 `.anyRequest()`** 兜底 → `/swagger-ui.html`、`/v3/api-docs` 不在任何规则内（L-01）。

原列的 5 项缺陷，2026-01-25 逐条复测（详见 [docs/security-findings.md](../security-findings.md) 的 S-01…S-06 / M-01…M-10）：

1. ✅ 已修：`validateToken()` 现为 `parseClaims(token) != null`（`JwtTokenProvider.java:189-205`，走 `Jwts.parser()` 验签 + 过期校验），`getTenantIdFromToken()`（`:141-144`）真正从 claim 取值。「任意串即已认证」不再成立。
2. ✅ 已修：令牌写入 4 个 claim（`username`/`tenantId`/`roles`/`permissions`，`:33-36` 与 `:89-94`），过滤器据此重建 authorities → `hasRole`/`hasAuthority` 生效，`/api/tenants/**` 不再恒定 403。这正是 **ISS-041** 的修法（权限点此前能加载却出不了 JWT 边界）。
3. ✅ 已修：见上条 `SecurityConfig` 规则（不再有 `/api/platform/**` 错配）。
4. ✅ 已修：`JwtAuthenticationFilter.java:31-33` 改为 `private final` + 构造注入；`getJwtFromRequest` 的 `Bearer ` 前缀剥离本就正确（M-02 系误报）。
5. ✅ 大部分已修：`SecurityConfig.java:35` 已加 `@EnableMethodSecurity`，**~~17 处 `@PreAuthorize` 覆盖 9/20 个控制器~~**（🔄 **2026-03-22 复测 = 29 处生效注解 / 10 个控制器**：8 处类级 + 21 处方法级 = 财务 9 + **采购 12（`PurchaseController:63,77,101,115,128,144,160,175,191,210,225,239`，本轮新增）**；另有 3 处 `@PreAuthorize` 只是 Javadoc 文本，不计入。111/202 端点受保护、91 端点无注解，见 [../tech/inventory/api-endpoints.csv](../tech/inventory/api-endpoints.csv)）（`UserController:31` `user:manage`、`RoleController:31` `role:manage`、`PlatformIntegrationController:46` `platform:manage`、~~6 个~~ **5 个** `*ImportController` 各 1 处（`CustomerImport:59` / `FinanceImport:59` / `LogisticsImport:60` / `OrderImport:59` / `ProductImport:58`）、`FinanceController` 9 处（`:56,78,105,120,137,154,173,188,203`））；`TenantIsolationAspect.java:55-86` 已从注释里解放并真正拦截跨租户入参（不一致抛 `BusinessException("TENANT_ACCESS_DENIED")`），`TenantContext` 也在 `JwtAuthenticationFilter.java:82` 的 `finally` 中清理。**未覆盖**：`Order`/`Customer`/`Logistics`/`LogisticsChannel`/`ShopWarehouse`/`Warehouse`/`WarehouseImport`/`Product`/~~`Purchase`~~/`Tenant`/`Auth` 这 ~~11~~ **10** 个控制器无任何方法级注解。
   🔴 **2026-01-25 复测：注解数 ≠ 权限点被生效。** 种子里有 **22** 个 PLATFORM_ADMIN 权限点（`role_jpa_entity_permissions` 实测 22 行，🔄 2026-03-22 复跑 SQL 仍为 22 行且**含 `purchase:read`/`purchase:write`**），但 ~~只有 **10** 个真正出现在 `hasAuthority()` 里~~ → 🔄 **2026-03-22 = 12 个**（新增 `purchase:read`、`purchase:write`）：`finance:read`、`finance:write`、`finance:import`、`order:import`、`product:import`、`customer:import`、`logistics:import`、`user:manage`、`role:manage`、`platform:manage`；~~`order`/`product`/`customer`/`warehouse`/`purchase`/`logistics` 的 `:read` 与 `:write` 共 **12 个只是种上了，没有任何端点检查它们**~~ → 剩 **10 个**：`order`/`product`/`customer`/`warehouse`/`logistics` 的 `:read` 与 `:write`。**采购两条本轮已被端点消费**。此外 ~~17 处~~ **29 处**全是 `hasRole('PLATFORM_ADMIN') **or** hasAuthority(...)`——OR 语义下平台管理员绕过权限点，而该分支自种子初始化修复（ISS-061）前从未被真正走过。`WarehouseImportController` 是 6 个导入控制器里**唯一 0 注解**的（10 个端点全靠路径规则）。详见 [security-findings.md M-06](../security-findings.md) 与 [`../qa/smoke-test-checklist.md`](../qa/smoke-test-checklist.md) 0.4。

**结论（更新后）：授权不再是"形同虚设"，但租户隔离只做了写路径。** 关键残留：
- ⚠️ **ISS-050 读路径未隔离**：仓储层显式不过滤（`TenantIsolationAspect.java:91-97` 只打 trace），凡入参不含 `tenantId` 的读方法即无隔离——~~采购域 `findById`/`findAll`/`findByStatus`/`findBySupplierId`/`findByWarehouseId`（`PurchaseOrderRepositoryImpl.java:58,70,83,97,111`）全部如此，A 租户可按 ID 直读 B 租户采购单~~。🔄 **2026-03-22 复测：该证据行已失效——采购域已收口**（`PurchaseOrderRepositoryImpl.findById(String tenantId, …)` `:68-74` 走 `findByTenantIdAndPurchaseOrderId`、空租户直接返回 `Optional.empty()`；无租户的 `findAll()` 整个从接口删除，列表改为 `search(PurchaseQuery,…)` + `PurchaseOrderSpecification` 带租户谓词）。**缺陷本身仍在，只是换了证据**：仍有 **9 个仓储**的按主键直读不带租户（`OrderRepositoryImpl:45,67`、`CustomerRepositoryImpl:51`、`ProductRepositoryImpl:60`、`LogisticsOrderRepositoryImpl:48,131`、`FinancialTransactionRepositoryImpl:60`、`WarehouseRepositoryImpl:34,60`、`InventoryRecordRepositoryImpl:36,80`、`PlatformIntegrationRepositoryImpl:43,62`、`LogisticsChannelRepositoryImpl:35,58`）。逐条行号与判据见 [../qa/known-issues.md](../qa/known-issues.md) ISS-050 与 [../tech/inventory/tech-debt-register.csv](../tech/inventory/tech-debt-register.csv) TD-01。
- ~~⚠️ **ISS-051**：`PurchaseController.java:47` 仍留 `@RequestParam(defaultValue = "default-tenant")`，与上面的切面直接冲突（非种子租户必须显式传 `tenantId`，前端目前靠传登录租户绕过）。~~ → 🔄 **2026-03-22 复测：已修**。`defaultValue` 形态在**全主源码 0 命中**（`grep 'defaultValue = "default-tenant"'`），采购 10 个端点的 `tenantId` 全是 `@RequestParam(required = false)`，归属由 `resolveTenant`（`:264-272`）三级回落决定。⚠️ 残留（不改判）：末级回落仍是 `PurchaseApplicationService.DEFAULT_TENANT_ID`，无会话的内部调用会静默归到种子租户；该形态只在采购/订单/客户三处出现，其余控制器各自硬兜底。
- ⚠️ 权限点无字典、无校验：`PUT /api/roles/{id}/permissions` 仍可写入任意字符串（数据字典 §3）；`TenantController`/`AuthController` 无类级注解（前者靠路径规则）。

## 7. 错误处理

`GlobalExceptionHandler`（`@ControllerAdvice + @ResponseBody`）映射：

| 异常 | HTTP | `code` |
|---|---|---|
| `BusinessException` | 400 | `ex.getErrorCode()`（自由字符串） |
| `EntityNotFoundException` | 404 | `"ENTITY_NOT_FOUND"`（硬编码） |
| `InvalidOperationException` | 400 | `"INVALID_OPERATION"` |
| `ValidationException` / `BadCredentialsException` | 400 | `"400"`（走 `Result.fail`） |
| `MethodArgumentNotValidException` | 400 | `"VALIDATION_ERROR"`，`data` 为 `{field:message}` |
| `Exception`（兜底） | 500 | `"SYSTEM_ERROR"`，message「系统内部错误，请稍后重试」 |

关键事实：

- **没有集中错误码字典**（无 enum/常量类），`code` 既出现 `"200"/"400"/"500"` 也出现 `ENTITY_NOT_FOUND` 这类语义串。
- 🔄 2026-01-25 复测：自定义异常**已在大量使用**——`throw new ValidationException()` **116** 处、`throw new BusinessException()` **14** 处、`throw new InvalidOperationException()` **6** 处、`throw new EntityNotFoundException()` **3** 处；`GlobalExceptionHandler` 已为这 4 类 + `BadCredentialsException` + `IllegalArgumentException`/`IllegalStateException` + `MethodArgumentNotValidException` 分别注册 handler（`interfaces/handler/GlobalExceptionHandler.java:37,53,69,85,104,120,141`）。**ISS-037 已修**：`IllegalArgumentException`/`IllegalStateException` 现在映射为 **400 + `INVALID_OPERATION` + 原始消息**（`:104-112`），不再是 500/`SYSTEM_ERROR`。
  ⚠️ 残留：领域内仍有 **32 处 `IllegalArgumentException` + 3 处 `IllegalStateException`** 用 JDK 异常而非本仓 4 类领域异常，靠全局兜底才不出 500；语义上它们应改用 `ValidationException`/`InvalidOperationException` 以便区分 400 与 409。
  - 因此同一个「实体不存在」在不同模块可能表现为 **500 / 404 / HTTP 200+code"500"** 三种形态。
- ❌ **Bean Validation 仍未启用**（M-10）：`interfaces` 下 `@Valid` **0 处**，DTO 与内部 `*Request` 无 `@NotNull/@Size` → `:120` 的 `MethodArgumentNotValidException` 分支是死代码。必填校验只落在 `@RequestParam` 与领域聚合内部。

## 8. springdoc 使用状态

- `SwaggerConfig` 定义了 title「跨境大卖ERP系统API」、contact `dev@example.com`，并加了全局 `SecurityRequirement().addList("Bearer Authentication")`，但 **`Components` 为空、未定义名为 `Bearer Authentication` 的 `SecurityScheme`** → swagger-ui 的 Authorize 按钮不生效，`Authorization` 头未在 OpenAPI 里正确建模。
- DTO 层文档不完整：`CreateOrderDto`、`OrderItemDto` 有 `@Schema(description/requiredMode/example/minLength)`；`OrderDto`、`ProductDto`、`FinancialTransactionDto`、所有内部 `*Request` 类、以及被当作请求体的领域聚合（`PlatformIntegration`、`InventoryMovement`）**无任何 Schema 说明**。

## 9. 前后端契约对齐

🔄 **2026-01-25 重测（逐模块数方法，非估算）**：user-web 的调用层集中在一个文件 `src/services/api.js`，21 个模块共 **194 个方法**；2026-03-08 复测 = 196 个方法 / 198 个后端端点（增量是 `platformIntegrationApi.testConnection:462` 与 `.pushFulfillment:464`，两端同时 +2 所以缺口没有变化）。<br>🔄 **2026-03-08 三测**：后端 **199** 个端点 vs user-web 仍是 **196** 个调用方法 ⇒ **缺口 +1**：新增的 `GET /api/platform-integrations/auth-specs` **没有任何前端调用方**（全仓 grep `auth-specs`/`authSpecs` 在 `*.js`/`*.ts`/`*.vue` 里 0 命中），它是给配置界面用的凭据要求目录，前端要按它动态渲染必填字段还得先补一层调用。<br>🔄 **2026-03-22 四测**：后端 **202** 个端点 vs user-web **199** 个调用方法（196 + 采购 3 条：`getStatuses`/`updatePurchaseOrderItem`/`removePurchaseOrderItem`）⇒ **缺口仍是 3**（`financeApi` −2、`platformIntegrationApi` −1）。逐模块对账脚本口径：`export const` 21 个对象、2 空格缩进的 `name:`/`name(` 行计 1 个方法。覆盖如下：

| 前端模块 | 方法数 | 对应控制器 | 端点数 | 差 |
|---|---:|---|---:|---|
| `authApi` | 4 | AuthController | 4 | 0 |
| `tenantApi` / `userApi` / `roleApi` | 9 / 11 / 10 | Tenant / User / Role | 9 / 11 / 10 | 0 |
| `orderApi` / `orderImportApi` | 9 / 11 | Order / OrderImport | 9 / 11 | 0 |
| `logisticsApi` / `logisticsChannelApi` / `logisticsImportApi` | 13 / 7 / 12 | Logistics / LogisticsChannel / LogisticsImport | 13 / 7 / 12 | 0 |
| `productApi` / `productImportApi` | 12 / 10 | Product / ProductImport | 12 / 10 | 0 |
| `warehouseApi` / `shopWarehouseApi` / `warehouseImportApi` | 13 / 5 / 10 | Warehouse / ShopWarehouse / WarehouseImport | 13 / 5 / 10 | 0 |
| `customerApi` / `customerImportApi` | 9 / 10 | Customer / CustomerImport | 9 / 10 | 0 |
| `financeApi` | 7 | FinanceController | 9 | **-2**（`GET /by-status`、`GET /by-type` 未接） |
| `financeImportApi` / `purchaseApi` / `platformIntegrationApi` | 11 / ~~9~~ **12** / **14** | FinanceImport / Purchase / PlatformIntegration（平台列 2026-03-08 由 12 → 14；🔄 采购列 2026-03-22 由 9 → 12，新增 `getStatuses`/`updatePurchaseOrderItem`/`removePurchaseOrderItem`） | 11 / **12** / **15** | ~~0~~ **−1**（平台 `GET /auth-specs` 仍无调用方） |
| `platformApi` | **0** | — | — | 仍是空 stub（只剩注释「根据实际后端API补充」）🔄 2026-03-22 复核：user-web `api.js:486` 起仍 0 个方法；admin-web 同名对象有 2 个方法，两端不同名同义 |

- 结论：**后端有、前端未消费的端点从「≈40」收敛到 ~~2 个~~ 3 个**（🔄 2026-03-22：`financeApi` 的 `GET /by-status`、`GET /by-type` + `platformIntegrationApi` 缺 `GET /auth-specs`；user-web 方法总数 **196 → 199**，后端 **199 → 202**，缺口 3）；`purchaseApi` 与物流/仓库/财务调用层本轮已填充（ISS-016），**采购域现已 12/12 全覆盖**。
- ✅ 旧口径「user-web 5 个空壳模块 + 对应页面仍用模拟数据」已不成立：45 个页面里 **41 个** import `services/`；仍含模拟数据的只剩 `pages/home/Home.jsx` 与 `pages/platform/PlatformDashboard.jsx`（admin-web 同名那份也是）。
- ✅ 旧口径「`admin-web/src/services/api.js` 整个文件是 mock 层（不 import axios）」**已不成立**：该文件 `:1` 就 `import axios from 'axios'`、`:4-10` 建实例，并有 `authApi`(4)/`tenantApi`(9)/`userApi`(11)/`roleApi`(10)/`platformApi` 真实调用；admin-web 5 个页面里 4 个走 `services/`。
- ✅ **双方共同缺口已补**：`GET /api/orders` 现已存在（`OrderController.java:64`，分页 + 9 个筛选条件 + `page/size`），旧文档「后端也不存在、`OrderList.jsx` 只能停留 mock」不再成立。
- ~~⚠️ 新缺口（接口形状）：采购域的创建与加项是 **query 参数**（`@RequestParam`）而非请求体，且 `GET /api/purchases` 返回全量数组无服务端分页 → 前端 `PurchaseList` 只能本地过滤（ISS-015/ISS-016）。~~ → 🔄 **2026-03-22 复测：三条全部失效**。创建/加项/改项已走 `@RequestBody`（`PurchaseController:65,:130,:146`），列表已改为 `PageResult` 服务端分页（`:79-95`，11 个可选筛选参数），`PurchaseList.jsx` 传 `page`/`size` 由服务端过滤。**该形态缺口现在只剩一处**：`POST /api/warehouses/{id}/inventory/count`、`PUT /api/orders/{id}/status` 等仍用 query 传业务参数（见 §3）。<br>ℹ️ 顺带更正一处常见误判：`api.js:45` 的 `response.data.code === "200"` **不是 bug**——`Result.success` 写的就是字符串 `"200"`（`Result.java:51,:61`），判等成立；真正的口径问题是 §1/§3 的「数字串码 vs 目标 `000000`」尚未收敛，以及 `ERROR_MESSAGES` 两份重复（见 [error-codes.md §6](error-codes.md)）。

## 10. 待改进项（按影响排序）

0. 🔄 **2026-01-25 进度对账**：下列 1、2 两条已**大部分完成**——§6 五项鉴权缺陷全部修掉（含 `@EnableMethodSecurity` + ~~17 处~~ **29 处** `@PreAuthorize`，但只覆盖 ~~9/20~~ **10/20** 个控制器），异常→状态码映射已成立（含 `IllegalArgumentException`/`IllegalStateException` → 400）。**剩下的头两项换成**：① 读路径租户隔离（ISS-050，仓储层按 `TenantContext` 过滤或查询方法显式带租户条件）+ 给那 ~~11~~ **10** 个无注解控制器补方法级鉴权；② 清掉 ~~3 个控制器的 **34 处**吞异常（`PlatformIntegrationController` 17 处已回真 500 但仍外泄 message；`PurchaseController` 9 + `FinanceController` 8 = **17 处仍是 HTTP 200 + code"500"**，M-08）与 `PurchaseController.java:47` 的 `default-tenant`（ISS-051）~~。→ 🔄 **2026-03-22 复测：②的两个采购部分都已收口**——`PurchaseController` **0 处 try/catch**（类注释 `:37-40` 记明改由 `GlobalExceptionHandler` 映射）、`defaultValue="default-tenant"` 全主源码 0 命中；HTTP 200 + `code "500"` 从 17 处降到 **8 处**（全在 `FinanceController:70,112,129,146,165,180,195,210`），`PlatformIntegrationController` 的 23 处已回真状态码（14×500 + 9×400）但仍外泄 `e.getMessage()`，全仓 **45 处**控制器 catch 都在外泄（`error-codes.md §5`）。口径与逐处行号见 [error-codes.md §1/§5](error-codes.md)。
1. 统一响应写法为 `ResponseEntity<Result<T>>`，消灭「HTTP 200 + code 500」（现状：20 个控制器里 11 个用 `ResponseEntity`、9 个返回裸 `Result`，M-05/ISS-017）；建错误码字典（无 enum/常量类这一条仍未解决）。
2. ~~修复 §6 五项鉴权缺陷 + `@EnableMethodSecurity` + 方法级角色校验~~ ✅ 已完成；「真正的租户隔离」只完成写路径，见 0。
3. 写接口统一 JSON `@RequestBody`；`tenantId` 一律从令牌取，禁止客户端传入。
4. 补 README 声称却缺失的读接口：订单列表、客户列表、产品列表、物流渠道管理、库位管理、SKU/属性/分类管理、对账与报表。
5. 引入分页/排序约定（`PageResult<T>` + `Pageable`）；列表接口禁止无界返回。
6. 用 DTO/VO 隔离领域聚合的序列化；`PlatformIntegration` 响应剔除 `apiSecret`。
7. 补 `@Valid` + Bean Validation；补 springdoc `SecurityScheme` 定义。
