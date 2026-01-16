import React, { useState, useEffect } from 'react';
import { Card, Form, Input, Button, Switch, message, Tabs } from 'antd';
import { platformApi } from '../../services/api';

const { TabPane } = Tabs;

const SystemConfig = () => {
  const [config, setConfig] = useState({});
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchSystemConfig();
  }, []);

  const fetchSystemConfig = async () => {
    setLoading(true);
    try {
      // 模拟获取系统配置
      const mockConfig = {
        general: {
          siteName: '物流ERP系统',
          siteDescription: '综合电商物流ERP管理系统',
          contactEmail: 'admin@example.com',
          contactPhone: '400-123-4567'
        },
        email: {
          smtpHost: 'smtp.example.com',
          smtpPort: 587,
          senderEmail: 'noreply@example.com',
          enabled: true
        },
        security: {
          sessionTimeout: 3600,
          maxLoginAttempts: 5,
          twoFactorAuthRequired: false,
          passwordPolicyEnabled: true
        }
      };
      setConfig(mockConfig);
      form.setFieldsValue(mockConfig);
    } catch (error) {
      message.error('获取系统配置失败');
    } finally {
      setLoading(false);
    }
  };

  const onFinish = async (values) => {
    setLoading(true);
    try {
      // 模拟保存配置
      console.log('Saving config:', values);
      message.success('系统配置保存成功');
    } catch (error) {
      message.error('保存系统配置失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="系统配置" style={{ margin: '20px' }}>
      <Form
        form={form}
        name="systemConfig"
        labelCol={{ span: 6 }}
        wrapperCol={{ span: 14 }}
        onFinish={onFinish}
        autoComplete="off"
      >
        <Tabs defaultActiveKey="general" tabPosition="left" style={{ minHeight: 400 }}>
          <TabPane tab="常规设置" key="general">
            <Form.Item
              label="站点名称"
              name={['general', 'siteName']}
              rules={[{ required: true, message: '请输入站点名称' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="站点描述"
              name={['general', 'siteDescription']}
            >
              <Input.TextArea rows={4} />
            </Form.Item>
            <Form.Item
              label="联系邮箱"
              name={['general', 'contactEmail']}
              rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="联系电话"
              name={['general', 'contactPhone']}
            >
              <Input />
            </Form.Item>
          </TabPane>

          <TabPane tab="邮件设置" key="email">
            <Form.Item
              label="SMTP主机"
              name={['email', 'smtpHost']}
              rules={[{ required: true, message: '请输入SMTP主机' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="SMTP端口"
              name={['email', 'smtpPort']}
              rules={[{ required: true, message: '请输入SMTP端口' }]}
            >
              <Input type="number" />
            </Form.Item>
            <Form.Item
              label="发件人邮箱"
              name={['email', 'senderEmail']}
              rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="启用邮件服务"
              name={['email', 'enabled']}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          </TabPane>

          <TabPane tab="安全设置" key="security">
            <Form.Item
              label="会话超时(秒)"
              name={['security', 'sessionTimeout']}
              rules={[{ required: true, message: '请输入会话超时时间' }]}
            >
              <Input type="number" />
            </Form.Item>
            <Form.Item
              label="最大登录尝试次数"
              name={['security', 'maxLoginAttempts']}
              rules={[{ required: true, message: '请输入最大登录尝试次数' }]}
            >
              <Input type="number" />
            </Form.Item>
            <Form.Item
              label="强制双因素认证"
              name={['security', 'twoFactorAuthRequired']}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            <Form.Item
              label="启用密码策略"
              name={['security', 'passwordPolicyEnabled']}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          </TabPane>
        </Tabs>

        <Form.Item wrapperCol={{ offset: 6, span: 14 }}>
          <Button type="primary" htmlType="submit" loading={loading}>
            保存配置
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default SystemConfig;