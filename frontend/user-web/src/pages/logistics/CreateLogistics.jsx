import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Input,
  Row,
  Select,
  Space,
  Typography,
  message
} from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { logisticsApi, logisticsChannelApi, orderApi } from '../../services/api';
import { DEFAULT_TENANT_ID } from '../../constants/logistics';

const { Title, Text } = Typography;

/**
 * 创建物流单：字段与后端 CreateLogisticsRequest 一一对应
 * （订单可用 orderId 或订单号定位，渠道可用 channelId 或渠道编码定位）。
 */
const CreateLogistics = () => {
  const navigate = useNavigate();
  const [channels, setChannels] = useState([]);
  const [loadingChannels, setLoadingChannels] = useState(false);
  const [orderTip, setOrderTip] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    orderNumber: '',
    channelCode: undefined,
    trackingNumber: '',
    carrier: '',
    shipImmediately: false
  });

  useEffect(() => {
    loadChannels();
  }, []);

  const loadChannels = async () => {
    setLoadingChannels(true);
    try {
      const data = await logisticsChannelApi.getChannels({ onlyActive: true });
      setChannels(data || []);
    } catch {
      message.error('加载物流渠道失败');
    } finally {
      setLoadingChannels(false);
    }
  };

  // 创建前按订单号校验订单是否存在，避免提交后才知道订单不存在
  const handleOrderNumberBlur = async () => {
    const orderNumber = form.orderNumber.trim();
    if (!orderNumber) {
      setOrderTip(null);
      return;
    }
    try {
      const data = await orderApi.getOrders({ tenantId: DEFAULT_TENANT_ID, orderNumber, page: 0, size: 1 });
      const matched = data?.content?.[0];
      setOrderTip(
        matched
          ? { type: 'success', text: `已匹配订单：${matched.orderNumber}（状态：${matched.statusLabel || matched.status}）` }
          : { type: 'warning', text: '系统内未找到该订单号，创建将失败，请先到订单列表确认' }
      );
    } catch {
      setOrderTip(null);
    }
  };

  const handleSubmit = async () => {
    if (!form.orderNumber.trim()) {
      message.warning('请输入订单编号');
      return;
    }
    if (!form.channelCode) {
      message.warning('请选择物流渠道');
      return;
    }
    setSubmitting(true);
    try {
      const created = await logisticsApi.createLogistics({
        tenantId: DEFAULT_TENANT_ID,
        orderNumber: form.orderNumber.trim(),
        channelCode: form.channelCode,
        trackingNumber: form.trackingNumber.trim() || undefined,
        carrier: form.carrier.trim() || undefined,
        shipImmediately: form.shipImmediately
      });
      message.success('物流单创建成功');
      navigate(`/logistics/${created.logisticsOrderId}`);
    } catch (error) {
      message.error(`创建失败：${error?.message || '未知错误'}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 8 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/logistics')}>返回列表</Button>
        <Title level={4} style={{ margin: 0 }}>创建物流单</Title>
      </div>

      <Card>
        <Row gutter={16}>
          <Col span={12}>
            <div style={{ marginBottom: 16 }}>
              <div style={{ marginBottom: 4 }}>订单编号 *</div>
              <Input
                placeholder="系统订单号，如 ORD-2026-0001"
                value={form.orderNumber}
                onChange={(e) => setForm((prev) => ({ ...prev, orderNumber: e.target.value }))}
                onBlur={handleOrderNumberBlur}
              />
              {orderTip && (
                <Alert style={{ marginTop: 8 }} type={orderTip.type} message={orderTip.text} showIcon />
              )}
            </div>
            <div style={{ marginBottom: 16 }}>
              <div style={{ marginBottom: 4 }}>物流渠道 *</div>
              <Select
                style={{ width: '100%' }}
                loading={loadingChannels}
                showSearch
                optionFilterProp="label"
                placeholder="请选择物流渠道"
                value={form.channelCode}
                onChange={(value) => setForm((prev) => ({ ...prev, channelCode: value }))}
                options={channels.map((item) => ({
                  value: item.channelCode,
                  label: `${item.channelName}（${item.channelCode}）`
                }))}
              />
              <Text type="secondary" style={{ fontSize: 12 }}>
                渠道列表来自「物流渠道」维护功能，缺失时请先在渠道管理中新增。
              </Text>
            </div>
          </Col>
          <Col span={12}>
            <div style={{ marginBottom: 16 }}>
              <div style={{ marginBottom: 4 }}>运单号</div>
              <Input
                placeholder="承运商运单号，可创建后补录"
                value={form.trackingNumber}
                onChange={(e) => setForm((prev) => ({ ...prev, trackingNumber: e.target.value }))}
              />
            </div>
            <div style={{ marginBottom: 16 }}>
              <div style={{ marginBottom: 4 }}>承运商</div>
              <Input
                placeholder="留空则取渠道名称"
                value={form.carrier}
                onChange={(e) => setForm((prev) => ({ ...prev, carrier: e.target.value }))}
              />
            </div>
            <Checkbox
              checked={form.shipImmediately}
              onChange={(e) => setForm((prev) => ({ ...prev, shipImmediately: e.target.checked }))}
            >
              创建后立即发货（状态置为已揽收）
            </Checkbox>
          </Col>
        </Row>

        <Space style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
          <Button onClick={() => navigate('/logistics')}>取消</Button>
          <Button type="primary" icon={<PlusOutlined />} loading={submitting} onClick={handleSubmit}>
            创建物流单
          </Button>
        </Space>
      </Card>
    </div>
  );
};

export default CreateLogistics;
