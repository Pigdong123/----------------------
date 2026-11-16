import { defineStore } from 'pinia'
import * as authApi from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('campus_token') || '',
    user: JSON.parse(localStorage.getItem('campus_user') || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    role: (state) => state.user?.role,
    isAdmin: (state) => ['SYS_ADMIN', 'COURSE_ADMIN'].includes(state.user?.role),
    isOps: (state) => ['OPS', 'SYS_ADMIN'].includes(state.user?.role)
  },
  actions: {
    setSession(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('campus_token', token)
      localStorage.setItem('campus_user', JSON.stringify(user))
    },
    async login(payload) {
      const data = await authApi.login(payload)
      this.setSession(data.token, data.user)
      return data
    },
    async register(payload) {
      return authApi.register(payload)
    },
    async logout() {
      try {
        await authApi.logout()
      } catch (e) {
        // 忽略退出接口错误，本地会话仍需清理
      }
      this.token = ''
      this.user = null
      localStorage.removeItem('campus_token')
      localStorage.removeItem('campus_user')
    }
  }
})
