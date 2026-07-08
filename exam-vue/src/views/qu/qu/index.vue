<template>

  <div>

    <data-table
      ref="pagingTable"
      :options="options"
      :list-query="listQuery"
      @multi-actions="handleMultiAction"
    >
      <template #filter-content>

        <el-row>
          <el-col :span="24">

            <el-select v-model="listQuery.params.quType" class="filter-item" clearable>
              <el-option
                v-for="item in quTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>

            <repo-select v-model="listQuery.params.repoIds" :multi="true" />

            <el-input v-model="listQuery.params.content" placeholder="题目内容" style="width: 200px;" class="filter-item" />

            <el-button-group class="filter-item" style="float:  right">
              <el-button v-if="isSa" size="mini" icon="el-icon-magic-stick" @click="showAiImport">AI导入</el-button>
              <el-button size="mini" icon="el-icon-document" @click="showWordExport">生成试卷Word</el-button>
              <el-button size="mini" icon="el-icon-upload2" @click="showImport">导入</el-button>
              <el-button size="mini" icon="el-icon-download" @click="exportExcel">导出</el-button>
            </el-button-group>

          </el-col>
        </el-row>

      </template>

      <template #data-columns>

        <el-table-column
          label="题目类型"
          align="center"
          width="100px"
        >
          <template v-slot="scope">
            {{ scope.row.quType | quTypeFilter() }}
          </template>
        </el-table-column>

        <el-table-column
          label="题目内容"
          show-overflow-tooltip
        >
          <template v-slot="scope">
            <router-link :to="{ name: 'UpdateQu', params:{ id: scope.row.id}}">
              {{ scope.row.content }}
            </router-link>
          </template>
        </el-table-column>

        <el-table-column
          label="创建时间"
          align="center"
          prop="createTime"
          width="180px"
        />

      </template>

    </data-table>

    <el-dialog
      :title="dialogTitle"
      :visible.sync="dialogVisible"
      width="30%"
    >

      <el-form label-position="left" label-width="100px">

        <el-form-item label="操作题库" prop="repoIds">
          <repo-select v-model="dialogRepos" :multi="true" />
        </el-form-item>

        <el-row>
          <el-button type="primary" @click="handlerRepoAction">保存</el-button>
        </el-row>

      </el-form>

    </el-dialog>

    <el-dialog
      :visible.sync="importVisible"
      title="导入试题"
      width="30%"
    >

      <el-row>
        <el-button type="primary" @click="chooseFile">上传导入</el-button>
        <el-button type="warning" @click="downloadTemplate">下载导入模板</el-button>
        <input ref="upFile" class="file" name="file" type="file" style="display: none" @change="doImport">
      </el-row>

    </el-dialog>

    <el-dialog
      :visible.sync="wordExportVisible"
      title="生成试卷Word"
      width="640px"
    >
      <el-form
        ref="wordExportForm"
        :model="wordExportForm"
        :rules="wordExportRules"
        label-position="left"
        label-width="110px"
      >
        <el-form-item label="试卷标题" prop="title">
          <el-input v-model="wordExportForm.title" placeholder="请输入试卷标题" />
        </el-form-item>

        <el-form-item label="抽题题库" prop="repoIds">
          <repo-select v-model="wordExportForm.repoIds" :multi="true" />
        </el-form-item>

        <el-form-item label="题型配置">
          <div class="word-export-type-list">
            <div class="word-export-row word-export-header-row">
              <div class="word-export-header">题型</div>
              <div class="word-export-header">抽题数量</div>
              <div class="word-export-header">每题分值</div>
              <div />
            </div>

            <div
              v-for="(item, index) in wordExportForm.types"
              :key="item.rowId"
              class="word-export-row"
            >
              <el-select v-model="item.quType" class="word-export-type-select" placeholder="题型">
                <el-option
                  v-for="type in quTypes"
                  :key="type.value"
                  :label="type.label"
                  :value="type.value"
                  :disabled="isWordExportTypeDisabled(type.value, index)"
                />
              </el-select>
              <el-input-number
                v-model="item.count"
                :min="0"
                :max="200"
                :controls="false"
                class="word-export-number"
              />
              <el-input-number
                v-model="item.score"
                :min="0"
                :max="100"
                :controls="false"
                class="word-export-number"
              />
              <el-button
                :disabled="wordExportForm.types.length <= 1"
                type="danger"
                icon="el-icon-delete"
                circle
                size="mini"
                @click="removeWordExportType(index)"
              />
            </div>

            <el-button
              type="text"
              icon="el-icon-plus"
              :disabled="wordExportForm.types.length >= quTypes.length"
              @click="addWordExportType"
            >
              添加题型
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="考试时长">
          <el-input-number v-model="wordExportForm.totalTime" :min="0" :max="600" controls-position="right" />
          <span class="word-export-unit">分钟</span>
        </el-form-item>

        <el-form-item label="导出内容">
          <el-checkbox v-model="wordExportForm.includeAnswer">参考答案</el-checkbox>
        </el-form-item>
      </el-form>

      <div slot="footer" class="dialog-footer">
        <el-button @click="wordExportVisible = false">取消</el-button>
        <el-button :loading="wordExportLoading" type="primary" @click="exportWord">生成并下载</el-button>
      </div>
    </el-dialog>

  </div>

</template>

<script>
import DataTable from '@/components/DataTable'
import RepoSelect from '@/components/RepoSelect'
import { batchAction } from '@/api/qu/repo'
import { exportExcel, exportRandomWord, importExcel, importTemplate } from '@/api/qu/qu'
import { QU_TYPE_OPTIONS } from '@/filters'

export default {
  name: 'QuList',
  components: { RepoSelect, DataTable },
  data() {
    return {

      dialogTitle: '加入题库',
      dialogVisible: false,
      importVisible: false,
      wordExportVisible: false,
      wordExportLoading: false,
      dialogRepos: [],
      dialogQuIds: [],
      dialogFlag: false,

      wordExportForm: {
        title: '随机试卷',
        repoIds: [],
        types: [
          { rowId: 'radio', quType: 1, count: 5, score: 2 },
          { rowId: 'multi', quType: 2, count: 3, score: 3 },
          { rowId: 'judge', quType: 3, count: 2, score: 1 }
        ],
        totalTime: 60,
        includeAnswer: true
      },
      wordExportRules: {
        title: [
          { required: true, message: '试卷标题不能为空', trigger: 'blur' }
        ],
        repoIds: [
          { required: true, type: 'array', min: 1, message: '请至少选择一个题库', trigger: 'change' }
        ]
      },

      listQuery: {
        current: 1,
        size: 10,
        params: {
          content: '',
          quType: '',
          repoIds: []
        }
      },

      quTypes: QU_TYPE_OPTIONS,

      options: {

        // 可批量操作
        multi: true,

        // 批量操作列表
        multiActions: [
          {
            value: 'add-repo',
            label: '加入题库..'
          },
          {
            value: 'remove-repo',
            label: '从..题库移除'
          },
          {
            value: 'delete',
            label: '删除'
          }
        ],
        // 列表请求URL
        listUrl: '/exam/api/qu/qu/paging',
        // 删除请求URL
        deleteUrl: '/exam/api/qu/qu/delete',
        // 添加数据路由
        addRoute: 'AddQu'
      }
    }
  },
  computed: {
    isSa() {
      const roles = this.$store.getters.roles || []
      return roles.indexOf('sa') !== -1
    }
  },
  methods: {

    handleMultiAction(obj) {
      if (obj.opt === 'add-repo') {
        this.dialogTitle = '加入题库'
        this.dialogFlag = false
      }

      if (obj.opt === 'remove-repo') {
        this.dialogTitle = '从题库移除'
        this.dialogFlag = true
      }

      this.dialogVisible = true
      this.dialogQuIds = obj.ids
    },

    handlerRepoAction() {
      const postForm = { repoIds: this.dialogRepos, quIds: this.dialogQuIds, remove: this.dialogFlag }

      batchAction(postForm).then(() => {
        this.$notify({
          title: '成功',
          message: '批量操作成功！',
          type: 'success',
          duration: 2000
        })

        this.dialogVisible = false
        this.$refs.pagingTable.getList()
      })
    },

    exportExcel() {
      // 导出当前查询的数据
      exportExcel(this.listQuery.params)
    },

    showWordExport() {
      this.wordExportVisible = true

      if (this.listQuery.params.repoIds && this.listQuery.params.repoIds.length > 0) {
        this.wordExportForm.repoIds = this.listQuery.params.repoIds
      }

      if (!this.wordExportForm.types || this.wordExportForm.types.length === 0) {
        this.wordExportForm.types = [this.newWordExportType(1, 0, 1)]
      }

      this.$nextTick(() => {
        if (this.$refs.wordExportForm) {
          this.$refs.wordExportForm.clearValidate()
        }
      })
    },

    exportWord() {
      this.$refs.wordExportForm.validate(valid => {
        if (!valid) {
          return
        }

        const typeConfigs = this.wordExportForm.types
          .filter(item => Number(item.count || 0) > 0)
          .map(item => ({
            quType: item.quType,
            count: Number(item.count || 0),
            score: Number(item.score || 0)
          }))

        if (typeConfigs.length === 0) {
          this.$message.warning('请至少设置一种题型的抽题数量！')
          return
        }

        const invalidType = typeConfigs.find(item => !item.quType)
        if (invalidType) {
          this.$message.warning('请选择题型！')
          return
        }

        const invalidScore = typeConfigs.find(item => item.score <= 0)
        if (invalidScore) {
          this.$message.warning('已设置抽题数量的题型，每题分值必须大于0！')
          return
        }

        const duplicateTypes = typeConfigs
          .map(item => item.quType)
          .filter((value, index, arr) => arr.indexOf(value) !== index)
        if (duplicateTypes.length > 0) {
          this.$message.warning('题型不能重复配置！')
          return
        }

        const payload = {
          ...this.wordExportForm,
          types: typeConfigs
        }

        this.wordExportLoading = true
        exportRandomWord(payload).then(() => {
          this.wordExportVisible = false
        }).finally(() => {
          this.wordExportLoading = false
        })
      })
    },

    newWordExportType(quType, count, score) {
      return {
        rowId: new Date().getTime() + '-' + Math.random(),
        quType: quType,
        count: count,
        score: score
      }
    },

    addWordExportType() {
      const usedTypes = this.wordExportForm.types.map(item => item.quType)
      const nextType = this.quTypes.find(item => usedTypes.indexOf(item.value) === -1)
      if (!nextType) {
        this.$message.warning('全部题型都已经添加！')
        return
      }
      this.wordExportForm.types.push(this.newWordExportType(nextType.value, 0, 1))
    },

    removeWordExportType(index) {
      if (this.wordExportForm.types.length <= 1) {
        return
      }
      this.wordExportForm.types.splice(index, 1)
    },

    isWordExportTypeDisabled(value, currentIndex) {
      return this.wordExportForm.types.some((item, index) => index !== currentIndex && item.quType === value)
    },

    downloadTemplate() {
      importTemplate()
    },

    showImport() {
      this.importVisible = true
    },

    showAiImport() {
      this.$router.push({ name: 'AiImportQu' })
    },

    // 只是为了美化一下导入按钮
    chooseFile: function() {
      this.$refs.upFile.dispatchEvent(new MouseEvent('click'))
    },

    doImport(e) {
      const file = e.target.files[0]

      importExcel(file).then(res => {
        if (res.code !== 0) {
          this.$alert(res.data.msg, '导入信息', {
            dangerouslyUseHTMLString: true
          })
        } else {
          this.$message({
            message: '数据导入成功！',
            type: 'success'
          })

          this.importVisible = false
          this.$refs.pagingTable.getList()
        }
      })
    }
  }
}
</script>

<style scoped>
.word-export-type-list {
  width: 100%;
}

.word-export-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 120px 120px 32px;
  grid-column-gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}

.word-export-header-row {
  margin-bottom: 8px;
}

.word-export-header {
  color: #909399;
  font-size: 13px;
  line-height: 1;
}

.word-export-type-select {
  width: 100%;
}

.word-export-number {
  width: 120px;
}

.word-export-unit {
  margin-left: 8px;
  color: #606266;
}
</style>
