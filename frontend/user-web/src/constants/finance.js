/**
 * 财务模块常量：交易类型、交易状态、币种与导入任务字段说明。
 * 前后端保持同一套取值约定（后端枚举见 FinancialTransactionType / FinancialTransactionStatus）。
 */

// 当前租户ID：优先取登录会话中保存的租户，未登录时回退到种子租户
export { DEFAULT_TENANT_ID, FILE_PLATFORM, platformLabel, platformOptions, importJobStatusMap } from './order';

/** 交易类型（对应后端 FinancialTransactionType 枚举名） */
export const transactionTypeOptions = [
  { value: 'INCOME', label: '收入' },
  { value: 'EXPENSE', label: '支出' },
  { value: 'REFUND', label: '退款' },
  { value: 'ADJUSTMENT', label: '调整' }
];

export const transactionTypeMap = {
  INCOME: { text: '收入', color: 'green' },
  EXPENSE: { text: '支出', color: 'orange' },
  REFUND: { text: '退款', color: 'red' },
  ADJUSTMENT: { text: '调整', color: 'blue' }
};

export const transactionTypeLabel = (code) => {
  const matched = transactionTypeMap[code];
  return matched ? matched.text : code || '-';
};

/** 交易状态（对应后端 FinancialTransactionStatus 枚举名） */
export const transactionStatusOptions = [
  { value: 'PENDING', label: '待处理' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELLED', label: '已取消' }
];

export const transactionStatusMap = {
  PENDING: { text: '待处理', color: 'processing' },
  SUCCESS: { text: '成功', color: 'success' },
  FAILED: { text: '失败', color: 'error' },
  CANCELLED: { text: '已取消', color: 'default' }
};

export const transactionStatusLabel = (code) => {
  const matched = transactionStatusMap[code];
  return matched ? matched.text : code || '-';
};

/** 币种选项 */
export const currencyOptions = [
  { value: 'CNY', label: 'CNY（人民币）' },
  { value: 'USD', label: 'USD（美元）' },
  { value: 'EUR', label: 'EUR（欧元）' },
  { value: 'GBP', label: 'GBP（英镑）' },
  { value: 'JPY', label: 'JPY（日元）' }
];

/** 财务导入标准目标字段中文说明（后端 /finance/import/target-fields 返回权威列表，此处静态兜底，键为后端 TargetFieldDto.code 枚举名） */
export const financeTargetFieldLabelMap = {
  EXTERNAL_TRANSACTION_ID: '平台流水号（幂等键）',
  TYPE: '交易类型',
  AMOUNT: '交易金额',
  CURRENCY: '币种',
  REFERENCE_ID: '关联单据号',
  REFERENCE_TYPE: '关联单据类型',
  DESCRIPTION: '交易描述',
  STATUS: '交易状态',
  OCCURRED_AT: '发生时间'
};

/** 金额格式化 */
export const formatAmount = (value, currency) => {
  if (value === null || value === undefined || value === '') return '-';
  const prefix = !currency || currency === 'CNY' ? '¥' : `${currency} `;
  return `${prefix}${Number(value).toFixed(2)}`;
};
