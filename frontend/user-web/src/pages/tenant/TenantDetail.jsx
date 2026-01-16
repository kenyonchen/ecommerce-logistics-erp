import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Card, Descriptions, Button, Space, Tag } from 'antd';
import { tenantApi } from '../../services/api';

const TenantDetail = () => {
  const { id } = useParams();
  const [tenant, setTenant] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchTenantDetail();
  }, [id]);

  const fetchTenantDetail = async () => {
    setLoading(true);
    try {
      const response = await tenantApi.getTenantById(id);
      setTenant(response.data);
    } catch (error) {
      console.error('获取租户详情失败:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading || !tenant) {
    return <div style={{ padding: '20px', textAlign: 'center' }}>加载中...</div>;
  }

  return (
    <Card 
      title={`租户详情 - ${tenant.name}`} 
      extra={
        <Space>
          <Button type="primary">编辑</Button>
          <Button>返回列表</Button>
        </Space>
      }
      style={{ margin: '20px' }}
    >
      <Descriptions bordered column={2} size="middle">
        <Descriptions.Item label="租户ID">{tenant.id}</Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag 
            color={tenant.status === 'ACTIVE' ? 'green' : 
                   tenant.status === 'SUSPENDED' ? 'red' : 'orange'}
          >
            {tenant.status}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="租户名称" span={2}>{tenant.name}</Descriptions.Item>
        <Descriptions.Item label="描述" span={2}>{tenant.description || '-'}</Descriptions.Item>
        <Descriptions.Item label="联系邮箱">{tenant.contactEmail}</Descriptions.Item>
        <Descriptions.Item label="联系电话">{tenant.contactPhone || '-'}</Descriptions.Item>
        <Descriptions.Item label="创建时间" span={2}>
          {tenant.createdAt ? new Date(tenant.createdAt).toLocaleString() : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="更新时间" span={2}>
          {tenant.updatedAt ? new Date(tenant.updatedAt).toLocaleString() : '-'}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export default TenantDetail;