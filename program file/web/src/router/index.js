import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  { path: '/', redirect: '/upload' },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/upload', name: 'upload', component: () => import('../views/UploadView.vue') },
  { path: '/tasks', name: 'tasks', component: () => import('../views/TaskListView.vue') },
  { path: '/tasks/:id', name: 'task-detail', component: () => import('../views/TaskDetailView.vue') },
  { path: '/history', name: 'history', component: () => import('../views/HistoryView.vue') },
  { path: '/feedback', name: 'feedback', component: () => import('../views/FeedbackView.vue') },
  { path: '/ai', name: 'ai', component: () => import('../views/AiView.vue') },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('../views/AdminView.vue'),
    meta: { roles: ['SYS_ADMIN', 'COURSE_ADMIN'] }
  },
  {
    path: '/admin/feedback',
    name: 'admin-feedback',
    component: () => import('../views/AdminFeedbackView.vue'),
    meta: { roles: ['SYS_ADMIN', 'COURSE_ADMIN', 'OPS'] }
  },
  {
    path: '/ops',
    name: 'ops',
    component: () => import('../views/OpsView.vue'),
    meta: { roles: ['OPS', 'SYS_ADMIN'] }
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.name === 'login') {
    return true
  }
  if (!userStore.isLogin) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta?.roles && !to.meta.roles.includes(userStore.role)) {
    return { name: 'upload' }
  }
  return true
})

export default router
