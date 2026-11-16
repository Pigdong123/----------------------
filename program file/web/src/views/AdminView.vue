<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createTemplate,
  deleteTemplate,
  listAudit,
  listTemplates,
  listUsers,
  statsByFormat,
  statsOverview,
  toggleTemplate,
  updateUserEnabled,
  updateUserRole
} from '../api/admin'

const tab = ref('users')
const users = ref([])
const templates = ref([])
const audits = ref([])
const overview = ref({})
const byFormat = ref([])

const newTemplate = reactive({ sourceExt: '', targetExt: '', engine: 'LIBREOFFICE', enabled: true })

async function loadUsers() {
  const data = await listUsers({ pageNum: 1, pageSize: 20 })
  users.value = data.records
}
async function loadTemplates() {
  templates.value = await listTemplates()
}
async function loadStats() {
  overview.value = await statsOverview()
  byFormat.value = await statsByFormat(7)
}
async function loadAudit() {
  const data = await listAudit({ pageNum: 1, pageSize: 20 })
  audits.value = data.records
}

async function changeRole(row) {
  await updateUserRole(row.id, row.role)
  ElMessage.success('角色已更新')
}
async function toggleUser(row) {
  const enabled = row.enabled === 1
  await updateUserEnabled(row.id, !enabled)
  loadUsers()
}
async function addTemplate() {
  await createTemplate({ ...newTemplate })
  ElMessage.success('模板已新增')
  loadTemplates()
}
async function removeTemplate(id) {
  await deleteTemplate(id)
  loadTemplates()
}
async function toggleTpl(row) {
  await toggleTemplate(row.id, row.enabled !== 1)
  loadTemplates()
}

onMounted(() => {
  loadUsers()
  loadTemplates()
  loadStats()
  loadAudit()
})
</script>

<template>
  <el-card>
    <h3>管理端</h3>
    <el-tabs v-model="tab">
      <el-tab-pane label="用户与权限" name="users">
        <el-table :data="users" border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column label="角色" width="200">
            <template #default="{ row }">
              <el-select v-model="row.role" @change="changeRole(row)">
                <el-option label="学生" value="STUDENT" />
                <el-option label="教师/助教" value="TEACHER" />
                <el-option label="课程管理员/教务" value="COURSE_ADMIN" />
                <el-option label="系统运维" value="OPS" />
                <el-option label="系统管理员" value="SYS_ADMIN" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="120">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled === 1" @change="toggleUser(row)" />
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="格式模板" name="templates">
        <div class="row">
          <el-input v-model="newTemplate.sourceExt" placeholder="源格式 docx" style="width: 140px" />
          <el-input v-model="newTemplate.targetExt" placeholder="目标格式 pdf" style="width: 140px" />
          <el-select v-model="newTemplate.engine" style="width: 180px">
            <el-option label="FFMPEG" value="FFMPEG" />
            <el-option label="LIBREOFFICE" value="LIBREOFFICE" />
            <el-option label="PANDOC" value="PANDOC" />
            <el-option label="IMAGEMAGICK" value="IMAGEMAGICK" />
          </el-select>
          <el-button type="primary" @click="addTemplate">新增</el-button>
        </div>
        <el-table :data="templates" border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="sourceExt" label="源格式" />
          <el-table-column prop="targetExt" label="目标格式" />
          <el-table-column prop="engine" label="引擎" />
          <el-table-column label="启用" width="120">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled === 1" @change="toggleTpl(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeTemplate(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="统计报表" name="stats">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="近7天任务">{{ overview.tasksLast7d }}</el-descriptions-item>
          <el-descriptions-item label="排队数">{{ overview.queuedCount }}</el-descriptions-item>
          <el-descriptions-item label="存储已用">{{ overview.storageUsedBytes }}</el-descriptions-item>
          <el-descriptions-item label="存储可用">{{ overview.storageAvailableBytes }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="byFormat" border class="mt">
          <el-table-column prop="targetFormat" label="目标格式" />
          <el-table-column prop="count" label="转换量" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="审计日志" name="audit">
        <el-table :data="audits" border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="userId" label="用户" width="90" />
          <el-table-column prop="action" label="操作" />
          <el-table-column prop="target" label="对象" />
          <el-table-column prop="ip" label="IP" width="140" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<style scoped>
.row { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.mt { margin-top: 16px; }
</style>
