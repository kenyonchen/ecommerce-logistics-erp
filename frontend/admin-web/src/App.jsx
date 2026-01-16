import React from 'react';
import { Layout, Menu, Button } from 'antd';
import { Outlet, Link, useNavigate } from 'react-router-dom';
import { DashboardOutlined, SettingOutlined, TeamOutlined, UserOutlined, LogoutOutlined } from '@ant-design/icons';
import './App.css';

const { Header, Sider, Content } = Layout;

function App() {
  const navigate = useNavigate();

  const handleLogout = () => {
    // 实际应用中应该清除认证信息
    console.log('Logout');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px', background: '#fff', boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)' }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <h1 style={{ margin: 0, fontSize: 18, fontWeight: 600, color: '#1890ff' }}>
            物流ERP管理系统
          </h1>
        </div>
        <div>
          <Button type="link" icon={<LogoutOutlined />} onClick={handleLogout}>
            退出登录
          </Button>
        </div>
      </Header>
      <Layout>
        <Sider width={200} style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}>
          <Menu
            mode="inline"
            style={{ height: '100%', borderRight: 0 }}
            selectedKeys={[window.location.pathname]}
            items={[
              {
                key: '/',
                icon: <DashboardOutlined />,
                label: <Link to="/">平台仪表盘</Link>
              },
              {
                key: '/platform/config',
                icon: <SettingOutlined />,
                label: <Link to="/platform/config">系统配置</Link>
              },
              {
                key: '/tenants',
                icon: <TeamOutlined />,
                label: <Link to="/tenants">租户管理</Link>
              },
              {
                key: '/users',
                icon: <UserOutlined />,
                label: <Link to="/users">用户管理</Link>
              }
            ]}
          />
        </Sider>
        <Content style={{ padding: '24px', background: '#f0f2f5', minHeight: 280 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

export default App;