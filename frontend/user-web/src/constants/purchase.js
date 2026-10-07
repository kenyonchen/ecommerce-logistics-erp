/**
 * 采购模块常量：采购状态、状态机与审批动作。
 * 前后端保持同一套取值约定（后端枚举见 PurchaseStatus，明细动作门禁由聚合的 requireEditable 判定，
 * 可流转状态由 PurchaseOrderDto.allowedNextStatuses 下发；这里的 flow 只作字典不可用时的兜底）。
 */

export { DEFAULT_TENANT_ID, formatAmount } from './order';

/** 采购状态（对应后端 PurchaseStatus 枚举名，中文取 PurchaseStatus#description） */
export const purchaseStatusMap = {
  PENDING: { text: '待处理', color: 'blue' },
  APPROVED: { text: '已批准', color: 'green' },
  REJECTED: { text: '已拒绝', color: 'red' },
  IN_TRANSIT: { text: '运输中', color: 'orange' },
  DELIVERED: { text: '已送达', color: 'purple' },
  CANCELLED: { text: '已取消', color: 'default' }
};

export const purchaseStatusOptions = Object.entries(purchaseStatusMap).map(([value, meta]) => ({
  value,
  label: meta.text
}));

export const purchaseStatusLabel = (code) => {
  const matched = purchaseStatusMap[code];
  return matched ? matched.text : code || '-';
};

/** 采购状态机（与后端 PurchaseStatus#allowedNextStatuses 一致，终态无后继） */
export const purchaseStatusFlow = {
  PENDING: ['APPROVED', 'REJECTED', 'CANCELLED'],
  APPROVED: ['IN_TRANSIT', 'CANCELLED'],
  IN_TRANSIT: ['DELIVERED'],
  DELIVERED: [],
  REJECTED: [],
  CANCELLED: []
};

/**
 * 目标状态 → 动作按钮配置。
 * APPROVED / REJECTED 共用同一个审批弹窗（approval=true），审批人默认取登录账号；
 * DELIVERED 会在同一事务里产生入库记录与采购应付流水，必须二次确认。
 */
export const purchaseActionMap = {
  APPROVED: { label: '批准', api: 'approvePurchaseOrder', approval: true, success: '采购单已批准' },
  REJECTED: { label: '拒绝', api: 'rejectPurchaseOrder', approval: true, success: '采购单已拒绝' },
  IN_TRANSIT: { label: '标记运输中', api: 'markPurchaseOrderAsInTransit', success: '已标记为运输中' },
  DELIVERED: {
    label: '确认送达',
    api: 'markPurchaseOrderAsDelivered',
    success: '已确认送达，收货入库与采购应付已登记',
    confirm: '确认送达将按明细行增加收货仓库库存，并生成一笔采购应付流水；该动作不可撤销。是否继续？'
  },
  CANCELLED: {
    label: '取消采购单',
    api: 'cancelPurchaseOrder',
    danger: true,
    success: '采购单已取消',
    confirm: '取消后采购单不可恢复，需要重新创建。是否继续？'
  }
};

/** 审批弹窗的审批结果选项 */
export const purchaseApprovalOptions = [
  { value: 'APPROVED', label: '通过' },
  { value: 'REJECTED', label: '拒绝' }
];

/** 明细行只在这个状态下可维护（与后端 PurchaseOrder#requireEditable 一致） */
export const purchaseItemEditableStatus = 'PENDING';
