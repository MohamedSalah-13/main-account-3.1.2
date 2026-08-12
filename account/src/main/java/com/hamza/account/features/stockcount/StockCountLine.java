package com.hamza.account.features.stockcount;

import com.hamza.controlsfx.util.NumberUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

/**
 * One item on a count sheet: what the system said, and what was actually there.
 *
 * <h2>Units</h2>
 * {@link #countedQuantity} is in the unit the person counted in - four cartons is
 * four, not forty-eight - and {@link #typeValue} is that unit's factor. Everything
 * compared against the system is converted first, which is the same arrangement an
 * invoice line uses and for the same reason: an item's factor may be corrected later,
 * and that must not silently rewrite what a past count meant.
 *
 * <h2>Why the system quantity is stored</h2>
 * {@link #systemQuantity} is a snapshot taken when the line was added, not something
 * recomputed on posting. If it were recomputed, a sale made while the shop was
 * counting would be swallowed by the count: the difference posted would cancel it out
 * and the sale would leave no mark on the balance. Storing it means the count posts
 * exactly the difference the counter saw, and the sale stays a sale.
 */
public class StockCountLine {

    private final int itemId;
    private final String itemName;
    private final String barcode;
    private final int unitId;
    private final String unitName;
    private final double typeValue;
    private final double systemQuantity;

    /** Mutable and observable: it is the one cell the user types into. */
    private final DoubleProperty countedQuantity = new SimpleDoubleProperty();

    private int id;

    public StockCountLine(int id, int itemId, String itemName, String barcode,
                          int unitId, String unitName, double typeValue,
                          double systemQuantity, double countedQuantity) {
        this.id = id;
        this.itemId = itemId;
        this.itemName = itemName;
        this.barcode = barcode;
        this.unitId = unitId;
        this.unitName = unitName;
        // A zero or negative factor would zero or reverse the count, exactly as it
        // would a stock movement; ItemUnits.factor guards it for the invoice screens
        // and the same guard belongs here.
        this.typeValue = typeValue > 0 ? typeValue : 1;
        this.systemQuantity = systemQuantity;
        this.countedQuantity.set(countedQuantity);
    }

    /** What was counted, converted to the item's base unit. */
    public double countedInBaseUnits() {
        return countedQuantity.get() * typeValue;
    }

    /**
     * How far the shelf is from the books, in base units. Positive means more was
     * found than the system knew about; negative means stock is missing. This is the
     * number posting adds to the balance.
     */
    public double difference() {
        return NumberUtils.roundToTwoDecimalPlaces(countedInBaseUnits() - systemQuantity);
    }

    /** Whether this line changes anything at all - most lines on a full count do not. */
    public boolean hasDifference() {
        return difference() != 0;
    }

    /**
     * What the item's balance will be once this sheet is posted - which is simply what
     * was counted, in base units, because that is the whole promise of a count.
     * <p>
     * It exists to be shown beside {@link #difference()}, which is otherwise easy to
     * misread when the books are negative. An item the system thinks is at -10, counted
     * at 15, needs an adjustment of +25; read on its own that looks like the two numbers
     * were added together. Read as "-10, counted 15, adjust by +25, ends at 15" it is
     * obvious - and it shows that the alternative, +5, would leave the item at -5 after
     * a count that found fifteen of them on the shelf.
     */
    public double resultingBalance() {
        return NumberUtils.roundToTwoDecimalPlaces(countedInBaseUnits());
    }

    /**
     * Whether the books say this item is at less than nothing.
     * <p>
     * Worth pointing out before posting rather than after: a negative balance is not a
     * counting problem but a recorded one - a purchase invoice never entered, or a wrong
     * opening balance - and the honest fix is usually to enter what is missing and then
     * count. Posting over it works, but it buries the cause in one adjustment.
     */
    public boolean hasNegativeSystemBalance() {
        return systemQuantity < 0;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getBarcode() {
        return barcode;
    }

    public int getUnitId() {
        return unitId;
    }

    public String getUnitName() {
        return unitName;
    }

    public double getTypeValue() {
        return typeValue;
    }

    public double getSystemQuantity() {
        return systemQuantity;
    }

    public double getCountedQuantity() {
        return countedQuantity.get();
    }

    public void setCountedQuantity(double value) {
        countedQuantity.set(value);
    }

    public DoubleProperty countedQuantityProperty() {
        return countedQuantity;
    }
}
