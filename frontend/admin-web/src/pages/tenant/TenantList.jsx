import React, { useState, useEffect } from 'react';
import { Button, Table, Space, Modal, Form, Input, Select, message, Popconfirm, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, InfoCircleOutlined } from '@ant-design/icons';
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
    } catch {
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
    } catch {
      message.error('删除租户失败');
    }
  };

  // 保存租户
  const handleSave = async (values) => {
    try {
      if (editingTenant) {
        // 更新租户（编辑时不涉及管理员账号字段）
        const updates = {
          name: values.name,
          description: values.description,
          contactEmail: values.contactEmail,
          contactPhone: values.contactPhone,
        };
        await tenantApi.updateTenant(editingTenant.id, updates);
        message.success('租户更新成功');
      } else {
        // 创建租户（同一请求内一并创建租户管理员账号）
        await tenantApi.createTenant(values);
        message.success(`租户创建成功，管理员账号「${values.adminUsername}」可直接登录`);
      }
      setModalVisible(false);
      fetchTenants();
    } catch (error) {
      message.error('保存租户失败: ' + (error.response?.data?.message || error.message));
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
      width: 160,
      ellipsis: true,
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '联系邮箱',
      dataIndex: 'contactEmail',
      key: 'contactEmail',
      width: 200,
    },
    {
      title: '联系电话',
      dataIndex: 'contactPhone',
      key: 'contactPhone',
      width: 140,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status) => getStatusTag(status),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (createdAt) => (createdAt ? new Date(createdAt).toLocaleString() : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small" wrap>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          {record.status === 'ACTIVE' ? (
            <>
              <Popconfirm title="确认暂停该租户？" onConfirm={() => handleStatusChange(record.id, 'suspendTenant')}
                okText="确定" cancelText="取消">
                <Button type="link" danger>暂停</Button>
              </Popconfirm>
              <Popconfirm title="确认停用该租户？" onConfirm={() => handleStatusChange(record.id, 'deactivateTenant')}
                okText="确定" cancelText="取消">
                <Button type="link">停用</Button>
              </Popconfirm>
            </>
          ) : record.status !== 'DELETED' && (
            <Popconfirm title="确认激活该租户？" onConfirm={() => handleStatusChange(record.id, 'activateTenant')}
              okText="确定" cancelText="取消">
              <Button type="link">激活</Button>
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
    <div className="page">
      <div className="page__header">
        <div>
          <h1 className="page__title">租户管理</h1>
          <p className="page__subtitle">共 {tenants.length} 个租户，可新建、编辑与变更状态</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          新建租户
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={tenants}
        rowKey="id"
        loading={loading}
        // 窄屏时表格内部横向滚动，操作列吸附在右侧
        scroll={{ x: 'max-content' }}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (total) => `共 ${total} 条`,
        }}
      />

      <Modal
        title={editingTenant ? '编辑租户' : '新建租户'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        width={520}
        destroyOnHidden
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
          {!editingTenant && (
            <>
              <Form.Item
                label="管理员账号"
                style={{ marginBottom: 8 }}
              >
                <span style={{ color: '#888', fontSize: 13 }}>创建租户时会一并创建该租户的管理员账号，创建成功后即可直接登录</span>
              </Form.Item>
              <Form.Item
                name="adminUsername"
                label="管理员用户名"
                rules={[
                  { required: true, message: '请输入管理员用户名' },
                  { pattern: /^[a-zA-Z0-9_-]{3,32}$/, message: '3-32位，仅限字母、数字、下划线、中划线' },
                ]}
              >
                <Input placeholder="请输入管理员用户名（全局唯一，用于登录）" />
              </Form.Item>
              <Form.Item
                name="adminPassword"
                label="管理员密码"
                rules={[
                  { required: true, message: '请输入管理员密码' },
                  { min: 6, message: '密码至少 6 位' },
                ]}
              >
                <Input.Password placeholder="请输入管理员密码" autoComplete="new-password" />
              </Form.Item>
              <Form.Item
                name="adminEmail"
                label="管理员邮箱"
                rules={[{ type: 'email', message: '邮箱格式不正确' }]}
              >
                <Input placeholder="选填" />
              </Form.Item>
            </>
          )}
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