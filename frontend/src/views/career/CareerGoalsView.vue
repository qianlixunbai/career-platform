<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>职业目标</h2>
            <p>把期望岗位和发展方向写清楚，持续跟踪目标进度。</p>
          </div>
          <el-button type="primary" @click="openCreate">新增目标</el-button>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && goals.length === 0" description="还没有职业目标" />
        <el-table v-else :data="goals" stripe>
          <el-table-column prop="targetPosition" label="目标岗位" min-width="180" show-overflow-tooltip />
          <el-table-column prop="targetCity" label="城市" width="130" show-overflow-tooltip />
          <el-table-column prop="targetIndustry" label="行业" width="140" show-overflow-tooltip />
          <el-table-column prop="salaryExpectation" label="薪资期望" width="140" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="notes" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增职业目标' : '编辑职业目标'" width="600px" destroy-on-close>
      <el-form :model="form" label-position="top" @submit.prevent="save">
        <div class="form-grid">
          <el-form-item label="目标岗位" required>
            <el-input v-model="form.targetPosition" maxlength="100" placeholder="例如：Java 后端工程师" />
          </el-form-item>
          <el-form-item label="状态" required>
            <el-select v-model="form.status" class="full-width" placeholder="选择状态">
              <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标城市">
            <el-input v-model="form.targetCity" maxlength="100" placeholder="例如：上海 / 杭州" />
          </el-form-item>
          <el-form-item label="目标行业">
            <el-input v-model="form.targetIndustry" maxlength="100" placeholder="例如：互联网 / 金融科技" />
          </el-form-item>
          <el-form-item label="目标公司偏好">
            <el-input v-model="form.targetCompanyPreference" maxlength="255" placeholder="例如：重视技术成长的中大型团队" />
          </el-form-item>
          <el-form-item label="薪资期望">
            <el-input v-model="form.salaryExpectation" maxlength="100" placeholder="例如：20k-30k" />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="form.notes" type="textarea" :rows="5" maxlength="16000" show-word-limit placeholder="补充目标拆解、时间安排或关注事项" />
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
import { createCareerGoal, deleteCareerGoal, listCareerGoals, updateCareerGoal } from '@/api/career'
import type { CareerGoal, CareerGoalRequest, CareerGoalStatus } from '@/types/career'

const statusOptions: Array<{ value: CareerGoalStatus; label: string }> = [
  { value: 'ACTIVE', label: '进行中' },
  { value: 'PAUSED', label: '已暂停' },
  { value: 'ACHIEVED', label: '已达成' },
]

const goals = ref<CareerGoal[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

function emptyForm(): CareerGoalRequest {
  return {
    targetPosition: '',
    targetCity: '',
    targetIndustry: '',
    targetCompanyPreference: '',
    salaryExpectation: '',
    notes: '',
    status: 'ACTIVE',
  }
}

const form = reactive<CareerGoalRequest>(emptyForm())

function statusLabel(value: CareerGoalStatus): string {
  return statusOptions.find((option) => option.value === value)?.label ?? value
}

function statusTag(value: CareerGoalStatus): 'success' | 'warning' | 'info' {
  if (value === 'ACHIEVED') return 'success'
  if (value === 'PAUSED') return 'warning'
  return 'info'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    goals.value = await listCareerGoals()
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

function openEdit(row: CareerGoal): void {
  editingId.value = row.id
  Object.assign(form, {
    targetPosition: row.targetPosition,
    targetCity: row.targetCity ?? '',
    targetIndustry: row.targetIndustry ?? '',
    targetCompanyPreference: row.targetCompanyPreference ?? '',
    salaryExpectation: row.salaryExpectation ?? '',
    notes: row.notes ?? '',
    status: row.status,
  })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  saving.value = true
  const payload: CareerGoalRequest = {
    targetPosition: form.targetPosition.trim(),
    targetCity: form.targetCity?.trim() || undefined,
    targetIndustry: form.targetIndustry?.trim() || undefined,
    targetCompanyPreference: form.targetCompanyPreference?.trim() || undefined,
    salaryExpectation: form.salaryExpectation?.trim() || undefined,
    notes: form.notes?.trim() || undefined,
    status: form.status,
  }
  try {
    const id = editingId.value
    const isCreate = id === null
    if (isCreate) {
      await createCareerGoal(payload)
    } else {
      await updateCareerGoal(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '职业目标已新增' : '职业目标已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

async function remove(row: CareerGoal): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${row.targetPosition}”这个职业目标吗？`, '确认删除', { type: 'warning' })
    await deleteCareerGoal(row.id)
    ElMessage.success('职业目标已删除')
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
