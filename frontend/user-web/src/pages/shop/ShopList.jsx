import React, { useState, useEffect, useMemo } from 'react';
import {
  Table, Button, Space, Modal, Form, Input, Select, Switch, Tag, message,
  Popconfirm, DatePicker, Alert, Typography
} from 'antd';
import { PlusOutlined, ReloadOutlined, KeyOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { shopApi, platformIntegrationApi } from '../../services/api';
import { useAuth } from '../../context/AuthContext.jsx';
import { platformLabel } from '../../constants/product.js';

const { Text } = Typography;

const AMAZON_REGION_OPTIONS = [
  { value: 'NA', label: 'NA（北美）' },
  { value: 'EU', label: 'EU（欧洲）' },
  { value: 'FE', label: 'FE（远东）' }
];

const PUSH_BACK_POLICY_OPTIONS = [
  { value: 'AUTO', label: '自动（发货即推平台）' },
  { value: 'MANUAL', label: '人工确认后推' },
  { value: 'OFF', label: '不推送' }
];

const pushBackPolicyLabel = (policy) =>
  PUSH_BACK_POLICY_OPTIONS.find((item) => item.value === policy)?.label || policy || '-';

/** 授权状态（对应后端 ShopAuthStatus） */
const authStatusMap = {
  PENDING: { text: '待验证', color: 'warning' },
  ACTIVE: { text: '已授权', color: 'success' },
  FAILED: { text: '授权失败', color: 'error' },
  EXPIRED: { text: '已过期', color: 'error' }
};

const ShopList = () => {
  const { user, hasRole, hasPermission } = useAuth();
  // 后端按登录租户过滤，tenantId 入参仅平台管理员需要
  const canWrite = hasRole('PLATFORM_ADMIN') || hasPermission('shop:write');
  const canWriteCredentials = hasRole('PLATFORM_ADMIN') || hasPermission('shop:credential_write');

  const [shops, setShops] = useState([]);
  const [integrations, setIntegrations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [createVisible, setCreateVisible] = useState(false);
  const [editingShop, setEditingShop] = useState(null);
  const [credentialShop, setCredentialShop] = useState(null);
  const [policyShop, setPolicyShop] = useState(null);
  const [saving, setSaving] = useState(false);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [credentialForm] = Form.useForm();
  const [policyForm] = Form.useForm();

  const selectedPlatform = Form.useWatch('platform', createForm);

  const fetchShops = async () => {
    setLoading(true);
    try {
      const data = await shopApi.getShops();
      setShops(data || []);
    } catch (error) {
      if (error?.status !== 403) {
        message.error(error?.message || '获取店铺列表失败');
      }
    } finally {
      setLoading(false);
    }
  };

  const fetchIntegrations = async () => {
    try {
      const data = await platformIntegrationApi.getIntegrations();
      setIntegrations(data || []);
    } catch (error) {
      // 集成列表加载失败不阻塞建店（可走自授权模式）
      if (error?.status !== 403) {
        message.warning(error?.message || '获取平台集成列表失败，可跳过集成走自授权模式');
      }
    }
  };

  useEffect(() => {
    fetchShops();
    fetchIntegrations();
  }, []);

  const integrationLabel = useMemo(() => {
    const map = new Map(integrations.map((item) => [item.id, item]));
    return (id) => {
      const matched = map.get(id);
      if (!matched) return id || '自授权模式';
      return `${platformLabel(matched.platformName)} · ${matched.storeUrl || matched.id}`;
    };
  }, [integrations]);

  // ---------- 新建店铺 ----------
  const handleCreate = () => {
    createForm.resetFields();
    setCreateVisible(true);
  };

  const handleCreateSubmit = async (values) => {
    setSaving(true);
    try {
      await shopApi.createShop({
        integrationId: values.integrationId || null,
        platform: values.platform,
        shopCode: values.shopCode.trim(),
        shopName: values.shopName,
        region: values.region || null,
        marketplaceId: values.marketplaceId || null
      });
      message.success('店铺创建成功，请接着配置店铺凭据');
      setCreateVisible(false);
      fetchShops();
    } catch (error) {
      if (error?.status !== 403) {
        message.error(error?.message || '创建店铺失败');
      }
    } finally {
      setSaving(false);
    }
  };

  // ---------- 编辑主数据 ----------
  const handleEdit = (shop) => {
    setEditingShop(shop);
    editForm.setFieldsValue({ shopName: shop.shopName });
  };

  const handleEditSubmit = async (values) => {
    setSaving(true);
    try {
      await shopApi.updateShop(editingShop.id, { shopName: values.shopName });
      message.success('店铺更新成功');
      setEditingShop(null);
      fetchShops();
    } catch (error) {
      if (error?.status !== 403) {
        message.error(error?.message || '更新店铺失败');
      }
    } finally {
      setSaving(false);
    }
  };

  // ---------- 启用/停用 ----------
  const handleToggleActive = async (shop, active) => {
    try {
      await shopApi.updateShop(shop.id, { active });
      message.success(active ? '店铺已启用' : '店铺已停用');
      fetchShops();
    } catch (error) {
      if (error?.status !== 403) {
        message.error(error?.message || '操作失败');
      }
    }
  };

  // ---------- 凭据配置 ----------
  const handleCredentials = (shop) => {
    setCredentialShop(shop);
    // 出参只有掩码与「是否已配置」位，无法原样回填，表单留空即表示保留原值
    credentialForm.resetFields();
  };

  const handleCredentialsSubmit = async (values) => {
    setSaving(true);
    try {
      const payload = {
        apiKey: values.apiKey ?? null,
        apiSecret: values.apiSecret ?? null,
        accessToken: values.accessToken ?? null,
        refreshToken: values.refreshToken ?? null,
        tokenExpiresAt: values.tokenExpiresAt
          ? values.tokenExpiresAt.format('YYYY-MM-DDTHH:mm:ss')
          : null,
        marketplaceId: values.marketplaceId ?? null,
        region: values.region ?? null,
        externalShopId: values.externalShopId ?? null,
        storeUrl: values.storeUrl ?? null,
        apiVersion: values.apiVersion ?? null
      };
      await shopApi.bindCredentials(credentialShop.id, payload);
      message.success('凭据已保存，授权状态回到待验证，请重新做连通性验证');
      setCredentialShop(null);
      fetchShops();
    } catch (error) {
      if (error?.status !== 403) {
        message.error(error?.message || '保存凭据失败');
      }
    } finally {
      setSaving(false);
    }
  };

  // ---------- 回写策略 ----------
  const handlePolicy = (shop) => {
    setPolicyShop(shop);
    policyForm.setFieldsValue({ pushBackPolicy: shop.pushBackPolicy });
  };

  const handlePolicySubmit = async (values) => {
    setSaving(true);
    try {
      await shopApi.changePushBackPolicy(policyShop.id, values.pushBackPolicy);
      message.success('发货回写策略已更新');
      setPolicyShop(null);
      fetchShops();
    } catch (error) {
      if (error?.status !== 403) {
        message.error(error?.message || '更新回写策略失败');
      }
    } finally {
      setSaving(false);
    }
  };

  // ---------- 删除 ----------
  const handleDelete = async (shop) => {
    try {
      await shopApi.deleteShop(shop.id);
      message.success('店铺删除成功');
      fetchShops();
    } catch (error) {
      if (error?.status !== 403) {
        message.error(error?.message || '删除店铺失败');
      }
    }
  };

  const renderAuth = (record) => {
    if (!record.credentialConfigured) {
      return <Tag color="default">未配凭据</Tag>;
    }
    const status = authStatusMap[record.authStatus] || { text: record.authStatus, color: 'default' };
    return <Tag color={status.color}>{status.text}</Tag>;
  };

  const columns = [
    {
      title: '店铺编码',
      dataIndex: 'shopCode',
      key: 'shopCode',
      width: 140,
      ellipsis: true,
    },
    {
      title: '店铺名称',
      dataIndex: 'shopName',
      key: 'shopName',
      width: 160,
      ellipsis: true,
      render: (name) => name || '-',
    },
    {
      title: '平台',
      dataIndex: 'platform',
      key: 'platform',
      width: 110,
      render: (platform) => platformLabel(platform),
    },
    {
      title: '区域/站点',
      key: 'region',
      width: 160,
      ellipsis: true,
      render: (_, record) =>
        [record.region, record.marketplaceId].filter(Boolean).join(' / ') || '-',
    },
    {
      title: '关联集成',
      dataIndex: 'integrationId',
      key: 'integrationId',
      width: 200,
      ellipsis: true,
      render: (integrationId) => integrationLabel(integrationId),
    },
    {
      title: '授权状态',
      key: 'authStatus',
      width: 110,
      render: (_, record) => renderAuth(record),
    },
    {
      title: '发货回写',
      dataIndex: 'pushBackPolicy',
      key: 'pushBackPolicy',
      width: 130,
      ellipsis: true,
      render: (policy) => pushBackPolicyLabel(policy),
    },
    {
      title: '启用',
      dataIndex: 'active',
      key: 'active',
      width: 80,
      render: (active, record) =>
        canWrite ? (
          <Switch
            size="small"
            checked={active}
            onChange={(checked) => handleToggleActive(record, checked)}
          />
        ) : (
          <Tag color={active ? 'success' : 'default'}>{active ? '启用' : '停用'}</Tag>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small" wrap>
          {canWriteCredentials && (
            <Button type="link" size="small" icon={<KeyOutlined />} onClick={() => handleCredentials(record)}>
              凭据
            </Button>
          )}
          {canWrite && (
            <>
              <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handlePolicy(record)}>
                回写策略
              </Button>
              <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
                编辑
              </Button>
              <Popconfirm
                title="确定要删除该店铺吗？"
                onConfirm={() => handleDelete(record)}
                okText="确定"
                cancelText="取消"
              >
                <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                  删除
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className="page">
      <div className="page__header">
        <div>
          <h1 className="page__title">店铺管理</h1>
          <p className="page__subtitle">共 {shops.length} 个店铺，接入第三方平台站点的经营实体</p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchShops}>
            刷新
          </Button>
          {canWrite && (
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              新建店铺
            </Button>
          )}
        </Space>
      </div>

      {!canWrite && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="你只有店铺查看权限，如需建店或配置凭据请联系管理员分配 shop:write / shop:credential_write 权限"
        />
      )}

      <Table
        columns={columns}
        dataSource={shops}
        rowKey="id"
        loading={loading}
        scroll={{ x: 'max-content' }}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (total) => `共 ${total} 条`,
        }}
      />

      {/* 新建店铺 */}
      <Modal
        title="新建店铺"
        open={createVisible}
        onCancel={() => setCreateVisible(false)}
        footer={null}
        width={520}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="建店只落主数据，凭据为空、授权状态为待验证；创建成功后请再点「凭据」配置密钥"
        />
        <Form form={createForm} layout="vertical" onFinish={handleCreateSubmit}>
          <Form.Item
            name="integrationId"
            label="关联平台集成"
            extra="选填：绑定本租户已有的平台集成作为对接通道；不选即自授权模式"
          >
            <Select
              allowClear
              placeholder="不选则走自授权模式"
              options={integrations.map((item) => ({
                value: item.id,
                label: `${platformLabel(item.platformName)} · ${item.storeUrl || item.id}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="platform"
            label="平台"
            rules={[{ required: true, message: '请选择平台' }]}
          >
            <Select
              placeholder="请选择平台"
              options={['AMAZON', 'EBAY', 'SHOPIFY', 'ALIEXPRESS', 'WISH', 'MAGENTO', 'OTHER'].map((value) => ({
                value,
                label: platformLabel(value),
              }))}
            />
          </Form.Item>
          <Form.Item
            name="shopCode"
            label="店铺编码"
            rules={[{ required: true, message: '请输入店铺编码' }]}
            extra="租户内唯一，创建后不可修改（改编码等于换店）"
          >
            <Input placeholder="如 my-shop-01" maxLength={64} />
          </Form.Item>
          <Form.Item name="shopName" label="店铺名称">
            <Input placeholder="选填" maxLength={128} />
          </Form.Item>
          <Form.Item
            name="region"
            label="区域"
            rules={
              selectedPlatform === 'AMAZON'
                ? [{ required: true, message: 'Amazon 必须选择区域' }]
                : undefined
            }
            extra={selectedPlatform === 'AMAZON' ? 'Amazon 只能是 NA/EU/FE 之一' : '仅 Amazon 需要区域'}
          >
            {selectedPlatform === 'AMAZON' ? (
              <Select placeholder="请选择区域" options={AMAZON_REGION_OPTIONS} />
            ) : (
              <Input placeholder="选填" maxLength={32} />
            )}
          </Form.Item>
          <Form.Item
            name="marketplaceId"
            label="站点标识"
            extra="如 Amazon 的 ATVPDKIKX0DER；同平台下不能与其他店铺重复"
          >
            <Input placeholder="选填" maxLength={64} />
          </Form.Item>
          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={() => setCreateVisible(false)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={saving}>保存</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑店铺主数据 */}
      <Modal
        title={`编辑店铺：${editingShop?.shopCode || ''}`}
        open={!!editingShop}
        onCancel={() => setEditingShop(null)}
        footer={null}
        width={440}
        destroyOnHidden
      >
        <Form form={editForm} layout="vertical" onFinish={handleEditSubmit}>
          <Form.Item name="shopName" label="店铺名称">
            <Input placeholder="请输入店铺名称" maxLength={128} />
          </Form.Item>
          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={() => setEditingShop(null)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={saving}>保存</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 凭据配置 */}
      <Modal
        title={`配置凭据：${credentialShop?.shopCode || ''}`}
        open={!!credentialShop}
        onCancel={() => setCredentialShop(null)}
        footer={null}
        width={560}
        destroyOnHidden
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="不填 = 保留原值；填空串 = 清除该字段。任何写入都会把授权状态打回「待验证」，需重新做连通性验证"
        />
        <Form form={credentialForm} layout="vertical" onFinish={handleCredentialsSubmit}>
          <Form.Item name="apiKey" label="API Key / Client ID">
            <Input.Password placeholder="留空保留原值" autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="apiSecret" label="API Secret / Client Secret">
            <Input.Password placeholder="留空保留原值" autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="accessToken" label="访问令牌">
            <Input.Password placeholder="如 Shopify 的 shpat_ 令牌；留空保留原值" autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="refreshToken" label="刷新令牌">
            <Input.Password placeholder="OAuth 协议需要；留空保留原值" autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="tokenExpiresAt" label="令牌到期时间">
            <DatePicker showTime style={{ width: '100%' }} placeholder="选填" />
          </Form.Item>
          <Form.Item name="externalShopId" label="平台侧店铺标识">
            <Input placeholder="如 Shopee/TikTok 的 shop_id；留空保留原值" maxLength={64} />
          </Form.Item>
          <Form.Item name="storeUrl" label="店铺地址">
            <Input placeholder="Shopify 域名或自建店铺 API 基址；留空保留原值" maxLength={255} />
          </Form.Item>
          <Form.Item name="region" label="区域">
            {credentialShop?.platform === 'AMAZON' ? (
              <Select allowClear placeholder="留空保留原值" options={AMAZON_REGION_OPTIONS} />
            ) : (
              <Input placeholder="留空保留原值" maxLength={32} />
            )}
          </Form.Item>
          <Form.Item name="marketplaceId" label="站点标识">
            <Input placeholder="留空保留原值" maxLength={64} />
          </Form.Item>
          <Form.Item name="apiVersion" label="API 版本">
            <Input placeholder="如 2026-07；留空保留原值" maxLength={32} />
          </Form.Item>
          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={() => setCredentialShop(null)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={saving}>保存凭据</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 回写策略 */}
      <Modal
        title={`发货回写策略：${policyShop?.shopCode || ''}`}
        open={!!policyShop}
        onCancel={() => setPolicyShop(null)}
        footer={null}
        width={440}
        destroyOnHidden
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          只影响该店铺后续发货的推送方式，不会改写已有物流单上的推送状态
        </Text>
        <Form form={policyForm} layout="vertical" onFinish={handlePolicySubmit}>
          <Form.Item
            name="pushBackPolicy"
            label="策略"
            rules={[{ required: true, message: '请选择回写策略' }]}
          >
            <Select options={PUSH_BACK_POLICY_OPTIONS} />
          </Form.Item>
          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={() => setPolicyShop(null)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={saving}>保存</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ShopList;
