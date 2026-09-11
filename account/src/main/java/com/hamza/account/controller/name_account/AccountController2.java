package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.model.TreeAccountModelForPrint;
import com.hamza.account.controller.name_account.impl.AccountTotalsPurchase;
import com.hamza.account.controller.name_account.impl.AccountTotalsSales;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.balances.BalanceState;
import com.hamza.account.features.party.balances.PartyAreaOption;
import com.hamza.account.features.party.balances.PartyBalanceFilter;
import com.hamza.account.features.party.balances.PartyBalancePage;
import com.hamza.account.features.party.balances.PartyBalanceRow;
import com.hamza.account.features.party.balances.PartyBalanceService;
import com.hamza.account.features.party.balances.PartyBalanceSummary;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.TableSetting;
import javafx.scene.control.TableColumn;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.PageJumpBox;
import com.hamza.account.view.AddAccountApplication;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.BusinessRuleException;
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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.hamza.controlsfx.others.DateSetting.dateAction;
import static com.hamza.controlsfx.others.Utils.setOptionalNumberFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * What every customer or supplier owes.
 * <p>
 * <b>It used to read the whole table on every refresh.</b> {@code accountTotalList(null, null)}
 * returned every row of {@code account_customer_totals}, and the screen filtered it with a
 * {@code FilteredList}, searched it with a Java predicate, summed it by streaming the loaded rows and
 * offered one checkbox - "show the zeros" - as its only filter. On a shop with five thousand
 * customers that is five thousand rows crossing the wire to show fifty. Its period filter did not
 * exist: {@code PartyLedgerSpec.totalsBetweenDatesSql()}, written and documented for exactly this,
 * had no caller anywhere in the application.
 * <p>
 * Everything now goes through {@link PartyBalanceService}: filtered, summed and paged in SQL, with
 * one {@code PartyBalanceFilter} reaching the page, the footer and the export - so a saved file
 * cannot describe a different set of parties from the table. <b>That is also the fix for a defect
 * rather than a tidy-up:</b> the export took the <em>ticked</em> rows while asking the table whether
 * it was empty, so exporting with nothing ticked wrote an empty spreadsheet and reported success.
 * <p>
 * The four figures above the table are filters as well as facts. "Over limit: 12" is a number
 * somebody then has to go and find; clicking it narrows the list to those twelve. The same idea as
 * {@code ModernDashboardApp}'s clickable cards.
 */
@Log4j2
@FxmlPath(pathFile = "account-totals.fxml")
public class AccountController2<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> {

    /** Set on a row whose balance has passed the party's credit limit. Styled in {@code app-theme.css}. */
    private static final PseudoClass OVER_LIMIT = PseudoClass.getPseudoClass("over-limit");

    /** What "idle" means on the card. A quarter with no movement is the usual question. */
    private static final int IDLE_DAYS = 90;

    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final PartyBalanceService balanceService = new PartyBalanceService();

    private final TableView<PartyBalanceRow> table = new TableView<>();
    private final ComboBox<BalanceState> comboState = new ComboBox<>();
    private final ComboBox<PartyAreaOption> comboArea = new ComboBox<>();
    private final DatePicker asOf = new DatePicker(LocalDate.now());
    private final TextField balanceFrom = new TextField();
    private final TextField balanceTo = new TextField();
    private final TextField search = new TextField();
    private final CheckBox overLimitOnly = new CheckBox(text("party.balances.filter.over.limit"));
    private final TextField idleDays = new TextField();

    private final Label statParties = statValue("stat-parties");
    private final Label statOwed = statValue("stat-owed");
    private final Label statCredit = statValue("stat-credit");
    private final Label statOverLimit = statValue("stat-over-limit");
    /**
     * Type a page number, land on it - the invoice totals screen's pager, reused.
     * <p>
     * A hundred and forty-five parties is three pages today and thirty on a database that has
     * been running a few years, and the only way to the last one was to press "next" until it
     * arrived. The clamping, and the Arabic-Indic digits an Arabic keyboard actually produces,
     * are {@code PageJump}'s - one definition for both screens.
     */
    private final PageJumpBox pageJump = new PageJumpBox(this::search);

    private final Label countLabel = new Label();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final ProgressIndicator progress = new ProgressIndicator();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    private PartyBalanceFilter filter;
    private PartyBalanceSummary summary = PartyBalanceSummary.EMPTY;
    private int generation;
    private boolean loading;

    public AccountController2(DaoFactory daoFactory, DataPublisher dataPublisher,
                             DataInterface<?, ?, T3, T4> dataInterface) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.filter = PartyBalanceFilter.allToday(partyKind());
    }

    @FXML
    public void initialize() {
        buildTable();
        box.getChildren().setAll(toolbar(), statCards(), filterBar(), tableArea(), footer());
        VBox.setVgrow(box, Priority.ALWAYS);

        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(AccountChanged.class, event -> {
                if (event.kind() == partyKind()) {
                    reload();
                }
            }));
            subscriptions.add(eventBus.subscribe(NameChanged.class, event -> {
                if (event.kind() == partyKind()) {
                    reload();
                }
            }));
            subscriptions.add(eventBus.subscribe(InvoiceSaved.class, event -> {
                if (event.side() == dataInterface.invoiceSide()) {
                    reload();
                }
            }));
            subscriptions.disposeWith(stackPane);
        }
        Platform.runLater(this::loadAreasAndRows);
    }

    // ---- the screen ------------------------------------------------------------------

    /**
     * What the toolbar keeps: the things that act on the <b>list</b>.
     * <p>
     * Collecting and opening a statement left it, because they act on <b>one party</b> and are
     * now buttons in that party's own row. A toolbar button that acts on "the selected row" is
     * two gestures and carries a message that exists only because the control is in the wrong
     * place - "choose a row first". A refresh, a print and an export have no row, so they stay.
     */
    private HBox toolbar() {
        // The ageing report opens from here rather than from the main menu: it is the same data
        // and the same permission, and "these are the balances" leads straight to "how old are
        // they". A menu entry of its own would need a product feature in the signed catalogue,
        // which is a decision about editions rather than about this report.
        Button ageing = button("party.ageing.open", AppIcon.REPORT, this::openAgeing);
        Button refresh = button("refresh", AppIcon.REFRESH, this::reload);
        Button print = button("print", AppIcon.PRINT, this::print);
        Button excel = button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel);
        excel.getStyleClass().setAll("excel-button");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, ageing, spacer, refresh, print, excel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-card");
        return bar;
    }

    /**
     * The four figures, each of which narrows the list when clicked.
     * <p>
     * A count of parties over their limit is a fact somebody then has to act on, and the action is
     * always "show me which ones". Making the number the button removes a step that was always taken.
     */
    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("balance-stats");
        cards.getChildren().addAll(
                card("party.balances.stat.parties", statParties, () -> narrow(BalanceState.ALL, false, null)),
                card("party.balances.stat.owed", statOwed, () -> narrow(BalanceState.DEBTOR, false, null)),
                card("party.balances.stat.credit", statCredit, () -> narrow(BalanceState.CREDITOR, false, null)),
                card("party.balances.stat.over.limit", statOverLimit, () -> narrow(BalanceState.ALL, true, null)),
                card("party.balances.stat.idle", new Label(""), () -> narrow(BalanceState.ALL, false, IDLE_DAYS)));
        return cards;
    }

    private VBox card(String titleKey, Label value, Runnable onClick) {
        Label caption = new Label(text(titleKey));
        caption.getStyleClass().add("stat-title");
        VBox card = new VBox(4, caption, value);
        card.getStyleClass().add("dashboard-tile");
        card.setMinWidth(150);
        card.setOnMouseClicked(event -> onClick.run());
        card.setStyle("-fx-cursor: hand;");
        return card;
    }

    private void narrow(BalanceState state, boolean overLimit, Integer idle) {
        loading = true;
        try {
            comboState.getSelectionModel().select(state);
            overLimitOnly.setSelected(overLimit);
            idleDays.setText(idle == null ? "" : String.valueOf(idle));
        } finally {
            loading = false;
        }
        search(0);
    }

    private VBox filterBar() {
        comboState.getItems().setAll(BalanceState.values());
        comboState.setConverter(converter(state -> text(state.messageKey())));
        comboState.getSelectionModel().select(BalanceState.ALL);
        comboState.setId("balance-state");

        comboArea.setConverter(converter(area -> area == null || area.id() == 0
                ? text("party.statement.filter.all") : area.name()));
        comboArea.setId("balance-area");

        dateAction(asOf);
        // Not setTextFormatter: that one seeds "0.0", which made this screen open on
        // balance >= 0 AND balance <= 0 - the parties whose account came to nothing.
        setOptionalNumberFormatter(balanceFrom, balanceTo, idleDays);
        balanceFrom.setPromptText(text("party.balances.filter.balance.from"));
        balanceTo.setPromptText(text("party.balances.filter.balance.to"));
        idleDays.setPromptText(text("party.balances.filter.idle"));
        search.setPromptText(text("party.balances.filter.text"));
        search.setId("balance-search");
        HBox.setHgrow(search, Priority.ALWAYS);

        Button apply = new Button(text("search"), AppIcon.SEARCH.graphic());
        apply.getStyleClass().add("app-primary-button");
        apply.setId("balance-apply");
        apply.setOnAction(event -> search(0));

        Button clear = new Button(text("party.statement.filter.reset"), AppIcon.CLEAR.graphic());
        clear.getStyleClass().add("app-neutral-button");
        clear.setOnAction(event -> reset());

        comboState.setOnAction(event -> search(0));
        comboArea.setOnAction(event -> search(0));
        asOf.setOnAction(event -> search(0));
        overLimitOnly.setOnAction(event -> search(0));

        // The Enter order, declared once, in the order the bar is filled - rule ق-ل9.
        whenEnterPressed(balanceFrom, balanceTo, idleDays, search, apply);

        HBox first = new HBox(8, new Label(text("party.balances.filter.state")), comboState,
                new Label(text("party.balances.filter.area")), comboArea,
                new Label(text("party.balances.filter.as.of")), asOf, overLimitOnly);
        first.setAlignment(Pos.CENTER_LEFT);
        HBox second = new HBox(8, balanceFrom, balanceTo, idleDays, search, apply, clear);
        second.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(8, first, second);
        bar.getStyleClass().add("app-card");
        bar.setId("balance-filters");
        return bar;
    }

    private StackPane tableArea() {
        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        StackPane area = new StackPane(table, progress);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    private HBox footer() {
        previous.setGraphic(AppIcon.SHOW.graphic());
        previous.setText(text("masterdata.previous"));
        next.setText(text("masterdata.next"));
        previous.getStyleClass().add("app-neutral-button");
        next.getStyleClass().add("app-neutral-button");
        previous.setOnAction(event -> search(filter.page() - 1));
        next.setOnAction(event -> search(filter.page() + 1));

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // No separate page label: the jump box says "page 1 of 3" and lets that 1 be typed, so a
        // label beside it saying "page 1" is the same fact twice.
        HBox bar = new HBox(12, countLabel, spacer, previous, pageJump, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("summary-card");
        return bar;
    }

    /**
     * The columns, built in code and with the money ones going through {@code Columns.money}.
     * <p>
     * The old table's amounts were {@code Columns.number} over a {@code double}, so a balance that
     * had been through arithmetic printed as {@code 1234.5600000000002}.
     */
    private void buildTable() {
        table.setId("balance-table");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(text("party.balances.empty")));
        table.getColumns().setAll(List.of(
                Columns.number("code", PartyBalanceRow::partyId),
                Columns.text("name", PartyBalanceRow::name),
                Columns.text("column.tel", PartyBalanceRow::phone),
                Columns.text("party.column.area", PartyBalanceRow::areaName),
                Columns.money("party.balances.column.period.debit", PartyBalanceRow::periodDebit),
                Columns.money("party.balances.column.period.credit", PartyBalanceRow::periodCredit),
                Columns.money("party.balances.column.balance", PartyBalanceRow::balance),
                Columns.money("party.balances.column.limit", PartyBalanceRow::creditLimit),
                Columns.date("party.balances.column.last", PartyBalanceRow::lastMovement),
                actionsColumn()));

        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(PartyBalanceRow row, boolean empty) {
                super.updateItem(row, empty);
                pseudoClassStateChanged(OVER_LIMIT, !empty && row != null && row.isOverCreditLimit());
            }
        });
        table.setOnMouseClicked(event -> {
            PartyBalanceRow selected = table.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                openStatement(selected);
            }
        });
        TableSetting.tableMenuSetting(getClass(), table);
    }

    // ---- loading ---------------------------------------------------------------------

    private void loadAreasAndRows() {
        try {
            List<PartyAreaOption> areas = new ArrayList<>();
            areas.add(new PartyAreaOption(0, ""));
            areas.addAll(balanceService.areas(partyKind()));
            comboArea.setItems(FXCollections.observableArrayList(areas));
            comboArea.getSelectionModel().selectFirst();
        } catch (Exception e) {
            report(e);
        }
        search(0);
    }

    private void reload() {
        search(filter.page());
    }

    private void reset() {
        loading = true;
        try {
            comboState.getSelectionModel().select(BalanceState.ALL);
            comboArea.getSelectionModel().selectFirst();
            asOf.setValue(LocalDate.now());
            balanceFrom.clear();
            balanceTo.clear();
            idleDays.clear();
            search.clear();
            overLimitOnly.setSelected(false);
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
        filter = readFilter(Math.max(page, 0));
        int token = ++generation;
        progress.setVisible(true);
        Task<PartyBalancePage> task = new Task<>() {
            @Override
            protected PartyBalancePage call() throws Exception {
                return balanceService.search(filter);
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
        Thread thread = new Thread(task, "party-balances-load");
        thread.setDaemon(true);
        thread.start();
    }

    private PartyBalanceFilter readFilter(int page) {
        return new PartyBalanceFilter(partyKind(),
                asOf.getValue() == null ? LocalDate.now() : asOf.getValue(),
                null,
                comboState.getValue() == null ? BalanceState.ALL : comboState.getValue(),
                amount(balanceFrom), amount(balanceTo),
                comboArea.getValue() == null || comboArea.getValue().id() == 0
                        ? null : comboArea.getValue().id(),
                null,
                overLimitOnly.isSelected(),
                days(idleDays),
                search.getText(),
                page, PartyBalanceFilter.DEFAULT_PAGE_SIZE);
    }

    private void show(PartyBalancePage page) {
        summary = page.summary();
        table.setItems(FXCollections.observableArrayList(page.rows()));

        statParties.setText(String.valueOf(summary.parties()));
        statOwed.setText(Columns.money(summary.totalOwed()));
        statCredit.setText(Columns.money(summary.totalInCredit()));
        statOverLimit.setText(String.valueOf(summary.overLimit()));

        // The page count is arithmetic on figures already fetched - the summary counts every
        // party the filter matched - so the pager knows how many there are without a query of
        // its own, and without the list having to be walked to find out.
        pageJump.showing(page.page(), pageCount(summary.parties(), filter.pageSize()));
        countLabel.setText(LanguageManager.getInstance()
                .getString("party.balances.count", page.rows().size(), summary.parties()));
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

    // ---- actions ---------------------------------------------------------------------

    /**
     * The two things you do to one party, in that party's own row.
     * <p>
     * Both take the row they were pressed in, so neither can be reached without one - which is
     * why neither still needs the "choose a row first" refusal the toolbar versions carried.
     * The permissions are asked with {@code isGranted} and are a <b>hint</b>: a user who may not
     * collect is not offered the button, and {@code AccountCustomerService} still calls
     * {@code require} because the same save is reachable from elsewhere.
     */
    private TableColumn<PartyBalanceRow, Void> actionsColumn() {
        List<RowAction<PartyBalanceRow>> actions = List.of(
                RowAction.of("party.action.collect", AppIcon.TREASURY_CASH, "app-primary-button",
                        dataInterface.permAccountAndNameInt().createAccounts(), this::openCollection),
                // The statement shows what the row already summarises, so it asks the same
                // permission the screen itself needed to open.
                RowAction.of("party.action.open.statement", AppIcon.REPORT, "app-neutral-button",
                        dataInterface.permAccountAndNameInt().showAccounts(), this::openStatement));
        return RowActionsColumn.of("party.balances.column.actions", RowAction.permitted(actions));
    }

    /** Opens the statement of one party, which is what a double click does too. */
    private void openStatement(PartyBalanceRow party) {
        try {
            AccountDetailsInterface lines = dataInterface.designInterface().showDataForCustomer()
                    ? new AccountTotalsSales() : new AccountTotalsPurchase();
            new OpenApplication<>(new AccountDetailsWithItemsController<>(
                    daoFactory, dataPublisher, dataInterface,
                    party.partyId(), party.name(), lines));
        } catch (Exception e) {
            report(e);
        }
    }

    private void openCollection(PartyBalanceRow party) {
        try {
            new AddAccountApplication(daoFactory, dataPublisher, dataInterface,
                    party.partyId(), 0, party.name());
        } catch (Exception e) {
            report(e);
        }
    }

    /** The ageing report, over the same parties this screen is showing. */
    private void openAgeing() {
        try {
            new OpenApplication<>(new PartyAgeingController<>(daoFactory, dataPublisher, dataInterface));
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * Prints every party the filter matched.
     * <p>
     * Not the ticked ones and not the page: {@code forPrint} reads the whole filtered set with the
     * same filter the table used.
     */
    private void print() {
        try {
            PartyBalancePage extract = balanceService.forPrint(filter);
            requireRows(extract);
            List<TreeAccountModelForPrint> rows = new ArrayList<>();
            for (PartyBalanceRow row : extract.rows()) {
                TreeAccountModelForPrint printed = new TreeAccountModelForPrint();
                printed.setId(row.partyId());
                printed.setName(row.name());
                printed.setDate(row.lastMovement() == null ? "" : row.lastMovement().toString());
                printed.setPurchase(row.periodDebit().doubleValue());
                printed.setPaid(row.periodCredit().doubleValue());
                printed.setAmount(row.balance().doubleValue());
                printed.setNotes(row.areaName());
                rows.add(printed);
            }
            printReports.printTotalsAccounts(rows, null);
        } catch (Exception e) {
            report(e);
        }
    }

    private void exportExcel() {
        try {
            PartyBalancePage extract = balanceService.forPrint(filter);
            requireRows(extract);
            int written = ExportData.exportDataToExcel(extract.rows(),
                    new PartyBalanceExcelWriter(extract.rows()));
            if (written < 1) {
                throw new BusinessRuleException(text("party.error.cannot.save"));
            }
            AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            if (extract.hasNext()) {
                report(new UserValidationException(LanguageManager.getInstance()
                        .getString("party.balances.truncated", PartyBalanceService.PRINT_LIMIT)));
            }
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * Refuses an export of nothing, out loud.
     * <p>
     * The old export asked the <em>table</em> whether it was empty and then wrote the <em>ticked</em>
     * rows, so ticking nothing produced an empty file and a message saying it had saved.
     */
    private static void requireRows(PartyBalancePage extract) throws UserValidationException {
        if (extract.rows().isEmpty()) {
            throw new UserValidationException(text("party.error.no.data.export"));
        }
    }

    // ---- plumbing --------------------------------------------------------------------

    private PartyKind partyKind() {
        return nameAndAccountInterface.partyKind();
    }

    private Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setOnAction(event -> action.run());
        return button;
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
                ? null : BigDecimal.valueOf(com.hamza.controlsfx.others.DoubleSetting
                .parseDoubleOrDefault(value));
    }

    private static Integer days(TextField field) {
        String value = field.getText();
        if (value == null || value.isBlank()) {
            return null;
        }
        int parsed = (int) com.hamza.controlsfx.others.DoubleSetting.parseDoubleOrDefault(value);
        return parsed <= 0 ? null : parsed;
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private void report(Throwable e) {
        AllAlerts.handleError(text("party.error.account.operation"),
                e instanceof Exception exception ? exception : new RuntimeException(e));
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
