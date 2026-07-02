/**
 * 文本是否像程序代码
 * @param {string} text
 */
export function looksLikeCode(text) {
  if (!text) {
    return false
  }
  return /#include|#define|void\s+main|int\s+main|main\s*\(|int\s+\w+\s*[=()]|public\s+class|def\s+\w+\(|function\s+\w+\(/.test(text)
}

const CODE_START_PATTERN = /^\s*(#include|#define|(int|char|void|float|double|long|short|unsigned)\s+\w+\s*[=()]|main\s*\()/m
const RUN_RESULT_SUFFIX = /运行结果\s*[:：]\s*.+$/m
const INLINE_INSTRUCTION = /阅读程序[，,]?\s*|写出运行结果[。.]?\s*/g

export function cleanQuestionStem(stem) {
  if (!stem) {
    return ''
  }
  return String(stem)
    .replace(/^\s*\d+[.．、]\s*/, '')
    .replace(RUN_RESULT_SUFFIX, '')
    .replace(/\s+/g, ' ')
    .trim()
}

export function formatProgramCode(code) {
  if (!code) {
    return ''
  }
  let text = String(code).trim()
  text = text.replace(INLINE_INSTRUCTION, '')
  text = text.replace(RUN_RESULT_SUFFIX, '').trim()
  text = text.replace(/^[ \u4e00-\u9fa5，。；：、！？…]+$/gm, '').trim()

  if (needsLayoutNormalize(text)) {
    text = text.replace(/;\s*/g, ';\n')
    text = text.replace(/\{\s*/g, ' {\n')
    text = text.replace(/\}\s*/g, '\n}\n')
  }

  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const result = []
  let level = 0
  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) {
      continue
    }
    if (line.startsWith('}')) {
      level = Math.max(0, level - 1)
    }
    result.push(`${'  '.repeat(level)}${line}`)
    if (line.endsWith('{')) {
      level += 1
    }
  }
  return result.join('\n')
}

function needsLayoutNormalize(text) {
  if (!text.includes('{') && !text.includes(';')) {
    return false
  }
  const lines = text.split('\n')
  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) {
      continue
    }
    if (/\{[^}]*;/.test(line)) {
      return true
    }
    if (line.length > 48 && line.includes(';')) {
      return true
    }
  }
  return false
}

function findCodeBlockStart(text) {
  if (!text) {
    return -1
  }
  const match = text.match(CODE_START_PATTERN)
  if (match && match.index != null) {
    return match.index
  }
  const markers = ['#include', 'void main', 'int main', 'main()']
  let earliest = -1
  for (const marker of markers) {
    const regex = new RegExp(`(^|\\n)\\s*${marker.replace(/[()]/g, '\\$&')}`)
    const m = text.match(regex)
    if (m && m.index != null) {
      const idx = m.index + (m[1] ? m[1].length : 0)
      if (earliest < 0 || idx < earliest) {
        earliest = idx
      }
    }
  }
  return earliest
}

export function isFillProgramQuType(type) {
  return type === 5
}

export function isReadProgramQuType(type) {
  return type === 6
}

/**
 * 阅读程序写结果题是否带 A/B/C/D 选项（展示方式与单选题一致）
 * @param {number} quType
 * @param {Array} answerList
 */
export function isReadProgramChoiceDisplay(quType, answerList) {
  if (quType !== 6 || !answerList || answerList.length < 2) {
    return false
  }
  return answerList.every(item => item && item.content && !looksLikeCode(item.content))
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
  let text = String(content).trim()
  text = text.replace(RUN_RESULT_SUFFIX, '').trim()

  const idx = findCodeBlockStart(text)
  if (idx > 0) {
    return {
      stem: cleanQuestionStem(text.substring(0, idx)),
      code: formatProgramCode(text.substring(idx))
    }
  }
  if (idx === 0) {
    return { stem: '', code: formatProgramCode(text) }
  }
  if (looksLikeCode(text)) {
    return { stem: '', code: formatProgramCode(text) }
  }
  return { stem: cleanQuestionStem(text), code: '' }
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
