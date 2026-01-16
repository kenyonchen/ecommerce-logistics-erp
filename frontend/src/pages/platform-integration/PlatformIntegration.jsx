import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Input, Select, DatePicker, Typography, Card, Tag, Switch } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { platformIntegrationApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const PlatformIntegration = () => {
  const navigate = useNavigate();
  const [integrations, setIntegrations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  // 查询条件
  const [searchParams, setSearchParams] = useState({
    integrationId: '',
    platformName: '',
    status: '',
    dateRange: []
  });

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

  // 集成状态选项
  const integrationStatusOptions = [
    { value: 'ACTIVE', label: '活跃' },
    { value: 'INACTIVE', label: '不活跃' },
    { value: 'ERROR', label: '错误' },
    { value: 'SYNCING', label: '同步中' }
  ];

  // 获取平台集成列表
  const fetchIntegrations = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的分页查询API
      // 暂时使用模拟数据
      const mockIntegrations = Array.from({ length: 20 }, (_, index) => {
        const platform = platformTypeOptions[Math.floor(Math.random() * platformTypeOptions.length)];
        const status = integrationStatusOptions[Math.floor(Math.random() * integrationStatusOptions.length)];
        
        return {
          id: `pi-${index + 1}`,
          platformName: platform.label,
          platformType: platform.value,
          status: status.value,
          apiKey: `api_key_${index + 1}`,
          secretKey: `secret_key_${index + 1}`,
          createdAt: new Date(Date.now() - Math.random() * 365 * 24 * 60 * 60 * 1000).toISOString(),
          lastSyncTime: new Date(Date.now() - Math.random() * 24 * 60 * 60 * 1000).toISOString(),
          nextSyncTime: new Date(Date.now() + Math.random() * 24 * 60 * 60 * 1000).toISOString(),
          syncInterval: `${Math.floor(Math.random() * 24) + 1}小时`,
          isEnabled: Math.random() > 0.3,
          errorMessage: status.value === 'ERROR' ? `错误信息${index + 1}` : null
        };
      });
      
      setIntegrations(mockIntegrations);
      setPagination(prev => ({
        ...prev,
        total: mockIntegrations.length
      }));
    } catch (error) {
      console.error('获取平台集成列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchIntegrations();
  }, []);

  // 处理分页变化
  const handlePaginationChange = (page, pageSize) => {
    setPagination(prev => ({
      ...prev,
      current: page,
      pageSize
    }));
  };

  // 处理查询
  const handleSearch = () => {
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
    fetchIntegrations();
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({
      integrationId: '',
      platformName: '',
      status: '',
      dateRange: []
    });
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
    fetchIntegrations();
  };

  // 处理启用/禁用切换
  const handleToggleEnable = (id, enabled) => {
    // 实际应该调用后端API来更新状态
    setIntegrations(prev => prev.map(item => 
      item.id === id ? { ...item, isEnabled: !enabled } : item
    ));
  };

  // 处理手动同步
  const handleManualSync = (id) => {
    // 实际应该调用后端API来执行同步
    setIntegrations(prev => prev.map(item => 
      item.id === id ? { ...item, status: 'SYNCING' } : item
    ));
    
    // 模拟同步完成
    setTimeout(() => {
      setIntegrations(prev => prev.map(item => 
        item.id === id ? { ...item, status: 'ACTIVE', lastSyncTime: new Date().toISOString() } : item
      ));
    }, 2000);
  };

  // 集成状态映射
  const statusMap = {
    ACTIVE: { text: '活跃', color: 'success' },
    INACTIVE: { text: '不活跃', color: 'default' },
    ERROR: { text: '错误', color: 'error' },
    SYNCING: { text: '同步中', color: 'processing' }
  };

  // 表格列配置
  const columns = [
    {
      title: '集成ID',
      dataIndex: 'id',
      key: 'id',
      width: 120
    },
    {
      title: '平台名称',
      dataIndex: 'platformName',
      key: 'platformName',
      width: 120
    },
    {
      title: '平台类型',
      dataIndex: 'platformType',
      key: 'platformType',
      width: 120,
      render: (text) => {
        const platform = platformTypeOptions.find(p => p.value === text);
        return platform ? platform.label : text;
      }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (text) => (
        <Tag color={statusMap[text].color}>{statusMap[text].text}</Tag>
      )
    },
    {
      title: 'API密钥',
      dataIndex: 'apiKey',
      key: 'apiKey',
      width: 150,
      ellipsis: true
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (text) => new Date(text).toLocaleDateString()
    },
    {
      title: '最后同步时间',
      dataIndex: 'lastSyncTime',
      key: 'lastSyncTime',
      width: 150,
      render: (text) => new Date(text).toLocaleString()
    },
    {
      title: '下次同步时间',
      dataIndex: 'nextSyncTime',
      key: 'nextSyncTime',
      width: 150,
      render: (text) => new Date(text).toLocaleString()
    },
    {
      title: '同步间隔',
      dataIndex: 'syncInterval',
      key: 'syncInterval',
      width: 100
    },
    {
      title: '启用',
      dataIndex: 'isEnabled',
      key: 'isEnabled',
      width: 80,
      render: (text, record) => (
        <Switch
          checked={text}
          onChange={() => handleToggleEnable(record.id, text)}
        />
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/platform-integration/${record.id}`)}>查看</Button>
          <Button type="link" icon={<EditOutlined />}>编辑</Button>
          <Button type="link" icon={<ReloadOutlined />} onClick={() => handleManualSync(record.id)}>同步</Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4}>平台集成</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/platform-integration/create')}>添加集成</Button>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>集成ID：</span>
            <Input
              placeholder="请输入集成ID"
              value={searchParams.integrationId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, integrationId: e.target.value }))}
              style={{ width: 120 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>平台名称：</span>
            <Select
              placeholder="请选择平台"
              value={searchParams.platformName}
              onChange={(value) => setSearchParams(prev => ({ ...prev, platformName: value }))}
              style={{ width: 120 }}
            >
              {platformTypeOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>状态：</span>
            <Select
              placeholder="请选择状态"
              value={searchParams.status}
              onChange={(value) => setSearchParams(prev => ({ ...prev, status: value }))}
              style={{ width: 100 }}
            >
              {integrationStatusOptions.map(option => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>创建时间：</span>
            <RangePicker
              value={searchParams.dateRange}
              onChange={(dateRange) => setSearchParams(prev => ({ ...prev, dateRange }))}
            />
          </div>
        </div>
        
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button onClick={handleReset}>重置</Button>
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>查询</Button>
        </div>
      </Card>

      {/* 平台集成表格 */}
      <Card>
        <Table
          columns={columns}
          dataSource={integrations}
          rowKey="id"
          loading={loading}
          pagination={pagination}
          onChange={handlePaginationChange}
          scroll={{ x: 1400 }}
        />
      </Card>
    </div>
  );
};

export default PlatformIntegration;
