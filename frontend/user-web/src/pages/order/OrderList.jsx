import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Input, Select, DatePicker, Typography } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { orderApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const OrderList = () => {
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
    status: '',
    dateRange: []
  });

  // 订单状态选项
  const orderStatusOptions = [
    { value: 'PENDING', label: '待处理' },
    { value: 'PROCESSING', label: '处理中' },
    { value: 'SHIPPED', label: '已发货' },
    { value: 'COMPLETED', label: '已完成' },
    { value: 'CANCELLED', label: '已取消' }
  ];

  // 获取订单列表
  const fetchOrders = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的分页查询API
      // 暂时使用模拟数据
      const mockOrders = Array.from({ length: 20 }, (_, index) => ({
        id: `order-${index + 1}`,
        orderNumber: `ORD-${2024}-${String(index + 1).padStart(6, '0')}`,
        customerId: `customer-${Math.floor(Math.random() * 10) + 1}`,
        customerName: `客户${Math.floor(Math.random() * 10) + 1}`,
        totalAmount: 100 + Math.random() * 900,
        status: orderStatusOptions[Math.floor(Math.random() * orderStatusOptions.length)].value,
        createdAt: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - Math.random() * 3 * 24 * 60 * 60 * 1000).toISOString()
      }));
      
      setOrders(mockOrders);
      setPagination(prev => ({
        ...prev,
        total: mockOrders.length
      }));
    } catch (error) {
      console.error('获取订单列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchOrders();
  }, []);

  // 处理分页变化
  const handlePaginationChange = (page, pageSize) => {
    setPagination(prev => ({
      ...prev,
      current: page,
      pageSize
    }));
  };

  // 处理查询
  const handleSearch = () => {
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
    fetchOrders();
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({
      orderNumber: '',
      customerId: '',
      status: '',
      dateRange: []
    });
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
    fetchOrders();
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
      width: 120
    },
    {
      title: '客户名称',
      dataIndex: 'customerName',
      key: 'customerName',
      width: 120
    },
    {
      title: '订单金额',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 120,
      render: (text) => `¥${text.toFixed(2)}`
    },
    {
      title: '订单状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (text) => {
        const statusMap = {
          PENDING: '待处理',
          PROCESSING: '处理中',
          SHIPPED: '已发货',
          COMPLETED: '已完成',
          CANCELLED: '已取消'
        };
        return statusMap[text] || text;
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (text) => new Date(text).toLocaleString()
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (text) => new Date(text).toLocaleString()
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<EyeOutlined />}><Link to={`/orders/${record.id}`}>查看</Link></Button>
          <Button type="link" icon={<EditOutlined />}>编辑</Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Title level={4}>订单列表</Title>
        <Button type="primary" icon={<PlusOutlined />}><Link to="/orders/create">创建订单</Link></Button>
      </div>

      {/* 查询条件 */}
      <div style={{ backgroundColor: '#fff', padding: 16, marginBottom: 5, borderRadius: 8, boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)' }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 5 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>订单编号：</span>
            <Input
              placeholder="请输入订单编号"
              value={searchParams.orderNumber}
              onChange={(e) => setSearchParams(prev => ({ ...prev, orderNumber: e.target.value }))}
              style={{ width: 200 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>客户ID：</span>
            <Input
              placeholder="请输入客户ID"
              value={searchParams.customerId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, customerId: e.target.value }))}
              style={{ width: 150 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>订单状态：</span>
            <Select
              placeholder="请选择状态"
              value={searchParams.status}
              onChange={(value) => setSearchParams(prev => ({ ...prev, status: value }))}
              style={{ width: 120 }}
            >
              {orderStatusOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>创建时间：</span>
            <RangePicker
              value={searchParams.dateRange}
              onChange={(dateRange) => setSearchParams(prev => ({ ...prev, dateRange }))}
            />
          </div>
        </div>
        
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button onClick={handleReset}>重置</Button>
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>查询</Button>
        </div>
      </div>

      {/* 订单表格 */}
      <div style={{ backgroundColor: '#fff', padding: 16, borderRadius: 8, boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)' }}>
        <Table
          columns={columns}
          dataSource={orders}
          rowKey="id"
          loading={loading}
          pagination={pagination}
          onChange={handlePaginationChange}
          scroll={{ x: 1000 }}
        />
      </div>
    </div>
  );
};

export default OrderList;
