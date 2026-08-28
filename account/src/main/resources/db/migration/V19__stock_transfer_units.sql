-- A transfer line has carried only a base-unit quantity: nothing scaled it if the
-- person moving stock counted in cartons rather than pieces, and nothing recorded
-- which unit they actually counted in. Every other document family stores both the
-- unit (`type`) and the factor it was worth at the time (`type_value`), exactly so a
-- later change to the item's own factor cannot rewrite what an old line meant - see
-- V5__item_units.sql. stock_transfer_list gets the same two columns, with the same
-- defaults, so every row written before this migration reads back as one base unit
-- moved at a factor of 1, which is what it already was.
ALTER TABLE stock_transfer_list
    ADD COLUMN type       INT            DEFAULT 1 NOT NULL AFTER item_id,
    ADD COLUMN type_value DECIMAL(14, 3) DEFAULT 1 NOT NULL AFTER quantity;

ALTER TABLE stock_transfer_list
    ADD CONSTRAINT stock_transfer_list_units_unit_id_fk FOREIGN KEY (type) REFERENCES units (unit_id);
