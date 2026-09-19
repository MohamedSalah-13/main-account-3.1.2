-- =====================================================================
-- V70 - قاعدة عمولة المندوب، مؤرَّخة. المرحلة أ من docs/delegates-plan.md.
--
-- ### ما الذي تستبدله
--
-- `targeted_sales` (V1) صف واحد للمندوب **بلا شهر ولا سنة**، والـview `target_delegate` تربطه
-- بكل شهر في التاريخ - فتعديل التارجت اليوم يغيّر عمولة يناير الماضي. وسلسلة `IF` فيها تنتهي
-- بـ`rate_3` بلا أي شرط، فمندوب حقّق 1% من تارجته يأخذ النسبة الثالثة على كل ما باع، و
-- `target_ratio3` عمود يُقرأ ولا يُستعمل. (ع-٩ وع-١٠ في docs/employees-plan.md.)
--
-- القاعدة هنا تحمل تاريخ سريانها، كما فعل V57 بالراتب في `employee_compensation` ولنفس السبب:
-- الشهر يُحاسَب بالقاعدة السارية في أول يوم منه، وقاعدة بتاريخ مستقبلي هي الحالة العادية
-- (تارجت أبريل يُتفق عليه في مارس).
--
-- ### الشرائح
--
-- من واحدة إلى ثلاث، **الأدنى حدًّا أولًا**، وكل شريحة «من هذه النسبة من التارجت، بهذه
-- النسبة». **تحت أدنى حدّ = لا عمولة.** الشريحتان الثانية والثالثة NULL إن لم تُستعملا،
-- والقيود أدناه تقول ما يقوله `CommissionTiers` في Java: حدود تصاعدية بصرامة، نسب بين 0
-- و100، وقاعدة بلا تارجت (target = 0) لا تعني شيئًا إلا نسبة ثابتة - شريحة واحدة من الصفر.
-- القيد والصنف يقولان الشيء نفسه عمدًا: القاعدة تُكتب من الشاشة اليوم، ومن أي مكان غدًا.
--
-- ### ما لا تفعله هذه الهجرة
--
-- **لا تترجم صفوف `targeted_sales`.** قيمها الافتراضية (`target_ratio2 = 0`،
-- `target_ratio3 = 0`) لا تُقرأ كحدود تصاعدية دون تخمين ما قصده صاحبها، وشاشتها أُزيلت في
-- 7516ac4 فلا أحد يحرّرها منذ زمن. الجدول والـview يبقيان للقراءة التاريخية ولا يُحذفان -
-- فيهما صفوف عملاء - ولا يقرأ منهما كود جديد. درس V67: الربط بالتخمين أسوأ من عدم الربط.
--
-- ولا جداول الاحتساب (`commission_run`/`commission_line`) ولا مندوب التحصيل: كلٌّ في هجرة
-- مرحلته. هجرة مشحونة لا تُعدَّل، فلا يُشحن جدول قبل أن يوجد الكود الذي يثبت شكله.
--
-- لا إجراءات مساعدة هنا: جدول جديد بقيوده، وصفوف صلاحيات - ولا `ALTER` واحد.
-- =====================================================================

-- ON DELETE CASCADE عمدًا، ولذلك لا يُعلَن في DeleteRegistry (القاعدة: يُعلَن ما لا يَجرف
-- وحده). قواعد العمولة تخص صف الموظف كما يخصّه تاريخ رواتبه؛ والموظف الذي على فاتورة لا
-- يُحذف أصلًا، فلا تضيع قاعدة حوسب بها أحد.
CREATE TABLE IF NOT EXISTS employee_commission_rule
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    employee_id    INT                                     NOT NULL,
    effective_from DATE                                    NOT NULL,
    basis          VARCHAR(20)   DEFAULT 'SALES'           NOT NULL
        COMMENT 'SALES = صافي فواتيره ناقص مرتجعات الشهر؛ COLLECTED = ما دخل الخزينة من عملائه',
    tier_mode      VARCHAR(20)   DEFAULT 'WHOLE'           NOT NULL
        COMMENT 'WHOLE = نسبة أعلى شريحة على المبلغ كله؛ MARGINAL = كل شريحة على ما يقع داخلها',
    target         DECIMAL(14, 2) DEFAULT 0                NOT NULL
        COMMENT 'تارجت الشهر. صفر = بلا تارجت، ولا يصح معه إلا شريحة واحدة من الصفر',
    tier1_from     DECIMAL(6, 2) DEFAULT 0                 NOT NULL
        COMMENT 'نسبة من التارجت تبدأ عندها الشريحة. تحتها لا عمولة',
    tier1_rate     DECIMAL(5, 2) DEFAULT 0                 NOT NULL,
    tier2_from     DECIMAL(6, 2)                           NULL,
    tier2_rate     DECIMAL(5, 2)                           NULL,
    tier3_from     DECIMAL(6, 2)                           NULL,
    tier3_rate     DECIMAL(5, 2)                           NULL,
    notes          VARCHAR(200)                            NULL,
    date_insert    DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id        INT           DEFAULT 1                 NOT NULL,
    CONSTRAINT employee_commission_rule_uk UNIQUE (employee_id, effective_from),
    CONSTRAINT employee_commission_rule_employees_id_fk
        FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT employee_commission_rule_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT employee_commission_rule_basis_chk CHECK (basis IN ('SALES', 'COLLECTED')),
    CONSTRAINT employee_commission_rule_mode_chk CHECK (tier_mode IN ('WHOLE', 'MARGINAL')),
    CONSTRAINT employee_commission_rule_target_chk CHECK (target >= 0),
    CONSTRAINT employee_commission_rule_tier1_chk
        CHECK (tier1_from >= 0 AND tier1_rate BETWEEN 0 AND 100),
    -- الشريحة إما موجودة بحدّها ونسبتها أو غائبة بهما معًا، والثالثة لا توجد بلا ثانية.
    -- `IS NOT NULL` مكتوبة صراحة في كل فرع: قيد CHECK في MySQL **يمرّ حين تكون نتيجته NULL**،
    -- فبدونها شريحة لها حدّ وبلا نسبة (`5 > 0 AND NULL BETWEEN ...`) تُقبل بصمت.
    CONSTRAINT employee_commission_rule_tier2_chk
        CHECK ((tier2_from IS NULL AND tier2_rate IS NULL)
            OR (tier2_from IS NOT NULL AND tier2_rate IS NOT NULL
                AND tier2_from > tier1_from AND tier2_rate BETWEEN 0 AND 100)),
    CONSTRAINT employee_commission_rule_tier3_chk
        CHECK ((tier3_from IS NULL AND tier3_rate IS NULL)
            OR (tier3_from IS NOT NULL AND tier3_rate IS NOT NULL AND tier2_from IS NOT NULL
                AND tier3_from > tier2_from AND tier3_rate BETWEEN 0 AND 100)),
    CONSTRAINT employee_commission_rule_flat_chk
        CHECK (target > 0 OR (tier1_from = 0 AND tier2_from IS NULL))
);

-- ---------------------------------------------------------------------
-- الصلاحيتان
-- ---------------------------------------------------------------------
-- `commission.show`: رؤية القواعد والنسب. النسبة كالراتب - لا تُجلب لمن لا يملك رؤيتها -
-- فتُمنح لمن يملك `employees.show.salary` اليوم.
-- `commission.rule.update`: وضع القاعدة وتغييرها. تُمنح لمن يملك `employee.salary.change`:
-- من يقرّر راتب مندوب اليوم هو من يقرّر عمولته. والإدارة تسحب أيًّا منهما بعد ذلك.
-- على نمط V34 وV55 وV62: لا أحد يفقد قدرة عند الترقية. والأوصاف تحت 50 حرفًا (درس V65).

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('commission.show', 'عرض قواعد عمولة المناديب ونسبها', 'COMMISSION', 'commission',
        'SHOW', 'LOW', 0, 1, 1),
       ('commission.rule.update', 'وضع قاعدة عمولة المندوب وتعديلها', 'COMMISSION', 'commission.rule',
        'UPDATE', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, new_permission.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'employees.show.salary'
         JOIN auth_permission new_permission
              ON new_permission.permission_key = 'commission.show';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, new_permission.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'employee.salary.change'
         JOIN auth_permission new_permission
              ON new_permission.permission_key = 'commission.rule.update';
