<template>
  <div class="app-container">

    <el-form ref="postForm" :model="postForm" :rules="rules" label-position="left" label-width="150px">

      <el-card>

        <el-form-item label="题目类型 " prop="quType">

          <el-select v-model="postForm.quType" :disabled="quTypeDisabled" class="filter-item" @change="handleTypeChange">
            <el-option
              v-for="item in quTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>

        </el-form-item>

        <el-form-item label="难度等级 " prop="level">

          <el-select v-model="postForm.level" class="filter-item">
            <el-option
              v-for="item in levels"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>

        </el-form-item>

        <el-form-item label="归属题库" prop="repoIds">

          <repo-select v-model="postForm.repoIds" :multi="true" />

        </el-form-item>

        <el-form-item v-if="!isStemCodeSplitType" label="题目内容" prop="content">
          <el-input
            v-model="postForm.content"
            :rows="isProgramType ? 5 : (isLongTextType ? 8 : 4)"
            :placeholder="contentPlaceholder"
            :class="{ 'code-textarea': isFixProgramType && !isStemCodeSplitType }"
            type="textarea"
          />
        </el-form-item>

        <el-form-item label="试题图片">
          <file-upload v-model="postForm.image" accept=".jpg,.jepg,.png" />
        </el-form-item>

        <el-form-item label="整题解析" prop="oriPrice">
          <el-input v-model="postForm.analysis" :precision="1" :max="999999" type="textarea" />
        </el-form-item>

      </el-card>

      <!-- 客观题 / 阅读程序选择题：选项编辑器 -->
      <div v-if="isObjectiveType || isReadProgramChoiceType" class="filter-container" style="margin-top: 25px">

        <el-button class="filter-item" type="primary" icon="el-icon-plus" size="small" plain @click="handleAdd">
          添加
        </el-button>

        <el-table
          :data="postForm.answerList"
          :border="true"
          style="width: 100%;"
        >
          <el-table-column
            label="是否答案"
            width="120"
            align="center"
          >
            <template v-slot="scope">

              <el-checkbox v-model="scope.row.isRight">答案</el-checkbox>

            </template>

          </el-table-column>

          <el-table-column
            v-if="itemImage"
            label="选项图片"
            width="120px"
            align="center"
          >
            <template v-slot="scope">

              <file-upload
                v-model="scope.row.image"
                accept=".jpg,.jepg,.png"
              />

            </template>
          </el-table-column>

          <el-table-column
            label="答案内容"
          >
            <template v-slot="scope">
              <el-input v-model="scope.row.content" type="textarea" />
            </template>
          </el-table-column>

          <el-table-column
            label="答案解析"
          >
            <template v-slot="scope">
              <el-input v-model="scope.row.analysis" type="textarea" />
            </template>
          </el-table-column>

          <el-table-column
            label="操作"
            align="center"
            width="100px"
          >
            <template v-slot="scope">
              <el-button type="danger" icon="el-icon-delete" circle @click="removeItem(scope.$index)" />
            </template>
          </el-table-column>

        </el-table>

      </div>

      <!-- 普通填空题 -->
      <div v-if="isNormalFillType" class="filter-container" style="margin-top: 25px">

        <el-button class="filter-item" type="primary" icon="el-icon-plus" size="small" plain @click="handleAddReference">
          添加参考答案
        </el-button>

        <el-table
          :data="postForm.answerList"
          :border="true"
          style="width: 100%;"
        >
          <el-table-column label="序号" width="80" align="center">
            <template v-slot="scope">{{ scope.$index + 1 }}</template>
          </el-table-column>

          <el-table-column label="参考答案">
            <template v-slot="scope">
              <el-input v-model="scope.row.content" type="textarea" placeholder="请输入参考答案" />
            </template>
          </el-table-column>

          <el-table-column label="操作" align="center" width="100px">
            <template v-slot="scope">
              <el-button type="danger" icon="el-icon-delete" circle @click="removeItem(scope.$index)" />
            </template>
          </el-table-column>

        </el-table>

      </div>

      <!-- 程序填空 / 阅读程序 / 程序改错：题干+代码分栏 -->
      <div v-if="isStemCodeSplitType" class="filter-container" style="margin-top: 25px">

        <el-form-item label="题干说明">
          <el-input
            v-model="stemText"
            :rows="4"
            :placeholder="stemPlaceholder"
            type="textarea"
          />
        </el-form-item>

        <el-form-item :label="codeSectionLabel">
          <el-input
            v-model="codeText"
            :rows="16"
            :placeholder="codePlaceholder"
            type="textarea"
            class="code-textarea"
          />
        </el-form-item>

        <!-- 程序填空：多空答案表 -->
        <template v-if="isFillProgramType">
          <el-button class="filter-item" type="primary" icon="el-icon-plus" size="small" plain @click="handleAddReference">
            添加空位答案
          </el-button>

          <el-table
            :data="postForm.answerList"
            :border="true"
            style="width: 100%;"
          >
            <el-table-column label="空位" width="100" align="center">
              <template v-slot="scope">第{{ scope.$index + 1 }}空</template>
            </el-table-column>

            <el-table-column label="参考答案">
              <template v-slot="scope">
                <el-input v-model="scope.row.content" type="textarea" placeholder="如 a[num] 或 a[num]=b[n]" />
              </template>
            </el-table-column>

            <el-table-column label="操作" align="center" width="100px">
              <template v-slot="scope">
                <el-button type="danger" icon="el-icon-delete" circle @click="removeItem(scope.$index)" />
              </template>
            </el-table-column>

          </el-table>
        </template>

        <!-- 阅读程序 / 程序改错：单条参考答案 -->
        <el-form-item v-else-if="!isReadProgramChoiceType" :label="referenceLabel">
          <el-input
            v-model="referenceText"
            :rows="isFixProgramType ? 16 : 6"
            :placeholder="referencePlaceholder"
            :class="{ 'code-textarea': isFixProgramType }"
            type="textarea"
          />
        </el-form-item>

      </div>

      <!-- 编程题 / 综合应用：大文本参考答案 -->
      <div v-if="isLongTextType" class="filter-container" style="margin-top: 25px">

        <el-form-item :label="referenceLabel">
          <el-input
            v-model="referenceText"
            :rows="isProgramType ? 16 : 10"
            :placeholder="referencePlaceholder"
            :class="{ 'code-textarea': isProgramType || postForm.quType === 8 }"
            type="textarea"
          />
        </el-form-item>

      </div>

      <div style="margin-top: 20px">
        <el-button type="primary" @click="submitForm">保存</el-button>
        <el-button type="info" @click="onCancel">返回</el-button>
      </div>

    </el-form>

  </div>
</template>

<script>
import { fetchDetail, saveData } from '@/api/qu/qu'
import RepoSelect from '@/components/RepoSelect'
import FileUpload from '@/components/FileUpload'
import { QU_TYPE_OPTIONS, isObjectiveQuType, isFillQuType, isFillProgramQuType, isNormalFillQuType, isStemCodeQuType, isReadProgramQuType, isReadProgramChoiceDisplay, isProgramQuType, isFixProgramQuType } from '@/filters'
import { subjectiveAnswerLabel, parseFillProgramContent, stemCodeSectionLabel } from '@/utils/quFormat'

export default {
  name: 'QuDetail',
  components: { FileUpload, RepoSelect },
  data() {
    return {

      quTypeDisabled: false,
      itemImage: true,
      referenceText: '',
      stemText: '',
      codeText: '',

      levels: [
        { value: 1, label: '普通' },
        { value: 2, label: '较难' }
      ],

      quTypes: QU_TYPE_OPTIONS,

      postForm: {
        repoIds: [],
        tagList: [],
        answerList: []
      },
      rules: {
        content: [
          { required: true, message: '题目内容不能为空！' }
        ],

        quType: [
          { required: true, message: '题目类型不能为空！' }
        ],

        level: [
          { required: true, message: '必须选择难度等级！' }
        ],

        repoIds: [
          { required: true, message: '至少要选择一个题库！' }
        ]
      }
    }
  },
  computed: {
    isObjectiveType() {
      return isObjectiveQuType(this.postForm.quType)
    },
    isFillType() {
      return isFillQuType(this.postForm.quType)
    },
    isNormalFillType() {
      return isNormalFillQuType(this.postForm.quType)
    },
    isFillProgramType() {
      return isFillProgramQuType(this.postForm.quType)
    },
    isReadProgramType() {
      return isReadProgramQuType(this.postForm.quType)
    },
    isReadProgramChoiceType() {
      return isReadProgramChoiceDisplay(this.postForm.quType, this.postForm.answerList)
    },
    isFixProgramType() {
      return isFixProgramQuType(this.postForm.quType)
    },
    isStemCodeSplitType() {
      return isStemCodeQuType(this.postForm.quType)
    },
    isLongTextType() {
      return this.postForm.quType === 7 || this.postForm.quType === 9
    },
    isProgramType() {
      return this.postForm.quType === 7
    },
    referenceLabel() {
      return subjectiveAnswerLabel(this.postForm.quType)
    },
    codeSectionLabel() {
      return stemCodeSectionLabel(this.postForm.quType)
    },
    stemPlaceholder() {
      if (this.isReadProgramType) {
        return '请输入题目说明，例如：阅读下列程序，写出运行结果...'
      }
      if (this.isFixProgramType) {
        return '请输入题目说明，例如：以下程序存在错误，请改正...'
      }
      return '请输入题目说明，例如：以下函数把 b 字符串连接到 a 字符串的后面...'
    },
    codePlaceholder() {
      if (this.isReadProgramType) {
        return '请输入完整程序代码，保留换行和缩进'
      }
      if (this.isFixProgramType) {
        return '请输入有错程序代码，保留换行和缩进'
      }
      return '请输入带空位的程序代码骨架，保留换行和缩进'
    },
    contentPlaceholder() {
      if (this.postForm.quType === 7) {
        return '请输入题干描述（题目要求与背景），不要包含程序代码'
      }
      if (this.postForm.quType === 6) {
        return '请输入题干，可包含待阅读的程序代码'
      }
      if (this.postForm.quType === 8) {
        return '请输入题干及有错程序代码'
      }
      return '请输入题目内容'
    },
    referencePlaceholder() {
      if (this.postForm.quType === 7) {
        return '请输入完整参考程序代码'
      }
      if (this.postForm.quType === 8) {
        return '请输入改正后的程序代码'
      }
      if (this.postForm.quType === 6) {
        return '请输入程序运行结果或参考答案'
      }
      return '请输入参考答案或评分要点'
    }
  },
  created() {
    const id = this.$route.params.id
    if (typeof id !== 'undefined') {
      this.quTypeDisabled = true
      this.fetchData(id)
    }
  },
  methods: {

    handleTypeChange(v) {
      this.postForm.answerList = []
      this.referenceText = ''
      this.stemText = ''
      this.codeText = ''
      if (v === 3) {
        this.postForm.answerList.push({ isRight: true, content: '正确', analysis: '' })
        this.postForm.answerList.push({ isRight: false, content: '错误', analysis: '' })
      }

      if (v === 1 || v === 2) {
        this.postForm.answerList.push({ isRight: false, content: '', analysis: '' })
        this.postForm.answerList.push({ isRight: false, content: '', analysis: '' })
        this.postForm.answerList.push({ isRight: false, content: '', analysis: '' })
        this.postForm.answerList.push({ isRight: false, content: '', analysis: '' })
      }

      if (v === 4) {
        this.postForm.answerList.push({ isRight: true, content: '', analysis: '' })
      }

      if (v === 5) {
        this.postForm.answerList.push({ isRight: true, content: '', analysis: '' })
        this.postForm.answerList.push({ isRight: true, content: '', analysis: '' })
      }

      if (v === 6 || v === 8) {
        this.referenceText = ''
      }

      if (v === 7 || v === 9) {
        this.referenceText = ''
      }
    },

    handleAdd() {
      this.postForm.answerList.push({ isRight: false, content: '', analysis: '' })
    },

    handleAddReference() {
      this.postForm.answerList.push({ isRight: true, content: '', analysis: '' })
    },

    removeItem(index) {
      this.postForm.answerList.splice(index, 1)
    },

    syncReferenceTextBeforeSave() {
      if (this.isStemCodeSplitType) {
        const stem = (this.stemText || '').trim()
        const code = (this.codeText || '').trim()
        if (stem && code) {
          this.postForm.content = stem + '\n\n' + code
        } else if (code) {
          this.postForm.content = code
        } else {
          this.postForm.content = stem
        }
        if (this.isFillProgramType) {
          this.postForm.answerList.forEach(item => {
            item.isRight = true
          })
        } else if (!this.isReadProgramChoiceType && this.referenceText && this.referenceText.trim()) {
          this.postForm.answerList = [{ isRight: true, content: this.referenceText.trim(), analysis: '' }]
        } else if (!this.isReadProgramChoiceType) {
          this.postForm.answerList = []
        }
      }
      if (this.isLongTextType) {
        if (this.referenceText && this.referenceText.trim()) {
          this.postForm.answerList = [{ isRight: true, content: this.referenceText.trim(), analysis: '' }]
        } else {
          this.postForm.answerList = []
        }
      }
      if (this.isFillType && !this.isFillProgramType) {
        this.postForm.answerList.forEach(item => {
          item.isRight = true
        })
      }
    },

    loadStemCodeFields() {
      if (!this.isStemCodeSplitType) {
        return
      }
      const parsed = parseFillProgramContent(this.postForm.content)
      this.stemText = parsed.stem
      this.codeText = parsed.code
      if (!this.isFillProgramType && !this.isReadProgramChoiceType && this.postForm.answerList && this.postForm.answerList.length > 0) {
        this.referenceText = this.postForm.answerList.map(item => item.content).join('\n\n')
      }
    },

    fetchData(id) {
      fetchDetail(id).then(response => {
        this.postForm = response.data
        if (this.isLongTextType && this.postForm.answerList && this.postForm.answerList.length > 0) {
          this.referenceText = this.postForm.answerList.map(item => item.content).join('\n\n')
        }
        this.loadStemCodeFields()
      })
    },
    submitForm() {
      this.syncReferenceTextBeforeSave()

      let rightCount = 0

      if (this.isObjectiveType || this.isReadProgramChoiceType) {
        this.postForm.answerList.forEach(function(item) {
          if (item.isRight) {
            rightCount += 1
          }
        })
      }

      if (this.postForm.quType === 1 || this.isReadProgramChoiceType) {
        if (rightCount !== 1) {
          this.$message({
            message: this.isReadProgramChoiceType ? '阅读程序选择题只能有一个正确答案' : '单选题答案只能有一个',
            type: 'warning'
          })

          return
        }
      }

      if (this.postForm.quType === 2) {
        if (rightCount < 1) {
          this.$message({
            message: '多选题至少要有一个正确答案！',
            type: 'warning'
          })

          return
        }
      }

      if (this.postForm.quType === 3) {
        if (rightCount !== 1) {
          this.$message({
            message: '判断题只能有一个正确项！',
            type: 'warning'
          })

          return
        }
      }

      if (this.isNormalFillType || this.isFillProgramType) {
        const validAnswers = (this.postForm.answerList || []).filter(item => item.content && item.content.trim())
        if (validAnswers.length === 0) {
          this.$message({
            message: this.isFillProgramType ? '程序填空题至少要填写一个空的参考答案！' : '填空题至少要填写一个参考答案！',
            type: 'warning'
          })
          return
        }
      }

      if (this.isStemCodeSplitType && !this.codeText.trim()) {
        this.$message({
          message: `${this.codeSectionLabel}不能为空！`,
          type: 'warning'
        })
        return
      }

      if (this.isReadProgramType && !this.isReadProgramChoiceType) {
        if (!this.referenceText || !this.referenceText.trim()) {
          this.$message({
            message: '阅读程序题必须填写运行结果/参考答案！',
            type: 'warning'
          })
          return
        }
      }

      if (this.isFixProgramType) {
        if (!this.referenceText || !this.referenceText.trim()) {
          this.$message({
            message: '程序改错题必须填写改正后程序！',
            type: 'warning'
          })
          return
        }
      }

      if (this.isProgramType) {
        if (!this.referenceText || !this.referenceText.trim()) {
          this.$message({
            message: '编程题必须填写参考程序代码！',
            type: 'warning'
          })
          return
        }
      }

      this.$refs.postForm.validate((valid) => {
        if (!valid) {
          return
        }

        saveData(this.postForm).then(response => {
          this.postForm = response.data
          this.$notify({
            title: '成功',
            message: '试题保存成功！',
            type: 'success',
            duration: 2000
          })

          this.$router.push({ name: 'ListQu' })
        })
      })
    },
    onCancel() {
      this.$router.push({ name: 'ListQu' })
    }

  }
}
</script>

<style scoped>

.code-textarea >>> textarea {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}

</style>
