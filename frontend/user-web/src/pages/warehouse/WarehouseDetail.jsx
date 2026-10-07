import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Button, Space, Card, Typography, Descriptions, Tag, message, Table, Modal, Input, InputNumber, Select, Form, Popconfirm
} from 'antd';
import {
  ArrowLeftOutlined, EditOutlined, DeleteOutlined, InboxOutlined, EnvironmentOutlined,
  PlusOutlined, ImportOutlined, ExportOutlined
} from '@ant-design/icons';
import { warehouseApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const WarehouseDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [warehouse, setWarehouse] = useState(null);
  const [loading, setLoading] = useState(false);
  const [inventoryData, setInventoryData] = useState([]);
  const [inventoryModalVisible, setInventoryModalVisible] = useState(false);
  const [locationModalVisible, setLocationModalVisible] = useState(false);
  const [locationAddModalVisible, setLocationAddModalVisible] = useState(false);
  const [inventoryFormModalVisible, setInventoryFormModalVisible] = useState(false);
  const [inventoryFormType, setInventoryFormType] = useState('IN');
  const [inventoryForm] = Form.useForm();
  const [locationForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // 状态映射
  const inventoryStatusMap = {
    NORMAL: { text: '正常', color: 'success' },
    SHORTAGE: { text: '不足', color: 'warning' },
    EXCESS: { text: '过剩', color: 'blue' },
    OUT_OF_STOCK: { text: '缺货', color: 'error' }
  };

  const locationTypeMap = {
    SHELF: '货架',
    PALLET: '托盘',
    BIN: '料箱',
    AREA: '存储区'
  };

  // 获取仓库详情与库存
  const fetchWarehouseDetail = useCallback(async () => {
    setLoading(true);
    try {
      const data = await warehouseApi.getWarehouseById(id);
      setWarehouse(data);
      const inventory = await warehouseApi.getInventory(id);
      setInventoryData(Array.isArray(inventory) ? inventory : []);
    } catch (error) {
      console.error('获取仓库详情失败:', error);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchWarehouseDetail();
  }, [fetchWarehouseDetail]);

  const handleBack = () => navigate('/warehouses');

  // 编辑仓库
  const handleEdit = () => {
    editForm.setFieldsValue({
      warehouseName: warehouse.warehouseName,
      warehouseCode: warehouse.warehouseCode,
      address: warehouse.address,
      capacity: warehouse.capacity
    });
    setEditModalVisible(true);
  };

  const handleEditSubmit = async () => {
    try {
      const values = await editForm.validateFields();
      setSubmitting(true);
      await warehouseApi.updateWarehouse(id, values);
      message.success('仓库更新成功');
      setEditModalVisible(false);
      fetchWarehouseDetail();
    } catch {
      // 表单校验失败或接口错误
    } finally {
      setSubmitting(false);
    }
  };

  // 删除仓库
  const handleDelete = async () => {
    try {
      await warehouseApi.deleteWarehouse(id);
      message.success('仓库删除成功');
      navigate('/warehouses');
    } catch (error) {
      console.error('删除仓库失败:', error);
    }
  };

  // 变更状态
  const handleToggleStatus = async () => {
    try {
      const data = await warehouseApi.changeStatus(id, !warehouse.active);
      message.success(data.active ? '仓库已启用' : '仓库已停用');
      fetchWarehouseDetail();
    } catch (error) {
      console.error('变更状态失败:', error);
    }
  };

  // 添加库位
  const handleAddLocation = async () => {
    try {
      const values = await locationForm.validateFields();
      setSubmitting(true);
      await warehouseApi.addLocation(id, values);
      message.success('库位添加成功');
      setLocationModalVisible(false);
      locationForm.resetFields();
      fetchWarehouseDetail();
    } catch {
      // 表单校验失败或接口错误
    } finally {
      setSubmitting(false);
    }
  };

  // 打开出入库弹窗
  const openInventoryForm = (type) => {
    setInventoryFormType(type);
    inventoryForm.resetFields();
    setInventoryFormModalVisible(true);
  };

  // 提交出入库
  const handleInventorySubmit = async () => {
    try {
      const values = await inventoryForm.validateFields();
      setSubmitting(true);
      const payload = { sku: values.sku, quantity: values.quantity };
      if (inventoryFormType === 'IN') {
        await warehouseApi.inventoryIn(id, payload);
        message.success('入库成功');
      } else {
        await warehouseApi.inventoryOut(id, payload);
        message.success('出库成功');
      }
      setInventoryFormModalVisible(false);
      setInventoryModalVisible(true);
      fetchWarehouseDetail();
    } catch {
      // 表单校验失败或接口错误
    } finally {
      setSubmitting(false);
    }
  };

  // 单条盘点
  const handleCount = async (record) => {
    const actualQuantity = window.prompt(`输入 SKU [${record.sku}] 的实际盘点数量：`, String(record.quantity));
    if (actualQuantity === null) return;
    const parsed = parseInt(actualQuantity, 10);
    if (Number.isNaN(parsed) || parsed < 0) {
      message.error('请输入有效的非负整数');
      return;
    }
    try {
      await warehouseApi.countInventory(id, record.productId || record.sku, parsed);
      message.success('盘点完成');
      fetchWarehouseDetail();
    } catch (error) {
      console.error('盘点失败:', error);
    }
  };

  // 库存表格列
  const inventoryColumns = [
    { title: 'SKU', dataIndex: 'sku', key: 'sku', width: 140 },
    { title: '产品ID', dataIndex: 'productId', key: 'productId', width: 140, render: (t) => t || '-' },
    { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 80 },
    {
      title: '状态',
      dataIndex: 'inventoryStatus',
      key: 'inventoryStatus',
      width: 90,
      render: (text) => {
        const status = inventoryStatusMap[text] || { text, color: 'default' };
        return <Tag color={status.color}>{status.text}</Tag>;
      }
    },
    { title: '库位', dataIndex: 'locationId', key: 'locationId', width: 120, render: (t) => t || '-' },
    {
      title: '最后更新',
      dataIndex: 'lastUpdated',
      key: 'lastUpdated',
      width: 170,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) => <Button type="link" onClick={() => handleCount(record)}>盘点</Button>
    }
  ];

  // 库位表格列
  const locationColumns = [
    { title: '库位编码', dataIndex: 'locationCode', key: 'locationCode', width: 120 },
    {
      title: '类型',
      dataIndex: 'locationType',
      key: 'locationType',
      width: 100,
      render: (text) => locationTypeMap[text] || text
    },
    { title: '容量', dataIndex: 'capacity', key: 'capacity', width: 80 },
    { title: '已占用', dataIndex: 'occupied', key: 'occupied', width: 80 },
    {
      title: '状态',
      dataIndex: 'active',
      key: 'active',
      width: 90,
      render: (active) => (
        <Tag color={active ? 'green' : 'default'}>{active ? '启用' : '停用'}</Tag>
      )
    }
  ];

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!warehouse) {
    return (
      <div style={{ textAlign: 'center', padding: 40 }}>
        <p>仓库不存在</p>
        <Button onClick={handleBack}>返回列表</Button>
      </div>
    );
  }

  const locations = warehouse.locations || [];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>仓库详情</Title>
        </div>
        <Space>
          <Button icon={<InboxOutlined />} onClick={() => setInventoryModalVisible(true)}>库存管理</Button>
          <Button icon={<EnvironmentOutlined />} onClick={() => setLocationModalVisible(true)}>库位管理</Button>
          <Button icon={<EditOutlined />} onClick={handleEdit}>编辑</Button>
          <Button onClick={handleToggleStatus}>{warehouse.active ? '停用' : '启用'}</Button>
          <Popconfirm title="确认删除该仓库？" onConfirm={handleDelete}>
            <Button danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      </div>

      {/* 仓库基本信息 */}
      <Card style={{ marginBottom: 5 }}>
        <Descriptions title="基本信息" column={2}>
          <Descriptions.Item label="仓库ID">{warehouse.warehouseId}</Descriptions.Item>
          <Descriptions.Item label="仓库编码">{warehouse.warehouseCode}</Descriptions.Item>
          <Descriptions.Item label="仓库名称">{warehouse.warehouseName}</Descriptions.Item>
          <Descriptions.Item label="租户ID">{warehouse.tenantId || '-'}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={warehouse.active ? 'success' : 'default'}>{warehouse.active ? '启用' : '停用'}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="总容量">{warehouse.capacity}</Descriptions.Item>
          <Descriptions.Item label="详细地址" span={2}>{warehouse.address || '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {warehouse.createdAt ? new Date(warehouse.createdAt).toLocaleString() : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {warehouse.updatedAt ? new Date(warehouse.updatedAt).toLocaleString() : '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 库存概览 */}
      <Card style={{ marginBottom: 5 }} title={`库存概览（${inventoryData.length} 条记录）`}>
        <Table
          columns={inventoryColumns}
          dataSource={inventoryData}
          rowKey="inventoryId"
          pagination={{ pageSize: 5 }}
          size="small"
        />
      </Card>

      {/* 库存管理弹窗 */}
      <Modal
        title="库存管理"
        open={inventoryModalVisible}
        onCancel={() => setInventoryModalVisible(false)}
        footer={null}
        width={900}
      >
        <div style={{ marginBottom: 8 }}>
          <Space>
            <Button type="primary" icon={<ImportOutlined />} onClick={() => openInventoryForm('IN')}>入库</Button>
            <Button icon={<ExportOutlined />} onClick={() => openInventoryForm('OUT')}>出库</Button>
          </Space>
        </div>
        <Table
          columns={inventoryColumns}
          dataSource={inventoryData}
          rowKey="inventoryId"
          pagination={{ pageSize: 10 }}
          size="small"
        />
      </Modal>

      {/* 出入库弹窗 */}
      <Modal
        title={inventoryFormType === 'IN' ? '库存入库' : '库存出库'}
        open={inventoryFormModalVisible}
        onCancel={() => setInventoryFormModalVisible(false)}
        onOk={handleInventorySubmit}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={inventoryForm} layout="vertical">
          <Form.Item
            label="SKU编码"
            name="sku"
            rules={[{ required: true, message: '请输入SKU编码' }]}
          >
            <Input placeholder="请输入SKU编码" />
          </Form.Item>
          <Form.Item
            label={inventoryFormType === 'IN' ? '入库数量' : '出库数量'}
            name="quantity"
            rules={[{ required: true, message: '请输入数量' }]}
          >
            <InputNumber style={{ width: '100%' }} min={1} placeholder="请输入数量" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 库位管理弹窗 */}
      <Modal
        title="库位管理"
        open={locationModalVisible}
        onCancel={() => setLocationModalVisible(false)}
        footer={null}
        width={800}
      >
        <div style={{ marginBottom: 8 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setLocationAddModalVisible(true)}>添加库位</Button>
        </div>
        <Table
          columns={locationColumns}
          dataSource={locations}
          rowKey="locationId"
          pagination={{ pageSize: 10 }}
          size="small"
        />
      </Modal>

      {/* 编辑仓库弹窗 */}
      <Modal
        title="编辑仓库"
        open={editModalVisible}
        onCancel={() => setEditModalVisible(false)}
        onOk={handleEditSubmit}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Form.Item
            label="仓库名称"
            name="warehouseName"
            rules={[{ required: true, message: '请输入仓库名称' }]}
          >
            <Input placeholder="请输入仓库名称" />
          </Form.Item>
          <Form.Item
            label="仓库编码"
            name="warehouseCode"
            rules={[{ required: true, message: '请输入仓库编码' }]}
          >
            <Input placeholder="请输入仓库编码" />
          </Form.Item>
          <Form.Item
            label="详细地址"
            name="address"
            rules={[{ required: true, message: '请输入详细地址' }]}
          >
            <Input.TextArea rows={2} placeholder="请输入详细地址" />
          </Form.Item>
          <Form.Item
            label="总容量"
            name="capacity"
            rules={[{ required: true, message: '请输入总容量' }]}
          >
            <InputNumber style={{ width: '100%' }} min={1} placeholder="请输入总容量" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 添加库位弹窗 */}
      <Modal
        title="添加库位"
        open={locationAddModalVisible}
        onCancel={() => setLocationAddModalVisible(false)}
        onOk={handleAddLocation}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={locationForm} layout="vertical">
          <Form.Item label="库位编码" name="locationCode" rules={[{ required: true, message: '请输入库位编码' }]}>
            <Input placeholder="例如：A-01-01" />
          </Form.Item>
          <Form.Item label="库位类型" name="locationType" initialValue="SHELF">
            <Select>
              <Option value="SHELF">货架</Option>
              <Option value="PALLET">托盘</Option>
              <Option value="BIN">料箱</Option>
              <Option value="AREA">存储区</Option>
            </Select>
          </Form.Item>
          <Form.Item label="容量" name="capacity" initialValue={100}>
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default WarehouseDetail;
