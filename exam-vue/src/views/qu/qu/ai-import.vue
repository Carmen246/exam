<template>
  <div class="app-container ai-import-page">
    <el-card shadow="never" class="import-panel">
      <div slot="header" class="panel-header">
        <div>
          <div class="panel-title">AI导入试题</div>
          <div class="panel-subtitle">选择题库和难度后，上传文档或粘贴文本，后台异步解析，完成后确认入库。</div>
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

      <el-card v-if="taskRunning || taskFailed || taskPartialCompleted" shadow="never" class="task-progress-card">
        <div class="task-progress-header">
          <span>{{ taskProgressTitle }}</span>
          <div class="task-progress-tags">
            <el-tag v-if="taskStatusLabel" size="mini">{{ taskStatusLabel }}</el-tag>
            <el-tag v-if="taskId" size="mini" type="info">任务 {{ taskId }}</el-tag>
          </div>
        </div>
        <el-progress
          :percentage="taskProgress"
          :status="taskFailed ? 'exception' : (taskPartialCompleted ? 'warning' : (taskProgress >= 100 ? 'success' : undefined))"
          :stroke-width="16"
        />
        <div class="task-progress-message">{{ taskMessage }}</div>
        <div v-if="taskTotalBatches > 0" class="task-progress-batch">
          <span v-if="taskDeepCleanBatchCount > 0">深度清洗 {{ taskDeepCleanBatchCount }} 批 · </span>
          批次进度：{{ taskCompletedBatches }} / {{ taskTotalBatches }}
          <span v-if="taskFailedBatchCount > 0"> · 失败 {{ taskFailedBatchCount }} 批</span>
        </div>
        <div v-if="taskFailed || taskPartialCompleted" class="task-progress-actions">
          <span v-if="retryHint" class="task-retry-hint">{{ retryHint }}</span>
          <el-button
            type="primary"
            size="small"
            icon="el-icon-refresh"
            :loading="retryLoading"
            :disabled="!canRetryTask"
            @click="handleRetryTask()"
          >
            重试全部失败批次
          </el-button>
        </div>

        <div v-if="failedBatches.length > 0" class="failed-batch-panel">
          <el-collapse v-model="failedBatchCollapse">
            <el-collapse-item :title="'失败批次 ' + failedBatches.length + ' 批'" name="failed">
              <el-table :data="failedBatches" size="mini" border stripe>
                <el-table-column prop="batchNo" label="批次" width="70" align="center" />
                <el-table-column prop="errorMessage" label="失败原因" min-width="180" show-overflow-tooltip />
                <el-table-column prop="previewText" label="文本片段" min-width="200" show-overflow-tooltip />
                <el-table-column label="操作" width="140" align="center">
                  <template v-slot="scope">
                    <el-button type="text" size="mini" @click="showBatchPreview(scope.row)">查看片段</el-button>
                    <el-button
                      type="text"
                      size="mini"
                      :disabled="!canRetryTask || taskRunning"
                      @click="handleRetryTask(scope.row.batchNo)"
                    >
                      重试
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-card>

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
                accept=".docx,.txt,.pdf,.xls,.xlsx"
                :disabled="taskRunning"
                :on-change="handleFileChange"
              >
                <el-button icon="el-icon-upload2" type="primary" plain :loading="fileLoading" :disabled="taskRunning">选择试卷文档(docx/txt/pdf/xls/xlsx)</el-button>
              </el-upload>
              <el-upload
                action=""
                :auto-upload="false"
                :show-file-list="false"
                accept=".docx,.txt,.pdf,.xls,.xlsx"
                :disabled="taskRunning"
                :on-change="handleAnswerFileChange"
                class="answer-upload"
              >
                <el-button icon="el-icon-document" plain :disabled="taskRunning">选择答案文档（可选）</el-button>
              </el-upload>
              <el-alert
                v-if="pendingFile && !taskRunning"
                class="file-selected-alert"
                :title="'已选择试卷：' + fileInfo.fileName"
                :description="fileReadyTip"
                type="info"
                :closable="false"
                show-icon
              />
              <el-alert
                v-if="pendingAnswerFile && !taskRunning"
                class="file-selected-alert"
                :title="'已选择答案文档：' + answerFileInfo.fileName"
                type="success"
                :closable="false"
                show-icon
              />
              <div v-if="!pendingFile && !pendingAnswerFile" class="upload-tip">也可以直接在下方粘贴试题文本。上传试卷/答案文档后，点击「开始AI导入」开始解析。</div>
            </div>

            <el-input
              v-model="parseForm.text"
              type="textarea"
              :rows="18"
              :readonly="taskRunning"
              :placeholder="textPlaceholder"
            />

            <div class="source-footer">
              <span class="text-count">当前文本 {{ textLength }} 字</span>
              <div>
                <el-button size="small" :disabled="taskRunning" @click="clearSource">清空</el-button>
                <el-button
                  size="small"
                  icon="el-icon-view"
                  :disabled="!rawSourceText"
                  @click="showRawText"
                >
                  查看清洗前文本
                </el-button>
                <el-button
                  type="primary"
                  size="small"
                  icon="el-icon-magic-stick"
                  :loading="taskRunning"
                  @click="handleStartImport"
                >
                  开始AI导入
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

            <el-empty v-if="questions.length === 0" :description="taskRunning ? '正在解析，请稍候...' : '解析完成后将在这里预览试题'" />

            <div v-else class="question-list">
              <div v-for="(item, index) in questions" :key="index" class="question-item">
                <div class="question-head">
                  <div class="question-head-main">
                    <div class="question-index-line">
                      <el-tag size="mini" :type="quTypeTag(item.quType)">{{ quTypeLabel(item.quType) }}</el-tag>
                      <span class="question-index">{{ index + 1 }}.</span>
                    </div>
                    <div v-if="isSubjectiveQuType(item.quType) && !isStemCodeQuType(item.quType)" class="answer-section-title">题干</div>
                    <template v-if="isStemCodeQuType(item.quType)">
                      <div class="answer-section-title">题干</div>
                      <formatted-text
                        :text="parseStemCodeContent(item.content).stem"
                        class="question-content"
                      />
                      <div v-if="parseStemCodeContent(item.content).code" class="fill-program-code-block">
                        <div class="answer-section-title">{{ stemCodeSectionLabel(item.quType) }}</div>
                        <formatted-text
                          :text="parseStemCodeContent(item.content).code"
                          :code="true"
                        />
                      </div>
                    </template>
                    <template v-else-if="isProgramQuType(item.quType)">
                      <div class="answer-section-title">题干</div>
                      <formatted-text :text="item.content" class="question-content" />
                    </template>
                    <formatted-text
                      v-else
                      :text="item.content"
                      :code="needsCodeFormatForStem(item.quType, item.content)"
                      class="question-content"
                    />
                  </div>
                  <el-button type="text" class="danger-link" :disabled="taskRunning" @click="removeQuestion(index)">删除</el-button>
                </div>

                <div v-if="isObjectiveQuType(item.quType) || isReadProgramChoiceDisplay(item.quType, item.answerList)" class="answer-list">
                  <div
                    v-for="(answer, answerIndex) in item.answerList"
                    :key="answerIndex"
                    class="answer-item"
                    :class="{ right: answer.isRight }"
                  >
                    <span class="answer-prefix">{{ optionLabel(answerIndex) }}.</span>
                    <formatted-text :text="answer.content" class="answer-content" />
                    <el-tag v-if="answer.isRight" size="mini" type="success">正确</el-tag>

                    <div v-if="answer.analysis" class="answer-analysis">
                      答案解析：<formatted-text :text="answer.analysis" />
                    </div>
                  </div>
                </div>

                <div v-else-if="isFillProgramQuType(item.quType) && isFillProgramChoiceDisplay(item.quType, item.answerList)" class="answer-list">
                  <div
                    v-for="(answer, answerIndex) in item.answerList"
                    :key="answerIndex"
                    class="fill-blank-choice-group"
                  >
                    <div v-if="item.answerList.length > 1" class="answer-section-title">{{ fillProgramBlankLabel(answerIndex) }}</div>
                    <div
                      v-for="(opt, optIndex) in answer.optionList"
                      :key="optIndex"
                      class="answer-item"
                      :class="{ right: opt.isRight }"
                    >
                      <span class="answer-prefix">{{ opt.letter }}.</span>
                      <formatted-text :text="opt.content" class="answer-content" />
                      <el-tag v-if="opt.isRight" size="mini" type="success">正确</el-tag>

                      <div v-if="opt.analysis" class="answer-analysis">
                        答案解析：<formatted-text :text="opt.analysis" />
                      </div>
                    </div>
                  </div>
                </div>

                <div v-else-if="isFillProgramQuType(item.quType) && item.answerList && item.answerList.length" class="answer-list">
                  <div class="answer-section-title">各空参考答案</div>
                  <div
                    v-for="(answer, answerIndex) in item.answerList"
                    :key="answerIndex"
                    class="answer-item reference-item"
                  >
                    <span class="answer-prefix">{{ fillProgramBlankLabel(answerIndex) }}：</span>
                    <formatted-text :text="answer.content" class="answer-content" />
                  </div>
                </div>

                <div v-else-if="isNormalFillQuType(item.quType) && item.answerList && item.answerList.length" class="answer-list">
                  <div class="answer-section-title">参考答案</div>
                  <div
                    v-for="(answer, answerIndex) in item.answerList"
                    :key="answerIndex"
                    class="answer-item reference-item"
                  >
                    <span class="answer-prefix">{{ answerIndex + 1 }}.</span>
                    <formatted-text :text="answer.content" class="answer-content" />
                  </div>
                </div>

                <div v-else-if="item.answerList && item.answerList.length && !isReadProgramChoiceDisplay(item.quType, item.answerList)" class="reference-block">
                  <div class="answer-section-title">{{ subjectiveAnswerLabel(item.quType) }}</div>
                  <div
                    v-for="(answer, answerIndex) in item.answerList"
                    :key="answerIndex"
                    class="reference-text"
                  >
                    <formatted-text
                      :text="answer.content"
                      :code="needsCodeFormatForAnswer(item.quType, answer.content)"
                    />
                  </div>
                </div>

                <div v-else-if="isSubjectiveQuType(item.quType)" class="reference-block">
                  <el-tag size="mini" type="info">主观题（暂无参考答案）</el-tag>
                </div>

                <div v-if="item.analysis" class="analysis">
                  <div class="answer-section-title">整体解析</div>
                  <formatted-text :text="item.analysis" />
                </div>
              </div>
            </div>

            <div class="preview-footer">
              <el-button :disabled="taskRunning || (!pendingFile && !parseForm.text)" @click="handleStartImport">重新导入</el-button>
              <el-button type="primary" :disabled="questions.length === 0 || taskRunning" :loading="importLoading" @click="handleConfirmImport">
                确认导入题库
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-dialog
      title="批次文本片段"
      :visible.sync="batchPreviewDialogVisible"
      width="760px"
      append-to-body
    >
      <div v-if="batchPreviewItem.batchNo" class="batch-preview-meta">
        第 {{ batchPreviewItem.batchNo }} 批
        <span v-if="batchPreviewItem.errorMessage"> · {{ batchPreviewItem.errorMessage }}</span>
      </div>
      <el-input
        v-model="batchPreviewItem.previewText"
        type="textarea"
        :rows="12"
        readonly
      />
      <div slot="footer">
        <el-button @click="batchPreviewDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>

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
      </div>
    </el-dialog>
  </div>
</template>

<script>
import RepoSelect from '@/components/RepoSelect'
import FormattedText from '@/components/FormattedText'
import { createImportTask, getImportTaskStatus, retryImportTask, confirmQuestionImport, TASK_POLL_INTERVAL } from '@/api/qu/qu'
import { quTypeFilter, isObjectiveQuType, isFillQuType, isFillProgramQuType, isNormalFillQuType, isSubjectiveQuType, isStemCodeQuType, isProgramQuType, isReadProgramChoiceDisplay, isFillProgramChoiceDisplay } from '@/filters'
import {
  needsCodeFormatForStem,
  needsCodeFormatForAnswer,
  subjectiveAnswerLabel,
  parseFillProgramContent,
  parseStemCodeContent,
  fillProgramBlankLabel,
  hideFillMarkersForDisplay,
  stemCodeSectionLabel
} from '@/utils/quFormat'

export default {
  name: 'AiImportQu',
  components: { RepoSelect, FormattedText },
  data() {
    return {
      pendingFile: null,
      pendingAnswerFile: null,
      fileLoading: false,
      importSource: 'text',
      importLoading: false,
      rawTextDialogVisible: false,
      rawSourceText: '',
      fileInfo: {
        fileName: ''
      },
      answerFileInfo: {
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
      taskId: '',
      taskRunning: false,
      taskFailed: false,
      taskPartialCompleted: false,
      taskProgress: 0,
      taskMessage: '',
      taskStatusLabel: '',
      taskTotalBatches: 0,
      taskCompletedBatches: 0,
      taskFailedBatchCount: 0,
      taskDeepCleanBatchCount: 0,
      taskBatches: [],
      failedBatchCollapse: ['failed'],
      batchPreviewDialogVisible: false,
      batchPreviewItem: {},
      lastPolledStatus: '',
      taskHasNormalizedText: false,
      taskErrorMessage: '',
      retryLoading: false,
      pollTimer: null,
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
      if (this.taskRunning || this.parseForm.text || this.pendingFile) {
        return 2
      }
      if (this.parseForm.repoIds && this.parseForm.repoIds.length > 0) {
        return 1
      }
      return 0
    },
    textLength() {
      return this.parseForm.text ? this.parseForm.text.length : 0
    },
    fileReadyTip() {
      if (this.isExcelFile(this.fileInfo.fileName)) {
        return 'Excel 文件已就绪，将直接解析结构化数据，确认后点击「开始AI导入」。'
      }
      const parts = ['试卷文档已就绪']
      if (this.pendingAnswerFile) {
        parts.push('答案文档已就绪')
      } else {
        parts.push('可选上传答案文档')
      }
      parts.push('确认后点击「开始AI导入」')
      return parts.join('，') + '。'
    },
    textPlaceholder() {
      if (this.taskRunning && this.importSource === 'file') {
        return '文档正在后台解析，完成后将在这里显示清洗后的文本...'
      }
      return '在这里粘贴试题文本，例如：1. Java属于什么类型的语言？ A. 编程语言 B. 数据库 C. 操作系统 D. 浏览器 答案：A'
    },
    retryHint() {
      if (!this.taskFailed && !this.taskPartialCompleted) {
        return ''
      }
      if (this.taskErrorMessage) {
        return this.taskErrorMessage
      }
      if (this.isExcelFile(this.fileInfo.fileName)) {
        return 'Excel 解析失败，请检查表头格式后重新上传'
      }
      if (!this.rawSourceText || !this.rawSourceText.trim()) {
        return '文档未提取成功，请重新上传文件'
      }
      if (this.taskPartialCompleted && this.taskFailedBatchCount > 0) {
        return '失败 ' + this.taskFailedBatchCount + ' 批，点击重试失败批次'
      }
      return '点击重试任务'
    },
    canRetryTask() {
      if (this.isExcelFile(this.fileInfo.fileName)) {
        return false
      }
      if (!this.rawSourceText || !this.rawSourceText.trim()) {
        return false
      }
      return this.taskFailed || (this.taskPartialCompleted && this.taskFailedBatchCount > 0)
    },
    failedBatches() {
      return (this.taskBatches || []).filter(item => item.status === 'FAILED')
    },
    taskProgressTitle() {
      if (this.taskRunning) {
        return '正在后台处理'
      }
      if (this.taskPartialCompleted) {
        return '部分批次完成'
      }
      return '任务处理失败'
    }
  },
  beforeDestroy() {
    this.stopPolling()
  },
  methods: {
    isExcelFile(fileName) {
      const lowerName = (fileName || '').toLowerCase()
      return lowerName.endsWith('.xls') || lowerName.endsWith('.xlsx')
    },
    handleFileChange(file) {
      const rawFile = file.raw
      if (!rawFile) {
        return
      }

      const fileName = rawFile.name || ''
      const lowerName = fileName.toLowerCase()
      if (!lowerName.endsWith('.docx') && !lowerName.endsWith('.txt') && !lowerName.endsWith('.pdf')
          && !lowerName.endsWith('.xls') && !lowerName.endsWith('.xlsx')) {
        this.$message.warning('只支持 docx、txt、pdf、xls、xlsx 文件')
        return
      }

      this.pendingFile = rawFile
      this.importSource = 'file'
      this.fileInfo = { fileName: fileName }
      this.parseForm.text = ''
      this.questions = []
      this.rawSourceText = ''
      this.resetTaskState(false)

      const isExcel = lowerName.endsWith('.xls') || lowerName.endsWith('.xlsx')
      const tip = isExcel
        ? '已选择 Excel：' + fileName + '，将直接解析结构化数据，可点击「开始AI导入」'
        : (this.pendingAnswerFile
          ? '已选择试卷：' + fileName + '，可点击「开始AI导入」'
          : '已选择试卷：' + fileName + '，可选上传答案文档后点击「开始AI导入」')
      this.$message.success(tip)
    },

    handleAnswerFileChange(file) {
      const rawFile = file.raw
      if (!rawFile) {
        return
      }

      const fileName = rawFile.name || ''
      const lowerName = fileName.toLowerCase()
      if (!lowerName.endsWith('.docx') && !lowerName.endsWith('.txt') && !lowerName.endsWith('.pdf')) {
        this.$message.warning('答案文档只支持 docx、txt 和 pdf 文件')
        return
      }

      this.pendingAnswerFile = rawFile
      this.answerFileInfo = { fileName: fileName }
      this.$message.success('已选择答案文档：' + fileName + '，确认后点击「开始AI导入」')
    },

    handleStartImport() {
      this.$refs.parseForm.validate(valid => {
        if (!valid) {
          if (this.pendingFile) {
            this.$message.warning('请先选择题库后再解析文档')
          }
          return
        }

        const hasFile = !!this.pendingFile
        const hasText = this.parseForm.text && this.parseForm.text.trim().length > 0
        if (!hasFile && !hasText) {
          this.$message.warning('请先上传文件或粘贴试题文本')
          return
        }

        if (hasFile) {
          this.importSource = 'file'
        } else {
          this.importSource = 'text'
        }

        this.resetTaskState(false)
        this.taskRunning = true
        this.fileLoading = hasFile
        this.taskProgress = 0
        this.taskMessage = hasFile ? '文件已上传，正在创建导入任务...' : '正在创建导入任务...'
        this.questions = []

        createImportTask({
          file: hasFile ? this.pendingFile : null,
          answerFile: this.pendingAnswerFile || null,
          text: hasText ? this.parseForm.text.trim() : null,
          repoIds: this.parseForm.repoIds,
          level: this.parseForm.level,
          importMode: 'SMART'
        }).then(res => {
          const data = res.data || {}
          this.taskId = data.taskId || ''
          this.taskMessage = hasFile ? '任务已创建，正在提取文档文本...' : '任务已创建，正在后台处理...'
          this.startPolling()
        }, () => {
          this.taskRunning = false
          this.fileLoading = false
          this.taskFailed = true
          this.taskMessage = '创建导入任务失败'
        })
      })
    },

    startPolling() {
      this.stopPolling()
      this.pollTaskStatus()
      this.pollTimer = setInterval(() => {
        this.pollTaskStatus()
      }, TASK_POLL_INTERVAL)
    },

    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },

    pollTaskStatus() {
      if (!this.taskId) {
        return
      }

      getImportTaskStatus(this.taskId).then(res => {
        const data = res.data || {}
        const prevStatus = this.lastPolledStatus
        this.applyTaskStatus(data)
        this.notifyStatusChange(prevStatus, data)

        if (data.status === 'COMPLETED' || data.status === 'PARTIAL_COMPLETED' || data.status === 'FAILED') {
          this.stopPolling()
          this.taskRunning = false
          this.fileLoading = false
          if (data.status === 'COMPLETED') {
            this.taskPartialCompleted = false
            this.taskFailed = false
            this.handleTaskCompleted(data)
          } else if (data.status === 'PARTIAL_COMPLETED') {
            this.taskPartialCompleted = true
            this.taskFailed = false
            this.handleTaskPartialCompleted(data)
          } else {
            this.taskFailed = true
            this.taskPartialCompleted = false
            this.taskHasNormalizedText = !!(data.normalizedText && data.normalizedText.trim())
            if (data.rawText) {
              this.rawSourceText = hideFillMarkersForDisplay(data.rawText)
            }
            if (data.questions && data.questions.length) {
              this.questions = data.questions
            }
            this.$message.error(data.errorMessage || '导入任务处理失败')
          }
        }
      }, () => {
        // 轮询失败时不打断任务，等待下次重试
      })
    },

    handleRetryTask(batchNo) {
      if (!this.taskId || (!this.taskFailed && !this.taskPartialCompleted)) {
        return
      }

      this.retryLoading = true
      retryImportTask(this.taskId, batchNo).then(res => {
        const data = res.data || {}
        this.taskFailed = false
        this.taskPartialCompleted = false
        this.taskRunning = true
        this.retryLoading = false
        this.lastPolledStatus = ''
        this.applyTaskStatus(data)
        const tip = batchNo ? ('正在重试第 ' + batchNo + ' 批') : (data.message || '正在重试失败批次')
        this.$message.info(tip)
        this.startPolling()
      }, () => {
        this.retryLoading = false
      })
    },

    showBatchPreview(row) {
      this.batchPreviewItem = Object.assign({}, row)
      this.batchPreviewDialogVisible = true
    },

    notifyStatusChange(prevStatus, data) {
      const status = data.status || ''
      if (!status || status === prevStatus) {
        return
      }
      this.lastPolledStatus = status

      if (this.importSource === 'file') {
        const fileStageTips = {
          EXTRACTING: this.isExcelFile(this.fileInfo.fileName) ? '正在解析 Excel 文件...' : '正在提取文档文本...',
          NORMALIZING: '文档解析完成，正在 AI 清洗文本',
          PARSING: 'AI 清洗完成，正在解析试题',
          COMPLETED: this.isExcelFile(this.fileInfo.fileName) ? 'Excel 解析完成，请确认试题内容' : 'AI 解析完成，请确认试题内容'
        }
        if (fileStageTips[status] && status !== 'COMPLETED') {
          this.$message.info(fileStageTips[status])
        }
        return
      }

      const textStageTips = {
        NORMALIZING: '正在 AI 清洗文本...',
        PARSING: '正在 AI 解析试题...'
      }
      if (textStageTips[status]) {
        this.$message.info(textStageTips[status])
      }
    },

    applyTaskStatus(data) {
      this.taskProgress = data.progress || 0
      this.taskStatusLabel = data.statusLabel || ''
      this.taskMessage = data.message || data.statusLabel || ''
      this.taskErrorMessage = data.errorMessage || ''
      this.taskTotalBatches = data.totalBatches || 0
      this.taskCompletedBatches = data.completedBatches || 0
      this.taskFailedBatchCount = data.failedBatchCount || 0
      this.taskDeepCleanBatchCount = data.deepCleanBatchCount || 0
      this.taskBatches = data.batches || []
    },

    handleTaskPartialCompleted(data) {
      this.questions = data.questions || []
      this.rawSourceText = hideFillMarkersForDisplay(data.rawText || '')
      if (data.normalizedText) {
        this.parseForm.text = data.normalizedText
      }
      this.$message.warning(data.message || '部分批次解析失败，可预览已成功题目并重试失败批次')
    },

    handleTaskCompleted(data) {
      this.questions = data.questions || []
      this.rawSourceText = hideFillMarkersForDisplay(data.rawText || '')
      if (data.normalizedText) {
        this.parseForm.text = data.normalizedText
      }

      if (this.questions.length === 0) {
        this.$message.warning('未解析出试题，请调整文本后重试')
        return
      }

      if (this.importSource === 'file') {
        const isExcel = this.isExcelFile(this.fileInfo.fileName)
        this.$message.success(isExcel
          ? 'Excel 解析完成，共识别 ' + this.questions.length + ' 题，请确认后入库'
          : '文档解析完成，共识别 ' + this.questions.length + ' 题，请确认后入库')
      } else {
        this.$message.success('AI 解析完成，请确认试题内容')
      }
    },

    resetTaskState(clearLastStatus = true) {
      this.stopPolling()
      this.taskId = ''
      this.taskRunning = false
      this.taskFailed = false
      this.taskPartialCompleted = false
      this.fileLoading = false
      this.taskProgress = 0
      this.taskMessage = ''
      this.taskStatusLabel = ''
      this.taskTotalBatches = 0
      this.taskCompletedBatches = 0
      this.taskFailedBatchCount = 0
      this.taskDeepCleanBatchCount = 0
      this.taskBatches = []
      this.taskHasNormalizedText = false
      this.taskErrorMessage = ''
      if (clearLastStatus) {
        this.lastPolledStatus = ''
      }
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

    clearSource() {
      this.pendingFile = null
      this.pendingAnswerFile = null
      this.importSource = 'text'
      this.fileInfo = { fileName: '' }
      this.answerFileInfo = { fileName: '' }
      this.parseForm.text = ''
      this.rawSourceText = ''
      this.rawTextDialogVisible = false
      this.questions = []
      this.resetTaskState()
    },

    goBack() {
      this.stopPolling()
      this.$router.push({ name: 'ListQu' })
    },

    quTypeLabel(type) {
      return quTypeFilter(String(type)) || '未知题型'
    },

    quTypeTag(type) {
      const map = {
        1: '',
        2: 'warning',
        3: 'info',
        4: 'success',
        5: 'success',
        6: '',
        7: 'warning',
        8: 'danger',
        9: 'info'
      }
      return map[type] || 'danger'
    },

    isObjectiveQuType,
    isFillQuType,
    isFillProgramQuType,
    isNormalFillQuType,
    isSubjectiveQuType,
    isStemCodeQuType,
    isProgramQuType,
    isReadProgramChoiceDisplay,
    isFillProgramChoiceDisplay,
    needsCodeFormatForStem,
    needsCodeFormatForAnswer,
    subjectiveAnswerLabel,
    parseFillProgramContent,
    parseStemCodeContent,
    fillProgramBlankLabel,
    stemCodeSectionLabel,

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

.task-progress-card {
  margin-bottom: 18px;
  border-radius: 6px;
}

.task-progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 600;
}

.task-progress-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-progress-message {
  margin-top: 10px;
  color: #606266;
  font-size: 13px;
}

.task-progress-batch {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

.task-progress-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  gap: 12px;
}

.failed-batch-panel {
  margin-top: 14px;
}

.fill-program-code-block {
  margin-top: 10px;
}

.batch-preview-meta {
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}

.task-retry-hint {
  flex: 1;
  color: #909399;
  font-size: 13px;
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

.answer-upload {
  margin-top: 8px;
}

.file-selected-alert {
  margin-top: 10px;
}

.fill-blank-choice-group + .fill-blank-choice-group {
  margin-top: 14px;
}

.answer-list .fill-blank-choice-group .answer-section-title {
  margin-bottom: 8px;
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

.question-head-main {
  flex: 1;
  min-width: 0;
}

.question-index-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.question-index {
  font-weight: 600;
  color: #303133;
}

.question-content {
  color: #303133;
  font-weight: 500;
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
  flex-wrap: wrap;
  align-items: center;
  min-height: 28px;
  color: #606266;
  gap: 8px;
}

.answer-item.right {
  color: #2f8f46;
  font-weight: 600;
}

.answer-content {
  flex: 1;
  min-width: 120px;
}

.answer-content.formatted-text,
.answer-content >>> .formatted-text {
  flex: 1;
  min-width: 120px;
}

.answer-analysis {
  flex-basis: 100%;
  margin-left: 26px;
  color: #909399;
  font-size: 12px;
  line-height: 20px;
}

.analysis {
  margin-top: 8px;
  padding: 8px 10px;
  color: #606266;
  background: #f7f8fa;
  border-radius: 4px;
  line-height: 22px;
}

.answer-section-title {
  margin-bottom: 6px;
  color: #303133;
  font-weight: 600;
  font-size: 13px;
}

.reference-block {
  margin-top: 8px;
  padding-left: 8px;
}

.reference-text {
  margin-top: 6px;
}

.reference-item {
  margin-top: 4px;
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
