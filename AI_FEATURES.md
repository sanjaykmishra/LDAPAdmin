# AI-Enabled Feature Recommendations — Security & Productivity

## Security — Anomaly Detection & Risk

### 1. Anomalous Access Pattern Detection
Analyze audit logs with an LLM or ML model to flag unusual activity: a user suddenly added to 15 groups in one day, an admin making changes at 3am for the first time, bulk deletions from an account that normally only reads. Surface these as alerts on the dashboard with risk scores.

### 2. Privilege Creep Analysis
Periodically scan group memberships and flag users who have accumulated excessive permissions over time compared to peers in the same OU or department. "This user is in 12 groups while the average for their department is 4." Feeds directly into access review campaigns.

### 3. Dormant Account Detection
Combine LDAP `lastLogonTimestamp`/`pwdLastSet` with audit log activity to identify accounts that haven't been used in X days. AI ranks them by risk (service accounts vs. human accounts, privileged vs. standard) and suggests deprovisioning candidates.

### 4. Smart Access Review Recommendations
During access review campaigns, pre-populate AI-suggested decisions: "REVOKE — user hasn't logged in for 180 days", "CONFIRM — user actively uses this group's resources daily." Reviewers still make the final call, but it dramatically speeds up large campaigns.

### 5. Natural Language Audit Log Queries
Instead of building complex filters, let admins ask: "Show me all password resets in the last week that weren't initiated by the user themselves" or "Who added members to the VPN group in March?" The AI translates to structured audit log queries.

---

## Productivity — Intelligent Assistance

### 6. Natural Language LDAP Search
Type "find all disabled users in the Engineering OU who haven't logged in since January" and have it translated to a proper LDAP filter like `(&(ou=Engineering)(userAccountControl:1.2.840.113556.1.4.803:=2)(lastLogonTimestamp<=...))`. Massive time saver for admins who don't memorize filter syntax.

### 7. AI-Assisted Bulk Operations
Describe what you want in plain English: "Move all contractors whose accounts expire before April to the archived-contractors OU and remove them from all VPN groups." The AI generates a preview of the bulk operation for review before execution.

### 8. Smart User Provisioning Suggestions
When creating a new user, AI suggests group memberships based on similar users in the same OU/department: "Users in Engineering typically belong to: github-access, vpn-users, jira-users. Add these?" Reduces onboarding errors.

### 9. Intelligent Schema Assistance
When an admin is creating or editing entries, provide contextual help: explain what an objectClass requires, suggest appropriate attribute values based on existing entries, warn about schema violations before they hit the server.

### 10. Change Impact Analysis
Before executing a change (deleting a group, modifying an OU, changing a user's attributes), AI analyzes downstream impact: "This group is referenced by 3 other groups as a nested member and is used in 2 access review campaigns. Proceed?" Prevents accidental breakage.

---

## Compliance & Reporting

### 11. AI-Generated Compliance Reports
Generate human-readable compliance narratives from raw data: "During Q1 2026, 3 access review campaigns were completed covering 47 groups. 12 memberships were revoked. Average review completion time was 4.2 days. Two campaigns exceeded their deadline..." — ready for SOX/SOC2 auditors.

### 12. Policy Violation Detection
Define organizational policies in natural language ("No user should be in both the Finance-Admin and Finance-Auditor groups") and have AI continuously monitor for violations, flagging separation-of-duty conflicts.

### 13. Automated Audit Log Summarization
Daily/weekly digest emails that summarize audit activity in plain English rather than raw log entries: "Tuesday was unusual — 23 group membership changes (3x the daily average), primarily by admin jsmith affecting the contractors OU."

---

## Recommended Priority

| Feature | Security Impact | Productivity Impact | Implementation Effort |
|---------|:-:|:-:|:-:|
| Natural Language LDAP Search | Low | Very High | Medium |
| Anomalous Access Detection | Very High | Medium | High |
| Smart Access Review Suggestions | High | Very High | Medium |
| Privilege Creep Analysis | Very High | High | Medium |
| Dormant Account Detection | High | High | Low |
| Change Impact Analysis | High | High | Medium |
| NL Audit Log Queries | Low | High | Medium |
| Policy Violation Detection | Very High | Medium | High |

Start with **Natural Language LDAP Search** (immediate productivity win, moderate effort), **Dormant Account Detection** (high security value, low effort since the data already exists in audit logs), and **Smart Access Review Suggestions** (multiplies the value of the access review system already built).
