package com.hamza.account.controller.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.users.ShiftCorrectionReasonPrompt;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.account.features.events.TreasuriesChanged;
import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingService;
import com.hamza.account.features.expense.ExpensePage;
import com.hamza.account.features.expense.ExpenseRow;
import com.hamza.account.features.expense.ExpenseService;
import com.hamza.account.features.expense.ExpenseSummary;
import com.hamza.account.features.expense.ExpenseUserOption;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.PageJumpBox;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.account.table.TableColumnViews;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.prefs.Preferences;

import static com.hamza.controlsfx.others.DateSetting.dateFilter;
import static com.hamza.controlsfx.others.Utils.setOptionalNumberFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The expenses list.
 * <p>
 * <b>What it replaces could answer none of the questions an expenses list is opened for.</b>
 * {@code ExpensesDetailsApplication} read the whole table, searched fifty rows at a time, offered no
 * filter by period, heading, till or person, and totalled the rows on screen under the word "total" -
 * so the one figure it showed was a sum of whatever the last search had happened to return
 * (docs/expenses-plan.md ع-٦). And it opened on {@code treasury.show}, while {@code expenses.show}
 * existed and nothing read it (ع-٥).
 * <p>
 * It follows the employees list class for class: SQL-side filtering and paging through
 * {@link ExpenseService}, figures over the whole filtered set, buttons in the row they act on, the
 * "العرض" menu over columns that all carry ids, content-sized columns, a typed page number, and a PDF
 * and a spreadsheet of the columns on screen. What it adds is the period in the bar - it says
 * <i>which</i> list this is, so the closed filters panel never hides it - and the row's details in a
 * drawer rather than a second table under the first.
 */
@Log4j2
@FxmlPath(pathFile = "expenses.fxml")
public class ExpensesController extends LoadData {

    /** Set on a row paid to an employee. Styled in {@code app-theme.css}. */
    private static final PseudoClass EMPLOYEE = PseudoClass.getPseudoClass("employee-payment");

    private static final String ACTIONS_COLUMN = "expense-actions";
    private static final Set<String> TOTALLED_COLUMNS = Set.of("expense-amount");

    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final ExpenseService expenseService = ServiceRegistry.get(ExpenseService.class);
    private final ExpenseHeadingService headingService = ServiceRegistry.get(ExpenseHeadingService.class);

    private final TableView<ExpenseRow> table = new TableView<>();
    private final ComboBox<StatementPeriod> comboPeriod = new ComboBox<>();
    private final DatePicker dateFrom = new DatePicker();
    private final DatePicker dateTo = new DatePicker();
    private final TextField search = new TextField();
    private final ComboBox<ExpenseHeading> comboHeading = new ComboBox<>();
    private final ComboBox<TreasuryBalanceSummary> comboTreasury = new ComboBox<>();
    private final ComboBox<ExpenseUserOption> comboUser = new ComboBox<>();
    private final TextField amountFrom = new TextField();
    private final TextField amountTo = new TextField();

    private final Label statTotal = statValue("stat-expense-total");
    private final Label statCount = statValue("stat-expense-count");
    private final Label statAverage = statValue("stat-expense-average");
    private final Label statChange = statValue("stat-expense-change");
    private final Label statTop = statValue("stat-expense-top");

    private final PageJumpBox pageJump = new PageJumpBox(this::search);
    private final ContentSizedColumns<ExpenseRow> columnSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();
    private final ListToolbar toolbar = new ListToolbar();

    private final Label countLabel = new Label();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final ProgressIndicator progress = new ProgressIndicator();

    private final GridPane detailGrid = new GridPane();
    private RowDetailDrawer detail;

    @FXML
    private StackPane stackPane;
    @FXML
    private AnchorPane root;
    @FXML
    private VBox box;

    private ExpenseFilter filter = ExpenseFilter.between(null, null);
    private int generation;
    private boolean loading;

    public ExpensesController(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    @FXML
    public void initialize() {
        stackPane.getStyleClass().add("screen-expenses");

        buildTable();
        columnViews().install(viewMenu, table);
        box.getChildren().setAll(header(), statCards(), filterBar(), tableArea(), footer());
        buildDetail();

        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(ExpensesChanged.class, event -> reload()));
            subscriptions.disposeWith(stackPane);
        }
        Platform.runLater(this::loadOptionsAndRows);
    }

    // ---- the screen ----------------------------------------------------------------------

    private HBox header() {
        HBox iconBox = new HBox(AppIcon.TREASURY_CASH.graphic(32));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-screen-icon-box");

        Label title = new Label(text("expenses"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("expense.screen.subtitle"));
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

    /** Five figures over the whole filtered set - never over the page, which answered nothing. */
    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("expense-stats");
        cards.getChildren().addAll(
                card("expense.stat.total", statTotal),
                card("expense.stat.count", statCount),
                card("expense.stat.average", statAverage),
                card("expense.stat.change", statChange),
                card("expense.stat.top", statTop));
        return cards;
    }

    private VBox card(String titleKey, Label value) {
        Label caption = new Label(text(titleKey));
        caption.getStyleClass().add("stat-title");
        VBox card = new VBox(4, caption, value);
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        card.setMinWidth(150);
        return card;
    }

    private VBox filterBar() {
        comboPeriod.getItems().setAll(StatementPeriod.values());
        comboPeriod.setConverter(converter(period -> period == null ? "" : text(period.messageKey())));
        comboPeriod.setId("expense-period");
        comboPeriod.setOnAction(event -> applyPeriod(comboPeriod.getValue()));

        // A bound left alone means "any day", not "today": dateAction seeds today, which would open this
        // list on whatever was entered this morning.
        dateFilter(dateFrom);
        dateFilter(dateTo);
        dateFrom.setPromptText(text("expense.filter.from"));
        dateTo.setPromptText(text("expense.filter.to"));
        dateFrom.setOnAction(event -> search(0));
        dateTo.setOnAction(event -> search(0));

        search.setPromptText(text("expense.filter.text"));
        search.setId("expense-search");
        search.setPrefWidth(240);
        HBox.setHgrow(search, Priority.ALWAYS);

        comboHeading.setConverter(converter(heading -> heading == null || heading.id() == 0
                ? text("party.statement.filter.all") : heading.path()));
        comboTreasury.setConverter(converter(treasury -> treasury == null || treasury.id() == 0
                ? text("party.statement.filter.all") : treasury.name()));
        comboUser.setConverter(converter(user -> user == null || user.id() == 0
                ? text("party.statement.filter.all") : user.name()));
        comboHeading.setOnAction(event -> search(0));
        comboTreasury.setOnAction(event -> search(0));
        comboUser.setOnAction(event -> search(0));

        // setOptionalNumberFormatter, not setTextFormatter: the latter seeds "0.0", which would open this
        // screen filtering on amount >= 0 AND amount <= 0 with nobody having typed anything.
        setOptionalNumberFormatter(amountFrom, amountTo);
        amountFrom.setPromptText(text("expense.filter.amount.from"));
        amountTo.setPromptText(text("expense.filter.amount.to"));

        Button apply = new Button(text("search"), AppIcon.SEARCH.graphic());
        apply.getStyleClass().addAll("app-primary-button", "party-primary-button");
        apply.setMinWidth(Region.USE_PREF_SIZE);
        apply.setOnAction(event -> search(0));

        // The Enter order, declared once, in the order the bar is filled - rule ق-ل9.
        whenEnterPressed(amountFrom, amountTo, search, apply);

        HBox first = new HBox(8, caption("expense.filter.heading"), comboHeading,
                caption("expense.filter.treasury"), comboTreasury,
                caption("expense.filter.user"), comboUser);
        first.setAlignment(Pos.CENTER_LEFT);
        HBox second = new HBox(8, caption("expense.filter.amount"), amountFrom, amountTo);
        second.setAlignment(Pos.CENTER_LEFT);
        VBox panel = new VBox(8, first, second);

        toolbar.searchField(caption("expense.filter.period"), comboPeriod, dateFrom, dateTo, search)
                .search(apply)
                .filters(ListToolbar.filtersToggle(), panel)
                .clear(ListToolbar.clearButton(this::reset));
        listActions();
        FlowPane row = toolbar.installIn(new FlowPane(8, 8));
        row.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(8, row, panel);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        bar.setId("expense-filters");
        return bar;
    }

    /** What acts on the list, after the filters that decide what the list is. */
    private void listActions() {
        List<Node> extras = new ArrayList<>();
        if (AuthorizationGuard.isGranted(AppPermissions.EXPENSES_CREATE)) {
            Button add = button("expense.action.add", AppIcon.ADD, this::openNew);
            add.getStyleClass().setAll("button", "app-primary-button", "party-primary-button");
            extras.add(add);
            extras.add(button("expense.action.batch", AppIcon.SELECT_ALL, this::openBatch));
        }
        if (AuthorizationGuard.isGranted(AppPermissions.EXPENSES_HEADINGS_UPDATE)) {
            extras.add(button("expense.action.headings", AppIcon.TREE, this::openHeadings));
        }
        toolbar.refresh(ListToolbar.refreshButton(this::reload));
        if (AuthorizationGuard.isGranted(AppPermissions.EXPENSES_EXPORT)) {
            toolbar.print(ListToolbar.printButton(this::print))
                    .export(button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel));
        }
        toolbar.view(viewMenu).extra(extras.toArray(new Node[0]));
    }

    private TableColumnViews<ExpenseRow> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(ExpensesController.class).node("expenses");
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.COMPACT,
                Set.of("expense-code", "expense-date", "expense-heading", "expense-amount", "expense-treasury",
                        "expense-payee", "expense-notes"),
                Set.of(ACTIONS_COLUMN));
    }

    private StackPane tableArea() {
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane area = new StackPane(table, progress);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    private HBox footer() {
        previous.setText(text("masterdata.previous"));
        next.setText(text("masterdata.next"));
        previous.getStyleClass().add("app-neutral-button");
        next.getStyleClass().add("app-neutral-button");
        previous.setOnAction(event -> search(filter.page() - 1));
        next.setOnAction(event -> search(filter.page() + 1));

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, countLabel, spacer, previous, pageJump, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("summary-card", "party-summary-bar");
        return bar;
    }

    private void buildTable() {
        table.setId("expenses-table");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPlaceholder(new Label(text("expense.empty")));
        table.getColumns().setAll(List.of(
                named(ACTIONS_COLUMN, actionsColumn()),
                named("expense-code", Columns.number("code", ExpenseRow::id)),
                named("expense-date", Columns.date("date", ExpenseRow::date)),
                named("expense-heading", Columns.text("expense.column.heading", ExpenseRow::headingPath)),
                named("expense-amount", Columns.money("column.amount", ExpenseRow::amount)),
                named("expense-treasury", Columns.text("invoice.treasury", ExpenseRow::treasuryName)),
                named("expense-payee", Columns.text("expense.column.payee", ExpenseRow::payee)),
                named("expense-reference", Columns.text("expense.column.reference", ExpenseRow::referenceNo)),
                named("expense-employee", Columns.text("expense.column.employee", ExpenseRow::employeeName)),
                named("expense-notes", Columns.text("column.notes", ExpenseRow::notes)),
                named("expense-user", Columns.text("expense.column.user", ExpenseRow::userName)),
                named("expense-entered", Columns.dateTime("expense.column.entered", ExpenseRow::enteredAt))));

        table.setRowFactory(view -> new TableRow<>() {
            {
                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !isEmpty() && getItem() != null) {
                        showDetails(getItem());
                    }
                });
            }

            @Override
            protected void updateItem(ExpenseRow row, boolean empty) {
                super.updateItem(row, empty);
                pseudoClassStateChanged(EMPLOYEE, !empty && row != null && row.paidToEmployee());
            }
        });
        columnSizing.install(table);
        TableSetting.tableMenuSetting(getClass(), table);
        table.setTableMenuButtonVisible(false);
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    /**
     * What is done to one expense, in its own row. The permissions are a hint in the {@code isGranted}
     * sense - the action is left out rather than shown and refused - and the service still requires.
     */
    private TableColumn<ExpenseRow, Void> actionsColumn() {
        List<RowAction<ExpenseRow>> actions = List.of(
                RowAction.of("expense.action.details", AppIcon.SHOW, "app-neutral-button",
                        AppPermissions.EXPENSES_SHOW, this::showDetails),
                RowAction.of("expense.action.edit", AppIcon.EDIT, "app-primary-button",
                        AppPermissions.EXPENSES_UPDATE, this::openEdit),
                RowAction.of("expense.action.delete", AppIcon.DELETE, "app-neutral-button",
                        AppPermissions.EXPENSES_DELETE, this::delete));
        return RowActionsColumn.of("employee.column.actions", RowAction.permitted(actions));
    }

    /** The whole expense, over the list - who entered it and when, and the shift the cash left under. */
    private void buildDetail() {
        detailGrid.setHgap(12);
        detailGrid.setVgap(8);
        ColumnConstraints captions = new ColumnConstraints();
        captions.setMinWidth(120);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        detailGrid.getColumnConstraints().setAll(captions, values);
        detail = RowDetailDrawer.installIn(root);
        detail.setContent(new VBox(10, detailGrid));
        detail.setPreferredWidth(460);
    }

    private void showDetails(ExpenseRow row) {
        detailGrid.getChildren().clear();
        int line = 0;
        line = detailLine(line, "code", String.valueOf(row.id()));
        line = detailLine(line, "date", row.date() == null ? "" : row.date().toString());
        line = detailLine(line, "expense.column.heading", row.headingPath());
        line = detailLine(line, "column.amount", Columns.money(row.amount()));
        line = detailLine(line, "invoice.treasury", row.treasuryName());
        line = detailLine(line, "expense.column.payee", row.payee());
        line = detailLine(line, "expense.column.reference", row.referenceNo());
        line = detailLine(line, "expense.column.employee", row.employeeName());
        line = detailLine(line, "column.notes", row.notes());
        line = detailLine(line, "expense.column.user", row.userName());
        line = detailLine(line, "expense.column.entered",
                row.enteredAt() == null ? "" : Columns.DATE_TIME.format(row.enteredAt()));
        detailLine(line, "expense.column.shift", row.shiftId() == null ? "" : String.valueOf(row.shiftId()));
        detail.show(row.headingPath(), Columns.money(row.amount()) + "  -  " + row.date());
    }

    private int detailLine(int line, String captionKey, String value) {
        Label caption = caption(captionKey);
        Label content = new Label(value == null || value.isBlank() ? "—" : value);
        content.setWrapText(true);
        // A bare Label inherits the drawer's muted text and was all but invisible when the screen was
        // first opened - the captions beside it carried a class and the values did not.
        content.getStyleClass().add("detail-drawer-value");
        detailGrid.add(caption, 0, line);
        detailGrid.add(content, 1, line);
        return line + 1;
    }

    // ---- loading -------------------------------------------------------------------------

    /**
     * The pickers, then the rows. The list opens on the current month - the question an expenses list is
     * opened with - and the period stays in the bar where it can be seen and changed.
     */
    private void loadOptionsAndRows() {
        loading = true;
        try {
            List<ExpenseHeading> headings = new ArrayList<>();
            headings.add(allHeadings());
            headings.addAll(headingService.all());
            comboHeading.setItems(FXCollections.observableArrayList(headings));
            comboHeading.getSelectionModel().selectFirst();

            List<TreasuryBalanceSummary> tills = new ArrayList<>();
            tills.add(new TreasuryBalanceSummary(0, "", null, true, 0, null, null, null, null, null));
            tills.addAll(expenseService.treasuries());
            comboTreasury.setItems(FXCollections.observableArrayList(tills));
            comboTreasury.getSelectionModel().selectFirst();

            List<ExpenseUserOption> users = new ArrayList<>();
            users.add(new ExpenseUserOption(0, ""));
            users.addAll(expenseService.users());
            comboUser.setItems(FXCollections.observableArrayList(users));
            comboUser.getSelectionModel().selectFirst();

            comboPeriod.getSelectionModel().select(StatementPeriod.THIS_MONTH);
            setPeriod(StatementPeriod.THIS_MONTH);
        } catch (Exception e) {
            report(e);
        } finally {
            loading = false;
        }
        search(0);
    }

    private static ExpenseHeading allHeadings() {
        return new ExpenseHeading(0, "", null, null, true, null, false);
    }

    private void applyPeriod(StatementPeriod period) {
        if (loading || period == null) {
            return;
        }
        loading = true;
        try {
            setPeriod(period);
        } finally {
            loading = false;
        }
        search(0);
    }

    /** "Everything" is no bounds at all - an expenses list has no first movement to start from. */
    private void setPeriod(StatementPeriod period) {
        LocalDate today = LocalDate.now();
        if (period.needsEarliestMovement()) {
            dateFrom.setValue(null);
            dateTo.setValue(null);
        } else {
            dateFrom.setValue(period.from(today));
            dateTo.setValue(period.to(today));
        }
    }

    private void reload() {
        search(filter.page());
    }

    private void reset() {
        loading = true;
        try {
            comboHeading.getSelectionModel().selectFirst();
            comboTreasury.getSelectionModel().selectFirst();
            comboUser.getSelectionModel().selectFirst();
            amountFrom.clear();
            amountTo.clear();
            search.clear();
            comboPeriod.getSelectionModel().select(StatementPeriod.THIS_MONTH);
            setPeriod(StatementPeriod.THIS_MONTH);
        } finally {
            loading = false;
        }
        search(0);
    }

    /** Reads the controls into a filter and fetches that page, off the JavaFX thread. */
    private void search(int page) {
        if (loading) {
            return;
        }
        try {
            filter = readFilter(Math.max(page, 0));
        } catch (IllegalArgumentException contradiction) {
            // A "from" after a "to" is the filter record refusing to describe an impossible set.
            report(new UserValidationException(text("expense.error.filter.range")));
            return;
        }
        ExpenseFilter requested = filter;
        int token = ++generation;
        progress.setVisible(true);
        Task<ExpensePage> task = new Task<>() {
            @Override
            protected ExpensePage call() throws Exception {
                return expenseService.search(requested);
            }
        };
        task.setOnSucceeded(event -> {
            if (token == generation) {
                progress.setVisible(false);
                show(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (token == generation) {
                progress.setVisible(false);
                report(task.getException());
            }
        });
        Thread thread = new Thread(task, "expenses-load");
        thread.setDaemon(true);
        thread.start();
    }

    private ExpenseFilter readFilter(int page) {
        ExpenseHeading heading = comboHeading.getValue();
        TreasuryBalanceSummary treasury = comboTreasury.getValue();
        ExpenseUserOption user = comboUser.getValue();
        return new ExpenseFilter(dateFrom.getValue(), dateTo.getValue(),
                heading == null ? null : heading.id(),
                treasury == null ? null : treasury.id(),
                user == null ? null : user.id(),
                amount(amountFrom), amount(amountTo),
                search.getText(), page, ExpenseFilter.DEFAULT_PAGE_SIZE);
    }

    private void show(ExpensePage page) {
        ExpenseSummary summary = page.summary();
        toolbar.showActiveFilters(filter.panelConditionCount());
        table.setItems(FXCollections.observableArrayList(page.rows()));
        columnSizing.layout(table);

        statTotal.setText(Columns.money(summary.total()));
        statCount.setText(String.valueOf(summary.count()));
        statAverage.setText(Columns.money(summary.average()));
        BigDecimal change = summary.changePercent();
        statChange.setText(change == null ? "—" : (change.signum() > 0 ? "+" : "") + change.toPlainString() + "%");
        statTop.setText(summary.topHeading() == null ? "—"
                : summary.topHeading() + "  " + Columns.money(summary.topHeadingTotal()));

        pageJump.showing(page.page(), pageCount(summary.count(), filter.pageSize()));
        countLabel.setText(LanguageManager.getInstance()
                .getString("expense.count", page.rows().size(), summary.count()));
        previous.setDisable(!page.hasPrevious());
        next.setDisable(!page.hasNext());
    }

    /** Rounded up, and never less than one: an empty list is still page one of one. */
    static int pageCount(int rows, int pageSize) {
        if (pageSize < 1) {
            return 1;
        }
        return Math.max(1, (rows + pageSize - 1) / pageSize);
    }

    // ---- actions -------------------------------------------------------------------------

    private void openNew() {
        open(0);
    }

    private void openEdit(ExpenseRow row) {
        open(row.id());
    }

    private void open(int expenseId) {
        try {
            new AddForAllApplication(expenseId, new ExpenseEntryController(expenseId));
        } catch (Exception e) {
            report(e);
        }
    }

    private void openBatch() {
        try {
            new AddForAllApplication(0, new ExpenseBatchController());
        } catch (Exception e) {
            report(e);
        }
    }

    private void openHeadings() {
        try {
            new AddForAllApplication(0, new ExpenseHeadingsController());
            // A heading renamed or stopped there is a filter option here.
            loadOptionsAndRows();
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * Removes an expense entered by mistake, with the reason the shift journal records. Refused inside a
     * closed period, by the service.
     */
    private void delete(ExpenseRow row) {
        try {
            if (!AllAlerts.confirmDelete()) {
                return;
            }
            var reason = ShiftCorrectionReasonPrompt.forDelete();
            if (reason.isEmpty()) {
                return;
            }
            expenseService.delete(row.id(), reason.get());
            detail.hide();
            if (eventBus != null) {
                eventBus.publish(new ExpensesChanged());
                eventBus.publish(new TreasuriesChanged());
            } else {
                reload();
            }
        } catch (Exception e) {
            report(e);
        }
    }

    /** Prints every expense the filter matched, as a PDF of the columns on screen. */
    private void print() {
        String title = text("expenses");
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        ExpenseFilter printed = filter;
        Task<ExpensePage> load = new Task<>() {
            @Override
            protected ExpensePage call() throws Exception {
                return expenseService.forPrint(printed);
            }
        };
        load.setOnSucceeded(event -> {
            ExpensePage extract = load.getValue();
            if (extract.rows().isEmpty()) {
                AllAlerts.alertError(text("party.error.no.data.print"));
                return;
            }
            TablePdfLayout layout = TablePdfLayout.from(table, extract.rows(),
                    Set.of(ACTIONS_COLUMN), TOTALLED_COLUMNS, text("total"));
            TablePdfReport.write(target, title, printSubtitle(printed), layout, () -> warnIfTruncated(extract));
        });
        AllAlerts.handleTaskFailure(text("party.error.export.generic"), load);
        TablePdfReport.start(load, "expenses-pdf-load");
    }

    private static String printSubtitle(ExpenseFilter printed) {
        String from = printed.from() == null ? "…" : printed.from().toString();
        String to = printed.to() == null ? "…" : printed.to().toString();
        String subtitle = text("expense.print.period") + ": " + from + "  -  " + to;
        if (printed.hasText()) {
            subtitle += "  -  " + text("search") + ": " + printed.text();
        }
        return subtitle;
    }

    private void warnIfTruncated(ExpensePage extract) {
        if (extract.truncated()) {
            report(new UserValidationException(LanguageManager.getInstance()
                    .getString("expense.truncated", ExpenseService.PRINT_LIMIT)));
        }
    }

    private void exportExcel() {
        try {
            ExpensePage extract = expenseService.forPrint(filter);
            if (extract.rows().isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(extract.rows(),
                    VisibleColumnsExcelWriter.of(text("expenses"), table, Set.of(ACTIONS_COLUMN), extract.rows()));
            if (written < 1) {
                return;
            }
            AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            warnIfTruncated(extract);
        } catch (Exception e) {
            report(e);
        }
    }

    // ---- plumbing ------------------------------------------------------------------------

    private Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static Label statValue(String id) {
        Label label = new Label("0");
        label.getStyleClass().add("stat-value");
        label.setId(id);
        return label;
    }

    private static BigDecimal amount(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank()
                ? null : BigDecimal.valueOf(com.hamza.controlsfx.others.DoubleSetting.parseDoubleOrDefault(value));
    }

    private static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private void report(Throwable e) {
        AllAlerts.handleError(text("expense.error.operation"),
                e instanceof Exception exception ? exception : new RuntimeException(e));
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
