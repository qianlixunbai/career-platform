<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>实习经历</h2>
            <p>记录实习单位、岗位和实践成果，方便持续更新职业履历。</p>
          </div>
          <el-button type="primary" @click="openCreate">新增实习</el-button>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && internships.length === 0" description="还没有实习经历" />
        <el-table v-else :data="internships" stripe>
          <el-table-column prop="companyName" label="公司" min-width="180" show-overflow-tooltip />
          <el-table-column prop="position" label="岗位" min-width="150" show-overflow-tooltip />
          <el-table-column label="时间" width="210">
            <template #default="{ row }">{{ dateRange(row.startDate, row.endDate) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="280" show-overflow-tooltip />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增实习经历' : '编辑实习经历'" width="560px" destroy-on-close>
      <el-form :model="form" label-position="top" @submit.prevent="save">
        <div class="form-grid">
          <el-form-item label="公司名称" required>
            <el-input v-model="form.companyName" maxlength="200" placeholder="例如：星河科技" />
          </el-form-item>
          <el-form-item label="实习岗位" required>
            <el-input v-model="form.position" maxlength="100" placeholder="例如：产品实习生" />
          </el-form-item>
          <el-form-item label="开始日期" required>
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="full-width" />
          </el-form-item>
          <el-form-item label="结束日期">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="在职可留空" class="full-width" />
          </el-form-item>
        </div>
        <el-form-item label="经历描述">
          <el-input v-model="form.description" type="textarea" :rows="5" maxlength="5000" show-word-limit placeholder="说明工作内容、负责范围和量化成果" />
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
  createInternshipExperience,
  deleteInternshipExperience,
  listInternshipExperiences,
  updateInternshipExperience,
} from '@/api/profile'
import type { InternshipExperience, InternshipExperienceRequest } from '@/types/profile'

const internships = ref<InternshipExperience[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

function emptyForm(): InternshipExperienceRequest {
  return { companyName: '', position: '', startDate: '', endDate: '', description: '' }
}

const form = reactive<InternshipExperienceRequest>(emptyForm())

function dateRange(startDate: string, endDate: string | null): string {
  return `${startDate || '未填写'} — ${endDate || '至今'}`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    internships.value = await listInternshipExperiences()
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

function openEdit(row: InternshipExperience): void {
  editingId.value = row.id
  Object.assign(form, {
    companyName: row.companyName,
    position: row.position,
    startDate: row.startDate,
    endDate: row.endDate ?? '',
    description: row.description ?? '',
  })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  saving.value = true
  const payload: InternshipExperienceRequest = {
    companyName: form.companyName.trim(),
    position: form.position.trim(),
    startDate: form.startDate,
    endDate: form.endDate || undefined,
    description: form.description?.trim() || undefined,
  }
  try {
    const id = editingId.value
    const isCreate = id === null
    if (isCreate) {
      await createInternshipExperience(payload)
    } else {
      await updateInternshipExperience(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '实习经历已新增' : '实习经历已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

async function remove(row: InternshipExperience): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${row.companyName} · ${row.position}”吗？`, '确认删除', { type: 'warning' })
    await deleteInternshipExperience(row.id)
    ElMessage.success('实习经历已删除')
    await load()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

onMounted(load)
</script>

<style scoped>
.page-shell { max-width: 1120px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.table-wrap { min-height: 180px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
@media (max-width: 640px) { .page-heading { align-items: flex-start; flex-direction: column; } .form-grid { grid-template-columns: 1fr; } }
</style>
