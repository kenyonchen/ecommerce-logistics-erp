import React, { useEffect, useState } from 'react';
import { Button, Card, Descriptions, Divider, Popconfirm, Space, Table, Tag, Typography, message } from 'antd';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  StopOutlined
} from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { productApi } from '../../services/api';
import {
  categoryLabel,
  DEFAULT_CURRENCY,
  DEFAULT_TENANT_ID,
  formatAttributes,
  getProductStatusTag,
  gramToKg,
  platformLabel
} from '../../constants/product';

const { Title } = Typography;

const ProductDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchDetail = async () => {
    setLoading(true);
    try {
      const data = await productApi.getProductById(id);
      setProduct(data);
    } catch {
      message.error('获取商品详情失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (id) fetchDetail();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handleToggleActive = async () => {
    try {
      if (product.active) {
        await productApi.deactivateProduct(id);
      } else {
        await productApi.activateProduct(id);
      }
      message.success(product.active ? '商品已停用' : '商品已启用');
      fetchDetail();
    } catch {
      message.error('操作失败');
    }
  };

  const handleDelete = async () => {
    try {
      await productApi.deleteProduct(id, DEFAULT_TENANT_ID);
      message.success('商品删除成功');
      navigate('/products');
    } catch {
      message.error('删除商品失败');
    }
  };

  const handleRemoveSku = async (skuCode) => {
    try {
      await productApi.removeSku(id, skuCode, DEFAULT_TENANT_ID);
      message.success('SKU删除成功');
      fetchDetail();
    } catch {
      message.error('删除SKU失败');
    }
  };

  if (loading || !product) {
    return <Card loading={loading}>{!loading && !product ? '商品不存在' : null}</Card>;
  }

  const statusTag = getProductStatusTag(product.active);
  const skus = product.skus || [];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Space size="middle">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/products')}>返回列表</Button>
          <Title level={4} style={{ margin: 0 }}>商品详情</Title>
        </Space>
        <Space>
          <Button
            icon={product.active ? <StopOutlined /> : <CheckCircleOutlined />}
            onClick={handleToggleActive}
          >
            {product.active ? '停用' : '启用'}
          </Button>
          <Button type="primary" icon={<EditOutlined />} onClick={() => navigate(`/products/${id}/edit`)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该商品吗？" onConfirm={handleDelete} okText="确定" cancelText="取消">
            <Button danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      </div>

      <Card size="small" style={{ marginBottom: 8 }}>
        <Descriptions column={2} size="small" bordered>
          <Descriptions.Item label="商品ID">{product.id}</Descriptions.Item>
          <Descriptions.Item label="商品名称">{product.name}</Descriptions.Item>
          <Descriptions.Item label="分类">{categoryLabel(product.categoryName)}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={statusTag.color}>{statusTag.text}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="租户ID">{product.tenantId || '-'}</Descriptions.Item>
          <Descriptions.Item label="来源平台">
            {product.sourcePlatform ? platformLabel(product.sourcePlatform) : <Tag>手工创建</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="平台商品ID">{product.sourceProductId || '-'}</Descriptions.Item>
          <Descriptions.Item label="SKU数量">{skus.length}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{product.createdAt || '-'}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{product.updatedAt || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card size="small" title="商品描述" style={{ marginBottom: 8 }}>
        <div style={{ whiteSpace: 'pre-wrap' }}>{product.description || '暂无描述'}</div>
      </Card>

      <Card size="small" title="SKU明细">
        <Table
          size="small"
          rowKey="id"
          dataSource={skus}
          pagination={false}
          scroll={{ x: 1100 }}
          columns={[
            { title: 'SKU编码', dataIndex: 'skuCode', key: 'skuCode', width: 180 },
            { title: 'SKU名称', dataIndex: 'skuName', key: 'skuName', width: 180 },
            {
              title: '销售价',
              dataIndex: 'sellingPrice',
              key: 'sellingPrice',
              width: 120,
              render: (text, record) =>
                text != null ? `${record.currency || DEFAULT_CURRENCY} ${Number(text).toFixed(2)}` : '-'
            },
            {
              title: '成本价',
              dataIndex: 'costPrice',
              key: 'costPrice',
              width: 120,
              render: (text, record) =>
                text != null ? `${record.currency || DEFAULT_CURRENCY} ${Number(text).toFixed(2)}` : '-'
            },
            {
              title: '重量(KG)',
              dataIndex: 'weightInGrams',
              key: 'weightInGrams',
              width: 110,
              render: (gram) => {
                const kg = gramToKg(gram);
                return kg != null ? Number(kg).toFixed(2) : '-';
              }
            },
            {
              title: '体积(cm³)',
              dataIndex: 'volumeInCubicCentimeters',
              key: 'volumeInCubicCentimeters',
              width: 110,
              render: (text) => text ?? '-'
            },
            {
              title: '变体属性',
              dataIndex: 'attributes',
              key: 'attributes',
              render: (attributes) => formatAttributes(attributes)
            },
            {
              title: '状态',
              dataIndex: 'active',
              key: 'active',
              width: 90,
              render: (active) => {
                const tag = getProductStatusTag(active);
                return <Tag color={tag.color}>{tag.text}</Tag>;
              }
            },
            {
              title: '操作',
              key: 'action',
              width: 90,
              fixed: 'right',
              render: (_, record) => (
                <Popconfirm
                  title="确定删除该SKU吗？"
                  onConfirm={() => handleRemoveSku(record.skuCode)}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button type="link" danger size="small" icon={<DeleteOutlined />}>
                    删除
                  </Button>
                </Popconfirm>
              )
            }
          ]}
        />
        <Divider />
        <div style={{ color: '#888' }}>提示：新增SKU请在「编辑」页面统一提交。</div>
      </Card>
    </div>
  );
};

export default ProductDetail;
