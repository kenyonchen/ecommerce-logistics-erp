import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Space, Input, Select, DatePicker, Typography, Card, Tag, Popconfirm, message } from 'antd';
import { PlusOutlined, SearchOutlined, EyeOutlined, ImportOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { financeApi } from '../../services/api';
import {
  DEFAULT_TENANT_ID,
  transactionTypeOptions,
  transactionTypeMap,
  transactionStatusOptions,
  transactionStatusMap,
  formatAmount
} from '../../constants/finance';

const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const FinanceList = () => {
  const navigate = useNavigate();
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0
  });

  // 查询条件（与后端 GET /finance/transactions 参数对齐；金额区间后端不支持，不提供筛选）
  const [searchParams, setSearchParams] = useState({
    type: undefined,
    status: undefined,
    referenceId: '',
    dateRange: []
  });

  // 获取交易列表（服务端分页：antd current 从 1 映射为后端 page 从 0）
  const fetchTransactions = useCallback(async () => {
    setLoading(true);
    try {
      const [startDate, endDate] = searchParams.dateRange || [];
      const params = {
        tenantId: DEFAULT_TENANT_ID,
        page: (pagination.current || 1) - 1,
        size: pagination.pageSize || 20,
        type: searchParams.type || undefined,
        status: searchParams.status || undefined,
        referenceId: searchParams.referenceId || undefined,
        // 后端 LocalDateTime + ISO.DATE_TIME 绑定，不能带 Z 偏移后缀，传本地日界
        from: startDate ? startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss') : undefined,
        to: endDate ? endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss') : undefined
      };
      const result = await financeApi.getTransactions(params);
      setTransactions(result?.content || []);
      setPagination((prev) => ({
        ...prev,
        total: result?.total || 0,
        current: (result?.page ?? 0) + 1
      }));
    } catch (error) {
      console.error('获取交易列表失败:', error);
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize, searchParams]);

  // 初始化与条件变化加载数据
  useEffect(() => {
    fetchTransactions();
  }, [fetchTransactions]);

  // 处理分页变化
  const handleTableChange = (pageInfo) => {
    setPagination((prev) => ({
      ...prev,
      current: pageInfo.current || 1,
      pageSize: pageInfo.pageSize || 20
    }));
  };

  // 处理查询
  const handleSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
    fetchTransactions();
  };

  // 处理重置
  const handleReset = () => {
    setSearchParams({
      type: undefined,
      status: undefined,
      referenceId: '',
      dateRange: []
    });
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  // PENDING 状态推进：完成 / 失败 / 取消
  const handleStatusAction = async (action, transactionId) => {
    try {
      await financeApi[`${action}Transaction`](transactionId);
      message.success('状态更新成功');
      fetchTransactions();
    } catch (error) {
      console.error('状态更新失败:', error);
    }
  };

  // 表格列配置
  const columns = [
    {
      title: '交易ID',
      dataIndex: 'transactionId',
      key: 'transactionId',
      width: 260,
      ellipsis: true
    },
    {
      title: '交易类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (text) => {
        const matched = transactionTypeMap[text];
        return matched ? <Tag color={matched.color}>{matched.text}</Tag> : text || '-';
      }
    },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 120,
      render: (text, record) => formatAmount(text, record.currency)
    },
    {
      title: '币种',
      dataIndex: 'currency',
      key: 'currency',
      width: 80
    },
    {
      title: '关联单据',
      dataIndex: 'referenceId',
      key: 'referenceId',
      width: 180,
      render: (text, record) =>
        text ? `${text}${record.referenceType ? `（${record.referenceType}）` : ''}` : '-'
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (text) => {
        const matched = transactionStatusMap[text];
        return matched ? <Tag color={matched.color}>{matched.text}</Tag> : text || '-';
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (text) => (text ? new Date(text).toLocaleString() : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 230,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/finance/${record.transactionId}`)}>
            查看
          </Button>
          {['PENDING', 'FAILED'].includes(record.status) && (
            <>
              {record.status === 'PENDING' && (
                <>
                  <Popconfirm
                    title="确认将该笔交易标记为成功？"
                    okText="确定"
                    cancelText="取消"
                    onConfirm={() => handleStatusAction('complete', record.transactionId)}
                  >
                    <Button type="link">完成</Button>
                  </Popconfirm>
                  <Popconfirm
                    title="确认将该笔交易标记为失败？"
                    okText="确定"
                    cancelText="取消"
                    onConfirm={() => handleStatusAction('fail', record.transactionId)}
                  >
                    <Button type="link" danger>失败</Button>
                  </Popconfirm>
                </>
              )}
              <Popconfirm
                title="确认取消该笔交易？"
                okText="确定"
                cancelText="取消"
                onConfirm={() => handleStatusAction('cancel', record.transactionId)}
              >
                <Button type="link">取消</Button>
              </Popconfirm>
            </>
          )}
        </Space>
      )
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <Title level={4}>财务交易列表</Title>
        <Space>
          <Button icon={<ImportOutlined />}>
            <Link to="/finance/import">财务导入</Link>
          </Button>
          <Button type="primary" icon={<PlusOutlined />}>
            <Link to="/finance/create">添加交易</Link>
          </Button>
        </Space>
      </div>

      {/* 查询条件 */}
      <Card style={{ marginBottom: 5 }} styles={{ body: { padding: 16 } }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>交易类型：</span>
            <Select
              placeholder="请选择类型"
              allowClear
              value={searchParams.type}
              onChange={(value) => setSearchParams((prev) => ({ ...prev, type: value }))}
              style={{ width: 130 }}
            >
              {transactionTypeOptions.map((option) => (
                <Option key={option.value} value={option.value}>
                  {option.label}
                </Option>
              ))}
            </Select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>状态：</span>
            <Select
              placeholder="请选择状态"
              allowClear
              value={searchParams.status}
              onChange={(value) => setSearchParams((prev) => ({ ...prev, status: value }))}
              style={{ width: 130 }}
            >
              {transactionStatusOptions.map((option) => (
                <Option key={option.value} value={option.value}>
                  {option.label}
                </Option>
              ))}
            </Select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>关联单据号：</span>
            <Input
              placeholder="订单号/关联单据号"
              allowClear
              value={searchParams.referenceId}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, referenceId: e.target.value }))}
              style={{ width: 180 }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>创建时间：</span>
            <RangePicker
              value={searchParams.dateRange}
              onChange={(dateRange) => setSearchParams((prev) => ({ ...prev, dateRange: dateRange || [] }))}
            />
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button onClick={handleReset}>重置</Button>
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            查询
          </Button>
        </div>
      </Card>

      {/* 交易表格 */}
      <Card styles={{ body: { padding: 16 } }}>
        <Table
          columns={columns}
          dataSource={transactions}
          rowKey="transactionId"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={handleTableChange}
          scroll={{ x: 1200 }}
        />
      </Card>
    </div>
  );
};

export default FinanceList;
