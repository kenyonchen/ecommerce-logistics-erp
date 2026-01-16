import React, { useState, useEffect } from 'react';
import { Layout, Menu, Typography, Button } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import {
  HomeOutlined,
  ShoppingCartOutlined,
  TruckOutlined,
  ProductOutlined,
  ContainerOutlined,
  UserOutlined,
  DollarOutlined,
  ShoppingOutlined,
  LinkOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined
} from '@ant-design/icons';

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

const AppLayout = ({ children }) => {
  const location = useLocation();
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

  // 导航菜单配置
  const menuItems = [
    {
      key: '/',
      icon: <HomeOutlined />,
      label: <Link to="/">首页</Link>
    },
    {
      key: '/orders',
      icon: <ShoppingCartOutlined />,
      label: <Link to="/orders">订单管理</Link>
    },
    {
      key: '/logistics',
      icon: <TruckOutlined />,
      label: <Link to="/logistics">物流管理</Link>
    },
    {
      key: '/products',
      icon: <ProductOutlined />,
      label: <Link to="/products">产品管理</Link>
    },
    {
      key: '/warehouses',
      icon: <ContainerOutlined />,
      label: <Link to="/warehouses">仓储管理</Link>
    },
    {
      key: '/customers',
      icon: <UserOutlined />,
      label: <Link to="/customers">客户管理</Link>
    },
    {
      key: '/finance',
      icon: <DollarOutlined />,
      label: <Link to="/finance">财务管理</Link>
    },
    {
      key: '/purchase',
      icon: <ShoppingOutlined />,
      label: <Link to="/purchase">采购管理</Link>
    },
    {
      key: '/platform-integration',
      icon: <LinkOutlined />,
      label: <Link to="/platform-integration">平台集成</Link>
    }
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* 顶部导航栏 */}
      <Header style={{ 
        display: 'flex', 
        justifyContent: 'space-between',
        alignItems: 'center', 
        backgroundColor: '#fff', 
        boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)',
        padding: '0 24px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button 
            type="text" 
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} 
            onClick={toggleCollapsed}
            style={{ fontSize: '16px', width: 64, height: 64 }}
          />
          <Title level={3} style={{ margin: 0, color: '#1890ff' }}>电商物流ERP系统</Title>
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
            selectedKeys={[location.pathname.split('/')[1] ? `/${location.pathname.split('/')[1]}` : '/']}
            style={{ height: '100%', borderRight: 0 }}
            items={menuItems}
          />
        </Sider>
        
        {/* 主内容区 */}
        <Content style={{ 
          margin: '16px', 
          padding: 24, 
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
