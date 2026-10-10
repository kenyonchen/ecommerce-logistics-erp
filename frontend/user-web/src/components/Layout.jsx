import React, { useState, useEffect } from 'react';
import { Layout, Menu, Typography, Button, Dropdown, Space } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';  // 添加useNavigate
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  LogoutOutlined
} from '@ant-design/icons';
import { useAuth } from '../context/AuthContext.jsx';
import { MENU_GROUPS, buildMenuItems, findSelectedKeys, findOpenGroupKeys } from '../constants/menus.js';

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

const AppLayout = ({ children }) => {
  const location = useLocation();
  const navigate = useNavigate();  // 使用navigate进行编程式导航
  const { user, logout, hasRole, can } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [siderWidth, setSiderWidth] = useState(200);

  // 监听屏幕大小变化，自动调整布局
  useEffect(() => {
    const handleResize = () => {
      const screenWidth = window.innerWidth;
      if (screenWidth < 768) {
        // 移动端
        setCollapsed(true);
        setSiderWidth(80);
      } else if (screenWidth < 1024) {
        // 平板
        setCollapsed(false);
        setSiderWidth(180);
      } else {
        // 桌面端
        setCollapsed(false);
        setSiderWidth(200);
      }
    };

    // 初始调用
    handleResize();

    // 添加事件监听
    window.addEventListener('resize', handleResize);

    // 清理事件监听
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // 切换侧边栏展开/折叠
  const toggleCollapsed = () => {
    setCollapsed(!collapsed);
  };

  // 处理菜单点击
  const handleMenuClick = (e) => {
    navigate(e.key);
  };

  // 一级分组默认收起。路由变化时在渲染期同步展开当前页所属分组，
  // 避免用 effect + setState 触发级联渲染（用户手动展开的分组也会保留到下次切页）。
  const [menuState, setMenuState] = useState(() => ({
    pathname: location.pathname,
    openKeys: findOpenGroupKeys(location.pathname)
  }));
  if (menuState.pathname !== location.pathname) {
    setMenuState({ pathname: location.pathname, openKeys: findOpenGroupKeys(location.pathname) });
  }

  // 处理登出
  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  // 用户下拉菜单
  const userMenuItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout
    }
  ];

  // 导航菜单：由 constants/menus.js 按业务域分组，再按角色与权限点过滤
  const menuItems = buildMenuItems(hasRole, can);
  const selectedKeys = findSelectedKeys(location.pathname);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* 顶部导航栏 */}
      <Header style={{ 
        display: 'flex', 
        justifyContent: 'space-between',
        alignItems: 'center', 
        backgroundColor: '#fff', 
        boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)',
        padding: '0 5px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button 
            type="text" 
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} 
            onClick={toggleCollapsed}
            style={{ fontSize: '16px', width: 64, height: 64 }}
          />
          <Title level={3} style={{ margin: 0, color: '#1890ff' }}>跨境大卖ERP系统</Title>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, paddingRight: 16 }}>
          {user && (
            <Space size={4}>
              <UserOutlined />
              <span>{user.username}{user.roles && user.roles.length > 0 ? `（${user.roles.join('、')}）` : ''}</span>
            </Space>
          )}
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <Button type="text" icon={<LogoutOutlined />} />
          </Dropdown>
        </div>
      </Header>
      
      <Layout>
        {/* 侧边栏 */}
        <Sider 
          width={collapsed ? 80 : siderWidth} 
          collapsible 
          collapsed={collapsed}
          onCollapse={(value) => setCollapsed(value)}
          style={{ 
            backgroundColor: '#fff', 
            boxShadow: '2px 0 8px rgba(0, 0, 0, 0.08)',
            transition: 'width 0.3s'
          }}
        >
          <Menu
            mode="inline"
            onClick={handleMenuClick}  /* 添加点击处理 */
            selectedKeys={selectedKeys}
            openKeys={collapsed ? undefined : menuState.openKeys}
            onOpenChange={openKeys => setMenuState(prev => ({ ...prev, openKeys }))}
            style={{ height: '100%', borderRight: 0 }}
            items={menuItems}
          />
        </Sider>
        
        {/* 主内容区 */}
        <Content style={{ 
          marginLeft: '10px',
          marginRight: '10px',
          padding: 10,
          backgroundColor: '#f0f2f5', 
          minHeight: 280,
          borderRadius: 8,
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
          overflow: 'auto'
        }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
};

export default AppLayout;