export type ResumeVersionStatus = 'DRAFT' | 'FINALIZED'

export type ResumeSectionType = 'PROFILE' | 'EDUCATION' | 'SKILL' | 'PROJECT' | 'INTERNSHIP' | 'CERTIFICATE'

export type ResumeSourceType = ResumeSectionType

export interface Resume {
  id: number
  name: string
  description?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ResumeRequest {
  name: string
  description?: string
}

export interface ResumeFileMetadata {
  versionId: number
  originalFilename: string
  contentType: string
  fileSize: number
  createdAt?: string
  updatedAt?: string
}

export interface ResumeUploadRequest {
  name: string
  description?: string
  versionLabel?: string
}

export interface ResumeUploadResponse {
  resume: Resume
  version: ResumeVersion
  file: ResumeFileMetadata
}

export interface ResumeVersionUploadResponse {
  version: ResumeVersion
  file: ResumeFileMetadata
}

export interface ResumeVersion {
  id: number
  resumeId: number
  versionNo: number
  label?: string | null
  status: ResumeVersionStatus
  finalizedAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ResumeVersionRequest {
  label?: string
}

export interface ResumeContentItem {
  id: number
  versionId: number
  sectionType: ResumeSectionType
  title?: string | null
  content: string
  sourceType?: ResumeSourceType | null
  sourceId?: number | null
  sortOrder: number
  createdAt?: string
  updatedAt?: string
}

export interface ResumeContentItemRequest {
  sectionType: ResumeSectionType
  title?: string
  content: string
  sortOrder: number
}
