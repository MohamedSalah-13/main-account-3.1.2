
-- sales
DROP TRIGGER IF EXISTS before_sales_insert;

DELIMITER |
create trigger before_sales_insert
    before insert
    on sales
    for each row
begin
    set @sum_sel = ROUND(NEW.price * NEW.quantity, 2);
    set @sum_buy = ROUND(NEW.buy_price * NEW.quantity, 2);
    set NEW.type_value = (SELECT value_d from units where unit_id = NEW.type),
        NEW.total_sel_price = @sum_sel,
        NEW.total_buy_price = @sum_buy,
        NEW.total_profit = @sum_sel - @sum_buy;
end;
|
DELIMITER ;

-- sales_re
DROP TRIGGER IF EXISTS before_sales_re_insert;

DELIMITER |
create trigger before_sales_re_insert
    before insert
    on sales_re
    for each row
begin
    set @buyPrice = (SELECT items.buy_price From items where items.id = NEW.item_id);
    set @sum_sel = ROUND(NEW.price * NEW.quantity, 2);
    set @sum_buy = ROUND(@buyPrice * NEW.quantity, 2);
    set NEW.type_value = (SELECT value_d from units where unit_id = NEW.type),
        NEW.buy_price = @buyPrice,
        NEW.total_sel_price = @sum_sel,
        NEW.total_buy_price = @sum_buy,
        NEW.total_profit = @sum_sel - @sum_buy;

end;
|
DELIMITER ;

-- sales_package
DROP TRIGGER IF EXISTS before_sales_package_insert;


