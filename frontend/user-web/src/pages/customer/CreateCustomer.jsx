import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { customerApi } from '../../services/api';
import { DEFAULT_TENANT_ID } from '../../constants/product';

const { Title } = Typography;

/**
 * 手动创建客户：字段与后端 CustomerCreateRequest 对齐（名称必填，邮箱需合法且全局唯一）。
 */
const CreateCustomer = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      const created = await customerApi.createCustomer({
        tenantId: DEFAULT_TENANT_ID,
        customerName: values.customerName,
        email: values.email || undefined,
        phone: values.phone || undefined,
        address: values.address || undefined,
        company: values.company || undefined
      });
      message.success(`客户创建成功：${created?.customerId || ''}`);
      navigate('/customers');
    } catch {
      // 拦截器已提示错误
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 5 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/customers')}>
          返回列表
        </Button>
        <Title level={4} style={{ margin: 0 }}>创建客户</Title>
      </div>

      <Card>
        <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ maxWidth: 720 }}>
          <Form.Item
            label="客户名称"
            name="customerName"
            rules={[{ required: true, message: '请输入客户名称' }]}
          >
            <Input placeholder="请输入客户名称" maxLength={64} />
          </Form.Item>

          <Form.Item
            label="邮箱"
            name="email"
            tooltip="可选；平台买家常无邮箱。填写时需格式合法且系统中不重复"
            rules={[{ type: 'email', message: '邮箱格式不正确' }]}
          >
            <Input placeholder="请输入邮箱" maxLength={128} />
          </Form.Item>

          <Form.Item label="联系电话" name="phone">
            <Input placeholder="请输入联系电话" maxLength={32} />
          </Form.Item>

          <Form.Item label="公司名称" name="company">
            <Input placeholder="请输入公司名称" maxLength={256} />
          </Form.Item>

          <Form.Item label="收货地址" name="address">
            <Input.TextArea rows={3} placeholder="请输入收货地址" maxLength={512} />
          </Form.Item>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/customers')}>
              取消
            </Button>
            <Button type="primary" icon={<PlusOutlined />} htmlType="submit" loading={loading}>
              创建客户
            </Button>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default CreateCustomer;
