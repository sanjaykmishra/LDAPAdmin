# BambooHR & Entra ID Integration Testing Guide

## BambooHR API Credentials

1. **Get an API key** from your BambooHR account:
   - Log in to BambooHR → click your profile (top right) → **API Keys**
   - Click **Add New Key**, give it a name, copy the key
   - Auth is HTTP Basic: API key as username, `x` as password
   - Your subdomain (e.g., `yourcompany.bamboohr.com`) is needed for the base URL

2. **If you don't have a BambooHR account:** They don't offer a free sandbox. Options:
   - Request a **trial account** at bamboohr.com (typically 7 days)
   - Skip real API testing entirely and use **WireMock stubs** with sample response payloads from their API documentation

## Entra ID (Microsoft Graph) Credentials

1. **Register an app** in Azure Portal:
   - Go to **Microsoft Entra ID** → **App registrations** → **New registration**
   - Name it (e.g., "LDAPAdmin Dev"), set it as single-tenant
   - Note the **Application (client) ID** and **Directory (tenant) ID**

2. **Create a client secret:**
   - In the app registration → **Certificates & secrets** → **New client secret**
   - Copy the secret value immediately (it's only shown once)

3. **Grant API permissions:**
   - **Microsoft Graph** → Application permissions → `User.Read.All`, `Group.Read.All`, `GroupMember.Read.All`
   - Click **Grant admin consent** (requires admin role)

4. **If you don't have an Azure tenant:** Create a free one:
   - Sign up for the **Microsoft 365 Developer Program** at developer.microsoft.com/microsoft-365/dev-program
   - You get a free E5 sandbox tenant with 25 user licenses and sample data
   - This is the best option for development — it comes pre-populated with test users and groups

## Testing Strategy

### Unit Tests (No External Dependencies)

Mock the HTTP client layer. Both integrations are read-only REST API consumers:

- Create a connector/client class (e.g., `BambooHrClient`, `EntraIdClient`)
- Use constructor injection (`@RequiredArgsConstructor`) so you can pass mocks directly
- Mock HTTP responses with Mockito — the codebase uses `java.net.http.HttpClient` in `SiemClient`

```java
@ExtendWith(MockitoExtension.class)
class BambooHrClientTest {
    @Mock HttpClient httpClient;
    @Mock HttpResponse<String> response;

    // Test: successful employee list parse
    // Test: API error handling (401, 429, 500)
    // Test: malformed response handling
    // Test: employee status mapping (active/terminated)
}
```

Mock the sync service to test business logic that maps external data to internal models:

```java
@ExtendWith(MockitoExtension.class)
class HrSyncServiceTest {
    @Mock BambooHrClient bambooHrClient;
    @Mock LdapUserService ldapUserService;

    // Test: detect orphaned accounts (HR terminated, LDAP active)
    // Test: detect new hires (HR active, no LDAP account)
    // Test: identity correlation (email match, employee ID match)
}
```

### Integration Tests with WireMock

For testing actual HTTP request/response cycles without hitting real APIs:

```java
@WireMockTest(httpPort = 8089)
class BambooHrClientIntegrationTest {
    // Stub BambooHR /v1/employees/directory endpoint
    // Stub Entra ID /v1.0/users and /v1.0/groups endpoints
    // Test pagination handling, OAuth token refresh, rate limiting
}
```

Add WireMock as a test dependency in `pom.xml`:

```xml
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.9.1</version>
    <scope>test</scope>
</dependency>
```

### Entra ID-Specific Test Concerns

- **Token acquisition** — mock the `/oauth2/v2.0/token` endpoint
- **Token refresh** — verify the client handles expiry and re-authenticates
- **Graph API pagination** — Entra ID uses `@odata.nextLink` for paging
- **User/group mapping** — Entra ID schema differs from LDAP; test the attribute mapping

### BambooHR-Specific Test Concerns

- **Employee directory sync** — parse the `/v1/employees/directory` response
- **Status change detection** — compare snapshots to detect terminations/hires
- **Field mapping** — map BambooHR fields (work email, department, status) to internal model

### Orphaned Account Detection Tests

Pure business logic — no external calls needed:

```java
// Given: HR says these 100 employees are active
// Given: LDAP has these 110 accounts
// Then: 10 accounts are orphaned
// Test edge cases: email mismatches, multiple LDAP accounts per person, etc.
```

### Live API Smoke Tests (Optional)

Gate behind environment variables so they only run when credentials are available:

```java
@Test
@EnabledIfEnvironmentVariable(named = "BAMBOOHR_API_KEY", matches = ".+")
void liveApiSmokeTest() {
    // Only runs when credentials are available
}
```

This keeps the main test suite fast and credential-free.
