/**
 * DTOs exposed by the shared profile endpoints.
 *
 * The API deliberately does not expose an owner/userId field to the client:
 * ownership is inferred from the authenticated JWT on the server.
 */

export interface UserProfile {
  id?: number
  fullName: string | null
  phone: string | null
  avatarUrl: string | null
  currentCity: string | null
  personalWebsite: string | null
  githubUrl: string | null
}

export interface ProfileUpdateRequest {
  fullName?: string
  phone?: string
  avatarUrl?: string
  currentCity?: string
  personalWebsite?: string
  githubUrl?: string
}

export interface EducationExperience {
  id: number
  schoolName: string
  major: string
  degree: string
  startDate: string
  endDate: string | null
  description: string | null
}

export interface EducationExperienceRequest {
  schoolName: string
  major: string
  degree: string
  startDate: string
  endDate?: string
  description?: string
}

export interface Skill {
  id: number
  name: string
}

export type Proficiency = 'BEGINNER' | 'FAMILIAR' | 'PROFICIENT'

export interface UserSkill {
  id: number
  skillId: number
  skillName: string
  proficiency: Proficiency
}

export interface UserSkillCreateRequest {
  skillId: number
  proficiency: Proficiency
}

export interface UserSkillUpdateRequest {
  proficiency: Proficiency
}

export interface ProjectExperience {
  id: number
  projectName: string
  role: string
  startDate: string
  endDate: string | null
  description: string | null
  techStack: string
  projectUrl: string | null
}

export interface ProjectExperienceRequest {
  projectName: string
  role: string
  startDate: string
  endDate?: string
  description?: string
  techStack: string
  projectUrl?: string
}

export interface InternshipExperience {
  id: number
  companyName: string
  position: string
  startDate: string
  endDate: string | null
  description: string | null
}

export interface InternshipExperienceRequest {
  companyName: string
  position: string
  startDate: string
  endDate?: string
  description?: string
}

export type CertificateAwardType = 'CERTIFICATE' | 'AWARD'

export interface CertificateAward {
  id: number
  name: string
  type: CertificateAwardType
  issuer: string
  issueDate: string
  description: string | null
}

export interface CertificateAwardRequest {
  name: string
  type: CertificateAwardType
  issuer: string
  issueDate: string
  description?: string
}
