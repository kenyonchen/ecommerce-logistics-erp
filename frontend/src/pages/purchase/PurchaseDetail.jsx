import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Descriptions, Tag, message, Table, Modal, Input, Select } from 'antd';
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined, CheckOutlined, InboxOutlined } from '@ant-design/icons';
import { purchaseApi } from "../../services/api";

const { Title } = Typography;
const { Option } = Select;

const PurchaseDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [purchase, setPurchase] = useState(null);
  const [loading, setLoading] = useState(false);
  const [approvalModalVisible, setApprovalModalVisible] = useState(false);
  const [inboundModalVisible, setInboundModalVisible] = useState(false);
  const [approvalStatus, setApprovalStatus] = useState('');
  const [inboundItems, setInboundItems] = useState([]);

  // 采购状态映射
  const statusMap = {
    PENDING: { text: '待处理', color: 'processing' },
    PROCESSING: { text: '处理中', color: 'blue' },
    COMPLETED: { text: '已完成', color: 'success' },
    CANCELLED: { text: '已取消', color: 'default' }
  };

  // 获取采购详情
  const fetchPurchaseDetail = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的详情API
      // 暂时使用模拟数据
      const mockPurchaseDetail = {
        id: id,
        purchaseOrderId: `PO${id.split('-')[1].padStart(8, '0')}`,
        supplier: `供应商${id.split('-')[1]}`,
        status: 'PROCESSING',
        approvalStatus: 'PENDING', // 新增审批状态
        totalAmount: 28500.00,
        expectedDeliveryDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
        actualDeliveryDate: null,
        createdBy: `采购员${id.split('-')[1]}`,
        createdAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
        items: [
          {
            id: `item-1`,
            productId: `PROD-001`,
            productName: '测试产品1',
            quantity: 100,
            unitPrice: 150.00,
            totalPrice: 15000.00
          },
          {
            id: `item-2`,
            productId: `PROD-002`,
            productName: '测试产品2',
            quantity: 50,
            unitPrice: 270.00,
            totalPrice: 13500.00
          }
        ]
      };
      
      setPurchase(mockPurchaseDetail);
      
      // 初始化入库物品数据
      setInboundItems(mockPurchaseDetail.items.map(item => ({
        ...item,
        inboundQuantity: item.quantity,
        warehouse: ''
      })));
    } catch (error) {
      console.error('获取采购详情失败:', error);
      message.error('获取采购详情失败');
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchPurchaseDetail();
  }, [id]);

  // 返回列表页
  const handleBack = () => {
    navigate('/purchase');
  };

  // 编辑采购
  const handleEdit = () => {
    // 实际应该跳转到编辑页面
    message.info('编辑功能开发中');
  };

  // 删除采购
  const handleDelete = () => {
    // 实际应该调用后端API删除
    message.info('删除功能开发中');
  };

  // 处理采购审批
  const handleApproval = () => {
    setApprovalModalVisible(true);
  };

  // 处理审批确认
  const handleApprovalConfirm = () => {
    if (!approvalStatus) {
      message.error('请选择审批状态');
      return;
    }
    
    // 模拟审批成功
    setPurchase(prev => ({
      ...prev,
      approvalStatus,
      status: approvalStatus === 'APPROVED' ? 'PROCESSING' : 'CANCELLED',
      updatedAt: new Date().toISOString()
    }));
    
    message.success('采购审批成功');
    setApprovalModalVisible(false);
  };

  // 处理采购入库
  const handleInbound = () => {
    if (purchase.approvalStatus !== 'APPROVED') {
      message.error('采购单尚未审批通过，无法入库');
      return;
    }
    
    setInboundModalVisible(true);
  };

  // 处理入库确认
  const handleInboundConfirm = () => {
    // 模拟入库成功
    setPurchase(prev => ({
      ...prev,
      status: 'COMPLETED',
      actualDeliveryDate: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }));
    
    message.success('采购入库成功');
    setInboundModalVisible(false);
  };

  // 处理入库数量变化
  const handleInboundQuantityChange = (itemId, value) => {
    setInboundItems(prev => prev.map(item => 
      item.id === itemId ? { ...item, inboundQuantity: parseInt(value) || 0 } : item
    ));
  };

  // 处理仓库选择变化
  const handleWarehouseChange = (itemId, value) => {
    setInboundItems(prev => prev.map(item => 
      item.id === itemId ? { ...item, warehouse: value } : item
    ));
  };

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!purchase) {
    return <div>采购记录不存在</div>;
  }

  // 采购物品列配置
  const columns = [
    {
      title: '物品ID',
      dataIndex: 'id',
      key: 'id',
    },
    {
      title: '产品ID',
      dataIndex: 'productId',
      key: 'productId',
    },
    {
      title: '产品名称',
      dataIndex: 'productName',
      key: 'productName',
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      render: (text) => `¥${text.toFixed(2)}`,
    },
    {
      title: '总价',
      dataIndex: 'totalPrice',
      key: 'totalPrice',
      render: (text) => `¥${text.toFixed(2)}`,
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>采购详情</Title>
        </div>
        <Space>
          {purchase.approvalStatus === 'PENDING' && (
            <Button type="primary" icon={<CheckOutlined />} onClick={handleApproval}>采购审批</Button>
          )}
          {purchase.approvalStatus === 'APPROVED' && purchase.status !== 'COMPLETED' && (
            <Button type="primary" icon={<InboxOutlined />} onClick={handleInbound}>采购入库</Button>
          )}
          <Button icon={<EditOutlined />} onClick={handleEdit}>编辑</Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete}>删除</Button>
        </Space>
      </div>

      {/* 采购基本信息 */}
      <Card style={{ marginBottom: 16 }}>
        <Descriptions title="基本信息" column={2}>
          <Descriptions.Item label="采购ID">{purchase.id}</Descriptions.Item>
          <Descriptions.Item label="采购订单ID">{purchase.purchaseOrderId}</Descriptions.Item>
          <Descriptions.Item label="供应商">{purchase.supplier}</Descriptions.Item>
          <Descriptions.Item label="采购状态">
            <Tag color={statusMap[purchase.status].color}>
              {statusMap[purchase.status].text}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="审批状态">
            <Tag color={
              purchase.approvalStatus === 'APPROVED' ? 'success' : 
              purchase.approvalStatus === 'REJECTED' ? 'error' : 'processing'
            }>
              {purchase.approvalStatus === 'APPROVED' ? '已通过' : 
               purchase.approvalStatus === 'REJECTED' ? '已拒绝' : '待审批'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="总金额">¥{purchase.totalAmount.toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="期望交付日期">{new Date(purchase.expectedDeliveryDate).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="实际交付日期">{purchase.actualDeliveryDate ? new Date(purchase.actualDeliveryDate).toLocaleString() : '未交付'}</Descriptions.Item>
          <Descriptions.Item label="创建人">{purchase.createdBy}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{new Date(purchase.createdAt).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{new Date(purchase.updatedAt).toLocaleString()}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 采购物品列表 */}
      <Card style={{ marginBottom: 16 }}>
        <Title level={5}>采购物品</Title>
        <Table 
          dataSource={purchase.items} 
          columns={columns} 
          rowKey="id" 
          pagination={false}
        />
      </Card>

      {/* 采购审批弹窗 */}
      <Modal
        title="采购审批"
        open={approvalModalVisible}
        onOk={handleApprovalConfirm}
        onCancel={() => setApprovalModalVisible(false)}
        okText="确定"
        cancelText="取消"
      >
        <div style={{ marginBottom: 16 }}>
          <p>采购订单：{purchase.purchaseOrderId}</p>
          <p>供应商：{purchase.supplier}</p>
          <p>总金额：¥{purchase.totalAmount.toFixed(2)}</p>
        </div>
        <div style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>审批状态：</label>
          <Select style={{ width: '100%' }} value={approvalStatus} onChange={setApprovalStatus} placeholder="请选择审批状态">
            <Option value="APPROVED">通过</Option>
            <Option value="REJECTED">拒绝</Option>
          </Select>
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 8 }}>审批意见：</label>
          <Input.TextArea rows={3} placeholder="请输入审批意见" />
        </div>
      </Modal>

      {/* 采购入库弹窗 */}
      <Modal
        title="采购入库"
        open={inboundModalVisible}
        onOk={handleInboundConfirm}
        onCancel={() => setInboundModalVisible(false)}
        okText="确定"
        cancelText="取消"
      >
        <div style={{ marginBottom: 16 }}>
          <p>采购订单：{purchase.purchaseOrderId}</p>
          <p>供应商：{purchase.supplier}</p>
          <p>入库日期：{new Date().toLocaleString()}</p>
        </div>
        <Table
          columns={[
            ...columns,
            {
              title: '入库数量',
              key: 'inboundQuantity',
              width: 100,
              render: (_, record) => {
                const inboundItem = inboundItems.find(item => item.id === record.id);
                return (
                  <Input
                    type="number"
                    min={0}
                    max={record.quantity}
                    value={inboundItem?.inboundQuantity || 0}
                    onChange={(e) => handleInboundQuantityChange(record.id, e.target.value)}
                  />
                );
              }
            },
            {
              title: '入库仓库',
              key: 'warehouse',
              width: 120,
              render: (_, record) => {
                const inboundItem = inboundItems.find(item => item.id === record.id);
                return (
                  <Select style={{ width: '100%' }} value={inboundItem?.warehouse} onChange={(value) => handleWarehouseChange(record.id, value)} placeholder="请选择仓库">
                    <Option value="warehouse-001">仓库1</Option>
                    <Option value="warehouse-002">仓库2</Option>
                    <Option value="warehouse-003">仓库3</Option>
                  </Select>
                );
              }
            }
          ]}
          dataSource={purchase.items}
          rowKey="id"
          pagination={false}
        />
      </Modal>
    </div>
  );
};

export default PurchaseDetail;
