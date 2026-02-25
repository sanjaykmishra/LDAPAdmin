import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ── Public ─────────────────────────────────────────────────────────────
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },

    // ── App shell ───────────────────────────────────────────────────────────
    {
      path: '/',
      component: () => import('@/components/AppLayout.vue'),
      children: [
        { path: '', redirect: '/directories' },

        // Directories
        {
          path: 'directories',
          name: 'directories',
          component: () => import('@/views/directories/DirectoryListView.vue'),
        },

        // Users
        {
          path: 'directories/:dirId/users',
          name: 'users',
          component: () => import('@/views/users/UserListView.vue'),
        },

        // Groups
        {
          path: 'directories/:dirId/groups',
          name: 'groups',
          component: () => import('@/views/groups/GroupListView.vue'),
        },

        // Audit log
        {
          path: 'directories/:dirId/audit',
          name: 'audit',
          component: () => import('@/views/audit/AuditLogView.vue'),
        },

        // Bulk CSV
        {
          path: 'directories/:dirId/bulk',
          name: 'bulk',
          component: () => import('@/views/bulk/BulkView.vue'),
        },

        // Reports
        {
          path: 'directories/:dirId/reports',
          name: 'reports',
          component: () => import('@/views/reports/ReportJobsView.vue'),
        },

        // Attribute Profiles
        {
          path: 'directories/:dirId/profiles',
          name: 'profiles',
          component: () => import('@/views/profiles/AttributeProfilesView.vue'),
        },

        // Schema Browser
        {
          path: 'directories/:dirId/schema',
          name: 'schema',
          component: () => import('@/views/schema/SchemaView.vue'),
        },

        // Settings
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/settings/SettingsView.vue'),
        },

        // Superadmin
        {
          path: 'superadmin',
          name: 'superadmin',
          component: () => import('@/views/superadmin/SuperadminView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'superadmin/tenants',
          name: 'tenants',
          component: () => import('@/views/superadmin/TenantsView.vue'),
          meta: { requiresSuperadmin: true },
        },
      ],
    },

    // Catch-all
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresSuperadmin && !auth.isSuperadmin) {
    return { path: '/directories' }
  }
})

export default router
