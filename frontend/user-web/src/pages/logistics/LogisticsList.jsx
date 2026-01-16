import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Input, Select, DatePicker, Typography, Card } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { logisticsApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const LogisticsList = () => {
  const navigate = useNavigate();
  const [logisticsOrders, setLogisticsOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  // 查询条件
  const [searchParams, setSearchParams] = useState({
    logisticsNumber: '',
    orderNumber: '',
    status: '',
    channel: '',
    dateRange: []
  });

  // 物流状态选项
  const logisticsStatusOptions = [
    { value: 'PENDING', label: '待处理' },
    { value: 'PICKED_UP', label: '已揽收' },
    { value: 'IN_TRANSIT', label: '运输中' },
    { value: 'DELIVERED', label: '已送达' },
    { value: 'FAILED', label: '配送失败' }
  ];

  // 物流渠道选项
  const logisticsChannelOptions = [
    { value: 'FEDEX', label: 'FedEx' },
    { value: 'UPS', label: 'UPS' },
    { value: 'DHL', label: 'DHL' },
    { value: 'SF', label: '顺丰' },
    { value: 'STO', label: '申通' },
    { value: 'YTO', label: '圆通' },
    { value: 'YUNDA', label: '韵达' },
    { value: 'ZTO', label: '中通' }
  ];

  // 获取物流单列表
  const fetchLogisticsOrders = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的分页查询API
      // 暂时使用模拟数据
      const mockLogisticsOrders = Array.from({ length: 20 }, (_, index) => ({
        id: `logistics-${index + 1}`,
        logisticsNumber: `LOG-${2024}-${String(index + 1).padStart(6, '0')}`,
        orderNumber: `ORD-${2024}-${String(index + 100).padStart(6, '0')}`,
        channel: logisticsChannelOptions[Math.floor(Math.random() * logisticsChannelOptions.length)].value,
        status: logisticsStatusOptions[Math.floor(Math.random() * logisticsStatusOptions.length)].value,
        recipient: `收件人${Math.floor(Math.random() * 10) + 1}`,
        recipientPhone: `1380013800${Math.floor(Math.random() * 10)}`,
        shippingAddress: `北京市朝阳区建国路${Math.floor(Math.random() * 100) + 1}号`,
        trackingNumber: `TRK-${Math.floor(Math.random() * 900000) + 100000}`,
        createdAt: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - Math.random() * 3 * 24 * 60 * 60 * 1000).toISOString()
      }));
      
      setLogisticsOrders(mockLogisticsOrders);
      setPagination(prev => ({
        ...prev,
        total: mockLogisticsOrders.length
      }));
    } catch (error) {
      console.error('获取物流单列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchLogisticsOrders();
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
    fetchLogisticsOrders();
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({
      logisticsNumber: '',
      orderNumber: '',
      status: '',
      channel: '',
      dateRange: []
    });
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
    fetchLogisticsOrders();
  };

  // 物流状态映射
  const statusMap = {
    PENDING: { text: '待处理', color: 'blue' },
    PICKED_UP: { text: '已揽收', color: 'processing' },
    IN_TRANSIT: { text: '运输中', color: 'orange' },
    DELIVERED: { text: '已送达', color: 'success' },
    FAILED: { text: '配送失败', color: 'error' }
  };

  // 物流渠道映射
  const channelMap = {
    FEDEX: 'FedEx',
    UPS: 'UPS',
    DHL: 'DHL',
    SF: '顺丰',
    STO: '申通',
    YTO: '圆通',
    YUNDA: '韵达',
    ZTO: '中通'
  };

  // 表格列配置
  const columns = [
    {
      title: '物流单号',
      dataIndex: 'logisticsNumber',
      key: 'logisticsNumber',
      width: 180
    },
    {
      title: '订单编号',
      dataIndex: 'orderNumber',
      key: 'orderNumber',
      width: 180
    },
    {
      title: '物流渠道',
      dataIndex: 'channel',
      key: 'channel',
      width: 100,
      render: (text) => channelMap[text] || text
    },
    {
      title: '物流状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (text) => {
        return <span style={{ color: statusMap[text].color }}>{statusMap[text].text}</span>;
      }
    },
    {
      title: '收件人',
      dataIndex: 'recipient',
      key: 'recipient',
      width: 100
    },
    {
      title: '联系电话',
      dataIndex: 'recipientPhone',
      key: 'recipientPhone',
      width: 120
    },
    {
      title: '收货地址',
      dataIndex: 'shippingAddress',
      key: 'shippingAddress',
      ellipsis: true
    },
    {
      title: '追踪单号',
      dataIndex: 'trackingNumber',
      key: 'trackingNumber',
      width: 150
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (text) => new Date(text).toLocaleString()
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/logistics/${record.id}`)}>查看</Button>
          <Button type="link" icon={<EditOutlined />}>编辑</Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Title level={4}>物流单列表</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/logistics/create')}>创建物流单</Button>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 5 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 5 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>物流单号：</span>
            <Input
              placeholder="请输入物流单号"
              value={searchParams.logisticsNumber}
              onChange={(e) => setSearchParams(prev => ({ ...prev, logisticsNumber: e.target.value }))}
              style={{ width: 200 }}
            />
          </div>
          
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
            <span>物流状态：</span>
            <Select
              placeholder="请选择状态"
              value={searchParams.status}
              onChange={(value) => setSearchParams(prev => ({ ...prev, status: value }))}
              style={{ width: 120 }}
            >
              {logisticsStatusOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>物流渠道：</span>
            <Select
              placeholder="请选择渠道"
              value={searchParams.channel}
              onChange={(value) => setSearchParams(prev => ({ ...prev, channel: value }))}
              style={{ width: 120 }}
            >
              {logisticsChannelOptions.map(option => (
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
      </Card>

      {/* 物流单表格 */}
      <Card>
        <Table
          columns={columns}
          dataSource={logisticsOrders}
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

export default LogisticsList;
