<template>
  <div class="app-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基础配置" name="basic">
        <el-form ref="postForm" :model="postForm" :rules="rules" label-position="left" label-width="100px">
          <el-card>
            <el-form-item label="系统名称">
              <el-input v-model="postForm.siteName" placeholder="系统显示名称" />
            </el-form-item>

            <el-form-item label="系统LOGO">
              <file-upload v-model="postForm.backLogo" accept=".jpg,.jepg,.png" />
            </el-form-item>

            <el-row>
              <el-button type="primary" @click="submitForm">保存</el-button>
            </el-row>
          </el-card>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="AI 配置" name="ai" lazy>
        <sys-ai-config-panel v-if="activeTab === 'ai'" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { fetchDetail, saveData } from '@/api/sys/config/config'
import FileUpload from '@/components/FileUpload'
import SysAiConfigPanel from './ai-config'

export default {
  name: 'Config',
  components: { FileUpload, SysAiConfigPanel },
  data() {
    return {
      activeTab: 'basic',
      postForm: {
        id: '1'
      },
      loading: false,
      rules: {}
    }
  },
  created() {
    this.fetchData()
  },
  methods: {
    fetchData() {
      fetchDetail().then(response => {
        this.postForm = response.data
      })
    },
    submitForm() {
      this.$refs.postForm.validate(valid => {
        if (!valid) {
          return
        }

        this.loading = true
        this.postForm.id = '1'

        saveData(this.postForm).then(() => {
          this.$notify({
            title: '成功',
            message: '配置保存成功！',
            type: 'success',
            duration: 2000
          })
        })
        this.loading = false
      })
    }
  }
}
</script>

<style scoped>
</style>
