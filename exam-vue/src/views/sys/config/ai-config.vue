<template>
  <div class="ai-config-panel">
    <el-form ref="aiForm" :model="form" :rules="rules" label-width="150px" class="ai-config-form">
      <el-divider content-position="left">模型接口</el-divider>

      <el-form-item label="提供方" prop="provider">
        <el-select v-model="form.provider" placeholder="请选择">
          <el-option label="OpenAI 兼容 API" value="openai" />
          <el-option label="RAGFlow 聊天助手" value="ragflow" />
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

      <el-form-item v-if="form.provider === 'ragflow'" label="聊天助手 ID" prop="chatId">
        <el-input v-model="form.chatId" placeholder="RAGFlow chat-id" />
      </el-form-item>

      <el-form-item label="模型名称">
        <el-input v-model="form.modelName" placeholder="例如 deepseek-chat" />
      </el-form-item>

      <el-form-item label="请求超时">
        <el-input-number v-model="form.timeoutSeconds" :min="10" :max="600" controls-position="right" />
        <span class="unit-text">秒</span>
      </el-form-item>

      <el-divider content-position="left">RAGFlow 知识库（可选）</el-divider>

      <el-form-item label="知识库 API 地址">
        <el-input v-model="form.ragflowBaseUrl" placeholder="例如 http://localhost:9380" />
      </el-form-item>

      <el-form-item label="知识库 API Key">
        <el-input
          v-model="form.ragflowApiKey"
          :placeholder="form.ragflowApiKeyConfigured ? '已配置，留空则不修改' : '请输入 RAGFlow API Key'"
          show-password
        />
      </el-form-item>

      <el-form-item label="知识库 ID">
        <el-input v-model="form.ragflowDatasetId" placeholder="留空则按名称或列表自动匹配" />
      </el-form-item>

      <el-form-item label="知识库名称">
        <el-input v-model="form.ragflowDatasetName" placeholder="dataset-id 为空时按名称匹配" />
      </el-form-item>

      <el-form-item label="自动上传试卷">
        <el-switch v-model="form.ragflowAutoUpload" />
        <span class="field-tip">AI 导入时将试卷文档同步上传到 RAGFlow 知识库</span>
      </el-form-item>

      <el-form-item label="上传失败中断">
        <el-switch v-model="form.ragflowUploadFailFast" />
        <span class="field-tip">开启后，RAGFlow 上传失败将中断本次 AI 导入</span>
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
        id: '1',
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
        if (this.form.apiKeyConfigured) {
          this.form.apiKey = MASKED_SECRET
        }
        if (this.form.ragflowApiKeyConfigured) {
          this.form.ragflowApiKey = MASKED_SECRET
        }
      }).catch(() => {
        this.form = this.createDefaultForm()
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
      if (this.form.provider === 'ragflow' && !this.form.chatId) {
        this.$message.warning('RAGFlow 模式下请填写聊天助手 ID')
        return
      }

      const payload = { ...this.form, enabled: true }
        if (payload.apiKey === MASKED_SECRET) {
          payload.apiKey = ''
        }
        if (payload.ragflowApiKey === MASKED_SECRET) {
          payload.ragflowApiKey = ''
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
      if (this.form.provider === 'ragflow' && !this.form.chatId) {
        this.$message.warning('RAGFlow 模式下请先填写聊天助手 ID')
        return
      }

      const payload = { ...this.form, enabled: true }
      if (payload.apiKey === MASKED_SECRET) {
        payload.apiKey = ''
      }
      if (payload.ragflowApiKey === MASKED_SECRET) {
        payload.ragflowApiKey = ''
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
