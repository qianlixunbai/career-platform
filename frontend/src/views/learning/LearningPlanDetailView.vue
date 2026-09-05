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
              <div class="review-heading-actions">
                <el-button type="primary" plain :loading="aiReviewLoading" @click="openAiReviewDialog">AI 辅助周复盘</el-button>
                <el-tag v-if="review" type="success">已保存 · {{ review.reviewedAt }}</el-tag>
                <el-tag v-else type="info">尚未复盘</el-tag>
              </div>
            </div>
            <el-alert
              v-if="review"
              title="已有复盘存在"
              description="AI 建议不会自动覆盖已保存内容。你可以查看建议，应用到当前表单后，再自行点击“保存复盘”。"
              type="warning"
              :closable="false"
              show-icon
              class="review-alert"
            />
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

    <el-dialog
      v-model="aiReviewDialogVisible"
      title="AI 辅助周复盘"
      width="860px"
      top="5vh"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-alert
        v-if="aiReviewError"
        :title="aiReviewError"
        type="error"
        :closable="false"
        show-icon
        class="review-ai-alert"
      />
      <template v-if="aiReviewSuggestion">
        <el-alert
          v-if="review"
          title="已有复盘存在，AI 不会自动覆盖"
          description="“应用到当前表单”只会填充下方表单，不会发起保存请求。请确认内容后再点击表单底部的“保存复盘”。"
          type="warning"
          :closable="false"
          show-icon
          class="review-ai-alert"
        />
        <div class="review-ai-scroll">
          <section class="review-ai-panel">
            <h3>Java 计算的本周指标</h3>
            <p class="muted">这些数字来自当前计划的任务和学习记录，不由 AI 重新计算。</p>
            <div class="review-ai-metrics">
              <div><span>完成任务</span><strong>{{ aiReviewSuggestion.metrics.doneCount }} / {{ aiReviewSuggestion.metrics.taskCount }}</strong></div>
              <div><span>完成率</span><strong>{{ completionRateLabel(aiReviewSuggestion.metrics.completionRate) }}</strong></div>
              <div><span>计划时间</span><strong>{{ aiReviewSuggestion.metrics.plannedMinutes }} 分钟</strong></div>
              <div><span>实际记录</span><strong>{{ aiReviewSuggestion.metrics.actualMinutes }} 分钟</strong></div>
              <div><span>待开始</span><strong>{{ aiReviewSuggestion.metrics.todoCount }}</strong></div>
              <div><span>进行中</span><strong>{{ aiReviewSuggestion.metrics.inProgressCount }}</strong></div>
              <div><span>已跳过</span><strong>{{ aiReviewSuggestion.metrics.skippedCount }}</strong></div>
              <div><span>学习记录数</span><strong>{{ aiReviewSuggestion.metrics.studyRecordCount }}</strong></div>
            </div>
            <el-table
              v-if="aiReviewSuggestion.metrics.tasks && aiReviewSuggestion.metrics.tasks.length > 0"
              :data="aiReviewSuggestion.metrics.tasks"
              size="small"
              stripe
              class="review-ai-task-metrics"
            >
              <el-table-column prop="title" label="任务" min-width="200" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">{{ taskStatusLabel(row.status) }}</template>
              </el-table-column>
              <el-table-column label="计划 / 实际" width="130">
                <template #default="{ row }">{{ row.plannedMinutes }} / {{ row.actualMinutes }} 分钟</template>
              </el-table-column>
              <el-table-column prop="studyRecordCount" label="记录数" width="80" />
            </el-table>
          </section>

          <section class="review-ai-panel">
            <h3>来源事实与可信依据</h3>
            <div v-if="aiReviewSuggestion.evidence.length > 0" class="review-ai-evidence-list">
              <div v-for="evidence in aiReviewSuggestion.evidence" :key="evidence.key" class="review-ai-evidence">
                <div class="review-ai-evidence__heading">
                  <strong>{{ evidence.label }}</strong>
                  <el-tag size="small" type="info">{{ evidenceTypeLabel(evidence.type) }}</el-tag>
                </div>
                <p>{{ evidence.excerpt || evidence.fact || evidence.value || 'Java context fact' }}</p>
              </div>
            </div>
            <el-empty v-else :image-size="56" description="本次没有可展示的额外依据" />
          </section>

          <section class="review-ai-panel">
            <h3>AI 建议候选</h3>
            <p class="muted">可先编辑候选内容，再应用到当前复盘表单；应用不会保存。</p>
            <el-form v-if="aiReviewDraft" label-position="top" class="review-ai-form">
              <el-form-item label="总结">
                <el-input v-model="aiReviewDraft.summary" type="textarea" :rows="3" maxlength="16000" show-word-limit />
              </el-form-item>
              <el-form-item label="本周成果">
                <el-input v-model="aiReviewDraft.achievements" type="textarea" :rows="4" maxlength="16000" show-word-limit />
              </el-form-item>
              <div v-if="aiReviewSuggestion.achievementItems.length > 0" class="review-ai-items">
                <span class="ai-field-label">成果明细与依据</span>
                <div v-for="item in aiReviewSuggestion.achievementItems" :key="item.text" class="review-ai-item">
                  <p>{{ item.text }}</p>
                  <div v-if="reviewItemEvidence(item).length > 0" class="review-ai-item__evidence">
                    <span v-for="evidence in reviewItemEvidence(item)" :key="evidence.key">{{ evidence.label }}</span>
                  </div>
                </div>
              </div>
              <el-form-item label="遇到的问题">
                <el-input v-model="aiReviewDraft.problems" type="textarea" :rows="4" maxlength="16000" show-word-limit />
              </el-form-item>
              <div v-if="aiReviewSuggestion.problemItems.length > 0" class="review-ai-items">
                <span class="ai-field-label">问题明细与依据</span>
                <div v-for="item in aiReviewSuggestion.problemItems" :key="item.text" class="review-ai-item">
                  <p>{{ item.text }}</p>
                  <div v-if="reviewItemEvidence(item).length > 0" class="review-ai-item__evidence">
                    <span v-for="evidence in reviewItemEvidence(item)" :key="evidence.key">{{ evidence.label }}</span>
                  </div>
                </div>
              </div>
              <el-form-item label="下一步">
                <el-input v-model="aiReviewDraft.nextSteps" type="textarea" :rows="4" maxlength="16000" show-word-limit />
              </el-form-item>
              <div v-if="aiReviewSuggestion.nextStepItems.length > 0" class="review-ai-items">
                <span class="ai-field-label">下一步明细与依据</span>
                <div v-for="item in aiReviewSuggestion.nextStepItems" :key="item.text" class="review-ai-item">
                  <p>{{ item.text }}</p>
                  <div v-if="reviewItemEvidence(item).length > 0" class="review-ai-item__evidence">
                    <span v-for="evidence in reviewItemEvidence(item)" :key="evidence.key">{{ evidence.label }}</span>
                  </div>
                </div>
              </div>
            </el-form>
            <el-alert
              v-if="aiReviewSuggestion.warnings.length > 0"
              title="AI 提示"
              type="info"
              :closable="false"
              class="review-ai-alert"
            >
              <ul class="review-warning-list">
                <li v-for="warning in aiReviewSuggestion.warnings" :key="warning">{{ warning }}</li>
              </ul>
            </el-alert>
          </section>
        </div>
      </template>
      <el-empty v-else-if="!aiReviewLoading && !aiReviewError" description="暂时没有复盘建议" />
      <el-skeleton v-else-if="aiReviewLoading" :rows="8" animated />
      <template #footer>
        <el-button @click="aiReviewDialogVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!aiReviewDraft" @click="applyAiReview">应用到当前表单</el-button>
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
  suggestWeeklyReviewWithAi,
  updateLearningTask,
  updateStudyRecord,
  updateWeeklyReview,
} from '@/api/learning'
import type {
  LearningPlan,
  LearningAiEvidence,
  WeeklyReviewAiItem,
  WeeklyReviewAiSuggestionResponse,
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
const aiReviewDialogVisible = ref(false)
const aiReviewLoading = ref(false)
const aiReviewError = ref('')
const aiReviewSuggestion = ref<WeeklyReviewAiSuggestionResponse | null>(null)
const aiReviewDraft = ref<{
  summary: string
  achievements: string
  problems: string
  nextSteps: string
} | null>(null)

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

function evidenceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    CAREER_GOAL: '职业目标',
    USER_SKILL: '用户技能',
    JOB_REQUIREMENT: '岗位要求',
    LEARNING_PLAN: '历史计划',
    LEARNING_TASK: '学习任务',
    STUDY_RECORD: '学习记录',
    WEEKLY_REVIEW: '周复盘',
    LEARNING_NOTE: '学习笔记',
    USER_FOCUS: '本周关注',
  }
  return labels[type] ?? type
}

function completionRateLabel(value: number): string {
  if (!Number.isFinite(value)) return '—'
  const percentage = value <= 1 ? value * 100 : value
  return `${percentage.toFixed(1)}%`
}

function reviewItemEvidence(item: WeeklyReviewAiItem): LearningAiEvidence[] {
  const evidence = aiReviewSuggestion.value?.evidence ?? []
  return (item.evidenceKeys ?? [])
    .map((key) => evidence.find((entry) => entry.key === key))
    .filter((entry): entry is LearningAiEvidence => Boolean(entry))
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

async function openAiReviewDialog(): Promise<void> {
  const id = planId.value
  if (!Number.isInteger(id) || id <= 0 || aiReviewLoading.value) return
  aiReviewDialogVisible.value = true
  aiReviewLoading.value = true
  aiReviewError.value = ''
  aiReviewSuggestion.value = null
  aiReviewDraft.value = null
  try {
    const response = await suggestWeeklyReviewWithAi(id)
    const suggestion: WeeklyReviewAiSuggestionResponse = {
      ...response,
      metrics: response.metrics,
      summary: response.summary ?? '',
      achievements: response.achievements ?? '',
      problems: response.problems ?? '',
      nextSteps: response.nextSteps ?? '',
      achievementItems: response.achievementItems ?? [],
      problemItems: response.problemItems ?? [],
      nextStepItems: response.nextStepItems ?? [],
      evidence: response.evidence ?? [],
      warnings: response.warnings ?? [],
    }
    aiReviewSuggestion.value = suggestion
    aiReviewDraft.value = {
      summary: suggestion.summary,
      achievements: suggestion.achievements,
      problems: suggestion.problems,
      nextSteps: suggestion.nextSteps,
    }
  } catch {
    aiReviewError.value = 'AI 周复盘暂时不可用；当前手工复盘表单仍可正常编辑和保存。'
  } finally {
    aiReviewLoading.value = false
  }
}

function applyAiReview(): void {
  if (!aiReviewDraft.value) return
  Object.assign(reviewForm, {
    summary: aiReviewDraft.value.summary,
    achievements: aiReviewDraft.value.achievements,
    problems: aiReviewDraft.value.problems,
    nextSteps: aiReviewDraft.value.nextSteps,
  })
  aiReviewDialogVisible.value = false
  ElMessage.success('AI 建议已应用到当前表单，请点击“保存复盘”完成保存')
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
.review-heading-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 8px; }
.table-wrap { min-height: 180px; }
.record-toolbar { display: flex; align-items: center; gap: 12px; }
.task-select { width: 260px; }
.review-alert { margin-bottom: 18px; }
.review-form { padding-top: 4px; }
.review-ai-alert { margin-bottom: 16px; }
.review-ai-scroll { max-height: 70vh; overflow-y: auto; padding: 2px 4px 4px 2px; }
.review-ai-panel { margin-bottom: 16px; padding: 16px; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.review-ai-panel:last-child { margin-bottom: 0; }
.review-ai-panel h3 { margin: 0 0 6px; font-size: 16px; color: var(--el-text-color-primary); }
.review-ai-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-top: 14px; }
.review-ai-metrics div { display: flex; flex-direction: column; gap: 5px; }
.review-ai-metrics span, .ai-field-label { color: var(--el-text-color-secondary); font-size: 12px; }
.review-ai-metrics strong { color: var(--el-text-color-primary); font-weight: 600; }
.review-ai-task-metrics { margin-top: 16px; }
.review-ai-evidence-list { display: grid; gap: 10px; margin-top: 14px; }
.review-ai-evidence { padding: 11px 12px; border-radius: 6px; background: var(--el-fill-color-light); }
.review-ai-evidence__heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.review-ai-evidence__heading strong { color: var(--el-text-color-primary); font-weight: 500; }
.review-ai-evidence p { margin: 7px 0 0; color: var(--el-text-color-regular); line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; }
.review-ai-form { margin-top: 14px; }
.review-ai-items { margin: -2px 0 16px; padding: 11px 12px; border-radius: 6px; background: var(--el-color-primary-light-9); }
.review-ai-item { margin-top: 9px; padding-top: 9px; border-top: 1px solid var(--el-border-color-lighter); }
.review-ai-item:first-of-type { border-top: 0; }
.review-ai-item p { margin: 0; color: var(--el-text-color-primary); line-height: 1.55; white-space: pre-wrap; overflow-wrap: anywhere; }
.review-ai-item__evidence { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.review-ai-item__evidence span { padding: 2px 7px; border-radius: 4px; color: var(--el-text-color-secondary); background: var(--el-fill-color-light); font-size: 12px; }
.review-warning-list { margin: 0; padding-left: 20px; line-height: 1.7; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
.form-actions { display: flex; justify-content: flex-end; margin-top: 4px; }
.form-tip { margin: -4px 0 0; color: var(--el-text-color-secondary); font-size: 12px; }
@media (max-width: 680px) {
  .page-heading, .section-heading { align-items: flex-start; flex-direction: column; }
  .review-heading-actions { width: 100%; justify-content: flex-start; }
  .form-grid { grid-template-columns: 1fr; }
  .review-ai-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .record-toolbar { width: 100%; flex-wrap: wrap; }
  .task-select { width: 100%; }
}
@media (max-width: 440px) {
  .review-ai-metrics { grid-template-columns: 1fr; }
}
</style>
