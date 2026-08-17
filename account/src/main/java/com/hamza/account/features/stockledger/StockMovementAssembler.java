package com.hamza.account.features.stockledger;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.stockcount.StockCount;
import com.hamza.account.features.stockcount.StockCountLine;
import com.hamza.account.model.base.BasePurchasesAndSales;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link StockMovement} rows one invoice save produces - the dual-write
 * counterpart to {@code InvoiceLineAssembler}, which builds the detached line rows the
 * same save writes to {@code purchase}/{@code sales}/{@code purchase_re}/{@code sales_re}.
 * <p>
 * A line's quantity and unit factor come straight off {@code persistedLines}, not
 * recomputed: {@code line.getUnitsType().getValue()} is exactly the same value the four
 * DAOs write to their own {@code type_value} column (see e.g. {@code SalesDao.getData}),
 * so this mirrors what was already persisted rather than deriving it a second way.
 */
public final class StockMovementAssembler {

    private StockMovementAssembler() {
    }

    /**
     * What {@link StockMovement#referenceType()} is for a document of this type - the
     * same string {@link #assemble} writes onto every movement it builds. Exposed so a
     * caller can look up (and delete) a document's existing movements before writing its
     * new ones, without duplicating {@link DocumentMovementType}'s mapping.
     */
    public static String referenceTypeFor(DocumentType documentType) {
        return DocumentMovementType.of(documentType).name();
    }

    public static <T extends BasePurchasesAndSales> List<StockMovement> assemble(
            DocumentType documentType, int stockId, long invoiceNumber, LocalDate movementDate,
            List<T> persistedLines, Integer userId) {
        MovementType movementType = DocumentMovementType.of(documentType);
        String referenceType = movementType.name();
        boolean stockIn = documentType.stockDirection() == DocumentType.Direction.IN;

        List<StockMovement> movements = new ArrayList<>(persistedLines.size());
        for (T line : persistedLines) {
            double quantity = line.getQuantity();
            movements.add(new StockMovement(
                    line.getItems().getId(), stockId, movementDate, movementType,
                    stockIn ? quantity : 0, stockIn ? 0 : quantity,
                    line.getUnitsType().getUnit_id(), line.getUnitsType().getValue(),
                    referenceType, invoiceNumber, userId));
        }
        return movements;
    }

    /**
     * One movement per line the count actually changed, signed by
     * {@link StockCountLine#difference()} - the same expression
     * {@code adjustment_agg} in {@code R__views.sql} sums, so the ledger and the view
     * agree on what a posted count moved without either reading the other.
     * <p>
     * A line with no difference contributes no row: {@code quantity_in}/{@code
     * quantity_out} cannot both be zero (see {@link StockMovement}'s own guard), and a
     * count that changed nothing for an item genuinely moved nothing.
     * <p>
     * Recorded in the unit the line was counted in, not the base unit: {@code
     * difference()} is already in base units, so it is converted back through the
     * line's own {@code typeValue} - the same in-original-unit-plus-factor shape every
     * other movement in this class uses.
     */
    public static List<StockMovement> forStockCount(StockCount count) {
        List<StockCountLine> changed = count.linesWithDifference();
        List<StockMovement> movements = new ArrayList<>(changed.size());
        for (StockCountLine line : changed) {
            double baseDifference = line.difference();
            boolean stockIn = baseDifference > 0;
            double quantity = Math.abs(baseDifference) / line.getTypeValue();
            movements.add(new StockMovement(
                    line.getItemId(), count.getStockId(), count.getCountDate(),
                    stockIn ? MovementType.INVENTORY_ADJUST_IN : MovementType.INVENTORY_ADJUST_OUT,
                    stockIn ? quantity : 0, stockIn ? 0 : quantity,
                    line.getUnitId(), line.getTypeValue(),
                    "INVENTORY", count.getId(), count.getUserId()));
        }
        return movements;
    }
}
