<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  adminFeedbackStats,
  adminGetFeedback,
  adminListFeedback,
  adminReplyFeedback,
  adminUpdateFeedbackStatus
} from '../api/feedback'
import { FEEDBACK_CATEGORY, FEEDBACK_STATUS, FEEDBACK_STATUS_TYPE } from '../utils/constants'
import { formatTime } from '../utils/format'

const stats = ref({})
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, status: '', category: '', keyword: '' })

const drawer = ref(false)
const current = ref(null)
const replying = ref(false)
const replyForm = reactive({ reply: '', status: 'RESOLVED' })

async function loadStats() {
  stats.value = await adminFeedbackStats()
}

async function loadList() {
  loading.value = true
  try {
    const data = await adminListFeedback({ ...query })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  current.value = await adminGetFeedback(row.id)
  replyForm.reply = current.value.adminReply || ''
  replyForm.status = current.value.status === 'PENDING' ? 'RESOLVED' : current.value.status
  drawer.value = true
}

async function submitReply() {
  if (!replyForm.reply.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replying.value = true
  try {
    current.value = await adminReplyFeedback(current.value.id, {
      reply: replyForm.reply.trim(),
      status: replyForm.status
    })
    ElMessage.success('回复已发送')
    drawer.value = false
    await loadList()
    await loadStats()
  } finally {
    replying.value = false
  }
}

async function quickStatus(row, status) {
  await adminUpdateFeedbackStatus(row.id, status)
  ElMessage.success('状态已更新')
  await loadList()
  await loadStats()
}

function resetQuery() {
  query.status = ''
  query.category = ''
  query.keyword = ''
  query.pageNum = 1
  loadList()
}

onMounted(() => {
  loadStats()
  loadList()
})
</script>

<template>
  <el-card>
    <h3>反馈管理</h3>

    <el-row :gutter="16" class="stats">
      <el-col :span="4"><el-card shadow="never"><div class="stat-label">总数</div><div class="stat-value">{{ stats.total || 0 }}</div></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><div class="stat-label">待处理</div><div class="stat-value warning">{{ stats.pending || 0 }}</div></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><div class="stat-label">处理中</div><div class="stat-value primary">{{ stats.processing || 0 }}</div></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><div class="stat-label">已解决</div><div class="stat-value success">{{ stats.resolved || 0 }}</div></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><div class="stat-label">平均评分</div><div class="stat-value">{{ stats.avgRating ? Number(stats.avgRating).toFixed(1) : '—' }}</div></el-card></el-col>
    </el-row>

    <div class="row">
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
        <el-option v-for="(label, key) in FEEDBACK_STATUS" :key="key" :label="label" :value="key" />
      </el-select>
      <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 140px">
        <el-option v-for="(label, key) in FEEDBACK_CATEGORY" :key="key" :label="label" :value="key" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="搜索标题/内容" clearable style="width: 220px" />
      <el-button type="primary" @click="query.pageNum = 1; loadList()">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column label="分类" width="110">
        <template #default="{ row }">{{ FEEDBACK_CATEGORY[row.category] || row.category }}</template>
      </el-table-column>
      <el-table-column label="评分" width="120">
        <template #default="{ row }">
          <el-rate :model-value="row.rating || 0" disabled />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-select
            :model-value="row.status"
            size="small"
            @change="(value) => quickStatus(row, value)"
          >
            <el-option v-for="(label, key) in FEEDBACK_STATUS" :key="key" :label="label" :value="key" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情 / 回复</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="mt"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNum"
      @current-change="(p) => { query.pageNum = p; loadList() }"
    />

    <el-drawer v-model="drawer" title="反馈详情与回复" size="620px">
      <template v-if="current">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ current.id }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ current.username || '—' }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ FEEDBACK_CATEGORY[current.category] || current.category }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="FEEDBACK_STATUS_TYPE[current.status] || 'info'">
              {{ FEEDBACK_STATUS[current.status] || current.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="任务ID">{{ current.taskId || '通用反馈' }}</el-descriptions-item>
          <el-descriptions-item label="评分">{{ current.rating || '—' }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ current.title || '—' }}</el-descriptions-item>
          <el-descriptions-item label="反馈内容" :span="2">
            <div class="pre">{{ current.comment }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="联系方式" :span="2">{{ current.contact || '—' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间" :span="2">{{ formatTime(current.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">处理回复</el-divider>
        <el-form label-width="80px">
          <el-form-item label="回复内容">
            <el-input
              v-model="replyForm.reply"
              type="textarea"
              :rows="5"
              maxlength="1000"
              show-word-limit
              placeholder="填写给用户的处理结果或说明"
            />
          </el-form-item>
          <el-form-item label="处理状态">
            <el-select v-model="replyForm.status" style="width: 180px">
              <el-option v-for="(label, key) in FEEDBACK_STATUS" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="replying" @click="submitReply">发送回复</el-button>
          </el-form-item>
        </el-form>
      </template>
    </el-drawer>
  </el-card>
</template>

<style scoped>
.stats { margin-bottom: 16px; }
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 24px; font-weight: 600; margin-top: 6px; }
.stat-value.warning { color: #e6a23c; }
.stat-value.primary { color: #409eff; }
.stat-value.success { color: #67c23a; }
.row { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.mt { margin-top: 16px; }
.pre { white-space: pre-wrap; word-break: break-word; }
</style>
