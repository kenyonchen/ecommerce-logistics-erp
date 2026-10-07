import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Space, Input, Typography, Card, Tag, Popconfirm, message } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined, DeleteOutlined, ShopOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { warehouseApi } from '../../services/api';

const { Title } = Typography;

const WarehouseList = () => {
  const navigate = useNavigate();
  const [warehouses, setWarehouses] = useState([]);
  const [filtered, setFiltered] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchParams, setSearchParams] = useState({
    warehouseId: '',
    warehouseName: '',
    address: '',
    status: ''
  });

  // 获取仓库列表
  const fetchWarehouses = useCallback(async () => {
    setLoading(true);
    try {
      const data = await warehouseApi.getWarehouses();
      const list = Array.isArray(data) ? data : [];
      setWarehouses(list);
      setFiltered(list);
    } catch (error) {
      console.error('获取仓库列表失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  // 初始化加载数据
  useEffect(() => {
    fetchWarehouses();
  }, [fetchWarehouses]);

  // 处理查询
  const handleSearch = () => {
    const { warehouseId, warehouseName, address, status } = searchParams;
    const result = warehouses.filter(item => {
      if (warehouseId && !(item.warehouseId || '').toLowerCase().includes(warehouseId.toLowerCase())) return false;
      if (warehouseName && !(item.warehouseName || '').toLowerCase().includes(warehouseName.toLowerCase())) return false;
      if (address && !(item.address || '').toLowerCase().includes(address.toLowerCase())) return false;
      if (status === 'ACTIVE' && !item.active) return false;
      if (status === 'INACTIVE' && item.active) return false;
      return true;
    });
    setFiltered(result);
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({ warehouseId: '', warehouseName: '', address: '', status: '' });
    setFiltered(warehouses);
  };

  // 删除仓库
  const handleDelete = async (id) => {
    try {
      await warehouseApi.deleteWarehouse(id);
      message.success('仓库删除成功');
      fetchWarehouses();
    } catch (error) {
      console.error('删除仓库失败:', error);
    }
  };

  // 仓库状态映射
  const statusMap = {
    ACTIVE: { text: '启用', color: 'success' },
    INACTIVE: { text: '停用', color: 'default' }
  };

  // 表格列配置
  const columns = [
    {
      title: '仓库编码',
      dataIndex: 'warehouseCode',
      key: 'warehouseCode',
      width: 140
    },
    {
      title: '仓库名称',
      dataIndex: 'warehouseName',
      key: 'warehouseName',
      width: 160
    },
    {
      title: '详细地址',
      dataIndex: 'address',
      key: 'address',
      ellipsis: true
    },
    {
      title: '容量',
      dataIndex: 'capacity',
      key: 'capacity',
      width: 100
    },
    {
      title: '库位数',
      key: 'locationCount',
      width: 90,
      render: (_, record) => (record.locations ? record.locations.length : 0)
    },
    {
      title: '状态',
      dataIndex: 'active',
      key: 'active',
      width: 90,
      render: (active) => (
        <Tag color={active ? statusMap.ACTIVE.color : statusMap.INACTIVE.color}>
          {active ? statusMap.ACTIVE.text : statusMap.INACTIVE.text}
        </Tag>
      )
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/warehouses/${record.warehouseId}`)}>查看</Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => navigate(`/warehouses/${record.warehouseId}`)}>编辑</Button>
          <Popconfirm title="确认删除该仓库？" onConfirm={() => handleDelete(record.warehouseId)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Title level={4}>仓库列表</Title>
        <Space>
          <Button icon={<ShopOutlined />} onClick={() => navigate('/warehouses/shop')}>店铺仓库对接</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/warehouses/create')}>添加仓库</Button>
        </Space>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 5 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 5 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>仓库编码：</span>
            <Input
              placeholder="请输入仓库编码"
              value={searchParams.warehouseId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, warehouseId: e.target.value }))}
              style={{ width: 140 }}
              allowClear
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>仓库名称：</span>
            <Input
              placeholder="请输入仓库名称"
              value={searchParams.warehouseName}
              onChange={(e) => setSearchParams(prev => ({ ...prev, warehouseName: e.target.value }))}
              style={{ width: 140 }}
              allowClear
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>地址：</span>
            <Input
              placeholder="请输入地址"
              value={searchParams.address}
              onChange={(e) => setSearchParams(prev => ({ ...prev, address: e.target.value }))}
              style={{ width: 140 }}
              allowClear
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>状态：</span>
            <Input
              placeholder="ACTIVE / INACTIVE"
              value={searchParams.status}
              onChange={(e) => setSearchParams(prev => ({ ...prev, status: e.target.value.toUpperCase() }))}
              style={{ width: 140 }}
              allowClear
            />
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
          dataSource={filtered}
          rowKey="warehouseId"
          loading={loading}
          pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条` }}
          scroll={{ x: 1100 }}
        />
      </Card>
    </div>
  );
};

export default WarehouseList;
