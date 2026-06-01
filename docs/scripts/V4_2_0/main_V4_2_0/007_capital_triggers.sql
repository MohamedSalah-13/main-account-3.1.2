USE account_system_db;

-- =====================================================================
-- 007_capital_triggers.sql
-- Triggers رأس المال والشركاء
-- =====================================================================

DROP TRIGGER IF EXISTS before_partner_shares_insert;
DROP TRIGGER IF EXISTS before_partner_shares_update;
DROP TRIGGER IF EXISTS before_capital_movements_insert;
DROP TRIGGER IF EXISTS after_profit_loss_distribution_insert;

DELIMITER $$

CREATE TRIGGER before_partner_shares_insert
    BEFORE INSERT ON partner_shares
    FOR EACH ROW
BEGIN
    DECLARE v_total_percentage DECIMAL(8,3);

    SELECT COALESCE(SUM(share_percentage), 0) + NEW.share_percentage
    INTO v_total_percentage
    FROM partner_shares
    WHERE capital_id = NEW.capital_id;

    IF v_total_percentage > 100 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'مجموع نسب الشركاء لا يمكن أن يتجاوز 100%';
    END IF;

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

CREATE TRIGGER before_capital_movements_insert
    BEFORE INSERT ON capital_movements
    FOR EACH ROW
BEGIN
    DECLARE v_last_balance DECIMAL(16,2);

    SELECT COALESCE(
                   (
                       SELECT balance_after
                       FROM capital_movements
                       WHERE capital_id = NEW.capital_id
                       ORDER BY id DESC
                       LIMIT 1
                   ),
                   (
                       SELECT total_capital
                       FROM capital
                       WHERE id = NEW.capital_id
                   ),
                   0
           )
    INTO v_last_balance;

    SET NEW.amount_in = COALESCE(NEW.amount_in, 0);
    SET NEW.amount_out = COALESCE(NEW.amount_out, 0);
    SET NEW.balance_after = v_last_balance + NEW.amount_in - NEW.amount_out;
END$$

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

SELECT '007_capital_triggers.sql executed successfully' AS status;