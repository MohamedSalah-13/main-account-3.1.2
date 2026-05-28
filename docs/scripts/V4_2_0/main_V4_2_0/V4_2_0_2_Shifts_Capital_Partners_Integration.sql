-- =====================================================================
-- V4_2_0_2: تحسينات الورديات + رأس المال + نسب الشركاء
-- =====================================================================

USE account_system_db;

-- =====================================================================
-- 1) تعديل جدول الورديات لربطه بالخزينة
-- =====================================================================

ALTER TABLE user_shifts 
ADD COLUMN treasury_id INT DEFAULT 1 NOT NULL AFTER user_id,
ADD COLUMN shift_status VARCHAR(20) DEFAULT 'OPEN' NOT NULL AFTER is_open,
ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL AFTER notes,
ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

-- إضافة Foreign Key للخزينة
ALTER TABLE user_shifts
ADD CONSTRAINT user_shifts_treasury_fk 
    FOREIGN KEY (treasury_id) REFERENCES treasury(id);

-- إضافة constraint للحالة
ALTER TABLE user_shifts
ADD CONSTRAINT user_shifts_status_chk 
    CHECK (shift_status IN ('OPEN', 'CLOSED', 'FORCE_CLOSED'));

-- إضافة Index للأداء
CREATE INDEX idx_user_shifts_treasury ON user_shifts(treasury_id, open_time);
CREATE INDEX idx_user_shifts_status ON user_shifts(shift_status, is_open);

-- =====================================================================
-- 2) إضافة shift_id للفواتير والمعاملات
-- =====================================================================

-- فواتير المبيعات
ALTER TABLE total_sales
ADD COLUMN shift_id INT NULL AFTER user_id;

ALTER TABLE total_sales
ADD CONSTRAINT total_sales_shift_fk 
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_total_sales_shift ON total_sales(shift_id);

-- مرتجعات المبيعات
ALTER TABLE total_sales_re
ADD COLUMN shift_id INT NULL AFTER user_id;

ALTER TABLE total_sales_re
ADD CONSTRAINT total_sales_re_shift_fk 
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_total_sales_re_shift ON total_sales_re(shift_id);

-- المصروفات
ALTER TABLE expenses_details
ADD COLUMN shift_id INT NULL AFTER user_id;

ALTER TABLE expenses_details
ADD CONSTRAINT expenses_details_shift_fk 
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_expenses_details_shift ON expenses_details(shift_id);

-- الإيداعات والمسحوبات
ALTER TABLE treasury_deposit_expenses
ADD COLUMN shift_id INT NULL AFTER user_id;

ALTER TABLE treasury_deposit_expenses
ADD CONSTRAINT treasury_deposit_expenses_shift_fk 
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_treasury_deposit_expenses_shift ON treasury_deposit_expenses(shift_id);

-- حسابات العملاء (الدفعات)
ALTER TABLE customers_accounts
ADD COLUMN shift_id INT NULL AFTER user_id;

ALTER TABLE customers_accounts
ADD CONSTRAINT customers_accounts_shift_fk 
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_customers_accounts_shift ON customers_accounts(shift_id);

-- =====================================================================
-- 3) جدول رأس المال والشركاء
-- =====================================================================

-- جدول الشركاء
CREATE TABLE IF NOT EXISTS partners
(
    id              INT AUTO_INCREMENT PRIMARY KEY,
    partner_name    VARCHAR(100)                             NOT NULL,
    partner_code    VARCHAR(50)                              NULL,
    national_id     VARCHAR(50)                              NULL,
    phone           VARCHAR(50)                              NULL,
    email           VARCHAR(100)                             NULL,
    address         VARCHAR(255)                             NULL,
    join_date       DATE                                     NOT NULL,
    exit_date       DATE                                     NULL,
    is_active       TINYINT        DEFAULT 1                 NOT NULL,
    notes           TEXT                                     NULL,
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id         INT            DEFAULT 1                 NOT NULL,
    
    CONSTRAINT partners_name_uk UNIQUE (partner_name),
    CONSTRAINT partners_code_uk UNIQUE (partner_code),
    CONSTRAINT partners_active_chk CHECK (is_active IN (0, 1)),
    CONSTRAINT partners_users_fk FOREIGN KEY (user_id) REFERENCES users(id)
);

-- جدول رأس المال
CREATE TABLE IF NOT EXISTS capital
(
    id               INT AUTO_INCREMENT PRIMARY KEY,
    capital_name     VARCHAR(100)                             NOT NULL,
    total_capital    DECIMAL(16, 2)                           NOT NULL,
    start_date       DATE                                     NOT NULL,
    end_date         DATE                                     NULL,
    is_active        TINYINT        DEFAULT 1                 NOT NULL,
    notes            TEXT                                     NULL,
    created_at       DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id          INT            DEFAULT 1                 NOT NULL,
    
    CONSTRAINT capital_name_uk UNIQUE (capital_name),
    CONSTRAINT capital_active_chk CHECK (is_active IN (0, 1)),
    CONSTRAINT capital_amount_chk CHECK (total_capital > 0),
    CONSTRAINT capital_users_fk FOREIGN KEY (user_id) REFERENCES users(id)
);

-- جدول حصص الشركاء
CREATE TABLE IF NOT EXISTS partner_shares
(
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    capital_id           INT                                      NOT NULL,
    partner_id           INT                                      NOT NULL,
    share_amount         DECIMAL(16, 2)                           NOT NULL,
    share_percentage     DECIMAL(6, 3)                            NOT NULL,
    profit_percentage    DECIMAL(6, 3)                            NOT NULL,
    loss_percentage      DECIMAL(6, 3)                            NOT NULL,
    contribution_date    DATE                                     NOT NULL,
    is_managing_partner  TINYINT        DEFAULT 0                 NOT NULL,
    notes                TEXT                                     NULL,
    created_at           DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id              INT            DEFAULT 1                 NOT NULL,
    
    CONSTRAINT partner_shares_capital_fk FOREIGN KEY (capital_id) REFERENCES capital(id) ON DELETE CASCADE,
    CONSTRAINT partner_shares_partner_fk FOREIGN KEY (partner_id) REFERENCES partners(id) ON DELETE CASCADE,
    CONSTRAINT partner_shares_uk UNIQUE (capital_id, partner_id),
    CONSTRAINT partner_shares_amount_chk CHECK (share_amount > 0),
    CONSTRAINT partner_shares_percentage_chk CHECK (share_percentage > 0 AND share_percentage <= 100),
    CONSTRAINT partner_shares_profit_chk CHECK (profit_percentage >= 0 AND profit_percentage <= 100),
    CONSTRAINT partner_shares_loss_chk CHECK (loss_percentage >= 0 AND loss_percentage <= 100),
    CONSTRAINT partner_shares_managing_chk CHECK (is_managing_partner IN (0, 1)),
    CONSTRAINT partner_shares_users_fk FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_partner_shares_capital ON partner_shares(capital_id);
CREATE INDEX idx_partner_shares_partner ON partner_shares(partner_id);

-- جدول حركات رأس المال
CREATE TABLE IF NOT EXISTS capital_movements
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    capital_id       INT                                      NOT NULL,
    partner_id       INT                                      NULL,
    movement_date    DATE                                     NOT NULL,
    movement_type    VARCHAR(30)                              NOT NULL,
    amount_in        DECIMAL(16, 2) DEFAULT 0                 NOT NULL,
    amount_out       DECIMAL(16, 2) DEFAULT 0                 NOT NULL,
    balance_after    DECIMAL(16, 2) DEFAULT 0                 NOT NULL,
    reference_type   VARCHAR(50)                              NULL,
    reference_id     BIGINT                                   NULL,
    notes            TEXT                                     NULL,
    created_at       DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id          INT            DEFAULT 1                 NOT NULL,
    
    CONSTRAINT capital_movements_capital_fk FOREIGN KEY (capital_id) REFERENCES capital(id) ON DELETE CASCADE,
    CONSTRAINT capital_movements_partner_fk FOREIGN KEY (partner_id) REFERENCES partners(id),
    CONSTRAINT capital_movements_users_fk FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT capital_movements_amount_chk 
        CHECK ((amount_in > 0 AND amount_out = 0) OR (amount_in = 0 AND amount_out > 0)),
    CONSTRAINT capital_movements_type_chk 
        CHECK (movement_type IN (
            'INITIAL_CAPITAL',
            'ADDITIONAL_INVESTMENT',
            'WITHDRAWAL',
            'PROFIT_DISTRIBUTION',
            'LOSS_DISTRIBUTION',
            'PARTNER_EXIT',
            'PARTNER_JOIN',
            'ADJUSTMENT'
        ))
);

CREATE INDEX idx_capital_movements_capital ON capital_movements(capital_id, movement_date);
CREATE INDEX idx_capital_movements_partner ON capital_movements(partner_id, movement_date);
CREATE INDEX idx_capital_movements_reference ON capital_movements(reference_type, reference_id);

-- جدول توزيع الأرباح والخسائر
CREATE TABLE IF NOT EXISTS profit_loss_distribution
(
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    capital_id            INT                                      NOT NULL,
    distribution_date     DATE                                     NOT NULL,
    period_from           DATE                                     NOT NULL,
    period_to             DATE                                     NOT NULL,
    total_revenue         DECIMAL(16, 2) DEFAULT 0                 NOT NULL,
    total_expenses        DECIMAL(16, 2) DEFAULT 0                 NOT NULL,
    net_profit_loss       DECIMAL(16, 2) DEFAULT 0                 NOT NULL,
    is_profit             TINYINT        DEFAULT 1                 NOT NULL,
    distribution_status   VARCHAR(20)    DEFAULT 'PENDING'         NOT NULL,
    distributed_at        DATETIME                                 NULL,
    notes                 TEXT                                     NULL,
    created_at            DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id               INT            DEFAULT 1                 NOT NULL,
    
    CONSTRAINT profit_loss_distribution_capital_fk FOREIGN KEY (capital_id) REFERENCES capital(id) ON DELETE CASCADE,
    CONSTRAINT profit_loss_distribution_users_fk FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT profit_loss_distribution_is_profit_chk CHECK (is_profit IN (0, 1)),
    CONSTRAINT profit_loss_distribution_status_chk 
        CHECK (distribution_status IN ('PENDING', 'DISTRIBUTED', 'CANCELLED'))
);

CREATE INDEX idx_profit_loss_distribution_capital ON profit_loss_distribution(capital_id, distribution_date);

-- جدول تفاصيل توزيع الأرباح/الخسائر على الشركاء
CREATE TABLE IF NOT EXISTS profit_loss_distribution_details
(
    id                     INT AUTO_INCREMENT PRIMARY KEY,
    distribution_id        INT                                      NOT NULL,
    partner_id             INT                                      NOT NULL,
    partner_share_percent  DECIMAL(6, 3)                            NOT NULL,
    partner_profit_percent DECIMAL(6, 3)                            NOT NULL,
    partner_amount         DECIMAL(16, 2)                           NOT NULL,
    paid_amount            DECIMAL(16, 2) DEFAULT 0                 NOT NULL,
    payment_status         VARCHAR(20)    DEFAULT 'UNPAID'          NOT NULL,
    payment_date           DATE                                     NULL,
    treasury_id            INT                                      NULL,
    notes                  TEXT                                     NULL,
    
    CONSTRAINT profit_loss_details_distribution_fk 
        FOREIGN KEY (distribution_id) REFERENCES profit_loss_distribution(id) ON DELETE CASCADE,
    CONSTRAINT profit_loss_details_partner_fk 
        FOREIGN KEY (partner_id) REFERENCES partners(id),
    CONSTRAINT profit_loss_details_treasury_fk 
        FOREIGN KEY (treasury_id) REFERENCES treasury(id),
    CONSTRAINT profit_loss_details_payment_status_chk 
        CHECK (payment_status IN ('UNPAID', 'PARTIAL', 'PAID'))
);

CREATE INDEX idx_profit_loss_details_distribution ON profit_loss_distribution_details(distribution_id);
CREATE INDEX idx_profit_loss_details_partner ON profit_loss_distribution_details(partner_id);

-- =====================================================================
-- 4) Views للورديات المحسّنة
-- =====================================================================

-- View تقرير الورديات الشامل
CREATE OR REPLACE VIEW v_user_shifts_report AS
SELECT 
    us.id,
    us.user_id,
    u.user_name,
    us.treasury_id,
    t.t_name AS treasury_name,
    us.open_time,
    us.close_time,
    us.open_balance,
    us.close_balance,
    us.total_sales,
    us.total_sales_returns,
    us.total_expenses,
    us.total_deposits,
    us.total_withdrawals,
    us.expected_balance,
    us.difference,
    us.invoices_count,
    us.is_open,
    us.shift_status,
    us.notes,
    -- حسابات إضافية
    (us.total_sales - us.total_sales_returns) AS net_sales,
    (us.close_balance - us.open_balance) AS net_change,
    CASE 
        WHEN us.difference = 0 THEN 'متوازن'
        WHEN us.difference > 0 THEN 'زيادة'
        ELSE 'عجز'
    END AS balance_status,
    -- مدة الوردية بالساعات
    CASE 
        WHEN us.close_time IS NOT NULL THEN 
            TIMESTAMPDIFF(HOUR, us.open_time, us.close_time)
        ELSE 
            TIMESTAMPDIFF(HOUR, us.open_time, NOW())
    END AS shift_duration_hours,
    us.created_at,
    us.updated_at
FROM user_shifts us
JOIN users u ON u.id = us.user_id
JOIN treasury t ON t.id = us.treasury_id;

-- View تفاصيل فواتير الوردية
CREATE OR REPLACE VIEW v_shift_invoices_details AS
SELECT 
    us.id AS shift_id,
    us.user_id,
    u.user_name,
    us.treasury_id,
    'SALE' AS transaction_type,
    ts.invoice_number AS reference_number,
    ts.invoice_date AS transaction_date,
    ts.total AS amount,
    ts.discount,
    ts.paid_up AS cash_amount,
    c.name AS customer_name,
    ts.notes
FROM user_shifts us
JOIN users u ON u.id = us.user_id
JOIN total_sales ts ON ts.shift_id = us.id
JOIN custom c ON c.id = ts.sup_code

UNION ALL

SELECT 
    us.id,
    us.user_id,
    u.user_name,
    us.treasury_id,
    'SALE_RETURN',
    tsr.id,
    tsr.invoice_date,
    tsr.total,
    tsr.discount,
    tsr.paid_from_treasury,
    c.name,
    tsr.notes
FROM user_shifts us
JOIN users u ON u.id = us.user_id
JOIN total_sales_re tsr ON tsr.shift_id = us.id
JOIN custom c ON c.id = tsr.sup_id

UNION ALL

SELECT 
    us.id,
    us.user_id,
    u.user_name,
    us.treasury_id,
    'EXPENSE',
    ed.id,
    ed.date,
    ed.amount,
    0,
    ed.amount,
    e.expenses_name,
    ed.notes
FROM user_shifts us
JOIN users u ON u.id = us.user_id
JOIN expenses_details ed ON ed.shift_id = us.id
JOIN expenses e ON e.id = ed.type_code

UNION ALL

SELECT 
    us.id,
    us.user_id,
    u.user_name,
    us.treasury_id,
    CASE WHEN tde.deposit_or_expenses = 1 THEN 'DEPOSIT' ELSE 'WITHDRAWAL' END,
    tde.id,
    tde.date_inter,
    tde.amount,
    0,
    tde.amount,
    tde.statement,
    tde.description_data
FROM user_shifts us
JOIN users u ON u.id = us.user_id
JOIN treasury_deposit_expenses tde ON tde.shift_id = us.id

ORDER BY transaction_date DESC;

-- =====================================================================
-- 5) Views لرأس المال والشركاء
-- =====================================================================

-- View ملخص رأس المال
CREATE OR REPLACE VIEW v_capital_summary AS
SELECT 
    c.id,
    c.capital_name,
    c.total_capital,
    c.start_date,
    c.end_date,
    c.is_active,
    COUNT(DISTINCT ps.partner_id) AS partners_count,
    COALESCE(SUM(ps.share_amount), 0) AS total_shares,
    COALESCE(SUM(cm.amount_in), 0) AS total_investments,
    COALESCE(SUM(cm.amount_out), 0) AS total_withdrawals,
    (c.total_capital + COALESCE(SUM(cm.amount_in), 0) - COALESCE(SUM(cm.amount_out), 0)) AS current_capital,
    c.notes,
    c.created_at
FROM capital c
LEFT JOIN partner_shares ps ON ps.capital_id = c.id
LEFT JOIN capital_movements cm ON cm.capital_id = c.id
GROUP BY c.id, c.capital_name, c.total_capital, c.start_date, c.end_date, 
         c.is_active, c.notes, c.created_at;

-- View تفاصيل حصص الشركاء
CREATE OR REPLACE VIEW v_partner_shares_details AS
SELECT 
    ps.id,
    ps.capital_id,
    c.capital_name,
    ps.partner_id,
    p.partner_name,
    p.partner_code,
    ps.share_amount,
    ps.share_percentage,
    ps.profit_percentage,
    ps.loss_percentage,
    ps.is_managing_partner,
    ps.contribution_date,
    -- حساب رأس المال الحالي للشريك
    ps.share_amount + 
    COALESCE((SELECT SUM(cm.amount_in - cm.amount_out) 
              FROM capital_movements cm 
              WHERE cm.capital_id = ps.capital_id 
              AND cm.partner_id = ps.partner_id), 0) AS current_share_value,
    p.is_active AS partner_active,
    p.phone,
    p.email,
    ps.notes
FROM partner_shares ps
JOIN capital c ON c.id = ps.capital_id
JOIN partners p ON p.id = ps.partner_id;

-- View حركات رأس المال مع التفاصيل
CREATE OR REPLACE VIEW v_capital_movements_details AS
SELECT 
    cm.id,
    cm.capital_id,
    c.capital_name,
    cm.partner_id,
    p.partner_name,
    cm.movement_date,
    cm.movement_type,
    cm.amount_in,
    cm.amount_out,
    cm.balance_after,
    cm.reference_type,
    cm.reference_id,
    cm.notes,
    u.user_name AS created_by,
    cm.created_at
FROM capital_movements cm
JOIN capital c ON c.id = cm.capital_id
LEFT JOIN partners p ON p.id = cm.partner_id
JOIN users u ON u.id = cm.user_id;

-- View توزيع الأرباح والخسائر
CREATE OR REPLACE VIEW v_profit_loss_distribution_report AS
SELECT 
    pld.id,
    pld.capital_id,
    c.capital_name,
    pld.distribution_date,
    pld.period_from,
    pld.period_to,
    DATEDIFF(pld.period_to, pld.period_from) + 1 AS period_days,
    pld.total_revenue,
    pld.total_expenses,
    pld.net_profit_loss,
    pld.is_profit,
    CASE WHEN pld.is_profit = 1 THEN 'ربح' ELSE 'خسارة' END AS profit_loss_type,
    pld.distribution_status,
    pld.distributed_at,
    -- عدد الشركاء المستفيدين
    COUNT(pldd.id) AS partners_count,
    -- المبلغ الموزع
    COALESCE(SUM(pldd.partner_amount), 0) AS total_distributed,
    -- المبلغ المدفوع
    COALESCE(SUM(pldd.paid_amount), 0) AS total_paid,
    -- المتبقي
    COALESCE(SUM(pldd.partner_amount - pldd.paid_amount), 0) AS remaining,
    pld.notes,
    pld.created_at
FROM profit_loss_distribution pld
JOIN capital c ON c.id = pld.capital_id
LEFT JOIN profit_loss_distribution_details pldd ON pldd.distribution_id = pld.id
GROUP BY pld.id, pld.capital_id, c.capital_name, pld.distribution_date,
         pld.period_from, pld.period_to, pld.total_revenue, pld.total_expenses,
         pld.net_profit_loss, pld.is_profit, pld.distribution_status,
         pld.distributed_at, pld.notes, pld.created_at;

-- View تفاصيل توزيع الأرباح على الشركاء
CREATE OR REPLACE VIEW v_partner_profit_distribution AS
SELECT 
    pldd.id,
    pldd.distribution_id,
    pld.distribution_date,
    pld.period_from,
    pld.period_to,
    pldd.partner_id,
    p.partner_name,
    p.partner_code,
    pldd.partner_share_percent,
    pldd.partner_profit_percent,
    pld.net_profit_loss AS total_profit_loss,
    pldd.partner_amount,
    pldd.paid_amount,
    (pldd.partner_amount - pldd.paid_amount) AS remaining_amount,
    pldd.payment_status,
    pldd.payment_date,
    pldd.treasury_id,
    t.t_name AS treasury_name,
    CASE WHEN pld.is_profit = 1 THEN 'ربح' ELSE 'خسارة' END AS type,
    pldd.notes
FROM profit_loss_distribution_details pldd
JOIN profit_loss_distribution pld ON pld.id = pldd.distribution_id
JOIN partners p ON p.id = pldd.partner_id
LEFT JOIN treasury t ON t.id = pldd.treasury_id;

-- =====================================================================
-- 6) Stored Procedures للورديات
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_get_shift_summary;

DELIMITER $$

CREATE PROCEDURE sp_get_shift_summary(
    IN p_shift_id INT
)
BEGIN
    SELECT 
        us.*,
        u.user_name,
        t.t_name AS treasury_name,
        (us.total_sales - us.total_sales_returns) AS net_sales,
        (us.close_balance - us.open_balance) AS net_change,
        -- عدد الفواتير حسب النوع
        (SELECT COUNT(*) FROM total_sales WHERE shift_id = p_shift_id) AS sales_count,
        (SELECT COUNT(*) FROM total_sales_re WHERE shift_id = p_shift_id) AS sales_return_count,
        (SELECT COUNT(*) FROM expenses_details WHERE shift_id = p_shift_id) AS expenses_count
    FROM user_shifts us
    JOIN users u ON u.id = us.user_id
    JOIN treasury t ON t.id = us.treasury_id
    WHERE us.id = p_shift_id;
END$$

DELIMITER ;

-- =====================================================================
-- 7) Stored Procedures لرأس المال
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_calculate_partner_profit;

DELIMITER $$

-- حساب حصة شريك من الأرباح/الخسائر
CREATE PROCEDURE sp_calculate_partner_profit(
    IN p_partner_id INT,
    IN p_capital_id INT,
    IN p_net_profit_loss DECIMAL(16,2),
    IN p_is_profit TINYINT,
    OUT p_partner_amount DECIMAL(16,2),
    OUT p_partner_percentage DECIMAL(6,3)
)
BEGIN
    DECLARE v_profit_percentage DECIMAL(6,3);
    DECLARE v_loss_percentage DECIMAL(6,3);
    
    -- الحصول على نسب الشريك
    SELECT profit_percentage, loss_percentage
    INTO v_profit_percentage, v_loss_percentage
    FROM partner_shares
    WHERE partner_id = p_partner_id 
    AND capital_id = p_capital_id;
    
    -- حساب المبلغ حسب نوع التوزيع
    IF p_is_profit = 1 THEN
        SET p_partner_percentage = v_profit_percentage;
        SET p_partner_amount = (p_net_profit_loss * v_profit_percentage) / 100;
    ELSE
        SET p_partner_percentage = v_loss_percentage;
        SET p_partner_amount = (p_net_profit_loss * v_loss_percentage) / 100;
    END IF;
END$$

DELIMITER ;

-- =====================================================================

DROP PROCEDURE IF EXISTS sp_distribute_profit_loss;

DELIMITER $$

-- توزيع الأرباح/الخسائر على الشركاء
CREATE PROCEDURE sp_distribute_profit_loss(
    IN p_distribution_id INT,
    IN p_user_id INT
)
BEGIN
    DECLARE v_capital_id INT;
    DECLARE v_net_profit_loss DECIMAL(16,2);
    DECLARE v_is_profit TINYINT;
    DECLARE v_partner_id INT;
    DECLARE v_partner_amount DECIMAL(16,2);
    DECLARE v_partner_percentage DECIMAL(6,3);
    DECLARE v_done INT DEFAULT FALSE;
    
    DECLARE partner_cursor CURSOR FOR
        SELECT ps.partner_id
        FROM partner_shares ps
        JOIN profit_loss_distribution pld ON pld.capital_id = ps.capital_id
        WHERE pld.id = p_distribution_id;
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;
    
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- الحصول على بيانات التوزيع
    SELECT capital_id, net_profit_loss, is_profit
    INTO v_capital_id, v_net_profit_loss, v_is_profit
    FROM profit_loss_distribution
    WHERE id = p_distribution_id;
    
    -- فتح المؤشر
    OPEN partner_cursor;
    
    read_loop: LOOP
        FETCH partner_cursor INTO v_partner_id;
        IF v_done THEN
            LEAVE read_loop;
        END IF;
        
        -- حساب حصة الشريك
        CALL sp_calculate_partner_profit(
            v_partner_id,
            v_capital_id,
            v_net_profit_loss,
            v_is_profit,
            v_partner_amount,
            v_partner_percentage
        );
        
        -- إدخال التفاصيل
        INSERT INTO profit_loss_distribution_details (
            distribution_id,
            partner_id,
            partner_share_percent,
            partner_profit_percent,
            partner_amount,
            paid_amount,
            payment_status
        ) VALUES (
            p_distribution_id,
            v_partner_id,
            (SELECT share_percentage FROM partner_shares 
             WHERE partner_id = v_partner_id AND capital_id = v_capital_id),
            v_partner_percentage,
            v_partner_amount,
            0,
            'UNPAID'
        );
        
        -- تسجيل الحركة في رأس المال
        INSERT INTO capital_movements (
            capital_id,
            partner_id,
            movement_date,
            movement_type,
            amount_in,
            amount_out,
            balance_after,
            reference_type,
            reference_id,
            notes,
            user_id
        ) VALUES (
            v_capital_id,
            v_partner_id,
            CURDATE(),
            IF(v_is_profit = 1, 'PROFIT_DISTRIBUTION', 'LOSS_DISTRIBUTION'),
            IF(v_is_profit = 1, v_partner_amount, 0),
            IF(v_is_profit = 0, v_partner_amount, 0),
            0, -- سيتم حسابه في trigger
            'PROFIT_LOSS_DISTRIBUTION',
            p_distribution_id,
            CONCAT('توزيع ', IF(v_is_profit = 1, 'أرباح', 'خسائر'), ' فترة ', 
                   (SELECT CONCAT(period_from, ' إلى ', period_to) 
                    FROM profit_loss_distribution WHERE id = p_distribution_id)),
            p_user_id
        );
        
    END LOOP;
    
    CLOSE partner_cursor;
    
    -- تحديث حالة التوزيع
    UPDATE profit_loss_distribution
    SET distribution_status = 'DISTRIBUTED',
        distributed_at = NOW()
    WHERE id = p_distribution_id;
    
    COMMIT;
    
    SELECT 'تم توزيع الأرباح/الخسائر بنجاح' AS message;
END$$

DELIMITER ;

-- =====================================================================
-- 8) إضافة بيانات تجريبية (اختياري)
-- =====================================================================

-- إدخال شريك تجريبي
INSERT IGNORE INTO partners (id, partner_name, partner_code, join_date, is_active, user_id)
VALUES (1, 'الشريك الأول', 'P001', CURDATE(), 1, 1);

-- إدخال رأس مال تجريبي
INSERT IGNORE INTO capital (id, capital_name, total_capital, start_date, is_active, user_id)
VALUES (1, 'رأس المال الأساسي', 100000.00, CURDATE(), 1, 1);

-- إدخال حصة تجريبية
INSERT IGNORE INTO partner_shares (
    capital_id, partner_id, share_amount, share_percentage, 
    profit_percentage, loss_percentage, contribution_date, user_id
)
VALUES (1, 1, 100000.00, 100.00, 100.00, 100.00, CURDATE(), 1);

-- =====================================================================
-- 9) تسجيل نسخة التحديث
-- =====================================================================

INSERT INTO database_migrations (version, description, executed_at)
VALUES ('4.2.0.2', 'Shifts-Capital-Partners Integration', NOW());

-- =====================================================================
-- نهاية السكريبت
-- =====================================================================

SELECT 'تم تنفيذ تحديث الورديات ورأس المال والشركاء بنجاح!' AS status;
