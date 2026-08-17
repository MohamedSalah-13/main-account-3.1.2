package com.hamza.account.features.stockledger;

import com.hamza.account.document.DocumentType;

/**
 * What one of the four invoice document types is called in {@code stock_movements}.
 * <p>
 * {@link DocumentType} spells sales and purchases in the plural
 * ({@code SALES}/{@code PURCHASE_RETURN}); the table's {@code CHECK} constraints spell
 * a sale singular ({@code 'SALE'}) - so {@code DocumentType.name()} cannot be written
 * straight into either column, and this is the one place that difference is bridged.
 * The same four strings satisfy both {@code stock_movements_type_chk} and
 * {@code stock_movements_reference_type_chk}, so one mapping serves both
 * {@link StockMovement#movementType()} and {@link StockMovement#referenceType()}.
 */
final class DocumentMovementType {

    private DocumentMovementType() {
    }

    static MovementType of(DocumentType documentType) {
        return switch (documentType) {
            case SALES -> MovementType.SALE;
            case SALES_RETURN -> MovementType.SALE_RETURN;
            case PURCHASE -> MovementType.PURCHASE;
            case PURCHASE_RETURN -> MovementType.PURCHASE_RETURN;
        };
    }
}
