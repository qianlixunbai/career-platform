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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  copyResumeVersion,
  createResumeItem,
  createResumeVersion,
  deleteResumeItem,
  deleteResumeVersion,
  finalizeResumeVersion,
  generateResumeVersion,
  getResume,
  listResumeItems,
  listResumeVersions,
  updateResume,
  updateResumeItem,
  updateResumeVersion,
} from '@/api/resume'
import type {
  Resume,
  ResumeContentItem,
  ResumeContentItemRequest,
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

const loading = ref(false)
const versionsLoading = ref(false)
const itemsLoading = ref(false)
const savingResume = ref(false)
const savingVersion = ref(false)
const savingItem = ref(false)

const versionDialogVisible = ref(false)
const versionAction = ref<'create' | 'generate' | 'copy'>('create')
const copySourceId = ref<number | null>(null)
const versionFormRef = ref<FormInstance>()
const itemDialogVisible = ref(false)
const editingItemId = ref<number | null>(null)
const itemFormRef = ref<FormInstance>()

const versionForm = reactive<ResumeVersionRequest>({ label: '' })
const itemForm = reactive<ResumeContentItemRequest>({ sectionType: 'PROFILE', title: '', content: '', sortOrder: 0 })

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

async function load(): Promise<void> {
  const id = resumeId.value
  if (!Number.isInteger(id) || id <= 0) return
  loading.value = true
  try {
    const loadedResume = await getResume(id)
    resume.value = loadedResume
    Object.assign(resumeForm, { name: loadedResume.name, description: loadedResume.description ?? '' })
    await loadVersions()
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
      selectedVersionId.value = null
      selectedVersion.value = null
      items.value = []
    }
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    versionsLoading.value = false
  }
}

async function selectVersion(row: ResumeVersion): Promise<void> {
  selectedVersionId.value = row.id
  selectedVersion.value = row
  versionForm.label = row.label ?? ''
  itemsLoading.value = true
  try {
    items.value = await listResumeItems(resumeId.value, row.id)
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    itemsLoading.value = false
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
.heading-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.table-wrap { min-height: 180px; }
.version-meta-form { margin: 0 0 10px; }
.version-meta-form :deep(.el-form-item) { margin-bottom: 10px; }
.readonly-alert { margin: 8px 0 20px; }
.items-heading { margin-top: 24px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
@media (max-width: 760px) {
  .page-heading, .section-heading { align-items: flex-start; flex-direction: column; }
  .heading-actions { justify-content: flex-start; }
  .form-grid { grid-template-columns: 1fr; }
  .version-meta-form { display: block; }
}
</style>
