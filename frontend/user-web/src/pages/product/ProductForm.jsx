import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Divider, Form, Input, InputNumber, Row, Select, Space, Switch, Table, Typography, message } from 'antd';
import { ArrowLeftOutlined, DeleteOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { productApi } from '../../services/api';
import {
  DEFAULT_CURRENCY,
  kgToGram,
  gramToKg,
  parseAttributes,
  formatAttributes,
  productCategoryOptions
} from '../../constants/product';

const { Title } = Typography;
const { TextArea } = Input;

const currencyOptions = [
  { value: 'CNY', label: 'CNY 人民币' },
  { value: 'USD', label: 'USD 美元' },
  { value: 'EUR', label: 'EUR 欧元' },
  { value: 'GBP', label: 'GBP 英镑' },
  { value: 'JPY', label: 'JPY 日元' }
];

/**
 * 商品新增/编辑共用表单（SKU以列表形式维护）
 */
const ProductForm = ({ mode = 'create', productId }) => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (mode !== 'edit' || !productId) return;
    const load = async () => {
      setLoading(true);
      try {
        const product = await productApi.getProductById(productId);
        form.setFieldsValue({
          name: product.name,
          description: product.description,
          categoryName: product.categoryName,
          active: product.active,
          skus: (product.skus || []).map((sku) => ({
            skuCode: sku.skuCode,
            skuName: sku.skuName,
            sellingPrice: sku.sellingPrice != null ? Number(sku.sellingPrice) : undefined,
            costPrice: sku.costPrice != null ? Number(sku.costPrice) : undefined,
            currency: sku.currency || DEFAULT_CURRENCY,
            weight: gramToKg(sku.weightInGrams),
            attributes: formatAttributes(sku.attributes) === '-' ? '' : formatAttributes(sku.attributes),
            active: sku.active !== false
          }))
        });
      } catch {
        message.error('加载商品信息失败');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [mode, productId, form]);

  const handleSubmit = async (values) => {
    const skus = (values.skus || [])
      .filter((sku) => sku && sku.skuCode)
      .map((sku) => ({
        skuCode: sku.skuCode,
        skuName: sku.skuName || sku.skuCode,
        sellingPrice: sku.sellingPrice ?? 0,
        costPrice: sku.costPrice ?? 0,
        currency: sku.currency || DEFAULT_CURRENCY,
        weightInGrams: kgToGram(sku.weight),
        attributes: parseAttributes(sku.attributes),
        active: sku.active !== false
      }));

    // 编辑态护栏：后端 replaceSkus 为整体覆盖语义，提交空 SKU 集合会删光该商品全部 SKU。
    // 表单行缺失或全部被过滤（如未填SKU编码）时禁止提交（ISS-069）。
    if (mode === 'edit' && skus.length === 0) {
      message.error('SKU列表为空：编辑保存会用本次提交整体覆盖原有SKU，将删除该商品的全部SKU。请至少填写一行有效SKU。');
      return;
    }

    const payload = {
      // 不传 tenantId：由后端从 JWT 租户上下文解析（ISS-051），避免硬编码默认租户
      name: values.name,
      description: values.description,
      categoryName: values.categoryName,
      active: values.active !== false,
      skus
    };

    setSubmitting(true);
    try {
      if (mode === 'edit') {
        await productApi.updateProduct(productId, payload);
        message.success('商品更新成功');
      } else {
        await productApi.createProduct(payload);
        message.success('商品创建成功');
      }
      navigate('/products');
    } catch {
      message.error(mode === 'edit' ? '更新商品失败' : '创建商品失败');
    } finally {
      setSubmitting(false);
    }
  };

  // SKU表格直接以 Form.List 的 fields 作为行记录（每项为 { name, key }），
  // render 的第二参即该记录，故 field.name 为有效的行索引（ISS-069）。
  const skuColumns = [
    {
      title: 'SKU编码 *',
      dataIndex: 'skuCode',
      key: 'skuCode',
      width: 170,
      render: (_, field) => (
        <Form.Item name={[field.name, 'skuCode']} style={{ margin: 0 }} rules={[{ required: true, message: '请输入SKU编码' }]}>
          <Input placeholder="如 SKU-001" />
        </Form.Item>
      )
    },
    {
      title: 'SKU名称',
      dataIndex: 'skuName',
      key: 'skuName',
      width: 170,
      render: (_, field) => (
        <Form.Item name={[field.name, 'skuName']} style={{ margin: 0 }}>
          <Input placeholder="变体标题" />
        </Form.Item>
      )
    },
    {
      title: '销售价 *',
      dataIndex: 'sellingPrice',
      key: 'sellingPrice',
      width: 130,
      render: (_, field) => (
        <Form.Item name={[field.name, 'sellingPrice']} style={{ margin: 0 }} rules={[{ required: true, message: '请输入销售价' }]}>
          <InputNumber min={0} step={0.01} style={{ width: '100%' }} placeholder="0.00" />
        </Form.Item>
      )
    },
    {
      title: '成本价',
      dataIndex: 'costPrice',
      key: 'costPrice',
      width: 130,
      render: (_, field) => (
        <Form.Item name={[field.name, 'costPrice']} style={{ margin: 0 }}>
          <InputNumber min={0} step={0.01} style={{ width: '100%' }} placeholder="0.00" />
        </Form.Item>
      )
    },
    {
      title: '币种',
      dataIndex: 'currency',
      key: 'currency',
      width: 130,
      render: (_, field) => (
        <Form.Item name={[field.name, 'currency']} initialValue={DEFAULT_CURRENCY} style={{ margin: 0 }}>
          <Select options={currencyOptions} />
        </Form.Item>
      )
    },
    {
      title: '重量(KG)',
      dataIndex: 'weight',
      key: 'weight',
      width: 120,
      render: (_, field) => (
        <Form.Item name={[field.name, 'weight']} style={{ margin: 0 }}>
          <InputNumber min={0} step={0.1} style={{ width: '100%' }} placeholder="系统按克存储" />
        </Form.Item>
      )
    },
    {
      title: '变体属性',
      dataIndex: 'attributes',
      key: 'attributes',
      width: 200,
      render: (_, field) => (
        <Form.Item name={[field.name, 'attributes']} style={{ margin: 0 }}>
          <Input placeholder="如 颜色:黑色,容量:128GB" />
        </Form.Item>
      )
    },
    {
      title: '启用',
      dataIndex: 'active',
      key: 'active',
      width: 80,
      render: (_, field) => (
        <Form.Item name={[field.name, 'active']} initialValue={true} valuePropName="checked" style={{ margin: 0 }}>
          <Switch size="small" />
        </Form.Item>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 8 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/products')}>返回列表</Button>
        <Title level={4} style={{ margin: 0 }}>{mode === 'edit' ? '编辑商品' : '新增商品'}</Title>
      </div>

      <Card loading={loading}>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ active: true, currency: DEFAULT_CURRENCY, skus: [{}] }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="商品名称" name="name" rules={[{ required: true, message: '请输入商品名称' }]}>
                <Input placeholder="请输入商品名称" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item label="分类" name="categoryName">
                <Select allowClear placeholder="请选择分类" options={productCategoryOptions} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item label="启用状态" name="active" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="停用" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="商品描述" name="description">
            <TextArea rows={3} placeholder="请输入商品描述" />
          </Form.Item>

          <Divider orientation="left">SKU明细</Divider>

          <Form.List name="skus">
            {(fields, { add, remove }) => (
              <>
                <Form.Item>
                  <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({})} style={{ width: '100%' }}>
                    添加SKU
                  </Button>
                </Form.Item>
                {fields.length > 0 && (
                  <Table
                    size="small"
                    rowKey={(record) => record.key}
                    dataSource={fields}
                    pagination={false}
                    scroll={{ x: 1200 }}
                    columns={[
                      ...skuColumns,
                      {
                        title: '操作',
                        key: 'operation',
                        width: 80,
                        fixed: 'right',
                        render: (_, field) => (
                          <Button
                            type="link"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() => remove(field.name)}
                          />
                        )
                      }
                    ]}
                  />
                )}
              </>
            )}
          </Form.List>

          <Divider />

          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button onClick={() => navigate('/products')}>取消</Button>
            <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={submitting}>
              保存
            </Button>
          </Space>
        </Form>
      </Card>
    </div>
  );
};

export default ProductForm;
