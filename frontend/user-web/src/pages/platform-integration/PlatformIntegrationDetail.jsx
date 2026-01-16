import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Descriptions, Tag, message, Table, Modal, Switch, Select, Input } from 'antd';
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined, ReloadOutlined, LinkOutlined, SyncOutlined, ShoppingOutlined } from '@ant-design/icons';
import { platformIntegrationApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const PlatformIntegrationDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [integration, setIntegration] = useState(null);
  const [loading, setLoading] = useState(false);
  const [platformConfigModalVisible, setPlatformConfigModalVisible] = useState(false);
  const [syncConfigModalVisible, setSyncConfigModalVisible] = useState(false);
  const [syncProductsModalVisible, setSyncProductsModalVisible] = useState(false);
  const [platforms, setPlatforms] = useState([]);

  // 平台类型选项
  const platformTypeOptions = [
    { value: 'AMAZON', label: 'Amazon' },
    { value: 'EBAY', label: 'eBay' },
    { value: 'ALIEXPRESS', label: 'AliExpress' },
    { value: 'WISH', label: 'Wish' },
    { value: 'SHOPIFY', label: 'Shopify' },
    { value: 'TAOBAO', label: '淘宝' },
    { value: 'JD', label: '京东' },
    { value: 'PDD', label: '拼多多' }
  ];

  // 集成状态映射
  const statusMap = {
    ACTIVE: { text: '活跃', color: 'success' },
    INACTIVE: { text: '不活跃', color: 'default' },
    ERROR: { text: '错误', color: 'error' },
    SYNCING: { text: '同步中', color: 'processing' }
  };

  // 获取平台集成详情
  const fetchIntegrationDetail = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的详情API
      // 暂时使用模拟数据
      const mockIntegrationDetail = {
        id: id,
        platformName: `平台${id.split('-')[1]}`,
        platformType: platformTypeOptions[Math.floor(Math.random() * platformTypeOptions.length)].value,
        status: 'ACTIVE',
        apiKey: `api_key_${id.split('-')[1]}`,
        secretKey: `secret_key_${id.split('-')[1]}`,
        createdAt: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
        lastSyncTime: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
        nextSyncTime: new Date(Date.now() + 22 * 24 * 60 * 60 * 1000).toISOString(),
        syncInterval: '24小时',
        isEnabled: true,
        errorMessage: null,
        webhookUrl: `https://api.example.com/webhook/${id}`,
        syncHistory: [
          {
            id: 'sync-1',
            timestamp: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
            status: 'SUCCESS',
            message: '同步成功',
            syncedOrders: 15,
            syncedProducts: 20
          },
          {
            id: 'sync-2',
            timestamp: new Date(Date.now() - 26 * 24 * 60 * 60 * 1000).toISOString(),
            status: 'SUCCESS',
            message: '同步成功',
            syncedOrders: 12,
            syncedProducts: 18
          },
          {
            id: 'sync-3',
            timestamp: new Date(Date.now() - 50 * 24 * 60 * 60 * 1000).toISOString(),
            status: 'ERROR',
            message: '同步失败：API 密钥无效',
            syncedOrders: 0,
            syncedProducts: 0
          }
        ],
        // 新增同步配置
        syncConfig: {
          autoSyncOrders: true,
          autoSyncProducts: true,
          syncOrderStatus: true,
          syncInventory: true,
          syncInterval: '24小时'
        }
      };
      
      setIntegration(mockIntegrationDetail);
      
      // 模拟已对接的平台
      setPlatforms([
        {
          id: 'plat-001',
          name: 'Amazon US',
          type: 'AMAZON',
          status: 'ACTIVE',
          connectedAt: new Date(Date.now() - 15 * 24 * 60 * 60 * 1000).toISOString()
        },
        {
          id: 'plat-002',
          name: 'eBay UK',
          type: 'EBAY',
          status: 'ACTIVE',
          connectedAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString()
        }
      ]);
    } catch (error) {
      console.error('获取平台集成详情失败:', error);
      message.error('获取平台集成详情失败');
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchIntegrationDetail();
  }, [id]);

  // 返回列表页
  const handleBack = () => {
    navigate('/platform-integration');
  };

  // 编辑平台集成
  const handleEdit = () => {
    // 实际应该跳转到编辑页面
    message.info('编辑功能开发中');
  };

  // 删除平台集成
  const handleDelete = () => {
    // 实际应该调用后端API删除
    message.info('删除功能开发中');
  };

  // 手动同步
  const handleManualSync = () => {
    // 实际应该调用后端API来执行同步
    setIntegration(prev => prev ? { ...prev, status: 'SYNCING' } : prev);
    
    // 模拟同步完成
    setTimeout(() => {
      setIntegration(prev => prev ? { 
        ...prev, 
        status: 'ACTIVE', 
        lastSyncTime: new Date().toISOString() 
      } : prev);
      
      message.success('同步完成');
    }, 2000);
  };

  // 处理电商平台对接
  const handlePlatformIntegration = () => {
    setPlatformConfigModalVisible(true);
  };

  // 处理订单自动同步配置
  const handleOrderSyncConfig = () => {
    setSyncConfigModalVisible(true);
  };

  // 处理产品信息同步
  const handleProductSync = () => {
    setSyncProductsModalVisible(true);
  };

  // 处理添加平台
  const handleAddPlatform = () => {
    message.success('平台添加成功');
    setPlatformConfigModalVisible(false);
  };

  // 处理保存同步配置
  const handleSaveSyncConfig = () => {
    message.success('同步配置保存成功');
    setSyncConfigModalVisible(false);
  };

  // 处理产品同步
  const handleSyncProducts = () => {
    message.loading('正在同步产品...');
    
    // 模拟同步完成
    setTimeout(() => {
      message.success('产品同步完成');
      setSyncProductsModalVisible(false);
    }, 2000);
  };

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!integration) {
    return <div>平台集成不存在</div>;
  }

  // 获取平台类型名称
  const getPlatformTypeName = (value) => {
    const platform = platformTypeOptions.find(p => p.value === value);
    return platform ? platform.label : value;
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>平台集成详情</Title>
        </div>
        <Space>
          <Button icon={<LinkOutlined />} onClick={handlePlatformIntegration}>电商平台对接</Button>
          <Button icon={<SyncOutlined />} onClick={handleOrderSyncConfig}>订单自动同步</Button>
          <Button icon={<ShoppingOutlined />} onClick={handleProductSync}>产品信息同步</Button>
          <Button type="primary" icon={<ReloadOutlined />} onClick={handleManualSync}>手动同步</Button>
          <Button icon={<EditOutlined />} onClick={handleEdit}>编辑</Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete}>删除</Button>
        </Space>
      </div>

      {/* 基本信息 */}
      <Card style={{ marginBottom: 5 }}>
        <Descriptions title="基本信息" column={2}>
          <Descriptions.Item label="集成ID">{integration.id}</Descriptions.Item>
          <Descriptions.Item label="平台名称">{integration.platformName}</Descriptions.Item>
          <Descriptions.Item label="平台类型">{getPlatformTypeName(integration.platformType)}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={statusMap[integration.status].color}>
              {statusMap[integration.status].text}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="API密钥">{integration.apiKey}</Descriptions.Item>
          <Descriptions.Item label="Secret密钥">{integration.secretKey}</Descriptions.Item>
          <Descriptions.Item label="Webhook URL">{integration.webhookUrl}</Descriptions.Item>
          <Descriptions.Item label="启用状态">{integration.isEnabled ? '启用' : '禁用'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{new Date(integration.createdAt).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="最后同步时间">{new Date(integration.lastSyncTime).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="下次同步时间">{new Date(integration.nextSyncTime).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="同步间隔">{integration.syncInterval}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 已对接平台 */}
      <Card style={{ marginBottom: 5 }}>
        <Title level={5}>已对接平台</Title>
        <div style={{ marginTop: 16 }}>
          {platforms.map((platform) => (
            <Card key={platform.id} style={{ marginBottom: 8 }}>
              <Descriptions column={3}>
                <Descriptions.Item label="平台名称">{platform.name}</Descriptions.Item>
                <Descriptions.Item label="平台类型">{getPlatformTypeName(platform.type)}</Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color={statusMap[platform.status].color}>
                    {statusMap[platform.status].text}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="对接时间">{new Date(platform.connectedAt).toLocaleString()}</Descriptions.Item>
                <Descriptions.Item label="操作">
                  <Space>
                    <Button type="link">编辑</Button>
                    <Button type="link">断开</Button>
                  </Space>
                </Descriptions.Item>
              </Descriptions>
            </Card>
          ))}
        </div>
      </Card>

      {/* 同步历史 */}
      <Card style={{ marginBottom: 5 }}>
        <Title level={5}>同步历史</Title>
        <div style={{ marginTop: 16 }}>
          {integration.syncHistory.map((sync) => (
            <Card key={sync.id} style={{ marginBottom: 8 }}>
              <Descriptions column={3}>
                <Descriptions.Item label="时间">{new Date(sync.timestamp).toLocaleString()}</Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color={sync.status === 'SUCCESS' ? 'success' : 'error'}>
                    {sync.status === 'SUCCESS' ? '成功' : '失败'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="消息">{sync.message}</Descriptions.Item>
                <Descriptions.Item label="同步订单数">{sync.syncedOrders}</Descriptions.Item>
                <Descriptions.Item label="同步产品数">{sync.syncedProducts}</Descriptions.Item>
              </Descriptions>
            </Card>
          ))}
        </div>
      </Card>

      {/* 电商平台对接弹窗 */}
      <Modal
        title="电商平台对接"
        open={platformConfigModalVisible}
        onOk={handleAddPlatform}
        onCancel={() => setPlatformConfigModalVisible(false)}
        okText="添加"
        cancelText="取消"
      >
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>平台类型：</label>
          <Select style={{ width: '100%' }} placeholder="请选择电商平台">
            {platformTypeOptions.map((option) => (
              <Option key={option.value} value={option.value}>{option.label}</Option>
            ))}
          </Select>
        </div>
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>平台名称：</label>
          <Input placeholder="请输入平台名称，如 Amazon US" />
        </div>
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>API密钥：</label>
          <Input placeholder="请输入API密钥" />
        </div>
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>Secret密钥：</label>
          <Input placeholder="请输入Secret密钥" />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 8 }}>其他配置：</label>
          <Input.TextArea rows={3} placeholder="请输入其他配置信息" />
        </div>
      </Modal>

      {/* 订单自动同步配置弹窗 */}
      <Modal
        title="订单自动同步配置"
        open={syncConfigModalVisible}
        onOk={handleSaveSyncConfig}
        onCancel={() => setSyncConfigModalVisible(false)}
        okText="保存"
        cancelText="取消"
      >
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>自动同步订单：</label>
          <Switch checked={integration.syncConfig.autoSyncOrders} />
        </div>
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>自动同步产品：</label>
          <Switch checked={integration.syncConfig.autoSyncProducts} />
        </div>
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>同步订单状态：</label>
          <Switch checked={integration.syncConfig.syncOrderStatus} />
        </div>
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>同步库存：</label>
          <Switch checked={integration.syncConfig.syncInventory} />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 8 }}>同步间隔：</label>
          <Select style={{ width: '100%' }} value={integration.syncConfig.syncInterval}>
            <Option value="1小时">1小时</Option>
            <Option value="6小时">6小时</Option>
            <Option value="12小时">12小时</Option>
            <Option value="24小时">24小时</Option>
          </Select>
        </div>
      </Modal>

      {/* 产品信息同步弹窗 */}
      <Modal
        title="产品信息同步"
        open={syncProductsModalVisible}
        onOk={handleSyncProducts}
        onCancel={() => setSyncProductsModalVisible(false)}
        okText="开始同步"
        cancelText="取消"
      >
        <div style={{ marginBottom: 5 }}>
          <p>选择同步选项：</p>
        </div>
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>同步全部产品：</label>
          <Switch />
        </div>
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>仅同步新增产品：</label>
          <Switch />
        </div>
        <div style={{ marginBottom: 5 }}>
          <label style={{ display: 'block', marginBottom: 8 }}>同步库存：</label>
          <Switch />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 8 }}>同步价格：</label>
          <Switch />
        </div>
      </Modal>
    </div>
  );
};

export default PlatformIntegrationDetail;
