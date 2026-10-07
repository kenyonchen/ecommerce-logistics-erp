/**
 * 客户（买家）模块常量：状态映射与平台导入相关取值。
 * 前后端保持同一套约定（后端枚举见 CustomerStatus / CustomerImportTargetField）。
 */

/** 客户状态（对应后端 CustomerStatus） */
export const customerStatusMap = {
  ACTIVE: { text: '活跃', color: 'success' },
  INACTIVE: { text: '非活跃', color: 'default' },
  SUSPENDED: { text: '已暂停', color: 'warning' },
  DELETED: { text: '已删除', color: 'error' }
};

export const customerStatusOptions = Object.entries(customerStatusMap).map(([value, item]) => ({
  value,
  label: item.text
}));

/** 客户导入数据来源 */
export const CUSTOMER_SOURCE_TYPES = [
  { value: 'CUSTOMERS', label: '平台买家数据' },
  { value: 'ORDERS', label: '平台订单（抽取买家）' }
];

/** 客户导入标准目标字段中文说明（后端 /customers/import/target-fields 返回权威列表，此处兜底） */
export const customerTargetFieldLabelMap = {
  EXTERNAL_BUYER_ID: '平台买家ID',
  CUSTOMER_NAME: '客户名称',
  CUSTOMER_EMAIL: '客户邮箱',
  CUSTOMER_PHONE: '客户电话',
  COMPANY: '公司',
  COUNTRY: '国家',
  PROVINCE: '省/州',
  CITY: '城市',
  ADDRESS: '详细地址'
};
