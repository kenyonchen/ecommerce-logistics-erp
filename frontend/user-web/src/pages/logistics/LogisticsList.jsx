import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  DatePicker,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message
} from 'antd';
import {
  DeleteOutlined,
  EyeOutlined,
  ImportOutlined,
  PlusOutlined,
  PrinterOutlined,
  RocketOutlined,
  SearchOutlined,
  StopOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { logisticsApi, logisticsChannelApi } from '../../services/api';
import {
  DEFAULT_TENANT_ID,
  logisticsStatusMap,
  logisticsStatusOptions,
  logisticsStatusLabel,
  logisticsShippableStatuses,
  formatDateTime,
  formatAmount
} from '../../constants/logistics';
import BatchShipModal from './BatchShipModal.jsx';

import Can from '../../components/Auth/Can.jsx';
const { Title } = Typography;
const { RangePicker } = DatePicker;

/**
 * 物流单列表：对接后端 GET /api/logistics 条件分页查询，并提供发货、面单、取消、删除与批量发货入口。
 */
const LogisticsList = () => {
  const navigate = useNavigate();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [channels, setChannels] = useState([]);
  const [statusOptions, setStatusOptions] = useState(logisticsStatusOptions);
  const [batchShipVisible, setBatchShipVisible] = useState(false);

  const [searchParams, setSearchParams] = useState({
    keyword: '',
    trackingNumber: '',
    status: undefined,
    channelId: undefined,
    dateRange: []
  });

  useEffect(() => {
    loadChannels();
    loadStatuses();
  }, []);

  const loadChannels = async () => {
    try {
      const data = await logisticsChannelApi.getChannels();
      setChannels(data || []);
    } catch {
      message.error('加载物流渠道失败');
    }
  };

  const loadStatuses = async () => {
    try {
      const data = await logisticsApi.getStatuses();
      if (data && data.length) {
        setStatusOptions(data.map((item) => ({ value: item.code, label: item.label })));
      }
    } catch {
      // 字典接口不可用时使用前端静态兜底
    }
  };

  const fetchRows = useCallback(async () => {
    setLoading(true);
    try {
      const [startDate, endDate] = searchParams.dateRange || [];
      const data = await logisticsApi.getLogisticsOrders({
        tenantId: DEFAULT_TENANT_ID,
        keyword: searchParams.keyword || undefined,
        trackingNumber: searchParams.trackingNumber || undefined,
        status: searchParams.status || undefined,
        channelId: searchParams.channelId || undefined,
        startDate: startDate ? startDate.format('YYYY-MM-DD') : undefined,
        endDate: endDate ? endDate.format('YYYY-MM-DD') : undefined,
        page: (pagination.current || 1) - 1,
        size: pagination.pageSize || 20
      });
      setRows(data?.content || []);
      setPagination((prev) => ({
        ...prev,
        total: data?.total ?? 0,
        current: (data?.page ?? 0) + 1
      }));
    } catch (error) {
      console.error('获取物流单列表失败:', error);
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize, searchParams]);

  useEffect(() => {
    fetchRows();
  }, [fetchRows]);

  const handleSearch = () => setPagination((prev) => ({ ...prev, current: 1 }));

  const handleReset = () => {
    setSearchParams({ keyword: '', trackingNumber: '', status: undefined, channelId: undefined, dateRange: [] });
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const runAction = async (action, successText) => {
    try {
      await action();
      message.success(successText);
      fetchRows();
    } catch (error) {
      message.error(`${successText}失败：${error?.message || '未知错误'}`);
    }
  };

  const columns = [
    {
      title: '物流单号',
      dataIndex: 'logisticsOrderNumber',
      width: 180,
      render: (value, record) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => navigate(`/logistics/${record.logisticsOrderId}`)}>
          {value || record.logisticsOrderId}
        </Button>
      )
    },
    { title: '订单号', dataIndex: 'orderNumber', width: 170, render: (v) => v || '-' },
    {
      title: '渠道',
      dataIndex: 'channelName',
      width: 130,
      render: (value, record) => value || record.channelCode || '-'
    },
    { title: '承运商', dataIndex: 'carrier', width: 120, render: (v) => v || '-' },
    { title: '运单号', dataIndex: 'trackingNumber', width: 170, render: (v) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value, record) => {
        const meta = logisticsStatusMap[value];
        return <Tag color={meta?.color}>{record.statusLabel || logisticsStatusLabel(value)}</Tag>;
      }
    },
    {
      title: '运费',
      dataIndex: 'totalFee',
      width: 110,
      render: (value, record) => (value == null ? '-' : `${record.currency || ''} ${formatAmount(value)}`)
    },
    { title: '收件人', dataIndex: 'recipient', width: 100, render: (v) => v || '-' },
    {
      title: '平台来源',
      dataIndex: 'platform',
      width: 120,
      render: (value, record) =>
        value ? (
          <Space size={4}>
            <Tag>{value}</Tag>
            {record.externalLogisticsId ? (
              <span style={{ color: '#8c8c8c', fontSize: 12 }}>{record.externalLogisticsId}</span>
            ) : null}
          </Space>
        ) : (
          '系统内'
        )
    },
    { title: '发货时间', dataIndex: 'shippedAt', width: 170, render: (v) => formatDateTime(v) },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: (v) => formatDateTime(v) },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 230,
      render: (_, record) => {
        const canShip = logisticsShippableStatuses.includes(record.status);
        const isFinal = record.status === 'DELIVERED' || record.status === 'CANCELLED';
        return (
          <Space size={0} wrap>
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => navigate(`/logistics/${record.logisticsOrderId}`)}
            >
              查看
            </Button>
            {canShip && (
              <Button
                type="link"
                size="small"
                icon={<RocketOutlined />}
                onClick={() => runAction(() => logisticsApi.ship(record.logisticsOrderId), '发货成功')}
              >
                发货
              </Button>
            )}
            <Button
              type="link"
              size="small"
              icon={<PrinterOutlined />}
              onClick={() => runAction(() => logisticsApi.generateLabel(record.logisticsOrderId), '面单生成成功')}
            >
              面单
            </Button>
            {!isFinal && (
              <Button
                type="link"
                size="small"
                icon={<StopOutlined />}
                onClick={() => runAction(() => logisticsApi.cancel(record.logisticsOrderId), '已取消')}
              >
                取消
              </Button>
            )}
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => runAction(() => logisticsApi.deleteLogistics(record.logisticsOrderId), '删除成功')}
            >
              删除
            </Button>
          </Space>
        );
      }
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>物流单列表</Title>
        <Space>
          <Can permission="logistics:import">
            <Button icon={<ImportOutlined />} onClick={() => navigate('/logistics/import')}>平台物流导入</Button>
          </Can>
          <Can permission="logistics:write">
            <Button icon={<RocketOutlined />} onClick={() => setBatchShipVisible(true)}>批量发货</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/logistics/create')}>
              创建物流单
            </Button>
          </Can>
        </Space>
      </div>

      <Card size="small" style={{ marginBottom: 8 }}>
        <Space wrap size={12}>
          <Input
            placeholder="物流单号/运单号/承运商"
            allowClear
            style={{ width: 220 }}
            value={searchParams.keyword}
            onChange={(e) => setSearchParams((prev) => ({ ...prev, keyword: e.target.value }))}
            onPressEnter={handleSearch}
          />
          <Input
            placeholder="运单号"
            allowClear
            style={{ width: 180 }}
            value={searchParams.trackingNumber}
            onChange={(e) => setSearchParams((prev) => ({ ...prev, trackingNumber: e.target.value }))}
            onPressEnter={handleSearch}
          />
          <Select
            placeholder="物流状态"
            allowClear
            style={{ width: 140 }}
            value={searchParams.status}
            onChange={(value) => setSearchParams((prev) => ({ ...prev, status: value }))}
            options={statusOptions}
          />
          <Select
            placeholder="物流渠道"
            allowClear
            showSearch
            optionFilterProp="label"
            style={{ width: 180 }}
            value={searchParams.channelId}
            onChange={(value) => setSearchParams((prev) => ({ ...prev, channelId: value }))}
            options={channels.map((item) => ({
              value: item.channelCode,
              label: `${item.channelName}（${item.channelCode}）`
            }))}
          />
          <RangePicker
            value={searchParams.dateRange}
            onChange={(dateRange) => setSearchParams((prev) => ({ ...prev, dateRange }))}
          />
          <Button icon={<SearchOutlined />} type="primary" onClick={handleSearch}>查询</Button>
          <Button onClick={handleReset}>重置</Button>
        </Space>
      </Card>

      <Card size="small">
        <Table
          rowKey="logisticsOrderId"
          size="small"
          loading={loading}
          dataSource={rows}
          columns={columns}
          scroll={{ x: 1700 }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={(pageInfo) =>
            setPagination((prev) => ({ ...prev, current: pageInfo.current, pageSize: pageInfo.pageSize }))
          }
        />
      </Card>

      <BatchShipModal
        open={batchShipVisible}
        channels={channels}
        onCancel={() => setBatchShipVisible(false)}
        onShipped={() => fetchRows()}
      />
    </div>
  );
};

export default LogisticsList;
