<template>
  <div class="app-container ai-import-page">
    <el-card shadow="never" class="import-panel">
      <div slot="header" class="panel-header">
        <div>
          <div class="panel-title">AI导入试题</div>
          <div class="panel-subtitle">选择题库和难度后，上传文档或粘贴文本，AI解析后确认入库。</div>
        </div>
        <el-button size="small" @click="goBack">返回题目列表</el-button>
      </div>

      <el-steps :active="activeStep" finish-status="success" simple class="import-steps">
        <el-step title="选择题库" />
        <el-step title="上传/粘贴" />
        <el-step title="解析预览" />
        <el-step title="确认入库" />
      </el-steps>

      <el-form ref="parseForm" :model="parseForm" :rules="rules" label-width="90px" class="base-form">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="归属题库" prop="repoIds">
              <repo-select v-model="parseForm.repoIds" :multi="true" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="难度等级" prop="level">
              <el-select v-model="parseForm.level" class="level-select">
                <el-option
                  v-for="item in levelOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="18" class="main-row">
        <el-col :xs="24" :md="10">
          <el-card shadow="never" class="work-card">
            <div slot="header" class="card-header">
              <span>试题来源</span>
              <el-tag v-if="fileInfo.fileName" size="mini" type="info">{{ fileInfo.fileName }}</el-tag>
            </div>

            <div class="upload-box">
              <el-upload
                action=""
                :auto-upload="false"
                :show-file-list="false"
                accept=".docx,.txt"
                :on-change="handleFileChange"
              >
                <el-button icon="el-icon-upload2" type="primary" plain :loading="fileLoading">选择docx/txt文件</el-button>
              </el-upload>
              <div class="upload-tip">也可以直接在下方粘贴试题文本。</div>
            </div>

            <el-input
              v-model="parseForm.text"
              type="textarea"
              :rows="18"
              placeholder="在这里粘贴试题文本，例如：1. Java属于什么类型的语言？ A. 编程语言 B. 数据库 C. 操作系统 D. 浏览器 答案：A"
            />

            <div class="source-footer">
              <span class="text-count">当前文本 {{ textLength }} 字</span>
              <div>
                <el-button size="small" @click="clearSource">清空</el-button>
                <el-button
                  size="small"
                  icon="el-icon-view"
                  :disabled="!rawSourceText"
                  @click="showRawText"
                >
                  查看清洗前文本
                </el-button>
                <el-button
                  size="small"
                  icon="el-icon-edit-outline"
                  :loading="normalizeLoading"
                  @click="handleNormalize"
                >
                  AI清洗文本
                </el-button>
                <el-button type="primary" size="small" icon="el-icon-magic-stick" :loading="parseLoading" @click="handleParse">
                  开始AI解析
                </el-button>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="14">
          <el-card shadow="never" class="work-card preview-card">
            <div slot="header" class="card-header">
              <span>解析结果预览</span>
              <div>
                <el-tag size="mini" type="success">共 {{ questions.length }} 题</el-tag>
              </div>
            </div>

            <el-empty v-if="questions.length === 0" description="解析后将在这里预览试题" />

            <div v-else class="question-list">
              <div v-for="(item, index) in questions" :key="index" class="question-item">
                <div class="question-head">
                  <div>
                    <el-tag size="mini" :type="quTypeTag(item.quType)">{{ quTypeLabel(item.quType) }}</el-tag>
                    <span class="question-title">{{ index + 1 }}. {{ item.content }}</span>
                  </div>
                  <el-button type="text" class="danger-link" @click="removeQuestion(index)">删除</el-button>
                </div>

                <div class="answer-list">
                  <div
                    v-for="(answer, answerIndex) in item.answerList"
                    :key="answerIndex"
                    class="answer-item"
                    :class="{ right: answer.isRight }"
                  >
                    <span class="answer-prefix">{{ optionLabel(answerIndex) }}.</span>
                    <span>{{ answer.content }}</span>
                    <el-tag v-if="answer.isRight" size="mini" type="success">正确</el-tag>
                  </div>
                </div>

                <div v-if="item.analysis" class="analysis">解析：{{ item.analysis }}</div>
              </div>
            </div>

            <div class="preview-footer">
              <el-button :disabled="questions.length === 0" @click="handleParse">重新解析</el-button>
              <el-button type="primary" :disabled="questions.length === 0" :loading="importLoading" @click="handleConfirmImport">
                确认导入题库
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-dialog
      title="清洗前文本"
      :visible.sync="rawTextDialogVisible"
      width="760px"
      append-to-body
    >
      <el-input
        v-model="rawSourceText"
        type="textarea"
        :rows="18"
        readonly
      />
      <div slot="footer">
        <el-button @click="rawTextDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="replaceWithRawText">使用清洗前文本替换</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import RepoSelect from '@/components/RepoSelect'
import { parseQuestionText, parseQuestions, confirmQuestionImport } from '@/api/qu/qu'
import { post } from '@/utils/request'

export default {
  name: 'AiImportQu',
  components: { RepoSelect },
  data() {
    return {
      fileLoading: false,
      normalizeLoading: false,
      parseLoading: false,
      importLoading: false,
      rawTextDialogVisible: false,
      rawSourceText: '',
      fileInfo: {
        fileName: ''
      },
      parseForm: {
        repoIds: [],
        level: 1,
        text: ''
      },
      levelOptions: [
        { label: '普通', value: 1 },
        { label: '较难', value: 2 }
      ],
      questions: [],
      rules: {
        repoIds: [
          { required: true, type: 'array', min: 1, message: '请至少选择一个题库', trigger: 'change' }
        ],
        level: [
          { required: true, message: '请选择难度等级', trigger: 'change' }
        ]
      }
    }
  },
  computed: {
    activeStep() {
      if (this.questions.length > 0) {
        return 3
      }
      if (this.parseForm.text) {
        return 2
      }
      if (this.parseForm.repoIds && this.parseForm.repoIds.length > 0) {
        return 1
      }
      return 0
    },
    textLength() {
      return this.parseForm.text ? this.parseForm.text.length : 0
    }
  },
  methods: {
    handleFileChange(file) {
      const rawFile = file.raw
      if (!rawFile) {
        return
      }

      const fileName = rawFile.name || ''
      const lowerName = fileName.toLowerCase()
      if (!lowerName.endsWith('.docx') && !lowerName.endsWith('.txt')) {
        this.$message.warning('只支持docx和txt文件')
        return
      }

      this.fileLoading = true
      parseQuestionText(rawFile).then(res => {
        this.fileInfo = {
          fileName: res.data.fileName
        }
        const rawText = res.data.rawText || ''
        this.rawSourceText = rawText
        this.questions = []
        this.$message.success('文档解析完成，正在自动AI清洗文本')
        this.normalizeTextValue(rawText).then(normalizedText => {
          this.parseForm.text = normalizedText
          this.$message.success('AI清洗完成，已填入清洗后的文本')
          this.fileLoading = false
        }, () => {
          this.parseForm.text = rawText
          this.$message.warning('AI清洗失败，已保留原始解析文本')
          this.fileLoading = false
        })
      }, () => {
        this.fileLoading = false
      })
    },

    handleNormalize() {
      if (!this.parseForm.text || this.parseForm.text.trim().length === 0) {
        this.$message.warning('请先上传文件或粘贴试题文本')
        return
      }

      this.normalizeLoading = true
      this.rawSourceText = this.parseForm.text
      this.normalizeTextValue(this.parseForm.text).then(normalizedText => {
        this.parseForm.text = normalizedText
        this.questions = []
        this.$message.success('AI清洗完成，请检查文本后再解析')
        this.normalizeLoading = false
      }, () => {
        this.normalizeLoading = false
      })
    },

    normalizeTextValue(text) {
      return post('/exam/api/qu/normalize-text', {
        text: text
      }).then(res => {
        const data = res.data || {}
        if (!data.normalizedText) {
          return Promise.reject(new Error('AI未返回清洗后的文本'))
        }
        return data.normalizedText
      })
    },

    handleParse() {
      this.$refs.parseForm.validate(valid => {
        if (!valid) {
          return
        }

        if (!this.parseForm.text || this.parseForm.text.trim().length === 0) {
          this.$message.warning('请先上传文件或粘贴试题文本')
          return
        }

        this.parseLoading = true
        parseQuestions({
          repoIds: this.parseForm.repoIds,
          level: this.parseForm.level,
          text: this.parseForm.text
        }).then(res => {
          const data = res.data || {}
          this.questions = data.questions || []
          if (this.questions.length === 0) {
            this.$message.warning('未解析出试题，请调整文本后重试')
            this.parseLoading = false
            return
          }
          this.$message.success('AI解析完成，请确认试题内容')
          this.parseLoading = false
        }, () => {
          this.parseLoading = false
        })
      })
    },

    handleConfirmImport() {
      if (this.questions.length === 0) {
        this.$message.warning('没有可导入的试题')
        return
      }

      this.importLoading = true
      const importQuestions = this.questions.map(item => {
        return Object.assign({}, item, {
          repoIds: this.parseForm.repoIds,
          level: this.parseForm.level
        })
      })

      confirmQuestionImport({
        questions: importQuestions
      }).then(res => {
        const count = res.data && res.data.count ? res.data.count : importQuestions.length
        this.$notify({
          title: '导入成功',
          message: '已导入 ' + count + ' 道试题',
          type: 'success',
          duration: 2000
        })
        this.$router.push({ name: 'ListQu' })
        this.importLoading = false
      }, () => {
        this.importLoading = false
      })
    },

    removeQuestion(index) {
      this.questions.splice(index, 1)
    },

    showRawText() {
      if (!this.rawSourceText) {
        this.$message.warning('暂无清洗前文本')
        return
      }
      this.rawTextDialogVisible = true
    },

    replaceWithRawText() {
      this.parseForm.text = this.rawSourceText
      this.questions = []
      this.rawTextDialogVisible = false
      this.$message.success('已替换为清洗前文本')
    },

    clearSource() {
      this.fileInfo = {
        fileName: ''
      }
      this.parseForm.text = ''
      this.rawSourceText = ''
      this.rawTextDialogVisible = false
      this.questions = []
    },

    goBack() {
      this.$router.push({ name: 'ListQu' })
    },

    quTypeLabel(type) {
      const map = {
        1: '单选题',
        2: '多选题',
        3: '判断题'
      }
      return map[type] || '未知题型'
    },

    quTypeTag(type) {
      const map = {
        1: '',
        2: 'warning',
        3: 'info'
      }
      return map[type] || 'danger'
    },

    optionLabel(index) {
      return String.fromCharCode(65 + index)
    }
  }
}
</script>

<style scoped>
.ai-import-page {
  color: #303133;
}

.import-panel {
  border-radius: 6px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  line-height: 26px;
}

.panel-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}

.import-steps {
  margin-bottom: 18px;
}

.base-form {
  padding: 12px 0 0;
}

.level-select {
  width: 180px;
}

.main-row {
  margin-top: 4px;
}

.work-card {
  min-height: 560px;
  border-radius: 6px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.upload-box {
  padding: 4px 0 14px;
}

.upload-tip {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.source-footer,
.preview-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.text-count {
  color: #909399;
  font-size: 12px;
}

.preview-card {
  position: relative;
}

.question-list {
  max-height: 466px;
  overflow-y: auto;
  padding-right: 4px;
}

.question-item {
  padding: 14px 0;
  border-bottom: 1px solid #ebeef5;
}

.question-item:first-child {
  padding-top: 0;
}

.question-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.question-title {
  margin-left: 8px;
  font-weight: 600;
  line-height: 24px;
}

.danger-link {
  color: #f56c6c;
}

.answer-list {
  margin-top: 8px;
  padding-left: 8px;
}

.answer-item {
  display: flex;
  align-items: center;
  min-height: 28px;
  color: #606266;
  gap: 8px;
}

.answer-item.right {
  color: #2f8f46;
  font-weight: 600;
}

.answer-prefix {
  width: 18px;
  color: #909399;
}

.analysis {
  margin-top: 8px;
  padding: 8px 10px;
  color: #606266;
  background: #f7f8fa;
  border-radius: 4px;
  line-height: 22px;
}

@media (max-width: 992px) {
  .preview-card {
    margin-top: 18px;
  }

  .work-card {
    min-height: auto;
  }
}
</style>
