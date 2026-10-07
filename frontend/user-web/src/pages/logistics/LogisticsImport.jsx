import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
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
  ReloadOutlined,
  SaveOutlined,
  SyncOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { logisticsImportApi, platformIntegrationApi } from '../../services/api';
import {
  DEFAULT_TENANT_ID,
  FILE_PLATFORM,
  formatDateTime,
  importJobStatusMap,
  logisticsStatusMap,
  logisticsTargetFieldLabelMap,
  platformLabel,
  platformOptions,
  transformLabelMap
} from '../../constants/logistics';

const { Title, Text } = Typography;
const { Dragger } = Upload;

/**
 * 第三方电商平台物流信息导入页：
 * 1) 平台拉取 / 文件上传 获取店铺原始物流数据
 * 2) 字段映射配置（可保存为「租户 + 平台」级模板）
 * 3) 批量导入 / 一键同步，并给出行级结果与导入任务反馈
 */
const LogisticsImport = () => {
  const navigate = useNavigate();

  const [integrations, setIntegrations] = useState([]);
  const [integrationId, setIntegrationId] = useState(undefined);
  const [platformManual, setPlatformManual] = useState(FILE_PLATFORM);
  const [useSampleData, setUseSampleData] = useState(true);

  const [orderNumber, setOrderNumber] = useState('');
  const [trackingNumber, setTrackingNumber] = useState('');
  const [range, setRange] = useState(null);
  const [limit, setLimit] = useState(50);

  const [targetFields, setTargetFields] = useState([]);
  const [transforms, setTransforms] = useState([]);
  const [mappingRows, setMappingRows] = useState([]);
  const [previewRows, setPreviewRows] = useState([]);
  const [previewMeta, setPreviewMeta] = useState(null);

  const [file, setFile] = useState(null);
  const [updateExisting, setUpdateExisting] = useState(true);
  const [result, setResult] = useState(null);
  const [resultModalVisible, setResultModalVisible] = useState(false);

  const [jobs, setJobs] = useState([]);
  const [jobsPagination, setJobsPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const [loadingPreview, setLoadingPreview] = useState(false);
  const [loadingImport, setLoadingImport] = useState(false);
  const [loadingSync, setLoadingSync] = useState(false);
  const [loadingJobs, setLoadingJobs] = useState(false);

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
        logisticsImportApi.getTargetFields(),
        logisticsImportApi.getTransforms(),
        platformIntegrationApi.getIntegrations()
      ]);
      setTargetFields(fields || []);
      setTransforms(transformList || []);
      setIntegrations(integrationList || []);
    } catch {
      message.error('加载物流导入配置失败');
    }
  };

  const loadJobs = async (page = 1, pageSize = 10) => {
    setLoadingJobs(true);
    try {
      const data = await logisticsImportApi.getJobs({
        tenantId: DEFAULT_TENANT_ID,
        page: page - 1,
        size: pageSize
      });
      setJobs(data?.content || []);
      setJobsPagination({
        current: (data?.page ?? page - 1) + 1,
        pageSize: data?.size ?? pageSize,
        total: data?.total ?? 0
      });
    } catch {
      message.error('加载物流导入任务失败');
    } finally {
      setLoadingJobs(false);
    }
  };

  const loadSavedMappings = async (platform) => {
    if (!platform || platform === FILE_PLATFORM) return;
    try {
      const saved = await logisticsImportApi.getFieldMappings(platform, DEFAULT_TENANT_ID);
      if (saved && saved.length > 0) {
        setMappingRows(saved);
        message.info(`已加载 ${platformLabel(platform)} 已保存的字段映射`);
      }
    } catch {
      // 无历史映射时使用拉取结果自动识别的映射
    }
  };

  const fetchParams = () => ({
    tenantId: DEFAULT_TENANT_ID,
    integrationId,
    platform: currentPlatform,
    orderNumber: orderNumber || undefined,
    trackingNumber: trackingNumber || undefined,
    shippedFrom: range?.[0] ? range[0].format('YYYY-MM-DD HH:mm:ss') : undefined,
    shippedTo: range?.[1] ? range[1].format('YYYY-MM-DD HH:mm:ss') : undefined,
    limit,
    useSampleData: integrationId ? useSampleData : true
  });

  const applyPreview = (data) => {
    setMappingRows(data.mappings || []);
    setPreviewRows(data.rows || []);
    setPreviewMeta({
      platform: data.platform,
      fileName: data.fileName,
      sourceFields: data.sourceFields || [],
      totalCount: data.totalCount,
      warnings: data.warnings || [],
      rowErrors: data.rowErrors || []
    });
  };

  const handlePreview = async (overrideMappings) => {
    if (!integrationId && !useSampleData) {
      message.warning('请选择平台集成，或勾选「使用示例数据」后再拉取');
      return;
    }
    setLoadingPreview(true);
    try {
      const payload = { ...fetchParams(), mappings: overrideMappings ?? undefined };
      const data = await logisticsImportApi.preview(payload);
      applyPreview(data);
      message.success(`已获取 ${data.totalCount ?? 0} 条平台物流数据`);
    } catch (error) {
      message.error(`拉取失败：${error?.message || '未知错误'}`);
    } finally {
      setLoadingPreview(false);
    }
  };

  const handleUpload = async (mode) => {
    if (!file) {
      message.warning('请先选择导入文件');
      return;
    }
    setLoadingPreview(mode === 'preview');
    setLoadingImport(mode === 'import');
    try {
      const data = await logisticsImportApi.upload(file, {
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
    const invalid = previewRows.filter((row) => row.errors && row.errors.length > 0).length;
    setLoadingImport(true);
    try {
      const job = await logisticsImportApi.import({
        tenantId: DEFAULT_TENANT_ID,
        integrationId,
        platform: previewMeta?.platform || currentPlatform,
        rows: previewRows,
        updateExisting,
        enableIdempotency: true,
        fileName: previewMeta?.fileName
      });
      showResult(job);
      if (invalid > 0) {
        message.warning(`有 ${invalid} 行存在字段级错误，导入时已跳过`);
      }
    } catch (error) {
      message.error(`导入失败：${error?.message || '未知错误'}`);
    } finally {
      setLoadingImport(false);
    }
  };

  const handleSync = async () => {
    if (!integrationId) {
      message.warning('请选择平台集成后再执行一键同步');
      return;
    }
    setLoadingSync(true);
    try {
      const job = await logisticsImportApi.sync(fetchParams());
      showResult(job);
    } catch (error) {
      message.error(`同步失败：${error?.message || '未知错误'}`);
    } finally {
      setLoadingSync(false);
    }
  };

  const handleSaveMappings = async () => {
    if (!mappingRows.length) {
      message.warning('暂无可保存的字段映射');
      return;
    }
    try {
      await logisticsImportApi.saveFieldMappings({
        tenantId: DEFAULT_TENANT_ID,
        platform: currentPlatform,
        mappings: mappingRows
      });
      message.success('字段映射已保存');
    } catch (error) {
      message.error(`保存失败：${error?.message || '未知错误'}`);
    }
  };

  const handleDownloadTemplate = async () => {
    try {
      const blob = await logisticsImportApi.downloadTemplate();
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      link.download = 'logistics_import_template.csv';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch {
      message.error('下载模板失败');
    }
  };

  const handleViewJob = async (jobId) => {
    try {
      const job = await logisticsImportApi.getJob(jobId);
      setResult(job);
      setResultModalVisible(true);
    } catch {
      message.error('查询导入任务失败');
    }
  };

  const updateMapping = (index, patch) => {
    const rows = [...mappingRows];
    rows[index] = { ...rows[index], ...patch };
    setMappingRows(rows);
  };

  // 后端 TargetFieldDto 字段名为 code / label / required
  const targetFieldOptions = targetFields.length
    ? targetFields.map((item) => ({
        value: item.code,
        label: item.required ? `${item.label} *` : item.label
      }))
    : Object.entries(logisticsTargetFieldLabelMap).map(([code, label]) => ({ value: code, label }));

  const transformOptions = transforms.length
    ? transforms.map((item) => ({ value: item.code, label: item.label }))
    : Object.entries(transformLabelMap).map(([code, label]) => ({ value: code, label }));

  const mappingColumns = [
    { title: '平台原始字段', dataIndex: 'sourceField', width: 220 },
    {
      title: '映射到系统字段',
      dataIndex: 'targetField',
      width: 200,
      render: (value, record, index) => (
        <Select
          allowClear
          style={{ width: '100%' }}
          placeholder="请选择系统字段"
          value={value || undefined}
          options={targetFieldOptions}
          onChange={(next) => updateMapping(index, { targetField: next, enabled: Boolean(next) })}
        />
      )
    },
    {
      title: '转换规则',
      dataIndex: 'transform',
      width: 150,
      render: (value, record, index) => (
        <Select
          style={{ width: '100%' }}
          value={value || 'NONE'}
          options={transformOptions}
          onChange={(next) => updateMapping(index, { transform: next })}
        />
      )
    },
    {
      title: '缺失时默认值',
      dataIndex: 'defaultValue',
      width: 140,
      render: (value, record, index) => (
        <Input
          value={value || ''}
          placeholder="选填"
          onChange={(e) => updateMapping(index, { defaultValue: e.target.value })}
        />
      )
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 70,
      render: (value, record, index) => (
        <Switch
          size="small"
          checked={value !== false}
          onChange={(checked) => updateMapping(index, { enabled: checked })}
        />
      )
    }
  ];

  const previewColumns = [
    { title: '行号', dataIndex: 'rowNumber', width: 60 },
    { title: '平台物流单号', dataIndex: 'externalLogisticsId', width: 170, render: (v) => v || '-' },
    { title: '运单号', dataIndex: 'trackingNumber', width: 160, render: (v) => v || '-' },
    { title: '订单号', dataIndex: 'orderNumber', width: 150, render: (v, r) => v || r.externalOrderId || '-' },
    { title: '渠道', dataIndex: 'channelCode', width: 110, render: (v, r) => v || r.carrier || '-' },
    {
      title: '物流状态',
      dataIndex: 'status',
      width: 100,
      render: (v) => {
        const meta = logisticsStatusMap[v];
        return <Tag color={meta?.color}>{meta?.text || v || '-'}</Tag>;
      }
    },
    { title: '发货时间', dataIndex: 'shippedAt', width: 165, render: (v) => formatDateTime(v) },
    {
      title: '轨迹',
      dataIndex: 'traces',
      width: 70,
      render: (v) => (v && v.length ? `${v.length} 条` : '-')
    },
    {
      title: '行状态',
      width: 260,
      render: (_, record) =>
        record.errors && record.errors.length > 0 ? (
          <Space size={[4, 4]} wrap>
            {record.errors.map((err, idx) => (
              <Tag color="error" key={idx}>
                {err.field ? `${logisticsTargetFieldLabelMap[err.field] || err.field}：` : ''}
                {err.message}
              </Tag>
            ))}
          </Space>
        ) : (
          <Tag color="success">校验通过</Tag>
        )
    }
  ];

  const errorColumns = [
    { title: '行号', dataIndex: 'rowNumber', width: 60 },
    { title: '平台物流单号', dataIndex: 'externalLogisticsId', width: 170, render: (v) => v || '-' },
    { title: '订单号', dataIndex: 'orderNumber', width: 150, render: (v) => v || '-' },
    {
      title: '字段',
      dataIndex: 'field',
      width: 140,
      render: (v) => (v ? logisticsTargetFieldLabelMap[v] || v : '-')
    },
    { title: '错误说明', dataIndex: 'message' }
  ];

  const jobColumns = [
    { title: '任务ID', dataIndex: 'jobId', width: 240, ellipsis: true },
    {
      title: '平台',
      dataIndex: 'platform',
      width: 110,
      render: (v) => platformLabel(v)
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (status, record) => {
        const meta = importJobStatusMap[status] || { color: 'default' };
        return <Tag color={meta.color}>{record.statusLabel || meta.text || status}</Tag>;
      }
    },
    { title: '总数', dataIndex: 'totalCount', width: 70 },
    { title: '成功', dataIndex: 'successCount', width: 70 },
    { title: '失败', dataIndex: 'failureCount', width: 70 },
    { title: '跳过', dataIndex: 'skippedCount', width: 70 },
    { title: '开始时间', dataIndex: 'startedAt', width: 165, render: (v) => formatDateTime(v) },
    {
      title: '操作',
      width: 100,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => handleViewJob(record.jobId)}>
          查看明细
        </Button>
      )
    }
  ];

  const tabItems = [
    {
      key: 'platform',
      label: '平台拉取导入',
      children: (
        <Card size="small">
          <Row gutter={[12, 12]} align="bottom">
            <Col span={7}>
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
                    label: `${platformLabel(item.platformName)}${item.storeUrl ? ` (${item.storeUrl})` : ''}`
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
                <Text strong>订单号</Text>
                <Input
                  allowClear
                  value={orderNumber}
                  onChange={(e) => setOrderNumber(e.target.value)}
                  placeholder="平台/系统订单号"
                />
              </Space>
            </Col>
            <Col span={4}>
              <Space direction="vertical" style={{ width: '100%' }} size={4}>
                <Text strong>运单号</Text>
                <Input
                  allowClear
                  value={trackingNumber}
                  onChange={(e) => setTrackingNumber(e.target.value)}
                  placeholder="承运商运单号"
                />
              </Space>
            </Col>
            <Col span={4}>
              <Space direction="vertical" style={{ width: '100%' }} size={4}>
                <Text strong>拉取条数</Text>
                <InputNumber min={1} max={500} style={{ width: '100%' }} value={limit} onChange={setLimit} />
              </Space>
            </Col>
            <Col span={12}>
              <Space direction="vertical" style={{ width: '100%' }} size={4}>
                <Text strong>发货时间范围</Text>
                <DatePicker.RangePicker
                  showTime
                  style={{ width: '100%' }}
                  value={range}
                  onChange={setRange}
                  placeholder={['发货时间起', '发货时间止']}
                />
              </Space>
            </Col>
            <Col span={12}>
              <Space wrap>
                <Checkbox checked={useSampleData} onChange={(e) => setUseSampleData(e.target.checked)}>
                  使用示例数据
                </Checkbox>
                <Button
                  type="primary"
                  icon={<CloudDownloadOutlined />}
                  loading={loadingPreview}
                  onClick={() => handlePreview()}
                >
                  拉取并预览
                </Button>
                <Button icon={<SyncOutlined />} loading={loadingSync} onClick={handleSync}>
                  一键同步导入
                </Button>
              </Space>
            </Col>
          </Row>

          <div style={{ marginTop: 12 }}>
            <Alert
              type="info"
              showIcon
              message="选择平台集成后，系统按其 storeUrl 调用 {storeUrl}/logistics 拉取店铺物流数据（鉴权：X-API-Key / X-API-Secret 请求头）。未完成平台联调时，勾选「使用示例数据」即可验证「获取 → 映射 → 导入 → 结果反馈」完整链路。「一键同步导入」跳过预览直接落库。"
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
            <p className="ant-upload-hint">支持 JSON / CSV / Excel(xlsx)，单次建议不超过 2000 行</p>
          </Dragger>
          <Space style={{ marginTop: 12 }} wrap>
            <Button icon={<ReloadOutlined />} loading={loadingPreview} onClick={() => handleUpload('preview')}>
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
              平台物流单号已存在时覆盖更新
            </Checkbox>
            <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>
              下载CSV模板
            </Button>
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
            scroll={{ x: 1150 }}
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
  ];

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 8
        }}
      >
        <Title level={4} style={{ margin: 0 }}>
          物流导入
        </Title>
        <Space>
          <Text type="secondary">当前租户：{DEFAULT_TENANT_ID}</Text>
          <Button onClick={() => navigate('/logistics')}>返回物流列表</Button>
          <Button onClick={() => navigate('/logistics/channels')}>维护物流渠道</Button>
        </Space>
      </div>

      <Tabs defaultActiveKey="platform" items={tabItems} />

      {previewMeta && (
        <Card
          size="small"
          title={`字段映射（平台：${platformLabel(previewMeta.platform)}，识别 ${previewMeta.sourceFields.length} 个原始字段）`}
          style={{ marginTop: 8 }}
          extra={
            <Space>
              <Button
                icon={<ReloadOutlined />}
                loading={loadingPreview}
                onClick={() => handlePreview(mappingRows)}
              >
                按当前映射重新预览
              </Button>
              <Button icon={<SaveOutlined />} onClick={handleSaveMappings}>
                保存映射模板
              </Button>
              <Button type="primary" icon={<ImportOutlined />} loading={loadingImport} onClick={handleImport}>
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
            rowKey={(record, index) => `${record.sourceField}-${index}`}
            size="small"
            dataSource={mappingRows}
            columns={mappingColumns}
            pagination={false}
            scroll={{ x: 880 }}
          />

          <div style={{ marginTop: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
              <Text strong>导入预览（{previewMeta.totalCount ?? previewRows.length} 条）</Text>
              <Checkbox checked={updateExisting} onChange={(e) => setUpdateExisting(e.target.checked)}>
                平台物流单号已存在时覆盖更新
              </Checkbox>
            </div>
            <Table
              rowKey={(record, index) => `${record.externalLogisticsId || 'row'}-${index}`}
              size="small"
              dataSource={previewRows}
              columns={previewColumns}
              scroll={{ x: 1250 }}
              pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条` }}
            />
          </div>
        </Card>
      )}

      <Modal
        title="物流导入结果"
        open={resultModalVisible}
        onCancel={() => setResultModalVisible(false)}
        footer={[
          <Button key="close" type="primary" onClick={() => setResultModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={900}
      >
        {result && (
          <div>
            <Row gutter={16}>
              <Col span={6}>
                <Statistic title="总数" value={result.totalCount ?? 0} />
              </Col>
              <Col span={6}>
                <Statistic title="成功" value={result.successCount ?? 0} valueStyle={{ color: '#52c41a' }} />
              </Col>
              <Col span={6}>
                <Statistic title="失败" value={result.failureCount ?? 0} valueStyle={{ color: '#ff4d4f' }} />
              </Col>
              <Col span={6}>
                <Statistic title="跳过" value={result.skippedCount ?? 0} valueStyle={{ color: '#faad14' }} />
              </Col>
            </Row>
            <Alert
              style={{ margin: '12px 0' }}
              type={
                result.status === 'SUCCESS'
                  ? 'success'
                  : result.status === 'PARTIAL_SUCCESS'
                    ? 'warning'
                    : result.status === 'FAILED'
                      ? 'error'
                      : 'info'
              }
              showIcon
              message={
                <Space>
                  <Tag color={(importJobStatusMap[result.status] || {}).color || 'default'}>
                    {result.statusLabel || (importJobStatusMap[result.status] || {}).text || result.status}
                  </Tag>
                  <span>{result.message}</span>
                </Space>
              }
            />
            {result.createdLogisticsOrderIds?.length > 0 && (
              <div style={{ marginBottom: 8 }}>
                <Text type="secondary">新增物流单：</Text>
                <Space size={[4, 4]} wrap>
                  {result.createdLogisticsOrderIds.slice(0, 20).map((id) => (
                    <Tag key={id}>{id}</Tag>
                  ))}
                  {result.createdLogisticsOrderIds.length > 20 && (
                    <Tag>…共 {result.createdLogisticsOrderIds.length} 条</Tag>
                  )}
                </Space>
              </div>
            )}
            {result.updatedLogisticsOrderIds?.length > 0 && (
              <div style={{ marginBottom: 8 }}>
                <Text type="secondary">更新物流单：</Text>
                <Space size={[4, 4]} wrap>
                  {result.updatedLogisticsOrderIds.slice(0, 20).map((id) => (
                    <Tag color="blue" key={id}>
                      {id}
                    </Tag>
                  ))}
                  {result.updatedLogisticsOrderIds.length > 20 && (
                    <Tag>…共 {result.updatedLogisticsOrderIds.length} 条</Tag>
                  )}
                </Space>
              </div>
            )}
            {result.errors && result.errors.length > 0 && (
              <Table
                rowKey={(record, index) => `err-${index}`}
                columns={errorColumns}
                dataSource={result.errors}
                pagination={{ pageSize: 5 }}
                size="small"
                scroll={{ x: 780 }}
              />
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default LogisticsImport;
