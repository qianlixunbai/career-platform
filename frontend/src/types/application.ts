export type ApplicationStage = 'APPLIED' | 'ASSESSMENT' | 'INTERVIEW' | 'OFFER' | 'ENDED'

export type ApplicationEndReason =
  | 'COMPANY_REJECTED'
  | 'NO_RESPONSE'
  | 'NO_LONGER_INTERESTED'
  | 'ACCEPTED_ANOTHER_OFFER'
  | 'LOCATION_ISSUE'
  | 'COMPENSATION'
  | 'PERSONAL_REASON'
  | 'OTHER'
  | 'OFFER_ACCEPTED'
  | 'OFFER_REJECTED'

export type OfferStatus = 'CONSIDERING' | 'ACCEPTED' | 'REJECTED'

export interface Application {
  id: number
  jobId: number
  resumeVersionId: number
  currentStage: ApplicationStage
  endReason?: ApplicationEndReason | null
  endNote?: string | null
  jobTitleSnapshot: string
  companyNameSnapshot: string
  locationSnapshot?: string | null
  jobDescriptionSnapshot?: string | null
  appliedAt: string
  endedAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface CreateApplicationRequest {
  jobId: number
  resumeVersionId: number
}

export interface ApplicationTransitionRequest {
  targetStage: ApplicationStage
  endReason?: ApplicationEndReason
  note?: string
}

export interface ApplicationStageHistory {
  id: number
  applicationId: number
  fromStage?: ApplicationStage | null
  toStage: ApplicationStage
  endReason?: ApplicationEndReason | null
  note?: string | null
  changedAt: string
  createdAt?: string
}

export type AssessmentType = 'ONLINE_ASSESSMENT' | 'WRITTEN_TEST' | 'CODING_TEST' | 'OTHER'
export type AssessmentResult = 'PENDING' | 'PASSED' | 'FAILED' | 'OTHER'

export interface Assessment {
  id: number
  applicationId: number
  type: AssessmentType
  title: string
  scheduledAt?: string | null
  occurredAt?: string | null
  result?: AssessmentResult | null
  notes?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface AssessmentRequest {
  type: AssessmentType
  title: string
  scheduledAt?: string
  occurredAt?: string
  result?: AssessmentResult
  notes?: string
}

export type InterviewType = 'HR' | 'TECHNICAL' | 'MANAGER' | 'FINAL' | 'OTHER'
export type InterviewResult = 'PENDING' | 'PASSED' | 'REJECTED' | 'OTHER'

export interface Interview {
  id: number
  applicationId: number
  roundNo: number
  type: InterviewType
  title: string
  scheduledAt?: string | null
  occurredAt?: string | null
  result?: InterviewResult | null
  notes?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface InterviewRequest {
  roundNo: number
  type: InterviewType
  title: string
  scheduledAt?: string
  occurredAt?: string
  result?: InterviewResult
  notes?: string
}

export interface Offer {
  id: number
  applicationId: number
  status: OfferStatus
  positionTitle?: string | null
  compensation?: string | null
  expiresAt?: string | null
  notes?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface OfferCreateRequest {
  positionTitle?: string
  compensation?: string
  expiresAt?: string
  notes?: string
}

export interface OfferUpdateRequest {
  status: OfferStatus
  positionTitle?: string
  compensation?: string
  expiresAt?: string
  notes?: string
}

export interface FinalReview {
  id: number
  applicationId: number
  summary: string
  lessonsLearned?: string | null
  improvements?: string | null
  rating?: number | null
  reviewedAt: string
  createdAt?: string
  updatedAt?: string
}

export interface FinalReviewRequest {
  summary: string
  lessonsLearned?: string
  improvements?: string
  rating?: number
  reviewedAt: string
}
