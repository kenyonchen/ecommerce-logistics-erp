import React, { useState } from 'react';
import { Card, Row, Col, Statistic, Button, Table, Space, Tag, Typography, Progress } from 'antd';
import { UserOutlined, ShopOutlined, DatabaseOutlined, BarChartOutlined, AlertOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;

const PlatformDashboard = () => {
  // 模拟数据
  const [statistics] = useState({
    totalTenants: 12,
    activeTenants: 10,
    totalUsers: 156,
    totalOrders: 1284,
    systemLoad: 45,
    databaseUsage: 68,
    storageUsage: 32
  });

  // 最近活动
  const [recentActivities] = useState([
    {
      id: 1,
      type: 'tenant',
      action: '创建',
      name: '新租户 - 阿里巴巴',
      time: '2026-01-16 14:30',
      status: 'success'
    },
    {
      id: 2,
      type: 'user',
      action: '登录',
      name: 'admin',
      time: '2026-01-16 14:25',
      status: 'success'
    },
    {
      id: 3,
      type: 'order',
      action: '创建',
      name: '订单 #ORD-2026-001',
      time: '2026-01-16 14:20',
      status: 'success'
    },
    {
      id: 4,
      type: 'system',
      action: '错误',
      name: '数据库连接失败',
      time: '2026-01-16 14:15',
      status: 'error'
    },
    {
      id: 5,
      type: 'tenant',
      action: '状态变更',
      name: '租户 - 京东',
      time: '2026-01-16 14:10',
      status: 'warning'
    }
  ]);

  // 租户状态
  const [tenantStatus] = useState([
    {
      name: '活跃',
      value: 10,
      color: '#52c41a'
    },
    {
      name: '非活跃',
      value: 1,
      color: '#d9d9d9'
    },
    {
      name: '暂停',
      value: 1,
      color: '#faad14'
    },
    {
      name: '删除',
      value: 0,
      color: '#ff4d4f'
    }
  ]);

  // 获取活动类型图标
  const getActivityIcon = (type) => {
    switch (type) {
      case 'tenant':
        return <ShopOutlined />;
      case 'user':
        return <UserOutlined />;
      case 'order':
        return <BarChartOutlined />;
      case 'system':
        return <DatabaseOutlined />;
      default:
        return <AlertOutlined />;
    }
  };

  // 获取活动状态标签
  const getStatusTag = (status) => {
    switch (status) {
      case 'success':
        return <Tag color="green">成功</Tag>;
      case 'error':
        return <Tag color="red">错误</Tag>;
      case 'warning':
        return <Tag color="orange">警告</Tag>;
      default:
        return <Tag color="blue">信息</Tag>;
    }
  };

  const activityColumns = [
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type) => (
        <Space>
          {getActivityIcon(type)}
          <Text>{type === 'tenant' ? '租户' : type === 'user' ? '用户' : type === 'order' ? '订单' : '系统'}</Text>
        </Space>
      ),
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
    },
    {
      title: '对象',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '时间',
      dataIndex: 'time',
      key: 'time',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => getStatusTag(status),
    },
  ];

  return (
    <div style={{ padding: '20px' }}>
      <Title level={2}>平台管理仪表盘</Title>
      
      {/* 系统概览 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="总租户数"
              value={statistics.totalTenants}
              prefix={<ShopOutlined />}
              suffix={`/ ${statistics.activeTenants} 活跃`}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总用户数"
              value={statistics.totalUsers}
              prefix={<UserOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总订单数"
              value={statistics.totalOrders}
              prefix={<BarChartOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="系统负载"
              value={statistics.systemLoad}
              suffix="%"
              prefix={<DatabaseOutlined />}
            />
            <Progress percent={statistics.systemLoad} size="small" style={{ marginTop: 8 }} />
          </Card>
        </Col>
      </Row>
      
      {/* 资源使用情况 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card title="数据库使用情况">
            <Progress percent={statistics.databaseUsage} status="active" />
            <Text type="secondary" style={{ marginTop: 8, display: 'block' }}>
              已使用: {statistics.databaseUsage}% / 总容量: 100GB
            </Text>
          </Card>
        </Col>
        <Col span={8}>
          <Card title="存储使用情况">
            <Progress percent={statistics.storageUsage} status="success" />
            <Text type="secondary" style={{ marginTop: 8, display: 'block' }}>
              已使用: {statistics.storageUsage}% / 总容量: 500GB
            </Text>
          </Card>
        </Col>
        <Col span={8}>
          <Card title="租户状态分布">
            {tenantStatus.map((item) => (
              <div key={item.name} style={{ marginBottom: 8 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <Text>{item.name}</Text>
                  <Text>{item.value}</Text>
                </div>
                <Progress percent={(item.value / statistics.totalTenants) * 100} status="normal" strokeColor={item.color} />
              </div>
            ))}
          </Card>
        </Col>
      </Row>
      
      {/* 最近活动 */}
      <Card title="最近活动" style={{ marginBottom: 24 }}>
        <Table
          columns={activityColumns}
          dataSource={recentActivities}
          rowKey="id"
          pagination={{ pageSize: 5 }}
        />
      </Card>
      
      {/* 快捷操作 */}
      <Card title="快捷操作">
        <Row gutter={[16, 16]}>
          <Col span={6}>
            <Button type="primary" block icon={<ShopOutlined />}>
              管理租户
            </Button>
          </Col>
          <Col span={6}>
            <Button type="default" block icon={<UserOutlined />}>
              管理用户
            </Button>
          </Col>
          <Col span={6}>
            <Button type="default" block icon={<DatabaseOutlined />}>
              系统配置
            </Button>
          </Col>
          <Col span={6}>
            <Button type="default" block icon={<AlertOutlined />}>
              查看日志
            </Button>
          </Col>
        </Row>
      </Card>
    </div>
  );
};

export default PlatformDashboard;