// import parseTime, formatTime and set to filter
export { parseTime, formatTime } from '@/utils'

/**
 * Upper case first char
 * @param {String} string
 */
export function uppercaseFirst(string) {
  return string.charAt(0).toUpperCase() + string.slice(1)
}

/**
 * 通用状态过滤器
 * @param value
 * @returns {*}
 */
export function stateFilter(value) {
  const map = {
    '0': '正常',
    '1': '禁用'
  }
  return map[value]
}

export function quTypeFilter(value) {
  const map = {
    '1': '单选题',
    '2': '多选题',
    '3': '判断题',
    '4': '填空题',
    '5': '程序填空题',
    '6': '阅读程序写结果题',
    '7': '编程题',
    '8': '程序改错题',
    '9': '综合应用题'
  }
  return map[value]
}

export const QU_TYPE_OPTIONS = [
  { value: 1, label: '单选题' },
  { value: 2, label: '多选题' },
  { value: 3, label: '判断题' },
  { value: 4, label: '填空题' },
  { value: 5, label: '程序填空题' },
  { value: 6, label: '阅读程序写结果题' },
  { value: 7, label: '编程题' },
  { value: 8, label: '程序改错题' },
  { value: 9, label: '综合应用题' }
]

export function isObjectiveQuType(type) {
  return type === 1 || type === 2 || type === 3
}

export function isFillQuType(type) {
  return type === 4 || type === 5
}

export function isFillProgramQuType(type) {
  return type === 5
}

export function isReadProgramQuType(type) {
  return type === 6
}

export { isReadProgramChoiceDisplay, isFillProgramChoiceDisplay } from '@/utils/quFormat'

export function isProgramQuType(type) {
  return type === 7
}

export function isFixProgramQuType(type) {
  return type === 8
}

export function isStemCodeQuType(type) {
  return type === 5 || type === 6 || type === 8
}

export function isNormalFillQuType(type) {
  return type === 4
}

export function isSubjectiveQuType(type) {
  return type >= 4 && type <= 9
}

export function paperStateFilter(value) {
  const map = {
    '0': '考试中',
    '1': '待阅卷',
    '2': '已考完',
    '3': '!已弃考'
  }
  return map[value]
}

export function examOpenType(value) {
  const map = {
    '1': '完全公开',
    '2': '指定部门'
  }
  return map[value]
}

export function examStateFilter(value) {
  const map = {
    '0': '进行中',
    '1': '已禁用',
    '2': '待开始',
    '3': '已结束'
  }
  return map[value]
}
