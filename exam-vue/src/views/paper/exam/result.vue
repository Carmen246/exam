<template>
  <div class="app-container">

    <h2 class="text-center">{{ paperData.title }}</h2>
    <p class="text-center" style="color: #666">{{ paperData.createTime }}</p>

    <el-row :gutter="24" style="margin-top: 50px">

      <el-col :span="8" class="text-center">
        考生姓名：{{ paperData.userId_dictText }}
      </el-col>

      <el-col :span="8" class="text-center">
        考试用时：{{ paperData.userTime }}分钟
      </el-col>

      <el-col :span="8" class="text-center">
        考试得分：{{ paperData.userScore }}
      </el-col>

    </el-row>

    <el-card style="margin-top: 20px">

      <div v-for="item in paperData.quList" :key="item.id" class="qu-content">

        <template v-if="isStemCodeQuType(item.quType)">
          <p class="qu-title-line">{{ item.sort + 1 }}.（得分：{{ item.actualScore }}）</p>
          <div class="section-title">题干</div>
          <formatted-text :text="parseStemCodeContent(item.content).stem" />
          <div v-if="parseStemCodeContent(item.content).code" class="section-title">{{ stemCodeSectionLabel(item.quType) }}</div>
          <formatted-text
            v-if="parseStemCodeContent(item.content).code"
            :text="parseStemCodeContent(item.content).code"
            :code="true"
          />
        </template>
        <template v-else-if="isProgramQuType(item.quType)">
          <p class="qu-title-line">{{ item.sort + 1 }}.（得分：{{ item.actualScore }}）</p>
          <div class="section-title">题干</div>
          <formatted-text :text="item.content" />
        </template>
        <p v-else>{{ item.sort + 1 }}.{{ item.content }}（得分：{{ item.actualScore }}）</p>
        <p v-if="item.image!=null && item.image!=''">
          <el-image :src="item.image" style="max-width:100%;" />
        </p>
        <div v-if="item.quType === 1 || item.quType===3">
          <el-radio-group v-model="radioValues[item.id]">
            <el-radio v-for="an in item.answerList" :label="an.id">
              {{ an.abc }}.{{ an.content }}
              <div v-if="an.image!=null && an.image!=''" style="clear: both">
                <el-image :src="an.image" style="max-width:100%;" />
              </div>
            </el-radio>
          </el-radio-group>

          <el-row :gutter="24">

            <el-col :span="12" style="color: #24da70">
              正确答案：{{ radioRights[item.id] }}
            </el-col>

            <el-col v-if="!item.answered" :span="12" style="text-align: right; color: #ff0000;">
              答题结果：未答
            </el-col>

            <el-col v-if="item.answered && !item.isRight" :span="12" style="text-align: right; color: #ff0000;">
              答题结果：{{ myRadio[item.id] }}
            </el-col>

            <el-col v-if="item.answered && item.isRight" :span="12" style="text-align: right; color: #24da70;">
              答题结果：{{ myRadio[item.id] }}
            </el-col>

          </el-row>

        </div>

        <div v-if="item.quType >= 4 && item.quType <= 9">

          <el-row :gutter="24">

            <el-col :span="12">
              <template v-if="item.quType >= 6 && item.quType <= 8 && item.answer && looksLikeCode(item.answer)">
                我的回答：
                <formatted-text :text="item.answer" :code="true" />
              </template>
              <template v-else>
                我的回答：{{ item.answer || '未作答' }}
              </template>
            </el-col>

            <el-col :span="12" style="text-align: right; color: #909399;">
              <span v-if="paperData.hasSaq && paperData.state === 1">待批阅</span>
              <span v-else-if="item.actualScore != null">得分：{{ item.actualScore }}</span>
            </el-col>

          </el-row>

          <div v-if="item.answerList && item.answerList.length" style="margin-top: 10px; color: #24da70">
            {{ subjectiveAnswerLabel(item.quType) }}：
            <div v-for="(an, idx) in item.answerList" :key="idx" style="margin-top: 4px">
              <span v-if="isFillProgramQuType(item.quType)">{{ fillProgramBlankLabel(idx) }}：</span>
              <span v-else-if="item.quType === 4">{{ idx + 1 }}. </span>
              <formatted-text
                :text="an.content"
                :code="needsCodeFormatForAnswer(item.quType, an.content)"
              />
            </div>
          </div>

        </div>

        <div v-if="item.quType === 2">
          <el-checkbox-group v-model="multiValues[item.id]">
            <el-checkbox v-for="an in item.answerList" :key="an.id" :label="an.id">{{ an.abc }}.{{ an.content }}
              <div v-if="an.image!=null && an.image!=''" style="clear: both">
                <el-image :src="an.image" style="max-width:100%;" />
              </div>
            </el-checkbox>
          </el-checkbox-group>

          <el-row :gutter="24">

            <el-col :span="12" style="color: #24da70">
              正确答案：{{ multiRights[item.id].join(',') }}
            </el-col>

            <el-col v-if="!item.answered" :span="12" style="text-align: right; color: #ff0000;">
              答题结果：未答
            </el-col>

            <el-col v-if="item.answered && !item.isRight" :span="12" style="text-align: right; color: #ff0000;">
              答题结果：{{ myMulti[item.id].join(',') }}
            </el-col>

            <el-col v-if="item.answered && item.isRight" :span="12" style="text-align: right; color: #24da70;">
              答题结果：{{ myMulti[item.id].join(',') }}
            </el-col>

          </el-row>
        </div>

      </div>

    </el-card>

  </div>
</template>

<script>

import { paperResult } from '@/api/paper/exam'
import FormattedText from '@/components/FormattedText'
import { isFillProgramQuType, isStemCodeQuType, isProgramQuType } from '@/filters'
import {
  parseStemCodeContent,
  fillProgramBlankLabel,
  subjectiveAnswerLabel,
  needsCodeFormatForAnswer,
  stemCodeSectionLabel,
  looksLikeCode
} from '@/utils/quFormat'

export default {
  components: { FormattedText },
  data() {
    return {
      // 试卷ID
      paperId: '',
      paperData: {
        quList: []
      },
      radioValues: {},
      multiValues: {},
      radioRights: {},
      multiRights: {},
      myRadio: {},
      myMulti: {}
    }
  },
  created() {
    const id = this.$route.params.id
    if (typeof id !== 'undefined') {
      this.paperId = id
      this.fetchData(id)
    }
  },
  methods: {
    isFillProgramQuType,
    isStemCodeQuType,
    isProgramQuType,
    parseStemCodeContent,
    fillProgramBlankLabel,
    subjectiveAnswerLabel,
    needsCodeFormatForAnswer,
    stemCodeSectionLabel,
    looksLikeCode,

    fetchData(id) {
      const params = { id: id }
      paperResult(params).then(response => {
        // 试卷内容
        this.paperData = response.data

        // 填充该题目的答案
        this.paperData.quList.forEach((item) => {
          let radioValue = ''
          let radioRight = ''
          let myRadio = ''
          const multiValue = []
          const multiRight = []
          const myMulti = []

          item.answerList.forEach((an) => {
            // 用户选定的
            if (an.checked) {
              if (item.quType === 1 || item.quType === 3) {
                radioValue = an.id
                myRadio = an.abc
              } else {
                multiValue.push(an.id)
                myMulti.push(an.abc)
              }
            }

            // 正确答案
            if (an.isRight) {
              if (item.quType === 1 || item.quType === 3) {
                radioRight = an.abc
              } else {
                multiRight.push(an.abc)
              }
            }
          })

          this.multiValues[item.id] = multiValue
          this.radioValues[item.id] = radioValue

          this.radioRights[item.id] = radioRight
          this.multiRights[item.id] = multiRight

          this.myRadio[item.id] = myRadio
          this.myMulti[item.id] = myMulti
        })

        console.log(this.multiValues)
        console.log(this.radioValues)
      })
    }
  }
}
</script>

<style scoped>

  .qu-content{

    border-bottom: #eee 1px solid;
    padding-bottom: 10px;

  }

  .qu-content div{
    line-height: 30px;
  }

  .el-checkbox-group label,.el-radio-group label{
    width: 100%;
  }

  .card-title{
    background: #eee;
    line-height: 35px;
    text-align: center;
    font-size: 14px;
  }
  .card-line{
    padding-left: 10px
  }
  .card-line span {
    cursor: pointer;
    margin: 2px;
  }

  .qu-title-line {
    font-weight: 600;
    margin-bottom: 4px;
  }

  .section-title {
    margin: 10px 0 6px;
    font-weight: 600;
    color: #303133;
  }

</style>

