/**
 * 展示层格式化工具。
 *
 * 后端时间字段返回不带时区的 ISO 局部时间串（如 2026-10-09T07:14:13），
 * 交给 Date 解析会按运行环境时区偏移，故这里只做字符串归一，保证所见即后端所存。
 */

/** 归一为 YYYY-MM-DD HH:mm:ss；无法识别时原样返回，空值返回占位符 */
export const formatDateTime = (value) => {
  if (!value) return '-';
  const matched = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})/.exec(String(value));
  return matched ? `${matched[1]} ${matched[2]}` : String(value);
};
