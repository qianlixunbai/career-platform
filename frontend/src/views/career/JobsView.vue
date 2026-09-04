<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>岗位</h2>
            <p>记录求职中关注的岗位；归档只改变状态，不会删除岗位。</p>
          </div>
          <div class="heading-actions">
            <el-switch v-model="showArchived" inline-prompt active-text="已归档" inactive-text="进行中" @change="load" />
            <el-button type="primary" @click="openCreate">新增岗位</el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && jobs.length === 0" :description="showArchived ? '还没有已归档岗位' : '还没有进行中的岗位'" />
        <el-table v-else :data="jobs" stripe>
          <el-table-column prop="title" label="岗位名称" min-width="210" show-overflow-tooltip />
          <el-table-column label="公司" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ companyName(row.companyId) }}</template>
          </el-table-column>
          <el-table-column prop="city" label="城市" width="120" show-overflow-tooltip />
          <el-table-column label="岗位类型" width="130">
            <template #default="{ row }">{{ jobTypeLabel(row.jobType) }}</template>
          </el-table-column>
          <el-table-column prop="deadline" label="截止日期" width="130" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.archived ? 'info' : 'success'">{{ row.archived ? '已归档' : '进行中' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="230" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.id)">查看详情</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="!row.archived" link type="warning" @click="archive(row)">归档</el-button>
              <el-button v-else link type="success" @click="unarchive(row)">恢复</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增岗位' : '编辑岗位'" width="680px" destroy-on-close>
      <el-form :model="form" label-position="top" @submit.prevent="save">
        <div class="form-grid">
          <el-form-item label="公司" required>
            <el-select v-model="form.companyId" class="full-width" filterable placeholder="选择公司">
              <el-option v-for="company in companies" :key="company.id" :label="company.name" :value="company.id" />
            </el-select>
            <span v-if="companies.length === 0" class="form-tip">请先在“公司”页面创建公司。</span>
          </el-form-item>
          <el-form-item label="岗位名称" required>
            <el-input v-model="form.title" maxlength="150" placeholder="例如：Java 后端工程师" />
          </el-form-item>
          <el-form-item label="岗位类型" required>
            <el-select v-model="form.jobType" class="full-width" placeholder="选择岗位类型">
              <el-option v-for="option in jobTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="城市">
            <el-input v-model="form.city" maxlength="100" placeholder="例如：上海" />
          </el-form-item>
          <el-form-item label="发布日期">
            <el-date-picker v-model="form.publishDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="full-width" />
          </el-form-item>
          <el-form-item label="截止日期">
            <el-date-picker v-model="form.deadline" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="full-width" />
          </el-form-item>
          <el-form-item label="来源类型" required>
            <el-select v-model="form.sourceType" class="full-width" placeholder="选择来源">
              <el-option v-for="option in sourceTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源名称">
            <el-input v-model="form.sourceName" maxlength="150" placeholder="例如：牛客 / 官网" />
          </el-form-item>
        </div>
        <el-form-item label="来源链接">
          <el-input v-model="form.sourceUrl" maxlength="500" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="原始 JD">
          <el-input v-model="form.rawJd" type="textarea" :rows="7" maxlength="16000" show-word-limit placeholder="粘贴岗位描述，便于在详情页查看和整理要求" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { archiveJob, createJob, listCompanies, listJobs, unarchiveJob, updateJob } from '@/api/career'
import type { Company, Job, JobRequest, JobType, SourceType } from '@/types/career'

const router = useRouter()
const jobs = ref<Job[]>([])
const companies = ref<Company[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const showArchived = ref(false)

const jobTypeOptions: Array<{ value: JobType; label: string }> = [
  { value: 'FULL_TIME', label: '全职' },
  { value: 'INTERNSHIP', label: '实习' },
  { value: 'CAMPUS', label: '校招' },
  { value: 'PART_TIME', label: '兼职' },
  { value: 'CONTRACT', label: '合同制' },
  { value: 'OTHER', label: '其他' },
]
const sourceTypeOptions: Array<{ value: SourceType; label: string }> = [
  { value: 'MANUAL', label: '手动记录' },
  { value: 'CAMPUS_SITE', label: '校园招聘网站' },
  { value: 'COMPANY_WEBSITE', label: '公司官网' },
  { value: 'RECRUITMENT_PLATFORM', label: '招聘平台' },
  { value: 'REFERRAL', label: '内推' },
  { value: 'OTHER', label: '其他' },
]

function emptyForm(): JobRequest {
  return {
    companyId: 0,
    title: '',
    city: '',
    jobType: 'FULL_TIME',
    publishDate: '',
    deadline: '',
    rawJd: '',
    sourceType: 'MANUAL',
    sourceName: '',
    sourceUrl: '',
  }
}

const form = reactive<JobRequest>(emptyForm())

function jobTypeLabel(value: JobType): string {
  return jobTypeOptions.find((option) => option.value === value)?.label ?? value
}

function companyName(companyId: number): string {
  return companies.value.find((company) => company.id === companyId)?.name ?? `公司 #${companyId}`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [jobItems, companyItems] = await Promise.all([listJobs(showArchived.value), listCompanies()])
    jobs.value = jobItems
    companies.value = companyItems
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, emptyForm(), { companyId: companies.value[0]?.id ?? 0 })
  dialogVisible.value = true
}

function openEdit(row: Job): void {
  editingId.value = row.id
  Object.assign(form, {
    companyId: row.companyId,
    title: row.title,
    city: row.city ?? '',
    jobType: row.jobType,
    publishDate: row.publishDate ?? '',
    deadline: row.deadline ?? '',
    rawJd: row.rawJd ?? '',
    sourceType: row.sourceType,
    sourceName: row.sourceName ?? '',
    sourceUrl: row.sourceUrl ?? '',
  })
  dialogVisible.value = true
}

function openDetail(id: number): void {
  router.push(`/career/jobs/${id}`)
}

async function save(): Promise<void> {
  saving.value = true
  const payload: JobRequest = {
    companyId: form.companyId,
    title: form.title.trim(),
    city: form.city?.trim() || undefined,
    jobType: form.jobType,
    publishDate: form.publishDate || undefined,
    deadline: form.deadline || undefined,
    rawJd: form.rawJd?.trim() || undefined,
    sourceType: form.sourceType,
    sourceName: form.sourceName?.trim() || undefined,
    sourceUrl: form.sourceUrl?.trim() || undefined,
  }
  try {
    const id = editingId.value
    const isCreate = id === null
    if (isCreate) {
      await createJob(payload)
    } else {
      await updateJob(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '岗位已新增' : '岗位已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

async function archive(row: Job): Promise<void> {
  try {
    await ElMessageBox.confirm(`归档“${row.title}”后，它会从进行中列表隐藏，确定继续吗？`, '确认归档', { type: 'warning' })
    await archiveJob(row.id)
    ElMessage.success('岗位已归档')
    await load()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

async function unarchive(row: Job): Promise<void> {
  try {
    await unarchiveJob(row.id)
    ElMessage.success('岗位已恢复')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  }
}

onMounted(load)
</script>

<style scoped>
.page-shell { max-width: 1260px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.heading-actions { display: flex; align-items: center; gap: 16px; }
.table-wrap { min-height: 180px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
.form-tip { display: block; margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; }
@media (max-width: 720px) { .page-heading { align-items: flex-start; flex-direction: column; } .heading-actions { width: 100%; justify-content: space-between; } .form-grid { grid-template-columns: 1fr; } }
</style>
