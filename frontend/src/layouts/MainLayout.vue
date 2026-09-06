<template>
  <el-container class="app-shell">
    <el-aside class="app-sidebar" width="248px">
      <div class="app-sidebar__brand">
        <div class="app-sidebar__mark">CP</div>
        <div>
          <div class="app-sidebar__name">Career Platform</div>
          <div class="app-sidebar__caption">职业成长工作台</div>
        </div>
      </div>

      <el-scrollbar class="app-sidebar__scroll">
        <el-menu
          :default-active="activePath"
          :router="true"
          class="app-menu"
          background-color="transparent"
          text-color="#b5c7d1"
          active-text-color="#ffffff"
        >
          <el-menu-item index="/">
            <el-icon><House /></el-icon>
            <span>工作台</span>
          </el-menu-item>

          <el-menu-item-group v-for="group in navigationGroups" :key="group.label" :title="group.label">
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </el-menu-item-group>
        </el-menu>
      </el-scrollbar>

      <div class="app-sidebar__footer">
        <span class="app-sidebar__footer-dot" />
        <span>专注当下，持续成长</span>
      </div>
    </el-aside>

    <el-container class="app-main">
      <el-header class="app-header" height="72px">
        <div>
          <div class="app-header__eyebrow">PERSONAL CAREER OS</div>
          <div class="app-header__title">职业成长工作台</div>
        </div>

        <div class="app-header__actions">
          <div class="app-header__user">
            <el-avatar :size="36" class="app-header__avatar">{{ userInitial }}</el-avatar>
            <div class="app-header__user-copy">
              <span class="app-header__username">{{ session?.username ?? '用户' }}</span>
              <span class="app-header__status">{{ statusLabel }}</span>
            </div>
          </div>
          <el-button text class="app-header__logout" @click="logout">
            <el-icon><SwitchButton /></el-icon>
            退出
          </el-button>
        </div>
      </el-header>

      <el-main class="app-content">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Aim,
  Briefcase,
  Collection,
  Files,
  Folder,
  House,
  Medal,
  Notebook,
  OfficeBuilding,
  Promotion,
  Reading,
  Search,
  School,
  Star,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { clearSession, getSession } from '@/utils/auth'

interface NavigationItem {
  label: string
  path: string
  icon: Component
}

interface NavigationGroup {
  label: string
  items: NavigationItem[]
}

const route = useRoute()
const router = useRouter()
const session = getSession()

const navigationGroups: NavigationGroup[] = [
  {
    label: '基础档案',
    items: [
      { label: '基本资料', path: '/profile', icon: User },
      { label: '教育经历', path: '/profile/education', icon: School },
      { label: '技能', path: '/profile/skills', icon: Star },
      { label: '项目经历', path: '/profile/projects', icon: Folder },
      { label: '实习经历', path: '/profile/internships', icon: Briefcase },
      { label: '证书 / 获奖', path: '/profile/certificates', icon: Medal },
    ],
  },
  {
    label: '职业探索',
    items: [
      { label: '职业目标', path: '/career/goals', icon: Aim },
      { label: '公司', path: '/career/companies', icon: OfficeBuilding },
      { label: '我的岗位', path: '/career/jobs', icon: Collection },
      { label: '岗位发现', path: '/career/job-discovery', icon: Search },
    ],
  },
  {
    label: '学习提升',
    items: [
      { label: '学习计划', path: '/learning/plans', icon: Reading },
      { label: '笔记 / 资料', path: '/learning/resources', icon: Notebook },
    ],
  },
  {
    label: '简历',
    items: [{ label: '简历管理', path: '/resumes', icon: Files }],
  },
  {
    label: '求职管理',
    items: [{ label: '投递管理', path: '/applications', icon: Promotion }],
  },
]

const activePath = computed(() => {
  if (route.path.startsWith('/career/jobs/')) {
    return '/career/jobs'
  }
  if (route.path.startsWith('/learning/plans/')) {
    return '/learning/plans'
  }
  if (route.path.startsWith('/resumes/')) {
    return '/resumes'
  }
  if (route.path.startsWith('/applications/')) {
    return '/applications'
  }
  return route.path
})

const userInitial = computed(() => session?.username?.slice(0, 1).toUpperCase() ?? 'U')
const statusLabel = computed(() => (session?.status === 'DISABLED' ? '已停用' : '已登录'))

async function logout(): Promise<void> {
  clearSession()
  ElMessage.success('已安全退出')
  await router.replace({ name: 'login' })
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow: hidden;
  color: #b5c7d1;
  background: #123c55;
}

.app-sidebar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 92px;
  padding: 22px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.app-sidebar__mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 11px;
  color: #123c55;
  background: #f3c8a9;
  font-size: 13px;
  font-weight: 900;
  letter-spacing: 0.04em;
}

.app-sidebar__name {
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.01em;
}

.app-sidebar__caption {
  margin-top: 4px;
  color: #96b0bd;
  font-size: 12px;
}

.app-sidebar__scroll {
  flex: 1;
  padding: 14px 12px;
}

.app-menu {
  border-right: 0;
}

.app-menu :deep(.el-menu-item-group__title) {
  padding: 17px 14px 7px;
  color: #7795a3;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.app-menu :deep(.el-menu-item) {
  height: 42px;
  margin: 2px 0;
  border-radius: 9px;
  line-height: 42px;
}

.app-menu :deep(.el-menu-item:hover) {
  color: #fff;
  background: rgba(255, 255, 255, 0.09);
}

.app-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: #236584;
  box-shadow: 0 6px 16px rgba(5, 27, 41, 0.15);
}

.app-menu :deep(.el-icon) {
  margin-right: 10px;
  font-size: 17px;
}

.app-sidebar__footer {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 54px;
  padding: 0 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  color: #88a5b2;
  font-size: 12px;
}

.app-sidebar__footer-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f3c8a9;
}

.app-main {
  min-width: 0;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid var(--cp-border);
  background: rgba(255, 255, 255, 0.94);
}

.app-header__eyebrow {
  color: #80909a;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.13em;
}

.app-header__title {
  margin-top: 4px;
  color: var(--cp-primary);
  font-size: 16px;
  font-weight: 800;
}

.app-header__actions,
.app-header__user {
  display: flex;
  align-items: center;
}

.app-header__actions {
  gap: 20px;
}

.app-header__user {
  gap: 10px;
}

.app-header__avatar {
  color: #174a68;
  background: #e5f0f4;
  font-weight: 800;
}

.app-header__user-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.app-header__username {
  color: var(--cp-ink);
  font-size: 13px;
  font-weight: 750;
}

.app-header__status {
  color: var(--cp-muted);
  font-size: 11px;
}

.app-header__logout {
  color: var(--cp-muted);
}

.app-header__logout:hover {
  color: var(--cp-primary);
  background: var(--cp-primary-light);
}

.app-content {
  padding: 0;
  overflow: auto;
  background: #f4f7f9;
}

@media (max-width: 860px) {
  .app-sidebar {
    width: 72px !important;
  }

  .app-sidebar__brand {
    justify-content: center;
    padding: 18px 12px;
  }

  .app-sidebar__brand > div:not(.app-sidebar__mark),
  .app-sidebar__caption,
  .app-sidebar__footer,
  .app-menu :deep(.el-menu-item-group__title),
  .app-menu :deep(.el-menu-item span) {
    display: none;
  }

  .app-sidebar__scroll {
    padding: 14px 10px;
  }

  .app-menu :deep(.el-menu-item) {
    justify-content: center;
    padding: 0 !important;
  }

  .app-menu :deep(.el-icon) {
    margin-right: 0;
  }
}

@media (max-width: 560px) {
  .app-header {
    height: auto !important;
    min-height: 72px;
    padding: 12px 16px;
  }

  .app-header__eyebrow,
  .app-header__status,
  .app-header__logout :deep(.el-icon) {
    display: none;
  }

  .app-header__actions {
    gap: 8px;
  }

  .app-header__logout {
    padding: 8px;
  }
}
</style>
