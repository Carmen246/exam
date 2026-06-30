/**
 * 是否按代码块样式展示
 * @param {number} quType
 * @param {string} text
 */
export function needsCodeFormat(quType, text) {
  if (quType === 5 || quType === 6 || quType === 7 || quType === 8) {
    return true
  }
  if (!text) {
    return false
  }
  return /#include|void\s+main|int\s+main|public\s+class|def\s+\w+\(|function\s+\w+\(/.test(text)
}
