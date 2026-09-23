package com.hamza.account.controller.name_account;

import com.hamza.account.features.party.statement.PartyStatementRow;
import com.hamza.account.features.party.statement.PartyStatementSummary;
import com.hamza.account.features.party.statement.PartyStatementCurrency;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;

/**
 * A party statement as a spreadsheet.
 * <p>
 * The statement screen could only export a PDF, and that PDF went through
 * {@code CustomerAccountData} - a record shaped for a different report - which is how the exported
 * statement came to have a column of dates headed with the customer's name.
 * <p>
 * The rows are whatever the filter matched, read by the same call the screen and the print use, so
 * the file cannot describe a different set from the table. The closing balance is written as a
 * final row rather than left for the reader to add up: a statement that does not say what it comes
 * to is a statement somebody has to check by hand.
 */
public record PartyStatementExcelWriter(List<PartyStatementRow> rows, PartyStatementSummary summary,
                                        PartyStatementCurrency currency)
        implements WriteExcelInterface<PartyStatementRow> {

    public PartyStatementExcelWriter {
        currency = currency == null ? PartyStatementCurrency.BASE : currency;
    }

    /**
     * The headings. A party dealing in a foreign currency has its code on the three amounts - the rows
     * are in it - and a column more for each movement's book value (docs/currency-plan.md §14 ق-ج٨).
     */
    @Override
    public Object[] columnHeader() {
        var lm = LanguageManager.getInstance();
        String code = currency.isForeign() ? " (" + currency.code() + ")" : "";
        List<Object> headers = new java.util.ArrayList<>(List.of(
                lm.getString("date"),
                lm.getString("party.statement.column.kind"),
                lm.getString("party.statement.column.reference"),
                lm.getString("common.debtor") + code,
                lm.getString("common.creditor") + code,
                lm.getString("party.statement.column.running") + code));
        if (currency.isForeign()) {
            headers.add(lm.getString("treasury.statement.column.book"));
        }
        headers.addAll(List.of(
                lm.getString("party.statement.column.treasury"),
                lm.getString("party.statement.column.user"),
                lm.getString("column.notes")));
        return headers.toArray();
    }

    @Override
    public Object[] dataRow(PartyStatementRow stored) {
        PartyStatementRow row = currency.shown(stored);
        List<Object> cells = new java.util.ArrayList<>(List.of(
                row.date().toString(),
                LanguageManager.getInstance().getString(row.kind().messageKey()),
                row.reference(),
                row.debit(),
                row.credit(),
                row.runningBalance()));
        if (currency.isForeign()) {
            cells.add(currency.bookValue(stored));
        }
        cells.addAll(List.of(row.treasuryName(), row.userName(), row.notes()));
        return cells.toArray();
    }

    @Override
    public List<PartyStatementRow> itemsList() {
        return rows;
    }

    @Override
    public boolean addDataToFile() {
        return true;
    }

    @Override
    public String sheetName() {
        return LanguageManager.getInstance().getString("party.statement.export.excel.sheet");
    }
}
