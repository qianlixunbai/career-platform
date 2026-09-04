<template>
  <div class="empty-state">
    <el-icon :size="34"><component :is="icon ?? FolderOpened" /></el-icon>
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__description">{{ description }}</p>
    <div v-if="$slots.actions" class="empty-state__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { FolderOpened } from '@element-plus/icons-vue'

withDefaults(
  defineProps<{
    title: string
    description?: string
    icon?: Component
  }>(),
  {
    description: '',
  },
)
</script>

<style scoped>
.empty-state__actions {
  margin-top: 18px;
}
</style>
