import React from 'react';
import { message, Modal } from 'antd';

/**
 * 错误处理工具类
 */
export class ErrorHandler {
  /**
   * 处理API错误
   * @param {Error} error - 错误对象
   * @param {Object} options - 选项
   * @param {boolean} options.showMessage - 是否显示错误消息
   * @param {string} options.customMessage - 自定义错误消息
   * @returns {string} 错误消息
   */
  static handleApiError(error, options = {}) {
    const { showMessage = true, customMessage } = options;
    
    let errorMessage = customMessage || '操作失败，请稍后重试';
    
    if (error.response) {
      // 服务器返回错误
      const errorData = error.response.data;
      if (errorData.code) {
        errorMessage = this.getErrorMessageByCode(errorData.code, errorData.message);
      } else if (error.response.status === 404) {
        errorMessage = '请求的资源不存在';
      } else if (error.response.status === 500) {
        errorMessage = '系统内部错误，请稍后重试';
      }
    } else if (error.request) {
      // 请求已发出但没有收到响应
      errorMessage = '服务器无响应，请稍后重试';
    } else if (error.message) {
      // 其他错误
      errorMessage = error.message;
    }
    
    if (showMessage) {
      message.error(errorMessage);
    }
    
    return errorMessage;
  }

  /**
   * 根据错误码获取错误消息
   * @param {string} code - 错误码
   * @param {string} defaultMessage - 默认错误消息
   * @returns {string} 错误消息
   */
  static getErrorMessageByCode(code, defaultMessage = '操作失败') {
    const errorMessages = {
      'ENTITY_NOT_FOUND': '请求的资源不存在',
      'INVALID_OPERATION': '无效的操作',
      'VALIDATION_ERROR': '参数验证失败',
      'SYSTEM_ERROR': '系统内部错误，请稍后重试',
      'ERROR': '操作失败'
    };
    
    return errorMessages[code] || defaultMessage;
  }

  /**
   * 处理表单验证错误
   * @param {Object} errors - 错误对象
   * @param {boolean} showMessage - 是否显示错误消息
   * @returns {string} 错误消息
   */
  static handleFormError(errors, showMessage = true) {
    const errorMessages = Object.values(errors).join('; ');
    if (showMessage) {
      message.error(errorMessages);
    }
    return errorMessages;
  }

  /**
   * 处理网络错误
   * @param {Error} error - 错误对象
   * @param {boolean} showMessage - 是否显示错误消息
   * @returns {string} 错误消息
   */
  static handleNetworkError(error, showMessage = true) {
    const errorMessage = '网络错误，请检查网络连接';
    if (showMessage) {
      message.error(errorMessage);
    }
    return errorMessage;
  }
}

/**
 * 全局错误边界组件
 */
export class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    // 更新状态，下次渲染时显示错误边界
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    // 记录错误信息
    console.error('Error caught by ErrorBoundary:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      // 自定义错误UI
      return (
        <div style={{ 
          padding: '40px', 
          textAlign: 'center', 
          backgroundColor: '#fff',
          minHeight: '400px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center'
        }}>
          <h2>抱歉，页面出现了错误</h2>
          <p style={{ color: '#999', margin: '20px 0' }}>
            {this.state.error?.message || '系统内部错误，请稍后重试'}
          </p>
          <button 
            style={{ 
              padding: '8px 16px', 
              backgroundColor: '#1890ff', 
              color: '#fff', 
              border: 'none', 
              borderRadius: '4px',
              cursor: 'pointer'
            }}
            onClick={() => window.location.reload()}
          >
            刷新页面
          </button>
        </div>
      );
    }

    // 正常渲染子组件
    return this.props.children;
  }
}

/**
 * 异步操作错误处理装饰器
 * @param {Function} asyncFunction - 异步函数
 * @returns {Function} 包装后的函数
 */
export function withErrorHandling(asyncFunction) {
  return async function(...args) {
    try {
      return await asyncFunction(...args);
    } catch (error) {
      ErrorHandler.handleApiError(error);
      throw error;
    }
  };
}