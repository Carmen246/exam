import { post } from '@/utils/request'

export function fetchAiConfig() {
  return post('/exam/api/sys/ai-config/detail', {}, { silent: true })
}

export function saveAiConfig(data) {
  return post('/exam/api/sys/ai-config/save', data)
}

export function pingAiConfig(data) {
  return post('/exam/api/sys/ai-config/ping', data || {})
}
