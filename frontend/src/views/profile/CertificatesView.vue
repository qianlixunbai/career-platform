<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>证书与获奖</h2>
            <p>集中管理证书和奖项，为简历与求职材料提供可信依据。</p>
          </div>
          <el-button type="primary" @click="openCreate">新增记录</el-button>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && certificates.length === 0" description="还没有证书或获奖记录" />
        <el-table v-else :data="certificates" stripe>
          <el-table-column prop="name" label="名称" min-width="190" show-overflow-tooltip />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="row.type === 'CERTIFICATE' ? 'primary' : 'warning'">{{ typeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="issuer" label="颁发方" min-width="180" show-overflow-tooltip />
          <el-table-column prop="issueDate" label="颁发日期" width="130" />
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

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增证书/获奖' : '编辑证书/获奖'" width="560px" destroy-on-close>
      <el-form :model="form" label-position="top" @submit.prevent="save">
        <div class="form-grid">
          <el-form-item label="名称" required>
            <el-input v-model="form.name" maxlength="200" placeholder="例如：英语六级 / 校级优秀学生" />
          </el-form-item>
          <el-form-item label="类型" required>
            <el-select v-model="form.type" class="full-width" placeholder="选择类型">
              <el-option label="证书" value="CERTIFICATE" />
              <el-option label="奖项" value="AWARD" />
            </el-select>
          </el-form-item>
          <el-form-item label="颁发方" required>
            <el-input v-model="form.issuer" maxlength="200" placeholder="例如：中国教育考试网" />
          </el-form-item>
          <el-form-item label="颁发日期" required>
            <el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="full-width" />
          </el-form-item>
        </div>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="5000" show-word-limit placeholder="补充证书等级、获奖原因或成绩" />
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
import { createCertificateAward, deleteCertificateAward, listCertificateAwards, updateCertificateAward } from '@/api/profile'
import type { CertificateAward, CertificateAwardRequest, CertificateAwardType } from '@/types/profile'

const certificates = ref<CertificateAward[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

function emptyForm(): CertificateAwardRequest {
  return { name: '', type: 'CERTIFICATE', issuer: '', issueDate: '', description: '' }
}

const form = reactive<CertificateAwardRequest>(emptyForm())

function typeLabel(value: CertificateAwardType): string {
  return value === 'CERTIFICATE' ? '证书' : '奖项'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    certificates.value = await listCertificateAwards()
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

function openEdit(row: CertificateAward): void {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    type: row.type,
    issuer: row.issuer,
    issueDate: row.issueDate,
    description: row.description ?? '',
  })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  saving.value = true
  const payload: CertificateAwardRequest = {
    name: form.name.trim(),
    type: form.type,
    issuer: form.issuer.trim(),
    issueDate: form.issueDate,
    description: form.description?.trim() || undefined,
  }
  try {
    const id = editingId.value
    const isCreate = id === null
    if (isCreate) {
      await createCertificateAward(payload)
    } else {
      await updateCertificateAward(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '记录已新增' : '记录已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

async function remove(row: CertificateAward): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${row.name}”吗？`, '确认删除', { type: 'warning' })
    await deleteCertificateAward(row.id)
    ElMessage.success('记录已删除')
    await load()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

onMounted(load)
</script>

<style scoped>
.page-shell { max-width: 1160px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.table-wrap { min-height: 180px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
@media (max-width: 640px) { .page-heading { align-items: flex-start; flex-direction: column; } .form-grid { grid-template-columns: 1fr; } }
</style>
