package com.hamza.account.document;

import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.period.LockedDocument;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;

/**
 * The four documents the business writes: a sale, its return, a purchase, its return.
 * <p>
 * They were never four <em>kinds</em> of thing - they are one document with a type, and
 * the type decides four questions that the code has so far answered in four different
 * places: whose account it moves ({@link PartyKind}), which half of the ledger it belongs
 * to ({@link InvoiceSide}), which way it moves the stock and the treasury, and which
 * permissions guard it. Written out per screen, those answers drift: {@code BuyController2}
 * had to ask {@code designInterface().show() != SALES_SHOW} to find out whether it was
 * looking at a sale, because a permission was the only field that differed between
 * {@code DesignCustom} and {@code DesignCustomReturn}.
 * <p>
 * Declared here instead, in the same spirit as {@code DeleteRegistry}, {@code WipeCatalog}
 * and {@code PeriodLockRegistry}: a fifth document type is a row in this table, not a
 * search for every {@code if} that assumed there were four.
 * <p>
 * What is deliberately <em>not</em> here is where the rows live - the table and column
 * names still differ per family ({@code invoice_number} against {@code id},
 * {@code sup_code} against {@code sup_id}, three spellings of the paid column). That
 * belongs to the repository that will read them, not to the business meaning of the
 * document. The one exception is {@link #periodLock()}, which already had to name its
 * table and key to do its job.
 */
public enum DocumentType {

    SALES(PartyKind.CUSTOMER, InvoiceSide.SALES, false,
            Direction.OUT, Direction.IN, true,
            PeriodLockRegistry.SALES_INVOICE,
            AppPermissions.SALES_SHOW,
            AppPermissions.SALES_CREATE,
            AppPermissions.SALES_UPDATE,
            AppPermissions.SALES_DELETE,
            AppPermissions.TOTAL_SALES_SHOW,
            AppPermissions.TOTAL_SALES_SHOW_INVOICE),

    SALES_RETURN(PartyKind.CUSTOMER, InvoiceSide.SALES, true,
            Direction.IN, Direction.OUT, true,
            PeriodLockRegistry.SALES_RETURN,
            AppPermissions.SALES_RE_SHOW,
            AppPermissions.SALES_RE_CREATE,
            AppPermissions.SALES_RE_UPDATE,
            AppPermissions.SALES_RE_DELETE,
            AppPermissions.TOTAL_SALES_RE_SHOW,
            AppPermissions.TOTAL_SALES_RE_SHOW_INVOICE),

    PURCHASE(PartyKind.SUPPLIER, InvoiceSide.PURCHASE, false,
            Direction.IN, Direction.OUT, false,
            PeriodLockRegistry.PURCHASE_INVOICE,
            AppPermissions.PURCHASE_SHOW,
            AppPermissions.PURCHASE_CREATE,
            AppPermissions.PURCHASE_UPDATE,
            AppPermissions.PURCHASE_DELETE,
            AppPermissions.TOTAL_PURCHASE_SHOW,
            AppPermissions.TOTAL_PURCHASE_SHOW_INVOICE),

    PURCHASE_RETURN(PartyKind.SUPPLIER, InvoiceSide.PURCHASE, true,
            Direction.OUT, Direction.IN, false,
            PeriodLockRegistry.PURCHASE_RETURN,
            AppPermissions.PURCHASE_RE_SHOW,
            AppPermissions.PURCHASE_RE_CREATE,
            AppPermissions.PURCHASE_RE_UPDATE,
            AppPermissions.PURCHASE_RE_DELETE,
            AppPermissions.TOTAL_PURCHASE_RE_SHOW,
            AppPermissions.TOTAL_PURCHASE_RE_SHOW_INVOICE);

    /**
     * Which way a document moves a balance. {@code quantity_items_table} adds the
     * purchase side and subtracts the sales side; the sign is the same fact, written
     * once instead of once per CTE and once per screen.
     */
    public enum Direction {

        IN(+1),
        OUT(-1);

        private final int sign;

        Direction(int sign) {
            this.sign = sign;
        }

        public int sign() {
            return sign;
        }
    }

    private final PartyKind partyKind;
    private final InvoiceSide side;
    private final boolean isReturn;
    private final Direction stock;
    private final Direction cash;
    private final boolean hasDelegate;
    private final LockedDocument periodLock;
    private final PermissionKey show;
    private final PermissionKey create;
    private final PermissionKey update;
    private final PermissionKey delete;
    private final PermissionKey showTotals;
    private final PermissionKey showTotalsInvoice;

    DocumentType(PartyKind partyKind, InvoiceSide side, boolean isReturn,
                 Direction stock, Direction cash, boolean hasDelegate,
                 LockedDocument periodLock,
                 PermissionKey show, PermissionKey create, PermissionKey update, PermissionKey delete,
                 PermissionKey showTotals, PermissionKey showTotalsInvoice) {
        this.partyKind = partyKind;
        this.side = side;
        this.isReturn = isReturn;
        this.stock = stock;
        this.cash = cash;
        this.hasDelegate = hasDelegate;
        this.periodLock = periodLock;
        this.show = show;
        this.create = create;
        this.update = update;
        this.delete = delete;
        this.showTotals = showTotals;
        this.showTotalsInvoice = showTotalsInvoice;
    }

    /** Whose account this document moves: a customer's or a supplier's. */
    public PartyKind partyKind() {
        return partyKind;
    }

    /**
     * Which half of the ledger it belongs to. Two-valued, not four: a return shares
     * the side of what it reverses, which is why a purchases screen reloads when a
     * purchase return is saved.
     */
    public InvoiceSide side() {
        return side;
    }

    /** Whether this document reverses one of the other kind. */
    public boolean isReturn() {
        return isReturn;
    }

    /** Which way it moves the stock: a sale takes goods out, a purchase brings them in. */
    public Direction stockDirection() {
        return stock;
    }

    /** {@code +1} if it adds to the stock, {@code -1} if it takes from it. */
    public int stockSign() {
        return stock.sign();
    }

    /** Which way it moves the treasury: a sale brings money in, a purchase pays it out. */
    public Direction cashDirection() {
        return cash;
    }

    /** {@code +1} if money comes into the treasury, {@code -1} if it leaves. */
    public int cashSign() {
        return cash.sign();
    }

    /**
     * Whether the document carries a delegate. Only the sales side does -
     * {@code total_buy} and {@code total_buy_re} have no {@code delegate_id} column,
     * which is why the supplier screens never ask for one.
     */
    public boolean hasDelegate() {
        return hasDelegate;
    }

    /** Where the period lock finds this document, and what it calls it when it refuses. */
    public LockedDocument periodLock() {
        return periodLock;
    }

    /** What the document is called in the active language: "فاتورة بيع"/"Sales invoice", "مرتجع شراء"/"Purchase return". */
    public String label() {
        return periodLock.label();
    }

    public PermissionKey showPermission() {
        return show;
    }

    public PermissionKey createPermission() {
        return create;
    }

    public PermissionKey updatePermission() {
        return update;
    }

    public PermissionKey deletePermission() {
        return delete;
    }

    public PermissionKey showTotalsPermission() {
        return showTotals;
    }

    public PermissionKey showTotalsInvoicePermission() {
        return showTotalsInvoice;
    }

    /**
     * The document this one reverses, or itself when it reverses nothing. A sales
     * return answers {@link #SALES}, which is the relation the screens draw when they
     * load an invoice to return it.
     */
    public DocumentType reverses() {
        if (!isReturn) {
            return this;
        }
        return this == SALES_RETURN ? SALES : PURCHASE;
    }

    /** The document of this kind on the given side, returning or not. */
    public static DocumentType of(PartyKind partyKind, boolean isReturn) {
        for (DocumentType type : values()) {
            if (type.partyKind == partyKind && type.isReturn == isReturn) {
                return type;
            }
        }
        throw new IllegalArgumentException("No document type for " + partyKind + ", return=" + isReturn);
    }
}
