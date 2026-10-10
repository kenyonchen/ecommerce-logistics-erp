import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Row, Col, Statistic, Button, Table, Typography, Progress, Spin } from 'antd';
import {
  UserOutlined,
  ShoppingCartOutlined,
  ProductOutlined,
  ContainerOutlined,
  DollarOutlined,
  SettingOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { overviewApi } from '../../services/api';

const { Title, Text } = Typography;

const PlatformDashboard = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);

  // 概览数据来自 /overview（登录租户维度）；跨租户订单/系统资源类端点后端尚不存在
  const [statistics, setStatistics] = useState({
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

  const fetchStats = useCallback(async () => {
    setLoading(true);
    try {
      const data = await overviewApi.getSystemOverview({ recentOrderLimit: 5 });
      setStatistics({
        totalOrders: data?.totalOrders ?? 0,
        pendingOrders: data?.pendingOrders ?? 0,
        shippedOrders: data?.shippedOrders ?? 0,
        completedOrders: data?.completedOrders ?? 0,
        totalProducts: data?.totalProducts ?? 0,
        totalWarehouses: data?.totalWarehouses ?? 0,
        totalCustomers: data?.totalCustomers ?? 0,
        totalRevenue: Number(data?.totalRevenue ?? 0)
      });
      setRecentOrders(data?.recentOrders ?? []);
    } catch (error) {
      console.error('加载系统概览失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  const formatDateTime = (value) => {
    if (!value) return '-';
    const normalized = String(value).replace('T', ' ');
    return normalized.length >= 16 ? normalized.slice(0, 16) : normalized;
  };

  const orderColumns = [
    {
      title: '订单号',
      dataIndex: 'orderNumber',
      key: 'orderNumber',
      render: (text, record) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => navigate(`/orders/${record.orderId}`)}>
          {text || record.orderId}
        </Button>
      )
    },
    { title: '客户', dataIndex: 'customerName', key: 'customerName', render: (t) => t || '-' },
    {
      title: '金额',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      render: (amount) => `¥${Number(amount ?? 0).toFixed(2)}`
    },
    { title: '状态', dataIndex: 'statusLabel', key: 'statusLabel', render: (t, record) => t || record.status || '-' },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: formatDateTime
    }
  ];

  const shipmentRate = statistics.totalOrders > 0
    ? Math.round(((statistics.shippedOrders + statistics.completedOrders) / statistics.totalOrders) * 100)
    : 0;

  return (
    <div style={{ padding: '20px' }}>
      <Title level={2}>平台管理仪表盘</Title>

      {/* 经营概览：数据来自 /overview，口径为当前登录租户 */}
      <Spin spinning={loading}>
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} xl={6}>
            <Card>
              <Statistic
                title="总订单数"
                value={statistics.totalOrders}
                prefix={<ShoppingCartOutlined />}
                suffix={`/ ${statistics.pendingOrders} 待处理`}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card>
              <Statistic
                title="总产品数"
                value={statistics.totalProducts}
                prefix={<ProductOutlined />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card>
              <Statistic
                title="总仓库数"
                value={statistics.totalWarehouses}
                prefix={<ContainerOutlined />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card>
              <Statistic
                title="总收入"
                value={statistics.totalRevenue}
                precision={2}
                prefix={<DollarOutlined />}
                suffix="元"
                valueStyle={{ color: '#faad14' }}
              />
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} lg={8}>
            <Card title="订单发货进度">
              <div style={{ marginBottom: 8 }}>
                <Text>发货率（已发货 + 已完成）</Text>
                <Progress percent={shipmentRate} status="active" />
              </div>
              <div>
                <Text>完成率</Text>
                <Progress
                  percent={
                    statistics.totalOrders > 0
                      ? Math.round((statistics.completedOrders / statistics.totalOrders) * 100)
                      : 0
                  }
                  status="success"
                />
              </div>
            </Card>
          </Col>
          <Col xs={24} lg={16}>
            <Card title="客户与订单动态">
              <Row gutter={16} style={{ marginBottom: 16 }}>
                <Col span={12}>
                  <Statistic title="总客户数" value={statistics.totalCustomers} prefix={<UserOutlined />} />
                </Col>
                <Col span={12}>
                  <Statistic title="已完成订单" value={statistics.completedOrders} prefix={<ShoppingCartOutlined />} />
                </Col>
              </Row>
              <Table
                columns={orderColumns}
                dataSource={recentOrders}
                rowKey={(record) => record.orderId || record.orderNumber}
                pagination={false}
                size="small"
              />
            </Card>
          </Col>
        </Row>
      </Spin>

      {/* 快捷操作 */}
      <Card title="快捷操作">
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} xl={6}>
            <Button type="primary" block icon={<ShoppingCartOutlined />} onClick={() => navigate('/orders')}>
              管理订单
            </Button>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Button block icon={<UserOutlined />} onClick={() => navigate('/users')}>
              管理用户
            </Button>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Button block icon={<SettingOutlined />} onClick={() => navigate('/platform/config')}>
              系统配置
            </Button>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Button block icon={<FileTextOutlined />} disabled>
              查看日志
            </Button>
          </Col>
        </Row>
      </Card>
    </div>
  );
};

export default PlatformDashboard;
