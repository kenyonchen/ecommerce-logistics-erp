import React, { useState } from 'react';
import { Button, message, Form, Input, Select } from 'antd';
import { orderApi } from '../../services/api';
import { ErrorHandler, withErrorHandling } from '../../services/errorHandler.jsx';

const { Option } = Select;

const CreateOrder = () => {
  const [loading, setLoading] = useState(false);

  // 使用错误处理装饰器包装异步函数
  const handleSubmit = withErrorHandling(async (values) => {
    setLoading(true);
    try {
      // 构建订单数据
      const orderData = {
        customerId: values.customerId,
        orderItems: values.orderItems.map(item => ({
          productId: item.productId,
          quantity: item.quantity,
          price: item.price
        })),
        shippingAddress: {
          street: values.street,
          city: values.city,
          state: values.state,
          zipCode: values.zipCode,
          country: values.country
        },
        paymentInfo: {
          paymentMethod: values.paymentMethod,
          paymentStatus: 'PENDING'
        }
      };

      const result = await orderApi.createOrder(orderData);
      message.success('订单创建成功');
      console.log('Order created:', result);
    } finally {
      setLoading(false);
    }
  });

  const onFinish = (values) => {
    handleSubmit(values).catch(error => {
      // 错误已经被withErrorHandling处理
      console.error('Submit failed:', error);
    });
  };

  const onFinishFailed = (errorInfo) => {
    ErrorHandler.handleFormError(errorInfo.errorFields.reduce((acc, field) => {
      acc[field.name[0]] = field.errors[0];
      return acc;
    }, {}));
  };

  return (
    <div style={{ padding: '20px' }}>
      <h2>创建订单</h2>
      <Form
        name="createOrder"
        initialValues={{
          paymentMethod: 'CREDIT_CARD'
        }}
        onFinish={onFinish}
        onFinishFailed={onFinishFailed}
        layout="vertical"
      >
        <Form.Item
          label="客户ID"
          name="customerId"
          rules={[{ required: true, message: '请输入客户ID' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="产品ID"
          name={['orderItems', 0, 'productId']}
          rules={[{ required: true, message: '请输入产品ID' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="数量"
          name={['orderItems', 0, 'quantity']}
          rules={[{ required: true, message: '请输入数量' }]}
        >
          <Input type="number" min={1} />
        </Form.Item>

        <Form.Item
          label="价格"
          name={['orderItems', 0, 'price']}
          rules={[{ required: true, message: '请输入价格' }]}
        >
          <Input type="number" min={0} step={0.01} />
        </Form.Item>

        <Form.Item
          label="街道"
          name="street"
          rules={[{ required: true, message: '请输入街道' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="城市"
          name="city"
          rules={[{ required: true, message: '请输入城市' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="州/省"
          name="state"
          rules={[{ required: true, message: '请输入州/省' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="邮编"
          name="zipCode"
          rules={[{ required: true, message: '请输入邮编' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="国家"
          name="country"
          rules={[{ required: true, message: '请输入国家' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="支付方式"
          name="paymentMethod"
          rules={[{ required: true, message: '请选择支付方式' }]}
        >
          <Select>
            <Option value="CREDIT_CARD">信用卡</Option>
            <Option value="PAYPAL">PayPal</Option>
            <Option value="BANK_TRANSFER">银行转账</Option>
          </Select>
        </Form.Item>

        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading}>
            创建订单
          </Button>
        </Form.Item>
      </Form>
    </div>
  );
};

export default CreateOrder;