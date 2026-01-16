import React, { useState } from 'react';
import { Button, Checkbox, Form, Input, message, Card, Typography, Alert } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../services/api';

const { Title } = Typography;

const Login = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const onFinish = async (values) => {
    setLoading(true);
    try {
      const response = await authApi.login({
        username: values.username,
        password: values.password
      });
      
      // 保存token到本地存储
      localStorage.setItem('token', response.token);
      localStorage.setItem('username', values.username);
      
      // 跳转到首页
      message.success('登录成功');
      navigate('/');
    } catch (error) {
      message.error('登录失败：' + (error.message || '请检查用户名和密码'));
    } finally {
      setLoading(false);
    }
  };

  const onFinishFailed = (errorInfo) => {
    message.error('表单验证失败');
  };

  return (
    <div style={{ 
      minHeight: '100vh', 
      display: 'flex', 
      alignItems: 'center', 
      justifyContent: 'center',
      backgroundColor: '#f0f2f5'
    }}>
      <Card style={{ width: 400, boxShadow: '0 4px 12px rgba(0, 0, 0, 0.15)' }}>
        <Title level={2} style={{ textAlign: 'center', marginBottom: 24 }}>
          电商物流ERP系统
        </Title>
        
        <Alert
          message="系统登录"
          description="请使用您的用户名和密码登录系统"
          type="info"
          showIcon
          style={{ marginBottom: 24 }}
        />
        
        <Form
          name="login"
          initialValues={{
            remember: true,
          }}
          onFinish={onFinish}
          onFinishFailed={onFinishFailed}
          layout="vertical"
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              {
                required: true,
                message: '请输入用户名',
              },
            ]}
          >
            <Input prefix={<UserOutlined className="site-form-item-icon" />} placeholder="请输入用户名" />
          </Form.Item>
          
          <Form.Item
            name="password"
            label="密码"
            rules={[
              {
                required: true,
                message: '请输入密码',
              },
            ]}
          >
            <Input
              prefix={<LockOutlined className="site-form-item-icon" />}
              type="password"
              placeholder="请输入密码"
            />
          </Form.Item>
          
          <Form.Item>
            <Form.Item name="remember" valuePropName="checked" noStyle>
              <Checkbox>记住我</Checkbox>
            </Form.Item>
            
            <a className="login-form-forgot" href="#" style={{ float: 'right' }}>
              忘记密码?
            </a>
          </Form.Item>
          
          <Form.Item>
            <Button 
              type="primary" 
              htmlType="submit" 
              className="login-form-button"
              loading={loading}
              style={{ width: '100%', height: 40 }}
            >
              登录
            </Button>
          </Form.Item>
          
          <Form.Item style={{ textAlign: 'center' }}>
            平台管理员请联系系统管理员获取账号 | 租户管理员请使用注册的账号登录
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default Login;