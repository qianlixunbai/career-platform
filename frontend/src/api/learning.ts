import client from '@/api/client'
import type {
  LearningMaterial,
  LearningMaterialRequest,
  LearningNote,
  LearningNoteRequest,
  LearningPlan,
  LearningPlanAiConfirmRequest,
  LearningPlanAiConfirmResponse,
  LearningPlanAiSuggestionRequest,
  LearningPlanAiSuggestionResponse,
  LearningPlanRequest,
  LearningPlanRagQueryResponse,
  LearningTask,
  LearningTaskRequest,
  RagCitation,
  RagStatus,
  StudyRecord,
  StudyRecordRequest,
  WeeklyReview,
  WeeklyReviewAiSuggestionResponse,
  WeeklyReviewRequest,
} from '@/types/learning'

export async function listLearningPlans(): Promise<LearningPlan[]> {
  const response = await client.get<LearningPlan[]>('/v1/learning-plans')
  return response.data
}

export async function getLearningPlan(planId: number): Promise<LearningPlan> {
  const response = await client.get<LearningPlan>(`/v1/learning-plans/${planId}`)
  return response.data
}

export async function createLearningPlan(payload: LearningPlanRequest): Promise<LearningPlan> {
  const response = await client.post<LearningPlan>('/v1/learning-plans', payload)
  return response.data
}

export async function updateLearningPlan(planId: number, payload: LearningPlanRequest): Promise<LearningPlan> {
  const response = await client.put<LearningPlan>(`/v1/learning-plans/${planId}`, payload)
  return response.data
}

export async function deleteLearningPlan(planId: number): Promise<void> {
  await client.delete(`/v1/learning-plans/${planId}`)
}

/**
 * Generate an ephemeral weekly-plan candidate. The backend only reads owned
 * context for this request; no plan or task is persisted until confirmation.
 */
export async function suggestLearningPlanWithAi(
  payload: LearningPlanAiSuggestionRequest,
): Promise<LearningPlanAiSuggestionResponse> {
  const response = await client.post<LearningPlanAiSuggestionResponse>(
    '/v1/learning-plans/ai/plan-suggestion',
    payload,
  )
  return response.data
}

/**
 * Persist a user-reviewed AI candidate atomically as one plan and its tasks.
 * This endpoint must not invoke the provider again.
 */
export async function confirmLearningPlanAiSuggestion(
  payload: LearningPlanAiConfirmRequest,
): Promise<LearningPlanAiConfirmResponse> {
  const response = await client.post<LearningPlanAiConfirmResponse>(
    '/v1/learning-plans/ai/plan-suggestion/confirm',
    payload,
  )
  return response.data
}

export async function listLearningTasks(planId: number): Promise<LearningTask[]> {
  const response = await client.get<LearningTask[]>(`/v1/learning-plans/${planId}/tasks`)
  return response.data
}

export async function getLearningTask(planId: number, taskId: number): Promise<LearningTask> {
  const response = await client.get<LearningTask>(`/v1/learning-plans/${planId}/tasks/${taskId}`)
  return response.data
}

export async function createLearningTask(planId: number, payload: LearningTaskRequest): Promise<LearningTask> {
  const response = await client.post<LearningTask>(`/v1/learning-plans/${planId}/tasks`, payload)
  return response.data
}

export async function updateLearningTask(
  planId: number,
  taskId: number,
  payload: LearningTaskRequest,
): Promise<LearningTask> {
  const response = await client.put<LearningTask>(`/v1/learning-plans/${planId}/tasks/${taskId}`, payload)
  return response.data
}

export async function deleteLearningTask(planId: number, taskId: number): Promise<void> {
  await client.delete(`/v1/learning-plans/${planId}/tasks/${taskId}`)
}

export async function listStudyRecords(taskId: number): Promise<StudyRecord[]> {
  const response = await client.get<StudyRecord[]>(`/v1/learning-tasks/${taskId}/records`)
  return response.data
}

export async function getStudyRecord(taskId: number, recordId: number): Promise<StudyRecord> {
  const response = await client.get<StudyRecord>(`/v1/learning-tasks/${taskId}/records/${recordId}`)
  return response.data
}

export async function createStudyRecord(taskId: number, payload: StudyRecordRequest): Promise<StudyRecord> {
  const response = await client.post<StudyRecord>(`/v1/learning-tasks/${taskId}/records`, payload)
  return response.data
}

export async function updateStudyRecord(
  taskId: number,
  recordId: number,
  payload: StudyRecordRequest,
): Promise<StudyRecord> {
  const response = await client.put<StudyRecord>(`/v1/learning-tasks/${taskId}/records/${recordId}`, payload)
  return response.data
}

export async function deleteStudyRecord(taskId: number, recordId: number): Promise<void> {
  await client.delete(`/v1/learning-tasks/${taskId}/records/${recordId}`)
}

export async function getWeeklyReview(planId: number): Promise<WeeklyReview> {
  const response = await client.get<WeeklyReview>(`/v1/learning-plans/${planId}/review`)
  return response.data
}

export async function updateWeeklyReview(planId: number, payload: WeeklyReviewRequest): Promise<WeeklyReview> {
  const response = await client.put<WeeklyReview>(`/v1/learning-plans/${planId}/review`, payload)
  return response.data
}

/**
 * Generate an ephemeral review candidate. Applying it in the UI only copies
 * text into the existing form; the normal PUT endpoint remains the save path.
 */
export async function suggestWeeklyReviewWithAi(planId: number): Promise<WeeklyReviewAiSuggestionResponse> {
  const response = await client.post<WeeklyReviewAiSuggestionResponse>(
    `/v1/learning-plans/${planId}/ai/review-suggestion`,
  )
  return response.data
}

export async function listLearningNotes(planId: number): Promise<LearningNote[]> {
  const response = await client.get<LearningNote[]>(`/v1/learning-plans/${planId}/notes`)
  return response.data
}

export async function getLearningNote(planId: number, noteId: number): Promise<LearningNote> {
  const response = await client.get<LearningNote>(`/v1/learning-plans/${planId}/notes/${noteId}`)
  return response.data
}

export async function createLearningNote(planId: number, payload: LearningNoteRequest): Promise<LearningNote> {
  const response = await client.post<LearningNote>(`/v1/learning-plans/${planId}/notes`, payload)
  return response.data
}

export async function updateLearningNote(
  planId: number,
  noteId: number,
  payload: LearningNoteRequest,
): Promise<LearningNote> {
  const response = await client.put<LearningNote>(`/v1/learning-plans/${planId}/notes/${noteId}`, payload)
  return response.data
}

export async function deleteLearningNote(planId: number, noteId: number): Promise<void> {
  await client.delete(`/v1/learning-plans/${planId}/notes/${noteId}`)
}

export async function listLearningMaterials(planId: number): Promise<LearningMaterial[]> {
  const response = await client.get<LearningMaterial[]>(`/v1/learning-plans/${planId}/materials`)
  return response.data
}

export async function getLearningMaterial(planId: number, materialId: number): Promise<LearningMaterial> {
  const response = await client.get<LearningMaterial>(`/v1/learning-plans/${planId}/materials/${materialId}`)
  return response.data
}

export async function createLearningMaterial(
  planId: number,
  payload: LearningMaterialRequest,
): Promise<LearningMaterial> {
  const response = await client.post<LearningMaterial>(`/v1/learning-plans/${planId}/materials`, payload)
  return response.data
}

export async function updateLearningMaterial(
  planId: number,
  materialId: number,
  payload: LearningMaterialRequest,
): Promise<LearningMaterial> {
  const response = await client.put<LearningMaterial>(
    `/v1/learning-plans/${planId}/materials/${materialId}`,
    payload,
  )
  return response.data
}

export async function deleteLearningMaterial(planId: number, materialId: number): Promise<void> {
  await client.delete(`/v1/learning-plans/${planId}/materials/${materialId}`)
}

/** Upload and synchronously index a PDF/DOCX for the current learning plan. */
export async function uploadLearningMaterial(
  planId: number,
  file: File,
  taskId?: number,
): Promise<LearningMaterial> {
  const formData = new FormData()
  formData.append('file', file)
  if (taskId) formData.append('taskId', String(taskId))
  const response = await client.post<LearningMaterial>(
    `/v1/learning-plans/${planId}/materials/upload`,
    formData,
    {
      timeout: 120_000,
      headers: { 'Content-Type': 'multipart/form-data' },
    },
  )
  return response.data
}

/** Re-index an already uploaded material without changing its metadata. */
export async function reindexLearningMaterial(planId: number, materialId: number): Promise<LearningMaterial> {
  const response = await client.post<LearningMaterial>(
    `/v1/learning-plans/${planId}/materials/${materialId}/reindex`,
    undefined,
    { timeout: 120_000 },
  )
  return response.data
}

/** Fetch the owner-checked original file through the authenticated API client. */
export async function getLearningMaterialFile(planId: number, materialId: number): Promise<Blob> {
  const response = await client.get<Blob>(
    `/v1/learning-plans/${planId}/materials/${materialId}/file`,
    { responseType: 'blob' },
  )
  return response.data
}

export async function getLearningMaterialChunk(
  planId: number,
  materialId: number,
  chunkId: number,
): Promise<RagCitation> {
  const response = await client.get<RagCitation>(
    `/v1/learning-plans/${planId}/materials/${materialId}/chunks/${chunkId}`,
  )
  return response.data
}

export async function getRagStatus(planId: number): Promise<RagStatus> {
  const response = await client.get<RagStatus>(`/v1/learning-plans/${planId}/materials/rag/status`)
  return response.data
}

export async function queryLearningPlanRag(
  planId: number,
  question: string,
): Promise<LearningPlanRagQueryResponse> {
  const response = await client.post<LearningPlanRagQueryResponse>(
    `/v1/learning-plans/${planId}/materials/rag/query`,
    { question },
    { timeout: 120_000 },
  )
  return response.data
}
