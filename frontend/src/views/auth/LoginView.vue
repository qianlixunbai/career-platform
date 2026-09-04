<template>
  <div class="auth-page">
    <section class="auth-card" aria-labelledby="login-title">
      <div class="auth-brand">
        <div class="auth-brand__mark">CP</div>
        <div class="auth-brand__title">Career Platform</div>
      </div>

      <h1 id="login-title" class="auth-card__title">欢迎回来</h1>
      <p class="auth-card__description">登录你的职业成长工作台，继续推进下一步目标。</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="form.username"
            autocomplete="username"
            placeholder="请输入用户名"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            autocomplete="current-password"
            placeholder="请输入密码（至少 6 位）"
            type="password"
            show-password
          />
        </el-form-item>
        <el-button class="auth-submit" type="primary" native-type="submit" :loading="submitting">
          登录
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </el-form>

      <p class="auth-card__footer">
        还没有账号？<RouterLink :to="{ name: 'register' }">创建账号</RouterLink>
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'

import { login } from '@/api/auth'
import type { LoginRequest } from '@/types/auth'
import { setSession } from '@/utils/auth'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive<LoginRequest>({
  username: '',
  password: '',
})

const rules: FormRules<LoginRequest> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 50, message: '用户名长度不能超过 50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 72, message: '密码长度必须在 6 到 72 个字符之间', trigger: 'blur' },
  ],
}

onMounted(() => {
  const username = route.query.username
  if (typeof username === 'string') {
    form.username = username
  }
})

async function submit(): Promise<void> {
  if (!formRef.value || submitting.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    const session = await login(form)
    setSession(session)

    const redirect = route.query.redirect
    const destination =
      typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
        ? redirect
        : '/'
    await router.replace(destination)
  } catch {
    // The shared Axios response interceptor has already shown the API error.
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-submit {
  width: 100%;
  margin-top: 8px;
}

.auth-submit :deep(.el-icon) {
  margin-left: 6px;
}
</style>
