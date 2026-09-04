<template>
  <div class="page-container detail-page">
    <div class="back-row">
      <el-button link type="primary" @click="goBack">← 返回投递列表</el-button>
    </div>

    <el-skeleton v-if="loading && !application" :rows="12" animated />
    <el-result v-else-if="!application" icon="warning" title="投递不存在" sub-title="请返回投递列表后重试" />
    <template v-else>
      <el-card shadow="never" class="hero-card">
        <template #header>
          <div class="hero-heading">
            <div>
              <div class="eyebrow">APPLICATION #{{ application.id }}</div>
              <h1>{{ application.jobTitleSnapshot }}</h1>
              <p>{{ application.companyNameSnapshot }}<span v-if="application.locationSnapshot"> · {{ application.locationSnapshot }}</span></p>
            </div>
            <div class="hero-heading__status">
              <el-tag :type="stageTagType(application.currentStage)" size="large">{{ stageLabel(application.currentStage) }}</el-tag>
              <el-button v-if="availableTransitions.length" type="primary" plain @click="openTransitionDialog()">更新阶段</el-button>
            </div>
          </div>
        </template>

        <div class="meta-grid">
          <div><span>投递时间</span><strong>{{ formatDate(application.appliedAt) }}</strong></div>
          <div><span>绑定简历版本</span><strong>版本 #{{ application.resumeVersionId }}</strong></div>
          <div><span>最近更新</span><strong>{{ formatDate(application.updatedAt ?? application.appliedAt) }}</strong></div>
          <div><span>结束时间</span><strong>{{ formatDate(application.endedAt) }}</strong></div>
        </div>
        <div v-if="application.currentStage === 'ENDED'" class="ended-summary">
          <span>结束原因</span>
          <strong>{{ endReasonLabel(application.endReason) }}</strong>
          <p v-if="application.endNote">{{ application.endNote }}</p>
        </div>
        <el-collapse v-if="application.jobDescriptionSnapshot" class="snapshot-collapse">
          <el-collapse-item title="查看投递时保存的岗位描述" name="job-description">
            <p class="snapshot-text">{{ application.jobDescriptionSnapshot }}</p>
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <div class="content-grid content-grid--top">
        <el-card shadow="never" class="section-card stage-card">
          <template #header>
            <div class="section-heading">
              <div><h2>流程阶段</h2><p>只展示当前阶段可以迁移到的候选状态。</p></div>
            </div>
          </template>
          <el-steps :active="stageIndex" finish-status="success" process-status="process" align-center class="stage-steps">
            <el-step v-for="option in stageOptions" :key="option.value" :title="option.shortLabel" />
          </el-steps>
          <div v-if="availableTransitions.length" class="transition-actions">
            <span class="muted">下一步</span>
            <el-button v-for="target in availableTransitions" :key="target" size="small" type="primary" plain @click="openTransitionDialog(target)">
              {{ stageLabel(target) }}
            </el-button>
          </div>
          <el-alert v-else title="这条投递已经结束，阶段不可再迁移。" type="info" :closable="false" show-icon />
        </el-card>

        <el-card shadow="never" class="section-card history-card">
          <template #header>
            <div class="section-heading"><div><h2>阶段历史</h2><p>每一次状态变化都会被保留。</p></div></div>
          </template>
          <div v-loading="historyLoading" class="history-list">
            <el-empty v-if="!historyLoading && history.length === 0" description="还没有阶段历史" />
            <el-timeline v-else>
              <el-timeline-item v-for="item in history" :key="item.id" :timestamp="formatDate(item.changedAt)" placement="top">
                <strong>{{ item.fromStage ? `${stageLabel(item.fromStage)} → ` : '' }}{{ stageLabel(item.toStage) }}</strong>
                <p v-if="item.endReason" class="history-note">{{ endReasonLabel(item.endReason) }}</p>
                <p v-if="item.note" class="history-note">{{ item.note }}</p>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-card>
      </div>

      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="section-heading">
            <div><h2>测评记录</h2><p>记录笔试、在线测评等结果；新增记录不会自动改变投递阶段。</p></div>
            <el-button type="primary" @click="openAssessmentCreate">新增测评</el-button>
          </div>
        </template>
        <div v-loading="assessmentsLoading" class="resource-table">
          <el-empty v-if="!assessmentsLoading && assessments.length === 0" description="还没有测评记录" />
          <el-table v-else :data="assessments" stripe row-key="id">
            <el-table-column prop="title" label="测评名称" min-width="180" show-overflow-tooltip />
            <el-table-column label="类型" width="150" show-overflow-tooltip><template #default="{ row }">{{ assessmentTypeLabel(row.type) }}</template></el-table-column>
            <el-table-column label="计划时间" width="170"><template #default="{ row }">{{ formatDate(row.scheduledAt) }}</template></el-table-column>
            <el-table-column label="结果" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ assessmentResultLabel(row.result) }}</template></el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openAssessmentEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removeAssessment(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="section-heading">
            <div><h2>面试记录</h2><p>按轮次整理面试安排和反馈；新增记录不会自动改变投递阶段。</p></div>
            <el-button type="primary" @click="openInterviewCreate">新增面试</el-button>
          </div>
        </template>
        <div v-loading="interviewsLoading" class="resource-table">
          <el-empty v-if="!interviewsLoading && interviews.length === 0" description="还没有面试记录" />
          <el-table v-else :data="interviews" stripe row-key="id">
            <el-table-column label="轮次" width="90"><template #default="{ row }">第 {{ row.roundNo }} 轮</template></el-table-column>
            <el-table-column prop="title" label="面试名称" min-width="180" show-overflow-tooltip />
            <el-table-column label="形式" width="130" show-overflow-tooltip><template #default="{ row }">{{ interviewTypeLabel(row.type) }}</template></el-table-column>
            <el-table-column label="计划时间" width="170"><template #default="{ row }">{{ formatDate(row.scheduledAt) }}</template></el-table-column>
            <el-table-column label="结果" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ interviewResultLabel(row.result) }}</template></el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openInterviewEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removeInterview(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <div class="content-grid" :class="{ 'content-grid--single': !isEnded }">
        <el-card shadow="never" class="section-card offer-card">
          <template #header>
            <div class="section-heading">
              <div><h2>Offer</h2><p>创建后会进入 Offer 处理中阶段。</p></div>
              <el-button v-if="!offer && !isEnded" type="primary" @click="openOfferCreate">记录 Offer</el-button>
              <el-button v-else-if="offer && offer.status === 'CONSIDERING'" type="primary" plain @click="openOfferEdit">编辑</el-button>
            </div>
          </template>
          <div v-loading="offerLoading" class="offer-content">
            <el-empty v-if="!offerLoading && !offer" description="还没有 Offer 记录">
              <span class="muted">收到 Offer 后可以在这里记录具体信息。</span>
            </el-empty>
            <template v-else-if="offer">
              <div class="offer-status-row">
                <el-tag :type="offerTagType(offer.status)" size="large">{{ offerStatusLabel(offer.status) }}</el-tag>
                <span v-if="offer.expiresAt" class="muted">有效期至 {{ formatDate(offer.expiresAt) }}</span>
              </div>
              <dl class="detail-list">
                <div><dt>职位</dt><dd>{{ offer.positionTitle || '未填写' }}</dd></div>
                <div><dt>薪酬</dt><dd>{{ offer.compensation || '未填写' }}</dd></div>
                <div><dt>备注</dt><dd class="multiline">{{ offer.notes || '未填写' }}</dd></div>
              </dl>
              <el-alert v-if="offer.status !== 'CONSIDERING'" title="Offer 已结束，信息仅供查看。" type="info" :closable="false" />
            </template>
          </div>
        </el-card>

        <el-card v-if="isEnded" shadow="never" class="section-card review-card">
          <template #header>
            <div class="section-heading">
              <div><h2>最终复盘</h2><p>结束后再回看这次经历，把经验带到下一次准备中。</p></div>
              <el-button v-if="finalReview" type="primary" plain @click="openReviewEdit">编辑复盘</el-button>
              <el-button v-else type="primary" @click="openReviewCreate">填写复盘</el-button>
            </div>
          </template>
          <div v-loading="reviewLoading" class="review-content">
            <el-empty v-if="!reviewLoading && !finalReview" description="还没有填写最终复盘" />
            <template v-else-if="finalReview">
              <div class="review-rating"><span class="muted">自评</span><el-rate :model-value="readonlyRating" disabled /></div>
              <div class="review-block"><span>总结</span><p>{{ finalReview.summary }}</p></div>
              <div v-if="finalReview.lessonsLearned" class="review-block"><span>学到什么</span><p>{{ finalReview.lessonsLearned }}</p></div>
              <div v-if="finalReview.improvements" class="review-block"><span>下次改进</span><p>{{ finalReview.improvements }}</p></div>
              <div class="review-date">复盘时间：{{ formatDate(finalReview.reviewedAt) }}</div>
            </template>
          </div>
        </el-card>
      </div>
    </template>

    <el-dialog v-model="transitionDialogVisible" title="更新投递阶段" width="560px" destroy-on-close>
      <el-form ref="transitionFormRef" :model="transitionForm" label-position="top" @submit.prevent="submitTransition">
        <el-form-item label="目标阶段" required>
          <el-select v-model="transitionForm.targetStage" class="full-width">
            <el-option v-for="target in availableTransitions" :key="target" :label="stageLabel(target)" :value="target" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="transitionForm.targetStage === 'ENDED'" label="结束原因" required>
          <el-select v-model="transitionForm.endReason" class="full-width" placeholder="选择结束原因">
            <el-option v-for="option in endReasonOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="transitionForm.targetStage === 'ENDED' ? '结束备注' : '阶段备注'">
          <el-input v-model="transitionForm.note" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="可选，记录本次阶段变化的背景" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="transitionDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingTransition" @click="submitTransition">保存阶段</el-button></template>
    </el-dialog>

    <el-dialog v-model="assessmentDialogVisible" :title="assessmentEditingId === null ? '新增测评' : '编辑测评'" width="640px" destroy-on-close>
      <el-form ref="assessmentFormRef" :model="assessmentForm" :rules="assessmentRules" label-position="top" @submit.prevent="saveAssessment">
        <div class="form-grid">
          <el-form-item label="测评类型" prop="type"><el-select v-model="assessmentForm.type" class="full-width"><el-option v-for="option in assessmentTypeOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
          <el-form-item label="测评名称" prop="title"><el-input v-model="assessmentForm.title" maxlength="180" placeholder="例如：技术笔试" /></el-form-item>
          <el-form-item label="计划时间"><el-date-picker v-model="assessmentForm.scheduledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" placeholder="选择时间" /></el-form-item>
          <el-form-item label="实际完成时间"><el-date-picker v-model="assessmentForm.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" placeholder="选择时间" /></el-form-item>
        </div>
        <el-form-item label="结果"><el-select v-model="assessmentForm.result" class="full-width" clearable placeholder="选择结果"><el-option v-for="option in assessmentResultOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
        <el-form-item label="备注"><el-input v-model="assessmentForm.notes" type="textarea" :rows="5" maxlength="8000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="assessmentDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingAssessment" @click="saveAssessment">保存记录</el-button></template>
    </el-dialog>

    <el-dialog v-model="interviewDialogVisible" :title="interviewEditingId === null ? '新增面试' : '编辑面试'" width="640px" destroy-on-close>
      <el-form ref="interviewFormRef" :model="interviewForm" :rules="interviewRules" label-position="top" @submit.prevent="saveInterview">
        <div class="form-grid">
          <el-form-item label="面试轮次" prop="roundNo"><el-input-number v-model="interviewForm.roundNo" :min="1" :max="30" controls-position="right" class="full-width" /></el-form-item>
          <el-form-item label="面试形式" prop="type"><el-select v-model="interviewForm.type" class="full-width"><el-option v-for="option in interviewTypeOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
          <el-form-item label="面试名称" prop="title"><el-input v-model="interviewForm.title" maxlength="180" placeholder="例如：技术一面" /></el-form-item>
          <el-form-item label="计划时间"><el-date-picker v-model="interviewForm.scheduledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" placeholder="选择时间" /></el-form-item>
          <el-form-item label="实际完成时间"><el-date-picker v-model="interviewForm.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" placeholder="选择时间" /></el-form-item>
        </div>
        <el-form-item label="结果"><el-select v-model="interviewForm.result" class="full-width" clearable placeholder="选择结果"><el-option v-for="option in interviewResultOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
        <el-form-item label="面试备注"><el-input v-model="interviewForm.notes" type="textarea" :rows="5" maxlength="8000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="interviewDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingInterview" @click="saveInterview">保存记录</el-button></template>
    </el-dialog>

    <el-dialog v-model="offerDialogVisible" :title="offerDialogMode === 'create' ? '记录 Offer' : '编辑 Offer'" width="600px" destroy-on-close>
      <el-form ref="offerFormRef" :model="offerForm" :rules="offerRules" label-position="top" @submit.prevent="saveOffer">
        <el-alert v-if="offerDialogMode === 'create'" title="新建 Offer 默认处于“考虑中”，之后可以再更新为接受或拒绝。" type="info" :closable="false" show-icon class="dialog-alert" />
        <el-form-item v-else label="处理结果" prop="status"><el-select v-model="offerForm.status" class="full-width"><el-option v-for="option in offerStatusOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
        <div class="form-grid">
          <el-form-item label="职位名称"><el-input v-model="offerForm.positionTitle" maxlength="180" placeholder="可选" /></el-form-item>
          <el-form-item label="薪酬信息"><el-input v-model="offerForm.compensation" maxlength="180" placeholder="例如：25k × 14 薪" /></el-form-item>
          <el-form-item label="有效期至"><el-date-picker v-model="offerForm.expiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" placeholder="选择时间" /></el-form-item>
        </div>
        <el-form-item label="备注"><el-input v-model="offerForm.notes" type="textarea" :rows="5" maxlength="8000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="offerDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingOffer" @click="saveOffer">保存 Offer</el-button></template>
    </el-dialog>

    <el-dialog v-model="reviewDialogVisible" :title="finalReview ? '编辑最终复盘' : '填写最终复盘'" width="640px" destroy-on-close>
      <el-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules" label-position="top" @submit.prevent="saveReview">
        <el-form-item label="复盘总结" prop="summary"><el-input v-model="reviewForm.summary" type="textarea" :rows="5" maxlength="8000" show-word-limit placeholder="这次求职过程的整体结果和关键收获" /></el-form-item>
        <el-form-item label="学到什么"><el-input v-model="reviewForm.lessonsLearned" type="textarea" :rows="4" maxlength="8000" show-word-limit /></el-form-item>
        <el-form-item label="下次改进"><el-input v-model="reviewForm.improvements" type="textarea" :rows="4" maxlength="8000" show-word-limit /></el-form-item>
        <div class="form-grid">
          <el-form-item label="自评分数"><el-rate v-model="reviewForm.rating" show-score /></el-form-item>
          <el-form-item label="复盘时间" prop="reviewedAt"><el-date-picker v-model="reviewForm.reviewedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" placeholder="选择时间" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="reviewDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingReview" @click="saveReview">保存复盘</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  createAssessment,
  createInterview,
  createOffer,
  deleteAssessment,
  deleteInterview,
  getApplication,
  getFinalReview,
  getOffer,
  listApplicationHistory,
  listAssessments,
  listInterviews,
  transitionApplication,
  updateAssessment,
  updateInterview,
  updateOffer,
  upsertFinalReview,
} from '@/api/application'
import type {
  Application,
  ApplicationEndReason,
  ApplicationStage,
  ApplicationStageHistory,
  ApplicationTransitionRequest,
  Assessment,
  AssessmentResult,
  AssessmentType,
  AssessmentRequest,
  FinalReview,
  FinalReviewRequest,
  Interview,
  InterviewResult,
  InterviewType,
  InterviewRequest,
  Offer,
  OfferCreateRequest,
  OfferStatus,
  OfferUpdateRequest,
} from '@/types/application'

const route = useRoute()
const router = useRouter()
const applicationId = computed(() => Number(route.params.applicationId))

const application = ref<Application | null>(null)
const history = ref<ApplicationStageHistory[]>([])
const assessments = ref<Assessment[]>([])
const interviews = ref<Interview[]>([])
const offer = ref<Offer | null>(null)
const finalReview = ref<FinalReview | null>(null)

const loading = ref(false)
const historyLoading = ref(false)
const assessmentsLoading = ref(false)
const interviewsLoading = ref(false)
const offerLoading = ref(false)
const reviewLoading = ref(false)
const savingTransition = ref(false)
const savingAssessment = ref(false)
const savingInterview = ref(false)
const savingOffer = ref(false)
const savingReview = ref(false)

const transitionDialogVisible = ref(false)
const assessmentDialogVisible = ref(false)
const interviewDialogVisible = ref(false)
const offerDialogVisible = ref(false)
const reviewDialogVisible = ref(false)
const assessmentEditingId = ref<number | null>(null)
const interviewEditingId = ref<number | null>(null)
const offerDialogMode = ref<'create' | 'edit'>('create')

const transitionFormRef = ref<FormInstance>()
const assessmentFormRef = ref<FormInstance>()
const interviewFormRef = ref<FormInstance>()
const offerFormRef = ref<FormInstance>()
const reviewFormRef = ref<FormInstance>()

const stageOptions: Array<{ value: ApplicationStage; label: string; shortLabel: string }> = [
  { value: 'APPLIED', label: '已投递', shortLabel: '已投递' },
  { value: 'ASSESSMENT', label: '测评中', shortLabel: '测评' },
  { value: 'INTERVIEW', label: '面试中', shortLabel: '面试' },
  { value: 'OFFER', label: 'Offer 处理中', shortLabel: 'Offer' },
  { value: 'ENDED', label: '已结束', shortLabel: '结束' },
]

const transitionMatrix: Record<ApplicationStage, ApplicationStage[]> = {
  APPLIED: ['ASSESSMENT', 'INTERVIEW', 'OFFER', 'ENDED'],
  ASSESSMENT: ['INTERVIEW', 'OFFER', 'ENDED'],
  INTERVIEW: ['OFFER', 'ENDED'],
  OFFER: ['ENDED'],
  ENDED: [],
}

const endReasonOptions: Array<{ value: ApplicationEndReason; label: string }> = [
  { value: 'COMPANY_REJECTED', label: '公司拒绝' },
  { value: 'NO_RESPONSE', label: '长时间无回应' },
  { value: 'NO_LONGER_INTERESTED', label: '不再感兴趣' },
  { value: 'ACCEPTED_ANOTHER_OFFER', label: '已接受其他 Offer' },
  { value: 'LOCATION_ISSUE', label: '地点不合适' },
  { value: 'COMPENSATION', label: '薪酬不合适' },
  { value: 'PERSONAL_REASON', label: '个人原因' },
  { value: 'OTHER', label: '其他原因' },
]

const offerStatusOptions: Array<{ value: OfferStatus; label: string }> = [
  { value: 'CONSIDERING', label: '考虑中' },
  { value: 'ACCEPTED', label: '已接受' },
  { value: 'REJECTED', label: '已拒绝' },
]

const assessmentTypeOptions: Array<{ value: AssessmentType; label: string }> = [
  { value: 'ONLINE_ASSESSMENT', label: '在线测评' },
  { value: 'WRITTEN_TEST', label: '笔试' },
  { value: 'CODING_TEST', label: '编程测试' },
  { value: 'OTHER', label: '其他' },
]

const assessmentResultOptions: Array<{ value: AssessmentResult; label: string }> = [
  { value: 'PENDING', label: '待定' },
  { value: 'PASSED', label: '通过' },
  { value: 'FAILED', label: '未通过' },
  { value: 'OTHER', label: '其他' },
]

const interviewTypeOptions: Array<{ value: InterviewType; label: string }> = [
  { value: 'HR', label: 'HR 面' },
  { value: 'TECHNICAL', label: '技术面' },
  { value: 'MANAGER', label: '主管面' },
  { value: 'FINAL', label: '终面' },
  { value: 'OTHER', label: '其他' },
]

const interviewResultOptions: Array<{ value: InterviewResult; label: string }> = [
  { value: 'PENDING', label: '待定' },
  { value: 'PASSED', label: '通过' },
  { value: 'REJECTED', label: '未通过' },
  { value: 'OTHER', label: '其他' },
]

function emptyTransition(): ApplicationTransitionRequest {
  return { targetStage: 'ASSESSMENT', note: '' }
}

function emptyAssessment(): AssessmentRequest {
  return { type: 'ONLINE_ASSESSMENT', title: '', scheduledAt: '', occurredAt: '', result: undefined, notes: '' }
}

function emptyInterview(): InterviewRequest {
  return { roundNo: 1, type: 'HR', title: '', scheduledAt: '', occurredAt: '', result: undefined, notes: '' }
}

function emptyOffer(): OfferUpdateRequest {
  return { status: 'CONSIDERING', positionTitle: '', compensation: '', expiresAt: '', notes: '' }
}

function localDateTimeNow(): string {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function emptyReview(): FinalReviewRequest {
  return { summary: '', lessonsLearned: '', improvements: '', rating: undefined, reviewedAt: localDateTimeNow() }
}

const transitionForm = reactive<ApplicationTransitionRequest>(emptyTransition())
const assessmentForm = reactive<AssessmentRequest>(emptyAssessment())
const interviewForm = reactive<InterviewRequest>(emptyInterview())
const offerForm = reactive<OfferUpdateRequest>(emptyOffer())
const reviewForm = reactive<FinalReviewRequest>(emptyReview())

const assessmentRules: FormRules = {
  type: [{ required: true, message: '请输入测评类型', trigger: 'blur' }],
  title: [{ required: true, message: '请输入测评名称', trigger: 'blur' }],
}
const interviewRules: FormRules = {
  roundNo: [{ required: true, type: 'number', min: 1, message: '请输入有效轮次', trigger: 'change' }],
  type: [{ required: true, message: '请输入面试形式', trigger: 'blur' }],
  title: [{ required: true, message: '请输入面试名称', trigger: 'blur' }],
}
const offerRules: FormRules = {
  status: [{ required: true, message: '请选择 Offer 结果', trigger: 'change' }],
}
const reviewRules: FormRules = {
  summary: [{ required: true, message: '请填写复盘总结', trigger: 'blur' }],
  reviewedAt: [{ required: true, message: '请选择复盘时间', trigger: 'change' }],
}

const isEnded = computed(() => application.value?.currentStage === 'ENDED')
const availableTransitions = computed<ApplicationStage[]>(() => {
  const stage = application.value?.currentStage
  return stage ? transitionMatrix[stage] : []
})
const stageIndex = computed(() => {
  const stage = application.value?.currentStage
  return stage ? stageOptions.findIndex((option) => option.value === stage) : 0
})
const readonlyRating = computed(() => finalReview.value?.rating ?? 0)

function stageLabel(value: ApplicationStage): string {
  return stageOptions.find((option) => option.value === value)?.label ?? value
}

function stageTagType(value: ApplicationStage): 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'ENDED') return 'info'
  if (value === 'OFFER') return 'success'
  if (value === 'ASSESSMENT' || value === 'INTERVIEW') return 'warning'
  return 'success'
}

function endReasonLabel(value?: ApplicationEndReason | null): string {
  const labels: Partial<Record<ApplicationEndReason, string>> = {
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

function offerStatusLabel(value: OfferStatus): string {
  return offerStatusOptions.find((option) => option.value === value)?.label ?? value
}

function assessmentTypeLabel(value: AssessmentType): string {
  return assessmentTypeOptions.find((option) => option.value === value)?.label ?? value
}

function assessmentResultLabel(value?: AssessmentResult | null): string {
  return value ? assessmentResultOptions.find((option) => option.value === value)?.label ?? value : '未填写'
}

function interviewTypeLabel(value: InterviewType): string {
  return interviewTypeOptions.find((option) => option.value === value)?.label ?? value
}

function interviewResultLabel(value?: InterviewResult | null): string {
  return value ? interviewResultOptions.find((option) => option.value === value)?.label ?? value : '未填写'
}

function offerTagType(value: OfferStatus): 'success' | 'warning' | 'danger' {
  if (value === 'ACCEPTED') return 'success'
  if (value === 'REJECTED') return 'danger'
  return 'warning'
}

function formatDate(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function isNotFound(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 404
}

function clean(value?: string): string | undefined {
  const trimmed = value?.trim()
  return trimmed || undefined
}

async function loadHistory(id: number): Promise<void> {
  historyLoading.value = true
  try { history.value = await listApplicationHistory(id) } catch { /* interceptor handles error */ } finally { historyLoading.value = false }
}

async function loadAssessments(): Promise<void> {
  if (!application.value) return
  assessmentsLoading.value = true
  try { assessments.value = await listAssessments(application.value.id) } catch { /* interceptor handles error */ } finally { assessmentsLoading.value = false }
}

async function loadInterviews(): Promise<void> {
  if (!application.value) return
  interviewsLoading.value = true
  try { interviews.value = await listInterviews(application.value.id) } catch { /* interceptor handles error */ } finally { interviewsLoading.value = false }
}

async function loadOffer(id: number): Promise<void> {
  offerLoading.value = true
  try {
    offer.value = await getOffer(id)
    Object.assign(offerForm, {
      status: offer.value.status,
      positionTitle: offer.value.positionTitle ?? '',
      compensation: offer.value.compensation ?? '',
      expiresAt: offer.value.expiresAt ?? '',
      notes: offer.value.notes ?? '',
    })
  } catch (error) {
    if (isNotFound(error)) offer.value = null
  } finally { offerLoading.value = false }
}

async function loadFinalReview(id: number): Promise<void> {
  if (!isEnded.value) {
    finalReview.value = null
    return
  }
  reviewLoading.value = true
  try {
    finalReview.value = await getFinalReview(id)
  } catch (error) {
    if (isNotFound(error)) finalReview.value = null
  } finally { reviewLoading.value = false }
}

async function load(): Promise<void> {
  const id = applicationId.value
  if (!Number.isInteger(id) || id <= 0) return
  loading.value = true
  application.value = null
  history.value = []
  assessments.value = []
  interviews.value = []
  offer.value = null
  finalReview.value = null
  try {
    application.value = await getApplication(id)
  } catch {
    application.value = null
  } finally {
    loading.value = false
  }
  if (!application.value) return
  await Promise.all([
    loadHistory(id),
    loadAssessments(),
    loadInterviews(),
    loadOffer(id),
    loadFinalReview(id),
  ])
}

function goBack(): void {
  void router.push({ name: 'applications' })
}

function openTransitionDialog(target?: ApplicationStage): void {
  const nextTarget = target ?? availableTransitions.value[0]
  if (!nextTarget || !availableTransitions.value.includes(nextTarget)) return
  Object.assign(transitionForm, { targetStage: nextTarget, endReason: undefined, note: '' })
  transitionDialogVisible.value = true
}

async function submitTransition(): Promise<void> {
  if (!application.value) return
  const valid = await transitionFormRef.value?.validate().catch(() => false)
  if (valid === false) return
  if (transitionForm.targetStage === 'ENDED' && !transitionForm.endReason) {
    ElMessage.warning('请选择结束原因')
    return
  }
  savingTransition.value = true
  const payload: ApplicationTransitionRequest = {
    targetStage: transitionForm.targetStage,
    endReason: transitionForm.targetStage === 'ENDED' ? transitionForm.endReason : undefined,
    note: clean(transitionForm.note),
  }
  try {
    await transitionApplication(application.value.id, payload)
    transitionDialogVisible.value = false
    ElMessage.success('投递阶段已更新')
    await load()
  } catch { /* interceptor handles error */ } finally { savingTransition.value = false }
}

function openAssessmentCreate(): void {
  assessmentEditingId.value = null
  Object.assign(assessmentForm, emptyAssessment())
  assessmentDialogVisible.value = true
}

function openAssessmentEdit(row: Assessment): void {
  assessmentEditingId.value = row.id
  Object.assign(assessmentForm, {
    type: row.type,
    title: row.title,
    scheduledAt: row.scheduledAt ?? '',
    occurredAt: row.occurredAt ?? '',
    result: row.result ?? '',
    notes: row.notes ?? '',
  })
  assessmentDialogVisible.value = true
}

async function saveAssessment(): Promise<void> {
  if (!application.value) return
  const valid = await assessmentFormRef.value?.validate().catch(() => false)
  if (valid === false) return
  savingAssessment.value = true
  const payload: AssessmentRequest = {
    type: assessmentForm.type,
    title: assessmentForm.title.trim(),
    scheduledAt: clean(assessmentForm.scheduledAt),
    occurredAt: clean(assessmentForm.occurredAt),
    result: assessmentForm.result,
    notes: clean(assessmentForm.notes),
  }
  try {
    if (assessmentEditingId.value === null) await createAssessment(application.value.id, payload)
    else await updateAssessment(application.value.id, assessmentEditingId.value, payload)
    assessmentDialogVisible.value = false
    ElMessage.success(assessmentEditingId.value === null ? '测评记录已新增' : '测评记录已更新')
    await loadAssessments()
  } catch { /* interceptor handles error */ } finally { savingAssessment.value = false }
}

async function removeAssessment(row: Assessment): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${row.title}”这条测评记录吗？`, '确认删除', { type: 'warning' })
    await deleteAssessment(applicationId.value, row.id)
    ElMessage.success('测评记录已删除')
    await loadAssessments()
  } catch { /* cancellation and interceptor error */ }
}

function openInterviewCreate(): void {
  interviewEditingId.value = null
  Object.assign(interviewForm, emptyInterview(), { roundNo: interviews.value.length + 1 })
  interviewDialogVisible.value = true
}

function openInterviewEdit(row: Interview): void {
  interviewEditingId.value = row.id
  Object.assign(interviewForm, {
    roundNo: row.roundNo,
    type: row.type,
    title: row.title,
    scheduledAt: row.scheduledAt ?? '',
    occurredAt: row.occurredAt ?? '',
    result: row.result ?? '',
    notes: row.notes ?? '',
  })
  interviewDialogVisible.value = true
}

async function saveInterview(): Promise<void> {
  if (!application.value) return
  const valid = await interviewFormRef.value?.validate().catch(() => false)
  if (valid === false) return
  savingInterview.value = true
  const payload: InterviewRequest = {
    roundNo: interviewForm.roundNo,
    type: interviewForm.type,
    title: interviewForm.title.trim(),
    scheduledAt: clean(interviewForm.scheduledAt),
    occurredAt: clean(interviewForm.occurredAt),
    result: interviewForm.result,
    notes: clean(interviewForm.notes),
  }
  try {
    if (interviewEditingId.value === null) await createInterview(application.value.id, payload)
    else await updateInterview(application.value.id, interviewEditingId.value, payload)
    interviewDialogVisible.value = false
    ElMessage.success(interviewEditingId.value === null ? '面试记录已新增' : '面试记录已更新')
    await loadInterviews()
  } catch { /* interceptor handles error */ } finally { savingInterview.value = false }
}

async function removeInterview(row: Interview): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${row.title}”这条面试记录吗？`, '确认删除', { type: 'warning' })
    await deleteInterview(applicationId.value, row.id)
    ElMessage.success('面试记录已删除')
    await loadInterviews()
  } catch { /* cancellation and interceptor error */ }
}

function openOfferCreate(): void {
  offerDialogMode.value = 'create'
  Object.assign(offerForm, emptyOffer())
  offerDialogVisible.value = true
}

function openOfferEdit(): void {
  if (!offer.value || offer.value.status !== 'CONSIDERING') return
  offerDialogMode.value = 'edit'
  Object.assign(offerForm, {
    status: offer.value.status,
    positionTitle: offer.value.positionTitle ?? '',
    compensation: offer.value.compensation ?? '',
    expiresAt: offer.value.expiresAt ?? '',
    notes: offer.value.notes ?? '',
  })
  offerDialogVisible.value = true
}

async function saveOffer(): Promise<void> {
  if (!application.value) return
  const valid = await offerFormRef.value?.validate().catch(() => false)
  if (valid === false) return
  savingOffer.value = true
  const fields: OfferCreateRequest = {
    positionTitle: clean(offerForm.positionTitle),
    compensation: clean(offerForm.compensation),
    expiresAt: clean(offerForm.expiresAt),
    notes: clean(offerForm.notes),
  }
  try {
    if (offerDialogMode.value === 'create') {
      await createOffer(application.value.id, fields)
      ElMessage.success('Offer 已记录，投递阶段已更新')
    } else {
      const payload: OfferUpdateRequest = { ...fields, status: offerForm.status }
      await updateOffer(application.value.id, payload)
      ElMessage.success(offerForm.status === 'CONSIDERING' ? 'Offer 已更新' : 'Offer 结果已保存')
    }
    offerDialogVisible.value = false
    // Offer changes can also migrate the application to OFFER or ENDED.
    await load()
  } catch { /* interceptor handles error */ } finally { savingOffer.value = false }
}

function fillReviewForm(review?: FinalReview | null): void {
  Object.assign(reviewForm, {
    summary: review?.summary ?? '',
    lessonsLearned: review?.lessonsLearned ?? '',
    improvements: review?.improvements ?? '',
    rating: review?.rating ?? undefined,
    reviewedAt: review?.reviewedAt ?? localDateTimeNow(),
  })
}

function openReviewCreate(): void {
  if (!isEnded.value) return
  fillReviewForm()
  reviewDialogVisible.value = true
}

function openReviewEdit(): void {
  if (!isEnded.value || !finalReview.value) return
  fillReviewForm(finalReview.value)
  reviewDialogVisible.value = true
}

async function saveReview(): Promise<void> {
  if (!application.value || !isEnded.value) return
  const valid = await reviewFormRef.value?.validate().catch(() => false)
  if (valid === false) return
  savingReview.value = true
  const payload: FinalReviewRequest = {
    summary: reviewForm.summary.trim(),
    lessonsLearned: clean(reviewForm.lessonsLearned),
    improvements: clean(reviewForm.improvements),
    rating: reviewForm.rating,
    reviewedAt: reviewForm.reviewedAt,
  }
  try {
    await upsertFinalReview(application.value.id, payload)
    reviewDialogVisible.value = false
    ElMessage.success('最终复盘已保存')
    await loadFinalReview(application.value.id)
  } catch { /* interceptor handles error */ } finally { savingReview.value = false }
}

watch(applicationId, load)
onMounted(load)
</script>

<style scoped>
.detail-page { max-width: 1320px; }
.back-row { margin-bottom: 12px; }
.hero-card, .section-card { margin-bottom: 18px; border-radius: 14px; }
.hero-card { border-top: 3px solid var(--cp-accent); }
.hero-heading, .section-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.eyebrow { color: var(--cp-accent); font-size: 11px; font-weight: 800; letter-spacing: .12em; }
.hero-heading h1 { margin: 6px 0 0; color: var(--cp-ink); font-size: 28px; line-height: 1.25; }
.hero-heading p { margin: 7px 0 0; color: var(--cp-muted); font-size: 14px; }
.hero-heading__status { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 10px; }
.meta-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 18px; }
.meta-grid div { display: flex; flex-direction: column; gap: 6px; }
.meta-grid span, .ended-summary span { color: var(--cp-muted); font-size: 12px; }
.meta-grid strong, .ended-summary strong { color: var(--cp-ink); font-weight: 650; }
.ended-summary { margin-top: 22px; padding: 14px 16px; border-radius: 9px; background: #fff6ef; }
.ended-summary strong { display: block; margin-top: 4px; color: #9a4f2c; }
.ended-summary p { margin: 8px 0 0; color: var(--cp-muted); white-space: pre-wrap; }
.snapshot-collapse { margin-top: 18px; }
.snapshot-text { margin: 0; color: var(--cp-ink); line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; }
.content-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; align-items: stretch; }
.content-grid--top { margin-bottom: 0; }
.content-grid--single { grid-template-columns: 1fr; }
.section-heading { align-items: flex-start; }
.section-heading h2 { margin: 0; color: var(--cp-ink); font-size: 18px; }
.section-heading p { margin: 6px 0 0; color: var(--cp-muted); font-size: 13px; line-height: 1.5; }
.stage-card, .history-card { min-height: 286px; }
.stage-steps { margin: 20px 0 30px; }
.transition-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.transition-actions .muted { margin-right: 3px; }
.history-list { min-height: 180px; }
.history-list :deep(.el-timeline) { margin: 4px 0 0; padding-left: 4px; }
.history-list :deep(.el-timeline-item__timestamp) { color: var(--cp-muted); font-size: 12px; }
.history-note { margin: 5px 0 0; color: var(--cp-muted); font-size: 13px; white-space: pre-wrap; }
.resource-table { min-height: 170px; }
.offer-content, .review-content { min-height: 184px; }
.offer-status-row { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.detail-list { display: grid; gap: 14px; margin: 0 0 20px; }
.detail-list div { display: grid; grid-template-columns: 80px minmax(0, 1fr); gap: 14px; }
.detail-list dt { color: var(--cp-muted); font-size: 13px; }
.detail-list dd { margin: 0; color: var(--cp-ink); font-size: 14px; }
.multiline { white-space: pre-wrap; overflow-wrap: anywhere; }
.review-rating { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
.review-block { margin-bottom: 16px; }
.review-block > span { color: var(--cp-muted); font-size: 12px; }
.review-block p { margin: 6px 0 0; color: var(--cp-ink); line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.review-date { color: var(--cp-muted); font-size: 12px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
.dialog-alert { margin-bottom: 18px; }
.muted { color: var(--cp-muted); font-size: 13px; }
@media (max-width: 900px) { .content-grid { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .hero-heading, .section-heading { align-items: flex-start; flex-direction: column; } .hero-heading__status { width: 100%; justify-content: flex-start; } .meta-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .form-grid { grid-template-columns: 1fr; } .stage-steps :deep(.el-step__title) { font-size: 11px; } }
@media (max-width: 480px) { .meta-grid { grid-template-columns: 1fr; } }
</style>
