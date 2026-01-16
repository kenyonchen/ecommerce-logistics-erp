import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Space, Card, Typography, Descriptions, Tag, Progress, message, Table, Modal, Input, Select, DatePicker } from 'antd';
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined, InboxOutlined, EnvironmentOutlined, FileSearchOutlined } from '@ant-design/icons';
import { warehouseApi } from '../../services/api';

const { Title } = Typography;
const { Option } = Select;

const WarehouseDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [warehouse, setWarehouse] = useState(null);
  const [loading, setLoading] = useState(false);
  const [inventoryModalVisible, setInventoryModalVisible] = useState(false);
  const [locationModalVisible, setLocationModalVisible] = useState(false);
  const [inventoryData, setInventoryData] = useState([]);
  const [locationData, setLocationData] = useState([]);
  const [inventoryCheckModalVisible, setInventoryCheckModalVisible] = useState(false);

  // 仓库状态映射
  const statusMap = {
    ACTIVE: { text: '活跃', color: 'success' },
    INACTIVE: { text: '不活跃', color: 'default' },
    MAINTENANCE: { text: '维护中', color: 'warning' }
  };

  // 仓库类型映射
  const typeMap = {
    PRIMARY: '主仓库',
    SECONDARY: '二级仓库',
    REGIONAL: '区域仓库',
    CROSS_DOCK: '跨境仓库'
  };

  // 获取仓库详情
  const fetchWarehouseDetail = async () => {
    setLoading(true);
    try {
      // 这里简化处理，实际应该调用后端的详情API
      // 暂时使用模拟数据
      const capacity = 50000;
      const usage = 35000;
      const usageRate = (usage / capacity) * 100;
      
      const mockWarehouseDetail = {
        id: id,
        warehouseName: `仓库${id.split('-')[1]}`,
        location: `城市${id.split('-')[1]}`,
        type: 'PRIMARY',
        status: 'ACTIVE',
        capacity: capacity,
        usage: usage,
        usageRate: usageRate,
        manager: `管理员${id.split('-')[1]}`,
        contact: `1380013800${id.split('-')[1]}`,
        address: `城市${id.split('-')[1]} 区${id.split('-')[1]} 街道${id.split('-')[1]}号`,
        description: '这是一个测试仓库，用于展示仓库详情页面。',
        createdAt: new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - 15 * 24 * 60 * 60 * 1000).toISOString()
      };
      
      setWarehouse(mockWarehouseDetail);
      
      // 模拟库存数据
      setInventoryData([
        {
          id: 'inv-001',
          productId: 'prod-001',
          productName: 'iPhone 15 Pro',
          sku: 'SKU001',
          quantity: 100,
          location: 'A-01-01',
          lastUpdated: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString()
        },
        {
          id: 'inv-002',
          productId: 'prod-002',
          productName: 'AirPods Pro 2',
          sku: 'SKU002',
          quantity: 200,
          location: 'A-01-02',
          lastUpdated: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString()
        }
      ]);
      
      // 模拟库位数据
      setLocationData([
        {
          id: 'loc-001',
          code: 'A-01-01',
          type: 'SHELF',
          status: 'OCCUPIED',
          capacity: 500,
          usage: 100,
          usageRate: 20
        },
        {
          id: 'loc-002',
          code: 'A-01-02',
          type: 'SHELF',
          status: 'OCCUPIED',
          capacity: 500,
          usage: 200,
          usageRate: 40
        },
        {
          id: 'loc-003',
          code: 'A-01-03',
          type: 'SHELF',
          status: 'EMPTY',
          capacity: 500,
          usage: 0,
          usageRate: 0
        }
      ]);
    } catch (error) {
      console.error('获取仓库详情失败:', error);
      message.error('获取仓库详情失败');
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    fetchWarehouseDetail();
  }, [id]);

  // 返回列表页
  const handleBack = () => {
    navigate('/warehouses');
  };

  // 编辑仓库
  const handleEdit = () => {
    // 实际应该跳转到编辑页面
    message.info('编辑功能开发中');
  };

  // 删除仓库
  const handleDelete = () => {
    // 实际应该调用后端API删除
    message.info('删除功能开发中');
  };

  // 处理库存管理
  const handleInventoryManagement = () => {
    setInventoryModalVisible(true);
  };

  // 处理库位管理
  const handleLocationManagement = () => {
    setLocationModalVisible(true);
  };

  // 处理库存盘点
  const handleInventoryCheck = () => {
    message.loading('正在生成库存盘点表...');
    
    // 模拟生成成功
    setTimeout(() => {
      message.success('库存盘点表生成成功');
      setInventoryCheckModalVisible(true);
    }, 1000);
  };

  // 库存表格列
  const inventoryColumns = [
    {
      title: '产品名称',
      dataIndex: 'productName',
      key: 'productName'
    },
    {
      title: 'SKU',
      dataIndex: 'sku',
      key: 'sku',
      width: 100
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 80
    },
    {
      title: '库位',
      dataIndex: 'location',
      key: 'location',
      width: 100
    },
    {
      title: '最后更新',
      dataIndex: 'lastUpdated',
      key: 'lastUpdated',
      width: 180,
      render: (text) => new Date(text).toLocaleString()
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: () => (
        <Space size="middle">
          <Button type="link">编辑</Button>
          <Button type="link">调整</Button>
        </Space>
      )
    }
  ];

  // 库位表格列
  const locationColumns = [
    {
      title: '库位编码',
      dataIndex: 'code',
      key: 'code',
      width: 120
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (text) => {
        const typeMap = {
          SHELF: '货架',
          PALLET: '托盘',
          BIN: '料箱'
        };
        return typeMap[text] || text;
      }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (text) => {
        const statusMap = {
          OCCUPIED: <Tag color="blue">已占用</Tag>,
          EMPTY: <Tag color="green">空</Tag>,
          MAINTENANCE: <Tag color="orange">维护中</Tag>
        };
        return statusMap[text] || text;
      }
    },
    {
      title: '容量利用率',
      dataIndex: 'usageRate',
      key: 'usageRate',
      width: 120,
      render: (text) => <Progress percent={text} size="small" />
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: () => (
        <Space size="middle">
          <Button type="link">编辑</Button>
          <Button type="link">查看</Button>
        </Space>
      )
    }
  ];

  if (loading) {
    return <div>加载中...</div>;
  }

  if (!warehouse) {
    return <div>仓库不存在</div>;
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
          <Title level={4}>仓库详情</Title>
        </div>
        <Space>
          <Button icon={<InboxOutlined />} onClick={handleInventoryManagement}>库存管理</Button>
          <Button icon={<EnvironmentOutlined />} onClick={handleLocationManagement}>库位管理</Button>
          <Button icon={<FileSearchOutlined />} onClick={handleInventoryCheck}>库存盘点</Button>
          <Button type="primary" icon={<EditOutlined />} onClick={handleEdit}>编辑</Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete}>删除</Button>
        </Space>
      </div>

      {/* 仓库基本信息 */}
      <Card style={{ marginBottom: 5 }}>
        <Descriptions title="基本信息" column={2}>
          <Descriptions.Item label="仓库ID">{warehouse.id}</Descriptions.Item>
          <Descriptions.Item label="仓库名称">{warehouse.warehouseName}</Descriptions.Item>
          <Descriptions.Item label="位置">{warehouse.location}</Descriptions.Item>
          <Descriptions.Item label="类型">{typeMap[warehouse.type]}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={statusMap[warehouse.status].color}>
              {statusMap[warehouse.status].text}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="管理员">{warehouse.manager}</Descriptions.Item>
          <Descriptions.Item label="联系电话">{warehouse.contact}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{new Date(warehouse.createdAt).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{new Date(warehouse.updatedAt).toLocaleString()}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 仓库容量信息 */}
      <Card style={{ marginBottom: 5 }}>
        <Descriptions title="容量信息" column={1}>
          <Descriptions.Item label="总容量">{warehouse.capacity}</Descriptions.Item>
          <Descriptions.Item label="已使用">{warehouse.usage}</Descriptions.Item>
          <Descriptions.Item label="容量利用率">
            <div>
              <Progress percent={Math.round(warehouse.usageRate)} status={warehouse.usageRate > 80 ? 'warning' : warehouse.usageRate > 90 ? 'error' : 'normal'} />
              <div style={{ marginTop: 8, fontSize: 14 }}>
                {Math.round(warehouse.usage)}/{warehouse.capacity} ({Math.round(warehouse.usageRate)}%)
              </div>
            </div>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 仓库地址和描述 */}
      <Card style={{ marginBottom: 5 }}>
        <Descriptions title="地址和描述" column={1}>
          <Descriptions.Item label="详细地址">{warehouse.address}</Descriptions.Item>
          <Descriptions.Item label="描述">{warehouse.description}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 库存管理弹窗 */}
      <Modal
        title="库存管理"
        open={inventoryModalVisible}
        onCancel={() => setInventoryModalVisible(false)}
        footer={null}
        width={800}
      >
        <div style={{ marginBottom: 5 }}>
          <Button type="primary">添加库存</Button>
        </div>
        <Table
          columns={inventoryColumns}
          dataSource={inventoryData}
          rowKey="id"
          pagination={{ pageSize: 10 }}
        />
      </Modal>

      {/* 库位管理弹窗 */}
      <Modal
        title="库位管理"
        open={locationModalVisible}
        onCancel={() => setLocationModalVisible(false)}
        footer={null}
        width={800}
      >
        <div style={{ marginBottom: 5 }}>
          <Button type="primary">添加库位</Button>
        </div>
        <Table
          columns={locationColumns}
          dataSource={locationData}
          rowKey="id"
          pagination={{ pageSize: 10 }}
        />
      </Modal>

      {/* 库存盘点弹窗 */}
      <Modal
        title="库存盘点"
        open={inventoryCheckModalVisible}
        onCancel={() => setInventoryCheckModalVisible(false)}
        footer={null}
        width={800}
      >
        <div style={{ marginBottom: 5 }}>
          <p>库存盘点表已生成，包含以下信息：</p>
          <ul>
            <li>盘点时间：{new Date().toLocaleString()}</li>
            <li>盘点仓库：{warehouse.warehouseName}</li>
            <li>盘点项目数：{inventoryData.length}</li>
            <li>盘点状态：待确认</li>
          </ul>
        </div>
        <Table
          columns={[
            ...inventoryColumns,
            {
              title: '盘点数量',
              key: 'checkQuantity',
              width: 100,
              render: () => <Input style={{ width: 80 }} placeholder="实际数量" />
            }
          ]}
          dataSource={inventoryData}
          rowKey="id"
          pagination={{ pageSize: 10 }}
        />
        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Button>导出盘点表</Button>
          <Button type="primary" style={{ marginLeft: 8 }}>确认盘点</Button>
        </div>
      </Modal>
    </div>
  );
};

export default WarehouseDetail;
