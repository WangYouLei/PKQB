import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
    {
      path: '/',
      component: () => import('@/views/Layout.vue'),
      redirect: '/users',
      children: [
        { path: 'users', name: 'AdminUsers', component: () => import('@/views/AdminUsers.vue') },
        { path: 'classes', name: 'AdminClasses', component: () => import('@/views/AdminClasses.vue') }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  const publicPages = ['/login']
  if (publicPages.includes(to.path)) {
    auth.isLoggedIn ? next('/') : next()
  } else {
    auth.isLoggedIn ? next() : next('/login')
  }
})

export default router
