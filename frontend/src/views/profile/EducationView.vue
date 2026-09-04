<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>教育经历</h2>
            <p>记录学历、专业和就读时间，帮助完善你的职业档案。</p>
          </div>
          <el-button type="primary" @click="openCreate">新增经历</el-button>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && experiences.length === 0" description="还没有教育经历" />
        <el-table v-else :data="experiences" stripe>
          <el-table-column prop="schoolName" label="学校" min-width="170" show-overflow-tooltip />
          <el-table-column prop="major" label="专业" min-width="140" show-overflow-tooltip />
          <el-table-column prop="degree" label="学历" width="110" />
          <el-table-column label="就读时间" min-width="200">
            <template #default="{ row }">{{ dateRange(row.startDate, row.endDate) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增教育经历' : '编辑教育经历'" width="560px" destroy-on-close>
      <el-form :model="form" label-position="top" @submit.prevent="save">
        <div class="form-grid">
          <el-form-item label="学校名称" required>
            <el-input v-model="form.schoolName" maxlength="150" placeholder="例如：复旦大学" />
          </el-form-item>
          <el-form-item label="专业" required>
            <el-input v-model="form.major" maxlength="150" placeholder="例如：计算机科学与技术" />
          </el-form-item>
          <el-form-item label="学历" required>
            <el-input v-model="form.degree" maxlength="50" placeholder="例如：本科 / 硕士" />
          </el-form-item>
          <el-form-item label="开始日期" required>
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" class="full-width" />
          </el-form-item>
          <el-form-item label="结束日期">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="在读可留空" class="full-width" />
          </el-form-item>
        </div>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="5000" show-word-limit placeholder="补充课程、成绩或校园经历" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createEducationExperience,
  deleteEducationExperience,
  listEducationExperiences,
  updateEducationExperience,
} from '@/api/profile'
import type { EducationExperience, EducationExperienceRequest } from '@/types/profile'

const experiences = ref<EducationExperience[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

function emptyForm(): EducationExperienceRequest {
  return { schoolName: '', major: '', degree: '', startDate: '', endDate: '', description: '' }
}

const form = reactive<EducationExperienceRequest>(emptyForm())

function dateRange(startDate: string, endDate: string | null): string {
  return `${startDate || '未填写'} — ${endDate || '至今'}`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    experiences.value = await listEducationExperiences()
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

function openEdit(row: EducationExperience): void {
  editingId.value = row.id
  Object.assign(form, {
    schoolName: row.schoolName,
    major: row.major,
    degree: row.degree,
    startDate: row.startDate,
    endDate: row.endDate ?? '',
    description: row.description ?? '',
  })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  saving.value = true
  const payload: EducationExperienceRequest = {
    schoolName: form.schoolName.trim(),
    major: form.major.trim(),
    degree: form.degree.trim(),
    startDate: form.startDate,
    endDate: form.endDate || undefined,
    description: form.description?.trim() || undefined,
  }
  try {
    const id = editingId.value
    if (id === null) {
      await createEducationExperience(payload)
    } else {
      await updateEducationExperience(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(id === null ? '教育经历已新增' : '教育经历已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

async function remove(row: EducationExperience): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${row.schoolName}”这段教育经历吗？`, '确认删除', { type: 'warning' })
    await deleteEducationExperience(row.id)
    ElMessage.success('教育经历已删除')
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
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.table-wrap { min-height: 180px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
@media (max-width: 640px) { .page-heading { align-items: flex-start; flex-direction: column; } .form-grid { grid-template-columns: 1fr; } }
</style>
