/**
 * 物流模块常量：物流状态、渠道、导入字段与批量发货约定。
 * 前后端保持同一套取值约定（后端枚举见 LogisticsStatus / LogisticsImportTargetField / ImportJobStatus）。
 */

export {
  DEFAULT_TENANT_ID,
  FILE_PLATFORM,
  MANUAL_PLATFORM,
  platformLabel,
  platformOptions,
  importJobStatusMap,
  transformLabelMap,
  formatAmount
} from './order';

/** 物流状态（对应后端 LogisticsStatus 枚举名，中文取 LogisticsStatus#description） */
export const logisticsStatusMap = {
  PENDING: { text: '待处理', color: 'default' },
  COLLECTED: { text: '已揽收', color: 'processing' },
  LABEL_GENERATED: { text: '已打单', color: 'cyan' },
  IN_TRANSIT: { text: '运输中', color: 'orange' },
  DELIVERING: { text: '派送中', color: 'gold' },
  DELIVERED: { text: '已送达', color: 'success' },
  FAILED: { text: '配送失败', color: 'error' },
  CANCELLED: { text: '已取消', color: '#8c8c8c' }
};

export const logisticsStatusOptions = Object.entries(logisticsStatusMap).map(([value, meta]) => ({
  value,
  label: meta.text
}));

export const logisticsStatusLabel = (code) => {
  const matched = logisticsStatusMap[code];
  return matched ? matched.text : code || '-';
};

/** 终态：后端导入链路对这些状态不再回退更新 */
export const logisticsFinalStatuses = ['DELIVERED', 'CANCELLED'];

/** 发货（markShipped）可达状态：与后端 LogisticsOrder#markShipped 语义一致 */
export const logisticsShippableStatuses = ['PENDING', 'LABEL_GENERATED', 'COLLECTED'];

/** 物流导入标准目标字段中文说明（后端 /logistics/import/target-fields 返回权威列表，此处静态兜底） */
export const logisticsTargetFieldLabelMap = {
  EXTERNAL_LOGISTICS_ID: '平台物流单号',
  TRACKING_NUMBER: '运单号',
  ORDER_NUMBER: '系统订单号',
  EXTERNAL_ORDER_ID: '平台订单号',
  CHANNEL_CODE: '物流渠道编码',
  CARRIER: '承运商',
  STATUS: '物流状态',
  TRACKING_URL: '物流跟踪链接',
  LABEL_URL: '面单下载地址',
  LABEL_FORMAT: '面单格式',
  BARCODE: '条形码',
  SHIPPED_AT: '发货时间',
  ESTIMATED_ARRIVAL_AT: '预计送达时间',
  DELIVERED_AT: '签收时间',
  TOTAL_FEE: '物流费用',
  CURRENCY: '币种',
  RECIPIENT: '收件人',
  PHONE: '收件人电话',
  COUNTRY: '国家',
  PROVINCE: '省份',
  CITY: '城市',
  STREET: '详细地址',
  ZIP_CODE: '邮编',
  TRACES: '物流轨迹',
  TRACE_TIME: '单条轨迹时间',
  TRACE_STATUS: '单条轨迹状态',
  TRACE_DESCRIPTION: '单条轨迹描述',
  TRACE_LOCATION: '单条轨迹地点'
};

/** 导入预览行/错误行展示字段 */
export const logisticsImportRowColumns = ['externalLogisticsId', 'trackingNumber', 'channelCode', 'carrier', 'status'];

/** 后端时间格式统一为 yyyy-MM-dd HH:mm:ss，直接展示即可，无法解析时回退原值 */
export const formatDateTime = (value) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes()
  )}:${pad(date.getSeconds())}`;
};
