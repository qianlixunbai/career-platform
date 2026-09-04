<template>
  <div class="loading-state" aria-label="加载中">
    <el-icon class="loading-state__icon" :size="26"><Loading /></el-icon>
    <span>{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'

withDefaults(
  defineProps<{
    label?: string
  }>(),
  {
    label: '正在加载…',
  },
)
</script>

<style scoped>
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 180px;
  color: var(--cp-muted);
  font-size: 14px;
}

.loading-state__icon {
  animation: loading-spin 1s linear infinite;
}

@keyframes loading-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
