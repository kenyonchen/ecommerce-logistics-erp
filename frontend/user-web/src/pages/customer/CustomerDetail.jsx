import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message
} from 'antd';
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  StopOutlined
} from '@ant-design/icons';
import { customerApi, orderApi } from '../../services/api';
import { platformLabel } from '../../constants/product';
import { customerStatusMap } from '../../constants/customer';

const { Title } = Typography;

/**
 * 客户详情：真实接口拉取档案，支持编辑资料、状态变更、软删除，
 * 并关联展示该客户的历史订单（按客户ID与平台买家ID双查合并）。
 */
const CustomerDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [customer, setCustomer] = useState(null);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const fetchCustomer = useCallback(async () => {
    setLoading(true);
    try {
      const data = await customerApi.getCustomerById(id);
      setCustomer(data);
    } catch {
      // 拦截器已提示错误
    } finally {
      setLoading(false);
    }
  }, [id]);

  const fetchOrders = useCallback(
    async (record) => {
      setLoadingOrders(true);
      try {
        const keys = [record?.customerId, record?.externalBuyerId].filter(Boolean);
        const results = await Promise.all(
          keys.map((key) =>
            orderApi.getOrdersByCustomer(key).catch(() => [])
          )
        );
        const seen = new Set();
        const merged = [];
        results.flat().forEach((order) => {
          const key = order?.orderId || order?.orderNumber;
          if (key && !seen.has(key)) {
            seen.add(key);
            merged.push(order);
          }
        });
        merged.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
        setOrders(merged);
      } catch {
        // 拦截器已提示错误
      } finally {
        setLoadingOrders(false);
      }
    },
    []
  );

  useEffect(() => {
    fetchCustomer();
  }, [fetchCustomer]);

  useEffect(() => {
    if (customer) fetchOrders(customer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customer?.customerId]);

  const openEdit = () => {
    form.setFieldsValue({
      customerName: customer.customerName,
      email: customer.email,
      phone: customer.phone,
      address: customer.address,
      company: customer.company
    });
    setEditModalVisible(true);
  };

  const handleEditSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const updated = await customerApi.updateCustomer(id, {
        customerName: values.customerName,
        email: values.email || undefined,
        phone: values.phone || undefined,
        address: values.address || undefined,
        company: values.company || undefined
      });
      message.success('客户资料已更新');
      setCustomer(updated);
      setEditModalVisible(false);
    } catch {
      // 校验失败或拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  const handleStatusChange = async (action) => {
    try {
      const updated = await customerApi[`${action}Customer`](id);
      setCustomer(updated);
      message.success('状态变更成功');
    } catch {
      // 拦截器已提示错误
    }
  };

  const handleDelete = async () => {
    try {
      await customerApi.deleteCustomer(id);
      message.success('客户已删除（保留历史订单关联）');
      navigate('/customers');
    } catch {
      // 拦截器已提示错误
    }
  };

  const orderColumns = [
    { title: '订单号', dataIndex: 'orderNumber', key: 'orderNumber', width: 180 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (text) => <Tag>{text}</Tag>
    },
    {
      title: '平台',
      dataIndex: 'platform',
      key: 'platform',
      width: 110,
      render: (text) => platformLabel(text)
    },
    {
      title: '订单金额',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 130,
      render: (text, record) =>
        text != null ? `${record.currency || ''} ${Number(text).toFixed(2)}` : '-'
    },
    {
      title: '下单时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) => (
        <Button type="link" onClick={() => navigate(`/orders/${record.orderId || record.id}`)}>
          详情
        </Button>
      )
    }
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin />
      </div>
    );
  }

  if (!customer) {
    return (
      <div>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/customers')}>
          返回列表
        </Button>
        <Title level={4} style={{ marginTop: 16 }}>
          客户不存在或已被删除
        </Title>
      </div>
    );
  }

  const statusTag = customerStatusMap[customer.status] || { text: customer.status, color: 'default' };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/customers')}>
            返回列表
          </Button>
          <Title level={4} style={{ margin: 0 }}>
            客户详情
          </Title>
          <Tag color={statusTag.color}>{customer.statusLabel || statusTag.text}</Tag>
        </Space>
        <Space>
          {customer.status !== 'ACTIVE' && (
            <Button icon={<PlayCircleOutlined />} onClick={() => handleStatusChange('activate')}>
              激活
            </Button>
          )}
          {customer.status === 'ACTIVE' && (
            <>
              <Button icon={<PauseCircleOutlined />} onClick={() => handleStatusChange('deactivate')}>
                置为非活跃
              </Button>
              <Button icon={<StopOutlined />} onClick={() => handleStatusChange('suspend')}>
                暂停
              </Button>
            </>
          )}
          <Button type="primary" icon={<EditOutlined />} onClick={openEdit}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该客户？"
            description="删除为软删除，历史订单仍可查询"
            onConfirm={handleDelete}
            okText="删除"
            cancelText="取消"
          >
            <Button danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      </div>

      <Card style={{ marginBottom: 5 }}>
        <Descriptions title="基本信息" column={2} bordered size="small">
          <Descriptions.Item label="客户ID">{customer.customerId}</Descriptions.Item>
          <Descriptions.Item label="客户名称">{customer.customerName}</Descriptions.Item>
          <Descriptions.Item label="邮箱">{customer.email || '-'}</Descriptions.Item>
          <Descriptions.Item label="联系电话">{customer.phone || '-'}</Descriptions.Item>
          <Descriptions.Item label="公司名称">{customer.company || '-'}</Descriptions.Item>
          <Descriptions.Item label="收货地址" span={2}>
            {customer.address || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="来源平台">
            {customer.sourcePlatform ? platformLabel(customer.sourcePlatform) : '手动创建'}
          </Descriptions.Item>
          <Descriptions.Item label="平台买家ID">{customer.externalBuyerId || '-'}</Descriptions.Item>
          <Descriptions.Item label="注册时间">
            {customer.createdAt ? new Date(customer.createdAt).toLocaleString() : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {customer.updatedAt ? new Date(customer.updatedAt).toLocaleString() : '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="历史订单" style={{ marginBottom: 5 }}>
        <Table
          columns={orderColumns}
          dataSource={orders}
          rowKey={(record) => record.orderId || record.orderNumber}
          loading={loadingOrders}
          size="small"
          pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条` }}
        />
      </Card>

      <Modal
        title="编辑客户资料"
        open={editModalVisible}
        onOk={handleEditSubmit}
        onCancel={() => setEditModalVisible(false)}
        confirmLoading={saving}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="客户名称"
            name="customerName"
            rules={[{ required: true, message: '请输入客户名称' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item label="邮箱" name="email" rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item label="联系电话" name="phone">
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item label="公司名称" name="company">
            <Input maxLength={256} />
          </Form.Item>
          <Form.Item label="收货地址" name="address">
            <Input.TextArea rows={3} maxLength={512} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default CustomerDetail;
