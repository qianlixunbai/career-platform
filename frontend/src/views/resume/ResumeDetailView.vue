<template>
  <div class="page-shell">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <el-button link type="primary" class="back-button" @click="goBack">← 返回简历列表</el-button>
            <h2>{{ resume?.name || '简历版本中心' }}</h2>
            <p>管理草稿、生成快照并维护每个版本的内容。</p>
          </div>
          <el-button v-if="resume && !isFinalized" type="primary" plain @click="saveResume" :loading="savingResume">保存简历信息</el-button>
        </div>
      </template>

      <el-skeleton v-if="loading && !resume" :rows="5" animated />
      <el-empty v-else-if="!resume" description="简历不存在或暂时无法加载" />
      <div v-else>
        <el-form :model="resumeForm" label-position="top" class="resume-form" @submit.prevent="saveResume">
          <div class="form-grid">
            <el-form-item label="简历名称">
              <el-input v-model="resumeForm.name" maxlength="200" :disabled="isFinalized" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="resumeForm.description" maxlength="2000" :disabled="isFinalized" placeholder="目标岗位或版本用途" />
            </el-form-item>
          </div>
          <el-alert
            v-if="isFinalized"
            title="当前选中的版本已定稿 / 历史版本，简历和版本内容均为只读。"
            type="warning"
            :closable="false"
            show-icon
          />
        </el-form>

        <el-divider />

        <section>
          <div class="section-heading">
            <div>
              <h3>版本列表</h3>
              <span class="muted">生成会从当前共享基础档案创建新的 DRAFT 版本，不会覆盖旧版。</span>
            </div>
            <div class="heading-actions">
              <el-button type="primary" @click="openUploadDialog">上传新版本</el-button>
              <el-button @click="openVersionDialog('create')">新建空白 DRAFT</el-button>
              <el-button type="primary" @click="openVersionDialog('generate')">从基础档案生成新的 DRAFT</el-button>
            </div>
          </div>

          <div v-loading="versionsLoading" class="table-wrap">
            <el-empty v-if="!versionsLoading && versions.length === 0" description="还没有版本，请先创建草稿" />
            <el-table
              v-else
              :data="versions"
              row-key="id"
              highlight-current-row
              :current-row-key="selectedVersionId ?? undefined"
              stripe
              @row-click="selectVersion"
            >
              <el-table-column label="版本" width="100">
                <template #default="{ row }">v{{ row.versionNo }}</template>
              </el-table-column>
              <el-table-column prop="label" label="标签" min-width="210" show-overflow-tooltip>
                <template #default="{ row }">{{ row.label || '未命名版本' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="150">
                <template #default="{ row }">
                  <el-tag :type="versionStatusTag(row.status)">{{ versionStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="finalizedAt" label="定稿时间" width="190">
                <template #default="{ row }">{{ formatDate(row.finalizedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click.stop="openVersionDialog('copy', row)">复制 DRAFT</el-button>
                  <el-button v-if="row.status === 'DRAFT'" link type="danger" @click.stop="removeVersion(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <template v-if="selectedVersion">
          <el-divider />
          <section>
            <div class="section-heading section-heading--top">
              <div>
                <h3>v{{ selectedVersion.versionNo }} · {{ selectedVersion.label || '未命名版本' }}</h3>
                <span class="muted">{{ selectedVersion.status === 'FINALIZED' ? '已定稿 / 历史版本' : '草稿可继续编辑' }}</span>
              </div>
              <div class="heading-actions">
                <el-button v-if="!isFinalized" type="success" @click="finalizeVersion">定稿</el-button>
                <el-tag v-else type="success" size="large">已定稿 / 历史版本</el-tag>
              </div>
            </div>

            <el-form :model="versionForm" inline class="version-meta-form" @submit.prevent="saveVersionMetadata">
              <el-form-item label="版本标签">
                <el-input v-model="versionForm.label" :disabled="isFinalized" maxlength="200" placeholder="例如：投递互联网公司" />
              </el-form-item>
              <el-form-item>
                <el-button v-if="!isFinalized" type="primary" :loading="savingVersion" @click="saveVersionMetadata">保存标签</el-button>
              </el-form-item>
            </el-form>

            <el-alert
              v-if="isFinalized"
              title="已定稿 / 历史版本：该版本及内容不可新增、修改或删除。"
              type="info"
              :closable="false"
              show-icon
              class="readonly-alert"
            />

            <section class="resume-file-section">
              <div class="section-heading items-heading">
                <div>
                  <h3>原始简历文件</h3>
                  <span class="muted">可查看当前版本上传的 PDF / DOCX 原文件。</span>
                </div>
              </div>
              <div v-loading="fileMetadataLoading" class="file-panel">
                <div v-if="selectedFileMetadata" class="file-details">
                  <div class="file-details__name" :title="selectedFileMetadata.originalFilename">
                    {{ selectedFileMetadata.originalFilename }}
                  </div>
                  <div class="file-details__meta">
                    {{ formatFileType(selectedFileMetadata) }} · {{ formatFileSize(selectedFileMetadata.fileSize) }}
                  </div>
                  <el-button
                    type="primary"
                    plain
                    :loading="downloadingFile"
                    @click="downloadSelectedFile"
                  >
                    下载
                  </el-button>
                </div>
                <span v-else-if="!fileMetadataLoading && fileMetadataLoadFailed" class="muted">
                  文件信息加载失败，请稍后重新选择该版本重试
                </span>
                <span v-else-if="!fileMetadataLoading" class="muted">当前版本没有上传原始文件</span>
              </div>
            </section>

            <div class="section-heading items-heading">
              <div>
                <h3>内容条目</h3>
                <span class="muted">内容来源字段由后端返回，仅用于展示，不会由前端提交。</span>
              </div>
              <el-button v-if="!isFinalized" type="primary" @click="openItemCreate">新增内容</el-button>
            </div>
            <div v-loading="itemsLoading" class="table-wrap">
              <el-empty v-if="!itemsLoading && items.length === 0" description="当前版本还没有内容条目" />
              <el-table v-else :data="items" stripe>
                <el-table-column label="分区" width="130">
                  <template #default="{ row }">{{ sectionLabel(row.sectionType) }}</template>
                </el-table-column>
                <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.title || '—' }}</template>
                </el-table-column>
                <el-table-column prop="content" label="内容" min-width="320" show-overflow-tooltip />
                <el-table-column label="来源" width="130">
                  <template #default="{ row }">{{ row.sourceType ? `自动 · ${sectionLabel(row.sourceType)}` : '手动' }}</template>
                </el-table-column>
                <el-table-column prop="sortOrder" label="排序" width="80" />
                <el-table-column v-if="!isFinalized" label="操作" width="130" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openItemEdit(row)">编辑</el-button>
                    <el-button link type="danger" @click="removeItem(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </section>
        </template>
      </div>
    </el-card>

    <el-dialog v-model="versionDialogVisible" :title="versionDialogTitle" width="500px" destroy-on-close>
      <el-form ref="versionFormRef" :model="versionForm" label-position="top" @submit.prevent="submitVersionAction">
        <el-form-item label="版本标签">
          <el-input v-model="versionForm.label" maxlength="200" placeholder="可选，例如：校招前端版本" />
        </el-form-item>
      </el-form>
      <el-alert v-if="versionAction === 'generate'" title="将根据当前共享基础档案创建新的 DRAFT 版本，不会覆盖旧版本。" type="info" :closable="false" show-icon />
      <el-alert v-if="versionAction === 'copy'" title="将复制源版本内容为新的 DRAFT，源版本保持不变。" type="info" :closable="false" show-icon />
      <template #footer>
        <el-button @click="versionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingVersion" @click="submitVersionAction">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="uploadDialogVisible"
      title="上传新版本"
      width="min(500px, calc(100vw - 32px))"
      destroy-on-close
      :close-on-click-modal="!uploadingVersion"
      :close-on-press-escape="!uploadingVersion"
      :show-close="!uploadingVersion"
      @closed="resetUploadDialog"
    >
      <el-form label-position="top" @submit.prevent="submitUploadVersion">
        <el-form-item label="原始简历文件" required>
          <el-upload
            v-model:file-list="uploadFileList"
            class="resume-upload"
            accept=".pdf,.docx"
            :auto-upload="false"
            :limit="1"
            :disabled="uploadingVersion"
            :on-exceed="handleUploadExceed"
          >
            <el-button :disabled="uploadingVersion">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">仅支持 PDF / DOCX，文件大小不超过 5 MiB。</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="版本标签">
          <el-input v-model="uploadForm.label" maxlength="200" placeholder="可选，例如：校招前端版本" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="uploadingVersion" @click="closeUploadDialog">取消</el-button>
        <el-button type="primary" :loading="uploadingVersion" @click="submitUploadVersion">上传</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialogVisible" :title="editingItemId === null ? '新增内容条目' : '编辑内容条目'" width="620px" destroy-on-close>
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-position="top" @submit.prevent="saveItem">
        <div class="form-grid">
          <el-form-item label="分区类型" prop="sectionType">
            <el-select v-model="itemForm.sectionType" class="full-width">
              <el-option v-for="option in sectionOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序值" prop="sortOrder">
            <el-input-number v-model="itemForm.sortOrder" :min="0" :step="1" controls-position="right" class="full-width" />
          </el-form-item>
        </div>
        <el-form-item label="标题">
          <el-input v-model="itemForm.title" maxlength="200" placeholder="可选" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="itemForm.content" type="textarea" :rows="9" maxlength="16000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingItem" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadUserFile } from 'element-plus'

import {
  copyResumeVersion,
  createResumeItem,
  createResumeVersion,
  deleteResumeItem,
  deleteResumeVersion,
  finalizeResumeVersion,
  generateResumeVersion,
  getResume,
  getResumeFile,
  listResumeItems,
  listResumeVersions,
  downloadResumeFile,
  updateResume,
  updateResumeItem,
  updateResumeVersion,
  uploadResumeVersion,
} from '@/api/resume'
import type {
  Resume,
  ResumeContentItem,
  ResumeContentItemRequest,
  ResumeFileMetadata,
  ResumeRequest,
  ResumeSectionType,
  ResumeVersion,
  ResumeVersionRequest,
} from '@/types/resume'

const route = useRoute()
const router = useRouter()
const resumeId = computed(() => Number(route.params.resumeId))

const resume = ref<Resume | null>(null)
const resumeForm = reactive<ResumeRequest>({ name: '', description: '' })
const versions = ref<ResumeVersion[]>([])
const selectedVersionId = ref<number | null>(null)
const selectedVersion = ref<ResumeVersion | null>(null)
const items = ref<ResumeContentItem[]>([])
const selectedFileMetadata = ref<ResumeFileMetadata | null>(null)

const loading = ref(false)
const versionsLoading = ref(false)
const itemsLoading = ref(false)
const fileMetadataLoading = ref(false)
const fileMetadataLoadFailed = ref(false)
const savingResume = ref(false)
const savingVersion = ref(false)
const savingItem = ref(false)
const uploadingVersion = ref(false)
const downloadingFile = ref(false)

const versionDialogVisible = ref(false)
const versionAction = ref<'create' | 'generate' | 'copy'>('create')
const copySourceId = ref<number | null>(null)
const versionFormRef = ref<FormInstance>()
const itemDialogVisible = ref(false)
const editingItemId = ref<number | null>(null)
const itemFormRef = ref<FormInstance>()
const uploadDialogVisible = ref(false)
const uploadFileList = ref<UploadUserFile[]>([])
const uploadForm = reactive({ label: '' })

const versionForm = reactive<ResumeVersionRequest>({ label: '' })
const itemForm = reactive<ResumeContentItemRequest>({ sectionType: 'PROFILE', title: '', content: '', sortOrder: 0 })

let selectionRequestId = 0

const sectionOptions: Array<{ value: ResumeSectionType; label: string }> = [
  { value: 'PROFILE', label: '基本资料' },
  { value: 'EDUCATION', label: '教育经历' },
  { value: 'SKILL', label: '技能' },
  { value: 'PROJECT', label: '项目经历' },
  { value: 'INTERNSHIP', label: '实习经历' },
  { value: 'CERTIFICATE', label: '证书 / 获奖' },
]

const itemRules: FormRules = {
  sectionType: [{ required: true, message: '请选择内容分区', trigger: 'change' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
  sortOrder: [{ required: true, type: 'number', min: 0, message: '排序值必须为非负数', trigger: 'change' }],
}

const isFinalized = computed(() => selectedVersion.value?.status === 'FINALIZED')
const versionDialogTitle = computed(() => {
  if (versionAction.value === 'generate') return '从共享基础档案生成 DRAFT'
  if (versionAction.value === 'copy') return '复制为新的 DRAFT'
  return '新建空白 DRAFT'
})

const MAX_RESUME_FILE_SIZE = 5 * 1024 * 1024

function sectionLabel(value: ResumeSectionType): string {
  return sectionOptions.find((option) => option.value === value)?.label ?? value
}

function versionStatusLabel(status: ResumeVersion['status']): string {
  return status === 'FINALIZED' ? '已定稿 / 历史版本' : 'DRAFT 草稿'
}

function versionStatusTag(status: ResumeVersion['status']): 'warning' | 'success' {
  return status === 'FINALIZED' ? 'success' : 'warning'
}

function formatDate(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function formatFileType(metadata: ResumeFileMetadata): string {
  const filename = metadata.originalFilename.toLowerCase()
  if (metadata.contentType === 'application/pdf' || filename.endsWith('.pdf')) return 'PDF'
  if (
    metadata.contentType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' ||
    filename.endsWith('.docx')
  ) {
    return 'DOCX'
  }
  return metadata.contentType || '未知格式'
}

function formatFileSize(size: number): string {
  if (!Number.isFinite(size) || size < 0) return '未知大小'
  if (size < 1024) return `${size} B`
  const kilobytes = size / 1024
  if (kilobytes < 1024) {
    return `${Number.isInteger(kilobytes) ? kilobytes : kilobytes.toFixed(1)} KB`
  }
  const megabytes = kilobytes / 1024
  return `${Number.isInteger(megabytes) ? megabytes : megabytes.toFixed(1)} MB`
}

function getInitialVersionIdFromQuery(): number | undefined {
  const raw = route.query.versionId
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}

async function load(): Promise<void> {
  const id = resumeId.value
  if (!Number.isInteger(id) || id <= 0) return
  loading.value = true
  try {
    const loadedResume = await getResume(id)
    resume.value = loadedResume
    Object.assign(resumeForm, { name: loadedResume.name, description: loadedResume.description ?? '' })
    await loadVersions(getInitialVersionIdFromQuery())
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    loading.value = false
  }
}

async function loadVersions(preferredId?: number): Promise<void> {
  const id = resumeId.value
  versionsLoading.value = true
  try {
    versions.value = await listResumeVersions(id)
    const next = versions.value.find((version) => version.id === preferredId)
      ?? versions.value.find((version) => version.id === selectedVersionId.value)
      ?? versions.value[0]
    if (next) {
      await selectVersion(next)
    } else {
      selectionRequestId += 1
      selectedVersionId.value = null
      selectedVersion.value = null
      items.value = []
      selectedFileMetadata.value = null
      fileMetadataLoadFailed.value = false
      itemsLoading.value = false
      fileMetadataLoading.value = false
    }
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    versionsLoading.value = false
  }
}

async function selectVersion(row: ResumeVersion): Promise<void> {
  const requestId = ++selectionRequestId
  selectedVersionId.value = row.id
  selectedVersion.value = row
  versionForm.label = row.label ?? ''
  itemsLoading.value = true
  fileMetadataLoading.value = true
  items.value = []
  selectedFileMetadata.value = null
  fileMetadataLoadFailed.value = false
  try {
    const [loadedItems, loadedFileMetadata] = await Promise.allSettled([
      listResumeItems(resumeId.value, row.id),
      getResumeFile(resumeId.value, row.id),
    ])
    if (requestId !== selectionRequestId) return
    if (loadedItems.status === 'fulfilled') {
      items.value = loadedItems.value
    }
    if (loadedFileMetadata.status === 'fulfilled') {
      selectedFileMetadata.value = loadedFileMetadata.value
    } else {
      fileMetadataLoadFailed.value = true
    }
  } finally {
    if (requestId === selectionRequestId) {
      itemsLoading.value = false
      fileMetadataLoading.value = false
    }
  }
}

function goBack(): void {
  void router.push({ name: 'resumes' })
}

async function saveResume(): Promise<void> {
  if (!resume.value || isFinalized.value || !resumeForm.name.trim()) return
  savingResume.value = true
  try {
    resume.value = await updateResume(resume.value.id, {
      name: resumeForm.name.trim(),
      description: resumeForm.description?.trim() || undefined,
    })
    Object.assign(resumeForm, { name: resume.value.name, description: resume.value.description ?? '' })
    ElMessage.success('简历信息已更新')
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    savingResume.value = false
  }
}

function openVersionDialog(action: 'create' | 'generate' | 'copy', source?: ResumeVersion): void {
  if (action === 'copy' && !source) return
  versionAction.value = action
  copySourceId.value = action === 'copy' ? source?.id ?? null : null
  versionForm.label = ''
  versionDialogVisible.value = true
}

function openUploadDialog(): void {
  resetUploadDialog()
  uploadDialogVisible.value = true
}

function closeUploadDialog(): void {
  uploadDialogVisible.value = false
  resetUploadDialog()
}

function resetUploadDialog(): void {
  uploadFileList.value = []
  uploadForm.label = ''
}

function handleUploadExceed(): void {
  ElMessage.warning('一次只能选择一个文件，请先移除当前文件')
}

function getValidatedUploadFile(): File | null {
  const file = uploadFileList.value[0]?.raw
  if (!file || file.size <= 0) {
    ElMessage.warning('请选择要上传的 PDF 或 DOCX 文件')
    return null
  }

  const filename = file.name.toLowerCase()
  if (!filename.endsWith('.pdf') && !filename.endsWith('.docx')) {
    ElMessage.warning('仅支持上传 PDF 或 DOCX 文件')
    return null
  }

  if (file.size > MAX_RESUME_FILE_SIZE) {
    ElMessage.warning('文件大小不能超过 5 MiB')
    return null
  }

  return file
}

async function submitUploadVersion(): Promise<void> {
  if (uploadingVersion.value || !resume.value) return
  const file = getValidatedUploadFile()
  if (!file) return

  uploadingVersion.value = true
  try {
    const uploaded = await uploadResumeVersion(resume.value.id, file, uploadForm.label.trim() || undefined)
    closeUploadDialog()
    ElMessage.success('简历文件版本已上传')
    await loadVersions(uploaded.version.id)
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    uploadingVersion.value = false
  }
}

async function downloadSelectedFile(): Promise<void> {
  const currentResume = resume.value
  const currentVersion = selectedVersion.value
  const metadata = selectedFileMetadata.value
  if (!currentResume || !currentVersion || !metadata || downloadingFile.value) return

  const versionId = currentVersion.id
  const filename = metadata.originalFilename
  let objectUrl: string | null = null
  let anchor: HTMLAnchorElement | null = null
  downloadingFile.value = true
  try {
    const blob = await downloadResumeFile(currentResume.id, versionId)
    objectUrl = URL.createObjectURL(blob)
    anchor = document.createElement('a')
    anchor.href = objectUrl
    anchor.download = filename.replace(/[\\/:*?"<>|]/g, '_')
    anchor.style.display = 'none'
    document.body.appendChild(anchor)
    anchor.click()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    anchor?.remove()
    if (objectUrl) {
      const urlToRevoke = objectUrl
      window.setTimeout(() => URL.revokeObjectURL(urlToRevoke), 0)
    }
    downloadingFile.value = false
  }
}

async function submitVersionAction(): Promise<void> {
  if (!resume.value) return
  const label = versionForm.label?.trim()
  const payload: ResumeVersionRequest = label ? { label } : {}
  savingVersion.value = true
  try {
    let created: ResumeVersion
    if (versionAction.value === 'generate') {
      created = await generateResumeVersion(resume.value.id, payload)
    } else if (versionAction.value === 'copy') {
      if (!copySourceId.value) return
      created = await copyResumeVersion(resume.value.id, copySourceId.value, payload)
    } else {
      created = await createResumeVersion(resume.value.id, payload)
    }
    versionDialogVisible.value = false
    ElMessage.success(versionAction.value === 'copy' ? '版本已复制为新的 DRAFT' : '新的 DRAFT 版本已创建')
    await loadVersions(created.id)
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    savingVersion.value = false
  }
}

async function saveVersionMetadata(): Promise<void> {
  if (!resume.value || !selectedVersion.value || selectedVersion.value.status !== 'DRAFT') return
  savingVersion.value = true
  try {
    const updated = await updateResumeVersion(resume.value.id, selectedVersion.value.id, {
      label: versionForm.label?.trim() || undefined,
    })
    selectedVersion.value = updated
    versions.value = versions.value.map((version) => (version.id === updated.id ? updated : version))
    versionForm.label = updated.label ?? ''
    ElMessage.success('版本标签已更新')
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    savingVersion.value = false
  }
}

async function finalizeVersion(): Promise<void> {
  if (!resume.value || !selectedVersion.value || selectedVersion.value.status !== 'DRAFT') return
  try {
    await ElMessageBox.confirm('定稿后该版本及内容将不可修改。确定要定稿吗？', '确认定稿', {
      type: 'warning',
      confirmButtonText: '确认定稿',
      cancelButtonText: '取消',
    })
    const finalized = await finalizeResumeVersion(resume.value.id, selectedVersion.value.id)
    ElMessage.success('版本已定稿，内容现为只读')
    await loadVersions(finalized.id)
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

async function removeVersion(row: ResumeVersion): Promise<void> {
  if (!resume.value || row.status !== 'DRAFT') return
  try {
    await ElMessageBox.confirm(`确定删除 v${row.versionNo} 草稿吗？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteResumeVersion(resume.value.id, row.id)
    ElMessage.success('DRAFT 版本已删除')
    await loadVersions()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

function emptyItemForm(): ResumeContentItemRequest {
  return { sectionType: 'PROFILE', title: '', content: '', sortOrder: 0 }
}

function openItemCreate(): void {
  if (!selectedVersion.value || selectedVersion.value.status !== 'DRAFT') return
  editingItemId.value = null
  Object.assign(itemForm, emptyItemForm())
  itemDialogVisible.value = true
}

function openItemEdit(row: ResumeContentItem): void {
  if (!selectedVersion.value || selectedVersion.value.status !== 'DRAFT') return
  editingItemId.value = row.id
  Object.assign(itemForm, {
    sectionType: row.sectionType,
    title: row.title ?? '',
    content: row.content,
    sortOrder: row.sortOrder,
  })
  itemDialogVisible.value = true
}

async function saveItem(): Promise<void> {
  const valid = await itemFormRef.value?.validate().catch(() => false)
  if (!valid || !resume.value || !selectedVersion.value || selectedVersion.value.status !== 'DRAFT' || !itemForm.content.trim()) return
  savingItem.value = true
  const payload: ResumeContentItemRequest = {
    sectionType: itemForm.sectionType,
    title: itemForm.title?.trim() || undefined,
    content: itemForm.content.trim(),
    sortOrder: itemForm.sortOrder,
  }
  try {
    const id = editingItemId.value
    const isCreate = id === null
    if (isCreate) {
      await createResumeItem(resume.value.id, selectedVersion.value.id, payload)
    } else {
      await updateResumeItem(resume.value.id, selectedVersion.value.id, id, payload)
    }
    itemDialogVisible.value = false
    ElMessage.success(isCreate ? '内容条目已创建' : '内容条目已更新')
    await selectVersion(selectedVersion.value)
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    savingItem.value = false
  }
}

async function removeItem(row: ResumeContentItem): Promise<void> {
  if (!resume.value || !selectedVersion.value || selectedVersion.value.status !== 'DRAFT') return
  try {
    await ElMessageBox.confirm('确定删除这条内容吗？', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteResumeItem(resume.value.id, selectedVersion.value.id, row.id)
    ElMessage.success('内容条目已删除')
    await selectVersion(selectedVersion.value)
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

onMounted(load)
</script>

<style scoped>
.page-shell { max-width: 1240px; margin: 0 auto; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-heading h2 { margin: 4px 0 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.back-button { padding: 0; }
.resume-form { padding-top: 2px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 4px 0 16px; }
.section-heading--top { align-items: flex-start; }
.section-heading h3 { margin: 0 0 5px; font-size: 16px; color: var(--el-text-color-primary); }
.muted { color: var(--el-text-color-secondary); font-size: 13px; }
.heading-actions { display: flex; min-width: 0; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.table-wrap { min-height: 180px; }
.version-meta-form { margin: 0 0 10px; }
.version-meta-form :deep(.el-form-item) { margin-bottom: 10px; }
.readonly-alert { margin: 8px 0 20px; }
.items-heading { margin-top: 24px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
.file-panel { min-height: 64px; padding: 14px 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
.file-details { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 16px; }
.file-details__name { min-width: 0; max-width: 100%; overflow-wrap: anywhere; color: var(--el-text-color-primary); font-weight: 600; }
.file-details__meta { color: var(--el-text-color-secondary); font-size: 13px; }
.resume-upload { max-width: 100%; }
@media (max-width: 760px) {
  .page-heading, .section-heading { align-items: flex-start; flex-direction: column; }
  .heading-actions { width: 100%; justify-content: flex-start; }
  .form-grid { grid-template-columns: 1fr; }
  .version-meta-form { display: block; }
}
</style>
