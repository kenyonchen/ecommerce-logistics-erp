import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext.jsx'; // 导入AuthProvider
import AppLayout from './components/Layout.jsx';
import AppRoutes from './routes.jsx';
import './App.css';

// 临时添加错误边界以捕获可能的错误
class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error) {
    // 需在此处即写入 error（componentDidCatch 在 render 之后才执行）；errorInfo 保持 null，渲染处已容错（ISS-078）
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('App Error Boundary caught an error:', error, errorInfo);
    this.setState({
      hasError: true,
      error: error,
      errorInfo: errorInfo
    });
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 20, fontFamily: 'Arial, sans-serif' }}>
          <h2>应用程序出错啦!</h2>
          <details style={{ whiteSpace: 'pre-wrap' }}>
            {this.state.error && this.state.error.toString()}
            <br />
            {/* componentDidCatch 在 render 之后才执行，首次错误 render 时 errorInfo 仍为 null，须容错（ISS-078） */}
            {this.state.errorInfo && this.state.errorInfo.componentStack}
          </details>
        </div>
      );
    }

    return this.props.children;
  }
}

function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider> {/* 使用AuthProvider包裹应用 */}
          <AppRoutes />
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  );
}

export default App;