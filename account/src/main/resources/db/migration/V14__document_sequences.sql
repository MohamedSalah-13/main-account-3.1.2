-- Atomic, transactional numbering for the four invoice families.
-- current_value starts at the largest number already present, so the first allocation
-- returns MAX + 1 without renumbering any historical document.

CREATE TABLE IF NOT EXISTS document_sequences
(
    document_type VARCHAR(32) PRIMARY KEY,
    current_value BIGINT    DEFAULT 0                 NOT NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT document_sequences_value_chk CHECK (current_value >= 0)
);

INSERT INTO document_sequences(document_type, current_value)
SELECT seeded.document_type, seeded.seed_value
FROM (SELECT 'SALES' AS document_type,
             COALESCE(MAX(invoice_number), 0) AS seed_value
      FROM total_sales) AS seeded
ON DUPLICATE KEY UPDATE current_value =
        GREATEST(document_sequences.current_value, seeded.seed_value);

INSERT INTO document_sequences(document_type, current_value)
SELECT seeded.document_type, seeded.seed_value
FROM (SELECT 'SALES_RETURN' AS document_type,
             COALESCE(MAX(id), 0) AS seed_value
      FROM total_sales_re) AS seeded
ON DUPLICATE KEY UPDATE current_value =
        GREATEST(document_sequences.current_value, seeded.seed_value);

INSERT INTO document_sequences(document_type, current_value)
SELECT seeded.document_type, seeded.seed_value
FROM (SELECT 'PURCHASE' AS document_type,
             COALESCE(MAX(invoice_number), 0) AS seed_value
      FROM total_buy) AS seeded
ON DUPLICATE KEY UPDATE current_value =
        GREATEST(document_sequences.current_value, seeded.seed_value);

INSERT INTO document_sequences(document_type, current_value)
SELECT seeded.document_type, seeded.seed_value
FROM (SELECT 'PURCHASE_RETURN' AS document_type,
             COALESCE(MAX(id), 0) AS seed_value
      FROM total_buy_re) AS seeded
ON DUPLICATE KEY UPDATE current_value =
        GREATEST(document_sequences.current_value, seeded.seed_value);
