<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const mode = ref('login')
const loading = ref(false)
const form = reactive({ username: '', password: '', role: 'STUDENT' })

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'login') {
      await userStore.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
      router.push(route.query.redirect || '/upload')
    } else {
      await userStore.register({ ...form })
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>CampusFormat</h2>
      <p class="subtitle">校园课程资源格式适配与转换服务系统</p>
      <el-input v-model="form.username" placeholder="用户名" class="field" />
      <el-input v-model="form.password" type="password" placeholder="密码" class="field" show-password />
      <el-select v-if="mode === 'register'" v-model="form.role" class="field">
        <el-option label="学生" value="STUDENT" />
        <el-option label="教师/助教" value="TEACHER" />
        <el-option label="课程管理员/教务" value="COURSE_ADMIN" />
        <el-option label="系统运维" value="OPS" />
      </el-select>
      <el-button type="primary" :loading="loading" class="field" @click="submit">
        {{ mode === 'login' ? '登录' : '注册' }}
      </el-button>
      <el-button link type="primary" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
      </el-button>
      <p class="hint">首次注册的账号自动成为系统管理员，便于初始化演示。</p>
    </el-card>
  </div>
</template>

<style scoped>
.login-page { display: flex; align-items: center; justify-content: center; min-height: 100vh; background: #f0f2f5; }
.login-card { width: 380px; text-align: center; }
.subtitle { color: #909399; margin-bottom: 20px; }
.field { margin-bottom: 14px; }
.hint { color: #c0c4cc; font-size: 12px; margin-top: 12px; }
</style>
