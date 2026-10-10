import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Form, Input, Select, Space, Typography, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { platformIntegrationApi } from '../../services/api';
import { DEFAULT_TENANT_ID, authTypeOptions, platformOptions } from '../../constants/product';

const { Title } = Typography;

/**
 * 创建平台集成（入参字段与后端 PlatformIntegrationSaveRequest 一致）
 */
const CreatePlatformIntegration = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const authType = Form.useWatch('authType', form) || 'CUSTOM_SHOP';
  const isShopify = authType === 'SHOPIFY';

  const handleSubmit = async (values) => {
    setLoading(true);
    let createdId;
    try {
      const created = await platformIntegrationApi.createIntegration({
        tenantId: DEFAULT_TENANT_ID,
        platformName: values.platformName,
        authType: values.authType,
        apiKey: values.apiKey,
        apiSecret: values.apiSecret,
        accessToken: values.accessToken,
        apiVersion: values.apiVersion,
        storeUrl: values.storeUrl,
        status: 'INACTIVE'
      });
      createdId = created?.id;
      if (values.activateAfterCreate) {
        await platformIntegrationApi.activate(createdId);
      }
      message.success('平台集成创建成功');
      navigate('/platform-integration');
    } catch (error) {
      // 具体失败原因已由 services/api.js 的响应拦截器 toast（它自己注释的口径就是「无需重复弹窗」），
      // 这里再补一条「创建失败」会把原因盖掉；只有阶段信息是拦截器给不出的：
      // 集成已入库、仅「立即启用」失败时若仍提示创建失败，会诱导用户重复建单。
      if (createdId) {
        message.warning('平台集成已创建但未启用，可在列表页重试启用');
      } else if (!error?.notified) {
        message.error('创建平台集成失败');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 8 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/platform-integration')}>
          返回列表
        </Button>
        <Title level={4} style={{ margin: 0 }}>创建平台集成</Title>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ platformName: 'AMAZON', authType: 'CUSTOM_SHOP', activateAfterCreate: true }}
        >
          <Form.Item
            label="电商平台"
            name="platformName"
            rules={[{ required: true, message: '请选择电商平台' }]}
          >
            <Select options={platformOptions} />
          </Form.Item>

          <Form.Item
            label="对接协议"
            name="authType"
            extra="协议决定用哪套适配器外呼（认证方式与接口形状），与「平台」是两回事"
            rules={[{ required: true, message: '请选择对接协议' }]}
          >
            <Select options={authTypeOptions} />
          </Form.Item>

          {isShopify && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              title="Shopify 走 Admin REST API"
              description="需要 App 颁发的 shpat_ 访问令牌，并具备 read_orders / read_products / write_fulfillments；代码按官方文档规范实现并经 fixture 契约测试验证，但尚未用真实店铺凭据演练。"
            />
          )}

          <Form.Item
            label="店铺接口地址(Store URL)"
            name="storeUrl"
            extra={
              isShopify
                ? '形如 https://你的店铺.myshopify.com，不能填后台管理地址 admin.shopify.com'
                : '商品拉取时会被拼接为 {storeUrl}/products'
            }
            rules={[{ required: true, message: '请输入店铺接口地址' }]}
          >
            <Input placeholder={isShopify ? 'https://your-store.myshopify.com' : '如 https://api.example-store.com'} />
          </Form.Item>

          {isShopify ? (
            <>
              <Form.Item
                label="访问令牌(access_token)"
                name="accessToken"
                extra="创建后只以掩码显示，永不在接口出参中回传明文"
                rules={[{ required: true, message: '请输入 Shopify App 的 shpat_ 访问令牌' }]}
              >
                <Input.Password placeholder="shpat_ 开头" autoComplete="off" />
              </Form.Item>

              <Form.Item
                label="接口版本(api_version)"
                name="apiVersion"
                extra="形如 2026-01，必填：全局配置 platform.shopify.api-version 默认留空（Admin API 版本有 12 个月支持窗口，写死会随时间静默失效），所以每条 Shopify 集成都要自己指定"
                rules={[
                  { required: true, message: '请填写 Shopify Admin API 版本，形如 2026-01' },
                  { pattern: /^\d{4}-\d{2}$/, message: '版本格式是「四位年份-两位月份」，如 2026-01' },
                ]}
              >
                <Input placeholder="如 2026-01" />
              </Form.Item>

              <Form.Item label="API Key（可选，仅作运维备注标识）" name="apiKey">
                <Input placeholder="可留空" />
              </Form.Item>
            </>
          ) : (
            <>
              <Form.Item label="API Key" name="apiKey" rules={[{ required: true, message: '请输入API Key' }]}>
                <Input placeholder="平台提供的 API Key" />
              </Form.Item>

              <Form.Item
                label="API Secret"
                name="apiSecret"
                extra="留空表示不配置；创建后只以「已配置/未配置」显示"
              >
                <Input.Password placeholder="平台提供的 API Secret" autoComplete="off" />
              </Form.Item>
            </>
          )}

          <Form.Item label="创建后立即启用" name="activateAfterCreate" valuePropName="checked">
            <input type="checkbox" />
          </Form.Item>

          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button onClick={() => navigate('/platform-integration')}>取消</Button>
            <Button type="primary" htmlType="submit" icon={<PlusOutlined />} loading={loading}>
              创建集成
            </Button>
          </Space>
        </Form>
      </Card>
    </div>
  );
};

export default CreatePlatformIntegration;
