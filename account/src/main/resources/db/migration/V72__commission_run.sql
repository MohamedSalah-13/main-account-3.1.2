-- =====================================================================
-- V72 - احتساب العمولة الشهري، مجمَّدًا، وترحيله مرة واحدة. المرحلة ج من docs/delegates-plan.md.
--
-- ### ما الذي يصلحه
--
-- ع-١٠: العمولة كانت تُحسب عند كل عرض، فتعديل نسبة اليوم يعيد كتابة عمولة شهر صُرفت. هنا تُحسب
-- مرة عند الاعتماد وتُكتب **بكل المدخلات التي أنتجت الرقم** - الأساس والتارجت والشرائح كما كانت
-- والشريحة المبلوغة - لا الرقم وحده، حتى يُفهم السطر بعد سنة ولو تغيّرت القاعدة بعده.
--
-- ### لا مسودة
--
-- المعاينة تُحسب حيّة ولا تكتب شيئًا. الاعتماد ينشئ الاحتساب وسطوره في معاملة واحدة، فالصف
-- يولد معتمدًا. الحالة الوحيدة الأخرى `CANCELLED`، ولا يُنتقل إليها إلا لاحتساب **لم يُرحَّل منه
-- شيء**: بعد الترحيل صار الرقم استحقاقًا في حساب شخص، وإلغاؤه كان سيترك ذلك الاستحقاق يتيمًا.
--
-- ### احتساب واحد سارٍ للشهر
--
-- `active_key` عمود مولَّد = السنة*100 + الشهر ما دام الاحتساب `APPROVED`، و`NULL` بعد الإلغاء،
-- وعليه الفهرس الفريد. MySQL يعدّ كل NULL في فهرس فريد قيمة مختلفة، فالملغاة تتكرر بحرية ولا
-- يُعتمد للشهر إلا احتساب واحد. نفس حيلة `expense_budget.month_key` في V66، مقلوبة.
--
-- ### `commission_posting`: «مرة واحدة» قيدًا لا وعدًا
--
-- العمولة تصل حساب المندوب من أحد طريقين: مسيَّر الرواتب - و`PayrollService.approve` يكتب
-- `ENTITLEMENT` واحدًا يشملها - أو ترحيل مباشر بحركة `COMMISSION` لمحل لا يستعمل المسيَّر. الاثنان
-- معًا = عمولة مدفوعة مرتين على الورق. **المفتاح الأساسي هو رقم السطر**، فالمحاولة الثانية
-- تفشل في القاعدة أيًّا كان من كتبها، والقيد يقول «طريق واحد بالضبط».
-- جدول مستقل لا عمودان على السطر: `commission_line` لا يقبل `UPDATE` إطلاقًا.
--
-- ### الـtriggers
--
-- في `R__triggers.sql` (آخر الملف، تحت علامة V72): السطور والترحيلات لا تُعدَّل أبدًا ولا تُحذف
-- إلا تحت `@app_bulk_wipe`؛ والاحتساب لا يتغيّر فيه إلا `APPROVED -> CANCELLED`، ولا حتى ذلك
-- إن وُجد ترحيل. القاعدة نفسها التي كلّفت V43 فشلها: حارس الحذف يأخذ المهرب، حارس التعديل لا.
--
-- لا إجراءات مساعدة: جداول جديدة بقيودها، وصفوف صلاحيات - ولا `ALTER` واحد.
-- =====================================================================

CREATE TABLE IF NOT EXISTS commission_run
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    period_year   SMALLINT                              NOT NULL,
    period_month  TINYINT                               NOT NULL,
    status        VARCHAR(20) DEFAULT 'APPROVED'        NOT NULL,
    notes         VARCHAR(255)                          NULL,
    approved_at   DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id       INT         DEFAULT 1                 NOT NULL COMMENT 'من اعتمد',
    cancelled_at  DATETIME                              NULL,
    cancelled_by  INT                                   NULL,
    cancel_reason VARCHAR(255)                          NULL,
    active_key    INT GENERATED ALWAYS AS
        (IF(status = 'APPROVED', period_year * 100 + period_month, NULL)) STORED
        COMMENT 'فريد: احتساب واحد سارٍ للشهر، والملغاة NULL فتتكرر',
    CONSTRAINT commission_run_active_uk UNIQUE (active_key),
    CONSTRAINT commission_run_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT commission_run_cancelled_by_fk FOREIGN KEY (cancelled_by) REFERENCES users (id),
    CONSTRAINT commission_run_status_chk CHECK (status IN ('APPROVED', 'CANCELLED')),
    CONSTRAINT commission_run_period_chk
        CHECK (period_month BETWEEN 1 AND 12 AND period_year BETWEEN 2000 AND 2999),
    -- الإلغاء يحمل من ألغى ومتى ولماذا، والسارية لا تحمل شيئًا منها.
    CONSTRAINT commission_run_cancel_chk
        CHECK ((status = 'APPROVED' AND cancelled_at IS NULL AND cancelled_by IS NULL)
            OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
                AND cancel_reason IS NOT NULL))
);

-- لا مفتاح جارف واحد هنا عمدًا: سطر عمولة معتمد تاريخ مالي، لا يُجرف مع احتسابه ولا مع موظفه
-- ولا مع قاعدته. والمفتاح إلى القاعدة هو ما يمنع حذف قاعدة حوسب بها شهر (وُعد به في §10.4).
CREATE TABLE IF NOT EXISTS commission_line
(
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    run_id              INT                                  NOT NULL,
    employee_id         INT                                  NOT NULL,
    rule_id             INT                                  NOT NULL,
    basis               VARCHAR(20)                          NOT NULL,
    tier_mode           VARCHAR(20)                          NOT NULL,
    target              DECIMAL(14, 2)                       NOT NULL,
    tiers_snapshot      VARCHAR(120)                         NOT NULL
        COMMENT 'الشرائح كما كانت يوم الاعتماد: من:نسبة|من:نسبة',
    sales               DECIMAL(14, 2)                       NOT NULL,
    sales_returns       DECIMAL(14, 2)                       NOT NULL,
    collected           DECIMAL(14, 2)                       NOT NULL,
    base_amount         DECIMAL(14, 2)                       NOT NULL
        COMMENT 'ما حُسبت عليه العمولة بحسب الأساس. قد يكون سالبًا، والعمولة عندها صفر',
    achievement_percent DECIMAL(9, 2)                        NULL COMMENT 'NULL لقاعدة بلا تارجت',
    tier                TINYINT                              NOT NULL COMMENT 'الشريحة المبلوغة، 0 = لم يبلغ أدناها',
    rate_percent        DECIMAL(5, 2)                        NOT NULL,
    amount              DECIMAL(14, 2)                       NOT NULL,
    date_insert         DATETIME DEFAULT CURRENT_TIMESTAMP   NOT NULL,
    CONSTRAINT commission_line_run_employee_uk UNIQUE (run_id, employee_id),
    CONSTRAINT commission_line_run_fk FOREIGN KEY (run_id) REFERENCES commission_run (id),
    CONSTRAINT commission_line_employees_id_fk FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT commission_line_rule_fk FOREIGN KEY (rule_id) REFERENCES employee_commission_rule (id),
    CONSTRAINT commission_line_basis_chk CHECK (basis IN ('SALES', 'COLLECTED')),
    CONSTRAINT commission_line_mode_chk CHECK (tier_mode IN ('WHOLE', 'MARGINAL')),
    CONSTRAINT commission_line_amount_chk
        CHECK (amount >= 0 AND rate_percent BETWEEN 0 AND 100 AND tier BETWEEN 0 AND 3 AND target >= 0)
);

CREATE INDEX commission_line_employee_idx ON commission_line (employee_id, run_id);

CREATE TABLE IF NOT EXISTS commission_posting
(
    line_id         INT                                NOT NULL PRIMARY KEY
        COMMENT 'المفتاح الأساسي هو «مرة واحدة»: سطر عمولة لا يُرحَّل مرتين أيًّا كان الطريق',
    payroll_run_id  INT                                NULL,
    ledger_entry_id INT                                NULL,
    posted_at       DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id         INT      DEFAULT 1                 NOT NULL,
    CONSTRAINT commission_posting_line_fk FOREIGN KEY (line_id) REFERENCES commission_line (id),
    CONSTRAINT commission_posting_payroll_fk FOREIGN KEY (payroll_run_id) REFERENCES payroll_run (id),
    CONSTRAINT commission_posting_ledger_fk FOREIGN KEY (ledger_entry_id) REFERENCES employee_ledger (id),
    CONSTRAINT commission_posting_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    -- جمع عددي لا مقارنة: `IS NULL` يجيب 0 أو 1 دائمًا، فالقيد لا يمرّ على NULL كما مرّت مسوّدة V70.
    CONSTRAINT commission_posting_one_route_chk
        CHECK ((payroll_run_id IS NULL) + (ledger_entry_id IS NULL) = 1)
);

-- ---------------------------------------------------------------------
-- الصلاحيات
-- ---------------------------------------------------------------------
-- `commission.run.create`: اعتماد شهر - يجمّد أرقامًا. `commission.run.update`: إلغاء احتساب لم
-- يُرحَّل. `commission.run.post`: ترحيله إلى حسابات المناديب، و`POST` عند `AppPermissions` تعني
-- `CRITICAL` وهو مقامها: تكتب استحقاقًا في حساب شخص. رؤية الاحتسابات بـ`commission.show` (V70).
-- الثلاث تُمنح لمن يملك `commission.rule.update`: من يضع القاعدة اليوم هو من يحتسب بها.
-- الأوصاف تحت 50 حرفًا (درس V65).

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('commission.run.create', 'اعتماد احتساب عمولة شهر وتجميده', 'COMMISSION', 'commission.run',
        'CREATE', 'HIGH', 0, 1, 1),
       ('commission.run.update', 'إلغاء احتساب عمولة لم يُرحَّل', 'COMMISSION', 'commission.run',
        'UPDATE', 'HIGH', 0, 1, 1),
       ('commission.run.post', 'ترحيل العمولة المعتمدة إلى حسابات المناديب', 'COMMISSION', 'commission.run',
        'POST', 'CRITICAL', 0, 1, 1)
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
             AND held.permission_key = 'commission.rule.update'
         JOIN auth_permission new_permission
              ON new_permission.permission_key IN
                 ('commission.run.create', 'commission.run.update', 'commission.run.post');
