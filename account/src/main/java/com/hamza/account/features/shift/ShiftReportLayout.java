package com.hamza.account.features.shift;

import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.service.ShiftReportService.ShiftReportData;
import com.hamza.account.service.ShiftReportService.ShiftReportType;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.hamza.controlsfx.dateTime.DateUtils.DATE_TIME_FORMATTER;

/**
 * The X and Z reports, written out as the rows the 80mm template prints.
 * <p>
 * <b>The template used to place every figure at a fixed point, and that was three defects at once.</b>
 * The labels sat at the left edge and the figures to their right - an Arabic page printed as if it
 * were English. The amounts went in as doubles with a {@code pattern}, which Jasper resolves against
 * the report's Arabic locale, so {@code ١٠٫٠٠} was printed beside a shift number written {@code 2}.
 * And a row that did not apply still held its place: the X report has no counted cash, so it left a
 * blank where the Z report puts it, and the treasury row overlapped the rule drawn under it.
 * <p>
 * So the paper is a list of rows, each label and figure already written as text - the pattern the
 * 80mm invoice receipt settled on ({@code InvoiceReceiptLayout}). A row that does not apply is not
 * in the list. The template draws a label on the right and its figure on the left, and has no
 * arithmetic and no pattern of its own.
 * <p>
 * <b>The rows follow the drawer's arithmetic</b>: what came in, what went out, then the opening
 * balance, the expected balance, what was counted and the difference. The old paper printed the
 * expected balance before the movements that make it up.
 * <p>
 * <b>Under a blind close the X report carries no amount but the opening balance.</b> It used to print
 * every movement and hide only the expected balance and the two totals - which left that figure one
 * addition away, on the paper handed to the person it is being kept from. What remains is what the
 * cashier knows anyway: the shift's details, how many invoices they rang up, and the float they were
 * given. {@link ShiftScreenSummary} says the same for the screen, and the closing dialog's blind
 * wording no longer recites the five movement figures either.
 * <p>
 * No JavaFX and no database: {@link Columns#money} is a static formatter, and the caller hands over
 * the report, the moment of printing, who printed it and the labels.
 *
 * @param title      the report's name, as the heading of the paper
 * @param subtitle   what kind of reading it is - a Z report closes the shift, an X report does not
 * @param rows       everything between the heading and the footer, in order
 * @param notes      the shift's notes behind their label, empty when there are none
 * @param printedLabel what the printing line is called
 * @param printed    when and by whom the paper was printed - apart from its label, because a date that
 *                   follows an Arabic word in one line is reversed by the bidi pass ({@code 22-09-2026})
 * @param signatures the signature lines at the foot of a Z report; empty on an X report
 */
public record ShiftReportLayout(String title, String subtitle, List<Row> rows, String notes, String printedLabel,
                                String printed, List<String> signatures) {

    /** Resolves a message key; a test hands over {@code key -> key}. */
    @FunctionalInterface
    public interface Labels {
        String text(String key);
    }

    public ShiftReportLayout {
        rows = List.copyOf(rows);
        signatures = List.copyOf(signatures);
    }

    public static ShiftReportLayout of(ShiftReportData data, LocalDateTime printedAt, String printedBy, Labels labels) {
        UserShift shift = data.shift();
        ShiftSummary summary = data.summary();
        boolean closing = data.reportType() == ShiftReportType.Z;
        // A blind close withholds every amount that leads to the expected balance, so under it the
        // paper carries no movements at all - see ShiftScreenSummary, which says the same for the
        // screen. Printing the movements while hiding their total left the figure one addition away.
        boolean amounts = data.showExpectedBalance();

        List<Row> rows = new ArrayList<>();
        rows.add(Row.line(labels.text("user.shift.report.row.shift"), String.valueOf(shift.getId())));
        rows.add(Row.line(labels.text("user.shift.report.row.cashier"), text(shift.getUsername())));
        rows.add(Row.line(labels.text("user.shift.report.row.treasury"), text(shift.getTreasuryName())));
        rows.add(Row.line(labels.text("user.shift.report.row.opened"), dateTime(shift.getOpenTime())));
        if (closing) {
            rows.add(Row.line(labels.text("user.shift.report.row.closed"), dateTime(shift.getCloseTime())));
        }
        rows.add(Row.line(labels.text("user.shift.report.row.invoices"), String.valueOf(summary.getInvoicesCount())));

        if (amounts) {
            rows.add(Row.heading(labels.text("user.shift.report.section.in")));
            rows.add(Row.line(labels.text("user.shift.report.row.sales"), money(summary.getTotalSales())));
            rows.add(Row.line(labels.text("user.shift.report.row.deposits"), money(summary.getTotalDeposits())));
            rows.add(Row.line(labels.text("user.shift.report.row.other.in"), money(summary.getOtherIn())));
            rows.add(Row.total(labels.text("user.shift.report.row.total.in"), money(summary.getTotalIn())));

            rows.add(Row.heading(labels.text("user.shift.report.section.out")));
            rows.add(Row.line(labels.text("user.shift.report.row.returns"), money(summary.getTotalSalesReturns())));
            rows.add(Row.line(labels.text("user.shift.report.row.expenses"), money(summary.getTotalExpenses())));
            rows.add(Row.line(labels.text("user.shift.report.row.withdrawals"), money(summary.getTotalWithdrawals())));
            rows.add(Row.line(labels.text("user.shift.report.row.other.out"), money(summary.getOtherOut())));
            rows.add(Row.total(labels.text("user.shift.report.row.total.out"), money(summary.getTotalOut())));
        }

        rows.add(Row.heading(labels.text("user.shift.report.section.cash")));
        // The float the cashier was handed, which they know and the screen's header shows anyway.
        rows.add(Row.line(labels.text("user.shift.report.row.opening"), money(summary.getOpenBalance())));
        if (data.showExpectedBalance()) {
            rows.add(closing
                    ? Row.line(labels.text("user.shift.report.row.expected"), money(summary.getExpectedBalance()))
                    : Row.total(labels.text("user.shift.report.row.expected"), money(summary.getExpectedBalance())));
        }
        if (data.showActualBalance()) {
            rows.add(Row.line(labels.text("user.shift.report.row.counted"), money(shift.getCloseBalance())));
        }
        if (data.showDifference()) {
            BigDecimal difference = summary.calculateDifference(shift.getCloseBalance());
            String label = difference.signum() < 0 ? labels.text("user.shift.report.row.difference.short")
                    : difference.signum() > 0 ? labels.text("user.shift.report.row.difference.over")
                    : labels.text("user.shift.report.row.difference.none");
            // The sign is carried by the label, and the figure is printed without one: a minus sign in
            // a right-to-left line is moved to the far end of the number by the bidi pass.
            rows.add(Row.total(label, money(difference.abs())));
        }

        String notes = shift.getNotes() == null || shift.getNotes().isBlank()
                ? "" : labels.text("user.shift.report.notes") + " " + shift.getNotes().strip();
        String printed = dateTime(printedAt) + (printedBy == null || printedBy.isBlank() ? "" : " - " + printedBy);
        List<String> signatures = closing
                ? List.of(labels.text("user.shift.report.signature.cashier"),
                labels.text("user.shift.report.signature.supervisor"))
                : List.of();
        return new ShiftReportLayout(
                labels.text(closing ? "user.shift.report.z.title" : "user.shift.report.x.title"),
                // Written out rather than resolved from a variable: a key a static check cannot see
                // is a key nothing checks against the three bundles.
                labels.text(closing ? "user.shift.report.z.subtitle"
                        : amounts ? "user.shift.report.x.subtitle" : "user.shift.report.x.subtitle.blind"),
                rows, notes, labels.text("user.shift.report.printed"), printed, signatures);
    }

    private static String money(BigDecimal value) {
        return Columns.money(value == null ? BigDecimal.ZERO : value);
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME_FORMATTER);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /** How the template draws a row. */
    public enum Kind {
        /** The name of a section, across the whole width, with a rule above it. */
        HEADING,
        /** A label and its figure. */
        LINE,
        /** A label and its figure in bold, with a dashed rule above: what a section comes to. */
        TOTAL
    }

    /**
     * One printed row. A class with getters, not a record: the template reads it through
     * {@code JRBeanCollectionDataSource}, which looks for {@code getLabel()}, not {@code label()}.
     */
    public static final class Row {
        private final String label;
        private final String value;
        private final Kind kind;

        private Row(String label, String value, Kind kind) {
            this.label = label;
            this.value = value;
            this.kind = kind;
        }

        static Row heading(String label) {
            return new Row(label, "", Kind.HEADING);
        }

        static Row line(String label, String value) {
            return new Row(label, value, Kind.LINE);
        }

        static Row total(String label, String value) {
            return new Row(label, value, Kind.TOTAL);
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

        /** The kind's name, which is what the template compares - a Jasper expression has no import. */
        public String getKind() {
            return kind.name();
        }

        public Kind kind() {
            return kind;
        }
    }
}
