<template>
  <div class="page-shell">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div v-if="plan" class="page-heading">
          <div>
            <el-button link type="primary" class="back-button" @click="goBack">← 返回计划列表</el-button>
            <h2>{{ plan.mainGoal }}</h2>
            <p>{{ plan.weekStart }} — {{ plan.weekEnd }} · {{ statusLabel(plan.status) }}</p>
          </div>
          <el-tag :type="statusTag(plan.status)" size="large">{{ statusLabel(plan.status) }}</el-tag>
        </div>
      </template>
      <el-skeleton v-if="loading && !plan" :rows="4" animated />
      <el-empty v-else-if="!plan" description="学习计划不存在或暂时无法加载" />
      <div v-else>
        <el-tabs v-model="activeTab">
          <el-tab-pane label="学习任务" name="tasks">
            <div class="section-heading">
              <div>
                <h3>任务清单</h3>
                <span class="muted">在计划周期内安排任务，后端会校验截止日期和时长。</span>
              </div>
              <el-button type="primary" @click="openTaskCreate">新增任务</el-button>
            </div>
            <div v-loading="tasksLoading" class="table-wrap">
              <el-empty v-if="!tasksLoading && tasks.length === 0" description="还没有学习任务" />
              <el-table v-else :data="tasks" stripe>
                <el-table-column prop="title" label="任务" min-width="240" show-overflow-tooltip />
                <el-table-column label="状态" width="130">
                  <template #default="{ row }">
                    <el-tag :type="taskStatusTag(row.status)">{{ taskStatusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="计划用时" width="110">
                  <template #default="{ row }">{{ row.plannedMinutes ? `${row.plannedMinutes} 分钟` : '—' }}</template>
                </el-table-column>
                <el-table-column prop="dueDate" label="截止日期" width="130" />
                <el-table-column label="操作" width="250" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openTaskEdit(row)">编辑</el-button>
                    <el-button link type="primary" @click="openRecords(row)">学习记录</el-button>
                    <el-button link type="danger" @click="removeTask(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="学习记录" name="records">
            <div class="section-heading section-heading--wrap">
              <div>
                <h3>学习记录</h3>
                <span class="muted">选择任务后记录实际学习时长和内容。</span>
              </div>
              <div class="record-toolbar">
                <el-select
                  v-model="selectedTaskId"
                  placeholder="选择任务"
                  class="task-select"
                  :disabled="tasks.length === 0"
                  @change="loadRecords"
                >
                  <el-option v-for="task in tasks" :key="task.id" :label="task.title" :value="task.id" />
                </el-select>
                <el-button type="primary" :disabled="!selectedTaskId" @click="openRecordCreate">新增记录</el-button>
              </div>
            </div>
            <div v-loading="recordsLoading" class="table-wrap">
              <el-empty v-if="!recordsLoading && !selectedTaskId" description="请先选择一个任务" />
              <el-empty v-else-if="!recordsLoading && records.length === 0" description="这个任务还没有学习记录" />
              <el-table v-else :data="records" stripe>
                <el-table-column prop="studiedAt" label="学习时间" width="190" />
                <el-table-column label="时长" width="110">
                  <template #default="{ row }">{{ row.durationMinutes }} 分钟</template>
                </el-table-column>
                <el-table-column prop="content" label="学习内容" min-width="280" show-overflow-tooltip />
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openRecordEdit(row)">编辑</el-button>
                    <el-button link type="danger" @click="removeRecord(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="周复盘" name="review">
            <div class="section-heading">
              <div>
                <h3>周复盘</h3>
                <span class="muted">记录本周成果、问题和下一步计划。</span>
              </div>
              <el-tag v-if="review" type="success">已保存 · {{ review.reviewedAt }}</el-tag>
              <el-tag v-else type="info">尚未复盘</el-tag>
            </div>
            <el-alert
              v-if="!review"
              title="首次打开时没有复盘记录是正常状态，填写后保存即可创建。"
              type="info"
              :closable="false"
              show-icon
              class="review-alert"
            />
            <el-form
              ref="reviewFormRef"
              v-loading="reviewLoading"
              :model="reviewForm"
              :rules="reviewRules"
              label-position="top"
              class="review-form"
              @submit.prevent="saveReview"
            >
              <div class="form-grid">
                <el-form-item label="复盘时间" prop="reviewedAt">
                  <el-date-picker
                    v-model="reviewForm.reviewedAt"
                    type="datetime"
                    value-format="YYYY-MM-DDTHH:mm:ss"
                    placeholder="选择复盘时间"
                    class="full-width"
                  />
                </el-form-item>
              </div>
              <el-form-item label="总结">
                <el-input v-model="reviewForm.summary" type="textarea" :rows="3" maxlength="16000" show-word-limit />
              </el-form-item>
              <div class="form-grid">
                <el-form-item label="本周成果">
                  <el-input v-model="reviewForm.achievements" type="textarea" :rows="4" maxlength="16000" show-word-limit />
                </el-form-item>
                <el-form-item label="遇到的问题">
                  <el-input v-model="reviewForm.problems" type="textarea" :rows="4" maxlength="16000" show-word-limit />
                </el-form-item>
              </div>
              <el-form-item label="下一步">
                <el-input v-model="reviewForm.nextSteps" type="textarea" :rows="3" maxlength="16000" show-word-limit />
              </el-form-item>
              <div class="form-actions">
                <el-button type="primary" :loading="savingReview" @click="saveReview">保存复盘</el-button>
              </div>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-card>

    <el-dialog v-model="taskDialogVisible" :title="editingTaskId === null ? '新增学习任务' : '编辑学习任务'" width="600px" destroy-on-close>
      <el-form ref="taskFormRef" :model="taskForm" :rules="taskRules" label-position="top" @submit.prevent="saveTask">
        <el-form-item label="任务标题" prop="title">
          <el-input v-model="taskForm.title" maxlength="200" show-word-limit placeholder="例如：完成响应式布局练习" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input v-model="taskForm.description" type="textarea" :rows="3" maxlength="16000" show-word-limit />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="状态" prop="status">
            <el-select v-model="taskForm.status" class="full-width">
              <el-option v-for="option in taskStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="计划用时（分钟）" prop="plannedMinutes">
            <el-input-number v-model="taskForm.plannedMinutes" :min="1" :step="5" controls-position="right" class="full-width" />
          </el-form-item>
          <el-form-item label="截止日期">
            <el-date-picker v-model="taskForm.dueDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disableTaskDate" class="full-width" />
          </el-form-item>
          <el-form-item label="排序值">
            <el-input-number v-model="taskForm.sortOrder" :min="0" :step="1" controls-position="right" class="full-width" />
          </el-form-item>
        </div>
        <p class="form-tip">截止日期必须在 {{ plan?.weekStart }} 至 {{ plan?.weekEnd }} 之间。</p>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTask" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recordDialogVisible" :title="editingRecordId === null ? '新增学习记录' : '编辑学习记录'" width="560px" destroy-on-close>
      <el-form ref="recordFormRef" :model="recordForm" :rules="recordRules" label-position="top" @submit.prevent="saveRecord">
        <el-form-item label="学习时间" prop="studiedAt">
          <el-date-picker v-model="recordForm.studiedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择学习时间" class="full-width" />
        </el-form-item>
        <el-form-item label="学习时长（分钟）" prop="durationMinutes">
          <el-input-number v-model="recordForm.durationMinutes" :min="1" :step="5" controls-position="right" class="full-width" />
        </el-form-item>
        <el-form-item label="学习内容">
          <el-input v-model="recordForm.content" type="textarea" :rows="5" maxlength="16000" show-word-limit placeholder="记录看过的资料、完成的练习或遇到的问题" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRecord" @click="saveRecord">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  createLearningTask,
  createStudyRecord,
  deleteLearningTask,
  deleteStudyRecord,
  getLearningPlan,
  getWeeklyReview,
  listLearningTasks,
  listStudyRecords,
  updateLearningTask,
  updateStudyRecord,
  updateWeeklyReview,
} from '@/api/learning'
import type {
  LearningPlan,
  LearningPlanStatus,
  LearningTask,
  LearningTaskRequest,
  LearningTaskStatus,
  StudyRecord,
  StudyRecordRequest,
  WeeklyReview,
  WeeklyReviewRequest,
} from '@/types/learning'

const route = useRoute()
const router = useRouter()
const planId = computed(() => Number(route.params.planId))

const plan = ref<LearningPlan | null>(null)
const tasks = ref<LearningTask[]>([])
const records = ref<StudyRecord[]>([])
const review = ref<WeeklyReview | null>(null)
const loading = ref(false)
const tasksLoading = ref(false)
const recordsLoading = ref(false)
const reviewLoading = ref(false)
const savingTask = ref(false)
const savingRecord = ref(false)
const savingReview = ref(false)
const activeTab = ref('tasks')
const selectedTaskId = ref<number | null>(null)

const taskDialogVisible = ref(false)
const editingTaskId = ref<number | null>(null)
const taskFormRef = ref<FormInstance>()
const recordDialogVisible = ref(false)
const editingRecordId = ref<number | null>(null)
const recordFormRef = ref<FormInstance>()
const reviewFormRef = ref<FormInstance>()

const taskStatusOptions: Array<{ value: LearningTaskStatus; label: string }> = [
  { value: 'TODO', label: '待开始' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'DONE', label: '已完成' },
  { value: 'SKIPPED', label: '已跳过' },
]

function emptyTaskForm(): LearningTaskRequest {
  return { title: '', description: '', status: 'TODO', plannedMinutes: undefined, dueDate: undefined, sortOrder: 0 }
}

function emptyRecordForm(): StudyRecordRequest {
  return { studiedAt: nowLocalDateTime(), durationMinutes: 30, content: '' }
}

const taskForm = reactive<LearningTaskRequest>(emptyTaskForm())
const recordForm = reactive<StudyRecordRequest>(emptyRecordForm())
const taskRules: FormRules = {
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
  status: [{ required: true, message: '请选择任务状态', trigger: 'change' }],
  plannedMinutes: [{ type: 'number', min: 1, message: '计划用时必须大于 0', trigger: 'change' }],
}
const recordRules: FormRules = {
  studiedAt: [{ required: true, message: '请选择学习时间', trigger: 'change' }],
  durationMinutes: [{ required: true, type: 'number', min: 1, message: '学习时长必须大于 0', trigger: 'change' }],
}

const reviewForm = reactive<WeeklyReviewRequest>({
  summary: '',
  achievements: '',
  problems: '',
  nextSteps: '',
  reviewedAt: nowLocalDateTime(),
})
const reviewRules: FormRules = {
  reviewedAt: [{ required: true, message: '请选择复盘时间', trigger: 'change' }],
}

function nowLocalDateTime(): string {
  const date = new Date()
  const pad = (value: number): string => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function statusLabel(status: LearningPlanStatus): string {
  return { PLANNED: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成' }[status]
}

function statusTag(status: LearningPlanStatus): 'info' | 'warning' | 'success' {
  if (status === 'IN_PROGRESS') return 'warning'
  if (status === 'COMPLETED') return 'success'
  return 'info'
}

function taskStatusLabel(status: LearningTaskStatus): string {
  return taskStatusOptions.find((option) => option.value === status)?.label ?? status
}

function taskStatusTag(status: LearningTaskStatus): 'info' | 'warning' | 'success' | 'danger' {
  if (status === 'DONE') return 'success'
  if (status === 'IN_PROGRESS') return 'warning'
  if (status === 'SKIPPED') return 'danger'
  return 'info'
}

function isNotFound(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 404
}

async function load(): Promise<void> {
  const id = planId.value
  if (!Number.isInteger(id) || id <= 0) return
  loading.value = true
  try {
    const [loadedPlan, loadedTasks] = await Promise.all([getLearningPlan(id), listLearningTasks(id)])
    plan.value = loadedPlan
    tasks.value = loadedTasks
    if (!tasks.value.some((task) => task.id === selectedTaskId.value)) {
      selectedTaskId.value = tasks.value[0]?.id ?? null
    }
    await Promise.all([loadReview(id), loadRecords(selectedTaskId.value)])
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    loading.value = false
  }
}

async function loadReview(id = planId.value): Promise<void> {
  reviewLoading.value = true
  try {
    review.value = await getWeeklyReview(id)
    Object.assign(reviewForm, {
      summary: review.value.summary ?? '',
      achievements: review.value.achievements ?? '',
      problems: review.value.problems ?? '',
      nextSteps: review.value.nextSteps ?? '',
      reviewedAt: review.value.reviewedAt,
    })
  } catch (error: unknown) {
    if (isNotFound(error)) {
      review.value = null
      Object.assign(reviewForm, { summary: '', achievements: '', problems: '', nextSteps: '', reviewedAt: nowLocalDateTime() })
    }
  } finally {
    reviewLoading.value = false
  }
}

async function loadRecords(taskId = selectedTaskId.value): Promise<void> {
  if (!taskId) {
    records.value = []
    return
  }
  selectedTaskId.value = taskId
  recordsLoading.value = true
  try {
    records.value = await listStudyRecords(taskId)
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    recordsLoading.value = false
  }
}

function goBack(): void {
  void router.push({ name: 'learning-plans' })
}

function openTaskCreate(): void {
  editingTaskId.value = null
  Object.assign(taskForm, emptyTaskForm())
  taskDialogVisible.value = true
}

function openTaskEdit(row: LearningTask): void {
  editingTaskId.value = row.id
  Object.assign(taskForm, {
    title: row.title,
    description: row.description ?? '',
    status: row.status,
    plannedMinutes: row.plannedMinutes ?? undefined,
    dueDate: row.dueDate ?? undefined,
    sortOrder: row.sortOrder ?? 0,
  })
  taskDialogVisible.value = true
}

function disableTaskDate(date: Date): boolean {
  if (!plan.value) return false
  const value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return value < plan.value.weekStart || value > plan.value.weekEnd
}

async function saveTask(): Promise<void> {
  const valid = await taskFormRef.value?.validate().catch(() => false)
  if (!valid || !plan.value) return
  if (taskForm.dueDate && (taskForm.dueDate < plan.value.weekStart || taskForm.dueDate > plan.value.weekEnd)) {
    ElMessage.warning('截止日期必须在计划周期内')
    return
  }
  if (!taskForm.title.trim()) return
  savingTask.value = true
  const payload: LearningTaskRequest = {
    title: taskForm.title.trim(),
    description: taskForm.description?.trim() || undefined,
    status: taskForm.status,
    plannedMinutes: taskForm.plannedMinutes,
    dueDate: taskForm.dueDate || undefined,
    sortOrder: taskForm.sortOrder ?? undefined,
  }
  try {
    const id = editingTaskId.value
    const isCreate = id === null
    const saved = isCreate
      ? await createLearningTask(plan.value.id, payload)
      : await updateLearningTask(plan.value.id, id, payload)
    taskDialogVisible.value = false
    selectedTaskId.value = saved.id
    ElMessage.success(isCreate ? '学习任务已创建' : '学习任务已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    savingTask.value = false
  }
}

async function removeTask(row: LearningTask): Promise<void> {
  if (!plan.value) return
  try {
    await ElMessageBox.confirm(`确定删除“${row.title}”吗？该任务的学习记录也会被删除。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteLearningTask(plan.value.id, row.id)
    ElMessage.success('学习任务已删除')
    if (selectedTaskId.value === row.id) selectedTaskId.value = null
    await load()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

function openRecords(row: LearningTask): void {
  selectedTaskId.value = row.id
  activeTab.value = 'records'
  void loadRecords(row.id)
}

function openRecordCreate(): void {
  if (!selectedTaskId.value) return
  editingRecordId.value = null
  Object.assign(recordForm, emptyRecordForm())
  recordDialogVisible.value = true
}

function openRecordEdit(row: StudyRecord): void {
  if (!selectedTaskId.value) return
  editingRecordId.value = row.id
  Object.assign(recordForm, {
    studiedAt: row.studiedAt,
    durationMinutes: row.durationMinutes,
    content: row.content ?? '',
  })
  recordDialogVisible.value = true
}

async function saveRecord(): Promise<void> {
  const valid = await recordFormRef.value?.validate().catch(() => false)
  if (!valid || !selectedTaskId.value || !recordForm.studiedAt || !recordForm.durationMinutes) return
  savingRecord.value = true
  const payload: StudyRecordRequest = {
    studiedAt: recordForm.studiedAt,
    durationMinutes: recordForm.durationMinutes,
    content: recordForm.content?.trim() || undefined,
  }
  try {
    const id = editingRecordId.value
    const isCreate = id === null
    if (isCreate) {
      await createStudyRecord(selectedTaskId.value, payload)
    } else {
      await updateStudyRecord(selectedTaskId.value, id, payload)
    }
    recordDialogVisible.value = false
    ElMessage.success(isCreate ? '学习记录已创建' : '学习记录已更新')
    await loadRecords()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    savingRecord.value = false
  }
}

async function removeRecord(row: StudyRecord): Promise<void> {
  if (!selectedTaskId.value) return
  try {
    await ElMessageBox.confirm('确定删除这条学习记录吗？', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteStudyRecord(selectedTaskId.value, row.id)
    ElMessage.success('学习记录已删除')
    await loadRecords()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

async function saveReview(): Promise<void> {
  const valid = await reviewFormRef.value?.validate().catch(() => false)
  if (!valid || !plan.value || !reviewForm.reviewedAt) {
    if (!reviewForm.reviewedAt) ElMessage.warning('请选择复盘时间')
    return
  }
  savingReview.value = true
  const payload: WeeklyReviewRequest = {
    summary: reviewForm.summary?.trim() || undefined,
    achievements: reviewForm.achievements?.trim() || undefined,
    problems: reviewForm.problems?.trim() || undefined,
    nextSteps: reviewForm.nextSteps?.trim() || undefined,
    reviewedAt: reviewForm.reviewedAt,
  }
  try {
    review.value = await updateWeeklyReview(plan.value.id, payload)
    Object.assign(reviewForm, {
      summary: review.value.summary ?? '',
      achievements: review.value.achievements ?? '',
      problems: review.value.problems ?? '',
      nextSteps: review.value.nextSteps ?? '',
      reviewedAt: review.value.reviewedAt,
    })
    ElMessage.success('周复盘已保存')
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    savingReview.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-shell { max-width: 1220px; margin: 0 auto; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-heading h2 { margin: 4px 0 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.back-button { padding: 0; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 4px 0 16px; }
.section-heading--wrap { flex-wrap: wrap; }
.section-heading h3 { margin: 0 0 5px; font-size: 16px; color: var(--el-text-color-primary); }
.muted { color: var(--el-text-color-secondary); font-size: 13px; }
.table-wrap { min-height: 180px; }
.record-toolbar { display: flex; align-items: center; gap: 12px; }
.task-select { width: 260px; }
.review-alert { margin-bottom: 18px; }
.review-form { padding-top: 4px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
.form-actions { display: flex; justify-content: flex-end; margin-top: 4px; }
.form-tip { margin: -4px 0 0; color: var(--el-text-color-secondary); font-size: 12px; }
@media (max-width: 680px) {
  .page-heading, .section-heading { align-items: flex-start; flex-direction: column; }
  .form-grid { grid-template-columns: 1fr; }
  .record-toolbar { width: 100%; flex-wrap: wrap; }
  .task-select { width: 100%; }
}
</style>
