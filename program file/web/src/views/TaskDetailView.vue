<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import StatusBadge from '../components/StatusBadge.vue'
import { downloadTask, getTask } from '../api/task'
import { submitFeedback } from '../api/feedback'
import { formatTime } from '../utils/format'

const route = useRoute()
const task = ref(null)
const rating = ref(5)
const comment = ref('')

async function load() {
  task.value = await getTask(route.params.id)
}

async function onDownload() {
  const res = await downloadTask(route.params.id)
  const url = window.URL.createObjectURL(new Blob([res.data]))
  const link = document.createElement('a')
  link.href = url
  link.download = `result_${route.params.id}.${task.value.targetFormat}`
  link.click()
  window.URL.revokeObjectURL(url)
  load()
}

async function onFeedback() {
  await submitFeedback({ taskId: Number(route.params.id), rating: rating.value, comment: comment.value })
  ElMessage.success('反馈已提交')
}

onMounted(load)
</script>

<template>
  <el-card v-if="task">
    <el-descriptions title="任务详情" :column="2" border>
      <el-descriptions-item label="任务ID">{{ task.id }}</el-descriptions-item>
      <el-descriptions-item label="状态"><StatusBadge :status="task.status" /></el-descriptions-item>
      <el-descriptions-item label="源文件">{{ task.sourceFileName }}</el-descriptions-item>
      <el-descriptions-item label="目标格式">{{ task.targetFormat }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatTime(task.createdAt) }}</el-descriptions-item>
      <el-descriptions-item label="完成时间">{{ formatTime(task.finishedAt) }}</el-descriptions-item>
      <el-descriptions-item label="重试次数">{{ task.retryCount }}</el-descriptions-item>
      <el-descriptions-item label="失败原因">{{ task.errorMsg || '-' }}</el-descriptions-item>
      <el-descriptions-item label="AI 诊断" :span="2">{{ task.diagnosis || '-' }}</el-descriptions-item>
    </el-descriptions>

    <div class="actions">
      <el-button
        v-if="['SUCCESS', 'DOWNLOADED'].includes(task.status)"
        type="success"
        @click="onDownload"
      >下载结果</el-button>
    </div>

    <el-divider>反馈评价</el-divider>
    <el-rate v-model="rating" />
    <el-input v-model="comment" type="textarea" placeholder="评语（可选）" class="comment" />
    <el-button type="primary" @click="onFeedback">提交反馈</el-button>
  </el-card>
</template>

<style scoped>
.actions { margin-top: 16px; }
.comment { margin: 12px 0; max-width: 480px; }
</style>
