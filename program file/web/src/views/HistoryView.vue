<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteFile, listFiles } from '../api/file'
import { formatSize, formatTime } from '../utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const data = await listFiles({ ...query })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function onDelete(id) {
  await deleteFile(id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <el-card>
    <h3>我的文件</h3>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="originalName" label="文件名" />
      <el-table-column prop="ext" label="格式" width="90" />
      <el-table-column label="大小" width="120">
        <template #default="{ row }">{{ formatSize(row.size) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="130" />
      <el-table-column label="过期时间" width="180">
        <template #default="{ row }">{{ formatTime(row.expireAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="danger" @click="onDelete(row.id)">删除</el-button>
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
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
