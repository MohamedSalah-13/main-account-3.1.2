package com.hamza.account.controller.convert_treasury;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.AppIcon;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What the treasury history tables have in common once their columns are declared: an id, widths
 * measured from their content, a delete button in the row it deletes, and paper and a spreadsheet
 * of the columns on screen.
 * <p>
 * Each of those was a defect seen on the transfers screen on 2026-09-18: the columns were the
 * platform's default width, so a date read {@code ...-2026-09} and a treasury's name was cut; the
 * table had no id, so its saved widths were shared with every id-less table in its package; the
 * delete button was in the toolbar, acting on "the selected row" and needing an error message for
 * when there was none; and an empty list said {@code No content in table} in an Arabic program.
 */
final class TreasuryHistoryTable<T> {

    static final String ACTIONS_COLUMN = "treasuryHistoryActions";

    private final TableView<T> table;
    private final ContentSizedColumns<T> widths = new ContentSizedColumns<>();

    /** A report: rows to read, print and export, with nothing to do to any of them. */
    TreasuryHistoryTable(TableView<T> table, String id, List<TableColumn<T, ?>> columns) {
        this(table, id, columns, null, null, null);
    }

    /**
     * @param columns  the data columns, each already carrying an id
     * @param onDelete what the row's delete button does; the service behind it still asks permission
     * @param onVoucher prints the row's own paper, or {@code null} for a list that has none
     */
    TreasuryHistoryTable(TableView<T> table, String id, List<TableColumn<T, ?>> columns,
                         PermissionKey deletePermission, Consumer<T> onDelete, Consumer<T> onVoucher) {
        this.table = table;
        table.setId(id);
        table.setPlaceholder(new Label(text("treasury.history.empty")));

        List<TableColumn<T, ?>> all = new ArrayList<>();
        List<RowAction<T>> offered = new ArrayList<>();
        if (onVoucher != null) {
            // The same permission as the list itself: whoever may see the movement may print its paper.
            offered.add(RowAction.of("treasury.voucher.action.print", AppIcon.PRINT, "app-neutral-button",
                    deletePermission, onVoucher));
        }
        if (onDelete != null) {
            offered.add(RowAction.of("delete", AppIcon.DELETE, "app-neutral-button", deletePermission, onDelete));
        }
        List<RowAction<T>> actions = RowAction.permitted(offered);
        if (!actions.isEmpty()) {
            // First, not last: a wide table scrolls sideways, and a button meant for the row in
            // front of you must not be behind that scroll.
            TableColumn<T, Void> column = RowActionsColumn.of("column.actions", actions);
            column.setId(ACTIONS_COLUMN);
            all.add(column);
        }
        all.addAll(columns);
        table.getColumns().setAll(all);
        widths.install(table);
    }

    void show(List<T> rows) {
        table.setItems(FXCollections.observableArrayList(rows));
        widths.layout(table);
    }

    /** The columns on screen, over the whole filtered extract - never the page. */
    void print(String title, String subtitle, List<T> rows, Set<String> totalledColumnIds) {
        if (rows.isEmpty()) {
            AllAlerts.alertError(text("treasury.statement.error.print.empty"));
            return;
        }
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfReport.write(target, title, subtitle,
                TablePdfLayout.from(table, rows, Set.of(ACTIONS_COLUMN), totalledColumnIds, text("total")),
                () -> { });
    }

    void exportExcel(String sheetName, List<T> rows) throws Exception {
        if (rows.isEmpty()) {
            AllAlerts.alertError(text("treasury.statement.error.print.empty"));
            return;
        }
        int written = ExportData.exportDataToExcel(rows,
                VisibleColumnsExcelWriter.of(sheetName, table, Set.of(ACTIONS_COLUMN), rows));
        // Zero is the save dialog cancelled, not a failure - a real one throws.
        if (written > 0) {
            AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
        }
    }

    static <S, C> TableColumn<S, C> withId(String id, TableColumn<S, C> column) {
        column.setId(id);
        return column;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
