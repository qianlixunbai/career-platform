import client from '@/api/client'
import type {
  Application,
  ApplicationStage,
  ApplicationStageHistory,
  ApplicationTransitionRequest,
  Assessment,
  AssessmentRequest,
  CreateApplicationRequest,
  FinalReview,
  FinalReviewRequest,
  Interview,
  InterviewRequest,
  Offer,
  OfferCreateRequest,
  OfferUpdateRequest,
} from '@/types/application'

export interface ListApplicationsParams {
  currentStage?: ApplicationStage
  jobId?: number
}

export async function listApplications(params?: ListApplicationsParams): Promise<Application[]> {
  const response = await client.get<Application[]>('/v1/applications', { params })
  return response.data
}

export async function createApplication(payload: CreateApplicationRequest): Promise<Application> {
  const response = await client.post<Application>('/v1/applications', payload)
  return response.data
}

export async function getApplication(applicationId: number): Promise<Application> {
  const response = await client.get<Application>(`/v1/applications/${applicationId}`)
  return response.data
}

export async function transitionApplication(
  applicationId: number,
  payload: ApplicationTransitionRequest,
): Promise<Application> {
  const response = await client.post<Application>(`/v1/applications/${applicationId}/transitions`, payload)
  return response.data
}

export async function listApplicationHistory(applicationId: number): Promise<ApplicationStageHistory[]> {
  const response = await client.get<ApplicationStageHistory[]>(`/v1/applications/${applicationId}/history`)
  return response.data
}

export async function listAssessments(applicationId: number): Promise<Assessment[]> {
  const response = await client.get<Assessment[]>(`/v1/applications/${applicationId}/assessments`)
  return response.data
}

export async function getAssessment(applicationId: number, assessmentId: number): Promise<Assessment> {
  const response = await client.get<Assessment>(`/v1/applications/${applicationId}/assessments/${assessmentId}`)
  return response.data
}

export async function createAssessment(applicationId: number, payload: AssessmentRequest): Promise<Assessment> {
  const response = await client.post<Assessment>(`/v1/applications/${applicationId}/assessments`, payload)
  return response.data
}

export async function updateAssessment(
  applicationId: number,
  assessmentId: number,
  payload: AssessmentRequest,
): Promise<Assessment> {
  const response = await client.put<Assessment>(
    `/v1/applications/${applicationId}/assessments/${assessmentId}`,
    payload,
  )
  return response.data
}

export async function deleteAssessment(applicationId: number, assessmentId: number): Promise<void> {
  await client.delete(`/v1/applications/${applicationId}/assessments/${assessmentId}`)
}

export async function listInterviews(applicationId: number): Promise<Interview[]> {
  const response = await client.get<Interview[]>(`/v1/applications/${applicationId}/interviews`)
  return response.data
}

export async function getInterview(applicationId: number, interviewId: number): Promise<Interview> {
  const response = await client.get<Interview>(`/v1/applications/${applicationId}/interviews/${interviewId}`)
  return response.data
}

export async function createInterview(applicationId: number, payload: InterviewRequest): Promise<Interview> {
  const response = await client.post<Interview>(`/v1/applications/${applicationId}/interviews`, payload)
  return response.data
}

export async function updateInterview(
  applicationId: number,
  interviewId: number,
  payload: InterviewRequest,
): Promise<Interview> {
  const response = await client.put<Interview>(
    `/v1/applications/${applicationId}/interviews/${interviewId}`,
    payload,
  )
  return response.data
}

export async function deleteInterview(applicationId: number, interviewId: number): Promise<void> {
  await client.delete(`/v1/applications/${applicationId}/interviews/${interviewId}`)
}

/**
 * An offer is optional until one is created. The request-level silent status
 * keeps the normal response interceptor from showing an error for that 404.
 */
export async function getOffer(applicationId: number): Promise<Offer> {
  const response = await client.get<Offer>(`/v1/applications/${applicationId}/offer`, {
    silentStatuses: [404],
  })
  return response.data
}

export async function createOffer(applicationId: number, payload: OfferCreateRequest): Promise<Offer> {
  const response = await client.post<Offer>(`/v1/applications/${applicationId}/offer`, payload)
  return response.data
}

export async function updateOffer(applicationId: number, payload: OfferUpdateRequest): Promise<Offer> {
  const response = await client.put<Offer>(`/v1/applications/${applicationId}/offer`, payload)
  return response.data
}

/**
 * A final review is optional even after an application ends. A missing review
 * is therefore an expected 404 while loading the detail page.
 */
export async function getFinalReview(applicationId: number): Promise<FinalReview> {
  const response = await client.get<FinalReview>(`/v1/applications/${applicationId}/final-review`, {
    silentStatuses: [404],
  })
  return response.data
}

export async function upsertFinalReview(applicationId: number, payload: FinalReviewRequest): Promise<FinalReview> {
  const response = await client.put<FinalReview>(`/v1/applications/${applicationId}/final-review`, payload)
  return response.data
}
