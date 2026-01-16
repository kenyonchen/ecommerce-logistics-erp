import React, { useState, useEffect } from 'react';
import { Button, Table, Space, Modal, Form, Input, Select, message, Popconfirm, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, DetailOutlined } from '@ant-design/icons';
import { tenantApi } from '../../services/api';

const { Option } = Select;

const TenantList = () => {
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTenant, setEditingTenant] = useState(null);
  const [form] = Form.useForm();

  // 加载租户列表
  useEffect(() => {
    fetchTenants();
  }, []);

  const fetchTenants = async () => {
    setLoading(true);
    try {
      const response = await tenantApi.getTenants();
      setTenants(response);
    } catch (error) {
      message.error('加载租户列表失败');
    } finally {
      setLoading(false);
    }
  };

  // 打开创建租户模态框
  const handleAdd = () => {
    setEditingTenant(null);
    form.resetFields();
    setModalVisible(true);
  };

  // 打开编辑租户模态框
  const handleEdit = (tenant) => {
    setEditingTenant(tenant);
    form.setFieldsValue({
      name: tenant.name,
      description: tenant.description,
      contactEmail: tenant.contactEmail,
      contactPhone: tenant.contactPhone
    });
    setModalVisible(true);
  };

  // 删除租户
  const handleDelete = async (tenantId) => {
    try {
      await tenantApi.deleteTenant(tenantId);
      message.success('租户删除成功');
      fetchTenants();
    } catch (error) {
      message.error('删除租户失败');
    }
  };

  // 保存租户
  const handleSave = async (values) => {
    try {
      if (editingTenant) {
        // 更新租户
        await tenantApi.updateTenant(editingTenant.id, values);
        message.success('租户更新成功');
      } else {
        // 创建租户
        await tenantApi.createTenant(values);
        message.success('租户创建成功');
      }
      setModalVisible(false);
      fetchTenants();
    } catch (error) {
      message.error('保存租户失败');
    }
  };

  // 状态标签
  const getStatusTag = (status) => {
    switch (status) {
      case 'ACTIVE':
        return <Tag color="green">活跃</Tag>;
      case 'INACTIVE':
        return <Tag color="default">非活跃</Tag>;
      case 'SUSPENDED':
        return <Tag color="orange">暂停</Tag>;
      case 'DELETED':
        return <Tag color="red">删除</Tag>;
      default:
        return <Tag color="default">{status}</Tag>;
    }
  };

  const columns = [
    {
      title: '租户名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '联系邮箱',
      dataIndex: 'contactEmail',
      key: 'contactEmail',
    },
    {
      title: '联系电话',
      dataIndex: 'contactPhone',
      key: 'contactPhone',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => getStatusTag(status),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (createdAt) => createdAt ? new Date(createdAt).toLocaleString() : '-',
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<DetailOutlined />} href={`/tenants/${record.id}`}>
            详情
          </Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确定要删除这个租户吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2>租户管理</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          新建租户
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={tenants}
        rowKey="id"
        loading={loading}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
        }}
      />

      <Modal
        title={editingTenant ? '编辑租户' : '新建租户'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSave}
        >
          <Form.Item
            name="name"
            label="租户名称"
            rules={[{ required: true, message: '请输入租户名称' }]}
          >
            <Input placeholder="请输入租户名称" />
          </Form.Item>
          <Form.Item
            name="description"
            label="租户描述"
            rules={[{ required: true, message: '请输入租户描述' }]}
          >
            <Input.TextArea placeholder="请输入租户描述" rows={4} />
          </Form.Item>
          <Form.Item
            name="contactEmail"
            label="联系邮箱"
            rules={[{ required: true, message: '请输入联系邮箱' }]}
          >
            <Input placeholder="请输入联系邮箱" />
          </Form.Item>
          <Form.Item
            name="contactPhone"
            label="联系电话"
            rules={[{ required: true, message: '请输入联系电话' }]}
          >
            <Input placeholder="请输入联系电话" />
          </Form.Item>
          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={() => setModalVisible(false)}>
                取消
              </Button>
              <Button type="primary" htmlType="submit">
                保存
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TenantList;