import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '数据概览' },
      },
      {
        path: 'character-audit',
        name: 'CharacterAudit',
        component: () => import('../views/CharacterAudit.vue'),
        meta: { title: '角色审核' },
      },
      {
        path: 'user-manage',
        name: 'UserManage',
        component: () => import('../views/UserManage.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'content-audit',
        name: 'ContentAudit',
        component: () => import('../views/ContentAudit.vue'),
        meta: { title: '违规日志' },
      },
      {
        path: 'model-config',
        name: 'ModelConfig',
        component: () => import('../views/ModelConfig.vue'),
        meta: { title: '模型配置' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  if (to.meta.public) {
    if (userStore.isLoggedIn && userStore.isAdmin) {
      next('/dashboard')
    } else {
      next()
    }
    return
  }
  if (!userStore.isLoggedIn || !userStore.isAdmin) {
    next('/login')
    return
  }
  next()
})

export default router
