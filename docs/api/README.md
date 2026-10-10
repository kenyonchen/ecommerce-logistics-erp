# 接口文档导读（产品视角）

> 本文件**不是端点清单**。端点逐项契约（**196 个端点 / 20 个控制器**，2026-01-25 由注解扫描实测；`@Operation` 196 处、`@ApiResponse` 0 处。请求/响应、鉴权标注）在 **[`api.md`](./api.md)**，它是唯一权威来源（逐项清单见其 §4B）。
> 本文件回答三件事：**① 怎么读这套接口 ② 按业务场景去哪查 ③ 横切约定（响应、错误码、鉴权、分页）的产品视角摘要**。
> 这样分工是为了避免端点表出现两份而互相漂移。

---

## 1. 接口文档怎么读

| 你想知道 | 去哪查 |
|---|---|
| 某个端点的完整契约（路径、参数、返回、是否需要鉴权） | [`api.md` §4 端点清单](./api.md#4-端点清单) |
| 通用约定（前缀、日期格式、命名） | [`api.md` §1](./api.md#1-通用约定) |
| 响应信封 `Result<T>` 的三种写法 | [`api.md` §2](./api.md#2-响应信封-resultt) |
| 请求参数承载方式不一致的问题 | [`api.md` §3](./api.md#3-请求参数承载方式不统一) |
| 枚举的全部取值 | [`api.md` §5](./api.md#5-枚举取值) |
| 鉴权为何失效与技术细节 | [`api.md` §6](./api.md#6-认证与鉴权现状保护被架空)、[`../security-findings.md`](../security-findings.md) |
| 错误处理的完整映射 | [`api.md` §7](./api.md#7-错误处理) |
| Swagger / springdoc 使用状态 | [`api.md` §8](./api.md#8-springdoc-使用状态) |
| 前后端契约对齐情况 | [`api.md` §9](./api.md#9-前后端契约对齐) |
| 接口层待改进项 | [`api.md` §10](./api.md#10-待改进项按影响排序) |

**给非技术读者的读法**：跳过 §1~§3 的协议细节，直接看 §4 里你关心的业务模块小节，每行的「状态/说明」会告诉你这个接口能不能真用。

---

## 2. 按业务场景导航

| 业务问题 | 相关上下文 | 端点入口 | 功能规格 |
|---|---|---|---|
| 买家下单后怎么进系统 | 订单 | `/api/orders` | [`../spec/order.md`](../spec/order.md) |
| 怎么发货、怎么查物流到哪了 | 物流 | `/api/logistics` | [`../spec/logistics.md`](../spec/logistics.md) |
| 货在哪、还有多少 | 仓储 | `/api/warehouses` | [`../spec/warehouse.md`](../spec/warehouse.md) |
| 卖的是什么、SKU 怎么维护 | 产品 | `/api/products` | [`../spec/product.md`](../spec/product.md) |
| 缺货了怎么补 | 采购 | `/api/purchases` | [`../spec/purchase.md`](../spec/purchase.md) |
| 钱进多少出多少 | 财务 | `/api/finance/transactions` | [`../spec/finance.md`](../spec/finance.md) |
| 买家是谁、聊过什么 | 客户 | `/api/customers` | [`../spec/customer.md`](../spec/customer.md) |
| 怎么接 Amazon/eBay/Shopify | 平台集成 | `/api/platform-integrations` | [`../spec/platform-integration.md`](../spec/platform-integration.md) |
| 开户、成员、角色权限 | 租户与 IAM | `/api/tenants`、`/api/users`、`/api/roles`、`/api/auth` | [`../spec/tenant-and-iam.md`](../spec/tenant-and-iam.md) |

---

## 3. 横切约定（产品视角摘要）

### 3.1 调用前提

| 项 | 说明 |
|---|---|
| 服务地址 | `http://localhost:8082`，统一前缀 `/api` |
| 本地可见文档 | Swagger UI：`http://localhost:8082/swagger-ui.html` |
| 前端配置 | `services/api.js` 中 baseURL 硬编码；两个前端都**未配置 Vite proxy**，跨域依赖后端 CORS（🔄 2026-10-10：`SecurityConfig:63` 放行 **5110 / 5120**，即两个 `vite.config.js:9` 里写死的 `server.port`；`:5173` 是旧默认端口，已不在白名单） |
| 默认账号 | `admin` / `123456`（首次启动由 `DataInitializationConfig` 写入，仅可用于本地） |

### 3.2 响应与错误码

所有接口返回统一信封 `{ code, message, data }`，前端拦截器在 `code === "200"` 时直接返回 `data`，否则提示错误。

| 判定层 | 做法 |
|---|---|
| 业务是否成功 | 看 `code`（`"200"` 为成功） |
| HTTP 状态码 | 因三种封装写法并存而不完全可靠，**不要只依赖 HTTP 状态码** |
| 常见错误码 | `ENTITY_NOT_FOUND`、`INVALID_OPERATION`、`VALIDATION_ERROR`、`SYSTEM_ERROR`、`ERROR` |

产品层要求：**不得把后端 `message` 原文直接展示给用户**，应由前端按 code 映射为友好文案（如 `ENTITY_NOT_FOUND` → "没有找到这条记录"）。

### 3.3 鉴权（~~当前不可用，注意演示范围~~ → ✅ 2026-01-25 已真实生效，原表保留以便追溯）

| 现状 | 影响 |
|---|---|
| ~~`validateToken()` 恒返回 `true`~~ → 现走 `Jwts.parser()` 验签 + 过期校验（`JwtTokenProvider.java:189-205`） | ~~任意非空 Bearer 串可通过认证~~ → 伪造串会被 401 |
| ~~authorities 恒为空~~ → 令牌携带 `username`/`tenantId`/`roles`/`permissions` 四个 claim，过滤器重建 `GrantedAuthority`（ISS-041 的修法） | ~~角色授权规则永不满足；`/api/tenants/**` 反而可能恒 403~~ → `hasRole`/`hasAuthority` 生效，`/api/tenants/**` 需 `PLATFORM_ADMIN` |
| ~~`/api/**` 的保护实际可被绕过~~ → 需要**有效令牌**才算已认证 | ⚠️ 但**授权粒度仍粗**：20 个控制器只有 9 个有 `@PreAuthorize`，其余 11 个（含 `Order`/`Purchase`/`Warehouse`/`Product`）任何已认证用户都能写；`SecurityConfig` 依旧**没有 `.anyRequest()`** 兜底（L-01） |

登记见 [`../qa/known-issues.md`](../qa/known-issues.md) ISS-007 / ISS-008（已修）；修复方案见 [ADR-0004](../adr/0004-jwt-authentication.md)（决策）与 [ADR-0007](../adr/0007-jwt-auth-and-tenant-context.md)（实现差距 + 2026-01-25 更新）。

### 3.4 尚不存在的能力（需求侧）

| 缺失能力 | 产品影响 | 关联需求 |
|---|---|---|
| 分页 / 排序 | ~~数据量增长后列表不可用；订单/产品/财务/客户四个模块**连列表接口都没有**~~ ✅ 已大幅收敛（2026-01-25）：**20 个控制器里 11 个**返回 `domain/common/PageResult`（`Product`/`ProductImport`/`Order`/`OrderImport`/`Customer`/`CustomerImport`/`Logistics`/`LogisticsImport`/`Finance`/`FinanceImport`/`WarehouseImport`），入参统一 `page`（默认 0）/`size`（默认 10）；仓储侧 11 个 `*RepositoryImpl` 用 `PageRequest`。**仍无分页的列表**：`ShopWarehouseController`、`WarehouseController`、`PurchaseController`、`PlatformIntegrationController`、`LogisticsChannelController`、`TenantController`、`UserController`、`RoleController` | US-101、US-401、US-601、US-701（见 [`../product/08-user-stories.md`](../product/08-user-stories.md)） |
| 幂等键 | 重复提交/重复同步会产生脏数据 | US-107、US-802 |
| 版本管理（`/v1`） | 未来升级难以兼容 | ISS-（工程侧登记） |
| 批量操作与导出 | 仓库与财务的离案作业无法完成 | US-106、R-207 |

---

## 4. 产品线对接口层的诉求清单

与 [api.md §10](./api.md#10-待改进项按影响排序) 互补，这里只列**由用户需求推导出来**的诉求，便于排期对照：

| 优先级 | 诉求 | 依据 |
|---|---|---|
| P0 | 订单、产品、财务、客户四类列表接口（分页 + 至少 1 个筛选条件） | ✅ **已完成（2026-01-25）**：四类列表接口均已上线并带 `page`/`size`；对应地「前端 25 个页面仍在用 mock」已过期——45 个页面里 **42 个取真实接口**，只剩 `home/Home.jsx`（`:34-59` 的 `fetchStats` 把 1250 单 / ¥128500 写死）、`platform/PlatformDashboard.jsx`（`:20,64` 硬编码数组）、`platform/SystemConfig.jsx`（`:20-41` `mockConfig`）3 个仍是模拟数据，`CreateProduct.jsx`/`EditProduct.jsx` 是 `ProductForm.jsx`（走 `productApi`）的薄包装不算未接。`services/api.js` 现为 **21 个 api 模块 / 194 个方法**，唯一空壳是 `platformApi`（0 方法） |
| P0 | ~~鉴权与租户隔离生效~~ 🟡 鉴权已生效（`validateToken` 真验签、authorities 来自令牌、17 处 `@PreAuthorize` 覆盖 9/20 控制器）；**读路径租户隔离仍未做**（ISS-050） | [ISS-007/008/009/010](../qa/known-issues.md)（前三条已修）、[security-findings.md](../security-findings.md) M-03/M-06 |
| P1 | 响应/错误码统一并建立映射字典 | [`../product/04-information-architecture.md`](../product/04-information-architecture.md) 交互一致性要求 |
| P1 | 补读接口：物流渠道、库位、SKU、分类 | ["孤儿聚合"清单](../product/03-feature-map.md) |
| P1 | 提供库存/订单/流水的导出接口 | R-207 |
| P2 | 引入 `/v1` 版本号与幂等键 | 长期可维护性 |
