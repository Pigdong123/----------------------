<script setup>
const props = defineProps({
  // 例如 { bitrate: { type: 'enum', default: '192k', enum: ['128k','192k'] } }
  schema: { type: Object, default: () => ({}) },
  modelValue: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue'])

function update(key, value) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>

<template>
  <div v-if="Object.keys(schema).length" class="param-form">
    <el-form label-width="90px">
      <el-form-item v-for="(def, key) in schema" :key="key" :label="key">
        <el-select
          v-if="def.type === 'enum'"
          :model-value="modelValue[key] ?? def.default"
          @update:model-value="(v) => update(key, v)"
        >
          <el-option v-for="opt in def.enum || []" :key="opt" :label="opt" :value="opt" />
        </el-select>
        <el-input-number
          v-else-if="def.type === 'integer'"
          :model-value="modelValue[key] ?? def.default"
          :min="def.min"
          :max="def.max"
          @update:model-value="(v) => update(key, v)"
        />
        <el-input
          v-else
          :model-value="modelValue[key] ?? def.default"
          @update:model-value="(v) => update(key, v)"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.param-form { max-width: 420px; }
</style>
