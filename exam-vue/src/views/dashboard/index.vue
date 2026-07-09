<template>
  <div class="app-container">
    <el-card class="box">
      <div slot="header" class="clearfix">
        <span>系统概览</span>
        <el-button style="float: right; padding: 3px 0; font-size: 12px" type="text">系统版本：{{ version }}</el-button>
      </div>

      <div class="overview">
        <div class="title">系统介绍</div>
        <p>在线考试系统，支持题库管理、试卷组卷、在线考试、成绩统计与错题训练等功能。</p>

        <div class="title">快捷入口</div>
        <div class="links">
          <router-link :to="{ name: 'ListExam' }">考试管理</router-link>
          <router-link :to="{ name: 'ListQu' }">题目管理</router-link>
          <router-link :to="{ name: 'ListRepo' }">题库管理</router-link>
          <router-link :to="{ name: 'SysConfig' }">系统配置</router-link>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import config from '../../../package.json'

export default {
  name: 'Dashboard',
  data() {
    return {
      version: config.version
    }
  }
}
</script>

<style scoped>
.box {
  min-height: calc(100vh - 120px);
  font-size: 14px;
  line-height: 28px;
}

.overview p {
  margin: 0 0 8px;
  color: #606266;
}

.title {
  font-size: 18px;
  font-weight: 700;
  padding-bottom: 10px;
  padding-top: 20px;
  margin-bottom: 10px;
  border-bottom: #eee 1px dotted;
}

.title:first-child {
  padding-top: 0;
}

.links {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.links a {
  color: #409eff;
}
</style>
