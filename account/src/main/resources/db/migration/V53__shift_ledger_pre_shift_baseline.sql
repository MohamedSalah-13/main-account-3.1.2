-- A document can carry cash that belongs to no shift, and the journal has to be able to say so.
--
-- `ensureBaseline` writes a CREATE row for a document the journal has never seen, so that the
-- deltas that follow add up to the document's live value - the invariant
-- `ShiftReconciliationDao.countSourceMismatches` checks. When the document was created inside a
-- shift that baseline belongs to that shift, and `origin_shift_id` says which.
--
-- But a document created before shifts were switched on has no such shift, and the baseline was
-- being filed under whichever shift happened to be open when somebody edited it. Measured on a
-- real database: editing an invoice from a month earlier inside today's shift wrote CREATE +505
-- and then UPDATE -50, so today's drawer was expected to hold 455 that had been collected in
-- August. The cashier counting at close would be short by the whole amount, and the till would
-- look robbed.
--
-- The cash is real and the document's total is right; what is false is that any shift holds it.
-- So the column becomes nullable and NULL means exactly that: recorded, reconciled, owned by no
-- drawer. Every per-shift read filters `shift_id = ?` and so ignores it, while the per-document
-- reconciliation sums across shifts and still balances.
--
-- Forward-only, deliberately. Rows already written under the old rule are left where they are:
-- rewriting them would move the expected balance of shifts that are already closed, reconciled
-- and in some cases settled against a counted drawer. An install that has hit this can identify
-- them with the query in section 11 of docs/shift-plan.md.

ALTER TABLE shift_cash_ledger
    MODIFY COLUMN shift_id INT NULL
        COMMENT 'the shift that owns this movement; NULL only on a CREATE baseline for cash that moved before any shift existed';
