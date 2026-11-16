<script setup>
import { computed } from 'vue'
import { TASK_STATUS, STATUS_TYPE } from '../utils/constants'

const props = defineProps({
  status: { type: String, default: '' }
})

const label = computed(() => TASK_STATUS[props.status] || props.status)
const type = computed(() => STATUS_TYPE[props.status] || 'info')
</script>

<template>
  <el-tag :type="type" effect="light">{{ label }}</el-tag>
</template>
