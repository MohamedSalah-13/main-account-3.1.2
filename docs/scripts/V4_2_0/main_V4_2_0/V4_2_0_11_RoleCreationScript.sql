-- =====================================================================
-- V4_2_0_11: إنشاء الأدوار الافتراضية مع صلاحياتها
-- =====================================================================
-- هذا السكريبت يقوم بإنشاء الأدوار الأساسية للنظام:
-- 1. Admin (مدير النظام) - كل الصلاحيات
-- 2. Manager (مدير) - معظم الصلاحيات عدا إدارة المستخدمين والنظام
-- 3. Sales Manager (مدير مبيعات) - صلاحيات المبيعات والعملاء
-- 4. Purchase Manager (مدير مشتريات) - صلاحيات المشتريات والموردين
-- 5. Warehouse Manager (مدير مخزن) - صلاحيات المخازن والأصناف
-- 6. Accountant (محاسب) - صلاحيات المحاسبة والتقارير
-- 7. Cashier (كاشير) - صلاحيات البيع والخزينة فقط
-- 8. Data Entry (مدخل بيانات) - صلاحيات الإدخال الأساسية
-- =====================================================================

USE account_system_db;

-- =====================================================================
-- 1) حذف الأدوار القديمة (اختياري)
-- =====================================================================
-- DELETE FROM user_role;
-- DELETE FROM role_permission;
-- DELETE FROM roles;

-- =====================================================================
-- 2) إنشاء الأدوار الأساسية
-- =====================================================================

-- Admin Role - مدير النظام
INSERT INTO roles (id, name, description, active) VALUES 
(1, 'Admin', 'مدير النظام - صلاحيات كاملة', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- Manager Role - مدير عام
INSERT INTO roles (id, name, description, active) VALUES 
(2, 'Manager', 'مدير - صلاحيات إدارية واسعة', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- Sales Manager - مدير مبيعات
INSERT INTO roles (id, name, description, active) VALUES 
(3, 'Sales Manager', 'مدير مبيعات - إدارة المبيعات والعملاء', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- Purchase Manager - مدير مشتريات
INSERT INTO roles (id, name, description, active) VALUES 
(4, 'Purchase Manager', 'مدير مشتريات - إدارة المشتريات والموردين', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- Warehouse Manager - مدير مخزن
INSERT INTO roles (id, name, description, active) VALUES 
(5, 'Warehouse Manager', 'مدير مخزن - إدارة المخازن والأصناف', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- Accountant - محاسب
INSERT INTO roles (id, name, description, active) VALUES 
(6, 'Accountant', 'محاسب - المحاسبة والتقارير المالية', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- Cashier - كاشير
INSERT INTO roles (id, name, description, active) VALUES 
(7, 'Cashier', 'كاشير - البيع ونقاط البيع', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- Data Entry - مدخل بيانات
INSERT INTO roles (id, name, description, active) VALUES 
(8, 'Data Entry', 'مدخل بيانات - إدخال البيانات الأساسية', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- =====================================================================
-- 3) منح صلاحيات Admin - كل الصلاحيات
-- =====================================================================

INSERT INTO role_permission (role_id, permission_id, check_status)
SELECT 1, id, 1
FROM permission
WHERE active = 1
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 4) منح صلاحيات Manager - معظم الصلاحيات
-- =====================================================================

-- كل الصلاحيات عدا:
-- - إدارة المستخدمين (users.*)
-- - إدارة الأدوار (roles.*)
-- - إعدادات النظام (settings.system, settings.backup, settings.restore)

INSERT INTO role_permission (role_id, permission_id, check_status)
SELECT 2, id, 1
FROM permission
WHERE active = 1
AND code NOT LIKE 'users.%'
AND code NOT LIKE 'roles.%'
AND code NOT IN ('settings.system', 'settings.backup', 'settings.restore')
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 5) منح صلاحيات Sales Manager - مدير المبيعات
-- =====================================================================

INSERT INTO role_permission (role_id, permission_id, check_status)
SELECT 3, id, 1
FROM permission
WHERE active = 1
AND (
    code LIKE 'sales.%' OR
    code LIKE 'total.sales.%' OR
    code LIKE 'customers.%' OR
    code LIKE 'items.show%' OR
    code LIKE 'stock.show%' OR
    code LIKE 'treasury.show%' OR
    code LIKE 'reports.sales%' OR
    code LIKE 'reports.customers%' OR
    code LIKE 'reports.profit%' OR
    code LIKE 'reports.dashboard' OR
    code LIKE 'pos.%' OR
    code LIKE 'targets.%' OR
    code LIKE 'shifts.%'
)
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 6) منح صلاحيات Purchase Manager - مدير المشتريات
-- =====================================================================

INSERT INTO role_permission (role_id, permission_id, check_status)
SELECT 4, id, 1
FROM permission
WHERE active = 1
AND (
    code LIKE 'purchase.%' OR
    code LIKE 'total.purchase.%' OR
    code LIKE 'suppliers.%' OR
    code LIKE 'items.show%' OR
    code LIKE 'stock.show%' OR
    code LIKE 'treasury.show%' OR
    code LIKE 'reports.purchases%' OR
    code LIKE 'reports.suppliers%' OR
    code LIKE 'reports.inventory%'
)
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 7) منح صلاحيات Warehouse Manager - مدير المخزن
-- =====================================================================

INSERT INTO role_permission (role_id, permission_id, check_status)
SELECT 5, id, 1
FROM permission
WHERE active = 1
AND (
    code LIKE 'items.%' OR
    code LIKE 'stock.%' OR
    code LIKE 'main.group.%' OR
    code LIKE 'sub.group.%' OR
    code LIKE 'units.%' OR
    code LIKE 'reports.inventory%' OR
    code LIKE 'reports.item.card'
)
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 8) منح صلاحيات Accountant - المحاسب
-- =====================================================================

INSERT INTO role_permission (role_id, permission_id, check_status)
SELECT 6, id, 1
FROM permission
WHERE active = 1
AND (
    code LIKE 'reports.%' OR
    code LIKE 'treasury.%' OR
    code LIKE 'expenses.%' OR
    code LIKE 'customers.account.%' OR
    code LIKE 'suppliers.account.%' OR
    code LIKE 'customers.show' OR
    code LIKE 'suppliers.show' OR
    code LIKE 'sales.show' OR
    code LIKE 'purchase.show' OR
    code LIKE 'total.sales.show%' OR
    code LIKE 'total.purchase.show%' OR
    code LIKE 'employees.salary' OR
    code LIKE 'audit.%'
)
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 9) منح صلاحيات Cashier - الكاشير
-- =====================================================================

INSERT INTO role_permission (role_id, permission_id, check_status)
SELECT 7, id, 1
FROM permission
WHERE active = 1
AND (
    code IN (
        -- مبيعات
        'sales.show',
        'sales.create',
        'sales.print',
        'total.sales.show',
        'total.sales.create',
        'total.sales.print.invoice',
        
        -- مردودات مبيعات
        'sales.return.show',
        'sales.return.create',
        'total.sales.return.show',
        'total.sales.return.create',
        
        -- عملاء (عرض فقط + تحصيل)
        'customers.show',
        'customers.receipt',
        'customers.account.show',
        
        -- أصناف (عرض فقط)
        'items.show',
        'items.show.sell.price',
        
        -- مخزن (عرض فقط)
        'stock.show',
        'stock.show.quantity',
        
        -- خزينة (عرض + إيداع)
        'treasury.show',
        'treasury.show.balance',
        'treasury.deposit',
        
        -- نقاط البيع
        'pos.access',
        'pos.sale',
        'pos.return',
        'pos.discount',
        
        -- ورديات
        'shifts.open',
        'shifts.close',
        'shifts.show',
        
        -- تقارير محدودة
        'reports.dashboard',
        'reports.sales.daily'
    )
)
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 10) منح صلاحيات Data Entry - مدخل البيانات
-- =====================================================================

INSERT INTO role_permission (role_id, permission_id, check_status)
SELECT 8, id, 1
FROM permission
WHERE active = 1
AND (
    code IN (
        -- أصناف
        'items.show',
        'items.create',
        'items.update',
        
        -- مجموعات
        'main.group.show',
        'main.group.create',
        'main.group.update',
        'sub.group.show',
        'sub.group.create',
        'sub.group.update',
        
        -- وحدات
        'units.show',
        'units.create',
        'units.update',
        
        -- عملاء
        'customers.show',
        'customers.create',
        'customers.update',
        
        -- موردين
        'suppliers.show',
        'suppliers.create',
        'suppliers.update',
        
        -- موظفين
        'employees.show',
        'employees.create',
        'employees.update',
        
        -- عرض المخازن
        'stock.show',
        
        -- عرض الخزائن
        'treasury.show'
    )
)
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 2) منح الصلاحيات لـ Admin
-- =====================================================================

INSERT INTO role_permission (role_id, permission_id, check_status)
SELECT 1, id, 1
FROM permission
WHERE code LIKE 'capital.%'
   OR code LIKE 'partner.%'
   OR code LIKE 'profit.%'
   OR code IN ('reports.capital', 'reports.partners', 'reports.profit.distribution')
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 3) منح الصلاحيات للمستخدم Admin مباشرة
-- =====================================================================

INSERT INTO user_permission (user_id, permission_id, check_status)
SELECT 1, id, 1
FROM permission
WHERE code LIKE 'capital.%'
   OR code LIKE 'partner.%'
   OR code LIKE 'profit.%'
   OR code IN ('reports.capital', 'reports.partners', 'reports.profit.distribution')
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 4) منح صلاحيات محدودة للمحاسب (Role ID = 6)
-- =====================================================================

INSERT INTO role_permission (role_id, permission_id, check_status)
SELECT 6, id, 1
FROM permission
WHERE code IN (
               'capital.show',
               'partner.show',
               'partner.share.show',
               'profit.show',
               'profit.view.details',
               'reports.capital',
               'reports.partners',
               'reports.profit.distribution'
    )
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- 5) إضافة الوحدات الجديدة إلى قائمة الوحدات (للمرجعية)
-- =====================================================================

SELECT 'تم إضافة صلاحيات رأس المال والأرباح بنجاح!' AS status;

SELECT module,
       COUNT(*) AS permissions_count
FROM permission
WHERE code LIKE 'capital.%'
   OR code LIKE 'partner.%'
   OR code LIKE 'profit.%'
GROUP BY module;


-- =====================================================================
-- 11) تعيين دور Admin للمستخدم الأول (ID = 1)
-- =====================================================================

-- التأكد من وجود مستخدم Admin
INSERT INTO users (id, user_name, user_pass, user_activity, user_available) VALUES 
(1, 'admin', 'admin', 1, 1)
ON DUPLICATE KEY UPDATE user_name = VALUES(user_name);

-- تعيين دور Admin
INSERT INTO user_role (user_id, role_id) VALUES 
(1, 1)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- =====================================================================
-- 12) إنشاء سجلات صلاحيات فارغة لجميع المستخدمين
-- =====================================================================

-- إضافة جميع الصلاحيات للمستخدم Admin (ID = 1) كصلاحيات مباشرة
INSERT INTO user_permission (user_id, permission_id, check_status)
SELECT 1, id, 1
FROM permission
WHERE active = 1
ON DUPLICATE KEY UPDATE check_status = 1;

-- =====================================================================
-- التحقق من النتائج
-- =====================================================================

-- عدد الأدوار
SELECT 'إحصائيات الأدوار' AS info;
SELECT COUNT(*) AS total_roles FROM roles WHERE active = 1;

-- عدد الصلاحيات لكل دور
SELECT 
    r.name AS role_name,
    COUNT(rp.id) AS total_permissions,
    SUM(CASE WHEN rp.check_status = 1 THEN 1 ELSE 0 END) AS granted_permissions
FROM roles r
LEFT JOIN role_permission rp ON r.id = rp.role_id
WHERE r.active = 1
GROUP BY r.id, r.name
ORDER BY r.id;

-- أدوار المستخدم Admin
SELECT 
    u.user_name,
    GROUP_CONCAT(r.name SEPARATOR ', ') AS roles
FROM users u
LEFT JOIN user_role ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
WHERE u.id = 1
GROUP BY u.id, u.user_name;

SELECT 'تم إنشاء الأدوار الافتراضية بنجاح!' AS status;
