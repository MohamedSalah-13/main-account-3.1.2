package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A source invoice as {@link ReturnGuard} needs to see it: what it sold, per item, in
 * base units - and nothing about what has already been returned against it, which is a
 * separate map because it depends on which return is being saved (see
 * {@link ReturnableRepository#alreadyReturnedBaseQuantities}, not this).
 *
 * @param sourceType the document this return reverses - {@code SALES} or {@code PURCHASE}
 * @param sourceId   its invoice number
 * @param soldByItem base quantity sold, summed per item
 */
public record ReturnableDocument(
        DocumentType sourceType, int sourceId, Map<Integer, Double> soldByItem) {

    public ReturnableDocument {
        Objects.requireNonNull(sourceType, "sourceType");
        soldByItem = Map.copyOf(soldByItem == null ? Map.of() : soldByItem);
    }

    public static ReturnableDocument of(
            DocumentType sourceType, int sourceId, List<ReturnableRepository.SoldLine> lines) {
        Map<Integer, Double> byItem = new LinkedHashMap<>();
        for (ReturnableRepository.SoldLine line : lines) {
            byItem.merge(line.itemId(), line.baseQuantity(), Double::sum);
        }
        return new ReturnableDocument(sourceType, sourceId, byItem);
    }

    /** Whether the source invoice sold this item at all. */
    public boolean sold(int itemId) {
        return soldByItem.containsKey(itemId);
    }

    /** What is left to return for one item, after what other returns have already taken. */
    public double remaining(int itemId, Map<Integer, Double> alreadyReturned) {
        double sold = soldByItem.getOrDefault(itemId, 0.0);
        double returned = alreadyReturned.getOrDefault(itemId, 0.0);
        return sold - returned;
    }
}
