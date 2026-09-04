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
