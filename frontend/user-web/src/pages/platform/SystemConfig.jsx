import React, { useState, useEffect, useCallback } from 'react';
import { Card, Form, Input, InputNumber, Button, Switch, Space, message, Tabs } from 'antd';
import { systemConfigApi } from '../../services/api';

// 平台系统配置：与后端 GET/PUT /api/system-config 的 { general, email, security } 嵌套契约一一对应。
// 仅平台管理员（PLATFORM_ADMIN）可访问，路由由 PlatformAdminGuard 守卫。
const SystemConfig = () => {
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  const fetchSystemConfig = useCallback(async () => {
    setLoading(true);
    try {
      const config = await systemConfigApi.getConfig();
      form.setFieldsValue(config);
    } catch {
      // 失败提示由 api 拦截器统一给出，此处不把占位值写回表单
    } finally {
      setLoading(false);
    }
  }, [form]);

  useEffect(() => {
    fetchSystemConfig();
  }, [fetchSystemConfig]);

  const onFinish = async (values) => {
    setLoading(true);
    try {
      // 后端会校验并以同一结构回显，回显值覆盖表单，保证展示与落库一致
      const saved = await systemConfigApi.saveConfig(values);
      form.setFieldsValue(saved);
      message.success('系统配置保存成功');
    } catch {
      // 失败提示由 api 拦截器统一给出，不假装保存成功
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="系统配置" style={{ margin: '20px' }}>
      <Form
        form={form}
        name="systemConfig"
        layout="vertical"
        onFinish={onFinish}
        autoComplete="off"
      >
        {/* forceRender 必须开：Tabs 默认只挂载访问过的面板，未挂载分组的字段不会进 values，
            后端按全量配置校验会回 400 */}
        <Tabs
          defaultActiveKey="general"
          items={[
            {
              key: 'general',
              label: '常规设置',
              forceRender: true,
              children: (
                <>
                  <Form.Item
                    label="站点名称"
                    name={['general', 'siteName']}
                    rules={[{ required: true, message: '请输入站点名称' }]}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item label="站点描述" name={['general', 'siteDescription']}>
                    <Input.TextArea rows={4} />
                  </Form.Item>
                  <Form.Item
                    label="联系邮箱"
                    name={['general', 'contactEmail']}
                    rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item label="联系电话" name={['general', 'contactPhone']}>
                    <Input />
                  </Form.Item>
                </>
              ),
            },
            {
              key: 'email',
              label: '邮件设置',
              forceRender: true,
              children: (
                <>
                  <Form.Item label="SMTP主机" name={['email', 'smtpHost']}>
                    <Input />
                  </Form.Item>
                  <Form.Item
                    label="SMTP端口"
                    name={['email', 'smtpPort']}
                    rules={[
                      { required: true, message: '请输入SMTP端口' },
                      { type: 'number', min: 1, max: 65535, message: 'SMTP端口需在 1-65535 之间' },
                    ]}
                  >
                    <InputNumber min={1} max={65535} precision={0} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item
                    label="发件人邮箱"
                    name={['email', 'senderEmail']}
                    rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item label="启用邮件服务" name={['email', 'enabled']} valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </>
              ),
            },
            {
              key: 'security',
              label: '安全设置',
              forceRender: true,
              children: (
                <>
                  <Form.Item
                    label="会话超时(秒)"
                    name={['security', 'sessionTimeout']}
                    rules={[
                      { required: true, message: '请输入会话超时时间' },
                      { type: 'number', min: 300, max: 604800, message: '会话超时需在 300-604800 秒之间' },
                    ]}
                  >
                    <InputNumber min={300} max={604800} precision={0} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item
                    label="最大登录尝试次数"
                    name={['security', 'maxLoginAttempts']}
                    rules={[
                      { required: true, message: '请输入最大登录尝试次数' },
                      { type: 'number', min: 0, max: 100, message: '取值需在 0-100 之间，0 表示不锁定' },
                    ]}
                  >
                    <InputNumber min={0} max={100} precision={0} style={{ width: '100%' }} />
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
                </>
              ),
            },
          ]}
        />

        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={loading}>
              保存配置
            </Button>
            <Button onClick={fetchSystemConfig}>重置</Button>
          </Space>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default SystemConfig;
