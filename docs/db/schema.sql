-- =============================================================================
-- 跨境大卖ERP系统 — 数据库结构快照（schema snapshot）
--
-- 重要：本文件不是 schema 的唯一真实来源。
--   当前运行时的表结构由 Hibernate 依据 JPA 实体注解自动生成
--   （backend/src/main/resources/application.yml: spring.jpa.hibernate.ddl-auto=update）。
--   本快照按 ~~40~~ **43** 个带 @Table 的 `*JpaEntity` + 2 张 `@ElementCollection` 隐式集合表转写，共 ~~42~~ **45** 张表，
--   用于评审、ER 对照与将来切换 Flyway。
--   与实体逐列比对的基线时间：~~2026-03-22~~ → **2026-10-21**（45 张表的表名/列名/可空性与运行时库一致；
--   列级差异见下方 2026-03-22 更正与文末 2026-10-21 补登）。🔄 2026-10-21 补齐原先漏登的 3 张表：
--   `shops`（ShopJpaEntity）、`audit_logs`（AuditLogJpaEntity）、`system_configs`（SystemSettingJpaEntity），
--   逐列取自 `SHOW CREATE TABLE` 实测；此前快照只到 42 张，与运行时库（information_schema 实测 45 张）对不上。
--   ⚠️ 类型口径：本快照按实体声明形式书写。运行时库（ddl-auto=update 建出）里有 ~~**24 个列 / 17 张表**~~
--      → **2026-10-21 复测：26 个列 / 18 张表**实际是 MySQL 原生 `ENUM('...')`（information_schema 全量列出）：
--        · customers.status、orders.status、logistics_orders.status、inventory_records.status
--        · platform_integrations.platform_name、platform_integrations.status、**platform_integrations.auth_type**
--        · **audit_logs.result**（2026-10-21 新增登记）
--        · 6 张 *_import_jobs.status（customer/finance/logistics/order/product/shop_warehouse）
--        · 8 列 *_field_mappings.target_field + transform（customer/finance/logistics/order/product/shop_warehouse
--          各自两列；注意 warehouse 上下文用的是 shop_warehouse_field_mappings）
--      另 `platform_integrations.id`（UUID + @GeneratedValue）实际是 `binary(16)` 而非 CHAR(36)。切换 Flyway 时需以此为准。
--   修改表结构时，必须先改 JPA 实体，再同步本文件。
--
-- 方言：MySQL 8（database-platform = MySQL8Dialect）
-- 类型推导约定：String→VARCHAR(255)  Long→BIGINT  Integer/int→INT  Double→DOUBLE
--              boolean→BIT  LocalDateTime→DATETIME(6)  UUID→CHAR(36)
--              BigDecimal→DECIMAL(38,2)（未显式声明 precision/scale 时的 Hibernate 默认，实测）
--              仅显式写了 @Column(precision=19, scale=2) 的列才是 DECIMAL(19,2)
-- ✅ 2026-10-10（ISS-023 / R-09 / V7+V7.2）：金额侧已全部收敛，15 个金额列 =
--    14 个 DECIMAL(19,2) + 1 个 DECIMAL(19,4)（logistics_channels.price_per_gram，每克费率，R-52），
--    实体侧 15 个字段一律显式写 precision/scale，由 MoneyPrecisionGuardTest 的列宽名册常驻钉住。
--    仍然保留 DOUBLE 的只有两个**重量**列：skus.weight（映射自 weightInGrams）、
--    logistics_labels.package_weight —— 物理量不是货币，migration-plan §4 只约束金额列。
--    旧口径「BigDecimal→DECIMAL(38,2) 默认、只有 payment_infos.amount 是 19,2」自本行起作废。
-- ⚠️ 2026-03-22 更正（ISS-049）：6 张 *_import_job_errors 表的行号列原为 row_number，
--    ROW_NUMBER 是 MySQL 8 保留字，Hibernate 生成的 DDL 未加引号 → 建表语句直接报 1064 语法错误，
--    这 6 张表在 ddl-auto=update 下从未被建出来（连带 导入错误明细 写入功能整体不可用）。
--    实体侧已改名为 line_number，本快照同步。
-- 命名策略：Hibernate 隐式命名（camelCase 字段 → snake_case 列）
-- =============================================================================

CREATE DATABASE IF NOT EXISTS ecommerce_logistics_erp
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ecommerce_logistics_erp;

-- ---------------------------------------------------------------------------
-- 通用域：租户 / 用户 / 角色（多租户骨架）
-- 注意：租户列已从这 3 张表扩散到 ~~23 张表~~ → **25 张表**（orders / customers / products / logistics_orders / warehouses /
--       platform_integrations / financial_transactions / purchase_orders 及全部 *_import_jobs、*_field_mappings、shop_warehouses，
--       2026-10-21 新增 shops、audit_logs）。
--       2026-03-22 实测：42 张表中 23 张有 tenant_id，19 张没有（含租户主体表 tenants 自身）。
--       🔄 2026-10-21 实测：**45 张表中 25 张有 tenant_id，20 张没有**；有 tenant_id 的 25 张里 20 张建了以 tenant_id
--       打头的索引，缺索引的是 users / roles / warehouses / platform_integrations / financial_transactions 这 5 张
--       （见 data-dictionary.md R-06）。
--       仍无 tenant_id 的 ~~18~~ **19** 张（另有 tenants 自身也无此列）：order_items、skus、inventory_records、locations、logistics_channels、
--       logistics_fees、logistics_labels、payment_infos、shipping_addresses、purchase_order_items，
--       以及 6 张 *_import_job_errors（ISS-049 改名后已能建出来，但父表有列、子表无列的例外依旧，见 er-diagram.md §5.1）、
--       **system_configs**（全新表，平台级配置无租户列），
--       外加 user_roles、role_jpa_entity_permissions 两张 @ManyToMany 桥表（风险 R-07 未完全收敛，见 data-dictionary.md）
-- ---------------------------------------------------------------------------

CREATE TABLE tenants (
  id            VARCHAR(255) NOT NULL,          -- 由领域层赋值，无自增/无 UUID 生成策略
  name          VARCHAR(255) NOT NULL,
  description   VARCHAR(255),
  contact_email VARCHAR(255) NOT NULL,
  contact_phone VARCHAR(255),
  status        VARCHAR(255) NOT NULL,          -- ACTIVE / INACTIVE / SUSPENDED / DELETED（手写字符串）
  created_at    DATETIME(6)  NOT NULL,          -- 构造函数 now() 填充，更新时不会刷新
  updated_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenants_name (name),
  UNIQUE KEY uk_tenants_contact_email (contact_email)
) ENGINE = InnoDB;

CREATE TABLE users (
  id         VARCHAR(255) NOT NULL,
  tenant_id  VARCHAR(255) NOT NULL,
  username   VARCHAR(255) NOT NULL,
  password   VARCHAR(255) NOT NULL,             -- BCrypt 摘要（PasswordEncoder），列长未收紧
  email      VARCHAR(255) NOT NULL,
  phone      VARCHAR(255),
  first_name VARCHAR(255),
  last_name  VARCHAR(255),
  status     VARCHAR(255) NOT NULL,             -- ACTIVE（构造函数默认）；领域 UserStatus 未被持久层映射
  created_at DATETIME(6)  NOT NULL,
  updated_at DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),      -- 全局唯一，与 tenant_id 语义冲突（风险 R-07）
  UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB;

CREATE TABLE roles (
  id             VARCHAR(255) NOT NULL,
  tenant_id      VARCHAR(255) NOT NULL,
  name           VARCHAR(255) NOT NULL,
  description    VARCHAR(255),
  is_system_role BIT          NOT NULL,
  created_at     DATETIME(6)  NOT NULL,
  updated_at     DATETIME(6)  NOT NULL,
  PRIMARY KEY (id)
) ENGINE = InnoDB;

-- @ElementCollection Set<String> permissions，未标 @CollectionTable → 表名/列名走隐式命名
CREATE TABLE role_jpa_entity_permissions (
  role_jpa_entity_id VARCHAR(255) NOT NULL,
  permission         VARCHAR(255) NOT NULL,
  PRIMARY KEY (role_jpa_entity_id, permission),
  CONSTRAINT fk_role_perms_role FOREIGN KEY (role_jpa_entity_id) REFERENCES roles (id)
) ENGINE = InnoDB;

-- 用户-角色关联（UserJpaEntity @ElementCollection user_roles），支撑登录时构建角色/权限 authorities
CREATE TABLE user_roles (
  user_id VARCHAR(255) NOT NULL,
  role_id VARCHAR(255) NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- 支撑域：客户
-- ---------------------------------------------------------------------------

CREATE TABLE customers (
  id                VARCHAR(64)  NOT NULL,             -- 由应用层赋值，长度已收紧到 64（不再是 UUID 自动生成列）
  tenant_id         VARCHAR(64),                       -- 后加的租户列，历史行为 NULL
  name              VARCHAR(255) NOT NULL,
  email             VARCHAR(255),                      -- 实体未声明 nullable=false，列可空却带全局唯一约束（风险 R-07）
  phone             VARCHAR(64),
  address           VARCHAR(512),
  company           VARCHAR(256),
  source_platform   VARCHAR(32),                       -- 导入来源平台，FILE 表示文件导入
  external_buyer_id VARCHAR(64),                       -- 平台侧买家ID，导入幂等定位键
  status            VARCHAR(24)  NOT NULL,             -- @Enumerated(STRING): ACTIVE/INACTIVE/SUSPENDED/DELETED
  created_at        DATETIME(6),                       -- 无 @PrePersist 自动填充，必须由应用层赋值（风险 R-04 仍在）
  updated_at        DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_customers_email (email),               -- 全局唯一，与 tenant_id 语义冲突（风险 R-07）
  UNIQUE KEY uk_customers_external_buyer (tenant_id, source_platform, external_buyer_id),
  KEY idx_customers_tenant (tenant_id),
  KEY idx_customers_status (status)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- 核心域：订单
-- ---------------------------------------------------------------------------

-- ShippingAddress 值对象独立建表
CREATE TABLE shipping_addresses (
  id             VARCHAR(255) NOT NULL,
  recipient_name VARCHAR(255) NOT NULL,
  address_line1  VARCHAR(255) NOT NULL,
  address_line2  VARCHAR(255),
  city           VARCHAR(255) NOT NULL,
  state_province VARCHAR(255),
  postal_code    VARCHAR(255) NOT NULL,
  country        VARCHAR(255) NOT NULL,
  phone_number   VARCHAR(255) NOT NULL,
  PRIMARY KEY (id)
) ENGINE = InnoDB;

-- PaymentInfo 值对象独立建表
CREATE TABLE payment_infos (
  id             VARCHAR(255)   NOT NULL,
  payment_method VARCHAR(255)   NOT NULL,
  payment_status VARCHAR(255)   NOT NULL,
  transaction_id VARCHAR(128),
  amount         DECIMAL(19, 2),                -- BigDecimal 映射，precision=19 scale=2
  payment_date   BIGINT,
  PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE orders (
  id                  VARCHAR(255) NOT NULL,
  tenant_id           VARCHAR(64),               -- 后加的租户列，历史行为 NULL
  order_number        VARCHAR(64)  NOT NULL,
  customer_id         VARCHAR(64),               -- 裸字符串引用，无物理外键（风险 R-01/R-06）
  status              VARCHAR(24)  NOT NULL,     -- @Enumerated(STRING): PENDING/PROCESSING/CONFIRMED/SHIPPED/COMPLETED/CANCELLED
  shipping_address_id VARCHAR(255),
  payment_info_id     VARCHAR(255),
  customer_name       VARCHAR(128),
  customer_email      VARCHAR(128),
  total_amount        DECIMAL(19, 2),            -- BigDecimal + 显式 precision/scale（V7；原 DOUBLE，风险 R-09 已闭）
  platform            VARCHAR(32),               -- 平台名或 FILE（导入/同步来源）
  platform_order_id   VARCHAR(128),              -- 平台侧订单ID，导入幂等定位键
  created_at          BIGINT,                    -- Long epoch，与 DATETIME 并存（风险 R-10）
  updated_at          BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uk_orders_order_number (order_number),
  UNIQUE KEY uk_orders_platform_order (tenant_id, platform, platform_order_id),
  KEY idx_orders_tenant (tenant_id),
  KEY idx_orders_customer (customer_id),
  KEY idx_orders_status (status),
  KEY idx_orders_created_at (created_at),
  CONSTRAINT fk_orders_shipping_address FOREIGN KEY (shipping_address_id) REFERENCES shipping_addresses (id),
  CONSTRAINT fk_orders_payment_info     FOREIGN KEY (payment_info_id)     REFERENCES payment_infos (id)
) ENGINE = InnoDB;

CREATE TABLE order_items (
  id           VARCHAR(255) NOT NULL,
  order_id     VARCHAR(255) NOT NULL,
  product_name VARCHAR(255) NOT NULL,
  sku_code     VARCHAR(255) NOT NULL,            -- 指向 skus.sku_code，无外键
  quantity     INT          NOT NULL,
  unit_price   DECIMAL(19, 2) NOT NULL,          -- V7：原 DOUBLE
  total_price  DECIMAL(19, 2) NOT NULL,          -- V7：原 DOUBLE
  PRIMARY KEY (id),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)  -- cascade=ALL, orphanRemoval
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- 核心域：物流
-- ---------------------------------------------------------------------------

CREATE TABLE logistics_channels (
  id             VARCHAR(255) NOT NULL,
  channel_name   VARCHAR(128) NOT NULL,
  channel_code   VARCHAR(64)  NOT NULL,
  description    VARCHAR(512),
  countries      VARCHAR(512),                      -- 逗号分隔的国家清单，非关联表
  active         BIT          NOT NULL,
  base_price     DECIMAL(19, 2),                -- V7：原 DOUBLE
  price_per_gram DECIMAL(19, 4),                -- 每克费率不是金额，按 R-52 给 4 位（存量有 0.0050/0.0150）
  currency       VARCHAR(8),
  PRIMARY KEY (id),
  UNIQUE KEY uk_channel_name (channel_name),
  UNIQUE KEY uk_channel_code (channel_code),
  KEY idx_logistics_channels_active (active)
) ENGINE = InnoDB;

CREATE TABLE logistics_labels (
  id                VARCHAR(255) NOT NULL,
  tracking_number   VARCHAR(255),
  shipper_name      VARCHAR(255),
  shipper_address   VARCHAR(255),
  shipper_phone     VARCHAR(255),
  recipient_name    VARCHAR(255),
  recipient_address VARCHAR(255),
  recipient_phone   VARCHAR(255),                   -- 收件人姓名/地址/电话明文入库（风险 R-12 同类）
  package_weight    DOUBLE,
  package_quantity  INT,
  barcode           VARCHAR(255),
  label_data        TEXT,
  label_url         VARCHAR(255),
  label_format      VARCHAR(255),
  PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE logistics_fees (
  id       VARCHAR(255) NOT NULL,
  base_fee DECIMAL(19, 2),                      -- V7：原 DOUBLE
  weight_fee DECIMAL(19, 2),                    -- V7：原 DOUBLE
  total_fee  DECIMAL(19, 2)       NOT NULL,     -- V7：原 DOUBLE
  currency   VARCHAR(255) NOT NULL,
  PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE logistics_orders (
  id                     VARCHAR(255) NOT NULL,
  logistics_order_number VARCHAR(255) NOT NULL,
  tenant_id              VARCHAR(255),              -- 后加的租户列，历史行为 NULL
  order_id               VARCHAR(255) NOT NULL,     -- 裸字符串引用，无外键
  status                 VARCHAR(24)  NOT NULL,     -- PENDING/PICKED_UP/IN_TRANSIT/OUT_FOR_DELIVERY/DELIVERED/EXCEPTION/LABEL_GENERATED/CANCELLED
  logistics_channel_id   VARCHAR(255) NOT NULL,
  carrier                VARCHAR(255),              -- 承运商文本，与 logistics_channels 无关联
  logistics_label_id     VARCHAR(255),
  logistics_fee_id       VARCHAR(255),
  tracking_number        VARCHAR(255),
  tracking_url           VARCHAR(512),
  platform               VARCHAR(32),               -- 平台名或 FILE（导入/同步来源）
  external_logistics_id  VARCHAR(128),              -- 平台侧运单ID，导入幂等定位键
  tracking_traces        VARCHAR(4000),             -- JSON 数组轨迹明细，超长会被截断（风险 R-08）
  shipped_at             DATETIME(6),
  estimated_arrival_at   DATETIME(6),
  delivered_at           DATETIME(6),
  created_at             BIGINT,
  updated_at             BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uk_logistics_orders_number (logistics_order_number),
  KEY idx_logistics_orders_tenant (tenant_id),
  KEY idx_logistics_orders_order (order_id),
  KEY idx_logistics_orders_tracking (tracking_number),
  CONSTRAINT fk_lo_channel FOREIGN KEY (logistics_channel_id) REFERENCES logistics_channels (id),
  CONSTRAINT fk_lo_label   FOREIGN KEY (logistics_label_id)   REFERENCES logistics_labels (id),
  CONSTRAINT fk_lo_fee     FOREIGN KEY (logistics_fee_id)     REFERENCES logistics_fees (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- 支撑域：仓储
-- ---------------------------------------------------------------------------

CREATE TABLE warehouses (
  id             VARCHAR(255) NOT NULL,
  tenant_id      VARCHAR(255),                       -- 后加的租户列；旧数据为 NULL 时由 WarehouseMapper 回落 "default-tenant"
  warehouse_name VARCHAR(255) NOT NULL,
  warehouse_code VARCHAR(255) NOT NULL,
  address        VARCHAR(255),
  capacity       INT          NOT NULL,
  active         BIT          NOT NULL,
  created_at     DATETIME(6)  NOT NULL,
  updated_at     DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_warehouse_name (warehouse_name),
  UNIQUE KEY uk_warehouse_code (warehouse_code)
) ENGINE = InnoDB;

CREATE TABLE locations (
  id            VARCHAR(255) NOT NULL,
  warehouse_id  VARCHAR(255) NOT NULL,
  location_code VARCHAR(255) NOT NULL,
  location_type VARCHAR(255),
  capacity      INT,
  occupied      INT,
  active        BIT          NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_locations_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE = InnoDB;
-- 缺口：应补 UNIQUE (warehouse_id, location_code)（风险 R-05）

CREATE TABLE inventory_records (
  id           VARCHAR(255) NOT NULL,            -- 应用层赋值，无生成策略
  sku_code     VARCHAR(255) NOT NULL,            -- 指向 skus.sku_code，无外键
  product_id   VARCHAR(255),                     -- 冗余的商品ID，同为裸字符串引用（风险 R-01）
  warehouse_id VARCHAR(255) NOT NULL,
  location_id  VARCHAR(255),
  quantity     INT          NOT NULL,
  status       VARCHAR(255) NOT NULL,            -- NORMAL/SHORTAGE/EXCESS/OUT_OF_STOCK
  last_updated BIGINT,
  PRIMARY KEY (id),
  CONSTRAINT fk_inv_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_inv_location  FOREIGN KEY (location_id)  REFERENCES locations (id)
) ENGINE = InnoDB;
-- 缺口：findBySkuCodeAndWarehouseId 语义上要求唯一，但表上无 UNIQUE (sku_code, warehouse_id)（风险 R-05）

-- ---------------------------------------------------------------------------
-- 支撑域：产品
-- ---------------------------------------------------------------------------

CREATE TABLE products (
  id                VARCHAR(255) NOT NULL,
  tenant_id         VARCHAR(255),                    -- 后加的租户列，历史行为 NULL
  name              VARCHAR(255) NOT NULL,
  description       VARCHAR(2000),
  category_id       VARCHAR(255),                    -- 引用不存在的 categories 表（风险 R-01）
  category_name     VARCHAR(255),
  source_platform   VARCHAR(255),                    -- 导入来源平台，FILE 表示文件导入
  source_product_id VARCHAR(255),                    -- 平台侧商品ID，导入幂等定位键
  active            BIT          NOT NULL,           -- 构造函数默认 true
  created_at        DATETIME(6),                     -- 无 @PrePersist，由应用层赋值（风险 R-04）
  updated_at        DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_products_external_ref (tenant_id, source_platform, source_product_id),
  KEY idx_products_tenant (tenant_id),
  KEY idx_products_category (category_name),
  KEY idx_products_source (source_platform)
) ENGINE = InnoDB;

CREATE TABLE skus (
  id              VARCHAR(255) NOT NULL,
  product_id      VARCHAR(255) NOT NULL,
  sku_code        VARCHAR(255) NOT NULL,
  sku_name        VARCHAR(255) NOT NULL,
  price           DECIMAL(19, 2),                  -- 映射自 sellingPrice，列名未跟随字段名（风险 R-11）；V7：原 DOUBLE
  cost_price      DECIMAL(19, 2),                  -- V7：原 DOUBLE
  currency        VARCHAR(8),
  weight          DOUBLE,                            -- 映射自 weightInGrams，单位克
  volume_cc       INT,                               -- 体积，立方厘米
  attributes_json VARCHAR(4000),                     -- 替代旧的 dimensions 列；超 4000 字符会截断（风险 R-08）
  active          BIT          NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_skus_sku_code (sku_code),
  CONSTRAINT fk_skus_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB;
-- 说明：实体已不再映射 dimensions 列，ddl-auto=update 不会删除它，旧库可能残留该空列

-- ---------------------------------------------------------------------------
-- 支撑域：采购
-- ---------------------------------------------------------------------------

CREATE TABLE purchase_orders (
  purchase_order_id VARCHAR(255)   NOT NULL,      -- 主键列名与其余表不一致（风险 R-11）
  tenant_id         VARCHAR(64)    NULL,          -- 2026-01-25 补列（ISS-046）；可空，历史行仍为 NULL
  supplier_id       VARCHAR(255)   NOT NULL,      -- 无 suppliers 表（风险 R-01）
  supplier_name     VARCHAR(255)   NOT NULL,
  warehouse_id      VARCHAR(255)   NOT NULL,      -- 裸字符串引用，无外键
  status            VARCHAR(255)   NOT NULL,      -- 手写小写 code：pending/approved/rejected/in_transit/delivered/cancelled
  total_amount      DECIMAL(19, 2) NOT NULL,      -- V7.2 收敛：原本无显式 precision，按 Hibernate 默认建出 DECIMAL(38,2)
  created_at        DATETIME(6)    NOT NULL,
  updated_at        DATETIME(6)    NOT NULL,
  PRIMARY KEY (purchase_order_id)
) ENGINE = InnoDB;

CREATE TABLE purchase_order_items (
  id               BIGINT         NOT NULL AUTO_INCREMENT,   -- 全库唯一自增主键
  sku_code         VARCHAR(255)   NOT NULL,
  product_name     VARCHAR(255)   NOT NULL,
  quantity         INT            NOT NULL,
  unit_price       DECIMAL(19, 2) NOT NULL,      -- V7.2 收敛：原 38,2
  total_price      DECIMAL(19, 2) NOT NULL,      -- V7.2 收敛：原 38,2
  purchase_order_id VARCHAR(255)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_po_items_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (purchase_order_id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- 支撑域：财务
-- ---------------------------------------------------------------------------

CREATE TABLE financial_transactions (
  transaction_id VARCHAR(255)   NOT NULL,
  tenant_id      VARCHAR(255),                  -- 后加的租户列，历史行为 NULL
  type           VARCHAR(255)   NOT NULL,      -- 小写 code：income/expense/refund/adjustment
  amount         DECIMAL(19, 2) NOT NULL,      -- V7.2 收敛：原 38,2（无显式 precision 的默认宽度）
  currency       VARCHAR(3)     NOT NULL,      -- 唯一显式收紧长度的列
  reference_id   VARCHAR(255)   NOT NULL,      -- 多态引用（order/logistics/purchase），无外键
  reference_type VARCHAR(255)   NOT NULL,
  description    VARCHAR(255),
  status         VARCHAR(255)   NOT NULL,      -- 小写 code：pending/success/failed/cancelled
  created_at     DATETIME(6)    NOT NULL,
  updated_at     DATETIME(6)    NOT NULL,
  PRIMARY KEY (transaction_id)
) ENGINE = InnoDB;
-- 缺口：应补 UNIQUE (reference_id, reference_type, type) 或对账幂等键（风险 R-05）

-- ---------------------------------------------------------------------------
-- 通用域：平台集成
-- ---------------------------------------------------------------------------

CREATE TABLE platform_integrations (
  id                      BINARY(16)   NOT NULL,            -- ⚠️ 运行库实测 binary(16)（Hibernate 6 把 UUID 存成 16 字节原生二进制），既非 @Column 声明的 36 也不是字符列
  tenant_id               VARCHAR(64),                      -- 后加的租户列，历史行为 NULL
  platform_name           ENUM('ALIEXPRESS','AMAZON','EBAY','MAGENTO','MERCADO_LIBRE','OTHER','OZON','SHOPEE','SHOPIFY','TEMU','TIKTOK_SHOP','WISH') NOT NULL,  -- 🔄 2026-10-10 放宽到 12 值，与 PlatformName 对齐；见下方「enum 截断」
  auth_type               ENUM('ALIEXPRESS','AMAZON_SP_API','CUSTOM_SHOP','EBAY_SELL','MAGENTO','MERCADO_LIBRE','OZON','SHOPEE','SHOPIFY','TEMU','TIKTOK_SHOP') NULL,  -- 对接协议，外呼分派键；NULL 按 CUSTOM_SHOP 处理
  api_key                 VARCHAR(255) NOT NULL,            -- ~~明文~~ → AES-256-GCM 密文（`enc:v1:` 前缀），见下方 ⚠️ 宽度
  api_secret              VARCHAR(255) NOT NULL,            -- ~~明文~~ → 密文；Shopify 不使用该列，未配置时占位空串
  access_token            VARCHAR(1024),                    -- 访问令牌密文（Shopify `shpat_`）
  refresh_token           VARCHAR(1024),                    -- 刷新令牌密文（Amazon LWA / eBay OAuth）；刷新逻辑见下方
  token_expires_at        DATETIME(6),                      -- 令牌到期时间（非敏感，出参可见）
  api_version             VARCHAR(32),                      -- 平台接口版本（Shopify 形如 2026-01）；NULL 取全局配置
  store_url               VARCHAR(255),
  region                  VARCHAR(32),                      -- 平台区域，用于选 SP-API host（NA/EU/FE）；明文，非凭据
  external_shop_id        VARCHAR(128),                     -- 平台侧店铺/卖家标识（shop_id / user_id）；明文，非凭据
  marketplace_ids         VARCHAR(512),                     -- 逗号分隔的市场标识（Amazon `marketplaceIds` / eBay `marketplace_id`）
  status                  ENUM('ACTIVE','DISCONNECTED','ERROR','INACTIVE') NOT NULL,  -- DISCONNECTED 无写入路径
  last_products_synced_at DATETIME(6),                      -- 商品增量同步水位
  last_orders_synced_at   DATETIME(6),                      -- 订单增量同步水位
  created_at              DATETIME(6)  NOT NULL,
  updated_at              DATETIME(6)  NOT NULL,
  PRIMARY KEY (id)
) ENGINE = InnoDB;
-- ⚠️ region/external_shop_id/marketplace_ids 三列此前漏记（实体 :85/:91/:97 有、运行库有、本快照没有）。
--    auth_type/access_token/refresh_token/token_expires_at/api_version 同轮补记，共 8 列。
--    与快照其余部分同理：字节级真值以实际库 `mysqldump --no-data` 为准，本块是「可读镜像」。
-- ⚠️ **enum 截断（2026-04-29 实测）**：`platform_name` 在运行库是 ENUM，只有 ~~**7 个值**~~ 🔄 **2026-10-10 已放宽为 12 值**，而
--    `PlatformName` 枚举有 **12 个**——SHOPEE / OZON / TEMU / TIKTOK_SHOP / MERCADO_LIBRE 五家写不进去。
--    其中 SHOPEE/OZON/MERCADO_LIBRE 三家在 infrastructure/platform 下已有适配器类（见 spec/platform-integration.md §7.1）。
--    回滚式插探针复现：`INSERT ... platform_name='OZON'` → `ERROR 1265 (01000): Data truncated for column 'platform_name'`
--    （sql_mode 含 STRICT_TRANS_TABLES）。`auth_type` 的 ENUM 则是完整的 11 个值，两者不对称。
--    后果：这两家有适配器（Ozon/Mercado）的平台**建不出集成记录**，属结构性阻塞而非配置问题。
-- 🔄 **2026-10-10 复测：本库该阻塞已解除（`platform_name` 实测 12 值）**。因为 `ddl-auto=update` 不会改 ENUM
--    （下面那条 ⚠️ 就是原因），放宽只能显式执行；本项目不引入 Flyway（ISS-020），**所以下面这条语句就是
--    新环境的唯一复现途径**（按本轮 `information_schema` 实测列型逐值回写），照抄一次即可，
--    否则 `smoke.sh` 7.6 建 OZON 集成会回到 1265：
--    ```sql
--    ALTER TABLE platform_integrations MODIFY COLUMN platform_name ENUM(
--      'ALIEXPRESS','AMAZON','EBAY','MAGENTO','MERCADO_LIBRE','OTHER','OZON',
--      'SHOPEE','SHOPIFY','TEMU','TIKTOK_SHOP','WISH') NOT NULL;
--    ```
--    复测：`information_schema.COLUMNS` 的 COLUMN_TYPE 现为 12 值（与 `PlatformName` 12 值相等）、
--    `auth_type` 为 11 值（与 `PlatformAuthType` 11 值相等），两列对称；
--    `POST /api/platform-integrations` 用 `platformName=OZON` 实测 **200** 并能按 id 回读、探活与删除（`smoke.sh` 7.6/7.9）。
-- ⚠️ 列宽不是实体声明的结果：`platform_name`/`status`/`auth_type`/`id` 的 ENUM/BINARY(16) 形状由历史 DDL 决定，
--    `ddl-auto=update` 只加不减不改（ADR-0002），所以改实体不会修正运行库，必须显式迁移。
-- ⚠️ **密文比原文长**：`enc:v1:`(7) + Base64(12B IV + 明文 + 16B tag) ≈ 7 + 4/3×(明文长度+28)。
--    → `api_key`/`api_secret` 的 255 大约只能容纳 **180 字符左右**的原文，超过会插入失败或截断；
--      `access_token`/`refresh_token` 给到 1024 ≈ 容纳 **740 字符**原文。
--      Shopify `shpat_`(32) 与自建店铺 key 都够用，**eBay 的 user access token 可能超 1024**（接 eBay 前必须先量一次密文长度）。
-- ⚠️ 未注入 `PLATFORM_CREDENTIAL_MASTER_KEY` 时这些列存的是**明文**（`CredentialCipher` 打 WARN 后按明文读写）。
-- 令牌刷新：**有**自动刷新（非"预留"）——`infrastructure/platform/oauth/PlatformCredentials.java:57-87`
--   在每次外呼前解析凭据，`authType.oauthTokenBased()` 为真且临期（`refresh-skew` 默认 5m，:40/:98）时
--   经 `PlatformTokenExchange` 换发新令牌并 `repository.save` 回写（:78）；换发成功但落库失败只记 ERROR 不中断（:79-84）。
-- 缺口：无 `(tenant_id, platform_name, store_url)` 唯一约束（BR-03）；`tenant_id` 无索引、读路径不过滤（BR-11 / ISS-050）。
--   运行库实测本表**只有 PRIMARY 一个索引**（`information_schema.STATISTICS` 聚合确认）。


-- ---------------------------------------------------------------------------
-- 追加（财务模块缺陷修复 + 平台财务数据导入管线，ddl-auto=update 自动建列/建表，此处为文档镜像）
-- ---------------------------------------------------------------------------

-- financial_transactions 补列：聚合已有 tenantId 但此前缺列导致读回丢失
ALTER TABLE financial_transactions
  ADD COLUMN tenant_id VARCHAR(255);            -- 租户ID，历史行为 NULL，由导入/创建链路写入

-- 平台财务导入任务表（镜像 order_import_jobs）
CREATE TABLE finance_import_jobs (
  id                      VARCHAR(255)   NOT NULL,   -- UUID
  tenant_id               VARCHAR(255),
  platform                VARCHAR(32),               -- 平台名或 FILE（文件上传导入）
  integration_id          VARCHAR(64),
  file_name               VARCHAR(255),
  status                  VARCHAR(24)    NOT NULL,   -- PENDING/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED
  total_count             INT            NOT NULL,
  success_count           INT            NOT NULL,
  failure_count           INT            NOT NULL,
  skipped_count           INT            NOT NULL,
  message                 VARCHAR(1000),
  created_transaction_ids VARCHAR(4000),             -- JSON 数组
  updated_transaction_ids VARCHAR(4000),             -- JSON 数组
  started_at              DATETIME(6),
  finished_at             DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_finance_import_jobs_tenant (tenant_id)
) ENGINE = InnoDB;

-- 平台财务导入任务错误明细（行级异常）
CREATE TABLE finance_import_job_errors (
  id                       VARCHAR(255)   NOT NULL,  -- UUID
  job_id                   VARCHAR(255)   NOT NULL,
  line_number              INT,
  external_transaction_id  VARCHAR(128),
  field_name               VARCHAR(64),
  message                  VARCHAR(1000),
  PRIMARY KEY (id),
  CONSTRAINT fk_finance_import_errors_job FOREIGN KEY (job_id) REFERENCES finance_import_jobs (id)
) ENGINE = InnoDB;

-- 第三方平台财务字段映射（镜像 order_field_mappings）
CREATE TABLE finance_field_mappings (
  id            VARCHAR(255) NOT NULL,               -- UUID
  tenant_id     VARCHAR(255) NOT NULL,
  platform      VARCHAR(32)  NOT NULL,               -- 平台名或 FILE
  source_field  VARCHAR(128) NOT NULL,               -- 平台原始字段
  target_field  VARCHAR(64)  NOT NULL,               -- FinanceImportTargetField 枚举名
  transform     VARCHAR(32),                         -- FieldTransform 枚举名
  default_value VARCHAR(255),
  enabled       BIT          NOT NULL,
  created_at    DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_finance_field_mappings_ref (tenant_id, platform, source_field),
  KEY idx_finance_field_mappings_lookup (tenant_id, platform)
) ENGINE = InnoDB;

-- 财务导入落库幂等键约定（供参考，无新增列）：
-- financial_transactions.reference_type = 'PLATFORM_TRANSACTION'
-- financial_transactions.reference_id   = CONCAT(platform, ':', externalTransactionId)

-- ---------------------------------------------------------------------------
-- 追加（补齐 ISS-038 快照缺口）：order / customer / product / logistics / shop_warehouse
-- 五条导入管线的作业表、行级错误表与字段映射表，以及店铺仓库主数据表。
-- 结构与此前已收录的 finance_* 三张表同构，逐列取自对应 *JpaEntity 注解。
-- ---------------------------------------------------------------------------

-- 店铺仓库主数据（平台仓库 ↔ 本地仓库映射），ddl-auto=update 自动建表
CREATE TABLE shop_warehouses (
  id                      VARCHAR(255) NOT NULL,      -- UUID，应用层赋值
  tenant_id               VARCHAR(255) NOT NULL,
  platform                VARCHAR(32)  NOT NULL,      -- AMAZON/EBAY/... 或 FILE
  integration_id          VARCHAR(64),                -- 裸字符串引用 platform_integrations.id，无外键
  shop_code               VARCHAR(128),
  shop_name               VARCHAR(255),
  platform_warehouse_id   VARCHAR(128) NOT NULL,      -- 平台侧仓库标识
  platform_warehouse_name VARCHAR(255),
  local_warehouse_id      VARCHAR(64),                -- 裸字符串引用 warehouses.id，无外键（风险 R-01）
  local_warehouse_code    VARCHAR(64),
  address                 VARCHAR(500),
  default_warehouse       BIT          NOT NULL,      -- 每租户每平台的默认落库仓库
  active                  BIT          NOT NULL,
  last_synced_at          DATETIME(6),
  created_at              DATETIME(6)  NOT NULL,
  updated_at              DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_warehouse_platform_wh (tenant_id, platform, platform_warehouse_id),
  KEY idx_shop_warehouse_tenant (tenant_id),
  KEY idx_shop_warehouse_local_wh (local_warehouse_id)
) ENGINE = InnoDB;

-- ============================ 订单导入管线 ============================

CREATE TABLE order_import_jobs (
  id                VARCHAR(255) NOT NULL,            -- UUID
  tenant_id         VARCHAR(255),
  platform          VARCHAR(32),                      -- 平台名或 FILE（文件上传导入）
  integration_id    VARCHAR(64),
  file_name         VARCHAR(255),
  status            VARCHAR(24)  NOT NULL,            -- @Enumerated(STRING): PENDING/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED
  total_count       INT          NOT NULL,
  success_count     INT          NOT NULL,
  failure_count     INT          NOT NULL,
  skipped_count     INT          NOT NULL,
  message           VARCHAR(1000),
  created_order_ids VARCHAR(4000),                    -- JSON 数组
  updated_order_ids VARCHAR(4000),                    -- JSON 数组
  started_at        DATETIME(6),
  finished_at       DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_order_import_jobs_tenant (tenant_id)
) ENGINE = InnoDB;

CREATE TABLE order_import_job_errors (
  id                VARCHAR(255) NOT NULL,            -- UUID
  job_id            VARCHAR(255) NOT NULL,
  line_number       INT,
  external_order_id VARCHAR(128),
  order_number      VARCHAR(128),
  field_name        VARCHAR(64),                      -- 实体字段名为 field，列名显式指定为 field_name
  message           VARCHAR(1000),
  PRIMARY KEY (id),
  CONSTRAINT fk_order_import_errors_job FOREIGN KEY (job_id) REFERENCES order_import_jobs (id)
) ENGINE = InnoDB;

CREATE TABLE order_field_mappings (
  id            VARCHAR(255) NOT NULL,                -- UUID
  tenant_id     VARCHAR(255) NOT NULL,
  platform      VARCHAR(32)  NOT NULL,
  source_field  VARCHAR(128) NOT NULL,                -- 平台原始字段
  target_field  VARCHAR(64)  NOT NULL,                -- OrderImportTargetField 枚举名（EXTERNAL_ORDER_ID…ITEM_UNIT_PRICE，共 25 项）
  transform     VARCHAR(32),                          -- FieldTransform 枚举名：NONE/WEIGHT_KG_TO_G/BOOLEAN_FROM_YES_NO/DEFAULT_VALUE
  default_value VARCHAR(255),
  enabled       BIT          NOT NULL,
  created_at    DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_field_mappings_ref (tenant_id, platform, source_field),
  KEY idx_order_field_mappings_lookup (tenant_id, platform)
) ENGINE = InnoDB;

-- ============================ 客户导入管线 ============================

CREATE TABLE customer_import_jobs (
  id                   VARCHAR(255) NOT NULL,         -- UUID
  tenant_id            VARCHAR(255),
  platform             VARCHAR(32),
  integration_id       VARCHAR(64),
  file_name            VARCHAR(255),
  status               VARCHAR(24)  NOT NULL,         -- PENDING/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED
  total_count          INT          NOT NULL,
  success_count        INT          NOT NULL,
  failure_count        INT          NOT NULL,
  skipped_count        INT          NOT NULL,
  message              VARCHAR(1000),
  created_customer_ids VARCHAR(4000),                 -- JSON 数组
  updated_customer_ids VARCHAR(4000),                 -- JSON 数组
  started_at           DATETIME(6),
  finished_at          DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_customer_import_jobs_tenant (tenant_id)
) ENGINE = InnoDB;

CREATE TABLE customer_import_job_errors (
  id                VARCHAR(255) NOT NULL,            -- UUID
  job_id            VARCHAR(255) NOT NULL,
  line_number       INT,
  external_buyer_id VARCHAR(128),
  customer_name     VARCHAR(128),
  field_name        VARCHAR(64),
  message           VARCHAR(1000),
  PRIMARY KEY (id),
  CONSTRAINT fk_customer_import_errors_job FOREIGN KEY (job_id) REFERENCES customer_import_jobs (id)
) ENGINE = InnoDB;

CREATE TABLE customer_field_mappings (
  id            VARCHAR(255) NOT NULL,                -- UUID
  tenant_id     VARCHAR(255) NOT NULL,
  platform      VARCHAR(32)  NOT NULL,
  source_field  VARCHAR(128) NOT NULL,
  target_field  VARCHAR(64)  NOT NULL,                -- CustomerImportTargetField 枚举名（EXTERNAL_BUYER_ID…ADDRESS，共 9 项）
  transform     VARCHAR(32),
  default_value VARCHAR(255),
  enabled       BIT          NOT NULL,
  created_at    DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_field_mappings_ref (tenant_id, platform, source_field),
  KEY idx_customer_field_mappings_lookup (tenant_id, platform)
) ENGINE = InnoDB;

-- ============================ 商品导入管线 ============================
-- 注意：本管线是最早实现的一批，索引/唯一键名未带 product_ 前缀（与其余四套命名不一致，风险 R-11）

CREATE TABLE product_import_jobs (
  id                   VARCHAR(255) NOT NULL,         -- UUID
  tenant_id            VARCHAR(255),
  platform             VARCHAR(32),
  integration_id       VARCHAR(64),
  file_name            VARCHAR(255),
  status               VARCHAR(24)  NOT NULL,         -- PENDING/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED
  total_count          INT          NOT NULL,
  success_count        INT          NOT NULL,
  failure_count        INT          NOT NULL,
  skipped_count        INT          NOT NULL,
  message              VARCHAR(1000),
  created_product_ids  VARCHAR(4000),                 -- JSON 数组
  updated_product_ids  VARCHAR(4000),                 -- JSON 数组
  started_at           DATETIME(6),
  finished_at          DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_import_jobs_tenant (tenant_id)
) ENGINE = InnoDB;

CREATE TABLE product_import_job_errors (
  id                  VARCHAR(255) NOT NULL,          -- UUID
  job_id              VARCHAR(255) NOT NULL,
  line_number         INT,
  external_product_id VARCHAR(128),
  sku_code            VARCHAR(128),                   -- 指向 skus.sku_code，无外键
  field_name          VARCHAR(64),
  message             VARCHAR(1000),
  PRIMARY KEY (id),
  CONSTRAINT fk_product_import_errors_job FOREIGN KEY (job_id) REFERENCES product_import_jobs (id)
) ENGINE = InnoDB;

CREATE TABLE product_field_mappings (
  id            VARCHAR(255) NOT NULL,                -- UUID
  tenant_id     VARCHAR(255) NOT NULL,
  platform      VARCHAR(32)  NOT NULL,
  source_field  VARCHAR(128) NOT NULL,
  target_field  VARCHAR(64)  NOT NULL,                -- ImportTargetField 枚举名（PRODUCT_NAME…ACTIVE，共 14 项）
  transform     VARCHAR(32),                          -- 商品管线唯一使用 WEIGHT_KG_TO_G 的上下文
  default_value VARCHAR(255),
  enabled       BIT          NOT NULL,
  created_at    DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_field_mappings_ref (tenant_id, platform, source_field),
  KEY idx_field_mappings_lookup (tenant_id, platform)
) ENGINE = InnoDB;

-- ============================ 物流导入管线 ============================

CREATE TABLE logistics_import_jobs (
  id                             VARCHAR(255) NOT NULL,   -- UUID
  tenant_id                      VARCHAR(255),
  platform                       VARCHAR(32),
  integration_id                 VARCHAR(64),
  file_name                      VARCHAR(255),
  status                         VARCHAR(24)  NOT NULL,   -- PENDING/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED
  total_count                    INT          NOT NULL,
  success_count                  INT          NOT NULL,
  failure_count                  INT          NOT NULL,
  skipped_count                  INT          NOT NULL,
  message                        VARCHAR(1000),
  created_logistics_order_ids    VARCHAR(4000),           -- JSON 数组
  updated_logistics_order_ids    VARCHAR(4000),           -- JSON 数组，4000 上限会截断大批量作业的结果集（风险 R-08）
  started_at                     DATETIME(6),
  finished_at                    DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_logistics_import_jobs_tenant (tenant_id)
) ENGINE = InnoDB;

CREATE TABLE logistics_import_job_errors (
  id                   VARCHAR(255) NOT NULL,         -- UUID
  job_id               VARCHAR(255) NOT NULL,
  line_number          INT,
  external_logistics_id VARCHAR(128),
  order_number         VARCHAR(128),
  field_name           VARCHAR(64),
  message              VARCHAR(1000),
  PRIMARY KEY (id),
  CONSTRAINT fk_logistics_import_errors_job FOREIGN KEY (job_id) REFERENCES logistics_import_jobs (id)
) ENGINE = InnoDB;

CREATE TABLE logistics_field_mappings (
  id            VARCHAR(255) NOT NULL,                -- UUID
  tenant_id     VARCHAR(255) NOT NULL,
  platform      VARCHAR(32)  NOT NULL,
  source_field  VARCHAR(128) NOT NULL,
  target_field  VARCHAR(64)  NOT NULL,                -- LogisticsImportTargetField 枚举名（EXTERNAL_LOGISTICS_ID…TRACE_LOCATION，共 27 项；TRACES 为嵌套明细）
  transform     VARCHAR(32),
  default_value VARCHAR(255),
  enabled       BIT          NOT NULL,
  created_at    DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_logistics_field_mappings_ref (tenant_id, platform, source_field),
  KEY idx_logistics_field_mappings_lookup (tenant_id, platform)
) ENGINE = InnoDB;

-- ============================ 店铺仓库导入管线 ============================

CREATE TABLE shop_warehouse_import_jobs (
  id                          VARCHAR(255) NOT NULL,  -- UUID
  tenant_id                   VARCHAR(255),
  platform                    VARCHAR(32),
  integration_id              VARCHAR(64),
  file_name                   VARCHAR(255),
  status                      VARCHAR(24)  NOT NULL,  -- PENDING/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED
  total_count                 INT          NOT NULL,
  success_count               INT          NOT NULL,
  failure_count               INT          NOT NULL,
  skipped_count               INT          NOT NULL,
  message                     VARCHAR(1000),
  created_shop_warehouse_ids  VARCHAR(4000),          -- JSON 数组
  updated_shop_warehouse_ids  VARCHAR(4000),          -- JSON 数组
  started_at                  DATETIME(6),
  finished_at                 DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_shop_wh_import_jobs_tenant (tenant_id)
) ENGINE = InnoDB;

CREATE TABLE shop_warehouse_import_job_errors (
  id                    VARCHAR(255) NOT NULL,        -- UUID
  job_id                VARCHAR(255) NOT NULL,
  line_number           INT,
  shop_code             VARCHAR(128),
  platform_warehouse_id VARCHAR(128),
  field_name            VARCHAR(64),
  message               VARCHAR(1000),
  PRIMARY KEY (id),
  CONSTRAINT fk_shop_wh_import_errors_job FOREIGN KEY (job_id) REFERENCES shop_warehouse_import_jobs (id)
) ENGINE = InnoDB;

CREATE TABLE shop_warehouse_field_mappings (
  id            VARCHAR(255) NOT NULL,                -- UUID
  tenant_id     VARCHAR(255) NOT NULL,
  platform      VARCHAR(32)  NOT NULL,
  source_field  VARCHAR(128) NOT NULL,
  target_field  VARCHAR(64)  NOT NULL,                -- ShopWarehouseImportTargetField 枚举名（SHOP_CODE…ACTIVE，共 9 项）
  transform     VARCHAR(32),
  default_value VARCHAR(255),
  enabled       BIT          NOT NULL,
  created_at    DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_wh_field_mappings_ref (tenant_id, platform, source_field),
  KEY idx_shop_wh_field_mappings_lookup (tenant_id, platform)
) ENGINE = InnoDB;

-- 导入作业与错误明细的关联由实体侧 @OneToMany(mappedBy="job", cascade=ALL, orphanRemoval=true) 声明，
-- 上面的 job_id 外键即 Hibernate 运行时会建的约束（约束名与 Hibernate 默认生成名不同，勿据此比对）。

-- ---------------------------------------------------------------------------
-- 🔄 2026-10-21 补登：原先漏登的 3 张表。逐列取自运行时库 `SHOW CREATE TABLE`
--    （schema ecommerce_logistics_erp，information_schema 实测共 45 张表）。
--    这三张表在 Hibernate ddl-auto=update 下早已建出，只是快照未收录，
--    因此 data-dictionary.md 的 42 行清单、er-diagram.md 的实体清单同样缺它们。
-- ---------------------------------------------------------------------------

-- 店铺（平台店铺 = 平台对接元信息 + 凭据 + 店铺标识），ADR-0013 的站点粒度切片
CREATE TABLE shops (
  id                      VARCHAR(36)  NOT NULL,      -- UUID.toString()（36 位带连字符），非 BINARY(16)
  tenant_id               VARCHAR(64)  NOT NULL,      -- 注意宽度 64：与多数表的 VARCHAR(255) / audit_logs 的 VARCHAR(36) 不一致（R-14）
  integration_id          VARCHAR(36),                -- 裸字符串引用 platform_integrations.id，无外键；
                                                      -- 而后者实为 BINARY(16) → 无法按裸值 join（R-14）
  platform                VARCHAR(32)  NOT NULL,      -- 手写字符串，非原生 ENUM（与 platform_integrations 口径不同）
  shop_code               VARCHAR(128) NOT NULL,
  shop_name               VARCHAR(128) NOT NULL,
  external_shop_id        VARCHAR(128),
  marketplace_id          VARCHAR(64),
  region                  VARCHAR(32),
  store_url               VARCHAR(255),
  api_version             VARCHAR(32),
  auth_status             VARCHAR(24)  NOT NULL,      -- 手写字符串状态
  push_back_policy        VARCHAR(16)  NOT NULL,      -- 手写字符串（如 MANUAL/AUTO），无枚举约束
  api_key                 VARCHAR(512),               -- CredentialCipher 密文（enc:v1:…），非明文
  api_secret              VARCHAR(512),               -- 同上
  access_token            VARCHAR(2048),              -- 同上
  refresh_token           VARCHAR(2048),              -- 同上
  token_expires_at        DATETIME(6),
  last_orders_synced_at   DATETIME(6),
  last_products_synced_at DATETIME(6),
  active                  BIT          NOT NULL,
  created_at              DATETIME(6)  NOT NULL,
  updated_at              DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shops_tenant_code (tenant_id, shop_code),
  UNIQUE KEY uk_shops_marketplace (tenant_id, platform, marketplace_id),
  KEY idx_shops_tenant (tenant_id),
  KEY idx_shops_integration (integration_id)
) ENGINE = InnoDB;

-- 审计日志（append-only，无 updated_at、无软删除；当前实库 8181 行）
CREATE TABLE audit_logs (
  id            VARCHAR(36) NOT NULL,
  tenant_id     VARCHAR(36),                          -- 可空：平台级操作无租户
  action        VARCHAR(64) NOT NULL,
  action_label  VARCHAR(128),
  actor_id      VARCHAR(36),
  actor_name    VARCHAR(64),
  target_type   VARCHAR(32),
  target_id     VARCHAR(64),
  result        ENUM('FAILURE','SUCCESS') NOT NULL,   -- 原生 ENUM（@Enumerated(STRING)）
  detail        VARCHAR(512),
  error_message VARCHAR(512),
  ip            VARCHAR(64),
  trace_id      VARCHAR(32),
  created_at    DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_logs_tenant_created (tenant_id, created_at),
  KEY idx_audit_logs_actor_result_created (actor_name, result, created_at)
) ENGINE = InnoDB;

-- 系统配置（平台级键值表，无 tenant_id；PK 列名不是 id，而是 config_key）
CREATE TABLE system_configs (
  config_key   VARCHAR(64) NOT NULL,                  -- 业务键即主键（R-11 的非 id 主键之一）
  config_group VARCHAR(32) NOT NULL,
  config_value VARCHAR(512),
  updated_at   DATETIME(6) NOT NULL,
  updated_by   VARCHAR(64),
  PRIMARY KEY (config_key)
) ENGINE = InnoDB;


