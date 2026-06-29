import { post, upload, download } from '@/utils/request'

/**
 * 题库详情
 * @param data
 */
export function fetchDetail(id) {
  return post('/exam/api/qu/qu/detail', { id: id })
}

/**
 * 保存题库
 * @param data
 */
export function saveData(data) {
  return post('/exam/api/qu/qu/save', data)
}

/**
 * 导出
 * @param data
 */
export function exportExcel(data) {
  return download('/exam/api/qu/qu/export', data, '导出的数据.xlsx')
}

/**
 * 随机抽题导出Word试卷
 * @param data
 */
export function exportRandomWord(data) {
  const fileName = (data.title || '随机试卷') + '.docx'
  return download('/exam/api/paper/paper/export-random-word', data, fileName)
}

/**
 * 导入模板
 * @param data
 */
export function importTemplate() {
  return download('/exam/api/qu/qu/import/template', {}, 'qu-import-template.xlsx')
}

/**
 * 导出
 * @param data
 */
export function importExcel(file) {
  return upload('/exam/api/qu/qu/import', file)
}

/**
 * AI解析试题文档文本
 * @param file
 */
export function parseQuestionText(file) {
  return upload('/exam/api/qu/parse-text', file)
}

/**
 * AI解析试题
 * @param data
 */
export function parseQuestions(data) {
  return post('/exam/api/qu/parse-questions', data)
}

/**
 * AI确认导入试题
 * @param data
 */
export function confirmQuestionImport(data) {
  return post('/exam/api/qu/confirm-import', data)
}
