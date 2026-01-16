import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Form, Input, Select, DatePicker, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { logisticsApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const CreateLogistics = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  // 物流渠道选项
  const logisticsChannelOptions = [
    { value: 'FEDEX', label: 'FedEx' },
    { value: 'UPS', label: 'UPS' },
    { value: 'DHL', label: 'DHL' },
    { value: 'SF', label: '顺丰' },
    { value: 'STO', label: '申通' },
    { value: 'YTO', label: '圆通' },
    { value: 'YUNDA', label: '韵达' },
    { value: 'ZTO', label: '中通' }
  ];

  // 物流状态选项
  const logisticsStatusOptions = [
    { value: 'PENDING', label: '待处理' },
    { value: 'PICKED_UP', label: '已揽收' },
    { value: 'IN_TRANSIT', label: '运输中' },
    { value: 'DELIVERED', label: '已送达' }
  ];

  // 返回列表页
  const handleBack = () => {
    navigate('/logistics');
  };

  // 提交表单
  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      // 实际应该调用后端API创建物流单
      console.log('创建物流单:', values);
      // 模拟API调用成功
      setTimeout(() => {
        message.success('物流单创建成功');
        navigate('/logistics');
      }, 1000);
    } catch (error) {
      console.error('创建物流单失败:', error);
      message.error('创建物流单失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>创建物流单</Title>
        </div>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            channel: 'SF',
            status: 'PENDING'
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            {/* 基本信息 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>基本信息</Title>
            </div>
            
            <Form.Item
              label="订单编号"
              name="orderNumber"
              rules={[{ required: true, message: '请输入订单编号' }]}
            >
              <Input placeholder="请输入订单编号" />
            </Form.Item>
            
            <Form.Item
              label="物流渠道"
              name="channel"
              rules={[{ required: true, message: '请选择物流渠道' }]}
            >
              <Select placeholder="请选择物流渠道">
                {logisticsChannelOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="物流状态"
              name="status"
              rules={[{ required: true, message: '请选择物流状态' }]}
            >
              <Select placeholder="请选择物流状态">
                {logisticsStatusOptions.map(option => (
                  <Option key={option.value} value={option.value}>{option.label}</Option>
                ))}
              </Select>
            </Form.Item>
            
            <Form.Item
              label="追踪单号"
              name="trackingNumber"
              rules={[{ required: true, message: '请输入追踪单号' }]}
            >
              <Input placeholder="请输入追踪单号" />
            </Form.Item>
            
            <Form.Item
              label="预计送达时间"
              name="estimatedDeliveryTime"
              rules={[{ required: true, message: '请选择预计送达时间' }]}
            >
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>

            {/* 收货信息 */}
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>收货信息</Title>
            </div>
            
            <Form.Item
              label="收件人"
              name="recipient"
              rules={[{ required: true, message: '请输入收件人' }]}
            >
              <Input placeholder="请输入收件人" />
            </Form.Item>
            
            <Form.Item
              label="联系电话"
              name="recipientPhone"
              rules={[{ required: true, message: '请输入联系电话' }]}
            >
              <Input placeholder="请输入联系电话" />
            </Form.Item>
            
            <Form.Item
              label="收货地址"
              name="shippingAddress"
              rules={[{ required: true, message: '请输入收货地址' }]}
              style={{ gridColumn: 'span 2' }}
            >
              <Input.TextArea rows={3} placeholder="请输入收货地址" />
            </Form.Item>

            {/* 提交按钮 */}
            <div style={{ gridColumn: 'span 2', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>取消</Button>
              <Button type="primary" icon={<PlusOutlined />} htmlType="submit" loading={loading}>
                创建物流单
              </Button>
            </div>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default CreateLogistics;
