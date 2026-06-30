-- V10 — Decouple identity.audit_log.actor_user_id from identity.users
--
-- Rationale (copilot-instructions §16, §20):
--   * Audit rows must NEVER be silently dropped. The previous FK
--     audit_log_actor_user_id_fkey enforced actor_user_id REFERENCES identity.users(user_id),
--     so any audit emitted with a UUID that did not yet exist in identity.users
--     (frontend bootstrap default UUID, scheduled/system jobs, sessions where the
--     user row had been pruned) caused a DataIntegrityViolationException that
--     LoggingAuditService caught and only warn-logged — losing the audit row.
--   * Standard enterprise audit practice is that audit MUST survive user
--     deletion or scope changes (S20: "All important actions must be auditable.").
--     Therefore the FK is removed; the actor UUID is kept as a free-form
--     reference and the application layer enriches each row with a resolved
--     actor username / "System" label.
--
-- Backward-compatibility: column remains nullable; existing rows are untouched.

ALTER TABLE identity.audit_log
    DROP CONSTRAINT IF EXISTS audit_log_actor_user_id_fkey;

-- Helpful index for the audit page queries (filter by tenant + ordered by time).
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_at
    ON identity.audit_log (tenant_id, at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_actor_user_id
    ON identity.audit_log (actor_user_id);

