-- Editable least-privilege starter roles. They are deliberately not assigned to
-- users: an upgrade must never guess a person's job and silently broaden access.

INSERT INTO auth_role(role_code, role_name, description, system_role, active, created_by)
VALUES ('DEFAULT_BASIC_USER', 'مستخدم أساسي', 'إعدادات الحساب الشخصي فقط', 0, 1, 1),
       ('DEFAULT_SALES_CASHIER', 'موظف مبيعات', 'إنشاء المبيعات وخدمة العملاء دون حذف', 0, 1, 1),
       ('DEFAULT_SALES_MANAGER', 'مدير مبيعات', 'إدارة المبيعات والعملاء وتقارير المبيعات', 0, 1, 1),
       ('DEFAULT_PURCHASES', 'مسؤول مشتريات', 'المشتريات والموردون دون صلاحيات حذف', 0, 1, 1),
       ('DEFAULT_INVENTORY', 'مسؤول مخزن', 'الأصناف والوحدات والجرد والتسويات', 0, 1, 1),
       ('DEFAULT_ACCOUNTANT', 'محاسب', 'الحسابات والخزينة والمصروفات والتقارير المالية', 0, 1, 1),
       ('DEFAULT_SECURITY_ADMIN', 'مسؤول مستخدمين وصلاحيات', 'إدارة المستخدمين والأدوار دون صلاحيات تشغيلية', 0, 1, 1)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name),
                        description = VALUES(description);

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN ('setting.update.name', 'setting.update.pass')
WHERE r.role_code = 'DEFAULT_BASIC_USER';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN (
    'sales.show', 'sales.create', 'sales.re.show', 'sales.re.create',
    'total.sales.show', 'total.sales.show.invoice',
    'total.sales.re.show', 'total.sales.re.show.invoice',
    'customer.show', 'customer.create', 'customer.account.show', 'customer.account.create',
    'items.show', 'inventory.show', 'treasury.show'
)
WHERE r.role_code = 'DEFAULT_SALES_CASHIER';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN (
    'sales.update', 'sales.delete', 'sales.re.update', 'sales.re.delete',
    'customer.update', 'customer.delete', 'customer.account.update', 'customer.account.delete',
    'reports.show.sales', 'reports.show.customers', 'reports.show.delegate',
    'reports.show.day.details'
)
WHERE r.role_code = 'DEFAULT_SALES_MANAGER';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN (
    'purchase.show', 'purchase.create', 'purchase.update',
    'purchase.re.show', 'purchase.re.create', 'purchase.re.update',
    'total.purchase.show', 'total.purchase.show.invoice',
    'total.purchase.re.show', 'total.purchase.re.show.invoice',
    'suppliers.show', 'suppliers.create', 'suppliers.update',
    'suppliers.account.show', 'suppliers.account.create', 'suppliers.account.update',
    'items.show', 'inventory.show', 'show.column.buy.price'
)
WHERE r.role_code = 'DEFAULT_PURCHASES';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN (
    'items.show', 'items.create', 'items.update', 'items.delete', 'items.add.excel',
    'main.group.show', 'main.group.create', 'main.group.update', 'main.group.delete',
    'sub.group.show', 'sub.group.create', 'sub.group.update', 'sub.group.delete',
    'units.show', 'units.create', 'units.update', 'units.delete',
    'inventory.show', 'stock.count.show', 'stock.count.post',
    'show.column.buy.price'
)
WHERE r.role_code = 'DEFAULT_INVENTORY';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN (
    'customer.show', 'customer.account.show', 'customer.account.create', 'customer.account.update',
    'suppliers.show', 'suppliers.account.show', 'suppliers.account.create', 'suppliers.account.update',
    'expenses.show', 'expenses.create', 'expenses.update',
    'treasury.show', 'treasury.update',
    'total.sales.show', 'total.sales.show.invoice', 'total.sales.re.show', 'total.sales.re.show.invoice',
    'total.purchase.show', 'total.purchase.show.invoice',
    'total.purchase.re.show', 'total.purchase.re.show.invoice',
    'reports.show.summary', 'reports.show.customers.account.area',
    'reports.show.sales', 'reports.show.purchase', 'reports.show.profit',
    'invoice.profit.show', 'show.column.buy.price'
)
WHERE r.role_code = 'DEFAULT_ACCOUNTANT';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN ('users.show', 'users.manage', 'roles.manage')
WHERE r.role_code = 'DEFAULT_SECURITY_ADMIN';

-- Every operational role receives the harmless personal-account permissions.
INSERT IGNORE INTO auth_role_inheritance(child_role_id, parent_role_id, assigned_by)
SELECT child.id, parent.id, 1
FROM auth_role child
JOIN auth_role parent ON parent.role_code = 'DEFAULT_BASIC_USER'
WHERE child.role_code IN ('DEFAULT_SALES_CASHIER', 'DEFAULT_PURCHASES', 'DEFAULT_INVENTORY',
                          'DEFAULT_ACCOUNTANT', 'DEFAULT_SECURITY_ADMIN');

-- Managers compose the cashier role instead of duplicating its grants.
INSERT IGNORE INTO auth_role_inheritance(child_role_id, parent_role_id, assigned_by)
SELECT child.id, parent.id, 1
FROM auth_role child
JOIN auth_role parent ON parent.role_code = 'DEFAULT_SALES_CASHIER'
WHERE child.role_code = 'DEFAULT_SALES_MANAGER';

