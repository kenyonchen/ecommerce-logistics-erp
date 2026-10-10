import React, { useState, useEffect } from 'react';
import { Button, Table, Space, Modal, Form, Input, Select, message, Popconfirm, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { userApi, tenantApi } from '../../services/api';

const UserList = () => {
  const [users, setUsers] = useState([]);
  const [tenants, setTenants] = useState([]);
  const [selectedTenantId, setSelectedTenantId] = useState('');
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [form] = Form.useForm();

  /**
   * 平台管理员默认看全平台用户（后端 GET /api/users 不带 tenantId 即跨租户），
   * 曾经这里回落到「第一个租户」，于是看板说 4 个用户、列表只显示 2 个，
   * 而且新建用户会被静默建进那个没人选过的租户。
   */
  const effectiveTenantId = selectedTenantId;

  // 平台管理员按租户筛选用户：先加载租户下拉
  useEffect(() => {
    tenantApi.getTenants()
      .then((data) => setTenants(Array.isArray(data) ? data : []))
      .catch(() => setTenants([]));
  }, []);

  // 租户列表就绪或筛选变化时重新加载用户
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    userApi.getUsers(effectiveTenantId || undefined)
      .then((data) => {
        if (!cancelled) setUsers(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (!cancelled) {
          setUsers([]);
          message.error('加载用户列表失败');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [effectiveTenantId]);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const response = await userApi.getUsers(effectiveTenantId || undefined);
      setUsers(Array.isArray(response) ? response : []);
    } catch {
      message.error('加载用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  // 打开创建用户模态框
  const handleAdd = () => {
    setEditingUser(null);
    form.resetFields();
    setModalVisible(true);
  };

  // 打开编辑用户模态框
  const handleEdit = (user) => {
    setEditingUser(user);
    form.setFieldsValue({
      username: user.username,
      email: user.email,
      phone: user.phone,
      firstName: user.firstName,
      lastName: user.lastName
    });
    setModalVisible(true);
  };

  // 删除用户
  const handleDelete = async (userId) => {
    try {
      await userApi.deleteUser(userId);
      message.success('用户删除成功');
      fetchUsers();
    } catch {
      message.error('删除用户失败');
    }
  };

  // 保存用户
  const handleSave = async (values) => {
    try {
      if (editingUser) {
        // 更新用户
        await userApi.updateUser(editingUser.id, values);
        message.success('用户更新成功');
      } else {
        // 创建用户：后端要求平台管理员显式指定租户，这里不替用户挑一个
        if (!effectiveTenantId) {
          message.warning('请先在右上角选择租户，再新建用户');
          return;
        }
        await userApi.createUser({
          ...values,
          tenantId: effectiveTenantId,
        });
        message.success('用户创建成功');
      }
      setModalVisible(false);
      fetchUsers();
    } catch {
      message.error('保存用户失败');
    }
  };

  // 状态标签
  const getStatusTag = (status) => {
    switch (status) {
      case 'ACTIVE':
        return <Tag color="green">活跃</Tag>;
      case 'INACTIVE':
        return <Tag color="default">非活跃</Tag>;
      case 'LOCKED':
        return <Tag color="red">锁定</Tag>;
      case 'PENDING':
        return <Tag color="orange">待激活</Tag>;
      default:
        return <Tag color="default">{status}</Tag>;
    }
  };

  const columns = [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 180,
      ellipsis: true,
    },
    {
      // 默认视图是跨租户的，不显示归属就无法判断该编辑/禁用的是谁的账号
      title: '所属租户',
      dataIndex: 'tenantId',
      key: 'tenantId',
      width: 160,
      ellipsis: true,
      render: (tenantId) => {
        if (!tenantId) return '-';
        const tenant = tenants.find((t) => t.id === tenantId);
        return tenant ? tenant.name : tenantId;
      },
    },
    {
      title: '姓名',
      dataIndex: 'firstName',
      key: 'name',
      width: 120,
      render: (firstName, record) =>
        `${firstName || ''} ${record.lastName || ''}`.trim() || '-',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      width: 220,
    },
    {
      title: '电话',
      dataIndex: 'phone',
      key: 'phone',
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
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确定要删除这个用户吗？"
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
          <h1 className="page__title">用户管理</h1>
          <p className="page__subtitle">默认展示全平台用户，可按租户筛选；新建用户需先选定租户</p>
        </div>
        <Space wrap>
          <Select
            placeholder="全部租户"
            style={{ width: 200, maxWidth: '100%' }}
            value={selectedTenantId || undefined}
            onChange={setSelectedTenantId}
            allowClear
            options={tenants.map((t) => ({ value: t.id, label: t.name }))}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新建用户
          </Button>
        </Space>
      </div>

      <Table
        columns={columns}
        dataSource={users}
        rowKey="id"
        loading={loading}
        scroll={{ x: 'max-content' }}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (total) => `共 ${total} 条`,
        }}
      />

      <Modal
        title={editingUser ? '编辑用户' : '新建用户'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        width={560}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSave}
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="请输入用户名" />
          </Form.Item>
          {!editingUser && (
            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password placeholder="请输入密码" />
            </Form.Item>
          )}
          <Form.Item
            name="firstName"
            label="名"
            rules={[{ required: true, message: '请输入名' }]}
          >
            <Input placeholder="请输入名" />
          </Form.Item>
          <Form.Item
            name="lastName"
            label="姓"
            rules={[{ required: true, message: '请输入姓' }]}
          >
            <Input placeholder="请输入姓" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ required: true, message: '请输入邮箱' }]}
          >
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item
            name="phone"
            label="电话"
            rules={[{ required: true, message: '请输入电话' }]}
          >
            <Input placeholder="请输入电话" />
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

export default UserList;