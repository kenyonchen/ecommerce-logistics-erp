import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Form, Input, Select, InputNumber, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { financeApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const CreateFinance = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  // 交易类型选项
  const financeTypeOptions = [
    { value: 'INCOME', label: '收入' },
    { value: 'EXPENSE', label: '支出' },
    { value: 'REFUND', label: '退款' },
    { value: 'TRANSFER', label: '转账' }
  ];

  // 交易状态选项
  const financeStatusOptions = [
    { value: 'PENDING', label: '待处理' },
    { value: 'COMPLETED', label: '已完成' },
    { value: 'FAILED', label: '失败' },
    { value: 'CANCELLED', label: '已取消' }
  ];

  // 支付方式选项
  const paymentMethodOptions = [
    { value: 'ALIPAY', label: '支付宝' },
    { value: 'WECHAT', label: '微信支付' },
    { value: 'BANK', label: '银行转账' },
    { value: 'CASH', label: '现金' }
  ];

  // 返回列表页
  const handleBack = () => {
    navigate('/finance');
  };

  // 提交表单
  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      // 实际应该调用后端API创建财务记录
      console.log('创建财务记录:', values);
      // 模拟API调用成功
      setTimeout(() => {
        message.success('财务记录创建成功');
        navigate('/finance');
      }, 1000);
    } catch (error) {
      console.error('创建财务记录失败:', error);
      message.error('创建财务记录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>创建财务记录</Title>
        </div>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            type: 'INCOME',
            status: 'PENDING',
            paymentMethod: 'ALIPAY'
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            {/* 基本信息 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>基本信息</Title>
            </div>
            
            <Form.Item
              label="交易ID"
              name="transactionId"
              rules={[{ required: true, message: '请输入交易ID' }]}
            >
              <Input placeholder="请输入交易ID" />
            </Form.Item>
            
            <Form.Item
              label="交易类型"
              name="type"
              rules={[{ required: true, message: '请选择交易类型' }]}
            >
              <Select placeholder="请选择交易类型">
                {financeTypeOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="交易金额"
              name="amount"
              rules={[{ required: true, message: '请输入交易金额' }]}
            >
              <InputNumber style={{ width: '100%' }} placeholder="请输入交易金额" min={0} step={0.01} />
            </Form.Item>
            
            <Form.Item
              label="交易状态"
              name="status"
              rules={[{ required: true, message: '请选择交易状态' }]}
            >
              <Select placeholder="请选择交易状态">
                {financeStatusOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="支付方式"
              name="paymentMethod"
              rules={[{ required: true, message: '请选择支付方式' }]}
            >
              <Select placeholder="请选择支付方式">
                {paymentMethodOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="关联订单"
              name="relatedOrderId"
              rules={[{ required: true, message: '请输入关联订单' }]}
            >
              <Input placeholder="请输入关联订单" />
            </Form.Item>

            {/* 交易描述 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>交易描述</Title>
            </div>
            
            <Form.Item
              label="描述"
              name="description"
              rules={[{ required: true, message: '请输入交易描述' }]}
              style={{ gridColumn: 'span 2' }}
            >
              <Input.TextArea rows={4} placeholder="请输入交易描述" />
            </Form.Item>

            {/* 提交按钮 */}
            <div style={{ gridColumn: 'span 2', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>取消</Button>
              <Button type="primary" icon={<PlusOutlined />} htmlType="submit" loading={loading}>
                创建财务记录
              </Button>
            </div>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default CreateFinance;
