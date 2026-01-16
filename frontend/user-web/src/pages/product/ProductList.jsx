import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Input, Select, DatePicker, Typography, Card, Tag } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { productApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const ProductList = () => {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  // 查询条件
  const [searchParams, setSearchParams] = useState({
    productId: '',
    productName: '',
    skuCode: '',
    category: '',
    status: '',
    dateRange: []
  });

  // 产品状态选项
  const productStatusOptions = [
    { value: 'ACTIVE', label: '激活' },
    { value: 'INACTIVE', label: ' inactive' },
    { value: 'DISCONTINUED', label: '已停产' }
  ];

  // 产品分类选项
  const productCategoryOptions = [
    { value: 'ELECTRONICS', label: '电子产品' },
    { value: 'CLOTHING', label: '服装' },
    { value: 'HOME', label: '家居用品' },
    { value: 'BOOKS', label: '图书' },
    { value: 'BEAUTY', label: '美妆' },
    { value: 'SPORTS', label: '运动用品' },
    { value: 'FOOD', label: '食品' },
    { value: 'OTHER', label: '其他' }
  ];

  // 获取产品列表
  const fetchProducts = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的分页查询API
      // 暂时使用模拟数据
      const mockProducts = Array.from({ length: 20 }, (_, index) => ({
        id: `prod-${index + 1}`,
        productName: `产品${index + 1}`,
        skuCode: `SKU${String(index + 1).padStart(6, '0')}`,
        category: productCategoryOptions[Math.floor(Math.random() * productCategoryOptions.length)].value,
        status: productStatusOptions[Math.floor(Math.random() * productStatusOptions.length)].value,
        price: 100 + Math.random() * 900,
        stock: Math.floor(Math.random() * 1000),
        weight: 0.1 + Math.random() * 10,
        dimensions: `${Math.floor(Math.random() * 50) + 1}x${Math.floor(Math.random() * 50) + 1}x${Math.floor(Math.random() * 50) + 1}cm`,
        createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - Math.random() * 15 * 24 * 60 * 60 * 1000).toISOString()
      }));
      
      setProducts(mockProducts);
      setPagination(prev => ({
        ...prev,
        total: mockProducts.length
      }));
    } catch (error) {
      console.error('获取产品列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchProducts();
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
    fetchProducts();
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({
      productId: '',
      productName: '',
      skuCode: '',
      category: '',
      status: '',
      dateRange: []
    });
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
    fetchProducts();
  };

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

  // 表格列配置
  const columns = [
    {
      title: '产品ID',
      dataIndex: 'id',
      key: 'id',
      width: 120
    },
    {
      title: '产品名称',
      dataIndex: 'productName',
      key: 'productName',
      width: 150
    },
    {
      title: 'SKU码',
      dataIndex: 'skuCode',
      key: 'skuCode',
      width: 120
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      width: 100,
      render: (text) => categoryMap[text] || text
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (text) => (
        <Tag color={statusMap[text].color}>{statusMap[text].text}</Tag>
      )
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
      title: '重量',
      dataIndex: 'weight',
      key: 'weight',
      width: 80,
      render: (text) => `${text.toFixed(2)}kg`
    },
    {
      title: '尺寸',
      dataIndex: 'dimensions',
      key: 'dimensions',
      width: 120
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (text) => new Date(text).toLocaleString()
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/products/${record.id}`)}>查看</Button>
          <Button type="link" icon={<EditOutlined />}>编辑</Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Title level={4}>产品列表</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/products/create')}>添加产品</Button>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 5 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 5 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>产品ID：</span>
            <Input
              placeholder="请输入产品ID"
              value={searchParams.productId}
              onChange={(e) => setSearchParams(prev => ({ ...prev, productId: e.target.value }))}
              style={{ width: 150 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>产品名称：</span>
            <Input
              placeholder="请输入产品名称"
              value={searchParams.productName}
              onChange={(e) => setSearchParams(prev => ({ ...prev, productName: e.target.value }))}
              style={{ width: 150 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>SKU码：</span>
            <Input
              placeholder="请输入SKU码"
              value={searchParams.skuCode}
              onChange={(e) => setSearchParams(prev => ({ ...prev, skuCode: e.target.value }))}
              style={{ width: 150 }}
            />
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>分类：</span>
            <Select
              placeholder="请选择分类"
              value={searchParams.category}
              onChange={(value) => setSearchParams(prev => ({ ...prev, category: value }))}
              style={{ width: 120 }}
            >
              {productCategoryOptions.map(option => (
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
              style={{ width: 120 }}
            >
              {productStatusOptions.map(option => (
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

      {/* 产品表格 */}
      <Card>
        <Table
          columns={columns}
          dataSource={products}
          rowKey="id"
          loading={loading}
          pagination={pagination}
          onChange={handlePaginationChange}
          scroll={{ x: 1200 }}
        />
      </Card>
    </div>
  );
};

export default ProductList;
