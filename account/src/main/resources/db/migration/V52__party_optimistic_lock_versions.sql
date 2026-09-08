-- Customer and supplier editors compare the row version they loaded. Microsecond
-- precision matches the item and invoice guards and prevents two fast saves from
-- receiving the same version value.

ALTER TABLE custom
    MODIFY COLUMN updated_at TIMESTAMP(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

ALTER TABLE suppliers
    MODIFY COLUMN updated_at TIMESTAMP(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
