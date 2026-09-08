<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>简历管理</h2>
            <p>管理不同求职方向的简历，并在详情页维护版本和内容快照。</p>
          </div>
          <div class="heading-actions">
            <el-button type="primary" :disabled="uploading" @click="openUpload">上传已有简历</el-button>
            <el-button @click="openCreate">新建简历</el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && resumes.length === 0" description="还没有简历" />
        <el-table v-else :data="resumes" stripe>
          <el-table-column prop="name" label="简历名称" min-width="240" show-overflow-tooltip />
          <el-table-column prop="description" label="描述" min-width="360" show-overflow-tooltip />
          <el-table-column label="最近更新" width="180">
            <template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="viewResume(row.id)">版本中心</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新建简历' : '编辑简历'" width="min(560px, 92vw)" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <el-form-item label="简历名称" prop="name">
          <el-input v-model="form.name" maxlength="200" show-word-limit placeholder="例如：前端开发岗位简历" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="说明目标岗位或版本用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="uploadDialogVisible"
      title="上传已有简历"
      width="min(560px, 92vw)"
      destroy-on-close
      :close-on-click-modal="!uploading"
      :close-on-press-escape="!uploading"
      :show-close="!uploading"
      @closed="resetUploadDialog"
    >
      <el-form
        ref="uploadFormRef"
        :model="uploadForm"
        :rules="uploadRules"
        label-position="top"
        @submit.prevent="submitUpload"
      >
        <el-form-item label="简历文件" required :error="uploadFileError">
          <el-upload
            ref="uploadRef"
            v-model:file-list="uploadFileList"
            :auto-upload="false"
            :limit="1"
            accept=".pdf,.docx"
            :disabled="uploading"
            :on-change="handleUploadFileChange"
            :on-remove="handleUploadFileRemove"
            :on-exceed="handleUploadFileExceed"
          >
            <el-button :disabled="uploading">选择 PDF / DOCX</el-button>
            <template #tip>
              <div class="upload-file-tip">仅支持 PDF 或 DOCX 文件，文件大小不超过 5 MiB。</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="简历名称" prop="name">
          <el-input
            v-model="uploadForm.name"
            maxlength="200"
            show-word-limit
            placeholder="例如：前端开发岗位简历"
            @input="markUploadNameTouched"
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="可选，说明目标岗位或版本用途"
          />
        </el-form-item>
        <el-form-item label="版本标签" prop="versionLabel">
          <el-input
            v-model="uploadForm.versionLabel"
            maxlength="200"
            show-word-limit
            placeholder="可选，例如：校招前端版本"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="uploading" @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
  type UploadFile,
  type UploadFiles,
  type UploadInstance,
  type UploadUserFile,
} from 'element-plus'

import { createResume, deleteResume, listResumes, updateResume, uploadResume } from '@/api/resume'
import type { Resume, ResumeRequest, ResumeUploadRequest } from '@/types/resume'

const router = useRouter()
const resumes = ref<Resume[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploadRef = ref<UploadInstance>()
const uploadFile = ref<File | null>(null)
const uploadFileList = ref<UploadUserFile[]>([])
const uploadFileError = ref('')
const uploadNameTouched = ref(false)

function emptyForm(): ResumeRequest {
  return { name: '', description: '' }
}

const form = reactive<ResumeRequest>(emptyForm())
const uploadForm = reactive<ResumeUploadRequest>({ name: '', description: '', versionLabel: '' })
const rules: FormRules = {
  name: [{ required: true, message: '请输入简历名称', trigger: 'blur' }],
}
const uploadRules: FormRules = {
  name: [
    { required: true, message: '请输入简历名称', trigger: 'blur' },
    { max: 200, message: '简历名称不能超过 200 个字符', trigger: 'blur' },
  ],
  description: [{ max: 2000, message: '描述不能超过 2000 个字符', trigger: 'blur' }],
  versionLabel: [{ max: 200, message: '版本标签不能超过 200 个字符', trigger: 'blur' }],
}

const MAX_UPLOAD_FILE_SIZE = 5 * 1024 * 1024

function formatDate(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    resumes.value = await listResumes()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: Resume): void {
  editingId.value = row.id
  Object.assign(form, { name: row.name, description: row.description ?? '' })
  dialogVisible.value = true
}

function openUpload(): void {
  if (uploading.value) return
  resetUploadDialog()
  uploadDialogVisible.value = true
}

function viewResume(id: number): void {
  void router.push({ name: 'resume-detail', params: { resumeId: id } })
}

async function save(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.name.trim()) return
  saving.value = true
  const payload: ResumeRequest = {
    name: form.name.trim(),
    description: form.description?.trim() || undefined,
  }
  try {
    const id = editingId.value
    const isCreate = id === null
    if (isCreate) {
      await createResume(payload)
    } else {
      await updateResume(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '简历已创建' : '简历已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error, including a 409 delete conflict.
  } finally {
    saving.value = false
  }
}

function markUploadNameTouched(): void {
  uploadNameTouched.value = true
}

function uploadFileValidationError(file: File): string {
  const fileName = file.name.toLowerCase()
  if (!fileName.endsWith('.pdf') && !fileName.endsWith('.docx')) {
    return '只支持 PDF 或 DOCX 文件。'
  }
  if (file.size <= 0) {
    return '文件不能为空。'
  }
  if (file.size > MAX_UPLOAD_FILE_SIZE) {
    return '文件不能超过 5 MiB。'
  }
  return ''
}

function defaultResumeName(fileName: string): string {
  return fileName.replace(/\.(pdf|docx)$/i, '')
}

function handleUploadFileChange(file: UploadFile, fileList: UploadFiles): void {
  uploadFileError.value = ''
  const rawFile = file.raw
  if (!rawFile) return

  const validationError = uploadFileValidationError(rawFile)
  if (validationError) {
    uploadFile.value = null
    uploadFileList.value = []
    uploadRef.value?.clearFiles()
    uploadFileError.value = validationError
    return
  }

  uploadFile.value = rawFile
  uploadFileList.value = fileList
  if (!uploadNameTouched.value) {
    uploadForm.name = defaultResumeName(rawFile.name)
  }
}

function handleUploadFileRemove(): void {
  uploadFile.value = null
  uploadFileList.value = []
  uploadFileError.value = ''
}

function handleUploadFileExceed(): void {
  uploadFileError.value = '一次只能选择一个文件，请先移除当前文件。'
}

function resetUploadDialog(): void {
  uploadFile.value = null
  uploadFileList.value = []
  uploadFileError.value = ''
  uploadNameTouched.value = false
  Object.assign(uploadForm, { name: '', description: '', versionLabel: '' })
  uploadFormRef.value?.clearValidate()
  uploadRef.value?.clearFiles()
}

async function submitUpload(): Promise<void> {
  if (uploading.value) return

  const file = uploadFile.value
  if (!file && !uploadFileError.value) {
    uploadFileError.value = '请选择要上传的简历文件。'
  }
  const valid = await uploadFormRef.value?.validate().catch(() => false)
  if (!valid || !file) return

  const fileValidationError = uploadFileValidationError(file)
  if (fileValidationError) {
    uploadFileError.value = fileValidationError
    return
  }
  if (uploadFileError.value) return

  const name = uploadForm.name.trim()
  if (!name) return

  const payload: ResumeUploadRequest = {
    name,
    description: uploadForm.description?.trim() || undefined,
    versionLabel: uploadForm.versionLabel?.trim() || undefined,
  }
  uploading.value = true
  try {
    const response = await uploadResume(file, payload)
    ElMessage.success('简历上传成功')
    resetUploadDialog()
    uploadDialogVisible.value = false
    await router.push({
      name: 'resume-detail',
      params: { resumeId: response.resume.id },
      query: { versionId: response.version.id },
    })
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    uploading.value = false
  }
}

async function remove(row: Resume): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除“${row.name}”吗？如果它包含已定稿版本，后端会拒绝删除。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await deleteResume(row.id)
    ElMessage.success('简历已删除')
    await load()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

onMounted(load)
</script>

<style scoped>
.page-shell { max-width: 1180px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.heading-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.table-wrap { min-height: 180px; }
.upload-file-tip { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
@media (max-width: 640px) { .page-heading { align-items: flex-start; flex-direction: column; } }
</style>
