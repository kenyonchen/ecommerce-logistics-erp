import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Descriptions, Tag, Spin, Alert, Popconfirm, Table } from 'antd';
import { ArrowLeftOutlined, EyeOutlined } from '@ant-design/icons';
import { financeApi } from '../../services/api';
import {
  transactionTypeMap,
  transactionStatusMap,
  formatAmount
} from '../../constants/finance';

const { Title } = Typography;

const FinanceDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [finance, setFinance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [relatedRows, setRelatedRows] = useState([]);
  const [relatedLoading, setRelatedLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // 获取财务详情：GET /finance/transactions/{transactionId}
  const fetchFinanceDetail = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await financeApi.getTransactionById(id);
      setFinance(data);
    } catch (err) {
      setError(err?.message || '获取财务详情失败');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchFinanceDetail();
  }, [fetchFinanceDetail]);

  // 详情加载后按关联单据查询同类流水
  useEffect(() => {
    const referenceId = finance?.referenceId;
    const referenceType = finance?.referenceType;
    if (!referenceId) {
      setRelatedRows([]);
      return;
    }
    let cancelled = false;
    const fetchRelated = async () => {
      setRelatedLoading(true);
      try {
        const data = await financeApi.getTransactionsByReference(referenceId, referenceType || undefined);
        if (!cancelled) setRelatedRows(Array.isArray(data) ? data : data?.content || []);
      } catch {
        if (!cancelled) setRelatedRows([]);
      } finally {
        if (!cancelled) setRelatedLoading(false);
      }
    };
    fetchRelated();
    return () => {
      cancelled = true;
    };
  }, [finance?.referenceId, finance?.referenceType]);

  // 返回列表页
  const handleBack = () => {
    navigate('/finance');
  };

  // PENDING 状态推进：完成 / 失败 / 取消
  const handleStatusAction = async (action) => {
    setSubmitting(true);
    try {
      await financeApi[`${action}Transaction`](id);
      await fetchFinanceDetail();
    } catch (err) {
      console.error('状态更新失败:', err);
    } finally {
      setSubmitting(false);
    }
  };

  // 关联流水列配置
  const relatedColumns = [
    { title: '交易ID', dataIndex: 'transactionId', width: 260, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (text) => {
        const matched = transactionTypeMap[text];
        return matched ? <Tag color={matched.color}>{matched.text}</Tag> : text || '-';
      }
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      render: (text, record) => formatAmount(text, record.currency)
    },
    {
      title: '币种',
      dataIndex: 'currency',
      width: 80
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (text) => {
        const matched = transactionStatusMap[text];
        return matched ? <Tag color={matched.color}>{matched.text}</Tag> : text || '-';
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '操作',
      width: 100,
      render: (text, record) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => navigate(`/finance/${record.transactionId}`)}
        >
          查看
        </Button>
      )
    }
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin />
        <div style={{ marginTop: 8 }}>加载中...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <Button icon={<ArrowLeftOutlined />} onClick={handleBack} style={{ marginBottom: 12 }}>
          返回列表
        </Button>
        <Alert type="error" showIcon title="获取财务详情失败" description={error} />
      </div>
    );
  }

  if (!finance) {
    return (
      <div>
        <Button icon={<ArrowLeftOutlined />} onClick={handleBack} style={{ marginBottom: 12 }}>
          返回列表
        </Button>
        <Alert type="warning" showIcon title="财务记录不存在" />
      </div>
    );
  }

  const typeMeta = transactionTypeMap[finance.type];
  const statusMeta = transactionStatusMap[finance.status];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>财务详情</Title>
        </div>
        {['PENDING', 'FAILED'].includes(finance.status) && (
          <Space>
            {finance.status === 'PENDING' && (
              <>
                <Popconfirm
                  title="确认将该笔交易标记为成功？"
                  okText="确定"
                  cancelText="取消"
                  onConfirm={() => handleStatusAction('complete')}
                >
                  <Button type="primary" loading={submitting}>标记成功</Button>
                </Popconfirm>
                <Popconfirm
                  title="确认将该笔交易标记为失败？"
                  okText="确定"
                  cancelText="取消"
                  onConfirm={() => handleStatusAction('fail')}
                >
                  <Button danger loading={submitting}>标记失败</Button>
                </Popconfirm>
              </>
            )}
            <Popconfirm
              title="确认取消该笔交易？"
              okText="确定"
              cancelText="取消"
              onConfirm={() => handleStatusAction('cancel')}
            >
              <Button loading={submitting}>取消交易</Button>
            </Popconfirm>
          </Space>
        )}
      </div>

      {/* 交易基本信息 */}
      <Card style={{ marginBottom: 5 }}>
        <Descriptions title="基本信息" column={2}>
          <Descriptions.Item label="交易ID">{finance.transactionId}</Descriptions.Item>
          <Descriptions.Item label="交易类型">
            {typeMeta ? <Tag color={typeMeta.color}>{typeMeta.text}</Tag> : finance.type || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="交易金额">{formatAmount(finance.amount, finance.currency)}</Descriptions.Item>
          <Descriptions.Item label="币种">{finance.currency || '-'}</Descriptions.Item>
          <Descriptions.Item label="交易状态">
            {statusMeta ? <Tag color={statusMeta.color}>{statusMeta.text}</Tag> : finance.status || '-'}
          </Descriptions.Item>
          {/* 关联单据的号与类型合并成一项（与列表页"关联单据"列同款渲染）：
              单列项共 8 项才能和后面 span=2 的描述行凑成整行，9 项会剩半行触发 antd 的 span 告警 */}
          <Descriptions.Item label="关联单据">
            {finance.referenceId ? `${finance.referenceId}（${finance.referenceType || '-'}）` : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {finance.createdAt ? new Date(finance.createdAt).toLocaleString() : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {finance.updatedAt ? new Date(finance.updatedAt).toLocaleString() : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="交易描述" span={2}>
            {finance.description || '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 按关联单据查询同类流水 */}
      {finance.referenceId && (
        <Card
          style={{ marginBottom: 5 }}
          title={`关联流水（${finance.referenceId}${finance.referenceType ? ` / ${finance.referenceType}` : ''}）`}
        >
          <Table
            columns={relatedColumns}
            dataSource={relatedRows}
            rowKey={(record) => record.transactionId}
            loading={relatedLoading}
            pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条` }}
            size="small"
            scroll={{ x: 1000 }}
          />
        </Card>
      )}
    </div>
  );
};

export default FinanceDetail;
