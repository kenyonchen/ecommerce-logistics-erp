import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message
} from 'antd';
import { CalculatorOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { logisticsChannelApi } from '../../services/api';

const { Title, Text } = Typography;

/**
 * 物流渠道维护：渠道编码是平台物流导入与批量发货的匹配依据，
 * 导入行「无法匹配物流渠道」时必须在此新增或启用对应渠道。
 */
const LogisticsChannels = () => {
  const [channels, setChannels] = useState([]);
  const [loading, setLoading] = useState(false);
  const [onlyActive, setOnlyActive] = useState(false);
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const [feeFor, setFeeFor] = useState(null);
  const [feeResult, setFeeResult] = useState(null);
  const [form] = Form.useForm();
  const [feeForm] = Form.useForm();

  const fetchChannels = useCallback(async () => {
    setLoading(true);
    try {
      const data = await logisticsChannelApi.getChannels(onlyActive ? { onlyActive: true } : undefined);
      setChannels(data || []);
    } catch (error) {
      message.error(`加载渠道失败：${error?.message || '未知错误'}`);
    } finally {
      setLoading(false);
    }
  }, [onlyActive]);

  useEffect(() => {
    fetchChannels();
  }, [fetchChannels]);

  const openEditor = (record) => {
    setEditing(record || {});
    setFeeResult(null);
    form.setFieldsValue(
      record
        ? { ...record, countries: (record.countries || []).join(',') }
        : { channelCode: '', channelName: '', currency: 'CNY', basePrice: 10, pricePerGram: 0.01, active: true, countries: '' }
    );
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const payload = {
      ...values,
      countries: values.countries
        ? String(values.countries)
            .split(/[,，\s]+/)
            .map((item) => item.trim())
            .filter(Boolean)
        : []
    };
    setSaving(true);
    try {
      if (editing?.channelId) {
        await logisticsChannelApi.updateChannel(editing.channelId, payload);
        message.success('渠道已更新');
      } else {
        await logisticsChannelApi.createChannel(payload);
        message.success('渠道已创建');
      }
      setEditing(null);
      fetchChannels();
    } catch (error) {
      message.error(`保存失败：${error?.message || '未知错误'}`);
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (record, active) => {
    try {
      await logisticsChannelApi.changeStatus(record.channelId, active);
      message.success(active ? '渠道已启用' : '渠道已停用');
      fetchChannels();
    } catch (error) {
      message.error(`状态变更失败：${error?.message || '未知错误'}`);
    }
  };

  const handleDelete = (record) => {
    Modal.confirm({
      title: `删除渠道 ${record.channelName}`,
      content: '存在关联物流单的渠道无法删除，可改为停用。确认删除？',
      okType: 'danger',
      onOk: async () => {
        try {
          await logisticsChannelApi.deleteChannel(record.channelId);
          message.success('渠道已删除');
          fetchChannels();
        } catch (error) {
          message.error(`删除失败：${error?.message || '未知错误'}`);
        }
      }
    });
  };

  const openFee = (record) => {
    setFeeFor(record);
    setFeeResult(null);
    feeForm.setFieldsValue({ weight: 500, country: (record.countries || [])[0] || '' });
  };

  const handleCalculateFee = async () => {
    const values = await feeForm.validateFields();
    try {
      const data = await logisticsChannelApi.calculateFee(
        feeFor.channelId,
        values.weight,
        values.country || undefined
      );
      setFeeResult(data);
    } catch (error) {
      message.error(`试算失败：${error?.message || '未知错误'}`);
    }
  };

  const columns = [
    { title: '渠道编码', dataIndex: 'channelCode', width: 140 },
    { title: '渠道名称', dataIndex: 'channelName', width: 160 },
    { title: '说明', dataIndex: 'description', ellipsis: true, render: (v) => v || '-' },
    {
      title: '服务范围',
      dataIndex: 'countries',
      width: 180,
      render: (value) =>
        !value || value.length === 0 ? <Tag>全球可用</Tag> : value.map((c) => <Tag key={c}>{c}</Tag>)
    },
    { title: '首重费', dataIndex: 'basePrice', width: 100, render: (v) => v ?? '-' },
    { title: '续重(元/克)', dataIndex: 'pricePerGram', width: 120, render: (v) => v ?? '-' },
    { title: '币种', dataIndex: 'currency', width: 80 },
    {
      title: '状态',
      dataIndex: 'active',
      width: 100,
      render: (value, record) => (
        <Switch
          size="small"
          checked={value}
          onChange={(checked) => handleToggle(record, checked)}
          checkedChildren="启用"
          unCheckedChildren="停用"
        />
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size={0}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEditor(record)}>编辑</Button>
          <Button type="link" size="small" icon={<CalculatorOutlined />} onClick={() => openFee(record)}>试算</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>
            删除
          </Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>物流渠道</Title>
        <Space>
          <Text>仅看启用</Text>
          <Switch checked={onlyActive} onChange={setOnlyActive} />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor(null)}>新增渠道</Button>
        </Space>
      </div>

      <Card size="small">
        <Table
          rowKey="channelId"
          size="small"
          loading={loading}
          dataSource={channels}
          columns={columns}
          scroll={{ x: 1200 }}
          pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }}
        />
      </Card>

      <Modal
        title={editing?.channelId ? '编辑物流渠道' : '新增物流渠道'}
        open={!!editing}
        onOk={handleSubmit}
        confirmLoading={saving}
        onCancel={() => setEditing(null)}
        okText="保存"
        width={640}
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                label="渠道编码"
                name="channelCode"
                rules={[{ required: true, message: '请输入渠道编码，如 SF' }]}
                extra="平台回传的 channelCode/carrier 与该编码匹配"
              >
                <Input placeholder="SF" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="渠道名称" name="channelName" rules={[{ required: true, message: '请输入渠道名称' }]}>
                <Input placeholder="顺丰速运" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item label="说明" name="description">
                <Input placeholder="选填" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="国家/地区代码" name="countries" extra="逗号分隔，留空表示全球可用">
                <Input placeholder="CN,US,DE" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="币种" name="currency">
                <Input placeholder="CNY" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="首重费" name="basePrice" rules={[{ required: true, message: '请输入首重费' }]}>
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="续重单价（元/克）" name="pricePerGram" rules={[{ required: true, message: '请输入续重单价' }]}>
                <InputNumber min={0} step={0.001} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="启用" name="active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      <Modal
        title={`运费试算：${feeFor?.channelName || ''}`}
        open={!!feeFor}
        onCancel={() => setFeeFor(null)}
        footer={[<Button key="close" onClick={() => setFeeFor(null)}>关闭</Button>]}
      >
        <Form form={feeForm} layout="inline">
          <Form.Item label="重量(克)" name="weight" rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item label="国家" name="country">
            <Input style={{ width: 120 }} placeholder="CN" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={handleCalculateFee}>试算</Button>
          </Form.Item>
        </Form>
        {feeResult && (
          <Alert
            style={{ marginTop: 16 }}
            type="success"
            showIcon
            message={`首重 ${feeResult.baseFee} + 续重 ${feeResult.weightFee} = 合计 ${feeResult.totalFee} ${feeResult.currency}`}
          />
        )}
      </Modal>
    </div>
  );
};

export default LogisticsChannels;
