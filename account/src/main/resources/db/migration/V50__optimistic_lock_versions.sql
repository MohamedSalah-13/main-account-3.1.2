-- Item and document editors carry the version they read and update only that
-- version. Fractional precision matters on a busy till: two edits inside one
-- second must not compare equal and silently let the later save overwrite the first.
ALTER TABLE items
    MODIFY updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL
        ON UPDATE CURRENT_TIMESTAMP(6);

ALTER TABLE total_sales
    MODIFY updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL
        ON UPDATE CURRENT_TIMESTAMP(6);

ALTER TABLE total_buy
    MODIFY updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL
        ON UPDATE CURRENT_TIMESTAMP(6);

ALTER TABLE total_sales_re
    MODIFY updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL
        ON UPDATE CURRENT_TIMESTAMP(6);

ALTER TABLE total_buy_re
    MODIFY updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL
        ON UPDATE CURRENT_TIMESTAMP(6);
