import React, { useState, useEffect, useCallback } from 'react';
import { Card, Form, Input, InputNumber, Button, Switch, Space, message, Tabs } from 'antd';
import { systemConfigApi } from '../../services/api';

const SystemConfig = () => {
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  const fetchSystemConfig = useCallback(async () => {
    setLoading(true);
    try {
      const config = await systemConfigApi.getConfig();
      form.setFieldsValue(config);
    } catch {
      // 失败提示由 api 拦截器统一给出，此处不把旧值或占位值写回表单
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
    <div className="page">
      <div className="page__header">
        <div>
          <h1 className="page__title">系统配置</h1>
          <p className="page__subtitle">站点信息、邮件服务与安全策略</p>
        </div>
      </div>

      <Card>
        <Form
          form={form}
          name="systemConfig"
          // 窄屏纵向堆叠，避免 labelCol/wrapperCol 固定栅格把输入框挤窄
          layout="vertical"
          onFinish={onFinish}
          autoComplete="off"
        >
          {/* 标签页改为顶部排列：左侧竖排在窄屏会把表单压到不足 200px 宽 */}
          {/* forceRender 必须开：Tabs 默认只挂载访问过的面板，未挂载分组的字段不会进 values，
              后端按全量配置校验会回 400（例如只看过「安全设置」时报「SMTP端口不能为空」） */}
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
                    <Form.Item
                      label="SMTP主机"
                      name={['email', 'smtpHost']}
                      // 邮件服务未启用时后端允许该项为空，前端强校验会让「改完安全项直接保存」失败
                    >
                      <Input />
                    </Form.Item>
                    <Form.Item
                      label="SMTP端口"
                      name={['email', 'smtpPort']}
                      // 边界与后端 PlatformConfigKey 校验一致，避免提交后才在提示里发现越界
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
                    <Form.Item
                      label="启用邮件服务"
                      name={['email', 'enabled']}
                      valuePropName="checked"
                    >
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
                      // 与后端一致：会话超时会被用作 JWT 有效期，越界值在提交前就拦下
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
    </div>
  );
};

export default SystemConfig;
