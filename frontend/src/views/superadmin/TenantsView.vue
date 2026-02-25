<template>
  <div class="p-6 max-w-6xl">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Tenants</h1>
      <button @click="openCreateTenant" class="btn-primary">+ New Tenant</button>
    </div>

    <!-- Tenants list -->
    <div class="bg-white border border-gray-200 rounded-xl overflow-hidden mb-6">
      <div v-if="loading" class="p-8 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="tenants.length === 0" class="p-8 text-center text-gray-400 text-sm">No tenants yet.</div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Name</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Slug</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Max Dirs</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Features</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr
            v-for="t in tenants" :key="t.id"
            class="hover:bg-gray-50 cursor-pointer"
            @click="selectTenant(t)"
            :class="{ 'bg-blue-50': selectedTenant?.id === t.id }"
          >
            <td class="px-4 py-3 font-medium text-gray-900">{{ t.name }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ t.slug }}</td>
            <td class="px-4 py-3 text-gray-600">{{ t.maxDirectories ?? '—' }}</td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1">
                <span v-for="f in (t.enabledFeatures ?? [])" :key="f" class="text-xs bg-blue-50 text-blue-700 rounded px-1.5 py-0.5">{{ f }}</span>
              </div>
            </td>
            <td class="px-4 py-3 text-right">
              <button @click.stop="openEditTenant(t)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-3">Edit</button>
              <button @click.stop="confirmDeleteTenant(t)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Tenant detail panel -->
    <div v-if="selectedTenant" class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <!-- Tab bar -->
      <div class="flex border-b border-gray-200 px-2 pt-2">
        <button
          v-for="tab in detailTabs" :key="tab.key"
          @click="activeTab = tab.key; onTabSwitch(tab.key)"
          :class="activeTab === tab.key
            ? 'border-b-2 border-blue-600 text-blue-600'
            : 'text-gray-500 hover:text-gray-700 border-b-2 border-transparent'"
          class="px-4 py-2.5 text-sm font-medium transition-colors"
        >{{ tab.label }}</button>
      </div>

      <!-- ── Admins tab ─────────────────────────────────────────────────── -->
      <div v-if="activeTab === 'admins'" class="p-4">
        <div class="flex justify-between items-center mb-3">
          <span class="text-sm font-semibold text-gray-700">Admins — {{ selectedTenant.name }}</span>
          <button @click="openCreateAdmin" class="btn-sm-primary">+ Add Admin</button>
        </div>
        <div v-if="adminsLoading" class="text-sm text-gray-400 text-center py-6">Loading…</div>
        <div v-else-if="admins.length === 0" class="text-sm text-gray-400 text-center py-6">No admins.</div>
        <table v-else class="w-full text-sm">
          <thead class="bg-gray-50 border-b border-gray-100">
            <tr>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Username</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Email</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Active</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-50">
            <tr v-for="a in admins" :key="a.id" class="hover:bg-gray-50">
              <td class="px-3 py-2 font-medium text-gray-900">{{ a.username }}</td>
              <td class="px-3 py-2 text-gray-600">{{ a.email ?? '—' }}</td>
              <td class="px-3 py-2">
                <span :class="a.active !== false ? 'text-green-600' : 'text-gray-400'" class="text-xs font-medium">
                  {{ a.active !== false ? 'Active' : 'Inactive' }}
                </span>
              </td>
              <td class="px-3 py-2 text-right">
                <button @click="openPermissions(a)" class="text-purple-600 hover:text-purple-800 text-xs font-medium mr-2">Permissions</button>
                <button @click="openEditAdmin(a)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-2">Edit</button>
                <button @click="confirmDeleteAdmin(a)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ── Directories tab ───────────────────────────────────────────── -->
      <div v-if="activeTab === 'directories'" class="p-4">
        <div class="flex justify-between items-center mb-3">
          <span class="text-sm font-semibold text-gray-700">LDAP Directories — {{ selectedTenant.name }}</span>
          <button @click="openCreateDir" class="btn-sm-primary">+ Add Directory</button>
        </div>
        <div v-if="directoriesLoading" class="text-sm text-gray-400 text-center py-6">Loading…</div>
        <div v-else-if="tenantDirectories.length === 0" class="text-sm text-gray-400 text-center py-6">No directories configured.</div>
        <table v-else class="w-full text-sm">
          <thead class="bg-gray-50 border-b border-gray-100">
            <tr>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Name</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Host</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Port</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">SSL</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Base DN</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-50">
            <tr v-for="d in tenantDirectories" :key="d.id" class="hover:bg-gray-50">
              <td class="px-3 py-2 font-medium text-gray-900">{{ d.displayName }}</td>
              <td class="px-3 py-2 text-gray-600 font-mono text-xs">{{ d.host }}</td>
              <td class="px-3 py-2 text-gray-600">{{ d.port }}</td>
              <td class="px-3 py-2 text-gray-600 text-xs">{{ d.sslMode }}</td>
              <td class="px-3 py-2 text-gray-600 font-mono text-xs">{{ d.baseDn }}</td>
              <td class="px-3 py-2 text-right whitespace-nowrap">
                <button @click="doEvictPool(d)" class="text-amber-600 hover:text-amber-800 text-xs font-medium mr-2">Evict Pool</button>
                <button @click="openEditDir(d)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-2">Edit</button>
                <button @click="confirmDeleteDir(d)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ── Auth Config tab ────────────────────────────────────────────── -->
      <div v-if="activeTab === 'authConfig'" class="p-4">
        <div v-if="authConfigLoading" class="text-sm text-gray-400 text-center py-6">Loading…</div>
        <form v-else @submit.prevent="saveAuthConfig" class="space-y-4 max-w-xl">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Auth Type</label>
            <select v-model="authConfigForm.authType" class="input w-full">
              <option value="LDAP_BIND">LDAP Bind</option>
              <option value="SAML">SAML / SSO</option>
            </select>
          </div>

          <!-- LDAP Bind config -->
          <template v-if="authConfigForm.authType === 'LDAP_BIND'">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">LDAP Directory</label>
              <select v-model="authConfigForm.ldapDirectoryId" class="input w-full">
                <option value="">— select —</option>
                <option v-for="d in tenantDirectories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
              </select>
            </div>
            <FormField label="Bind DN Pattern" v-model="authConfigForm.ldapBindDnPattern" placeholder="uid={username},ou=people,dc=example,dc=com" />
          </template>

          <!-- SAML config -->
          <template v-else>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">IdP Type</label>
              <select v-model="authConfigForm.samlIdpType" class="input w-full">
                <option value="OKTA">Okta</option>
                <option value="IBM_VERIFY">IBM Verify</option>
                <option value="GENERIC">Generic</option>
              </select>
            </div>
            <FormField label="IdP Metadata URL" v-model="authConfigForm.samlIdpMetadataUrl" placeholder="https://idp.example.com/metadata" />
            <FormField label="SP Entity ID" v-model="authConfigForm.samlSpEntityId" />
            <FormField label="SP ACS URL" v-model="authConfigForm.samlSpAcsUrl" placeholder="https://app.example.com/saml/acs" />
            <div class="grid grid-cols-3 gap-3">
              <FormField label="Username Attr" v-model="authConfigForm.samlAttributeUsername" placeholder="email" />
              <FormField label="Email Attr" v-model="authConfigForm.samlAttributeEmail" placeholder="email" />
              <FormField label="Display Name Attr" v-model="authConfigForm.samlAttributeDisplayName" placeholder="displayName" />
            </div>
          </template>

          <div class="flex justify-end pt-1">
            <button type="submit" :disabled="authConfigSaving" class="btn-primary">
              {{ authConfigSaving ? 'Saving…' : 'Save Auth Config' }}
            </button>
          </div>
        </form>
      </div>

      <!-- ── Audit Sources tab ──────────────────────────────────────────── -->
      <div v-if="activeTab === 'auditSources'" class="p-4">
        <div class="flex justify-between items-center mb-3">
          <span class="text-sm font-semibold text-gray-700">Audit Data Sources</span>
          <button @click="openCreateSource" class="btn-sm-primary">+ Add Source</button>
        </div>
        <div v-if="sourcesLoading" class="text-sm text-gray-400 text-center py-6">Loading…</div>
        <div v-else-if="auditSources.length === 0" class="text-sm text-gray-400 text-center py-6">No audit sources configured.</div>
        <table v-else class="w-full text-sm">
          <thead class="bg-gray-50 border-b border-gray-100">
            <tr>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Name</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Host</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Port</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">SSL</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Enabled</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-50">
            <tr v-for="s in auditSources" :key="s.id" class="hover:bg-gray-50">
              <td class="px-3 py-2 font-medium text-gray-900">{{ s.displayName }}</td>
              <td class="px-3 py-2 text-gray-600 font-mono text-xs">{{ s.host }}</td>
              <td class="px-3 py-2 text-gray-600">{{ s.port }}</td>
              <td class="px-3 py-2 text-gray-600 text-xs">{{ s.sslMode }}</td>
              <td class="px-3 py-2">
                <span :class="s.enabled ? 'text-green-600' : 'text-gray-400'" class="text-xs font-medium">
                  {{ s.enabled ? 'Yes' : 'No' }}
                </span>
              </td>
              <td class="px-3 py-2 text-right">
                <button @click="openEditSource(s)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-2">Edit</button>
                <button @click="confirmDeleteSource(s)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ── Tenant modal ───────────────────────────────────────────────────── -->
    <AppModal v-if="showTenantModal" :title="editTenant ? 'Edit Tenant' : 'New Tenant'" size="lg" @close="showTenantModal = false">
      <form @submit.prevent="saveTenant" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Name" v-model="tenantForm.name" required />
          <FormField label="Slug" v-model="tenantForm.slug" placeholder="acme" required />
          <FormField label="Max Directories" v-model.number="tenantForm.maxDirectories" type="number" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Enabled Features</label>
          <div class="flex flex-wrap gap-x-4 gap-y-2">
            <label v-for="f in allFeatures" :key="f" class="flex items-center gap-1 text-sm">
              <input type="checkbox" :value="f" v-model="tenantForm.enabledFeatures" class="rounded" />
              {{ f }}
            </label>
          </div>
        </div>
        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showTenantModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="tenantSaving" class="btn-primary">{{ tenantSaving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <!-- ── Admin modal ───────────────────────────────────────────────────── -->
    <AppModal v-if="showAdminModal" :title="editAdmin ? 'Edit Admin' : 'New Admin'" size="sm" @close="showAdminModal = false">
      <form @submit.prevent="saveAdmin" class="space-y-4">
        <FormField label="Username" v-model="adminForm.username" required />
        <FormField label="Email" v-model="adminForm.email" />
        <FormField v-if="!editAdmin" label="Password" v-model="adminForm.password" type="password" required />
        <FormField v-else label="New Password" v-model="adminForm.password" type="password" placeholder="Leave blank to keep" />
        <div class="flex items-center gap-2">
          <input type="checkbox" id="adminActive" v-model="adminForm.active" class="rounded" />
          <label for="adminActive" class="text-sm text-gray-700">Active</label>
        </div>
        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showAdminModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="adminSaving" class="btn-primary">{{ adminSaving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <!-- ── Permissions modal ─────────────────────────────────────────────── -->
    <AppModal v-if="showPermissionsModal" :title="`Permissions — ${permissionsAdmin?.username}`" size="xl" @close="showPermissionsModal = false">
      <div v-if="permissionsLoading" class="text-sm text-gray-400 text-center py-6">Loading…</div>
      <div v-else class="space-y-6">

        <!-- Directory Roles -->
        <div>
          <h3 class="text-sm font-semibold text-gray-700 mb-3">Directory Roles</h3>
          <div v-if="permissions.directoryRoles?.length" class="mb-3">
            <table class="w-full text-sm">
              <thead class="bg-gray-50">
                <tr>
                  <th class="px-3 py-2 text-left font-medium text-gray-500">Directory</th>
                  <th class="px-3 py-2 text-left font-medium text-gray-500">Role</th>
                  <th class="px-3 py-2 text-left font-medium text-gray-500">Branch Restrictions</th>
                  <th class="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr v-for="r in permissions.directoryRoles" :key="r.id" class="hover:bg-gray-50">
                  <td class="px-3 py-2 font-medium text-gray-800">{{ r.directoryDisplayName }}</td>
                  <td class="px-3 py-2">
                    <span :class="r.baseRole === 'ADMIN' ? 'text-blue-700 bg-blue-50' : 'text-gray-600 bg-gray-100'" class="text-xs rounded px-2 py-0.5 font-medium">
                      {{ r.baseRole }}
                    </span>
                  </td>
                  <td class="px-3 py-2">
                    <div v-if="permissions.branchRestrictions?.[r.directoryId]?.length" class="text-xs text-gray-600 font-mono">
                      {{ permissions.branchRestrictions[r.directoryId].join(', ') }}
                    </div>
                    <div v-else>
                      <input
                        :value="branchDnInputs[r.directoryId] ?? ''"
                        @input="branchDnInputs[r.directoryId] = $event.target.value"
                        placeholder="Comma-separated DNs (empty = full access)"
                        class="input text-xs w-full"
                      />
                    </div>
                  </td>
                  <td class="px-3 py-2 text-right">
                    <button @click="saveBranchRestrictions(r.directoryId)" class="text-green-600 hover:text-green-800 text-xs font-medium mr-2">Save DNs</button>
                    <button @click="doRemoveDirectoryRole(r.directoryId)" class="text-red-500 hover:text-red-700 text-xs font-medium">Remove</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="text-xs text-gray-400 mb-3">No directory roles assigned.</div>
          <div class="flex gap-2 items-end">
            <div class="flex-1">
              <label class="block text-xs font-medium text-gray-600 mb-1">Directory</label>
              <select v-model="dirRoleForm.directoryId" class="input w-full text-sm">
                <option value="">— select —</option>
                <option v-for="d in tenantDirectories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
              </select>
            </div>
            <div class="w-32">
              <label class="block text-xs font-medium text-gray-600 mb-1">Role</label>
              <select v-model="dirRoleForm.baseRole" class="input w-full text-sm">
                <option value="ADMIN">Admin</option>
                <option value="READ_ONLY">Read Only</option>
              </select>
            </div>
            <button @click="doAddDirectoryRole" :disabled="!dirRoleForm.directoryId" class="btn-sm-primary mb-0.5">Add</button>
          </div>
        </div>

        <!-- Feature Overrides -->
        <div>
          <h3 class="text-sm font-semibold text-gray-700 mb-3">Feature Overrides</h3>
          <p class="text-xs text-gray-500 mb-3">Leave "Inherit" to use the tenant default. Override to explicitly allow or deny for this admin.</p>
          <div class="grid grid-cols-2 gap-2">
            <div v-for="fk in allFeatureKeys" :key="fk" class="flex items-center justify-between bg-gray-50 rounded-lg px-3 py-2">
              <span class="text-xs font-mono text-gray-700">{{ fk }}</span>
              <select
                :value="featureOverrideValue(fk)"
                @change="setFeatureOverride(fk, $event.target.value)"
                class="input text-xs py-0.5 px-1 ml-2"
              >
                <option value="inherit">Inherit</option>
                <option value="allow">Allow</option>
                <option value="deny">Deny</option>
              </select>
            </div>
          </div>
          <div class="flex justify-end mt-3">
            <button @click="saveFeatureOverrides" :disabled="featureSaving" class="btn-primary">
              {{ featureSaving ? 'Saving…' : 'Save Feature Overrides' }}
            </button>
          </div>
        </div>
      </div>
    </AppModal>

    <!-- ── Audit Source modal ────────────────────────────────────────────── -->
    <AppModal v-if="showSourceModal" :title="editSource ? 'Edit Audit Source' : 'New Audit Source'" size="lg" @close="showSourceModal = false">
      <form @submit.prevent="saveSource" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Display Name" v-model="sourceForm.displayName" required />
          <FormField label="Host" v-model="sourceForm.host" required />
          <FormField label="Port" v-model.number="sourceForm.port" type="number" placeholder="389" />
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">SSL Mode</label>
            <select v-model="sourceForm.sslMode" class="input w-full">
              <option value="NONE">None</option>
              <option value="LDAPS">LDAPS</option>
              <option value="STARTTLS">STARTTLS</option>
            </select>
          </div>
          <FormField label="Bind DN" v-model="sourceForm.bindDn" required />
          <FormField label="Bind Password" v-model="sourceForm.bindPassword" type="password" :placeholder="editSource ? 'Leave blank to keep' : ''" />
          <FormField label="Changelog Base DN" v-model="sourceForm.changelogBaseDn" placeholder="cn=changelog" required />
          <FormField label="Branch Filter DN" v-model="sourceForm.branchFilterDn" placeholder="optional" />
        </div>
        <div class="flex items-center gap-2">
          <input type="checkbox" id="srcEnabled" v-model="sourceForm.enabled" class="rounded" />
          <label for="srcEnabled" class="text-sm text-gray-700">Enabled</label>
        </div>
        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showSourceModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="sourceSaving" class="btn-primary">{{ sourceSaving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <!-- ── Directory modal ──────────────────────────────────────────────── -->
    <AppModal v-if="showDirModal" :title="editDir ? 'Edit Directory' : 'New Directory'" size="lg" @close="showDirModal = false">
      <form @submit.prevent="saveDir" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Display Name" v-model="dirForm.displayName" required />
          <FormField label="Host" v-model="dirForm.host" required />
          <FormField label="Port" v-model.number="dirForm.port" type="number" placeholder="389" />
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">SSL Mode</label>
            <select v-model="dirForm.sslMode" class="input w-full">
              <option value="NONE">None</option>
              <option value="LDAPS">LDAPS</option>
              <option value="STARTTLS">STARTTLS</option>
            </select>
          </div>
          <FormField label="Bind DN" v-model="dirForm.bindDn" required placeholder="cn=admin,dc=example,dc=com" />
          <FormField label="Bind Password" v-model="dirForm.bindPassword" type="password" :placeholder="editDir ? 'Leave blank to keep' : ''" />
          <div class="col-span-2">
            <FormField label="Base DN" v-model="dirForm.baseDn" required placeholder="dc=example,dc=com" />
          </div>
        </div>
        <div class="flex items-center gap-2">
          <input type="checkbox" id="dirTrustCerts" v-model="dirForm.trustAllCerts" class="rounded" />
          <label for="dirTrustCerts" class="text-sm text-gray-700">Trust all certificates (insecure)</label>
        </div>
        <!-- Test connection result -->
        <div v-if="testDirResult" :class="testDirResult.success ? 'bg-green-50 border-green-200 text-green-800' : 'bg-red-50 border-red-200 text-red-700'" class="border rounded-lg px-3 py-2 text-sm">
          {{ testDirResult.success ? '✓ ' : '✗ ' }}{{ testDirResult.message }}
        </div>
        <div class="flex justify-between items-center pt-2">
          <button type="button" @click="doTestDir" :disabled="testDirLoading" class="btn-secondary text-sm">
            {{ testDirLoading ? 'Testing…' : 'Test Connection' }}
          </button>
          <div class="flex gap-2">
            <button type="button" @click="showDirModal = false" class="btn-secondary">Cancel</button>
            <button type="submit" :disabled="dirSaving" class="btn-primary">{{ dirSaving ? 'Saving…' : 'Save' }}</button>
          </div>
        </div>
      </form>
    </AppModal>

    <!-- Delete confirms -->
    <ConfirmDialog v-if="deleteTenantTarget" :message="`Delete tenant '${deleteTenantTarget.name}'?`" @confirm="doDeleteTenant" @cancel="deleteTenantTarget = null" />
    <ConfirmDialog v-if="deleteAdminTarget" :message="`Remove admin '${deleteAdminTarget.username}'?`" @confirm="doDeleteAdmin" @cancel="deleteAdminTarget = null" />
    <ConfirmDialog v-if="deleteDirTarget" :message="`Delete directory '${deleteDirTarget.displayName}'?`" @confirm="doDeleteDir" @cancel="deleteDirTarget = null" />
    <ConfirmDialog v-if="deleteSourceTarget" :message="`Delete source '${deleteSourceTarget.displayName}'?`" @confirm="doDeleteSource" @cancel="deleteSourceTarget = null" />
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import {
  listTenants, createTenant, updateTenant, deleteTenant,
  listAdmins, createAdmin, updateAdmin, deleteAdmin,
  getTenantAuthConfig, updateTenantAuthConfig,
  listTenantDirectories, createTenantDirectory, updateTenantDirectory,
  deleteTenantDirectory, testTenantConnection, evictTenantPool,
} from '@/api/superadmin'
import {
  getPermissions, setDirectoryRole, removeDirectoryRole,
  setBranchRestrictions, setFeaturePermissions, clearFeaturePermission,
} from '@/api/adminPermissions'
import { listAuditSources, createAuditSource, updateAuditSource, deleteAuditSource } from '@/api/auditDataSources'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const notif = useNotificationStore()

// ── Tenants ────────────────────────────────────────────────────────────────────

const loading        = ref(false)
const tenants        = ref([])
const selectedTenant = ref(null)
const activeTab      = ref('admins')

const detailTabs = [
  { key: 'admins',       label: 'Admins' },
  { key: 'directories',  label: 'Directories' },
  { key: 'authConfig',   label: 'Auth Config' },
  { key: 'auditSources', label: 'Audit Sources' },
]

const allFeatures = [
  'REPORTS_RUN', 'REPORTS_EXPORT', 'REPORTS_SCHEDULE',
  'AUDIT_LOG', 'BULK_IMPORT', 'BULK_EXPORT', 'ATTRIBUTE_PROFILES',
  'USER_CREATE', 'USER_EDIT', 'USER_DELETE', 'USER_ENABLE_DISABLE',
  'USER_MOVE', 'GROUP_MANAGE_MEMBERS', 'GROUP_CREATE_DELETE',
]

const showTenantModal  = ref(false)
const editTenant       = ref(null)
const tenantSaving     = ref(false)
const deleteTenantTarget = ref(null)
const tenantForm = ref({ name: '', slug: '', maxDirectories: null, enabledFeatures: [] })

async function loadTenants() {
  loading.value = true
  try {
    const { data } = await listTenants()
    tenants.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}
onMounted(loadTenants)

function openCreateTenant() {
  editTenant.value = null
  tenantForm.value = { name: '', slug: '', maxDirectories: null, enabledFeatures: [] }
  showTenantModal.value = true
}
function openEditTenant(t) {
  editTenant.value = t
  tenantForm.value = { name: t.name, slug: t.slug, maxDirectories: t.maxDirectories ?? null, enabledFeatures: [...(t.enabledFeatures ?? [])] }
  showTenantModal.value = true
}
async function saveTenant() {
  tenantSaving.value = true
  try {
    editTenant.value
      ? await updateTenant(editTenant.value.id, tenantForm.value)
      : await createTenant(tenantForm.value)
    notif.success(editTenant.value ? 'Tenant updated' : 'Tenant created')
    showTenantModal.value = false
    await loadTenants()
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { tenantSaving.value = false }
}
function confirmDeleteTenant(t) { deleteTenantTarget.value = t }
async function doDeleteTenant() {
  try {
    await deleteTenant(deleteTenantTarget.value.id)
    notif.success('Tenant deleted')
    if (selectedTenant.value?.id === deleteTenantTarget.value.id) { selectedTenant.value = null }
    deleteTenantTarget.value = null
    await loadTenants()
  } catch (e) { notif.error(e.response?.data?.detail || e.message); deleteTenantTarget.value = null }
}

async function selectTenant(t) {
  if (selectedTenant.value?.id === t.id) { selectedTenant.value = null; return }
  selectedTenant.value = t
  activeTab.value = 'admins'
  await Promise.all([loadAdmins(), loadTenantDirectories()])
}

function onTabSwitch(tab) {
  if (tab === 'authConfig')   loadAuthConfig()
  if (tab === 'auditSources') loadAuditSources()
  if (tab === 'directories')  loadTenantDirectories()
}

// ── Tenant Directories ────────────────────────────────────────────────────────

const tenantDirectories    = ref([])
const directoriesLoading   = ref(false)
const showDirModal         = ref(false)
const editDir              = ref(null)
const dirSaving            = ref(false)
const deleteDirTarget      = ref(null)
const testDirLoading       = ref(false)
const testDirResult        = ref(null)  // { success, message }
const dirForm = ref({
  displayName: '', host: '', port: 389, sslMode: 'NONE',
  trustAllCerts: false, bindDn: '', bindPassword: '', baseDn: '',
})

async function loadTenantDirectories() {
  directoriesLoading.value = true
  try {
    const { data } = await listTenantDirectories(selectedTenant.value.id)
    tenantDirectories.value = data
  } catch { tenantDirectories.value = [] }
  finally { directoriesLoading.value = false }
}

function openCreateDir() {
  editDir.value = null
  dirForm.value = { displayName: '', host: '', port: 389, sslMode: 'NONE', trustAllCerts: false, bindDn: '', bindPassword: '', baseDn: '' }
  testDirResult.value = null
  showDirModal.value = true
}
function openEditDir(d) {
  editDir.value = d
  dirForm.value = { displayName: d.displayName, host: d.host, port: d.port, sslMode: d.sslMode, trustAllCerts: d.trustAllCerts ?? false, bindDn: d.bindDn, bindPassword: '', baseDn: d.baseDn ?? '' }
  testDirResult.value = null
  showDirModal.value = true
}
async function saveDir() {
  dirSaving.value = true
  try {
    const payload = { ...dirForm.value }
    if (editDir.value && !payload.bindPassword) delete payload.bindPassword
    editDir.value
      ? await updateTenantDirectory(selectedTenant.value.id, editDir.value.id, payload)
      : await createTenantDirectory(selectedTenant.value.id, payload)
    notif.success(editDir.value ? 'Directory updated' : 'Directory created')
    showDirModal.value = false
    await loadTenantDirectories()
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { dirSaving.value = false }
}
function confirmDeleteDir(d) { deleteDirTarget.value = d }
async function doDeleteDir() {
  try {
    await deleteTenantDirectory(selectedTenant.value.id, deleteDirTarget.value.id)
    notif.success('Directory deleted')
    deleteDirTarget.value = null
    await loadTenantDirectories()
  } catch (e) { notif.error(e.response?.data?.detail || e.message); deleteDirTarget.value = null }
}
async function doTestDir() {
  testDirLoading.value = true
  testDirResult.value = null
  try {
    const { data } = await testTenantConnection(selectedTenant.value.id, dirForm.value)
    testDirResult.value = data
  } catch (e) {
    testDirResult.value = { success: false, message: e.response?.data?.detail || e.message }
  } finally { testDirLoading.value = false }
}
async function doEvictPool(d) {
  try {
    await evictTenantPool(selectedTenant.value.id, d.id)
    notif.success('Connection pool evicted')
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
}

// ── Admins ─────────────────────────────────────────────────────────────────────

const adminsLoading  = ref(false)
const admins         = ref([])
const showAdminModal = ref(false)
const editAdmin      = ref(null)
const adminSaving    = ref(false)
const deleteAdminTarget = ref(null)
const adminForm = ref({ username: '', email: '', password: '', active: true })

async function loadAdmins() {
  adminsLoading.value = true
  try {
    const { data } = await listAdmins(selectedTenant.value.id)
    admins.value = data
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { adminsLoading.value = false }
}
function openCreateAdmin() {
  editAdmin.value = null
  adminForm.value = { username: '', email: '', password: '', active: true }
  showAdminModal.value = true
}
function openEditAdmin(a) {
  editAdmin.value = a
  adminForm.value = { username: a.username, email: a.email ?? '', password: '', active: a.active !== false }
  showAdminModal.value = true
}
async function saveAdmin() {
  adminSaving.value = true
  try {
    const payload = { ...adminForm.value }
    if (editAdmin.value && !payload.password) delete payload.password
    editAdmin.value
      ? await updateAdmin(selectedTenant.value.id, editAdmin.value.id, payload)
      : await createAdmin(selectedTenant.value.id, payload)
    notif.success(editAdmin.value ? 'Admin updated' : 'Admin created')
    showAdminModal.value = false
    await loadAdmins()
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { adminSaving.value = false }
}
function confirmDeleteAdmin(a) { deleteAdminTarget.value = a }
async function doDeleteAdmin() {
  try {
    await deleteAdmin(selectedTenant.value.id, deleteAdminTarget.value.id)
    notif.success('Admin removed')
    deleteAdminTarget.value = null
    await loadAdmins()
  } catch (e) { notif.error(e.response?.data?.detail || e.message); deleteAdminTarget.value = null }
}

// ── Permissions ────────────────────────────────────────────────────────────────

const showPermissionsModal = ref(false)
const permissionsAdmin     = ref(null)
const permissionsLoading   = ref(false)
const permissions          = ref({ directoryRoles: [], branchRestrictions: {}, featurePermissions: [] })
const dirRoleForm          = ref({ directoryId: '', baseRole: 'ADMIN' })
const branchDnInputs       = reactive({})
const featureSaving        = ref(false)
const pendingFeatureOverrides = reactive({}) // featureKey → 'inherit'|'allow'|'deny'

const allFeatureKeys = [
  'USER_CREATE', 'USER_EDIT', 'USER_DELETE', 'USER_ENABLE_DISABLE', 'USER_MOVE',
  'GROUP_MANAGE_MEMBERS', 'GROUP_CREATE_DELETE',
  'BULK_IMPORT', 'BULK_EXPORT',
  'REPORTS_RUN', 'REPORTS_EXPORT', 'REPORTS_SCHEDULE',
]

function featureOverrideValue(fk) {
  if (fk in pendingFeatureOverrides) return pendingFeatureOverrides[fk]
  const override = permissions.value.featurePermissions?.find(f => f.featureKey === fk)
  if (!override) return 'inherit'
  return override.enabled ? 'allow' : 'deny'
}
function setFeatureOverride(fk, val) { pendingFeatureOverrides[fk] = val }

async function openPermissions(admin) {
  permissionsAdmin.value = admin
  showPermissionsModal.value = true
  permissionsLoading.value = true
  // Reset
  Object.keys(pendingFeatureOverrides).forEach(k => delete pendingFeatureOverrides[k])
  Object.keys(branchDnInputs).forEach(k => delete branchDnInputs[k])
  dirRoleForm.value = { directoryId: '', baseRole: 'ADMIN' }
  try {
    const { data } = await getPermissions(selectedTenant.value.id, admin.id)
    permissions.value = data
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { permissionsLoading.value = false }
}

async function doAddDirectoryRole() {
  if (!dirRoleForm.value.directoryId) return
  try {
    await setDirectoryRole(selectedTenant.value.id, permissionsAdmin.value.id, dirRoleForm.value)
    notif.success('Directory role assigned')
    const { data } = await getPermissions(selectedTenant.value.id, permissionsAdmin.value.id)
    permissions.value = data
    dirRoleForm.value.directoryId = ''
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
}
async function doRemoveDirectoryRole(directoryId) {
  try {
    await removeDirectoryRole(selectedTenant.value.id, permissionsAdmin.value.id, directoryId)
    notif.success('Directory role removed')
    const { data } = await getPermissions(selectedTenant.value.id, permissionsAdmin.value.id)
    permissions.value = data
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
}
async function saveBranchRestrictions(directoryId) {
  const raw = branchDnInputs[directoryId] ?? ''
  const branchDns = raw.split(',').map(s => s.trim()).filter(Boolean)
  try {
    await setBranchRestrictions(selectedTenant.value.id, permissionsAdmin.value.id, { directoryId, branchDns })
    notif.success('Branch restrictions saved')
    const { data } = await getPermissions(selectedTenant.value.id, permissionsAdmin.value.id)
    permissions.value = data
    delete branchDnInputs[directoryId]
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
}
async function saveFeatureOverrides() {
  featureSaving.value = true
  try {
    const toSet = []
    const toClear = []
    for (const fk of allFeatureKeys) {
      const val = pendingFeatureOverrides[fk]
      if (!val || val === 'inherit') {
        toClear.push(fk)
      } else {
        toSet.push({ featureKey: fk, enabled: val === 'allow' })
      }
    }
    if (toSet.length) {
      await setFeaturePermissions(selectedTenant.value.id, permissionsAdmin.value.id, toSet)
    }
    for (const fk of toClear) {
      try { await clearFeaturePermission(selectedTenant.value.id, permissionsAdmin.value.id, fk) } catch { /* ok if not set */ }
    }
    notif.success('Feature overrides saved')
    const { data } = await getPermissions(selectedTenant.value.id, permissionsAdmin.value.id)
    permissions.value = data
    Object.keys(pendingFeatureOverrides).forEach(k => delete pendingFeatureOverrides[k])
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { featureSaving.value = false }
}

// ── Auth Config ────────────────────────────────────────────────────────────────

const authConfigLoading = ref(false)
const authConfigSaving  = ref(false)
const authConfigForm = ref({
  authType: 'LDAP_BIND', ldapDirectoryId: '', ldapBindDnPattern: '',
  samlIdpType: 'GENERIC', samlIdpMetadataUrl: '', samlSpEntityId: '', samlSpAcsUrl: '',
  samlAttributeUsername: '', samlAttributeEmail: '', samlAttributeDisplayName: '',
})

async function loadAuthConfig() {
  authConfigLoading.value = true
  try {
    const { data } = await getTenantAuthConfig(selectedTenant.value.id)
    if (data) {
      Object.assign(authConfigForm.value, {
        authType:                  data.authType ?? 'LDAP_BIND',
        ldapDirectoryId:           data.ldapDirectoryId ?? '',
        ldapBindDnPattern:         data.ldapBindDnPattern ?? '',
        samlIdpType:               data.samlIdpType ?? 'GENERIC',
        samlIdpMetadataUrl:        data.samlIdpMetadataUrl ?? '',
        samlSpEntityId:            data.samlSpEntityId ?? '',
        samlSpAcsUrl:              data.samlSpAcsUrl ?? '',
        samlAttributeUsername:     data.samlAttributeUsername ?? '',
        samlAttributeEmail:        data.samlAttributeEmail ?? '',
        samlAttributeDisplayName:  data.samlAttributeDisplayName ?? '',
      })
    }
  } catch { /* no config yet is fine */ }
  finally { authConfigLoading.value = false }
}
async function saveAuthConfig() {
  authConfigSaving.value = true
  try {
    await updateTenantAuthConfig(selectedTenant.value.id, authConfigForm.value)
    notif.success('Auth config saved')
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { authConfigSaving.value = false }
}

// ── Audit Sources ──────────────────────────────────────────────────────────────

const sourcesLoading    = ref(false)
const auditSources      = ref([])
const showSourceModal   = ref(false)
const editSource        = ref(null)
const sourceSaving      = ref(false)
const deleteSourceTarget = ref(null)
const sourceForm = ref({
  displayName: '', host: '', port: 389, sslMode: 'NONE',
  trustAllCerts: false, bindDn: '', bindPassword: '',
  changelogBaseDn: 'cn=changelog', branchFilterDn: '', enabled: true,
})

async function loadAuditSources() {
  sourcesLoading.value = true
  try {
    const { data } = await listAuditSources(selectedTenant.value.id)
    auditSources.value = data
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { sourcesLoading.value = false }
}
function openCreateSource() {
  editSource.value = null
  sourceForm.value = { displayName: '', host: '', port: 389, sslMode: 'NONE', trustAllCerts: false, bindDn: '', bindPassword: '', changelogBaseDn: 'cn=changelog', branchFilterDn: '', enabled: true }
  showSourceModal.value = true
}
function openEditSource(s) {
  editSource.value = s
  sourceForm.value = { displayName: s.displayName, host: s.host, port: s.port, sslMode: s.sslMode, trustAllCerts: s.trustAllCerts, bindDn: s.bindDn, bindPassword: '', changelogBaseDn: s.changelogBaseDn, branchFilterDn: s.branchFilterDn ?? '', enabled: s.enabled }
  showSourceModal.value = true
}
async function saveSource() {
  sourceSaving.value = true
  try {
    const payload = { ...sourceForm.value }
    if (editSource.value && !payload.bindPassword) delete payload.bindPassword
    editSource.value
      ? await updateAuditSource(selectedTenant.value.id, editSource.value.id, payload)
      : await createAuditSource(selectedTenant.value.id, payload)
    notif.success(editSource.value ? 'Source updated' : 'Source created')
    showSourceModal.value = false
    await loadAuditSources()
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { sourceSaving.value = false }
}
function confirmDeleteSource(s) { deleteSourceTarget.value = s }
async function doDeleteSource() {
  try {
    await deleteAuditSource(selectedTenant.value.id, deleteSourceTarget.value.id)
    notif.success('Source deleted')
    deleteSourceTarget.value = null
    await loadAuditSources()
  } catch (e) { notif.error(e.response?.data?.detail || e.message); deleteSourceTarget.value = null }
}
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary    { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary  { @apply px-4 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50; }
.btn-sm-primary { @apply px-3 py-1.5 bg-blue-600 text-white rounded-lg text-xs font-medium hover:bg-blue-700; }
.input          { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
