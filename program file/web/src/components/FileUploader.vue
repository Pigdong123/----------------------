<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile } from '../api/file'

const emit = defineEmits(['uploaded'])

const progress = ref(0)
const uploading = ref(false)

async function handleChange(option) {
  const raw = option?.file || option?.raw
  if (!raw) return
  uploading.value = true
  progress.value = 0
  try {
    const data = await uploadFile(raw, (p) => (progress.value = p))
    ElMessage.success('上传成功')
    emit('uploaded', data)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <el-upload
    drag
    :show-file-list="false"
    :auto-upload="true"
    :http-request="handleChange"
    accept=".doc,.docx,.ppt,.pptx,.flac,.wav,.m4a,.png,.jpg,.jpeg"
  >
    <div class="upload-area">拖拽文件到此处，或 <em>点击上传</em></div>
    <template #tip>
      <div class="el-upload__tip">支持 doc/docx/ppt/pptx、flac/wav/m4a、png/jpg/jpeg</div>
    </template>
  </el-upload>
  <el-progress v-if="uploading" :percentage="progress" />
</template>

<style scoped>
.upload-area { padding: 24px 0; color: #606266; }
.upload-area em { color: #409eff; font-style: normal; }
</style>
