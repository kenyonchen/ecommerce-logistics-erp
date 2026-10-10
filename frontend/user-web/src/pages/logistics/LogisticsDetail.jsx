import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message
} from 'antd';
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  PrinterOutlined,
  RocketOutlined,
  StopOutlined,
  SyncOutlined
} from '@ant-design/icons';
import { logisticsApi } from '../../services/api';
import {
  formatDateTime,
  formatAmount,
  logisticsStatusMap,
  logisticsStatusLabel,
  logisticsShippableStatuses
} from '../../constants/logistics';

const { Title } = Typography;

/**
 * 物流单详情：对接 GET /api/logistics/{id}，并提供状态推进、发货、面单、轨迹维护等操作。
 */
const LogisticsDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(false);
  const [statusModalOpen, setStatusModalOpen] = useState(false);
  const [traceModalOpen, setTraceModalOpen] = useState(false);
  const [statusForm, setStatusForm] = useState({ status: undefined, description: '' });
  const [traceForm, setTraceForm] = useState({ time: null, status: 'IN_TRANSIT', description: '', location: '' });

  const fetchDetail = useCallback(async () => {
    setLoading(true);
    try {
      const data = await logisticsApi.getLogisticsById(id);
      setDetail(data);
    } catch (error) {
      message.error(`获取物流单详情失败：${error?.message || '未知错误'}`);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  const runAction = async (action, successText) => {
    try {
      await action();
      message.success(successText);
      fetchDetail();
      return true;
    } catch (error) {
      message.error(`${successText}失败：${error?.message || '未知错误'}`);
      return false;
    }
  };

  const handleSubmitStatus = async () => {
    if (!statusForm.status) {
      message.warning('请选择目标状态');
      return;
    }
    const ok = await runAction(
      () => logisticsApi.updateStatus(id, { status: statusForm.status, description: statusForm.description || undefined }),
      '状态更新成功'
    );
    if (ok) setStatusModalOpen(false);
  };

  const handleSubmitTrace = async () => {
    if (!traceForm.description.trim()) {
      message.warning('请填写轨迹描述');
      return;
    }
    const ok = await runAction(
      () => logisticsApi.addTrace(id, {
        time: traceForm.time ? traceForm.time.format('YYYY-MM-DD HH:mm:ss') : undefined,
        status: traceForm.status || undefined,
        description: traceForm.description.trim(),
        location: traceForm.location.trim() || undefined
      }),
      '轨迹添加成功'
    );
    if (ok) {
      setTraceModalOpen(false);
      setTraceForm({ time: null, status: 'IN_TRANSIT', description: '', location: '' });
    }
  };

  const handleDelete = () => {
    Modal.confirm({
      title: '删除物流单',
      content: '删除后该物流单及其轨迹将不可恢复，确认删除？',
      okType: 'danger',
      onOk: async () => {
        await runAction(() => logisticsApi.deleteLogistics(id), '删除成功');
        navigate('/logistics');
      }
    });
  };

  const statusOptions = Object.entries(logisticsStatusMap).map(([value, meta]) => ({ value, label: meta.text }));
  const isFinal = detail && (detail.status === 'DELIVERED' || detail.status === 'CANCELLED');

  const traceColumns = [
    { title: '时间', dataIndex: 'time', width: 180, render: (v) => formatDateTime(v) },
    {
      title: '节点',
      dataIndex: 'status',
      width: 120,
      render: (v) => {
        const meta = logisticsStatusMap[v];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : v || '-';
      }
    },
    { title: '描述', dataIndex: 'description' },
    { title: '地点', dataIndex: 'location', width: 160, render: (v) => v || '-' }
  ];

  if (!loading && !detail) {
    return (
      <div>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/logistics')}>返回列表</Button>
        <Alert style={{ marginTop: 16 }} type="warning" showIcon title="物流单不存在或已被删除" />
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/logistics')}>返回列表</Button>
          <Title level={4} style={{ margin: 0 }}>物流单详情</Title>
        </Space>
        <Space wrap>
          <Button
            icon={<RocketOutlined />}
            disabled={!detail || !logisticsShippableStatuses.includes(detail.status)}
            onClick={() => runAction(() => logisticsApi.ship(id), '发货成功')}
          >
            发货
          </Button>
          <Button
            icon={<PrinterOutlined />}
            disabled={!detail}
            onClick={() => runAction(() => logisticsApi.generateLabel(id), '面单生成成功')}
          >
            生成面单
          </Button>
          <Button
            icon={<SyncOutlined />}
            disabled={!detail}
            onClick={() => runAction(() => logisticsApi.track(id), '轨迹已刷新')}
          >
            刷新轨迹
          </Button>
          <Button disabled={!detail} onClick={() => setStatusModalOpen(true)}>更新状态</Button>
          {!isFinal && detail && (
            <Button
              icon={<StopOutlined />}
              onClick={() => runAction(() => logisticsApi.cancel(id), '已取消')}
            >
              取消物流单
            </Button>
          )}
          <Button danger icon={<DeleteOutlined />} disabled={!detail} onClick={handleDelete}>删除</Button>
        </Space>
      </div>

      <Card size="small" loading={loading} style={{ marginBottom: 8 }}>
        <Descriptions title="基本信息" column={3} size="small" bordered>
          <Descriptions.Item label="物流单号">{detail?.logisticsOrderNumber || '-'}</Descriptions.Item>
          <Descriptions.Item label="订单号">{detail?.orderNumber || detail?.orderId || '-'}</Descriptions.Item>
          <Descriptions.Item label="状态">
            {detail ? (
              <Tag color={logisticsStatusMap[detail.status]?.color}>
                {detail.statusLabel || logisticsStatusLabel(detail.status)}
              </Tag>
            ) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="物流渠道">{detail?.channelName || detail?.channelCode || '-'}</Descriptions.Item>
          <Descriptions.Item label="承运商">{detail?.carrier || '-'}</Descriptions.Item>
          <Descriptions.Item label="运单号">{detail?.trackingNumber || '-'}</Descriptions.Item>
          <Descriptions.Item label="平台来源">{detail?.platform || '系统内'}</Descriptions.Item>
          <Descriptions.Item label="平台物流单号">{detail?.externalLogisticsId || '-'}</Descriptions.Item>
          <Descriptions.Item label="运费">
            {detail?.totalFee == null ? '-' : (
              <>
                {`${detail.currency || ''} ${formatAmount(detail.totalFee)}`}
                {detail?.baseFee != null && detail?.weightFee != null && (
                  <Typography.Text type="secondary" style={{ marginLeft: 8 }}>
                    {`(首重 ${formatAmount(detail.baseFee)} + 续重 ${formatAmount(detail.weightFee)})`}
                  </Typography.Text>
                )}
              </>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="发货时间">{formatDateTime(detail?.shippedAt)}</Descriptions.Item>
          <Descriptions.Item label="预计送达">{formatDateTime(detail?.estimatedArrivalAt)}</Descriptions.Item>
          <Descriptions.Item label="签收时间">{formatDateTime(detail?.deliveredAt)}</Descriptions.Item>
          <Descriptions.Item label="面单文件">{detail?.labelUrl || '-'}</Descriptions.Item>
          <Descriptions.Item label="面单格式">{detail?.labelFormat || '-'}</Descriptions.Item>
          <Descriptions.Item label="条形码">{detail?.barcode || '-'}</Descriptions.Item>
          <Descriptions.Item label="跟踪链接" span={3}>{detail?.trackingUrl || '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{formatDateTime(detail?.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{formatDateTime(detail?.updatedAt)}</Descriptions.Item>
          <Descriptions.Item label="租户">{detail?.tenantId || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card size="small" style={{ marginBottom: 8 }}>
        <Descriptions title="收货信息" column={2} size="small">
          <Descriptions.Item label="收件人">{detail?.recipient || '-'}</Descriptions.Item>
          <Descriptions.Item label="联系电话">{detail?.phone || '-'}</Descriptions.Item>
          <Descriptions.Item label="收货地址" span={2}>{detail?.shippingAddress || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card
        size="small"
        title="物流轨迹"
        extra={<Button type="primary" size="small" onClick={() => setTraceModalOpen(true)}>添加轨迹</Button>}
      >
        <Table
          rowKey={(record, index) => `${record.time}-${index}`}
          size="small"
          dataSource={detail?.traces || []}
          columns={traceColumns}
          pagination={false}
        />
      </Card>

      <Modal
        title="更新物流状态"
        open={statusModalOpen}
        onOk={handleSubmitStatus}
        onCancel={() => setStatusModalOpen(false)}
        okText="确定"
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Select
            style={{ width: '100%' }}
            placeholder="选择目标状态"
            value={statusForm.status}
            options={statusOptions}
            onChange={(value) => setStatusForm((prev) => ({ ...prev, status: value }))}
          />
          <Input
            placeholder="轨迹描述（留空则使用状态中文名）"
            value={statusForm.description}
            onChange={(e) => setStatusForm((prev) => ({ ...prev, description: e.target.value }))}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            后端会校验状态流转：已送达/已取消为终态，不允许再变更。
          </Typography.Text>
        </Space>
      </Modal>

      <Modal
        title="添加物流轨迹"
        open={traceModalOpen}
        onOk={handleSubmitTrace}
        onCancel={() => setTraceModalOpen(false)}
        okText="确定"
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <DatePicker
            showTime
            style={{ width: '100%' }}
            value={traceForm.time}
            onChange={(value) => setTraceForm((prev) => ({ ...prev, time: value }))}
            placeholder="轨迹时间，留空为当前时间"
          />
          <Select
            style={{ width: '100%' }}
            value={traceForm.status}
            options={statusOptions}
            onChange={(value) => setTraceForm((prev) => ({ ...prev, status: value }))}
          />
          <Input
            placeholder="地点"
            value={traceForm.location}
            onChange={(e) => setTraceForm((prev) => ({ ...prev, location: e.target.value }))}
          />
          <Input.TextArea
            rows={3}
            placeholder="轨迹描述，如：包裹已到达深圳转运中心"
            value={traceForm.description}
            onChange={(e) => setTraceForm((prev) => ({ ...prev, description: e.target.value }))}
          />
        </Space>
      </Modal>
    </div>
  );
};

export default LogisticsDetail;
