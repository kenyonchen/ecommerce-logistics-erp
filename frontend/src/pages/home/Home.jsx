import React, { useState, useEffect } from 'react';
import { Typography, Card, Row, Col, Statistic, Progress, List, Tag, Space } from 'antd';
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
  CloseCircleOutlined
} from '@ant-design/icons';

const { Title, Paragraph } = Typography;

const Home = () => {
  const [stats, setStats] = useState({
    totalOrders: 0,
    pendingOrders: 0,
    shippedOrders: 0,
    completedOrders: 0,
    totalProducts: 0,
    totalWarehouses: 0,
    totalCustomers: 0,
    totalRevenue: 0
  });

  const [recentOrders, setRecentOrders] = useState([]);

  // 获取统计数据
  const fetchStats = async () => {
    try {
      // 这里简化处理，实际应该调用后端的API
      // 暂时使用模拟数据
      setStats({
        totalOrders: 1250,
        pendingOrders: 150,
        shippedOrders: 320,
        completedOrders: 780,
        totalProducts: 500,
        totalWarehouses: 5,
        totalCustomers: 800,
        totalRevenue: 128500.50
      });

      // 模拟最近订单数据
      setRecentOrders([
        {
          id: 'order-12345',
          customer: '张三',
          amount: 1280.50,
          status: 'PENDING',
          time: '2024-01-15 10:30'
        },
        {
          id: 'order-12344',
          customer: '李四',
          amount: 899.99,
          status: 'PROCESSING',
          time: '2024-01-15 09:45'
        },
        {
          id: 'order-12343',
          customer: '王五',
          amount: 2560.00,
          status: 'SHIPPED',
          time: '2024-01-15 08:20'
        },
        {
          id: 'order-12342',
          customer: '赵六',
          amount: 599.50,
          status: 'COMPLETED',
          time: '2024-01-15 07:10'
        },
        {
          id: 'order-12341',
          customer: '钱七',
          amount: 1899.99,
          status: 'CANCELLED',
          time: '2024-01-14 17:30'
        }
      ]);
    } catch (error) {
      console.error('获取统计数据失败:', error);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchStats();
  }, []);

  // 订单状态映射
  const statusMap = {
    PENDING: { text: '待处理', color: 'blue', icon: <ClockCircleOutlined /> },
    PROCESSING: { text: '处理中', color: 'processing', icon: <ClockCircleOutlined /> },
    SHIPPED: { text: '已发货', color: 'green', icon: <CheckCircleOutlined /> },
    COMPLETED: { text: '已完成', color: 'success', icon: <CheckCircleOutlined /> },
    CANCELLED: { text: '已取消', color: 'error', icon: <CloseCircleOutlined /> }
  };

  // 计算订单完成率
  const orderCompletionRate = stats.totalOrders > 0 
    ? (stats.completedOrders / stats.totalOrders) * 100 
    : 0;

  // 计算订单发货率
  const orderShippingRate = stats.totalOrders > 0 
    ? ((stats.shippedOrders + stats.completedOrders) / stats.totalOrders) * 100 
    : 0;

  return (
    <div>
      <Title level={4}>系统概览</Title>
      <Paragraph>欢迎使用电商物流ERP系统，这里展示了系统的关键指标和最新动态。</Paragraph>

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
          <Card title="订单处理进度" style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 16 }}>
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
                { title: '财务管理', icon: <DollarOutlined />, count: '¥128,500.50' },
                { title: '采购管理', icon: <ShoppingOutlined />, count: 120 },
                { title: '平台集成', icon: <LinkOutlined />, count: 5 }
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
              dataSource={recentOrders}
              renderItem={order => (
                <List.Item
                  key={order.id}
                  actions={[
                    <Tag color={statusMap[order.status].color} icon={statusMap[order.status].icon}>
                      {statusMap[order.status].text}
                    </Tag>
                  ]}
                >
                  <List.Item.Meta
                    title={`订单号: ${order.id}`}
                    description={
                      <Space direction="vertical">
                        <span>客户: {order.customer}</span>
                        <span>金额: ¥{order.amount.toFixed(2)}</span>
                        <span>时间: {order.time}</span>
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Home;
