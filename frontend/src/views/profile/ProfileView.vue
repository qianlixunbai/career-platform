<template>
  <div class="profile-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>基本资料</h2>
            <p>维护个人信息，这些内容会用于职业目标和简历。</p>
          </div>
          <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
        </div>
      </template>

      <el-skeleton v-if="loading" :rows="7" animated />
      <el-form v-else ref="formRef" :model="form" label-position="top" class="profile-form" @submit.prevent="saveProfile">
        <div class="profile-form-grid">
          <el-form-item label="姓名">
            <el-input v-model="form.fullName" maxlength="100" show-word-limit placeholder="例如：张三" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" maxlength="50" placeholder="请输入常用手机号" />
          </el-form-item>
          <el-form-item label="当前城市">
            <el-input v-model="form.currentCity" maxlength="100" placeholder="例如：上海" />
          </el-form-item>
          <el-form-item label="邮箱地址">
            <el-input v-model="form.email" maxlength="254" placeholder="例如：zhangsan@example.com" />
          </el-form-item>
          <el-form-item label="个人网站">
            <el-input v-model="form.personalWebsite" maxlength="500" placeholder="https://..." />
          </el-form-item>
          <el-form-item label="GitHub 地址">
            <el-input v-model="form.githubUrl" maxlength="500" placeholder="https://github.com/..." />
          </el-form-item>
        </div>

      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getProfile, updateProfile } from '@/api/profile'
import type { ProfileUpdateRequest } from '@/types/profile'

const loading = ref(true)
const saving = ref(false)
const form = reactive<ProfileUpdateRequest>({
  fullName: '',
  phone: '',
  email: '',
  currentCity: '',
  personalWebsite: '',
  githubUrl: '',
})

async function loadProfile(): Promise<void> {
  loading.value = true
  try {
    const profile = await getProfile()
    form.fullName = profile.fullName ?? ''
    form.phone = profile.phone ?? ''
    form.email = profile.email ?? ''
    form.currentCity = profile.currentCity ?? ''
    form.personalWebsite = profile.personalWebsite ?? ''
    form.githubUrl = profile.githubUrl ?? ''
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    loading.value = false
  }
}

async function saveProfile(): Promise<void> {
  saving.value = true
  try {
    await updateProfile({
      fullName: form.fullName?.trim(),
      phone: form.phone?.trim(),
      email: form.email?.trim(),
      currentCity: form.currentCity?.trim(),
      personalWebsite: form.personalWebsite?.trim(),
      githubUrl: form.githubUrl?.trim(),
    })
    ElMessage.success('资料已保存')
  } catch {
    // The response interceptor presents the structured API error.
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page { max-width: 960px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h2 { margin: 0; font-size: 20px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.profile-form { padding: 8px 4px 0; }
.profile-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px 24px; }
@media (max-width: 640px) { .profile-form-grid { grid-template-columns: 1fr; } .page-heading { align-items: flex-start; flex-direction: column; } }
</style>
