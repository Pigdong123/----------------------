<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from './stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const showShell = computed(() => route.name !== 'login')

function logout() {
  userStore.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container v-if="showShell" class="app-shell">
    <el-header class="app-header">
      <span class="brand">CampusFormat</span>
      <el-menu mode="horizontal" :default-active="route.path" router class="app-menu">
        <el-menu-item index="/upload">上传转换</el-menu-item>
        <el-menu-item index="/tasks">任务</el-menu-item>
        <el-menu-item index="/history">历史</el-menu-item>
        <el-menu-item index="/feedback">反馈</el-menu-item>
        <el-menu-item index="/ai">AI 助手</el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/admin">管理端</el-menu-item>
        <el-menu-item v-if="userStore.isAdmin || userStore.isOps" index="/admin/feedback">反馈管理</el-menu-item>
        <el-menu-item v-if="userStore.isOps" index="/ops">运维端</el-menu-item>
      </el-menu>
      <div class="app-user">
        <span>{{ userStore.user?.username }}（{{ userStore.user?.role }}）</span>
        <el-button link type="primary" @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
  <router-view v-else />
</template>

<style>
body { margin: 0; background: #f5f7fa; }
.app-header { display: flex; align-items: center; gap: 24px; background: #fff; border-bottom: 1px solid #ebeef5; }
.brand { font-weight: 700; font-size: 18px; color: #409eff; }
.app-menu { flex: 1; border-bottom: none; }
.app-user { display: flex; align-items: center; gap: 8px; color: #606266; }
</style>
