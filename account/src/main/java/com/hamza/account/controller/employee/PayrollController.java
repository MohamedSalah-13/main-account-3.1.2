package com.hamza.account.controller.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.payroll.PayrollLine;
import com.hamza.account.features.employee.payroll.PayrollLineEdit;
import com.hamza.account.features.employee.payroll.PayrollPeriod;
import com.hamza.account.features.employee.payroll.PayrollRun;
import com.hamza.account.features.employee.payroll.PayrollRunStatus;
import com.hamza.account.features.employee.payroll.PayrollService;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.TableColumnViews;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.function.Function;

/**
 * The month's payroll on screen: pick a month, look at it, correct it, approve it, pay it.
 *
 * <h2>One answer decides every control</h2>
 * {@code run.isEditable()} - which is {@code status == DRAFT} and nothing else. The buttons,
 * the table's editability and the status bar all read that same answer rather than each
 * writing its own {@code if}, which is how a screen ends up offering an action the service
 * then refuses. The stock-count screen arrived at the same shape for the same reason.
 *
 * <h2>The screen never computes a figure</h2>
 * An edited cell sends what was typed to {@link PayrollService#updateLine}, which recalculates
 * the basic and the net through the same calculator that built the draft. A screen that wrote
 * a net it worked out itself would be a second definition of a month's pay - the defect the
 * whole employees area exists to remove.
 *
 * <h2>Approving and paying are separate buttons because they are separate permissions</h2>
 * Who computes does not disburse. Each button is left out rather than shown and refused when
 * its permission is absent - the {@code isGranted} hint - and the service behind it still calls
 * {@code require}.
 */
@Log4j2
@FxmlPath(pathFile = "payroll.fxml")
public class PayrollController implements AppSettingInterface {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** The columns a printed totals line adds up. A sum of rates answers nothing. */
    private static final Set<String> TOTALLED_COLUMNS = Set.of(
            "payroll-basic", "payroll-allowances", "payroll-commission",
            "payroll-deductions", "payroll-absence-deduction", "payroll-advances", "payroll-net");

    private final PayrollService service = ServiceRegistry.get(PayrollService.class);

    private final ComboBox<PayrollRun> runs = new ComboBox<>();
    private final Spinner<Integer> year = new Spinner<>();
    private final ComboBox<Integer> month = new ComboBox<>();
    private final TextField notes = new TextField();

    private final TableView<PayrollLine> table = new TableView<>();
    private final ContentSizedColumns<PayrollLine> columnSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();

    private final Label statusLabel = new Label();
    private final Label statLines = statValue("stat-lines");
    private final Label statEarned = statValue("stat-earned");
    private final Label statDeducted = statValue("stat-deducted");
    private final Label statNet = statValue("stat-net");

    private final Button close = closeButton();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    private PayrollRun current;
    private List<PayrollLine> lines = new ArrayList<>();

    @FXML
    public void initialize() {
        stackPane.getStyleClass().add("screen-employees");

        buildTable();
        columnViews().install(viewMenu, table);
        box.getChildren().setAll(identityHeader(), statCards(), runBar(), actionBar(),
                tableArea(), footer());
        VBox.setVgrow(box, Priority.ALWAYS);
        Platform.runLater(this::loadRuns);
    }

    // ---- the screen --------------------------------------------------------------------------

    private HBox identityHeader() {
        HBox iconBox = new HBox(AppIcon.REPORT.graphic(32));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-screen-icon-box");

        Label title = new Label(text("payroll.title"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("payroll.subtitle"));
        subtitle.getStyleClass().add("party-screen-subtitle");
        subtitle.setWrapText(true);

        VBox textBox = new VBox(3, title, subtitle);
        textBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox bar = new HBox(14, iconBox, textBox);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private Pane statCards() {
        HBox cards = new HBox(12,
                card("payroll.stat.lines", statLines),
                card("payroll.stat.earned", statEarned),
                card("payroll.stat.deducted", statDeducted),
                card("payroll.stat.net", statNet));
        cards.setId("payroll-stats");
        return cards;
    }

    private VBox card(String titleKey, Label value) {
        Label caption = new Label(text(titleKey));
        caption.getStyleClass().add("stat-title");
        VBox pane = new VBox(4, caption, value);
        pane.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        pane.setMinWidth(160);
        return pane;
    }

    private static Label statValue(String id) {
        Label label = new Label(Columns.money(BigDecimal.ZERO));
        label.setId(id);
        label.getStyleClass().add("stat-value");
        return label;
    }

    /** Choosing which month to look at, and creating one that does not exist yet. */
    private HBox runBar() {
        runs.setConverter(converter(run -> run == null ? ""
                : run.period() + "  -  " + text(run.status().messageKey())));
        runs.setOnAction(event -> show(runs.getValue()));
        runs.setMinWidth(240);

        int thisYear = LocalDate.now().getYear();
        year.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                2000, 2200, thisYear));
        year.setEditable(true);
        year.setMaxWidth(110);

        for (int m = 1; m <= 12; m++) {
            month.getItems().add(m);
        }
        month.getSelectionModel().select(Integer.valueOf(LocalDate.now().getMonthValue()));
        month.setMinWidth(80);

        notes.setPromptText(text("payroll.notes"));
        HBox.setHgrow(notes, Priority.ALWAYS);

        HBox bar = new HBox(8,
                caption("payroll.run"), runs,
                new Label("  "),
                caption("payroll.year"), year,
                caption("payroll.month"), month,
                notes);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("filter-bar");
        return bar;
    }

    /**
     * The actions, each present only where its permission is.
     * <p>
     * A button left out is the {@code isGranted} hint; the service still calls {@code require}.
     * Refresh and the view menu are here rather than in a row because they act on the list.
     */
    private HBox actionBar() {
        List<javafx.scene.Node> buttons = new ArrayList<>();
        buttons.add(button("payroll.action.payslip", AppIcon.PRINT, this::printPayslip));
        if (AuthorizationGuard.isGranted(AppPermissions.PAYROLL_CREATE)) {
            buttons.add(primary("payroll.action.create", AppIcon.ADD, this::createDraft));
            buttons.add(button("payroll.action.rebuild", AppIcon.REFRESH, this::rebuildDraft));
        }
        if (AuthorizationGuard.isGranted(AppPermissions.PAYROLL_APPROVE)) {
            buttons.add(primary("payroll.action.approve", AppIcon.CONFIRM, this::approve));
        }
        if (AuthorizationGuard.isGranted(AppPermissions.PAYROLL_PAY)) {
            buttons.add(button("payroll.action.pay", AppIcon.TREASURY_CASH, this::markPaid));
        }
        if (AuthorizationGuard.isGranted(AppPermissions.PAYROLL_CREATE)) {
            buttons.add(button("payroll.action.delete", AppIcon.DELETE, this::deleteDraft));
        }

        // The list's own actions first, in the order every list screen uses; the run's workflow
        // - create, approve, pay, delete - after them.
        HBox bar = new ListToolbar()
                .refresh(ListToolbar.refreshButton(this::loadRuns))
                .print(ListToolbar.printButton(this::print))
                .export(button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel))
                .view(viewMenu)
                .extra(buttons.toArray(new javafx.scene.Node[0]))
                .installIn(new HBox(8));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("filter-bar");
        return bar;
    }

    private VBox tableArea() {
        statusLabel.getStyleClass().add("form-label");
        statusLabel.setWrapText(true);
        VBox area = new VBox(8, statusLabel, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    private HBox footer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, close, spacer);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("summary-card", "party-summary-bar");
        return bar;
    }

    private void buildTable() {
        table.setId("payroll-table");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPlaceholder(new Label(text("payroll.empty")));
        table.setEditable(true);

        table.getColumns().setAll(List.of(
                named("payroll-employee", Columns.text("name", PayrollLine::employeeName)),
                named("payroll-job", Columns.text("employee.column.job", PayrollLine::jobName)),
                named("payroll-kind", Columns.text("employee.column.salary.kind",
                        line -> text(line.salaryKind().messageKey()))),
                named("payroll-rate", Columns.money("payroll.column.rate", PayrollLine::rate)),
                named("payroll-basic", Columns.money("payroll.column.basic", PayrollLine::basic)),
                named("payroll-allowances",
                        Columns.money("payroll.column.allowances", PayrollLine::allowances)),
                named("payroll-commission",
                        Columns.money("payroll.column.commission", PayrollLine::commission)),
                named("payroll-absence-days",
                        Columns.money("payroll.column.absence.days", PayrollLine::absenceDays)),
                named("payroll-absence-deduction", Columns.money("payroll.column.absence",
                        PayrollLine::absenceDeduction)),
                named("payroll-deductions",
                        Columns.money("payroll.column.deductions", PayrollLine::deductions)),
                named("payroll-advances",
                        Columns.money("payroll.column.advances", PayrollLine::advancesOutstanding)),
                named("payroll-net", Columns.money("payroll.column.net", PayrollLine::netPay))));

        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    private static <T> TableColumn<PayrollLine, T> named(String id,
                                                         TableColumn<PayrollLine, T> column) {
        column.setId(id);
        return column;
    }

    private TableColumnViews<PayrollLine> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(PayrollController.class)
                .node("payroll");
        // Full by default, so nobody's screen loses a column it never had: this list is new.
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.FULL,
                Set.of("payroll-employee", "payroll-basic", "payroll-allowances",
                        "payroll-deductions", "payroll-net"),
                Set.of());
    }

    // ---- loading -----------------------------------------------------------------------------

    private void loadRuns() {
        loadRuns(current == null ? 0 : current.id());
    }

    /**
     * Reloads the list and selects one run by id.
     * <p>
     * The id is <b>passed in rather than read back from {@code current}</b>, and the combo's
     * handler is detached while the items are replaced. Both are needed for the same reason:
     * {@code setAll} fires the handler, which writes whatever the combo happens to hold into
     * {@code current} - so a "keep what was selected" rule read afterwards re-selects the run
     * that was showing before. Creating a month then left the screen on the previous month,
     * announcing that it had created one. Found by pressing the button.
     */
    private void loadRuns(int selectRunId) {
        try {
            List<PayrollRun> list = service.recentRuns(60);
            runs.setOnAction(null);
            runs.getItems().setAll(list);
            runs.setOnAction(event -> show(runs.getValue()));
            if (list.isEmpty()) {
                show(null);
                return;
            }
            PayrollRun keep = list.stream()
                    .filter(run -> run.id() == selectRunId).findFirst().orElse(list.get(0));
            runs.getSelectionModel().select(keep);
            show(keep);
        } catch (Exception e) {
            report(e);
        }
    }

    private void show(PayrollRun run) {
        current = run;
        try {
            lines = run == null ? new ArrayList<>() : service.linesOf(run.id());
        } catch (Exception e) {
            lines = new ArrayList<>();
            report(e);
        }
        table.getItems().setAll(lines);
        columnSizing.layout(table);
        table.setEditable(run != null && run.isEditable());
        updateStatus();
        updateCards();
    }

    /** Who did what and when - the thing a person opening a closed month needs first. */
    private void updateStatus() {
        if (current == null) {
            statusLabel.setText(text("payroll.status.none"));
            return;
        }
        StringBuilder line = new StringBuilder();
        line.append(current.period()).append("  |  ")
                .append(text(current.status().messageKey()));
        if (current.createdByName() != null) {
            line.append("  |  ").append(text("payroll.status.created.by"))
                    .append(' ').append(current.createdByName());
        }
        if (current.approvedAt() != null) {
            line.append("  |  ").append(text("payroll.status.approved.by")).append(' ')
                    .append(current.approvedByName()).append(' ')
                    .append(current.approvedAt().format(STAMP));
        }
        if (current.paidAt() != null) {
            line.append("  |  ").append(text("payroll.status.paid.by")).append(' ')
                    .append(current.paidByName()).append(' ')
                    .append(current.paidAt().format(STAMP));
        }
        statusLabel.setText(line.toString());
    }

    private void updateCards() {
        BigDecimal earned = sum(PayrollLine::earned);
        BigDecimal deducted = sum(PayrollLine::totalDeductions);
        BigDecimal net = sum(PayrollLine::netPay);
        statLines.setText(String.valueOf(lines.size()));
        statEarned.setText(Columns.money(earned));
        statDeducted.setText(Columns.money(deducted));
        statNet.setText(Columns.money(net));
    }

    private BigDecimal sum(Function<PayrollLine, BigDecimal> part) {
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollLine line : lines) {
            total = total.add(part.apply(line));
        }
        return total;
    }

    // ---- the actions -------------------------------------------------------------------------

    private void createDraft() {
        try {
            PayrollPeriod period = PayrollPeriod.parse(year.getValue(), month.getValue());
            int runId = service.createDraft(period, emptyToNull(notes.getText()));
            notes.clear();
            loadRuns(runId);
            AllAlerts.alertSaveWithMessage(text("payroll.created"));
        } catch (Exception e) {
            report(e);
        }
    }

    private void rebuildDraft() {
        if (requireRun() == null) {
            return;
        }
        if (!AllAlerts.confirmSave()) {
            return;
        }
        try {
            service.rebuildDraft(current.id());
            loadRuns();
        } catch (Exception e) {
            report(e);
        }
    }

    private void approve() {
        if (requireRun() == null) {
            return;
        }
        if (!AllAlerts.confirmSave()) {
            return;
        }
        try {
            service.approve(current.id());
            loadRuns();
            AllAlerts.alertSaveWithMessage(text("payroll.approved"));
        } catch (Exception e) {
            report(e);
        }
    }

    private void markPaid() {
        if (requireRun() == null) {
            return;
        }
        if (!AllAlerts.confirmSave()) {
            return;
        }
        try {
            service.markPaid(current.id());
            loadRuns();
            AllAlerts.alertSaveWithMessage(text("payroll.paid"));
        } catch (Exception e) {
            report(e);
        }
    }

    private void deleteDraft() {
        if (requireRun() == null) {
            return;
        }
        if (!AllAlerts.confirmDelete()) {
            return;
        }
        try {
            service.deleteDraft(current.id());
            current = null;
            loadRuns();
        } catch (Exception e) {
            report(e);
        }
    }

    private PayrollRun requireRun() {
        if (current == null) {
            AllAlerts.alertError(text("payroll.error.missing"));
        }
        return current;
    }

    // ---- printing ----------------------------------------------------------------------------

    private void print() {
        if (requireRun() == null || lines.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        String title = text("payroll.title") + " - " + current.period();
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfLayout layout = TablePdfLayout.from(table, lines, Set.of(), TOTALLED_COLUMNS,
                text("total"));
        TablePdfReport.write(target, title, statusLabel.getText(), layout, () -> {
        });
    }

    /**
     * One employee's payslip.
     * <p>
     * It prints {@code advancesOutstanding} as a line of its own, below the total, <b>and does
     * not subtract it</b>: the accountant reads "this is what he earned, this is what he has
     * already had, so hand over the difference". Subtracting it would charge the employee twice
     * for one payment (rule ق-٥), and a payslip that did so would look exactly like one that
     * did not.
     */
    private void printPayslip() {
        PayrollLine line = table.getSelectionModel().getSelectedItem();
        if (current == null || line == null) {
            AllAlerts.alertError(text("payroll.error.select.line"));
            return;
        }
        String title = text("payroll.payslip.title") + " - " + line.employeeName();
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{text("payroll.column.basic"), Columns.money(line.basic())});
        rows.add(new String[]{text("payroll.column.allowances"), Columns.money(line.allowances())});
        rows.add(new String[]{text("payroll.column.commission"), Columns.money(line.commission())});
        rows.add(new String[]{text("payroll.column.absence"),
                Columns.money(line.absenceDeduction().negate())});
        rows.add(new String[]{text("payroll.column.deductions"),
                Columns.money(line.deductions().negate())});
        rows.add(new String[]{text("payroll.payslip.advances.note"),
                Columns.money(line.advancesOutstanding())});

        TablePdfLayout layout = new TablePdfLayout(
                new String[]{text("payroll.payslip.item"), text("payroll.payslip.amount")},
                new float[]{60f, 40f},
                rows,
                new String[]{text("payroll.column.net"), Columns.money(line.netPay())});

        String subtitle = current.period() + "  |  " + line.employeeName()
                + "  |  " + text(line.salaryKind().messageKey());
        TablePdfReport.write(target, title, subtitle, layout, () -> {
        });
    }

    private void exportExcel() {
        try {
            if (lines.isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(lines,
                    VisibleColumnsExcelWriter.of(text("payroll.title"), table, Set.of(), lines));
            if (written < 1) {
                return;
            }
            AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
        } catch (Exception e) {
            report(e);
        }
    }

    // ---- plumbing ----------------------------------------------------------------------------

    private Button primary(String key, AppIcon icon, Runnable action) {
        Button button = button(key, icon, action);
        button.getStyleClass().remove("app-neutral-button");
        button.getStyleClass().add("app-primary-button");
        return button;
    }

    private Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    /** See {@link #addLastPane()}: this screen has no dialog buttons, so it carries its own way out. */
    private Button closeButton() {
        Button button = button("common.close", AppIcon.CLOSE, () -> {
        });
        button.setId("btnClose");
        button.setOnAction(event -> ((Stage) button.getScene().getWindow()).close());
        return button;
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static <T> StringConverter<T> converter(Function<T, String> toText) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return toText.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    private void report(Throwable error) {
        AllAlerts.handleError(text("payroll.error.operation"),
                error instanceof Exception exception ? exception : new RuntimeException(error));
    }

    // ---- the dialog contract -------------------------------------------------------------------

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return text("payroll.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    /**
     * No dialog buttons: approving and paying are this screen's own actions, and a generic
     * "save" over a run that is approved by a named person would mean nothing. The close button
     * in the footer is the way out - the same shape {@code DeleteDataController} uses, and what
     * the statement screen was changed to after its save button was found asking a question it
     * had no answer to.
     */
    @Override
    public boolean addLastPane() {
        return false;
    }

    @Override
    public double minWidth() {
        return 1000;
    }

    @Override
    public double minHeight() {
        return 620;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }

    /** Corrects one line of a draft; the service recalculates what follows from it. */
    void applyEdit(PayrollLine line, BigDecimal absenceDays, BigDecimal deductions,
                   BigDecimal commission) {
        try {
            PayrollLineEdit edit = PayrollLineEdit.parse(line.id(), line.workedDays(), absenceDays,
                    line.workedHours(), commission, deductions, line.notes());
            service.updateLine(current.id(), edit);
            show(current);
        } catch (Exception e) {
            report(e);
        }
    }

    Optional<PayrollRun> currentRun() {
        return Optional.ofNullable(current);
    }

    PayrollRunStatus currentStatus() {
        return current == null ? PayrollRunStatus.DRAFT : current.status();
    }
}
