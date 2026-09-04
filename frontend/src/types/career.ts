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

export interface JobNote {
  id: number
  content: string
}

export interface JobNoteRequest {
  content: string
}
