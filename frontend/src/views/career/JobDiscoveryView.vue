<template>
  <div class="discovery-page" :aria-busy="discovering || initialLoading">
    <header class="discovery-hero">
      <div class="discovery-hero__copy">
        <p class="eyebrow">岗位发现 / SOURCE-LED SEARCH</p>
        <h1>发现岗位，核对来源后再保存</h1>
        <p class="discovery-hero__lede">
          让职业目标和技能帮你缩小搜索范围。候选岗位只在本次会话中保留约 15 分钟，保存前请逐项核对网页来源。
        </p>
      </div>
      <div class="discovery-hero__signal" aria-label="候选岗位保存期限约 15 分钟">
        <div class="signal-mark" aria-hidden="true">
          <span />
          <span />
          <span />
          <span />
        </div>
        <div>
          <span class="signal-mark__label">候选有效期</span>
          <strong>约 15 分钟</strong>
        </div>
      </div>
    </header>

    <el-alert
      v-if="initialError"
      class="page-alert"
      type="warning"
      :title="initialError"
      :closable="false"
      show-icon
    />

    <div class="discovery-layout">
      <aside class="search-rail">
        <section class="search-card">
          <div class="section-kicker">SEARCH BRIEF</div>
          <h2>先说清楚想找什么</h2>
          <p class="search-card__intro">搜索会使用所选职业目标和你的技能背景，补充说明只用于本次发现。</p>

          <el-form class="search-form" label-position="top" @submit.prevent="submitSearch">
            <el-form-item label="职业目标" required>
              <el-select
                v-model="selectedGoalId"
                class="full-width"
                filterable
                :loading="goalsLoading"
                :disabled="discovering || saving"
                placeholder="选择一个职业目标"
              >
                <el-option v-for="goal in goals" :key="goal.id" :label="goalLabel(goal)" :value="goal.id">
                  <div class="goal-option">
                    <span>{{ goal.targetPosition }}</span>
                    <small>{{ goalStatusLabel(goal.status) }}</small>
                  </div>
                </el-option>
              </el-select>
              <span v-if="goals.length === 0 && !goalsLoading" class="field-tip">
                还没有可用的职业目标，先去
                <RouterLink :to="{ name: 'career-goals' }">创建职业目标</RouterLink>。
              </span>
            </el-form-item>

            <el-form-item label="搜索补充说明">
              <el-input
                v-model="searchNote"
                type="textarea"
                :rows="4"
                maxlength="1000"
                show-word-limit
                :disabled="discovering || saving"
                placeholder="例如：偏好能接触数据平台的团队，接受应届校招"
              />
            </el-form-item>

            <el-form-item label="本次搜索地点">
              <el-input
                v-model="locationOverride"
                maxlength="100"
                :disabled="discovering || saving"
                placeholder="覆盖职业目标中的地点，例如：上海"
              />
            </el-form-item>

            <el-form-item label="最大候选数">
              <el-input-number
                v-model="maxCandidates"
                class="full-width"
                :min="1"
                :max="10"
                :step="1"
                controls-position="right"
                :disabled="discovering || saving"
              />
              <span class="field-tip">每次最多获取 10 个候选，默认 5 个。</span>
            </el-form-item>

            <el-button
              native-type="submit"
              type="primary"
              class="search-submit"
              :loading="discovering"
              :disabled="initialLoading || goalsLoading || saving"
            >
              <el-icon v-if="!discovering"><Search /></el-icon>
              {{ discovering ? '正在发现…' : '开始发现岗位' }}
            </el-button>
          </el-form>
        </section>

        <section class="trust-card">
          <div class="trust-card__icon" aria-hidden="true"><el-icon><Lock /></el-icon></div>
          <div>
            <strong>先核对，再写入</strong>
            <p>网页事实、候选字段和 AI 建议分开呈现。只有你点击确认保存，才会生成正式岗位。</p>
          </div>
        </section>
      </aside>

      <main class="results-column">
        <div class="results-heading">
          <div>
            <p class="section-kicker">DISCOVERY QUEUE</p>
            <h2>推荐岗位 <span v-if="rankedCandidates.length" class="result-count">{{ rankedCandidates.length }}</span></h2>
            <p class="results-heading__description">
              {{ resultsDescription }}
            </p>
          </div>
          <div v-if="searchCalls !== null" class="search-stat">
            <span>本次搜索调用</span>
            <strong>{{ searchCalls }} 次</strong>
          </div>
        </div>

        <el-alert
          v-if="discoveryError"
          class="result-alert"
          type="error"
          :title="discoveryError"
          :closable="false"
          show-icon
        />

        <el-alert
          v-if="warnings.length > 0"
          class="result-alert"
          type="info"
          title="本次发现有提示"
          :closable="false"
          show-icon
        >
          <ul class="warning-list">
            <li v-for="(warning, index) in warnings" :key="`warning-${index}`">{{ warning }}</li>
          </ul>
        </el-alert>

        <section v-if="discovering" class="state-card state-card--loading" aria-live="polite">
          <div class="loading-orbit" aria-hidden="true"><span /><span /><span /></div>
          <h3>正在寻找匹配岗位</h3>
          <p>正在整理搜索结果并生成匹配解释，可能需要几分钟。请保持此页面打开。</p>
        </section>

        <template v-else-if="rankedCandidates.length > 0">
          <article
            v-for="candidate in rankedCandidates"
            :key="candidate.candidateId"
            class="candidate-card"
            :class="{ 'candidate-card--expired': (isExpired(candidate) || isUnavailable(candidate)) && !isConsumed(candidate) }"
          >
            <header class="candidate-card__header">
              <div class="rank-badge" :aria-label="`AI 排名 ${candidate.aiAdvice.rank}`">
                <span>RANK</span>
                <strong>{{ candidate.aiAdvice.rank }}</strong>
              </div>
              <div class="candidate-title-block">
                <p class="candidate-title-block__eyebrow">候选岗位 · 待核实字段</p>
                <h3>{{ candidate.extractedFields.jobTitle || '未提供岗位名称' }}</h3>
                <div class="candidate-meta">
                  <span><el-icon><OfficeBuilding /></el-icon>{{ candidate.extractedFields.companyName || '待核实' }}</span>
                  <span><el-icon><Location /></el-icon>{{ candidate.extractedFields.location || '地点未知' }}</span>
                </div>
              </div>
              <el-tag v-if="isConsumed(candidate)" type="success" effect="plain">已保存</el-tag>
              <el-tag v-else-if="isUnavailable(candidate)" type="info" effect="plain">已失效</el-tag>
              <el-tag v-else-if="isExpired(candidate)" type="info" effect="plain">已过期</el-tag>
            </header>

            <div class="extracted-strip">
              <div>
                <span>候选字段</span>
                <strong>{{ candidate.extractedFields.jobTitle || '未提供' }}</strong>
              </div>
              <div>
                <span>公司</span>
                <strong>{{ candidate.extractedFields.companyName || '待核实' }}</strong>
              </div>
              <div>
                <span>地点</span>
                <strong>{{ candidate.extractedFields.location || '地点未知' }}</strong>
              </div>
              <div>
                <span>岗位类型建议</span>
                <strong>{{ jobTypeSuggestionLabel(candidate.extractedFields.jobTypeSuggestion) }}</strong>
              </div>
              <small>保存前可编辑</small>
            </div>

            <div class="candidate-card__body">
              <section class="evidence-panel evidence-panel--source">
                <div class="panel-heading">
                  <span class="panel-heading__index">01</span>
                  <div>
                    <h4>来源事实</h4>
                    <p>网页原文提供的可核对信息</p>
                  </div>
                </div>
                <dl class="facts-list">
                  <div>
                    <dt>来源网站</dt>
                    <dd>{{ candidate.sourceFacts.sourceHost || '未知来源网站' }}</dd>
                  </div>
                  <div>
                    <dt>发布日期</dt>
                    <dd>{{ formatPublishedAt(candidate.sourceFacts.publishedAt) }}</dd>
                  </div>
                  <div>
                    <dt>网页标题</dt>
                    <dd>{{ candidate.sourceFacts.sourceTitle || '未提供网页标题' }}</dd>
                  </div>
                </dl>
                <div class="snippet-box">
                  <span>来源摘要</span>
                  <p>{{ candidate.sourceFacts.sourceSnippet || '未提供来源摘要' }}</p>
                </div>
                <a
                  v-if="safeSourceUrl(candidate.sourceFacts.sourceUrl)"
                  class="source-link"
                  :href="safeSourceUrl(candidate.sourceFacts.sourceUrl)"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  查看原始来源
                  <el-icon><TopRight /></el-icon>
                </a>
                <span v-else class="source-link source-link--disabled">来源链接不可用</span>
              </section>

              <section class="evidence-panel evidence-panel--ai">
                <div class="panel-heading panel-heading--ai">
                  <span class="panel-heading__index">02</span>
                  <div>
                    <h4>AI 匹配分析</h4>
                    <p>基于职业目标和技能的建议</p>
                  </div>
                  <el-tag type="warning" effect="plain" size="small">AI 建议</el-tag>
                </div>
                <p class="fit-summary">{{ candidate.aiAdvice.fitSummary || '本次未提供匹配说明。' }}</p>

                <div v-if="candidate.aiAdvice.matchedSkills.length > 0" class="matched-skills">
                  <span class="analysis-label">匹配技能</span>
                  <div class="skill-list">
                    <el-tag v-for="skill in candidate.aiAdvice.matchedSkills" :key="skill.key" type="success" effect="plain">
                      {{ skill.name }}
                    </el-tag>
                  </div>
                </div>

                <div class="analysis-grid">
                  <div class="analysis-list">
                    <h5>优势</h5>
                    <ul v-if="candidate.aiAdvice.strengths.length > 0">
                      <li v-for="(strength, index) in candidate.aiAdvice.strengths" :key="`${candidate.candidateId}-strength-${index}`">{{ strength }}</li>
                    </ul>
                    <span v-else class="analysis-empty">未提供</span>
                  </div>
                  <div class="analysis-list">
                    <h5>技能缺口</h5>
                    <ul v-if="candidate.aiAdvice.gaps.length > 0">
                      <li v-for="(gap, index) in candidate.aiAdvice.gaps" :key="`${candidate.candidateId}-gap-${index}`">{{ gap }}</li>
                    </ul>
                    <span v-else class="analysis-empty">未提供</span>
                  </div>
                  <div class="analysis-list">
                    <h5>不确定项</h5>
                    <ul v-if="candidate.aiAdvice.uncertainty.length > 0">
                      <li v-for="(item, index) in candidate.aiAdvice.uncertainty" :key="`${candidate.candidateId}-uncertainty-${index}`">{{ item }}</li>
                    </ul>
                    <span v-else class="analysis-empty">未提供</span>
                  </div>
                </div>
              </section>
            </div>

            <footer class="candidate-card__footer">
              <span class="expiry-copy" :class="{ 'expiry-copy--expired': (isExpired(candidate) || isUnavailable(candidate)) && !isConsumed(candidate) }">
                <el-icon><Timer /></el-icon>
                {{ isConsumed(candidate) ? '已写入我的岗位' : isUnavailable(candidate) ? '候选已失效，请重新发现' : expiryLabel(candidate) }}
              </span>
              <div class="card-actions">
                <el-button
                  v-if="isConsumed(candidate)"
                  link
                  type="success"
                  @click="viewSavedJob(consumedJobIds[candidate.candidateId])"
                >
                  查看岗位 <el-icon><ArrowRight /></el-icon>
                </el-button>
                <el-button v-else-if="isUnavailable(candidate)" type="info" plain disabled>候选已失效</el-button>
                <el-button
                  v-else
                  type="primary"
                  plain
                  :disabled="discovering || saving || isExpired(candidate)"
                  @click="openConfirm(candidate)"
                >
                  {{ isExpired(candidate) ? '候选已过期' : '保存到我的岗位' }}
                </el-button>
              </div>
            </footer>
          </article>
        </template>

        <section v-else-if="discoveryError" class="state-card state-card--error">
          <div class="state-card__icon"><el-icon><WarningFilled /></el-icon></div>
          <h3>这次发现没有完成</h3>
          <p>{{ searchContextDescription }}。可以保留当前输入，稍后重试。</p>
        </section>

        <section v-else-if="hasSearched" class="state-card state-card--empty">
          <div class="empty-compass" aria-hidden="true"><el-icon><Compass /></el-icon></div>
          <h3>暂时没有可靠候选</h3>
          <p>{{ searchContextDescription }}没有找到可核对的岗位来源。试着调整地点或补充说明后再次发现。</p>
        </section>

        <section v-else class="state-card state-card--guide">
          <div class="guide-mark" aria-hidden="true">
            <span>01</span>
            <span>02</span>
            <span>03</span>
          </div>
          <h3>从一个清晰的目标开始</h3>
          <p>选择职业目标，补充这次搜索的边界，查看真实来源后再决定是否保存。</p>
          <div class="guide-steps">
            <div><strong>选择</strong><span>职业目标</span></div>
            <div><strong>发现</strong><span>真实来源</span></div>
            <div><strong>核对</strong><span>再保存岗位</span></div>
          </div>
        </section>
      </main>
    </div>

    <el-drawer
      v-model="drawerVisible"
      class="confirm-drawer"
      title="确认保存到我的岗位"
      direction="rtl"
      size="min(520px, 100vw)"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      destroy-on-close
    >
      <div v-if="confirmCandidate">
        <div class="drawer-intro">
          <p class="section-kicker">FINAL CHECK</p>
          <h2>{{ confirmCandidate.extractedFields.jobTitle || '未提供岗位名称' }}</h2>
          <p>核对公司、岗位类型和名称后保存，原始来源将随岗位保留。</p>
        </div>

        <el-alert
          v-if="confirmError"
          class="drawer-alert"
          type="error"
          :title="confirmError"
          :closable="false"
          show-icon
        />

        <el-alert
          v-if="confirmCandidate && isConfirmUnavailable"
          class="drawer-alert"
          type="warning"
          title="候选已失效，无法再次保存，请重新发现岗位。"
          :closable="false"
          show-icon
        />

        <el-alert
          v-else-if="confirmCandidate && isExpired(confirmCandidate) && !isConsumed(confirmCandidate)"
          class="drawer-alert"
          type="warning"
          title="候选已过期，请重新发现岗位。"
          :closable="false"
          show-icon
        />

        <el-form class="confirm-form" label-position="top" @submit.prevent="confirmSave">
          <el-form-item label="公司" required>
            <el-select
              v-model="confirmForm.companyId"
              class="full-width"
              filterable
              :loading="companiesLoading"
              :disabled="saving || isConfirmExpired"
              placeholder="明确选择已有公司"
            >
              <el-option v-for="company in companies" :key="company.id" :label="company.name" :value="company.id" />
            </el-select>
            <span v-if="companies.length === 0 && !companiesLoading" class="field-tip">
              还没有公司档案，请先
              <a :href="companiesPageHref" target="_blank" rel="noopener noreferrer">在新标签页创建公司</a>，再回到这里刷新。
              <el-button link type="primary" size="small" :disabled="saving" @click="refreshCompanies">刷新公司列表</el-button>
            </span>
            <span v-else class="field-tip">候选中的公司名称仅供核对，不会自动创建公司。</span>
            <span v-if="companyRefreshError" class="field-tip field-tip--error">{{ companyRefreshError }}</span>
          </el-form-item>

          <el-form-item label="岗位名称" required>
            <el-input
              v-model="confirmForm.title"
              maxlength="150"
              show-word-limit
              :disabled="saving || isConfirmExpired"
              placeholder="确认正式岗位名称"
            />
          </el-form-item>

          <el-form-item label="城市">
            <el-input
              v-model="confirmForm.city"
              maxlength="100"
              :disabled="saving || isConfirmExpired"
              placeholder="可按需要修改，例如：上海"
            />
          </el-form-item>

          <el-form-item label="岗位类型" required>
            <el-select
              v-model="confirmForm.jobType"
              class="full-width"
              :disabled="saving || isConfirmExpired"
              placeholder="请选择岗位类型"
            >
              <el-option v-for="option in jobTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <span v-if="confirmCandidate.extractedFields.jobTypeSuggestion" class="field-tip">
              AI 建议：{{ jobTypeSuggestionLabel(confirmCandidate.extractedFields.jobTypeSuggestion) }}，仍需你确认。
            </span>
          </el-form-item>

          <el-form-item label="完整 JD（可选）">
            <el-input
              v-model="confirmForm.rawJd"
              type="textarea"
              :rows="8"
              maxlength="16000"
              show-word-limit
              :disabled="saving || isConfirmExpired"
              placeholder="粘贴完整岗位描述，便于后续使用 AI 岗位要求解析"
            />
            <span class="field-tip">暂不填写也可保存，补全完整岗位描述后可使用 AI 岗位要求解析。</span>
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button :disabled="saving" @click="drawerVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="isConfirmExpired"
            @click="confirmSave"
          >
            确认保存
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Compass,
  Location,
  Lock,
  OfficeBuilding,
  Search,
  Timer,
  TopRight,
  WarningFilled,
} from '@element-plus/icons-vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

import { confirmAiDiscoveredJob, discoverJobsWithAi, listCareerGoals, listCompanies } from '@/api/career'
import { getApiErrorMessage } from '@/api/client'
import type {
  CareerGoal,
  CareerGoalStatus,
  Company,
  JobCandidate,
  JobDiscoveryConfirmRequest,
  JobDiscoveryRequest,
  JobType,
} from '@/types/career'

interface SearchContext {
  goalId: number
  note: string
  location: string
  maxCandidates: number
}

interface ConfirmForm {
  companyId: number | null
  title: string
  city: string
  jobType: JobType | null
  rawJd: string
}

const router = useRouter()
const goals = ref<CareerGoal[]>([])
const companies = ref<Company[]>([])
const goalsLoading = ref(false)
const companiesLoading = ref(false)
const initialLoading = ref(true)
const initialError = ref('')
const companyRefreshError = ref('')

const selectedGoalId = ref<number | null>(null)
const searchNote = ref('')
const locationOverride = ref('')
const maxCandidates = ref(5)

const candidates = ref<JobCandidate[]>([])
const warnings = ref<string[]>([])
const searchCalls = ref<number | null>(null)
const discovering = ref(false)
const hasSearched = ref(false)
const discoveryError = ref('')
const lastSearchContext = ref<SearchContext | null>(null)

const drawerVisible = ref(false)
const confirmCandidate = ref<JobCandidate | null>(null)
const saving = ref(false)
const confirmError = ref('')
const confirmForm = reactive<ConfirmForm>({
  companyId: null,
  title: '',
  city: '',
  jobType: null,
  rawJd: '',
})
const consumedJobIds = reactive<Record<string, number>>({})
const unavailableCandidateIds = reactive<Record<string, boolean>>({})
const now = ref(Date.now())
let expiryTimer: ReturnType<typeof setInterval> | undefined

const jobTypeOptions: Array<{ value: JobType; label: string }> = [
  { value: 'FULL_TIME', label: '全职' },
  { value: 'INTERNSHIP', label: '实习' },
  { value: 'CAMPUS', label: '校招' },
  { value: 'PART_TIME', label: '兼职' },
  { value: 'CONTRACT', label: '合同制' },
  { value: 'OTHER', label: '其他' },
]

const rankedCandidates = computed(() => {
  return [...candidates.value].sort((left, right) => {
    const leftRank = Number.isFinite(left.aiAdvice?.rank) ? left.aiAdvice.rank : Number.MAX_SAFE_INTEGER
    const rightRank = Number.isFinite(right.aiAdvice?.rank) ? right.aiAdvice.rank : Number.MAX_SAFE_INTEGER
    if (leftRank !== rightRank) return leftRank - rightRank
    return left.candidateId.localeCompare(right.candidateId)
  })
})

const activeGoal = computed(() => goals.value.find((goal) => goal.id === selectedGoalId.value))
const companiesPageHref = computed(() => router.resolve({ name: 'career-companies' }).href)
const isConfirmExpired = computed(() => {
  return confirmCandidate.value
    ? (isExpired(confirmCandidate.value) || isUnavailable(confirmCandidate.value)) && !isConsumed(confirmCandidate.value)
    : true
})
const isConfirmUnavailable = computed(() => {
  return confirmCandidate.value ? isUnavailable(confirmCandidate.value) && !isConsumed(confirmCandidate.value) : false
})
const searchContextDescription = computed(() => {
  const context = lastSearchContext.value
  if (!context) return '请先选择职业目标并开始一次搜索'
  const goal = goals.value.find((item) => item.id === context.goalId)
  const goalName = goal?.targetPosition || '所选职业目标'
  const location = context.location ? `，地点为“${context.location}”` : ''
  return `基于“${goalName}”${location}的本次搜索`
})
const resultsDescription = computed(() => {
  if (discovering.value) return '正在整理候选和来源事实…'
  if (rankedCandidates.value.length > 0) return '按 AI 匹配排名排列；请分别查看来源事实和匹配建议。'
  if (hasSearched.value) return '本次搜索已经完成，可以调整左侧条件后重新发现。'
  return '搜索结果会在这里出现，候选岗位只保留约 15 分钟。'
})

function goalStatusLabel(status: CareerGoalStatus): string {
  const labels: Record<CareerGoalStatus, string> = {
    ACTIVE: '进行中',
    PAUSED: '已暂停',
    ACHIEVED: '已达成',
  }
  return labels[status]
}

function goalLabel(goal: CareerGoal): string {
  const details = [goal.targetCity, goal.targetIndustry].filter(Boolean).join(' · ')
  return details ? `${goal.targetPosition} · ${details}` : goal.targetPosition
}

function jobTypeSuggestionLabel(value: string | null): string {
  if (!value) return '未提供'
  const option = jobTypeOptions.find((item) => item.value === value)
  return option?.label ?? value
}

function formatPublishedAt(value: string | null): string {
  if (!value) return '未知'
  // The provider date is a calendar date (YYYY-MM-DD), not a timestamp.
  // Keep it as received so a browser timezone cannot shift the source fact.
  return value.length >= 10 ? value.slice(0, 10) : value
}

function safeSourceUrl(value: string): string {
  try {
    const parsed = new URL(value)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? value : ''
  } catch {
    return ''
  }
}

function isExpired(candidate: JobCandidate): boolean {
  const expiresAt = Date.parse(candidate.expiresAt)
  return Number.isNaN(expiresAt) || expiresAt <= now.value
}

function expiryLabel(candidate: JobCandidate): string {
  const expiresAt = Date.parse(candidate.expiresAt)
  if (Number.isNaN(expiresAt) || expiresAt <= now.value) return '候选已过期，请重新发现'
  const remainingMinutes = Math.ceil((expiresAt - now.value) / 60_000)
  return `候选还可保留约 ${Math.max(1, remainingMinutes)} 分钟`
}

function isConsumed(candidate: JobCandidate): boolean {
  return typeof consumedJobIds[candidate.candidateId] === 'number'
}

function isUnavailable(candidate: JobCandidate): boolean {
  return unavailableCandidateIds[candidate.candidateId] === true
}

function errorStatus(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined
}

function viewSavedJob(jobId: number | undefined): void {
  if (typeof jobId !== 'number') return
  void router.push({ name: 'career-job-detail', params: { jobId } })
}

function clearStaleResults(): void {
  if (discovering.value) return
  if (!hasSearched.value && candidates.value.length === 0 && !discoveryError.value) return
  candidates.value = []
  warnings.value = []
  searchCalls.value = null
  discoveryError.value = ''
  hasSearched.value = false
  lastSearchContext.value = null
}

watch([selectedGoalId, searchNote, locationOverride, maxCandidates], clearStaleResults)

async function loadInitialData(): Promise<void> {
  initialLoading.value = true
  initialError.value = ''
  goalsLoading.value = true
  companiesLoading.value = true

  const [goalsResult, companiesResult] = await Promise.allSettled([listCareerGoals(), listCompanies()])

  if (goalsResult.status === 'fulfilled') {
    goals.value = goalsResult.value
  } else {
    initialError.value = `职业目标暂时加载失败：${getApiErrorMessage(goalsResult.reason, '请稍后刷新页面重试')}`
  }

  if (companiesResult.status === 'fulfilled') {
    companies.value = companiesResult.value
  } else {
    const companyError = `公司列表暂时加载失败：${getApiErrorMessage(companiesResult.reason, '打开确认面板时可以再次刷新')}`
    initialError.value = initialError.value ? `${initialError.value}；${companyError}` : companyError
  }

  goalsLoading.value = false
  companiesLoading.value = false
  initialLoading.value = false
}

async function refreshCompanies(): Promise<void> {
  companiesLoading.value = true
  companyRefreshError.value = ''
  try {
    companies.value = await listCompanies()
  } catch (error: unknown) {
    companyRefreshError.value = `公司列表刷新失败：${getApiErrorMessage(error, '请稍后重试')}`
  } finally {
    companiesLoading.value = false
  }
}

async function submitSearch(): Promise<void> {
  if (discovering.value || saving.value) return

  const goalId = selectedGoalId.value
  if (goalId === null) {
    ElMessage.warning('请先选择职业目标')
    return
  }

  const note = searchNote.value.trim()
  const location = locationOverride.value.trim()
  const max = Number(maxCandidates.value)
  if (note.length > 1000) {
    ElMessage.warning('搜索补充说明不能超过 1000 个字符')
    return
  }
  if (location.length > 100) {
    ElMessage.warning('本次搜索地点不能超过 100 个字符')
    return
  }
  if (!Number.isInteger(max) || max < 1 || max > 10) {
    ElMessage.warning('最大候选数需要在 1 到 10 之间')
    return
  }

  const payload: JobDiscoveryRequest = {
    careerGoalId: goalId,
    maxCandidates: max,
  }
  if (note) payload.searchNote = note
  if (location) payload.locationOverride = location

  lastSearchContext.value = { goalId, note, location, maxCandidates: max }
  hasSearched.value = true
  discoveryError.value = ''
  discovering.value = true
  try {
    const response = await discoverJobsWithAi(payload)
    now.value = Date.now()
    candidates.value = response.candidates ?? []
    warnings.value = response.warnings ?? []
    searchCalls.value = typeof response.searchCalls === 'number' ? response.searchCalls : null
  } catch (error: unknown) {
    discoveryError.value = `${getApiErrorMessage(error, '岗位发现暂时不可用')} 当前输入和已有候选仍保留。`
  } finally {
    discovering.value = false
  }
}

function openConfirm(candidate: JobCandidate): void {
  if (saving.value || isExpired(candidate) || isUnavailable(candidate)) {
    ElMessage.warning(isUnavailable(candidate) ? '候选已失效，请重新发现岗位' : '候选已过期，请重新发现岗位')
    return
  }
  confirmCandidate.value = candidate
  confirmError.value = ''
  companyRefreshError.value = ''
  Object.assign(confirmForm, {
    companyId: null,
    title: candidate.extractedFields.jobTitle || '',
    city: candidate.extractedFields.location || '',
    jobType: null,
    rawJd: '',
  })
  drawerVisible.value = true
}

watch(drawerVisible, (visible) => {
  if (visible) {
    void refreshCompanies()
  } else if (!saving.value) {
    confirmError.value = ''
  }
})

watch(confirmForm, () => {
  if (!saving.value) confirmError.value = ''
})

async function confirmSave(): Promise<void> {
  const candidate = confirmCandidate.value
  if (!candidate || saving.value) return
  if (isConfirmExpired.value) {
    confirmError.value = isConfirmUnavailable.value ? '候选已失效，请重新发现岗位。' : '候选已过期，请重新发现岗位。'
    return
  }

  const title = confirmForm.title.trim()
  const city = confirmForm.city.trim()
  const rawJd = confirmForm.rawJd.trim()
  if (confirmForm.companyId === null) {
    confirmError.value = '请选择一个已有公司后再保存。'
    return
  }
  if (!confirmForm.jobType) {
    confirmError.value = '请选择岗位类型后再保存。'
    return
  }
  if (!title) {
    confirmError.value = '岗位名称不能为空。'
    return
  }
  if (title.length > 150) {
    confirmError.value = '岗位名称不能超过 150 个字符。'
    return
  }
  if (city.length > 100) {
    confirmError.value = '城市不能超过 100 个字符。'
    return
  }
  if (rawJd.length > 16_000) {
    confirmError.value = '完整 JD 不能超过 16000 个字符。'
    return
  }

  const payload: JobDiscoveryConfirmRequest = {
    candidateId: candidate.candidateId,
    companyId: confirmForm.companyId,
    title,
    jobType: confirmForm.jobType,
  }
  if (city) payload.city = city
  if (rawJd) payload.rawJd = rawJd

  saving.value = true
  confirmError.value = ''
  try {
    const response = await confirmAiDiscoveredJob(payload)
    consumedJobIds[candidate.candidateId] = response.jobId
    drawerVisible.value = false
    ElMessage.success('岗位已保存到我的岗位')
    if (response.warnings?.length) {
      ElMessage.info(response.warnings.join('；'))
    }
  } catch (error: unknown) {
    const status = errorStatus(error)
    if (status === 409) {
      unavailableCandidateIds[candidate.candidateId] = true
      confirmError.value = '候选已失效或已被保存，请重新发现岗位。'
    } else if (status === 404) {
      confirmError.value = '请检查所选公司或重新发现岗位。'
    } else {
      confirmError.value = `${getApiErrorMessage(error, '保存岗位失败')} 候选仍保留，可以检查后重试。`
    }
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadInitialData()
  expiryTimer = setInterval(() => {
    now.value = Date.now()
  }, 1_000)
})

onBeforeUnmount(() => {
  if (expiryTimer) clearInterval(expiryTimer)
})
</script>

<style scoped>
.discovery-page {
  --discovery-ink: #1d2b36;
  --discovery-muted: #6d7f89;
  --discovery-line: #dce7eb;
  --discovery-teal: #174a68;
  --discovery-teal-soft: #eaf3f5;
  --discovery-coral: #c26a3b;
  --discovery-paper: #fff;
  max-width: 1480px;
  margin: 0 auto;
  padding: 34px 38px 60px;
  color: var(--discovery-ink);
}

.discovery-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  margin-bottom: 28px;
  padding-bottom: 26px;
  border-bottom: 1px solid var(--discovery-line);
}

.eyebrow,
.section-kicker {
  margin: 0;
  color: #81929b;
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 0.15em;
  line-height: 1.5;
  text-transform: uppercase;
}

.discovery-hero h1 {
  max-width: 720px;
  margin: 9px 0 9px;
  color: var(--discovery-teal);
  font-size: clamp(28px, 4vw, 46px);
  font-weight: 820;
  letter-spacing: -0.045em;
  line-height: 1.1;
}

.discovery-hero__lede {
  max-width: 720px;
  margin: 0;
  color: var(--discovery-muted);
  font-size: 14px;
  line-height: 1.75;
}

.discovery-hero__signal {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 166px;
  padding: 13px 15px;
  border: 1px solid #d7e5e8;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.58);
}

.signal-mark {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  width: 30px;
  height: 28px;
}

.signal-mark span {
  display: block;
  width: 5px;
  border-radius: 3px 3px 1px 1px;
  background: var(--discovery-coral);
}

.signal-mark span:nth-child(1) { height: 9px; opacity: 0.44; }
.signal-mark span:nth-child(2) { height: 15px; opacity: 0.64; }
.signal-mark span:nth-child(3) { height: 22px; opacity: 0.82; }
.signal-mark span:nth-child(4) { height: 28px; }

.signal-mark__label {
  display: block;
  color: #80919a;
  font-size: 10px;
}

.discovery-hero__signal strong {
  display: block;
  margin-top: 2px;
  color: var(--discovery-teal);
  font-size: 15px;
}

.page-alert,
.result-alert {
  margin-bottom: 18px;
}

.discovery-layout {
  display: grid;
  grid-template-columns: minmax(252px, 302px) minmax(0, 1fr);
  align-items: start;
  gap: 28px;
}

.search-rail {
  position: sticky;
  top: 22px;
}

.search-card,
.trust-card,
.candidate-card,
.state-card {
  border: 1px solid var(--discovery-line);
  background: var(--discovery-paper);
  box-shadow: 0 16px 40px rgba(23, 74, 104, 0.055);
}

.search-card {
  padding: 22px 20px 20px;
  border-top: 3px solid var(--discovery-teal);
  border-radius: 14px;
}

.search-card h2 {
  margin: 8px 0 6px;
  color: var(--discovery-ink);
  font-size: 20px;
  letter-spacing: -0.02em;
}

.search-card__intro {
  margin: 0 0 20px;
  color: var(--discovery-muted);
  font-size: 12px;
  line-height: 1.65;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 17px;
}

.search-form :deep(.el-form-item__label),
.confirm-form :deep(.el-form-item__label) {
  height: auto;
  margin-bottom: 7px;
  color: #425965;
  font-size: 12px;
  font-weight: 760;
  line-height: 1.4;
}

.search-form :deep(.el-textarea__inner),
.confirm-form :deep(.el-textarea__inner),
.search-form :deep(.el-input__wrapper),
.confirm-form :deep(.el-input__wrapper) {
  border-radius: 9px;
  box-shadow: 0 0 0 1px #d5e2e6 inset;
}

.search-form :deep(.el-input__wrapper.is-focus),
.confirm-form :deep(.el-input__wrapper.is-focus),
.search-form :deep(.el-textarea__inner:focus),
.confirm-form :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px var(--discovery-teal) inset;
}

.full-width,
.search-submit {
  width: 100%;
}

.search-submit {
  height: 42px;
  margin-top: 3px;
  border-radius: 9px;
  font-weight: 750;
}

.search-submit .el-icon {
  margin-right: 7px;
}

.field-tip {
  display: block;
  margin-top: 6px;
  color: #82919a;
  font-size: 11px;
  line-height: 1.55;
}

.field-tip a {
  color: var(--discovery-teal);
  font-weight: 700;
}

.field-tip--error {
  color: #bb5b44;
}

.goal-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.goal-option small {
  color: #8a9aa2;
  font-size: 11px;
}

.trust-card {
  display: flex;
  gap: 11px;
  margin-top: 14px;
  padding: 15px;
  border-radius: 12px;
  background: #f5faf9;
  box-shadow: none;
}

.trust-card__icon {
  display: grid;
  flex: none;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: 8px;
  color: #367772;
  background: #dcefeb;
}

.trust-card strong {
  display: block;
  color: #2f5f62;
  font-size: 12px;
}

.trust-card p {
  margin: 4px 0 0;
  color: #6f8587;
  font-size: 11px;
  line-height: 1.55;
}

.results-column {
  min-width: 0;
}

.results-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.results-heading h2 {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 7px 0 4px;
  color: var(--discovery-ink);
  font-size: 24px;
  letter-spacing: -0.03em;
}

.result-count {
  display: inline-grid;
  width: 27px;
  height: 27px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: var(--discovery-teal);
  font-size: 12px;
  letter-spacing: 0;
}

.results-heading__description {
  margin: 0;
  color: var(--discovery-muted);
  font-size: 12px;
  line-height: 1.55;
}

.search-stat {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
  color: #80919a;
  font-size: 11px;
  white-space: nowrap;
}

.search-stat strong {
  color: var(--discovery-teal);
  font-size: 14px;
}

.warning-list {
  margin: 0;
  padding-left: 20px;
  color: #566f7b;
  font-size: 12px;
  line-height: 1.7;
}

.candidate-card {
  margin-bottom: 18px;
  overflow: hidden;
  border-radius: 15px;
}

.candidate-card--expired {
  opacity: 0.75;
}

.candidate-card__header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 19px 21px 16px;
  border-bottom: 1px solid #e8eff1;
}

.rank-badge {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 52px;
  border-radius: 9px;
  color: #fff;
  background: var(--discovery-teal);
}

.rank-badge span {
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.12em;
  opacity: 0.68;
}

.rank-badge strong {
  margin-top: 2px;
  font-size: 21px;
  line-height: 1;
}

.candidate-title-block {
  min-width: 0;
  flex: 1;
}

.candidate-title-block__eyebrow {
  margin: 1px 0 4px;
  color: #8a9ba3;
  font-size: 10px;
  font-weight: 780;
  letter-spacing: 0.1em;
}

.candidate-title-block h3 {
  overflow: hidden;
  margin: 0;
  color: var(--discovery-ink);
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.025em;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 16px;
  margin-top: 7px;
  color: #61747e;
  font-size: 12px;
}

.candidate-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.candidate-meta .el-icon {
  color: #78919a;
}

.candidate-card__header :deep(.el-tag) {
  flex: none;
  margin-top: 1px;
}

.extracted-strip {
  position: relative;
  display: grid;
  grid-template-columns: 1.3fr 1fr 0.8fr 1fr;
  gap: 14px;
  padding: 13px 21px 15px;
  border-bottom: 1px solid #e5edef;
  background: #fbfcfc;
}

.extracted-strip div {
  min-width: 0;
}

.extracted-strip span,
.extracted-strip strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.extracted-strip span {
  margin-bottom: 4px;
  color: #91a0a7;
  font-size: 10px;
}

.extracted-strip strong {
  color: #405964;
  font-size: 12px;
}

.extracted-strip > small {
  position: absolute;
  right: 21px;
  bottom: 3px;
  color: #a0adb2;
  font-size: 9px;
}

.candidate-card__body {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
}

.evidence-panel {
  min-width: 0;
  padding: 21px;
}

.evidence-panel--source {
  border-right: 1px solid #e5edef;
  background: #fbfdfd;
}

.evidence-panel--ai {
  background: #fff;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 17px;
}

.panel-heading__index {
  display: grid;
  width: 25px;
  height: 25px;
  flex: none;
  place-items: center;
  border: 1px solid #bfd5da;
  border-radius: 7px;
  color: #527580;
  font-size: 10px;
  font-weight: 800;
}

.panel-heading h4 {
  margin: 1px 0 2px;
  color: #314d58;
  font-size: 14px;
}

.panel-heading p {
  margin: 0;
  color: #94a1a7;
  font-size: 10px;
}

.panel-heading--ai .panel-heading__index {
  border-color: #eccab7;
  color: #a85f3d;
  background: #fff8f4;
}

.panel-heading--ai :deep(.el-tag) {
  margin-left: auto;
}

.facts-list {
  margin: 0;
}

.facts-list > div {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed #e3ebed;
}

.facts-list dt {
  color: #97a5aa;
  font-size: 10px;
}

.facts-list dd {
  overflow: hidden;
  margin: 0;
  color: #405964;
  font-size: 11px;
  line-height: 1.45;
  text-overflow: ellipsis;
}

.snippet-box {
  margin-top: 15px;
  padding: 11px 12px;
  border-left: 2px solid #bfd8dc;
  background: #f5faf9;
}

.snippet-box span {
  color: #7f969a;
  font-size: 10px;
  font-weight: 760;
}

.snippet-box p {
  display: -webkit-box;
  overflow: hidden;
  margin: 5px 0 0;
  color: #536b73;
  font-size: 11px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.source-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 14px;
  color: var(--discovery-teal);
  font-size: 12px;
  font-weight: 760;
}

.source-link:hover {
  color: #236584;
  text-decoration: underline;
}

.source-link--disabled {
  color: #9aa8ad;
  font-weight: 600;
}

.fit-summary {
  margin: 0 0 17px;
  color: #405964;
  font-size: 13px;
  line-height: 1.7;
}

.analysis-label {
  display: block;
  margin-bottom: 7px;
  color: #8c9ca3;
  font-size: 10px;
  font-weight: 760;
}

.skill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.skill-list :deep(.el-tag) {
  border-radius: 999px;
  font-size: 10px;
}

.matched-skills {
  margin-bottom: 20px;
}

.analysis-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.analysis-list {
  min-width: 0;
}

.analysis-list h5 {
  margin: 0 0 8px;
  color: #536a73;
  font-size: 11px;
}

.analysis-list:nth-child(1) h5 { color: #3f7770; }
.analysis-list:nth-child(2) h5 { color: #ac694b; }
.analysis-list:nth-child(3) h5 { color: #788894; }

.analysis-list ul {
  display: grid;
  gap: 7px;
  margin: 0;
  padding: 0 0 0 14px;
  color: #60737b;
  font-size: 11px;
  line-height: 1.55;
}

.analysis-empty {
  color: #a3afb3;
  font-size: 11px;
}

.candidate-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 13px 21px;
  border-top: 1px solid #e5edef;
  background: #fcfdfd;
}

.expiry-copy {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #80969b;
  font-size: 11px;
}

.expiry-copy .el-icon {
  color: #a1b4b5;
}

.expiry-copy--expired {
  color: #b66b55;
}

.card-actions :deep(.el-button) {
  font-weight: 730;
}

.card-actions .el-icon {
  margin-left: 3px;
}

.state-card {
  display: flex;
  min-height: 330px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 34px 24px;
  border-radius: 15px;
  text-align: center;
  box-shadow: none;
}

.state-card h3 {
  margin: 15px 0 5px;
  color: var(--discovery-ink);
  font-size: 17px;
}

.state-card p {
  max-width: 430px;
  margin: 0;
  color: var(--discovery-muted);
  font-size: 12px;
  line-height: 1.7;
}

.state-card--loading {
  background: #f9fcfc;
}

.loading-orbit {
  display: flex;
  align-items: flex-end;
  gap: 5px;
  height: 34px;
}

.loading-orbit span {
  display: block;
  width: 7px;
  height: 17px;
  border-radius: 5px;
  background: #7cabb0;
  animation: discovery-pulse 1.2s ease-in-out infinite;
}

.loading-orbit span:nth-child(2) { height: 29px; animation-delay: 0.14s; background: var(--discovery-teal); }
.loading-orbit span:nth-child(3) { height: 22px; animation-delay: 0.28s; background: var(--discovery-coral); }

@keyframes discovery-pulse {
  0%, 100% { transform: scaleY(0.65); opacity: 0.55; }
  50% { transform: scaleY(1); opacity: 1; }
}

.state-card--error {
  border-color: #edd8ce;
  background: #fffaf8;
}

.state-card__icon {
  display: grid;
  width: 43px;
  height: 43px;
  place-items: center;
  border-radius: 50%;
  color: #b76a4c;
  background: #f6e5dc;
  font-size: 21px;
}

.empty-compass {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border: 1px solid #cfe0e2;
  border-radius: 50%;
  color: #5c9294;
  background: #eef8f7;
  font-size: 24px;
}

.guide-mark {
  display: flex;
  gap: 6px;
}

.guide-mark span {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 1px solid #c7dce0;
  border-radius: 10px;
  color: #54868d;
  background: #f3fafa;
  font-size: 11px;
  font-weight: 800;
}

.guide-mark span:nth-child(2) { transform: translateY(-8px); color: #a8664a; background: #fff7f2; }
.guide-mark span:nth-child(3) { color: #527881; }

.guide-steps {
  display: flex;
  gap: 22px;
  margin-top: 24px;
}

.guide-steps div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.guide-steps strong {
  color: var(--discovery-teal);
  font-size: 12px;
}

.guide-steps span {
  color: #97a6aa;
  font-size: 10px;
}

.drawer-intro {
  padding-bottom: 18px;
  border-bottom: 1px solid #e4edef;
}

.drawer-intro h2 {
  overflow: hidden;
  margin: 7px 0 7px;
  color: var(--discovery-ink);
  font-size: 21px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-intro > p:last-child {
  margin: 0;
  color: #74858c;
  font-size: 12px;
  line-height: 1.65;
}

.drawer-alert {
  margin-top: 16px;
}

.confirm-form {
  padding-top: 20px;
}

.confirm-form :deep(.el-form-item) {
  margin-bottom: 19px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.confirm-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 21px 23px 17px;
  border-bottom: 1px solid #e4edef;
  color: var(--discovery-teal);
  font-weight: 780;
}

.confirm-drawer :deep(.el-drawer__body) {
  padding: 21px 23px 10px;
}

.confirm-drawer :deep(.el-drawer__footer) {
  padding: 14px 23px 20px;
  border-top: 1px solid #e4edef;
}

@media (max-width: 1060px) {
  .discovery-page { padding-right: 24px; padding-left: 24px; }
  .discovery-layout { grid-template-columns: minmax(230px, 270px) minmax(0, 1fr); gap: 20px; }
  .extracted-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); padding-bottom: 25px; }
}

@media (max-width: 860px) {
  .discovery-hero { align-items: flex-start; flex-direction: column; }
  .discovery-hero__signal { align-self: flex-start; }
  .discovery-layout { grid-template-columns: 1fr; }
  .search-rail { position: static; }
  .search-card { padding: 22px; }
  .candidate-card__body { grid-template-columns: 1fr; }
  .evidence-panel--source { border-right: 0; border-bottom: 1px solid #e5edef; }
}

@media (max-width: 560px) {
  .discovery-page { padding: 24px 16px 44px; }
  .discovery-hero { gap: 18px; margin-bottom: 21px; padding-bottom: 21px; }
  .discovery-hero h1 { font-size: 31px; }
  .discovery-hero__lede { font-size: 13px; }
  .results-heading { align-items: flex-start; flex-direction: column; gap: 10px; }
  .search-stat { align-items: flex-start; }
  .candidate-card__header { padding: 16px; }
  .candidate-card__body { display: block; }
  .extracted-strip { gap: 11px; padding-right: 16px; padding-left: 16px; }
  .evidence-panel { padding: 17px 16px; }
  .analysis-grid { grid-template-columns: 1fr; gap: 14px; }
  .candidate-card__footer { align-items: flex-start; flex-direction: column; padding: 13px 16px 16px; }
  .card-actions { width: 100%; }
  .card-actions :deep(.el-button) { width: 100%; }
  .candidate-title-block h3 { font-size: 17px; white-space: normal; }
  .rank-badge { width: 43px; height: 48px; }
  .state-card { min-height: 280px; }
  .guide-steps { gap: 13px; }
  .confirm-drawer :deep(.el-drawer__header),
  .confirm-drawer :deep(.el-drawer__body) { padding-right: 18px; padding-left: 18px; }
  .confirm-drawer :deep(.el-drawer__footer) { padding-right: 18px; padding-left: 18px; }
}

@media (prefers-reduced-motion: reduce) {
  .loading-orbit span { animation: none; }
}
</style>
