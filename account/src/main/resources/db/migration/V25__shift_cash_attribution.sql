-- Exact shift attribution for every table that contributes cash to treasury_balance.
-- NULL preserves legacy rows and movements created while shifts are disabled/optional.
ALTER TABLE total_buy ADD COLUMN shift_id INT NULL;
ALTER TABLE total_buy_re ADD COLUMN shift_id INT NULL;
ALTER TABLE total_sales ADD COLUMN shift_id INT NULL;
ALTER TABLE total_sales_re ADD COLUMN shift_id INT NULL;
ALTER TABLE customers_accounts ADD COLUMN shift_id INT NULL;
ALTER TABLE suppliers_accounts ADD COLUMN shift_id INT NULL;
ALTER TABLE expenses_details ADD COLUMN shift_id INT NULL;
ALTER TABLE treasury_deposit_expenses ADD COLUMN shift_id INT NULL;
ALTER TABLE treasury_transfers
    ADD COLUMN source_shift_id INT NULL,
    ADD COLUMN destination_shift_id INT NULL;

ALTER TABLE total_buy ADD CONSTRAINT fk_total_buy_shift
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT;
ALTER TABLE total_buy_re ADD CONSTRAINT fk_total_buy_re_shift
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT;
ALTER TABLE total_sales ADD CONSTRAINT fk_total_sales_shift
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT;
ALTER TABLE total_sales_re ADD CONSTRAINT fk_total_sales_re_shift
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT;
ALTER TABLE customers_accounts ADD CONSTRAINT fk_customers_accounts_shift
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT;
ALTER TABLE suppliers_accounts ADD CONSTRAINT fk_suppliers_accounts_shift
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT;
ALTER TABLE expenses_details ADD CONSTRAINT fk_expenses_details_shift
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT;
ALTER TABLE treasury_deposit_expenses ADD CONSTRAINT fk_treasury_cash_shift
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT;
ALTER TABLE treasury_transfers ADD CONSTRAINT fk_treasury_transfer_source_shift
    FOREIGN KEY (source_shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT;
ALTER TABLE treasury_transfers ADD CONSTRAINT fk_treasury_transfer_destination_shift
    FOREIGN KEY (destination_shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT;
