<template>
  <div class="auth-page">
    <section class="auth-card" aria-labelledby="register-title">
      <div class="auth-brand">
        <div class="auth-brand__mark">CP</div>
        <div class="auth-brand__title">Career Platform</div>
      </div>

      <h1 id="register-title" class="auth-card__title">创建你的账号</h1>
      <p class="auth-card__description">建立一份可持续迭代的职业档案，从今天开始积累。</p>

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
            autocomplete="new-password"
            placeholder="请输入密码（至少 6 位）"
            type="password"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            type="password"
            show-password
          />
        </el-form-item>
        <el-button class="auth-submit" type="primary" native-type="submit" :loading="submitting">
          创建账号
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </el-form>

      <p class="auth-card__footer">
        已有账号？<RouterLink :to="{ name: 'login' }">返回登录</RouterLink>
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules, FormItemRule } from 'element-plus'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

import { register } from '@/api/auth'
import type { RegisterRequest } from '@/types/auth'

interface RegisterForm extends RegisterRequest {
  confirmPassword: string
}

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive<RegisterForm>({
  username: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword: FormItemRule['validator'] = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules<RegisterForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 50, message: '用户名长度不能超过 50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 72, message: '密码长度必须在 6 到 72 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }],
}

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
    await register({ username: form.username, password: form.password })
    ElMessage.success('注册成功，请登录')
    await router.replace({ name: 'login', query: { username: form.username } })
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
