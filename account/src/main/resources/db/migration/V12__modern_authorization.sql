-- Modern authorization schema. V11 imported every legacy grant; the obsolete
-- per-user matrix can now be removed with no loss of effective access.

DROP TRIGGER IF EXISTS after_permission_insert;
DROP TRIGGER IF EXISTS after_users_insert;

RENAME TABLE permission TO auth_permission,
             roles TO auth_role,
             role_permission TO auth_role_permission,
             user_role TO auth_user_role,
             rbac_audit_log TO auth_audit_log;

ALTER TABLE auth_permission
    CHANGE COLUMN name_permission permission_key VARCHAR(160) NOT NULL,
    CHANGE COLUMN category module_key VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN resource_key VARCHAR(140) NOT NULL DEFAULT 'general',
    ADD COLUMN action_key VARCHAR(40) NOT NULL DEFAULT 'show',
    ADD COLUMN risk_level VARCHAR(12) NOT NULL DEFAULT 'LOW',
    ADD COLUMN system_permission TINYINT NOT NULL DEFAULT 1,
    ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT auth_permission_risk_chk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    ADD CONSTRAINT auth_permission_system_chk CHECK (system_permission IN (0, 1)),
    ADD CONSTRAINT auth_permission_enabled_chk CHECK (enabled IN (0, 1));

UPDATE auth_permission
SET permission_key = REPLACE(permission_key, '_', '.');

UPDATE auth_permission
SET module_key = UPPER(SUBSTRING_INDEX(permission_key, '.', 1)),
    action_key = UPPER(SUBSTRING_INDEX(permission_key, '.', -1)),
    resource_key = LEFT(permission_key,
                        CHAR_LENGTH(permission_key) - CHAR_LENGTH(SUBSTRING_INDEX(permission_key, '.', -1)) - 1),
    risk_level = CASE UPPER(SUBSTRING_INDEX(permission_key, '.', -1))
                     WHEN 'DELETE' THEN 'CRITICAL'
                     WHEN 'BYPASS' THEN 'CRITICAL'
                     WHEN 'MANAGE' THEN 'CRITICAL'
                     WHEN 'POST' THEN 'CRITICAL'
                     WHEN 'UPDATE' THEN 'HIGH'
                     WHEN 'ADD' THEN 'HIGH'
                     ELSE 'LOW'
                 END;

-- Split create from view. Existing roles keep their previous ability to create;
-- administrators can revoke the new key independently after the upgrade.
INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('purchase.create', 'إنشاء فاتورة شراء', 'PURCHASE', 'purchase', 'CREATE', 'HIGH', 0, 1, 1),
       ('purchase.re.create', 'إنشاء مرتجع شراء', 'PURCHASE', 'purchase.re', 'CREATE', 'HIGH', 0, 1, 1),
       ('sales.create', 'إنشاء فاتورة مبيعات', 'SALES', 'sales', 'CREATE', 'HIGH', 0, 1, 1),
       ('sales.re.create', 'إنشاء مرتجع مبيعات', 'SALES', 'sales.re', 'CREATE', 'HIGH', 0, 1, 1),
       ('items.create', 'إضافة صنف', 'ITEMS', 'items', 'CREATE', 'HIGH', 0, 1, 1),
       ('main.group.create', 'إضافة مجموعة رئيسية', 'MAIN', 'main.group', 'CREATE', 'HIGH', 0, 1, 1),
       ('sub.group.create', 'إضافة مجموعة فرعية', 'SUB', 'sub.group', 'CREATE', 'HIGH', 0, 1, 1),
       ('units.create', 'إضافة وحدة', 'UNITS', 'units', 'CREATE', 'HIGH', 0, 1, 1),
       ('customer.create', 'إضافة عميل', 'CUSTOMER', 'customer', 'CREATE', 'HIGH', 0, 1, 1),
       ('customer.account.create', 'إضافة حركة حساب عميل', 'CUSTOMER', 'customer.account', 'CREATE', 'HIGH', 0, 1, 1),
       ('suppliers.create', 'إضافة مورد', 'SUPPLIERS', 'suppliers', 'CREATE', 'HIGH', 0, 1, 1),
       ('suppliers.account.create', 'إضافة حركة حساب مورد', 'SUPPLIERS', 'suppliers.account', 'CREATE', 'HIGH', 0, 1, 1),
       ('expenses.create', 'إضافة مصروف', 'EXPENSES', 'expenses', 'CREATE', 'HIGH', 0, 1, 1),
       ('employee.create', 'إضافة موظف', 'EMPLOYEE', 'employee', 'CREATE', 'HIGH', 0, 1, 1),
       ('company.update', 'تعديل بيانات الشركة', 'COMPANY', 'company', 'UPDATE', 'HIGH', 0, 1, 1),
       ('area.create', 'إضافة منطقة', 'AREA', 'area', 'CREATE', 'HIGH', 0, 1, 1),
       ('area.update', 'تعديل منطقة', 'AREA', 'area', 'UPDATE', 'HIGH', 0, 1, 1),
       ('area.delete', 'حذف منطقة', 'AREA', 'area', 'DELETE', 'CRITICAL', 0, 1, 1),
       ('audit.delete', 'حذف سجل التدقيق', 'AUDIT', 'audit', 'DELETE', 'CRITICAL', 0, 1, 1),
       ('user.shift.manage', 'إدارة ورديات المستخدمين', 'USER', 'user.shift', 'MANAGE', 'CRITICAL', 0, 1, 1);

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, created.id, 1
FROM auth_role_permission existing
JOIN auth_permission source ON source.id = existing.permission_id
JOIN auth_permission created ON created.permission_key = CASE source.permission_key
    WHEN 'purchase.show' THEN 'purchase.create'
    WHEN 'purchase.re.show' THEN 'purchase.re.create'
    WHEN 'sales.show' THEN 'sales.create'
    WHEN 'sales.re.show' THEN 'sales.re.create'
    WHEN 'items.show' THEN 'items.create'
    WHEN 'main.group.show' THEN 'main.group.create'
    WHEN 'sub.group.show' THEN 'sub.group.create'
    WHEN 'units.show' THEN 'units.create'
    WHEN 'customer.show' THEN 'customer.create'
    WHEN 'customer.account.update' THEN 'customer.account.create'
    WHEN 'suppliers.show' THEN 'suppliers.create'
    WHEN 'suppliers.account.update' THEN 'suppliers.account.create'
    WHEN 'expenses.show' THEN 'expenses.create'
    WHEN 'employee.show' THEN 'employee.create'
    WHEN 'setting.company.show' THEN 'company.update'
    WHEN 'users.manage' THEN 'user.shift.manage'
END
WHERE source.permission_key IN ('purchase.show', 'purchase.re.show', 'sales.show', 'sales.re.show',
                                'items.show', 'main.group.show', 'sub.group.show', 'units.show',
                                'customer.show', 'customer.account.update', 'suppliers.show',
                                'suppliers.account.update', 'expenses.show', 'employee.show',
                                'setting.company.show', 'users.manage');

CREATE TABLE auth_role_inheritance
(
    child_role_id  INT                                NOT NULL,
    parent_role_id INT                                NOT NULL,
    assigned_by    INT                                NULL,
    assigned_at    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (child_role_id, parent_role_id),
    CONSTRAINT auth_role_inheritance_self_chk CHECK (child_role_id <> parent_role_id),
    CONSTRAINT auth_role_inheritance_child_fk FOREIGN KEY (child_role_id)
        REFERENCES auth_role (id) ON DELETE CASCADE,
    CONSTRAINT auth_role_inheritance_parent_fk FOREIGN KEY (parent_role_id)
        REFERENCES auth_role (id) ON DELETE CASCADE,
    CONSTRAINT auth_role_inheritance_actor_fk FOREIGN KEY (assigned_by)
        REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE auth_user_permission_override
(
    user_id       INT                                NOT NULL,
    permission_id INT                                NOT NULL,
    effect        VARCHAR(5)                         NOT NULL,
    reason        VARCHAR(255)                       NULL,
    expires_at    DATETIME                           NULL,
    granted_by    INT                                NULL,
    granted_at    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT auth_override_effect_chk CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT auth_override_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT auth_override_permission_fk FOREIGN KEY (permission_id)
        REFERENCES auth_permission (id) ON DELETE CASCADE,
    CONSTRAINT auth_override_actor_fk FOREIGN KEY (granted_by)
        REFERENCES users (id) ON DELETE SET NULL
);

DROP TABLE user_permission;
