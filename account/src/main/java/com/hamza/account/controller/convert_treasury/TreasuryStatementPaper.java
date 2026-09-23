package com.hamza.account.controller.convert_treasury;

import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.treasury.statement.TreasuryStatementCurrency;
import com.hamza.account.features.treasury.statement.TreasuryStatementFilter;
import com.hamza.account.features.treasury.statement.TreasuryStatementPrintData;
import com.hamza.account.features.treasury.statement.TreasuryStatementRow;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The printed treasury statement - its columns, rows, totals line and subtitle - in the currency of the
 * extract it prints, which is the screen's (docs/currency-plan.md §13).
 * <p>
 * A foreign treasury's paper names its currency in the three amount headings, writes them to that
 * currency's places, and carries each movement's value in the books in a column of its own; its subtitle
 * says what the closing balance is worth in the base. Every other statement prints as it always did.
 * It lives outside the screen so the paper can be checked without a toolkit and without a save dialog.
 */
public final class TreasuryStatementPaper {

    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final float[] BASE_WIDTHS = {65, 85, 70, 120, 110, 85, 85, 95, 100};
    /** The running balance widest: "الرصيد المتحرك (USD)" wrapped at 95 and printed its words out of order. */
    private static final float[] FOREIGN_WIDTHS = {50, 80, 60, 100, 95, 85, 85, 125, 100, 80};

    private TreasuryStatementPaper() {
    }

    public static TablePdfLayout layout(TreasuryStatementPrintData data) {
        TreasuryStatementCurrency currency = data.currency();
        boolean foreign = currency.isForeign();
        List<String> headers = new ArrayList<>(List.of(
                text("treasury.statement.column.reference"), text("treasury.statement.column.date"),
                text("treasury.statement.column.time"), text("treasury.statement.column.movement"),
                text("treasury.statement.column.treasury"), titled("treasury.statement.column.income", currency),
                titled("treasury.statement.column.output", currency),
                titled("treasury.statement.column.balance", currency)));
        if (foreign) headers.add(text("treasury.statement.column.book"));
        headers.add(text("treasury.statement.column.user"));

        List<String[]> rows = data.rows().stream().map(row -> row(row, currency)).toList();

        var summary = data.summary();
        List<String> totals = new ArrayList<>(List.of(text("total"), "", "", "", "",
                amount(summary.totalIncome(), currency), amount(summary.totalOutput(), currency),
                amount(summary.closingBalance(), currency)));
        if (foreign) totals.add(Columns.money(data.totals().base().netMovement()));
        totals.add("");
        return new TablePdfLayout(headers.toArray(String[]::new), foreign ? FOREIGN_WIDTHS : BASE_WIDTHS, rows,
                totals.toArray(String[]::new));
    }

    private static String[] row(TreasuryStatementRow row, TreasuryStatementCurrency currency) {
        List<String> cells = new ArrayList<>(List.of(
                String.valueOf(row.referenceId()), row.movementDate().toString(),
                row.recordedAt() == null ? "" : row.recordedAt().format(TIME_FORMAT),
                text(row.kind().labelKey()), row.treasuryName(), amount(currency.income(row), currency),
                amount(currency.output(row), currency), amount(currency.runningBalance(row), currency)));
        if (currency.isForeign()) cells.add(Columns.money(row.netMovement()));
        cells.add(row.username());
        return cells.toArray(String[]::new);
    }

    /**
     * Which statement this is, on the paper as on the screen: the treasury, whatever narrows the
     * rows, and the balance carried into the period. The paper used to carry the two dates alone -
     * so a printed statement of one wallet could not be told from one of every treasury, and its
     * running balance began at a figure printed nowhere on it. A foreign treasury's adds what its
     * figures are in and what its closing balance is worth in the books.
     *
     * @param treasury the treasury as the screen's picker names it
     * @param user     the user as the screen's picker names it, printed only when the filter names one
     */
    public static String subtitle(TreasuryStatementFilter filter, String treasury, String user,
                                  TreasuryStatementPrintData data) {
        StringBuilder line = new StringBuilder(text("from")).append(": ").append(filter.from())
                .append("  |  ").append(text("to")).append(": ").append(filter.to())
                .append("  |  ").append(text("treasury.statement.column.treasury")).append(": ").append(treasury);
        if (filter.kind() != null) {
            line.append("  |  ").append(text("treasury.statement.column.movement")).append(": ")
                    .append(text(filter.kind().labelKey()));
        }
        if (filter.userId() != null) {
            line.append("  |  ").append(text("treasury.statement.column.user")).append(": ").append(user);
        }
        line.append("  |  ").append(text("treasury.statement.print.opening")).append(": ")
                .append(amount(data.summary().openingBalance(), data.currency()));
        if (data.currency().isForeign()) {
            // A line of its own: appended to the first, it made the subtitle wrap, and a wrapped Arabic
            // paragraph on this paper prints its end first (PdfExportService.addHeader).
            line.append("\n").append(note(data.currency(), data.totals().base().closingBalance()));
        }
        return line.toString();
    }

    /**
     * An amount as the statement writes it: the books' two places, thousands separated, or a foreign
     * treasury's own figure to its currency's places - a Kuwaiti dinar's three, a yen's none.
     */
    static String amount(BigDecimal value, TreasuryStatementCurrency currency) {
        return currency.isForeign() ? CurrencyFormat.amount(value, currency.foreign()) : Columns.money(value);
    }

    /** "المبالغ بعملة الخزينة USD، وقيمة رصيدها الدفترية ..." - under the cards and on the paper. */
    static String note(TreasuryStatementCurrency currency, BigDecimal bookBalance) {
        return LanguageManager.getInstance().getString("treasury.statement.currency.note",
                currency.code(), Columns.money(bookBalance));
    }

    /** A heading, with the currency's code after it on a foreign statement: "الوارد (USD)". */
    static String titled(String key, TreasuryStatementCurrency currency) {
        return currency.isForeign() ? text(key) + " (" + currency.code() + ")" : text(key);
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
