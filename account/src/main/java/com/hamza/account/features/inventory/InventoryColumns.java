package com.hamza.account.features.inventory;

import com.hamza.controlsfx.language.Setting_Language;

import java.util.List;

import static com.hamza.account.features.inventory.InventoryColumn.group;
import static com.hamza.account.features.inventory.InventoryColumn.money;
import static com.hamza.account.features.inventory.InventoryColumn.quantity;
import static com.hamza.account.features.inventory.InventoryColumn.text;
import static com.hamza.controlsfx.language.Setting_Language.BALANCE_NOW;
import static com.hamza.controlsfx.language.Setting_Language.FIRST_BALANCE;
import static com.hamza.controlsfx.language.Setting_Language.PRICE;
import static com.hamza.controlsfx.language.Setting_Language.RETURN;
import static com.hamza.controlsfx.language.Setting_Language.TOTAL;

/**
 * Every column the inventory sheet can show, in the order it shows them.
 * <p>
 * Adding one is a line here. It reaches the table, and anything else built from the
 * same list, without a controller being touched - which is the difference between
 * this and the block-per-column the screen used to carry.
 * <p>
 * The quantity columns are all in the item's base unit; {@code quantity_items_table}
 * has already multiplied each invoice line by the factor it was written with, so a
 * carton of twelve and a loose piece are comparable by the time they reach a cell.
 * The {@link #UNIT} column says which unit that is - the sheet used to print bare
 * numbers and leave a business selling by the carton to guess.
 */
public final class InventoryColumns {

    /**
     * Not in {@code Setting_Language}: the two transfer columns were literals in the
     * controller before this, and moving them into the bundle would change every
     * language file for a screen that is being reworked. Kept together here so there
     * is one place to lift them from when they go in.
     */
    private static final String TRANSFERS_OUT = "تحويلات صادرة";
    private static final String TRANSFERS_IN = "تحويلات واردة";

    public static final InventoryColumn NAME =
            text("name", Setting_Language.WORD_NAME, InventoryRow::nameItem, 250);
    public static final InventoryColumn BARCODE =
            text("barcode", Setting_Language.WORD_BARCODE, InventoryRow::barcode, 130);
    public static final InventoryColumn UNIT =
            text("unit", Setting_Language.Unit, InventoryRow::unitName, 90);

    public static final InventoryColumn OPENING =
            quantity("opening", FIRST_BALANCE, InventoryRow::opening);
    public static final InventoryColumn PURCHASE =
            quantity("purchase", Setting_Language.WORD_PUR, InventoryRow::purchase);
    public static final InventoryColumn SALES =
            quantity("sales", Setting_Language.WORD_SALES, InventoryRow::sales);
    public static final InventoryColumn PURCHASE_RETURN =
            quantity("purchaseReturn", RETURN + "\n" + Setting_Language.WORD_PUR, InventoryRow::purchaseReturn);
    public static final InventoryColumn SALES_RETURN =
            quantity("salesReturn", RETURN + "\n" + Setting_Language.WORD_SALES, InventoryRow::salesReturn);
    public static final InventoryColumn TRANSFER_OUT =
            quantity("transferOut", TRANSFERS_OUT, InventoryRow::transferOut);
    public static final InventoryColumn TRANSFER_IN =
            quantity("transferIn", TRANSFERS_IN, InventoryRow::transferIn);

    /**
     * What posted stock counts corrected the balance by. It is a column of its own
     * rather than folded silently into the balance, because the whole point of
     * recording a count as a movement is that someone can see it was made.
     */
    public static final InventoryColumn ADJUSTMENT =
            quantity("adjustment", "تسويات الجرد", InventoryRow::adjustment);

    public static final InventoryColumn BALANCE =
            quantity("balance", BALANCE_NOW, InventoryRow::balance);

    public static final InventoryColumn AT_COST =
            group("purchaseValue", Setting_Language.PURCHASE,
                    money("purchaseValuePrice", PRICE, InventoryRow::buyPrice),
                    money("purchaseValueTotal", TOTAL, InventoryRow::valueAtCost));

    public static final InventoryColumn AT_SALE =
            group("salesValue", Setting_Language.SALES,
                    money("salesValuePrice", PRICE, InventoryRow::sellPrice),
                    money("salesValueTotal", TOTAL, InventoryRow::valueAtSale));

    public static final List<InventoryColumn> ALL = List.of(
            NAME, BARCODE, UNIT,
            OPENING, PURCHASE, SALES, PURCHASE_RETURN, SALES_RETURN,
            TRANSFER_OUT, TRANSFER_IN, ADJUSTMENT, BALANCE,
            AT_COST, AT_SALE);

    private InventoryColumns() {
    }
}
