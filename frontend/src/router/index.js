import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { myRealms } from '@/api/auth'

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
        { path: '', name: 'home', redirect: () => '/superadmin/directories' },

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

        // Realms
        {
          path: 'directories/:dirId/realms',
          name: 'realms',
          component: () => import('@/views/realms/RealmsView.vue'),
          meta: { requiresSuperadmin: true },
        },

        // Schema Browser (superadmin)
        {
          path: 'superadmin/schema',
          name: 'schema',
          component: () => import('@/views/schema/SchemaView.vue'),
          meta: { requiresSuperadmin: true },
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
          redirect: '/superadmin/admins',
        },
        {
          path: 'superadmin/admins',
          name: 'adminUsers',
          component: () => import('@/views/superadmin/AdminUsersView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'superadmin/directories',
          name: 'manageDirectories',
          component: () => import('@/views/superadmin/DirectoriesManageView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'superadmin/audit-sources',
          name: 'auditSources',
          component: () => import('@/views/superadmin/AuditSourcesView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'superadmin/user-forms',
          name: 'userForms',
          component: () => import('@/views/userForms/UserFormsView.vue'),
          meta: { requiresSuperadmin: true },
        },
      ],
    },

    // Catch-all
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

/**
 * Resolve the default landing path for the current user.
 * Superadmins land on the directories management page;
 * regular admins land on the user list for their first authorized realm.
 */
async function resolveHomePath(auth) {
  if (auth.isSuperadmin) return '/superadmin/directories'
  try {
    const { data } = await myRealms()
    if (data.length) return `/directories/${data[0].directoryId}/users`
  } catch { /* fall through */ }
  return '/login'
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  // Restore session from httpOnly cookie on first navigation after page load
  await auth.init()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresSuperadmin && !auth.isSuperadmin) {
    return { path: await resolveHomePath(auth) }
  }
})

export default router
