import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Row, Col, Statistic, Button, Table, Space, Tag, Typography, Progress, Spin } from 'antd';
import {
  UserOutlined,
  ShopOutlined,
  DatabaseOutlined,
  BarChartOutlined,
  SettingOutlined,
  FileTextOutlined,
  AlertOutlined,
} from '@ant-design/icons';
import { tenantApi, userApi, auditApi } from '../../services/api';
import { formatDateTime } from '../../utils/format';

const { Text } = Typography;

const TENANT_STATUS_META = [
  { key: 'ACTIVE', name: '活跃', color: '#16a34a' },
  { key: 'INACTIVE', name: '非活跃', color: '#94a3b8' },
  { key: 'SUSPENDED', name: '暂停', color: '#d97706' },
  { key: 'DELETED', name: '删除', color: '#dc2626' },
];

// 审计日志的对象类型映射到看板的四类图标，AuthSession / SystemConfig 等归入系统
const TARGET_CATEGORY = {
  Tenant: 'tenant',
  User: 'user',
  Role: 'user',
  Order: 'order',
};
const CATEGORY_LABEL = { tenant: '租户', user: '用户', order: '订单', system: '系统' };

const PlatformDashboard = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);

  // 租户/用户统计来自真实接口；后端没有跨租户订单与系统资源端点，故不再展示假指标
  const [statistics, setStatistics] = useState({
    totalTenants: 0,
    activeTenants: 0,
    totalUsers: 0
  });
  const [tenantStatus, setTenantStatus] = useState(
    TENANT_STATUS_META.map((m) => ({ ...m, value: 0 }))
  );
  // 活动日志只带 tenantId，展示租户名靠这张映射表
  const [tenantNameMap, setTenantNameMap] = useState({});
  const [recentActivities, setRecentActivities] = useState([]);

  const fetchStats = useCallback(async () => {
    setLoading(true);
    try {
      const tenants = await tenantApi.getTenants();
      const list = Array.isArray(tenants) ? tenants : [];
      const statusCount = {};
      list.forEach((t) => {
        statusCount[t.status] = (statusCount[t.status] || 0) + 1;
      });
      setTenantNameMap(
        list.reduce((map, t) => ({ ...map, [t.id]: t.name }), {})
      );
      // 平台管理员不带 tenantId 请求 /users 即拿到全平台用户，无需逐租户汇总
      let totalUsers = 0;
      try {
        const users = await userApi.getUsers();
        totalUsers = Array.isArray(users) ? users.length : 0;
      } catch {
        // 失败时统计为 0：错误提示由 api 拦截器统一给出，不猜测用户量
      }
      setStatistics({
        totalTenants: list.length,
        activeTenants: statusCount.ACTIVE || 0,
        totalUsers
      });
      setTenantStatus(TENANT_STATUS_META.map((m) => ({ ...m, value: statusCount[m.key] || 0 })));
    } catch (error) {
      console.error('加载平台概览失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchRecentActivities = useCallback(async () => {
    try {
      const activities = await auditApi.getRecent(10);
      setRecentActivities(Array.isArray(activities) ? activities : []);
    } catch {
      // 保持空态：错误提示由 api 拦截器统一给出，此处不回填占位记录
      setRecentActivities([]);
    }
  }, []);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  useEffect(() => {
    fetchRecentActivities();
  }, [fetchRecentActivities]);

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
      case 'SUCCESS':
        return <Tag color="green">成功</Tag>;
      case 'error':
        return <Tag color="red">错误</Tag>;
      case 'FAILURE':
        return <Tag color="red">失败</Tag>;
      case 'warning':
        return <Tag color="orange">警告</Tag>;
      default:
        return <Tag color="blue">信息</Tag>;
    }
  };

  const activityColumns = [
    {
      title: '活动类型',
      dataIndex: 'targetType',
      key: 'type',
      render: (targetType) => {
        const category = TARGET_CATEGORY[targetType] || 'system';
        return (
          <Space>
            {getActivityIcon(category)}
            <Text>{CATEGORY_LABEL[category]}</Text>
          </Space>
        );
      },
    },
    {
      title: '所属租户',
      dataIndex: 'tenantId',
      key: 'tenantId',
      render: (tenantId) =>
        // 平台级操作没有租户归属
        tenantId ? tenantNameMap[tenantId] || tenantId : '平台级',
    },
    {
      title: '操作人',
      dataIndex: 'actorName',
      key: 'actorName',
    },
    {
      title: '描述',
      dataIndex: 'actionLabel',
      key: 'actionLabel',
      render: (actionLabel, record) =>
        record.detail ? `${actionLabel}（${record.detail}）` : actionLabel,
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (createdAt) => formatDateTime(createdAt),
    },
    {
      title: '状态',
      dataIndex: 'result',
      key: 'result',
      render: (result) => getStatusTag(result),
    },
  ];

  return (
    <div className="page">
      <div className="page__header">
        <div>
          <h1 className="page__title">平台管理仪表盘</h1>
          <p className="page__subtitle">租户、用户与系统资源的全局概览</p>
        </div>
      </div>

      <Spin spinning={loading}>
        {/* 系统概览：窄屏 1 列 / 平板 2 列 / 桌面 3 列 */}
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} xl={8}>
            <Card>
              <Statistic
                title="总租户数"
                value={statistics.totalTenants}
                prefix={<ShopOutlined />}
                suffix={`/ ${statistics.activeTenants} 活跃`}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={8}>
            <Card>
              <Statistic
                title="总用户数"
                value={statistics.totalUsers}
                prefix={<UserOutlined />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={8}>
            <Card title="租户状态分布">
              {tenantStatus.map((item) => (
                <div key={item.name} style={{ marginBottom: 8 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                    <Text>{item.name}</Text>
                    <Text>{item.value}</Text>
                  </div>
                  <Progress
                    percent={
                      statistics.totalTenants > 0
                        ? Math.round((item.value / statistics.totalTenants) * 100)
                        : 0
                    }
                    strokeColor={item.color}
                  />
                </div>
              ))}
            </Card>
          </Col>
        </Row>

        {/* 最近活动 */}
        <Card title="最近活动">
          <Table
            columns={activityColumns}
            dataSource={recentActivities}
            rowKey="id"
            pagination={{ pageSize: 5, hideOnSinglePage: true }}
            scroll={{ x: 'max-content' }}
          />
        </Card>
      </Spin>

      {/* 快捷操作：窄屏 1 列 / 平板 2 列 / 桌面 4 列 */}
      <Card title="快捷操作">
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} xl={6}>
            <Button type="primary" block icon={<ShopOutlined />} onClick={() => navigate('/tenants')}>
              管理租户
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
            <Button block icon={<FileTextOutlined />} onClick={() => navigate('/platform/audit-logs')}>
              查看日志
            </Button>
          </Col>
        </Row>
      </Card>
    </div>
  );
};

export default PlatformDashboard;
