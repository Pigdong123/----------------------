<script setup>
const props = defineProps({
  modelValue: { type: String, default: '' },
  formats: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <el-select
    :model-value="modelValue"
    placeholder="选择目标格式"
    @update:model-value="(val) => emit('update:modelValue', val)"
  >
    <el-option v-for="fmt in formats" :key="fmt" :label="fmt.toUpperCase()" :value="fmt" />
  </el-select>
</template>
