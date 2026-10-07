import React, { useState } from 'react';
import { Form, Input, Button, Card, message } from 'antd';
import { tenantApi } from '../../services/api';
import { useNavigate } from 'react-router-dom';

const CreateTenant = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const onFinish = async (values) => {
    setLoading(true);
    try {
      // 后端 CreateTenantRequest 字段：name/description/contactEmail/contactPhone
      const response = await tenantApi.createTenant({
        name: values.name,
        description: values.description,
        contactEmail: values.contactEmail,
        contactPhone: values.contactPhone
      });
      message.success('租户创建成功');
      // 跳转到租户列表
      navigate('/tenants');
    } catch (error) {
      message.error('租户创建失败: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="创建租户" style={{ maxWidth: 800, margin: '20px auto' }}>
      <Form
        name="createTenant"
        labelCol={{ span: 4 }}
        wrapperCol={{ span: 16 }}
        onFinish={onFinish}
        autoComplete="off"
      >
        <Form.Item
          label="租户名称"
          name="name"
          rules={[{ required: true, message: '请输入租户名称' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="租户描述"
          name="description"
        >
          <Input.TextArea rows={4} />
        </Form.Item>

        <Form.Item
          label="联系邮箱"
          name="contactEmail"
          rules={[
            { required: true, message: '请输入联系邮箱' },
            { type: 'email', message: '请输入有效的邮箱地址' }
          ]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="联系电话"
          name="contactPhone"
        >
          <Input />
        </Form.Item>

        <Form.Item wrapperCol={{ offset: 4, span: 16 }}>
          <Button type="primary" htmlType="submit" loading={loading}>
            提交
          </Button>
          <Button style={{ marginLeft: 8 }} onClick={() => navigate('/tenants')}>
            取消
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default CreateTenant;
