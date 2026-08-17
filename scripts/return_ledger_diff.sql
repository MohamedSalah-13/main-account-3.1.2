-- =====================================================================
-- أثر تصحيح المرتجعات على أرصدة قاعدة بيانات قائمة
-- =====================================================================
--
-- شغّل هذا الملف **قبل** تطبيق R__views.sql المصحَّح لتعرف بالضبط أي أرصدة
-- ستتحرك وبكم. كل استعلام فيه يحسب الفرق من الجداول الخام مباشرة ولا يقرأ أي
-- view، فيعطي نفس الناتج قبل الترقية وبعدها ويصلح للتحقق في الحالتين.
--
-- ما الذي تغيّر:
--
--   القديم في account_customer_table / account_suppliers_table:
--       purchase = IF(invoice_type = 1, total, 0)
--       discount = IF(invoice_type = 1, discount, 0)
--       paid     = paid_from_treasury
--   الجديد:
--       purchase = -total,  discount = -discount,  paid = -paid_from_treasury
--
--   القديم في treasury_balance:
--       output = IF(invoice_type = 1, paid_from_treasury,
--                   total - discount - paid_from_treasury)
--   الجديد:
--       output = paid_from_treasury
--
-- المرتجع النقدي (invoice_type = 1) يُخزَّن بـ paid = الصافي، فتتساوى الصيغتان
-- عنده تماما ولا يتحرك له رصيد. **كل الفروق أدناه لمرتجعات آجلة فقط.**
--
-- المقدار المشترك في كل ما يلي:
--
--       misplaced = total - discount - 2 * paid
--
-- وهو المبلغ الذي كان محمّلا على الجهة الخطأ: خُصم من الخزينة وكان يجب أن يُخصم
-- من حساب الطرف. في الحالة الشائعة (مرتجع آجل بلا أي رد نقدي، paid = 0) يساوي
-- صافي المرتجع كاملا. رصيد الطرف يتحرك بـ -misplaced والخزينة بـ +misplaced في
-- مرتجع المبيعات، وكلاهما بـ -misplaced في مرتجع المشتريات.
--
-- الرصيد في هذا النظام = purchase - discount - paid، أي ما يدين به الطرف.
-- انظر DocumentLedgerEffect و PartyLedgerViewAcceptanceTest.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1) هل توجد مرتجعات آجلة أصلا؟ لو المجموع صفر فلن يتغير عندك شيء.
-- ---------------------------------------------------------------------
SELECT 'مرتجع مبيعات آجل' AS document_kind,
       COUNT(*)           AS document_count,
       ROUND(COALESCE(SUM(total - discount - 2 * paid_from_treasury), 0), 2)
                          AS misplaced_total
FROM total_sales_re
WHERE invoice_type = 2
UNION ALL
SELECT 'مرتجع مشتريات آجل',
       COUNT(*),
       ROUND(COALESCE(SUM(total - discount - 2 * paid_to_treasury), 0), 2)
FROM total_buy_re
WHERE invoice_type = 2;


-- ---------------------------------------------------------------------
-- 2) العملاء الذين سيتغير رصيدهم
--    balance_change السالب = مديونية العميل ستنخفض بهذا المقدار، وهو المبلغ
--    الذي كان النظام يطالبه به رغم أنه ردّ البضاعة.
-- ---------------------------------------------------------------------
SELECT c.id                                              AS customer_id,
       c.name                                            AS customer_name,
       COUNT(*)                                          AS return_count,
       ROUND(SUM(-tsr.paid_from_treasury), 2)            AS old_share,
       ROUND(SUM(-(tsr.total - tsr.discount
                   - tsr.paid_from_treasury)), 2)        AS new_share,
       ROUND(SUM(-(tsr.total - tsr.discount
                   - 2 * tsr.paid_from_treasury)), 2)    AS balance_change
FROM total_sales_re tsr
         JOIN custom c ON c.id = tsr.sup_id
WHERE tsr.invoice_type = 2
  AND ROUND(tsr.total - tsr.discount - 2 * tsr.paid_from_treasury, 2) <> 0
GROUP BY c.id, c.name
ORDER BY ABS(SUM(tsr.total - tsr.discount - 2 * tsr.paid_from_treasury)) DESC;


-- ---------------------------------------------------------------------
-- 3) الموردون الذين سيتغير رصيدهم
--    balance_change السالب = ما ندين به للمورد سينخفض.
-- ---------------------------------------------------------------------
SELECT s.id                                              AS supplier_id,
       s.name                                            AS supplier_name,
       COUNT(*)                                          AS return_count,
       ROUND(SUM(-tbr.paid_to_treasury), 2)              AS old_share,
       ROUND(SUM(-(tbr.total - tbr.discount
                   - tbr.paid_to_treasury)), 2)          AS new_share,
       ROUND(SUM(-(tbr.total - tbr.discount
                   - 2 * tbr.paid_to_treasury)), 2)      AS balance_change
FROM total_buy_re tbr
         JOIN suppliers s ON s.id = tbr.sup_id
WHERE tbr.invoice_type = 2
  AND ROUND(tbr.total - tbr.discount - 2 * tbr.paid_to_treasury, 2) <> 0
GROUP BY s.id, s.name
ORDER BY ABS(SUM(tbr.total - tbr.discount - 2 * tbr.paid_to_treasury)) DESC;


-- ---------------------------------------------------------------------
-- 4) الخزائن التي سيتغير رصيدها
--    balance_change الموجب = الخزينة كانت تُظهر نقدا خارجا لم يخرج فعلا،
--    ورصيدها المعروض سيرتفع بهذا المقدار بعد التصحيح.
-- ---------------------------------------------------------------------
SELECT t.id                         AS treasury_id,
       t.t_name                     AS treasury_name,
       ROUND(SUM(change_amount), 2) AS balance_change
FROM (
         -- مرتجع مبيعات آجل: كان يصرف من الخزينة الجزء الذي يخص حساب العميل
         SELECT treasury_id,
                (total - discount - 2 * paid_from_treasury) AS change_amount
         FROM total_sales_re
         WHERE invoice_type = 2
         UNION ALL
         -- مرتجع مشتريات آجل: كان يقبض في الخزينة الجزء الذي يخص حساب المورد
         SELECT treasury_id,
                -(total - discount - 2 * paid_to_treasury)
         FROM total_buy_re
         WHERE invoice_type = 2
     ) AS moved
         JOIN treasury t ON t.id = moved.treasury_id
GROUP BY t.id, t.t_name
HAVING ROUND(SUM(change_amount), 2) <> 0
ORDER BY ABS(SUM(change_amount)) DESC;


-- ---------------------------------------------------------------------
-- 5) الملخص في سطر واحد
-- ---------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM total_sales_re WHERE invoice_type = 2)
           AS deferred_sales_returns,
       (SELECT COUNT(*) FROM total_buy_re WHERE invoice_type = 2)
           AS deferred_purchase_returns,
       -- ما سيُخصم من مديونيات العملاء
       ROUND((SELECT COALESCE(SUM(total - discount - 2 * paid_from_treasury), 0)
              FROM total_sales_re WHERE invoice_type = 2), 2)
           AS customer_debt_written_off,
       -- ما سيُخصم مما ندين به للموردين
       ROUND((SELECT COALESCE(SUM(total - discount - 2 * paid_to_treasury), 0)
              FROM total_buy_re WHERE invoice_type = 2), 2)
           AS supplier_debt_written_off,
       -- صافي حركة أرصدة الخزائن مجتمعة
       ROUND((SELECT COALESCE(SUM(total - discount - 2 * paid_from_treasury), 0)
              FROM total_sales_re WHERE invoice_type = 2)
           - (SELECT COALESCE(SUM(total - discount - 2 * paid_to_treasury), 0)
              FROM total_buy_re WHERE invoice_type = 2), 2)
           AS net_treasury_correction;


-- ---------------------------------------------------------------------
-- 6) بعد الترقية: تحقّق أن الـ views توافق الحساب اليدوي.
--    شغّل هذا الجزء **بعد** إعادة تشغيل التطبيق ليطبّق Flyway الـ R__views.
--    الناتج يجب أن يكون صفرا في السطرين.
-- ---------------------------------------------------------------------
-- SELECT 'customers' AS side,
--        ROUND(SUM(act.purchase - act.discount - act.paid)
--              - SUM(-(tsr.total - tsr.discount - tsr.paid_from_treasury)), 2)
--            AS view_minus_expected
-- FROM account_customer_table act
--          JOIN total_sales_re tsr ON tsr.id = act.account_num
-- WHERE act.information = 4
-- UNION ALL
-- SELECT 'suppliers',
--        ROUND(SUM(ast.purchase - ast.discount - ast.paid)
--              - SUM(-(tbr.total - tbr.discount - tbr.paid_to_treasury)), 2)
-- FROM account_suppliers_table ast
--          JOIN total_buy_re tbr ON tbr.id = ast.account_num
-- WHERE ast.information = 4;
