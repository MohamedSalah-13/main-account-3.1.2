-- V11 - Role Based Access Control.
-- user_permission is deliberately retained as read-only legacy evidence; all
-- authorization after this migration is resolved through user_role and role_permission.

ALTER TABLE permission
    ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

INSERT INTO permission (id, name_permission, description, category, sort_order)
VALUES (92, 'users_show', 'عرض المستخدمين', 'SECURITY', 920),
       (93, 'users_manage', 'إضافة وتعديل وحذف المستخدمين', 'SECURITY', 930),
       (94, 'roles_manage', 'إدارة الأدوار والصلاحيات', 'SECURITY', 940)
ON DUPLICATE KEY UPDATE description = VALUES(description),
                        category = VALUES(category),
                        sort_order = VALUES(sort_order);

UPDATE permission SET category = 'PURCHASES', sort_order = id WHERE name_permission LIKE '%purchase%';
UPDATE permission SET category = 'SALES', sort_order = id WHERE name_permission LIKE '%sales%';
UPDATE permission SET category = 'PARTIES', sort_order = id
WHERE name_permission LIKE 'customer%' OR name_permission LIKE 'suppliers%';
UPDATE permission SET category = 'INVENTORY', sort_order = id
WHERE name_permission LIKE 'items%' OR name_permission LIKE 'inventory%'
   OR name_permission LIKE 'stock_count%' OR name_permission LIKE 'units%'
   OR name_permission LIKE 'main_group%' OR name_permission LIKE 'sub_group%';
UPDATE permission SET category = 'TREASURY', sort_order = id WHERE name_permission LIKE 'treasury%';
UPDATE permission SET category = 'REPORTS', sort_order = id WHERE name_permission LIKE 'reports%';
UPDATE permission SET category = 'SETTINGS', sort_order = id
WHERE name_permission LIKE 'setting%' OR name_permission LIKE 'accounting_lock%';
UPDATE permission SET category = 'SECURITY', sort_order = id
WHERE name_permission IN ('users_show', 'users_manage', 'roles_manage');

CREATE TABLE IF NOT EXISTS roles
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    role_code   VARCHAR(80)                           NOT NULL,
    role_name   VARCHAR(120)                          NOT NULL,
    description VARCHAR(255)                          NULL,
    system_role TINYINT     DEFAULT 0                 NOT NULL,
    active      TINYINT     DEFAULT 1                 NOT NULL,
    created_by  INT                                   NULL,
    created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT roles_code_uk UNIQUE (role_code),
    CONSTRAINT roles_name_uk UNIQUE (role_name),
    CONSTRAINT roles_system_chk CHECK (system_role IN (0, 1)),
    CONSTRAINT roles_active_chk CHECK (active IN (0, 1)),
    CONSTRAINT roles_created_by_fk FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS role_permission
(
    role_id       INT                                NOT NULL,
    permission_id INT                                NOT NULL,
    granted_by    INT                                NULL,
    granted_at    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT role_permission_role_fk FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT role_permission_permission_fk FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE CASCADE,
    CONSTRAINT role_permission_granted_by_fk FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS user_role
(
    user_id     INT                                NOT NULL,
    role_id     INT                                NOT NULL,
    assigned_by INT                                NULL,
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT user_role_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_role_role_fk FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT user_role_assigned_by_fk FOREIGN KEY (assigned_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS rbac_audit_log
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id INT                                NULL,
    action_name   VARCHAR(50)                        NOT NULL,
    entity_type   VARCHAR(30)                        NOT NULL,
    entity_id     INT                                NOT NULL,
    details       TEXT                               NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX rbac_audit_created_at_idx (created_at),
    INDEX rbac_audit_actor_idx (actor_user_id),
    CONSTRAINT rbac_audit_actor_fk FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);

INSERT INTO roles (role_code, role_name, description, system_role, active, created_by)
VALUES ('SYSTEM_ADMIN', 'مدير النظام', 'دور محمي يملك جميع الصلاحيات', 1, 1, 1)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description),
                        system_role = 1, active = 1;

INSERT INTO role_permission (role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM roles r
CROSS JOIN permission p
WHERE r.role_code = 'SYSTEM_ADMIN'
ON DUPLICATE KEY UPDATE granted_by = VALUES(granted_by);

INSERT INTO user_role (user_id, role_id, assigned_by)
SELECT 1, r.id, 1 FROM roles r WHERE r.role_code = 'SYSTEM_ADMIN'
ON DUPLICATE KEY UPDATE assigned_by = VALUES(assigned_by);

-- One compatibility role per current user preserves the exact effective grants.
INSERT INTO roles (role_code, role_name, description, system_role, active, created_by)
SELECT CONCAT('LEGACY_USER_', u.id), CONCAT('صلاحيات ', u.user_name),
       'تم إنشاؤه تلقائيًا عند التحويل إلى RBAC', 0, 1, 1
FROM users u
WHERE u.id <> 1
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), active = 1;

INSERT INTO user_role (user_id, role_id, assigned_by)
SELECT u.id, r.id, 1
FROM users u
JOIN roles r ON r.role_code = CONCAT('LEGACY_USER_', u.id)
WHERE u.id <> 1
ON DUPLICATE KEY UPDATE assigned_by = VALUES(assigned_by);

INSERT INTO role_permission (role_id, permission_id, granted_by)
SELECT r.id, up.permission_id, 1
FROM user_permission up
JOIN roles r ON r.role_code = CONCAT('LEGACY_USER_', up.user_id)
WHERE up.check_status = 1
ON DUPLICATE KEY UPDATE granted_by = VALUES(granted_by);

-- SETTING_SHOW previously exposed user and permission administration. Preserve
-- that access for existing installations while future grants use the new codes.
INSERT INTO role_permission (role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM user_permission up
JOIN roles r ON r.role_code = CONCAT('LEGACY_USER_', up.user_id)
JOIN permission p ON p.id IN (92, 93, 94)
WHERE up.permission_id = 65 AND up.check_status = 1
ON DUPLICATE KEY UPDATE granted_by = VALUES(granted_by);
