import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Col,
  DatePicker,
  Input,
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
  message
} from 'antd';
import { orderApi, logisticsApi } from '../../services/api';
import { DEFAULT_TENANT_ID } from '../../constants/logistics';

const { Text } = Typography;

/**
 * 批量发货弹窗。
 * <p>
 * 后端 /logistics/batch-ship 以「订单」为处理单元：按行定位订单 → 定位渠道 → 建单并置为已发货，
 * 同时把订单状态推进到 SHIPPED；已存在非待处理物流单的订单会被记为跳过，行级失败不影响其他行。
 * </p>
 */
const BatchShipModal = ({ open, onCancel, channels = [], onShipped }) => {
  const [mode, setMode] = useState('pick');
  const [channelCode, setChannelCode] = useState(undefined);
  const [shippedAt, setShippedAt] = useState(null);
  const [syncOrderStatus, setSyncOrderStatus] = useState(true);

  const [orders, setOrders] = useState([]);
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [orderPagination, setOrderPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [orderKeyword, setOrderKeyword] = useState('');
  const [selectedOrders, setSelectedOrders] = useState([]);

  const [pasteText, setPasteText] = useState('');
  const [pasteOverrides, setPasteOverrides] = useState({});
  const [shipRows, setShipRows] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);

  const loadOrders = useCallback(async (page = 1, size = 10) => {
    setLoadingOrders(true);
    try {
      const data = await orderApi.getOrders({
        tenantId: DEFAULT_TENANT_ID,
        status: 'CON_FIRMED',
        keyword: orderKeyword || undefined,
        page: page - 1,
        size
      });
      setOrders(data?.content || []);
      setOrderPagination({ current: (data?.page ?? 0) + 1, pageSize: data?.size ?? size, total: data?.total ?? 0 });
    } catch {
      message.error('加载订单列表失败');
    } finally {
      setLoadingOrders(false);
    }
  }, [orderKeyword]);

  useEffect(() => {
    if (open) {
      setResult(null);
      setSelectedOrders([]);
      setShipRows([]);
      loadOrders(1, 10);
    }
  }, [open]);

  // 勾选变化后同步待发货行：保留已填写的运单号
  useEffect(() => {
    if (mode !== 'pick') return;
    setShipRows((prev) => {
      const kept = prev.filter((row) =>
        selectedOrders.some((order) => (order.orderId || order.id) === row.key)
      );
      const added = selectedOrders
        .filter((order) => !kept.some((row) => row.key === (order.orderId || order.id)))
        .map((order, idx) => ({
          key: order.orderId || order.id,
          orderId: order.orderId || order.id,
          orderNumber: order.orderNumber,
          externalOrderId: order.platformOrderId,
          trackingNumber: '',
          rowNumber: kept.length + idx + 1
        }));
      return [...kept, ...added];
    });
  }, [selectedOrders, mode]);

  const pasteBase = useMemo(() => {
    if (mode !== 'paste') return [];
    return pasteText
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line, idx) => {
        const [orderNumber, trackingNumber] = line.split(/[,，\t ]+/);
        return {
          key: `paste-${idx}`,
          orderNumber: (orderNumber || '').trim(),
          trackingNumber: (trackingNumber || '').trim(),
          rowNumber: idx + 1
        };
      });
  }, [mode, pasteText]);

  const pasteRows = useMemo(
    () => pasteBase.map((row) => ({ ...row, trackingNumber: pasteOverrides[row.key] ?? row.trackingNumber })),
    [pasteBase, pasteOverrides]
  );

  const activeRows = mode === 'pick' ? shipRows : pasteRows;

  const updateRow = (key, patch) => {
    if (mode === 'pick') {
      setShipRows((prev) => prev.map((row) => (row.key === key ? { ...row, ...patch } : row)));
    } else {
      setPasteOverrides((prev) => ({ ...prev, [key]: patch.trackingNumber }));
    }
  };

  const handleSubmit = async () => {
    if (!activeRows.length) {
      message.warning('请先选择或填写要发货的订单');
      return;
    }
    if (!channelCode) {
      message.warning('请选择物流渠道');
      return;
    }
    const items = activeRows.map((row, idx) => ({
      rowNumber: idx + 1,
      orderId: row.orderId || undefined,
      orderNumber: row.orderNumber || undefined,
      externalOrderId: row.externalOrderId || undefined,
      channelCode,
      trackingNumber: row.trackingNumber || undefined,
      shippedAt: shippedAt ? shippedAt.format('YYYY-MM-DD HH:mm:ss') : undefined
    }));
    setSubmitting(true);
    try {
      const data = await logisticsApi.batchShip({
        tenantId: DEFAULT_TENANT_ID,
        channelCode,
        items,
        updateOrderStatus: syncOrderStatus
      });
      setResult(data);
      if (data?.failureCount > 0 || data?.skippedCount > 0) {
        message.warning(data?.message || '部分订单未发货成功，请查看失败明细');
      } else {
        message.success(data?.message || '批量发货完成');
      }
      onShipped?.(data);
    } catch (error) {
      message.error(`批量发货失败：${error?.message || '未知错误'}`);
    } finally {
      setSubmitting(false);
    }
  };

  const orderColumns = [
    { title: '订单号', dataIndex: 'orderNumber', width: 170 },
    { title: '平台', dataIndex: 'platform', width: 100, render: (v) => v || '-' },
    { title: '客户', dataIndex: 'customerName', width: 120, render: (v) => v || '-' },
    {
      title: '状态',
      dataIndex: 'statusLabel',
      width: 100,
      render: (v, record) => <Tag>{v || record.status}</Tag>
    }
  ];

  const shipColumns = [
    { title: '行号', dataIndex: 'rowNumber', width: 60 },
    { title: '订单号', dataIndex: 'orderNumber', width: 180, render: (v) => v || '-' },
    {
      title: '运单号',
      dataIndex: 'trackingNumber',
      render: (value, record) => (
        <Input
          value={value}
          placeholder="必填：承运商运单号"
          onChange={(e) => updateRow(record.key, { trackingNumber: e.target.value })}
        />
      )
    }
  ];

  const failureColumns = [
    { title: '行号', dataIndex: 'rowNumber', width: 60 },
    { title: '订单号', dataIndex: 'orderNumber', width: 170 },
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (v) => (v === 'SKIPPED' ? <Tag>跳过</Tag> : <Tag color="error">失败</Tag>)
    },
    { title: '原因', dataIndex: 'reason' }
  ];

  return (
    <Modal
      title="批量发货"
      open={open}
      width={900}
      onCancel={onCancel}
      onOk={handleSubmit}
      okText="执行批量发货"
      confirmLoading={submitting}
    >
      <Row gutter={12} style={{ marginBottom: 12 }}>
        <Col span={8}>
          <div style={{ marginBottom: 4 }}>物流渠道：</div>
          <Select
            showSearch
            style={{ width: '100%' }}
            placeholder="按渠道编码/名称选择"
            optionFilterProp="label"
            value={channelCode}
            onChange={setChannelCode}
            options={channels.map((item) => ({
              value: item.channelCode,
              label: `${item.channelName}（${item.channelCode}）`
            }))}
          />
        </Col>
        <Col span={8}>
          <div style={{ marginBottom: 4 }}>发货时间：</div>
          <DatePicker
            showTime
            style={{ width: '100%' }}
            value={shippedAt}
            onChange={setShippedAt}
            placeholder="留空表示当前时间"
          />
        </Col>
        <Col span={8}>
          <div style={{ marginBottom: 4 }}>同步订单状态为已发货：</div>
          <Switch checked={syncOrderStatus} onChange={setSyncOrderStatus} />
        </Col>
      </Row>

      <Tabs
        activeKey={mode}
        onChange={setMode}
        items={[
          {
            key: 'pick',
            label: '从订单挑选',
            children: (
              <>
                <Space style={{ marginBottom: 8 }}>
                  <Input
                    placeholder="订单号/客户关键字"
                    allowClear
                    value={orderKeyword}
                    onChange={(e) => setOrderKeyword(e.target.value)}
                    onPressEnter={() => loadOrders(1, orderPagination.pageSize)}
                    style={{ width: 220 }}
                  />
                  <Button onClick={() => loadOrders(1, orderPagination.pageSize)}>查询已确认订单</Button>
                  <Text type="secondary">仅展示「已确认」订单，其他状态请先在订单列表中推进</Text>
                </Space>
                <Table
                  rowKey={(record) => record.orderId || record.id}
                  size="small"
                  loading={loadingOrders}
                  dataSource={orders}
                  columns={orderColumns}
                  scroll={{ x: 600, y: 220 }}
                  rowSelection={{
                    selectedRowKeys: selectedOrders.map((order) => order.orderId || order.id),
                    onChange: (keys, rows) => setSelectedOrders(rows)
                  }}
                  pagination={{
                    current: orderPagination.current,
                    pageSize: orderPagination.pageSize,
                    total: orderPagination.total,
                    size: 'small',
                    showTotal: (total) => `共 ${total} 条`
                  }}
                  onChange={(pageInfo) => loadOrders(pageInfo.current, pageInfo.pageSize)}
                />
              </>
            )
          },
          {
            key: 'paste',
            label: '粘贴订单清单',
            children: (
              <>
                <Input.TextArea
                  rows={6}
                  value={pasteText}
                  onChange={(e) => setPasteText(e.target.value)}
                  placeholder={'每行一条：订单号,运单号（运单号可留空）\nORD-2026-0001,SF1234567890\nORD-2026-0002'}
                />
                <Text type="secondary">支持逗号、空格或制表符分隔；行号即清单顺序，失败明细按该行号回显。</Text>
              </>
            )
          }
        ]}
      />

      <div style={{ marginTop: 12 }}>
        <Text strong>待发货清单（{activeRows.length}）</Text>
        <Table
          rowKey="key"
          size="small"
          style={{ marginTop: 8 }}
          dataSource={activeRows}
          columns={shipColumns}
          scroll={{ x: 600, y: 180 }}
          pagination={false}
        />
      </div>

      {result && (
        <>
          <Alert
            style={{ margin: '12px 0' }}
            type={result.failureCount > 0 ? 'warning' : 'success'}
            showIcon
            message={result.message || '批量发货完成'}
          />
          <Row gutter={12} style={{ marginBottom: 12 }}>
            <Col span={6}><Statistic title="总数" value={result.totalCount} /></Col>
            <Col span={6}><Statistic title="成功" value={result.successCount} /></Col>
            <Col span={6}><Statistic title="失败" value={result.failureCount} /></Col>
            <Col span={6}><Statistic title="跳过" value={result.skippedCount} /></Col>
          </Row>
          {result.failures?.length > 0 && (
            <Table
              rowKey={(record, index) => `${record.rowNumber}-${index}`}
              size="small"
              dataSource={result.failures}
              columns={failureColumns}
              pagination={{ pageSize: 5, size: 'small' }}
            />
          )}
        </>
      )}
    </Modal>
  );
};

export default BatchShipModal;
