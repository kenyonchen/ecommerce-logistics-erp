import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  Typography,
  Descriptions,
  Table,
  Button,
  Card,
  Space,
  Tag,
  Modal,
  Select,
  Spin,
  Alert,
  Popconfirm,
  message
} from 'antd';
import { ArrowLeftOutlined, SyncOutlined, DeleteOutlined } from '@ant-design/icons';
import { orderApi, platformIntegrationApi } from '../../services/api';
import {
  DEFAULT_TENANT_ID,
  orderStatusMap,
  orderStatusOptions,
  orderStatusFlow,
  platformLabel,
  formatAmount
} from '../../constants/order';

const { Title, Paragraph } = Typography;
const { Option } = Select;

// 付款方式/付款状态中文映射（后端为字符串字段，取值与 CreateOrder 表单选项及导入默认值对齐；
// constants/order.js 暂无此映射，故在本页定义，未识别取值回退原始码）（ISS-081①）
const paymentMethodLabelMap = {
  CREDIT_CARD: '信用卡',
  PAYPAL: 'PayPal',
  BANK_TRANSFER: '银行转账'
};

const paymentStatusLabelMap = {
  PENDING: '待支付',
  PAID: '已支付',
  FAILED: '支付失败',
  REFUNDED: '已退款'
};

const OrderDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [statusModalVisible, setStatusModalVisible] = useState(false);
  const [selectedStatus, setSelectedStatus] = useState(undefined);
  const [submitting, setSubmitting] = useState(false);
  const [syncing, setSyncing] = useState(false);

  // 获取订单详情
  const fetchOrderDetail = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await orderApi.getOrderById(id);
      setOrder(data);
      // 选项仅来自 allowedNext（候选流转状态），当前状态不在其中；
      // 若选中当前状态，antd 会回显原始枚举码（如 PROCESSING），故保持未选（ISS-080）
      setSelectedStatus(undefined);
    } catch (err) {
      setError(err?.message || '获取订单详情失败');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchOrderDetail();
  }, [fetchOrderDetail]);

  // 处理订单状态变更
  const handleStatusChange = async () => {
    if (!selectedStatus) {
      message.warning('请选择要变更的目标状态');
      return;
    }
    setSubmitting(true);
    try {
      const updated = await orderApi.updateOrderStatus(id, selectedStatus);
      setOrder(updated);
      setSelectedStatus(undefined);
      message.success('订单状态更新成功');
      setStatusModalVisible(false);
    } catch (err) {
      console.error('更新订单状态失败:', err);
    } finally {
      setSubmitting(false);
    }
  };

  // 处理订单取消
  const handleCancelOrder = async () => {
    try {
      const updated = await orderApi.cancelOrder(id);
      setOrder(updated);
      message.success('订单取消成功');
    } catch (err) {
      console.error('取消订单失败:', err);
    }
  };

  // 处理订单删除
  const handleDelete = async () => {
    try {
      await orderApi.deleteOrder(id);
      message.success('订单删除成功');
      navigate('/orders');
    } catch (err) {
      console.error('删除订单失败:', err);
    }
  };

  // 从来源平台重新拉取该订单（幂等覆盖更新）
  const handleSyncOrder = async () => {
    if (!order?.platform || !order?.platformOrderId) {
      message.info('该订单非平台导入订单，无需同步');
      return;
    }
    setSyncing(true);
    try {
      const integrations = await platformIntegrationApi.getIntegrations();
      const integration = (integrations || []).find(
        (item) => item.platformName === order.platform && item.status === 'ACTIVE'
      );
      if (!integration) {
        message.warning(`未找到 ${platformLabel(order.platform)} 的已启用集成配置，无法同步`);
        return;
      }
      const result = await platformIntegrationApi.syncOrders(integration.id, {
        tenantId: order.tenantId || DEFAULT_TENANT_ID,
        orderNumber: order.platformOrderId,
        limit: 1
      });
      message.success(
        `同步完成：成功 ${result?.successCount ?? 0} 条，失败 ${result?.failureCount ?? 0} 条，跳过 ${result?.skippedCount ?? 0} 条`
      );
      fetchOrderDetail();
    } catch (err) {
      console.error('同步订单失败:', err);
    } finally {
      setSyncing(false);
    }
  };

  // 订单行项表格列
  const columns = [
    {
      title: '产品名称',
      dataIndex: 'productName',
      key: 'productName'
    },
    {
      title: 'SKU',
      dataIndex: 'skuCode',
      key: 'skuCode',
      width: 140
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 80
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      width: 110,
      render: (text) => formatAmount(text)
    },
    {
      title: '小计',
      dataIndex: 'totalPrice',
      key: 'totalPrice',
      width: 110,
      render: (text) => formatAmount(text)
    }
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 60 }}>
        <Spin size="large" />
        <div style={{ marginTop: 8 }}>加载中...</div>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div>
        <Alert type="error" title={error || '订单不存在'} showIcon />
        <div style={{ marginTop: 16 }}>
          <Link to="/orders">返回订单列表</Link>
        </div>
      </div>
    );
  }

  const statusMeta = orderStatusMap[order.status] || { text: order.status, color: 'default' };
  const allowedNext = order.allowedNextStatuses || orderStatusFlow[order.status] || [];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Title level={4}>
          <Space>
            <Button type="text" icon={<ArrowLeftOutlined />}>
              <Link to="/orders">返回列表</Link>
            </Button>
            订单详情
          </Space>
        </Title>
        <Space>
          <Button icon={<SyncOutlined />} loading={syncing} onClick={handleSyncOrder}>
            同步订单
          </Button>
          <Popconfirm
            title="确认删除该订单？"
            description="删除后无法恢复。"
            okText="确定"
            cancelText="取消"
            onConfirm={handleDelete}
          >
            <Button danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      </div>

      {/* 订单基本信息 */}
      <Card style={{ marginBottom: 5 }}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="订单编号" span={1}>
            {order.orderNumber || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="订单状态" span={1}>
            <Tag color={statusMeta.color}>{statusMeta.text}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="客户ID" span={1}>
            {order.customerId || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="客户名称" span={1}>
            {order.customerName || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="客户邮箱" span={1}>
            {order.customerEmail || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="订单金额" span={1}>
            <strong>{formatAmount(order.totalAmount)}</strong>
          </Descriptions.Item>
          <Descriptions.Item label="来源平台" span={1}>
            {platformLabel(order.platform)}
          </Descriptions.Item>
          <Descriptions.Item label="平台订单号" span={1}>
            {order.platformOrderId || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间" span={1}>
            {order.createdAt ? new Date(order.createdAt).toLocaleString() : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="更新时间" span={1}>
            {order.updatedAt ? new Date(order.updatedAt).toLocaleString() : '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 收货地址 */}
      <Card title="收货地址" style={{ marginBottom: 5 }}>
        {order.shippingAddress ? (
          <Descriptions column={1} size="small">
            <Descriptions.Item label="收件人">{order.shippingAddress.recipient}</Descriptions.Item>
            <Descriptions.Item label="联系电话">{order.shippingAddress.phone}</Descriptions.Item>
            <Descriptions.Item label="地址">
              {`${order.shippingAddress.country || ''} ${order.shippingAddress.province || ''} ${
                order.shippingAddress.city || ''
              } ${order.shippingAddress.street || ''} ${order.shippingAddress.zipCode || ''}`}
            </Descriptions.Item>
          </Descriptions>
        ) : (
          <span>暂无收货地址</span>
        )}
      </Card>

      {/* 付款信息 */}
      <Card title="付款信息" style={{ marginBottom: 5 }}>
        {order.paymentInfo ? (
          <Descriptions column={1} size="small">
            <Descriptions.Item label="付款方式">
              {paymentMethodLabelMap[order.paymentInfo.paymentMethod] || order.paymentInfo.paymentMethod || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="付款状态">
              <Tag color={order.paymentInfo.paymentStatus === 'PAID' ? 'success' : 'processing'}>
                {paymentStatusLabelMap[order.paymentInfo.paymentStatus] || order.paymentInfo.paymentStatus || '-'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="交易ID">{order.paymentInfo.transactionId}</Descriptions.Item>
            <Descriptions.Item label="付款金额">{formatAmount(order.paymentInfo.amount)}</Descriptions.Item>
          </Descriptions>
        ) : (
          <span>暂无付款信息</span>
        )}
      </Card>

      {/* 订单商品 */}
      <Card title="订单商品" style={{ marginBottom: 5 }}>
        <Table
          columns={columns}
          dataSource={order.orderItems || []}
          rowKey={(record, index) => record.skuCode || index}
          pagination={false}
          scroll={{ x: 600 }}
        />
        <div style={{ textAlign: 'right', marginTop: 16 }}>
          <Paragraph>
            <strong>订单总价：{formatAmount(order.totalAmount)}</strong>
          </Paragraph>
        </div>
      </Card>

      {/* 操作按钮 */}
      <div style={{ textAlign: 'right', marginTop: 24 }}>
        <Space>
          {order.status !== 'CANCELLED' && order.status !== 'COMPLETED' && (
            <>
              <Button type="primary" disabled={allowedNext.length === 0} onClick={() => setStatusModalVisible(true)}>
                更新状态
              </Button>
              <Popconfirm
                title="确认取消订单？"
                description="取消后将无法恢复。"
                okText="确定"
                cancelText="取消"
                onConfirm={handleCancelOrder}
              >
                <Button danger>取消订单</Button>
              </Popconfirm>
            </>
          )}
        </Space>
      </div>

      {/* 状态更新弹窗 */}
      <Modal
        title="更新订单状态"
        open={statusModalVisible}
        onOk={handleStatusChange}
        onCancel={() => setStatusModalVisible(false)}
        okText="确定"
        cancelText="取消"
        confirmLoading={submitting}
      >
        <div style={{ marginBottom: 12 }}>
          <p>
            当前状态：<Tag color={statusMeta.color}>{statusMeta.text}</Tag>
          </p>
        </div>
        <div>
          <p>选择新状态：</p>
          <Select
            style={{ width: '100%' }}
            value={selectedStatus}
            onChange={setSelectedStatus}
            placeholder="请选择订单状态"
          >
            {/* 当前状态以中文标签展示为禁用项：既可视化来源状态，又避免回显裸枚举码（ISS-080） */}
            <Option key={`current-${order.status}`} value={order.status} disabled>
              {statusMeta.text}（当前状态）
            </Option>
            {allowedNext
              .filter((status) => status !== order.status)
              .map((status) => (
                <Option key={status} value={status}>
                  {orderStatusMap[status]?.text || orderStatusOptions.find((o) => o.value === status)?.label || status}
                </Option>
              ))}
          </Select>
        </div>
      </Modal>
    </div>
  );
};

export default OrderDetail;
