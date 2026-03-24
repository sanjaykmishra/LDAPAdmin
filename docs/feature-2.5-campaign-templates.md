# Feature 2.5: Campaign Templates — Manual Testing Guide

## Overview

Campaign Templates allow superadmins to save reusable access review campaign configurations. Instead of manually configuring groups, reviewers, deadlines, and options each time, users can create a template once and launch campaigns from it with a single click.

## What Was Built

### Backend
- **Migration V43**: `campaign_templates` table with JSONB `config` column
- **Entity**: `CampaignTemplate` + `CampaignTemplateConfig` (JSONB-mapped)
- **Repository**: `CampaignTemplateRepository`
- **DTOs**: `CreateCampaignTemplateRequest`, `UpdateCampaignTemplateRequest`, `CampaignTemplateResponse`
- **Service**: `CampaignTemplateService` — full CRUD, create-from-campaign, convert-to-campaign-request
- **Controller**: `CampaignTemplateController` — 7 endpoints under `/api/v1/directories/{dirId}/campaign-templates`

### Frontend
- **API layer**: `frontend/src/api/campaignTemplates.js`
- **Templates list view**: `CampaignTemplatesView.vue`
- **Template form view**: `CampaignTemplateFormView.vue` (create + edit)
- **Campaign create view**: Updated with "Start from template" dropdown
- **Campaign detail view**: Added "Save as Template" button
- **Router**: 3 new routes for template list, create, and edit

### Tests (38 new tests)
- `CampaignTemplateServiceTest` — 24 tests covering all service methods
- `CampaignTemplateControllerTest` — 14 tests covering all endpoints + auth

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/directories/{dirId}/campaign-templates` | Create template |
| `GET` | `/api/v1/directories/{dirId}/campaign-templates` | List templates |
| `GET` | `/api/v1/directories/{dirId}/campaign-templates/{id}` | Get template |
| `PUT` | `/api/v1/directories/{dirId}/campaign-templates/{id}` | Update template |
| `DELETE` | `/api/v1/directories/{dirId}/campaign-templates/{id}` | Delete template |
| `POST` | `/api/v1/directories/{dirId}/campaign-templates/{id}/create-campaign` | Create campaign from template |
| `POST` | `/api/v1/directories/{dirId}/campaign-templates/from-campaign/{campaignId}` | Save existing campaign as template |

All endpoints require `ACCESS_REVIEW_MANAGE` feature permission and `SUPERADMIN` role (except list and get, which require only `ACCESS_REVIEW_MANAGE`).

## Manual Testing Steps

### Prerequisites
- Application running with PostgreSQL (V43 migration applied automatically)
- Logged in as a superadmin
- At least one directory connection configured
- At least one admin account to assign as reviewer

### 1. Create a Template Directly

1. Navigate to **Directories > [your directory] > Campaign Templates**
2. Click **New Template**
3. Fill in:
   - Name: "Quarterly Admin Access Review"
   - Description: "Review admin group membership every quarter"
   - Deadline: 30 days
   - Recurrence: 3 months
   - Enable "Auto-revoke on decision"
4. Add a group:
   - Select a group DN from the picker
   - Set member attribute to "member"
   - Select a reviewer
5. Click **Create Template**
6. Verify the template appears in the list with correct details

### 2. Edit a Template

1. From the templates list, click **Edit** on the template created above
2. Change the deadline to 45 days
3. Add a second group
4. Click **Update Template**
5. Verify changes are reflected in the list

### 3. Create Campaign from Template

1. From the templates list, click **Create Campaign** on a template
2. Verify you're redirected to the new campaign's detail page
3. Check that the campaign has:
   - The template's name
   - Correct deadline days
   - Correct recurrence setting
   - All groups and reviewers from the template

### 4. Start from Template (Campaign Creation Form)

1. Navigate to **Access Reviews > New Campaign**
2. At the top, observe the "Start from template" dropdown
3. Select your template and click **Apply**
4. Verify all form fields are pre-filled:
   - Description, deadline, recurrence, auto-revoke settings
   - Groups with correct DNs, member attributes, and reviewers
5. Change the campaign name to something unique
6. Click **Create Campaign**

### 5. Save Campaign as Template

1. Navigate to an existing campaign's detail page
2. Click the **Save as Template** button
3. Verify a success toast appears
4. Navigate to Campaign Templates
5. Verify a new template exists with the campaign's name + " (Template)" suffix
6. Verify the template config matches the campaign's settings

### 6. Delete a Template

1. From the templates list, click **Delete** on a template
2. Confirm the deletion dialog
3. Verify the template is removed from the list

### 7. Authorization Tests

1. Log in as a non-superadmin ADMIN user
2. Attempt to access `/directories/{dirId}/campaign-templates` — should be blocked
3. Attempt to call `POST /api/v1/directories/{dirId}/campaign-templates` via API — should return 403

### 8. Validation Tests

1. Try creating a template with:
   - Empty name → should fail (400)
   - Deadline of 0 → should fail (400)
   - No groups → should fail (400)
   - Recurrence of 0 → should fail (service rejects with error message)
