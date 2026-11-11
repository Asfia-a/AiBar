import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../utils/request'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { guest: true },
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', name: 'Home', component: () => import('../views/Home.vue') },
      { path: 'worlds', name: 'WorldPlaza', component: () => import('../views/WorldPlaza.vue') },
      { path: 'worlds/:id', name: 'WorldDetail', component: () => import('../views/WorldDetail.vue') },
      { path: 'my-worlds', name: 'MyWorldTrees', component: () => import('../views/MyWorldTrees.vue'), meta: { requiresAuth: true } },
      { path: 'character/:id', name: 'CharacterDetail', component: () => import('../views/CharacterDetail.vue') },
      { path: 'character/:id/worldbook', name: 'WorldBook', component: () => import('../views/WorldBook.vue'), meta: { requiresAuth: true } },
      { path: 'chat/:sessionId?', name: 'Chat', component: () => import('../views/Chat.vue'), meta: { requiresAuth: true, fullBleed: true } },
      { path: 'create', name: 'CreateCharacter', component: () => import('../views/CreateCharacter.vue'), meta: { requiresAuth: true } },
      { path: 'edit/:id', name: 'EditCharacter', component: () => import('../views/CreateCharacter.vue'), meta: { requiresAuth: true } },
      { path: 'favorites', name: 'Favorites', component: () => import('../views/Favorites.vue'), meta: { requiresAuth: true } },
      { path: 'mine', name: 'MyCharacters', component: () => import('../views/MyCharacters.vue'), meta: { requiresAuth: true } },
      { path: 'workshop', name: 'Workshop', component: () => import('../views/Workshop.vue'), meta: { requiresAuth: true } },
      { path: 'inspiration', name: 'Inspiration', component: () => import('../views/Inspiration.vue'), meta: { requiresAuth: true } },
      { path: 'narrative', name: 'Narrative', component: () => import('../views/Narrative.vue'), meta: { requiresAuth: true } },
      { path: 'community', name: 'Community', component: () => import('../views/Community.vue'), meta: { requiresAuth: true } },
      { path: 'metrics', name: 'Metrics', component: () => import('../views/Metrics.vue'), meta: { requiresAuth: true } },
      { path: 'world-tree', name: 'WorldTree', component: () => import('../views/WorldTree.vue'), meta: { requiresAuth: true } },
      { path: 'profile', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { requiresAuth: true } },
      { path: 'checkin', name: 'CheckIn', component: () => import('../views/CheckIn.vue'), meta: { requiresAuth: true } },
      { path: 'search', name: 'Search', component: () => import('../views/Search.vue') },
      { path: 'notifications', name: 'Notifications', component: () => import('../views/Notifications.vue'), meta: { requiresAuth: true } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to, _from, next) => {
  const token = getToken()
  if (to.meta.requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.meta.guest && token && to.name === 'Login') {
    next({ name: 'Home' })
  } else {
    next()
  }
})

export default router
