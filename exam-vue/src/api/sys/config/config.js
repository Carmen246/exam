import { post } from '@/utils/request'

// 获得站点配置（启动时静默加载，失败不弹 toast）
export function fetchDetail() {
  return post('/exam/api/sys/config/detail', { id: '1' }, { silent: true })
}

export function saveData(data) {
  return post('/exam/api/sys/config/save', data)
}
