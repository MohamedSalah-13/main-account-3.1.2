package com.hamza.account.controller.name_account;

import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.features.party.statement.PartyStatementPrintData;
import com.hamza.account.features.party.statement.PartyStatementRow;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import com.itextpdf.kernel.geom.PageSize;

import java.util.ArrayList;
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
        String[] headers = {
                lm.getString("date"),
                lm.getString("party.statement.column.kind"),
                lm.getString("party.statement.column.reference"),
                lm.getString("common.debtor"),
                lm.getString("common.creditor"),
                lm.getString("party.statement.column.running"),
                lm.getString("column.notes")
        };
        float[] widths = {12f, 13f, 10f, 13f, 13f, 14f, 25f};

        List<String[]> rows = new ArrayList<>();
        // The balance carried into the period is the first line of the page, not a figure the reader
        // has to find in a header - it is what makes every running balance below it follow.
        rows.add(new String[]{"", lm.getString("party.statement.opening"), "", "", "",
                Columns.money(statement.summary().openingBalance()), ""});
        for (PartyStatementRow row : statement.rowsOldestFirst()) {
            rows.add(new String[]{
                    row.date().toString(),
                    lm.getString(row.kind().messageKey()),
                    row.reference() == 0 ? "" : String.valueOf(row.reference()),
                    Columns.money(row.debit()),
                    Columns.money(row.credit()),
                    Columns.money(row.runningBalance()),
                    row.notes()
            });
        }

        return pdf.exportGenericReport(outputPath,
                lm.getString("party.account.card.title", partyName),
                lm.getString("party.statement.total.debit") + ": "
                        + Columns.money(statement.summary().totalDebit()) + "   "
                        + lm.getString("party.statement.total.credit") + ": "
                        + Columns.money(statement.summary().totalCredit()),
                headers, widths, rows,
                lm.getString("party.statement.closing"),
                Columns.money(statement.summary().closingBalance()), null, PageSize.A4);
    }
}
