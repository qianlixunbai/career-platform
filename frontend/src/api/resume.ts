import client from '@/api/client'
import type {
  Resume,
  ResumeContentItem,
  ResumeContentItemRequest,
  ResumeRequest,
  ResumeVersion,
  ResumeVersionRequest,
} from '@/types/resume'

export async function listResumes(): Promise<Resume[]> {
  const response = await client.get<Resume[]>('/v1/resumes')
  return response.data
}

export async function getResume(resumeId: number): Promise<Resume> {
  const response = await client.get<Resume>(`/v1/resumes/${resumeId}`)
  return response.data
}

export async function createResume(payload: ResumeRequest): Promise<Resume> {
  const response = await client.post<Resume>('/v1/resumes', payload)
  return response.data
}

export async function updateResume(resumeId: number, payload: ResumeRequest): Promise<Resume> {
  const response = await client.put<Resume>(`/v1/resumes/${resumeId}`, payload)
  return response.data
}

export async function deleteResume(resumeId: number): Promise<void> {
  await client.delete(`/v1/resumes/${resumeId}`)
}

export async function listResumeVersions(resumeId: number): Promise<ResumeVersion[]> {
  const response = await client.get<ResumeVersion[]>(`/v1/resumes/${resumeId}/versions`)
  return response.data
}

export async function getResumeVersion(resumeId: number, versionId: number): Promise<ResumeVersion> {
  const response = await client.get<ResumeVersion>(`/v1/resumes/${resumeId}/versions/${versionId}`)
  return response.data
}

export async function createResumeVersion(resumeId: number, payload: ResumeVersionRequest = {}): Promise<ResumeVersion> {
  const response = await client.post<ResumeVersion>(`/v1/resumes/${resumeId}/versions`, payload)
  return response.data
}

export async function generateResumeVersion(
  resumeId: number,
  payload: ResumeVersionRequest = {},
): Promise<ResumeVersion> {
  const response = await client.post<ResumeVersion>(`/v1/resumes/${resumeId}/versions/generate`, payload)
  return response.data
}

export async function updateResumeVersion(
  resumeId: number,
  versionId: number,
  payload: ResumeVersionRequest,
): Promise<ResumeVersion> {
  const response = await client.put<ResumeVersion>(`/v1/resumes/${resumeId}/versions/${versionId}`, payload)
  return response.data
}

export async function copyResumeVersion(
  resumeId: number,
  versionId: number,
  payload: ResumeVersionRequest = {},
): Promise<ResumeVersion> {
  const response = await client.post<ResumeVersion>(
    `/v1/resumes/${resumeId}/versions/${versionId}/copy`,
    payload,
  )
  return response.data
}

export async function finalizeResumeVersion(resumeId: number, versionId: number): Promise<ResumeVersion> {
  const response = await client.post<ResumeVersion>(
    `/v1/resumes/${resumeId}/versions/${versionId}/finalize`,
  )
  return response.data
}

export async function deleteResumeVersion(resumeId: number, versionId: number): Promise<void> {
  await client.delete(`/v1/resumes/${resumeId}/versions/${versionId}`)
}

export async function listResumeItems(resumeId: number, versionId: number): Promise<ResumeContentItem[]> {
  const response = await client.get<ResumeContentItem[]>(
    `/v1/resumes/${resumeId}/versions/${versionId}/items`,
  )
  return response.data
}

export async function getResumeItem(
  resumeId: number,
  versionId: number,
  itemId: number,
): Promise<ResumeContentItem> {
  const response = await client.get<ResumeContentItem>(
    `/v1/resumes/${resumeId}/versions/${versionId}/items/${itemId}`,
  )
  return response.data
}

export async function createResumeItem(
  resumeId: number,
  versionId: number,
  payload: ResumeContentItemRequest,
): Promise<ResumeContentItem> {
  const response = await client.post<ResumeContentItem>(
    `/v1/resumes/${resumeId}/versions/${versionId}/items`,
    payload,
  )
  return response.data
}

export async function updateResumeItem(
  resumeId: number,
  versionId: number,
  itemId: number,
  payload: ResumeContentItemRequest,
): Promise<ResumeContentItem> {
  const response = await client.put<ResumeContentItem>(
    `/v1/resumes/${resumeId}/versions/${versionId}/items/${itemId}`,
    payload,
  )
  return response.data
}

export async function deleteResumeItem(resumeId: number, versionId: number, itemId: number): Promise<void> {
  await client.delete(`/v1/resumes/${resumeId}/versions/${versionId}/items/${itemId}`)
}
