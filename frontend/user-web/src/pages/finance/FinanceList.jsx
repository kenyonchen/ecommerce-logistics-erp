import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Input, Select, DatePicker, Typography, Card, Tag } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { financeApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const FinanceList = () => {
  const navigate = useNavigate();
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  // 查询条件
  const [searchParams, setSearchParams] = useState({
    transactionId: '',
    orderNumber: '',
    amount: '',
    type: '',
    status: '',
    dateRange: []
  });

  // 交易类型选项
  const transactionTypeOptions = [
    { value: 'PAYMENT', label: '付款' },
    { value: 'REFUND', label: '退款' },
    { value: 'FEE', label: '费用' },
    { value: 'REVENUE', label: '收入' }
  ];

  // 交易状态选项
  const transactionStatusOptions = [
    { value: 'PENDING', label: '待处理' },
    { value: 'COMPLETED', label: '已完成' },
    { value: 'FAILED', label: '失败' },
    { value: 'REFUNDED', label: '已退款' }
  ];

  // 获取交易列表
  const fetchTransactions = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的分页查询API
      // 暂时使用模拟数据
      const mockTransactions = Array.from({ length: 20 }, (_, index) => ({
        id: `trans-${index + 1}`,
        orderNumber: `ORD-${2024}-${String(index + 100).padStart(6, '0')}`,
        amount: 100 + Math.random() * 9900,
        type: transactionTypeOptions[Math.floor(Math.random() * transactionTypeOptions.length)].value,
        status: transactionStatusOptions[Math.floor(Math.random() * transactionStatusOptions.length)].value,
        paymentMethod: ['支付宝', '微信支付', '银行卡', 'PayPal'][Math.floor(Math.random() * 4)],
        transactionDate: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
        customerId: `cust-${Math.floor(Math.random() * 20) + 1}`,
        customerName: `客户${Math.floor(Math.random() * 20) + 1}`,
        notes: `交易备注信息${index + 1}`
      }));
      
      setTransactions(mockTransactions);
      setPagination(prev => ({
        ...prev,
        total: mockTransactions.length
      }));
    } catch (error) {
      console.error('获取交易列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchTransactions();
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
    fetchTransactions();
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({
      transactionId: '',
      orderNumber: '',
      amount: '',
      type: '',
      status: '',
      dateRange: []
    });
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
    fetchTransactions();
  };

  // 交易类型映射
  const typeMap = {
    PAYMENT: { text: '付款', color: 'green' },
    REFUND: { text: '退款', color: 'red' },
    FEE: { text: '费用', color: 'orange' },
    REVENUE: { text: '收入', color: 'blue' }
  };

  // 交易状态映射
  const statusMap = {
    PENDING: { text: '待处理', color: 'blue' },
    COMPLETED: { text: '已完成', color: 'green' },
    FAILED: { text: '失败', color: 'red' },
    REFUNDED: { text: '已退款', color: 'orange' }
  };

  // 表格列配置
  const columns = [
    {
      title: '交易ID',
      dataIndex: 'id',
      key: 'id',
      width: 120
    },
    {
      title: '订单编号',
      dataIndex: 'orderNumber',
      key: 'orderNumber',
      width: 150
    },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 100,
      render: (text) => `¥${text.toFixed(2)}`
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 80,
      render: (text) => (
        <Tag color={typeMap[text].color}>{typeMap[text].text}</Tag>
      )
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
      title: '支付方式',
      dataIndex: 'paymentMethod',
      key: 'paymentMethod',
      width: 100
    },
    {
      title: '交易日期',
      dataIndex: 'transactionDate',
      key: 'transactionDate',
      width: 150,
      render: (text) => new Date(text).toLocaleString()
    },
    {
      title: '客户ID',
      dataIndex: 'customerId',
      key: 'customerId',
      width: 100
    },
    {
      title: '客户名称',
      dataIndex: 'customerName',
      key: 'customerName',
      width: 120
    },
    {
      title: '备注',
      dataIndex: 'notes',
      key: 'notes',
      ellipsis: true
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/finance/${record.id}`)}>查看</Button>
          <Button type="link" icon={<EditOutlined />}>编辑</Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Title level={4}>财务交易列表</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/finance/create')}>添加交易</Button>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 5 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 5 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>交易ID：</span>
            <Input
              placeholder="请输入交易ID"
              value={searchParams.transactionId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, transactionId: e.target.value }))}
              style={{ width: 120 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>订单编号：</span>
            <Input
              placeholder="请输入订单编号"
              value={searchParams.orderNumber}
              onChange={(e) => setSearchParams(prev => ({ ...prev, orderNumber: e.target.value }))}
              style={{ width: 150 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>金额：</span>
            <Input
              placeholder="请输入金额"
              value={searchParams.amount}
              onChange={(e) => setSearchParams(prev => ({ ...prev, amount: e.target.value }))}
              style={{ width: 100 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>类型：</span>
            <Select
              placeholder="请选择类型"
              value={searchParams.type}
              onChange={(value) => setSearchParams(prev => ({ ...prev, type: value }))}
              style={{ width: 100 }}
            >
              {transactionTypeOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>状态：</span>
            <Select
              placeholder="请选择状态"
              value={searchParams.status}
              onChange={(value) => setSearchParams(prev => ({ ...prev, status: value }))}
              style={{ width: 100 }}
            >
              {transactionStatusOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>交易时间：</span>
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

      {/* 交易表格 */}
      <Card>
        <Table
          columns={columns}
          dataSource={transactions}
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

export default FinanceList;
