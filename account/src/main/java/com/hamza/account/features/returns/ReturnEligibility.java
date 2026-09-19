package com.hamza.account.features.returns;

import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Whether a proposed set of return lines may be saved against a {@link ReturnableDocument} -
 * pure, no database and no JavaFX, so it is testable the way {@code InvoiceSaveValidator}
 * and {@code InvoiceStockGuard} are.
 */
public final class ReturnEligibility {

    private static final double QUANTITY_EPSILON = 0.000_001;

    private ReturnEligibility() {
    }

    public static Decision check(ReturnableDocument source, Map<Integer, Double> alreadyReturned,
                                 List<LineQuantity> proposedLines, ReturnPolicy policy) {
        Map<Integer, Double> proposedByItem = new LinkedHashMap<>();
        Map<Integer, String> namesByItem = new LinkedHashMap<>();
        for (LineQuantity line : proposedLines) {
            proposedByItem.merge(line.itemId(), line.baseQuantity(), Double::sum);
            namesByItem.putIfAbsent(line.itemId(), line.itemName());
        }

        for (Map.Entry<Integer, Double> entry : proposedByItem.entrySet()) {
            int itemId = entry.getKey();
            double proposed = entry.getValue();
            // The person reading this knows the item by its name; the id is ours.
            String name = namesByItem.getOrDefault(itemId, null);
            Object shown = name == null || name.isBlank() ? itemId : name;

            if (!source.sold(itemId)) {
                return Decision.refused(message(
                        "return.error.not.on.source", shown, source.sourceId()));
            }

            if (policy.allowExceedingSource()) {
                continue;
            }
            double remaining = source.remaining(itemId, alreadyReturned);
            if (proposed - remaining > QUANTITY_EPSILON) {
                return Decision.refused(message(
                        "return.error.exceeds.remaining", shown, quantityText(remaining),
                        quantityText(proposed)));
            }
        }
        return Decision.allowed();
    }

    private static String quantityText(double quantity) {
        return BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString();
    }

    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }

    /**
     * One item's proposed return quantity, already converted to base units.
     *
     * @param itemName what to call it in a refusal; the id is used when it is absent, which is
     *                 what every refusal said before - "الصنف رقم 2" in front of somebody holding
     *                 the item itself
     */
    public record LineQuantity(int itemId, String itemName, double baseQuantity) {

        public LineQuantity(int itemId, double baseQuantity) {
            this(itemId, null, baseQuantity);
        }
    }

    /** {@link #allowed()} or {@link #refused}, the latter carrying the Arabic reason. */
    public sealed interface Decision {

        static Decision allowed() {
            return Allowed.INSTANCE;
        }

        static Decision refused(String message) {
            return new Refused(message);
        }

        boolean isAllowed();

        record Allowed() implements Decision {
            static final Allowed INSTANCE = new Allowed();

            @Override
            public boolean isAllowed() {
                return true;
            }
        }

        record Refused(String message) implements Decision {
            @Override
            public boolean isAllowed() {
                return false;
            }
        }
    }
}
