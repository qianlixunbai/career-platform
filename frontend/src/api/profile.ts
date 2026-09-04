import client from '@/api/client'
import type {
  CertificateAward,
  CertificateAwardRequest,
  EducationExperience,
  EducationExperienceRequest,
  InternshipExperience,
  InternshipExperienceRequest,
  ProfileUpdateRequest,
  ProjectExperience,
  ProjectExperienceRequest,
  Skill,
  UserProfile,
  UserSkill,
  UserSkillCreateRequest,
  UserSkillUpdateRequest,
} from '@/types/profile'

type ClientResponse<T> = T | { data: T }

/** Supports both a normal Axios response and a client interceptor returning response.data. */
function unwrap<T>(response: ClientResponse<T>): T {
  if (typeof response === 'object' && response !== null && 'data' in response) {
    return response.data
  }
  return response as T
}

function body<T>(response: Promise<ClientResponse<T>>): Promise<T> {
  return response.then(unwrap)
}

export function getProfile(): Promise<UserProfile> {
  return body(client.get<UserProfile>('/v1/profile'))
}

export function updateProfile(payload: ProfileUpdateRequest): Promise<UserProfile> {
  return body(client.put<UserProfile>('/v1/profile', payload))
}

export function listEducationExperiences(): Promise<EducationExperience[]> {
  return body(client.get<EducationExperience[]>('/v1/education-experiences'))
}

export function getEducationExperience(id: number): Promise<EducationExperience> {
  return body(client.get<EducationExperience>(`/v1/education-experiences/${id}`))
}

export function createEducationExperience(payload: EducationExperienceRequest): Promise<EducationExperience> {
  return body(client.post<EducationExperience>('/v1/education-experiences', payload))
}

export function updateEducationExperience(id: number, payload: EducationExperienceRequest): Promise<EducationExperience> {
  return body(client.put<EducationExperience>(`/v1/education-experiences/${id}`, payload))
}

export function deleteEducationExperience(id: number): Promise<void> {
  return body(client.delete<void>(`/v1/education-experiences/${id}`))
}

export function listSkills(): Promise<Skill[]> {
  return body(client.get<Skill[]>('/v1/skills'))
}

export function createSkill(name: string): Promise<Skill> {
  return body(client.post<Skill>('/v1/skills', { name }))
}

export function getSkill(id: number): Promise<Skill> {
  return body(client.get<Skill>(`/v1/skills/${id}`))
}

export function listUserSkills(): Promise<UserSkill[]> {
  return body(client.get<UserSkill[]>('/v1/user-skills'))
}

export function createUserSkill(payload: UserSkillCreateRequest): Promise<UserSkill> {
  return body(client.post<UserSkill>('/v1/user-skills', payload))
}

export function getUserSkill(id: number): Promise<UserSkill> {
  return body(client.get<UserSkill>(`/v1/user-skills/${id}`))
}

export function updateUserSkill(id: number, payload: UserSkillUpdateRequest): Promise<UserSkill> {
  return body(client.put<UserSkill>(`/v1/user-skills/${id}`, payload))
}

export function deleteUserSkill(id: number): Promise<void> {
  return body(client.delete<void>(`/v1/user-skills/${id}`))
}

export function listProjectExperiences(): Promise<ProjectExperience[]> {
  return body(client.get<ProjectExperience[]>('/v1/project-experiences'))
}

export function createProjectExperience(payload: ProjectExperienceRequest): Promise<ProjectExperience> {
  return body(client.post<ProjectExperience>('/v1/project-experiences', payload))
}

export function getProjectExperience(id: number): Promise<ProjectExperience> {
  return body(client.get<ProjectExperience>(`/v1/project-experiences/${id}`))
}

export function updateProjectExperience(id: number, payload: ProjectExperienceRequest): Promise<ProjectExperience> {
  return body(client.put<ProjectExperience>(`/v1/project-experiences/${id}`, payload))
}

export function deleteProjectExperience(id: number): Promise<void> {
  return body(client.delete<void>(`/v1/project-experiences/${id}`))
}

export function listInternshipExperiences(): Promise<InternshipExperience[]> {
  return body(client.get<InternshipExperience[]>('/v1/internship-experiences'))
}

export function createInternshipExperience(payload: InternshipExperienceRequest): Promise<InternshipExperience> {
  return body(client.post<InternshipExperience>('/v1/internship-experiences', payload))
}

export function getInternshipExperience(id: number): Promise<InternshipExperience> {
  return body(client.get<InternshipExperience>(`/v1/internship-experiences/${id}`))
}

export function updateInternshipExperience(id: number, payload: InternshipExperienceRequest): Promise<InternshipExperience> {
  return body(client.put<InternshipExperience>(`/v1/internship-experiences/${id}`, payload))
}

export function deleteInternshipExperience(id: number): Promise<void> {
  return body(client.delete<void>(`/v1/internship-experiences/${id}`))
}

export function listCertificateAwards(): Promise<CertificateAward[]> {
  return body(client.get<CertificateAward[]>('/v1/certificate-awards'))
}

export function createCertificateAward(payload: CertificateAwardRequest): Promise<CertificateAward> {
  return body(client.post<CertificateAward>('/v1/certificate-awards', payload))
}

export function getCertificateAward(id: number): Promise<CertificateAward> {
  return body(client.get<CertificateAward>(`/v1/certificate-awards/${id}`))
}

export function updateCertificateAward(id: number, payload: CertificateAwardRequest): Promise<CertificateAward> {
  return body(client.put<CertificateAward>(`/v1/certificate-awards/${id}`, payload))
}

export function deleteCertificateAward(id: number): Promise<void> {
  return body(client.delete<void>(`/v1/certificate-awards/${id}`))
}
