import React, { useState } from 'react';
import { Button, Card, message, Form, Input, InputNumber, Select, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { orderApi } from '../../services/api';
import { ErrorHandler, withErrorHandling } from '../../services/errorHandler.jsx';
import { formatAmount } from '../../constants/order';

const { Title } = Typography;
const { Option } = Select;

/**
 * 创建订单页。
 * 提交结构需与后端 CreateOrderDto 完全一致：
 * orderItems: [{ productName, skuCode, quantity, unitPrice, totalPrice }]
 * shippingAddress: { recipient, phone, country, province, city, street, zipCode }
 * paymentInfo: { paymentMethod, paymentStatus, transactionId, amount }
 */
const CreateOrder = () => {
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  const navigate = useNavigate();

  // 使用错误处理装饰器包装异步函数
  const submitOrder = withErrorHandling(async (values) => {
    setLoading(true);
    try {
      const items = (values.orderItems || []).map((item) => ({
        productName: item.productName,
        skuCode: item.skuCode,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        totalPrice: Number((item.unitPrice * item.quantity).toFixed(2))
      }));
      const totalAmount = items.reduce((sum, item) => sum + item.totalPrice, 0);

      const orderData = {
        orderNumber: values.orderNumber,
        customerId: values.customerId,
        customerName: values.customerName,
        customerEmail: values.customerEmail,
        orderItems: items,
        shippingAddress: {
          recipient: values.recipient,
          phone: values.phone,
          country: values.country,
          province: values.province,
          city: values.city,
          street: values.street,
          zipCode: values.zipCode
        },
        paymentInfo: {
          paymentMethod: values.paymentMethod,
          paymentStatus: 'PENDING',
          transactionId: values.transactionId || `TXN-${Date.now()}`,
          amount: Number(totalAmount.toFixed(2))
        }
      };

      const result = await orderApi.createOrder(orderData);
      message.success(`订单创建成功：${result?.orderNumber || ''}`);
      navigate(`/orders/${result?.orderId}`);
    } finally {
      setLoading(false);
    }
  });

  const onFinish = (values) => {
    submitOrder(values).catch((error) => {
      // 错误已经被withErrorHandling处理
      console.error('Submit failed:', error);
    });
  };

  const onFinishFailed = (errorInfo) => {
    ErrorHandler.handleFormError(errorInfo.errorFields.reduce((acc, field) => {
      acc[field.name.join('.')] = field.errors[0];
      return acc;
    }, {}));
  };

  // 计算订单总额
  const items = Form.useWatch('orderItems', form) || [];
  const totalAmount = items.reduce(
    (sum, item) => sum + (item?.unitPrice && item?.quantity ? item.unitPrice * item.quantity : 0),
    0
  );

  return (
    <div>
      <Title level={4}>创建订单</Title>
      <Card>
        <Form
          form={form}
          name="createOrder"
          initialValues={{ paymentMethod: 'CREDIT_CARD' }}
          onFinish={onFinish}
          onFinishFailed={onFinishFailed}
          layout="vertical"
          style={{ maxWidth: 960 }}
        >
          <Card type="inner" title="基本信息" style={{ marginBottom: 16 }}>
            <Form.Item label="订单编号" name="orderNumber" extra="留空时系统自动生成">
              <Input placeholder="如 ORD-20261001-001" allowClear />
            </Form.Item>
            <Form.Item
              label="客户ID"
              name="customerId"
              rules={[{ required: true, message: '请输入客户ID' }]}
            >
              <Input allowClear />
            </Form.Item>
            <Form.Item label="客户名称" name="customerName">
              <Input allowClear />
            </Form.Item>
            <Form.Item
              label="客户邮箱"
              name="customerEmail"
              rules={[{ type: 'email', message: '邮箱格式不正确' }]}
            >
              <Input allowClear />
            </Form.Item>
          </Card>

          <Card type="inner" title="订单商品" style={{ marginBottom: 16 }}>
            <Form.List name="orderItems" initialValue={[{}]}>
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field) => (
                    <Card
                      key={field.key}
                      type="inner"
                      size="small"
                      title={`商品 ${field.name + 1}`}
                      extra={
                        fields.length > 1 && (
                          <Button
                            type="link"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() => remove(field.name)}
                          >
                            删除
                          </Button>
                        )
                      }
                      style={{ marginBottom: 12 }}
                    >
                      <Form.Item
                        label="商品名称"
                        name={[field.name, 'productName']}
                        rules={[{ required: true, message: '请输入商品名称' }]}
                      >
                        <Input allowClear />
                      </Form.Item>
                      <Form.Item
                        label="SKU编码"
                        name={[field.name, 'skuCode']}
                        rules={[{ required: true, message: '请输入SKU编码' }]}
                      >
                        <Input allowClear />
                      </Form.Item>
                      <Form.Item
                        label="数量"
                        name={[field.name, 'quantity']}
                        rules={[{ required: true, message: '请输入数量' }]}
                      >
                        <InputNumber min={1} precision={0} style={{ width: '100%' }} />
                      </Form.Item>
                      <Form.Item
                        label="单价"
                        name={[field.name, 'unitPrice']}
                        rules={[{ required: true, message: '请输入单价' }]}
                      >
                        <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
                      </Form.Item>
                    </Card>
                  ))}
                  <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add()}>
                    添加商品
                  </Button>
                </>
              )}
            </Form.List>
          </Card>

          <Card type="inner" title="收货地址" style={{ marginBottom: 16 }}>
            <Form.Item
              label="收件人"
              name="recipient"
              rules={[{ required: true, message: '请输入收件人' }]}
            >
              <Input allowClear />
            </Form.Item>
            <Form.Item
              label="联系电话"
              name="phone"
              rules={[{ required: true, message: '请输入联系电话' }]}
            >
              <Input allowClear />
            </Form.Item>
            <Form.Item
              label="国家"
              name="country"
              rules={[{ required: true, message: '请输入国家' }]}
            >
              <Input allowClear />
            </Form.Item>
            <Form.Item
              label="省/州"
              name="province"
              rules={[{ required: true, message: '请输入省/州' }]}
            >
              <Input allowClear />
            </Form.Item>
            <Form.Item
              label="城市"
              name="city"
              rules={[{ required: true, message: '请输入城市' }]}
            >
              <Input allowClear />
            </Form.Item>
            <Form.Item
              label="详细地址"
              name="street"
              rules={[{ required: true, message: '请输入详细地址' }]}
            >
              <Input allowClear />
            </Form.Item>
            <Form.Item
              label="邮编"
              name="zipCode"
              rules={[{ required: true, message: '请输入邮编' }]}
            >
              <Input allowClear />
            </Form.Item>
          </Card>

          <Card type="inner" title="付款信息" style={{ marginBottom: 16 }}>
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
            <Form.Item label="交易号" name="transactionId" extra="留空时系统自动生成">
              <Input allowClear />
            </Form.Item>
            <div style={{ marginBottom: 24 }}>
              订单总额：<strong>{formatAmount(totalAmount)}</strong>
            </div>
          </Card>

          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading}>
              创建订单
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default CreateOrder;
