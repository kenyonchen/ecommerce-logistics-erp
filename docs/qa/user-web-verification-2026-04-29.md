# 用户端（user-web）页面级功能测试与业务流程验证报告

> 测试时点：**2026-04-29 00:05–01:15**（工作树未提交状态，`main` 停在 `bfbce09`）
> **二轮复测时点：2026-04-29 01:30–02:10，只做导入线六入口的逐页元素级复测**（响应"其他的导入页面都有问题"）；本轮更正了 V-01 的影响面表述、把 V-02/V-05 从"订单线"扩到"5 条线同形"，并新增 V-26 / V-27 / V-28。
> 交付目标：回答"用户端能不能按业务闭环跑通"，并给出可复现的缺陷清单与根因
> 关联缺陷登记：新立条目见 [known-issues.md §5](./known-issues.md)，复证条目已标 ISS 号

---

## 1. 测试环境与方法

| 项 | 实测值 |
|---|---|
| 前端 | `frontend/user-web` Vite dev server，**http://localhost:5174**（admin-web 5173 未纳入本轮）<br>⚠️ 该端口是当时 Vite 占用递增的结果；🔄 **2026-10-10**：两端端口已写死为 user-web **5120** / admin-web **5110**（`vite.config.js:9` + `strictPort: true`），照本文件旧端口起服务会落在 CORS 白名单外 |
| 后端 | Spring Boot 3.5.9，**http://localhost:8082**（IDE 启动，日志不落盘） |
| 复测实例 | 自建 **8083** 实例（同一 MySQL），用于抓异常栈，测毕已停止 |
| 数据库 | docker `erp-mysql`（MySQL 8，库 `ecommerce_logistics_erp`） |
| 消息 | docker `rmqnamesrv` Up，**`rmqbroker` 处于 Restarting(255) 重启循环** |
| 账号 A | `founder@local.com`（角色 TENANT_ADMIN）——即截图里那个登录态 |
| 账号 B | `admin` / `123456`（角色 PLATFORM_ADMIN + TENANT_ADMIN） |
| 路由全集 | **42 条**（`/` index + `routes.jsx`/`App.jsx` 41 条），本轮全部访问过 |

方法：
1. **页面遍历**——42 条路由逐条进入，记录渲染结果与全部网络请求；
2. **元素级操作**——按钮、Tab、下拉、开关、Modal、Popconfirm、分页、表格行操作逐个真点；
3. **字段值验证**——表单填合法值、边界值（0、负数、非法国家码、不存在的 ID）、空值，比对提交结果、回显、校验提示、联动；
4. **流程串联**——按 导入→建单→状态流转→物流发货→采购入库→财务结算 的真实顺序跨页面操作，并回 DB 核对落库；
5. **根因定位**——每条缺陷追到 文件:行号，阻断项用异常栈或运行时证据（React fiber 读注册 namePath、DOM 实测）坐实；
6. **二轮补充的测量纪律**：antd v6 的 `Select` 选项**只在下拉展开时挂载**，且虚拟列表只渲染视窗。首轮我用"未展开时数 `.ant-select-item-option`"得到一片 `opt: 0`，据此写了"六个页面映射都不可用"——那是**假信号**（商品/客户/物流三页其实有完整的常量回退字典）。二轮全部改成"先 `mousedown` 展开、再数展开面板里的选项"，并逐页核 `*LabelMap` 的条目数与后端 `target-fields` 的实际返回数是否一致。报"下拉是空的"之前必须展开数一次。

### ⚠️ 本轮写入共享库的测试数据（需要确认是否清理）

| 对象 | 测试前 | 测试后 | 说明 |
|---|---|---|---|
| `orders` | 13 | **63** | 其中 49 行来自订单导入的样例数据；另 8 行是命名含 QA 的手工单（QAOK-1/2、QABAD-1、QASOLO-1、QA-EXT-001/002、ORD20261006CEA4A6D2、ORD20261005E619CA5…） |
| `order_import_jobs` | 6 | **13** | 全部 RUNNING（见 V-05） |
| `purchase_orders` | 4 | 5 | 新增 PO-c125ffde（用于验证采购闭环，已走完审批→送达→入库） |
| `financial_transactions` | 4 | 5 | 新增 TXN-ed95a6fb（采购应付，已置成功） |
| `logistics_orders` | 3 | 4 | 新增 LG202610060035133641712（用于复现 V-06 单条发货） |
| `locations` | — | +1 | A-01-01 |
| `warehouses`/库存 | — | SKU-Z 45→**48** | 采购送达入库所致 |
| 测试商品 | 已自删 | — | QA-LOSS-TEST、QA-PROBE、QA-BLASTRADIUS 三个探针商品均已 DELETE |

我未改动任何业务代码。若要把数据回到测试前状态，我可以逐条删除以上 QA 命名对象（**等你确认再动**）。

**二轮（01:30–02:10）没有新增任何写入**：只做了点开下拉、切页签、勾「使用示例数据」、点「拉取并预览」与「新增映射」（纯前端 state）、以及一次「查看明细」Modal；**没有点过任何「执行导入 / 直接导入 / 上传并直接导入 / 一键同步导入 / 开始同步」**。复测后 `order_import_jobs` 仍是 13、`product/customer/logistics/finance_import_jobs` 仍是各 1 行，与首轮计数字节一致。

---

## 2. 结论（一句话）

**用户端目前不具备"业务闭环可演示"的条件**：截图里订单导入页的报错只是表象，根子上有 **4 个 P0 阻断**（导入页配置加载被单个 403 整批拖垮——六个入口的平台集成下拉恒空、真实平台拉取通道整体不可用，其中 order/finance 两页的字段映射编辑器另断 0 选项；批量导入 6 个模块里 5 个必 500；商品编辑与新增 SKU 必 500；菜单把租户管理员引向 4 个他没权限的入口）；**跨模块联动事实上不存在**（订单创建不生成物流单、销售不扣库存、物流完成不入财务），单号/状态/枚举的展示层问题遍布 6 个页面但不阻断。

采购线是**唯一一条完整跑通的闭环**（创建→审批→运输中→送达→自动入库+自动应付→财务置成功），全程 UI 实操通过。

---

## 3. 缺陷总览

| ID | 级别 | 类型 | 一句话 | 影响面 |
|---|---|---|---|---|
| V-01 | P0 | 前端缺陷 | 导入页 `Promise.all` 被 403 拖垮：平台集成下拉恒空（6 页），映射编辑器另断 2 页 | 6 个导入入口（映射编辑器实测只有 order/finance 真断） |
| V-02 | P0 | 后端缺陷 | 批量导入执行阶段 5/6 模块 HTTP 500（Hibernate 集合替换异常） | 订单/商品/客户/物流/财务导入 |
| V-03 | P0 | 前后端 | 商品编辑与新增 SKU 必 500（聚合 `domainEvents` 为 null 触发 NPE） | 商品模块全部写路径 |
| V-04 | P0 | 权限一致性 | 菜单无条件渲染 4 个平台级入口，租户管理员进去全 403 | 导航 + 5 个页面 |
| V-05 | P1 | 数据一致性 | 导入任务永远停在 RUNNING、错误表恒 0 行，但订单已提交（500 却有数据） | 全部导入线 |
| V-06 | P1 | 流程不闭环 | 订单创建不发事件→不自动生成物流单；单条发货不回填订单状态；订单详情页无物流区块 | 订单↔物流 |
| V-07 | P1 | 功能缺陷 | 订单关键词搜索不匹配客户名称（搜索框却写着支持客户） | 订单列表 |
| V-08 | P1 | 流程不闭环 | 销售全链路不扣库存、不产生收入流水 | 订单→仓储→财务 |
| V-09 | P1 | 数据关联 | 导入订单的 `customerId` 恒为空串，客户详情"历史订单"永远无数据 | 客户↔订单 |
| V-10 | P1 | 校验缺失 | 创建订单不校验客户与 SKU 是否存在，物流/采购却校验 | 订单模块 |
| V-11 | P2 | 展示 | 状态选择器与批量发货表显示原始枚举码（`PROCESSING`、`CON_FIRMED`） | 订单详情、批量发货 |
| V-12 | P2 | 健壮性 | ErrorBoundary 自身抛错（`errorInfo` 恒为 null 却被解引用） | 全局兜底失效 |
| V-13 | P2 | 错误处理 | 未映射路径、415 等内容协商错误统一塌缩成 500 SYSTEM_ERROR | 全局 |
| V-14 | P2 | 展示 | 付款方式/付款状态/关联单据类型未本地化 | 订单详情、财务 |
| V-15 | P2 | 展示 | 首页发货率进度条显示 `7.6923076923076925%` | 首页 |
| V-16 | P2 | 展示 | 商品详情时间显示原始 ISO 串 `2026-04-29T00:11:02.755709` | 商品详情 |
| V-17 | P2 | 主数据 | 库存行 `productId` 与 `sku` 同值（都是 SKU 码），商品↔库存无连接 | 仓储详情 |
| V-18 | P2 | 数据可信度 | 支付交易号由前端 `TXN-${Date.now()}` 伪造后落库 | 订单创建 |
| V-19 | P2 | 契约 | `platform-integration`、`/api/warehouses` 直接返回聚合，`domainEvents` 出现在响应里 | 复证 ISS-063 |
| V-20 | P2 | 环境 | `rmqbroker` 重启循环，全部 MQ 事件投递失败（`No route info of this topic`） | 事件外发通道 |
| V-21 | P3 | 校验 | 运费试算接受重量 0 与 -5，无下限校验 | 物流试算 |
| V-22 | P3 | 性能 | StrictMode 下每个 GET 双发，`/api/auth/me` 单次加载 4 次 | 全站 |
| V-23 | P3 | 数据 | 平台筛选项缺 `MANUAL`/`FILE`，导入与手工单永远筛不到 | 订单列表 |
| V-24 | P3 | 种子数据 | `PO-54a6405d` 引用不存在的仓库 `WH-1` | 采购列表 |
| V-25 | P3 | 前端告警 | antd v6 弃用告警与 `Descriptions span` 溢出若干 | 控制台噪音 |
| V-26 | P2 | 功能缺失 | 商品线、客户线没有「下载模板」入口，而后端 `/template` 与 `api.js` 的 `downloadTemplate` 都在 | `/products/import`、`/customers/import` |
| V-27 | P2 | 过度承诺 | 文件导入的平台下拉写死 8 项（Amazon/eBay/Wish…），适配器实际只有 2 个协议键 | 6 个导入入口，复证 ISS-064 |
| V-28 | P3 | 一致性 | 「导入任务」页签有三种形态（刷新按钮 / 「说明」列 / 按钮文案 / 结果 Modal 内容各写各的） | 6 个导入入口 |

---

## 4. P0 阻断项详解

### V-01 导入页配置加载被 403 拖垮（截图报错的直接根因）

- **页面（6 个导入入口，失效面分两类）**：`/orders/import`、`/products/import`、`/customers/import`、`/finance/import`、`/logistics/import`、`/warehouses/shop`
- **共同机制**：五个页面把「目标字段字典 + 转换规则 + 平台集成列表」捆进同一个 `Promise.all`（`OrderImport.jsx:105`、`ProductImport.jsx:93`、`CustomerImport.jsx:97`、`FinanceImport.jsx:105`、`LogisticsImport.jsx:102`），而 `GET /api/platform-integrations` 对 TENANT_ADMIN 返回 **403**（`PlatformIntegrationController.java:44-46` 类级 `@PreAuthorize`）⇒ 整批 reject，两个**已经 200 返回**的字典接口结果一起被丢弃，`setTargetFields / setTransforms / setIntegrations` 一个都没执行。第六个页面 `/warehouses/shop` 给每次调用各自挂了 `.catch(() => [])`（`ShopWarehouseSync.jsx:44-47`），所以**不弹错**，但同样拿不到集成列表。
- **接口层是健康的**（判据，别误报后端）：`/api/{module}/import/target-fields` 与 `/transforms` 对 `founder@local.com`（TENANT_ADMIN）和 `admin`（PLATFORM_ADMIN）**都是 200**——target-fields 分别 订单 24、商品 14、客户 9、物流 28、财务 9、店铺仓 9 个字段，transforms 一律 4 条规则；整个导入表面**唯一的 403 就是 `/api/platform-integrations`**。
- ⚠️ **方法学教训（本轮我自己错过一次）**：antd v6 的 `Select` 选项**只在下拉展开时挂载**，且虚拟列表只渲染视窗，未展开时 `optionCount` 恒为 0——那是假信号。下表全部改成"点开下拉后再数"。
- **逐页实测（🔄 2026-04-29 二次实测更正：初版把"映射下拉 0 选项"笼统写成 6 个页面，过头了）**：

  | 页面 | 挂载提示 | 平台集成下拉 | 映射表（预览后点开下拉） |
  |---|---|---|---|
  | `/orders/import` | 「没有权限执行该操作」×2 +「加载订单导入配置失败」×2 | **0 项** | 预览出 19 行，但「系统标准字段」「转换规则」**均 0 项**、显示原始码 `EXTERNAL_ORDER_ID` / `NONE`；「新增映射」新建行同样 0 项 ⇒ **映射编辑器完全不可用** |
  | `/finance/import` | 同上，前缀「财务」 | **0 项** | 10 行，两个 Select **均 0 项**（`EXTERNAL_TRANSACTION_I…` / `NONE`）⇒ **不可用** |
  | `/products/import` | 同上，无前缀「加载导入配置失败」 | **0 项**（点开显示 No data） | 12 行，目标字段与转换规则**有选项**（回退表实为 14 / 4 项，点开视窗内可见 11 / 4）⇒ 可用 |
  | `/customers/import` | 同上，前缀「客户」 | **0 项** | 7 行，目标字段有选项（回退 9 项），但**转换规则只剩 1 项「不转换」**（`CustomerImport.jsx:271` 的回退数组只写了 `NONE`，另外 3 条规则丢失）⇒ 半可用 |
  | `/logistics/import` | 同上，前缀「物流」 | **0 项** | 20 行（示例 50 条），目标字段与转换规则**有选项**（回退 28 / 4 项）⇒ 可用 |
  | `/warehouses/shop` | **无任何提示**（`.catch` 吞掉） | **0 项** | 本页无映射表；`fetchJobs`（`:61-69`）失败只 `console.error`，任务列表静默为空 |

  > 行数按 `tbody tr` 计数，含 antd 的 `.ant-table-measure-row` 一行占位；真实映射行数 = 计数 − 1（订单 18 行、财务 9 行、商品 11 行、客户 6 行、物流 19 行）。Select 的判定不受此影响（数的是展开面板里的 `.ant-select-item-option`）。
- **两条真实后果**：
  1. `integrationId` 在用户端**永远取不到值** ⇒ 「平台拉取导入」主通道整体不可用，只剩「使用示例数据」这条假数据链路。实测死控件：`/logistics/import` 的「一键同步导入」弹「请选择平台集成后再执行一键同步」（`LogisticsImport.jsx:255-257`），`/warehouses/shop` 的「开始同步」弹「请选择平台集成」。
  2. order / finance 两页的映射既不可编辑也无法新建（新增行的两个下拉都 0 项）。
- **回退字典与后端一致（不是漂移，别误报）**：前端常量表条目数与后端实测逐一对齐（order 24、product 14、customer 9、logistics 28、finance 9；transforms 4），所以有回退的三页映射结果可信；残留风险只是"两处字典需同步维护"。
- **修复方向**：`Promise.all` → `Promise.allSettled`（或按 `ShopWarehouseSync.jsx:44-47` 每次调用自带 `.catch`），集成列表失败时降级为空数组并给可区分文案；同时给 order / finance 补上其余三页已有的 `*TargetFieldLabelMap` 回退，并把 customer 的转换规则回退补满 4 条。`platform:manage` 的 403 属 V-04，两者都要修。

### V-02 批量导入执行阶段 5/6 模块必 500

- **页面**：各导入页的"导入"动作（`POST /api/{module}/import` 与 `POST /api/orders/import/upload?mode=import`）
- **现象**：预览能出数据、导入直接红条「系统内部错误，请稍后重试」，任务列表里那条永远"执行中"。
- **复现**（两条路都验过）：
  1. `POST /api/orders/import/upload`，`mode=preview` → **200**，`mode=import` → **HTTP 500 SYSTEM_ERROR**；
  2. `POST /api/orders/import/preview`（`useSampleData=true`，50 行）→ `POST /api/orders/import` → **HTTP 500**。
- **异常栈**（8083 实例抓到的原文）：
  ```
  org.springframework.orm.jpa.JpaSystemException:
    A collection with orphan deletion was no longer referenced by the owning entity instance:
    ...jpa.entity.OrderImportJobJpaEntity.errors
    at ...JpaTransactionManager.doCommit(JpaTransactionManager.java:567)
    at ...OrderImportJobRepositoryImpl$$SpringCGLIB$$0.save(<generated>)
    at ...OrderImportApplicationService.persistJob(OrderImportApplicationService.java:616)
    at ...OrderImportApplicationService.importOrders(OrderImportApplicationService.java:199)
  ```
- **根因**：`OrderImportJobJpaEntity.java:77-78` 的 `errors` 集合是 `cascade = ALL + orphanRemoval = true`，而 `OrderImportJobRepositoryImpl.save()` 先 `findById(job.getId()).orElseGet(new)`（`:36-37`）拿到**托管实体**，再 `entity.setErrors(new ArrayList<>(...))`（`:66`）**整体替换**该集合引用——Hibernate 在 commit 时抛集合替换异常。
- **🔄 2026-04-29 二次实测：这条不是订单线独有，而是 5/6 条线共用同一份代码形状**：
  - 六条线的作业仓储**全部**是 `findById().orElseGet(new)` + `entity.setErrors(freshList)`（`OrderImportJobRepositoryImpl:36,66`、`ProductImportJobRepositoryImpl:66`、`CustomerImportJobRepositoryImpl:66`、`LogisticsImportJobRepositoryImpl:67`、`FinanceImportJobRepositoryImpl:65`、`ShopWarehouseImportJobRepositoryImpl:37,67`），六张 `*ImportJobJpaEntity` 也都带 `orphanRemoval=true`（各 `:77`/`:78`）。
  - 区别只在**持久化次数**：五条业务线都是"先落一次 RUNNING、跑完再落一次"的双写——`OrderImportApplicationService:183 / :198 job.complete() / :199`、`ProductImportApplicationService:171 / :186 / :187`、`CustomerImportApplicationService:188 / :203 / :204`、`LogisticsImportApplicationService:186 / :201 / :202`、`FinanceImportApplicationService:186 / :201 / :202`（`persistJob` 定义在 `:615 / :572 / :608 / :673 / :573`）；第二次 `save` 命中托管实体 ⇒ 抛异常。由于导入方法本身**没有** `@Transactional`（只有仓储 `save` 有），第一次持久化已各自提交，于是作业行留在 RUNNING、异常只吞掉收尾那一次写。
  - 唯一正常的 `shop_warehouse` 线不是写法更安全，而是**只保存一次**：`WarehouseImportApplicationService:187 job.complete()` → `:495-496 persistJob/save`，实体仍是瞬态 ⇒ 不触集合替换。这就是仓内现成的修法样板。
  - **运行库实证**（2026-04-29 直读 `ecommerce_logistics_erp`）：`order_import_jobs` 13 行**全 RUNNING**，`product / customer / logistics / finance_import_jobs` 各 1 行**也全 RUNNING**；对应的 5 张 `*_import_job_errors` **全部 0 行**。只有 `shop_warehouse_import_jobs` 有 `FAILED` 终态 + `shop_warehouse_import_job_errors` 2 行。⇒ 与 V-05 完全对上：错误行落不进表、作业永远不到终态。
- **修复方向**：改成 `errors.clear()` 后 `addAll(...)`（保持集合引用不变），或照 `WarehouseImportApplicationService` 收敛成完成后单次持久化。这条与 V-05 必须一起修，否则"500 但数据已写"的裂缝一直存在。

### V-03 商品编辑 / 新增 SKU 必定 500，且编辑页 SKU 全空白

这是本轮最严重的一条，**前端绑错 + 后端空指针**叠加。

**（a）后端：任何走聚合方法的商品写操作都 NPE**

- **接口实测**（对同一个自建探针商品）：
  | 操作 | 结果 |
  |---|---|
  | `PUT /api/products/{id}`（省略 skus） | **500** |
  | `PUT /api/products/{id}`（skus 合法两条） | **500** |
  | `PUT /api/products/{id}`（`skus: []`） | **500** |
  | `PUT /api/products/{id}`（skus 一条） | **500** |
  | `POST /api/products/{id}/skus`（新增 SKU） | **500** |
  | `PUT /{id}/activate`、`/deactivate`、`/products/batch/active` | 200（这些方法不注册事件，侥幸躲过） |
  四种 PUT 全部 500 ⇒ **商品编辑功能 100% 不可用**；500 触发事务回滚，所以数据没有被改坏（这也是我原先"会静默删光 SKU"推测的反证——**已修正结论，按实测写**）。
- **异常栈原文**：
  ```
  java.lang.NullPointerException: Cannot invoke "java.util.List.add(Object)" because "this.domainEvents" is null
    at ...domain.product.aggregate.Product.updateInfo(Product.java:128)
    at ...application.service.ProductApplicationService.updateProduct(ProductApplicationService.java:149)
  另一次同型异常落在 Product.addSKU(Product.java:145)
  ```
- **根因**：`Product.java:38` 的 `domainEvents` 没有内联初始化，只有 `Product.create(...)`（`:104` 显式 `.domainEvents(new ArrayList<>())`）会给值；而 `ProductMapper.java:82` 是 `@Mapping(target = "domainEvents", ignore = true)`，从库里重建出来的聚合该字段就是 **null**，任何 `registerEvent` 立即 NPE。同一缺陷在订单/仓储侧表现为"事件列表恒空、静默不发"（`OrderMapper.java:90-91` 反射重置成 new ArrayList），在商品侧表现为"直接炸"——两种失效形态同源。
- **修复方向**：`Product.java` 字段内联初始化（或 builder 兜底），并把"聚合从库重建后 domainEvents 必须非空"做成断言级测试。

**（b）前端：编辑页 SKU 表格的每个单元格都绑到了 `undefined`**

- **页面**：`/products/:id/edit`（用种子商品 `60696d57-…` 实测）
- **现象**：标题、租户等普通字段正常回显，**SKU 表格 8 个单元格全空**，货币也空，启用开关为 null；点保存之后（修好 V-03a 之前）是 500，修好之后按现有提交逻辑会把 SKU 清空。
- **复现**：商品列表 → 任一商品 → 编辑。
- **证据**：React fiber 读出的**实际注册 namePath** 是 `[null,'skuCode'] … [null,'attributes']`（8 列全中），store 里读到的是 `skus: [{}]`。
- **根因**：`ProductForm.jsx:246` 把 `Form.List` 的 field 包了一层记录再喂给 Table：
  ```jsx
  dataSource={fields.map((field) => ({ key: field.key, field }))}
  ```
  而每个单元格的渲染签名是 `render: (_, field) => <Form.Item name={[field.name, 'skuCode']} …/>`（`:111/122/133/144/155/166/177/188`）。antd Table 传给 render 第二个参数是**行记录**（即 `{key, field}` 包装对象），不是 `Form.List` 的 field，于是 `field.name === undefined`，namePath 变成 `[undefined, 'skuCode']`，写进了另一处 store。对照证据：**"操作"列写的是 `record.field.name`（`:246` 附近、`:261`）——作者知道真实形状，只有数据列写错了**。
- **连带后果**：`handleSubmit` 用 `.filter((sku) => sku && sku.skuCode)`（`:75`）过滤，编辑态一条 SKU 都没有 ⇒ 提交 `skus: []`，而 `ProductApplicationService.java:164-166` + `replaceSkus`（`:353`，注释"移除本次未提交的SKU（整体覆盖语义）"）是**整体覆盖**语义。一旦 V-03a 被单独修好而没修这里，就会变成"保存一次编辑、删光该商品所有 SKU"的真实数据丢失。
- **另一处佐证**：商品详情页写着「提示：新增SKU请在「编辑」页面统一提交」，而这条路是全断的。

### V-04 菜单与权限模型不一致（租户管理员被引向 403）

- **页面**：左侧导航 →「平台集成」「用户管理」「角色管理」「租户管理」相关全部入口
- **现象**（`founder@local.com`，TENANT_ADMIN）：
  - `/platform-integration`、`/users`、`/roles`、`/tenants` 四个菜单项**无条件显示**，点进去表格全空 + 红色「没有权限执行该操作」；
  - `/tenants` 更糟：接口 403 时**响应体是空的**（在过滤器层就被拒），axios 拦截器把它误判成「网络错误，请检查网络连接」；
  - `/platform-integration/create` 最恶劣：**整张表单可以填完**，只在点提交时 `POST` 才 403，然后弹两条「没有权限执行该操作 / 创建平台集成失败」；
  - `/users/create` 的角色下拉框是空的，页面根本配不下去。
- **根因**：`Layout.jsx:166-185` 只有"租户管理"用了 `hasRole('PLATFORM_ADMIN') ? [...] : []` 做门控，`平台集成(:169)`、`用户管理(:174)`、`角色管理(:179)` 是裸项；而路由侧 `AuthGuard` 对 `/platform/dashboard`、`/platform/config` 挂了 `PlatformAdminGuard`（能正确渲染 403 Result），对 `/tenants*`、`/users*`、`/roles`、`/platform-integration*` **没有任何角色门**。
- **这是有意的业务决策，不是后端漏配**：`PermissionPoints.java`（当前 untracked，你正在进行的工作）明确把 `platform:manage`、`user:manage`、`role:manage` 划为平台级，注释写着"租户隔离切面会让平台管理员跨租户放行，这三项权限不应下沉给租户管理员"，且 `DataInitializationConfig.ensureTenantAdminPermissions()` 会幂等地把 TENANT_ADMIN 对齐成 19 项租户级权限。**所以要改的是前端菜单与路由门控，不是放宽后端注解。**
- **对照验证**：`admin`（PLATFORM_ADMIN）下这四个接口全部 **200**，接口本身没问题。
- **⚠️ 别顺手放宽 `PlatformIntegrationController`**：`getAllPlatformIntegrations()`（`:98-110`）不接受 `tenantId`，直接委托无过滤的服务调用（`PlatformIntegrationApplicationService.java:95-97`），把注解放松会直接造成跨租户数据泄露。`/auth-specs`（`:59-62`）同理。
- **顺带同步**：`user-web` 角色管理页另有一份硬编码 `PERMISSION_PRESETS`，`PermissionPoints` 改动时必须同步，否则"后端有权限、界面配不出来"。

---

## 5. P1 数据一致性与流程裂缝

### V-05 导入结果完全不可回溯（500 但数据已提交）

实测 DB（测试时点）：

| 表 | 行数 | 状态 | 计数 |
|---|---|---|---|
| `order_import_jobs` | 13 | **全部 RUNNING** | total 合计 23，success 0，failure 0 |
| `product/customer/logistics/finance_import_jobs` | 各 1 | RUNNING | total 1，0 / 0 |
| `shop_warehouse_import_jobs` | 1 | FAILED | total 2，failure 2（唯一诚实的一条） |
| 五张 `*_import_job_errors` | **0 行** | — | 一条错误都没落库 |

同时：`OrderImportApplicationService.importOrders`（`:153-201`）**没有 `@Transactional`**，`:183` 先落 RUNNING 任务，逐行导入各自提交，`:198-199` 收尾 `job.complete()` + `persistJob` 才抛 V-02 的异常。结果就是**订单写进去了（12→13，样例批量导入那次 13→62），HTTP 给 500，任务永远"执行中"，错误明细一行没有**。

🔄 **二次实测细化（不要写成"弹窗也打不开"）**：任务页签的交互本身是通的——`/products/import` 点「查看明细」会正常弹出「导入结果」Modal，原文是「执行中：共 1 行，成功 0 行，失败 0 行，跳过 0 行 / 任务ID：284e22bf-… / 平台：文件上传 / 新增 0 个商品 更新 0 个商品 / 开始时间：2026-04-29 00:11:02」。**坏的只是内容**：状态永不翻转、计数恒 0、没有错误列表，所以用户无法知道这一行到底成没成。页签形态另有两种（记入 V-28）：order / finance 有「刷 新」按钮且表头多一列「说明」，product / customer / logistics 既无刷新也无「说明」列。

修复顺序建议：V-02（让任务能收尾）→ V-05（让失败行能落 `*_import_job_errors`）→ 前端把 SUCCESS/FAILED 计数与错误明细展示出来。

### V-06 订单 ↔ 物流三个方向全断

1. **下单不自动生成物流单**：`Order.create` 在 `Order.java:140` 注册了 `OrderCreatedEvent`，`OrderApplicationService.createOrder:63` 也确实调了 `publishEventsFrom(order)`——但传给发布器的是 `orderRepository.save(...)` 返回的**重建副本**（`OrderProcessingService.java:55` → `OrderRepositoryImpl.save` 末尾 `orderMapper.toDomainAggregate(savedEntity)`），该副本 `domainEvents` 被 `OrderMapper.java:90-91` 重置成空列表，`DomainEventPublisher.publishEventsFrom` 见空集合直接静默返回。
   **运行时验证**：8083 全程日志里 `发布领域事件` 只出现在商品域，`POST /orders` 建单成功后**零条订单事件日志**，`GET /api/logistics/order/{orderId}` 返回 `[]`。这正是 **ISS-062** 描述的现象，本轮新增了一条独立复现证据。
2. **单条发货不回填订单状态**：`LogisticsApplicationService.ship(logisticsOrderId, shippedAt, trackingNumber)`（`:193`）只动物流单，完全不写 `orderRepository`；而批量发货的 `shipSingle`（`:306`，受 `syncOrderStatus` 控制，`:332-335` 会 `order.updateStatus(OrderStatus.SHIPPED)`）会同步。**同一个模块里两条语义相反的路径**。
   实测：手工建单 LG202610060035133641712 → 点"发货" → 物流单变 `COLLECTED(已揽收)`，订单 QAOK-2 **仍是 `PENDING`**。
3. **订单详情页没有物流区块**：`OrderDetail.jsx` 全文 grep `logistics|物流` **0 命中**，而后端有 `GET /api/logistics/order/{orderId}`（`LogisticsController.java:107`）。反方向的入口（物流→订单）有，正方向没做。

### V-07 订单搜索：搜索框承诺了实现没有的能力

- 页面提示与 placeholder 都写"订单号/客户/平台订单号"，UI 输入 `张三` → **0 行**；
- `OrderSpecification.java:57-62` 的 keyword OR 只拼了 `orderNumber`、`platformOrderId`、`customerId`，**没有 `customerName`**（而 `:44-46` 说明 `customerName` 作为独立条件是存在且能匹配的）；
- 对照：`?customerName=张三` → 1 行，`?customerId=cust-1` → 1 行，`?keyword=张三` → 0 行。

### V-08 销售链路不出库、不产生收入

- 探针：`STOCK-CHECK-1` 用 SKU-Z ×10，`WH-EAST` 初始 45；订单一路推到 `CON_FIRMED → SHIPPED`，**库存始终 45**。只有采购确认送达才把 45 变成 48。
- 首页"总收入"始终是导入的那一笔平台流水 ¥199，销售完成不生成 INCOME 流水。
- 根因不是 bug 而是**空实现**：`OrderStatusChangedEventConsumer.onMessage` 与进程内 `EventHandler.handleOrderStatusChangedEvent` 都只写着「订单确认后应预留库存；仓储侧预留接口尚未实现，此处仅记录意图」；`handleLogisticsCompletedEvent` 同样只 `log.info("物流 {} 完成，生成财务结算记录")` 而无任何调用。**两条通道（MQ + 进程内）都是桩。**
- 叠加 V-06(1)：这些桩本来就收不到消息，因为事件根本没发出去。

### V-09 客户 ↔ 订单没有关联起来

- `OrderImportApplicationService.java:349`：`String customerId = StringUtils.hasText(row.getCustomerId()) ? row.getCustomerId() : "";`——导入**从不解析也从不创建 Customer**，客户 ID 恒为空串。
- 结果：客户张三的记录 ID 是 `CUST-e265f04f`，他的订单却挂在 `cust-1` 或 `''` 上；`CustomerDetail.jsx:63-66` 用 `[customerId, externalBuyerId]` 去调 `GET /api/orders/customer/{id}`，实测 `CUST-e265f04f → 200 []`、`amz-buyer-001 → 0`，所以客户详情页「历史订单」区域恒为 **No data**。同页 `cust-1 → 1`、`CUST-QA-01 → 1` 证明接口本身是通的。

### V-10 引用完整性校验在各模块之间不对称

| 入口 | 传不存在的引用 | 结果 |
|---|---|---|
| `POST /api/orders` | `customerId=CUST-NOT-EXIST-999` | **200**，建出 `ORD20261006D9EC3DC1` |
| `POST /api/orders` | SKU 不在商品目录 | **接受**，不校验 |
| `POST /api/logistics` | `orderId=NO-SUCH-ORDER-999` | 404「订单不存在，订单号=NO-SUCH-ORDER-999」 |
| `POST /api/purchases` | `warehouseId=NO-SUCH-WH-999` | 400「收货仓库不存在: NO-SUCH-WH-999」 |

订单是链路最上游、也最该校验的一环，反而是唯一不校验的。

---

## 6. P2 / P3 展示与健壮性

- **V-11 状态机选择器显示原始码**：`OrderDetail.jsx:39` 初始 `useState(undefined)`，`:50` 把 `setSelectedStatus(data?.status)` 设成**当前状态**，而 `:349-353` 的下拉选项来自 `allowedNext`（`:190`）——当前状态不在候选集里，antd 只能回显裸码。DOM 实测 `<div class="ant-select-content-value" title="PROCESSING">PROCESSING</div>`，而下拉里正确地是"已确认/已发货/已取消"。批量发货弹窗的"状态"列同样显示 `CON_FIRMED`（与 ISS-004 的枚举命名问题连在一起）。
- **V-12 错误边界自己是坏的**：`App.jsx:15-17` 的 `getDerivedStateFromError` 只返回 `{hasError: true}`，`:36` 却无条件解引用 `this.state.errorInfo.componentStack`（`componentDidCatch` 在 render 之后才跑）→ 兜底页自己抛 `Cannot read properties of null (reading 'componentStack')`。现有 ISS-033 只记了"两份实现"，没记这个必崩的 bug。
- **V-13 错误码塌缩**：`/api/finance`、`/api/purchase`（少 /s）、`/api/definitely-not-here` 这类未映射路径，以及往 multipart 的 `/upload` 发 JSON 造成的 415，**全部返回 500 SYSTEM_ERROR**，把路由错、内容协商错都伪装成服务端崩溃，排查时误导方向。
- **V-14 枚举未本地化**：订单详情的付款方式显示 `CREDIT_CARD`、付款状态 `PENDING`；财务列表与详情的"关联单据类型"显示 `PURCHASE_ORDER`。
- **V-15 首页百分比**：`Home.jsx:242` 的完成率正确，`:249` 的发货率 `<Progress percent={orderShippingRate}>` 没取整 → 进度条旁是 `7.7%`（`:247` 正确 `toFixed(1)`）而进度条内嵌文字是 `7.6923076923076925%`。
- **V-16 商品详情时间**：原样打印 `2026-04-29T00:11:02.755709`。
- **V-17 商品↔库存断链**：仓库详情的库存行 `productId` 与 `sku` 同值（例如两者都是 `SKU-Z`），`locationId`、`lastUpdated` 为 null，无法从商品侧下钻到库存。
- **V-18 前端伪造交易号**：`CreateOrder.jsx:55` 写成 `transactionId: values.transactionId || TXN-加时间戳` 的兜底。之所以前端必须补这个假值，是因为 `PaymentInfo` 的工厂要求交易号非空——实测漏传时报 `JSON parse error: Cannot construct instance of ...PaymentInfo, problem: Transaction ID cannot be null`，返回 400「请求体格式不正确，请检查必填字段与取值」（提示也与真实原因不符）。结果是**库里存的支付交易号是客户端生成的**，不具备任何外部可对账性。
- **V-19 聚合当契约**（复证 ISS-063）：`/api/platform-integrations`、`/api/warehouses` 的响应里带 `domainEvents: []`。
- **V-20 环境**：`docker ps` 显示 `rmqbroker` **Restarting (255)**；8083 日志里 15 次 `No route info of this topic: product-events` + `发布事件到RocketMQ失败`，`DomainEventPublisher.publishToRocketMQ` 吞掉异常只记 error，业务侧照常返回 200。即便修好 V-06，MQ 通道在当前环境也一条消息都出不去。
- **V-21 运费试算边界**：重量填 `0` 与 `-5` 都**静默接受**并返回报价（无 min 校验）；`country=CN` 选 DHL 能正确拒绝、非数字输入能正确拒绝——校验只做了一半。
- **V-22 StrictMode 双发**：每个 GET 请求都发两次，`/api/auth/me` 一次加载发 4 次；截图里"每条错误弹 2 个 toast"就是这个放大的。
- **V-23 筛选项缺失**：`constants/order.js` 的 `platformOptions` 不含 `MANUAL`/`FILE`，导入单和手工单在平台维度永远筛不到。
- **V-24 种子数据引用不存在的仓库**：`PO-54a6405d` 的 `warehouseId = WH-1`，仓库表无此行（讽刺的是 `POST /api/purchases` 自己会校验这个）。
- **V-25 antd v6 弃用告警**：Divider `orientation`、Modal `destroyOnClose`、Statistic `valueStyle`、List、Space `direction`、Descriptions `span` 溢出、Table rowKey 用 index 等，控制台长期噪音。
- **V-26 商品线、客户线没有"下载模板"入口**：`api.js` 给**全部六条线**都实现了 `downloadTemplate`（`orderImportApi:148-172`、`logisticsImportApi:212-237`、`productImportApi:258-281`、`warehouseImportApi:311-335`、`customerImportApi:351-374`、`financeImportApi:395-419`），后端六个 `/api/{module}/import/template` 实测**全部 200** 且返回真实 blob（订单 448、商品 183、客户 199、物流 200、仓库 622、财务 286 字节）——但界面只在 order / finance / logistics 的「文件上传导入」页签渲染了「下载CSV模板」，`/warehouses/shop` 渲染了「下载导入模板」，**`/products/import` 与 `/customers/import` 的文件页签只有「解析预览」「直接导入」两个按钮**。⇒ 这两条线的用户只能靠猜列名准备 CSV，接口与 service 层都已就绪，缺的只是一颗按钮。
- **V-27 平台下拉过度承诺**：文件导入页签的"平台"下拉是前端写死的 **8 项**（`文件上传` + `Amazon / eBay / Shopify / AliExpress / Wish / …`，点开实测 `optCount = 8`），而 `PlatformFetcherRegistry` 实际只有 **SHOPIFY（4 个适配器）与 CUSTOM_SHOP（7 个）两个协议键**（见 ISS-064）。用户选 Amazon/eBay 以为能拉，实际落不到任何适配器上。今天这一条被 V-01 挡着（集成根本选不到），所以**修 V-01 之前先收紧这份常量表**，否则修完就把误用面铺开。
- **V-28 导入任务页签三种形态**：同一个"导入任务"能力在三组页面长得不一样——order / finance 有「刷 新」按钮且表头多一列「说明」（10 行全执行中，每行「明细」）；product / customer / logistics 无刷新按钮、无「说明」列，按钮文案是「查看明细」且列表只有 1 行；`/warehouses/shop` 则是把任务表直接平铺在同步页里（表头多「文件名」「平台」两列，操作叫「明细」）。Modal 内容也是各写各的：订单线弹「总数：1 / 成功：0 / 失败：0 / 跳过：0 / 任务状态：执行中」，商品线弹「执行中：共 1 行，成功 0 行… / 新增 0 个商品 更新 0 个商品 / 开始时间…」——**两者的共同点是都没有错误列表**（与 `*_import_job_errors` 恒 0 行一致）。属同一套后端 DTO 的三种前端抄写，后续加状态筛选/分页时要一起收敛。

---

## 7. 页面遍历台账（42 条路由）

图例：✅ 正常 ｜ ⚠️ 可用但有缺陷 ｜ ⛔ 阻断 ｜ 🔒 权限不足

| # | 路由 | 结果 | 备注 |
|---|---|---|---|
| 1 | `/login` | ✅ | 双账号均可登录 |
| 2 | `/`（首页） | ⚠️ | V-15；卡片跳转均正常 |
| 3 | `/orders` | ⚠️ | 列表/详情/分页（13→2 页→10 条每页）均正常；V-07 搜索、V-23 筛选 |
| 4 | `/orders/:id` | ⚠️ | 状态更新 处理中→已确认 成功并刷新表格；V-06(3) 无物流区块、V-11、V-14 |
| 5 | `/orders/create` | ⚠️ | 建单成功并跳详情；V-10 不校验引用、V-18 伪造交易号；`Form.List` 用 Card 渲染，**没有** ProductForm 的绑定缺陷 |
| 6 | `/orders/import` | ⛔ | **截图页**；V-01（两条红条 ×2，二轮点开下拉实测：平台集成 **0 项**，预览 19 行映射的「系统标准字段」「转换规则」也 **0 项**、显示 `EXTERNAL_ORDER_ID`/`NONE`，「新增映射」新行同样 0 项 ⇒ 映射编辑器真断）、V-02 导入 500、V-05 任务不可回溯；有「下载CSV模板」 |
| 7 | `/products` | ✅ | 列表渲染正常（早前一次崩溃是我手工 remove DOM 导致，非产品缺陷，已排除） |
| 8 | `/products/:id` | ⚠️ | V-16、V-17，且提示语把用户往坏路上引 |
| 9 | `/products/create` | ⚠️ | 创建可成功（走 `Product.create`，事件能发） |
| 10 | `/products/:id/edit` | ⛔ | V-03 双缺陷：SKU 全空白 + 保存必 500 |
| 11 | `/products/import` | ⛔ | V-01（红条 ×2、集成下拉 0 项）+ V-02；🔄 二轮更正：**映射编辑器可用**（`ProductImport.jsx:259-265` 常量回退，点开实测目标字段/转换规则都有选项，回退表 14/4 项与后端一致）；缺「下载模板」按钮（V-26） |
| 12 | `/customers` | ✅ | 列表正常 |
| 13 | `/customers/:id` | ⚠️ | V-09 历史订单恒空 |
| 14 | `/customers/create` | ✅ | 建客户 + 边界值校验通过 |
| 15 | `/customers/import` | ⛔ | V-01 + V-02；🔄 二轮细化：目标字段回退正常（9 项），但**转换规则只剩「不转换」1 项**（`CustomerImport.jsx:271` 回退数组只写了 `NONE`）；缺「下载模板」按钮（V-26） |
| 16 | `/warehouses` | ✅ | 列表/详情正常 |
| 17 | `/warehouses/:id` | ⚠️ | V-17；添加库位 A-01-01 成功 |
| 18 | `/warehouses/create` | ✅ | 创建 + 回显正常 |
| 19 | `/warehouses/shop` | ⛔ | 🔄 二轮更正：**本页不弹任何红条**（`ShopWarehouseSync.jsx:44-47` 每调用各自 `.catch(() => [])`，`:61-69` 的 `fetchJobs` 失败只 `console.error`），代价是集成下拉静默空、任务表可能空；「开始同步」守卫文案正确（「请选择平台集成」）但属永久死控件；店铺仓导入仍是唯一任务态诚实的一条（FAILED + 2 条错误行） |
| 20 | `/logistics` | ✅ | 列表、状态字典正常 |
| 21 | `/logistics/:id` | ⚠️ | 单条发货不同步订单（V-06(2)） |
| 22 | `/logistics/create` | ✅ | 建单成功并自动跳详情 |
| 23 | `/logistics/channels` | ✅ | 渠道列表正常 |
| 24 | `/logistics/import` | ⛔ | V-01 + V-02；🔄 二轮细化：映射编辑器**可用**（前端回退表 28 项 / 4 条规则，与后端 `logistics/import/target-fields` 实测 28 项、`transforms` 4 条一致），示例数据可出 50 条 / 20 行映射，预览后出现「按当前映射重新预览」；「一键同步导入」守卫正确但属永久死控件（V-01 后果）；有「下载CSV模板」 |
| 25 | `/purchase` | ✅ | 列表正常；V-24 种子脏数据 |
| 26 | `/purchase/:id` | ✅ | 审批弹窗、按 `allowedNextStatuses` 渐进出现的动作按钮、Popconfirm 均正确 |
| 27 | `/purchase/create` | ✅ | 预估总金额实时联动正确；该页用本地 `useState` 管明细，不受 ProductForm 缺陷影响 |
| 28 | `/finance` | ✅ | 列表、"完成"Popconfirm 置成功正常 |
| 29 | `/finance/:id` | ⚠️ | V-14 关联单据类型裸码 |
| 30 | `/finance/create` | ✅ | 手工流水创建成功 |
| 31 | `/finance/import` | ⛔ | V-01 + V-02 + V-05；🔄 二轮实测：与订单页同形——集成下拉 **0 项**，预览 10 行映射的两个 Select **0 项**、显示 `EXTERNAL_TRANSACTION_I…`/`NONE` ⇒ 映射编辑器真断（`FinanceImport.jsx:339`、`:358` 直接 map 状态数组，无回退）；有「下载CSV模板」；本页比订单页多捆一路 `getTransactionTypes()`（4 项，实测 200） |
| 32 | `/platform-integration` | 🔒 | V-04 403 空表 |
| 33 | `/platform-integration/create` | 🔒 | V-04 表单可填完、提交才失败 |
| 34 | `/platform-integration/:id` | 🔒 | V-04 |
| 35 | `/users` | 🔒 | V-04 |
| 36 | `/users/create` | 🔒 | V-04，角色下拉为空 |
| 37 | `/users/:id` | 🔒 | V-04 |
| 38 | `/roles` | 🔒 | V-04 |
| 39 | `/tenants` | 🔒 | V-04 + 空响应体被误报成网络错误 |
| 40 | `/tenants/create` | 🔒 | V-04 |
| 41 | `/platform/dashboard` | ✅ | 有 `PlatformAdminGuard`，TENANT_ADMIN 下正确渲染 403 Result（门控范本） |
| 42 | `/platform/config` | ✅ | 同上 |

---

## 8. 业务流程闭环矩阵

| 流程 | 结论 | 依据 |
|---|---|---|
| 采购：创建→审批→运输中→送达→入库→应付→财务置成功 | ✅ **唯一完整闭环** | UI 全程实操：PO-c125ffde（预估 ¥60.00 实时联动）→ 审批通过 → 标记运输中 → 确认送达（提示「已确认送达，收货入库与采购应付已登记」）→ SKU-Z 45→48 + TXN-ed95a6fb（EXPENSE 60.0 PENDING）→ 财务完成 → 成功 |
| 订单：创建→状态流转→取消/删除 | ✅ | 处理中→已确认 成功且列表刷新；删除 Popconfirm→成功→回跳；"同步订单"对非平台单正确拦截 |
| 物流：手工建单→发货 | ⚠️ 半通 | 建单成功；批量发货弹窗可用（8 个渠道、"从订单挑选"只给 `CON_FIRMED`、跳过原因准确、结果面板总数/成功/失败/齐全）；但单条发货不回写订单 |
| 订单→物流 自动联动 | ⛔ | V-06(1)：事件从未发布 |
| 导入：预览→映射→执行→回溯 | ⛔ | 预览✅（六条线的示例数据都拉得出来）；映射编辑器 order/finance ⛔（0 选项）、customer ⚠️（转换规则只剩「不转换」）、product/logistics ✅；执行 ⛔ V-02（5 条线双写收尾必 500）；回溯 ⛔ V-05（恒 RUNNING、错误表 0 行）；**平台真实拉取通道整体 ⛔**：集成下拉恒空，`integrationId` 取不到，「一键同步导入」「开始同步」是死控件（V-01） |
| 销售→库存扣减 | ⛔ | V-08：预留/出库两侧都是桩 |
| 物流完成→财务结算 | ⛔ | V-08：`handleLogisticsCompletedEvent` 只打日志 |
| 客户→历史订单 | ⛔ | V-09：导入单 `customerId` 恒空串 |
| 商品→库存 | ⛔ | V-17：库存行无 productId↔SKU 连接 |
| 商品编辑→回显 | ⛔ | V-03 |
| 平台集成→同步订单 | 🔒 | V-04：租户管理员拿不到集成列表，入口即 403 |

---

## 9. 已验证通过（避免重复报修）

- 全部列表页与详情页渲染无崩溃，包括首页、订单、物流、商品、仓库、客户、财务、采购、渠道；
- 订单状态更新弹窗、订单删除 Popconfirm、采购审批 Modal、财务"完成"Popconfirm 均按预期工作并有正确的二次确认文案；
- 采购单状态按钮严格按 `allowedNextStatuses` 渐进出现，无越权状态可点；
- 批量发货的"从订单挑选"过滤、跳过原因（「订单已存在物流单[SF-QA-88001]，状态为[已揽收]，已跳过」）、结果统计全部准确；
- 库位新增、订单分页与每页条数切换、订单同步守卫、运费试算的国家/非数字校验、创建商品、创建客户、创建物流单、创建财务流水、创建采购单均通过；
- `admin`（PLATFORM_ADMIN）下平台集成/用户/角色/租户四个接口均 200，接口层没有功能性缺陷。
- **导入线逐项复测（2026-04-29 二轮，六条线全维度）**：六个 `/api/{module}/import/target-fields`（24/14/9/28/9/9）、六个 `/transforms`（各 4）、六个 `/template`（均 200 且是真 blob）、六个 `/field-mappings`、六个 `/jobs`、三个状态字典（订单 6 / 物流 8 / 财务 4）在 `founder@local.com`（TENANT_ADMIN）与 `admin` 下**全部 200**；「使用示例数据」预览在订单/商品/客户/物流/财务五条线都出数（19/12/7/20/10 行映射 + 各自数据行）；`/orders/import` 与 `/finance/import` 的「新增映射」能建行、`/logistics/import` 预览后出现「按当前映射重新预览」按钮；`/products/import` 与 `/logistics/import` 的映射下拉**可用**（前端 `*LabelMap` 常量回退生效）；`/warehouses/shop` 因每步各自 `.catch(() => [])` 而**不弹错**（`ShopWarehouseSync.jsx:44-47`）；`/logistics/import` 的「一键同步导入」与 `/warehouses/shop` 的「开始同步」在没有集成时都给出**正确守卫文案**（「请选择平台集成后再执行一键同步」/「请选择平台集成」）；`/products/import`、`/customers/import`、`/logistics/import` 未选集成直接拉取时提示「请选择平台集成，或勾选「使用示例数据」后再拉取」，文案准确。

---

## 10. 修复优先级建议

1. **V-01**（改 `Promise.all` → `allSettled`）——成本最低、解掉的正是截图上那四条报错，六个页面同时受益。**但别把它当成"修完映射就能用"**：`allSettled` 只把字典与集成列表救回来，order/finance 两页的映射编辑器还需要补 `*LabelMap` 常量回退（照 `ProductImport.jsx:259-265`），customer 那页要把转换规则回退从 1 项补满 4 项（`CustomerImport.jsx:271`）；集成列表本身仍要等 V-04 放开或提供只读候选接口，否则「平台拉取导入」「一键同步导入」「开始同步」依旧是死控件。
2. **V-04**（前端菜单与路由按 `PermissionPoints` 的门限收口，参照 `PlatformAdminGuard` 写法）——**不要动后端注解**，`getAllPlatformIntegrations()` 无租户过滤，放宽即泄露。
3. **V-02 + V-05**（导入任务集合改增量更新 + 错误落表 + 收尾状态）——导入线目前完全不可交付。
4. **V-03**（两步：先修 `ProductMapper` 的 `domainEvents`（或字段内联初始化）止血 NPE，**同轮**修 `ProductForm.jsx:246` 的 `render: (_, field)` 绑定，缺一半就会把 500 变成静默删 SKU）+ 同步把 `CreatePurchase.jsx` 的正确写法作为参照。
5. **V-06**（统一 `ship` 与 `shipSingle` 的订单同步语义，给订单详情补物流区块）——事件通道属 ISS-062 的架构级修复，单独立项。
6. **V-07 / V-08 / V-09 / V-10**——按业务优先级：搜索是纯 bug 可立刻补 `customerName`；扣库存与收入需要产品先定义记账规则；导入的客户解析要决定"按名匹配"还是"自动建档"。
7. P2/P3 批量清理（本地化、格式化、ErrorBoundary、错误码语义、antd 告警）。

---

## 11. 缺陷立项对照（与 [known-issues.md](./known-issues.md) §5 对齐）

本报告全部 V-xx 已按项目约定登记进 `known-issues.md`（新增 ISS-066…ISS-085，未重排历史编号）：

| V-xx | ISS-xx | 关系 |
|---|---|---|
| V-01 | ISS-066 | 新立 |
| V-02 | ISS-067 | 新立 |
| V-03a | ISS-068 | 新立（**ISS-062 的反向形态**：商品侧是 null 崩，其余是空列表静默） |
| V-03b | ISS-069 | 新立 |
| V-04 | ISS-070 | 新立 |
| V-05 | ISS-071 | 新立（与 ISS-040 同域不同面） |
| V-06(1) | — | **复证 ISS-062**，本轮补一条运行时证据 |
| V-06(2) | ISS-072 | 新立 |
| V-06(3) | ISS-073 | 新立 |
| V-07 | ISS-074 | 新立 |
| V-09 | ISS-075 | 新立 |
| V-10 | ISS-076 | 新立 |
| V-08 | ISS-077 | 新立（即便修好 ISS-062 仍断，因为是桩） |
| V-12 | ISS-078 | 新立（**不是** ISS-033 的重复实现问题） |
| V-13 | ISS-079 | 新立（与 ADR-0011 错误码字典缺失同族） |
| V-11 | ISS-080 | 新立（关联 ISS-004 枚举命名） |
| V-14 / V-15 / V-16 | ISS-081 | 合并为一条展示层残留 |
| V-17 | ISS-082 | 新立 |
| V-18 | ISS-083 | 新立 |
| V-19 | — | **复证 ISS-063**（现命中 `platform-integrations`、`warehouses`） |
| V-20 / V-21 / V-22 / V-23 / V-24 | ISS-084 | 环境项 + 四个 P3 小项合并登记 |
| V-25 | — | antd 弃用告警，与 ISS-055 同族，暂不单立 |
| V-26 / V-27 / V-28 | ISS-085 | 二轮导入线专项复测新增，三条合并登记 |

