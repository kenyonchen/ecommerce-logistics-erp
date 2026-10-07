/**
 * 订单模块常量：订单状态、状态机、来源平台与导入任务状态。
 * 前后端保持同一套取值约定（后端枚举见 OrderStatus / PlatformName / ImportJobStatus）。
 */

// 当前租户ID：优先取登录会话中保存的租户，未登录时回退到种子租户
export const DEFAULT_TENANT_ID = localStorage.getItem('tenantId') || 'default-tenant';

/** 订单状态（对应后端 OrderStatus 枚举名） */
export const ORDER_STATUS = {
  PENDING: 'PENDING',
  PROCESSING: 'PROCESSING',
  CON_FIRMED: 'CON_FIRMED',
  SHIPPED: 'SHIPPED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED'
};

export const orderStatusOptions = [
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'CON_FIRMED', label: '已确认' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
];

export const orderStatusMap = {
  PENDING: { text: '待处理', color: 'blue' },
  PROCESSING: { text: '处理中', color: 'processing' },
  CON_FIRMED: { text: '已确认', color: 'cyan' },
  SHIPPED: { text: '已发货', color: 'green' },
  COMPLETED: { text: '已完成', color: 'success' },
  CANCELLED: { text: '已取消', color: 'error' }
};

export const orderStatusLabel = (code) => {
  const matched = orderStatusMap[code];
  return matched ? matched.text : code || '-';
};

/**
 * 订单状态机：与后端 OrderStatus#allowedNextStatuses 保持一致。
 * 终态（COMPLETED / CANCELLED）不允许继续流转。
 */
export const orderStatusFlow = {
  PENDING: ['PROCESSING', 'CON_FIRMED', 'CANCELLED'],
  PROCESSING: ['CON_FIRMED', 'SHIPPED', 'CANCELLED'],
  CON_FIRMED: ['PROCESSING', 'SHIPPED', 'CANCELLED'],
  SHIPPED: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: []
};

/** 来源平台（MANUAL=系统内手工创建，FILE=文件导入） */
export const platformOptions = [
  { value: 'AMAZON', label: 'Amazon' },
  { value: 'EBAY', label: 'eBay' },
  { value: 'SHOPIFY', label: 'Shopify' },
  { value: 'ALIEXPRESS', label: 'AliExpress' },
  { value: 'WISH', label: 'Wish' },
  { value: 'MAGENTO', label: 'Magento' },
  { value: 'OTHER', label: '其他平台' }
];

export const FILE_PLATFORM = 'FILE';
export const MANUAL_PLATFORM = 'MANUAL';

export const platformLabel = (code) => {
  if (!code) return '-';
  if (code === FILE_PLATFORM) return '文件上传';
  if (code === MANUAL_PLATFORM) return '手工创建';
  const matched = platformOptions.find((item) => item.value === code);
  return matched ? matched.label : code;
};

/** 导入任务状态（对应后端 ImportJobStatus） */
export const importJobStatusMap = {
  PENDING: { text: '待执行', color: 'default' },
  RUNNING: { text: '执行中', color: 'processing' },
  SUCCESS: { text: '全部成功', color: 'success' },
  PARTIAL_SUCCESS: { text: '部分成功', color: 'warning' },
  FAILED: { text: '执行失败', color: 'error' }
};

/** 订单导入标准目标字段中文说明（后端 /orders/import/target-fields 返回权威列表，此处静态兜底） */
export const orderTargetFieldLabelMap = {
  EXTERNAL_ORDER_ID: '平台订单号',
  ORDER_NUMBER: '系统订单号',
  CUSTOMER_ID: '客户ID',
  CUSTOMER_NAME: '客户名称',
  CUSTOMER_EMAIL: '客户邮箱',
  ORDER_STATUS: '订单状态',
  CURRENCY: '币种',
  ORDER_TIME: '下单时间',
  PAYMENT_METHOD: '支付方式',
  PAYMENT_STATUS: '支付状态',
  TRANSACTION_ID: '交易号',
  TOTAL_AMOUNT: '订单总金额',
  SHIPPING_RECIPIENT: '收件人',
  SHIPPING_PHONE: '联系电话',
  SHIPPING_COUNTRY: '国家',
  SHIPPING_PROVINCE: '省/州',
  SHIPPING_CITY: '城市',
  SHIPPING_STREET: '详细地址',
  SHIPPING_ZIPCODE: '邮编',
  ITEMS: '订单明细',
  ITEM_SKU: 'SKU编码',
  ITEM_NAME: '商品名称',
  ITEM_QUANTITY: '数量',
  ITEM_UNIT_PRICE: '单价'
};

/** 字段转换规则（后端 FieldTransform） */
export const transformLabelMap = {
  NONE: '不转换',
  WEIGHT_KG_TO_G: '千克→克',
  BOOLEAN_FROM_YES_NO: '是/否→布尔',
  DEFAULT_VALUE: '缺失值填充默认值'
};

/** 金额格式化 */
export const formatAmount = (value) => {
  if (value === null || value === undefined || value === '') return '-';
  return `¥${Number(value).toFixed(2)}`;
};
