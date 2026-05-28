-- =====================================================================
-- Triggers لرأس المال والشركاء
-- =====================================================================

USE account_system_db;

-- =====================================================================
-- 1) Trigger للتحقق من مجموع النسب عند إضافة/تعديل حصة شريك
-- =====================================================================

DROP TRIGGER IF EXISTS before_partner_shares_insert;
DROP TRIGGER IF EXISTS before_partner_shares_update;

DELIMITER $$

CREATE TRIGGER before_partner_shares_insert
BEFORE INSERT ON partner_shares
FOR EACH ROW
BEGIN
    DECLARE v_total_percentage DECIMAL(8,3);
    
    -- حساب مجموع النسب الحالية + النسبة الجديدة
    SELECT COALESCE(SUM(share_percentage), 0) + NEW.share_percentage
    INTO v_total_percentage
    FROM partner_shares
    WHERE capital_id = NEW.capital_id;
    
    -- التحقق من عدم تجاوز 100%
    IF v_total_percentage > 100 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'مجموع نسب الشركاء لا يمكن أن يتجاوز 100%';
    END IF;
    
    -- التحقق من توافق المبلغ مع النسبة
    IF NEW.share_amount != (SELECT total_capital FROM capital WHERE id = NEW.capital_id) * NEW.share_percentage / 100 THEN
        SET NEW.share_amount = (SELECT total_capital FROM capital WHERE id = NEW.capital_id) * NEW.share_percentage / 100;
    END IF;
END$$

CREATE TRIGGER before_partner_shares_update
BEFORE UPDATE ON partner_shares
FOR EACH ROW
BEGIN
    DECLARE v_total_percentage DECIMAL(8,3);
    
    SELECT COALESCE(SUM(share_percentage), 0) - OLD.share_percentage + NEW.share_percentage
    INTO v_total_percentage
    FROM partner_shares
    WHERE capital_id = NEW.capital_id;
    
    IF v_total_percentage > 100 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'مجموع نسب الشركاء لا يمكن أن يتجاوز 100%';
    END IF;
END$$

DELIMITER ;

-- =====================================================================
-- 2) Trigger لحساب الرصيد بعد كل حركة في رأس المال
-- =====================================================================

DROP TRIGGER IF EXISTS before_capital_movements_insert;

DELIMITER $$

CREATE TRIGGER before_capital_movements_insert
BEFORE INSERT ON capital_movements
FOR EACH ROW
BEGIN
    DECLARE v_last_balance DECIMAL(16,2);
    
    -- الحصول على آخر رصيد
    SELECT COALESCE(balance_after, 
           (SELECT total_capital FROM capital WHERE id = NEW.capital_id))
    INTO v_last_balance
    FROM capital_movements
    WHERE capital_id = NEW.capital_id
    ORDER BY id DESC
    LIMIT 1;
    
    -- حساب الرصيد الجديد
    SET NEW.balance_after = v_last_balance + NEW.amount_in - NEW.amount_out;
END$$

DELIMITER ;

-- =====================================================================
-- 3) Trigger لتسجيل Audit عند توزيع الأرباح
-- =====================================================================

DROP TRIGGER IF EXISTS after_profit_loss_distribution_insert;

DELIMITER $$

CREATE TRIGGER after_profit_loss_distribution_insert
AFTER INSERT ON profit_loss_distribution
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (
        table_name,
        record_id,
        action_type,
        user_id,
        new_data,
        source,
        notes
    ) VALUES (
        'profit_loss_distribution',
        NEW.id,
        'INSERT',
        NEW.user_id,
        JSON_OBJECT(
            'capital_id', NEW.capital_id,
            'period_from', NEW.period_from,
            'period_to', NEW.period_to,
            'net_profit_loss', NEW.net_profit_loss,
            'is_profit', NEW.is_profit
        ),
        'TRIGGER',
        'تسجيل توزيع أرباح/خسائر جديد'
    );
END$$

DELIMITER ;

SELECT 'تم إنشاء Triggers رأس المال بنجاح!' AS status;
