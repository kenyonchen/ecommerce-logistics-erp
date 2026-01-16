import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Card, Descriptions, Button, Space, Tag } from 'antd';
import { userApi } from '../../services/api';

const UserDetail = () => {
  const { id } = useParams();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchUserDetail();
  }, [id]);

  const fetchUserDetail = async () => {
    setLoading(true);
    try {
      const response = await userApi.getUserById(id);
      setUser(response.data);
    } catch (error) {
      console.error('获取用户详情失败:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading || !user) {
    return <div style={{ padding: '20px', textAlign: 'center' }}>加载中...</div>;
  }

  return (
    <Card 
      title={`用户详情 - ${user.name}`} 
      extra={
        <Space>
          <Button type="primary">编辑</Button>
          <Button>返回列表</Button>
        </Space>
      }
      style={{ margin: '20px' }}
    >
      <Descriptions bordered column={2} size="middle">
        <Descriptions.Item label="用户ID">{user.id}</Descriptions.Item>
        <Descriptions.Item label="用户名">{user.username}</Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag 
            color={user.status === 'ACTIVE' ? 'green' : 
                   user.status === 'SUSPENDED' ? 'red' : 'orange'}
          >
            {user.status}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="角色">
          <Tag color="blue">{user.role}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="姓名" span={2}>{user.name}</Descriptions.Item>
        <Descriptions.Item label="邮箱">{user.email}</Descriptions.Item>
        <Descriptions.Item label="电话">{user.phone || '-'}</Descriptions.Item>
        <Descriptions.Item label="创建时间" span={2}>
          {user.createdAt ? new Date(user.createdAt).toLocaleString() : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="更新时间" span={2}>
          {user.updatedAt ? new Date(user.updatedAt).toLocaleString() : '-'}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export default UserDetail;