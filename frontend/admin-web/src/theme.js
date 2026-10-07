/**
 * antd 主题令牌：全站唯一的配色来源。
 *
 * 组件内不再出现 #1890ff / #f0f0f0 这类硬编码色值——否则主题一旦调整，
 * 需要逐页改，且容易出现「同一语义两种蓝」。取值与 src/index.css 的
 * CSS 变量保持一致。
 */

/** 设计令牌：色板与圆角 */
export const themeTokens = {
  colorPrimary: '#1e40af',
  colorInfo: '#1e40af',
  colorSuccess: '#16a34a',
  colorWarning: '#d97706',
  colorError: '#dc2626',

  colorBgLayout: '#f1f5f9',
  colorBgContainer: '#ffffff',
  colorBgElevated: '#ffffff',

  colorText: '#0f172a',
  colorTextSecondary: '#475569',

  colorBorder: '#dbeafe',
  colorBorderSecondary: '#e8eef7',

  borderRadius: 6,
  borderRadiusLG: 8,
  borderRadiusSM: 4,

  controlHeight: 36,

  // 4/8dp 间距节奏
  spacing: 8,

  fontFamily:
    "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif",
};

/** antd v6 使用 CSS-in-JS 变量，这里统一布局、菜单、表格的局部令牌 */
export const themeConfig = {
  token: themeTokens,
  components: {
    Layout: {
      headerHeight: 56,
      headerPadding: '0 16px',
      headerBg: '#ffffff',
      bodyBg: '#f1f5f9',
      siderBg: '#ffffff',
    },
    Menu: {
      itemHeight: 40,
      itemMarginInline: 8,
      itemBorderRadius: 6,
    },
    Card: {
      headerFontSize: 15,
    },
    Table: {
      headerBg: '#f8fafc',
      cellPaddingBlock: 12,
    },
  },
};

export default themeConfig;
