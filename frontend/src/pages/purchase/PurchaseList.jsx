import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Input, Select, DatePicker, Typography, Card, Tag } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { purchaseApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const PurchaseList = () => {
  const navigate = useNavigate();
  const [purchaseOrders, setPurchaseOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  // 查询条件
  const [searchParams, setSearchParams] = useState({
    purchaseOrderId: '',
    supplierId: '',
    supplierName: '',
    amount: '',
    status: '',
    dateRange: []
  });

  // 采购订单状态选项
  const purchaseStatusOptions = [
    { value: 'PENDING', label: '待处理' },
    { value: 'APPROVED', label: '已批准' },
    { value: 'REJECTED', label: '已拒绝' },
    { value: 'IN_TRANSIT', label: '运输中' },
    { value: 'DELIVERED', label: '已送达' },
    { value: 'CANCELLED', label: '已取消' }
  ];

  // 获取采购订单列表
  const fetchPurchaseOrders = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的分页查询API
      // 暂时使用模拟数据
      const mockPurchaseOrders = Array.from({ length: 20 }, (_, index) => ({
        id: `po-${index + 1}`,
        supplierId: `supp-${Math.floor(Math.random() * 10) + 1}`,
        supplierName: `供应商${Math.floor(Math.random() * 10) + 1}`,
        amount: 1000 + Math.random() * 99000,
        status: purchaseStatusOptions[Math.floor(Math.random() * purchaseStatusOptions.length)].value,
        orderDate: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
        expectedDeliveryDate: new Date(Date.now() + Math.random() * 15 * 24 * 60 * 60 * 1000).toISOString(),
        actualDeliveryDate: Math.random() > 0.5 ? new Date(Date.now() - Math.random() * 10 * 24 * 60 * 60 * 1000).toISOString() : null,
        paymentStatus: ['PENDING', 'COMPLETED', 'PARTIAL'][Math.floor(Math.random() * 3)],
        itemsCount: Math.floor(Math.random() * 10) + 1,
        notes: `采购订单备注信息${index + 1}`
      }));
      
      setPurchaseOrders(mockPurchaseOrders);
      setPagination(prev => ({
        ...prev,
        total: mockPurchaseOrders.length
      }));
    } catch (error) {
      console.error('获取采购订单列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchPurchaseOrders();
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
    fetchPurchaseOrders();
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({
      purchaseOrderId: '',
      supplierId: '',
      supplierName: '',
      amount: '',
      status: '',
      dateRange: []
    });
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
    fetchPurchaseOrders();
  };

  // 采购订单状态映射
  const statusMap = {
    PENDING: { text: '待处理', color: 'blue' },
    APPROVED: { text: '已批准', color: 'green' },
    REJECTED: { text: '已拒绝', color: 'red' },
    IN_TRANSIT: { text: '运输中', color: 'orange' },
    DELIVERED: { text: '已送达', color: 'purple' },
    CANCELLED: { text: '已取消', color: 'default' }
  };

  // 付款状态映射
  const paymentStatusMap = {
    PENDING: { text: '待付款', color: 'blue' },
    COMPLETED: { text: '已付款', color: 'green' },
    PARTIAL: { text: '部分付款', color: 'orange' }
  };

  // 表格列配置
  const columns = [
    {
      title: '采购订单ID',
      dataIndex: 'id',
      key: 'id',
      width: 120
    },
    {
      title: '供应商ID',
      dataIndex: 'supplierId',
      key: 'supplierId',
      width: 100
    },
    {
      title: '供应商名称',
      dataIndex: 'supplierName',
      key: 'supplierName',
      width: 120
    },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 100,
      render: (text) => `¥${text.toFixed(2)}`
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (text) => (
        <Tag color={statusMap[text].color}>{statusMap[text].text}</Tag>
      )
    },
    {
      title: '付款状态',
      dataIndex: 'paymentStatus',
      key: 'paymentStatus',
      width: 100,
      render: (text) => (
        <Tag color={paymentStatusMap[text].color}>{paymentStatusMap[text].text}</Tag>
      )
    },
    {
      title: '订单日期',
      dataIndex: 'orderDate',
      key: 'orderDate',
      width: 150,
      render: (text) => new Date(text).toLocaleDateString()
    },
    {
      title: '预计送达日期',
      dataIndex: 'expectedDeliveryDate',
      key: 'expectedDeliveryDate',
      width: 150,
      render: (text) => new Date(text).toLocaleDateString()
    },
    {
      title: '实际送达日期',
      dataIndex: 'actualDeliveryDate',
      key: 'actualDeliveryDate',
      width: 150,
      render: (text) => text ? new Date(text).toLocaleDateString() : '未送达'
    },
    {
      title: '商品数量',
      dataIndex: 'itemsCount',
      key: 'itemsCount',
      width: 80
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/purchase/${record.id}`)}>查看</Button>
          <Button type="link" icon={<EditOutlined />}>编辑</Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4}>采购订单列表</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/purchase/create')}>创建采购订单</Button>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>采购订单ID：</span>
            <Input
              placeholder="请输入采购订单ID"
              value={searchParams.purchaseOrderId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, purchaseOrderId: e.target.value }))}
              style={{ width: 120 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>供应商ID：</span>
            <Input
              placeholder="请输入供应商ID"
              value={searchParams.supplierId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, supplierId: e.target.value }))}
              style={{ width: 100 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>供应商名称：</span>
            <Input
              placeholder="请输入供应商名称"
              value={searchParams.supplierName}
              onChange={(e) => setSearchParams(prev => ({ ...prev, supplierName: e.target.value }))}
              style={{ width: 120 }}
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
            <span>状态：</span>
            <Select
              placeholder="请选择状态"
              value={searchParams.status}
              onChange={(value) => setSearchParams(prev => ({ ...prev, status: value }))}
              style={{ width: 100 }}
            >
              {purchaseStatusOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>订单时间：</span>
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

      {/* 采购订单表格 */}
      <Card>
        <Table
          columns={columns}
          dataSource={purchaseOrders}
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

export default PurchaseList;
