<template>
  <div class="ai-config-panel">
    <el-form ref="aiForm" :model="form" :rules="rules" label-width="150px" class="ai-config-form">
      <el-divider content-position="left">模型接口</el-divider>

      <el-form-item label="提供方" prop="provider">
        <el-select v-model="form.provider" placeholder="请选择">
          <el-option label="OpenAI 兼容 API" value="openai" />
        </el-select>
      </el-form-item>

      <el-form-item label="API 地址" prop="baseUrl">
        <el-input v-model="form.baseUrl" placeholder="例如 https://api.deepseek.com" />
      </el-form-item>

      <el-form-item label="API Key" prop="apiKey">
        <el-input
          v-model="form.apiKey"
          :placeholder="form.apiKeyConfigured ? '已配置，留空则不修改' : '请输入 API Key'"
          show-password
        />
      </el-form-item>

      <el-form-item label="模型名称">
        <el-input v-model="form.modelName" placeholder="例如 deepseek-chat" />
      </el-form-item>

      <el-form-item label="请求超时">
        <el-input-number v-model="form.timeoutSeconds" :min="10" :max="600" controls-position="right" />
        <span class="unit-text">秒</span>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="submitForm">保存配置</el-button>
        <el-button :loading="pingLoading" @click="handlePing">测试连通性</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
import { fetchAiConfig, saveAiConfig, pingAiConfig } from '@/api/sys/config/ai-config'

const MASKED_SECRET = '******'

export default {
  name: 'SysAiConfigPanel',
  data() {
    return {
      saving: false,
      pingLoading: false,
      form: this.createDefaultForm(),
      rules: {}
    }
  },
  created() {
    this.loadData()
  },
  methods: {
    createDefaultForm() {
      return {
        id: '',
        enabled: true,
        provider: 'openai',
        baseUrl: '',
        apiKey: '',
        apiKeyConfigured: false,
        chatId: '',
        modelName: '',
        timeoutSeconds: 60,
        ragflowBaseUrl: '',
        ragflowApiKey: '',
        ragflowApiKeyConfigured: false,
        ragflowDatasetId: '',
        ragflowDatasetName: '',
        ragflowAutoUpload: false,
        ragflowUploadFailFast: false
      }
    },
    loadData() {
      fetchAiConfig().then(res => {
        this.form = Object.assign(this.createDefaultForm(), res.data || {})
        this.form.provider = 'openai'
        if (this.form.apiKeyConfigured) {
          this.form.apiKey = MASKED_SECRET
        }
      }).catch(err => {
        this.form = this.createDefaultForm()
        const message = (err && err.message) || '加载 AI 配置失败，请确认数据库表 sys_ai_config 已创建'
        this.$message.error(message)
      })
    },
    submitForm() {
      if (!this.form.provider) {
        this.$message.warning('请选择提供方')
        return
      }
      if (!this.form.baseUrl) {
        this.$message.warning('API 地址不能为空')
        return
      }
      if (!this.form.apiKeyConfigured && !this.form.apiKey) {
        this.$message.warning('请填写 API Key')
        return
      }

      const payload = { ...this.form, enabled: true, provider: 'openai' }
      delete payload.apiKeyConfigured
      delete payload.ragflowApiKeyConfigured
      delete payload.chatId
      delete payload.ragflowBaseUrl
      delete payload.ragflowApiKey
      delete payload.ragflowDatasetId
      delete payload.ragflowDatasetName
      delete payload.ragflowAutoUpload
      delete payload.ragflowUploadFailFast
      if (payload.apiKey === MASKED_SECRET) {
        payload.apiKey = ''
      }

      this.saving = true
      saveAiConfig(payload).then(() => {
          this.$message.success('AI 配置保存成功')
          this.loadData()
        }).finally(() => {
          this.saving = false
        })
    },
    handlePing() {
      if (!this.form.baseUrl) {
        this.$message.warning('请先填写 API 地址')
        return
      }
      if (!this.form.apiKeyConfigured && !this.form.apiKey) {
        this.$message.warning('请先填写 API Key')
        return
      }

      const payload = { ...this.form, enabled: true, provider: 'openai' }
      if (payload.apiKey === MASKED_SECRET) {
        payload.apiKey = ''
      }

      this.pingLoading = true
      pingAiConfig(payload).then(res => {
        this.$message.success(res.data || 'AI 连通正常')
      }).finally(() => {
        this.pingLoading = false
      })
    }
  }
}
</script>

<style scoped>
.ai-config-form {
  max-width: 760px;
}

.unit-text,
.field-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
