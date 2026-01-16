import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Descriptions, Tag, message, Table, Modal, Input, Select, Tree } from 'antd';
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined, TagOutlined, AppstoreOutlined, SettingOutlined } from '@ant-design/icons';
import { productApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const ProductDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(false);
  const [categoryModalVisible, setCategoryModalVisible] = useState(false);
  const [skuModalVisible, setSkuModalVisible] = useState(false);
  const [attributeModalVisible, setAttributeModalVisible] = useState(false);
  const [skuData, setSkuData] = useState([]);
  const [attributeData, setAttributeData] = useState([]);

  // 产品状态映射
  const statusMap = {
    ACTIVE: { text: '激活', color: 'success' },
    INACTIVE: { text: ' inactive', color: 'default' },
    DISCONTINUED: { text: '已停产', color: 'error' }
  };

  // 产品分类映射
  const categoryMap = {
    ELECTRONICS: '电子产品',
    CLOTHING: '服装',
    HOME: '家居用品',
    BOOKS: '图书',
    BEAUTY: '美妆',
    SPORTS: '运动用品',
    FOOD: '食品',
    OTHER: '其他'
  };

  // 模拟分类树数据
  const categoryTreeData = [
    {
      title: '电子产品',
      value: 'ELECTRONICS',
      children: [
        { title: '手机', value: 'PHONE' },
        { title: '电脑', value: 'COMPUTER' },
        { title: '配件', value: 'ACCESSORY' }
      ]
    },
    {
      title: '服装',
      value: 'CLOTHING',
      children: [
        { title: '上衣', value: 'TOP' },
        { title: '裤子', value: 'PANTS' },
        { title: '鞋子', value: 'SHOES' }
      ]
    }
  ];

  // 获取产品详情
  const fetchProductDetail = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的详情API
      // 暂时使用模拟数据
      const mockProductDetail = {
        id: id,
        productName: `产品${id.split('-')[1]}`,
        skuCode: `SKU${String(parseInt(id.split('-')[1])).padStart(6, '0')}`,
        category: 'ELECTRONICS',
        status: 'ACTIVE',
        price: 1999.99,
        stock: 100,
        weight: 2.5,
        dimensions: '15x10x8cm',
        description: '这是一个测试产品，用于展示产品详情页面。',
        createdAt: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString()
      };
      
      setProduct(mockProductDetail);
      
      // 模拟SKU数据
      setSkuData([
        {
          id: 'sku-001',
          skuCode: `SKU${String(parseInt(id.split('-')[1])).padStart(6, '0')}-001`,
          attributes: '颜色:黑色,容量:128GB',
          price: 1999.99,
          stock: 50,
          status: 'ACTIVE'
        },
        {
          id: 'sku-002',
          skuCode: `SKU${String(parseInt(id.split('-')[1])).padStart(6, '0')}-002`,
          attributes: '颜色:白色,容量:128GB',
          price: 1999.99,
          stock: 30,
          status: 'ACTIVE'
        },
        {
          id: 'sku-003',
          skuCode: `SKU${String(parseInt(id.split('-')[1])).padStart(6, '0')}-003`,
          attributes: '颜色:黑色,容量:256GB',
          price: 2499.99,
          stock: 20,
          status: 'ACTIVE'
        }
      ]);
      
      // 模拟属性数据
      setAttributeData([
        {
          id: 'attr-001',
          name: '颜色',
          values: ['黑色', '白色', '金色', '蓝色']
        },
        {
          id: 'attr-002',
          name: '容量',
          values: ['64GB', '128GB', '256GB', '512GB']
        },
        {
          id: 'attr-003',
          name: '版本',
          values: ['国行', '港版', '美版']
        }
      ]);
    } catch (error) {
      console.error('获取产品详情失败:', error);
      message.error('获取产品详情失败');
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchProductDetail();
  }, [id]);

  // 返回列表页
  const handleBack = () => {
    navigate('/products');
  };

  // 编辑产品
  const handleEdit = () => {
    // 实际应该跳转到编辑页面
    message.info('编辑功能开发中');
  };

  // 删除产品
  const handleDelete = () => {
    // 实际应该调用后端API删除
    message.info('删除功能开发中');
  };

  // 处理产品分类管理
  const handleCategoryManagement = () => {
    setCategoryModalVisible(true);
  };

  // 处理SKU管理
  const handleSkuManagement = () => {
    setSkuModalVisible(true);
  };

  // 处理产品属性管理
  const handleAttributeManagement = () => {
    setAttributeModalVisible(true);
  };

  // SKU表格列
  const skuColumns = [
    {
      title: 'SKU码',
      dataIndex: 'skuCode',
      key: 'skuCode',
      width: 150
    },
    {
      title: '属性',
      dataIndex: 'attributes',
      key: 'attributes'
    },
    {
      title: '价格',
      dataIndex: 'price',
      key: 'price',
      width: 100,
      render: (text) => `¥${text.toFixed(2)}`
    },
    {
      title: '库存',
      dataIndex: 'stock',
      key: 'stock',
      width: 80
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (text) => (
        <Tag color={text === 'ACTIVE' ? 'success' : 'default'}>
          {text === 'ACTIVE' ? '激活' : '禁用'}
        </Tag>
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: () => (
        <Space size="middle">
          <Button type="link">编辑</Button>
          <Button type="link">删除</Button>
        </Space>
      )
    }
  ];

  // 属性表格列
  const attributeColumns = [
    {
      title: '属性名称',
      dataIndex: 'name',
      key: 'name'
    },
    {
      title: '属性值',
      dataIndex: 'values',
      key: 'values',
      render: (values) => values.join(', ')
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: () => (
        <Space size="middle">
          <Button type="link">编辑</Button>
          <Button type="link">删除</Button>
        </Space>
      )
    }
  ];

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!product) {
    return <div>产品不存在</div>;
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>产品详情</Title>
        </div>
        <Space>
          <Button icon={<TagOutlined />} onClick={handleCategoryManagement}>分类管理</Button>
          <Button icon={<AppstoreOutlined />} onClick={handleSkuManagement}>SKU管理</Button>
          <Button icon={<SettingOutlined />} onClick={handleAttributeManagement}>属性管理</Button>
          <Button type="primary" icon={<EditOutlined />} onClick={handleEdit}>编辑</Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete}>删除</Button>
        </Space>
      </div>

      {/* 产品基本信息 */}
      <Card style={{ marginBottom: 5 }}>
        <Descriptions title="基本信息" column={2}>
          <Descriptions.Item label="产品ID">{product.id}</Descriptions.Item>
          <Descriptions.Item label="产品名称">{product.productName}</Descriptions.Item>
          <Descriptions.Item label="SKU码">{product.skuCode}</Descriptions.Item>
          <Descriptions.Item label="分类">{categoryMap[product.category]}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={statusMap[product.status].color}>
              {statusMap[product.status].text}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="价格">¥{product.price.toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="库存">{product.stock}</Descriptions.Item>
          <Descriptions.Item label="重量">{product.weight}kg</Descriptions.Item>
          <Descriptions.Item label="尺寸">{product.dimensions}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{new Date(product.createdAt).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{new Date(product.updatedAt).toLocaleString()}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 产品描述 */}
      <Card style={{ marginBottom: 5 }}>
        <Descriptions title="产品描述" column={1}>
          <Descriptions.Item label="描述">{product.description}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 产品分类管理弹窗 */}
      <Modal
        title="产品分类管理"
        open={categoryModalVisible}
        onCancel={() => setCategoryModalVisible(false)}
        footer={null}
        width={600}
      >
        <div style={{ marginBottom: 5 }}>
          <Button type="primary">添加分类</Button>
        </div>
        <Tree
          treeData={categoryTreeData}
          defaultExpandAll
          titleRender={(node) => (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>{node.title}</span>
              <Space size="small">
                <Button type="link" size="small">编辑</Button>
                <Button type="link" size="small">删除</Button>
              </Space>
            </div>
          )}
        />
      </Modal>

      {/* SKU管理弹窗 */}
      <Modal
        title="SKU管理"
        open={skuModalVisible}
        onCancel={() => setSkuModalVisible(false)}
        footer={null}
        width={800}
      >
        <div style={{ marginBottom: 5 }}>
          <Button type="primary">添加SKU</Button>
        </div>
        <Table
          columns={skuColumns}
          dataSource={skuData}
          rowKey="id"
          pagination={{ pageSize: 10 }}
        />
      </Modal>

      {/* 产品属性管理弹窗 */}
      <Modal
        title="产品属性管理"
        open={attributeModalVisible}
        onCancel={() => setAttributeModalVisible(false)}
        footer={null}
        width={800}
      >
        <div style={{ marginBottom: 5 }}>
          <Button type="primary">添加属性</Button>
        </div>
        <Table
          columns={attributeColumns}
          dataSource={attributeData}
          rowKey="id"
          pagination={{ pageSize: 10 }}
        />
      </Modal>
    </div>
  );
};

export default ProductDetail;
