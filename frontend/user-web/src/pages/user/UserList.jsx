import React, { useState, useEffect } from 'react';
import { Button, Table, Space, Modal, Form, Input, Select, message, Popconfirm, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, InfoCircleOutlined, SafetyOutlined } from '@ant-design/icons';
import { userApi, roleApi } from '../../services/api';
import { useAuth } from '../../context/AuthContext.jsx';

const { Option } = Select;

const UserList = () => {
  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [roleModalVisible, setRoleModalVisible] = useState(false);
  const [roleTarget, setRoleTarget] = useState(null);
  const [roleModalRoles, setRoleModalRoles] = useState([]);
  const [form] = Form.useForm();
  const [roleForm] = Form.useForm();
  const { user: currentUser } = useAuth();

  // 当前租户ID：优先取登录用户上下文
  const currentTenantId = currentUser?.tenantId || localStorage.getItem('tenantId') || '';

  // 加载用户列表和角色列表
  useEffect(() => {
    fetchUsers();
    fetchRoles();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTenantId]);

  const fetchUsers = async () => {
    if (!currentTenantId) {
      message.warning('无法确定当前租户，请重新登录');
      return;
    }
    setLoading(true);
    try {
      const response = await userApi.getUsers(currentTenantId);
      setUsers(Array.isArray(response) ? response : []);
    } catch (error) {
      message.error('加载用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  const fetchRoles = async () => {
    if (!currentTenantId) return;
    try {
      const response = await roleApi.getRoles(currentTenantId);
      setRoles(Array.isArray(response) ? response : []);
    } catch (error) {
      message.error('加载角色列表失败');
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
    } catch (error) {
      message.error('删除用户失败');
    }
  };

  // 状态流转
  const handleStatusChange = async (userId, action) => {
    try {
      await userApi[action](userId);
      message.success('操作成功');
      fetchUsers();
    } catch (error) {
      message.error('操作失败: ' + error.message);
    }
  };

  // 保存用户
  const handleSave = async (values) => {
    try {
      if (editingUser) {
        // 更新用户（后端不接受 username/角色等变更，仅基本信息）
        await userApi.updateUser(editingUser.id, {
          email: values.email,
          phone: values.phone,
          firstName: values.firstName,
          lastName: values.lastName
        });
        message.success('用户更新成功');
      } else {
        // 创建用户
        await userApi.createUser({
          tenantId: currentTenantId,
          username: values.username,
          password: values.password || '123456', // 默认密码
          email: values.email,
          phone: values.phone,
          firstName: values.firstName,
          lastName: values.lastName
        });
        message.success('用户创建成功');
      }
      setModalVisible(false);
      fetchUsers();
    } catch (error) {
      message.error('保存用户失败: ' + error.message);
    }
  };

  // 打开角色分配模态框
  const openRoleModal = (user) => {
    setRoleTarget(user);
    setRoleModalRoles(user.roles || []);
    roleForm.setFieldsValue({ roleId: undefined });
    setRoleModalVisible(true);
  };

  // 分配角色
  const handleAssignRole = async (values) => {
    if (!roleTarget || !values.roleId) return;
    try {
      await userApi.assignRole(roleTarget.id, values.roleId);
      message.success('角色分配成功');
      setRoleModalVisible(false);
      fetchUsers();
    } catch (error) {
      message.error('角色分配失败: ' + error.message);
    }
  };

  // 移除角色
  const handleRemoveRole = async (userId, roleId) => {
    try {
      await userApi.removeRole(userId, roleId);
      message.success('角色移除成功');
      // 同步刷新模态框内角色
      const updated = users.find(u => u.id === userId);
      if (updated) {
        setRoleModalRoles((updated.roles || []).filter(r => r.id !== roleId));
      }
      fetchUsers();
    } catch (error) {
      message.error('角色移除失败: ' + error.message);
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
    },
    {
      title: '姓名',
      dataIndex: 'firstName',
      key: 'name',
      render: (firstName, record) => `${firstName || ''} ${record.lastName || ''}`.trim() || '-',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: '电话',
      dataIndex: 'phone',
      key: 'phone',
      render: (phone) => phone || '-',
    },
    {
      title: '角色',
      dataIndex: 'roles',
      key: 'roles',
      render: (roles) =>
        roles && roles.length > 0
          ? roles.map(role => <Tag key={role.id} color="blue">{role.name}</Tag>)
          : <Tag color="default">未分配</Tag>,
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
          <Button type="link" icon={<InfoCircleOutlined />} href={`/users/${record.id}`}>
            详情
          </Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button type="link" icon={<SafetyOutlined />} onClick={() => openRoleModal(record)}>
            角色
          </Button>
          {record.status === 'ACTIVE' ? (
            <Popconfirm title="确认停用该用户？" onConfirm={() => handleStatusChange(record.id, 'deactivateUser')}
              okText="确定" cancelText="取消">
              <Button type="link" danger>停用</Button>
            </Popconfirm>
          ) : (
            <Popconfirm title="确认激活该用户？" onConfirm={() => handleStatusChange(record.id, 'activateUser')}
              okText="确定" cancelText="取消">
              <Button type="link">激活</Button>
            </Popconfirm>
          )}
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
    <div style={{ padding: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <h2>用户管理</h2>
        <Space>
          <Button icon={<PlusOutlined />} onClick={fetchUsers}>
            刷新
          </Button>
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
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
        }}
      />

      <Modal
        title={editingUser ? '编辑用户' : '新建用户'}
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
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="请输入用户名" disabled={!!editingUser} />
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
          >
            <Input placeholder="请输入名" />
          </Form.Item>
          <Form.Item
            name="lastName"
            label="姓"
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

      <Modal
        title={`角色分配 - ${roleTarget?.username || ''}`}
        open={roleModalVisible}
        onCancel={() => setRoleModalVisible(false)}
        footer={null}
      >
        <Form form={roleForm} layout="vertical" onFinish={handleAssignRole}>
          <Form.Item
            name="roleId"
            label="选择角色"
            rules={[{ required: true, message: '请选择要分配的角色' }]}
          >
            <Select placeholder="请选择角色">
              {roles.map(role => (
                <Option key={role.id} value={role.id}>{role.name}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              分配角色
            </Button>
          </Form.Item>
        </Form>
        <div>
          <h4>已分配角色：</h4>
          {roleModalRoles.length === 0 ? (
            <p style={{ color: '#999' }}>暂无角色</p>
          ) : (
            roleModalRoles.map(role => (
              <div key={role.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
                <Tag color="blue">{role.name}</Tag>
                <Popconfirm
                  title="确认移除该角色？"
                  onConfirm={() => handleRemoveRole(roleTarget.id, role.id)}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button type="link" danger size="small">移除</Button>
                </Popconfirm>
              </div>
            ))
          )}
        </div>
      </Modal>
    </div>
  );
};

export default UserList;
