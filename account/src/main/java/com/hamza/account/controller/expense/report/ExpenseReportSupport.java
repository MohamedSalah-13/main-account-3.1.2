package com.hamza.account.controller.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What the report tabs share: loading off the JavaFX thread with a token that drops a superseded answer,
 * writing a percentage, opening the list, and the two files.
 */
final class ExpenseReportSupport {

    /** Reads one report; a checked failure is shown, never swallowed. */
    @FunctionalInterface
    interface Reader<T> {
        T read() throws DaoException;
    }

    private final Consumer<ExpenseFilter> openList;
    private final Function<ExpenseFilter, String> scopeText;

    /**
     * @param scopeText what a printed report writes under its title for the scope it was read over
     */
    ExpenseReportSupport(Consumer<ExpenseFilter> openList, Function<ExpenseFilter, String> scopeText) {
        this.openList = openList;
        this.scopeText = scopeText;
    }

    String describe(ExpenseFilter scope) {
        return scopeText.apply(scope);
    }

    /** Opens the expenses list on a filter a report line stands for. */
    void openList(Optional<ExpenseFilter> filter) {
        filter.ifPresent(openList);
    }

    /**
     * A background load whose answer is used only while it is still the latest one asked for - a period
     * changed twice quickly must not end with the first answer drawn over the second.
     */
    static <T> void load(int[] generation, ProgressIndicator progress, String threadName, Reader<T> reader,
                         Consumer<T> show) {
        int mine = ++generation[0];
        progress.setVisible(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return reader.read();
            }
        };
        task.setOnSucceeded(event -> {
            if (mine == generation[0]) {
                progress.setVisible(false);
                show.accept(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (mine == generation[0]) {
                progress.setVisible(false);
                report(task.getException());
            }
        });
        Thread worker = new Thread(task, threadName);
        worker.setDaemon(true);
        worker.start();
    }

    /** "12.5%", "+3.0%" with a sign, or a dash where there is nothing to divide by. */
    static String percent(Optional<BigDecimal> value, boolean signed) {
        return value.map(number -> (signed && number.signum() > 0 ? "+" : "") + number.toPlainString() + "%")
                .orElse("—");
    }

    /** Prints a table of what is drawn, with a totals line over the named columns. */
    static <T> void printTable(TableView<T> table, List<T> rows, String title, String subtitle,
                               Set<String> totalled, byte[] chart) {
        if (rows.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        // "الاتجاه" alone on a page says nothing about what was trended - found on the first printed report.
        title = text("expense.report.title") + " - " + title;
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfLayout layout = TablePdfLayout.from(table, rows, Set.of(ExpenseReportsController.ACTIONS_COLUMN),
                totalled, text("total"));
        if (chart == null) {
            TablePdfReport.write(target, title, subtitle, layout, () -> { });
        } else {
            TablePdfReport.write(target, title, subtitle, chart, layout, () -> { });
        }
    }

    /** A spreadsheet of the table's visible columns. */
    static <T> void exportTable(TableView<T> table, List<T> rows, String sheet) {
        try {
            if (rows.isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(rows,
                    VisibleColumnsExcelWriter.of(sheet, table, Set.of(ExpenseReportsController.ACTIONS_COLUMN), rows));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            report(e);
        }
    }

    static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    static VBox empty(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return new VBox(label);
    }

    static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    static Label statValue(String id) {
        Label label = new Label("0.00");
        label.getStyleClass().add("stat-value");
        label.setId(id);
        return label;
    }

    static VBox card(String titleKey, Label value) {
        Label caption = new Label(text(titleKey));
        caption.getStyleClass().add("stat-title");
        VBox card = new VBox(4, caption, value);
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        card.setMinWidth(150);
        return card;
    }

    static void report(Throwable e) {
        AllAlerts.handleError(text("expense.error.operation"),
                e instanceof Exception exception ? exception : new RuntimeException(e));
    }

    static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
