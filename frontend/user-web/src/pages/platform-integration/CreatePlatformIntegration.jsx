import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Form, Input, Select, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { platformIntegrationApi } from "../../services/api";

const { Title } = Typography;
const { Option } = Select;

const CreatePlatformIntegration = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  // 平台类型选项
  const platformTypeOptions = [
    { value: 'AMAZON', label: 'Amazon' },
    { value: 'EBAY', label: 'eBay' },
    { value: 'ALIEXPRESS', label: 'AliExpress' },
    { value: 'WISH', label: 'Wish' },
    { value: 'SHOPIFY', label: 'Shopify' },
    { value: 'TAOBAO', label: '淘宝' },
    { value: 'JD', label: '京东' },
    { value: 'PDD', label: '拼多多' }
  ];

  // 同步间隔选项
  const syncIntervalOptions = [
    { value: '1小时', label: '1小时' },
    { value: '6小时', label: '6小时' },
    { value: '12小时', label: '12小时' },
    { value: '24小时', label: '24小时' },
    { value: '48小时', label: '48小时' }
  ];

  // 返回列表页
  const handleBack = () => {
    navigate('/platform-integration');
  };

  // 提交表单
  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      // 实际应该调用后端API创建平台集成
      console.log('创建平台集成:', values);
      // 模拟API调用成功
      setTimeout(() => {
        message.success('平台集成创建成功');
        navigate('/platform-integration');
      }, 1000);
    } catch (error) {
      console.error('创建平台集成失败:', error);
      message.error('创建平台集成失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>创建平台集成</Title>
        </div>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            platformType: 'AMAZON',
            syncInterval: '24小时',
            isEnabled: true
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            {/* 基本信息 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>基本信息</Title>
            </div>
            
            <Form.Item
              label="平台名称"
              name="platformName"
              rules={[{ required: true, message: '请输入平台名称' }]}
            >
              <Input placeholder="请输入平台名称" />
            </Form.Item>
            
            <Form.Item
              label="平台类型"
              name="platformType"
              rules={[{ required: true, message: '请选择平台类型' }]}
            >
              <Select placeholder="请选择平台类型">
                {platformTypeOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="API密钥"
              name="apiKey"
              rules={[{ required: true, message: '请输入API密钥' }]}
            >
              <Input placeholder="请输入API密钥" />
            </Form.Item>
            
            <Form.Item
              label="Secret密钥"
              name="secretKey"
              rules={[{ required: true, message: '请输入Secret密钥' }]}
            >
              <Input placeholder="请输入Secret密钥" />
            </Form.Item>
            
            <Form.Item
              label="Webhook URL"
              name="webhookUrl"
              rules={[{ required: true, message: '请输入Webhook URL' }]}
              style={{ gridColumn: 'span 2' }}
            >
              <Input placeholder="请输入Webhook URL" />
            </Form.Item>

            {/* 同步设置 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>同步设置</Title>
            </div>
            
            <Form.Item
              label="同步间隔"
              name="syncInterval"
              rules={[{ required: true, message: '请选择同步间隔' }]}
            >
              <Select placeholder="请选择同步间隔">
                {syncIntervalOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="启用"
              name="isEnabled"
              valuePropName="checked"
            >
              <input type="checkbox" />
            </Form.Item>

            {/* 提交按钮 */}
            <div style={{ gridColumn: 'span 2', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>取消</Button>
              <Button type="primary" icon={<PlusOutlined />} htmlType="submit" loading={loading}>
                创建平台集成
              </Button>
            </div>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default CreatePlatformIntegration;
