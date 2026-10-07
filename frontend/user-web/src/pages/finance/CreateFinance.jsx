import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Typography, Form, Input, Select, InputNumber, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { financeApi } from '../../services/api';
import { transactionTypeOptions, currencyOptions } from '../../constants/finance';

const { Title } = Typography;
const { Option } = Select;

const CreateFinance = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  // 返回列表页
  const handleBack = () => {
    navigate('/finance');
  };

  // 提交表单：POST /finance/transactions
  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      await financeApi.createTransaction({
        type: values.type,
        amount: values.amount,
        currency: values.currency,
        referenceId: values.referenceId || undefined,
        referenceType: values.referenceType || undefined,
        description: values.description || undefined
      });
      message.success('财务记录创建成功');
      navigate('/finance');
    } catch (error) {
      console.error('创建财务记录失败:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
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
            currency: 'CNY'
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Form.Item
              label="交易类型"
              name="type"
              rules={[{ required: true, message: '请选择交易类型' }]}
            >
              <Select placeholder="请选择交易类型">
                {transactionTypeOptions.map((option) => (
                  <Option key={option.value} value={option.value}>
                    {option.label}
                  </Option>
                ))}
              </Select>
            </Form.Item>

            <Form.Item
              label="交易金额"
              name="amount"
              rules={[{ required: true, message: '请输入交易金额' }]}
            >
              <InputNumber style={{ width: '100%' }} placeholder="请输入交易金额" min={0} precision={2} step={0.01} />
            </Form.Item>

            <Form.Item
              label="币种"
              name="currency"
              rules={[{ required: true, message: '请选择币种' }]}
            >
              <Select placeholder="请选择币种">
                {currencyOptions.map((option) => (
                  <Option key={option.value} value={option.value}>
                    {option.label}
                  </Option>
                ))}
              </Select>
            </Form.Item>

            <Form.Item
              label="关联单据号"
              name="referenceId"
              rules={[{ required: true, message: '请输入关联单据号' }]}
            >
              <Input placeholder="如订单号" allowClear />
            </Form.Item>

            <Form.Item
              label="关联单据类型"
              name="referenceType"
              rules={[{ required: true, message: '请输入关联单据类型' }]}
            >
              <Input placeholder="如 ORDER / LOGISTICS / PURCHASE" allowClear />
            </Form.Item>

            <Form.Item label="描述" name="description" style={{ gridColumn: 'span 2' }}>
              <Input.TextArea rows={4} placeholder="请输入交易描述（可空）" />
            </Form.Item>

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
