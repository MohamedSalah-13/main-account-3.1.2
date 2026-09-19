package com.hamza.account.controller.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.delegate.CommissionLine;
import com.hamza.account.features.delegate.CommissionRun;
import com.hamza.account.features.delegate.CommissionRunService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
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
import javafx.scene.control.TextInputDialog;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * A month's commission: the preview while it is open, the frozen lines once it is approved.
 *
 * <p><b>The screen says which of the two it is showing, in words, above the table</b> - the same
 * columns hold a figure that will move tomorrow and one that never will, and nothing about a
 * number tells them apart.
 *
 * <p>The three buttons are offered by what the month's state allows and the reader may do, and
 * are hints in the {@code isGranted} sense: {@code CommissionRunService} still calls
 * {@code require}, and the triggers on the run refuse what the service would.
 *
 * <p>The screen displays and does not decide: the lines are {@code CommissionLine.preview}'s or
 * the stored ones, and there is no arithmetic here.
 */
@Log4j2
public class CommissionRunController implements AppSettingInterface {

    /** What one month looks like: its approved run if it has one, else the preview. */
    private record MonthView(Optional<CommissionRun> run, Optional<CommissionRun> lastCancelled,
                             List<CommissionLine> lines) {
    }

    private final CommissionRunService service = ServiceRegistry.get(CommissionRunService.class);

    private final TableView<CommissionLine> table = new TableView<>();
    private final ContentSizedColumns<CommissionLine> columnSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();
    private final ListToolbar toolbar = new ListToolbar();

    private final Label monthLabel = new Label();
    private final Label stateLabel = new Label();
    private final Label totalLabel = statValue("commission-run-total");
    private final Label postedLabel = statValue("commission-run-posted");
    private final Button approve = action("commission.run.action.approve", AppIcon.SAVE, this::approve);
    private final Button cancel = action("commission.run.action.cancel", AppIcon.DELETE, this::cancel);
    private final Button post = action("commission.run.action.post", AppIcon.REPORT, this::post);
    private final ProgressIndicator progress = new ProgressIndicator();
    private final StackPane content = new StackPane();

    /** Last month: the newest month that can be approved at all. */
    private YearMonth month = YearMonth.now().minusMonths(1);
    private MonthView shown;
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
        screen.setId("commission-run");

        Platform.runLater(this::load);
        return screen;
    }

    private HBox titleBar() {
        Label title = new Label(text("commission.run.title"));
        title.getStyleClass().add("party-screen-title");
        HBox bar = new HBox(12, AppIcon.REPORT.graphic(24), title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.getChildren().addAll(card("commission.run.stat.total", totalLabel),
                card("commission.run.stat.posted", postedLabel));
        return cards;
    }

    private VBox card(String titleKey, Label value) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("stat-title");
        VBox box = new VBox(4, title, value);
        box.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        box.setMinWidth(170);
        return box;
    }

    private VBox bar() {
        monthLabel.getStyleClass().add("form-label");
        monthLabel.setMinWidth(90);
        monthLabel.setAlignment(Pos.CENTER);
        monthLabel.setId("commission-run-month");

        HBox monthBox = new HBox(6,
                stepper("delegate.performance.month.previous", () -> show(month.minusMonths(1))),
                monthLabel,
                stepper("delegate.performance.month.next", () -> show(month.plusMonths(1))));
        monthBox.setAlignment(Pos.CENTER_LEFT);

        toolbar.searchField(monthBox)
                .refresh(ListToolbar.refreshButton(this::load))
                .print(ListToolbar.printButton(this::print))
                .export(action("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel))
                .view(viewMenu)
                .extra(approve, post, cancel);
        FlowPane row = toolbar.installIn(new FlowPane(8, 8));
        row.setAlignment(Pos.CENTER_LEFT);

        stateLabel.getStyleClass().add("form-hint");
        stateLabel.setWrapText(true);
        stateLabel.setId("commission-run-state");

        VBox bar = new VBox(8, row, stateLabel);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    private void buildTable() {
        table.setId("commission-run-table");
        table.setPlaceholder(new Label(text("commission.run.empty")));
        table.getColumns().setAll(List.of(
                named("run-name", Columns.text("delegate.performance.column.name", CommissionLine::employeeName)),
                named("run-basis", Columns.text("commission.rule.basis", line -> text(line.basis().messageKey()))),
                named("run-sales", Columns.money("delegate.performance.column.sales", CommissionLine::sales)),
                named("run-returns", Columns.money("delegate.performance.column.returns", CommissionLine::salesReturns)),
                named("run-collected", Columns.money("delegate.performance.column.collected", CommissionLine::collected)),
                named("run-base", Columns.money("commission.run.column.base", CommissionLine::baseAmount)),
                named("run-target", Columns.money("commission.rule.target", CommissionLine::target)),
                named("run-tiers", Columns.text("commission.rule.tiers", CommissionLine::tiersSnapshot)),
                named("run-achievement", Columns.money("delegate.performance.column.achievement",
                        CommissionLine::achievementPercent)),
                named("run-rate", Columns.money("delegate.performance.column.rate", CommissionLine::ratePercent)),
                named("run-amount", Columns.money("commission.run.column.amount", CommissionLine::amount)),
                named("run-posting", Columns.text("commission.run.column.posting",
                        line -> text(line.posting().messageKey())))));
        columnSizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    private TableColumnViews<CommissionLine> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(CommissionRunController.class).node("commission-run");
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.FULL,
                Set.of("run-name", "run-base", "run-achievement", "run-rate", "run-amount", "run-posting"), Set.of());
    }

    // ---- loading ---------------------------------------------------------------------------

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

        Task<MonthView> task = new Task<>() {
            @Override
            protected MonthView call() throws Exception {
                List<CommissionRun> ofMonth = service.runs().stream()
                        .filter(run -> run.period().equals(asked)).toList();
                Optional<CommissionRun> active = ofMonth.stream()
                        .filter(run -> run.status() == CommissionRun.Status.APPROVED).findFirst();
                Optional<CommissionRun> cancelled = ofMonth.stream()
                        .filter(run -> run.status() == CommissionRun.Status.CANCELLED).findFirst();
                List<CommissionLine> lines = active.isPresent()
                        ? service.linesOf(active.get().id()) : service.preview(asked);
                return new MonthView(active, cancelled, lines);
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
        Thread worker = new Thread(task, "commission-run");
        worker.setDaemon(true);
        worker.start();
    }

    private void paint(MonthView loaded) {
        shown = loaded;
        table.setItems(FXCollections.observableArrayList(loaded.lines()));
        columnSizing.layout(table);

        totalLabel.setText(Columns.money(loaded.lines().stream().map(CommissionLine::amount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)));
        postedLabel.setText(loaded.lines().stream().filter(CommissionLine::posted).count()
                + " / " + loaded.lines().size());
        stateLabel.setText(stateText(loaded));

        boolean approved = loaded.run().isPresent();
        boolean monthOver = YearMonth.now().isAfter(month);
        offer(approve, !approved && monthOver && !loaded.lines().isEmpty(), AppPermissions.COMMISSION_RUN_CREATE);
        offer(cancel, approved && loaded.run().get().mayBeCancelled(), AppPermissions.COMMISSION_RUN_UPDATE);
        offer(post, approved && loaded.lines().stream()
                        .anyMatch(line -> !line.posted() && line.amount().signum() > 0),
                AppPermissions.COMMISSION_RUN_POST);
    }

    /** Which of the two the table is holding, said in words. */
    private String stateText(MonthView loaded) {
        if (loaded.run().isPresent()) {
            CommissionRun run = loaded.run().get();
            return text("commission.run.state.approved") + " " + run.approvedBy()
                    + (run.approvedAt() == null ? "" : " - " + run.approvedAt().toLocalDate());
        }
        String preview = text(YearMonth.now().isAfter(month)
                ? "commission.run.state.preview" : "commission.run.state.open");
        return loaded.lastCancelled()
                .map(run -> preview + "  |  " + text("commission.run.state.cancelled") + " " + run.cancelReason())
                .orElse(preview);
    }

    private static void offer(Button button, boolean possible, com.hamza.account.authorization.PermissionKey key) {
        boolean visible = possible && AuthorizationGuard.isGranted(key);
        button.setVisible(visible);
        button.setManaged(visible);
    }

    // ---- the three decisions -----------------------------------------------------------------

    private void approve() {
        if (!AllAlerts.confirm_all(text("commission.run.action.approve"),
                text("commission.run.confirm.approve") + " " + month)) {
            return;
        }
        run(() -> service.approve(month, null));
    }

    private void cancel() {
        if (shown == null || shown.run().isEmpty()) {
            return;
        }
        TextInputDialog reason = new TextInputDialog();
        reason.setTitle(text("commission.run.action.cancel"));
        reason.setHeaderText(text("commission.run.cancel.reason"));
        reason.initOwner(table.getScene().getWindow());
        Optional<String> typed = reason.showAndWait();
        if (typed.isEmpty()) {
            return;
        }
        int runId = shown.run().get().id();
        run(() -> service.cancel(runId, typed.get()));
    }

    private void post() {
        if (shown == null || shown.run().isEmpty()
                || !AllAlerts.confirm_all(text("commission.run.action.post"),
                text("commission.run.confirm.post"))) {
            return;
        }
        int runId = shown.run().get().id();
        run(() -> service.postToAccounts(runId));
    }

    @FunctionalInterface
    private interface Decision {
        void take() throws Exception;
    }

    /** Takes the decision, then reads the month again rather than painting what it expects to find. */
    private void run(Decision decision) {
        try {
            decision.take();
        } catch (Exception e) {
            report(e);
        }
        load();
    }

    // ---- print and export ------------------------------------------------------------------

    private void print() {
        if (nothingToWrite("party.error.no.data.print")) {
            return;
        }
        String title = text("commission.run.title");
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfLayout layout = TablePdfLayout.from(table, shown.lines(), Set.of(), Set.of("run-amount"), text("total"));
        // The paper says whether it is a preview or an approved run, as the screen does.
        TablePdfReport.write(target, title, month + "  |  " + stateText(shown), layout, () -> { });
    }

    private void exportExcel() {
        if (nothingToWrite("party.error.no.data.export")) {
            return;
        }
        try {
            int written = ExportData.exportDataToExcel(shown.lines(),
                    VisibleColumnsExcelWriter.of(text("commission.run.title"), table, Set.of(), shown.lines()));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            report(e);
        }
    }

    private boolean nothingToWrite(String key) {
        if (shown == null || shown.lines().isEmpty()) {
            report(new UserValidationException(text(key)));
            return true;
        }
        return false;
    }

    // ---- plumbing --------------------------------------------------------------------------

    @Override
    public String title() {
        return text("commission.run.title");
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
        AllAlerts.handleError(text("commission.run.title"),
                error instanceof Exception ? (Exception) error : new Exception(error));
    }

    private static Button action(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

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
