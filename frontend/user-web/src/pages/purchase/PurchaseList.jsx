import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Space, Input, Select, DatePicker, Typography, Card, Tag } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { purchaseApi } from '../../services/api';
import {
  formatAmount,
  purchaseStatusMap,
  purchaseStatusOptions,
  purchaseStatusLabel
} from '../../constants/purchase';

import Can from '../../components/Auth/Can.jsx';
const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const emptySearchParams = {
  purchaseOrderId: '',
  supplierId: '',
  supplierName: '',
  minTotalAmount: '',
  status: undefined,
  dateRange: []
};

const PurchaseList = () => {
  const navigate = useNavigate();
  const [purchaseOrders, setPurchaseOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [statusOptions, setStatusOptions] = useState(purchaseStatusOptions);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  // 查询条件
  const [searchParams, setSearchParams] = useState(emptySearchParams);

  // 状态下拉取服务端字典，避免与 PurchaseStatus 枚举漂移
  useEffect(() => {
    const loadStatuses = async () => {
      try {
        const data = await purchaseApi.getStatuses();
        if (data && data.length) {
          setStatusOptions(data.map((item) => ({ value: item.code, label: item.label })));
        }
      } catch {
        // 字典接口不可用时使用前端静态兜底
      }
    };
    loadStatuses();
  }, []);

  // 获取采购订单列表（服务端分页 + 条件查询，不再在前端过滤全量数组）
  const fetchPurchaseOrders = useCallback(async () => {
    setLoading(true);
    try {
      const [startDate, endDate] = searchParams.dateRange || [];
      // 不下发 tenantId：后端按 JWT 租户上下文解析，避免非平台管理员用户被 TenantIsolationAspect 判越权
      const result = await purchaseApi.getPurchaseOrders({
        page: (pagination.current || 1) - 1,
        size: pagination.pageSize || 10,
        purchaseOrderId: searchParams.purchaseOrderId || undefined,
        supplierId: searchParams.supplierId || undefined,
        supplierName: searchParams.supplierName || undefined,
        minTotalAmount: searchParams.minTotalAmount || undefined,
        status: searchParams.status || undefined,
        startDate: startDate ? startDate.format('YYYY-MM-DD') : undefined,
        endDate: endDate ? endDate.format('YYYY-MM-DD') : undefined
      });
      setPurchaseOrders(result?.content || []);
      setPagination((prev) => ({
        ...prev,
        total: result?.total || 0,
        current: (result?.page ?? 0) + 1
      }));
    } catch (error) {
      console.error('获取采购订单列表失败:', error);
      setPurchaseOrders([]);
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize, searchParams]);

  useEffect(() => {
    fetchPurchaseOrders();
  }, [fetchPurchaseOrders]);

  const handleTableChange = (pageInfo) => {
    setPagination((prev) => ({
      ...prev,
      current: pageInfo.current || 1,
      pageSize: pageInfo.pageSize || 10
    }));
  };

  // 处理查询：条件变化后回到第一页，交给服务端重新分页
  const handleSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams(emptySearchParams);
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  // 表格列配置（字段与后端 PurchaseOrderDto 一一对应）
  const columns = [
    {
      title: '采购单号',
      dataIndex: 'purchaseOrderId',
      key: 'purchaseOrderId',
      width: 200
    },
    {
      title: '供应商ID',
      dataIndex: 'supplierId',
      key: 'supplierId',
      width: 140,
      render: (text) => text || '-'
    },
    {
      title: '供应商名称',
      dataIndex: 'supplierName',
      key: 'supplierName',
      width: 160,
      render: (text) => text || '-'
    },
    {
      title: '收货仓库ID',
      dataIndex: 'warehouseId',
      key: 'warehouseId',
      width: 160,
      render: (text) => text || '-'
    },
    {
      title: '采购项数',
      key: 'itemsCount',
      width: 90,
      render: (_, record) => (record.items ? record.items.length : 0)
    },
    {
      title: '总金额',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 120,
      render: (text) => formatAmount(text)
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (text, record) => {
        const status = purchaseStatusMap[text] || { color: 'default' };
        return <Tag color={status.color}>{record.statusLabel || purchaseStatusLabel(text)}</Tag>;
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/purchase/${record.purchaseOrderId}`)}>查看</Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Title level={4}>采购订单列表</Title>
        <Can permission="purchase:write">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/purchase/create')}>创建采购订单</Button>
        </Can>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 5 }} styles={{ body: { padding: 16 } }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>采购单号：</span>
            <Input
              placeholder="请输入采购单号"
              allowClear
              value={searchParams.purchaseOrderId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, purchaseOrderId: e.target.value }))}
              style={{ width: 160 }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>供应商ID：</span>
            <Input
              placeholder="请输入供应商ID"
              allowClear
              value={searchParams.supplierId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, supplierId: e.target.value }))}
              style={{ width: 140 }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>供应商名称：</span>
            <Input
              placeholder="请输入供应商名称"
              allowClear
              value={searchParams.supplierName}
              onChange={(e) => setSearchParams(prev => ({ ...prev, supplierName: e.target.value }))}
              style={{ width: 140 }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>总金额≥：</span>
            <Input
              placeholder="请输入金额下限"
              allowClear
              value={searchParams.minTotalAmount}
              onChange={(e) => setSearchParams(prev => ({ ...prev, minTotalAmount: e.target.value }))}
              style={{ width: 120 }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>状态：</span>
            <Select
              placeholder="请选择状态"
              allowClear
              value={searchParams.status}
              onChange={(value) => setSearchParams(prev => ({ ...prev, status: value }))}
              style={{ width: 120 }}
            >
              {statusOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>创建时间：</span>
            <RangePicker
              value={searchParams.dateRange}
              onChange={(dateRange) => setSearchParams(prev => ({ ...prev, dateRange: dateRange || [] }))}
            />
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button onClick={handleReset}>重置</Button>
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>查询</Button>
        </div>
      </Card>

      {/* 采购订单表格 */}
      <Card styles={{ body: { padding: 16 } }}>
        <Table
          columns={columns}
          dataSource={purchaseOrders}
          rowKey="purchaseOrderId"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={handleTableChange}
          scroll={{ x: 1500 }}
        />
      </Card>
    </div>
  );
};

export default PurchaseList;
