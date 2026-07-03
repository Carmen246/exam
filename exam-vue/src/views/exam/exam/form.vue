<template>
  <div class="app-container">

    <h3>组卷信息</h3>
    <el-card style="margin-top: 20px">

      <div style="float: right; font-weight: bold; color: #ff0000">试卷总分：{{ postForm.totalScore }}分</div>

      <div>

        <el-button class="filter-item" size="small" type="primary" icon="el-icon-plus" @click="handleAdd">
          添加题库
        </el-button>

        <el-table
          :data="repoList"
          :border="false"
          empty-text="请点击上面的`添加题库`进行设置"
          style="width: 100%; margin-top: 15px"
        >
          <el-table-column
            label="题库"
            width="240"
          >
            <template v-slot="scope">
              <repo-select
                v-model="scope.row.repoId"
                :multi="false"
                :excludes="excludes"
                @change="repoChange($event, scope.row)" />
            </template>

          </el-table-column>
          <el-table-column
            label="题型配置"
            min-width="760"
          >
            <template v-slot="scope">
              <div class="exam-type-list">
                <div class="exam-type-row exam-type-header">
                  <div>题型</div>
                  <div>抽题数量</div>
                  <div>每题分值</div>
                  <div />
                </div>
                <div
                  v-for="(item, typeIndex) in scope.row.types"
                  :key="item.rowId"
                  class="exam-type-row"
                >
                  <el-select v-model="item.quType" placeholder="题型" class="exam-type-select">
                    <el-option
                      v-for="type in quTypes"
                      :key="type.value"
                      :label="type.label"
                      :value="type.value"
                      :disabled="isRepoTypeDisabled(scope.row, type.value, typeIndex)"
                    />
                  </el-select>
                  <div class="exam-count-cell">
                    <el-input-number
                      v-model="item.count"
                      :min="0"
                      :max="getRepoTypeTotal(scope.row, item.quType)"
                      :controls="false"
                      class="exam-number-input"
                    />
                    <span class="exam-count-total">/ {{ getRepoTypeTotal(scope.row, item.quType) }}</span>
                  </div>
                  <el-input-number
                    v-model="item.score"
                    :min="0"
                    :max="100"
                    :controls="false"
                    class="exam-number-input"
                  />
                  <el-button
                    :disabled="scope.row.types.length <= 1"
                    type="danger"
                    icon="el-icon-delete"
                    circle
                    size="mini"
                    @click="removeRepoType(scope.row, typeIndex)"
                  />
                </div>
                <el-button
                  type="text"
                  icon="el-icon-plus"
                  :disabled="scope.row.types.length >= quTypes.length"
                  @click="addRepoType(scope.row)"
                >
                  添加题型
                </el-button>
              </div>
            </template>
          </el-table-column>

          <el-table-column
            label="删除"
            align="center"
            width="80px"
          >
            <template v-slot="scope">
              <el-button type="danger" icon="el-icon-delete" circle @click="removeItem(scope.$index)" />
            </template>
          </el-table-column>

        </el-table>

      </div>

    </el-card>

    <h3>考试配置</h3>
    <el-card style="margin-top: 20px">

      <el-form ref="postForm" :model="postForm" :rules="rules" label-position="left" label-width="120px">

        <el-form-item label="考试名称" prop="title">
          <el-input v-model="postForm.title" />
        </el-form-item>

        <el-form-item label="考试描述" prop="content">
          <el-input v-model="postForm.content" type="textarea" />
        </el-form-item>

        <el-form-item label="总分数" prop="totalScore">
          <el-input-number :value="postForm.totalScore" disabled />
        </el-form-item>

        <el-form-item label="及格分" prop="qualifyScore">
          <el-input-number v-model="postForm.qualifyScore" :max="postForm.totalScore" />
        </el-form-item>

        <el-form-item label="考试时长(分钟)" prop="totalTime">
          <el-input-number v-model="postForm.totalTime" />
        </el-form-item>

        <el-form-item label="是否限时">
          <el-checkbox v-model="postForm.timeLimit" />
        </el-form-item>

        <el-form-item v-if="postForm.timeLimit" label="考试时间" prop="totalTime">

          <el-date-picker
            v-model="dateValues"
            format="yyyy-MM-dd"
            value-format="yyyy-MM-dd"
            type="daterange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />

        </el-form-item>

      </el-form>

    </el-card>

    <h3>权限配置</h3>
    <el-card style="margin-top: 20px;">

      <el-radio-group v-model="postForm.openType" style="margin-bottom: 20px">
        <el-radio :label="1" border>完全公开</el-radio>
        <el-radio :label="2" border>部门开放</el-radio>
      </el-radio-group>

      <el-alert
        v-if="postForm.openType===1"
        title="开放的，任何人都可以进行考试！"
        type="warning"
      />

      <div v-if="postForm.openType===2">
        <el-input
          v-model="filterText"
          placeholder="输入关键字进行过滤"
        />

        <el-tree

          v-loading="treeLoading"
          ref="tree"
          :data="treeData"
          :default-checked-keys="postForm.departIds"
          :props="defaultProps"
          :filter-node-method="filterNode"
          empty-text=" "
          default-expand-all
          show-checkbox
          node-key="id"
          @check-change="handleCheckChange"
        />

      </div>

    </el-card>

    <div style="margin-top: 20px">
      <el-button type="primary" @click="handleSave">保存</el-button>
    </div>

  </div>
</template>

<script>
import { fetchDetail, saveData } from '@/api/exam/exam'
import { fetchTree } from '@/api/sys/depart/depart'
import RepoSelect from '@/components/RepoSelect'
import { QU_TYPE_OPTIONS } from '@/filters'

export default {
  name: 'ExamDetail',
  components: { RepoSelect },
  data() {
    return {

      treeData: [],
      defaultProps: {
        label: 'deptName'
      },
      filterText: '',
      treeLoading: false,
      dateValues: [],
      quTypes: QU_TYPE_OPTIONS,
      // 题库
      repoList: [],
      // 已选择的题库
      excludes: [],
      postForm: {
        // 总分数
        totalScore: 0,
        // 题库列表
        repoList: [],
        // 开放类型
        openType: 1,
        // 考试班级列表
        departIds: []
      },
      rules: {
        title: [
          { required: true, message: '考试名称不能为空！' }
        ],

        content: [
          { required: true, message: '考试名称不能为空！' }
        ],

        open: [
          { required: true, message: '考试权限不能为空！' }
        ],

        totalScore: [
          { required: true, message: '考试分数不能为空！' }
        ],

        qualifyScore: [
          { required: true, message: '及格分不能为空！' }
        ],

        totalTime: [
          { required: true, message: '考试时间不能为空！' }
        ],

        ruleId: [
          { required: true, message: '考试规则不能为空' }
        ],
        password: [
          { required: true, message: '考试口令不能为空！' }
        ]
      }
    }
  },

  watch: {

    filterText(val) {
      this.$refs.tree.filter(val)
    },

    dateValues: {

      handler() {
        this.postForm.startTime = this.dateValues[0]
        this.postForm.endTime = this.dateValues[1]
      }
    },

    // 题库变换
    repoList: {

      handler(val) {
        let totalScore = 0
        this.excludes = []
        for (let i = 0; i<val.length; i++) {
          const item = val[i]
          this.ensureRepoTypes(item)
          for (let j = 0; j < item.types.length; j++) {
            const type = item.types[j]
            if (type.count > 0 && type.score > 0) {
              totalScore += type.count * type.score
            }
          }
          this.excludes.push(item.id)
        }

        // 赋值
        this.postForm.totalScore = totalScore
        this.postForm.repoList = val
        this.$forceUpdate()
      },
      deep: true
    }

  },
  created() {
    const id = this.$route.params.id
    if (typeof id !== undefined) {
      this.fetchData(id)
    }

    fetchTree({}).then(response => {
      this.treeData = response.data
    })
  },
  methods: {

    handleSave() {
      this.$refs.postForm.validate((valid) => {
        if (!valid) {
          return
        }

        if (this.postForm.totalScore === 0) {
          this.$notify({
            title: '提示信息',
            message: '考试规则设置不正确，请确认！',
            type: 'warning',
            duration: 2000
          })

          return
        }

        for (let i = 0; i < this.postForm.repoList.length; i++) {
          const repo = this.postForm.repoList[i]
          if (!repo.repoId) {
            this.$notify({
              title: '提示信息',
              message: '考试题库选择不正确！',
              type: 'warning',
              duration: 2000
            })
            return
          }

          const typeConfigs = this.normalizeRepoPayload(repo)
          if (typeConfigs.length === 0) {
            this.$notify({
              title: '提示信息',
              message: '题库第：[' + (i + 1) + ']项至少需要配置一种题型！',
              type: 'warning',
              duration: 2000
            })
            return
          }

          const invalidType = typeConfigs.find(item => !item.quType)
          if (invalidType) {
            this.$notify({
              title: '提示信息',
              message: '题库第：[' + (i + 1) + ']项存在未选择题型的配置！',
              type: 'warning',
              duration: 2000
            })
            return
          }

          const invalidScore = typeConfigs.find(item => item.score <= 0)
          if (invalidScore) {
            this.$notify({
              title: '提示信息',
              message: '题库第：[' + (i + 1) + ']项存在无效的每题分值！',
              type: 'warning',
              duration: 2000
            })
            return
          }

          const duplicateTypes = typeConfigs
            .map(item => item.quType)
            .filter((value, index, arr) => arr.indexOf(value) !== index)
          if (duplicateTypes.length > 0) {
            this.$notify({
              title: '提示信息',
              message: '题库第：[' + (i + 1) + ']项题型不能重复配置！',
              type: 'warning',
              duration: 2000
            })
            return
          }

          const overLimitType = typeConfigs.find(item => item.count > this.getRepoTypeTotal(repo, item.quType))
          if (overLimitType) {
            this.$notify({
              title: '提示信息',
              message: '题库第：[' + (i + 1) + ']项抽题数量超过题库总量！',
              type: 'warning',
              duration: 2000
            })
            return
          }
        }

        this.$confirm('确实要提交保存吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.submitForm()
        })
      })
    },

    handleCheckChange() {
      const that = this
      // 置空
      this.postForm.departIds = []
      const nodes = this.$refs.tree.getCheckedNodes()
      nodes.forEach(function(item) {
        that.postForm.departIds.push(item.id)
      })
    },

    // 添加子项
    handleAdd() {
      this.repoList.push(this.newRepoConfig())
    },

    removeItem(index) {
      this.repoList.splice(index, 1)
    },

    fetchData(id) {
      fetchDetail(id).then(response => {
        this.postForm = response.data

        if (this.postForm.startTime && this.postForm.endTime) {
          this.dateValues[0] = this.postForm.startTime
          this.dateValues[1] = this.postForm.endTime
        }
        this.repoList = (this.postForm.repoList || []).map(item => this.normalizeRepoConfig(item))
      })
    },

    submitForm() {
      // 校验和处理数据
      this.postForm.repoList = this.repoList.map(item => {
        const row = { ...item }
        const typeConfigs = this.normalizeRepoPayload(row)
        this.syncLegacyTypeFields(row, typeConfigs)
        row.types = typeConfigs
        return row
      })

      saveData(this.postForm).then(() => {
        this.$notify({
          title: '成功',
          message: '考试保存成功！',
          type: 'success',
          duration: 2000
        })

        this.$router.push({ name: 'ListExam' })
      })
    },

    filterNode(value, data) {
      if (!value) return true
      return data.deptName.indexOf(value) !== -1
    },

    newRepoConfig() {
      return {
        id: '',
        rowId: new Date().getTime() + '-' + Math.random(),
        repoId: '',
        radioCount: 0,
        radioScore: 0,
        multiCount: 0,
        multiScore: 0,
        judgeCount: 0,
        judgeScore: 0,
        typeTotals: '{}',
        typeTotalsMap: {},
        types: this.defaultRepoTypes()
      }
    },

    normalizeRepoConfig(repo) {
      const row = {
        ...this.newRepoConfig(),
        ...repo,
        rowId: repo.rowId || repo.id || (new Date().getTime() + '-' + Math.random())
      }
      row.typeTotalsMap = this.parseTypeTotals(row.typeTotals)
      this.ensureRepoTypes(row)
      return row
    },

    defaultRepoTypes() {
      return [
        this.newRepoType(1, 0, 1),
        this.newRepoType(2, 0, 1),
        this.newRepoType(3, 0, 1)
      ]
    },

    newRepoType(quType, count, score) {
      return {
        rowId: new Date().getTime() + '-' + Math.random(),
        quType: quType,
        count: count,
        score: score
      }
    },

    ensureRepoTypes(repo) {
      if (repo.types && repo.types.length > 0) {
        repo.types.forEach(item => {
          if (!item.rowId) {
            this.$set(item, 'rowId', new Date().getTime() + '-' + Math.random())
          }
        })
        return
      }

      const legacyTypes = []
      if (repo.radioCount > 0 || repo.radioScore > 0) {
        legacyTypes.push(this.newRepoType(1, Number(repo.radioCount || 0), Number(repo.radioScore || 0)))
      }
      if (repo.multiCount > 0 || repo.multiScore > 0) {
        legacyTypes.push(this.newRepoType(2, Number(repo.multiCount || 0), Number(repo.multiScore || 0)))
      }
      if (repo.judgeCount > 0 || repo.judgeScore > 0) {
        legacyTypes.push(this.newRepoType(3, Number(repo.judgeCount || 0), Number(repo.judgeScore || 0)))
      }

      this.$set(repo, 'types', legacyTypes.length > 0 ? legacyTypes : this.defaultRepoTypes())
    },

    addRepoType(row) {
      this.ensureRepoTypes(row)
      const usedTypes = row.types.map(item => item.quType)
      const nextType = this.quTypes.find(item => usedTypes.indexOf(item.value) === -1)
      if (!nextType) {
        this.$message.warning('全部题型都已经添加！')
        return
      }
      row.types.push(this.newRepoType(nextType.value, 0, 1))
    },

    removeRepoType(row, index) {
      this.ensureRepoTypes(row)
      if (row.types.length <= 1) {
        return
      }
      row.types.splice(index, 1)
    },

    isRepoTypeDisabled(row, value, currentIndex) {
      this.ensureRepoTypes(row)
      return row.types.some((item, index) => index !== currentIndex && item.quType === value)
    },

    parseTypeTotals(value) {
      if (!value) {
        return {}
      }
      if (typeof value === 'object') {
        return value
      }
      try {
        return JSON.parse(value)
      } catch (e) {
        return {}
      }
    },

    getRepoTypeTotal(row, quType) {
      if (!row || !quType) {
        return 0
      }
      if (!row.typeTotalsMap) {
        this.$set(row, 'typeTotalsMap', this.parseTypeTotals(row.typeTotals))
      }
      const totals = row.typeTotalsMap || {}
      if (totals[quType] !== undefined) {
        return Number(totals[quType] || 0)
      }
      if (quType === 1) {
        return Number(row.totalRadio || 0)
      }
      if (quType === 2) {
        return Number(row.totalMulti || 0)
      }
      if (quType === 3) {
        return Number(row.totalJudge || 0)
      }
      return 0
    },

    normalizeRepoPayload(repo) {
      this.ensureRepoTypes(repo)
      return repo.types
        .filter(item => Number(item.count || 0) > 0)
        .map(item => ({
          quType: item.quType,
          count: Number(item.count || 0),
          score: Number(item.score || 0)
        }))
    },

    syncLegacyTypeFields(repo, typeConfigs) {
      repo.radioCount = 0
      repo.radioScore = 0
      repo.multiCount = 0
      repo.multiScore = 0
      repo.judgeCount = 0
      repo.judgeScore = 0

      typeConfigs.forEach(item => {
        if (item.quType === 1) {
          repo.radioCount = item.count
          repo.radioScore = item.score
        }
        if (item.quType === 2) {
          repo.multiCount = item.count
          repo.multiScore = item.score
        }
        if (item.quType === 3) {
          repo.judgeCount = item.count
          repo.judgeScore = item.score
        }
      })
    },

    repoChange(e, row) {
      // 赋值ID
      row.id = e ? e.id : ''

      if (e != null) {
        row.totalRadio = e.radioCount
        row.totalMulti = e.multiCount
        row.totalJudge = e.judgeCount
        row.typeTotals = e.typeTotals || '{}'
        this.$set(row, 'typeTotalsMap', this.parseTypeTotals(row.typeTotals))
        this.$set(row, 'types', this.defaultRepoTypes())
      } else {
        row.totalRadio = 0
        row.totalMulti = 0
        row.totalJudge = 0
        row.typeTotals = '{}'
        this.$set(row, 'typeTotalsMap', {})
        this.$set(row, 'types', this.defaultRepoTypes())
      }
    }

  }
}
</script>

<style scoped>
.exam-type-list {
  min-width: 680px;
}

.exam-type-row {
  display: grid;
  grid-template-columns: 220px 220px 180px 48px;
  column-gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}

.exam-type-row:last-child {
  margin-bottom: 0;
}

.exam-type-header {
  margin-bottom: 8px;
  color: #909399;
  font-size: 13px;
  line-height: 20px;
}

.exam-type-select {
  width: 100%;
}

.exam-count-cell {
  display: flex;
  align-items: center;
}

.exam-number-input {
  width: 140px;
}

.exam-count-total {
  margin-left: 8px;
  color: #606266;
  white-space: nowrap;
}
</style>

