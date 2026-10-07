import React, { useState } from 'react';
import { Button, Card, DatePicker, Input, Select, Space, Table, Tag, Typography } from 'antd';
import { ImportOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { customerApi } from '../../services/api';
import { DEFAULT_TENANT_ID, platformLabel } from '../../constants/product';
import { customerStatusMap, customerStatusOptions } from '../../constants/customer';

const { Title } = Typography;
const { RangePicker } = DatePicker;

/**
 * 客户列表：服务端分页条件查询（客户ID/名称/邮箱/电话/状态/注册时间）。
 */
const CustomerList = () => {
  const navigate = useNavigate();
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const [searchParams, setSearchParams] = useState({
    customerId: '',
    customerName: '',
    email: '',
    phone: '',
    status: undefined,
    dateRange: []
  });

  const fetchCustomers = async (page = pagination.current, pageSize = pagination.pageSize) => {
    setLoading(true);
    try {
      const data = await customerApi.getCustomers({
        tenantId: DEFAULT_TENANT_ID,
        customerId: searchParams.customerId || undefined,
        customerName: searchParams.customerName || undefined,
        email: searchParams.email || undefined,
        phone: searchParams.phone || undefined,
        status: searchParams.status || undefined,
        createdFrom: searchParams.dateRange?.[0]?.format('YYYY-MM-DD') || undefined,
        createdTo: searchParams.dateRange?.[1]?.format('YYYY-MM-DD') || undefined,
        page: page - 1,
        size: pageSize,
        sort: 'createdAt,desc'
      });
      setCustomers(data?.content || []);
      setPagination({ current: (data?.page ?? page - 1) + 1, pageSize: data?.size ?? pageSize, total: data?.total ?? 0 });
    } catch {
      // 拦截器已提示错误
    } finally {
      setLoading(false);
    }
  };

  React.useEffect(() => {
    fetchCustomers(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSearch = () => fetchCustomers(1);

  const handleReset = () => {
    setSearchParams({
      customerId: '',
      customerName: '',
      email: '',
      phone: '',
      status: undefined,
      dateRange: []
    });
    fetchCustomers(1, pagination.pageSize);
  };

  const columns = [
    { title: '客户ID', dataIndex: 'customerId', key: 'customerId', width: 160, ellipsis: true },
    { title: '客户名称', dataIndex: 'customerName', key: 'customerName', width: 140 },
    { title: '邮箱', dataIndex: 'email', key: 'email', width: 200, render: (text) => text || '-' },
    { title: '电话', dataIndex: 'phone', key: 'phone', width: 140, render: (text) => text || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status, record) => {
        const tag = customerStatusMap[status] || { text: status, color: 'default' };
        return <Tag color={tag.color}>{record.statusLabel || tag.text}</Tag>;
      }
    },
    {
      title: '来源平台',
      dataIndex: 'sourcePlatform',
      key: 'sourcePlatform',
      width: 120,
      render: (text) => (text ? platformLabel(text) : '手动创建')
    },
    {
      title: '平台买家ID',
      dataIndex: 'externalBuyerId',
      key: 'externalBuyerId',
      width: 160,
      ellipsis: true,
      render: (text) => text || '-'
    },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      fixed: 'right',
      render: (_, record) => (
        <Button type="link" onClick={() => navigate(`/customers/${record.customerId}`)}>
          详情
        </Button>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Title level={4}>客户列表</Title>
        <Space>
          <Button icon={<ImportOutlined />} onClick={() => navigate('/customers/import')}>
            导入客户
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/customers/create')}>
            添加客户
          </Button>
        </Space>
      </div>

      <Card style={{ marginBottom: 5 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 5 }}>
          <Space>
            <span>客户ID：</span>
            <Input
              placeholder="请输入客户ID"
              value={searchParams.customerId}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, customerId: e.target.value }))}
              style={{ width: 160 }}
            />
          </Space>
          <Space>
            <span>客户名称：</span>
            <Input
              placeholder="请输入客户名称"
              value={searchParams.customerName}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, customerName: e.target.value }))}
              style={{ width: 140 }}
            />
          </Space>
          <Space>
            <span>邮箱：</span>
            <Input
              placeholder="请输入邮箱"
              value={searchParams.email}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, email: e.target.value }))}
              style={{ width: 180 }}
            />
          </Space>
          <Space>
            <span>电话：</span>
            <Input
              placeholder="请输入电话"
              value={searchParams.phone}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, phone: e.target.value }))}
              style={{ width: 140 }}
            />
          </Space>
          <Space>
            <span>状态：</span>
            <Select
              allowClear
              placeholder="请选择状态"
              value={searchParams.status}
              onChange={(value) => setSearchParams((prev) => ({ ...prev, status: value }))}
              style={{ width: 120 }}
              options={customerStatusOptions.filter((item) => item.value !== 'DELETED')}
            />
          </Space>
          <Space>
            <span>注册时间：</span>
            <RangePicker
              value={searchParams.dateRange}
              onChange={(dateRange) => setSearchParams((prev) => ({ ...prev, dateRange }))}
            />
          </Space>
        </div>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button onClick={handleReset}>重置</Button>
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            查询
          </Button>
        </div>
      </Card>

      <Card>
        <Table
          columns={columns}
          dataSource={customers}
          rowKey="customerId"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={(pageInfo) => fetchCustomers(pageInfo.current, pageInfo.pageSize)}
          scroll={{ x: 1200 }}
        />
      </Card>
    </div>
  );
};

export default CustomerList;
