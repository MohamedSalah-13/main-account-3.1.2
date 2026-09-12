-- =====================================================================
-- V60 - الحضور والإجازات: ما يغذي خصم الغياب وساعات الأجر بالساعة.
--
-- العقد في docs/employees-plan.md §6 (المرحلة د) وق-٨.
--
-- ### يوم واحد لكل موظف، ولا يومان
--
-- `UNIQUE(employee_id, work_date)` هو كل شيء هنا. بدونه يصير ليوم واحد صفّان - حاضر
-- وغائب - ويصير خصم الغياب رقما يعتمد على أي صف قرأه الاستعلام أولا. والشبكة الشهرية
-- تكتب بـ`ON DUPLICATE KEY UPDATE`، فتصحيح يوم هو كتابة اليوم نفسه لا صف ثان.
--
-- ### الإجازة المعتمدة ليست غيابا، والقرار في النوع لا في الكود
--
-- `leave_type.is_paid` هو ما يقرر إن كان يوم الإجازة يُخصم أم لا، و`annual_limit` هو
-- الحد السنوي - **رقمان في جدول، لا في Java**: نظام الإجازات يختلف بالشركة وبالبلد،
-- ورقم مكتوب في الكود هو رقم يخص شركة واحدة. وهو نفس المبدأ الذي جعل `payroll_rule`
-- جدولا في V59 و`jobs` صفوفا في V57 بدل `UsersType`.
--
-- **ولا يُقرأ `is_paid` نسخةً على صف الحضور.** صف الحضور يحمل `leave_type_id`، والقرار
-- يُقرأ بالربط - لأن نسخ القرار وقت الكتابة يعني أن تغيير النوع لاحقا لا يصحح الأيام
-- المكتوبة، وتصير في القاعدة إجابتان لسؤال واحد. وهو العطل الذي أنفق نظام الأطراف شهرا
-- في إزالته (V15).
--
-- ### الحالات خمس، وكل واحدة تجيب سؤالا مختلفا في المسيَّر
--
-- | الحالة | في المسيَّر |
-- |---|---|
-- | `PRESENT` | يوم عمل، وساعاته تغذي الأجر بالساعة |
-- | `ABSENT` | غياب بلا أجر - يُخصم |
-- | `LEAVE` | إجازة: تُخصم إن كان نوعها غير مدفوع، ولا تُخصم إن كان مدفوعا |
-- | `WEEKEND` | راحة المحل الأسبوعية - لا حضور ولا غياب |
-- | `HOLIDAY` | عطلة رسمية - كذلك |
--
-- و**`WEEKEND` و`HOLIDAY` ليستا غيابا ولا حضورا**: الراتب الشهري لا ينقص بهما، والأجر
-- اليومي لا يزيد بهما. الخلط بينهما وبين `PRESENT` يدفع أجر يوم لم يُعمل؛ والخلط بينهما
-- وبين `ABSENT` يخصم يوم راحة.
--
-- ### أسبوع العمل إعداد المحل لا الجهاز (ق-٨)
--
-- أيام الراحة وساعات اليوم تُحفظ في `app_setting` عبر `SharedSettingKeys`، لأن جهازين
-- يختلفان على يوم الجمعة **ليس اختلاف تفضيل بل عطل** - وهو معيار العضوية المكتوب في
-- `docs/multi-device-plan.md`، لا «هل المشاركة مريحة». والقيمتان تُزرعان هنا بما يوافق
-- الأغلبية في السوق العربي (الجمعة راحة، ثماني ساعات)، ويُعدّلهما صاحب المحل.
--
-- وبداية الأسبوع تُقرأ من `StatementPeriod.FIRST_DAY_OF_WEEK` في Java - تعريف واحد
-- للأسبوع في النظام كله، **ولا ثانٍ مكتوب في MySQL** بـ`WEEK()` ومُعاملاتها.
--
-- ### الإجراءات المساعدة
--
-- معرَّفة هنا ومحذوفة في آخر الملف: `add_index_if_missing` يحذفه V1 في سطره 994.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_attendance_index$$
CREATE PROCEDURE add_attendance_index(IN t_name VARCHAR(64), IN i_name VARCHAR(64),
                                      IN i_columns TEXT)
BEGIN
    DECLARE index_exists INT;

    SELECT COUNT(*)
    INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND INDEX_NAME = i_name;

    IF index_exists = 0 THEN
        SET @query = CONCAT('CREATE INDEX ', i_name, ' ON ', t_name, ' (', i_columns, ')');
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1) أنواع الإجازات - الحد والدفع رقمان في جدول
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS leave_type
(
    id           INT AUTO_INCREMENT PRIMARY KEY,
    type_name    VARCHAR(50)                          NOT NULL,
    is_paid      TINYINT(1) DEFAULT 1                 NOT NULL,
    annual_limit INT        DEFAULT 0                 NOT NULL,
    is_active    TINYINT(1) DEFAULT 1                 NOT NULL,
    notes        VARCHAR(255)                         NULL,
    date_insert  DATETIME   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP  DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id      INT        DEFAULT 1                 NOT NULL,
    CONSTRAINT leave_type_name_uk UNIQUE (type_name),
    CONSTRAINT leave_type_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT leave_type_limit_chk CHECK (annual_limit >= 0)
);

-- حد صفر يعني «بلا حد مكتوب»، لا «ممنوعة»: لا يُفرض على أحد رقم عند الترقية.
INSERT IGNORE INTO leave_type(type_name, is_paid, annual_limit, notes)
VALUES ('إجازة سنوية', 1, 0, 'مدفوعة - لا تُخصم من الراتب'),
       ('إجازة مرضية', 1, 0, 'مدفوعة - لا تُخصم من الراتب'),
       ('إجازة بدون أجر', 0, 0, 'تُخصم من الراتب كيوم غياب');

-- ---------------------------------------------------------------------
-- 2) الحضور - يوم واحد لكل موظف
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS attendance
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    employee_id   INT                                     NOT NULL,
    work_date     DATE                                    NOT NULL,
    status        VARCHAR(20)                             NOT NULL,
    hours         DECIMAL(6, 2) DEFAULT 0                 NOT NULL,
    leave_type_id INT                                     NULL,
    notes         VARCHAR(255)                            NULL,
    date_insert   DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id       INT           DEFAULT 1                 NOT NULL,
    CONSTRAINT attendance_employee_date_uk UNIQUE (employee_id, work_date),
    CONSTRAINT attendance_employees_id_fk FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT attendance_leave_type_id_fk FOREIGN KEY (leave_type_id) REFERENCES leave_type (id),
    CONSTRAINT attendance_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT attendance_status_chk
        CHECK (status IN ('PRESENT', 'ABSENT', 'LEAVE', 'WEEKEND', 'HOLIDAY')),
    CONSTRAINT attendance_hours_chk CHECK (hours >= 0 AND hours <= 24),
    -- يوم إجازة بلا نوع لا يعرف أحد إن كان يُخصم أم لا، ونوعٌ على يوم ليس إجازة معنى
    -- معلَّق على صف لا يقرؤه شيء. القاعدة ترفض الاثنين بدل أن يكتشفهما المسيَّر.
    CONSTRAINT attendance_leave_type_chk
        CHECK ((status = 'LEAVE' AND leave_type_id IS NOT NULL)
            OR (status <> 'LEAVE' AND leave_type_id IS NULL))
);

-- المسيَّر يقرأ (موظف، مدى تواريخ)، والمفتاح الفريد يغطي ذلك بالفعل؛ والشبكة تقرأ شهرا
-- كاملا لكل الموظفين، فتحتاج التاريخ أولا.
CALL add_attendance_index('attendance', 'attendance_date_idx', 'work_date');

-- ---------------------------------------------------------------------
-- 3) طلب الإجازة ودورة اعتماده
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS leave_request
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    employee_id   INT                                    NOT NULL,
    leave_type_id INT                                    NOT NULL,
    date_from     DATE                                   NOT NULL,
    date_to       DATE                                   NOT NULL,
    status        VARCHAR(20) DEFAULT 'PENDING'          NOT NULL,
    reason        VARCHAR(255)                           NULL,
    decision_note VARCHAR(255)                           NULL,
    date_insert   DATETIME    DEFAULT CURRENT_TIMESTAMP  NOT NULL,
    updated_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id       INT         DEFAULT 1                  NOT NULL,
    decided_at    DATETIME                               NULL,
    decided_by    INT                                    NULL,
    CONSTRAINT leave_request_employees_id_fk FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT leave_request_leave_type_id_fk FOREIGN KEY (leave_type_id) REFERENCES leave_type (id),
    CONSTRAINT leave_request_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT leave_request_decided_by_fk FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT leave_request_status_chk CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT leave_request_range_chk CHECK (date_to >= date_from)
);

CALL add_attendance_index('leave_request', 'leave_request_employee_idx',
                          'employee_id, date_from');

-- ---------------------------------------------------------------------
-- 4) أسبوع العمل - إعداد المحل (ق-٨)
-- ---------------------------------------------------------------------
--
-- أرقام `DayOfWeek` في Java: الاثنين 1 ... الأحد 7. فالجمعة 5.

INSERT IGNORE INTO app_setting(setting_key, setting_value)
VALUES ('attendance.weekend.days', '5'),
       ('attendance.hours.per.day', '8');

-- ---------------------------------------------------------------------
-- 5) الصلاحيات
-- ---------------------------------------------------------------------
--
-- **`attendance.record` منفصل عن `attendance.show` عمدا، و`leave.request` عن
-- `leave.approve`**: تسجيل الحضور عمل يومي لموظف استقبال، والاعتماد قرار. وهو نفس
-- التفريق الذي أقامته V55 وV58 وV59.
--
-- وكل مفتاح يُمنح لمن يملك ما يقابله اليوم (V34، V35، V55، V57، V58، V59).

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('attendance.show', 'عرض شبكة الحضور',
        'EMPLOYEES', 'attendance', 'SHOW', 'LOW', 0, 1, 1),
       ('attendance.record', 'تسجيل الحضور والغياب',
        'EMPLOYEES', 'attendance', 'RECORD', 'MEDIUM', 0, 1, 1),
       ('leave.request', 'تسجيل طلب إجازة',
        'EMPLOYEES', 'leave', 'REQUEST', 'LOW', 0, 1, 1),
       ('leave.approve', 'اعتماد أو رفض طلب إجازة',
        'EMPLOYEES', 'leave', 'APPROVE', 'MEDIUM', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key        = VALUES(module_key),
                        resource_key      = VALUES(resource_key),
                        action_key        = VALUES(action_key),
                        risk_level        = VALUES(risk_level),
                        system_permission = 1,
                        enabled           = 1;

-- رؤية الشبكة وتسجيل الطلب لمن يفتح شاشة الموظفين اليوم: كلاهما لا يقرر شيئا.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'employee.show'
         JOIN auth_permission granted
              ON granted.permission_key IN ('attendance.show', 'leave.request');

-- والتسجيل والاعتماد لمن يعدّل موظفا اليوم.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'employee.update'
         JOIN auth_permission granted
              ON granted.permission_key IN ('attendance.record', 'leave.approve');

DROP PROCEDURE IF EXISTS add_attendance_index;
