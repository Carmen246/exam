import { post, upload, download, get } from '@/utils/request'

const AI_IMPORT_TIMEOUT = 600000
const TASK_POLL_INTERVAL = 2000

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
 * 创建 AI 导入异步任务
 * @param {{ file?: File, text?: string, repoIds: string[], level?: number }} data
 */
export function createImportTask(data) {
  const formData = new FormData()
  if (data.file) {
    formData.append('file', data.file)
  }
  if (data.answerFile) {
    formData.append('answerFile', data.answerFile)
  }
  if (data.text) {
    formData.append('text', data.text)
  }
  if (data.repoIds && data.repoIds.length) {
    data.repoIds.forEach(id => formData.append('repoIds', id))
  }
  formData.append('level', data.level || 1)
  formData.append('importMode', data.importMode || 'SMART')
  return post('/exam/api/qu/import-task', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 查询 AI 导入任务进度
 * @param taskId
 */
export function getImportTaskStatus(taskId) {
  return get('/exam/api/qu/import-task/' + taskId, { silent: true })
}

/**
 * 重试失败的 AI 导入任务
 * @param taskId
 */
export function retryImportTask(taskId, batchNo) {
  let url = '/exam/api/qu/import-task/' + taskId + '/retry'
  if (batchNo) {
    url += '?batchNo=' + batchNo
  }
  return post(url)
}

export { TASK_POLL_INTERVAL }

/**
 * AI解析试题
 * @param data
 */
export function parseQuestions(data) {
  return post('/exam/api/qu/parse-questions', data, { timeout: AI_IMPORT_TIMEOUT })
}

/**
 * AI确认导入试题
 * @param data
 */
export function confirmQuestionImport(data) {
  return post('/exam/api/qu/confirm-import', data)
}
