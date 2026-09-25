package com.hamza.account.features.invoice;

import com.hamza.account.model.base.BasePurchasesAndSales;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An invoice's lines as they stood before a step that adds several of them, so a step that stops part way
 * leaves them as it found them - a bundle's barcode (V87, docs/pricing-and-offers-plan.md ق-ع١٢) puts every
 * component on the invoice or none. Without it a component refused for its stock, or an expiry question
 * dismissed, left the components before it on the invoice at their ordinary prices, with no offer to
 * earn and nothing saying so.
 *
 * <p>A component either becomes a line of its own or is folded into the line of the same item and unit,
 * and the offers the screen previews while a question is open may rewrite any line's discount - so what is
 * kept is which rows there were and every figure of each that those two steps move.</p>
 */
public final class InvoiceLinesCheckpoint {

    private record Row(BasePurchasesAndSales line, double quantity, double discount, double total,
                       double totalAfterDiscount, Integer offerId, BigDecimal offerDiscount,
                       BigDecimal offerQuantity, String offerName) {
    }

    private final List<Row> rows;

    private InvoiceLinesCheckpoint(List<Row> rows) {
        this.rows = rows;
    }

    public static InvoiceLinesCheckpoint of(List<? extends BasePurchasesAndSales> lines) {
        Objects.requireNonNull(lines, "lines");
        List<Row> rows = new ArrayList<>(lines.size());
        for (BasePurchasesAndSales line : lines) {
            if (line != null) {
                rows.add(new Row(line, line.getQuantity(), line.getDiscount(), line.getTotal(),
                        line.getTotal_after_discount(), line.getOfferId(), line.getOfferDiscount(),
                        line.getOfferQuantity(), line.getOfferName()));
            }
        }
        return new InvoiceLinesCheckpoint(List.copyOf(rows));
    }

    /**
     * Takes out every row added since, by identity - two new lines of one item are equal to nothing but
     * themselves - and puts back each kept row's figures.
     */
    public void restore(List<? extends BasePurchasesAndSales> lines) {
        Objects.requireNonNull(lines, "lines");
        Set<BasePurchasesAndSales> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        rows.forEach(row -> kept.add(row.line()));
        lines.removeIf(line -> line != null && !kept.contains(line));
        for (Row row : rows) {
            BasePurchasesAndSales line = row.line();
            line.setQuantity(row.quantity());
            line.setDiscount(row.discount());
            line.setTotal(row.total());
            line.setTotal_after_discount(row.totalAfterDiscount());
            line.setOfferId(row.offerId());
            line.setOfferDiscount(row.offerDiscount());
            line.setOfferQuantity(row.offerQuantity());
            line.setOfferName(row.offerName());
        }
    }
}
