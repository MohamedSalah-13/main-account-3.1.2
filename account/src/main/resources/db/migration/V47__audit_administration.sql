-- Administrative operations on the audit trail must outlive the rows they affect.
-- This journal is intentionally separate from audit_log and is made append-only by
-- repeatable triggers. Retention starts disabled on every existing and fresh database.
CREATE TABLE audit_admin_event
(
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_type       VARCHAR(40)  NOT NULL,
    actor_user_id    INT          NULL,
    actor_name       VARCHAR(100) NULL,
    source            VARCHAR(50)  NOT NULL DEFAULT 'DATABASE',
    workstation_id   VARCHAR(128) NULL,
    workstation_name VARCHAR(100) NULL,
    occurred_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason            VARCHAR(500) NULL,
    affected_rows    BIGINT       NOT NULL DEFAULT 0,
    details           JSON         NULL,
    CONSTRAINT fk_audit_admin_event_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_audit_admin_event_time (occurred_at),
    INDEX idx_audit_admin_event_type_time (event_type, occurred_at)
) ENGINE = InnoDB;

INSERT INTO app_setting(setting_key, setting_value, updated_by)
VALUES ('audit.retention.enabled', 'false', NULL),
       ('audit.retention.days', '365', NULL),
       ('audit.retention.last_run', '', NULL)
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('audit.export', 'تصدير سجل التدقيق', 'AUDIT', 'audit', 'EXPORT', 'HIGH', 0, 1, 1),
       ('audit.retention.manage', 'إدارة الاحتفاظ بسجل التدقيق', 'AUDIT', 'audit.retention',
        'MANAGE', 'CRITICAL', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

-- Preserve the access existing roles already had while keeping both capabilities
-- independently removable after the upgrade.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT DISTINCT existing.role_id, audit_export.id, 1
FROM auth_role_permission existing
         JOIN auth_permission source_permission
              ON source_permission.id = existing.permission_id
             AND source_permission.permission_key = 'audit.view'
         JOIN auth_permission audit_export
              ON audit_export.permission_key = 'audit.export';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT DISTINCT existing.role_id, retention_manage.id, 1
FROM auth_role_permission existing
         JOIN auth_permission source_permission
              ON source_permission.id = existing.permission_id
             AND source_permission.permission_key = 'audit.delete'
         JOIN auth_permission retention_manage
              ON retention_manage.permission_key = 'audit.retention.manage';
