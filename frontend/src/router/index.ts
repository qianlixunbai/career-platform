import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { isAuthenticated } from '@/utils/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/ProfileView.vue'),
      },
      {
        path: 'profile/education',
        name: 'profile-education',
        component: () => import('@/views/profile/EducationView.vue'),
      },
      {
        path: 'profile/skills',
        name: 'profile-skills',
        component: () => import('@/views/profile/SkillsView.vue'),
      },
      {
        path: 'profile/projects',
        name: 'profile-projects',
        component: () => import('@/views/profile/ProjectsView.vue'),
      },
      {
        path: 'profile/internships',
        name: 'profile-internships',
        component: () => import('@/views/profile/InternshipsView.vue'),
      },
      {
        path: 'profile/certificates',
        name: 'profile-certificates',
        component: () => import('@/views/profile/CertificatesView.vue'),
      },
      {
        path: 'career/goals',
        name: 'career-goals',
        component: () => import('@/views/career/CareerGoalsView.vue'),
      },
      {
        path: 'career/companies',
        name: 'career-companies',
        component: () => import('@/views/career/CompaniesView.vue'),
      },
      {
        path: 'career/jobs',
        name: 'career-jobs',
        component: () => import('@/views/career/JobsView.vue'),
      },
      {
        path: 'career/job-discovery',
        name: 'career-job-discovery',
        component: () => import('@/views/career/JobDiscoveryView.vue'),
      },
      {
        path: 'career/jobs/:jobId',
        name: 'career-job-detail',
        component: () => import('@/views/career/JobDetailView.vue'),
      },
      {
        path: 'learning/plans',
        name: 'learning-plans',
        component: () => import('@/views/learning/LearningPlansView.vue'),
      },
      {
        path: 'learning/plans/:planId',
        name: 'learning-plan-detail',
        component: () => import('@/views/learning/LearningPlanDetailView.vue'),
      },
      {
        path: 'learning/resources',
        name: 'learning-resources',
        component: () => import('@/views/learning/LearningResourcesView.vue'),
      },
      {
        path: 'resumes',
        name: 'resumes',
        component: () => import('@/views/resume/ResumesView.vue'),
      },
      {
        path: 'resumes/:resumeId',
        name: 'resume-detail',
        component: () => import('@/views/resume/ResumeDetailView.vue'),
      },
      {
        path: 'applications',
        name: 'applications',
        component: () => import('@/views/application/ApplicationsView.vue'),
      },
      {
        path: 'applications/:applicationId',
        name: 'application-detail',
        component: () => import('@/views/application/ApplicationDetailView.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const authenticated = isAuthenticated()

  if (to.matched.some((record) => record.meta.requiresAuth) && !authenticated) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    }
  }

  if (to.matched.some((record) => record.meta.guestOnly) && authenticated) {
    return { name: 'dashboard' }
  }

  return true
})

export default router
