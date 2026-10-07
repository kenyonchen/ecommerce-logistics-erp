import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
  message
} from 'antd';
import {
  CloudDownloadOutlined,
  CloudUploadOutlined,
  DownloadOutlined,
  ImportOutlined,
  InboxOutlined,
  SaveOutlined,
  SearchOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { platformIntegrationApi, productImportApi } from '../../services/api';
import {
  DEFAULT_TENANT_ID,
  FILE_PLATFORM,
  platformLabel,
  platformOptions,
  importJobStatusMap,
  targetFieldLabelMap,
  transformLabelMap,
  categoryLabel
} from '../../constants/product';

const { Title, Text } = Typography;
const { Dragger } = Upload;

/**
 * 第三方电商平台商品导入页：
 * 1) 平台拉取 / 文件上传 获取原始数据
 * 2) 字段映射配置（支持保存为租户级模板）
 * 3) 批量导入并给出行级结果与任务反馈
 */
const ProductImport = () => {
  const navigate = useNavigate();

  const [integrations, setIntegrations] = useState([]);
  const [integrationId, setIntegrationId] = useState(undefined);
  const [platformManual, setPlatformManual] = useState(FILE_PLATFORM);
  const [useSampleData, setUseSampleData] = useState(false);
  const [keyword, setKeyword] = useState('');  const [limit, setLimit] = useState(20);
  const [updateExisting, setUpdateExisting] = useState(true);

  const [targetFields, setTargetFields] = useState([]);
  const [transforms, setTransforms] = useState([]);
  const [mappingRows, setMappingRows] = useState([]);
  const [previewRows, setPreviewRows] = useState([]);
  const [previewMeta, setPreviewMeta] = useState(null);

  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [resultModalVisible, setResultModalVisible] = useState(false);
  const [jobs, setJobs] = useState([]);
  const [jobsPagination, setJobsPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const [loadingPreview, setLoadingPreview] = useState(false);
  const [loadingImport, setLoadingImport] = useState(false);
  const [loadingJobs, setLoadingJobs] = useState(false);

  // 当前生效的平台：优先取所选集成的平台
  const currentPlatform = useMemo(() => {
    const matched = integrations.find((item) => item.id === integrationId);
    if (matched?.platformName) return matched.platformName;
    return platformManual || FILE_PLATFORM;
  }, [integrationId, integrations, platformManual]);

  useEffect(() => {
    loadMeta();
    loadJobs(1);
  }, []);

  const loadMeta = async () => {
    try {
      const [fields, transformList, integrationList] = await Promise.all([
        productImportApi.getTargetFields(),
        productImportApi.getTransforms(),
        platformIntegrationApi.getIntegrations()
      ]);
      setTargetFields(fields || []);
      setTransforms(transformList || []);
      setIntegrations(integrationList || []);
    } catch {
      message.error('加载导入配置失败');
    }
  };

  const loadJobs = async (page = 1, pageSize = 10) => {
    setLoadingJobs(true);
    try {
      const data = await productImportApi.getJobs({
        tenantId: DEFAULT_TENANT_ID,
        page: page - 1,
        size: pageSize
      });
      setJobs(data?.content || []);
      setJobsPagination({ current: (data?.page ?? 0) + 1, pageSize: data?.size ?? pageSize, total: data?.total ?? 0 });
    } catch {
      message.error('加载导入任务失败');
    } finally {
      setLoadingJobs(false);
    }
  };

  const loadSavedMappings = async (platform) => {
    if (!platform || platform === FILE_PLATFORM) return;
    try {
      const saved = await productImportApi.getFieldMappings(platform, DEFAULT_TENANT_ID);
      if (saved && saved.length > 0) {
        setMappingRows(saved);
        message.info(`已加载 ${platformLabel(platform)} 已保存的字段映射`);
      }
    } catch {
      // 无历史映射时忽略
    }
  };

  const buildPreviewPayload = () => ({
    tenantId: DEFAULT_TENANT_ID,
    integrationId,
    platform: currentPlatform,
    keyword: keyword || undefined,
    limit,
    useSampleData: !integrationId ? true : useSampleData
  });

  const applyPreview = (data) => {
    setMappingRows(data.mappings || []);
    setPreviewRows(data.rows || []);
    setPreviewMeta({
      platform: data.platform,
      sourceFields: data.sourceFields || [],
      totalCount: data.totalCount,
      warnings: data.warnings || [],
      rowErrors: data.rowErrors || []
    });
  };

  const handlePreviewFromPlatform = async () => {
    if (!integrationId && !useSampleData) {
      message.warning('请选择平台集成，或勾选「使用示例数据」后再拉取');
      return;
    }
    setLoadingPreview(true);
    try {
      const data = await productImportApi.preview(buildPreviewPayload());
      applyPreview(data);
      message.success(`已获取 ${data.totalCount ?? 0} 条平台商品数据`);
    } catch (error) {
      message.error(`拉取失败：${error?.message || '未知错误'}`);
    } finally {
      setLoadingPreview(false);
    }
  };

  const showResult = (job) => {
    setResult(job);
    setResultModalVisible(true);
    loadJobs(1);
  };

  const handleImport = async () => {
    if (!previewRows.length) {
      message.warning('请先获取预览数据');
      return;
    }
    setLoadingImport(true);
    try {
      const job = await productImportApi.import({
        tenantId: DEFAULT_TENANT_ID,
        integrationId,
        platform: previewMeta?.platform || currentPlatform,
        rows: previewRows,
        updateExisting,
        enableIdempotency: true,
        fileName: previewMeta?.fileName
      });
      showResult(job);
    } catch (error) {
      message.error(`导入失败：${error?.message || '未知错误'}`);
    } finally {
      setLoadingImport(false);
    }
  };

  const handleSaveMappings = async () => {
    if (!mappingRows.length) {
      message.warning('暂无可保存的字段映射');
      return;
    }
    try {
      await productImportApi.saveFieldMappings({
        tenantId: DEFAULT_TENANT_ID,
        platform: currentPlatform,
        mappings: mappingRows
      });
      message.success('字段映射已保存');
    } catch (error) {
      message.error(`保存失败：${error?.message || '未知错误'}`);
    }
  };

  const handleUpload = async (mode) => {
    if (!file) {
      message.warning('请先选择导入文件');
      return;
    }
    setLoadingImport(mode === 'import');
    setLoadingPreview(mode === 'preview');
    try {
      const data = await productImportApi.upload(file, {
        tenantId: DEFAULT_TENANT_ID,
        platform: FILE_PLATFORM,
        mode,
        updateExisting
      });
      if (mode === 'preview') {
        applyPreview(data);
        message.success(`文件解析完成，共 ${data.totalCount ?? 0} 条`);
      } else {
        showResult(data);
      }
    } catch (error) {
      message.error(`文件导入失败：${error?.message || '未知错误'}`);
    } finally {
      setLoadingPreview(false);
      setLoadingImport(false);
    }
  };

  const handleViewJob = async (jobId) => {
    try {
      const job = await productImportApi.getJob(jobId);
      setResult(job);
      setResultModalVisible(true);
    } catch {
      message.error('查询导入任务失败');
    }
  };

  const targetFieldOptions = targetFields.length
    ? targetFields.map((item) => ({ value: item.code, label: item.required ? `${item.label} *` : item.label }))
    : Object.entries(targetFieldLabelMap).map(([code, label]) => ({ value: code, label }));

  const transformOptions = transforms.length
    ? transforms.map((item) => ({ value: item.code, label: item.label }))
    : Object.entries(transformLabelMap).map(([code, label]) => ({ value: code, label }));

  const mappingColumns = [
    { title: '平台原始字段', dataIndex: 'sourceField', key: 'sourceField', width: 220 },
    {
      title: '映射到系统字段',
      dataIndex: 'targetField',
      key: 'targetField',
      width: 220,
      render: (value, record, index) => (
        <Select
          allowClear
          style={{ width: '100%' }}
          placeholder="请选择系统字段"
          value={value || undefined}
          options={targetFieldOptions}
          onChange={(next) => {
            const rows = [...mappingRows];
            rows[index] = { ...rows[index], targetField: next, enabled: next ? true : false };
            setMappingRows(rows);
          }}
        />
      )
    },
    {
      title: '转换规则',
      dataIndex: 'transform',
      key: 'transform',
      width: 160,
      render: (value, record, index) => (
        <Select
          style={{ width: '100%' }}
          value={value || 'NONE'}
          options={transformOptions}
          onChange={(next) => {
            const rows = [...mappingRows];
            rows[index] = { ...rows[index], transform: next };
            setMappingRows(rows);
          }}
        />
      )
    },
    {
      title: '缺失时默认值',
      dataIndex: 'defaultValue',
      key: 'defaultValue',
      width: 140,
      render: (value, record, index) => (
        <Input
          value={value || ''}
          placeholder="选填"
          onChange={(e) => {
            const rows = [...mappingRows];
            rows[index] = { ...rows[index], defaultValue: e.target.value };
            setMappingRows(rows);
          }}
        />
      )
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (value, record, index) => (
        <Switch
          size="small"
          checked={value !== false}
          onChange={(checked) => {
            const rows = [...mappingRows];
            rows[index] = { ...rows[index], enabled: checked };
            setMappingRows(rows);
          }}
        />
      )
    }
  ];

  const previewColumns = [
    { title: '行号', dataIndex: 'rowNumber', key: 'rowNumber', width: 70 },
    { title: '商品名称', dataIndex: 'name', key: 'name', width: 200 },
    { title: 'SKU编码', dataIndex: 'skuCode', key: 'skuCode', width: 160 },
    {
      title: '分类',
      dataIndex: 'categoryName',
      key: 'categoryName',
      width: 120,
      render: (text) => categoryLabel(text)
    },
    {
      title: '销售价',
      dataIndex: 'sellingPrice',
      key: 'sellingPrice',
      width: 120,
      render: (text, record) => (text != null ? `${record.currency || ''} ${Number(text).toFixed(2)}` : '-')
    },
    {
      title: '重量(克)',
      dataIndex: 'weightInGrams',
      key: 'weightInGrams',
      width: 110,
      render: (text) => text ?? '-'
    },
    {
      title: '变体属性',
      dataIndex: 'attributes',
      key: 'attributes',
      render: (attributes) =>
        attributes && attributes.length ? attributes.map((a) => `${a.name}:${a.value}`).join('，') : '-'
    },
    {
      title: '行状态',
      key: 'rowStatus',
      width: 220,
      render: (_, record) =>
        record.errors && record.errors.length > 0 ? (
          <Space size={[4, 4]} wrap>
            {record.errors.map((err, idx) => (
              <Tag color="error" key={idx}>
                {err.field ? `${targetFieldLabelMap[err.field] || err.field}：` : ''}
                {err.message}
              </Tag>
            ))}
          </Space>
        ) : (
          <Tag color="success">校验通过</Tag>
        )
    }
  ];

  const jobColumns = [
    {
      title: '任务ID',
      dataIndex: 'jobId',
      key: 'jobId',
      width: 240,
      ellipsis: true
    },
    {
      title: '平台',
      dataIndex: 'platform',
      key: 'platform',
      width: 120,
      render: (text) => platformLabel(text)
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status) => {
        const tag = importJobStatusMap[status] || { text: status, color: 'default' };
        return <Tag color={tag.color}>{tag.text}</Tag>;
      }
    },
    { title: '总数', dataIndex: 'totalCount', key: 'totalCount', width: 80 },
    { title: '成功', dataIndex: 'successCount', key: 'successCount', width: 80 },
    { title: '失败', dataIndex: 'failureCount', key: 'failureCount', width: 80 },
    { title: '跳过', dataIndex: 'skippedCount', key: 'skippedCount', width: 80 },
    { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => handleViewJob(record.jobId)}>
          查看明细
        </Button>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>商品导入</Title>
        <Space>
          <Button icon={<DownloadOutlined />} onClick={() => navigate('/products')}>返回商品列表</Button>
        </Space>
      </div>

      <Tabs
        defaultActiveKey="platform"
        items={[
          {
            key: 'platform',
            label: '平台拉取导入',
            children: (
              <Card size="small">
                <Row gutter={16} align="bottom">
                  <Col span={8}>
                    <Space direction="vertical" style={{ width: '100%' }} size={4}>
                      <Text strong>第三方平台集成</Text>
                      <Select
                        allowClear
                        style={{ width: '100%' }}
                        placeholder="请选择已配置的平台集成"
                        value={integrationId}
                        onChange={(value) => {
                          setIntegrationId(value);
                          const matched = integrations.find((item) => item.id === value);
                          if (matched?.platformName) loadSavedMappings(matched.platformName);
                        }}
                        options={integrations.map((item) => ({
                          value: item.id,
                          label: `${platformLabel(item.platformName)} ${item.storeUrl ? `(${item.storeUrl})` : ''}`
                        }))}
                      />
                    </Space>
                  </Col>
                  <Col span={5}>
                    <Space direction="vertical" style={{ width: '100%' }} size={4}>
                      <Text strong>平台（无集成时使用）</Text>
                      <Select
                        style={{ width: '100%' }}
                        value={platformManual}
                        onChange={setPlatformManual}
                        options={[{ value: FILE_PLATFORM, label: '文件上传' }, ...platformOptions]}
                      />
                    </Space>
                  </Col>
                  <Col span={4}>
                    <Space direction="vertical" style={{ width: '100%' }} size={4}>
                      <Text strong>商品关键字</Text>
                      <Input
                        allowClear
                        value={keyword}
                        onChange={(e) => setKeyword(e.target.value)}
                        placeholder="平台侧过滤条件"
                      />
                    </Space>
                  </Col>
                  <Col span={3}>
                    <Space direction="vertical" style={{ width: '100%' }} size={4}>
                      <Text strong>拉取条数</Text>
                      <InputNumber min={1} max={200} style={{ width: '100%' }} value={limit} onChange={setLimit} />
                    </Space>
                  </Col>
                  <Col span={4}>
                    <Space>
                      <Checkbox checked={useSampleData} onChange={(e) => setUseSampleData(e.target.checked)}>
                        使用示例数据
                      </Checkbox>
                      <Button
                        type="primary"
                        icon={<CloudDownloadOutlined />}
                        loading={loadingPreview}
                        onClick={handlePreviewFromPlatform}
                      >
                        拉取并预览
                      </Button>
                    </Space>
                  </Col>
                </Row>

                <div style={{ marginTop: 12 }}>
                  <Alert
                    type="info"
                    showIcon
                    message="选择平台集成后，系统会按其 storeUrl 调用 {storeUrl}/products 拉取商品（鉴权：X-API-Key / X-API-Secret 请求头）。未完成平台联调时，可勾选「使用示例数据」验证完整导入链路。"
                  />
                </div>
              </Card>
            )
          },
          {
            key: 'file',
            label: '文件上传导入',
            children: (
              <Card size="small">
                <Dragger
                  accept=".csv,.json,.txt,.xlsx,.xls"
                  maxCount={1}
                  beforeUpload={(newFile) => {
                    setFile(newFile);
                    return false;
                  }}
                  onRemove={() => setFile(null)}
                >
                  <p className="ant-upload-drag-icon">
                    <InboxOutlined />
                  </p>
                  <p className="ant-upload-text">点击或拖拽文件到此处上传</p>
                  <p className="ant-upload-hint">
                    支持 JSON 与 CSV（首行为表头）格式，单次建议不超过 2000 行
                  </p>
                </Dragger>
                <Space style={{ marginTop: 12 }}>
                  <Button icon={<SearchOutlined />} loading={loadingPreview} onClick={() => handleUpload('preview')}>
                    解析预览
                  </Button>
                  <Button
                    type="primary"
                    icon={<CloudUploadOutlined />}
                    loading={loadingImport}
                    onClick={() => handleUpload('import')}
                  >
                    直接导入
                  </Button>
                  <Checkbox checked={updateExisting} onChange={(e) => setUpdateExisting(e.target.checked)}>
                    SKU已存在时覆盖更新
                  </Checkbox>
                </Space>
              </Card>
            )
          },
          {
            key: 'jobs',
            label: '导入任务',
            children: (
              <Card size="small">
                <Table
                  rowKey="jobId"
                  size="small"
                  loading={loadingJobs}
                  dataSource={jobs}
                  columns={jobColumns}
                  scroll={{ x: 1100 }}
                  pagination={{
                    current: jobsPagination.current,
                    pageSize: jobsPagination.pageSize,
                    total: jobsPagination.total,
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条`
                  }}
                  onChange={(pageInfo) => loadJobs(pageInfo.current, pageInfo.pageSize)}
                />
              </Card>
            )
          }
        ]}
      />

      {previewMeta && (
        <Card
          size="small"
          title={`字段映射（平台：${platformLabel(previewMeta.platform)}，共识别 ${previewMeta.sourceFields.length} 个原始字段）`}
          style={{ marginTop: 8 }}
          extra={
            <Space>
              <Button icon={<SaveOutlined />} onClick={handleSaveMappings}>
                保存映射模板
              </Button>
              <Button
                type="primary"
                icon={<ImportOutlined />}
                loading={loadingImport}
                onClick={handleImport}
              >
                确认导入（{previewRows.length} 条）
              </Button>
            </Space>
          }
        >
          {previewMeta.warnings.length > 0 && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 8 }}
              message={previewMeta.warnings.join('；')}
            />
          )}
          <Table
            rowKey="sourceField"
            size="small"
            dataSource={mappingRows}
            columns={mappingColumns}
            pagination={false}
            scroll={{ x: 900 }}
          />

          <div style={{ marginTop: 16 }}>
            <Text strong>导入预览</Text>
            <div style={{ float: 'right' }}>
              <Checkbox checked={updateExisting} onChange={(e) => setUpdateExisting(e.target.checked)}>
                SKU已存在时覆盖更新
              </Checkbox>
            </div>
            <Table
              rowKey="rowNumber"
              size="small"
              style={{ marginTop: 8 }}
              dataSource={previewRows}
              columns={previewColumns}
              scroll={{ x: 1200 }}
              pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 行` }}
            />
          </div>
        </Card>
      )}

      <Modal
        title="导入结果"
        open={resultModalVisible}
        width={900}
        footer={[
          <Button key="close" onClick={() => setResultModalVisible(false)}>
            关闭
          </Button>
        ]}
        onCancel={() => setResultModalVisible(false)}
      >
        {result && (
          <>
            <Alert
              style={{ marginBottom: 12 }}
              type={
                result.status === 'SUCCESS'
                  ? 'success'
                  : result.status === 'PARTIAL_SUCCESS'
                    ? 'warning'
                    : 'error'
              }
              showIcon
              message={`${result.statusLabel}：共 ${result.totalCount} 行，成功 ${result.successCount} 行，失败 ${result.failureCount} 行，跳过 ${result.skippedCount} 行`}
              description={result.message}
            />
            <DescriptionsLike result={result} />
            {result.errors && result.errors.length > 0 && (
              <Table
                rowKey={(record, index) => `${record.rowNumber}-${index}`}
                size="small"
                dataSource={result.errors}
                pagination={{ pageSize: 5 }}
                scroll={{ x: 700 }}
                columns={[
                  { title: '行号', dataIndex: 'rowNumber', key: 'rowNumber', width: 80 },
                  { title: '平台商品ID', dataIndex: 'externalProductId', key: 'externalProductId', width: 160 },
                  { title: 'SKU编码', dataIndex: 'skuCode', key: 'skuCode', width: 160 },
                  {
                    title: '字段',
                    dataIndex: 'field',
                    key: 'field',
                    width: 140,
                    render: (text) => targetFieldLabelMap[text] || text || '-'
                  },
                  { title: '错误原因', dataIndex: 'message', key: 'message' }
                ]}
              />
            )}
          </>
        )}
      </Modal>
    </div>
  );
};

/**
 * 结果概览：展示新增/更新的商品ID数量
 */
const DescriptionsLike = ({ result }) => (
  <Space style={{ marginBottom: 12 }} wrap>
    <Tag color="blue">任务ID：{result.jobId}</Tag>
    <Tag color="blue">平台：{platformLabel(result.platform)}</Tag>
    <Tag color="green">新增 {result.createdProductIds?.length || result.successCount || 0} 个商品</Tag>
    <Tag color="orange">更新 {result.updatedProductIds?.length || 0} 个商品</Tag>
    <Tag>开始时间：{result.startedAt || '-'}</Tag>
  </Space>
);

export default ProductImport;
