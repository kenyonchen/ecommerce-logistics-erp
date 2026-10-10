import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Divider,
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
  CloudUploadOutlined,
  DownloadOutlined,
  ImportOutlined,
  InboxOutlined,
  SaveOutlined,
  SearchOutlined
} from '@ant-design/icons';
import { financeImportApi, platformIntegrationApi, currentTenantId } from '../../services/api';
import {
  DEFAULT_TENANT_ID,
  FILE_PLATFORM,
  platformLabel,
  platformOptions,
  importJobStatusMap,
  transactionTypeMap,
  transactionStatusMap,
  financeTargetFieldLabelMap,
  formatAmount
} from '../../constants/finance';
import { transformLabelMap } from '../../constants/order';

const { Title, Text } = Typography;
const { Option } = Select;
const { Dragger } = Upload;

/**
 * 第三方平台财务流水（结算/回款）导入页：
 * 1) 平台拉取 / 文件上传 获取原始流水数据
 * 2) 字段映射配置（支持保存为租户级模板）
 * 3) 批量导入并给出行级结果与任务反馈
 */
const FinanceImport = () => {
  const [activeTab, setActiveTab] = useState('platform');

  // 数据来源参数
  const [integrations, setIntegrations] = useState([]);
  const [integrationId, setIntegrationId] = useState(undefined);
  const [platformManual, setPlatformManual] = useState(FILE_PLATFORM);
  const [useSampleData, setUseSampleData] = useState(true);
  const [referenceId, setReferenceId] = useState('');
  const [limit, setLimit] = useState(20);
  const [updateExisting, setUpdateExisting] = useState(true);

  // 字段映射
  const [targetFields, setTargetFields] = useState([]);
  const [transforms, setTransforms] = useState([]);
  const [transactionTypes, setTransactionTypes] = useState([]);
  const [mappingRows, setMappingRows] = useState([]);

  // 预览
  const [previewRows, setPreviewRows] = useState([]);
  const [previewMeta, setPreviewMeta] = useState(null);

  // 文件上传
  const [file, setFile] = useState(null);
  const [uploadMode, setUploadMode] = useState('preview');

  // 结果反馈
  const [result, setResult] = useState(null);
  const [resultModalVisible, setResultModalVisible] = useState(false);
  const [jobs, setJobs] = useState([]);
  const [jobsPagination, setJobsPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const [loadingPreview, setLoadingPreview] = useState(false);
  const [loadingImport, setLoadingImport] = useState(false);
  const [loadingJobs, setLoadingJobs] = useState(false);
  const [loadingSaveMapping, setLoadingSaveMapping] = useState(false);

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
      const [fields, transformList, typeList, integrationList] = await Promise.all([
        financeImportApi.getTargetFields(),
        financeImportApi.getTransforms(),
        financeImportApi.getTransactionTypes(),
        platformIntegrationApi.getIntegrations()
      ]);
      setTargetFields(fields || []);
      setTransforms(transformList || []);
      setTransactionTypes(
        (typeList || []).map((item) => (typeof item === 'string' ? { code: item, label: transactionTypeMap[item]?.text || item } : item))
      );
      setIntegrations(integrationList || []);
    } catch {
      message.error('加载财务导入配置失败');
    }
  };

  const loadJobs = async (page = 1, pageSize = 10) => {
    setLoadingJobs(true);
    try {
      const data = await financeImportApi.getJobs({
        tenantId: DEFAULT_TENANT_ID,
        page: page - 1,
        size: pageSize
      });
      setJobs(data?.content || []);
      setJobsPagination({
        current: (data?.page ?? 0) + 1,
        pageSize: data?.size ?? pageSize,
        total: data?.total ?? 0
      });
    } catch {
      message.error('加载导入任务失败');
    } finally {
      setLoadingJobs(false);
    }
  };

  const loadSavedMappings = async (platform) => {
    if (!platform) return;
    try {
      const saved = await financeImportApi.getFieldMappings(platform, DEFAULT_TENANT_ID);
      if (saved && saved.length > 0) {
        setMappingRows(saved);
        message.info(`已加载 ${platformLabel(platform)} 已保存的字段映射`);
      }
    } catch {
      // 未配置映射时静默处理
    }
  };

  useEffect(() => {
    if (currentPlatform && currentPlatform !== FILE_PLATFORM) {
      loadSavedMappings(currentPlatform);
    }
  }, [currentPlatform]);

  const activeMappings = () =>
    mappingRows
      .filter((row) => row.sourceField && row.targetField && row.enabled !== false)
      .map((row) => ({
        sourceField: row.sourceField,
        targetField: row.targetField,
        transform: row.transform || 'NONE',
        defaultValue: row.defaultValue,
        enabled: row.enabled !== false
      }));

  const handlePreview = async () => {
    setLoadingPreview(true);
    try {
      const payload = {
        tenantId: DEFAULT_TENANT_ID,
        integrationId: integrationId || undefined,
        platform: currentPlatform,
        useSampleData,
        externalTransactionId: referenceId || undefined,
        limit,
        mappings: activeMappings()
      };
      const data = await financeImportApi.preview(payload);
      setPreviewRows(data?.rows || []);
      setPreviewMeta({
        totalCount: data?.totalCount ?? 0,
        sourceFields: data?.sourceFields || [],
        warnings: data?.warnings || [],
        rowErrors: data?.rowErrors || [],
        fileName: data?.fileName
      });
      if (mappingRows.length === 0 && (data?.mappings || []).length > 0) {
        setMappingRows(data.mappings);
      }
      message.success(`预览完成，共 ${data?.totalCount ?? 0} 条流水`);
    } catch (err) {
      console.error('预览失败:', err);
    } finally {
      setLoadingPreview(false);
    }
  };

  const handleImport = async (rows, fileName) => {
    setLoadingImport(true);
    try {
      // 契约冻结：POST /finance/import body 为 { tenantId, platform, rows, updateExisting, fileName }
      const payload = {
        tenantId: DEFAULT_TENANT_ID,
        platform: currentPlatform,
        rows: rows && rows.length > 0 ? rows : undefined,
        updateExisting,
        fileName
      };
      const data = await financeImportApi.import(payload);
      setResult(data);
      setResultModalVisible(true);
      loadJobs(1);
    } catch (err) {
      console.error('导入失败:', err);
    } finally {
      setLoadingImport(false);
    }
  };

  const handleSaveMappings = async () => {
    setLoadingSaveMapping(true);
    try {
      const saved = await financeImportApi.saveFieldMappings({
        tenantId: DEFAULT_TENANT_ID,
        platform: currentPlatform || FILE_PLATFORM,
        mappings: activeMappings()
      });
      setMappingRows(saved || []);
      message.success('字段映射已保存');
    } catch (err) {
      console.error('保存字段映射失败:', err);
    } finally {
      setLoadingSaveMapping(false);
    }
  };

  const handleUpload = async () => {
    if (!file) {
      message.warning('请先选择要导入的文件');
      return;
    }
    setLoadingPreview(true);
    try {
      const data = await financeImportApi.upload(file, {
        tenantId: DEFAULT_TENANT_ID,
        platform: currentPlatform,
        mode: uploadMode,
        updateExisting
      });
      if (uploadMode === 'import') {
        setResult(data);
        setResultModalVisible(true);
        loadJobs(1);
      } else {
        setPreviewRows(data?.rows || []);
        setPreviewMeta({
          totalCount: data?.totalCount ?? 0,
          sourceFields: data?.sourceFields || [],
          warnings: data?.warnings || [],
          rowErrors: data?.rowErrors || [],
          fileName: data?.fileName || file?.name
        });
        if (mappingRows.length === 0 && (data?.mappings || []).length > 0) {
          setMappingRows(data.mappings);
        }
        message.success(`文件解析完成，共 ${data?.totalCount ?? 0} 条流水`);
      }
    } catch (err) {
      console.error('文件导入失败:', err);
    } finally {
      setLoadingPreview(false);
    }
  };

  const handleDownloadTemplate = async () => {
    try {
      const blob = await financeImportApi.downloadTemplate();
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      link.download = 'finance_import_template.csv';
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
      const data = await financeImportApi.getJob(jobId);
      setResult(data);
      setResultModalVisible(true);
    } catch {
      message.error('查询任务详情失败');
    }
  };

  const updateMappingRow = (index, patch) => {
    setMappingRows((prev) => prev.map((row, idx) => (idx === index ? { ...row, ...patch } : row)));
  };

  // 字段映射表
  const mappingColumns = [
    {
      title: '平台原始字段',
      dataIndex: 'sourceField',
      width: 200,
      render: (value, record, index) => (
        <Input
          value={value}
          placeholder="如 settlement-id"
          onChange={(e) => updateMappingRow(index, { sourceField: e.target.value })}
        />
      )
    },
    {
      title: '系统标准字段',
      dataIndex: 'targetField',
      width: 220,
      render: (value, record, index) => (
        <Select
          style={{ width: '100%' }}
          value={value || undefined}
          placeholder="请选择目标字段"
          showSearch
          optionFilterProp="children"
          onChange={(val) => updateMappingRow(index, { targetField: val })}
        >
          {targetFields.map((field) => (
            <Option key={field.code} value={field.code}>
              {field.label || financeTargetFieldLabelMap[field.code] || field.code}
              {field.required ? ' *' : ''}
            </Option>
          ))}
        </Select>
      )
    },
    {
      title: '转换规则',
      dataIndex: 'transform',
      width: 160,
      render: (value, record, index) => (
        <Select
          style={{ width: '100%' }}
          value={value || 'NONE'}
          onChange={(val) => updateMappingRow(index, { transform: val })}
        >
          {transforms.map((item) => (
            <Option key={item.code} value={item.code}>
              {item.label || transformLabelMap[item.code] || item.code}
            </Option>
          ))}
        </Select>
      )
    },
    {
      title: '默认值',
      dataIndex: 'defaultValue',
      width: 140,
      render: (value, record, index) => (
        <Input
          value={value}
          placeholder="字段缺失时填充"
          onChange={(e) => updateMappingRow(index, { defaultValue: e.target.value })}
        />
      )
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (value, record, index) => (
        <Switch
          checked={value !== false}
          onChange={(checked) => updateMappingRow(index, { enabled: checked })}
        />
      )
    },
    {
      title: '操作',
      width: 80,
      render: (value, record, index) => (
        <Button
          type="link"
          danger
          onClick={() => setMappingRows((prev) => prev.filter((row, idx) => idx !== index))}
        >
          删除
        </Button>
      )
    }
  ];

  // 预览表
  const previewColumns = [
    { title: '行号', dataIndex: 'rowNumber', width: 70 },
    { title: '平台流水号', dataIndex: 'externalTransactionId', width: 170 },
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (v) => {
        const meta = transactionTypeMap[v];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : v || '-';
      }
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 110,
      render: (v, record) => (v === null || v === undefined ? '-' : formatAmount(v, record.currency))
    },
    { title: '币种', dataIndex: 'currency', width: 80, render: (v) => v || '-' },
    {
      title: '关联单据',
      dataIndex: 'referenceId',
      width: 160,
      render: (v, record) => (v ? `${v}${record.referenceType ? `（${record.referenceType}）` : ''}` : '-')
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v) => {
        const meta = transactionStatusMap[v];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : v || '-';
      }
    },
    {
      title: '校验结果',
      dataIndex: 'errors',
      render: (errors) => {
        if (!errors || errors.length === 0) return <Tag color="success">通过</Tag>;
        return (
          <span style={{ color: '#cf1322' }}>
            {errors.map((e) => `${e.field || ''}: ${e.message}`).join('；')}
          </span>
        );
      }
    }
  ];

  // 任务表
  const jobColumns = [
    { title: '任务ID', dataIndex: 'jobId', width: 260, ellipsis: true },
    { title: '平台', dataIndex: 'platform', width: 110, render: (v) => platformLabel(v) },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v) => {
        const meta = importJobStatusMap[v];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : v || '-';
      }
    },
    { title: '总数', dataIndex: 'totalCount', width: 80 },
    { title: '成功', dataIndex: 'successCount', width: 80 },
    { title: '失败', dataIndex: 'failureCount', width: 80 },
    { title: '跳过', dataIndex: 'skippedCount', width: 80 },
    { title: '开始时间', dataIndex: 'startedAt', width: 180 },
    { title: '说明', dataIndex: 'message', ellipsis: true },
    {
      title: '操作',
      width: 100,
      fixed: 'right',
      render: (value, record) => (
        <Button type="link" onClick={() => handleViewJob(record.jobId)}>
          明细
        </Button>
      )
    }
  ];

  const resultErrorColumns = [
    { title: '行号', dataIndex: 'rowNumber', width: 70 },
    { title: '平台流水号', dataIndex: 'externalTransactionId', width: 170 },
    { title: '字段', dataIndex: 'field', width: 160 },
    { title: '错误描述', dataIndex: 'message' }
  ];

  const tabItems = [
    {
      key: 'platform',
      label: '平台拉取导入',
      children: (
        <div>
          <Card size="small" style={{ marginBottom: 12 }}>
            <Row gutter={[16, 12]}>
              <Col span={8}>
                <div style={{ marginBottom: 4 }}>平台集成：</div>
                <Select
                  style={{ width: '100%' }}
                  allowClear
                  placeholder="选择已配置的平台集成"
                  value={integrationId}
                  onChange={(val) => setIntegrationId(val)}
                >
                  {integrations.map((item) => (
                    <Option key={item.id} value={item.id}>
                      {platformLabel(item.platformName)}（{item.status}）
                    </Option>
                  ))}
                </Select>
              </Col>
              <Col span={4}>
                <div style={{ marginBottom: 4 }}>平台：</div>
                <Select
                  style={{ width: '100%' }}
                  value={platformManual}
                  onChange={(val) => setPlatformManual(val)}
                >
                  {[...platformOptions, { value: FILE_PLATFORM, label: '文件上传' }].map((item) => (
                    <Option key={item.value} value={item.value}>
                      {item.label}
                    </Option>
                  ))}
                </Select>
              </Col>
              <Col span={4}>
                <div style={{ marginBottom: 4 }}>使用示例数据：</div>
                <Switch checked={useSampleData} onChange={setUseSampleData} />
              </Col>
              <Col span={4}>
                <div style={{ marginBottom: 4 }}>拉取条数：</div>
                <InputNumber style={{ width: '100%' }} min={1} max={200} value={limit} onChange={setLimit} />
              </Col>
              <Col span={4}>
                <div style={{ marginBottom: 4 }}>覆盖已存在：</div>
                <Switch checked={updateExisting} onChange={setUpdateExisting} />
              </Col>
              <Col span={6}>
                <div style={{ marginBottom: 4 }}>平台流水号：</div>
                <Input
                  placeholder="按平台流水号精确拉取（可空）"
                  allowClear
                  value={referenceId}
                  onChange={(e) => setReferenceId(e.target.value)}
                />
              </Col>
            </Row>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button icon={<SearchOutlined />} loading={loadingPreview} onClick={handlePreview}>
                拉取并预览
              </Button>
              <Button
                type="primary"
                icon={<ImportOutlined />}
                loading={loadingImport}
                onClick={() => handleImport(previewRows, previewMeta?.fileName)}
              >
                执行导入
              </Button>
            </div>
            <div style={{ marginTop: 8 }}>
              <Text type="secondary">
                幂等键为「租户 + 平台 + 平台流水号（externalTransactionId）」：重复导入同一流水时，开启覆盖会更新已有流水，关闭则跳过。
              </Text>
            </div>
            {transactionTypes.length > 0 && (
              <div style={{ marginTop: 8 }}>
                <Text type="secondary">
                  平台交易词将被映射为以下类型：
                  {transactionTypes
                    .map((item) => `${item.code}（${item.label || transactionTypeMap[item.code]?.text || item.code}）`)
                    .join('、')}
                </Text>
              </div>
            )}
          </Card>

          {previewMeta?.warnings?.length > 0 && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              title="处理提示"
              description={
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {previewMeta.warnings.map((item, idx) => (
                    <li key={idx}>{item}</li>
                  ))}
                </ul>
              }
            />
          )}

          <Card
            size="small"
            title={`字段映射（${currentPlatform}）`}
            extra={
              <Space>
                <Button
                  onClick={() => setMappingRows((prev) => [...prev, { sourceField: '', targetField: undefined, transform: 'NONE', enabled: true }])}
                >
                  新增映射
                </Button>
                <Button icon={<SaveOutlined />} loading={loadingSaveMapping} onClick={handleSaveMappings}>
                  保存映射
                </Button>
              </Space>
            }
            style={{ marginBottom: 12 }}
          >
            <Table
              columns={mappingColumns}
              dataSource={mappingRows}
              rowKey={(_, index) => `mapping-${index}`}
              pagination={false}
              size="small"
              scroll={{ x: 900 }}
            />
          </Card>

          <Card size="small" title={`待导入流水（${previewMeta?.totalCount ?? previewRows.length}）`}>
            <Table
              columns={previewColumns}
              dataSource={previewRows}
              rowKey={(record, index) => `${record.externalTransactionId || 'row'}-${index}`}
              pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条` }}
              size="small"
              scroll={{ x: 1100 }}
            />
          </Card>
        </div>
      )
    },
    {
      key: 'file',
      label: '文件上传导入',
      children: (
        <div>
          <Card size="small" style={{ marginBottom: 12 }}>
            <Row gutter={[16, 12]}>
              <Col span={6}>
                <div style={{ marginBottom: 4 }}>平台标识：</div>
                <Select style={{ width: '100%' }} value={platformManual} onChange={setPlatformManual}>
                  {[...platformOptions, { value: FILE_PLATFORM, label: '文件上传' }].map((item) => (
                    <Option key={item.value} value={item.value}>
                      {item.label}
                    </Option>
                  ))}
                </Select>
              </Col>
              <Col span={6}>
                <div style={{ marginBottom: 4 }}>上传后动作：</div>
                <Select style={{ width: '100%' }} value={uploadMode} onChange={setUploadMode}>
                  <Option value="preview">仅预览</Option>
                  <Option value="import">直接导入</Option>
                </Select>
              </Col>
              <Col span={6}>
                <div style={{ marginBottom: 4 }}>覆盖已存在：</div>
                <Switch checked={updateExisting} onChange={setUpdateExisting} />
              </Col>
              <Col span={6} style={{ display: 'flex', alignItems: 'flex-end' }}>
                <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>
                  下载CSV模板
                </Button>
              </Col>
            </Row>
            <div style={{ marginTop: 12 }}>
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
                <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
                <p className="ant-upload-hint">
                  支持 JSON 与 CSV（首行为表头）格式，结算/回款流水导出文件，单次建议不超过 2000 行
                </p>
              </Dragger>
            </div>
            <div style={{ marginTop: 12 }}>
              <Button
                type="primary"
                icon={<CloudUploadOutlined />}
                loading={loadingPreview}
                onClick={handleUpload}
              >
                {uploadMode === 'import' ? '上传并导入' : '上传并预览'}
              </Button>
            </div>
          </Card>

          <Card size="small" title={`解析结果（${previewMeta?.totalCount ?? previewRows.length}）`}>
            <Table
              columns={previewColumns}
              dataSource={previewRows}
              rowKey={(record, index) => `${record.externalTransactionId || 'row'}-${index}`}
              pagination={{ pageSize: 10 }}
              size="small"
              scroll={{ x: 1100 }}
            />
          </Card>
        </div>
      )
    },
    {
      key: 'jobs',
      label: '导入任务',
      children: (
        <Card size="small">
          <div style={{ marginBottom: 12 }}>
            <Button onClick={() => loadJobs(jobsPagination.current, jobsPagination.pageSize)}>刷新</Button>
          </div>
          <Table
            columns={jobColumns}
            dataSource={jobs}
            rowKey="jobId"
            loading={loadingJobs}
            pagination={{
              ...jobsPagination,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`
            }}
            onChange={(pageInfo) => loadJobs(pageInfo.current, pageInfo.pageSize)}
            size="small"
            scroll={{ x: 1200 }}
          />
        </Card>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <Title level={4}>财务导入</Title>
        <Text type="secondary">当前租户：{currentTenantId() || '未登录'}</Text>
      </div>

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />

      <Modal
        title="导入结果"
        open={resultModalVisible}
        onCancel={() => setResultModalVisible(false)}
        footer={[
          <Button key="close" type="primary" onClick={() => setResultModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={860}
      >
        {result && (
          <div>
            <Row gutter={16} style={{ marginBottom: 12 }}>
              <Col span={6}>总数：{result.totalCount ?? 0}</Col>
              <Col span={6}>成功：{result.successCount ?? 0}</Col>
              <Col span={6}>失败：{result.failureCount ?? 0}</Col>
              <Col span={6}>跳过：{result.skippedCount ?? 0}</Col>
            </Row>
            <Divider style={{ margin: '8px 0' }} />
            <div style={{ marginBottom: 8 }}>
              任务状态：
              <Tag color={importJobStatusMap[result.status]?.color || 'default'}>
                {importJobStatusMap[result.status]?.text || result.status}
              </Tag>
              <span style={{ marginLeft: 8 }}>{result.message}</span>
            </div>
            {result.errors && result.errors.length > 0 && (
              <Table
                columns={resultErrorColumns}
                dataSource={result.errors}
                rowKey={(_, index) => `err-${index}`}
                pagination={{ pageSize: 5 }}
                size="small"
              />
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default FinanceImport;
