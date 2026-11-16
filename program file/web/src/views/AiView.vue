<script setup>
import { onMounted, ref } from 'vue'
import { aiChat, aiDiagnose, aiStatus } from '../api/ai'

const status = ref({})
const question = ref('')
const answer = ref('')
const errorMsg = ref('')
const diagnosis = ref('')

async function loadStatus() {
  status.value = await aiStatus()
}

async function ask() {
  answer.value = await aiChat(question.value)
}

async function diagnose() {
  diagnosis.value = await aiDiagnose(errorMsg.value)
}

onMounted(loadStatus)
</script>

<template>
  <el-card>
    <h3>AI 助手（可选、可降级）</h3>
    <el-alert
      :closable="false"
      type="info"
      :title="`AI 状态：${status.enabled ? '启用' : '关闭'}，提供方：${status.provider}`"
    />

    <el-divider>智能客服</el-divider>
    <div class="row">
      <el-input v-model="question" placeholder="例如：为什么失败？怎么选格式？" style="width: 360px" />
      <el-button type="primary" @click="ask">提问</el-button>
    </div>
    <el-alert v-if="answer" :closable="false" type="success" :title="answer" />

    <el-divider>异常诊断</el-divider>
    <div class="row">
      <el-input v-model="errorMsg" placeholder="粘贴失败信息" style="width: 360px" />
      <el-button @click="diagnose">诊断</el-button>
    </div>
    <el-alert v-if="diagnosis" :closable="false" type="warning" :title="diagnosis" />
  </el-card>
</template>

<style scoped>
.row { display: flex; gap: 12px; margin-bottom: 12px; }
</style>
