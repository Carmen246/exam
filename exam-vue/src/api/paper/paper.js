import { post } from '@/utils/request'

/**
 * 试卷列表
 * @param data
 */
export function listPaper(userId, examId) {
  return post('/exam/api/paper/paper/paging', { current: 1, size: 5, params: { userId: userId, examId: examId }})
}

/**
 * 考试截图列表
 * @param paperId
 */
export function listCaptures(paperId) {
  return post('/exam/api/paper/paper/captures', { id: paperId })
}
