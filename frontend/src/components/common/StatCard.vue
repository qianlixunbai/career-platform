<template>
  <el-card class="stat-card" shadow="never">
    <div class="stat-card__topline">
      <span class="stat-card__label">{{ label }}</span>
      <span class="stat-card__icon" :class="`stat-card__icon--${tone}`">
        <el-icon><component :is="icon" /></el-icon>
      </span>
    </div>
    <div class="stat-card__value">{{ value }}</div>
    <div v-if="description" class="stat-card__description">{{ description }}</div>
  </el-card>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    label: string
    value: number | string
    description?: string
    icon: Component
    tone?: 'blue' | 'orange' | 'green' | 'purple'
  }>(),
  {
    description: '',
    tone: 'blue',
  },
)
</script>

<style scoped>
.stat-card {
  border: 1px solid var(--cp-border);
  border-radius: 14px;
}

.stat-card :deep(.el-card__body) {
  padding: 22px;
}

.stat-card__topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.stat-card__label {
  color: var(--cp-muted);
  font-size: 13px;
  font-weight: 650;
}

.stat-card__icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 10px;
}

.stat-card__icon--blue {
  color: #24617c;
  background: #e5f1f5;
}

.stat-card__icon--orange {
  color: #a4552d;
  background: #fceddf;
}

.stat-card__icon--green {
  color: #3c765f;
  background: #e8f3ec;
}

.stat-card__icon--purple {
  color: #6a5a91;
  background: #eeeafa;
}

.stat-card__value {
  margin-top: 16px;
  color: var(--cp-ink);
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
}

.stat-card__description {
  margin-top: 9px;
  color: var(--cp-muted);
  font-size: 12px;
}
</style>
