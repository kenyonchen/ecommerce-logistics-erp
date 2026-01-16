import React, { useState } from 'react';
import { Form, Input, Button, Card, message, Select } from 'antd';
import { tenantApi } from '../../services/api';

const { Option } = Select;

const CreateTenant = () => {
  const [loading, setLoading] = useState(false);

  const onFinish = async (values) => {
    setLoading(true);
    try {
      const response = await tenantApi.createTenant(values);
      message.success('租户创建成功');
      console.log('Tenant created:', response);
      // 可以在这里添加跳转逻辑
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

        <Form.Item
          label="状态"
          name="status"
          initialValue="ACTIVE"
        >
          <Select>
            <Option value="ACTIVE">激活</Option>
            <Option value="INACTIVE">未激活</Option>
            <Option value="SUSPENDED">暂停</Option>
          </Select>
        </Form.Item>

        <Form.Item wrapperCol={{ offset: 4, span: 16 }}>
          <Button type="primary" htmlType="submit" loading={loading}>
            提交
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default CreateTenant;