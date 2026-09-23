package com.hamza.account.controller.reports;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.features.export.StatementPdfLayout;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.features.profitloss.ProfitLossFigures;
import com.hamza.account.features.profitloss.ProfitLossService;
import com.hamza.account.features.profitloss.statement.ComparisonBasis;
import com.hamza.account.features.profitloss.statement.JdbcProfitLossStatementRepository;
import com.hamza.account.features.profitloss.statement.OutsideProfitFigures;
import com.hamza.account.features.profitloss.statement.ProfitLossGrouping;
import com.hamza.account.features.profitloss.statement.ProfitLossMovement;
import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import com.hamza.account.features.profitloss.statement.ProfitLossPeriodRow;
import com.hamza.account.features.profitloss.statement.ProfitLossReport;
import com.hamza.account.features.profitloss.statement.ProfitLossReportService;
import com.hamza.account.features.profitloss.statement.StatementLine;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.account.table.TableColumnViews;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.VisibleColumns;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.prefs.Preferences;

/**
 * The profit and loss statement over a period ({@code features/profitloss/statement}).
 *
 * <p><b>The screen draws and does not decide.</b> Which period is compared with which, how days are
 * filed into weeks, what the statement's lines are and what they add up to are the package's, with a test
 * each. Every figure on the screen - the cards, the statement's subtotals, the table's rows - is a sum of
 * the statement's own days, so the four cannot disagree with each other or with the yearly report.</p>
 *
 * <p>Two halves side by side, because they answer two questions: the statement says what the period is
 * made of, top to bottom, against the period it is compared with; the table says how it went day by day,
 * or week by week. A row opens what it is made of - its invoices, returns and expenses - in a
 * {@link RowDetailDrawer}, so a day that lost money can be explained without leaving the screen.</p>
 *
 * <p>Each control sits beside what it changes: the period in the bar, the comparison over the statement,
 * the grouping over the table. Loading is off the JavaFX thread with a {@code generation} token that drops
 * the answer to a question the user has already replaced.</p>
 */
public class ProfitLossController {

    private static final String ACTIONS = "pl-actions";
    private static final String PERIOD = "pl-period";
    private static final String NET_SALES = "pl-net-sales";
    private static final String COST = "pl-cost";
    private static final String GROSS_PROFIT = "pl-gross-profit";
    private static final String GROSS_MARGIN = "pl-gross-margin";
    private static final String EXPENSES = "pl-expenses";
    private static final String NET_PROFIT = "pl-net-profit";
    private static final String NET_MARGIN = "pl-net-margin";

    /** What the compact view keeps: the statement's five columns, a row to a line. */
    private static final Set<String> COMPACT = Set.of(PERIOD, NET_SALES, COST, GROSS_PROFIT, EXPENSES, NET_PROFIT);
    /** The amounts the printed totals line sums. Never a margin: a sum of margins is not a margin. */
    private static final Set<String> TOTALLED = Set.of(NET_SALES, COST, GROSS_PROFIT, EXPENSES, NET_PROFIT);

    private static final PseudoClass HEADING = PseudoClass.getPseudoClass("pl-heading");
    private static final PseudoClass SUBTOTAL = PseudoClass.getPseudoClass("pl-subtotal");
    private static final PseudoClass RESULT = PseudoClass.getPseudoClass("pl-result");
    private static final String NOTHING = "—";
    /** What the row of cards is measured as wide as before it has been laid out. */
    private static final double WRAP_LENGTH = 1300;

    private final ProfitLossReportService service;

    private final ComboBox<PeriodChoice> comboPeriod = new ComboBox<>();
    private final DatePicker dateFrom = new DatePicker();
    private final DatePicker dateTo = new DatePicker();
    private final ComboBox<ComparisonBasis> comboBasis = new ComboBox<>();
    private final ComboBox<ProfitLossGrouping> comboGrouping = new ComboBox<>();
    private final Label subtitle = new Label();

    private final Label statNetSales = statValue("pl-stat-net-sales");
    private final FigureLine statNetSalesPrevious = new FigureLine();
    private final Label statGrossProfit = statValue("pl-stat-gross-profit");
    private final FigureLine statGrossMargin = new FigureLine();
    private final Label statExpenses = statValue("pl-stat-expenses");
    private final FigureLine statExpensesPrevious = new FigureLine();
    private final Label statNetProfit = statValue("pl-stat-net-profit");
    private final FigureLine statNetMargin = new FigureLine();
    private final FigureLine statNetProfitPrevious = new FigureLine();
    private final Label statOutside = statValue("pl-stat-outside");
    private final FigureLine statOutsideStock = new FigureLine();
    private final FigureLine statOutsideTill = new FigureLine();

    private final TableView<StatementLine> statementTable = new TableView<>();
    private final TableColumn<StatementLine, BigDecimal> previousColumn =
            Columns.money("profitloss.column.previous", StatementLine::previous);
    private final TableColumn<StatementLine, String> changeColumn =
            Columns.text("profitloss.column.change", line -> line.isHeading() ? "" : change(line.change()));
    private final TableView<ProfitLossPeriodRow> table = new TableView<>();
    private final TableView<ProfitLossMovement> movementsTable = new TableView<>();
    private final ContentSizedColumns<ProfitLossPeriodRow> columnSizing = new ContentSizedColumns<>();
    private final ContentSizedColumns<ProfitLossMovement> movementsSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();
    private final ListToolbar toolbar = new ListToolbar();
    private final ProgressIndicator progress = new ProgressIndicator();

    private RowDetailDrawer drawer;
    private ProfitLossReport shown;
    private int generation;
    private int movementsGeneration;
    /** Set while the screen moves its own controls, so their listeners do not each start a load. */
    private boolean arranging;

    public ProfitLossController(ProfitLossReportService service) {
        this.service = service;
    }

    /** The report over the application's own profit and loss statement, which asks the permission. */
    public static ProfitLossController standard() {
        ProfitLossService statement = ServiceRegistry.get(ProfitLossService.class);
        return new ProfitLossController(new ProfitLossReportService(statement::load,
                new JdbcProfitLossStatementRepository()));
    }

    // ---- the screen ------------------------------------------------------------------

    public Pane pane() {
        buildStatement();
        buildTable();
        columnViews().install(viewMenu, table);

        SplitPane body = new SplitPane(statementCard(), tableCard());
        body.setDividerPositions(0.44);

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setPadding(new Insets(8));
        layout.setTop(new VBox(8, header(), statCards()));
        layout.setCenter(body);
        BorderPane.setMargin(body, new Insets(8, 0, 0, 0));

        AnchorPane host = new AnchorPane(layout);
        AnchorPane.setTopAnchor(layout, 0.0);
        AnchorPane.setRightAnchor(layout, 0.0);
        AnchorPane.setBottomAnchor(layout, 0.0);
        AnchorPane.setLeftAnchor(layout, 0.0);
        drawer = RowDetailDrawer.installIn(host);
        drawer.setContent(movementsPane());
        drawer.setPreferredWidth(780);

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane screen = new StackPane(host, progress);
        screen.getStyleClass().addAll("app-root", "profit-loss");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("profit-loss");
        screen.setPrefSize(1280, 800);

        choosePreset(StatementPeriod.THIS_MONTH);
        return screen;
    }

    /** The title and the bar share one card: two stacked cards cost the tables two rows on a 768 screen. */
    private VBox header() {
        Label title = new Label(text("report.profit.loss.title"));
        title.getStyleClass().add("report-title");
        subtitle.getStyleClass().add("report-subtitle");
        subtitle.setWrapText(true);
        subtitle.setId("pl-subtitle");
        VBox titles = new VBox(4, title, subtitle);
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox top = new HBox(16, titles, bar());
        top.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(top);
        card.getStyleClass().add("app-card");
        return card;
    }

    /** The period says which statement this is, so it stays in the bar; there is no filters panel. */
    private HBox bar() {
        comboPeriod.setId("pl-period-choice");
        comboPeriod.getItems().setAll(PeriodChoice.all());
        comboPeriod.setConverter(PeriodChoice.CONVERTER);
        comboPeriod.setOnAction(event -> {
            PeriodChoice choice = comboPeriod.getValue();
            if (!arranging && choice != null && choice.preset() != null) {
                choosePreset(choice.preset());
            }
        });
        for (DatePicker picker : new DatePicker[]{dateFrom, dateTo}) {
            picker.setPrefWidth(130);
            picker.valueProperty().addListener((observable, was, now) -> {
                if (!arranging) {
                    arranging = true;
                    try {
                        comboPeriod.setValue(PeriodChoice.CUSTOM);
                    } finally {
                        arranging = false;
                    }
                    reload();
                }
            });
        }
        dateFrom.setId("pl-from");
        dateTo.setId("pl-to");
        Label caption = new Label(text("profitloss.period"));
        caption.getStyleClass().add("form-label");
        Label dash = new Label("–");
        dash.getStyleClass().add("form-label");
        HBox period = new HBox(8, caption, comboPeriod, dateFrom, dash, dateTo);
        period.setAlignment(Pos.CENTER_LEFT);

        toolbar.searchField(period)
                .refresh(ListToolbar.refreshButton(this::reload))
                .print(ListToolbar.printButton(this::print))
                .export(ListToolbar.button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel))
                .view(viewMenu);
        // A row rather than a FlowPane: beside the title a FlowPane asks for its whole wrap length and
        // leaves the title a sliver.
        HBox row = toolbar.installIn(new HBox(8));
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setMinWidth(Region.USE_PREF_SIZE);
        return row;
    }

    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("pl-stats");
        // Measured at a screen's width a FlowPane is one row; asked before it has a width it measures itself
        // wrapped at 400 points, which put the screen's minimum height past 768.
        cards.setPrefWrapLength(WRAP_LENGTH);
        cards.getChildren().addAll(
                card("profitloss.net.sales", statNetSales, statNetSalesPrevious),
                card("profitloss.gross.profit", statGrossProfit, statGrossMargin),
                card("profitloss.expenses", statExpenses, statExpensesPrevious),
                card("profitloss.net.profit", statNetProfit, statNetMargin, statNetProfitPrevious),
                card("profitloss.section.outside", statOutside, statOutsideStock, statOutsideTill));
        return cards;
    }

    private static VBox card(String titleKey, Label value, FigureLine... lines) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("stat-title");
        VBox card = new VBox(4, title, value);
        for (FigureLine line : lines) {
            card.getChildren().add(line.box);
        }
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        card.setMinWidth(190);
        return card;
    }

    /** The statement, with what it is compared against chosen over it. */
    private VBox statementCard() {
        Label title = new Label(text("profitloss.statement.title"));
        title.getStyleClass().add("section-title");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        comboBasis.setId("pl-basis");
        comboBasis.getItems().setAll(ComparisonBasis.values());
        comboBasis.setConverter(keyed(ComparisonBasis::messageKey));
        comboBasis.setValue(ComparisonBasis.PREVIOUS_PERIOD);
        comboBasis.setOnAction(event -> {
            if (!arranging) {
                reload();
            }
        });
        Label caption = new Label(text("profitloss.compare.caption"));
        caption.getStyleClass().add("form-label");
        HBox head = new HBox(8, title, grow, caption, comboBasis);
        head.setAlignment(Pos.CENTER_LEFT);

        Label note = new Label(text("profitloss.outside.note"));
        note.getStyleClass().add("form-hint");
        note.setWrapText(true);
        // A wrapping label in a VBox is offered one line's height and cut with an ellipsis otherwise.
        note.setMinHeight(Region.USE_PREF_SIZE);

        VBox card = new VBox(8, head, statementTable, note);
        VBox.setVgrow(statementTable, Priority.ALWAYS);
        card.getStyleClass().add("app-card");
        card.setMinWidth(380);
        return card;
    }

    /** The rows, with how they are grouped chosen over them. */
    private VBox tableCard() {
        Label title = new Label(text("profitloss.table.title"));
        title.getStyleClass().add("section-title");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        comboGrouping.setId("pl-grouping");
        comboGrouping.getItems().setAll(ProfitLossGrouping.values());
        comboGrouping.setConverter(keyed(ProfitLossGrouping::messageKey));
        comboGrouping.setValue(ProfitLossGrouping.DAY);
        comboGrouping.setOnAction(event -> {
            if (!arranging) {
                reload();
            }
        });
        Label caption = new Label(text("profitloss.grouping.caption"));
        caption.getStyleClass().add("form-label");
        HBox head = new HBox(8, title, grow, caption, comboGrouping);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, head, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        card.getStyleClass().add("app-card");
        card.setMinWidth(420);
        return card;
    }

    /**
     * Built in code, every title a whole key. A line's kind is a pseudo-class on its row, so the theme
     * draws a heading, a subtotal and a result - and so a deduction is not painted as a loss: only a result
     * below zero is red.
     */
    private void buildStatement() {
        statementTable.setId("pl-statement");
        statementTable.getStyleClass().add("pl-statement");
        statementTable.setPlaceholder(new Label(text("profitloss.empty")));
        statementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<StatementLine, StatementLine> caption = new TableColumn<>(text("profitloss.column.line"));
        caption.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));
        caption.setCellFactory(column -> new CaptionCell());
        caption.setSortable(false);
        caption.setPrefWidth(210);
        TableColumn<StatementLine, BigDecimal> current = Columns.money("profitloss.column.current",
                StatementLine::current);
        for (TableColumn<StatementLine, ?> column : List.of(current, previousColumn, changeColumn)) {
            column.setSortable(false);
        }
        current.setPrefWidth(110);
        previousColumn.setPrefWidth(110);
        changeColumn.setPrefWidth(70);
        changeColumn.setStyle(Columns.AMOUNT_ALIGNMENT);
        statementTable.getColumns().setAll(List.of(caption, current, previousColumn, changeColumn));
        statementTable.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(StatementLine line, boolean empty) {
                super.updateItem(line, empty);
                StatementLine.Kind kind = empty || line == null ? null : line.kind();
                pseudoClassStateChanged(HEADING, kind == StatementLine.Kind.HEADING);
                pseudoClassStateChanged(SUBTOTAL, kind == StatementLine.Kind.SUBTOTAL);
                pseudoClassStateChanged(RESULT, kind == StatementLine.Kind.RESULT);
            }
        });
    }

    /** A line's caption: a heading's word, an item indented under it, an expense heading by its own name. */
    private static final class CaptionCell extends TableCell<StatementLine, StatementLine> {
        private final Label label = new Label();
        private final Region indent = new Region();
        private final HBox box = new HBox(indent, label);

        CaptionCell() {
            indent.setMinWidth(16);
            box.setAlignment(Pos.CENTER_LEFT);
            label.getStyleClass().add("pl-caption");
        }

        @Override
        protected void updateItem(StatementLine line, boolean empty) {
            super.updateItem(line, empty);
            if (empty || line == null) {
                setGraphic(null);
                return;
            }
            label.setText(caption(line));
            boolean indented = line.kind() == StatementLine.Kind.ITEM;
            indent.setVisible(indented);
            indent.setManaged(indented);
            setGraphic(box);
        }
    }

    /**
     * Built in code, each amount through {@code Columns.money}. The actions go first, as on every list
     * here: a column of buttons at the end of a wide table lands behind its scroll.
     */
    private void buildTable() {
        table.setId("pl-periods-table");
        table.setPlaceholder(new Label(text("profitloss.empty")));
        table.setMinHeight(160);
        TableColumn<ProfitLossPeriodRow, String> period = Columns.text("profitloss.column.row", this::rowLabel);
        period.setSortable(false);
        table.getColumns().setAll(List.of(
                named(ACTIONS, actionsColumn()),
                named(PERIOD, period),
                named(NET_SALES, Columns.money("profitloss.net.sales", row -> row.figures().netSales())),
                named(COST, Columns.money("profitloss.cost.sales", row -> row.figures().costOfSales())),
                named(GROSS_PROFIT, Columns.money("profitloss.gross.profit", row -> row.figures().grossProfit())),
                named(GROSS_MARGIN, Columns.text("profitloss.column.gross.margin",
                        row -> percent(row.figures().grossMargin()))),
                named(EXPENSES, Columns.money("profitloss.expenses", row -> row.figures().expenses())),
                named(NET_PROFIT, Columns.money("profitloss.net.profit", row -> row.figures().netProfit())),
                named(NET_MARGIN, Columns.text("profitloss.column.net.margin",
                        row -> percent(row.figures().netMargin())))));
        table.setOnMouseClicked(event -> {
            ProfitLossPeriodRow selected = table.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                openMovements(selected);
            }
        });
        // An open panel follows the selection, so reading down the month is one click a day.
        table.getSelectionModel().selectedItemProperty().addListener((observable, was, row) -> {
            if (row != null && drawer != null && drawer.isShowing()) {
                openMovements(row);
            }
        });
        columnSizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    /** Opens what a row is made of. No permission of its own here: the service asks it. */
    private TableColumn<ProfitLossPeriodRow, Void> actionsColumn() {
        List<RowAction<ProfitLossPeriodRow>> actions = List.of(new RowAction<>("profitloss.action.movements",
                AppIcon.SHOW, "app-neutral-button", null, ProfitLossPeriodRow::hasActivity, this::openMovements));
        return RowActionsColumn.of("party.balances.column.actions", actions);
    }

    /** It opens compact; the full view adds the two margins. */
    private TableColumnViews<ProfitLossPeriodRow> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(ProfitLossController.class).node("profitloss");
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.COMPACT, COMPACT,
                Set.of(ACTIONS));
    }

    /** A row's invoices, returns and expenses, each with what it did to the row's columns. */
    private VBox movementsPane() {
        movementsTable.setId("pl-movements-table");
        movementsTable.setPlaceholder(new Label(text("profitloss.empty")));
        movementsTable.getColumns().setAll(List.of(
                Columns.text("profitloss.column.kind", movement -> text(movement.kind().messageKey())),
                Columns.number("profitloss.column.number", ProfitLossMovement::number),
                Columns.text("profitloss.column.name", ProfitLossMovement::name),
                Columns.money("profitloss.net.sales", ProfitLossMovement::netSales),
                Columns.money("profitloss.cost.sales", ProfitLossMovement::cost),
                Columns.money("profitloss.expenses", ProfitLossMovement::expense),
                Columns.money("profitloss.column.profit", ProfitLossMovement::profit),
                // The day and the note last: a day's row repeats its date on every line, and the profit is
                // what the panel is opened to read.
                Columns.date("profitloss.column.date", ProfitLossMovement::date),
                Columns.text("profitloss.column.note", ProfitLossMovement::note)));
        movementsSizing.install(movementsTable);
        VBox pane = new VBox(movementsTable);
        VBox.setVgrow(movementsTable, Priority.ALWAYS);
        return pane;
    }

    // ---- loading ---------------------------------------------------------------------

    /** Sets the pickers to a preset and the grouping to what suits its length, then loads once. */
    private void choosePreset(StatementPeriod preset) {
        LocalDate today = LocalDate.now();
        arranging = true;
        try {
            comboPeriod.setValue(PeriodChoice.of(preset));
            dateFrom.setValue(preset.from(today));
            dateTo.setValue(preset.to(today));
            comboGrouping.setValue(ProfitLossGrouping.suitedTo(new ProfitLossPeriod(preset.from(today),
                    preset.to(today))));
        } finally {
            arranging = false;
        }
        reload();
    }

    private void reload() {
        ProfitLossPeriod period;
        try {
            period = ProfitLossPeriod.of(dateFrom.getValue(), dateTo.getValue());
        } catch (UserValidationException e) {
            AllAlerts.alertError(e.getMessage());
            return;
        }
        ComparisonBasis basis = comboBasis.getValue();
        ProfitLossGrouping grouping = comboGrouping.getValue();
        int mine = ++generation;
        progress.setVisible(true);
        Task<ProfitLossReport> task = new Task<>() {
            @Override
            protected ProfitLossReport call() throws Exception {
                return service.report(period, basis, grouping);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                show(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (mine == generation) {
                progress.setVisible(false);
                report("profitloss.error.load", task.getException());
            }
        });
        run(task, "profit-loss-load");
    }

    private static void run(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    private void show(ProfitLossReport report) {
        shown = report;
        if (drawer.isShowing()) {
            drawer.hide();
        }
        // Two lines, never one wrapped: the comparison is a sentence of its own.
        subtitle.setText(periodSentence(report) + "\n" + comparedSentence(report));
        statementTable.setItems(FXCollections.observableArrayList(report.statement()));
        table.setItems(FXCollections.observableArrayList(report.rows()));
        columnSizing.layout(table);

        ProfitLossFigures current = report.current();
        ProfitLossFigures previous = report.previous();
        boolean compared = report.hasPrevious();
        money(statNetSales, current.netSales());
        previous(statNetSalesPrevious, compared, previous.netSales(), report.netSalesChange());
        money(statGrossProfit, current.grossProfit());
        statGrossMargin.show("profitloss.stat.margin", percent(current.grossMargin()), false, null);
        money(statExpenses, current.expenses());
        previous(statExpensesPrevious, compared, previous.expenses(), report.expensesChange());
        money(statNetProfit, current.netProfit());
        statNetMargin.show("profitloss.stat.margin", percent(current.netMargin()), false, null);
        previous(statNetProfitPrevious, compared, previous.netProfit(), report.netProfitChange());
        OutsideProfitFigures outside = report.outside();
        money(statOutside, outside.net());
        BigDecimal stock = outside.stockSurplus().subtract(outside.stockShortage());
        BigDecimal till = outside.tillSurplus().subtract(outside.tillShortage());
        statOutsideStock.show("profitloss.stat.stock", Columns.money(stock), stock.signum() < 0, null);
        statOutsideTill.show("profitloss.stat.till", Columns.money(till), till.signum() < 0, null);
    }

    private void openMovements(ProfitLossPeriodRow row) {
        if (!row.hasActivity()) {
            // A quiet day has nothing to show, and an open panel must not go on showing another day's.
            if (drawer.isShowing()) {
                drawer.hide();
            }
            return;
        }
        int mine = ++movementsGeneration;
        Task<List<ProfitLossMovement>> task = new Task<>() {
            @Override
            protected List<ProfitLossMovement> call() throws Exception {
                return service.movements(row);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine != movementsGeneration) {
                return;
            }
            List<ProfitLossMovement> movements = task.getValue();
            movementsTable.setItems(FXCollections.observableArrayList(movements));
            movementsSizing.layout(movementsTable);
            drawer.show(rowLabel(row), LanguageManager.getInstance().getString("profitloss.movements.subtitle",
                    movements.size()));
        });
        task.setOnFailed(event -> {
            if (mine == movementsGeneration) {
                report("profitloss.error.movements", task.getException());
            }
        });
        run(task, "profit-loss-movements");
    }

    // ---- printing and export ---------------------------------------------------------

    /** The statement, then the table of the columns on screen with a totals line - what is loaded, not read again. */
    private void print() {
        if (shown == null) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        String title = text("report.profit.loss.title");
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        StatementPdfLayout statement = statementLayout(shown);
        TablePdfLayout rows = TablePdfLayout.from(table, shown.rows(), Set.of(ACTIONS), TOTALLED, text("total"));
        String subtitleText = printSubtitle();
        TablePdfReport.write(target, file -> new PdfExportService().exportStatementReport(file.getAbsolutePath(),
                title, subtitleText, statement, rows.headers(), rows.columnWidths(), rows.rows(), rows.totals(),
                TablePdfReport.pageSizeFor(Math.max(rows.headers().length, 4))));
    }

    private StatementPdfLayout statementLayout(ProfitLossReport report) {
        List<StatementPdfLayout.Line> lines = new ArrayList<>();
        for (StatementLine line : report.statement()) {
            if (line.isHeading()) {
                lines.add(new StatementPdfLayout.Line(StatementPdfLayout.Style.HEADING, new String[]{caption(line)}));
                continue;
            }
            StatementPdfLayout.Style style = switch (line.kind()) {
                case SUBTOTAL -> StatementPdfLayout.Style.SUBTOTAL;
                case RESULT -> StatementPdfLayout.Style.RESULT;
                default -> StatementPdfLayout.Style.ROW;
            };
            lines.add(new StatementPdfLayout.Line(style, new String[]{caption(line), Columns.money(line.current()),
                    Columns.money(line.previous()), change(line.change())}));
        }
        return new StatementPdfLayout(new String[]{text("profitloss.column.line"), text("profitloss.column.current"),
                text("profitloss.column.previous"), text("profitloss.column.change")},
                new float[]{3, 1.4f, 1.4f, 1}, lines);
    }

    /**
     * Which period, against what, on lines of their own: a subtitle is shaped before it is wrapped and a
     * wrapped Arabic line prints its end first.
     */
    private String printSubtitle() {
        return periodSentence(shown) + "\n" + comparedSentence(shown);
    }

    /** One sheet: the statement, a blank row, then the table's columns on screen. */
    private void exportExcel() {
        try {
            if (shown == null) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            List<Object[]> rows = new ArrayList<>();
            for (StatementLine line : shown.statement()) {
                rows.add(line.isHeading() ? new Object[]{caption(line)} : new Object[]{caption(line),
                        line.current(), line.previous(), change(line.change())});
            }
            rows.add(new Object[]{""});
            List<TableColumn<ProfitLossPeriodRow, ?>> columns =
                    VisibleColumns.dataColumns(table.getVisibleLeafColumns(), Set.of(ACTIONS));
            rows.add(columns.stream().map(TableColumn::getText).toArray());
            for (ProfitLossPeriodRow row : shown.rows()) {
                Object[] cells = new Object[columns.size()];
                for (int index = 0; index < columns.size(); index++) {
                    Object value = VisibleColumns.value(columns.get(index), row);
                    cells[index] = value == null ? "" : value;
                }
                rows.add(cells);
            }
            Object[] headers = {text("profitloss.column.line"), text("profitloss.column.current"),
                    text("profitloss.column.previous"), text("profitloss.column.change")};
            int written = ExportData.exportDataToExcel(rows,
                    new ProfitLossExcelWriter(text("report.profit.loss.title"), headers, rows));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("profitloss.error.export"), e);
        }
    }

    // ---- words -----------------------------------------------------------------------

    private static String caption(StatementLine line) {
        return line.name() != null ? line.name() : text(line.messageKey());
    }

    private static String periodSentence(ProfitLossReport report) {
        return LanguageManager.getInstance().getString("profitloss.period.sentence",
                dayName(report.period().from()), dayName(report.period().to()));
    }

    private static String comparedSentence(ProfitLossReport report) {
        return LanguageManager.getInstance().getString("profitloss.compared.sentence",
                text(report.basis().messageKey()), dayName(report.previousPeriod().from()),
                dayName(report.previousPeriod().to()));
    }

    /**
     * A day as "23 سبتمبر 2026": a {@code yyyy-MM-dd} after an Arabic word is drawn back to front, since
     * hyphens do not join numbers that follow Arabic letters. Latin digits either way.
     */
    private static String dayName(LocalDate day) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", LanguageManager.getInstance().getCurrentLocale())
                .format(day);
    }

    /** A day by its weekday, a week by its two ends, a month by its name - each cut at the period's edges. */
    private String rowLabel(ProfitLossPeriodRow row) {
        var locale = LanguageManager.getInstance().getCurrentLocale();
        if (row.isOneDay()) {
            return DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(row.start());
        }
        boolean wholeMonth = row.start().getDayOfMonth() == 1
                && row.end().equals(row.start().withDayOfMonth(row.start().lengthOfMonth()));
        if (wholeMonth) {
            return DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(row.start());
        }
        DateTimeFormatter day = DateTimeFormatter.ofPattern("d MMMM", locale);
        return day.format(row.start()) + " – " + day.format(row.end());
    }

    private static String percent(Optional<BigDecimal> value) {
        return value.map(amount -> amount.toPlainString() + "%").orElse(NOTHING);
    }

    /**
     * A change with its sign, "+6.30%" or "-4.20%". Signs, not arrows: the paper's font has no glyph for an
     * arrow. Safe because a change is never written inside an Arabic sentence.
     */
    private static String change(Optional<BigDecimal> value) {
        return value.map(amount -> (amount.signum() > 0 ? "+" : "") + amount.toPlainString() + "%")
                .orElse(NOTHING);
    }

    private static void money(Label label, BigDecimal value) {
        label.setText(Columns.money(value));
        label.pseudoClassStateChanged(Columns.NEGATIVE, value.signum() < 0);
    }

    private static void previous(FigureLine line, boolean compared, BigDecimal previous, Optional<BigDecimal> change) {
        if (!compared) {
            line.hide();
            return;
        }
        line.show("profitloss.stat.previous", Columns.money(previous), previous.signum() < 0,
                change.map(value -> change(Optional.of(value))).orElse(null));
    }

    private static <T> StringConverter<T> keyed(Function<T, String> key) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : text(key.apply(value));
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    /**
     * The choices of the period combo: the statement's presets, less "everything there has ever been", and
     * "custom" for dates typed in. A record rather than the preset itself, because "custom" is not one.
     */
    private record PeriodChoice(StatementPeriod preset) {
        static final PeriodChoice CUSTOM = new PeriodChoice(null);
        static final StringConverter<PeriodChoice> CONVERTER = new StringConverter<>() {
            @Override
            public String toString(PeriodChoice choice) {
                if (choice == null) {
                    return "";
                }
                return text(choice.preset() == null ? "profitloss.period.custom" : choice.preset().messageKey());
            }

            @Override
            public PeriodChoice fromString(String string) {
                return null;
            }
        };

        static PeriodChoice of(StatementPeriod preset) {
            return new PeriodChoice(preset);
        }

        static List<PeriodChoice> all() {
            List<PeriodChoice> choices = new ArrayList<>();
            for (StatementPeriod preset : StatementPeriod.values()) {
                if (!preset.needsEarliestMovement()) {
                    choices.add(new PeriodChoice(preset));
                }
            }
            choices.add(CUSTOM);
            return choices;
        }
    }

    /**
     * A caption and its figures on one line, each figure a label of its own read left to right. Written
     * into one Arabic sentence, a loss drew its minus on the far side of the number.
     */
    private static final class FigureLine {
        private final Label caption = subtitleLabel();
        private final Label value = subtitleLabel();
        private final Label change = subtitleLabel();
        private final HBox box = new HBox(6, caption, value, change);

        FigureLine() {
            value.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            change.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            box.setAlignment(Pos.CENTER_LEFT);
            hide();
        }

        void show(String captionKey, String valueText, boolean negative, String changeText) {
            caption.setText(text(captionKey));
            value.setText(valueText);
            value.pseudoClassStateChanged(Columns.NEGATIVE, negative);
            change.setText(changeText == null ? "" : changeText);
            change.setVisible(changeText != null);
            change.setManaged(changeText != null);
            box.setVisible(true);
            box.setManaged(true);
        }

        void hide() {
            box.setVisible(false);
            box.setManaged(false);
        }

        private static Label subtitleLabel() {
            Label label = new Label();
            label.getStyleClass().add("stat-subtitle");
            return label;
        }
    }

    private static void report(String titleKey, Throwable error) {
        AllAlerts.handleError(text(titleKey), error instanceof Exception exception ? exception : new Exception(error));
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private static Label statValue(String id) {
        Label label = new Label(Columns.money(BigDecimal.ZERO));
        label.getStyleClass().add("stat-value");
        label.setId(id);
        // Left to right, so a loss keeps its minus sign on the side a reader looks for it.
        label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
