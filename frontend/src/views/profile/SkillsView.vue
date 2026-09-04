<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>技能</h2>
            <p>从全局技能库关联技能，并单独维护自己的掌握程度。</p>
          </div>
          <div class="heading-actions">
            <el-button @click="openSkillCreate">新建技能</el-button>
            <el-button type="primary" @click="openCreate">关联技能</el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && userSkills.length === 0" description="还没有关联技能" />
        <el-table v-else :data="userSkills" stripe>
          <el-table-column prop="skillName" label="技能" min-width="180" />
          <el-table-column label="掌握程度" width="180">
            <template #default="{ row }">
              <el-tag :type="proficiencyTag(row.proficiency)">{{ proficiencyLabel(row.proficiency) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">修改熟练度</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '关联技能' : '修改熟练度'" width="440px" destroy-on-close>
      <el-form :model="form" label-position="top">
        <el-form-item v-if="editingId === null" label="技能" required>
          <el-select v-model="form.skillId" placeholder="选择技能" class="full-width" filterable>
            <el-option v-for="skill in skills" :key="skill.id" :label="skill.name" :value="skill.id" />
          </el-select>
          <span v-if="skills.length === 0" class="form-tip">技能库为空，请先新建一个技能。</span>
        </el-form-item>
        <el-form-item label="掌握程度" required>
          <el-select v-model="form.proficiency" placeholder="选择掌握程度" class="full-width">
            <el-option v-for="option in proficiencyOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="skillDialogVisible" title="新建全局技能" width="420px" destroy-on-close>
      <el-form :model="skillForm" label-position="top" @submit.prevent="saveSkill">
        <el-form-item label="技能名称" required>
          <el-input v-model="skillForm.name" maxlength="100" show-word-limit placeholder="例如：TypeScript" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="skillDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSkill" @click="saveSkill">创建并选择</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSkill,
  createUserSkill,
  deleteUserSkill,
  listSkills,
  listUserSkills,
  updateUserSkill,
} from '@/api/profile'
import type { Proficiency, Skill, UserSkill, UserSkillCreateRequest, UserSkillUpdateRequest } from '@/types/profile'

const proficiencyOptions: Array<{ value: Proficiency; label: string }> = [
  { value: 'BEGINNER', label: '入门' },
  { value: 'FAMILIAR', label: '熟悉' },
  { value: 'PROFICIENT', label: '熟练' },
]

const skills = ref<Skill[]>([])
const userSkills = ref<UserSkill[]>([])
const loading = ref(false)
const saving = ref(false)
const savingSkill = ref(false)
const dialogVisible = ref(false)
const skillDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<UserSkillCreateRequest>({ skillId: 0, proficiency: 'BEGINNER' })
const skillForm = reactive({ name: '' })

function proficiencyLabel(value: Proficiency): string {
  return proficiencyOptions.find((option) => option.value === value)?.label ?? value
}

function proficiencyTag(value: Proficiency): 'success' | 'warning' | 'info' {
  if (value === 'PROFICIENT') return 'success'
  if (value === 'FAMILIAR') return 'warning'
  return 'info'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [availableSkills, linkedSkills] = await Promise.all([listSkills(), listUserSkills()])
    skills.value = availableSkills
    userSkills.value = linkedSkills
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.skillId = skills.value[0]?.id ?? 0
  form.proficiency = 'BEGINNER'
  dialogVisible.value = true
}

function openEdit(row: UserSkill): void {
  editingId.value = row.id
  form.skillId = row.skillId
  form.proficiency = row.proficiency
  dialogVisible.value = true
}

function openSkillCreate(): void {
  skillForm.name = ''
  skillDialogVisible.value = true
}

async function saveSkill(): Promise<void> {
  if (!skillForm.name.trim()) return
  savingSkill.value = true
  try {
    const created = await createSkill(skillForm.name.trim())
    skills.value = [...skills.value, created].sort((left, right) => left.name.localeCompare(right.name))
    form.skillId = created.id
    skillDialogVisible.value = false
    dialogVisible.value = true
    ElMessage.success('技能已创建，请设置掌握程度')
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    savingSkill.value = false
  }
}

async function save(): Promise<void> {
  saving.value = true
  try {
    const id = editingId.value
    if (id === null) {
      await createUserSkill({ skillId: form.skillId, proficiency: form.proficiency })
    } else {
      const payload: UserSkillUpdateRequest = { proficiency: form.proficiency }
      await updateUserSkill(id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(id === null ? '技能已关联' : '掌握程度已更新')
    await load()
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

async function remove(row: UserSkill): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定移除“${row.skillName}”吗？这不会删除全局技能。`, '确认移除', { type: 'warning' })
    await deleteUserSkill(row.id)
    ElMessage.success('技能已移除')
    await load()
  } catch {
    // Cancellation and API failures require no additional page-level message.
  }
}

onMounted(load)
</script>

<style scoped>
.page-shell { max-width: 960px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.heading-actions { display: flex; gap: 8px; }
.table-wrap { min-height: 180px; }
.full-width { width: 100%; }
.form-tip { display: block; margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; }
@media (max-width: 640px) { .page-heading { align-items: flex-start; flex-direction: column; } .heading-actions { width: 100%; } }
</style>
