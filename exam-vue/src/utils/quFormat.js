/**
 * 文本是否像程序代码
 * @param {string} text
 */
export function looksLikeCode(text) {
  if (!text) {
    return false
  }
  return /#include|#define|void\s+main|int\s+main|int\s+\w+\s*\(|public\s+class|def\s+\w+\(|function\s+\w+\(/.test(text)
}

export function isFillProgramQuType(type) {
  return type === 5
}

export function isReadProgramQuType(type) {
  return type === 6
}

export function isProgramQuType(type) {
  return type === 7
}

export function isFixProgramQuType(type) {
  return type === 8
}

/** content 内合并存储题干+程序代码的题型：5/6/8 */
export function isStemCodeQuType(type) {
  return type === 5 || type === 6 || type === 8
}

/**
 * 拆分题干与程序代码（5/6/8 共用）
 * @param {string} content
 */
export function parseStemCodeContent(content) {
  return parseFillProgramContent(content)
}

/**
 * 拆分程序填空题的题干与代码（content 内合并存储）
 * @param {string} content
 */
export function parseFillProgramContent(content) {
  if (!content) {
    return { stem: '', code: '' }
  }
  const text = String(content)
  const idx = text.search(/^\s*(#include|#define|(int|char|void|float|double|long|short|unsigned)\s+\w+\s*\()/m)
  if (idx > 0) {
    return {
      stem: text.substring(0, idx).trim(),
      code: text.substring(idx).trim()
    }
  }
  if (looksLikeCode(text)) {
    return { stem: '', code: text.trim() }
  }
  return { stem: text.trim(), code: '' }
}

export function fillProgramBlankLabel(index) {
  return `第${index + 1}空`
}

/** 隐藏 docx 内部 {FILL:答案} 标记，预览/展示用 */
export function hideFillMarkersForDisplay(text) {
  if (!text) {
    return text
  }
  return String(text).replace(/\{FILL:([^}]+)\}/g, '____')
}

/**
 * 题干区域是否使用代码样式
 * @param {number} quType
 * @param {string} text
 */
export function needsCodeFormatForStem(quType, text) {
  if (quType === 6 || quType === 8) {
    return looksLikeCode(text)
  }
  return false
}

/**
 * 程序代码区域是否使用代码样式
 */
export function needsCodeFormatForCode(text) {
  return looksLikeCode(text)
}

/**
 * 参考答案区域是否使用代码样式
 * @param {number} quType
 * @param {string} text
 */
export function needsCodeFormatForAnswer(quType, text) {
  if (quType === 5 || quType === 6) {
    return looksLikeCode(text)
  }
  if (quType === 7 || quType === 8) {
    return true
  }
  return looksLikeCode(text)
}

/**
 * @deprecated 使用 needsCodeFormatForStem / needsCodeFormatForAnswer
 */
export function needsCodeFormat(quType, text) {
  return needsCodeFormatForStem(quType, text) || needsCodeFormatForAnswer(quType, text)
}

/**
 * 主观题参考答案区标题
 * @param {number} quType
 */
export function subjectiveAnswerLabel(quType) {
  const map = {
    4: '参考答案',
    5: '各空参考答案',
    6: '运行结果/参考答案',
    7: '参考程序',
    8: '改正后程序',
    9: '参考答案/评分要点'
  }
  return map[quType] || '参考答案/评分要点'
}

/**
 * 主观题作答 placeholder
 * @param {number} quType
 */
export function subjectiveAnswerPlaceholder(quType) {
  const map = {
    4: '请输入你的答案',
    5: '请按空位顺序填写答案，每行一空',
    6: '请输入程序运行结果或分析结论',
    7: '请在此编写完整程序代码',
    8: '请写出改正后的完整程序代码',
    9: '请输入你的答案'
  }
  return map[quType] || '请输入你的答案'
}

/**
 * 题干+代码分栏时的代码区标题
 * @param {number} quType
 */
export function stemCodeSectionLabel(quType) {
  const map = {
    5: '程序代码',
    6: '阅读程序',
    8: '有错程序'
  }
  return map[quType] || '程序代码'
}
