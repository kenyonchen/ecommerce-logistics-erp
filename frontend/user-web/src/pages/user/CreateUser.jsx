import React, { useState } from 'react';
import { Form, Input, Button, Card, message, Select } from 'antd';
import { userApi } from '../../services/api';

const { Option } = Select;

const CreateUser = () => {
  const [loading, setLoading] = useState(false);

  const onFinish = async (values) => {
    setLoading(true);
    try {
      const response = await userApi.createUser(values);
      message.success('用户创建成功');
      console.log('User created:', response);
      // 可以在这里添加跳转逻辑
    } catch (error) {
      message.error('用户创建失败: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="创建用户" style={{ maxWidth: 800, margin: '20px auto' }}>
      <Form
        name="createUser"
        labelCol={{ span: 4 }}
        wrapperCol={{ span: 16 }}
        onFinish={onFinish}
        autoComplete="off"
      >
        <Form.Item
          label="用户名"
          name="username"
          rules={[{ required: true, message: '请输入用户名' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="姓名"
          name="name"
          rules={[{ required: true, message: '请输入姓名' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="邮箱"
          name="email"
          rules={[
            { required: true, message: '请输入邮箱' },
            { type: 'email', message: '请输入有效的邮箱地址' }
          ]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="电话"
          name="phone"
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="角色"
          name="role"
        >
          <Select placeholder="请选择用户角色">
            <Option value="USER">普通用户</Option>
            <Option value="ADMIN">管理员</Option>
            <Option value="TENANT_ADMIN">租户管理员</Option>
            <Option value="PLATFORM_ADMIN">平台管理员</Option>
          </Select>
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

export default CreateUser;