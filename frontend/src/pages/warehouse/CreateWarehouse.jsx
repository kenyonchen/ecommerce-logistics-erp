import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Form, Input, Select, InputNumber, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { warehouseApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const CreateWarehouse = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

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

  // 返回列表页
  const handleBack = () => {
    navigate('/warehouses');
  };

  // 提交表单
  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      // 实际应该调用后端API创建仓库
      console.log('创建仓库:', values);
      // 模拟API调用成功
      setTimeout(() => {
        message.success('仓库创建成功');
        navigate('/warehouses');
      }, 1000);
    } catch (error) {
      console.error('创建仓库失败:', error);
      message.error('创建仓库失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>创建仓库</Title>
        </div>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            type: 'PRIMARY',
            status: 'ACTIVE',
            capacity: 0,
            usage: 0
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            {/* 基本信息 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>基本信息</Title>
            </div>
            
            <Form.Item
              label="仓库名称"
              name="warehouseName"
              rules={[{ required: true, message: '请输入仓库名称' }]}
            >
              <Input placeholder="请输入仓库名称" />
            </Form.Item>
            
            <Form.Item
              label="位置"
              name="location"
              rules={[{ required: true, message: '请输入位置' }]}
            >
              <Input placeholder="请输入位置" />
            </Form.Item>
            
            <Form.Item
              label="类型"
              name="type"
              rules={[{ required: true, message: '请选择类型' }]}
            >
              <Select placeholder="请选择类型">
                {warehouseTypeOptions.map(option => (
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
                {warehouseStatusOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="管理员"
              name="manager"
              rules={[{ required: true, message: '请输入管理员' }]}
            >
              <Input placeholder="请输入管理员" />
            </Form.Item>
            
            <Form.Item
              label="联系电话"
              name="contact"
              rules={[{ required: true, message: '请输入联系电话' }]}
            >
              <Input placeholder="请输入联系电话" />
            </Form.Item>

            {/* 容量信息 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>容量信息</Title>
            </div>
            
            <Form.Item
              label="总容量"
              name="capacity"
              rules={[{ required: true, message: '请输入总容量' }]}
            >
              <InputNumber style={{ width: '100%' }} placeholder="请输入总容量" min={0} />
            </Form.Item>
            
            <Form.Item
              label="已使用"
              name="usage"
              rules={[{ required: true, message: '请输入已使用容量' }]}
            >
              <InputNumber style={{ width: '100%' }} placeholder="请输入已使用容量" min={0} />
            </Form.Item>

            {/* 地址和描述 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>地址和描述</Title>
            </div>
            
            <Form.Item
              label="详细地址"
              name="address"
              rules={[{ required: true, message: '请输入详细地址' }]}
              style={{ gridColumn: 'span 2' }}
            >
              <Input.TextArea rows={3} placeholder="请输入详细地址" />
            </Form.Item>
            
            <Form.Item
              label="描述"
              name="description"
              rules={[{ required: true, message: '请输入描述' }]}
              style={{ gridColumn: 'span 2' }}
            >
              <Input.TextArea rows={3} placeholder="请输入描述" />
            </Form.Item>

            {/* 提交按钮 */}
            <div style={{ gridColumn: 'span 2', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>取消</Button>
              <Button type="primary" icon={<PlusOutlined />} htmlType="submit" loading={loading}>
                创建仓库
              </Button>
            </div>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default CreateWarehouse;
