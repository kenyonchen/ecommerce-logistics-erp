import React, { useState, useEffect, useCallback } from 'react';
import {
  Typography,
  Card,
  Row,
  Col,
  Statistic,
  Progress,
  List,
  Tag,
  Space,
  Spin,
  Alert,
  Button,
  Empty
} from 'antd';
import {
  ShoppingCartOutlined,
  TruckOutlined,
  ProductOutlined,
  ContainerOutlined,
  UserOutlined,
  DollarOutlined,
  ShoppingOutlined,
  LinkOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import { overviewApi } from '../../services/api';

const { Title, Paragraph } = Typography;

const INITIAL_STATS = {
  totalOrders: 0,
  pendingOrders: 0,
  shippedOrders: 0,
  completedOrders: 0,
  totalProducts: 0,
  totalWarehouses: 0,
  totalCustomers: 0,
  totalRevenue: 0,
  totalPurchases: 0,
  totalPlatformIntegrations: 0
};

const Home = () => {
  const [stats, setStats] = useState(INITIAL_STATS);
  const [recentOrders, setRecentOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 订单状态映射，需与后端 OrderStatus 枚举保持一致（含拼写异常的 CON_FIRMED）
  const statusMap = {
    PENDING: { text: '待处理', color: 'blue', icon: <ClockCircleOutlined /> },
    PROCESSING: { text: '处理中', color: 'processing', icon: <ClockCircleOutlined /> },
    CON_FIRMED: { text: '已确认', color: 'cyan', icon: <ClockCircleOutlined /> },
    SHIPPED: { text: '已发货', color: 'green', icon: <CheckCircleOutlined /> },
    COMPLETED: { text: '已完成', color: 'success', icon: <CheckCircleOutlined /> },
    CANCELLED: { text: '已取消', color: 'error', icon: <CloseCircleOutlined /> }
  };

  // 获取统计数据（tenantId 省略，由后端按登录租户解析）
  const fetchStats = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await overviewApi.getSystemOverview({ recentOrderLimit: 5 });
      setStats({
        totalOrders: data.totalOrders ?? 0,
        pendingOrders: data.pendingOrders ?? 0,
        shippedOrders: data.shippedOrders ?? 0,
        completedOrders: data.completedOrders ?? 0,
        totalProducts: data.totalProducts ?? 0,
        totalWarehouses: data.totalWarehouses ?? 0,
        totalCustomers: data.totalCustomers ?? 0,
        totalRevenue: Number(data.totalRevenue ?? 0),
        totalPurchases: data.totalPurchases ?? 0,
        totalPlatformIntegrations: data.totalPlatformIntegrations ?? 0
      });
      setRecentOrders(data.recentOrders ?? []);
    } catch (e) {
      setError(e?.message || '加载系统概览失败');
    } finally {
      setLoading(false);
    }
  }, []);

  // 初始化加载数据
  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  // 百分比统一保留一位小数：文字与进度条内嵌文字共用同一数值（ISS-081②）
  const roundRate = (rate) => Math.round(rate * 10) / 10;

  // 计算订单完成率
  const orderCompletionRate = roundRate(
    stats.totalOrders > 0 ? (stats.completedOrders / stats.totalOrders) * 100 : 0
  );

  // 计算订单发货率
  const orderShippingRate = roundRate(
    stats.totalOrders > 0 ? ((stats.shippedOrders + stats.completedOrders) / stats.totalOrders) * 100 : 0
  );

  const formatDateTime = (value) => {
    if (!value) return '-';
    const normalized = String(value).replace('T', ' ');
    return normalized.length >= 16 ? normalized.slice(0, 16) : normalized;
  };

  const formatAmount = (value) =>
    `¥${Number(value ?? 0).toLocaleString('zh-CN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })}`;

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>系统概览</Title>
        <Button
          icon={<ReloadOutlined />}
          onClick={fetchStats}
          loading={loading}
        >
          刷新
        </Button>
      </Row>
      <Paragraph>欢迎使用跨境大卖ERP系统，这里展示了系统的关键指标和最新动态。</Paragraph>

      {error && (
        <Alert
          type="error"
          showIcon
          title="系统概览数据加载失败"
          description={error}
          style={{ marginBottom: 16 }}
          action={<Button size="small" onClick={fetchStats}>重试</Button>}
        />
      )}

      <Spin spinning={loading} tip="加载中...">
        {/* 统计卡片 */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card>
              <Statistic
                title="总订单数"
                value={stats.totalOrders}
                prefix={<ShoppingCartOutlined />}
                suffix="单"
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card>
              <Statistic
                title="待处理订单"
                value={stats.pendingOrders}
                prefix={<ClockCircleOutlined />}
                suffix="单"
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card>
              <Statistic
                title="已发货订单"
                value={stats.shippedOrders}
                prefix={<TruckOutlined />}
                suffix="单"
                valueStyle={{ color: '#52c41a' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card>
              <Statistic
                title="已完成订单"
                value={stats.completedOrders}
                prefix={<CheckCircleOutlined />}
                suffix="单"
                valueStyle={{ color: '#722ed1' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card>
              <Statistic
                title="总产品数"
                value={stats.totalProducts}
                prefix={<ProductOutlined />}
                suffix="个"
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card>
              <Statistic
                title="总仓库数"
                value={stats.totalWarehouses}
                prefix={<ContainerOutlined />}
                suffix="个"
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card>
              <Statistic
                title="总客户数"
                value={stats.totalCustomers}
                prefix={<UserOutlined />}
                suffix="个"
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card>
              <Statistic
                title="总收入"
                value={stats.totalRevenue}
                precision={2}
                prefix={<DollarOutlined />}
                suffix="元"
                valueStyle={{ color: '#faad14' }}
              />
            </Card>
          </Col>
        </Row>

        {/* 进度和最近订单 */}
        <Row gutter={[16, 16]}>
          {/* 左侧：进度条 */}
          <Col xs={24} md={8}>
            <Card title="订单处理进度" style={{ marginBottom: 5 }}>
              <div style={{ marginBottom: 5 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span>订单完成率</span>
                  <span>{orderCompletionRate.toFixed(1)}%</span>
                </div>
                <Progress percent={orderCompletionRate} status="active" />
              </div>
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span>订单发货率</span>
                  <span>{orderShippingRate.toFixed(1)}%</span>
                </div>
                <Progress percent={orderShippingRate} status="active" />
              </div>
            </Card>

            <Card title="系统模块">
              <List
                size="small"
                dataSource={[
                  { title: '订单管理', icon: <ShoppingCartOutlined />, count: stats.totalOrders },
                  { title: '物流管理', icon: <TruckOutlined />, count: stats.shippedOrders },
                  { title: '产品管理', icon: <ProductOutlined />, count: stats.totalProducts },
                  { title: '仓储管理', icon: <ContainerOutlined />, count: stats.totalWarehouses },
                  { title: '客户管理', icon: <UserOutlined />, count: stats.totalCustomers },
                  { title: '财务管理', icon: <DollarOutlined />, count: formatAmount(stats.totalRevenue) },
                  { title: '采购管理', icon: <ShoppingOutlined />, count: stats.totalPurchases },
                  { title: '平台集成', icon: <LinkOutlined />, count: stats.totalPlatformIntegrations }
                ]}
                renderItem={item => (
                  <List.Item>
                    <Space>
                      {item.icon}
                      <span>{item.title}</span>
                      <Tag color="blue">{item.count}</Tag>
                    </Space>
                  </List.Item>
                )}
              />
            </Card>
          </Col>

          {/* 右侧：最近订单 */}
          <Col xs={24} md={16}>
            <Card title="最近订单">
              <List
                locale={{ emptyText: <Empty description="暂无订单数据" /> }}
                dataSource={recentOrders}
                renderItem={order => {
                  const status = statusMap[order.status] ?? {
                    text: order.status || '未知',
                    color: 'default',
                    icon: null
                  };
                  return (
                    <List.Item
                      key={order.orderId || order.orderNumber}
                      actions={[
                        <Tag key="status" color={status.color} icon={status.icon}>
                          {status.text}
                        </Tag>
                      ]}
                    >
                      <List.Item.Meta
                        title={`订单号: ${order.orderNumber || order.orderId}`}
                        description={
                          <Space direction="vertical">
                            <span>客户: {order.customerName || '-'}</span>
                            <span>金额: {formatAmount(order.totalAmount)}</span>
                            <span>时间: {formatDateTime(order.createdAt)}</span>
                          </Space>
                        }
                      />
                    </List.Item>
                  );
                }}
              />
            </Card>
          </Col>
        </Row>
      </Spin>
    </div>
  );
};

export default Home;