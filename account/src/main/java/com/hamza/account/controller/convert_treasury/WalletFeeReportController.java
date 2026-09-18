package com.hamza.account.controller.convert_treasury;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.treasury.WalletFeeReport;
import com.hamza.account.table.ListToolbar;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * What the wallets and the bank kept over a period, by treasury and by what the fee was charged on.
 * <p>
 * Opened from the treasuries screen rather than the main menu - the same data and the same
 * permission, and a menu entry would need a feature in the signed product catalogue; the reasoning
 * the ageing report follows on the balances screen.
 * <p>
 * Built in code: a period, a table and a total are one place to get right, and the rows are a
 * handful - one per treasury per kind - so there is no page. Every figure is
 * {@link WalletFeeReport}'s; this draws it.
 */
public class WalletFeeReportController implements AppSettingInterface {

    private static final String FEES_COLUMN = "feeReportFees";
    private static final String COUNT_COLUMN = "feeReportMovements";

    private final WalletFeeReport report = new WalletFeeReport();
    private final DatePicker from = new DatePicker(LocalDate.now().withDayOfYear(1));
    private final DatePicker to = new DatePicker(LocalDate.now());
    private final Label totals = new Label();
    private final TableView<WalletFeeReport.Row> table = new TableView<>();
    private TreasuryHistoryTable<WalletFeeReport.Row> rowsTable;
    private List<WalletFeeReport.Row> shown = List.of();

    @Override
    public Pane pane() {
        rowsTable = new TreasuryHistoryTable<>(table, "walletFeeReportTable", List.of(
                TreasuryHistoryTable.withId("feeReportTreasury",
                        Columns.text("treasury.fee.report.column.treasury", WalletFeeReport.Row::treasuryName)),
                TreasuryHistoryTable.withId("feeReportKind",
                        Columns.text("treasury.fee.report.column.kind", row -> text(row.kindLabelKey()))),
                TreasuryHistoryTable.withId(COUNT_COLUMN,
                        Columns.number("treasury.fee.report.column.movements", WalletFeeReport.Row::movements)),
                TreasuryHistoryTable.withId(FEES_COLUMN,
                        Columns.money("treasury.fee.report.column.fees", WalletFeeReport.Row::fees))));
        table.getStyleClass().add("app-table");
        VBox.setVgrow(table, Priority.ALWAYS);

        from.setPrefWidth(150);
        to.setPrefWidth(150);
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        new ListToolbar()
                .searchField(caption("from"), from, caption("to"), to)
                .search(ListToolbar.button("search", AppIcon.SEARCH, this::load))
                .print(ListToolbar.printButton(this::print))
                .export(ListToolbar.button("treasury.history.export.excel", AppIcon.SPREADSHEET, this::exportExcel))
                .installIn(bar);

        Label hint = new Label(text("treasury.fee.report.hint"));
        hint.getStyleClass().add("page-subtitle");
        hint.setWrapText(true);
        totals.getStyleClass().add("section-title");

        VBox root = new VBox(10, bar, hint, table, totals);
        root.setPadding(new Insets(14));
        root.setPrefSize(820, 560);
        load();
        return root;
    }

    private void load() {
        if (from.getValue() == null || to.getValue() == null || from.getValue().isAfter(to.getValue())) {
            AllAlerts.alertError(text("treasury.statement.error.period.reversed"));
            return;
        }
        try {
            shown = report.between(from.getValue(), to.getValue(), null);
            rowsTable.show(shown);
            totals.setText(LanguageManager.getInstance().getString("treasury.fee.report.totals",
                    WalletFeeReport.totalMovements(shown), Columns.money(WalletFeeReport.totalFees(shown))));
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.fee.report.title"), e);
        }
    }

    private void print() {
        rowsTable.print(text("treasury.fee.report.title"),
                text("from") + ": " + from.getValue() + "  |  " + text("to") + ": " + to.getValue(),
                shown, Set.of(FEES_COLUMN));
    }

    private void exportExcel() {
        try {
            rowsTable.exportExcel(text("treasury.fee.report.title"), shown);
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.history.export.excel"), e);
        }
    }

    @Override
    public String title() {
        return text("treasury.fee.report.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    private static Label caption(String key) {
        Label label = new Label(LanguageManager.getInstance().getString(key));
        label.getStyleClass().add("form-label");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
