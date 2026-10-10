/**
 * 商品模块常量：状态、分类、第三方平台与字段映射目标字段。
 * 前后端保持同一套取值约定（后端枚举见 ImportTargetField / PlatformName）。
 */

// 当前租户ID：优先取登录会话中保存的租户，未登录时回退到种子租户
export const DEFAULT_TENANT_ID = localStorage.getItem('tenantId') || 'default-tenant';

/** 商品启用状态：后端以布尔字段 active 表达 */
export const PRODUCT_STATUS = {
  ACTIVE: true,
  INACTIVE: false
};

export const productStatusOptions = [
  { value: true, label: '启用' },
  { value: false, label: '停用' }
];

export const getProductStatusTag = (active) => (
  active ? { color: 'success', text: '启用' } : { color: 'default', text: '停用' }
);

/** 商品分类（与后端 categoryName 自由文本对应） */
export const productCategoryOptions = [
  { value: 'ELECTRONICS', label: '电子产品' },
  { value: 'CLOTHING', label: '服装' },
  { value: 'HOME', label: '家居用品' },
  { value: 'BOOKS', label: '图书' },
  { value: 'BEAUTY', label: '美妆' },
  { value: 'SPORTS', label: '运动用品' },
  { value: 'FOOD', label: '食品' },
  { value: 'OTHER', label: '其他' }
];

export const categoryLabel = (code) => {
  const matched = productCategoryOptions.find((item) => item.value === code);
  return matched ? matched.label : code || '-';
};

/** 第三方电商平台：清单只在 constants/order.js 里定义一份（与后端 PlatformName 逐值对齐），
 *  这里原样转出。此前两处各自维护同一份 7 项列表，是 ISS-086「后端能收、界面选不到」的成因。 */
import { platformOptions } from './order';

export { platformOptions };

/** 文件上传导入使用的虚拟平台标识 */
export const FILE_PLATFORM = 'FILE';

export const platformLabel = (code) => {
  if (!code) return '-';
  if (code === FILE_PLATFORM) return '文件上传';
  const matched = platformOptions.find((item) => item.value === code);
  return matched ? matched.label : code;
};

/**
 * 对接协议（对应后端 PlatformAuthType，11 值）：决定用哪套适配器外呼，与「平台」是两回事。
 * 未识别的取值一律按 CUSTOM_SHOP 处理，与后端默认值一致（PlatformAuthType#parse 的 fallback）。
 */
export const authTypeOptions = [
  { value: 'CUSTOM_SHOP', label: '通用自建店铺（API Key/Secret）' },
  { value: 'SHOPIFY', label: 'Shopify Admin API（访问令牌）' },
  { value: 'MAGENTO', label: 'Magento 2 REST Admin API（Bearer 集成令牌）' },
  { value: 'AMAZON_SP_API', label: 'Amazon SP-API（LWA 刷新令牌，分区域 host）' },
  { value: 'EBAY_SELL', label: 'eBay Sell APIs（OAuth2 + Bearer）' },
  { value: 'TEMU', label: 'Temu 开放平台（公共参数 + app_secret 签名）' },
  { value: 'TIKTOK_SHOP', label: 'TikTok Shop 开放平台（OAuth2 + 请求签名）' },
  { value: 'ALIEXPRESS', label: '速卖通开放平台（method 式接口 + 签名 + session）' },
  { value: 'SHOPEE', label: 'Shopee 开放平台（OAuth2 + 请求签名，partner/shop 双身份）' },
  { value: 'MERCADO_LIBRE', label: 'Mercado Libre（OAuth2 用户授权 + Bearer）' },
  { value: 'OZON', label: 'Ozon（静态凭据 Client-Id + Api-Key）' }
];

export const authTypeLabel = (code) => {
  const matched = authTypeOptions.find((item) => item.value === (code || 'CUSTOM_SHOP'));
  return matched ? matched.label : code;
};

/** 导入任务状态（对应后端 ImportJobStatus） */
export const IMPORT_JOB_STATUS = {
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
  SUCCESS: 'SUCCESS',
  PARTIAL_SUCCESS: 'PARTIAL_SUCCESS',
  FAILED: 'FAILED'
};

export const importJobStatusMap = {
  PENDING: { text: '待执行', color: 'default' },
  RUNNING: { text: '执行中', color: 'processing' },
  SUCCESS: { text: '全部成功', color: 'success' },
  PARTIAL_SUCCESS: { text: '部分成功', color: 'warning' },
  FAILED: { text: '执行失败', color: 'error' }
};

/** 标准目标字段中文说明（后端 /import/target-fields 会返回权威列表，此处用于静态兜底） */
export const targetFieldLabelMap = {
  PRODUCT_NAME: '商品名称',
  PRODUCT_DESCRIPTION: '商品描述',
  CATEGORY_NAME: '分类名称',
  SKU_CODE: 'SKU编码',
  SKU_NAME: 'SKU名称',
  SELLING_PRICE: '销售价',
  COST_PRICE: '成本价',
  CURRENCY: '币种',
  WEIGHT_IN_GRAMS: '重量(克)',
  VOLUME_IN_CC: '体积(立方厘米)',
  ATTRIBUTES: '变体属性',
  EXTERNAL_PRODUCT_ID: '平台商品ID',
  EXTERNAL_SKU_ID: '平台SKU ID',
  ACTIVE: '是否启用'
};

/** 字段转换规则（后端 FieldTransform） */
export const transformLabelMap = {
  NONE: '不转换',
  WEIGHT_KG_TO_G: '千克→克',
  BOOLEAN_FROM_YES_NO: '是/否→布尔',
  DEFAULT_VALUE: '缺失值填充默认值'
};

/** 默认币种 */
export const DEFAULT_CURRENCY = 'CNY';

/** 重量单位换算：页面以千克录入，系统以克存储 */
export const kgToGram = (kg) => (kg === undefined || kg === null || kg === '' ? null : Math.round(Number(kg) * 1000));
export const gramToKg = (gram) => (gram === undefined || gram === null || gram === '' ? null : Number(gram) / 1000);

/** SKU属性数组 -> 展示文本 */
export const formatAttributes = (attributes) => {
  if (!attributes || attributes.length === 0) return '-';
  return attributes.map((attr) => `${attr.name}:${attr.value}`).join('，');
};

/** SKU属性文本 -> 数组（支持 中文冒号/英文冒号，逗号、分号分隔） */
export const parseAttributes = (text) => {
  if (!text) return [];
  return String(text)
    .split(/[,，;；]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => {
      const idx = item.indexOf(':') >= 0 ? item.indexOf(':') : item.indexOf('：');
      if (idx > 0) {
        return { name: item.slice(0, idx).trim(), value: item.slice(idx + 1).trim() };
      }
      return { name: item, value: item };
    });
};
