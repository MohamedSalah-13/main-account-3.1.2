package com.hamza.account.features.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;

import java.math.BigDecimal;
import java.util.Objects;

/** One immutable, display-ready line from a saved invoice. */
public record InvoiceDetailsLine(
        int itemId,
        String itemName,
        String unitName,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal total,
        BigDecimal discount,
        BigDecimal net) {

    public InvoiceDetailsLine {
        itemName = itemName == null ? "" : itemName;
        unitName = unitName == null ? "" : unitName;
        quantity = Objects.requireNonNull(quantity, "quantity");
        price = Objects.requireNonNull(price, "price");
        total = Objects.requireNonNull(total, "total");
        discount = Objects.requireNonNull(discount, "discount");
        net = Objects.requireNonNull(net, "net");
    }

    public static InvoiceDetailsLine from(BasePurchasesAndSales line) {
        Objects.requireNonNull(line, "line");
        var item = line.getItems();
        var unit = line.getUnitsType();
        int itemId = item == null ? line.getNumItem() : item.getId();
        String itemName = item == null ? "" : item.getNameItem();
        String unitName = unit == null ? "" : unit.getUnit_name();
        BigDecimal total = MoneyMath.money(line.getTotal());
        BigDecimal discount = MoneyMath.money(line.getDiscount());
        return new InvoiceDetailsLine(itemId, itemName, unitName,
                MoneyMath.decimal(line.getQuantity()), MoneyMath.money(line.getPrice()),
                total, discount, MoneyMath.subtract(total, discount));
    }
}
