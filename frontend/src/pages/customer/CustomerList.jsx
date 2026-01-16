import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Input, Select, DatePicker, Typography, Card, Tag } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { customerApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const CustomerList = () => {
  const navigate = useNavigate();
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  // 查询条件
  const [searchParams, setSearchParams] = useState({
    customerId: '',
    customerName: '',
    email: '',
    phone: '',
    status: '',
    dateRange: []
  });

  // 客户状态选项
  const customerStatusOptions = [
    { value: 'ACTIVE', label: '活跃' },
    { value: 'INACTIVE', label: '不活跃' },
    { value: 'SUSPENDED', label: '已暂停' }
  ];

  // 获取客户列表
  const fetchCustomers = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的分页查询API
      // 暂时使用模拟数据
      const mockCustomers = Array.from({ length: 20 }, (_, index) => ({
        id: `cust-${index + 1}`,
        customerName: `客户${index + 1}`,
        email: `customer${index + 1}@example.com`,
        phone: `1380013800${index + 1}`,
        status: customerStatusOptions[Math.floor(Math.random() * customerStatusOptions.length)].value,
        registrationDate: new Date(Date.now() - Math.random() * 365 * 24 * 60 * 60 * 1000).toISOString(),
        lastOrderDate: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
        totalOrders: Math.floor(Math.random() * 100),
        totalSpent: 100 + Math.random() * 9900,
        address: `城市${index + 1} 区${index + 1} 街道${index + 1}号`,
        company: `公司${index + 1}`,
        notes: `客户备注信息${index + 1}`
      }));
      
      setCustomers(mockCustomers);
      setPagination(prev => ({
        ...prev,
        total: mockCustomers.length
      }));
    } catch (error) {
      console.error('获取客户列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchCustomers();
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
    fetchCustomers();
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({
      customerId: '',
      customerName: '',
      email: '',
      phone: '',
      status: '',
      dateRange: []
    });
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
    fetchCustomers();
  };

  // 客户状态映射
  const statusMap = {
    ACTIVE: { text: '活跃', color: 'success' },
    INACTIVE: { text: '不活跃', color: 'default' },
    SUSPENDED: { text: '已暂停', color: 'error' }
  };

  // 表格列配置
  const columns = [
    {
      title: '客户ID',
      dataIndex: 'id',
      key: 'id',
      width: 100
    },
    {
      title: '客户名称',
      dataIndex: 'customerName',
      key: 'customerName',
      width: 120
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      width: 180
    },
    {
      title: '电话',
      dataIndex: 'phone',
      key: 'phone',
      width: 120
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (text) => (
        <Tag color={statusMap[text].color}>{statusMap[text].text}</Tag>
      )
    },
    {
      title: '注册日期',
      dataIndex: 'registrationDate',
      key: 'registrationDate',
      width: 150,
      render: (text) => new Date(text).toLocaleDateString()
    },
    {
      title: '最近订单日期',
      dataIndex: 'lastOrderDate',
      key: 'lastOrderDate',
      width: 150,
      render: (text) => new Date(text).toLocaleDateString()
    },
    {
      title: '总订单数',
      dataIndex: 'totalOrders',
      key: 'totalOrders',
      width: 90
    },
    {
      title: '总消费',
      dataIndex: 'totalSpent',
      key: 'totalSpent',
      width: 100,
      render: (text) => `¥${text.toFixed(2)}`
    },
    {
      title: '公司',
      dataIndex: 'company',
      key: 'company',
      width: 120
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/customers/${record.id}`)}>查看</Button>
          <Button type="link" icon={<EditOutlined />}>编辑</Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4}>客户列表</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/customers/create')}>添加客户</Button>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>客户ID：</span>
            <Input
              placeholder="请输入客户ID"
              value={searchParams.customerId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, customerId: e.target.value }))}
              style={{ width: 120 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>客户名称：</span>
            <Input
              placeholder="请输入客户名称"
              value={searchParams.customerName}
              onChange={(e) => setSearchParams(prev => ({ ...prev, customerName: e.target.value }))}
              style={{ width: 120 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>邮箱：</span>
            <Input
              placeholder="请输入邮箱"
              value={searchParams.email}
              onChange={(e) => setSearchParams(prev => ({ ...prev, email: e.target.value }))}
              style={{ width: 180 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>电话：</span>
            <Input
              placeholder="请输入电话"
              value={searchParams.phone}
              onChange={(e) => setSearchParams(prev => ({ ...prev, phone: e.target.value }))}
              style={{ width: 120 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>状态：</span>
            <Select
              placeholder="请选择状态"
              value={searchParams.status}
              onChange={(value) => setSearchParams(prev => ({ ...prev, status: value }))}
              style={{ width: 100 }}
            >
              {customerStatusOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>注册时间：</span>
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
      </Card>

      {/* 客户表格 */}
      <Card>
        <Table
          columns={columns}
          dataSource={customers}
          rowKey="id"
          loading={loading}
          pagination={pagination}
          onChange={handlePaginationChange}
          scroll={{ x: 1200 }}
        />
      </Card>
    </div>
  );
};

export default CustomerList;
