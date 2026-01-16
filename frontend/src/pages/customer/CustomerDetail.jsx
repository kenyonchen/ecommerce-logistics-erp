import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Descriptions, Tag, message, Table, Modal, Input, Select, DatePicker } from 'antd';
const { TextArea } = Input;
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined, MessageOutlined, TagOutlined } from '@ant-design/icons';
import { customerApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const CustomerDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [customer, setCustomer] = useState(null);
  const [loading, setLoading] = useState(false);
  const [communicationModalVisible, setCommunicationModalVisible] = useState(false);
  const [categoryModalVisible, setCategoryModalVisible] = useState(false);
  const [communicationData, setCommunicationData] = useState([]);
  const [newCommunication, setNewCommunication] = useState({
    type: 'PHONE',
    content: '',
    date: new Date()
  });

  // 客户类型映射
  const typeMap = {
    INDIVIDUAL: '个人客户',
    BUSINESS: '企业客户',
    WHOLESALE: '批发客户'
  };

  // 客户等级映射
  const levelMap = {
    VIP: { text: 'VIP', color: 'red' },
    GOLD: { text: '金牌', color: 'gold' },
    SILVER: { text: '银牌', color: 'silver' },
    BRONZE: { text: '铜牌', color: 'orange' }
  };

  // 沟通类型映射
  const communicationTypeMap = {
    PHONE: '电话',
    EMAIL: '邮件',
    WECHAT: '微信',
    FACE_TO_FACE: '面对面',
    OTHER: '其他'
  };

  // 获取客户详情
  const fetchCustomerDetail = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的详情API
      // 暂时使用模拟数据
      const mockCustomerDetail = {
        id: id,
        customerName: `客户${id.split('-')[1]}`,
        contactPerson: `联系人${id.split('-')[1]}`,
        phone: `1380013800${id.split('-')[1]}`,
        email: `customer${id.split('-')[1]}@example.com`,
        address: `城市${id.split('-')[1]} 区${id.split('-')[1]} 街道${id.split('-')[1]}号`,
        type: 'INDIVIDUAL',
        level: 'GOLD',
        categories: ['零售客户', 'VIP客户'], // 新增客户分类
        totalOrders: 120,
        totalSpent: 58000,
        lastOrderDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
        createdAt: new Date(Date.now() - 365 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString()
      };
      
      setCustomer(mockCustomerDetail);
      
      // 模拟沟通记录数据
      setCommunicationData([
        {
          id: 'comm-001',
          type: 'PHONE',
          content: '客户咨询产品价格和发货时间',
          date: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
          createdBy: '客服小王'
        },
        {
          id: 'comm-002',
          type: 'EMAIL',
          content: '发送产品报价单给客户',
          date: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(),
          createdBy: '销售小李'
        }
      ]);
    } catch (error) {
      console.error('获取客户详情失败:', error);
      message.error('获取客户详情失败');
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchCustomerDetail();
  }, [id]);

  // 返回列表页
  const handleBack = () => {
    navigate('/customers');
  };

  // 编辑客户
  const handleEdit = () => {
    // 实际应该跳转到编辑页面
    message.info('编辑功能开发中');
  };

  // 删除客户
  const handleDelete = () => {
    // 实际应该调用后端API删除
    message.info('删除功能开发中');
  };

  // 处理客户沟通记录
  const handleCommunicationRecord = () => {
    setCommunicationModalVisible(true);
  };

  // 处理添加沟通记录
  const handleAddCommunication = () => {
    if (!newCommunication.content) {
      message.error('请填写沟通内容');
      return;
    }
    
    // 模拟添加成功
    const newRecord = {
      id: `comm-${Date.now()}`,
      ...newCommunication,
      date: new Date().toISOString(),
      createdBy: '当前用户'
    };
    
    setCommunicationData([newRecord, ...communicationData]);
    message.success('沟通记录添加成功');
    setCommunicationModalVisible(false);
    setNewCommunication({
      type: 'PHONE',
      content: '',
      date: new Date()
    });
  };

  // 处理客户分类管理
  const handleCategoryManagement = () => {
    setCategoryModalVisible(true);
  };

  // 沟通记录表格列
  const communicationColumns = [
    {
      title: '沟通类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (text) => communicationTypeMap[text]
    },
    {
      title: '沟通内容',
      dataIndex: 'content',
      key: 'content'
    },
    {
      title: '沟通日期',
      dataIndex: 'date',
      key: 'date',
      width: 180,
      render: (text) => new Date(text).toLocaleString()
    },
    {
      title: '记录人',
      dataIndex: 'createdBy',
      key: 'createdBy',
      width: 100
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: () => (
        <Button type="link">删除</Button>
      )
    }
  ];

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!customer) {
    return <div>客户不存在</div>;
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>客户详情</Title>
        </div>
        <Space>
          <Button icon={<MessageOutlined />} onClick={handleCommunicationRecord}>沟通记录</Button>
          <Button icon={<TagOutlined />} onClick={handleCategoryManagement}>客户分类</Button>
          <Button type="primary" icon={<EditOutlined />} onClick={handleEdit}>编辑</Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete}>删除</Button>
        </Space>
      </div>

      {/* 客户基本信息 */}
      <Card style={{ marginBottom: 16 }}>
        <Descriptions title="基本信息" column={2}>
          <Descriptions.Item label="客户ID">{customer.id}</Descriptions.Item>
          <Descriptions.Item label="客户名称">{customer.customerName}</Descriptions.Item>
          <Descriptions.Item label="联系人">{customer.contactPerson}</Descriptions.Item>
          <Descriptions.Item label="联系电话">{customer.phone}</Descriptions.Item>
          <Descriptions.Item label="邮箱">{customer.email}</Descriptions.Item>
          <Descriptions.Item label="地址">{customer.address}</Descriptions.Item>
          <Descriptions.Item label="客户类型">{typeMap[customer.type]}</Descriptions.Item>
          <Descriptions.Item label="客户等级">
            <Tag color={levelMap[customer.level].color}>
              {levelMap[customer.level].text}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="客户分类" span={2}>
            <Space>
              {customer.categories.map((category, index) => (
                <Tag key={index}>{category}</Tag>
              ))}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">{new Date(customer.createdAt).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{new Date(customer.updatedAt).toLocaleString()}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 客户购买信息 */}
      <Card style={{ marginBottom: 16 }}>
        <Descriptions title="购买信息" column={2}>
          <Descriptions.Item label="总订单数">{customer.totalOrders}</Descriptions.Item>
          <Descriptions.Item label="总消费金额">¥{customer.totalSpent.toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="最后下单时间">{new Date(customer.lastOrderDate).toLocaleString()}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 客户沟通记录弹窗 */}
      <Modal
        title="客户沟通记录"
        open={communicationModalVisible}
        onCancel={() => setCommunicationModalVisible(false)}
        footer={null}
        width={800}
      >
        <div style={{ marginBottom: 16 }}>
          <Button type="primary" onClick={() => setCommunicationModalVisible(true)}>添加沟通记录</Button>
        </div>
        <Table
          columns={communicationColumns}
          dataSource={communicationData}
          rowKey="id"
          pagination={{ pageSize: 10 }}
        />
        <Modal
          title="添加沟通记录"
          open={communicationModalVisible}
          onOk={handleAddCommunication}
          onCancel={() => setCommunicationModalVisible(false)}
          okText="确定"
          cancelText="取消"
        >
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 8 }}>沟通类型：</label>
            <Select style={{ width: '100%' }} value={newCommunication.type} onChange={(value) => setNewCommunication({ ...newCommunication, type: value })}>
              {Object.entries(communicationTypeMap).map(([key, value]) => (
                <Option key={key} value={key}>{value}</Option>
              ))}
            </Select>
          </div>
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 8 }}>沟通内容：</label>
            <TextArea rows={4} value={newCommunication.content} onChange={(e) => setNewCommunication({ ...newCommunication, content: e.target.value })} placeholder="请输入沟通内容" />
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: 8 }}>沟通日期：</label>
            <DatePicker style={{ width: '100%' }} value={newCommunication.date} onChange={(date) => setNewCommunication({ ...newCommunication, date })} />
          </div>
        </Modal>
      </Modal>

      {/* 客户分类管理弹窗 */}
      <Modal
        title="客户分类管理"
        open={categoryModalVisible}
        onCancel={() => setCategoryModalVisible(false)}
        footer={null}
        width={600}
      >
        <div style={{ marginBottom: 16 }}>
          <Button type="primary">添加分类</Button>
        </div>
        <Card>
          <Descriptions title="当前分类" column={1}>
            <Descriptions.Item label="分类列表">
              <Space wrap>
                {customer.categories.map((category, index) => (
                  <Tag key={index} closable>{category}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
          </Descriptions>
        </Card>
        <div style={{ marginTop: 16 }}>
          <Card>
            <Descriptions title="可用分类" column={1}>
              <Descriptions.Item label="分类列表">
                <Space wrap>
                  {['零售客户', '批发客户', 'VIP客户', '新客户', '老客户', '潜在客户'].map((category, index) => (
                    !customer.categories.includes(category) && (
                      <Tag key={index} color="blue" closable>{category}</Tag>
                    )
                  ))}
                </Space>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </div>
      </Modal>
    </div>
  );
};

export default CustomerDetail;
