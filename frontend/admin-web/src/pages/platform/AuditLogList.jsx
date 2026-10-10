import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Table, Space, Select, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { auditApi, tenantApi } from '../../services/api';
import { formatDateTime } from '../../utils/format';

const { Text } = Typography;

// 后端 size 上限为 200，前端先夹住避免翻页时被拒绝
const MAX_PAGE_SIZE = 200;
const PAGE_SIZE_OPTIONS = ['10', '20', '50', '100', '200'];

const RESULT_OPTIONS = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILURE', label: '失败' },
];

const AuditLogList = () => {
  const [tenants, setTenants] = useState([]);
  const [logs, setLogs] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  // 后端分页 0 起，antd 的 current 从 1 起，转换只在提交/展示时做
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [result, setResult] = useState(undefined);
  const [tenantId, setTenantId] = useState(undefined);

  const tenantNameMap = useMemo(
    () => tenants.reduce((map, t) => ({ ...map, [t.id]: t.name }), {}),
    [tenants]
  );

  // 租户筛选与日志里的租户名都依赖同一份租户列表
  useEffect(() => {
    tenantApi
      .getTenants()
      .then((data) => setTenants(Array.isArray(data) ? data : []))
      .catch(() => setTenants([]));
  }, []);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size };
      if (result) params.result = result;
      if (tenantId) params.tenantId = tenantId;
      const data = await auditApi.getAuditLogs(params);
      setLogs(Array.isArray(data?.content) ? data.content : []);
      setTotal(typeof data?.total === 'number' ? data.total : 0);
    } catch {
      // 失败时保持空态：错误提示由 api 拦截器统一给出，不回填占位记录
      setLogs([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, size, result, tenantId]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  // 换筛选条件时回到第一页，否则容易停在被过滤掉的空页上
  const handleResultChange = (value) => {
    setPage(0);
    setResult(value);
  };

  const handleTenantChange = (value) => {
    setPage(0);
    setTenantId(value);
  };

  const columns = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (createdAt) => formatDateTime(createdAt),
    },
    {
      title: '所属租户',
      dataIndex: 'tenantId',
      key: 'tenantId',
      width: 160,
      render: (value) =>
        // 平台级操作没有租户归属
        value ? tenantNameMap[value] || value : '平台级',
    },
    {
      title: '操作人',
      dataIndex: 'actorName',
      key: 'actorName',
      width: 140,
      render: (actorName) => actorName || '-',
    },
    {
      title: '操作',
      dataIndex: 'actionLabel',
      key: 'actionLabel',
      width: 200,
      render: (actionLabel, record) => (
        <Space vertical size={0}>
          <Text>{actionLabel || record.action}</Text>
          {record.detail ? (
            <Text type="secondary">{record.detail}</Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: '对象类型',
      dataIndex: 'targetType',
      key: 'targetType',
      width: 120,
      render: (targetType) => targetType || '-',
    },
    {
      title: '对象ID',
      dataIndex: 'targetId',
      key: 'targetId',
      width: 220,
      ellipsis: true,
      render: (targetId) => targetId || '-',
    },
    {
      title: '结果',
      dataIndex: 'result',
      key: 'result',
      width: 100,
      render: (value) =>
        value === 'SUCCESS' ? (
          <Tag color="green">成功</Tag>
        ) : value === 'FAILURE' ? (
          <Tag color="red">失败</Tag>
        ) : (
          <Tag color="default">{value || '-'}</Tag>
        ),
    },
    {
      title: 'IP',
      dataIndex: 'ip',
      key: 'ip',
      width: 160,
      render: (ip) => ip || '-',
    },
    {
      title: 'traceId',
      dataIndex: 'traceId',
      key: 'traceId',
      width: 140,
      ellipsis: true,
      render: (traceId) => traceId || '-',
    },
  ];

  return (
    <div className="page">
      <div className="page__header">
        <div>
          <h1 className="page__title">审计日志</h1>
          <p className="page__subtitle">平台与租户管理员操作的留痕记录</p>
        </div>
        <Space wrap>
          <Select
            placeholder="操作结果"
            style={{ width: 160, maxWidth: '100%' }}
            value={result}
            onChange={handleResultChange}
            allowClear
            options={RESULT_OPTIONS}
          />
          <Select
            placeholder="按租户筛选"
            style={{ width: 200, maxWidth: '100%' }}
            value={tenantId}
            onChange={handleTenantChange}
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            options={tenants.map((t) => ({ value: t.id, label: t.name }))}
          />
          <Button icon={<ReloadOutlined />} onClick={fetchLogs} loading={loading}>
            刷新
          </Button>
        </Space>
      </div>

      <Table
        columns={columns}
        dataSource={logs}
        rowKey="id"
        loading={loading}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total,
          showSizeChanger: true,
          pageSizeOptions: PAGE_SIZE_OPTIONS,
          showTotal: (totalCount) => `共 ${totalCount} 条`,
          onChange: (current, pageSize) => {
            const nextSize = Math.min(pageSize || size, MAX_PAGE_SIZE);
            setSize(nextSize);
            // 每页条数变化时后端可能落在不同页，回到首页最稳妥
            setPage(nextSize !== size ? 0 : Math.max(current - 1, 0));
          },
        }}
      />
    </div>
  );
};

export default AuditLogList;
