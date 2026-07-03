<template>
  <div class="app-container">

    <el-card style="margin-top: 20px">

      <div class="qu-content">

        <p>【{{ quData.quType | quTypeFilter() }}】</p>

        <template v-if="isStemCodeQuType(quData.quType)">
          <div class="section-title">题干</div>
          <formatted-text :text="stemCodeParts.stem" class="qu-block" />
          <div v-if="stemCodeParts.code" class="section-title">{{ stemCodeSectionLabel(quData.quType) }}</div>
          <formatted-text v-if="stemCodeParts.code" :text="stemCodeParts.code" :code="true" class="qu-block" />
        </template>

        <template v-else-if="isProgramQuType(quData.quType)">
          <div class="section-title">题干</div>
          <formatted-text :text="quData.content" class="qu-block" />
        </template>

        <template v-else>
          <formatted-text :text="quData.content" :code="needsCodeFormatForStem(quData.quType, quData.content)" class="qu-block" />
        </template>

        <p v-if="quData.image!=null && quData.image!=''">
          <el-image :src="quData.image" style="max-width:80%;" />
        </p>

        <div v-if="quData.quType === 1 || isReadProgramChoiceDisplay(quData.quType, quData.answerList)">
          <el-radio-group v-model="radioValues" readonly>
            <el-radio v-for="an in quData.answerList" :key="an.id" :label="an.id" readonly>{{ an.content }}</el-radio>
          </el-radio-group>
        </div>

        <div v-if="quData.quType === 2">
          <el-checkbox-group v-model="multiValues" readonly>
            <el-checkbox v-for="an in quData.answerList" :key="an.id" :label="an.id">{{ an.content }}</el-checkbox>
          </el-checkbox-group>
        </div>

        <div v-if="quData.quType === 3">
          <el-radio-group v-model="radioValues" readonly>
            <el-radio v-for="an in quData.answerList" :key="an.id" :label="an.id">{{ an.content }}</el-radio>
          </el-radio-group>
        </div>

        <div v-if="isNormalFillQuType(quData.quType) && quData.answerList && quData.answerList.length">
          <div class="section-title">参考答案</div>
          <div v-for="(an, idx) in quData.answerList" :key="idx" class="blank-answer-line">
            {{ idx + 1 }}. <formatted-text :text="an.content" />
          </div>
        </div>

        <div v-if="isFillProgramQuType(quData.quType) && isFillProgramChoiceDisplay(quData.quType, quData.answerList)">
          <div v-for="(an, idx) in quData.answerList" :key="idx" class="blank-choice-group">
            <div v-if="quData.answerList.length > 1" class="section-title">{{ fillProgramBlankLabel(idx) }}</div>
            <div
              v-for="opt in an.optionList"
              :key="opt.letter"
              :class="{ 'blank-choice-right': opt.isRight }"
              class="blank-answer-line"
            >
              {{ opt.letter }}. <formatted-text :text="opt.content" />
              <el-tag v-if="opt.isRight" size="mini" type="success">正确</el-tag>
              <div v-if="opt.analysis" class="option-analysis">答案解析：{{ opt.analysis }}</div>
            </div>
          </div>
        </div>

        <div v-if="isFillProgramQuType(quData.quType) && quData.answerList && quData.answerList.length && !isFillProgramChoiceDisplay(quData.quType, quData.answerList)">
          <div class="section-title">各空参考答案</div>
          <div v-for="(an, idx) in quData.answerList" :key="idx" class="blank-answer-line">
            {{ fillProgramBlankLabel(idx) }}：<formatted-text :text="an.content" />
          </div>
        </div>

        <div v-if="showSubjectiveReference && quData.answerList && quData.answerList.length">
          <div class="section-title">{{ subjectiveAnswerLabel(quData.quType) }}</div>
          <div v-for="(an, idx) in quData.answerList" :key="idx" class="blank-answer-line">
            <formatted-text
              :text="an.content"
              :code="needsCodeFormatForAnswer(quData.quType, an.content)"
            />
          </div>
        </div>

      </div>

    </el-card>

    <el-card class="qu-analysis" style="margin-top: 20px">
      整题解析：
      <formatted-text :text="quData.analysis" />
      <p v-if="!quData.analysis">暂无解析内容！</p>
    </el-card>

    <el-card v-if="showOptionAnalysis" class="qu-analysis" style="margin-top: 20px; margin-bottom: 30px">
      选项解析：
      <template v-if="isFillProgramChoiceDisplay(quData.quType, quData.answerList)">
        <div v-for="(an, idx) in quData.answerList" :key="idx">
          <p v-if="quData.answerList.length > 1" style="color: #303133; font-weight: 600;">{{ fillProgramBlankLabel(idx) }}</p>
          <div v-for="opt in an.optionList" v-if="opt.analysis" :key="opt.letter" class="qu-analysis-line">
            <p style="color: #555;">{{ opt.letter }}. {{ opt.content }}：</p>
            <p style="color: #1890ff;">{{ opt.analysis }}</p>
          </div>
        </div>
      </template>
      <template v-else>
        <div v-for="an in quData.answerList" v-if="an.analysis" :key="an.id" class="qu-analysis-line">
          <p style="color: #555;">{{ an.content }}：</p>
          <p style="color: #1890ff;">{{ an.analysis }}</p>
        </div>
      </template>
      <p v-if="analysisCount === 0">暂无选项解析</p>
    </el-card>

    <el-button type="info" @click="onCancel">返回</el-button>

  </div>
</template>

<script>
import { fetchDetail } from '@/api/qu/qu'
import FormattedText from '@/components/FormattedText'
import {
  isFillProgramQuType,
  isNormalFillQuType,
  isStemCodeQuType,
  isReadProgramQuType,
  isReadProgramChoiceDisplay,
  isFillProgramChoiceDisplay,
  isProgramQuType,
  isFixProgramQuType
} from '@/filters'
import {
  needsCodeFormatForStem,
  needsCodeFormatForAnswer,
  parseFillProgramContent,
  fillProgramBlankLabel,
  subjectiveAnswerLabel,
  stemCodeSectionLabel
} from '@/utils/quFormat'

export default {
  name: 'QuView',
  components: { FormattedText },
  data() {
    return {
      quData: {
        answerList: []
      },
      radioValues: '',
      multiValues: [],
      analysisCount: 0
    }
  },
  computed: {
    stemCodeParts() {
      return parseFillProgramContent(this.quData.content)
    },
    showSubjectiveReference() {
      if (isReadProgramChoiceDisplay(this.quData.quType, this.quData.answerList)) {
        return false
      }
      return isReadProgramQuType(this.quData.quType) ||
        isProgramQuType(this.quData.quType) ||
        isFixProgramQuType(this.quData.quType) ||
        this.quData.quType === 9
    },
    showOptionAnalysis() {
      return this.quData.quType === 1 ||
        this.quData.quType === 2 ||
        isReadProgramChoiceDisplay(this.quData.quType, this.quData.answerList) ||
        isFillProgramChoiceDisplay(this.quData.quType, this.quData.answerList)
    }
  },
  created() {
    const id = this.$route.params.id
    if (typeof id !== 'undefined') {
      this.fetchData(id)
    }
  },
  methods: {
    isFillProgramQuType,
    isFillProgramChoiceDisplay,
    isNormalFillQuType,
    isStemCodeQuType,
    isProgramQuType,
    needsCodeFormatForStem,
    needsCodeFormatForAnswer,
    fillProgramBlankLabel,
    subjectiveAnswerLabel,
    stemCodeSectionLabel,

    fetchData(id) {
      fetchDetail(id).then(response => {
        this.quData = response.data
        this.analysisCount = 0

        this.quData.answerList.forEach((an) => {
          if (an.analysis) {
            this.analysisCount += 1
          }
          if (an.optionList && an.optionList.length) {
            an.optionList.forEach((opt) => {
              if (opt.analysis) {
                this.analysisCount += 1
              }
            })
          }
          if (an.isRight) {
            if (this.quData.quType === 1 || this.quData.quType === 3 ||
              isReadProgramChoiceDisplay(this.quData.quType, this.quData.answerList)) {
              this.radioValues = an.id
            } else if (this.quData.quType === 2) {
              this.multiValues.push(an.id)
            }
          }
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

  .qu-content{
    padding-bottom: 10px;
  }

  .section-title {
    margin: 12px 0 6px;
    font-weight: 600;
    color: #303133;
  }

  .qu-block {
    margin-bottom: 8px;
  }

  .blank-answer-line {
    margin: 6px 0;
    color: #606266;
  }

  .blank-choice-right {
    color: #67c23a;
    font-weight: 600;
  }

  .option-analysis {
    width: 100%;
    margin-top: 4px;
    color: #909399;
    font-size: 13px;
  }

  .blank-choice-group + .blank-choice-group {
    margin-top: 16px;
  }

  .qu-analysis p{
    color: #555; font-size: 14px
  }
  .qu-analysis-line{
    margin-top: 20px; border-bottom: #eee 1px solid
  }

  .el-checkbox-group label,.el-radio-group label{
    width: 100%;
  }

</style>
