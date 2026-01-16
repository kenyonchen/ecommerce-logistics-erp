import React, { useState, useEffect } from 'react';
import { Button, Table, Space, Modal, Form, Input, Select, message, Popconfirm, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, DetailOutlined } from '@ant-design/icons';
import { userApi, roleApi } from '../../services/api';

const { Option } = Select;

const UserList = () => {
  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [form] = Form.useForm();

  // 加载用户列表和角色列表
  useEffect(() => {
    fetchUsers();
    fetchRoles();
  }, []);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      // 获取当前租户ID（实际应用中应该从认证上下文中获取）
      const tenantId = 'current-tenant-id';
      const response = await userApi.getUsers(tenantId);
      setUsers(response);
    } catch (error) {
      message.error('加载用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  const fetchRoles = async () => {
    try {
      // 获取当前租户ID（实际应用中应该从认证上下文中获取）
      const tenantId = 'current-tenant-id';
      const response = await roleApi.getRoles(tenantId);
      setRoles(response);
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

  // 保存用户
  const handleSave = async (values) => {
    try {
      // 获取当前租户ID（实际应用中应该从认证上下文中获取）
      const tenantId = 'current-tenant-id';
      
      if (editingUser) {
        // 更新用户
        await userApi.updateUser(editingUser.id, values);
        message.success('用户更新成功');
      } else {
        // 创建用户
        await userApi.createUser({
          ...values,
          tenantId,
          password: values.password || '123456' // 默认密码
        });
        message.success('用户创建成功');
      }
      setModalVisible(false);
      fetchUsers();
    } catch (error) {
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
    },
    {
      title: '姓名',
      dataIndex: 'firstName',
      key: 'name',
      render: (firstName, record) => `${firstName} ${record.lastName}`.trim(),
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
          <Button type="link" icon={<DetailOutlined />} href={`/users/${record.id}`}>
            详情
          </Button>
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
    <div style={{ padding: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2>用户管理</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          新建用户
        </Button>
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