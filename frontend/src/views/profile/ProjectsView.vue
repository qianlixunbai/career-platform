<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>项目经历</h2>
            <p>沉淀项目中的职责、技术栈和可公开访问的成果链接。</p>
          </div>
          <el-button type="primary" @click="openCreate">新增项目</el-button>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && projects.length === 0" description="还没有项目经历" />
        <el-table v-else :data="projects" stripe>
          <el-table-column prop="projectName" label="项目名称" min-width="170" show-overflow-tooltip />
          <el-table-column prop="role" label="角色" width="140" show-overflow-tooltip />
          <el-table-column label="时间" width="200">
            <template #default="{ row }">{{ dateRange(row.startDate, row.endDate) }}</template>
          </el-table-column>
          <el-table-column prop="techStack" label="技术栈" min-width="190" show-overflow-tooltip />
          <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增项目经历' : '编辑项目经历'" width="600px" destroy-on-close>
      <el-form :model="form" label-position="top" @submit.prevent="save">
        <div class="form-grid">
          <el-form-item label="项目名称" required>
            <el-input v-model="form.projectName" maxlength="200" placeholder="例如：求职管理平台" />
          </el-form-item>
          <el-form-item label="项目角色" required>
            <el-input v-model="form.role" maxlength="100" placeholder="例如：后端开发" />
          </el-form-item>
          <el-form-item label="开始日期" required>
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="full-width" />
          </el-form-item>
          <el-form-item label="结束日期">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="full-width" :disabled="ongoing" />
            <el-checkbox v-model="ongoing">至今</el-checkbox>
          </el-form-item>
        </div>
        <el-form-item label="技术栈" required>
          <el-input v-model="form.techStack" maxlength="500" placeholder="例如：Vue 3、Spring Boot、MySQL" />
        </el-form-item>
        <el-form-item label="项目链接">
          <el-input v-model="form.projectUrl" maxlength="500" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="5000" show-word-limit placeholder="说明项目背景、你的贡献和结果" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createProjectExperience,
  deleteProjectExperience,
  listProjectExperiences,
  updateProjectExperience,
} from '@/api/profile'
import type { ProjectExperience, ProjectExperienceRequest } from '@/types/profile'

const projects = ref<ProjectExperience[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const ongoing = ref(false)


function emptyForm(): ProjectExperienceRequest {
  return { projectName: '', role: '', startDate: '', endDate: '', description: '', techStack: '', projectUrl: '' }
}

const form = reactive<ProjectExperienceRequest>(emptyForm())
watch(ongoing, (value) => {
  if (value) {
    form.endDate = ''
  }
})

function dateRange(startDate: string, endDate: string | null): string {
  return `${startDate || '未填写'} — ${endDate || '至今'}`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    projects.value = await listProjectExperiences()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, emptyForm())
  ongoing.value = false
  dialogVisible.value = true
}

function openEdit(row: ProjectExperience): void {
  editingId.value = row.id
  Object.assign(form, {
    projectName: row.projectName,
    role: row.role,
    startDate: row.startDate,
    endDate: row.endDate ?? '',
    description: row.description ?? '',
    techStack: row.techStack,
    projectUrl: row.projectUrl ?? '',
  })
  ongoing.value = row.endDate === null
  dialogVisible.value = true
}

async function save(): Promise<void> {
  saving.value = true
  const payload: ProjectExperienceRequest = {
    projectName: form.projectName.trim(),
    role: form.role.trim(),
    startDate: form.startDate,
    endDate: ongoing.value ? undefined : (form.endDate || undefined),
    description: form.description?.trim() || undefined,
    techStack: form.techStack.trim(),
    projectUrl: form.projectUrl?.trim() || undefined,
  }
  try {
    const id = editingId.value
    const isCreate = id === null
    if (isCreate) {
      await createProjectExperience(payload)
    } else {
      await updateProjectExperience(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '项目经历已新增' : '项目经历已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

async function remove(row: ProjectExperience): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${row.projectName}”吗？`, '确认删除', { type: 'warning' })
    await deleteProjectExperience(row.id)
    ElMessage.success('项目经历已删除')
    await load()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

onMounted(load)
</script>

<style scoped>
.page-shell { max-width: 1200px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.table-wrap { min-height: 180px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
@media (max-width: 640px) { .page-heading { align-items: flex-start; flex-direction: column; } .form-grid { grid-template-columns: 1fr; } }
</style>
