import axios from 'axios'
import { MessageBox, Message } from 'element-ui'
import { Loading } from 'element-ui'
import store from '@/store'
import { getToken } from '@/utils/auth'

const AUTH_WHITE_PATHS = ['/login', '/register']

function isSilent(config) {
  return config && config.silent === true
}

function isAuthWhitePage() {
  const hash = window.location.hash || ''
  return AUTH_WHITE_PATHS.some(path => hash.includes(path))
}

// 请求实例
const instance = axios.create({
  baseURL: process.env.VUE_APP_BASE_API,
  timeout: 60000
})

// 请求前置过滤器
instance.interceptors.request.use(
  config => {
    if (store.getters.token) {
      config.headers['token'] = getToken()
    }
    return config
  },
  error => {
    console.log(error)
    return Promise.reject(error)
  }
)

// 响应数据拦截并做通用处理
instance.interceptors.response.use(
  response => {
    const res = response.data

    // 下载文件直接返回，避免把 Word、Excel 等 blob 当成普通 JSON 处理
    if (response.config.responseType === 'blob') {
      return response
    }

    // 下载文件直接返回
    if (res.type === 'application/octet-stream') {
      return response
    }

    if (res.type === 'application/vnd.ms-excel') {
      return response
    }

    // 0为正确响应码
    if (res.code !== 0) {
      const silent = isSilent(response.config)
      if (!silent) {
        Message({
          message: res.msg || 'Error',
          type: 'error',
          duration: 5 * 1000
        })
      }

      // 登录超时响应码（登录/注册页不弹重新登录框）
      if (res.code === 10010002 && !silent && !isAuthWhitePage()) {
        MessageBox.confirm('登录超时，请重新登录！', '登录提示', {
          confirmButtonText: '重新登录',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          store.dispatch('user/resetToken').then(() => {
            location.reload()
          })
        })
      }
      return Promise.reject(new Error(res.msg || 'Error'))
    } else {
      return res
    }
  },
  error => {
    console.log('err' + error)
    if (!isSilent(error.config)) {
      Message({
        message: error.message,
        type: 'error',
        duration: 5 * 1000
      })
    }
    return Promise.reject(error)
  }
)

async function parseBlobError(blob) {
  try {
    const text = await blob.text()
    const json = JSON.parse(text)
    if (json && typeof json.code !== 'undefined') {
      return json.msg || '下载失败'
    }
  } catch (e) {
    // 非 JSON 错误体，按正常文件处理
  }
  return null
}

/**
 * 上传
 * @param url
 * @param data
 */
export function upload(url, file, data) {
  const formData = new FormData()
  formData.append('file', file)

  // 附加数据
  if (data) {
    Object.keys(data).forEach((key) => {
      formData.append(key, data[key])
    })
  }

  return new Promise((resolve, reject) => {
    // 打开
    const loading = Loading.service({
      text: '正在上传数据...',
      background: 'rgba(0, 0, 0, 0.7)'
    })

    instance.request({
      url: url,
      method: 'post',
      data: formData,
      timeout: 1200000
    }).then(response => {
      console.log(response)
      loading.close()
      resolve(response)
    }).catch(err => {
      loading.close()
      reject(err)
    })
  })
}

/**
 * 下载
 * @param url
 * @param data
 */
export function download(url, data, fileName) {
  return new Promise((resolve, reject) => {
    // 打开
    const loading = Loading.service({
      text: '正在下载数据...',
      background: 'rgba(0, 0, 0, 0.7)'
    })

    instance.request({
      url: url,
      method: 'post',
      data: data,
      timeout: 1200000,
      responseType: 'blob'
    }).then(async res => {
      loading.close()

      const contentType = res.headers['content-type'] || ''
      if (contentType.includes('application/json')) {
        const msg = await parseBlobError(res.data)
        Message.error(msg || '下载失败')
        reject(new Error(msg || '下载失败'))
        return
      }

      // 文件下载
      const blob = new Blob([res.data], {
        type: contentType || 'application/octet-stream'
      })

      if (blob.size < 2048) {
        const msg = await parseBlobError(blob)
        if (msg) {
          Message.error(msg)
          reject(new Error(msg))
          return
        }
      }

      // 获得文件名称
      let link = document.createElement('a')
      const objectUrl = URL.createObjectURL(blob)
      link.href = objectUrl
      link.setAttribute('download', fileName)
      link.click()
      link = null
      URL.revokeObjectURL(objectUrl)
      Message.success('导出成功!')
      resolve()
    }).catch(err => {
      loading.close()
      reject(err)
    })
  })
}

/**
 * 封装post请求
 * @param url
 * @param data
 * @returns {Promise}
 */
export function post(url, data = {}, config = {}) {
  return new Promise((resolve, reject) => {
    instance.post(url, data, config)
      .then(response => {
        resolve(response)
      }, err => {
        reject(err)
      })
  })
}
