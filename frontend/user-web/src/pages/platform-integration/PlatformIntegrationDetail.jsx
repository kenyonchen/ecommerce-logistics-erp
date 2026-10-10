import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Card, Descriptions, Popconfirm, Space, Tag, Typography, message } from 'antd';
import { ArrowLeftOutlined, DeleteOutlined, ReloadOutlined, ApiOutlined } from '@ant-design/icons';
import { platformIntegrationApi } from '../../services/api';
import { DEFAULT_TENANT_ID, platformLabel, authTypeLabel } from '../../constants/product';

const { Title } = Typography;

const statusMap = {
  ACTIVE: { text: '活跃', color: 'success' },
  INACTIVE: { text: '未启用', color: 'default' },
  DISCONNECTED: { text: '已断开', color: 'warning' },
  ERROR: { text: '错误', color: 'error' }
};

// 后端出参只回传掩码后的 apiKeyMasked 与「是否已配置」布尔位（BR-02），前端不再自行打码
const boolTag = (configured) => (configured ? <Tag color="success">已配置</Tag> : <Tag>未配置</Tag>);

const PlatformIntegrationDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [integration, setIntegration] = useState(null);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [syncingCustomers, setSyncingCustomers] = useState(false);
  const [syncingLogistics, setSyncingLogistics] = useState(false);
  const [testing, setTesting] = useState(false);

  const fetchDetail = useCallback(async () => {
    setLoading(true);
    try {
      const data = await platformIntegrationApi.getIntegrationById(id);
      setIntegration(data);
    } catch {
      message.error('获取平台集成详情失败');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  const handleToggleActive = async () => {
    try {
      if (integration.status === 'ACTIVE') {
        await platformIntegrationApi.deactivate(id);
      } else {
        await platformIntegrationApi.activate(id);
      }
      message.success(integration.status === 'ACTIVE' ? '已停用' : '已启用');
      fetchDetail();
    } catch {
      message.error('操作失败');
    }
  };

  const handleSyncProducts = async () => {
    setSyncing(true);
    try {
      const job = await platformIntegrationApi.syncProducts(id, DEFAULT_TENANT_ID);
      message.success(
        `商品同步完成：成功 ${job.successCount} 行，失败 ${job.failureCount} 行，跳过 ${job.skippedCount} 行`
      );
      fetchDetail();
    } catch (error) {
      message.error(`商品同步失败：${error?.message || '未知错误'}`);
    } finally {
      setSyncing(false);
    }
  };

  const handleSyncCustomers = async () => {
    setSyncingCustomers(true);
    try {
      const job = await platformIntegrationApi.syncCustomers(id, { tenantId: DEFAULT_TENANT_ID });
      message.success(
        `客户同步完成：成功 ${job.successCount} 行，失败 ${job.failureCount} 行，跳过 ${job.skippedCount} 行`
      );
    } catch (error) {
      message.error(`客户同步失败：${error?.message || '未知错误'}`);
    } finally {
      setSyncingCustomers(false);
    }
  };

  // 物流同步：调用后端 sync-logistics，走「拉取→字段映射→批量导入」链路，返回导入任务结果
  const handleSyncLogistics = async () => {
    setSyncingLogistics(true);
    try {
      const job = await platformIntegrationApi.syncLogistics(id, { tenantId: DEFAULT_TENANT_ID });
      const detail = `${job.message || ''}（成功 ${job.successCount ?? 0} / 失败 ${job.failureCount ?? 0} / 跳过 ${job.skippedCount ?? 0}）`;
      if ((job.failureCount ?? 0) > 0) {
        const first = job.errors?.[0];
        message.warning(
          `物流同步完成但存在失败行：${detail}${first ? `；首个错误：${first.message || ''}` : ''}`,
          6
        );
      } else {
        message.success(`物流同步完成：${detail}`);
      }
    } catch (error) {
      message.error(`物流同步失败：${error?.message || '未知错误'}`);
    } finally {
      setSyncingLogistics(false);
    }
  };

  // 连通性测试：一次最小代价只读外呼，验证店铺地址与凭据；失败原因由后端给出，不含凭据明文
  const handleTestConnection = async () => {
    setTesting(true);
    try {
      const result = await platformIntegrationApi.testConnection(id);
      if (result?.success) {
        message.success(`连接正常（${result.latencyMs}ms）：${result.message || ''}`);
      } else {
        message.error(`连通性测试未通过：${result?.message || '未知原因'}`, 6);
      }
    } catch (error) {
      message.error(`连通性测试失败：${error?.message || '未知错误'}`);
    } finally {
      setTesting(false);
    }
  };

  const handleDelete = async () => {
    try {
      await platformIntegrationApi.deleteIntegration(id);
      message.success('平台集成已删除');
      navigate('/platform-integration');
    } catch {
      message.error('删除失败');
    }
  };

  if (!integration && loading) {
    return <Card loading />;
  }

  if (!integration) {
    return <Card>平台集成不存在</Card>;
  }

  const tag = statusMap[integration.status] || { text: integration.status, color: 'default' };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Space size="middle">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/platform-integration')}>
            返回列表
          </Button>
          <Title level={4} style={{ margin: 0 }}>平台集成详情</Title>
        </Space>
        <Space>
          <Button icon={<ApiOutlined />} loading={testing} onClick={handleTestConnection}>
            连通性测试
          </Button>
          <Button icon={<ReloadOutlined />} loading={syncing} onClick={handleSyncProducts}>
            同步商品
          </Button>
          <Button icon={<ReloadOutlined />} loading={syncingCustomers} onClick={handleSyncCustomers}>
            同步客户
          </Button>
          <Button icon={<ReloadOutlined />} loading={syncingLogistics} onClick={handleSyncLogistics}>
            同步物流
          </Button>
          <Button onClick={handleToggleActive}>{integration.status === 'ACTIVE' ? '停用' : '启用'}</Button>
          <Popconfirm title="确定删除该平台集成吗？" onConfirm={handleDelete} okText="确定" cancelText="取消">
            <Button danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      </div>

      <Card size="small">
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="集成ID">{integration.id}</Descriptions.Item>
          <Descriptions.Item label="平台">{platformLabel(integration.platformName)}</Descriptions.Item>
          <Descriptions.Item label="对接协议">{authTypeLabel(integration.authType || 'CUSTOM_SHOP')}</Descriptions.Item>
          <Descriptions.Item label="接口版本">
            {integration.apiVersion
              || (integration.authType === 'SHOPIFY' ? '未配置（Shopify 集成必须显式指定，探活会因此失败）' : '未配置')}
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={tag.color}>{tag.text}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="租户ID">{integration.tenantId || '-'}</Descriptions.Item>
          <Descriptions.Item label="店铺地址">{integration.storeUrl || '未配置'}</Descriptions.Item>
          <Descriptions.Item label="API Key">{integration.apiKeyMasked || '-'}</Descriptions.Item>
          <Descriptions.Item label="API Secret">{boolTag(integration.hasApiSecret)}</Descriptions.Item>
          <Descriptions.Item label="访问令牌">{boolTag(integration.hasAccessToken)}</Descriptions.Item>
          <Descriptions.Item label="令牌过期时间">{integration.tokenExpiresAt || '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{integration.createdAt || '-'}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{integration.updatedAt || '-'}</Descriptions.Item>
          <Descriptions.Item label="最后商品同步时间">
            {integration.lastProductsSyncedAt || '从未同步'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  );
};

export default PlatformIntegrationDetail;
