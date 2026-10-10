import React from 'react';
import { useAuth } from '../../context/AuthContext.jsx';

/**
 * 按权限点收敛界面入口：没有该权限点时不渲染 children。
 *
 * 这只是体验层的「不要把进不去的按钮摆出来」，不是安全边界 ——
 * 写操作的强制在后端 @PreAuthorize，绕过前端直接打接口一样会被拒。
 *
 * @param permission 权限点名，与后端 PermissionPoints 目录一字不差；不填则始终渲染
 * @param fallback   无权限时的替代内容，默认什么都不渲染
 */
const Can = ({ permission, children = null, fallback = null }) => {
  const { can } = useAuth();
  return can(permission) ? children : fallback;
};

export default Can;
