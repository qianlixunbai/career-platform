<template>
  <div class="page-container dashboard-page">
    <PageHeader
      :title="`欢迎回来，${username}`"
      description="把今天的行动，沉淀成下一次机会。这里是你的职业成长总览。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadDashboard">
          <el-icon><Refresh /></el-icon>
          刷新数据
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      class="dashboard-note"
      title="所有数据来自你的真实业务档案"
      description="从职业目标、岗位记录、学习计划和简历开始，持续完善自己的成长闭环。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="dashboard-stats">
      <StatCard
        v-for="stat in stats"
        :key="stat.label"
        :label="stat.label"
        :value="stat.value"
        :description="stat.description"
        :icon="stat.icon"
        :tone="stat.tone"
      />
    </div>

    <section class="dashboard-section">
      <div class="dashboard-section__heading">
        <div>
          <h2>下一步</h2>
          <p>从一个小动作开始，让档案保持新鲜。</p>
        </div>
      </div>
      <div class="dashboard-actions">
        <RouterLink v-for="action in actions" :key="action.path" :to="action.path" class="dashboard-action">
          <span class="dashboard-action__icon"><el-icon><component :is="action.icon" /></el-icon></span>
          <span class="dashboard-action__copy">
            <strong>{{ action.label }}</strong>
            <small>{{ action.description }}</small>
          </span>
          <el-icon class="dashboard-action__arrow"><ArrowRight /></el-icon>
        </RouterLink>
      </div>
    </section>

    <section class="dashboard-section dashboard-tip">
      <div class="dashboard-tip__mark">01</div>
      <div>
        <h2>让目标成为可执行的计划</h2>
        <p>先在职业目标中写下方向，再用学习计划拆解每周行动，最后用简历记录阶段性成果。</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, Aim, Briefcase, Files, Reading, Refresh } from '@element-plus/icons-vue'

import { listCareerGoals, listJobs } from '@/api/career'
import { listLearningPlans } from '@/api/learning'
import { listResumes } from '@/api/resume'
import PageHeader from '@/components/common/PageHeader.vue'
import StatCard from '@/components/common/StatCard.vue'
import { getSession } from '@/utils/auth'
import type { Component } from 'vue'

interface DashboardStat {
  label: string
  value: number
  description: string
  icon: Component
  tone: 'blue' | 'orange' | 'green' | 'purple'
}

interface DashboardAction {
  label: string
  description: string
  path: string
  icon: Component
}

const session = getSession()
const username = computed(() => session?.username ?? '朋友')
const loading = ref(false)
const stats = ref<DashboardStat[]>([
  { label: '职业目标', value: 0, description: '持续校准方向', icon: Aim, tone: 'blue' },
  { label: '岗位记录', value: 0, description: '值得关注的机会', icon: Briefcase, tone: 'orange' },
  { label: '学习计划', value: 0, description: '正在推进的行动', icon: Reading, tone: 'green' },
  { label: '简历', value: 0, description: '可持续迭代的版本', icon: Files, tone: 'purple' },
])

const actions: DashboardAction[] = [
  { label: '完善基础资料', description: '补充你的职业画像', path: '/profile', icon: Aim },
  { label: '记录一个岗位', description: '保存新的职业机会', path: '/career/jobs', icon: Briefcase },
  { label: '制定学习计划', description: '把目标拆成每周行动', path: '/learning/plans', icon: Reading },
  { label: '管理我的简历', description: '查看与更新简历版本', path: '/resumes', icon: Files },
]

async function loadDashboard(): Promise<void> {
  loading.value = true
  try {
    const [goals, jobs, plans, resumes] = await Promise.all([
      listCareerGoals().catch(() => []),
      listJobs().catch(() => []),
      listLearningPlans().catch(() => []),
      listResumes().catch(() => []),
    ])

    stats.value = [
      { ...stats.value[0], value: goals.length },
      { ...stats.value[1], value: jobs.length },
      { ...stats.value[2], value: plans.length },
      { ...stats.value[3], value: resumes.length },
    ]
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadDashboard()
})
</script>

<style scoped>
.dashboard-note {
  margin-bottom: 24px;
  border: 1px solid #dcebf1;
  background: #eef7fa;
}

.dashboard-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.dashboard-section {
  margin-top: 30px;
}

.dashboard-section__heading h2,
.dashboard-tip h2 {
  margin: 0;
  color: var(--cp-ink);
  font-size: 18px;
}

.dashboard-section__heading p,
.dashboard-tip p {
  margin: 6px 0 0;
  color: var(--cp-muted);
  font-size: 13px;
  line-height: 1.6;
}

.dashboard-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.dashboard-action {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--cp-border);
  border-radius: 12px;
  background: var(--cp-surface);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.dashboard-action:hover {
  border-color: #a6c4d2;
  box-shadow: 0 8px 22px rgba(23, 74, 104, 0.08);
  transform: translateY(-2px);
}

.dashboard-action__icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 9px;
  color: var(--cp-primary);
  background: var(--cp-primary-light);
}

.dashboard-action__copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.dashboard-action__copy strong {
  overflow: hidden;
  color: var(--cp-ink);
  font-size: 13px;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-action__copy small {
  overflow: hidden;
  color: var(--cp-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-action__arrow {
  flex: 0 0 auto;
  color: #8ba0ab;
}

.dashboard-tip {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  border: 1px solid #f2dfcf;
  border-radius: 14px;
  background: #fffaf6;
}

.dashboard-tip__mark {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 11px;
  color: #a4552d;
  background: #fceddf;
  font-size: 12px;
  font-weight: 800;
}

@media (max-width: 1120px) {
  .dashboard-stats,
  .dashboard-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .dashboard-stats,
  .dashboard-actions {
    grid-template-columns: 1fr;
  }
}
</style>
