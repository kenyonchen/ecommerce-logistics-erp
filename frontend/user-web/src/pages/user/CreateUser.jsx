import React, { useState } from 'react';
import { Form, Input, Button, Card, message, Select } from 'antd';
import { userApi, roleApi } from '../../services/api';
import { useAuth } from '../../context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';

const { Option } = Select;

const CreateUser = () => {
  const [loading, setLoading] = useState(false);
  const [roles, setRoles] = useState([]);
  const { user } = useAuth();
  const navigate = useNavigate();
  const [form] = Form.useForm();

  React.useEffect(() => {
    // 加载当前租户可选角色
    const tenantId = user?.tenantId || localStorage.getItem('tenantId');
    if (tenantId) {
      roleApi.getRoles(tenantId)
        .then(data => setRoles(Array.isArray(data) ? data : []))
        .catch(() => setRoles([]));
    }
  }, [user]);

  const onFinish = async (values) => {
    setLoading(true);
    try {
      // 后端 CreateUserRequest 字段：tenantId/username/password/email/phone/firstName/lastName
      const tenantId = user?.tenantId || localStorage.getItem('tenantId');
      const response = await userApi.createUser({
        tenantId: tenantId,
        username: values.username,
        password: values.password,
        email: values.email,
        phone: values.phone,
        firstName: values.firstName,
        lastName: values.lastName
      });

      // 创建成功后分配角色（角色通过独立接口 POST /users/{id}/roles 分配）
      if (values.roleId && response && response.id) {
        try {
          await userApi.assignRole(response.id, values.roleId);
        } catch (roleError) {
          message.warning('用户已创建，但角色分配失败: ' + roleError.message);
        }
      }

      message.success('用户创建成功');
      navigate('/users');
    } catch (error) {
      message.error('用户创建失败: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="创建用户" style={{ maxWidth: 800, margin: '20px auto' }}>
      <Form
        form={form}
        name="createUser"
        labelCol={{ span: 4 }}
        wrapperCol={{ span: 16 }}
        onFinish={onFinish}
        autoComplete="off"
      >
        <Form.Item
          label="用户名"
          name="username"
          rules={[{ required: true, message: '请输入用户名' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="密码"
          name="password"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 6, message: '密码至少6位' }
          ]}
        >
          <Input.Password />
        </Form.Item>

        <Form.Item
          label="姓"
          name="firstName"
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="名"
          name="lastName"
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="邮箱"
          name="email"
          rules={[
            { required: true, message: '请输入邮箱' },
            { type: 'email', message: '请输入有效的邮箱地址' }
          ]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="电话"
          name="phone"
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="角色"
          name="roleId"
        >
          <Select placeholder="请选择用户角色（可后续分配）" allowClear>
            {roles.map(role => (
              <Option key={role.id} value={role.id}>{role.name}</Option>
            ))}
          </Select>
        </Form.Item>

        <Form.Item wrapperCol={{ offset: 4, span: 16 }}>
          <Button type="primary" htmlType="submit" loading={loading}>
            提交
          </Button>
          <Button style={{ marginLeft: 8 }} onClick={() => navigate('/users')}>
            取消
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default CreateUser;
