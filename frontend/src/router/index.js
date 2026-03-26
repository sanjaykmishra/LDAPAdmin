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

    // ── Setup Wizard (own layout, outside AppLayout) ──────────────────────
    {
      path: '/setup',
      name: 'setup',
      component: () => import('@/views/SetupWizardView.vue'),
    },

    // ── App shell ───────────────────────────────────────────────────────────
    {
      path: '/',
      component: () => import('@/components/AppLayout.vue'),
      children: [
        { path: '', name: 'home', redirect: () => '/superadmin/dashboard' },
        { path: 'no-access', name: 'noAccess', component: { template: '<div />' } },

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

        // Compliance Reports (PDF)
        {
          path: 'directories/:dirId/compliance-reports',
          name: 'complianceReports',
          component: () => import('@/views/reports/ComplianceReportsView.vue'),
        },

        // Lifecycle Playbooks
        {
          path: 'directories/:dirId/playbooks',
          name: 'playbooks',
          component: () => import('@/views/playbooks/PlaybooksView.vue'),
        },

        // Pending Approvals
        {
          path: 'directories/:dirId/approvals',
          name: 'approvals',
          component: () => import('@/views/approvals/PendingApprovalsView.vue'),
        },

        // SoD Policies
        {
          path: 'directories/:dirId/sod-policies',
          name: 'sodPolicies',
          component: () => import('@/views/sodPolicies/SodPoliciesView.vue'),
        },
        {
          path: 'directories/:dirId/sod-policies/new',
          name: 'sodPolicyCreate',
          component: () => import('@/views/sodPolicies/SodPolicyFormView.vue'),
        },
        {
          path: 'directories/:dirId/sod-policies/:policyId/edit',
          name: 'sodPolicyEdit',
          component: () => import('@/views/sodPolicies/SodPolicyFormView.vue'),
        },
        {
          path: 'directories/:dirId/sod-violations',
          name: 'sodViolations',
          component: () => import('@/views/sodPolicies/SodViolationsView.vue'),
        },

        // Access Drift Detection
        {
          path: 'directories/:dirId/access-drift',
          name: 'accessDrift',
          component: () => import('@/views/drift/AccessDriftView.vue'),
        },

        // Campaign Templates
        {
          path: 'directories/:dirId/campaign-templates',
          name: 'campaignTemplates',
          component: () => import('@/views/accessReviews/CampaignTemplatesView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'directories/:dirId/campaign-templates/new',
          name: 'campaignTemplateCreate',
          component: () => import('@/views/accessReviews/CampaignTemplateFormView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'directories/:dirId/campaign-templates/:templateId/edit',
          name: 'campaignTemplateEdit',
          component: () => import('@/views/accessReviews/CampaignTemplateFormView.vue'),
          meta: { requiresSuperadmin: true },
        },

        // HR Integration
        {
          path: 'directories/:dirId/hr',
          name: 'hrConnection',
          component: () => import('@/views/hr/HrConnectionView.vue'),
        },
        {
          path: 'directories/:dirId/hr/employees',
          name: 'hrEmployees',
          component: () => import('@/views/hr/HrEmployeesView.vue'),
        },

        // Access Reviews
        {
          path: 'directories/:dirId/access-reviews',
          name: 'accessReviews',
          component: () => import('@/views/accessReviews/CampaignListView.vue'),
        },
        {
          path: 'directories/:dirId/access-reviews/cross-campaign-report',
          name: 'crossCampaignReport',
          component: () => import('@/views/accessReviews/CrossCampaignReportView.vue'),
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
          path: 'superadmin/dashboard',
          name: 'dashboard',
          component: () => import('@/views/superadmin/DashboardView.vue'),
          meta: { requiresSuperadmin: true },
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
          path: 'superadmin/audit-log',
          name: 'superadminAuditLog',
          component: () => import('@/views/audit/AuditLogView.vue'),
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

        // Superadmin directory-scoped wrappers (with directory picker)
        {
          path: 'superadmin/sod-policies',
          component: () => import('@/views/superadmin/DirectoryScopedWrapper.vue'),
          props: { defaultChild: 'sodPolicies' },
          meta: { requiresSuperadmin: true },
          children: [
            { path: ':dirId', name: 'superadminSodPolicies', component: () => import('@/views/sodPolicies/SodPoliciesView.vue') },
          ],
        },
        {
          path: 'superadmin/sod-violations',
          component: () => import('@/views/superadmin/DirectoryScopedWrapper.vue'),
          props: { defaultChild: 'sodViolations' },
          meta: { requiresSuperadmin: true },
          children: [
            { path: ':dirId', name: 'superadminSodViolations', component: () => import('@/views/sodPolicies/SodViolationsView.vue') },
          ],
        },
        {
          path: 'superadmin/access-drift',
          component: () => import('@/views/superadmin/DirectoryScopedWrapper.vue'),
          props: { defaultChild: 'accessDrift' },
          meta: { requiresSuperadmin: true },
          children: [
            { path: ':dirId', name: 'superadminAccessDrift', component: () => import('@/views/drift/AccessDriftView.vue') },
          ],
        },
        {
          path: 'superadmin/reports',
          name: 'superadminReports',
          component: () => import('@/views/reports/ReportJobsView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'superadmin/audit-reports',
          name: 'superadminAuditReports',
          component: () => import('@/views/reports/AuditReportsView.vue'),
          meta: { requiresSuperadmin: true },
        },
        {
          path: 'superadmin/compliance-reports',
          component: () => import('@/views/superadmin/DirectoryScopedWrapper.vue'),
          props: { defaultChild: 'complianceReports' },
          meta: { requiresSuperadmin: true },
          children: [
            { path: ':dirId', name: 'superadminComplianceReports', component: () => import('@/views/reports/ComplianceReportsView.vue') },
          ],
        },
        {
          path: 'superadmin/playbooks',
          component: () => import('@/views/superadmin/DirectoryScopedWrapper.vue'),
          props: { defaultChild: 'playbooks' },
          meta: { requiresSuperadmin: true },
          children: [
            { path: ':dirId', name: 'superadminPlaybooks', component: () => import('@/views/playbooks/PlaybooksView.vue') },
          ],
        },
        {
          path: 'superadmin/hr',
          component: () => import('@/views/superadmin/DirectoryScopedWrapper.vue'),
          props: { defaultChild: 'hrConnection' },
          meta: { requiresSuperadmin: true },
          children: [
            { path: ':dirId', name: 'superadminHr', component: () => import('@/views/hr/HrConnectionView.vue') },
          ],
        },
      ],
    },

    // ── Self-service portal (authenticated — requires SELF_SERVICE principal) ──
    {
      path: '/self-service',
      component: () => import('@/layouts/SelfServiceLayout.vue'),
      children: [
        {
          path: 'profile',
          name: 'selfServiceProfile',
          component: () => import('@/views/selfservice/SelfServiceProfileView.vue'),
          meta: { requiresSelfService: true },
        },
        {
          path: 'password',
          name: 'selfServicePassword',
          component: () => import('@/views/selfservice/SelfServicePasswordView.vue'),
          meta: { requiresSelfService: true },
        },
        {
          path: 'groups',
          name: 'selfServiceGroups',
          component: () => import('@/views/selfservice/SelfServiceGroupsView.vue'),
          meta: { requiresSelfService: true },
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
  if (auth.isSuperadmin) return '/superadmin/dashboard'
  try {
    const { data } = await myProfiles()
    if (data.length) return `/directories/${data[0].directoryId}/users`
  } catch (e) { console.warn('Failed to resolve home path:', e) }
  return '/no-access'
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  // Restore session from httpOnly cookie on first navigation after page load
  await auth.init()

  // Public routes — no auth needed
  if (to.meta.public) return

  // Self-service protected routes
  if (to.meta.requiresSelfService) {
    if (!auth.isLoggedIn) {
      return { name: 'selfServiceLogin', query: { redirect: to.fullPath } }
    }
    if (!auth.isSelfService) {
      return { path: await resolveHomePath(auth) }
    }
    return
  }

  // Admin routes — require logged in non-self-service user
  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (auth.isSelfService) {
    // Self-service users cannot access admin routes
    return { name: 'selfServiceProfile' }
  }
  if (to.meta.requiresSuperadmin && !auth.isSuperadmin) {
    return { path: await resolveHomePath(auth) }
  }
  // Redirect superadmin to setup wizard if first-run setup is pending
  if (auth.isSuperadmin && auth.setupPending && to.name !== 'setup') {
    return { name: 'setup' }
  }
})

export default router
