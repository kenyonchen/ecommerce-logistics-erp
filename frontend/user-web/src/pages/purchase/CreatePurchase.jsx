import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Form, Input, Select, InputNumber, message, Table, DatePicker } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { purchaseApi } from "../../services/api";

const { Title } = Typography;
const { Option } = Select;
const { TextArea } = Input;

const CreatePurchase = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState([{ id: 1, productId: '', productName: '', quantity: 1, unitPrice: 0 }]);

  // 采购状态选项
  const purchaseStatusOptions = [
    { value: 'PENDING', label: '待处理' },
    { value: 'PROCESSING', label: '处理中' },
    { value: 'COMPLETED', label: '已完成' },
    { value: 'CANCELLED', label: '已取消' }
  ];

  // 返回列表页
  const handleBack = () => {
    navigate('/purchase');
  };

  // 添加采购物品
  const addItem = () => {
    setItems([...items, { id: items.length + 1, productId: '', productName: '', quantity: 1, unitPrice: 0 }]);
  };

  // 删除采购物品
  const removeItem = (id) => {
    setItems(items.filter(item => item.id !== id));
  };

  // 提交表单
  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      // 计算总金额
      const totalAmount = items.reduce((sum, item) => sum + (item.quantity * item.unitPrice), 0);
      
      // 构建采购记录数据
      const purchaseData = {
        ...values,
        totalAmount,
        items
      };
      
      // 实际应该调用后端API创建采购记录
      console.log('创建采购记录:', purchaseData);
      // 模拟API调用成功
      setTimeout(() => {
        message.success('采购记录创建成功');
        navigate('/purchase');
      }, 1000);
    } catch (error) {
      console.error('创建采购记录失败:', error);
      message.error('创建采购记录失败');
    } finally {
      setLoading(false);
    }
  };

  // 采购物品列配置
  const columns = [
    {
      title: '序号',
      dataIndex: 'id',
      key: 'id',
    },
    {
      title: '产品ID',
      dataIndex: 'productId',
      key: 'productId',
      render: (text, record) => (
        <Input 
          value={record.productId} 
          onChange={(e) => {
            const newItems = items.map(item => 
              item.id === record.id ? { ...item, productId: e.target.value } : item
            );
            setItems(newItems);
          }} 
          placeholder="请输入产品ID"
        />
      ),
    },
    {
      title: '产品名称',
      dataIndex: 'productName',
      key: 'productName',
      render: (text, record) => (
        <Input 
          value={record.productName} 
          onChange={(e) => {
            const newItems = items.map(item => 
              item.id === record.id ? { ...item, productName: e.target.value } : item
            );
            setItems(newItems);
          }} 
          placeholder="请输入产品名称"
        />
      ),
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
      render: (text, record) => (
        <InputNumber 
          value={record.quantity} 
          onChange={(value) => {
            const newItems = items.map(item => 
              item.id === record.id ? { ...item, quantity: value } : item
            );
            setItems(newItems);
          }} 
          min={1}
        />
      ),
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      render: (text, record) => (
        <InputNumber 
          value={record.unitPrice} 
          onChange={(value) => {
            const newItems = items.map(item => 
              item.id === record.id ? { ...item, unitPrice: value } : item
            );
            setItems(newItems);
          }} 
          min={0}
          step={0.01}
        />
      ),
    },
    {
      title: '总价',
      dataIndex: 'totalPrice',
      key: 'totalPrice',
      render: (text, record) => {
        const totalPrice = record.quantity * record.unitPrice;
        return `¥${totalPrice.toFixed(2)}`;
      },
    },
    {
      title: '操作',
      key: 'action',
      render: (text, record) => (
        <Button danger icon={<DeleteOutlined />} onClick={() => removeItem(record.id)}>
          删除
        </Button>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>创建采购记录</Title>
        </div>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            status: 'PENDING'
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            {/* 基本信息 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>基本信息</Title>
            </div>
            
            <Form.Item
              label="采购订单ID"
              name="purchaseOrderId"
              rules={[{ required: true, message: '请输入采购订单ID' }]}
            >
              <Input placeholder="请输入采购订单ID" />
            </Form.Item>
            
            <Form.Item
              label="供应商"
              name="supplier"
              rules={[{ required: true, message: '请输入供应商' }]}
            >
              <Input placeholder="请输入供应商" />
            </Form.Item>
            
            <Form.Item
              label="采购状态"
              name="status"
              rules={[{ required: true, message: '请选择采购状态' }]}
            >
              <Select placeholder="请选择采购状态">
                {purchaseStatusOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="期望交付日期"
              name="expectedDeliveryDate"
              rules={[{ required: true, message: '请选择期望交付日期' }]}
            >
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            
            <Form.Item
              label="创建人"
              name="createdBy"
              rules={[{ required: true, message: '请输入创建人' }]}
            >
              <Input placeholder="请输入创建人" />
            </Form.Item>

            {/* 采购物品 */}
            <div style={{ gridColumn: 'span 2' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
                <Title level={5}>采购物品</Title>
                <Button type="primary" icon={<PlusOutlined />} onClick={addItem}>
                  添加物品
                </Button>
              </div>
              <Table 
                dataSource={items} 
                columns={columns} 
                rowKey="id" 
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
