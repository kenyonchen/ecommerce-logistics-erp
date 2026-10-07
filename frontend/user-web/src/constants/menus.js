/**
 * 用户端导航菜单配置：按业务域分组为一级菜单，二级菜单放具体功能页。
 *
 * 约定：
 * - 二级菜单的 `key` 必须与 `routes.jsx` 中的 `path` 严格一致，选中态与展开态都依赖此约定；
 * - `icon` 存组件引用而非元素，由 Layout 统一 createElement，本文件保持纯 .js 不含 JSX；
 * - `roles` 不填表示所有登录用户可见，填了则仅拥有其中任一角色时可见。
 */
import { createElement } from 'react';
import {
  HomeOutlined,
  ShoppingCartOutlined,
  TruckOutlined,
  ContainerOutlined,
  DollarOutlined,
  SettingOutlined
} from '@ant-design/icons';

/** 菜单分组定义，顺序即侧边栏展示顺序 */
export const MENU_GROUPS = [
  {
    key: '/',
    label: '首页',
    icon: HomeOutlined
  },
  {
    key: 'group:trade',
    label: '交易管理',
    icon: ShoppingCartOutlined,
    children: [
      { key: '/orders', label: '订单管理' },
      { key: '/orders/import', label: '订单导入' },
      { key: '/customers', label: '客户管理' },
      { key: '/customers/import', label: '客户导入' }
    ]
  },
  {
    key: 'group:fulfillment',
    label: '物流履约',
    icon: TruckOutlined,
    children: [
      { key: '/logistics', label: '物流管理' },
      { key: '/logistics/import', label: '物流导入' },
      { key: '/logistics/channels', label: '物流渠道' }
    ]
  },
  {
    key: 'group:goods',
    label: '商品与仓储',
    icon: ContainerOutlined,
    children: [
      { key: '/products', label: '产品管理' },
      { key: '/products/import', label: '商品导入' },
      { key: '/warehouses', label: '仓储管理' },
      { key: '/warehouses/shop', label: '店铺仓库对接' }
    ]
  },
  {
    key: 'group:cost',
    label: '采购与财务',
    icon: DollarOutlined,
    children: [
      { key: '/purchase', label: '采购管理' },
      { key: '/finance', label: '财务管理' },
      { key: '/finance/import', label: '财务导入' }
    ]
  },
  {
    key: 'group:system',
    label: '平台与设置',
    icon: SettingOutlined,
    children: [
      { key: '/shops', label: '店铺管理' },
      { key: '/platform-integration', label: '平台集成' },
      { key: '/users', label: '用户管理' },
      { key: '/roles', label: '角色管理' },
      { key: '/tenants', label: '租户管理', roles: ['PLATFORM_ADMIN'] }
    ]
  }
];

/** 菜单项对当前用户是否可见 */
const isVisible = (item, hasRole) => !item.roles || item.roles.some(role => hasRole(role));

/** 把配置项转成 antd Menu 的 item 结构（一级菜单带图标，二级菜单靠缩进区分层级） */
const toMenuItem = ({ key, label, icon }) => ({
  key,
  label,
  ...(icon ? { icon: createElement(icon) } : {})
});

/**
 * 生成 antd Menu 的 items：按角色过滤不可见项，
 * 分组内子项被过滤光时整个分组一起隐藏。
 */
export const buildMenuItems = (hasRole, groups = MENU_GROUPS) =>
  groups
    .map(group => {
      if (!isVisible(group, hasRole)) return null;
      if (!group.children) return toMenuItem(group);
      const children = group.children.filter(child => isVisible(child, hasRole)).map(toMenuItem);
      return children.length > 0
        ? { key: group.key, label: group.label, icon: createElement(group.icon), children }
        : null;
    })
    .filter(Boolean);

/** 把分组结构拍平成 [{ group, item }]，便于按路径反查 */
const flatten = (groups) =>
  groups.flatMap(group => {
    const owned = { group, item: group };
    return group.children ? group.children.map(item => ({ group, item })) : [owned];
  });

/**
 * 反查当前路径对应的菜单项：先精确匹配 key，
 * 未命中则取最长路径前缀（详情页 /orders/123 归属 /orders）。
 */
const matchByPath = (entries, pathname) =>
  entries.find(({ item }) => item.key === pathname) ||
  entries
    .filter(({ item }) => pathname.startsWith(`${item.key}/`))
    .sort((a, b) => b.item.key.length - a.item.key.length)[0];

/** 当前路径应高亮的菜单 key */
export const findSelectedKeys = (pathname, groups = MENU_GROUPS) => {
  const matched = matchByPath(flatten(groups), pathname);
  return matched ? [matched.item.key] : [];
};

/** 当前路径所属分组的 key，用于自动展开一级菜单 */
export const findOpenGroupKeys = (pathname, groups = MENU_GROUPS) => {
  const matched = matchByPath(flatten(groups), pathname);
  return matched && matched.group.children ? [matched.group.key] : [];
};