-- V15: Mark individual user base DNs as editable or read-only.
--
-- An editable branch is one where the application is permitted to create,
-- move, and delete user entries.  Read-only branches are browsable but
-- write operations targeting them will be rejected by LdapOperationService.
--
-- Defaults to FALSE so existing branches are treated as read-only until an
-- administrator explicitly enables writes for each one.

ALTER TABLE directory_user_base_dns
    ADD COLUMN editable BOOLEAN NOT NULL DEFAULT FALSE;
