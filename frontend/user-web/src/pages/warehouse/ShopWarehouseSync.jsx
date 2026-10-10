import React, { useState, useEffect, useCallback } from 'react';
import {
  Button, Space, Card, Typography, Table, Tag, message, Modal, Select, Form, Upload, Checkbox, Input, InputNumber,
  Alert, Descriptions, Divider, Popconfirm
} from 'antd';
import {
  SyncOutlined, LinkOutlined, DisconnectOutlined, UploadOutlined, DownloadOutlined,
  ImportOutlined, HistoryOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { shopWarehouseApi, warehouseApi, warehouseImportApi, platformIntegrationApi } from '../../services/api';

import Can from '../../components/Auth/Can.jsx';
const { Title, Text } = Typography;
const { Option } = Select;

const ShopWarehouseSync = () => {
  const navigate = useNavigate();
  const [integrations, setIntegrations] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [shopWarehouses, setShopWarehouses] = useState([]);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [syncIntegrationId, setSyncIntegrationId] = useState(null);
  const [useSampleData, setUseSampleData] = useState(true);
  const [syncResult, setSyncResult] = useState(null);

  const [bindModalVisible, setBindModalVisible] = useState(false);
  const [bindRecord, setBindRecord] = useState(null);
  const [bindForm] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  // 批量导入状态
  const [importPreview, setImportPreview] = useState(null);
  const [importLoading, setImportLoading] = useState(false);
  const [importPlatform, setImportPlatform] = useState('FILE');
  const [updateExisting, setUpdateExisting] = useState(true);
  const [importJobs, setImportJobs] = useState([]);
  const [jobDetail, setJobDetail] = useState(null);

  // 加载基础数据
  const fetchBase = useCallback(async () => {
    setLoading(true);
    try {
      const [integrationsData, warehousesData, shopWarehousesData] = await Promise.all([
        platformIntegrationApi.getIntegrations().catch(() => []),
        warehouseApi.getWarehouses({ page: 0, size: 200 }).catch(() => []),
        shopWarehouseApi.getShopWarehouses({}).catch(() => [])
      ]);
      setIntegrations(Array.isArray(integrationsData) ? integrationsData : []);
      setWarehouses(Array.isArray(warehousesData) ? warehousesData : (warehousesData?.content ?? []));
      setShopWarehouses(Array.isArray(shopWarehousesData) ? shopWarehousesData : []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchBase();
  }, [fetchBase]);

  // 加载导入任务列表
  const fetchJobs = useCallback(async () => {
    try {
      const data = await warehouseImportApi.getJobs({ page: 0, size: 10 });
      setImportJobs(data && data.content ? data.content : []);
    } catch (error) {
      console.error('获取导入任务失败:', error);
    }
  }, []);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  // 从平台同步店铺仓库
  const handleSync = async () => {
    if (!syncIntegrationId) {
      message.warning('请选择平台集成');
      return;
    }
    setSyncing(true);
    setSyncResult(null);
    try {
      const result = await shopWarehouseApi.syncFromPlatform(syncIntegrationId, {
        useSampleData,
        limit: 50
      });
      setSyncResult(result);
      message.success(`同步完成：新增 ${result.createdCount}，更新 ${result.updatedCount}，自动绑定 ${result.autoBoundCount}`);
      fetchBase();
    } catch (error) {
      console.error('同步店铺仓库失败', error.message || error);
    } finally {
      setSyncing(false);
    }
  };

  // 打开绑定弹窗
  const openBind = (record) => {
    setBindRecord(record);
    bindForm.setFieldsValue({
      platform: record.platform,
      shopCode: record.shopCode,
      shopName: record.shopName,
      platformWarehouseId: record.platformWarehouseId,
      platformWarehouseName: record.platformWarehouseName,
      address: record.address,
      localWarehouseId: record.localWarehouseId
    });
    setBindModalVisible(true);
  };

  // 提交绑定
  const handleBind = async () => {
    try {
      const values = await bindForm.validateFields();
      setSubmitting(true);
      await shopWarehouseApi.bindShopWarehouse(values);
      message.success('绑定成功');
      setBindModalVisible(false);
      fetchBase();
    } catch {
      // 表单校验失败或接口错误
    } finally {
      setSubmitting(false);
    }
  };

  // 解绑
  const handleUnbind = async (record) => {
    try {
      await shopWarehouseApi.unbindShopWarehouse(record.id);
      message.success('解绑成功');
      fetchBase();
    } catch (error) {
      console.error('解绑失败:', error);
    }
  };

  // 上传文件（预览或直接导入）
  const handleUpload = async (file, mode) => {
    setImportLoading(true);
    try {
      const data = await warehouseImportApi.upload(file, {
        platform: importPlatform,
        mode,
        updateExisting
      });
      if (mode === 'preview') {
        setImportPreview(data);
        message.success(`解析完成，共 ${data.totalCount} 行`);
      } else {
        message.success(`导入完成：${data.message}`);
        setImportPreview(null);
        fetchJobs();
        fetchBase();
      }
    } catch (error) {
      console.error('上传失败:', error);
    } finally {
      setImportLoading(false);
    }
    return false; // 阻止antd自动上传
  };

  // 执行导入
  const handleImport = async () => {
    if (!importPreview || !importPreview.rows) {
      message.warning('请先上传并预览数据');
      return;
    }
    setImportLoading(true);
    try {
      const result = await warehouseImportApi.import({
        tenantId: importPreview.tenantId,
        platform: importPreview.platform,
        rows: importPreview.rows,
        updateExisting,
        fileName: importPreview.fileName
      });
      message.success(`导入完成：${result.message}`);
      setImportPreview(null);
      fetchJobs();
      fetchBase();
    } catch (error) {
      console.error('导入失败:', error);
    } finally {
      setImportLoading(false);
    }
  };

  // 查看任务详情
  const viewJob = async (job) => {
    try {
      const detail = await warehouseImportApi.getJob(job.jobId);
      setJobDetail(detail);
    } catch (error) {
      console.error('获取任务详情失败:', error);
    }
  };

  // 下载模板
  const handleDownloadTemplate = async () => {
    try {
      const blob = await warehouseImportApi.downloadTemplate();
      const url = window.URL.createObjectURL(new Blob([blob], { type: 'text/csv;charset=utf-8' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = 'shop_warehouse_import_template.csv';
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('下载模板失败:', error);
    }
  };

  const statusColor = {
    SUCCESS: 'success',
    PARTIAL_SUCCESS: 'warning',
    FAILED: 'error',
    RUNNING: 'processing',
    PENDING: 'default'
  };

  // 店铺仓库表格列
  const shopWarehouseColumns = [
    { title: '平台', dataIndex: 'platform', key: 'platform', width: 100 },
    { title: '店铺', dataIndex: 'shopName', key: 'shopName', width: 130, render: (t, r) => t || r.shopCode || '-' },
    { title: '平台仓库ID', dataIndex: 'platformWarehouseId', key: 'platformWarehouseId', width: 140 },
    { title: '平台仓库名称', dataIndex: 'platformWarehouseName', key: 'platformWarehouseName', width: 150 },
    {
      title: '绑定本地仓库',
      key: 'localWarehouse',
      width: 200,
      render: (_, record) => record.bound
        ? <Tag color="success">{record.localWarehouseCode || record.localWarehouseId}</Tag>
        : <Tag color="warning">未绑定</Tag>
    },
    { title: '本地仓库ID', dataIndex: 'localWarehouseId', key: 'localWarehouseId', ellipsis: true, render: (t) => t || '-' },
    {
      title: '默认',
      dataIndex: 'defaultWarehouse',
      key: 'defaultWarehouse',
      width: 70,
      render: (v) => (v ? <Tag color="blue">默认</Tag> : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, record) => (
        <Space>
          <Can permission="warehouse:write">
            <Button type="link" size="small" icon={<LinkOutlined />} onClick={() => openBind(record)}>
              {record.bound ? '重新绑定' : '绑定'}
            </Button>
            {record.bound && (
              <Popconfirm title="确认解除绑定？" onConfirm={() => handleUnbind(record)}>
                <Button type="link" size="small" danger icon={<DisconnectOutlined />}>解绑</Button>
              </Popconfirm>
            )}
          </Can>
        </Space>
      )
    }
  ];

  // 预览表格列
  const previewColumns = [
    { title: '行号', dataIndex: 'rowNumber', key: 'rowNumber', width: 60 },
    { title: '店铺', dataIndex: 'shopName', key: 'shopName', width: 110 },
    { title: '平台仓库ID', dataIndex: 'platformWarehouseId', key: 'platformWarehouseId', width: 130 },
    { title: '平台仓库名称', dataIndex: 'platformWarehouseName', key: 'platformWarehouseName', width: 130 },
    { title: '本地仓库编码', dataIndex: 'warehouseCode', key: 'warehouseCode', width: 120 },
    { title: '地址', dataIndex: 'address', key: 'address', ellipsis: true },
    { title: '默认', dataIndex: 'isDefault', key: 'isDefault', width: 60, render: (v) => (v ? '是' : '否') },
    {
      title: '校验结果',
      key: 'errors',
      width: 220,
      render: (_, record) => record.errors && record.errors.length > 0
        ? <Text type="danger">{record.errors.map(e => e.message).join('；')}</Text>
        : <Tag color="success">通过</Tag>
    }
  ];

  // 任务错误明细列
  const jobErrorColumns = [
    { title: '行号', dataIndex: 'rowNumber', key: 'rowNumber', width: 60 },
    { title: '店铺编码', dataIndex: 'shopCode', key: 'shopCode', width: 120 },
    { title: '平台仓库ID', dataIndex: 'platformWarehouseId', key: 'platformWarehouseId', width: 130 },
    { title: '字段', dataIndex: 'field', key: 'field', width: 140 },
    { title: '错误信息', dataIndex: 'message', key: 'message' }
  ];

  const jobsColumns = [
    { title: '任务ID', dataIndex: 'jobId', key: 'jobId', width: 140, ellipsis: true },
    { title: '平台', dataIndex: 'platform', key: 'platform', width: 90 },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
    { title: '总数', dataIndex: 'totalCount', key: 'totalCount', width: 60 },
    { title: '成功', dataIndex: 'successCount', key: 'successCount', width: 60 },
    { title: '失败', dataIndex: 'failureCount', key: 'failureCount', width: 60 },
    { title: '跳过', dataIndex: 'skippedCount', key: 'skippedCount', width: 60 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (text, record) => <Tag color={statusColor[text] || 'default'}>{record.statusLabel || text}</Tag>
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => viewJob(record)}>详情</Button>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<SyncOutlined spin={false} />} onClick={() => navigate('/warehouses')}>返回列表</Button>
          <Title level={4}>店铺仓库对接</Title>
        </div>
      </div>

      {/* 平台同步 */}
      <Card title="从第三方平台同步店铺仓库" style={{ marginBottom: 5 }}>
        <Space wrap>
          <Select
            placeholder="选择平台集成"
            style={{ width: 280 }}
            value={syncIntegrationId}
            onChange={setSyncIntegrationId}
          >
            {integrations.map(item => (
              <Option key={item.id} value={item.id}>
                {item.platformName || item.platform} - {(item.storeUrl || '未配置店铺地址').substring(0, 30)}
              </Option>
            ))}
          </Select>
          <Checkbox checked={useSampleData} onChange={(e) => setUseSampleData(e.target.checked)}>
            使用示例数据（未联调真实平台时勾选）
          </Checkbox>
          <Can permission="shop:sync">
            <Button type="primary" icon={<SyncOutlined spin={syncing} />} loading={syncing} onClick={handleSync}>
              开始同步
            </Button>
          </Can>
        </Space>

        {syncResult && (
          <div style={{ marginTop: 12 }}>
            <Alert
              type={syncResult.failureCount > 0 ? 'warning' : 'success'}
              title={`同步结果：共拉取 ${syncResult.totalFetched} 条，新增 ${syncResult.createdCount}，更新 ${syncResult.updatedCount}，自动绑定 ${syncResult.autoBoundCount}，失败 ${syncResult.failureCount}`}
              description={syncResult.failures && syncResult.failures.length > 0
                ? syncResult.failures.map((f, i) => <div key={i}>{f}</div>)
                : null}
              showIcon
              closable
            />
          </div>
        )}
      </Card>

      {/* 店铺仓库列表 */}
      <Card title={`店铺仓库列表（${shopWarehouses.length}）`} style={{ marginBottom: 5 }}>
        <Table
          columns={shopWarehouseColumns}
          dataSource={shopWarehouses}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条` }}
          scroll={{ x: 1100 }}
        />
      </Card>

      {/* 批量导入 */}
      <Card title="批量导入店铺仓库" style={{ marginBottom: 5 }}>
        <Space wrap style={{ marginBottom: 12 }}>
          <Select value={importPlatform} onChange={setImportPlatform} style={{ width: 160 }}>
            <Option value="FILE">文件导入 (FILE)</Option>
          </Select>
          <Checkbox checked={updateExisting} onChange={(e) => setUpdateExisting(e.target.checked)}>
            平台仓库ID已存在时覆盖更新
          </Checkbox>
          <Can permission="warehouse:write">
            <Upload
              beforeUpload={(file) => handleUpload(file, 'preview')}
              showUploadList={false}
              accept=".csv,.json,.xlsx,.xls"
            >
              <Button icon={<UploadOutlined />} loading={importLoading}>上传文件预览 (CSV/JSON/Excel)</Button>
            </Upload>
            <Upload
              beforeUpload={(file) => handleUpload(file, 'import')}
              showUploadList={false}
              accept=".csv,.json,.xlsx,.xls"
            >
              <Button icon={<ImportOutlined />} loading={importLoading}>上传并直接导入</Button>
            </Upload>
          </Can>
          <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>下载导入模板</Button>
          <Button icon={<HistoryOutlined />} onClick={fetchJobs}>刷新任务列表</Button>
        </Space>

        {importPreview && (
          <>
            <Divider orientation="left">
              导入预览（共 {importPreview.totalCount} 行）
              {importPreview.warnings && importPreview.warnings.map((w, i) => (
                <Text key={i} type="warning" style={{ marginLeft: 8 }}>{w}</Text>
              ))}
            </Divider>
            <Alert
              type={importPreview.rowErrors && importPreview.rowErrors.length > 0 ? 'warning' : 'info'}
              title={`行级校验错误 ${importPreview.rowErrors ? importPreview.rowErrors.length : 0} 条；存在错误的行导入时将被跳过并记录到任务明细`}
              style={{ marginBottom: 8 }}
            />
            <Table
              columns={previewColumns}
              dataSource={importPreview.rows}
              rowKey="rowNumber"
              pagination={{ pageSize: 5 }}
              size="small"
              scroll={{ x: 1000 }}
            />
            <div style={{ marginTop: 12, textAlign: 'right' }}>
              <Space>
                <Button onClick={() => setImportPreview(null)}>取消</Button>
                <Button type="primary" loading={importLoading} onClick={handleImport}>确认导入</Button>
              </Space>
            </div>
          </>
        )}
      </Card>

      {/* 导入任务列表 */}
      <Card title="导入任务记录（结果反馈）">
        <Table
          columns={jobsColumns}
          dataSource={importJobs}
          rowKey="jobId"
          pagination={{ pageSize: 5 }}
          size="small"
          scroll={{ x: 900 }}
        />
      </Card>

      {/* 绑定弹窗 */}
      <Modal
        title={bindRecord && bindRecord.bound ? '重新绑定本地仓库' : '绑定本地仓库'}
        open={bindModalVisible}
        onCancel={() => setBindModalVisible(false)}
        onOk={handleBind}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={bindForm} layout="vertical">
          <Form.Item label="平台" name="platform" rules={[{ required: true, message: '请输入平台' }]}>
            <Input disabled />
          </Form.Item>
          <Form.Item label="店铺编码" name="shopCode">
            <Input disabled />
          </Form.Item>
          <Form.Item label="平台仓库ID" name="platformWarehouseId" rules={[{ required: true, message: '请输入平台仓库ID' }]}>
            <Input disabled />
          </Form.Item>
          <Form.Item label="平台仓库名称" name="platformWarehouseName">
            <Input />
          </Form.Item>
          <Form.Item label="平台仓库地址" name="address">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item
            label="绑定本地仓库"
            name="localWarehouseId"
            rules={[{ required: true, message: '请选择本地仓库' }]}
          >
            <Select placeholder="请选择本地仓库">
              {warehouses.map(item => (
                <Option key={item.warehouseId} value={item.warehouseId}>
                  {item.warehouseName}（{item.warehouseCode}）
                </Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      {/* 任务详情弹窗 */}
      <Modal
        title="导入任务详情"
        open={!!jobDetail}
        onCancel={() => setJobDetail(null)}
        footer={null}
        width={900}
      >
        {jobDetail && (
          <>
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="任务ID">{jobDetail.jobId}</Descriptions.Item>
              <Descriptions.Item label="平台">{jobDetail.platform}</Descriptions.Item>
              <Descriptions.Item label="文件名">{jobDetail.fileName || '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColor[jobDetail.status]}>{jobDetail.statusLabel}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="总数/成功/失败/跳过">
                {jobDetail.totalCount} / {jobDetail.successCount} / {jobDetail.failureCount} / {jobDetail.skippedCount}
              </Descriptions.Item>
              <Descriptions.Item label="结果消息">{jobDetail.message}</Descriptions.Item>
              <Descriptions.Item label="开始时间">{jobDetail.startedAt || '-'}</Descriptions.Item>
              <Descriptions.Item label="结束时间">{jobDetail.finishedAt || '-'}</Descriptions.Item>
            </Descriptions>
            {jobDetail.errors && jobDetail.errors.length > 0 && (
              <>
                <Divider orientation="left">失败明细（{jobDetail.errors.length} 条）</Divider>
                <Table
                  columns={jobErrorColumns}
                  dataSource={jobDetail.errors}
                  rowKey={(record, index) => `${record.rowNumber}-${index}`}
                  pagination={{ pageSize: 5 }}
                  size="small"
                />
              </>
            )}
          </>
        )}
      </Modal>
    </div>
  );
};

export default ShopWarehouseSync;
