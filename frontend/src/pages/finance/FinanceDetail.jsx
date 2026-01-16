import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Descriptions, Tag, message, Table, Modal, DatePicker, Select } from 'antd';
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined, CheckCircleOutlined, CalculatorOutlined, FileTextOutlined } from '@ant-design/icons';
import { financeApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const FinanceDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [finance, setFinance] = useState(null);
  const [loading, setLoading] = useState(false);
  const [reconciliationModalVisible, setReconciliationModalVisible] = useState(false);
  const [expenseModalVisible, setExpenseModalVisible] = useState(false);
  const [reportModalVisible, setReportModalVisible] = useState(false);
  const [reconciliationData, setReconciliationData] = useState([]);

  // 交易类型映射
  const typeMap = {
    INCOME: '收入',
    EXPENSE: '支出',
    REFUND: '退款',
    TRANSFER: '转账'
  };

  // 交易状态映射
  const statusMap = {
    PENDING: { text: '待处理', color: 'processing' },
    COMPLETED: { text: '已完成', color: 'success' },
    FAILED: { text: '失败', color: 'error' },
    CANCELLED: { text: '已取消', color: 'default' }
  };

  // 获取财务详情
  const fetchFinanceDetail = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的详情API
      // 暂时使用模拟数据
      const mockFinanceDetail = {
        id: id,
        transactionId: `TXN${id.split('-')[1].padStart(8, '0')}`,
        type: 'INCOME',
        amount: 12500.50,
        status: 'COMPLETED',
        description: '销售订单支付',
        relatedOrderId: `ORD-${Math.floor(Math.random() * 10000)}`,
        paymentMethod: '支付宝',
        transactionDate: new Date(Date.now() - Math.floor(Math.random() * 30) * 24 * 60 * 60 * 1000).toISOString(),
        createdAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString()
      };
      
      setFinance(mockFinanceDetail);
      
      // 模拟对账数据
      setReconciliationData([
        {
          id: 'rec-001',
          orderId: mockFinanceDetail.relatedOrderId,
          orderAmount: 12500.50,
          paymentAmount: 12500.50,
          status: 'MATCHED',
          date: new Date().toISOString()
        },
        {
          id: 'rec-002',
          orderId: `ORD-${Math.floor(Math.random() * 10000)}`,
          orderAmount: 5000.00,
          paymentAmount: 5000.00,
          status: 'MATCHED',
          date: new Date().toISOString()
        }
      ]);
    } catch (error) {
      console.error('获取财务详情失败:', error);
      message.error('获取财务详情失败');
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchFinanceDetail();
  }, [id]);

  // 返回列表页
  const handleBack = () => {
    navigate('/finance');
  };

  // 编辑财务
  const handleEdit = () => {
    // 实际应该跳转到编辑页面
    message.info('编辑功能开发中');
  };

  // 删除财务
  const handleDelete = () => {
    // 实际应该调用后端API删除
    message.info('删除功能开发中');
  };

  // 处理订单对账
  const handleOrderReconciliation = () => {
    setReconciliationModalVisible(true);
  };

  // 处理费用核算
  const handleExpenseCalculation = () => {
    setExpenseModalVisible(true);
  };

  // 处理报表生成
  const handleReportGeneration = () => {
    message.loading('正在生成财务报表...');
    
    // 模拟生成成功
    setTimeout(() => {
      message.success('财务报表生成成功');
      setReportModalVisible(true);
    }, 1000);
  };

  // 对账表格列
  const reconciliationColumns = [
    {
      title: '订单ID',
      dataIndex: 'orderId',
      key: 'orderId'
    },
    {
      title: '订单金额',
      dataIndex: 'orderAmount',
      key: 'orderAmount',
      render: (text) => `¥${text.toFixed(2)}`
    },
    {
      title: '支付金额',
      dataIndex: 'paymentAmount',
      key: 'paymentAmount',
      render: (text) => `¥${text.toFixed(2)}`
    },
    {
      title: '对账状态',
      dataIndex: 'status',
      key: 'status',
      render: (text) => (
        <Tag color={text === 'MATCHED' ? 'success' : 'error'}>
          {text === 'MATCHED' ? '匹配' : '不匹配'}
        </Tag>
      )
    },
    {
      title: '对账日期',
      dataIndex: 'date',
      key: 'date',
      render: (text) => new Date(text).toLocaleString()
    }
  ];

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!finance) {
    return <div>财务记录不存在</div>;
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>财务详情</Title>
        </div>
        <Space>
          <Button icon={<CheckCircleOutlined />} onClick={handleOrderReconciliation}>订单对账</Button>
          <Button icon={<CalculatorOutlined />} onClick={handleExpenseCalculation}>费用核算</Button>
          <Button icon={<FileTextOutlined />} onClick={handleReportGeneration}>报表生成</Button>
          <Button icon={<EditOutlined />} onClick={handleEdit}>编辑</Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete}>删除</Button>
        </Space>
      </div>

      {/* 财务基本信息 */}
      <Card style={{ marginBottom: 16 }}>
        <Descriptions title="基本信息" column={2}>
          <Descriptions.Item label="财务ID">{finance.id}</Descriptions.Item>
          <Descriptions.Item label="交易ID">{finance.transactionId}</Descriptions.Item>
          <Descriptions.Item label="交易类型">{typeMap[finance.type]}</Descriptions.Item>
          <Descriptions.Item label="交易金额">¥{finance.amount.toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="交易状态">
            <Tag color={statusMap[finance.status].color}>
              {statusMap[finance.status].text}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="支付方式">{finance.paymentMethod}</Descriptions.Item>
          <Descriptions.Item label="关联订单">{finance.relatedOrderId}</Descriptions.Item>
          <Descriptions.Item label="交易日期">{new Date(finance.transactionDate).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{new Date(finance.createdAt).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{new Date(finance.updatedAt).toLocaleString()}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 交易描述 */}
      <Card style={{ marginBottom: 16 }}>
        <Descriptions title="交易描述" column={1}>
          <Descriptions.Item label="描述">{finance.description}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 订单对账弹窗 */}
      <Modal
        title="订单对账"
        open={reconciliationModalVisible}
        onCancel={() => setReconciliationModalVisible(false)}
        footer={null}
        width={800}
      >
        <div style={{ marginBottom: 16 }}>
          <Space>
            <DatePicker.RangePicker placeholder={['开始日期', '结束日期']} />
            <Select placeholder="选择支付方式">
              <Option value="all">全部</Option>
              <Option value="alipay">支付宝</Option>
              <Option value="wechat">微信支付</Option>
              <Option value="bank">银行转账</Option>
            </Select>
            <Button type="primary">查询</Button>
          </Space>
        </div>
        <Table
          columns={reconciliationColumns}
          dataSource={reconciliationData}
          rowKey="id"
          pagination={{ pageSize: 10 }}
        />
        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Button>导出对账结果</Button>
        </div>
      </Modal>

      {/* 费用核算弹窗 */}
      <Modal
        title="费用核算"
        open={expenseModalVisible}
        onCancel={() => setExpenseModalVisible(false)}
        footer={null}
        width={800}
      >
        <div style={{ marginBottom: 16 }}>
          <Space>
            <DatePicker.RangePicker placeholder={['开始日期', '结束日期']} />
            <Select placeholder="选择费用类型">
              <Option value="all">全部</Option>
              <Option value="logistics">物流费用</Option>
              <Option value="storage">仓储费用</Option>
              <Option value="purchase">采购费用</Option>
              <Option value="other">其他费用</Option>
            </Select>
            <Button type="primary">核算</Button>
          </Space>
        </div>
        <Card>
          <Descriptions column={2}>
            <Descriptions.Item label="物流费用">¥5,280.00</Descriptions.Item>
            <Descriptions.Item label="仓储费用">¥3,120.00</Descriptions.Item>
            <Descriptions.Item label="采购费用">¥18,500.00</Descriptions.Item>
            <Descriptions.Item label="其他费用">¥1,250.00</Descriptions.Item>
            <Descriptions.Item label="总费用" span={2}>
              <strong>¥28,150.00</strong>
            </Descriptions.Item>
          </Descriptions>
        </Card>
        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Button>导出核算结果</Button>
        </div>
      </Modal>

      {/* 报表生成弹窗 */}
      <Modal
        title="财务报表"
        open={reportModalVisible}
        onCancel={() => setReportModalVisible(false)}
        footer={null}
        width={800}
      >
        <div style={{ marginBottom: 16 }}>
          <p>财务报表已生成，包含以下信息：</p>
          <ul>
            <li>报表类型：月度财务报表</li>
            <li>报表期间：{new Date().getFullYear()}年{new Date().getMonth() + 1}月</li>
            <li>生成时间：{new Date().toLocaleString()}</li>
            <li>生成状态：成功</li>
          </ul>
        </div>
        <Card>
          <Descriptions column={2}>
            <Descriptions.Item label="总收入">¥128,500.00</Descriptions.Item>
            <Descriptions.Item label="总支出">¥28,150.00</Descriptions.Item>
            <Descriptions.Item label="净利润" span={2}>
              <strong>¥100,350.00</strong>
            </Descriptions.Item>
          </Descriptions>
        </Card>
        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Button>导出PDF</Button>
          <Button type="primary" style={{ marginLeft: 8 }}>导出Excel</Button>
        </div>
      </Modal>
    </div>
  );
};

export default FinanceDetail;
