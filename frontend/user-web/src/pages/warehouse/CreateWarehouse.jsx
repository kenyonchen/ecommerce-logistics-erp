import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Typography, Form, Input, InputNumber, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { warehouseApi } from '../../services/api';

const { Title } = Typography;

const CreateWarehouse = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  // 返回列表页
  const handleBack = () => {
    navigate('/warehouses');
  };

  // 提交表单
  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      await warehouseApi.createWarehouse({
        warehouseName: values.warehouseName,
        warehouseCode: values.warehouseCode,
        address: values.address,
        capacity: values.capacity
      });
      message.success('仓库创建成功');
      navigate('/warehouses');
    } catch (error) {
      console.error('创建仓库失败:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>创建仓库</Title>
        </div>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ capacity: 1000 }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, maxWidth: 900 }}>
            <div style={{ gridColumn: 'span 2' }}>
              <Title level={5}>基本信息</Title>
            </div>

            <Form.Item
              label="仓库名称"
              name="warehouseName"
              rules={[{ required: true, message: '请输入仓库名称' }]}
            >
              <Input placeholder="请输入仓库名称" />
            </Form.Item>

            <Form.Item
              label="仓库编码"
              name="warehouseCode"
              rules={[
                { required: false, message: '请输入仓库编码' },
                { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线与中划线' }
              ]}
              extra="留空时系统自动生成"
            >
              <Input placeholder="例如：WH-SZ-001" />
            </Form.Item>

            <Form.Item
              label="总容量"
              name="capacity"
              rules={[{ required: true, message: '请输入总容量' }]}
            >
              <InputNumber style={{ width: '100%' }} placeholder="请输入总容量" min={1} />
            </Form.Item>

            <Form.Item
              label="详细地址"
              name="address"
              rules={[{ required: true, message: '请输入详细地址' }]}
              style={{ gridColumn: 'span 2' }}
            >
              <Input.TextArea rows={3} placeholder="请输入详细地址" />
            </Form.Item>

            <div style={{ gridColumn: 'span 2', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>取消</Button>
              <Button type="primary" icon={<PlusOutlined />} htmlType="submit" loading={loading}>
                创建仓库
              </Button>
            </div>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default CreateWarehouse;
