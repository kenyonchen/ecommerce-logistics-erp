import React, { useState, useMemo } from 'react';
import { Layout, Menu, Button, Space, Grid, Typography } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  DashboardOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { useAuth } from './context/AuthContext';
import './App.css';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

/** 菜单项与路由一一对应，避免用 window.location 造成的高亮失效 */
const MENU_ITEMS = [
  { key: '/', icon: <DashboardOutlined />, label: '平台仪表盘' },
  { key: '/platform/config', icon: <SettingOutlined />, label: '系统配置' },
  { key: '/tenants', icon: <TeamOutlined />, label: '租户管理' },
  { key: '/users', icon: <UserOutlined />, label: '用户管理' },
];

function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();
  const screens = Grid.useBreakpoint();

  // 桌面端展开、窄屏折叠为抽屉式（collapsedWidth=0）
  const [collapsed, setCollapsed] = useState(false);
  const isDesktop = screens.lg;

  // /platform/dashboard 与 / 指向同一页面，高亮到「平台仪表盘」
  const selectedKey = useMemo(() => {
    const match = MENU_ITEMS.find(
      (item) => item.key !== '/' && location.pathname.startsWith(item.key),
    );
    return match ? match.key : '/';
  }, [location.pathname]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <Layout className="app-shell">
      <Header className="app-header">
        <div className="app-header__brand">
          {/* 折叠按钮仅在窄屏出现，桌面端常驻展开侧栏 */}
          {!isDesktop && (
            <Button
              type="text"
              aria-label={collapsed ? '展开菜单' : '收起菜单'}
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((prev) => !prev)}
            />
          )}
          <span>物流ERP管理系统</span>
        </div>

        <Space size={12}>
          {/* 窄屏隐藏用户名，避免顶栏把品牌名挤掉 */}
          {user && isDesktop && (
            <Text type="secondary">
              {user.username}
              {user.roles && user.roles.length > 0 ? `（${user.roles.join('、')}）` : ''}
            </Text>
          )}
          <Button type="link" icon={<LogoutOutlined />} onClick={handleLogout}>
            退出登录
          </Button>
        </Space>
      </Header>

      <Layout>
        <Sider
          className="app-sider"
          width={200}
          theme="light"
          trigger={null}
          collapsible
          collapsed={collapsed}
          collapsedWidth={0}
          breakpoint="lg"
          onBreakpoint={(broken) => setCollapsed(broken)}
        >
          <Menu
            className="app-sider__menu"
            mode="inline"
            selectedKeys={[selectedKey]}
            items={MENU_ITEMS}
            onClick={({ key }) => {
              navigate(key);
              // 窄屏点完菜单自动收起，避免遮住内容
              if (!isDesktop) setCollapsed(true);
            }}
          />
        </Sider>

        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

export default App;
