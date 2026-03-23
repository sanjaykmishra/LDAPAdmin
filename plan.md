# Password Policy & Generation — Implementation Plan

## Summary
Add profile-level password policy configuration, server-side password generation, email delivery of generated passwords, and a password visibility toggle on self-service forms.

---

## 1. Database Migration

**New file:** `V30__profile_password_policy.sql`

Add columns to `provisioning_profiles`:
```sql
password_length          INT     NOT NULL DEFAULT 16,
password_uppercase       BOOLEAN NOT NULL DEFAULT true,
password_lowercase       BOOLEAN NOT NULL DEFAULT true,
password_digits          BOOLEAN NOT NULL DEFAULT true,
password_special         BOOLEAN NOT NULL DEFAULT true,
password_special_chars   VARCHAR(50) NOT NULL DEFAULT '!@#$%^&*',
email_password_to_user   BOOLEAN NOT NULL DEFAULT false
```

No enum for password mode — the admin create form always shows a manual password field with a "Generate" button next to it. The generation settings control what the button produces.

---

## 2. Backend Entity

**File:** `ProvisioningProfile.java`

Add the 7 new fields with JPA annotations matching the migration columns.

---

## 3. Backend DTOs

**Files:** `CreateProfileRequest.java`, `UpdateProfileRequest.java`, `ProfileResponse.java`

Add the 7 fields to all three DTOs. Use `@JsonProperty` defaults where appropriate so existing clients that don't send these fields get sensible defaults.

---

## 4. Backend Service — `applyCommonFields()`

**File:** `ProvisioningProfileService.java`

Update `applyCommonFields()` to copy the 7 new fields from request DTO to entity.

Update `toResponse()` (or the mapping method) to include the new fields in `ProfileResponse`.

---

## 5. Password Generator Service

**New file:** `PasswordGeneratorService.java`

- `String generate(ProvisioningProfile profile)` — generates a password using the profile's settings
- Uses `java.security.SecureRandom`
- Builds a character pool from the enabled classes (upper/lower/digit/special)
- Guarantees at least one character from each enabled class
- Validates against LDAP `pwdMinLength` if available (via `PasswordPolicyService`)
- Takes the greater of `profile.passwordLength` and LDAP `pwdMinLength`

---

## 6. Password Generation API Endpoint

**File:** `ProvisioningProfileController.java`

```
POST /api/v1/profiles/{profileId}/generate-password
```

Response: `{ "password": "xK9#mP2v..." }`

This endpoint is called by the admin create form's "Generate" button. The generated password is returned to the frontend (over HTTPS) and populated into the password field.

---

## 7. Email Password to User

**File:** `ApprovalNotificationService.java` (or a new `UserNotificationService.java`)

Add method: `sendPasswordEmail(String recipientEmail, String userName, String password, String directoryName)`

**Called from:** `ProvisioningProfileService.provisionUser()` — after successful LDAP user creation, if `profile.emailPasswordToUser` is true and the user has a `mail` attribute, send the password via email.

---

## 8. Frontend — Profile Editor (Password Policy Settings)

**File:** `SuperadminProfilesView.vue`

Add password generation settings to the **General** tab (below existing fields):

- **Password Generation Settings** fieldset:
  - Password Length (number input, min 8, max 128)
  - Character classes: 4 checkboxes (Uppercase, Lowercase, Digits, Special)
  - Special Characters (text input, shown when Special is checked)
  - "Email generated password to user" checkbox

Update `emptyProfile()` to include defaults for the new fields.
Update the `openEdit()` mapping to include the new fields.

---

## 9. Frontend — Admin User Create Form

**File:** `UserForm.vue`

For the `userPassword` field (when `inputType === 'PASSWORD'`):
- Add a "Generate" button (key icon) next to the password input
- On click: call `POST /profiles/{profileId}/generate-password`
- Populate the returned password into the field
- Temporarily show the password in cleartext so the admin can see/copy it
- Add a copy-to-clipboard button

This requires passing the `profileId` as a prop to `UserForm.vue`.

---

## 10. Frontend — Password Visibility Toggle (Self-Registration & Self-Service)

**Files:** `RegisterView.vue`, self-service password change form

Add an eye icon button to password fields:
- On mousedown/touchstart: change input type from `password` to `text`
- On mouseup/touchend/mouseleave: change back to `password`
- Uses press-and-hold behavior (shows password only while pressed)

This is a UI-only change — wrap the password input in a relative container with an absolutely-positioned icon button.

---

## File Change Summary

| File | Change |
|------|--------|
| `V30__profile_password_policy.sql` | **New** — migration |
| `ProvisioningProfile.java` | Add 7 fields |
| `CreateProfileRequest.java` | Add 7 fields |
| `UpdateProfileRequest.java` | Add 7 fields |
| `ProfileResponse.java` | Add 7 fields |
| `ProvisioningProfileService.java` | Update `applyCommonFields()` and response mapping |
| `PasswordGeneratorService.java` | **New** — generation logic |
| `ProvisioningProfileController.java` | Add generate-password endpoint |
| `ApprovalNotificationService.java` | Add `sendPasswordEmail()` |
| `SuperadminProfilesView.vue` | Password policy config UI |
| `profiles.js` (frontend API) | Add `generatePassword()` API call |
| `UserForm.vue` | Generate button + show/copy for admin create |
| `RegisterView.vue` | Eye icon visibility toggle |
| Self-service password change view | Eye icon visibility toggle |
