<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import FileUploader from '../components/FileUploader.vue'
import FormatSelector from '../components/FormatSelector.vue'
import ParamForm from '../components/ParamForm.vue'
import { createTask } from '../api/task'
import { aiParams } from '../api/ai'

const uploaded = ref(null)
const targetFormat = ref('')
const params = reactive({})
const created = ref(null)

const formats = computed(() => uploaded.value?.recommendedFormats || [])

const schema = computed(() => {
  switch (targetFormat.value) {
    case 'mp3':
      return { bitrate: { type: 'enum', default: '192k', enum: ['128k', '192k', '256k', '320k'] } }
    case 'webp':
      return { quality: { type: 'integer', default: 80, min: 10, max: 100 } }
    case 'pdf':
      return { clarity: { type: 'enum', default: 'standard', enum: ['low', 'standard', 'high'] } }
    default:
      return {}
  }
})

function clearParams() {
  Object.keys(params).forEach((key) => delete params[key])
}

async function onUploaded(data) {
  uploaded.value = data
  created.value = null
  targetFormat.value = (data.recommendedFormats || [])[0] || ''
  clearParams()
  await applyRecommend()
}

async function applyRecommend() {
  if (!uploaded.value || !targetFormat.value) return
  try {
    const rec = await aiParams({
      sourceExt: uploaded.value.ext,
      targetFormat: targetFormat.value,
      sizeBytes: uploaded.value.size || 0
    })
    Object.assign(params, rec || {})
  } catch (e) {
    // AI 可降级，忽略推荐失败
  }
}

async function submit() {
  if (!uploaded.value) {
    ElMessage.warning('请先上传文件')
    return
  }
  if (!targetFormat.value) {
    ElMessage.warning('请选择目标格式')
    return
  }
  created.value = await createTask({
    sourceFileId: uploaded.value.fileId,
    targetFormat: targetFormat.value,
    params: { ...params }
  })
  ElMessage.success('转换任务已创建')
}
</script>

<template>
  <el-card>
    <h3>上传 → 校验 → 选择格式 → 创建任务</h3>
    <FileUploader @uploaded="onUploaded" />

    <div v-if="uploaded" class="section">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="文件">{{ uploaded.originalName }}</el-descriptions-item>
        <el-descriptions-item label="格式">{{ uploaded.ext }}</el-descriptions-item>
        <el-descriptions-item label="剩余配额">{{ uploaded.remainingQuota }}</el-descriptions-item>
      </el-descriptions>

      <div class="row">
        <span class="label">目标格式：</span>
        <FormatSelector v-model="targetFormat" :formats="formats" @update:model-value="applyRecommend" />
      </div>

      <ParamForm v-model="params" :schema="schema" />

      <el-button type="primary" @click="submit">创建转换任务</el-button>
    </div>

    <el-alert
      v-if="created"
      class="section"
      type="success"
      :closable="false"
      :title="`任务 #${created.id} 已创建，当前状态：${created.statusDesc}`"
    />
  </el-card>
</template>

<style scoped>
.section { margin-top: 20px; }
.row { display: flex; align-items: center; gap: 12px; margin: 16px 0; }
.label { color: #606266; }
</style>
