import { createRouter, createWebHistory, RouteRecordRaw, RouteRecordNormalized, RouteLocationNormalized, NavigationGuardNext } from 'vue-router'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import Profile from '../views/Profile.vue'
import MainLayout from '../layout/MainLayout.vue'
import { useUserStore } from '../store/user'
import { fetchMenuTree } from '../api/client'

const staticRoutes: RouteRecordRaw[] = [
  { path: '/login', name: 'Login', component: Login },
  {
    path: '/',
    component: MainLayout,
    children: [
    { path: '', redirect: '/dashboard' },
  { path: 'dashboard', name: 'Dashboard', component: Dashboard, meta: { requiresAuth: true, title: 'message.dashboard' } },
  { path: 'profile', name: 'Profile', component: Profile, meta: { requiresAuth: true, title: 'user.profile' } },
  { path: 'system/users', name: 'UserList', component: () => import('../views/system/UserList.vue'), alias: 'sys/users', meta: { requiresAuth: true, title: 'message.user_management' } },
  { path: 'system/users/create', name: 'CreateUser', component: () => import('../views/system/CreateUser.vue'), meta: { requiresAuth: true, title: 'message.create_user' } },
  { path: 'system/users/:id/edit', name: 'EditUser', component: () => import('../views/system/EditUser.vue'), meta: { requiresAuth: true, title: 'message.edit_user' } },
  { path: 'sys/roles/create', name: 'CreateRole', component: () => import('../views/system/CreateRole.vue'), meta: { requiresAuth: true, title: 'message.create_role' } },
  { path: 'sys/roles/:id/edit', name: 'EditRole', component: () => import('../views/system/EditRole.vue'), meta: { requiresAuth: true, title: 'message.edit_role' } },
      { path: 'sys/roles', name: 'SysRolesAlias', component: () => import('../views/system/RoleList.vue'), meta: { requiresAuth: true, title: 'message.role_management' } },
      { path: 'sys/menus', name: 'SysMenusAlias', component: () => import('../views/system/MenuList.vue'), meta: { requiresAuth: true, title: 'message.menu_management' } },
      { path: 'sys/audit', name: 'SysAuditAlias', component: () => import('../views/system/AuditLogList.vue'), meta: { requiresAuth: true, title: 'message.audit_log' } },
      { path: 'org/dept', name: 'OrgDeptAlias', component: () => import('../views/system/DeptList.vue'), meta: { requiresAuth: true, title: 'message.dept_management' } },
      { path: 'org/post', name: 'OrgPostAlias', component: () => import('../views/system/PostList.vue'), meta: { requiresAuth: true, title: 'message.post_management' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes
})

// Convert backend MenuNode[] to RouteRecordRaw[]
function menuNodesToRoutes(nodes: any[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  nodes.forEach(n => {
    // choose component by known keywords in path
    const p = (n.path || `m/${n.id}`).replace(/^\//, '')
    let componentLoader: any = () => import('../views/MenuPage.vue')
    if (/users?/i.test(p)) componentLoader = () => import('../views/system/UserList.vue')
    else if (/roles?/i.test(p)) componentLoader = () => import('../views/system/RoleList.vue')
    else if (/menus?/i.test(p)) componentLoader = () => import('../views/system/MenuList.vue')
    else if (/depts?|department/i.test(p)) componentLoader = () => import('../views/system/DeptList.vue')
    else if (/posts?|positions?/i.test(p)) componentLoader = () => import('../views/system/PostList.vue')
    else if (/audit|log/i.test(p)) componentLoader = () => import('../views/system/AuditLogList.vue')

    const childrenRoutes = (n.children && n.children.length) ? menuNodesToRoutes(n.children) : undefined
  const route: any = {
      path: p, // child path relative to parent
      name: n.name || `m_${n.id}`,
      meta: { requiresAuth: true, permission: n.permission, title: n.name },
      component: componentLoader,
      children: childrenRoutes
    }
    routes.push(route)
  })
  return routes
}

let dynamicLoaded = false
router.beforeEach(async (to: RouteLocationNormalized, from: RouteLocationNormalized, next: NavigationGuardNext) => {
  const token = localStorage.getItem('token')
  console.log('路由守卫检查:', { path: to.path, token: !!token })
  if (to.path === '/login') return next()
  if (!token) {
    console.log('无 token，跳转到登录页')
    return next({ path: '/login' })
  }
  console.log('有 token，继续导航')
  // load dynamic routes once after login or on page refresh
  if (!dynamicLoaded) {
    try {
      const store = useUserStore()
      // prefer store.loadInitialData which will fetch user info and menus and populate store
      if (typeof store.loadInitialData === 'function') {
        await store.loadInitialData()
      }
      const res = (store as any).menus || (await fetchMenuTree()) || []
      if (res && Array.isArray(res) && res.length) {
        const dyn = menuNodesToRoutes(res)
        // register each as child of root layout, but avoid duplicates
        dyn.forEach(r => {
          const name = (r.name || '') as string
          if (name) {
            if (!router.hasRoute(name)) {
              router.addRoute('/', r)
            }
          } else {
            // fallback: check by full path to avoid duplicate anonymous routes
            const fullPath = '/' + (r.path || '')
            const exists = router.getRoutes().some((rt: RouteRecordNormalized) => rt.path === fullPath)
            if (!exists) router.addRoute('/', r)
          }
        })
      }
    } catch (e) {
      console.warn((typeof (window as any)?.$t === 'function' ? (window as any).$t('message.load_menus_failed') : 'Failed to load menus'), e)
    }
    dynamicLoaded = true
    // retry navigation so newly added routes are taken into account
    return next({ ...to, replace: true })
  }
  next()
})

export default router
