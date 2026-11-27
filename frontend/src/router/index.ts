import { createRouter, createWebHistory } from 'vue-router'
import { getUser, isLoggedIn } from '@/auth/session'
import HomeView from '@/views/HomeView.vue'

/**
 * 路由按六大板块划分，一个板块一个路由、一个独立视图文件。
 *
 * <p>这样两位开发者各自负责的板块不会互相改动同一个文件；
 * 板块内的子页面（如学业发展下的证书列表）在对应视图下追加子路由即可。</p>
 *
 * <p>{@code meta.public} 标记匿名可访问的页面（目前只有登录页）；
 * {@code meta.roles} 声明角色要求，登录校验与角色校验都在全局守卫里做一次，
 * 页面组件不再各自判断，避免漏判。</p>
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { title: '总览' },
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
      path: '/review',
      name: 'review-list',
      component: () => import('@/views/review/ReviewListView.vue'),
      meta: { title: '审核工作台', roles: ['COU', 'ADMIN'] },
    },
    {
      path: '/review/:code/:studentId',
      name: 'review-detail',
      component: () => import('@/views/review/ReviewDetailView.vue'),
      meta: { title: '审核详情', roles: ['COU', 'ADMIN'] },
    },
    {
      path: '/enroll',
      name: 'enroll',
      component: () => import('@/views/EnrollView.vue'),
      // 建档是辅导员的业务写操作，管理员为只读授权，不在角色范围内
      meta: { title: '批量建档', roles: ['COU'] },
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
      meta: { title: '没有访问权限' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在', public: true },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

/**
 * 全局守卫：未登录拦截 + 角色拦截。
 *
 * <p>未登录时带上原地址跳登录页，登录后回到原处；已登录时访问登录页直接回总览。
 * 角色不匹配跳到 /forbidden 而不是静默回首页——否则用户会以为页面不存在。</p>
 */
router.beforeEach((to) => {
  const loggedIn = isLoggedIn()

  if (to.meta.public !== true && !loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && loggedIn) {
    return { name: 'home' }
  }

  const roles = to.meta.roles
  if (Array.isArray(roles) && roles.length > 0) {
    const user = getUser()
    if (user == null || !roles.includes(user.role)) {
      return { name: 'forbidden' }
    }
  }

  /**
   * 总览是教师/管理员的数据看板入口，学生侧边栏里没有这一项，
   * 直接访问或登录后默认回跳时落到「基本信息」，避免停在无菜单高亮的页面上。
   *
   * <p>口径已确认：学生首页就是「基本信息」，不是 403、也不是给他单独开一个总览。
   * 登录页默认回跳 '/' 时同样走这里，因此登录后 URL 会稳定落在 /sections/basic。</p>
   */
  if (to.name === 'home' && getUser()?.role === 'STU') {
    return { name: 'section-basic' }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 学生成长档案平台` : '学生成长档案平台'
})

export default router
