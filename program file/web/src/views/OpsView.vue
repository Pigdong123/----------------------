<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { engineHealth, opsOverview, queueStatus, runCleanup, storageStatus } from '../api/ops'
import { formatSize } from '../utils/format'

const queue = ref({})
const engines = ref({})
const storage = ref({})
const overview = ref({})

async function load() {
  queue.value = await queueStatus()
  engines.value = await engineHealth()
  storage.value = await storageStatus()
  overview.value = await opsOverview()
}

async function cleanup() {
  const result = await runCleanup()
  ElMessage.success(`清理完成：文件 ${result.expiredFiles}，任务 ${result.expiredTasks}`)
  load()
}

onMounted(load)
</script>

<template>
  <el-card>
    <h3>运维端</h3>
    <el-row :gutter="16">
      <el-col :span="8">
        <el-statistic title="排队任务" :value="queue.queuedCount || 0" />
      </el-col>
      <el-col :span="8">
        <el-statistic title="引擎健康" :value="engines.healthy ? '正常' : '异常'" />
      </el-col>
      <el-col :span="8">
        <el-statistic title="存储可用" :value="formatSize(storage.availableBytes)" />
      </el-col>
    </el-row>

    <el-divider>转换引擎</el-divider>
    <el-tag v-for="info in engines.engines || []" :key="info" class="tag">{{ info }}</el-tag>

    <el-divider>队列快照</el-divider>
    <el-space wrap>
      <el-tag v-for="id in queue.snapshot || []" :key="id" type="warning">任务 {{ id }}</el-tag>
    </el-space>

    <div class="actions">
      <el-button type="primary" @click="load">刷新</el-button>
      <el-button type="danger" @click="cleanup">手动清理过期文件</el-button>
    </div>
  </el-card>
</template>

<style scoped>
.tag { margin-right: 8px; }
.actions { margin-top: 20px; }
</style>
