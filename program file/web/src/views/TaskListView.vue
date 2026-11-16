<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import StatusBadge from '../components/StatusBadge.vue'
import { cancelTask, downloadTask, listTasks, retryTask } from '../api/task'
import { formatTime } from '../utils/format'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: '' })

async function load() {
  loading.value = true
  try {
    const data = await listTasks({ ...query, status: query.status || undefined })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function onCancel(id) {
  await cancelTask(id)
  ElMessage.success('已取消')
  load()
}

async function onRetry(id) {
  await retryTask(id)
  ElMessage.success('已重新排队')
  load()
}

async function onDownload(row) {
  const res = await downloadTask(row.id)
  const url = window.URL.createObjectURL(new Blob([res.data]))
  const link = document.createElement('a')
  link.href = url
  link.download = `result_${row.id}.${row.targetFormat}`
  link.click()
  window.URL.revokeObjectURL(url)
  load()
}

onMounted(load)
</script>

<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 160px" @change="load">
        <el-option label="排队中" value="QUEUED" />
        <el-option label="转换中" value="CONVERTING" />
        <el-option label="质检中" value="QC" />
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="sourceFileName" label="源文件" />
      <el-table-column prop="targetFormat" label="目标格式" width="100" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusBadge :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/tasks/${row.id}`)">详情</el-button>
          <el-button v-if="row.status === 'QUEUED'" link type="warning" @click="onCancel(row.id)">取消</el-button>
          <el-button v-if="row.status === 'FAILED'" link type="warning" @click="onRetry(row.id)">重试</el-button>
          <el-button
            v-if="['SUCCESS', 'DOWNLOADED'].includes(row.status)"
            link
            type="success"
            @click="onDownload(row)"
          >下载</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNum"
      @current-change="(p) => { query.pageNum = p; load() }"
    />
  </el-card>
</template>

<style scoped>
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
