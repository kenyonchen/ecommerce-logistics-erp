import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Input, Select, Typography, Card, Tag, Switch, Modal, message, Alert } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, ReloadOutlined, ApiOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { platformIntegrationApi } from '../../services/api';
import { useAuth } from '../../context/AuthContext.jsx';
import { platformOptions, platformLabel, authTypeLabel } from '../../constants/product';

const { Title } = Typography;

/** 平台连接状态（对应后端 PlatformStatus） */
const integrationStatusOptions = [
  { value: 'ACTIVE', label: '活跃' },
  { value: 'INACTIVE', label: '未启用' },
  { value: 'DISCONNECTED', label: '已断开' },
  { value: 'ERROR', label: '错误' }
];

const statusMap = {
  ACTIVE: { text: '活跃', color: 'success' },
  INACTIVE: { text: '未启用', color: 'default' },
  DISCONNECTED: { text: '已断开', color: 'warning' },
  ERROR: { text: '错误', color: 'error' }
};

const PlatformIntegration = () => {
  const navigate = useNavigate();
  const { user, hasRole, hasPermission } = useAuth();
  const currentTenantId = user?.tenantId || localStorage.getItem('tenantId') || '';
  // 读接口任何登录用户可用，写接口后端要求 PLATFORM_ADMIN 或 platform:manage，这里同步收敛按钮
  const canManage = hasRole('PLATFORM_ADMIN') || hasPermission('platform:manage');
  const [integrations, setIntegrations] = useState([]);
  const [filtered, setFiltered] = useState([]);
  const [loading, setLoading] = useState(false);
  const [syncingId, setSyncingId] = useState(null);
  const [syncingCustomerId, setSyncingCustomerId] = useState(null);
  const [syncingOrderId, setSyncingOrderId] = useState(null);
  const [syncingInventoryId, setSyncingInventoryId] = useState(null);
  const [syncingLogisticsId, setSyncingLogisticsId] = useState(null);
  const [testingId, setTestingId] = useState(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [searchParams, setSearchParams] = useState({ platformName: '', status: '', keyword: '' });

  // 获取平台集成列表（后端无分页，列表整体拉取后前端过滤）
  const fetchIntegrations = async () => {
    setLoading(true);
    try {
      // 后端按登录租户过滤，无需再传 tenantId
      const data = await platformIntegrationApi.getIntegrations();
      setIntegrations(data || []);
    } catch (error) {
      // 403 已由 axios 拦截器统一提示"没有权限执行该操作"，此处不再重复弹窗
      if (error?.status !== 403) {
        message.error(error?.message || '获取平台集成列表失败');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIntegrations();
  }, []);

  useEffect(() => {
    const keyword = searchParams.keyword.trim().toLowerCase();
    const result = integrations.filter((item) => {
      const matchPlatform = !searchParams.platformName || item.platformName === searchParams.platformName;
      const matchStatus = !searchParams.status || item.status === searchParams.status;
      const matchKeyword =
        !keyword ||
        (item.id || '').toLowerCase().includes(keyword) ||
        (item.storeUrl || '').toLowerCase().includes(keyword);
      return matchPlatform && matchStatus && matchKeyword;
    });
    setFiltered(result);
    setPagination((prev) => ({ ...prev, current: 1, total: result.length }));
  }, [integrations, searchParams]);

  const handlePaginationChange = (page, pageSize) => {
    setPagination((prev) => ({ ...prev, current: page, pageSize }));
  };

  const handleSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    setSearchParams({ platformName: '', status: '', keyword: '' });
  };

  const handleToggleActive = async (record) => {
    try {
      if (record.status === 'ACTIVE') {
        await platformIntegrationApi.deactivate(record.id);
      } else {
        await platformIntegrationApi.activate(record.id);
      }
      message.success(record.status === 'ACTIVE' ? '平台集成已停用' : '平台集成已启用');
      fetchIntegrations();
    } catch {
      message.error('操作失败');
    }
  };

  // 手动同步商品：调用后端 sync-products，返回本次导入任务的结果反馈
  const handleSyncProducts = async (record) => {
    setSyncingId(record.id);
    try {
      const job = await platformIntegrationApi.syncProducts(record.id, currentTenantId);
      Modal.success({
        title: '商品同步完成',
        content: (
          <div>
            <p>导入任务：{job.jobId}</p>
            <p>
              共 {job.totalCount} 行，成功 {job.successCount} 行，失败 {job.failureCount} 行，跳过{' '}
              {job.skippedCount} 行
            </p>
            <p>{job.message}</p>
          </div>
        )
      });
      fetchIntegrations();
    } catch (error) {
      Modal.error({
        title: '商品同步失败',
        content: `${error?.message || '未知错误'}；如需在未接入真实平台时验证链路，请在「商品导入」页面勾选「使用示例数据」。`
      });
    } finally {
      setSyncingId(null);
    }
  };

  // 手动同步客户：调用后端 sync-customers，按幂等上档导入平台买家
  const handleSyncCustomers = async (record) => {
    setSyncingCustomerId(record.id);
    try {
      const job = await platformIntegrationApi.syncCustomers(record.id, { tenantId: currentTenantId });
      Modal.success({
        title: '客户同步完成',
        content: (
          <div>
            <p>导入任务：{job.jobId}</p>
            <p>
              共 {job.totalCount} 行，成功 {job.successCount} 行，失败 {job.failureCount} 行，跳过{' '}
              {job.skippedCount} 行
            </p>
            <p>{job.message}</p>
          </div>
        )
      });
    } catch (error) {
      Modal.error({
        title: '客户同步失败',
        content: `${error?.message || '未知错误'}；如需在未接入真实平台时验证链路，请在「客户导入」页面勾选「使用示例数据」。`
      });
    } finally {
      setSyncingCustomerId(null);
    }
  };

  // 手动同步订单：调用后端 sync-orders，返回订单导入任务的结果反馈
  const handleSyncOrders = async (record) => {
    setSyncingOrderId(record.id);
    try {
      const job = await platformIntegrationApi.syncOrders(record.id, { tenantId: currentTenantId });
      Modal.success({
        title: '订单同步完成',
        content: (
          <div>
            <p>导入任务：{job.jobId}</p>
            <p>
              共 {job.totalCount} 行，成功 {job.successCount} 行，失败 {job.failureCount} 行，跳过{' '}
              {job.skippedCount} 行
            </p>
            <p>{job.message}</p>
          </div>
        )
      });
      fetchIntegrations();
    } catch (error) {
      Modal.error({
        title: '订单同步失败',
        content: `${error?.message || '未知错误'}；如需在未接入真实平台时验证链路，可在「订单导入」页面勾选「使用示例数据」。`
      });
    } finally {
      setSyncingOrderId(null);
    }
  };

  // 物流同步：调用后端 sync-logistics，走「拉取店铺物流→字段映射→批量导入」链路，返回导入任务结果
  const handleSyncLogistics = async (record) => {
    setSyncingLogisticsId(record.id);
    try {
      const job = await platformIntegrationApi.syncLogistics(record.id, { tenantId: currentTenantId });
      const hasFailure = (job.failureCount ?? 0) > 0;
      Modal[hasFailure ? 'warning' : 'success']({
        title: '物流同步完成',
        width: 640,
        content: (
          <div>
            <p>导入任务：{job.jobId}</p>
            <p>
              共 {job.totalCount} 行，成功 {job.successCount} 行，失败 {job.failureCount} 行，跳过{' '}
              {job.skippedCount} 行
            </p>
            <p>{job.message}</p>
            {job.errors && job.errors.length > 0 && (
              <div style={{ maxHeight: 200, overflow: 'auto' }}>
                {job.errors.slice(0, 20).map((err, idx) => (
                  <p key={idx} style={{ margin: 0 }}>
                    第 {err.rowNumber} 行 {err.externalLogisticsId ? `[${err.externalLogisticsId}]` : ''}：
                    {err.message}
                  </p>
                ))}
                {job.errors.length > 20 && <p>…共 {job.errors.length} 条错误</p>}
              </div>
            )}
          </div>
        )
      });
    } catch (error) {
      Modal.error({
        title: '物流同步失败',
        content: `${error?.message || '未知错误'}；可在「物流导入」页面勾选「使用示例数据」验证链路，或在「物流渠道」中先维护平台返回的渠道编码/承运商。`
      });
    } finally {
      setSyncingLogisticsId(null);
    }
  };

  // 库存推送到平台：调用后端 sync-inventory，返回推送SKU明细
  const handleSyncInventory = async (record) => {
    setSyncingInventoryId(record.id);
    try {
      const result = await platformIntegrationApi.syncInventory(record.id);
      Modal.success({
        title: '库存推送完成',
        content: (
          <div>
            <p>店铺：{result.storeUrl}</p>
            <p>推送SKU数：{result.pushedCount}</p>
            {result.pushedItems && result.pushedItems.length > 0 && (
              <p>
                明细：
                {result.pushedItems.slice(0, 10).map((item, idx) => (
                  <Tag key={idx} style={{ marginBottom: 4 }}>
                    {item.sku} × {item.quantity}
                  </Tag>
                ))}
                {result.pushedItems.length > 10 && <Tag>+{result.pushedItems.length - 10}</Tag>}
              </p>
            )}
          </div>
        )
      });
    } catch (error) {
      Modal.error({
        title: '库存推送失败',
        content: error?.message || '未知错误'
      });
    } finally {
      setSyncingInventoryId(null);
    }
  };

  // 连通性测试：调用后端 test-connection，按认证协议做一次最小代价只读外呼，验证地址与凭据是否可用
  const handleTestConnection = async (record) => {
    setTestingId(record.id);
    try {
      const result = await platformIntegrationApi.testConnection(record.id);
      if (result?.success) {
        message.success(`连接正常（${result.latencyMs}ms）：${result.message || ''}`);
      } else {
        Modal.warning({
          title: '连通性测试未通过',
          content: `${result?.message || '未知原因'}；适配器：${result?.adapterType || '-'}`
        });
      }
    } catch (error) {
      Modal.error({
        title: '连通性测试失败',
        content: error?.message || '未知错误'
      });
    } finally {
      setTestingId(null);
    }
  };

  const columns = [
    {
      title: '集成ID',
      dataIndex: 'id',
      key: 'id',
      width: 220,
      ellipsis: true,
      render: (text) => <span title={text}>{String(text).slice(0, 8)}…</span>
    },
    {
      title: '平台',
      dataIndex: 'platformName',
      key: 'platformName',
      width: 120,
      render: (text) => platformLabel(text)
    },
    {
      title: '对接协议',
      dataIndex: 'authType',
      key: 'authType',
      width: 150,
      render: (text) => authTypeLabel(text || 'CUSTOM_SHOP')
    },
    {
      title: '店铺地址',
      dataIndex: 'storeUrl',
      key: 'storeUrl',
      width: 200,
      ellipsis: true,
      render: (text) => text || <Tag color="warning">未配置</Tag>
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (text) => {
        const tag = statusMap[text] || { text: text, color: 'default' };
        return <Tag color={tag.color}>{tag.text}</Tag>;
      }
    },
    {
      // 后端出参只给掩码（BR-02），任何接口都不再回传凭据明文，前端无需二次打码
      title: 'API密钥',
      dataIndex: 'apiKeyMasked',
      key: 'apiKeyMasked',
      width: 140,
      render: (text, record) => (
        <Space size={4}>
          <span>{text || '-'}</span>
          {record.hasAccessToken && <Tag color="blue">令牌</Tag>}
        </Space>
      )
    },
    {
      title: '接口版本',
      dataIndex: 'apiVersion',
      key: 'apiVersion',
      width: 110,
      render: (text) => text || <Tag>取全局配置</Tag>
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '最后商品同步时间',
      dataIndex: 'lastProductsSyncedAt',
      key: 'lastProductsSyncedAt',
      width: 170,
      render: (text) => (text ? new Date(text).toLocaleString() : '从未同步')
    },
    {
      title: '启用',
      dataIndex: 'status',
      key: 'enabled',
      width: 80,
      render: (status, record) =>
        canManage ? (
          <Switch checked={status === 'ACTIVE'} onChange={() => handleToggleActive(record)} />
        ) : (
          <Tag color={status === 'ACTIVE' ? 'success' : 'default'}>
            {status === 'ACTIVE' ? '启用' : '停用'}
          </Tag>
        )
    },
    {
      title: '操作',
      key: 'action',
      width: canManage ? 480 : 100,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/platform-integration/${record.id}`)}>
            查看
          </Button>
          {canManage && (
            <>
              <Button
                type="link"
                size="small"
                icon={<ApiOutlined />}
                loading={testingId === record.id}
                onClick={() => handleTestConnection(record)}
              >
                连通性测试
              </Button>
              <Button
                type="link"
                size="small"
                icon={<ReloadOutlined />}
                loading={syncingId === record.id}
                onClick={() => handleSyncProducts(record)}
              >
                同步商品
              </Button>
              <Button
                type="link"
                size="small"
                icon={<ReloadOutlined />}
                loading={syncingCustomerId === record.id}
                onClick={() => handleSyncCustomers(record)}
              >
                同步客户
              </Button>
              <Button
                type="link"
                size="small"
                icon={<ReloadOutlined />}
                loading={syncingOrderId === record.id}
                onClick={() => handleSyncOrders(record)}
              >
                同步订单
              </Button>
              <Button
                type="link"
                size="small"
                icon={<ReloadOutlined />}
                loading={syncingLogisticsId === record.id}
                onClick={() => handleSyncLogistics(record)}
              >
                同步物流
              </Button>
              <Button
                type="link"
                size="small"
                icon={<ReloadOutlined />}
                loading={syncingInventoryId === record.id}
                onClick={() => handleSyncInventory(record)}
              >
                推送库存
              </Button>
            </>
          )}
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>平台集成</Title>
        {canManage && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/platform-integration/create')}>
            添加集成
          </Button>
        )}
      </div>

      {!canManage && (
        <Alert
          type="info"
          showIcon
          title="当前账号为只读访问"
          description="查看平台集成需要登录身份；创建、编辑、启停与同步操作需要平台管理员角色或 platform:manage 权限。"
          style={{ marginBottom: 8 }}
        />
      )}

      <Card size="small" style={{ marginBottom: 8 }}>
        <Space wrap>
          <Input
            allowClear
            placeholder="集成ID / 店铺地址"
            value={searchParams.keyword}
            onChange={(e) => setSearchParams((prev) => ({ ...prev, keyword: e.target.value }))}
            style={{ width: 200 }}
          />
          <Select
            allowClear
            placeholder="平台"
            options={platformOptions}
            value={searchParams.platformName || undefined}
            onChange={(value) => setSearchParams((prev) => ({ ...prev, platformName: value || '' }))}
            style={{ width: 150 }}
          />
          <Select
            allowClear
            placeholder="状态"
            options={integrationStatusOptions}
            value={searchParams.status || undefined}
            onChange={(value) => setSearchParams((prev) => ({ ...prev, status: value || '' }))}
            style={{ width: 130 }}
          />
          <Button onClick={handleReset}>重置</Button>
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            查询
          </Button>
        </Space>
      </Card>

      <Card size="small">
        <Table
          columns={columns}
          dataSource={filtered}
          rowKey="id"
          loading={loading}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={handlePaginationChange}
          scroll={{ x: 1900 }}
        />
      </Card>
    </div>
  );
};

export default PlatformIntegration;
