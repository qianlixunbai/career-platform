<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>学习计划</h2>
            <p>按周拆解学习目标，并在计划详情中跟踪任务、学习记录和复盘。</p>
          </div>
          <el-button type="primary" @click="openCreate">新建计划</el-button>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && plans.length === 0" description="还没有学习计划" />
        <el-table v-else :data="plans" stripe>
          <el-table-column label="计划周期" width="230">
            <template #default="{ row }">{{ dateRange(row.weekStart, row.weekEnd) }}</template>
          </el-table-column>
          <el-table-column prop="mainGoal" label="本周目标" min-width="280" show-overflow-tooltip />
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="viewPlan(row.id)">详情</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建学习计划' : '编辑学习计划'"
      width="540px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <div class="form-grid">
          <el-form-item label="开始日期" prop="weekStart">
            <el-date-picker
              v-model="form.weekStart"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择开始日期"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="结束日期" prop="weekEnd">
            <el-date-picker
              v-model="form.weekEnd"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择结束日期"
              class="full-width"
            />
          </el-form-item>
        </div>
        <el-form-item label="本周目标" prop="mainGoal">
          <el-input
            v-model="form.mainGoal"
            maxlength="500"
            show-word-limit
            placeholder="例如：完成 Vue 组件化练习并整理笔记"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" class="full-width" placeholder="选择计划状态">
            <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  createLearningPlan,
  deleteLearningPlan,
  listLearningPlans,
  updateLearningPlan,
} from '@/api/learning'
import type { LearningPlan, LearningPlanRequest, LearningPlanStatus } from '@/types/learning'

const router = useRouter()
const plans = ref<LearningPlan[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const statusOptions: Array<{ value: LearningPlanStatus; label: string }> = [
  { value: 'PLANNED', label: '未开始' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'COMPLETED', label: '已完成' },
]

function emptyForm(): LearningPlanRequest {
  return { weekStart: '', weekEnd: '', mainGoal: '', status: 'PLANNED' }
}

const form = reactive<LearningPlanRequest>(emptyForm())
const rules: FormRules = {
  weekStart: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  weekEnd: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
  mainGoal: [{ required: true, message: '请输入本周目标', trigger: 'blur' }],
  status: [{ required: true, message: '请选择计划状态', trigger: 'change' }],
}

function statusLabel(status: LearningPlanStatus): string {
  return statusOptions.find((option) => option.value === status)?.label ?? status
}

function statusTag(status: LearningPlanStatus): 'info' | 'warning' | 'success' {
  if (status === 'IN_PROGRESS') return 'warning'
  if (status === 'COMPLETED') return 'success'
  return 'info'
}

function dateRange(start: string, end: string): string {
  return `${start || '未填写'} — ${end || '未填写'}`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    plans.value = await listLearningPlans()
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

function openEdit(row: LearningPlan): void {
  editingId.value = row.id
  Object.assign(form, {
    weekStart: row.weekStart,
    weekEnd: row.weekEnd,
    mainGoal: row.mainGoal,
    status: row.status,
  })
  dialogVisible.value = true
}

function viewPlan(id: number): void {
  void router.push({ name: 'learning-plan-detail', params: { planId: id } })
}

async function save(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.weekEnd < form.weekStart) {
    ElMessage.warning('结束日期不能早于开始日期')
    return
  }

  saving.value = true
  const payload: LearningPlanRequest = {
    weekStart: form.weekStart,
    weekEnd: form.weekEnd,
    mainGoal: form.mainGoal.trim(),
    status: form.status,
  }
  try {
    const id = editingId.value
    const isCreate = id === null
    if (isCreate) {
      await createLearningPlan(payload)
    } else {
      await updateLearningPlan(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '学习计划已创建' : '学习计划已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

async function remove(row: LearningPlan): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${row.mainGoal}”这份学习计划吗？相关任务和资料也会被删除。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteLearningPlan(row.id)
    ElMessage.success('学习计划已删除')
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
@media (max-width: 640px) {
  .page-heading { align-items: flex-start; flex-direction: column; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
