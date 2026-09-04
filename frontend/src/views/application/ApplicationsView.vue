<template>
  <div class="page-container applications-page">
    <PageHeader
      title="投递管理"
      description="把每一次真实投递放进清晰的流程里，及时记录测评、面试和 Offer。"
    >
      <template #actions>
        <el-button type="primary" @click="goToJobs">从岗位创建投递</el-button>
      </template>
    </PageHeader>

    <section class="stage-overview" aria-label="投递阶段概览">
      <div v-for="item in stageOverview" :key="item.stage" class="stage-overview__item">
        <span class="stage-overview__label">{{ item.label }}</span>
        <strong>{{ item.count }}</strong>
        <span class="stage-overview__hint">{{ item.hint }}</span>
      </div>
    </section>

    <el-card shadow="never" class="applications-card">
      <template #header>
        <div class="filter-bar">
          <div>
            <h2>我的投递</h2>
            <p>历史投递会保留岗位快照，方便回顾每次求职过程。</p>
          </div>
          <div class="filter-bar__controls">
            <el-select v-model="stageFilter" clearable placeholder="全部阶段" class="stage-filter" @change="loadApplications">
              <el-option v-for="option in stageOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-select v-model="jobFilter" clearable filterable placeholder="全部岗位" class="job-filter" @change="loadApplications">
              <el-option v-for="job in jobs" :key="job.id" :label="jobOptionLabel(job)" :value="job.id" />
            </el-select>
            <el-button :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && applications.length === 0" description="还没有符合条件的投递">
          <el-button type="primary" @click="goToJobs">去岗位列表创建第一条投递</el-button>
        </el-empty>
        <el-table v-else :data="applications" stripe row-key="id">
          <el-table-column label="岗位" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="job-cell">
                <strong>{{ row.jobTitleSnapshot }}</strong>
                <span>{{ row.companyNameSnapshot }}<template v-if="row.locationSnapshot"> · {{ row.locationSnapshot }}</template></span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="当前阶段" width="130">
            <template #default="{ row }">
              <el-tag :type="stageTagType(row.currentStage)" effect="light">{{ stageLabel(row.currentStage) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="投递时间" width="170">
            <template #default="{ row }">{{ formatDate(row.appliedAt) }}</template>
          </el-table-column>
          <el-table-column label="最近更新" width="170">
            <template #default="{ row }">{{ formatDate(row.updatedAt ?? row.appliedAt) }}</template>
          </el-table-column>
          <el-table-column label="结束原因" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.currentStage === 'ENDED' ? endReasonLabel(row.endReason) : '进行中' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.id)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listApplications } from '@/api/application'
import { listJobs } from '@/api/career'
import PageHeader from '@/components/common/PageHeader.vue'
import type { Job } from '@/types/career'
import type { Application, ApplicationEndReason, ApplicationStage } from '@/types/application'

const router = useRouter()

const applications = ref<Application[]>([])
const jobs = ref<Job[]>([])
const stageFilter = ref<ApplicationStage | undefined>()
const jobFilter = ref<number | undefined>()
const loading = ref(false)

const stageOptions: Array<{ value: ApplicationStage; label: string }> = [
  { value: 'APPLIED', label: '已投递' },
  { value: 'ASSESSMENT', label: '测评中' },
  { value: 'INTERVIEW', label: '面试中' },
  { value: 'OFFER', label: 'Offer 处理中' },
  { value: 'ENDED', label: '已结束' },
]

const stageOverview = computed(() => [
  { stage: 'active', label: '进行中的投递', count: applications.value.filter((item) => item.currentStage !== 'ENDED').length, hint: '保持节奏' },
  ...stageOptions.map((option) => ({
    stage: option.value,
    label: option.label,
    count: applications.value.filter((item) => item.currentStage === option.value).length,
    hint: option.value === 'ENDED' ? '沉淀经验' : '持续跟进',
  })),
])

function stageLabel(value: ApplicationStage): string {
  return stageOptions.find((option) => option.value === value)?.label ?? value
}

function stageTagType(value: ApplicationStage): 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'ENDED') return 'info'
  if (value === 'OFFER') return 'success'
  if (value === 'INTERVIEW') return 'warning'
  return 'success'
}

function endReasonLabel(value?: ApplicationEndReason | null): string {
  const labels: Record<ApplicationEndReason, string> = {
    COMPANY_REJECTED: '公司拒绝',
    NO_RESPONSE: '长时间无回应',
    NO_LONGER_INTERESTED: '不再感兴趣',
    ACCEPTED_ANOTHER_OFFER: '已接受其他 Offer',
    LOCATION_ISSUE: '地点不合适',
    COMPENSATION: '薪酬不合适',
    PERSONAL_REASON: '个人原因',
    OTHER: '其他原因',
    OFFER_ACCEPTED: '接受 Offer',
    OFFER_REJECTED: '拒绝 Offer',
  }
  return value ? labels[value] ?? value : '未填写'
}

function formatDate(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function jobOptionLabel(job: Job): string {
  return `${job.title}${job.city ? ` · ${job.city}` : ''}`
}

async function loadJobs(): Promise<void> {
  try {
    const [active, archived] = await Promise.all([listJobs(false), listJobs(true)])
    const byId = new Map<number, Job>()
    ;[...active, ...archived].forEach((job) => byId.set(job.id, job))
    jobs.value = [...byId.values()].sort((left, right) => left.title.localeCompare(right.title, 'zh-CN'))
  } catch {
    // The response interceptor presents the structured API error.
  }
}

async function loadApplications(): Promise<void> {
  loading.value = true
  try {
    applications.value = await listApplications({
      currentStage: stageFilter.value,
      jobId: jobFilter.value,
    })
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    loading.value = false
  }
}

async function load(): Promise<void> {
  await Promise.all([loadJobs(), loadApplications()])
}

function openDetail(id: number): void {
  void router.push({ name: 'application-detail', params: { applicationId: id } })
}

function goToJobs(): void {
  void router.push({ name: 'career-jobs' })
}

onMounted(load)
</script>

<style scoped>
.applications-page { max-width: 1320px; }
.stage-overview { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; margin-bottom: 20px; }
.stage-overview__item { display: flex; min-height: 116px; flex-direction: column; justify-content: space-between; padding: 17px 18px; border: 1px solid var(--cp-border); border-radius: 12px; background: var(--cp-surface); }
.stage-overview__item:first-child { color: white; border-color: var(--cp-primary); background: linear-gradient(135deg, var(--cp-primary), #236584); }
.stage-overview__label { color: var(--cp-muted); font-size: 12px; }
.stage-overview__item:first-child .stage-overview__label, .stage-overview__item:first-child .stage-overview__hint { color: rgba(255, 255, 255, .75); }
.stage-overview strong { color: var(--cp-ink); font-size: 28px; font-weight: 750; line-height: 1; }
.stage-overview__item:first-child strong { color: white; }
.stage-overview__hint { color: #8a99a1; font-size: 11px; }
.applications-card { border-radius: 14px; }
.filter-bar { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.filter-bar h2 { margin: 0; color: var(--cp-ink); font-size: 19px; }
.filter-bar p { margin: 6px 0 0; color: var(--cp-muted); font-size: 13px; }
.filter-bar__controls { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; }
.stage-filter { width: 150px; }
.job-filter { width: 220px; }
.table-wrap { min-height: 240px; }
.job-cell { display: flex; flex-direction: column; gap: 4px; }
.job-cell strong { color: var(--cp-ink); font-weight: 700; }
.job-cell span { color: var(--cp-muted); font-size: 12px; }
@media (max-width: 1120px) { .stage-overview { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 720px) { .filter-bar { align-items: flex-start; flex-direction: column; } .filter-bar__controls { width: 100%; justify-content: flex-start; } .stage-filter, .job-filter { flex: 1; min-width: 140px; } }
@media (max-width: 480px) { .stage-overview { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
