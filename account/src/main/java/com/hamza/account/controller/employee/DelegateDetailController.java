package com.hamza.account.controller.employee;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.delegate.report.DelegateBreakdown;
import com.hamza.account.features.delegate.report.DelegateCollectionRow;
import com.hamza.account.features.delegate.report.DelegateDetailFilter;
import com.hamza.account.features.delegate.report.DelegateDetailRow;
import com.hamza.account.features.delegate.report.DelegateDetailService;
import com.hamza.account.features.delegate.report.DelegateDetailSummary;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
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
import javafx.util.StringConverter;

import java.io.File;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * One delegate's month in detail: his sales by customer, area, item or group, or his collections
 * one by one. Opened from his row of the performance report, on that report's month.
 *
 * <p><b>The screen displays and does not decide.</b> The rows and what they come to are
 * {@code DelegateDetailService}'s; the one thing worth knowing is on the cards rather than in the
 * code: a breakdown read off the lines shows "taken off whole invoices" as a figure of its own,
 * and every breakdown's net is the performance report's net for this delegate.
 *
 * <p>Two tables, one visible: a breakdown and a list of collections do not share a column. The
 * choice of view is in the bar and never behind a panel - it says which report this is.
 */
public class DelegateDetailController implements AppSettingInterface {

    /** The view combo's last entry: not a breakdown of sales but the list of collections. */
    private static final Object COLLECTIONS = new Object();

    private final DelegateDetailService service = new DelegateDetailService();
    private final int delegateId;
    private final String delegateName;

    private final TableView<DelegateDetailRow> breakdownTable = new TableView<>();
    private final TableView<DelegateCollectionRow> collectionTable = new TableView<>();
    private final ContentSizedColumns<DelegateDetailRow> breakdownSizing = new ContentSizedColumns<>();
    private final ContentSizedColumns<DelegateCollectionRow> collectionSizing = new ContentSizedColumns<>();
    private final TableColumn<DelegateDetailRow, ?> measureColumn =
            named("detail-measure", Columns.text("delegate.detail.column.documents",
                    row -> Columns.quantity(row.measure())));
    private final ComboBox<Object> comboView = new ComboBox<>();
    private final ListToolbar toolbar = new ListToolbar();

    private final Label monthLabel = new Label();
    private final Label statFirst = statValue("detail-stat-first");
    private final Label statSecond = statValue("detail-stat-second");
    private final Label statDiscount = statValue("detail-stat-discount");
    private final Label statNet = statValue("detail-stat-net");
    private final Label captionFirst = statTitle();
    private final Label captionSecond = statTitle();
    private final VBox discountCard = card(statTitle("delegate.detail.stat.header.discount"), statDiscount);
    private final VBox netCard = card(statTitle("delegate.detail.stat.net"), statNet);
    private final Label hint = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();

    private YearMonth month;
    private int generation;
    private List<DelegateDetailRow> shownBreakdown = List.of();
    private List<DelegateCollectionRow> shownCollections = List.of();

    public DelegateDetailController(int delegateId, String delegateName, YearMonth month) {
        this.delegateId = delegateId;
        this.delegateName = delegateName;
        this.month = month == null ? YearMonth.now() : month;
    }

    @Override
    public Pane pane() {
        buildTables();

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setTop(new VBox(8, titleBar(), statCards(), bar()));
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane content = new StackPane(breakdownTable, collectionTable, progress);
        layout.setCenter(content);
        BorderPane.setMargin(content, new Insets(8, 0, 0, 0));

        StackPane screen = new StackPane(layout);
        screen.getStyleClass().addAll("app-root", "screen-employees");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("delegate-detail");
        // A dialog takes its size from this node; fits a 1366x768 screen.
        screen.setPrefSize(980, 620);

        Platform.runLater(this::load);
        return screen;
    }

    private HBox titleBar() {
        Label title = new Label(text("delegate.detail.title") + " - " + delegateName);
        title.getStyleClass().add("party-screen-title");
        HBox bar = new HBox(12, AppIcon.REPORT.graphic(24), title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("delegate-detail-stats");
        cards.getChildren().addAll(card(captionFirst, statFirst), card(captionSecond, statSecond),
                discountCard, netCard);
        return cards;
    }

    private VBox bar() {
        monthLabel.getStyleClass().add("form-label");
        monthLabel.setMinWidth(90);
        monthLabel.setAlignment(Pos.CENTER);

        comboView.getItems().addAll((Object[]) DelegateBreakdown.values());
        comboView.getItems().add(COLLECTIONS);
        comboView.setConverter(new StringConverter<>() {
            @Override
            public String toString(Object view) {
                if (view == null) {
                    return "";
                }
                return view instanceof DelegateBreakdown breakdown
                        ? text(breakdown.messageKey())
                        : text("delegate.detail.collections");
            }

            @Override
            public Object fromString(String value) {
                return null;
            }
        });
        comboView.getSelectionModel().select(DelegateBreakdown.CUSTOMER);
        comboView.valueProperty().addListener((observable, before, after) -> load());

        // The caption stays in one HBox with the combo it names, and the month with its steppers.
        Label viewCaption = new Label(text("delegate.detail.view"));
        viewCaption.getStyleClass().add("form-label");
        viewCaption.setMinWidth(Region.USE_PREF_SIZE);
        HBox pick = new HBox(6,
                stepper("delegate.performance.month.previous", () -> step(-1)),
                monthLabel,
                stepper("delegate.performance.month.next", () -> step(1)),
                viewCaption, comboView);
        pick.setAlignment(Pos.CENTER_LEFT);

        toolbar.searchField(pick)
                .refresh(ListToolbar.refreshButton(this::load))
                .print(ListToolbar.printButton(this::print))
                .export(button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel));
        FlowPane row = toolbar.installIn(new FlowPane(8, 8));
        row.setAlignment(Pos.CENTER_LEFT);

        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        VBox bar = new VBox(8, row, hint);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    private void buildTables() {
        breakdownTable.setId("delegate-detail-breakdown");
        breakdownTable.setPlaceholder(new Label(text("delegate.detail.empty")));
        breakdownTable.getColumns().setAll(List.of(
                named("detail-name", Columns.text("name", DelegateDetailRow::name)),
                measureColumn,
                named("detail-sales", Columns.money("delegate.performance.column.sales", DelegateDetailRow::sales)),
                named("detail-returns", Columns.money("delegate.performance.column.returns", DelegateDetailRow::returns)),
                named("detail-net", Columns.money("delegate.performance.column.net", DelegateDetailRow::net))));
        breakdownSizing.install(breakdownTable);

        collectionTable.setId("delegate-detail-collections");
        collectionTable.setPlaceholder(new Label(text("delegate.detail.empty")));
        collectionTable.getColumns().setAll(List.of(
                named("collection-date", Columns.date("date", DelegateCollectionRow::date)),
                named("collection-customer", Columns.text("delegate.detail.column.customer",
                        DelegateCollectionRow::customer)),
                named("collection-invoice", Columns.text("delegate.detail.column.invoice",
                        row -> row.onAccount() ? text("delegate.detail.on.account") : String.valueOf(row.invoiceNumber()))),
                named("collection-amount", Columns.money("delegate.detail.column.amount", DelegateCollectionRow::amount)),
                named("collection-treasury", Columns.text("delegate.detail.column.treasury",
                        DelegateCollectionRow::treasury))));
        collectionSizing.install(collectionTable);
    }

    // ---- loading ---------------------------------------------------------------------------

    private void step(int months) {
        month = month.plusMonths(months);
        load();
    }

    /** Off the JavaFX thread; an answer to a view or month the user has already left is thrown away. */
    private void load() {
        monthLabel.setText(month.toString());
        Object view = comboView.getValue();
        if (view == null) {
            return;
        }
        int mine = ++generation;
        DelegateDetailFilter filter = new DelegateDetailFilter(delegateId, month.atDay(1), month.atEndOfMonth());
        progress.setVisible(true);

        Task<Object> task = new Task<>() {
            @Override
            protected Object call() throws Exception {
                return view instanceof DelegateBreakdown breakdown
                        ? service.breakdown(breakdown, filter)
                        : service.collections(filter);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                if (task.getValue() instanceof DelegateDetailService.Breakdown loaded) {
                    paint((DelegateBreakdown) view, loaded);
                } else {
                    paint((DelegateDetailService.Collections) task.getValue());
                }
            }
        });
        task.setOnFailed(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                report(task.getException());
            }
        });
        Thread worker = new Thread(task, "delegate-detail");
        worker.setDaemon(true);
        worker.start();
    }

    private void paint(DelegateBreakdown breakdown, DelegateDetailService.Breakdown loaded) {
        shownBreakdown = loaded.rows();
        shownCollections = List.of();
        measureColumn.setText(text(breakdown.readOffLines()
                ? "delegate.detail.column.quantity" : "delegate.detail.column.documents"));
        breakdownTable.setItems(FXCollections.observableArrayList(loaded.rows()));
        breakdownSizing.layout(breakdownTable);
        breakdownTable.setVisible(true);
        collectionTable.setVisible(false);

        DelegateDetailSummary summary = loaded.summary();
        captionFirst.setText(text("delegate.performance.column.sales"));
        captionSecond.setText(text("delegate.performance.column.returns"));
        statFirst.setText(Columns.money(summary.sales()));
        statSecond.setText(Columns.money(summary.returns()));
        statDiscount.setText(Columns.money(summary.headerDiscount()));
        statNet.setText(Columns.money(summary.net()));
        show(discountCard, breakdown.readOffLines());
        show(netCard, true);
        hint.setText(text(breakdown.readOffLines() ? "delegate.detail.hint.lines" : "delegate.detail.hint.documents"));
    }

    private void paint(DelegateDetailService.Collections loaded) {
        shownCollections = loaded.rows();
        shownBreakdown = List.of();
        collectionTable.setItems(FXCollections.observableArrayList(loaded.rows()));
        collectionSizing.layout(collectionTable);
        collectionTable.setVisible(true);
        breakdownTable.setVisible(false);

        captionFirst.setText(text("delegate.detail.stat.collections"));
        captionSecond.setText(text("delegate.detail.stat.on.account"));
        statFirst.setText(Columns.money(loaded.total()));
        statSecond.setText(Columns.money(loaded.onAccount()));
        show(discountCard, false);
        show(netCard, false);
        hint.setText(text("delegate.detail.hint.collections"));
    }

    /** Out of the layout as well as out of sight, so the remaining cards close the gap. */
    private static void show(VBox card, boolean visible) {
        card.setVisible(visible);
        card.setManaged(visible);
    }

    // ---- print and export ------------------------------------------------------------------

    private boolean collectionsShowing() {
        return collectionTable.isVisible();
    }

    private void print() {
        if (nothingToWrite("party.error.no.data.print")) {
            return;
        }
        File target = TablePdfReport.chooseTarget(breakdownTable.getScene().getWindow(), title());
        if (target == null) {
            return;
        }
        TablePdfLayout layout = collectionsShowing()
                ? TablePdfLayout.from(collectionTable, shownCollections, Set.of(),
                        Set.of("collection-amount"), text("total"))
                : TablePdfLayout.from(breakdownTable, shownBreakdown, Set.of(),
                        Set.of("detail-sales", "detail-returns", "detail-net"), text("total"));
        TablePdfReport.write(target, title(), subtitle(), layout, () -> { });
    }

    private void exportExcel() {
        if (nothingToWrite("party.error.no.data.export")) {
            return;
        }
        try {
            int written = collectionsShowing()
                    ? ExportData.exportDataToExcel(shownCollections,
                            VisibleColumnsExcelWriter.of(title(), collectionTable, Set.of(), shownCollections))
                    : ExportData.exportDataToExcel(shownBreakdown,
                            VisibleColumnsExcelWriter.of(title(), breakdownTable, Set.of(), shownBreakdown));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            report(e);
        }
    }

    /** Whose, which month, which view - and the figure that is not a row, so the paper reconciles too. */
    private String subtitle() {
        String which = delegateName + "  |  " + text("delegate.performance.month") + ": " + month
                + "  |  " + comboView.getConverter().toString(comboView.getValue());
        return discountCard.isVisible()
                ? which + "  |  " + text("delegate.detail.stat.header.discount") + ": " + statDiscount.getText()
                : which;
    }

    private boolean nothingToWrite(String key) {
        if (collectionsShowing() ? shownCollections.isEmpty() : shownBreakdown.isEmpty()) {
            report(new UserValidationException(text(key)));
            return true;
        }
        return false;
    }

    // ---- plumbing --------------------------------------------------------------------------

    @Override
    public String title() {
        return text("delegate.detail.title");
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
        AllAlerts.handleError(text("delegate.detail.title"),
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

    private static Button stepper(String key, Runnable action) {
        Button button = new Button(text(key));
        button.getStyleClass().add("app-neutral-button");
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static VBox card(Label title, Label value) {
        VBox box = new VBox(4, title, value);
        box.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        box.setMinWidth(150);
        return box;
    }

    private static Label statTitle() {
        Label title = new Label();
        title.getStyleClass().add("stat-title");
        return title;
    }

    private static Label statTitle(String key) {
        Label title = statTitle();
        title.setText(text(key));
        return title;
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
