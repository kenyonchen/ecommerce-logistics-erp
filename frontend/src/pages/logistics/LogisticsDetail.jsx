import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Descriptions, Table, Tag, message, Modal, Select, Input } from 'antd';
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined, SyncOutlined, FileTextOutlined, SettingOutlined } from '@ant-design/icons';
import { logisticsApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const LogisticsDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [logisticsOrder, setLogisticsOrder] = useState(null);
  const [loading, setLoading] = useState(false);
  const [trackingModalVisible, setTrackingModalVisible] = useState(false);
  const [channelModalVisible, setChannelModalVisible] = useState(false);
  const [newTrackingInfo, setNewTrackingInfo] = useState({
    time: new Date().toISOString(),
    location: '',
    description: ''
  });

  // 物流状态映射
  const statusMap = {
    PENDING: { text: '待处理', color: 'blue' },
    PICKED_UP: { text: '已揽收', color: 'processing' },
    IN_TRANSIT: { text: '运输中', color: 'orange' },
    DELIVERED: { text: '已送达', color: 'success' },
    FAILED: { text: '配送失败', color: 'error' }
  };

  // 物流渠道映射
  const channelMap = {
    FEDEX: 'FedEx',
    UPS: 'UPS',
    DHL: 'DHL',
    SF: '顺丰',
    STO: '申通',
    YTO: '圆通',
    YUNDA: '韵达',
    ZTO: '中通'
  };

  // 获取物流单详情
  const fetchLogisticsDetail = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的详情API
      // 暂时使用模拟数据
      const mockLogisticsDetail = {
        id: id,
        logisticsNumber: `LOG-${2024}-${String(parseInt(id.split('-')[1])).padStart(6, '0')}`,
        orderNumber: `ORD-${2024}-${String(parseInt(id.split('-')[1]) + 100).padStart(6, '0')}`,
        channel: 'SF',
        status: 'IN_TRANSIT',
        recipient: '张三',
        recipientPhone: '13800138001',
        shippingAddress: '北京市朝阳区建国路88号',
        trackingNumber: `TRK-${Math.floor(Math.random() * 900000) + 100000}`,
        estimatedDeliveryTime: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString(),
        actualDeliveryTime: null,
        createdAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
        // 物流轨迹
        trackingTraces: [
          {
            time: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
            location: '北京市朝阳区',
            description: '快递已揽收'
          },
          {
            time: new Date(Date.now() - 12 * 60 * 60 * 1000).toISOString(),
            location: '北京市',
            description: '快递已发出'
          },
          {
            time: new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString(),
            location: '上海市',
            description: '快递已到达中转站'
          }
        ]
      };
      
      setLogisticsOrder(mockLogisticsDetail);
    } catch (error) {
      console.error('获取物流单详情失败:', error);
      message.error('获取物流单详情失败');
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchLogisticsDetail();
  }, [id]);

  // 返回列表页
  const handleBack = () => {
    navigate('/logistics');
  };

  // 编辑物流单
  const handleEdit = () => {
    // 实际应该跳转到编辑页面
    message.info('编辑功能开发中');
  };

  // 删除物流单
  const handleDelete = () => {
    // 实际应该调用后端API删除
    message.info('删除功能开发中');
  };

  // 处理物流状态跟踪
  const handleTrackStatus = () => {
    message.loading('正在更新物流状态...');
    
    // 模拟更新成功
    setTimeout(() => {
      // 模拟添加新的物流轨迹
      const newTrace = {
        time: new Date().toISOString(),
        location: '上海市',
        description: '快递已到达目的地城市'
      };
      
      setLogisticsOrder(prev => ({
        ...prev,
        trackingTraces: [newTrace, ...prev.trackingTraces],
        updatedAt: new Date().toISOString()
      }));
      
      message.success('物流状态更新成功');
    }, 1000);
  };

  // 处理物流标签生成
  const handleGenerateLabel = () => {
    message.loading('正在生成物流标签...');
    
    // 模拟生成成功
    setTimeout(() => {
      message.success('物流标签生成成功，正在下载...');
      // 实际应该触发文件下载
    }, 1000);
  };

  // 处理物流渠道管理
  const handleManageChannel = () => {
    setChannelModalVisible(true);
  };

  // 处理添加物流轨迹
  const handleAddTrackingTrace = () => {
    if (!newTrackingInfo.location || !newTrackingInfo.description) {
      message.error('请填写完整的物流轨迹信息');
      return;
    }
    
    // 模拟添加成功
    const newTrace = {
      ...newTrackingInfo,
      time: new Date().toISOString()
    };
    
    setLogisticsOrder(prev => ({
      ...prev,
      trackingTraces: [newTrace, ...prev.trackingTraces],
      updatedAt: new Date().toISOString()
    }));
    
    message.success('物流轨迹添加成功');
    setTrackingModalVisible(false);
    setNewTrackingInfo({
      location: '',
      description: ''
    });
  };

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!logisticsOrder) {
    return <div>物流单不存在</div>;
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>物流单详情</Title>
        </div>
        <Space>
          <Button icon={<SyncOutlined />} onClick={handleTrackStatus}>更新物流状态</Button>
          <Button icon={<FileTextOutlined />} onClick={handleGenerateLabel}>生成物流标签</Button>
          <Button icon={<SettingOutlined />} onClick={handleManageChannel}>物流渠道管理</Button>
          <Button type="primary" icon={<EditOutlined />} onClick={handleEdit}>编辑</Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete}>删除</Button>
        </Space>
      </div>

      {/* 物流单基本信息 */}
      <Card style={{ marginBottom: 16 }}>
        <Descriptions title="基本信息" column={2}>
          <Descriptions.Item label="物流单号">{logisticsOrder.logisticsNumber}</Descriptions.Item>
          <Descriptions.Item label="订单编号">{logisticsOrder.orderNumber}</Descriptions.Item>
          <Descriptions.Item label="物流渠道">{channelMap[logisticsOrder.channel]}</Descriptions.Item>
          <Descriptions.Item label="物流状态">
            <Tag color={statusMap[logisticsOrder.status].color}>
              {statusMap[logisticsOrder.status].text}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="追踪单号">{logisticsOrder.trackingNumber}</Descriptions.Item>
          <Descriptions.Item label="预计送达时间">
            {new Date(logisticsOrder.estimatedDeliveryTime).toLocaleString()}
          </Descriptions.Item>
          <Descriptions.Item label="实际送达时间">
            {logisticsOrder.actualDeliveryTime ? new Date(logisticsOrder.actualDeliveryTime).toLocaleString() : '未送达'}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">{new Date(logisticsOrder.createdAt).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{new Date(logisticsOrder.updatedAt).toLocaleString()}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 收货信息 */}
      <Card style={{ marginBottom: 16 }}>
        <Descriptions title="收货信息" column={1}>
          <Descriptions.Item label="收件人">{logisticsOrder.recipient}</Descriptions.Item>
          <Descriptions.Item label="联系电话">{logisticsOrder.recipientPhone}</Descriptions.Item>
          <Descriptions.Item label="收货地址">{logisticsOrder.shippingAddress}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 物流轨迹 */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Title level={5}>物流轨迹</Title>
          <Button type="primary" onClick={() => setTrackingModalVisible(true)}>添加轨迹</Button>
        </div>
        <Table
          dataSource={logisticsOrder.trackingTraces}
          rowKey={(record, index) => index}
          columns={[
            {
              title: '时间',
              dataIndex: 'time',
              key: 'time',
              width: 200,
              render: (text) => new Date(text).toLocaleString()
            },
            {
              title: '地点',
              dataIndex: 'location',
              key: 'location',
              width: 150
            },
            {
              title: '描述',
              dataIndex: 'description',
              key: 'description'
            }
          ]}
          pagination={false}
        />
      </Card>

      {/* 添加物流轨迹弹窗 */}
      <Modal
        title="添加物流轨迹"
        open={trackingModalVisible}
        onOk={handleAddTrackingTrace}
        onCancel={() => setTrackingModalVisible(false)}
        okText="确定"
        cancelText="取消"
      >
        <div style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>地点：</label>
          <Input
            value={newTrackingInfo.location}
            onChange={(e) => setNewTrackingInfo(prev => ({ ...prev, location: e.target.value }))}
            placeholder="请输入地点"
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 8 }}>描述：</label>
          <Input.TextArea
            value={newTrackingInfo.description}
            onChange={(e) => setNewTrackingInfo(prev => ({ ...prev, description: e.target.value }))}
            placeholder="请输入物流状态描述"
            rows={3}
          />
        </div>
      </Modal>

      {/* 物流渠道管理弹窗 */}
      <Modal
        title="物流渠道管理"
        open={channelModalVisible}
        onOk={() => setChannelModalVisible(false)}
        onCancel={() => setChannelModalVisible(false)}
        okText="确定"
        cancelText="取消"
      >
        <div style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>当前渠道：</label>
          <p>{channelMap[logisticsOrder.channel]}</p>
        </div>
        <div style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>更换渠道：</label>
          <Select style={{ width: '100%' }} placeholder="请选择物流渠道">
            {Object.entries(channelMap).map(([key, value]) => (
              <Option key={key} value={key}>{value}</Option>
            ))}
          </Select>
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 8 }}>渠道配置：</label>
          <p>这里可以配置物流渠道的详细信息，如账号、API密钥等</p>
        </div>
      </Modal>
    </div>
  );
};

export default LogisticsDetail;
