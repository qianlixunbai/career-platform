<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>简历管理</h2>
            <p>管理不同求职方向的简历，并在详情页维护版本和内容快照。</p>
          </div>
          <el-button type="primary" @click="openCreate">新建简历</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新建简历' : '编辑简历'" width="560px" destroy-on-close>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import { createResume, deleteResume, listResumes, updateResume } from '@/api/resume'
import type { Resume, ResumeRequest } from '@/types/resume'

const router = useRouter()
const resumes = ref<Resume[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

function emptyForm(): ResumeRequest {
  return { name: '', description: '' }
}

const form = reactive<ResumeRequest>(emptyForm())
const rules: FormRules = {
  name: [{ required: true, message: '请输入简历名称', trigger: 'blur' }],
}

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
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.table-wrap { min-height: 180px; }
@media (max-width: 640px) { .page-heading { align-items: flex-start; flex-direction: column; } }
</style>
