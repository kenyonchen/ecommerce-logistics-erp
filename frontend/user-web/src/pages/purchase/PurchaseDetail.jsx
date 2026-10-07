import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Button,
  Space,
  Card,
  Typography,
  Descriptions,
  Tag,
  message,
  Table,
  Modal,
  Input,
  InputNumber,
  Select,
  Form,
  Popconfirm
} from 'antd';
import { ArrowLeftOutlined, PlusOutlined, CheckOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { purchaseApi } from "../../services/api";
import {
  formatAmount,
  purchaseActionMap,
  purchaseApprovalOptions,
  purchaseItemEditableStatus,
  purchaseStatusFlow,
  purchaseStatusMap,
  purchaseStatusLabel
} from '../../constants/purchase';

const { Title } = Typography;
const { Option } = Select;

const closedItemModal = { open: false, mode: 'add', itemId: null };

const PurchaseDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [purchase, setPurchase] = useState(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [approvalModalVisible, setApprovalModalVisible] = useState(false);
  const [itemModal, setItemModal] = useState(closedItemModal);
  const [approvalStatus, setApprovalStatus] = useState('APPROVED');
  const [approvalForm] = Form.useForm();
  const [itemForm] = Form.useForm();

  const fetchPurchaseDetail = useCallback(async () => {
    setLoading(true);
    try {
      const data = await purchaseApi.getPurchaseOrderById(id);
      setPurchase(data || null);
    } catch (error) {
      console.error('获取采购详情失败:', error);
      setPurchase(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchPurchaseDetail();
  }, [fetchPurchaseDetail]);

  const handleBack = () => {
    navigate('/purchase');
  };

  // 统一提交：后端业务失败以 400/404 + 具体原因返回，拦截器已弹出提示，这里只需要不再刷新
  const runRequest = async (request, successText) => {
    setSubmitting(true);
    try {
      await request();
      message.success(successText);
      await fetchPurchaseDetail();
      return true;
    } catch (error) {
      console.error('采购单操作失败:', error);
      return false;
    } finally {
      setSubmitting(false);
    }
  };

  // 动作按钮由服务端下发的 allowedNextStatuses 决定，字典缺失时回退到本地状态机
  const allowedNextStatuses = purchase?.allowedNextStatuses?.length
    ? purchase.allowedNextStatuses
    : (purchaseStatusFlow[purchase?.status] || []);
  const canApprove = allowedNextStatuses.includes('APPROVED') || allowedNextStatuses.includes('REJECTED');

  const handleAction = (target) => {
    const meta = purchaseActionMap[target];
    return runRequest(() => purchaseApi[meta.api](id), meta.success);
  };

  // 审批确认：APPROVED→approve 接口，REJECTED→reject 接口（拒绝必须填原因）
  const handleApprovalConfirm = async () => {
    const values = await approvalForm.validateFields();
    const meta = purchaseActionMap[approvalStatus];
    const payload = { approvedBy: values.approvedBy || undefined, comment: values.comment || undefined };
    const ok = await runRequest(
      () => purchaseApi[meta.api](id, payload),
      meta.success
    );
    if (ok) {
      setApprovalModalVisible(false);
      approvalForm.resetFields();
    }
  };

  const openApprovalModal = () => {
    setApprovalStatus('APPROVED');
    approvalForm.resetFields();
    setApprovalModalVisible(true);
  };

  const openAddItemModal = () => {
    itemForm.resetFields();
    itemForm.setFieldsValue({ quantity: 1, unitPrice: 0.01 });
    setItemModal({ ...closedItemModal, open: true });
  };

  const openEditItemModal = (record) => {
    itemForm.resetFields();
    itemForm.setFieldsValue({
      skuCode: record.skuCode,
      productName: record.productName,
      quantity: record.quantity,
      unitPrice: record.unitPrice
    });
    setItemModal({ open: true, mode: 'edit', itemId: record.id });
  };

  // 采购项：新增提交完整字段，修改只提交数量与单价（SKU 与品名由服务端沿用原行）
  const handleItemSubmit = async () => {
    const values = await itemForm.validateFields();
    if (itemModal.mode === 'edit') {
      const ok = await runRequest(
        () => purchaseApi.updatePurchaseOrderItem(id, itemModal.itemId, {
          quantity: values.quantity,
          unitPrice: values.unitPrice
        }),
        '采购项已更新'
      );
      if (ok) closeItemModal();
      return;
    }
    const ok = await runRequest(
      () => purchaseApi.addPurchaseOrderItem(id, {
        skuCode: values.skuCode,
        productName: values.productName,
        quantity: values.quantity,
        unitPrice: values.unitPrice
      }),
      '采购项添加成功'
    );
    if (ok) closeItemModal();
  };

  const closeItemModal = () => {
    setItemModal(closedItemModal);
    itemForm.resetFields();
  };

  const handleRemoveItem = (itemId) =>
    runRequest(() => purchaseApi.removePurchaseOrderItem(id, itemId), '采购项已删除');

  const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!purchase) {
    return (
      <div>
        <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
        <div style={{ marginTop: 16 }}>采购记录不存在</div>
      </div>
    );
  }

  // 明细行主键由服务端生成（添加后回读带出），改删都以它为准
  const items = (purchase.items || []).map((item, index) => ({
    ...item,
    rowKey: item.id || `${item.skuCode}-${index}`
  }));
  const status = purchase.status;
  const canEditItems = status === purchaseItemEditableStatus;

  // 采购物品列配置
  const columns = [
    {
      title: 'SKU编码',
      dataIndex: 'skuCode',
      key: 'skuCode',
    },
    {
      title: '产品名称',
      dataIndex: 'productName',
      key: 'productName',
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      render: (text) => formatAmount(text),
    },
    {
      title: '总价',
      dataIndex: 'totalPrice',
      key: 'totalPrice',
      render: (text) => formatAmount(text),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, record) => {
        if (!canEditItems) return '-';
        if (!record.id) return <span style={{ color: '#999' }}>保存后才能修改</span>;
        return (
          <Space size="small">
            <Button type="link" icon={<EditOutlined />} onClick={() => openEditItemModal(record)}>编辑</Button>
            <Popconfirm
              title="确认删除该采购项？"
              description="删除后总金额会按剩余明细重算。"
              okText="确定"
              cancelText="取消"
              onConfirm={() => handleRemoveItem(record.id)}
            >
              <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  const statusInfo = purchaseStatusMap[status] || { color: 'default' };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>采购详情</Title>
        </div>
        <Space wrap>
          {canEditItems && (
            <Button icon={<PlusOutlined />} onClick={openAddItemModal}>添加采购项</Button>
          )}
          {canApprove && (
            <Button type="primary" icon={<CheckOutlined />} loading={submitting} onClick={openApprovalModal}>采购审批</Button>
          )}
          {['IN_TRANSIT', 'DELIVERED', 'CANCELLED'].filter(target => allowedNextStatuses.includes(target)).map(target => {
            const meta = purchaseActionMap[target];
            if (meta.confirm) {
              return (
                <Popconfirm
                  key={target}
                  title="操作确认"
                  description={meta.confirm}
                  okText="确定"
                  cancelText="取消"
                  onConfirm={() => handleAction(target)}
                >
                  <Button type={meta.danger ? 'default' : 'primary'} danger={meta.danger} loading={submitting}>
                    {meta.label}
                  </Button>
                </Popconfirm>
              );
            }
            return (
              <Button key={target} type="primary" loading={submitting} onClick={() => handleAction(target)}>
                {meta.label}
              </Button>
            );
          })}
        </Space>
      </div>

      {/* 采购基本信息 */}
      <Card style={{ marginBottom: 5 }}>
        <Descriptions title="基本信息" column={2}>
          <Descriptions.Item label="采购单号">{purchase.purchaseOrderId}</Descriptions.Item>
          <Descriptions.Item label="租户ID">{purchase.tenantId || '-'}</Descriptions.Item>
          <Descriptions.Item label="供应商ID">{purchase.supplierId}</Descriptions.Item>
          <Descriptions.Item label="供应商名称">{purchase.supplierName}</Descriptions.Item>
          <Descriptions.Item label="收货仓库ID">{purchase.warehouseId}</Descriptions.Item>
          <Descriptions.Item label="采购状态">
            <Tag color={statusInfo.color}>{purchase.statusLabel || purchaseStatusLabel(status)}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="总金额">{formatAmount(purchase.totalAmount)}</Descriptions.Item>
          <Descriptions.Item label="审批人">{purchase.approvedBy || '-'}</Descriptions.Item>
          <Descriptions.Item label="审批时间">{formatDate(purchase.approvedAt)}</Descriptions.Item>
          <Descriptions.Item label="审批意见">{purchase.approvalComment || '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{formatDate(purchase.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{formatDate(purchase.updatedAt)}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 采购物品列表 */}
      <Card style={{ marginBottom: 5 }}>
        <Title level={5}>采购物品</Title>
        <Table
          dataSource={items}
          columns={columns}
          rowKey="rowKey"
          pagination={false}
        />
      </Card>

      {/* 采购审批弹窗 */}
      <Modal
        title="采购审批"
        open={approvalModalVisible}
        confirmLoading={submitting}
        onOk={handleApprovalConfirm}
        onCancel={() => setApprovalModalVisible(false)}
        okText="确定"
        cancelText="取消"
        forceRender
      >
        <div style={{ marginBottom: 5 }}>
          <p>采购单号：{purchase.purchaseOrderId}</p>
          <p>供应商：{purchase.supplierName}</p>
          <p>总金额：{formatAmount(purchase.totalAmount)}</p>
        </div>
        <Form form={approvalForm} layout="vertical">
          <Form.Item label="审批结果" required>
            <Select value={approvalStatus} onChange={setApprovalStatus} placeholder="请选择审批结果">
              {purchaseApprovalOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item label="审批人" name="approvedBy" extra="留空则记当前登录账号">
            <Input placeholder="请输入审批人姓名" maxLength={64} />
          </Form.Item>
          <Form.Item
            label="审批意见"
            name="comment"
            rules={approvalStatus === 'REJECTED' ? [{ required: true, message: '拒绝采购单必须填写原因' }] : []}
          >
            <Input.TextArea rows={3} placeholder={approvalStatus === 'REJECTED' ? '请填写拒绝原因' : '请填写审批意见'} maxLength={512} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 采购项弹窗（新增 / 修改） */}
      <Modal
        title={itemModal.mode === 'edit' ? '修改采购项' : '添加采购项'}
        open={itemModal.open}
        confirmLoading={submitting}
        onOk={handleItemSubmit}
        onCancel={closeItemModal}
        okText={itemModal.mode === 'edit' ? '保存' : '添加'}
        cancelText="取消"
        forceRender
      >
        <Form form={itemForm} layout="vertical" initialValues={{ quantity: 1, unitPrice: 0.01 }}>
          <Form.Item label="SKU编码" name="skuCode" rules={[{ required: true, message: '请输入SKU编码' }]}>
            <Input placeholder="请输入SKU编码" disabled={itemModal.mode === 'edit'} />
          </Form.Item>
          <Form.Item label="产品名称" name="productName" rules={[{ required: true, message: '请输入产品名称' }]}>
            <Input placeholder="请输入产品名称" disabled={itemModal.mode === 'edit'} />
          </Form.Item>
          <Form.Item label="数量" name="quantity" rules={[{ required: true, message: '请输入数量' }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="单价" name="unitPrice" rules={[{ required: true, message: '请输入单价' }]}>
            <InputNumber min={0.01} step={0.01} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default PurchaseDetail;
