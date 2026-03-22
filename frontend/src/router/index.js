import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { myProfiles } from '@/api/auth'

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
    {
      path: '/oidc/callback',
      name: 'oidcCallback',
      component: () => import('@/views/OidcCallbackView.vue'),
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

        // Pending Approvals
        {
          path: 'directories/:dirId/approvals',
          name: 'approvals',
          component: () => import('@/views/approvals/PendingApprovalsView.vue'),
        },

        // Access Reviews
        {
          path: 'directories/:dirId/access-reviews',
          name: 'accessReviews',
          component: () => import('@/views/accessReviews/CampaignListView.vue'),
        },
        {
          path: 'directories/:dirId/access-reviews/new',
          name: 'accessReviewCreate',
          component: () => import('@/views/accessReviews/CampaignCreateView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'directories/:dirId/access-reviews/:campaignId',
          name: 'accessReviewDetail',
          component: () => import('@/views/accessReviews/CampaignDetailView.vue'),
        },
        {
          path: 'directories/:dirId/access-reviews/:campaignId/groups/:groupId',
          name: 'accessReviewDecisions',
          component: () => import('@/views/accessReviews/ReviewDecisionsView.vue'),
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
          path: 'superadmin/profiles',
          name: 'profiles',
          component: () => import('@/views/profiles/SuperadminProfilesView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'superadmin/browser',
          name: 'directoryBrowser',
          component: () => import('@/views/superadmin/DirectoryBrowserView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'superadmin/search',
          name: 'ldapSearch',
          component: () => import('@/views/superadmin/LdapSearchView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'superadmin/integrity',
          name: 'integrityCheck',
          component: () => import('@/views/superadmin/IntegrityCheckView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'superadmin/access-reviews',
          name: 'superadminAccessReviews',
          component: () => import('@/views/superadmin/AccessReviewsView.vue'),
          meta: { requiresSuperadmin: true },
        },
      ],
    },

    // ── Self-service portal (authenticated) ──────────────────────────────
    {
      path: '/self-service',
      component: () => import('@/layouts/SelfServiceLayout.vue'),
      children: [
        {
          path: 'profile',
          name: 'selfServiceProfile',
          component: () => import('@/views/selfservice/SelfServiceProfileView.vue'),
          meta: { public: true },
        },
        {
          path: 'password',
          name: 'selfServicePassword',
          component: () => import('@/views/selfservice/SelfServicePasswordView.vue'),
          meta: { public: true },
        },
        {
          path: 'groups',
          name: 'selfServiceGroups',
          component: () => import('@/views/selfservice/SelfServiceGroupsView.vue'),
          meta: { public: true },
        },
      ],
    },

    // ── Self-service public pages ──────────────────────────────────────────
    {
      path: '/self-service/login',
      name: 'selfServiceLogin',
      component: () => import('@/views/selfservice/SelfServiceLoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/selfservice/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/register/verify/:token',
      name: 'verifyEmail',
      component: () => import('@/views/selfservice/VerifyEmailView.vue'),
      meta: { public: true },
    },
    {
      path: '/register/status/:requestId',
      name: 'registrationStatus',
      component: () => import('@/views/selfservice/RegistrationStatusView.vue'),
      meta: { public: true },
    },

    // Catch-all
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

/**
 * Resolve the default landing path for the current user.
 * Superadmins land on the directories management page;
 * regular admins land on the user list for their first authorized profile's directory.
 */
async function resolveHomePath(auth) {
  if (auth.isSuperadmin) return '/superadmin/directories'
  try {
    const { data } = await myProfiles()
    if (data.length) return `/directories/${data[0].directoryId}/users`
  } catch (e) { console.warn('Failed to resolve home path:', e) }
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
