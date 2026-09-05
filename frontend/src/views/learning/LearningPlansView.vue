<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>学习计划</h2>
            <p>按周拆解学习目标，并在计划详情中跟踪任务、学习记录和复盘。</p>
          </div>
          <div class="heading-actions">
            <el-button type="primary" plain @click="openAiPlanDialog">AI 生成本周计划</el-button>
            <el-button type="primary" @click="openCreate">新建计划</el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && plans.length === 0" description="还没有学习计划" />
        <el-table v-else :data="plans" stripe>
          <el-table-column label="计划周期" width="230">
            <template #default="{ row }">{{ dateRange(row.weekStart, row.weekEnd) }}</template>
          </el-table-column>
          <el-table-column prop="mainGoal" label="本周目标" min-width="280" show-overflow-tooltip />
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="viewPlan(row.id)">详情</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建学习计划' : '编辑学习计划'"
      width="540px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <div class="form-grid">
          <el-form-item label="开始日期" prop="weekStart">
            <el-date-picker
              v-model="form.weekStart"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择开始日期"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="结束日期" prop="weekEnd">
            <el-date-picker
              v-model="form.weekEnd"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择结束日期"
              class="full-width"
            />
          </el-form-item>
        </div>
        <el-form-item label="本周目标" prop="mainGoal">
          <el-input
            v-model="form.mainGoal"
            maxlength="500"
            show-word-limit
            placeholder="例如：完成 Vue 组件化练习并整理笔记"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" class="full-width" placeholder="选择计划状态">
            <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="aiPlanDialogVisible"
      title="AI 生成本周计划"
      width="900px"
      top="5vh"
      destroy-on-close
      :close-on-click-modal="false"
      @closed="resetAiPlan"
    >
      <el-alert
        v-if="aiPlanError"
        :title="aiPlanError"
        type="error"
        :closable="false"
        show-icon
        class="ai-alert"
      />

      <template v-if="aiPlanStage === 'input'">
        <el-alert
          title="AI 只会生成候选建议，不会自动创建学习计划或任务。"
          type="info"
          :closable="false"
          show-icon
          class="ai-alert"
        />
        <el-form
          ref="aiPlanFormRef"
          :model="aiPlanForm"
          :rules="aiPlanRules"
          label-position="top"
          @submit.prevent="generateAiPlan"
        >
          <div class="form-grid">
            <el-form-item label="计划开始日期" prop="weekStart">
              <el-date-picker
                v-model="aiPlanForm.weekStart"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择开始日期"
                class="full-width"
              />
            </el-form-item>
            <el-form-item label="计划结束日期" prop="weekEnd">
              <el-date-picker
                v-model="aiPlanForm.weekEnd"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择结束日期"
                class="full-width"
              />
            </el-form-item>
          </div>
          <el-form-item label="每周可用时间（分钟）" prop="availableMinutes">
            <el-input-number
              v-model="aiPlanForm.availableMinutes"
              :min="1"
              :max="10080"
              :step="30"
              controls-position="right"
              class="full-width"
            />
            <span class="form-tip">这是硬约束，AI 建议的任务总时长不会超过这个数值。</span>
          </el-form-item>
          <el-form-item label="关联职业目标（可选）" prop="careerGoalId">
            <el-select
              v-model="aiPlanForm.careerGoalId"
              clearable
              filterable
              :loading="careerGoalsLoading"
              class="full-width"
              placeholder="选择一个职业目标"
            >
              <el-option
                v-for="goal in careerGoals"
                :key="goal.id"
                :label="careerGoalLabel(goal)"
                :value="goal.id"
              />
            </el-select>
            <span v-if="!careerGoalsLoading && careerGoals.length === 0" class="form-tip">暂无可选职业目标，仍可继续生成。</span>
          </el-form-item>
          <el-form-item label="关注岗位（最多 5 个，可选）" prop="jobIds">
            <el-select
              v-model="aiPlanForm.jobIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              filterable
              :multiple-limit="5"
              :loading="jobsLoading"
              class="full-width"
              placeholder="选择结构化岗位要求作为参考"
            >
              <el-option v-for="job in jobs" :key="job.id" :label="jobLabel(job)" :value="job.id" />
            </el-select>
            <span class="form-tip">只会使用选中岗位的结构化要求，不会把完整原始 JD 发送给 AI。</span>
          </el-form-item>
          <el-form-item label="本周特别关注的问题（可选）" prop="focusNote">
            <el-input
              v-model="aiPlanForm.focusNote"
              type="textarea"
              :rows="4"
              maxlength="2000"
              show-word-limit
              placeholder="例如：这周只有 4 小时，希望优先补齐 Redis 基础"
            />
          </el-form-item>
        </el-form>
      </template>

      <template v-else-if="aiPlanCandidate">
        <el-alert
          title="AI 候选，尚未保存"
          description="请检查并编辑主目标和任务。只有点击“确认创建正式计划”后，才会写入学习计划和任务。"
          type="warning"
          :closable="false"
          show-icon
          class="ai-alert"
        />

        <div class="ai-candidate-scroll">
          <section class="ai-panel">
            <div class="section-heading">
              <div>
                <h3>本次分析范围</h3>
                <p>以下内容来自本次请求和 Java 构建的真实上下文。</p>
              </div>
              <el-button link type="primary" @click="aiPlanStage = 'input'">返回修改输入</el-button>
            </div>
            <div class="ai-metrics">
              <div><span>计划周期</span><strong>{{ aiPlanCandidate.weekStart }} — {{ aiPlanCandidate.weekEnd }}</strong></div>
              <div><span>每周可用时间</span><strong>{{ aiPlanCandidate.availableMinutes }} 分钟</strong></div>
              <div><span>当前候选总时长</span><strong>{{ aiPlanTotalMinutes }} 分钟</strong></div>
              <div><span>剩余缓冲</span><strong>{{ aiPlanBufferMinutes }} 分钟</strong></div>
            </div>
          </section>

          <section class="ai-panel">
            <h3>来源事实（Java context）</h3>
            <div v-if="aiPlanCandidate.sources.length > 0" class="source-list">
              <div v-for="source in aiPlanCandidate.sources" :key="source.key || source.label" class="source-item">
                <div class="source-item__heading">
                  <strong>{{ source.label }}</strong>
                  <el-tag v-if="source.type" size="small" type="info">{{ evidenceTypeLabel(source.type) }}</el-tag>
                </div>
                <p v-if="source.excerpt" class="source-excerpt">{{ source.excerpt }}</p>
                <ul v-if="source.facts && source.facts.length > 0" class="source-facts">
                  <li v-for="fact in source.facts" :key="fact">{{ fact }}</li>
                </ul>
              </div>
            </div>
            <div v-else-if="aiPlanCandidate.facts.length > 0" class="fact-list">
              <div v-for="fact in aiPlanCandidate.facts" :key="fact.key || fact.label" class="fact-item">
                <span>{{ fact.label }}</span><strong>{{ fact.value || '—' }}</strong>
              </div>
            </div>
            <el-empty v-else :image-size="56" description="本次没有可展示的额外来源事实" />
            <div v-if="aiPlanCandidate.facts.length > 0 && aiPlanCandidate.sources.length > 0" class="fact-list fact-list--inline">
              <div v-for="fact in aiPlanCandidate.facts" :key="`fact-${fact.key || fact.label}`" class="fact-item">
                <span>{{ fact.label }}</span><strong>{{ fact.value || '—' }}</strong>
              </div>
            </div>
          </section>

          <el-alert
            v-if="aiPlanCandidate.warnings.length > 0"
            title="上下文或候选提示"
            type="info"
            :closable="false"
            class="ai-alert"
          >
            <ul class="warning-list">
              <li v-for="warning in aiPlanCandidate.warnings" :key="warning">{{ warning }}</li>
            </ul>
          </el-alert>

          <section class="ai-panel">
            <div class="section-heading">
              <div>
                <h3>AI 建议</h3>
                <p>建议内容与来源事实分开呈现；任务依据只能引用 Java 返回的 evidence。</p>
              </div>
            </div>
            <el-form label-position="top" class="candidate-form">
              <el-form-item label="主目标">
                <el-input v-model="aiPlanCandidate.mainGoal" maxlength="500" show-word-limit />
              </el-form-item>
              <div class="advice-box">
                <span class="ai-field-label">为什么推荐</span>
                <p>{{ aiPlanCandidate.rationale || '本次没有额外的总体说明。' }}</p>
              </div>
            </el-form>
          </section>

          <section class="ai-panel">
            <div class="section-heading">
              <div>
                <h3>候选任务（{{ aiPlanCandidate.tasks.length }}）</h3>
                <p>可编辑、删除和调整顺序；每个任务至少需要一条可信依据。</p>
              </div>
            </div>
            <el-empty v-if="aiPlanCandidate.tasks.length === 0" description="请至少保留一个任务" />
            <div v-else class="candidate-task-list">
              <article v-for="(task, index) in aiPlanCandidate.tasks" :key="taskKey(task, index)" class="candidate-task">
                <div class="candidate-task__header">
                  <el-tag size="small" type="info">任务 {{ index + 1 }}</el-tag>
                  <div class="candidate-task__actions">
                    <el-button link type="primary" :disabled="index === 0" @click="moveAiTask(index, -1)">↑ 上移</el-button>
                    <el-button link type="primary" :disabled="index === aiPlanCandidate.tasks.length - 1" @click="moveAiTask(index, 1)">↓ 下移</el-button>
                    <el-button link type="danger" @click="removeAiTask(index)">删除</el-button>
                  </div>
                </div>
                <el-form label-position="top" class="candidate-task__form">
                  <el-form-item label="任务标题">
                    <el-input v-model="task.title" maxlength="200" show-word-limit />
                  </el-form-item>
                  <el-form-item label="任务描述">
                    <el-input v-model="task.description" type="textarea" :rows="3" maxlength="2000" show-word-limit />
                  </el-form-item>
                  <div class="form-grid form-grid--task">
                    <el-form-item label="计划用时（分钟）">
                      <el-input-number v-model="task.plannedMinutes" :min="1" :max="10080" :step="5" controls-position="right" class="full-width" />
                    </el-form-item>
                    <el-form-item label="截止日期">
                      <el-date-picker v-model="task.dueDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disableAiTaskDate" class="full-width" />
                    </el-form-item>
                  </div>
                </el-form>
                <div class="advice-box advice-box--task">
                  <span class="ai-field-label">AI 建议</span>
                  <p>{{ task.rationale || '根据本次上下文安排的可执行任务。' }}</p>
                </div>
                <div class="evidence-box">
                  <span class="ai-field-label">可信依据</span>
                  <div v-if="taskEvidence(task).length > 0" class="evidence-list">
                    <div v-for="evidence in taskEvidence(task)" :key="evidence.key" class="evidence-item">
                      <strong>{{ evidence.label }}</strong>
                      <span>{{ evidence.excerpt || evidence.fact || evidence.value || 'Java context fact' }}</span>
                    </div>
                  </div>
                  <el-alert v-else title="当前任务没有有效依据，不能确认创建。" type="error" :closable="false" />
                </div>
              </article>
            </div>
            <el-alert
              v-if="aiPlanValidationIssues.length > 0"
              :title="aiPlanValidationIssues.join('；')"
              type="error"
              :closable="false"
              class="candidate-validation"
            />
          </section>
        </div>
      </template>

      <template #footer>
        <template v-if="aiPlanStage === 'input'">
          <el-button @click="aiPlanDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="aiPlanLoading" @click="generateAiPlan">生成 AI 候选</el-button>
        </template>
        <template v-else>
          <el-button @click="aiPlanDialogVisible = false">放弃候选</el-button>
          <el-button type="primary" :loading="aiPlanConfirming" :disabled="!canConfirmAiPlan" @click="confirmAiPlan">确认创建正式计划</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import { listCareerGoals, listJobs } from '@/api/career'
import {
  confirmLearningPlanAiSuggestion,
  createLearningPlan,
  deleteLearningPlan,
  listLearningPlans,
  suggestLearningPlanWithAi,
  updateLearningPlan,
} from '@/api/learning'
import type { CareerGoal, Job } from '@/types/career'
import type {
  LearningAiEvidence,
  LearningPlan,
  LearningPlanAiConfirmRequest,
  LearningPlanAiSuggestionRequest,
  LearningPlanAiSuggestionResponse,
  LearningPlanRequest,
  LearningPlanStatus,
  LearningPlanAiTaskSuggestion,
} from '@/types/learning'

interface AiPlanForm {
  weekStart: string
  weekEnd: string
  availableMinutes: number | undefined
  careerGoalId: number | undefined
  jobIds: number[]
  focusNote: string
}

interface AiPlanCandidateDraft {
  weekStart: string
  weekEnd: string
  availableMinutes: number
  mainGoal: string
  rationale: string
  tasks: LearningPlanAiTaskSuggestion[]
  sources: LearningPlanAiSuggestionResponse['sources']
  facts: LearningPlanAiSuggestionResponse['facts']
  evidence: LearningAiEvidence[]
  warnings: string[]
}

const router = useRouter()
const plans = ref<LearningPlan[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const aiPlanDialogVisible = ref(false)
const aiPlanFormRef = ref<FormInstance>()
const aiPlanStage = ref<'input' | 'candidate'>('input')
const aiPlanLoading = ref(false)
const aiPlanConfirming = ref(false)
const aiPlanError = ref('')
const aiPlanCandidate = ref<AiPlanCandidateDraft | null>(null)
const careerGoals = ref<CareerGoal[]>([])
const jobs = ref<Job[]>([])
const careerGoalsLoading = ref(false)
const jobsLoading = ref(false)

const statusOptions: Array<{ value: LearningPlanStatus; label: string }> = [
  { value: 'PLANNED', label: '未开始' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'COMPLETED', label: '已完成' },
]

function emptyForm(): LearningPlanRequest {
  return { weekStart: '', weekEnd: '', mainGoal: '', status: 'PLANNED' }
}

function dateInput(date: Date): string {
  const pad = (value: number): string => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function emptyAiPlanForm(): AiPlanForm {
  const today = new Date()
  const day = today.getDay()
  const monday = new Date(today)
  monday.setDate(today.getDate() + (day === 0 ? -6 : 1 - day))
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  return {
    weekStart: dateInput(monday),
    weekEnd: dateInput(sunday),
    availableMinutes: undefined,
    careerGoalId: undefined,
    jobIds: [],
    focusNote: '',
  }
}

const form = reactive<LearningPlanRequest>(emptyForm())
const aiPlanForm = reactive<AiPlanForm>(emptyAiPlanForm())
const rules: FormRules = {
  weekStart: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  weekEnd: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
  mainGoal: [{ required: true, message: '请输入本周目标', trigger: 'blur' }],
  status: [{ required: true, message: '请选择计划状态', trigger: 'change' }],
}
const aiPlanRules: FormRules = {
  weekStart: [{ required: true, message: '请选择计划开始日期', trigger: 'change' }],
  weekEnd: [{ required: true, message: '请选择计划结束日期', trigger: 'change' }],
  availableMinutes: [
    { required: true, type: 'number', min: 1, max: 10080, message: '请输入1到10080分钟的可用时间', trigger: 'change' },
  ],
}

const aiPlanTotalMinutes = computed(() => aiPlanCandidate.value?.tasks.reduce((total, task) => total + (Number(task.plannedMinutes) || 0), 0) ?? 0)
const aiPlanBufferMinutes = computed(() => (aiPlanCandidate.value?.availableMinutes ?? 0) - aiPlanTotalMinutes.value)
const aiPlanValidationIssues = computed(() => {
  const candidate = aiPlanCandidate.value
  if (!candidate) return []
  const issues: string[] = []
  if (!candidate.mainGoal.trim()) issues.push('主目标不能为空')
  if (candidate.tasks.length === 0) issues.push('至少保留一个任务')
  if (candidate.tasks.length > 6) issues.push('最多保留6个任务')
  const seenTitles = new Set<string>()
  const seenSortOrders = new Set<number>()
  candidate.tasks.forEach((task, index) => {
    const label = `任务${index + 1}`
    const normalizedTitle = task.title.trim().toLocaleLowerCase()
    if (!normalizedTitle) issues.push(`${label}标题不能为空`)
    if (normalizedTitle && seenTitles.has(normalizedTitle)) issues.push('任务标题不能重复')
    if (normalizedTitle) seenTitles.add(normalizedTitle)
    if (!Number.isFinite(task.plannedMinutes) || task.plannedMinutes <= 0) issues.push(`${label}计划用时必须大于0`)
    if (!task.dueDate || task.dueDate < candidate.weekStart || task.dueDate > candidate.weekEnd) issues.push(`${label}截止日期必须在计划周期内`)
    if (!Number.isInteger(task.sortOrder) || task.sortOrder < 0 || seenSortOrders.has(task.sortOrder)) issues.push('任务排序值必须唯一且不小于0')
    seenSortOrders.add(task.sortOrder)
    if (task.evidenceKeys.length === 0 || taskEvidence(task).length === 0) issues.push(`${label}缺少可信依据`)
  })
  if (aiPlanTotalMinutes.value > candidate.availableMinutes) issues.push('任务总计划用时不能超过每周可用时间')
  return [...new Set(issues)]
})
const canConfirmAiPlan = computed(() => Boolean(aiPlanCandidate.value && aiPlanValidationIssues.value.length === 0 && !aiPlanConfirming.value))

function statusLabel(status: LearningPlanStatus): string {
  return statusOptions.find((option) => option.value === status)?.label ?? status
}

function statusTag(status: LearningPlanStatus): 'info' | 'warning' | 'success' {
  if (status === 'IN_PROGRESS') return 'warning'
  if (status === 'COMPLETED') return 'success'
  return 'info'
}

function dateRange(start: string, end: string): string {
  return `${start || '未填写'} — ${end || '未填写'}`
}

function careerGoalLabel(goal: CareerGoal): string {
  return `${goal.targetPosition}${goal.targetCompanyPreference ? ` · ${goal.targetCompanyPreference}` : ''}`
}

function jobLabel(job: Job): string {
  return `${job.title}${job.city ? ` · ${job.city}` : ''}`
}

function evidenceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    CAREER_GOAL: '职业目标',
    USER_SKILL: '用户技能',
    JOB_REQUIREMENT: '岗位要求',
    LEARNING_PLAN: '历史计划',
    LEARNING_TASK: '历史任务',
    STUDY_RECORD: '学习记录',
    WEEKLY_REVIEW: '周复盘',
    LEARNING_NOTE: '学习笔记',
    USER_FOCUS: '本周关注',
  }
  return labels[type] ?? type
}

function taskEvidence(task: LearningPlanAiTaskSuggestion): LearningAiEvidence[] {
  const evidence = aiPlanCandidate.value?.evidence ?? []
  return task.evidenceKeys
    .map((key) => evidence.find((item) => item.key === key))
    .filter((item): item is LearningAiEvidence => Boolean(item))
}

function taskKey(task: LearningPlanAiTaskSuggestion, index: number): string {
  return `${task.sortOrder}-${index}-${task.title}`
}

function syncAiTaskSortOrders(): void {
  aiPlanCandidate.value?.tasks.forEach((task, index) => { task.sortOrder = index })
}

function moveAiTask(index: number, direction: -1 | 1): void {
  const tasks = aiPlanCandidate.value?.tasks
  if (!tasks) return
  const target = index + direction
  if (target < 0 || target >= tasks.length) return
  const current = tasks[index]
  tasks[index] = tasks[target]
  tasks[target] = current
  syncAiTaskSortOrders()
}

function removeAiTask(index: number): void {
  aiPlanCandidate.value?.tasks.splice(index, 1)
  syncAiTaskSortOrders()
}

function disableAiTaskDate(date: Date): boolean {
  const candidate = aiPlanCandidate.value
  if (!candidate) return false
  const value = dateInput(date)
  return value < candidate.weekStart || value > candidate.weekEnd
}

async function load(): Promise<void> {
  loading.value = true
  try {
    plans.value = await listLearningPlans()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    loading.value = false
  }
}

async function loadCareerGoals(): Promise<void> {
  careerGoalsLoading.value = true
  try {
    careerGoals.value = (await listCareerGoals()).filter((goal) => goal.status === 'ACTIVE')
  } catch {
    careerGoals.value = []
  } finally {
    careerGoalsLoading.value = false
  }
}

async function loadJobs(): Promise<void> {
  jobsLoading.value = true
  try {
    jobs.value = await listJobs(false)
  } catch {
    jobs.value = []
  } finally {
    jobsLoading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: LearningPlan): void {
  editingId.value = row.id
  Object.assign(form, {
    weekStart: row.weekStart,
    weekEnd: row.weekEnd,
    mainGoal: row.mainGoal,
    status: row.status,
  })
  dialogVisible.value = true
}

function viewPlan(id: number): void {
  void router.push({ name: 'learning-plan-detail', params: { planId: id } })
}

async function save(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.weekEnd < form.weekStart) {
    ElMessage.warning('结束日期不能早于开始日期')
    return
  }

  saving.value = true
  const payload: LearningPlanRequest = {
    weekStart: form.weekStart,
    weekEnd: form.weekEnd,
    mainGoal: form.mainGoal.trim(),
    status: form.status,
  }
  try {
    const id = editingId.value
    const isCreate = id === null
    if (isCreate) {
      await createLearningPlan(payload)
    } else {
      await updateLearningPlan(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '学习计划已创建' : '学习计划已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

function openAiPlanDialog(): void {
  Object.assign(aiPlanForm, emptyAiPlanForm())
  aiPlanStage.value = 'input'
  aiPlanCandidate.value = null
  aiPlanError.value = ''
  aiPlanDialogVisible.value = true
  void Promise.all([loadCareerGoals(), loadJobs()])
}

async function generateAiPlan(): Promise<void> {
  const valid = await aiPlanFormRef.value?.validate().catch(() => false)
  if (!valid || !aiPlanForm.weekStart || !aiPlanForm.weekEnd || !aiPlanForm.availableMinutes) return
  if (aiPlanForm.weekEnd < aiPlanForm.weekStart) {
    ElMessage.warning('结束日期不能早于开始日期')
    return
  }
  if (aiPlanForm.jobIds.length > 5) {
    ElMessage.warning('最多选择5个关注岗位')
    return
  }

  aiPlanLoading.value = true
  aiPlanError.value = ''
  const payload: LearningPlanAiSuggestionRequest = {
    weekStart: aiPlanForm.weekStart,
    weekEnd: aiPlanForm.weekEnd,
    availableMinutes: aiPlanForm.availableMinutes,
    ...(aiPlanForm.careerGoalId ? { careerGoalId: aiPlanForm.careerGoalId } : {}),
    ...(aiPlanForm.jobIds.length > 0 ? { jobIds: [...aiPlanForm.jobIds] } : {}),
    ...(aiPlanForm.focusNote.trim() ? { focusNote: aiPlanForm.focusNote.trim() } : {}),
  }
  try {
    const response = await suggestLearningPlanWithAi(payload)
    aiPlanCandidate.value = {
      weekStart: response.weekStart || payload.weekStart,
      weekEnd: response.weekEnd || payload.weekEnd,
      availableMinutes: response.availableMinutes || payload.availableMinutes,
      mainGoal: response.mainGoal || '',
      rationale: response.rationale || '',
      tasks: response.tasks.map((task, index) => ({
        ...task,
        title: task.title || `学习任务 ${index + 1}`,
        description: task.description ?? '',
        plannedMinutes: Number(task.plannedMinutes) || 0,
        dueDate: task.dueDate || payload.weekEnd,
        sortOrder: Number.isInteger(task.sortOrder) ? task.sortOrder : index,
        evidenceKeys: Array.isArray(task.evidenceKeys) ? [...task.evidenceKeys] : [],
      })),
      sources: response.sources ?? [],
      facts: response.facts ?? [],
      evidence: response.evidence ?? [],
      warnings: response.warnings ?? [],
    }
    aiPlanCandidate.value.tasks.sort((a, b) => a.sortOrder - b.sortOrder)
    syncAiTaskSortOrders()
    aiPlanStage.value = 'candidate'
  } catch {
    aiPlanError.value = 'AI 计划建议暂时不可用；你仍可以关闭窗口并手工创建学习计划。'
  } finally {
    aiPlanLoading.value = false
  }
}

async function confirmAiPlan(): Promise<void> {
  const candidate = aiPlanCandidate.value
  if (!candidate || !canConfirmAiPlan.value) return
  aiPlanConfirming.value = true
  aiPlanError.value = ''
  const payload: LearningPlanAiConfirmRequest = {
    weekStart: candidate.weekStart,
    weekEnd: candidate.weekEnd,
    availableMinutes: candidate.availableMinutes,
    mainGoal: candidate.mainGoal.trim(),
    tasks: candidate.tasks.map((task, index) => ({
      title: task.title.trim(),
      description: task.description?.trim() || undefined,
      plannedMinutes: task.plannedMinutes,
      dueDate: task.dueDate,
      sortOrder: index,
    })),
  }
  try {
    const response = await confirmLearningPlanAiSuggestion(payload)
    aiPlanDialogVisible.value = false
    ElMessage.success('AI 候选已确认，学习计划已创建')
    const createdId = response.plan?.id
    if (createdId) {
      await router.push({ name: 'learning-plan-detail', params: { planId: createdId } })
    } else {
      await load()
    }
  } catch {
    aiPlanError.value = '确认创建失败，候选仍未保存；请检查内容后重试。'
  } finally {
    aiPlanConfirming.value = false
  }
}

function resetAiPlan(): void {
  aiPlanStage.value = 'input'
  aiPlanCandidate.value = null
  aiPlanError.value = ''
}

async function remove(row: LearningPlan): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${row.mainGoal}”这份学习计划吗？相关任务和资料也会被删除。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteLearningPlan(row.id)
    ElMessage.success('学习计划已删除')
    await load()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

onMounted(load)
</script>

<style scoped>
.page-shell { max-width: 1180px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.heading-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.table-wrap { min-height: 180px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.form-grid--task { gap: 0 14px; }
.full-width { width: 100%; }
.form-tip { display: block; margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.ai-alert { margin-bottom: 16px; }
.ai-candidate-scroll { max-height: 70vh; overflow-y: auto; padding: 2px 4px 4px 2px; }
.ai-panel { margin-bottom: 16px; padding: 16px; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.ai-panel:last-child { margin-bottom: 0; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.section-heading h3 { margin: 0 0 5px; font-size: 16px; color: var(--el-text-color-primary); }
.section-heading p { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
.ai-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.ai-metrics div { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.ai-metrics span, .ai-field-label { color: var(--el-text-color-secondary); font-size: 12px; }
.ai-metrics strong { color: var(--el-text-color-primary); font-weight: 600; overflow-wrap: anywhere; }
.source-list { display: grid; gap: 10px; }
.source-item { padding: 11px 12px; border-radius: 6px; background: var(--el-fill-color-light); }
.source-item__heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.source-item__heading strong { color: var(--el-text-color-primary); }
.source-excerpt { margin: 8px 0 0; color: var(--el-text-color-regular); line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; }
.source-facts, .warning-list { margin: 8px 0 0; padding-left: 20px; color: var(--el-text-color-regular); line-height: 1.7; }
.fact-list { display: grid; gap: 8px; }
.fact-list--inline { margin-top: 10px; }
.fact-item { display: flex; justify-content: space-between; gap: 14px; padding: 10px 12px; border-radius: 6px; background: var(--el-fill-color-light); }
.fact-item span { color: var(--el-text-color-secondary); }
.fact-item strong { color: var(--el-text-color-primary); font-weight: 500; text-align: right; overflow-wrap: anywhere; }
.candidate-form { margin-bottom: 0; }
.advice-box { padding: 11px 12px; border-radius: 6px; background: var(--el-color-primary-light-9); }
.advice-box p { margin: 6px 0 0; color: var(--el-text-color-primary); line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.candidate-task-list { display: grid; gap: 12px; }
.candidate-task { padding: 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.candidate-task__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.candidate-task__actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 2px; }
.candidate-task__form { margin-bottom: 0; }
.advice-box--task { margin-top: 2px; }
.evidence-box { margin-top: 12px; padding: 11px 12px; border-radius: 6px; background: var(--el-fill-color-light); }
.evidence-list { display: grid; gap: 8px; margin-top: 8px; }
.evidence-item { display: flex; flex-direction: column; gap: 4px; }
.evidence-item strong { color: var(--el-text-color-primary); font-weight: 500; }
.evidence-item span { color: var(--el-text-color-regular); line-height: 1.55; white-space: pre-wrap; overflow-wrap: anywhere; }
.candidate-validation { margin-top: 14px; }
@media (max-width: 760px) {
  .page-heading { align-items: flex-start; flex-direction: column; }
  .heading-actions { width: 100%; justify-content: flex-start; }
  .ai-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 560px) {
  .form-grid { grid-template-columns: 1fr; }
  .ai-metrics { grid-template-columns: 1fr; }
  .candidate-task__header { align-items: flex-start; flex-direction: column; }
  .candidate-task__actions { width: 100%; justify-content: flex-start; }
}
</style>
