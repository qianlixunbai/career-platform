export type LearningPlanStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED'

export type LearningTaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'SKIPPED'

export interface LearningPlan {
  id: number
  weekStart: string
  weekEnd: string
  mainGoal: string
  status: LearningPlanStatus
  createdAt?: string
  updatedAt?: string
}

export interface LearningPlanRequest {
  weekStart: string
  weekEnd: string
  mainGoal: string
  status: LearningPlanStatus
}

/**
 * Inputs used to build an ephemeral AI plan candidate.  This request never
 * creates a LearningPlan; persistence happens only through the confirm API
 * after the user has reviewed and edited the candidate.
 */
export interface LearningPlanAiSuggestionRequest {
  weekStart: string
  weekEnd: string
  availableMinutes: number
  careerGoalId?: number
  /** Canonical backend name; `jobIds` remains accepted for the UI contract. */
  selectedJobIds?: number[]
  jobIds?: number[]
  focusNote?: string
}

export interface LearningPlanAiConfirmTaskRequest {
  title: string
  description?: string
  plannedMinutes: number
  dueDate: string
  sortOrder: number
}

export interface LearningPlanAiConfirmRequest {
  weekStart: string
  weekEnd: string
  availableMinutes: number
  mainGoal: string
  tasks: LearningPlanAiConfirmTaskRequest[]
}

export type LearningAiEvidenceType =
  | 'CAREER_GOAL'
  | 'USER_SKILL'
  | 'JOB_REQUIREMENT'
  | 'LEARNING_PLAN'
  | 'LEARNING_TASK'
  | 'STUDY_RECORD'
  | 'WEEKLY_REVIEW'
  | 'LEARNING_NOTE'
  | 'USER_FOCUS'
  | string

/** A fact assembled by Java from an owned, current-user context. */
export interface LearningAiEvidence {
  key: string
  type: LearningAiEvidenceType
  label: string
  excerpt?: string | null
  fact?: string | null
  value?: string | null
}

/** A compact description of a real source included in the AI context. */
export interface LearningAiSource {
  key?: string
  type?: LearningAiEvidenceType | null
  label: string
  excerpt?: string | null
  facts?: string[]
}

export interface LearningAiFact {
  key?: string
  label: string
  value?: string | null
  sourceKey?: string | null
}

export interface LearningPlanAiTaskSuggestion {
  title: string
  description?: string | null
  plannedMinutes: number
  dueDate: string
  sortOrder: number
  rationale?: string | null
  evidenceKeys: string[]
}

export interface LearningPlanAiSuggestionResponse {
  weekStart: string
  weekEnd: string
  availableMinutes: number
  totalPlannedMinutes: number
  bufferMinutes: number
  mainGoal: string
  rationale: string
  tasks: LearningPlanAiTaskSuggestion[]
  sources: LearningAiSource[]
  facts: LearningAiFact[]
  evidence: LearningAiEvidence[]
  warnings: string[]
}

export interface LearningPlanAiConfirmResponse {
  plan: LearningPlan
  tasks: LearningTask[]
  totalPlannedMinutes: number
}

export interface WeeklyReviewAiMetrics {
  taskCount: number
  doneCount: number
  todoCount: number
  inProgressCount: number
  skippedCount: number
  completionRate: number
  plannedMinutes: number
  actualMinutes: number
  studyRecordCount: number
  statusCounts?: Partial<Record<LearningTaskStatus, number>>
  tasks?: LearningAiTaskMetric[]
}

export interface LearningAiTaskMetric {
  taskId: number
  title: string
  status: LearningTaskStatus
  plannedMinutes: number
  actualMinutes: number
  studyRecordCount: number
}

export interface WeeklyReviewAiItem {
  text: string
  evidenceKeys: string[]
}

export interface WeeklyReviewAiSuggestionResponse {
  planId?: number
  metrics: WeeklyReviewAiMetrics
  summary: string
  achievements: string
  problems: string
  nextSteps: string
  achievementItems: WeeklyReviewAiItem[]
  problemItems: WeeklyReviewAiItem[]
  nextStepItems: WeeklyReviewAiItem[]
  evidence: LearningAiEvidence[]
  warnings: string[]
  hasExistingReview?: boolean
}

export interface LearningTask {
  id: number
  title: string
  description?: string | null
  status: LearningTaskStatus
  plannedMinutes?: number | null
  dueDate?: string | null
  sortOrder?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface LearningTaskRequest {
  title: string
  description?: string
  status: LearningTaskStatus
  plannedMinutes?: number
  dueDate?: string
  sortOrder?: number
}

export interface StudyRecord {
  id: number
  studiedAt: string
  durationMinutes: number
  content?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface StudyRecordRequest {
  studiedAt: string
  durationMinutes: number
  content?: string
}

export interface WeeklyReview {
  id: number
  planId: number
  summary?: string | null
  achievements?: string | null
  problems?: string | null
  nextSteps?: string | null
  reviewedAt: string
  createdAt?: string
  updatedAt?: string
}

export interface WeeklyReviewRequest {
  summary?: string
  achievements?: string
  problems?: string
  nextSteps?: string
  reviewedAt: string
}

export interface LearningNote {
  id: number
  planId: number
  taskId?: number | null
  title: string
  content: string
  createdAt?: string
  updatedAt?: string
}

export interface LearningNoteRequest {
  taskId?: number
  title: string
  content: string
}

export interface LearningMaterial {
  id: number
  planId: number
  taskId?: number | null
  title: string
  sourceUrl?: string | null
  description?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface LearningMaterialRequest {
  taskId?: number
  title: string
  sourceUrl?: string
  description?: string
}
