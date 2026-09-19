package com.hamza.account.controller.employee;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.delegate.DelegatePerformanceMonth;
import com.hamza.account.features.delegate.DelegatePerformanceRow;
import com.hamza.account.features.delegate.DelegatePerformanceService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
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
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * The delegates' performance for one month: what each sold, took back and collected, and - for a
 * reader allowed to see rates - his target, how far he is along it and what the month would pay.
 *
 * <p><b>The screen displays and does not decide.</b> The figures are
 * {@code DelegateActivityQuery}'s, grouped in SQL; the commission is {@code CommissionTiers}';
 * the totals are {@code DelegatePerformanceMonth}'s. There is no arithmetic in this class.
 *
 * <p><b>The rate columns do not exist for a reader without {@code commission.show}</b> - they
 * are not built, rather than built and hidden, because the service did not fetch what would fill
 * them. The view menu, the PDF and the spreadsheet all read the table's own columns, so what is
 * not on the screen is off the paper and out of the file as well.
 *
 * <p>The month is in the bar and never behind a panel: it says <em>which</em> report this is.
 * The commission shown is a preview of the month as it stands; nothing here stores one.
 */
@Log4j2
public class DelegatePerformanceController implements AppSettingInterface {

    private static final String ACTIONS = "delegate-actions";

    private static final Set<String> TOTALLED = Set.of("delegate-sales", "delegate-returns",
            "delegate-net", "delegate-collected", "delegate-commission");

    private final DelegatePerformanceService service = ServiceRegistry.get(DelegatePerformanceService.class);
    private final boolean ratesVisible = service.ratesVisible();

    private final TableView<DelegatePerformanceRow> table = new TableView<>();
    private final ContentSizedColumns<DelegatePerformanceRow> columnSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();
    private final ListToolbar toolbar = new ListToolbar();

    private final Label monthLabel = new Label();
    private final Label statNet = statValue("delegate-stat-net");
    private final Label statCollected = statValue("delegate-stat-collected");
    private final Label statUnattributed = statValue("delegate-stat-unattributed");
    private final Label statBelow = statValue("delegate-stat-below");
    private final Label statCommission = statValue("delegate-stat-commission");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final StackPane content = new StackPane();

    private YearMonth month = YearMonth.now();
    private DelegatePerformanceMonth shown;
    private int generation;

    @Override
    public Pane pane() {
        buildTable();
        columnViews().install(viewMenu, table);

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setTop(new VBox(8, titleBar(), statCards(), bar()));
        layout.setCenter(content);
        BorderPane.setMargin(content, new Insets(8, 0, 0, 0));

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        content.getChildren().addAll(table, progress);

        StackPane screen = new StackPane(layout);
        screen.getStyleClass().addAll("app-root", "screen-employees");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("delegate-performance");
        // A dialog takes its size from this node: without one the last column - the commission
        // itself - opened behind a scroll bar. Fits a 1366x768 screen.
        screen.setPrefSize(1240, 640);

        Platform.runLater(this::load);
        return screen;
    }

    private HBox titleBar() {
        Label title = new Label(text("delegate.performance.title"));
        title.getStyleClass().add("party-screen-title");
        HBox bar = new HBox(12, AppIcon.REPORT.graphic(24), title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    /**
     * The figures above the table. "Collected with no delegate" is there on purpose: without it
     * the collected column cannot be reconciled with what the tills took, and on an install that
     * has just upgraded it is where every earlier collection sits.
     */
    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("delegate-stats");
        cards.getChildren().addAll(
                card("delegate.performance.stat.net", statNet),
                card("delegate.performance.stat.collected", statCollected),
                card("delegate.performance.stat.unattributed", statUnattributed));
        if (ratesVisible) {
            cards.getChildren().addAll(
                    card("delegate.performance.stat.below", statBelow),
                    card("delegate.performance.stat.commission", statCommission));
        }
        return cards;
    }

    private VBox card(String titleKey, Label value) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("stat-title");
        VBox box = new VBox(4, title, value);
        box.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        box.setMinWidth(150);
        return box;
    }

    private VBox bar() {
        monthLabel.getStyleClass().add("form-label");
        monthLabel.setMinWidth(90);
        monthLabel.setAlignment(Pos.CENTER);
        monthLabel.setId("delegate-month");

        // Each caption and control of the month stays in one HBox, so a wrapping row cannot
        // separate "previous" from the month it steps.
        HBox monthBox = new HBox(6,
                stepper("delegate.performance.month.previous", () -> step(-1)),
                monthLabel,
                stepper("delegate.performance.month.next", () -> step(1)),
                stepper("delegate.performance.month.current", () -> show(YearMonth.now())));
        monthBox.setAlignment(Pos.CENTER_LEFT);

        toolbar.searchField(monthBox)
                .refresh(ListToolbar.refreshButton(this::load))
                .print(ListToolbar.printButton(this::print))
                .export(button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel))
                .view(viewMenu);
        FlowPane row = toolbar.installIn(new FlowPane(8, 8));
        row.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(text(ratesVisible
                ? "delegate.performance.hint.preview" : "delegate.performance.hint.activity"));
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);

        VBox bar = new VBox(8, row, hint);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    private void buildTable() {
        table.setId("delegate-performance-table");
        table.setPlaceholder(new Label(text("delegate.performance.empty")));

        List<TableColumn<DelegatePerformanceRow, ?>> columns = new ArrayList<>(List.of(
                // First, not last: a column appended to the end lands behind the horizontal scroll.
                // No permission of its own - whoever reads this row may read what it is made of.
                named(ACTIONS, RowActionsColumn.of("employee.column.actions", List.of(
                        RowAction.of("delegate.detail.title", AppIcon.SHOW, "app-neutral-button",
                                null, this::openDetail)))),
                named("delegate-name", Columns.text("delegate.performance.column.name", DelegatePerformanceRow::name)),
                named("delegate-sales", Columns.money("delegate.performance.column.sales",
                        row -> row.activity().sales())),
                named("delegate-returns", Columns.money("delegate.performance.column.returns",
                        row -> row.activity().salesReturns())),
                named("delegate-net", Columns.money("delegate.performance.column.net",
                        row -> row.activity().netSales())),
                named("delegate-collected", Columns.money("delegate.performance.column.collected",
                        row -> row.activity().collected()))));
        if (ratesVisible) {
            columns.add(named("delegate-basis", Columns.text("commission.rule.basis",
                    row -> row.rule().map(rule -> text(rule.basis().messageKey())).orElse(""))));
            columns.add(named("delegate-target", Columns.money("commission.rule.target",
                    DelegatePerformanceRow::target)));
            columns.add(named("delegate-achievement", Columns.money("delegate.performance.column.achievement",
                    DelegatePerformanceRow::achievementPercent)));
            columns.add(named("delegate-rate", Columns.money("delegate.performance.column.rate",
                    DelegatePerformanceRow::ratePercent)));
            columns.add(named("delegate-commission", Columns.money("delegate.performance.column.commission",
                    DelegatePerformanceRow::commission)));
        }
        table.getColumns().setAll(columns);

        columnSizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    private TableColumnViews<DelegatePerformanceRow> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(DelegatePerformanceController.class)
                .node("delegate-performance");
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.FULL,
                Set.of("delegate-name", "delegate-net", "delegate-collected", "delegate-achievement",
                        "delegate-commission"),
                Set.of(ACTIONS));
    }

    /** The row's month taken apart - on the month this report is showing, not on today's. */
    private void openDetail(DelegatePerformanceRow row) {
        try {
            new com.hamza.account.view.OpenApplication<>(
                    new DelegateDetailController(row.activity().employeeId(), row.name(), month));
        } catch (Exception e) {
            report(e);
        }
    }

    // ---- loading ---------------------------------------------------------------------------

    private void step(int months) {
        show(month.plusMonths(months));
    }

    private void show(YearMonth chosen) {
        month = chosen;
        load();
    }

    /** Off the JavaFX thread; an answer to a month the user has already left is thrown away. */
    private void load() {
        monthLabel.setText(month.toString());
        int mine = ++generation;
        YearMonth asked = month;
        progress.setVisible(true);

        Task<DelegatePerformanceMonth> task = new Task<>() {
            @Override
            protected DelegatePerformanceMonth call() throws Exception {
                return service.month(asked);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                paint(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                report(task.getException());
            }
        });
        Thread worker = new Thread(task, "delegate-performance");
        worker.setDaemon(true);
        worker.start();
    }

    private void paint(DelegatePerformanceMonth loaded) {
        shown = loaded;
        table.setItems(FXCollections.observableArrayList(loaded.rows()));
        columnSizing.layout(table);

        statNet.setText(Columns.money(loaded.totalNetSales()));
        statCollected.setText(Columns.money(loaded.totalCollected()));
        statUnattributed.setText(Columns.money(loaded.unattributedCollections()));
        statBelow.setText(String.valueOf(loaded.belowLowestTier()));
        statCommission.setText(Columns.money(loaded.totalCommission()));
    }

    // ---- print and export ------------------------------------------------------------------

    /** The month on screen, in the columns on screen, with a totals line under the money. */
    private void print() {
        if (nothingToWrite("party.error.no.data.print")) {
            return;
        }
        String title = text("delegate.performance.title");
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfLayout layout = TablePdfLayout.from(table, shown.rows(), Set.of(ACTIONS), TOTALLED, text("total"));
        TablePdfReport.write(target, title, subtitle(), layout, () -> { });
    }

    private void exportExcel() {
        if (nothingToWrite("party.error.no.data.export")) {
            return;
        }
        try {
            int written = ExportData.exportDataToExcel(shown.rows(),
                    VisibleColumnsExcelWriter.of(text("delegate.performance.title"), table, Set.of(ACTIONS), shown.rows()));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * The month, and the one figure that is not a row - so the paper says what the screen says
     * about collections that name no delegate.
     */
    private String subtitle() {
        return text("delegate.performance.month") + ": " + shown.month() + "  |  "
                + text("delegate.performance.stat.unattributed") + ": "
                + Columns.money(shown.unattributedCollections());
    }

    private boolean nothingToWrite(String key) {
        if (shown == null || shown.rows().isEmpty()) {
            report(new UserValidationException(text(key)));
            return true;
        }
        return false;
    }

    // ---- plumbing --------------------------------------------------------------------------

    @Override
    public String title() {
        return text("delegate.performance.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }

    private void report(Throwable error) {
        AllAlerts.handleError(text("delegate.performance.title"),
                error instanceof Exception ? (Exception) error : new Exception(error));
    }

    private Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    /** A month step: words, no icon - "previous" and "next" are not pictures anybody agrees on in RTL. */
    private static Button stepper(String key, Runnable action) {
        Button button = new Button(text(key));
        button.getStyleClass().add("app-neutral-button");
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Label statValue(String id) {
        Label label = new Label("0");
        label.getStyleClass().add("stat-value");
        label.setId(id);
        return label;
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
