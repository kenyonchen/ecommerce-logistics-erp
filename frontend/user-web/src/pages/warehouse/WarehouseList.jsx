import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Space, Input, Select, Typography, Card, Tag, Popconfirm, message } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, DeleteOutlined, ShopOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { warehouseApi } from '../../services/api';

import Can from '../../components/Auth/Can.jsx';
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
  const [searchParams, setSearchParams] = useState({
    warehouseCode: '',
    warehouseName: '',
    address: '',
    status: undefined
  });

  // 服务端分页取当前页（后端 /warehouses 只有 page/size 参数，且不接收 tenantId，由登录令牌解析）
  const fetchWarehouses = useCallback(async () => {
    setLoading(true);
    try {
      const data = await warehouseApi.getWarehouses({
        page: (pagination.current || 1) - 1,
        size: pagination.pageSize || 10
      });
      setWarehouses(data?.content ?? []);
      setPagination((prev) => ({
        ...prev,
        total: data?.total ?? 0,
        current: (data?.page ?? 0) + 1
      }));
    } catch (error) {
      console.error('获取仓库列表失败:', error);
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize]);

  useEffect(() => {
    fetchWarehouses();
  }, [fetchWarehouses]);

  // 后端不支持按条件过滤，编码/名称/地址/状态只在当前页内做客户端筛选
  const { warehouseCode, warehouseName, address, status } = searchParams;
  const filtered = warehouses.filter(item => {
    if (warehouseCode && !(item.warehouseCode || '').toLowerCase().includes(warehouseCode.trim().toLowerCase())) return false;
    if (warehouseName && !(item.warehouseName || '').toLowerCase().includes(warehouseName.trim().toLowerCase())) return false;
    if (address && !(item.address || '').toLowerCase().includes(address.trim().toLowerCase())) return false;
    if (status === 'ACTIVE' && !item.active) return false;
    if (status === 'INACTIVE' && item.active) return false;
    return true;
  });

  const handleReset = () => {
    setSearchParams({ warehouseCode: '', warehouseName: '', address: '', status: undefined });
  };

  const handleDelete = async (id) => {
    try {
      await warehouseApi.deleteWarehouse(id);
      message.success('仓库删除成功');
      fetchWarehouses();
    } catch (error) {
      console.error('删除仓库失败:', error);
    }
  };

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
      title: '仓库ID',
      dataIndex: 'warehouseId',
      key: 'warehouseId',
      width: 220,
      ellipsis: true
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
        <Tag color={active ? 'success' : 'default'}>{active ? '启用' : '停用'}</Tag>
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
          <Can permission="warehouse:write">
            <Popconfirm title="确认删除该仓库？" onConfirm={() => handleDelete(record.warehouseId)}>
              <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Can>
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
          <Can permission="warehouse:write">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/warehouses/create')}>添加仓库</Button>
          </Can>
        </Space>
      </div>

      {/* 查询条件（仅作用于当前页） */}
      <Card style={{ marginBottom: 5 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 5 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>仓库编码：</span>
            <Input
              placeholder="请输入仓库编码"
              value={searchParams.warehouseCode}
              onChange={(e) => setSearchParams(prev => ({ ...prev, warehouseCode: e.target.value }))}
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
            <Select
              placeholder="请选择状态"
              value={searchParams.status}
              onChange={(value) => setSearchParams(prev => ({ ...prev, status: value }))}
              style={{ width: 140 }}
              allowClear
            >
              <Option value="ACTIVE">启用</Option>
              <Option value="INACTIVE">停用</Option>
            </Select>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button onClick={handleReset}>重置</Button>
        </div>
      </Card>

      {/* 仓库表格 */}
      <Card>
        <Table
          columns={columns}
          dataSource={filtered}
          rowKey="warehouseId"
          loading={loading}
          onChange={(pageInfo) => setPagination((prev) => ({
            ...prev,
            current: pageInfo.current || 1,
            pageSize: pageInfo.pageSize || 10
          }))}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          scroll={{ x: 1100 }}
        />
      </Card>
    </div>
  );
};

export default WarehouseList;
