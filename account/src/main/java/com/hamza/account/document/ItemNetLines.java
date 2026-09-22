package com.hamza.account.document;

/**
 * What one family of documents moved, item by item, over a period - net of the returns against it,
 * in base units.
 *
 * <p>Three reports ask this question and each used to answer it its own way. The dashboard's
 * "top selling" list summed {@code quantity} across units, so a carton of twelve and a single piece
 * counted as two of the same thing, and it ignored returns altogether; the customer's
 * "what did he buy" view listed raw lines with neither; and the delegate's detail report got both
 * right, for delegates only. This is the delegate report's shape made reusable, built from
 * {@link DocumentTableSpec} so the four tables' different spellings ({@code num}/{@code item_id},
 * {@code invoice_number}/{@code id}, {@code sup_code}/{@code sup_id}) are written in one place.</p>
 *
 * <p>Two rules carry it, and both are the delegate report's:</p>
 * <ul>
 *     <li><b>Each side is grouped before the union.</b> Joining the documents to their returns and
 *     grouping after multiplies every invoice by every return of the same item.</li>
 *     <li><b>A line's amount is after its own discount and before the document's.</b> A discount
 *     taken on a whole invoice belongs to no line, and sharing it out among the items would be an
 *     invented rule. A report that needs to reconcile to a document total shows it as a figure of
 *     its own, so lines less header discounts is the documents' net.</li>
 * </ul>
 *
 * <p>The statement answers five columns - {@code item_id}, {@code quantity} (base units, a return
 * negative), {@code amount} (the documents' lines), {@code returned_amount} (the returns' lines,
 * positive) and {@code documents} (how many documents of the family named the item) - one row per
 * item per side, so a consumer groups them again by {@code item_id}.</p>
 */
public enum ItemNetLines {

    SALES(DocumentTableSpec.SALES, DocumentTableSpec.SALES_RETURN),
    PURCHASES(DocumentTableSpec.PURCHASE, DocumentTableSpec.PURCHASE_RETURN);

    private final DocumentTableSpec documents;
    private final DocumentTableSpec returns;

    ItemNetLines(DocumentTableSpec documents, DocumentTableSpec returns) {
        this.documents = documents;
        this.returns = returns;
    }

    public DocumentTableSpec documents() {
        return documents;
    }

    public DocumentTableSpec returns() {
        return returns;
    }

    /**
     * The per-item lines of every document dated inside the period, and of its returns.
     *
     * @param forOneParty whether both halves are narrowed to one customer or supplier
     * @return a statement taking {@link #parameterCount(boolean)} parameters: the period and, when
     * narrowed, the party - once for the documents and once for the returns
     */
    public String perItemSql(boolean forOneParty) {
        return side(documents, "d", "h", forOneParty, false)
                + "\nUNION ALL\n"
                + side(returns, "r", "rh", forOneParty, true);
    }

    public int parameterCount(boolean forOneParty) {
        return forOneParty ? 6 : 4;
    }

    /**
     * A line's amount after its own discount. The two sales tables store {@code total_sel_price};
     * the purchase tables have no such column, and {@code quantity * price} is what it holds on the
     * sales side (see {@link DocumentTableSpec}'s item report, which checked it on real lines).
     */
    public static String lineAmount(DocumentTableSpec spec, String alias) {
        return spec.hasProfit()
                ? alias + ".total_sel_price - " + alias + ".discount"
                : alias + ".quantity * " + alias + ".price - " + alias + ".discount";
    }

    private static String side(DocumentTableSpec spec, String line, String header, boolean forOneParty,
                               boolean isReturn) {
        String item = line + "." + spec.lineItem();
        String quantity = "SUM(" + line + ".quantity * " + line + ".type_value)";
        String amount = "SUM(" + lineAmount(spec, line) + ")";
        return "SELECT " + item + " AS item_id,\n"
                + "       " + (isReturn ? "-" + quantity : quantity) + " AS quantity,\n"
                + "       " + (isReturn ? "0" : amount) + " AS amount,\n"
                + "       " + (isReturn ? amount : "0") + " AS returned_amount,\n"
                + "       " + (isReturn ? "0" : "COUNT(DISTINCT " + line + "." + DocumentTableSpec.LINE_DOCUMENT + ")")
                + " AS documents\n"
                + "FROM " + spec.lineTable() + " " + line
                + " JOIN " + spec.table() + " " + header
                + " ON " + header + "." + spec.key() + " = " + line + "." + DocumentTableSpec.LINE_DOCUMENT + "\n"
                + "WHERE " + header + "." + spec.dateColumn() + " BETWEEN ? AND ?"
                + (forOneParty ? " AND " + header + "." + spec.party() + " = ?" : "") + "\n"
                + "GROUP BY " + item;
    }
}
