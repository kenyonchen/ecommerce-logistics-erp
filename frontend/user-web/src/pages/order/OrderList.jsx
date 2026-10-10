import React, { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Button,
  Space,
  Input,
  Select,
  DatePicker,
  Typography,
  Popconfirm,
  Tag,
  Card,
  message
} from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, DeleteOutlined, ImportOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { orderApi } from '../../services/api';
import {
  DEFAULT_TENANT_ID,
  orderStatusOptions,
  orderStatusMap,
  platformOptions,
  platformLabel
} from '../../constants/order';

import Can from '../../components/Auth/Can.jsx';
const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const OrderList = () => {
  const navigate = useNavigate();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  // 查询条件
  const [searchParams, setSearchParams] = useState({
    orderNumber: '',
    customerId: '',
    customerName: '',
    status: undefined,
    platform: undefined,
    keyword: '',
    dateRange: []
  });

  // 获取订单列表（服务端分页）
  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const [startDate, endDate] = searchParams.dateRange || [];
      const params = {
        tenantId: DEFAULT_TENANT_ID,
        page: (pagination.current || 1) - 1,
        size: pagination.pageSize || 10,
        orderNumber: searchParams.orderNumber || undefined,
        customerId: searchParams.customerId || undefined,
        customerName: searchParams.customerName || undefined,
        status: searchParams.status || undefined,
        platform: searchParams.platform || undefined,
        keyword: searchParams.keyword || undefined,
        startDate: startDate ? startDate.format('YYYY-MM-DD') : undefined,
        endDate: endDate ? endDate.format('YYYY-MM-DD') : undefined
      };
      const result = await orderApi.getOrders(params);
      const content = result?.content || [];
      setOrders(content);
      setPagination((prev) => ({
        ...prev,
        total: result?.total || 0,
        current: (result?.page ?? 0) + 1
      }));
    } catch (error) {
      console.error('获取订单列表失败:', error);
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize, searchParams]);

  // 初始化与条件变化加载数据
  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  // 处理分页/排序变化
  const handleTableChange = (pageInfo) => {
    setPagination((prev) => ({
      ...prev,
      current: pageInfo.current || 1,
      pageSize: pageInfo.pageSize || 10
    }));
  };

  // 处理查询
  const handleSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
    fetchOrders();
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({
      orderNumber: '',
      customerId: '',
      customerName: '',
      status: undefined,
      platform: undefined,
      keyword: '',
      dateRange: []
    });
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  // 删除订单
  const handleDelete = async (id) => {
    try {
      await orderApi.deleteOrder(id);
      message.success('订单删除成功');
      fetchOrders();
    } catch (error) {
      console.error('删除订单失败:', error);
    }
  };

  // 表格列配置
  const columns = [
    {
      title: '订单编号',
      dataIndex: 'orderNumber',
      key: 'orderNumber',
      width: 180
    },
    {
      title: '客户ID',
      dataIndex: 'customerId',
      key: 'customerId',
      width: 140,
      render: (text) => text || '-'
    },
    {
      title: '客户名称',
      dataIndex: 'customerName',
      key: 'customerName',
      width: 120,
      render: (text) => text || '-'
    },
    {
      title: '订单金额',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 120,
      render: (text) => (text === null || text === undefined ? '-' : `¥${Number(text).toFixed(2)}`)
    },
    {
      title: '订单状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (text) => {
        const matched = orderStatusMap[text];
        return matched ? <Tag color={matched.color}>{matched.text}</Tag> : text || '-';
      }
    },
    {
      title: '来源平台',
      dataIndex: 'platform',
      key: 'platform',
      width: 110,
      render: (text) => platformLabel(text)
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/orders/${record.orderId}`)}>
            查看
          </Button>
          <Can permission="order:write">
            <Popconfirm
              title="确认删除该订单？"
              description="删除后无法恢复。"
              okText="确定"
              cancelText="取消"
              onConfirm={() => handleDelete(record.orderId)}
            >
              <Button type="link" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Can>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Title level={4}>订单列表</Title>
        <Space>
          <Can permission="order:import">
            <Button icon={<ImportOutlined />}>
              <Link to="/orders/import">订单导入</Link>
            </Button>
          </Can>
          <Can permission="order:write">
            <Button type="primary" icon={<PlusOutlined />}>
              <Link to="/orders/create">创建订单</Link>
            </Button>
          </Can>
        </Space>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 5 }} styles={{ body: { padding: 16 } }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>订单编号：</span>
            <Input
              placeholder="请输入订单编号"
              allowClear
              value={searchParams.orderNumber}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, orderNumber: e.target.value }))}
              style={{ width: 180 }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>客户ID：</span>
            <Input
              placeholder="请输入客户ID"
              allowClear
              value={searchParams.customerId}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, customerId: e.target.value }))}
              style={{ width: 150 }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>关键字：</span>
            <Input
              placeholder="订单号/客户/平台订单号"
              allowClear
              value={searchParams.keyword}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, keyword: e.target.value }))}
              style={{ width: 180 }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>订单状态：</span>
            <Select
              placeholder="请选择状态"
              allowClear
              value={searchParams.status}
              onChange={(value) => setSearchParams((prev) => ({ ...prev, status: value }))}
              style={{ width: 130 }}
            >
              {orderStatusOptions.map((option) => (
                <Option key={option.value} value={option.value}>
                  {option.label}
                </Option>
              ))}
            </Select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>来源平台：</span>
            <Select
              placeholder="请选择平台"
              allowClear
              value={searchParams.platform}
              onChange={(value) => setSearchParams((prev) => ({ ...prev, platform: value }))}
              style={{ width: 130 }}
            >
              {platformOptions.map((option) => (
                <Option key={option.value} value={option.value}>
                  {option.label}
                </Option>
              ))}
            </Select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>创建时间：</span>
            <RangePicker
              value={searchParams.dateRange}
              onChange={(dateRange) => setSearchParams((prev) => ({ ...prev, dateRange: dateRange || [] }))}
            />
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button onClick={handleReset}>重置</Button>
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            查询
          </Button>
        </div>
      </Card>

      {/* 订单表格 */}
      <Card styles={{ body: { padding: 16 } }}>
        <Table
          columns={columns}
          dataSource={orders}
          rowKey="orderId"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={handleTableChange}
          scroll={{ x: 1100 }}
        />
      </Card>
    </div>
  );
};

export default OrderList;
