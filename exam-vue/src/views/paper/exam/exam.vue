<template>

  <div class="app-container">

    <el-row :gutter="24">

      <el-col :span="24">
        <el-card style="margin-bottom: 10px">

          距离考试结束还有：
          <exam-timer v-model="paperData.leftSeconds" @timeout="doHandler()" />

          <el-button :loading="loading" style="float: right; margin-top: -10px" type="primary" icon="el-icon-plus" @click="handHandExam()">
            {{ handleText }}
          </el-button>

        </el-card>
      </el-col>

      <el-col :span="5" :xs="24" style="margin-bottom: 10px">

        <el-card class="content-h">

          <p class="card-title">答题卡</p>
          <el-row :gutter="24" class="card-line" style="padding-left: 10px">
            <el-tag type="info">未作答</el-tag>
            <el-tag type="success">已作答</el-tag>
          </el-row>

          <div v-if="paperData.radioList!==undefined && paperData.radioList.length > 0">
            <p class="card-title">单选题</p>
            <el-row :gutter="24" class="card-line">
              <el-tag v-for="item in paperData.radioList" :type="cardItemClass(item.answered, item.quId)" @click="handSave(item)"> {{ item.sort+1 }}</el-tag>
            </el-row>
          </div>

          <div v-if="paperData.multiList!==undefined && paperData.multiList.length > 0">
            <p class="card-title">多选题</p>
            <el-row :gutter="24" class="card-line">
              <el-tag v-for="item in paperData.multiList" :type="cardItemClass(item.answered, item.quId)" @click="handSave(item)">{{ item.sort+1 }}</el-tag>
            </el-row>
          </div>

          <div v-if="paperData.judgeList!==undefined && paperData.judgeList.length > 0">
            <p class="card-title">判断题</p>
            <el-row :gutter="24" class="card-line">
              <el-tag v-for="item in paperData.judgeList" :type="cardItemClass(item.answered, item.quId)" @click="handSave(item)">{{ item.sort+1 }}</el-tag>
            </el-row>
          </div>

          <div v-if="paperData.subjList!==undefined && paperData.subjList.length > 0">
            <p class="card-title">主观题</p>
            <el-row :gutter="24" class="card-line">
              <el-tag v-for="item in paperData.subjList" :key="item.quId" :type="cardItemClass(item.answered, item.quId)" @click="handSave(item)">{{ item.sort+1 }}</el-tag>
            </el-row>
          </div>

        </el-card>

      </el-col>

      <el-col :span="19" :xs="24">

        <el-card class="qu-content content-h">
          <div v-if="quData.content" class="qu-title-line">{{ quData.sort + 1 }}.</div>
          <template v-if="isStemCodeQuType(quData.quType)">
            <div class="section-title">题干</div>
            <formatted-text :text="stemCodeParts.stem" class="qu-stem" />
            <div v-if="stemCodeParts.code" class="section-title">{{ stemCodeSectionLabel(quData.quType) }}</div>
            <formatted-text v-if="stemCodeParts.code" :text="stemCodeParts.code" :code="true" class="qu-stem" />
          </template>
          <template v-else-if="isProgramQuType(quData.quType)">
            <div class="section-title">题干</div>
            <formatted-text :text="quData.content" class="qu-stem" />
          </template>
          <formatted-text
            v-else-if="quData.content"
            :text="quData.content"
            :code="needsCodeFormatForStem(quData.quType, quData.content)"
            class="qu-stem"
          />
          <p v-if="quData.image!=null && quData.image!=''">
            <el-image :src="quData.image" style="max-width:100%;" />
          </p>
          <div v-if="quData.quType === 1 || quData.quType===3">
            <el-radio-group v-model="radioValue">
              <el-radio v-for="item in quData.answerList" :label="item.id">{{ item.abc }}.{{ item.content }}
                <div v-if="item.image!=null && item.image!=''" style="clear: both">
                  <el-image :src="item.image" style="max-width:100%;" />
                </div>
              </el-radio>
            </el-radio-group>
          </div>

          <div v-if="quData.quType === 2">

            <el-checkbox-group v-model="multiValue">
              <el-checkbox v-for="item in quData.answerList" :key="item.id" :label="item.id">{{ item.abc }}.{{ item.content }}
                <div v-if="item.image!=null && item.image!=''" style="clear: both">
                  <el-image :src="item.image" style="max-width:100%;" />
                </div>
              </el-checkbox>
            </el-checkbox-group>

          </div>

          <div v-if="isSubjectiveQuType(quData.quType)">
            <el-input
              v-model="subjValue"
              type="textarea"
              :rows="isFillProgramQuType(quData.quType) ? 6 : (quData.quType >= 6 ? 12 : 4)"
              :placeholder="subjectiveAnswerPlaceholder"
            />
            <p v-if="isFillProgramQuType(quData.quType)" class="subj-tip">请按空位顺序填写答案，每行一空；提交后需人工批阅</p>
            <p v-else-if="isReadProgramQuType(quData.quType)" class="subj-tip">请写出程序运行结果；提交后需人工批阅</p>
            <p v-else-if="isProgramQuType(quData.quType)" class="subj-tip">请编写完整程序代码；提交后需人工批阅</p>
            <p v-else-if="isFixProgramQuType(quData.quType)" class="subj-tip">请写出改正后的完整程序；提交后需人工批阅</p>
            <p v-else-if="paperData.hasSaq" class="subj-tip">本题为人工批阅题，提交后需等待阅卷</p>
          </div>

          <div style="margin-top: 20px">
            <el-button v-if="showPrevious" type="primary" icon="el-icon-back" @click="handPrevious()">
              上一题
            </el-button>

            <el-button v-if="showNext" type="warning" icon="el-icon-right" @click="handNext()">
              下一题
            </el-button>

          </div>

        </el-card>

      </el-col>

    </el-row>
  </div>

</template>

<script>
import { paperDetail, quDetail, handExam, fillAnswer } from '@/api/paper/exam'
import { Loading } from 'element-ui'
import ExamTimer from '@/views/paper/exam/components/ExamTimer'
import FormattedText from '@/components/FormattedText'
import { isSubjectiveQuType, isFillProgramQuType, isStemCodeQuType, isProgramQuType, isReadProgramQuType, isFixProgramQuType } from '@/filters'
import { needsCodeFormatForStem, parseFillProgramContent, subjectiveAnswerPlaceholder as getSubjectiveAnswerPlaceholder, stemCodeSectionLabel } from '@/utils/quFormat'

export default {
  name: 'ExamProcess',
  components: { ExamTimer, FormattedText },
  data() {
    return {
      // 全屏/不全屏
      isFullscreen: false,
      showPrevious: false,
      showNext: true,
      loading: false,
      handleText: '交卷',
      pageLoading: false,
      // 试卷ID
      paperId: '',
      // 当前答题卡
      cardItem: {},
      allItem: [],
      // 当前题目内容
      quData: {
        answerList: []
      },
      // 试卷信息
      paperData: {
        leftSeconds: 99999,
        radioList: [],
        multiList: [],
        judgeList: [],
        subjList: [],
        hasSaq: false
      },
      // 单选选定值
      radioValue: '',
      // 多选选定值
      multiValue: [],
      // 主观题答案
      subjValue: '',
      // 已答ID
      answeredIds: []
    }
  },
  created() {
    const id = this.$route.params.id
    if (typeof id !== 'undefined') {
      this.paperId = id
      this.fetchData(id)
    }
  },

  computed: {
    stemCodeParts() {
      return parseFillProgramContent(this.quData.content)
    },
    subjectiveAnswerPlaceholder() {
      return getSubjectiveAnswerPlaceholder(this.quData.quType)
    }
  },

  methods: {

    // 答题卡样式
    cardItemClass(answered, quId) {
      if (quId === this.cardItem.quId) {
        return 'warning'
      }

      if (answered) {
        return 'success'
      }

      if (!answered) {
        return 'info'
      }
    },

    /**
     * 统计有多少题没答的
     * @returns {number}
     */
    countNotAnswered() {
      let notAnswered = 0

      this.paperData.radioList.forEach(function(item) {
        if (!item.answered) {
          notAnswered += 1
        }
      })

      this.paperData.multiList.forEach(function(item) {
        if (!item.answered) {
          notAnswered += 1
        }
      })

      this.paperData.judgeList.forEach(function(item) {
        if (!item.answered) {
          notAnswered += 1
        }
      })

      if (this.paperData.subjList) {
        this.paperData.subjList.forEach(function(item) {
          if (!item.answered) {
            notAnswered += 1
          }
        })
      }

      return notAnswered
    },

    /**
     * 下一题
     */
    handNext() {
      const index = this.cardItem.sort + 1
      this.handSave(this.allItem[index])
    },

    /**
     * 上一题
     */
    handPrevious() {
      const index = this.cardItem.sort - 1
      this.handSave(this.allItem[index])
    },

    doHandler() {
      this.handleText = '正在交卷，请等待...'
      this.loading = true

      const params = { id: this.paperId }
      handExam(params).then(() => {
        this.$message({
          message: '试卷提交成功，即将进入试卷详情！',
          type: 'success'
        })

        this.$router.push({ name: 'ShowExam', params: { id: this.paperId }})
      })
    },

    // 交卷操作
    handHandExam() {
      const that = this

      // 交卷保存答案
      this.handSave(this.cardItem, function() {
        const notAnswered = that.countNotAnswered()

        let msg = '确认要交卷吗？'

        if (notAnswered > 0) {
          msg = '您还有' + notAnswered + '题未作答，确认要交卷吗?'
        }

        that.$confirm(msg, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          that.doHandler()
        }).catch(() => {
          that.$message({
            type: 'info',
            message: '交卷已取消，您可以继续作答！'
          })
        })
      })
    },

    isSubjectiveQuType,
    isFillProgramQuType,
    isStemCodeQuType,
    isProgramQuType,
    isReadProgramQuType,
    isFixProgramQuType,
    needsCodeFormatForStem,
    stemCodeSectionLabel,

    // 保存答案
    handSave(item, callback) {
      if (item.id === this.allItem[0].id) {
        this.showPrevious = false
      } else {
        this.showPrevious = true
      }

      // 最后一个索引
      const last = this.allItem.length - 1

      if (item.id === this.allItem[last].id) {
        this.showNext = false
      } else {
        this.showNext = true
      }

      let params
      if (isSubjectiveQuType(this.quData.quType)) {
        params = {
          paperId: this.paperId,
          quId: this.cardItem.quId,
          answers: [],
          answer: this.subjValue || ''
        }
      } else {
        const answers = [...this.multiValue]
        if (this.radioValue !== '') {
          answers.push(this.radioValue)
        }
        params = { paperId: this.paperId, quId: this.cardItem.quId, answers: answers, answer: '' }
      }

      fillAnswer(params).then(() => {
        if (isSubjectiveQuType(this.quData.quType)) {
          if (this.subjValue && this.subjValue.trim()) {
            this.cardItem.answered = true
          }
        } else if (params.answers.length > 0) {
          this.cardItem.answered = true
        }

        // 最后一个动作，交卷
        if (callback) {
          callback()
        }

        // 查找详情
        this.fetchQuData(item)
      })
    },

    // 试卷详情
    fetchQuData(item) {
      // 打开
      const loading = Loading.service({
        text: '拼命加载中',
        background: 'rgba(0, 0, 0, 0.7)'
      })

      // 获得详情
      this.cardItem = item

      // 查找下个详情
      const params = { paperId: this.paperId, quId: item.quId }
      quDetail(params).then(response => {
        console.log(response)
        this.quData = response.data
        this.radioValue = ''
        this.multiValue = []
        this.subjValue = this.quData.answer || ''

        // 填充该题目的答案
        if (this.quData.answerList) {
          this.quData.answerList.forEach((item) => {
            if ((this.quData.quType === 1 || this.quData.quType === 3) && item.checked) {
              this.radioValue = item.id
            }

            if (this.quData.quType === 2 && item.checked) {
              this.multiValue.push(item.id)
            }
          })
        }

        // 关闭详情
        loading.close()
      })
    },

    // 试卷详情
    fetchData(id) {
      const params = { id: id }
      paperDetail(params).then(response => {
        // 试卷内容
        this.paperData = response.data

        // 获得第一题内容
        if (this.paperData.radioList && this.paperData.radioList.length>0) {
          this.cardItem = this.paperData.radioList[0]
        } else if (this.paperData.multiList && this.paperData.multiList.length>0) {
          this.cardItem = this.paperData.multiList[0]
        } else if (this.paperData.judgeList && this.paperData.judgeList.length>0) {
          this.cardItem = this.paperData.judgeList[0]
        } else if (this.paperData.subjList && this.paperData.subjList.length>0) {
          this.cardItem = this.paperData.subjList[0]
        }

        const that = this

        this.paperData.radioList.forEach(function(item) {
          that.allItem.push(item)
        })

        this.paperData.multiList.forEach(function(item) {
          that.allItem.push(item)
        })

        this.paperData.judgeList.forEach(function(item) {
          that.allItem.push(item)
        })

        if (this.paperData.subjList) {
          this.paperData.subjList.forEach(function(item) {
            that.allItem.push(item)
          })
        }

        if (this.cardItem && this.cardItem.quId) {
          this.fetchQuData(this.cardItem)
        }
      })
    }

  }
}
</script>

<style scoped>

  .qu-content div{
    line-height: 30px;
    width: 100%;
  }

  .el-checkbox-group label,.el-radio-group label{
    width: 100%;
  }

  .content-h{
    height: calc(100vh - 110px);
    overflow-y: auto;
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

  ::v-deep
  .el-radio, .el-checkbox{
    padding: 9px 20px 9px 10px;
    border-radius: 4px;
    border: 1px solid #dcdfe6;
    margin-bottom: 10px;
    width: 100%;
  }

  .is-checked{
    border: #409eff 1px solid;
  }

  .el-radio img, .el-checkbox img{
    max-width: 200px;
    max-height: 200px;
    border: #dcdfe6 1px dotted;
  }

  ::v-deep
  .el-checkbox__inner {
    display: none;
  }

  ::v-deep
  .el-radio__inner{
    display: none;
  }

  ::v-deep
  .el-checkbox__label{
    line-height: 30px;
  }

  ::v-deep
  .el-radio__label{
    line-height: 30px;
  }

  .subj-tip {
    margin-top: 8px;
    color: #909399;
    font-size: 13px;
  }

  .qu-stem {
    margin-bottom: 12px;
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

