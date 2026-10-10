import React, { useState, useEffect } from 'react';
import {
  Button, Table, Space, Modal, Form, Input, message, Popconfirm, Tag, Select,
} from 'antd';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined, KeyOutlined,
} from '@ant-design/icons';
import { roleApi } from '../../services/api';
import { useAuth } from '../../context/AuthContext.jsx';

// 预置权限点（与后端 PermissionPoints 目录一一对应，勿增删）
const PERMISSION_PRESETS = [
  { value: 'order:read', label: '订单-查看' },
  { value: 'order:write', label: '订单-创建/修改' },
  { value: 'order:import', label: '订单-批量导入' },
  { value: 'product:read', label: '商品-查看' },
  { value: 'product:write', label: '商品-创建/修改' },
  { value: 'product:import', label: '商品-批量导入' },
  { value: 'customer:read', label: '客户-查看' },
  { value: 'customer:write', label: '客户-创建/修改' },
  { value: 'customer:import', label: '客户-批量导入' },
  { value: 'shop:read', label: '店铺-查看' },
  { value: 'shop:write', label: '店铺-创建/修改' },
  { value: 'shop:credential_write', label: '店铺-凭据配置' },
  { value: 'shop:sync', label: '店铺-外呼同步' },
  { value: 'warehouse:read', label: '仓储-查看' },
  { value: 'warehouse:write', label: '仓储-管理' },
  { value: 'finance:read', label: '财务-查看' },
  { value: 'finance:write', label: '财务-管理' },
  { value: 'finance:import', label: '财务-批量导入' },
  { value: 'purchase:read', label: '采购-查看' },
  { value: 'purchase:write', label: '采购-管理' },
  { value: 'logistics:read', label: '物流-查看' },
  { value: 'logistics:write', label: '物流-管理' },
  { value: 'logistics:import', label: '物流-批量导入' },
  { value: 'platform:manage', label: '店铺对接-管理' },
  { value: 'user:manage', label: '用户-管理' },
  { value: 'role:manage', label: '角色-管理' },
];

const RoleList = () => {
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRole, setEditingRole] = useState(null);
  const [permModalVisible, setPermModalVisible] = useState(false);
  const [permTarget, setPermTarget] = useState(null);
  const [permSelected, setPermSelected] = useState([]);
  const [form] = Form.useForm();
  const { user: currentUser } = useAuth();

  const currentTenantId = currentUser?.tenantId || localStorage.getItem('tenantId') || '';

  useEffect(() => {
    fetchRoles();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTenantId]);

  const fetchRoles = async () => {
    if (!currentTenantId) return;
    setLoading(true);
    try {
      const response = await roleApi.getRoles(currentTenantId);
      setRoles(Array.isArray(response) ? response : []);
    } catch {
      message.error('加载角色列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleAdd = () => {
    setEditingRole(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (role) => {
    setEditingRole(role);
    form.setFieldsValue({
      name: role.name,
      description: role.description,
    });
    setModalVisible(true);
  };

  const handleSave = async (values) => {
    try {
      if (editingRole) {
        await roleApi.updateRole(editingRole.id, {
          name: values.name,
          description: values.description,
        });
        message.success('角色更新成功');
      } else {
        await roleApi.createRole({
          tenantId: currentTenantId,
          name: values.name,
          description: values.description,
        });
        message.success('角色创建成功');
      }
      setModalVisible(false);
      fetchRoles();
    } catch (error) {
      message.error('保存角色失败: ' + error.message);
    }
  };

  const handleDelete = async (roleId) => {
    try {
      await roleApi.deleteRole(roleId);
      message.success('角色删除成功');
      fetchRoles();
    } catch (error) {
      message.error('删除角色失败: ' + error.message);
    }
  };

  // 打开权限编辑模态框
  const openPermModal = (role) => {
    setPermTarget(role);
    setPermSelected(role.permissions || []);
    setPermModalVisible(true);
  };

  // 保存权限集（整组覆盖）
  const handleSavePermissions = async () => {
    if (!permTarget) return;
    try {
      await roleApi.setPermissions(permTarget.id, permSelected);
      message.success('权限更新成功');
      setPermModalVisible(false);
      fetchRoles();
    } catch (error) {
      message.error('权限更新失败: ' + error.message);
    }
  };

  const columns = [
    {
      title: '角色名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      render: (text) => text || '-',
    },
    {
      title: '系统角色',
      dataIndex: 'isSystemRole',
      key: 'isSystemRole',
      render: (isSystemRole) =>
        isSystemRole ? <Tag color="purple">系统</Tag> : <Tag color="default">自定义</Tag>,
    },
    {
      title: '权限数',
      dataIndex: 'permissions',
      key: 'permissions',
      render: (permissions) => (permissions ? permissions.length : 0),
    },
    {
      title: '权限明细',
      dataIndex: 'permissions',
      key: 'permList',
      render: (permissions) =>
        permissions && permissions.length > 0
          ? (
            <Space size={[4, 4]} wrap>
              {permissions.slice(0, 6).map(p => <Tag key={p}>{p}</Tag>)}
              {permissions.length > 6 && <Tag>+{permissions.length - 6}</Tag>}
            </Space>
          )
          : <Tag color="default">未配置</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space size="small" wrap>
          <Button type="link" icon={<KeyOutlined />} onClick={() => openPermModal(record)}>
            权限
          </Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          {!record.isSystemRole && (
            <Popconfirm
              title="确定要删除这个角色吗？"
              onConfirm={() => handleDelete(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button type="link" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
        <h2>角色管理</h2>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchRoles}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新建角色</Button>
        </Space>
      </div>

      <Table
        columns={columns}
        dataSource={roles}
        rowKey="id"
        loading={loading}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
        }}
      />

      <Modal
        title={editingRole ? '编辑角色' : '新建角色'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
      >
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item
            name="name"
            label="角色名称"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input placeholder="如：TENANT_ADMIN / 运营专员" disabled={editingRole?.isSystemRole} />
          </Form.Item>
          <Form.Item
            name="description"
            label="角色描述"
          >
            <Input.TextArea placeholder="请输入角色描述" rows={3} />
          </Form.Item>
          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={() => setModalVisible(false)}>取消</Button>
              <Button type="primary" htmlType="submit">保存</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`权限配置 - ${permTarget?.name || ''}`}
        open={permModalVisible}
        onCancel={() => setPermModalVisible(false)}
        footer={
          <Space>
            <Button onClick={() => setPermModalVisible(false)}>取消</Button>
            <Button type="primary" onClick={handleSavePermissions}>保存权限</Button>
          </Space>
        }
        width={560}
      >
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder="选择或搜索权限点"
          value={permSelected}
          onChange={setPermSelected}
          options={PERMISSION_PRESETS}
          allowClear
        />
        <p style={{ color: '#999', marginTop: 8 }}>
          提示：权限点在登录后通过 /auth/me 下发，可用于按钮级权限控制。
        </p>
      </Modal>
    </div>
  );
};

export default RoleList;
