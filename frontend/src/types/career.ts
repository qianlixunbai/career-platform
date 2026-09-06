export type CareerGoalStatus = 'ACTIVE' | 'PAUSED' | 'ACHIEVED'

export interface CareerGoal {
  id: number
  targetPosition: string
  targetCity: string | null
  targetIndustry: string | null
  targetCompanyPreference: string | null
  salaryExpectation: string | null
  notes: string | null
  status: CareerGoalStatus
}

export interface CareerGoalRequest {
  targetPosition: string
  targetCity?: string
  targetIndustry?: string
  targetCompanyPreference?: string
  salaryExpectation?: string
  notes?: string
  status: CareerGoalStatus
}

export interface Company {
  id: number
  name: string
  industry: string | null
  city: string | null
  website: string | null
  size: string | null
  notes: string | null
}

export interface CompanyRequest {
  name: string
  industry?: string
  city?: string
  website?: string
  size?: string
  notes?: string
}

export type JobType = 'FULL_TIME' | 'INTERNSHIP' | 'CAMPUS' | 'PART_TIME' | 'CONTRACT' | 'OTHER'
export type SourceType = 'MANUAL' | 'CAMPUS_SITE' | 'COMPANY_WEBSITE' | 'RECRUITMENT_PLATFORM' | 'REFERRAL' | 'OTHER'

export interface Job {
  id: number
  companyId: number
  title: string
  city: string | null
  jobType: JobType
  publishDate: string | null
  deadline: string | null
  rawJd: string | null
  sourceType: SourceType
  sourceName: string | null
  sourceUrl: string | null
  archived: boolean
}

export interface JobRequest {
  companyId: number
  title: string
  city?: string
  jobType: JobType
  publishDate?: string
  deadline?: string
  rawJd?: string
  sourceType: SourceType
  sourceName?: string
  sourceUrl?: string
}

export interface JobDiscoveryRequest {
  careerGoalId: number
  searchNote?: string
  locationOverride?: string
  maxCandidates: number
}

export interface JobDiscoverySourceFacts {
  sourceUrl: string
  sourceTitle: string
  sourceHost: string
  sourceSnippet: string
  publishedAt: string | null
  discoveredBy: string
}

export interface JobDiscoveryExtractedFields {
  jobTitle: string
  companyName: string | null
  location: string | null
  jobTypeSuggestion: string | null
}

export interface JobDiscoveryMatchedSkill {
  key: string
  name: string
}

export interface JobDiscoveryAiAdvice {
  rank: number
  fitSummary: string
  strengths: string[]
  gaps: string[]
  uncertainty: string[]
  matchedSkills: JobDiscoveryMatchedSkill[]
}

export interface JobCandidate {
  candidateId: string
  expiresAt: string
  sourceFacts: JobDiscoverySourceFacts
  extractedFields: JobDiscoveryExtractedFields
  aiAdvice: JobDiscoveryAiAdvice
}

export interface JobDiscoveryResponse {
  candidates: JobCandidate[]
  warnings: string[]
  searchCalls: number
}

export interface JobDiscoveryConfirmRequest {
  candidateId: string
  companyId: number
  title: string
  city?: string
  jobType: JobType
  rawJd?: string
}

export interface JobDiscoveryConfirmResponse {
  jobId: number
  warnings: string[]
}

export type RequirementType = 'SKILL' | 'EDUCATION' | 'MAJOR' | 'EXPERIENCE' | 'LANGUAGE' | 'OTHER'

export interface JobRequirement {
  id: number
  requirementType: RequirementType
  skillId: number | null
  requirementText: string
}

export interface JobRequirementRequest {
  requirementType: RequirementType
  skillId?: number
  requirementText: string
}

export type SkillResolutionStatus = 'RESOLVED' | 'UNRESOLVED' | 'NOT_APPLICABLE'
export type DuplicateStatus = 'NEW' | 'DUPLICATE_EXISTING'

export interface JdRequirementCandidate {
  requirementType: RequirementType
  description: string
  skillName: string | null
  evidenceQuote: string
  matchedSkillId: number | null
  matchedSkillName: string | null
  resolutionStatus: SkillResolutionStatus
  duplicateStatus: DuplicateStatus
  selected: boolean
}

export interface JdParseResponse {
  sourceFingerprint: string
  requirements: JdRequirementCandidate[]
  warnings: string[]
}

export interface JdParseConfirmRequirementRequest {
  selected: boolean
  requirementType: RequirementType
  skillId?: number
  requirementText: string
}

export interface JdParseConfirmRequest {
  sourceFingerprint: string
  requirements: JdParseConfirmRequirementRequest[]
}

export interface JdParseConfirmResponse {
  createdCount: number
  createdRequirements: JobRequirement[]
}

export interface JobNote {
  id: number
  content: string
}

export interface JobNoteRequest {
  content: string
}
