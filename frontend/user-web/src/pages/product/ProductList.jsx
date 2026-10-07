import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Input, Select, Tag, Card, Typography, Popconfirm, message } from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  EditOutlined,
  DeleteOutlined,
  ImportOutlined,
  CheckCircleOutlined,
  StopOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { productApi } from '../../services/api';
import {
  DEFAULT_TENANT_ID,
  DEFAULT_CURRENCY,
  productStatusOptions,
  productCategoryOptions,
  categoryLabel,
  platformOptions,
  platformLabel,
  getProductStatusTag,
  gramToKg
} from '../../constants/product';

const { Title } = Typography;

const ProductList = () => {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [searchParams, setSearchParams] = useState({
    keyword: '',
    categoryName: '',
    active: undefined,
    sourcePlatform: '',
    skuCode: ''
  });

  // 列表：服务端分页 + 条件查询
  const fetchProducts = async (params = {}) => {
    setLoading(true);
    try {
      const page = params.current || pagination.current;
      const pageSize = params.pageSize || pagination.pageSize;
      const query = {
        tenantId: DEFAULT_TENANT_ID,
        page: page - 1,
        size: pageSize,
        sort: 'createdAt,desc'
      };
      if (searchParams.keyword) query.keyword = searchParams.keyword;
      if (searchParams.categoryName) query.categoryName = searchParams.categoryName;
      if (searchParams.active !== undefined) query.active = searchParams.active;
      if (searchParams.sourcePlatform) query.sourcePlatform = searchParams.sourcePlatform;
      if (searchParams.skuCode) query.skuCode = searchParams.skuCode;

      const data = await productApi.getProducts(query);
      setProducts(data?.content || []);
      setPagination((prev) => ({
        ...prev,
        current: (data?.page ?? 0) + 1,
        pageSize: data?.size ?? pageSize,
        total: data?.total ?? 0
      }));
    } catch {
      message.error('获取商品列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProducts({ current: 1 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleTableChange = (pageInfo) => {
    fetchProducts({ current: pageInfo.current, pageSize: pageInfo.pageSize });
  };

  const handleSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
    fetchProducts({ current: 1 });
  };

  const handleReset = () => {
    const reset = { keyword: '', categoryName: '', active: undefined, sourcePlatform: '', skuCode: '' };
    setSearchParams(reset);
    setPagination((prev) => ({ ...prev, current: 1 }));
    setLoading(true);
    productApi
      .getProducts({ tenantId: DEFAULT_TENANT_ID, page: 0, size: pagination.pageSize, sort: 'createdAt,desc' })
      .then((data) => {
        setProducts(data?.content || []);
        setPagination((prev) => ({ ...prev, current: 1, total: data?.total ?? 0 }));
      })
      .catch(() => message.error('获取商品列表失败'))
      .finally(() => setLoading(false));
  };

  const handleDelete = async (id) => {
    try {
      await productApi.deleteProduct(id, DEFAULT_TENANT_ID);
      message.success('商品删除成功');
      fetchProducts();
    } catch {
      message.error('删除商品失败');
    }
  };

  const handleToggleActive = async (record) => {
    try {
      if (record.active) {
        await productApi.deactivateProduct(record.id);
      } else {
        await productApi.activateProduct(record.id);
      }
      message.success(record.active ? '商品已停用' : '商品已启用');
      fetchProducts();
    } catch {
      message.error('操作失败');
    }
  };

  const handleBatchActive = async (active) => {
    if (!selectedRowKeys.length) {
      message.warning('请先选择商品');
      return;
    }
    try {
      const count = await productApi.batchChangeActive(selectedRowKeys, active, DEFAULT_TENANT_ID);
      message.success(`已${active ? '启用' : '停用'} ${count} 个商品`);
      setSelectedRowKeys([]);
      fetchProducts();
    } catch {
      message.error('批量操作失败');
    }
  };

  const handleBatchDelete = async () => {
    if (!selectedRowKeys.length) {
      message.warning('请先选择商品');
      return;
    }
    try {
      const count = await productApi.batchDelete(selectedRowKeys, DEFAULT_TENANT_ID);
      message.success(`已删除 ${count} 个商品`);
      setSelectedRowKeys([]);
      fetchProducts();
    } catch {
      message.error('批量删除失败');
    }
  };

  // 列表展示用：取首个SKU的销售价与SKU编码
  const firstSku = (record) => (record.skus && record.skus.length > 0 ? record.skus[0] : null);

  const columns = [
    {
      title: '商品名称',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (text, record) => (
        <a onClick={() => navigate(`/products/${record.id}`)}>{text}</a>
      )
    },
    {
      title: 'SKU编码',
      key: 'skuCode',
      width: 160,
      render: (_, record) => firstSku(record)?.skuCode || '-'
    },
    {
      title: '分类',
      dataIndex: 'categoryName',
      key: 'categoryName',
      width: 120,
      render: (text) => categoryLabel(text)
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
      title: '销售价',
      key: 'sellingPrice',
      width: 110,
      render: (_, record) => {
        const sku = firstSku(record);
        return sku?.sellingPrice != null
          ? `${sku.currency || DEFAULT_CURRENCY} ${Number(sku.sellingPrice).toFixed(2)}`
          : '-';
      }
    },
    {
      title: '重量(KG)',
      key: 'weight',
      width: 100,
      render: (_, record) => {
        const kg = gramToKg(firstSku(record)?.weightInGrams);
        return kg != null ? Number(kg).toFixed(2) : '-';
      }
    },
    {
      title: '来源平台',
      dataIndex: 'sourcePlatform',
      key: 'sourcePlatform',
      width: 120,
      render: (text) => (text ? platformLabel(text) : <Tag>手工创建</Tag>)
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/products/${record.id}`)}>
            查看
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => navigate(`/products/${record.id}/edit`)}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            icon={record.active ? <StopOutlined /> : <CheckCircleOutlined />}
            onClick={() => handleToggleActive(record)}
          >
            {record.active ? '停用' : '启用'}
          </Button>
          <Popconfirm title="确定删除该商品吗？" onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>商品列表</Title>
        <Space>
          <Button icon={<ImportOutlined />} onClick={() => navigate('/products/import')}>商品导入</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/products/create')}>
            新增商品
          </Button>
        </Space>
      </div>

      <Card size="small" style={{ marginBottom: 8 }}>
        <Space wrap>
          <Input
            allowClear
            placeholder="关键字(名称/SKU)"
            value={searchParams.keyword}
            onChange={(e) => setSearchParams((prev) => ({ ...prev, keyword: e.target.value }))}
            style={{ width: 180 }}
          />
          <Input
            allowClear
            placeholder="SKU编码"
            value={searchParams.skuCode}
            onChange={(e) => setSearchParams((prev) => ({ ...prev, skuCode: e.target.value }))}
            style={{ width: 160 }}
          />
          <Select
            allowClear
            placeholder="分类"
            options={productCategoryOptions}
            value={searchParams.categoryName || undefined}
            onChange={(value) => setSearchParams((prev) => ({ ...prev, categoryName: value || '' }))}
            style={{ width: 140 }}
          />
          <Select
            allowClear
            placeholder="状态"
            options={productStatusOptions}
            value={searchParams.active}
            onChange={(value) => setSearchParams((prev) => ({ ...prev, active: value }))}
            style={{ width: 120 }}
          />
          <Select
            allowClear
            placeholder="来源平台"
            options={platformOptions}
            value={searchParams.sourcePlatform || undefined}
            onChange={(value) => setSearchParams((prev) => ({ ...prev, sourcePlatform: value || '' }))}
            style={{ width: 150 }}
          />
          <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>查询</Button>
        </Space>
      </Card>

      <Card size="small">
        {selectedRowKeys.length > 0 && (
          <Space style={{ marginBottom: 8 }}>
            <span>已选 {selectedRowKeys.length} 项</span>
            <Button size="small" icon={<CheckCircleOutlined />} onClick={() => handleBatchActive(true)}>
              批量启用
            </Button>
            <Button size="small" icon={<StopOutlined />} onClick={() => handleBatchActive(false)}>
              批量停用
            </Button>
            <Popconfirm title={`确定删除选中的 ${selectedRowKeys.length} 个商品吗？`} onConfirm={handleBatchDelete} okText="确定" cancelText="取消">
              <Button size="small" danger icon={<DeleteOutlined />}>批量删除</Button>
            </Popconfirm>
          </Space>
        )}
        <Table
          columns={columns}
          dataSource={products}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1400 }}
          rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={handleTableChange}
        />
      </Card>
    </div>
  );
};

export default ProductList;
