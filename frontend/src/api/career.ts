import client from '@/api/client'
import type {
  CareerGoal,
  CareerGoalRequest,
  Company,
  CompanyRequest,
  Job,
  JobNote,
  JobNoteRequest,
  JobRequirement,
  JobRequirementRequest,
  JobRequest,
} from '@/types/career'

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

export function listCareerGoals(): Promise<CareerGoal[]> {
  return body(client.get<CareerGoal[]>('/v1/career-goals'))
}

export function createCareerGoal(payload: CareerGoalRequest): Promise<CareerGoal> {
  return body(client.post<CareerGoal>('/v1/career-goals', payload))
}

export function getCareerGoal(id: number): Promise<CareerGoal> {
  return body(client.get<CareerGoal>(`/v1/career-goals/${id}`))
}

export function updateCareerGoal(id: number, payload: CareerGoalRequest): Promise<CareerGoal> {
  return body(client.put<CareerGoal>(`/v1/career-goals/${id}`, payload))
}

export function deleteCareerGoal(id: number): Promise<void> {
  return body(client.delete<void>(`/v1/career-goals/${id}`))
}

export function listCompanies(): Promise<Company[]> {
  return body(client.get<Company[]>('/v1/companies'))
}

export function createCompany(payload: CompanyRequest): Promise<Company> {
  return body(client.post<Company>('/v1/companies', payload))
}

export function getCompany(id: number): Promise<Company> {
  return body(client.get<Company>(`/v1/companies/${id}`))
}

export function updateCompany(id: number, payload: CompanyRequest): Promise<Company> {
  return body(client.put<Company>(`/v1/companies/${id}`, payload))
}

export function deleteCompany(id: number): Promise<void> {
  return body(client.delete<void>(`/v1/companies/${id}`))
}

export function listJobs(archived = false): Promise<Job[]> {
  return body(client.get<Job[]>('/v1/jobs', { params: { archived } }))
}

export function getJob(id: number): Promise<Job> {
  return body(client.get<Job>(`/v1/jobs/${id}`))
}

export function createJob(payload: JobRequest): Promise<Job> {
  return body(client.post<Job>('/v1/jobs', payload))
}

export function updateJob(id: number, payload: JobRequest): Promise<Job> {
  return body(client.put<Job>(`/v1/jobs/${id}`, payload))
}

export function archiveJob(id: number): Promise<Job> {
  return body(client.patch<Job>(`/v1/jobs/${id}/archive`))
}

export function unarchiveJob(id: number): Promise<Job> {
  return body(client.patch<Job>(`/v1/jobs/${id}/unarchive`))
}

export function listJobRequirements(jobId: number): Promise<JobRequirement[]> {
  return body(client.get<JobRequirement[]>(`/v1/jobs/${jobId}/requirements`))
}

export function createJobRequirement(jobId: number, payload: JobRequirementRequest): Promise<JobRequirement> {
  return body(client.post<JobRequirement>(`/v1/jobs/${jobId}/requirements`, payload))
}

export function getJobRequirement(jobId: number, id: number): Promise<JobRequirement> {
  return body(client.get<JobRequirement>(`/v1/jobs/${jobId}/requirements/${id}`))
}

export function updateJobRequirement(jobId: number, id: number, payload: JobRequirementRequest): Promise<JobRequirement> {
  return body(client.put<JobRequirement>(`/v1/jobs/${jobId}/requirements/${id}`, payload))
}

export function deleteJobRequirement(jobId: number, id: number): Promise<void> {
  return body(client.delete<void>(`/v1/jobs/${jobId}/requirements/${id}`))
}

export function listJobNotes(jobId: number): Promise<JobNote[]> {
  return body(client.get<JobNote[]>(`/v1/jobs/${jobId}/notes`))
}

export function createJobNote(jobId: number, payload: JobNoteRequest): Promise<JobNote> {
  return body(client.post<JobNote>(`/v1/jobs/${jobId}/notes`, payload))
}

export function getJobNote(jobId: number, id: number): Promise<JobNote> {
  return body(client.get<JobNote>(`/v1/jobs/${jobId}/notes/${id}`))
}

export function updateJobNote(jobId: number, id: number, payload: JobNoteRequest): Promise<JobNote> {
  return body(client.put<JobNote>(`/v1/jobs/${jobId}/notes/${id}`, payload))
}

export function deleteJobNote(jobId: number, id: number): Promise<void> {
  return body(client.delete<void>(`/v1/jobs/${jobId}/notes/${id}`))
}
