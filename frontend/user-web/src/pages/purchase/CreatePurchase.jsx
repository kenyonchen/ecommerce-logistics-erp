import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Form, Input, Select, InputNumber, message, Table } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { purchaseApi, warehouseApi } from "../../services/api";

const { Title } = Typography;
const { Option } = Select;

let rowKeySeed = 1;

const buildEmptyItem = () => ({ key: rowKeySeed++, skuCode: '', productName: '', quantity: 1, unitPrice: 0 });

const CreatePurchase = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [warehouses, setWarehouses] = useState([]);
  const [items, setItems] = useState([buildEmptyItem()]);

  const fetchWarehouses = useCallback(async () => {
    try {
      const data = await warehouseApi.getWarehouses({ page: 0, size: 200 });
      setWarehouses(Array.isArray(data) ? data : (data?.content ?? []));
    } catch (error) {
      console.error('获取仓库列表失败:', error);
      setWarehouses([]);
    }
  }, []);

  useEffect(() => {
    fetchWarehouses();
  }, [fetchWarehouses]);

  // 返回列表页
  const handleBack = () => {
    navigate('/purchase');
  };

  const updateItem = (key, patch) => {
    setItems(prev => prev.map(item => (item.key === key ? { ...item, ...patch } : item)));
  };

  // 添加采购物品
  const addItem = () => {
    setItems(prev => [...prev, buildEmptyItem()]);
  };

  // 删除采购物品
  const removeItem = (key) => {
    setItems(prev => prev.filter(item => item.key !== key));
  };

  // 提交：单头与明细行一次提交，总金额由服务端按明细小计重算
  const handleSubmit = async (values) => {
    const validItems = items.filter(item => item.skuCode && item.productName && item.quantity > 0 && item.unitPrice > 0);
    if (validItems.length !== items.length) {
      message.error('请完整填写每一行采购项的 SKU编码、产品名称、数量，单价必须大于 0');
      return;
    }
    const duplicated = validItems
      .map(item => item.skuCode.trim().toLowerCase())
      .filter((sku, index, all) => all.indexOf(sku) !== index);
    if (duplicated.length > 0) {
      message.error(`同一张采购单内 SKU 不能重复：${[...new Set(duplicated)].join('、').toUpperCase()}`);
      return;
    }

    setLoading(true);
    try {
      // 不传 tenantId：由后端从 JWT 租户上下文解析，传错值会被 TenantIsolationAspect 判越权
      const created = await purchaseApi.createPurchaseOrder({
        supplierId: values.supplierId,
        supplierName: values.supplierName,
        warehouseId: values.warehouseId,
        items: validItems.map(item => ({
          skuCode: item.skuCode.trim(),
          productName: item.productName.trim(),
          quantity: item.quantity,
          unitPrice: item.unitPrice
        }))
      });
      message.success(`采购单 ${created?.purchaseOrderId || ''} 创建成功`);
      navigate('/purchase');
    } catch (error) {
      // 业务失败（400/404）已由响应拦截器提示具体原因，例如仓库不属于当前租户
      console.error('创建采购记录失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 采购物品列配置
  const columns = [
    {
      title: 'SKU编码',
      dataIndex: 'skuCode',
      key: 'skuCode',
      render: (_, record) => (
        <Input
          value={record.skuCode}
          onChange={(e) => updateItem(record.key, { skuCode: e.target.value })}
          placeholder="请输入SKU编码"
        />
      ),
    },
    {
      title: '产品名称',
      dataIndex: 'productName',
      key: 'productName',
      render: (_, record) => (
        <Input
          value={record.productName}
          onChange={(e) => updateItem(record.key, { productName: e.target.value })}
          placeholder="请输入产品名称"
        />
      ),
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 120,
      render: (_, record) => (
        <InputNumber
          value={record.quantity}
          onChange={(value) => updateItem(record.key, { quantity: value })}
          min={1}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      width: 140,
      render: (_, record) => (
        <InputNumber
          value={record.unitPrice}
          onChange={(value) => updateItem(record.key, { unitPrice: value })}
          min={0.01}
          step={0.01}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: '总价',
      key: 'totalPrice',
      width: 120,
      render: (_, record) => {
        const totalPrice = (record.quantity || 0) * (record.unitPrice || 0);
        return `¥${totalPrice.toFixed(2)}`;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Button danger icon={<DeleteOutlined />} disabled={items.length === 1} onClick={() => removeItem(record.key)}>
          删除
        </Button>
      ),
    },
  ];

  const totalAmount = items.reduce((sum, item) => sum + (item.quantity || 0) * (item.unitPrice || 0), 0);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>创建采购记录</Title>
        </div>
      </div>

      <Card>
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            {/* 基本信息 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>基本信息</Title>
            </div>

            <Form.Item
              label="供应商ID"
              name="supplierId"
              rules={[{ required: true, message: '请输入供应商ID' }]}
            >
              <Input placeholder="请输入供应商ID" />
            </Form.Item>

            <Form.Item
              label="供应商名称"
              name="supplierName"
              rules={[{ required: true, message: '请输入供应商名称' }]}
            >
              <Input placeholder="请输入供应商名称" />
            </Form.Item>

            <Form.Item
              label="收货仓库"
              name="warehouseId"
              rules={[{ required: true, message: '请选择收货仓库' }]}
            >
              <Select placeholder="请选择收货仓库" showSearch optionFilterProp="children">
                {warehouses.map(warehouse => (
                  <Option key={warehouse.warehouseId} value={warehouse.warehouseId}>
                    {warehouse.warehouseName}（{warehouse.warehouseId}）
                  </Option>
                ))}
              </Select>
            </Form.Item>

            {/* 采购物品 */}
            <div style={{ gridColumn: 'span 2' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
                <Title level={5}>采购物品</Title>
                <Space>
                  <span>预估总金额：¥{totalAmount.toFixed(2)}</span>
                  <Button type="primary" icon={<PlusOutlined />} onClick={addItem}>
                    添加物品
                  </Button>
                </Space>
              </div>
              <Table
                dataSource={items}
                columns={columns}
                rowKey="key"
                pagination={false}
              />
            </div>

            {/* 提交按钮 */}
            <div style={{ gridColumn: 'span 2', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>取消</Button>
              <Button type="primary" icon={<PlusOutlined />} htmlType="submit" loading={loading}>
                创建采购记录
              </Button>
            </div>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default CreatePurchase;
