
-- purchase
DROP TRIGGER IF EXISTS before_purchase_insert;

DELIMITER |
create trigger before_purchase_insert
    before insert
    on purchase
    for each row
begin
    set NEW.type_value = (SELECT value_d from units where unit_id = NEW.type);
end;
|
DELIMITER ;

-- purchase_re
DROP TRIGGER IF EXISTS before_purchase_re_insert;

DELIMITER |
create trigger before_purchase_re_insert
    before insert
    on purchase_re
    for each row
begin
    set NEW.type_value = (SELECT value_d from units where unit_id = NEW.type);
end;
|
DELIMITER ;

