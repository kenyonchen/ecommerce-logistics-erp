import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Input, Select, Typography, Card, Tag, Progress } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { warehouseApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const WarehouseList = () => {
  const navigate = useNavigate();
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  // 查询条件
  const [searchParams, setSearchParams] = useState({
    warehouseId: '',
    warehouseName: '',
    location: '',
    type: '',
    status: ''
  });

  // 仓库类型选项
  const warehouseTypeOptions = [
    { value: 'PRIMARY', label: '主仓库' },
    { value: 'SECONDARY', label: '二级仓库' },
    { value: 'REGIONAL', label: '区域仓库' },
    { value: 'CROSS_DOCK', label: '跨境仓库' }
  ];

  // 仓库状态选项
  const warehouseStatusOptions = [
    { value: 'ACTIVE', label: '活跃' },
    { value: 'INACTIVE', label: '不活跃' },
    { value: 'MAINTENANCE', label: '维护中' }
  ];

  // 获取仓库列表
  const fetchWarehouses = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的分页查询API
      // 暂时使用模拟数据
      const mockWarehouses = Array.from({ length: 20 }, (_, index) => {
        const capacity = 10000 + Math.random() * 90000;
        const usage = Math.random() * capacity;
        const usageRate = (usage / capacity) * 100;
        
        return {
          id: `wh-${index + 1}`,
          warehouseName: `仓库${index + 1}`,
          location: `城市${index + 1}`,
          type: warehouseTypeOptions[Math.floor(Math.random() * warehouseTypeOptions.length)].value,
          status: warehouseStatusOptions[Math.floor(Math.random() * warehouseStatusOptions.length)].value,
          capacity: capacity,
          usage: usage,
          usageRate: usageRate,
          manager: `管理员${index + 1}`,
          contact: `1380013800${index + 1}`,
          address: `城市${index + 1} 区${index + 1} 街道${index + 1}号`,
          createdAt: new Date(Date.now() - Math.random() * 90 * 24 * 60 * 60 * 1000).toISOString(),
          updatedAt: new Date(Date.now() - Math.random() * 45 * 24 * 60 * 60 * 1000).toISOString()
        };
      });
      
      setWarehouses(mockWarehouses);
      setPagination(prev => ({
        ...prev,
        total: mockWarehouses.length
      }));
    } catch (error) {
      console.error('获取仓库列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchWarehouses();
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
    fetchWarehouses();
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({
      warehouseId: '',
      warehouseName: '',
      location: '',
      type: '',
      status: ''
    });
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
    fetchWarehouses();
  };

  // 仓库状态映射
  const statusMap = {
    ACTIVE: { text: '活跃', color: 'success' },
    INACTIVE: { text: '不活跃', color: 'default' },
    MAINTENANCE: { text: '维护中', color: 'warning' }
  };

  // 仓库类型映射
  const typeMap = {
    PRIMARY: '主仓库',
    SECONDARY: '二级仓库',
    REGIONAL: '区域仓库',
    CROSS_DOCK: '跨境仓库'
  };

  // 表格列配置
  const columns = [
    {
      title: '仓库ID',
      dataIndex: 'id',
      key: 'id',
      width: 100
    },
    {
      title: '仓库名称',
      dataIndex: 'warehouseName',
      key: 'warehouseName',
      width: 120
    },
    {
      title: '位置',
      dataIndex: 'location',
      key: 'location',
      width: 100
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (text) => typeMap[text] || text
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
      title: '容量利用率',
      dataIndex: 'usageRate',
      key: 'usageRate',
      width: 120,
      render: (text, record) => (
        <div>
          <Progress percent={Math.round(text)} size="small" status={text > 80 ? 'warning' : text > 90 ? 'error' : 'normal'} />
          <div style={{ marginTop: 4, fontSize: 12, textAlign: 'center' }}>
            {Math.round(record.usage)}/{Math.round(record.capacity)} ({Math.round(text)}%)
          </div>
        </div>
      )
    },
    {
      title: '管理员',
      dataIndex: 'manager',
      key: 'manager',
      width: 100
    },
    {
      title: '联系电话',
      dataIndex: 'contact',
      key: 'contact',
      width: 120
    },
    {
      title: '详细地址',
      dataIndex: 'address',
      key: 'address',
      ellipsis: true
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
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/warehouses/${record.id}`)}>查看</Button>
          <Button type="link" icon={<EditOutlined />}>编辑</Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4}>仓库列表</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/warehouses/create')}>添加仓库</Button>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>仓库ID：</span>
            <Input
              placeholder="请输入仓库ID"
              value={searchParams.warehouseId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, warehouseId: e.target.value }))}
              style={{ width: 120 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>仓库名称：</span>
            <Input
              placeholder="请输入仓库名称"
              value={searchParams.warehouseName}
              onChange={(e) => setSearchParams(prev => ({ ...prev, warehouseName: e.target.value }))}
              style={{ width: 120 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>位置：</span>
            <Input
              placeholder="请输入位置"
              value={searchParams.location}
              onChange={(e) => setSearchParams(prev => ({ ...prev, location: e.target.value }))}
              style={{ width: 120 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>类型：</span>
            <Select
              placeholder="请选择类型"
              value={searchParams.type}
              onChange={(value) => setSearchParams(prev => ({ ...prev, type: value }))}
              style={{ width: 120 }}
            >
              {warehouseTypeOptions.map(option => (
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
              style={{ width: 120 }}
            >
              {warehouseStatusOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>
        </div>
        
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button onClick={handleReset}>重置</Button>
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>查询</Button>
        </div>
      </Card>

      {/* 仓库表格 */}
      <Card>
        <Table
          columns={columns}
          dataSource={warehouses}
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

export default WarehouseList;
