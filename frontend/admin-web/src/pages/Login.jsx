import React, { useState } from 'react';
import { Button, Form, Input, message, Card, Typography, Alert } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../services/api';
import { useAuth } from '../context/AuthContext';
import '../App.css';

const { Title, Paragraph } = Typography;

const Login = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login, logout } = useAuth();

  const onFinish = async (values) => {
    setLoading(true);
    try {
      const response = await authApi.login({
        username: values.username,
        password: values.password
      });
      // 平台管理端仅允许平台管理员。
      // 角色校验必须早于 login()：login() 会把令牌写进 localStorage，
      // 若先落会话再判角色，校验失败时会留下「已登录但被守卫拦成 403」的幽灵会话。
      const roles = response.roles || [];
      if (!roles.includes('PLATFORM_ADMIN')) {
        message.error('该账号不是平台管理员，无法登录平台管理端');
        await logout();
        return;
      }
      login(response);
      message.success('登录成功');
      navigate('/');
    } catch (error) {
      // 错误已在拦截器中提示
      console.log('登录失败：' + (error.message || '请检查用户名和密码'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <Card className="login-card">
        <Title level={2} style={{ textAlign: 'center', marginBottom: 8 }}>
          物流ERP平台管理端
        </Title>
        <Paragraph
          type="secondary"
          style={{ textAlign: 'center', marginBottom: 24 }}
        >
          请使用平台管理员账号登录
        </Paragraph>

        <Alert
          message="平台管理员登录"
          description="本端仅供平台管理员（PLATFORM_ADMIN）使用，用于租户与全局用户管理"
          type="info"
          showIcon
          style={{ marginBottom: 24 }}
        />

        <Form
          name="adminLogin"
          onFinish={onFinish}
          layout="vertical"
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="请输入用户名" />
          </Form.Item>

          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input
              prefix={<LockOutlined />}
              type="password"
              placeholder="请输入密码"
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              style={{ width: '100%', height: 40 }}
            >
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default Login;
