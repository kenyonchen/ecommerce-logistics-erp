import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Typography, Descriptions, Table, Button, Card, Space, Tag, Modal, Select, message } from 'antd';
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined, SyncOutlined } from '@ant-design/icons';
import { orderApi } from '../../services/api';

const { Title, Paragraph } = Typography;
const { Option } = Select;

const OrderDetail = () => {
  const { id } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [statusModalVisible, setStatusModalVisible] = useState(false);
  const [selectedStatus, setSelectedStatus] = useState('');

  // 获取订单详情
  const fetchOrderDetail = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的API
      // 暂时使用模拟数据
      const mockOrder = {
        id,
        orderNumber: `ORD-2024-${String(id).padStart(6, '0')}`,
        customerId: 'customer-001',
        customerName: '张三',
        customerEmail: 'zhangsan@example.com',
        orderStatus: 'PROCESSING',
        totalAmount: 1280.50,
        shippingAddress: {
          recipient: '李四',
          street: '北京市朝阳区建国路88号',
          city: '北京市',
          province: '朝阳区',
          zipCode: '100022',
          country: '中国',
          phone: '13800138000'
        },
        paymentInfo: {
          paymentMethod: 'Credit Card',
          paymentStatus: 'PAID',
          transactionId: 'TXN-2024-000001'
        },
        orderItems: [
          {
            id: 'item-001',
            productId: 'prod-001',
            sku: 'SKU001',
            productName: 'iPhone 15 Pro',
            quantity: 1,
            unitPrice: 999.99,
            totalPrice: 999.99
          },
          {
            id: 'item-002',
            productId: 'prod-002',
            sku: 'SKU002',
            productName: 'AirPods Pro 2',
            quantity: 1,
            unitPrice: 280.51,
            totalPrice: 280.51
          }
        ],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      setOrder(mockOrder);
      setSelectedStatus(mockOrder.orderStatus);
    } catch (error) {
      console.error('获取订单详情失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchOrderDetail();
  }, [id]);

  // 订单状态映射
  const statusMap = {
    PENDING: { text: '待处理', color: 'blue' },
    PROCESSING: { text: '处理中', color: 'processing' },
    SHIPPED: { text: '已发货', color: 'green' },
    COMPLETED: { text: '已完成', color: 'success' },
    CANCELLED: { text: '已取消', color: 'error' }
  };

  // 订单行项表格列
  const columns = [
    {
      title: '产品名称',
      dataIndex: 'productName',
      key: 'productName'
    },
    {
      title: 'SKU',
      dataIndex: 'sku',
      key: 'sku',
      width: 100
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 80
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      width: 100,
      render: (text) => `¥${text.toFixed(2)}`
    },
    {
      title: '小计',
      dataIndex: 'totalPrice',
      key: 'totalPrice',
      width: 100,
      render: (text) => `¥${text.toFixed(2)}`
    }
  ];

  // 处理订单状态变更
  const handleStatusChange = async () => {
    try {
      // 调用后端API更新订单状态
      // await orderApi.updateOrderStatus(id, selectedStatus);
      
      // 模拟更新成功
      setOrder(prev => ({
        ...prev,
        orderStatus: selectedStatus,
        updatedAt: new Date().toISOString()
      }));
      
      message.success('订单状态更新成功');
      setStatusModalVisible(false);
    } catch (error) {
      console.error('更新订单状态失败:', error);
      message.error('更新订单状态失败');
    }
  };

  // 处理订单取消
  const handleCancelOrder = async () => {
    Modal.confirm({
      title: '确认取消订单',
      content: '您确定要取消此订单吗？取消后将无法恢复。',
      onOk: async () => {
        try {
          // 调用后端API取消订单
          // await orderApi.updateOrderStatus(id, 'CANCELLED');
          
          // 模拟取消成功
          setOrder(prev => ({
            ...prev,
            orderStatus: 'CANCELLED',
            updatedAt: new Date().toISOString()
          }));
          
          message.success('订单取消成功');
        } catch (error) {
          console.error('取消订单失败:', error);
          message.error('取消订单失败');
        }
      }
    });
  };

  // 处理订单同步
  const handleSyncOrder = async () => {
    try {
      // 调用后端API同步订单
      message.loading('正在同步订单...');
      
      // 模拟同步成功
      setTimeout(() => {
        message.success('订单同步成功');
      }, 1000);
    } catch (error) {
      console.error('同步订单失败:', error);
      message.error('同步订单失败');
    }
  };

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!order) {
    return <div>订单不存在</div>;
  }

  // 获取可切换的状态选项
  const getAvailableStatusOptions = () => {
    const statusFlow = {
      PENDING: ['PROCESSING', 'CANCELLED'],
      PROCESSING: ['SHIPPED', 'CANCELLED'],
      SHIPPED: ['COMPLETED', 'CANCELLED'],
      COMPLETED: [],
      CANCELLED: []
    };
    
    return statusFlow[order.orderStatus] || [];
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4}>
          <Space>
            <Button type="text" icon={<ArrowLeftOutlined />}><Link to="/orders">返回列表</Link></Button>
            订单详情
          </Space>
        </Title>
        <Space>
          <Button icon={<SyncOutlined />} onClick={handleSyncOrder}>同步订单</Button>
          <Button icon={<EditOutlined />}>编辑</Button>
          <Button danger icon={<DeleteOutlined />}>删除</Button>
        </Space>
      </div>

      {/* 订单基本信息 */}
      <Card style={{ marginBottom: 16 }}>
        <Descriptions column={2} bordered>
          <Descriptions.Item label="订单编号" span={1}>{order.orderNumber}</Descriptions.Item>
          <Descriptions.Item label="订单状态" span={1}>
            <Tag color={statusMap[order.orderStatus].color}>{statusMap[order.orderStatus].text}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="客户ID" span={1}>{order.customerId}</Descriptions.Item>
          <Descriptions.Item label="客户名称" span={1}>{order.customerName}</Descriptions.Item>
          <Descriptions.Item label="客户邮箱" span={1}>{order.customerEmail}</Descriptions.Item>
          <Descriptions.Item label="订单金额" span={1}><strong>¥{order.totalAmount.toFixed(2)}</strong></Descriptions.Item>
          <Descriptions.Item label="创建时间" span={1}>{new Date(order.createdAt).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="更新时间" span={1}>{new Date(order.updatedAt).toLocaleString()}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 收货地址 */}
      <Card title="收货地址" style={{ marginBottom: 16 }}>
        <Descriptions column={1}>
          <Descriptions.Item label="收件人">{order.shippingAddress.recipient}</Descriptions.Item>
          <Descriptions.Item label="联系电话">{order.shippingAddress.phone}</Descriptions.Item>
          <Descriptions.Item label="地址">
            {`${order.shippingAddress.country} ${order.shippingAddress.city} ${order.shippingAddress.province} ${order.shippingAddress.street} ${order.shippingAddress.zipCode}`}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 付款信息 */}
      <Card title="付款信息" style={{ marginBottom: 16 }}>
        <Descriptions column={1}>
          <Descriptions.Item label="付款方式">{order.paymentInfo.paymentMethod}</Descriptions.Item>
          <Descriptions.Item label="付款状态">
            <Tag color={order.paymentInfo.paymentStatus === 'PAID' ? 'success' : 'processing'}>
              {order.paymentInfo.paymentStatus === 'PAID' ? '已支付' : '未支付'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="交易ID">{order.paymentInfo.transactionId}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 订单商品 */}
      <Card title="订单商品" style={{ marginBottom: 16 }}>
        <Table
          columns={columns}
          dataSource={order.orderItems}
          rowKey="id"
          pagination={false}
          scroll={{ x: 600 }}
        />
        <div style={{ textAlign: 'right', marginTop: 16 }}>
          <Paragraph>
            <strong>订单总价：¥{order.totalAmount.toFixed(2)}</strong>
          </Paragraph>
        </div>
      </Card>

      {/* 操作按钮 */}
      <div style={{ textAlign: 'right', marginTop: 24 }}>
        <Space>
          <Button>打印订单</Button>
          <Button>导出订单</Button>
          {order.orderStatus !== 'CANCELLED' && (
            <>
              <Button type="primary" onClick={() => setStatusModalVisible(true)}>更新状态</Button>
              <Button danger onClick={handleCancelOrder}>取消订单</Button>
            </>
          )}
        </Space>
      </div>

      {/* 状态更新弹窗 */}
      <Modal
        title="更新订单状态"
        open={statusModalVisible}
        onOk={handleStatusChange}
        onCancel={() => setStatusModalVisible(false)}
        okText="确定"
        cancelText="取消"
      >
        <div style={{ marginBottom: 16 }}>
          <p>当前状态：<Tag color={statusMap[order.orderStatus].color}>{statusMap[order.orderStatus].text}</Tag></p>
        </div>
        <div>
          <p>选择新状态：</p>
          <Select
            style={{ width: '100%' }}
            value={selectedStatus}
            onChange={setSelectedStatus}
            placeholder="请选择订单状态"
          >
            {getAvailableStatusOptions().map(status => (
              <Option key={status} value={status}>{statusMap[status].text}</Option>
            ))}
          </Select>
        </div>
      </Modal>
    </div>
  );
};

export default OrderDetail;
