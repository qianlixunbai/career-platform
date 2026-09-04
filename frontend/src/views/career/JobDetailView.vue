<template>
  <div class="page-shell">
    <div class="back-row">
      <el-button link type="primary" @click="goBack">← 返回岗位列表</el-button>
    </div>

    <el-skeleton v-if="loading && !job" :rows="10" animated />
    <el-result v-else-if="!job" icon="warning" title="岗位不存在" sub-title="请返回岗位列表后重试" />
    <template v-else>
      <el-card shadow="never" class="job-card">
        <template #header>
          <div class="job-heading">
            <div>
              <div class="eyebrow">岗位详情</div>
              <h2>{{ job.title }}</h2>
              <p>{{ companyName }}<span v-if="job.city"> · {{ job.city }}</span></p>
            </div>
            <div class="job-actions">
              <el-tag :type="job.archived ? 'info' : 'success'">{{ job.archived ? '已归档' : '进行中' }}</el-tag>
              <el-button v-if="!job.archived" type="primary" :loading="savingApplication" @click="openApplicationCreate">创建投递</el-button>
              <el-button v-if="!job.archived" type="warning" plain :loading="actionLoading" @click="archive">归档</el-button>
              <el-button v-else type="success" plain :loading="actionLoading" @click="unarchive">恢复</el-button>
            </div>
          </div>
        </template>

        <div class="meta-grid">
          <div><span>岗位类型</span><strong>{{ jobTypeLabel(job.jobType) }}</strong></div>
          <div><span>发布日期</span><strong>{{ job.publishDate || '未填写' }}</strong></div>
          <div><span>截止日期</span><strong>{{ job.deadline || '未填写' }}</strong></div>
          <div><span>来源</span><strong>{{ sourceTypeLabel(job.sourceType) }}{{ job.sourceName ? ` · ${job.sourceName}` : '' }}</strong></div>
        </div>
        <div v-if="job.sourceUrl" class="source-link"><span>来源链接</span><a :href="job.sourceUrl" target="_blank" rel="noopener noreferrer">{{ job.sourceUrl }}</a></div>
        <el-divider />
        <section>
          <h3>原始 JD</h3>
          <p v-if="job.rawJd" class="raw-jd">{{ job.rawJd }}</p>
          <el-empty v-else :image-size="64" description="还没有记录原始 JD" />
        </section>
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="section-heading">
            <div>
              <h3>岗位要求</h3>
              <p>把 JD 中的要求拆成可跟踪的条目。</p>
            </div>
            <div class="section-actions">
              <el-button
                v-if="aiCandidates.length > 0"
                plain
                type="info"
                @click="aiReviewDialogVisible = true"
              >
                查看 AI 候选（{{ aiCandidates.length }}）
              </el-button>
              <el-button
                :disabled="!hasRawJd"
                :loading="aiParsing"
                type="primary"
                @click="parseWithAi"
              >
                AI 解析 JD
              </el-button>
              <el-button type="primary" plain @click="openRequirementCreate">新增要求</el-button>
            </div>
          </div>
        </template>
        <div v-loading="requirementsLoading" class="table-wrap">
          <el-empty v-if="!requirementsLoading && requirements.length === 0" description="还没有拆解岗位要求" />
          <el-table v-else :data="requirements" stripe>
            <el-table-column label="类型" width="130"><template #default="{ row }">{{ requirementTypeLabel(row.requirementType) }}</template></el-table-column>
            <el-table-column label="关联技能" width="180" show-overflow-tooltip><template #default="{ row }">{{ skillName(row.skillId) }}</template></el-table-column>
            <el-table-column prop="requirementText" label="要求内容" min-width="280" show-overflow-tooltip />
            <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openRequirementEdit(row)">编辑</el-button><el-button link type="danger" @click="removeRequirement(row)">删除</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="section-heading"><div><h3>跟进笔记</h3><p>记录投递、沟通和面试后的重要信息。</p></div><el-button type="primary" @click="openNoteCreate">新增笔记</el-button></div>
        </template>
        <div v-loading="notesLoading" class="notes-list">
          <el-empty v-if="!notesLoading && notes.length === 0" description="还没有岗位笔记" />
          <div v-for="note in notes" :key="note.id" class="note-item">
            <p>{{ note.content }}</p>
            <div class="note-actions"><el-button link type="primary" @click="openNoteEdit(note)">编辑</el-button><el-button link type="danger" @click="removeNote(note)">删除</el-button></div>
          </div>
        </div>
      </el-card>
    </template>

    <el-dialog v-model="applicationDialogVisible" title="创建投递" width="620px" destroy-on-close>
      <el-alert title="投递必须绑定已定稿的简历版本；草稿版本不可用于正式投递。" type="info" :closable="false" show-icon class="application-alert" />
      <el-form ref="applicationFormRef" :model="applicationForm" :rules="applicationRules" label-position="top" @submit.prevent="submitApplication">
        <el-form-item label="使用的简历" prop="resumeId">
          <el-select v-model="applicationForm.resumeId" class="full-width" filterable placeholder="选择一份简历" @change="onResumeChange">
            <el-option v-for="resume in resumes" :key="resume.id" :label="resume.name" :value="resume.id" />
          </el-select>
          <span v-if="resumes.length === 0 && !resumesLoading" class="form-tip">还没有简历，请先前往“简历管理”创建。</span>
        </el-form-item>
        <el-form-item label="定稿版本" prop="resumeVersionId">
          <el-select v-model="applicationForm.resumeVersionId" class="full-width" placeholder="选择已定稿版本" :loading="versionsLoading" :disabled="!applicationForm.resumeId">
            <el-option
              v-for="version in resumeVersions"
              :key="version.id"
              :label="resumeVersionLabel(version)"
              :value="version.id"
              :disabled="version.status !== 'FINALIZED'"
            />
          </el-select>
          <span v-if="applicationForm.resumeId && !versionsLoading && finalizedVersions.length === 0" class="form-tip form-tip--warning">这份简历还没有已定稿版本，请先在简历详情中定稿。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applicationDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingApplication" :disabled="!canSubmitApplication" @click="submitApplication">确认创建投递</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="requirementDialogVisible" :title="requirementEditingId === null ? '新增岗位要求' : '编辑岗位要求'" width="520px" destroy-on-close>
      <el-form :model="requirementForm" label-position="top" @submit.prevent="saveRequirement">
        <el-form-item label="要求类型" required>
          <el-select v-model="requirementForm.requirementType" class="full-width" placeholder="选择要求类型">
            <el-option v-for="option in requirementTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="requirementForm.requirementType === 'SKILL'" label="关联技能" required>
          <el-select v-model="requirementForm.skillId" class="full-width" filterable placeholder="选择技能">
            <el-option v-for="skill in skills" :key="skill.id" :label="skill.name" :value="skill.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="要求内容" required>
          <el-input v-model="requirementForm.requirementText" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="例如：熟悉 Java 集合与并发编程" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="requirementDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingRequirement" @click="saveRequirement">保存</el-button></template>
    </el-dialog>

    <el-dialog
      v-model="aiReviewDialogVisible"
      title="AI 解析候选"
      width="900px"
      top="5vh"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-alert
        title="AI 生成内容仅作为候选，确认后才会写入岗位要求。"
        type="warning"
        :closable="false"
        show-icon
        class="ai-review-alert"
      />

      <el-alert
        v-if="aiWarnings.length > 0"
        title="解析提醒"
        type="info"
        :closable="false"
        show-icon
        class="ai-warning-alert"
      >
        <ul class="ai-warning-list">
          <li v-for="warning in aiWarnings" :key="warning">{{ warning }}</li>
        </ul>
      </el-alert>

      <el-empty v-if="aiCandidates.length === 0" description="AI 没有返回可审核的岗位要求" />
      <div v-else class="ai-candidate-list">
        <article v-for="candidate in aiCandidates" :key="candidate.key" class="ai-candidate">
          <div class="ai-candidate__header">
            <el-checkbox
              v-model="candidate.selected"
              :disabled="!canSelectAiCandidate(candidate)"
              @change="onAiCandidateSelectionChange(candidate)"
            >
              采纳此条
            </el-checkbox>
            <div class="ai-candidate__status">
              <el-tag :type="resolutionStatusTag(candidate.resolutionStatus)" size="small">
                {{ resolutionStatusLabel(candidate.resolutionStatus) }}
              </el-tag>
              <el-tag :type="duplicateStatusTag(candidate.duplicateStatus)" size="small">
                {{ duplicateStatusLabel(candidate.duplicateStatus) }}
              </el-tag>
            </div>
          </div>

          <el-form :model="candidate" label-position="top" class="ai-candidate__form">
            <div class="ai-candidate__grid">
              <el-form-item label="要求类型" required>
                <el-select
                  v-model="candidate.requirementType"
                  class="full-width"
                  placeholder="选择要求类型"
                  @change="onAiCandidateTypeChange(candidate)"
                >
                  <el-option
                    v-for="option in requirementTypeOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item v-if="candidate.requirementType === 'SKILL'" label="关联技能" required>
                <el-select
                  v-model="candidate.skillId"
                  class="full-width"
                  filterable
                  clearable
                  placeholder="选择技能库中的真实技能"
                  @change="onAiCandidateSkillChange(candidate)"
                >
                  <el-option v-for="skill in skills" :key="skill.id" :label="skill.name" :value="skill.id" />
                </el-select>
                <span v-if="skills.length === 0" class="form-tip form-tip--warning">技能库为空，SKILL 候选无法确认。</span>
              </el-form-item>
            </div>

            <el-form-item label="要求内容" required>
              <el-input
                v-model="candidate.description"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
                placeholder="可编辑 AI 提取的要求内容"
              />
            </el-form-item>

            <div class="ai-candidate__evidence">
              <span class="ai-field-label">证据（来自原始 JD）</span>
              <blockquote>{{ candidate.evidenceQuote }}</blockquote>
            </div>
            <div v-if="candidate.requirementType === 'SKILL'" class="ai-candidate__matched-skill">
              <span class="ai-field-label">AI 识别技能</span>
              <span>{{ candidate.skillName || '未识别' }}</span>
            </div>
          </el-form>
        </article>
      </div>

      <template #footer>
        <el-button @click="aiReviewDialogVisible = false">稍后审核</el-button>
        <el-button
          type="primary"
          :loading="aiConfirming"
          :disabled="!canConfirmAi"
          @click="confirmAiCandidates"
        >
          确认写入{{ aiSelectedCount > 0 ? ` ${aiSelectedCount} 条` : '' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="noteDialogVisible" :title="noteEditingId === null ? '新增岗位笔记' : '编辑岗位笔记'" width="560px" destroy-on-close>
      <el-form :model="noteForm" label-position="top" @submit.prevent="saveNote">
        <el-form-item label="笔记内容" required><el-input v-model="noteForm.content" type="textarea" :rows="8" maxlength="16000" show-word-limit placeholder="记录投递渠道、面试反馈、待办事项等" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="noteDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingNote" @click="saveNote">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  archiveJob,
  confirmAiJobRequirements,
  listCompanies,
  createJobNote,
  createJobRequirement,
  deleteJobNote,
  deleteJobRequirement,
  getJob,
  listJobNotes,
  listJobRequirements,
  parseJobRequirementsWithAi,
  unarchiveJob,
  updateJobNote,
  updateJobRequirement,
} from '@/api/career'
import { createApplication } from '@/api/application'
import { listSkills } from '@/api/profile'
import { listResumes, listResumeVersions } from '@/api/resume'
import type { Company } from '@/types/career'
import type { Skill } from '@/types/profile'
import type { Resume, ResumeVersion } from '@/types/resume'
import type { CreateApplicationRequest } from '@/types/application'
import type {
  Job,
  DuplicateStatus,
  JdParseConfirmRequest,
  JdRequirementCandidate,
  JobNote,
  JobNoteRequest,
  JobRequirement,
  JobRequirementRequest,
  JobType,
  RequirementType,
  SkillResolutionStatus,
  SourceType,
} from '@/types/career'

type ElementTagType = 'success' | 'warning' | 'info' | 'danger'

interface AiCandidateDraft extends JdRequirementCandidate {
  key: number
  skillId?: number
}

const route = useRoute()
const router = useRouter()
const job = ref<Job | null>(null)
const companies = ref<Company[]>([])
const skills = ref<Skill[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const requirementsLoading = ref(false)
const notesLoading = ref(false)
const savingRequirement = ref(false)
const savingNote = ref(false)
const savingApplication = ref(false)
const requirements = ref<JobRequirement[]>([])
const notes = ref<JobNote[]>([])
const resumes = ref<Resume[]>([])
const resumeVersions = ref<ResumeVersion[]>([])
const resumesLoading = ref(false)
const versionsLoading = ref(false)
const requirementDialogVisible = ref(false)
const noteDialogVisible = ref(false)
const applicationDialogVisible = ref(false)
const aiReviewDialogVisible = ref(false)
const requirementEditingId = ref<number | null>(null)
const noteEditingId = ref<number | null>(null)
const applicationFormRef = ref<FormInstance>()
const aiParsing = ref(false)
const aiConfirming = ref(false)
const aiSourceFingerprint = ref('')
const aiCandidates = ref<AiCandidateDraft[]>([])
const aiWarnings = ref<string[]>([])

const jobId = computed(() => Number(route.params.jobId))
const companyName = computed(() => companies.value.find((company) => company.id === job.value?.companyId)?.name ?? `公司 #${job.value?.companyId ?? ''}`)
const hasRawJd = computed(() => Boolean(job.value?.rawJd?.trim()))

const jobTypeOptions: Array<{ value: JobType; label: string }> = [
  { value: 'FULL_TIME', label: '全职' }, { value: 'INTERNSHIP', label: '实习' }, { value: 'CAMPUS', label: '校招' },
  { value: 'PART_TIME', label: '兼职' }, { value: 'CONTRACT', label: '合同制' }, { value: 'OTHER', label: '其他' },
]
const sourceTypeOptions: Array<{ value: SourceType; label: string }> = [
  { value: 'MANUAL', label: '手动记录' }, { value: 'CAMPUS_SITE', label: '校园招聘网站' }, { value: 'COMPANY_WEBSITE', label: '公司官网' },
  { value: 'RECRUITMENT_PLATFORM', label: '招聘平台' }, { value: 'REFERRAL', label: '内推' }, { value: 'OTHER', label: '其他' },
]
const requirementTypeOptions: Array<{ value: RequirementType; label: string }> = [
  { value: 'SKILL', label: '技能' }, { value: 'EDUCATION', label: '学历' }, { value: 'MAJOR', label: '专业' },
  { value: 'EXPERIENCE', label: '经验' }, { value: 'LANGUAGE', label: '语言' }, { value: 'OTHER', label: '其他' },
]

function emptyRequirement(): JobRequirementRequest {
  return { requirementType: 'SKILL', skillId: undefined, requirementText: '' }
}
const requirementForm = reactive<JobRequirementRequest>(emptyRequirement())
const noteForm = reactive<JobNoteRequest>({ content: '' })
const applicationForm = reactive<{ resumeId: number | undefined; resumeVersionId: number | undefined }>({
  resumeId: undefined,
  resumeVersionId: undefined,
})
const applicationRules: FormRules = {
  resumeId: [{ required: true, message: '请选择简历', trigger: 'change' }],
  resumeVersionId: [{ required: true, message: '请选择已定稿版本', trigger: 'change' }],
}

const finalizedVersions = computed(() => resumeVersions.value.filter((version) => version.status === 'FINALIZED'))
const canSubmitApplication = computed(() => Boolean(applicationForm.resumeId && applicationForm.resumeVersionId && finalizedVersions.value.some((version) => version.id === applicationForm.resumeVersionId)))
const aiSelectedCount = computed(() => aiCandidates.value.filter((candidate) => candidate.selected).length)
const aiHasInvalidSelectedCandidate = computed(() => aiCandidates.value.some((candidate) => {
  if (!candidate.selected || !candidate.description.trim()) return candidate.selected
  return candidate.requirementType === 'SKILL' && !hasValidAiSkill(candidate)
}))
const canConfirmAi = computed(() => Boolean(
  aiSourceFingerprint.value &&
    aiSelectedCount.value > 0 &&
    !aiHasInvalidSelectedCandidate.value,
))

function jobTypeLabel(value: JobType): string { return jobTypeOptions.find((option) => option.value === value)?.label ?? value }
function sourceTypeLabel(value: SourceType): string { return sourceTypeOptions.find((option) => option.value === value)?.label ?? value }
function requirementTypeLabel(value: RequirementType): string { return requirementTypeOptions.find((option) => option.value === value)?.label ?? value }
function skillName(skillId: number | null): string { return skillId === null ? '—' : skills.value.find((skill) => skill.id === skillId)?.name ?? `技能 #${skillId}` }

function hasValidAiSkill(candidate: AiCandidateDraft): boolean {
  return candidate.requirementType === 'SKILL' && typeof candidate.skillId === 'number' && skills.value.some((skill) => skill.id === candidate.skillId)
}

function syncAiCandidateSkill(candidate: AiCandidateDraft): void {
  if (candidate.requirementType !== 'SKILL') {
    candidate.skillId = undefined
    candidate.matchedSkillId = null
    candidate.matchedSkillName = null
    candidate.resolutionStatus = 'NOT_APPLICABLE'
    return
  }

  const matched = skills.value.find((skill) => skill.id === candidate.skillId)
  candidate.matchedSkillId = matched?.id ?? null
  candidate.matchedSkillName = matched?.name ?? null
  candidate.resolutionStatus = matched ? 'RESOLVED' : 'UNRESOLVED'
  if (!matched) candidate.selected = false
}

function canSelectAiCandidate(candidate: AiCandidateDraft): boolean {
  return candidate.requirementType !== 'SKILL' || hasValidAiSkill(candidate)
}

function onAiCandidateTypeChange(candidate: AiCandidateDraft): void {
  syncAiCandidateSkill(candidate)
}

function onAiCandidateSkillChange(candidate: AiCandidateDraft): void {
  syncAiCandidateSkill(candidate)
}

function onAiCandidateSelectionChange(candidate: AiCandidateDraft): void {
  if (candidate.selected && !canSelectAiCandidate(candidate)) candidate.selected = false
}

function resolutionStatusLabel(status: SkillResolutionStatus): string {
  if (status === 'RESOLVED') return '技能已匹配'
  if (status === 'UNRESOLVED') return '技能未匹配'
  return '无需匹配技能'
}

function resolutionStatusTag(status: SkillResolutionStatus): ElementTagType {
  if (status === 'RESOLVED') return 'success'
  if (status === 'UNRESOLVED') return 'danger'
  return 'info'
}

function duplicateStatusLabel(status: DuplicateStatus): string {
  return status === 'NEW' ? '新要求' : '已有相同要求'
}

function duplicateStatusTag(status: DuplicateStatus): ElementTagType {
  return status === 'NEW' ? 'success' : 'warning'
}

function buildAiConfirmRequirement(candidate: AiCandidateDraft): JdParseConfirmRequest['requirements'][number] {
  const payload: JdParseConfirmRequest['requirements'][number] = {
    selected: candidate.selected,
    requirementType: candidate.requirementType,
    requirementText: candidate.description.trim(),
  }
  if (candidate.requirementType === 'SKILL' && hasValidAiSkill(candidate)) {
    payload.skillId = candidate.skillId
  }
  return payload
}

async function parseWithAi(): Promise<void> {
  if (!hasRawJd.value || aiParsing.value) return
  aiParsing.value = true
  try {
    const response = await parseJobRequirementsWithAi(jobId.value)
    aiSourceFingerprint.value = response.sourceFingerprint
    aiWarnings.value = [...response.warnings]
    aiCandidates.value = response.requirements.map((candidate, index) => ({
      ...candidate,
      key: index,
      skillId: candidate.matchedSkillId ?? undefined,
    }))
    aiCandidates.value.forEach(syncAiCandidateSkill)
    aiReviewDialogVisible.value = true
  } catch {
    // The response interceptor presents the structured API error. Existing manual requirements remain untouched.
  } finally {
    aiParsing.value = false
  }
}

async function confirmAiCandidates(): Promise<void> {
  if (!canConfirmAi.value || aiConfirming.value) return
  aiConfirming.value = true
  const payload: JdParseConfirmRequest = {
    sourceFingerprint: aiSourceFingerprint.value,
    requirements: aiCandidates.value
      .filter((candidate) => candidate.selected)
      .map(buildAiConfirmRequirement),
  }
  try {
    const response = await confirmAiJobRequirements(jobId.value, payload)
    aiReviewDialogVisible.value = false
    aiCandidates.value = []
    aiWarnings.value = []
    aiSourceFingerprint.value = ''
    await loadRequirements()
    ElMessage.success(response.createdCount > 0 ? `已确认 ${response.createdCount} 条岗位要求` : '没有新增岗位要求')
  } catch {
    // The response interceptor presents the structured API error; keep the review open for correction or retry.
  } finally {
    aiConfirming.value = false
  }
}

async function load(): Promise<void> {
  if (!Number.isFinite(jobId.value) || jobId.value <= 0) return
  loading.value = true
  try {
    const [jobItem, companyItems, skillItems] = await Promise.all([getJob(jobId.value), listCompanies(), listSkills()])
    job.value = jobItem
    companies.value = companyItems
    skills.value = skillItems
  } catch {
    job.value = null
  } finally {
    loading.value = false
  }
  await Promise.all([loadRequirements(), loadNotes()])
}

async function loadRequirements(): Promise<void> {
  requirementsLoading.value = true
  try { requirements.value = await listJobRequirements(jobId.value) } catch { /* interceptor handles error */ } finally { requirementsLoading.value = false }
}

async function loadNotes(): Promise<void> {
  notesLoading.value = true
  try { notes.value = await listJobNotes(jobId.value) } catch { /* interceptor handles error */ } finally { notesLoading.value = false }
}

function goBack(): void { router.push('/career/jobs') }

async function archive(): Promise<void> {
  if (!job.value) return
  try {
    await ElMessageBox.confirm('归档后岗位会从进行中列表隐藏，确定继续吗？', '确认归档', { type: 'warning' })
    actionLoading.value = true
    job.value = await archiveJob(job.value.id)
    ElMessage.success('岗位已归档')
  } catch { /* cancellation and interceptor error */ } finally { actionLoading.value = false }
}

async function unarchive(): Promise<void> {
  if (!job.value) return
  actionLoading.value = true
  try {
    job.value = await unarchiveJob(job.value.id)
    ElMessage.success('岗位已恢复')
  } catch { /* interceptor handles error */ } finally { actionLoading.value = false }
}

function openRequirementCreate(): void {
  requirementEditingId.value = null
  Object.assign(requirementForm, emptyRequirement())
  requirementDialogVisible.value = true
}

function openRequirementEdit(row: JobRequirement): void {
  requirementEditingId.value = row.id
  Object.assign(requirementForm, { requirementType: row.requirementType, skillId: row.skillId ?? undefined, requirementText: row.requirementText })
  requirementDialogVisible.value = true
}

async function saveRequirement(): Promise<void> {
  savingRequirement.value = true
  const payload: JobRequirementRequest = {
    requirementType: requirementForm.requirementType,
    skillId: requirementForm.requirementType === 'SKILL' ? requirementForm.skillId : undefined,
    requirementText: requirementForm.requirementText.trim(),
  }
  try {
    const id = requirementEditingId.value
    const isCreate = id === null
    if (isCreate) await createJobRequirement(jobId.value, payload)
    else await updateJobRequirement(jobId.value, id, payload)
    requirementDialogVisible.value = false
    ElMessage.success(isCreate ? '岗位要求已新增' : '岗位要求已更新')
    await loadRequirements()
  } catch { /* interceptor handles error */ } finally { savingRequirement.value = false }
}

async function removeRequirement(row: JobRequirement): Promise<void> {
  try {
    await ElMessageBox.confirm('确定删除这条岗位要求吗？', '确认删除', { type: 'warning' })
    await deleteJobRequirement(jobId.value, row.id)
    ElMessage.success('岗位要求已删除')
    await loadRequirements()
  } catch { /* cancellation and interceptor error */ }
}

function openNoteCreate(): void { noteEditingId.value = null; noteForm.content = ''; noteDialogVisible.value = true }
function openNoteEdit(row: JobNote): void { noteEditingId.value = row.id; noteForm.content = row.content; noteDialogVisible.value = true }

async function saveNote(): Promise<void> {
  savingNote.value = true
  const payload: JobNoteRequest = { content: noteForm.content.trim() }
  try {
    const id = noteEditingId.value
    const isCreate = id === null
    if (isCreate) await createJobNote(jobId.value, payload)
    else await updateJobNote(jobId.value, id, payload)
    noteDialogVisible.value = false
    ElMessage.success(isCreate ? '岗位笔记已新增' : '岗位笔记已更新')
    await loadNotes()
  } catch { /* interceptor handles error */ } finally { savingNote.value = false }
}

async function removeNote(row: JobNote): Promise<void> {
  try {
    await ElMessageBox.confirm('确定删除这条岗位笔记吗？', '确认删除', { type: 'warning' })
    await deleteJobNote(jobId.value, row.id)
    ElMessage.success('岗位笔记已删除')
    await loadNotes()
  } catch { /* cancellation and interceptor error */ }
}

function resumeVersionLabel(version: ResumeVersion): string {
  const name = version.label?.trim() || `版本 ${version.versionNo}`
  return `${name} · ${version.status === 'FINALIZED' ? '已定稿' : 'DRAFT 草稿'}`
}

async function loadApplicationResumes(): Promise<void> {
  resumesLoading.value = true
  try {
    resumes.value = await listResumes()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    resumesLoading.value = false
  }
}

async function loadResumeVersions(resumeId: number | undefined, preferredVersionId?: number): Promise<void> {
  resumeVersions.value = []
  applicationForm.resumeVersionId = undefined
  if (!resumeId) return
  versionsLoading.value = true
  try {
    resumeVersions.value = await listResumeVersions(resumeId)
    const preferred = resumeVersions.value.find((version) => version.id === preferredVersionId && version.status === 'FINALIZED')
    applicationForm.resumeVersionId = preferred?.id ?? finalizedVersions.value[0]?.id
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    versionsLoading.value = false
  }
}

async function onResumeChange(resumeId: number): Promise<void> {
  await loadResumeVersions(resumeId)
}

async function openApplicationCreate(): Promise<void> {
  applicationForm.resumeId = undefined
  applicationForm.resumeVersionId = undefined
  resumeVersions.value = []
  applicationDialogVisible.value = true
  await loadApplicationResumes()
  const firstResumeId = resumes.value[0]?.id
  if (firstResumeId) {
    applicationForm.resumeId = firstResumeId
    await loadResumeVersions(firstResumeId)
  }
}

async function submitApplication(): Promise<void> {
  if (!job.value || !canSubmitApplication.value) return
  const valid = await applicationFormRef.value?.validate().catch(() => false)
  if (valid === false) return
  const payload: CreateApplicationRequest = {
    jobId: job.value.id,
    resumeVersionId: applicationForm.resumeVersionId as number,
  }
  savingApplication.value = true
  try {
    const created = await createApplication(payload)
    applicationDialogVisible.value = false
    ElMessage.success('投递已创建')
    await router.push({ name: 'application-detail', params: { applicationId: created.id } })
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    savingApplication.value = false
  }
}

watch(jobId, load)
onMounted(load)
</script>

<style scoped>
.page-shell { max-width: 1200px; margin: 0 auto; }
.back-row { margin-bottom: 12px; }
.job-card, .section-card { margin-bottom: 18px; }
.job-heading, .section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.eyebrow { color: var(--el-color-primary); font-size: 12px; letter-spacing: .08em; text-transform: uppercase; }
.job-heading h2, .section-heading h3 { margin: 5px 0 0; color: var(--el-text-color-primary); }
.job-heading p, .section-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.job-actions { display: flex; align-items: center; gap: 12px; }
.section-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 8px; }
.application-alert { margin-bottom: 18px; }
.ai-review-alert { margin-bottom: 18px; }
.ai-warning-alert { margin-bottom: 18px; }
.ai-warning-list { margin: 0; padding-left: 20px; line-height: 1.7; }
.ai-candidate-list { display: grid; gap: 14px; max-height: min(62vh, 720px); overflow-y: auto; padding: 2px 4px 4px 2px; }
.ai-candidate { padding: 16px; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.ai-candidate__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.ai-candidate__status { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.ai-candidate__form { margin-bottom: 0; }
.ai-candidate__grid { display: grid; grid-template-columns: minmax(180px, 0.7fr) minmax(260px, 1.3fr); gap: 0 16px; }
.ai-candidate__evidence { margin-top: 2px; padding: 10px 12px; border-radius: 6px; background: var(--el-fill-color-light); }
.ai-field-label { display: block; margin-bottom: 5px; color: var(--el-text-color-secondary); font-size: 12px; }
.ai-candidate__evidence blockquote { margin: 0; color: var(--el-text-color-primary); line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; }
.ai-candidate__matched-skill { display: flex; gap: 12px; align-items: baseline; margin-top: 12px; color: var(--el-text-color-primary); }
.ai-candidate__matched-skill .ai-field-label { flex-shrink: 0; margin-bottom: 0; }
.meta-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.meta-grid div { display: flex; flex-direction: column; gap: 6px; }
.meta-grid span, .source-link span { color: var(--el-text-color-secondary); font-size: 12px; }
.meta-grid strong { color: var(--el-text-color-primary); font-weight: 500; }
.source-link { display: flex; flex-wrap: wrap; gap: 12px; align-items: baseline; margin-top: 18px; }
.source-link a { color: var(--el-color-primary); overflow-wrap: anywhere; }
section h3 { margin: 0 0 12px; font-size: 16px; }
.raw-jd { margin: 0; padding: 16px; border-radius: 6px; background: var(--el-fill-color-light); color: var(--el-text-color-primary); line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.table-wrap { min-height: 160px; }
.notes-list { min-height: 140px; }
.note-item { display: flex; justify-content: space-between; gap: 16px; padding: 14px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.note-item:first-child { padding-top: 0; }
.note-item:last-child { border-bottom: 0; padding-bottom: 0; }
.note-item p { flex: 1; margin: 0; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.note-actions { flex-shrink: 0; }
.full-width { width: 100%; }
.form-tip { display: block; margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; }
.form-tip--warning { color: var(--el-color-warning); }
@media (max-width: 760px) {
  .job-heading, .section-heading { align-items: flex-start; flex-direction: column; }
  .job-actions, .section-actions { width: 100%; justify-content: flex-start; }
  .section-actions .el-button { flex: 1 1 auto; }
  .meta-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .ai-candidate__grid { grid-template-columns: 1fr; }
}
</style>
