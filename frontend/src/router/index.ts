import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

/**
 * 路由按六大板块划分，一个板块一个路由、一个独立视图文件。
 *
 * 这样两位开发者各自负责的板块不会互相改动同一个文件；
 * 板块内的子页面（如学业发展下的证书列表）在对应视图下追加子路由即可。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { title: '板块总览' },
    },
    {
      path: '/sections/basic',
      name: 'section-basic',
      component: () => import('@/views/sections/BasicSectionView.vue'),
      meta: { title: '基本信息' },
    },
    {
      path: '/sections/academic',
      name: 'section-academic',
      component: () => import('@/views/sections/AcademicSectionView.vue'),
      meta: { title: '学业发展' },
    },
    {
      path: '/sections/interest',
      name: 'section-interest',
      component: () => import('@/views/sections/InterestSectionView.vue'),
      meta: { title: '兴趣特长' },
    },
    {
      path: '/sections/life',
      name: 'section-life',
      component: () => import('@/views/sections/LifeSectionView.vue'),
      meta: { title: '生活心理' },
    },
    {
      path: '/sections/career',
      name: 'section-career',
      component: () => import('@/views/sections/CareerSectionView.vue'),
      meta: { title: '职业规划' },
    },
    {
      path: '/sections/mentor',
      name: 'section-mentor',
      component: () => import('@/views/sections/MentorSectionView.vue'),
      meta: { title: '导师寄语' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 学生成长档案平台` : '学生成长档案平台'
})

export default router
