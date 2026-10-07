import React, { useState, useEffect } from 'react';
import { Button, Table, Space, Modal, Form, Input, message, Popconfirm, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, InfoCircleOutlined, UserAddOutlined, CaretRightOutlined, PauseOutlined, StopOutlined } from '@ant-design/icons';
import { tenantApi } from '../../services/api';

const TenantList = () => {
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTenant, setEditingTenant] = useState(null);
  const [adminModalVisible, setAdminModalVisible] = useState(false);
  const [adminTarget, setAdminTarget] = useState(null);
  const [form] = Form.useForm();
  const [adminForm] = Form.useForm();

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

  // 租户状态流转
  const handleStatusChange = async (tenantId, action) => {
    try {
      await tenantApi[action](tenantId);
      message.success('操作成功');
      fetchTenants();
    } catch (error) {
      message.error('操作失败: ' + error.message);
    }
  };

  // 打开创建租户管理员模态框
  const openAdminModal = (tenant) => {
    setAdminTarget(tenant);
    adminForm.resetFields();
    setAdminModalVisible(true);
  };

  // 创建租户管理员
  const handleCreateAdmin = async (values) => {
    if (!adminTarget) return;
    try {
      await tenantApi.createTenantAdmin(adminTarget.id, {
        username: values.username,
        password: values.password,
        email: values.email,
        phone: values.phone,
        firstName: values.firstName,
        lastName: values.lastName
      });
      message.success(`租户「${adminTarget.name}」管理员创建成功`);
      setAdminModalVisible(false);
    } catch (error) {
      message.error('租户管理员创建失败: ' + error.message);
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
        <Space size="small" wrap>
          <Button type="link" icon={<InfoCircleOutlined />} href={`/tenants/${record.id}`}>
            详情
          </Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button type="link" icon={<UserAddOutlined />} onClick={() => openAdminModal(record)}>
            管理员
          </Button>
          {record.status === 'ACTIVE' && (
            <Popconfirm title="确认暂停该租户？" onConfirm={() => handleStatusChange(record.id, 'suspendTenant')}
              okText="确定" cancelText="取消">
              <Button type="link" danger icon={<PauseOutlined />}>暂停</Button>
            </Popconfirm>
          )}
          {record.status !== 'ACTIVE' && record.status !== 'DELETED' && (
            <Popconfirm title="确认激活该租户？" onConfirm={() => handleStatusChange(record.id, 'activateTenant')}
              okText="确定" cancelText="取消">
              <Button type="link" icon={<CaretRightOutlined />}>激活</Button>
            </Popconfirm>
          )}
          {record.status === 'ACTIVE' && (
            <Popconfirm title="确认停用该租户？" onConfirm={() => handleStatusChange(record.id, 'deactivateTenant')}
              okText="确定" cancelText="取消">
              <Button type="link" icon={<StopOutlined />}>停用</Button>
            </Popconfirm>
          )}
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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
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

      <Modal
        title={`创建租户管理员 - ${adminTarget?.name || ''}`}
        open={adminModalVisible}
        onCancel={() => setAdminModalVisible(false)}
        footer={null}
      >
        <Form form={adminForm} layout="vertical" onFinish={handleCreateAdmin}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password placeholder="请输入密码" />
          </Form.Item>
          <Form.Item name="firstName" label="名">
            <Input placeholder="请输入名" />
          </Form.Item>
          <Form.Item name="lastName" label="姓">
            <Input placeholder="请输入姓" />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ required: true, message: '请输入邮箱' }]}>
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item name="phone" label="电话">
            <Input placeholder="请输入电话" />
          </Form.Item>
          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={() => setAdminModalVisible(false)}>取消</Button>
              <Button type="primary" htmlType="submit">创建管理员</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TenantList;