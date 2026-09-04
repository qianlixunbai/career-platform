<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>公司</h2>
            <p>管理求职过程中关注的公司，岗位会关联到这里的公司档案。</p>
          </div>
          <el-button type="primary" @click="openCreate">新增公司</el-button>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && companies.length === 0" description="还没有公司记录" />
        <el-table v-else :data="companies" stripe>
          <el-table-column prop="name" label="公司名称" min-width="190" show-overflow-tooltip />
          <el-table-column prop="industry" label="行业" min-width="140" show-overflow-tooltip />
          <el-table-column prop="city" label="城市" width="130" show-overflow-tooltip />
          <el-table-column prop="size" label="规模" width="130" show-overflow-tooltip />
          <el-table-column prop="website" label="官网" min-width="200" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增公司' : '编辑公司'" width="600px" destroy-on-close>
      <el-form :model="form" label-position="top" @submit.prevent="save">
        <div class="form-grid">
          <el-form-item label="公司名称" required>
            <el-input v-model="form.name" maxlength="150" placeholder="例如：星河科技" />
          </el-form-item>
          <el-form-item label="行业">
            <el-input v-model="form.industry" maxlength="100" placeholder="例如：互联网" />
          </el-form-item>
          <el-form-item label="城市">
            <el-input v-model="form.city" maxlength="100" placeholder="例如：上海" />
          </el-form-item>
          <el-form-item label="公司规模">
            <el-input v-model="form.size" maxlength="50" placeholder="例如：500-1000 人" />
          </el-form-item>
        </div>
        <el-form-item label="公司官网">
          <el-input v-model="form.website" maxlength="255" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.notes" type="textarea" :rows="4" maxlength="16000" show-word-limit placeholder="记录文化、技术栈、面试体验等" />
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
import { createCompany, deleteCompany, listCompanies, updateCompany } from '@/api/career'
import type { Company, CompanyRequest } from '@/types/career'

const companies = ref<Company[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

function emptyForm(): CompanyRequest {
  return { name: '', industry: '', city: '', website: '', size: '', notes: '' }
}

const form = reactive<CompanyRequest>(emptyForm())

async function load(): Promise<void> {
  loading.value = true
  try {
    companies.value = await listCompanies()
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

function openEdit(row: Company): void {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    industry: row.industry ?? '',
    city: row.city ?? '',
    website: row.website ?? '',
    size: row.size ?? '',
    notes: row.notes ?? '',
  })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  saving.value = true
  const payload: CompanyRequest = {
    name: form.name.trim(),
    industry: form.industry?.trim() || undefined,
    city: form.city?.trim() || undefined,
    website: form.website?.trim() || undefined,
    size: form.size?.trim() || undefined,
    notes: form.notes?.trim() || undefined,
  }
  try {
    const id = editingId.value
    const isCreate = id === null
    if (isCreate) {
      await createCompany(payload)
    } else {
      await updateCompany(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '公司已新增' : '公司已更新')
    await load()
  } catch {
    // In particular, a 409 when jobs reference this company is displayed by the interceptor.
  } finally {
    saving.value = false
  }
}

async function remove(row: Company): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${row.name}”吗？已有岗位关联时后端会拒绝删除。`, '确认删除', { type: 'warning' })
    await deleteCompany(row.id)
    ElMessage.success('公司已删除')
    await load()
  } catch {
    // Cancellation and structured API errors require no additional page-level message.
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
@media (max-width: 640px) { .page-heading { align-items: flex-start; flex-direction: column; } .form-grid { grid-template-columns: 1fr; } }
</style>
