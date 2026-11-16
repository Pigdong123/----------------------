import { defineStore } from 'pinia'
import * as taskApi from '../api/task'

export const useTaskStore = defineStore('task', {
  state: () => ({
    list: [],
    total: 0,
    loading: false,
    current: null
  }),
  actions: {
    async fetchList(params) {
      this.loading = true
      try {
        const data = await taskApi.listTasks(params)
        this.list = data.records
        this.total = data.total
      } finally {
        this.loading = false
      }
    },
    async fetchDetail(id) {
      this.current = await taskApi.getTask(id)
      return this.current
    }
  }
})
