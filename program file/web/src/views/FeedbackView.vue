<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createFeedback, getMyFeedback, listMyFeedback, withdrawFeedback } from '../api/feedback'
import { FEEDBACK_CATEGORY, FEEDBACK_STATUS, FEEDBACK_STATUS_TYPE } from '../utils/constants'
import { formatTime } from '../utils/format'

const tab = ref('submit')
const submitting = ref(false)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const detail = ref(null)
const detailVisible = ref(false)

const query = reactive({ pageNum: 1, pageSize: 10, status: '', category: '', keyword: '' })
const form = reactive({
  taskId: null,
  rating: 5,
  category: 'BUG',
  title: '',
  comment: '',
  contact: '',
  anonymous: false
})

async function submit() {
  if (!form.comment || !form.comment.trim()) {
    ElMessage.warning('请填写反馈内容')
    return
  }
  submitting.value = true
  try {
    await createFeedback({
      taskId: form.taskId || null,
      rating: form.taskId ? form.rating : null,
      category: form.category,
      title: form.title || null,
      comment: form.comment.trim(),
      contact: form.contact || null,
      anonymous: form.anonymous
    })
    ElMessage.success('反馈已提交')
    form.taskId = null
    form.rating = 5
    form.title = ''
    form.comment = ''
    form.contact = ''
    form.anonymous = false
    tab.value = 'mine'
    query.pageNum = 1
    await load()
  } finally {
    submitting.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await listMyFeedback({ ...query })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detail.value = await getMyFeedback(row.id)
  detailVisible.value = true
}

async function withdraw(row) {
  await ElMessageBox.confirm('确定撤回该反馈？', '提示', { type: 'warning' })
  await withdrawFeedback(row.id)
  ElMessage.success('已撤回')
  load()
}

function resetQuery() {
  query.status = ''
  query.category = ''
  query.keyword = ''
  query.pageNum = 1
  load()
}

onMounted(load)
</script>

<template>
  <el-card>
    <h3>反馈与建议</h3>
    <el-tabs v-model="tab">
      <el-tab-pane label="提交反馈" name="submit">
        <el-form :model="form" label-width="90px" style="max-width: 760px">
          <el-form-item label="反馈分类">
            <el-select v-model="form.category" style="width: 220px">
              <el-option v-for="(label, key) in FEEDBACK_CATEGORY" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item label="关联任务">
            <el-input-number v-model="form.taskId" :min="1" :controls="false" placeholder="可选：任务ID" />
            <span class="hint">填写任务ID可对转换结果评分</span>
          </el-form-item>
          <el-form-item label="评分">
            <el-rate v-model="form.rating" :disabled="!form.taskId" />
            <span v-if="!form.taskId" class="hint">关联任务后可评分</span>
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="一句话概括问题（可选）" />
          </el-form-item>
          <el-form-item label="反馈内容" required>
            <el-input
              v-model="form.comment"
              type="textarea"
              :rows="5"
              maxlength="1000"
              show-word-limit
              placeholder="请描述遇到的问题、复现步骤或改进建议"
            />
          </el-form-item>
          <el-form-item label="联系方式">
            <el-input v-model="form.contact" placeholder="邮箱/手机号（可选，便于回复）" />
          </el-form-item>
          <el-form-item label="匿名提交">
            <el-switch v-model="form.anonymous" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submit">提交反馈</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="我的反馈" name="mine">
        <div class="row">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="(label, key) in FEEDBACK_STATUS" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 140px">
            <el-option v-for="(label, key) in FEEDBACK_CATEGORY" :key="key" :label="label" :value="key" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="搜索标题/内容" clearable style="width: 220px" />
          <el-button type="primary" @click="query.pageNum = 1; load()">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </div>

        <el-table v-loading="loading" :data="rows" border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="分类" width="110">
            <template #default="{ row }">{{ FEEDBACK_CATEGORY[row.category] || row.category }}</template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
          <el-table-column label="评分" width="130">
            <template #default="{ row }">
              <el-rate :model-value="row.rating || 0" disabled />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="FEEDBACK_STATUS_TYPE[row.status] || 'info'">
                {{ FEEDBACK_STATUS[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.status === 'PENDING'" link type="danger" @click="withdraw(row)">撤回</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="mt"
          layout="total, prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          :current-page="query.pageNum"
          @current-change="(p) => { query.pageNum = p; load() }"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" title="反馈详情" width="640px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="分类">{{ FEEDBACK_CATEGORY[detail.category] || detail.category }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ FEEDBACK_STATUS[detail.status] || detail.status }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail.title || '—' }}</el-descriptions-item>
        <el-descriptions-item label="评分">{{ detail.rating || '—' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="反馈内容" :span="2">
          <div class="pre">{{ detail.comment }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="管理员回复" :span="2">
          <div class="pre">{{ detail.adminReply || '暂未回复' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="回复时间">{{ detail.repliedAt ? formatTime(detail.repliedAt) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ detail.contact || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.row { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.mt { margin-top: 16px; }
.hint { margin-left: 10px; color: #909399; font-size: 12px; }
.pre { white-space: pre-wrap; word-break: break-word; }
</style>
