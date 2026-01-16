import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Form, Input, Select, InputNumber, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { productApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const CreateProduct = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  // 产品分类选项
  const productCategoryOptions = [
    { value: 'ELECTRONICS', label: '电子产品' },
    { value: 'CLOTHING', label: '服装' },
    { value: 'HOME', label: '家居用品' },
    { value: 'BOOKS', label: '图书' },
    { value: 'BEAUTY', label: '美妆' },
    { value: 'SPORTS', label: '运动用品' },
    { value: 'FOOD', label: '食品' },
    { value: 'OTHER', label: '其他' }
  ];

  // 产品状态选项
  const productStatusOptions = [
    { value: 'ACTIVE', label: '激活' },
    { value: 'INACTIVE', label: ' inactive' },
    { value: 'DISCONTINUED', label: '已停产' }
  ];

  // 返回列表页
  const handleBack = () => {
    navigate('/products');
  };

  // 提交表单
  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      // 实际应该调用后端API创建产品
      console.log('创建产品:', values);
      // 模拟API调用成功
      setTimeout(() => {
        message.success('产品创建成功');
        navigate('/products');
      }, 1000);
    } catch (error) {
      console.error('创建产品失败:', error);
      message.error('创建产品失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>创建产品</Title>
        </div>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            category: 'ELECTRONICS',
            status: 'ACTIVE',
            stock: 0,
            weight: 0
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            {/* 基本信息 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>基本信息</Title>
            </div>
            
            <Form.Item
              label="产品名称"
              name="productName"
              rules={[{ required: true, message: '请输入产品名称' }]}
            >
              <Input placeholder="请输入产品名称" />
            </Form.Item>
            
            <Form.Item
              label="SKU码"
              name="skuCode"
              rules={[{ required: true, message: '请输入SKU码' }]}
            >
              <Input placeholder="请输入SKU码" />
            </Form.Item>
            
            <Form.Item
              label="分类"
              name="category"
              rules={[{ required: true, message: '请选择分类' }]}
            >
              <Select placeholder="请选择分类">
                {productCategoryOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="状态"
              name="status"
              rules={[{ required: true, message: '请选择状态' }]}
            >
              <Select placeholder="请选择状态">
                {productStatusOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="价格"
              name="price"
              rules={[{ required: true, message: '请输入价格' }]}
            >
              <InputNumber style={{ width: '100%' }} placeholder="请输入价格" min={0} step={0.01} />
            </Form.Item>
            
            <Form.Item
              label="库存"
              name="stock"
              rules={[{ required: true, message: '请输入库存' }]}
            >
              <InputNumber style={{ width: '100%' }} placeholder="请输入库存" min={0} />
            </Form.Item>
            
            <Form.Item
              label="重量"
              name="weight"
              rules={[{ required: true, message: '请输入重量' }]}
            >
              <InputNumber style={{ width: '100%' }} placeholder="请输入重量" min={0} step={0.1} />
            </Form.Item>
            
            <Form.Item
              label="尺寸"
              name="dimensions"
              rules={[{ required: true, message: '请输入尺寸' }]}
            >
              <Input placeholder="请输入尺寸，例如：15x10x8cm" />
            </Form.Item>

            {/* 产品描述 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>产品描述</Title>
            </div>
            
            <Form.Item
              label="描述"
              name="description"
              rules={[{ required: true, message: '请输入产品描述' }]}
              style={{ gridColumn: 'span 2' }}
            >
              <Input.TextArea rows={4} placeholder="请输入产品描述" />
            </Form.Item>

            {/* 提交按钮 */}
            <div style={{ gridColumn: 'span 2', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>取消</Button>
              <Button type="primary" icon={<PlusOutlined />} htmlType="submit" loading={loading}>
                创建产品
              </Button>
            </div>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default CreateProduct;
