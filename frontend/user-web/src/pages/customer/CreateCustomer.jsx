import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Form, Input, Select, InputNumber, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { customerApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const CreateCustomer = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  // 客户类型选项
  const customerTypeOptions = [
    { value: 'INDIVIDUAL', label: '个人客户' },
    { value: 'BUSINESS', label: '企业客户' },
    { value: 'WHOLESALE', label: '批发客户' }
  ];

  // 客户等级选项
  const customerLevelOptions = [
    { value: 'VIP', label: 'VIP' },
    { value: 'GOLD', label: '金牌' },
    { value: 'SILVER', label: '银牌' },
    { value: 'BRONZE', label: '铜牌' }
  ];

  // 返回列表页
  const handleBack = () => {
    navigate('/customers');
  };

  // 提交表单
  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      // 实际应该调用后端API创建客户
      console.log('创建客户:', values);
      // 模拟API调用成功
      setTimeout(() => {
        message.success('客户创建成功');
        navigate('/customers');
      }, 1000);
    } catch (error) {
      console.error('创建客户失败:', error);
      message.error('创建客户失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>创建客户</Title>
        </div>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            type: 'INDIVIDUAL',
            level: 'BRONZE'
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            {/* 基本信息 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>基本信息</Title>
            </div>
            
            <Form.Item
              label="客户名称"
              name="customerName"
              rules={[{ required: true, message: '请输入客户名称' }]}
            >
              <Input placeholder="请输入客户名称" />
            </Form.Item>
            
            <Form.Item
              label="联系人"
              name="contactPerson"
              rules={[{ required: true, message: '请输入联系人' }]}
            >
              <Input placeholder="请输入联系人" />
            </Form.Item>
            
            <Form.Item
              label="联系电话"
              name="phone"
              rules={[{ required: true, message: '请输入联系电话' }]}
            >
              <Input placeholder="请输入联系电话" />
            </Form.Item>
            
            <Form.Item
              label="邮箱"
              name="email"
              rules={[{ required: true, message: '请输入邮箱' }]}
            >
              <Input placeholder="请输入邮箱" />
            </Form.Item>
            
            <Form.Item
              label="地址"
              name="address"
              rules={[{ required: true, message: '请输入地址' }]}
              style={{ gridColumn: 'span 2' }}
            >
              <Input.TextArea rows={3} placeholder="请输入地址" />
            </Form.Item>
            
            <Form.Item
              label="客户类型"
              name="type"
              rules={[{ required: true, message: '请选择客户类型' }]}
            >
              <Select placeholder="请选择客户类型">
                {customerTypeOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="客户等级"
              name="level"
              rules={[{ required: true, message: '请选择客户等级' }]}
            >
              <Select placeholder="请选择客户等级">
                {customerLevelOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>

            {/* 提交按钮 */}
            <div style={{ gridColumn: 'span 2', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>取消</Button>
              <Button type="primary" icon={<PlusOutlined />} htmlType="submit" loading={loading}>
                创建客户
              </Button>
            </div>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default CreateCustomer;
