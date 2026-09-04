<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>学习笔记 / 资料</h2>
            <p>围绕学习计划沉淀笔记和资料元数据；当前资料仅记录链接与描述，不上传文件。</p>
          </div>
          <el-select
            v-model="selectedPlanId"
            placeholder="选择学习计划"
            class="plan-select"
            :disabled="plans.length === 0"
            @change="loadSelectedPlan"
          >
            <el-option v-for="plan in plans" :key="plan.id" :label="planLabel(plan)" :value="plan.id" />
          </el-select>
        </div>
      </template>

      <el-empty v-if="!plansLoading && plans.length === 0" description="请先创建学习计划，再管理笔记和资料" />
      <template v-else>
        <el-tabs v-model="activeTab">
          <el-tab-pane label="学习笔记" name="notes">
            <div class="section-heading">
              <div>
                <h3>笔记</h3>
                <span class="muted">可选地将笔记关联到当前计划中的某个任务。</span>
              </div>
              <el-button type="primary" :disabled="!selectedPlanId" @click="openNoteCreate">新增笔记</el-button>
            </div>
            <div v-loading="resourcesLoading" class="resource-grid">
              <el-empty v-if="!resourcesLoading && notes.length === 0" description="当前计划还没有笔记" />
              <el-card v-for="note in notes" v-else :key="note.id" shadow="hover" class="resource-card">
                <template #header>
                  <div class="resource-card__heading">
                    <span class="resource-card__title">{{ note.title }}</span>
                    <el-dropdown trigger="click" @command="(command: string) => handleNoteCommand(command, note)">
                      <el-button text>···</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="edit">编辑</el-dropdown-item>
                          <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
                <p class="resource-card__content">{{ note.content }}</p>
                <div class="resource-card__meta">{{ taskLabel(note.taskId) }} · 更新于 {{ formatDate(note.updatedAt || note.createdAt) }}</div>
              </el-card>
            </div>
          </el-tab-pane>

          <el-tab-pane label="资料元数据" name="materials">
            <div class="section-heading">
              <div>
                <h3>资料元数据</h3>
                <span class="muted">保存资料标题、链接和描述，便于后续整理。</span>
              </div>
              <el-button type="primary" :disabled="!selectedPlanId" @click="openMaterialCreate">新增资料</el-button>
            </div>
            <div v-loading="resourcesLoading" class="table-wrap">
              <el-empty v-if="!resourcesLoading && materials.length === 0" description="当前计划还没有资料" />
              <el-table v-else :data="materials" stripe>
                <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
                <el-table-column label="链接" min-width="240" show-overflow-tooltip>
                  <template #default="{ row }">
                    <a v-if="row.sourceUrl" :href="row.sourceUrl" target="_blank" rel="noreferrer">{{ row.sourceUrl }}</a>
                    <span v-else class="muted">未填写</span>
                  </template>
                </el-table-column>
                <el-table-column label="关联任务" width="170">
                  <template #default="{ row }">{{ taskLabel(row.taskId) }}</template>
                </el-table-column>
                <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
                <el-table-column label="操作" width="150" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openMaterialEdit(row)">编辑</el-button>
                    <el-button link type="danger" @click="removeMaterial(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-card>

    <el-dialog v-model="noteDialogVisible" :title="editingNoteId === null ? '新增学习笔记' : '编辑学习笔记'" width="600px" destroy-on-close>
      <el-form ref="noteFormRef" :model="noteForm" :rules="noteRules" label-position="top" @submit.prevent="saveNote">
        <el-form-item label="标题" prop="title">
          <el-input v-model="noteForm.title" maxlength="200" show-word-limit placeholder="例如：本周组件通信要点" />
        </el-form-item>
        <el-form-item label="关联任务">
          <el-select v-model="noteForm.taskId" clearable placeholder="可选" class="full-width">
            <el-option v-for="task in tasks" :key="task.id" :label="task.title" :value="task.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="noteForm.content" type="textarea" :rows="8" maxlength="16000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noteDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveNote">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="materialDialogVisible" :title="editingMaterialId === null ? '新增资料元数据' : '编辑资料元数据'" width="600px" destroy-on-close>
      <el-form ref="materialFormRef" :model="materialForm" :rules="materialRules" label-position="top" @submit.prevent="saveMaterial">
        <el-form-item label="标题" prop="title">
          <el-input v-model="materialForm.title" maxlength="200" show-word-limit placeholder="例如：Vue 官方响应式 API 文档" />
        </el-form-item>
        <el-form-item label="关联任务">
          <el-select v-model="materialForm.taskId" clearable placeholder="可选" class="full-width">
            <el-option v-for="task in tasks" :key="task.id" :label="task.title" :value="task.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源链接">
          <el-input v-model="materialForm.sourceUrl" maxlength="2048" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="materialForm.description" type="textarea" :rows="5" maxlength="16000" show-word-limit />
        </el-form-item>
        <el-alert title="当前版本只管理资料元数据，不会上传或处理文件。" type="info" :closable="false" show-icon />
      </el-form>
      <template #footer>
        <el-button @click="materialDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMaterial">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  createLearningMaterial,
  createLearningNote,
  deleteLearningMaterial,
  deleteLearningNote,
  listLearningMaterials,
  listLearningNotes,
  listLearningPlans,
  listLearningTasks,
  updateLearningMaterial,
  updateLearningNote,
} from '@/api/learning'
import type {
  LearningMaterial,
  LearningMaterialRequest,
  LearningNote,
  LearningNoteRequest,
  LearningPlan,
  LearningTask,
} from '@/types/learning'

const plans = ref<LearningPlan[]>([])
const tasks = ref<LearningTask[]>([])
const notes = ref<LearningNote[]>([])
const materials = ref<LearningMaterial[]>([])
const plansLoading = ref(false)
const resourcesLoading = ref(false)
const saving = ref(false)
const selectedPlanId = ref<number | null>(null)
const activeTab = ref('notes')

const noteDialogVisible = ref(false)
const editingNoteId = ref<number | null>(null)
const noteFormRef = ref<FormInstance>()
const materialDialogVisible = ref(false)
const editingMaterialId = ref<number | null>(null)
const materialFormRef = ref<FormInstance>()

function emptyNoteForm(): LearningNoteRequest {
  return { taskId: undefined, title: '', content: '' }
}

function emptyMaterialForm(): LearningMaterialRequest {
  return { taskId: undefined, title: '', sourceUrl: '', description: '' }
}

const noteForm = reactive<LearningNoteRequest>(emptyNoteForm())
const materialForm = reactive<LearningMaterialRequest>(emptyMaterialForm())
const noteRules: FormRules = {
  title: [{ required: true, message: '请输入笔记标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入笔记内容', trigger: 'blur' }],
}
const materialRules: FormRules = {
  title: [{ required: true, message: '请输入资料标题', trigger: 'blur' }],
}

function planLabel(plan: LearningPlan): string {
  return `${plan.weekStart} — ${plan.weekEnd} · ${plan.mainGoal}`
}

function taskLabel(taskId: number | null | undefined): string {
  if (!taskId) return '未关联任务'
  return tasks.value.find((task) => task.id === taskId)?.title ?? `任务 #${taskId}`
}

function formatDate(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

async function loadPlans(): Promise<void> {
  plansLoading.value = true
  try {
    plans.value = await listLearningPlans()
    if (!plans.value.some((plan) => plan.id === selectedPlanId.value)) {
      selectedPlanId.value = plans.value[0]?.id ?? null
    }
    await loadSelectedPlan()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    plansLoading.value = false
  }
}

async function loadSelectedPlan(): Promise<void> {
  if (!selectedPlanId.value) {
    notes.value = []
    materials.value = []
    tasks.value = []
    return
  }
  resourcesLoading.value = true
  try {
    const [loadedNotes, loadedMaterials, loadedTasks] = await Promise.all([
      listLearningNotes(selectedPlanId.value),
      listLearningMaterials(selectedPlanId.value),
      listLearningTasks(selectedPlanId.value),
    ])
    notes.value = loadedNotes
    materials.value = loadedMaterials
    tasks.value = loadedTasks
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    resourcesLoading.value = false
  }
}

function openNoteCreate(): void {
  if (!selectedPlanId.value) return
  editingNoteId.value = null
  Object.assign(noteForm, emptyNoteForm())
  noteDialogVisible.value = true
}

function openNoteEdit(row: LearningNote): void {
  editingNoteId.value = row.id
  Object.assign(noteForm, {
    taskId: row.taskId ?? undefined,
    title: row.title,
    content: row.content,
  })
  noteDialogVisible.value = true
}

function handleNoteCommand(command: string, row: LearningNote): void {
  if (command === 'edit') openNoteEdit(row)
  if (command === 'delete') void removeNote(row)
}

async function saveNote(): Promise<void> {
  const valid = await noteFormRef.value?.validate().catch(() => false)
  const planId = selectedPlanId.value
  if (!valid || !planId || !noteForm.title.trim() || !noteForm.content.trim()) return
  saving.value = true
  const payload: LearningNoteRequest = {
    taskId: noteForm.taskId || undefined,
    title: noteForm.title.trim(),
    content: noteForm.content.trim(),
  }
  try {
    const id = editingNoteId.value
    const isCreate = id === null
    if (isCreate) {
      await createLearningNote(planId, payload)
    } else {
      await updateLearningNote(planId, id, payload)
    }
    noteDialogVisible.value = false
    ElMessage.success(isCreate ? '学习笔记已创建' : '学习笔记已更新')
    await loadSelectedPlan()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

async function removeNote(row: LearningNote): Promise<void> {
  if (!selectedPlanId.value) return
  try {
    await ElMessageBox.confirm(`确定删除“${row.title}”吗？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteLearningNote(selectedPlanId.value, row.id)
    ElMessage.success('学习笔记已删除')
    await loadSelectedPlan()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

function openMaterialCreate(): void {
  if (!selectedPlanId.value) return
  editingMaterialId.value = null
  Object.assign(materialForm, emptyMaterialForm())
  materialDialogVisible.value = true
}

function openMaterialEdit(row: LearningMaterial): void {
  editingMaterialId.value = row.id
  Object.assign(materialForm, {
    taskId: row.taskId ?? undefined,
    title: row.title,
    sourceUrl: row.sourceUrl ?? '',
    description: row.description ?? '',
  })
  materialDialogVisible.value = true
}

async function saveMaterial(): Promise<void> {
  const valid = await materialFormRef.value?.validate().catch(() => false)
  const planId = selectedPlanId.value
  if (!valid || !planId || !materialForm.title.trim()) return
  saving.value = true
  const payload: LearningMaterialRequest = {
    taskId: materialForm.taskId || undefined,
    title: materialForm.title.trim(),
    sourceUrl: materialForm.sourceUrl?.trim() || undefined,
    description: materialForm.description?.trim() || undefined,
  }
  try {
    const id = editingMaterialId.value
    const isCreate = id === null
    if (isCreate) {
      await createLearningMaterial(planId, payload)
    } else {
      await updateLearningMaterial(planId, id, payload)
    }
    materialDialogVisible.value = false
    ElMessage.success(isCreate ? '资料元数据已创建' : '资料元数据已更新')
    await loadSelectedPlan()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

async function removeMaterial(row: LearningMaterial): Promise<void> {
  if (!selectedPlanId.value) return
  try {
    await ElMessageBox.confirm(`确定删除“${row.title}”吗？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteLearningMaterial(selectedPlanId.value, row.id)
    ElMessage.success('资料元数据已删除')
    await loadSelectedPlan()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

onMounted(loadPlans)
</script>

<style scoped>
.page-shell { max-width: 1220px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.plan-select { width: 320px; flex: 0 1 320px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 4px 0 16px; }
.section-heading h3 { margin: 0 0 5px; font-size: 16px; color: var(--el-text-color-primary); }
.muted { color: var(--el-text-color-secondary); font-size: 13px; }
.resource-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; min-height: 180px; }
.resource-card { min-height: 170px; }
.resource-card__heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.resource-card__title { overflow: hidden; color: var(--el-text-color-primary); font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.resource-card__content { min-height: 72px; margin: 0; color: var(--el-text-color-regular); line-height: 1.65; white-space: pre-wrap; }
.resource-card__meta { margin-top: 16px; color: var(--el-text-color-secondary); font-size: 12px; }
.table-wrap { min-height: 180px; }
.full-width { width: 100%; }
@media (max-width: 760px) {
  .page-heading, .section-heading { align-items: flex-start; flex-direction: column; }
  .plan-select { width: 100%; flex-basis: auto; }
  .resource-grid { grid-template-columns: 1fr; }
}
</style>
