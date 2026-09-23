package com.hamza.account.controller.name_account;

import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.features.party.statement.PartyStatementPrintData;
import com.hamza.account.features.party.statement.PartyStatementRow;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import com.itextpdf.kernel.geom.PageSize;

import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.party.statement.PartyStatementCurrency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.List;

/**
 * A party statement as a PDF.
 * <p>
 * <b>It has its own exporter because the one it replaces borrowed another report's.</b> The screen
 * built {@code CustomerAccountData} rows - a record shaped for the receivables report, whose columns
 * are a customer name, a debit, a credit and a balance - and put the movement's <em>date</em> in the
 * {@code customerName} field to get it onto the page. So an exported statement had a column of dates
 * headed with the customer's name, and no column saying what any row was.
 * <p>
 * The columns here are the statement's own, and the closing balance is the footer: a statement that
 * does not say what it comes to is one somebody has to add up by hand to trust.
 */
public final class PartyStatementPdfExporter {

    private final PdfExportService pdf = new PdfExportService();

    /**
     * Writes the statement.
     *
     * @param statement the rows and figures on screen - the same extract the table and the Excel
     *                  export use, so the three cannot describe different sets
     * @return whether a file was written. The caller must not claim success without it: the screen
     *         this replaces announced a saved PDF over a call that had been commented out
     */
    public boolean export(PartyStatementPrintData statement, String partyName, String outputPath) {
        var lm = LanguageManager.getInstance();
        // A party dealing in a foreign currency is printed in it, with each movement's book value in a
        // column of its own and the closing book value in a second subtitle line (docs/currency-plan.md
        // §14 ق-ج٨) - the paper is the statement on screen.
        PartyStatementCurrency currency = statement.currency();
        boolean foreign = currency.isForeign();
        Function<BigDecimal, String> amount = foreign
                ? value -> CurrencyFormat.amount(value, currency.foreign()) : Columns::money;
        String code = foreign ? " (" + currency.code() + ")" : "";
        List<String> headers = new ArrayList<>(List.of(
                lm.getString("date"),
                lm.getString("party.statement.column.kind"),
                lm.getString("party.statement.column.reference"),
                lm.getString("common.debtor") + code,
                lm.getString("common.creditor") + code,
                lm.getString("party.statement.column.running") + code));
        if (foreign) {
            headers.add(lm.getString("treasury.statement.column.book"));
        }
        headers.add(lm.getString("column.notes"));
        float[] widths = foreign
                ? new float[]{11f, 12f, 9f, 12f, 12f, 14f, 12f, 18f}
                : new float[]{12f, 13f, 10f, 13f, 13f, 14f, 25f};

        List<String[]> rows = new ArrayList<>();
        // The balance carried into the period is the first line of the page, not a figure the reader
        // has to find in a header - it is what makes every running balance below it follow.
        List<String> opening = new ArrayList<>(List.of("", lm.getString("party.statement.opening"), "", "", "",
                amount.apply(statement.summary().openingBalance())));
        if (foreign) {
            opening.add(Columns.money(statement.totals().base().openingBalance()));
        }
        opening.add("");
        rows.add(opening.toArray(String[]::new));
        for (PartyStatementRow stored : statement.rowsOldestFirst()) {
            PartyStatementRow row = currency.shown(stored);
            List<String> cells = new ArrayList<>(List.of(
                    row.date().toString(),
                    lm.getString(row.kind().messageKey()),
                    row.reference() == 0 ? "" : String.valueOf(row.reference()),
                    amount.apply(row.debit()),
                    amount.apply(row.credit()),
                    amount.apply(row.runningBalance())));
            if (foreign) {
                cells.add(Columns.money(currency.bookValue(stored)));
            }
            cells.add(row.notes());
            rows.add(cells.toArray(String[]::new));
        }

        String subtitle = lm.getString("party.statement.total.debit") + ": "
                + amount.apply(statement.summary().totalDebit()) + "   "
                + lm.getString("party.statement.total.credit") + ": "
                + amount.apply(statement.summary().totalCredit());
        if (foreign) {
            subtitle += "\n" + lm.getString("party.statement.currency.note", currency.code(),
                    Columns.money(statement.totals().base().closingBalance()));
        }
        return pdf.exportGenericReport(outputPath,
                lm.getString("party.account.card.title", partyName),
                subtitle,
                headers.toArray(String[]::new), widths, rows,
                lm.getString("party.statement.closing"),
                amount.apply(statement.summary().closingBalance()), null, PageSize.A4);
    }
}
