import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
    { path: '/register', name: 'Register', component: () => import('@/views/Register.vue') },
    {
      path: '/',
      component: () => import('@/views/Home.vue'),
      redirect: '/my-files',
      children: [
        { path: 'my-files', name: 'MyFiles', component: () => import('@/views/MyFiles.vue') },
        { path: 'class-files', name: 'ClassFiles', component: () => import('@/views/ClassFiles.vue') },
        { path: 'ai-chat', name: 'AIChat', component: () => import('@/views/AIChat.vue') },
        { path: 'rag-chat', name: 'RAGChat', component: () => import('@/views/RAGChat.vue') },
        { path: 'kb-upload', name: 'KBUpload', component: () => import('@/views/KBUpload.vue') },
        { path: 'rubric-upload', name: 'RubricUpload', component: () => import('@/views/RubricUpload.vue') },
        { path: 'settings', name: 'Settings', component: () => import('@/views/Settings.vue') },
      ],
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  const publicPages = ['/login', '/register']
  if (!publicPages.includes(to.path) && !token) {
    next('/login')
  } else if (publicPages.includes(to.path) && token) {
    next('/')
  } else {
    next()
  }
})

export default router
