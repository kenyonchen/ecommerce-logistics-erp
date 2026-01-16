import React, { useState, useEffect } from 'react';
import { Layout, Menu, Typography, Button } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';  // 添加useNavigate
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
  const navigate = useNavigate();  // 使用navigate进行编程式导航
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

  // 导航菜单配置
  const menuItems = [
    {
      key: '/',
      icon: <HomeOutlined />,
      label: '首页'
    },
    {
      key: '/orders',
      icon: <ShoppingCartOutlined />,
      label: '订单管理'
    },
    {
      key: '/logistics',
      icon: <TruckOutlined />,
      label: '物流管理'
    },
    {
      key: '/products',
      icon: <ProductOutlined />,
      label: '产品管理'
    },
    {
      key: '/warehouses',
      icon: <ContainerOutlined />,
      label: '仓储管理'
    },
    {
      key: '/customers',
      icon: <UserOutlined />,
      label: '客户管理'
    },
    {
      key: '/finance',
      icon: <DollarOutlined />,
      label: '财务管理'
    },
    {
      key: '/purchase',
      icon: <ShoppingOutlined />,
      label: '采购管理'
    },
    {
      key: '/platform-integration',
      icon: <LinkOutlined />,
      label: '平台集成'
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
            selectedKeys={[location.pathname]}
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