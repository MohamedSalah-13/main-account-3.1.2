package com.hamza.account.controller.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.EmployeeEntryKind;
import com.hamza.account.features.employee.EmployeeMovementSource;
import com.hamza.account.features.employee.statement.EmployeeStatementFilter;
import com.hamza.account.features.employee.statement.EmployeeStatementPage;
import com.hamza.account.features.employee.statement.EmployeeStatementRow;
import com.hamza.account.features.employee.statement.EmployeeStatementService;
import com.hamza.account.features.employee.statement.EmployeeStatementSummary;
import com.hamza.account.features.employee.statement.EmployeeStatementUserOption;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.PageJumpBox;
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
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.prefs.Preferences;

import static com.hamza.controlsfx.others.DateSetting.dateAction;
import static com.hamza.controlsfx.others.Utils.setOptionalNumberFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * One employee's account: what they earned, what was deducted, and every pound paid to them.
 * <p>
 * It reads {@code employee_account_table} through {@link EmployeeStatementService} — the ledger
 * unioned with the {@code expenses_details} rows carrying this employee's id. <b>So the years of
 * wage payments already in a customer's database are on this statement the first time it opens</b>,
 * with no data migration: it reads the table they were written to (ق-١).
 * <p>
 * Everything the party statement learned is applied here rather than rediscovered:
 * <ul>
 *   <li>The <b>running balance is accumulated in SQL</b>, seeded with what came before the period.
 *       A total restarted at zero prints September as though the employee began it owed nothing.</li>
 *   <li>The <b>two balances answer the dates alone</b>; only the two totals follow the filters. A
 *       "balance before the period" measured over deductions only is not anybody's balance, and
 *       this is the figure an employee signs for.</li>
 *   <li>The table, the print and the spreadsheet come from <b>one filter</b>, so an exported file
 *       cannot describe a different set from the screen.</li>
 *   <li>Loading is off the JavaFX thread with a {@code generation} token, so the answer to a search
 *       the user has already replaced is discarded rather than drawn.</li>
 * </ul>
 */
@Log4j2
@FxmlPath(pathFile = "employee-statement.fxml")
public class EmployeeStatementController implements AppSettingInterface {

    /** Set on a row that is cash out of a till, so a payment reads differently from a decision. */
    private static final PseudoClass CASH_ROW = PseudoClass.getPseudoClass("cash-movement");

    /** The columns a printed totals line adds up. A sum of running balances answers nothing. */
    private static final Set<String> TOTALLED_COLUMNS =
            Set.of("statement-debit", "statement-credit");

    private final EmployeeStatementService statementService =
            ServiceRegistry.get(EmployeeStatementService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final int employeeId;
    private final String employeeName;

    private final TableView<EmployeeStatementRow> table = new TableView<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker(LocalDate.now());
    private final ComboBox<StatementPeriod> comboPeriod = new ComboBox<>();
    private final MenuButton kindsMenu = new MenuButton();
    private final ComboBox<EmployeeMovementSource> comboSource = new ComboBox<>();
    private final ComboBox<EmployeeStatementUserOption> comboUser = new ComboBox<>();
    private final TextField amountFrom = new TextField();
    private final TextField amountTo = new TextField();
    private final TextField search = new TextField();

    private final Label statOpening = statValue("stat-opening");
    private final Label statCredit = statValue("stat-credit");
    private final Label statDebit = statValue("stat-debit");
    private final Label statClosing = statValue("stat-closing");

    private final PageJumpBox pageJump = new PageJumpBox(this::search);
    private final ContentSizedColumns<EmployeeStatementRow> columnSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();

    private final Label countLabel = new Label();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final Button close = closeButton();
    private final ProgressIndicator progress = new ProgressIndicator();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    private EmployeeStatementFilter filter;
    private EmployeeStatementSummary summary = EmployeeStatementSummary.EMPTY;
    private int generation;
    private boolean loading;

    public EmployeeStatementController(int employeeId, String employeeName) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.filter = EmployeeStatementFilter.all(employeeId,
                LocalDate.now().withDayOfMonth(1), LocalDate.now());
    }

    @FXML
    public void initialize() {
        stackPane.getStyleClass().add("screen-employees");

        buildTable();
        columnViews().install(viewMenu, table);
        box.getChildren().setAll(identityHeader(), statCards(), filterBar(), tableArea(), footer());
        VBox.setVgrow(box, Priority.ALWAYS);
        Platform.runLater(this::openOnTheWholeAccount);
    }

    // ---- the screen ----------------------------------------------------------------------

    private HBox identityHeader() {
        HBox iconBox = new HBox(AppIcon.REPORT.graphic(32));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-screen-icon-box");

        Label title = new Label(text("employee.statement.title") + " - " + employeeName);
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("employee.statement.subtitle"));
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

    /**
     * The four figures, and none of them is clickable.
     * <p>
     * On the employees list a figure doubles as a filter because "show me those twelve" is the
     * next thing anybody does with it. Here they are the statement's own arithmetic - narrowing
     * the rows by clicking an opening balance would produce a list that does not add up to it.
     */
    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("statement-stats");
        cards.getChildren().addAll(
                card("employee.statement.opening", statOpening),
                card("employee.statement.credit", statCredit),
                card("employee.statement.debit", statDebit),
                card("employee.statement.closing", statClosing));
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

    private VBox filterBar() {
        comboPeriod.getItems().setAll(StatementPeriod.values());
        comboPeriod.setConverter(converter(period -> period == null ? "" : text(period.messageKey())));
        comboPeriod.setOnAction(event -> applyPeriod());

        dateAction(from);
        dateAction(to);

        buildKindsMenu();

        comboSource.getItems().add(null);
        comboSource.getItems().addAll(EmployeeMovementSource.values());
        comboSource.setConverter(converter(source -> source == null
                ? text("party.statement.filter.all") : text(source.messageKey())));
        comboSource.getSelectionModel().selectFirst();
        comboSource.setOnAction(event -> search(0));

        comboUser.setConverter(converter(user -> user == null || user.id() == 0
                ? text("party.statement.filter.all") : user.name()));
        comboUser.setOnAction(event -> search(0));

        // A filter box, so the formatter that seeds 0.0 would open this screen filtering on
        // amount >= 0 AND amount <= 0 with nobody having typed anything.
        setOptionalNumberFormatter(amountFrom, amountTo);
        amountFrom.setPromptText(text("employee.statement.filter.amount.from"));
        amountTo.setPromptText(text("employee.statement.filter.amount.to"));
        search.setPromptText(text("employee.statement.filter.text"));
        HBox.setHgrow(search, Priority.ALWAYS);

        Button apply = new Button(text("search"), AppIcon.SEARCH.graphic());
        apply.getStyleClass().addAll("app-primary-button", "party-primary-button");
        apply.setMinWidth(Region.USE_PREF_SIZE);
        apply.setOnAction(event -> search(0));

        Button clear = new Button(text("party.statement.filter.reset"), AppIcon.CLEAR.graphic());
        clear.getStyleClass().add("app-neutral-button");
        clear.setMinWidth(Region.USE_PREF_SIZE);
        clear.setOnAction(event -> reset());

        whenEnterPressed(amountFrom, amountTo, search, apply);

        HBox first = new HBox(8, caption("employee.statement.period"), comboPeriod,
                caption("from"), from, caption("to"), to,
                caption("employee.statement.filter.kind"), kindsMenu,
                caption("employee.statement.filter.source"), comboSource);
        first.setAlignment(Pos.CENTER_LEFT);

        HBox second = new HBox(8, caption("employee.statement.filter.user"), comboUser,
                amountFrom, amountTo, search, apply, clear);
        second.getChildren().addAll(listActions());
        second.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(8, first, second);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    /**
     * The movement kinds, ticked rather than picked one at a time.
     * <p>
     * A statement is read by asking "show me the deductions and the advances", which is two kinds
     * and not one - a single-select combo would make that two passes over the same period. The
     * codes are the enums' own names and never anything a user typed, which is what lets the
     * filter be bound as one {@code FIND_IN_SET} value.
     */
    private void buildKindsMenu() {
        kindsMenu.setText(text("party.statement.filter.all"));
        for (EmployeeEntryKind kind : EmployeeEntryKind.values()) {
            kindsMenu.getItems().add(kindItem(kind.name(), text(kind.messageKey())));
        }
        for (var purpose : com.hamza.account.features.employee.EmployeeCashPurpose.values()) {
            kindsMenu.getItems().add(kindItem(purpose.name(), text(purpose.messageKey())));
        }
    }

    private CheckMenuItem kindItem(String code, String label) {
        CheckMenuItem item = new CheckMenuItem(label);
        item.setId("kind-" + code);
        item.setOnAction(event -> {
            kindsMenu.setText(describeKinds());
            search(0);
        });
        return item;
    }

    private Set<String> selectedKinds() {
        Set<String> codes = new LinkedHashSet<>();
        kindsMenu.getItems().stream()
                .filter(item -> item instanceof CheckMenuItem checked && checked.isSelected())
                .forEach(item -> codes.add(item.getId().substring("kind-".length())));
        return codes;
    }

    private String describeKinds() {
        int chosen = selectedKinds().size();
        return chosen == 0 ? text("party.statement.filter.all")
                : LanguageManager.getInstance().getString("employee.statement.filter.kinds", chosen);
    }

    private Node[] listActions() {
        Button pay = button("employee.action.pay", AppIcon.TREASURY_CASH, this::openPayment);
        pay.getStyleClass().setAll("button", "app-primary-button", "party-primary-button");
        pay.setDisable(!AuthorizationGuard.isGranted(AppPermissions.EMPLOYEE_PAY));

        Button record = button("employee.action.record", AppIcon.ADD, this::openLedgerEntry);
        record.setDisable(!AuthorizationGuard.isGranted(AppPermissions.EMPLOYEE_ACCOUNT_ADJUST));

        Button refresh = button("refresh", AppIcon.REFRESH, this::reload);
        Button print = button("print", AppIcon.PRINT, this::print);
        Button excel = button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel);

        Separator divider = new Separator(Orientation.VERTICAL);
        divider.getStyleClass().add("modern-separator");
        return new Node[]{divider, pay, record, refresh, print, excel, viewMenu};
    }

    private TableColumnViews<EmployeeStatementRow> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(EmployeeStatementController.class)
                .node("employee-statement");
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.FULL,
                Set.of("statement-date", "statement-kind", "statement-debit", "statement-credit",
                        "statement-balance"),
                Set.of());
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
        HBox bar = new HBox(12, countLabel, close, spacer, previous, pageJump, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("summary-card", "party-summary-bar");
        return bar;
    }

    /**
     * The statement's own way out, because {@code addLastPane()} is false.
     * <p>
     * It was true, and that is what {@code DialogApplication} reads as "this screen saves": the
     * dialog grew a save button, so a read-only statement asked "do you want to save?" and then
     * refused to close, since {@code ActionSave.save()} defaults to 0 and anything but 1 is a
     * failed save. Nothing here writes a row - the two screens that do are opened from it and
     * carry their own save. {@code DeleteDataController} is the same shape for the same reason.
     */
    private Button closeButton() {
        Button button = button("common.close", AppIcon.CLOSE, () -> { });
        button.setId("btnClose");
        button.setOnAction(event -> ((Stage) button.getScene().getWindow()).close());
        return button;
    }

    private void buildTable() {
        table.setId("employee-statement-table");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPlaceholder(new Label(text("employee.statement.empty")));
        table.getColumns().setAll(List.of(
                named("statement-date", Columns.date("date", EmployeeStatementRow::date)),
                named("statement-kind", Columns.text("employee.statement.column.kind",
                        row -> text(row.kindMessageKey()))),
                named("statement-source", Columns.text("employee.statement.column.source",
                        row -> text(row.source().messageKey()))),
                named("statement-debit", Columns.money("employee.statement.column.debit",
                        EmployeeStatementRow::debit)),
                named("statement-credit", Columns.money("employee.statement.column.credit",
                        EmployeeStatementRow::credit)),
                named("statement-balance", Columns.money("employee.statement.column.balance",
                        EmployeeStatementRow::runningBalance)),
                named("statement-notes", Columns.text("column.notes", EmployeeStatementRow::notes)),
                named("statement-user", Columns.text("users", EmployeeStatementRow::userName))));

        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(EmployeeStatementRow row, boolean empty) {
                super.updateItem(row, empty);
                pseudoClassStateChanged(CASH_ROW, !empty && row != null && row.isCash());
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

    // ---- loading -------------------------------------------------------------------------

    /**
     * Opens on the employee's whole account rather than on this month.
     * <p>
     * A statement opened on a period the employee has no movement in is an empty screen that looks
     * like a defect. The first day comes from the database - one scalar read - rather than from
     * loading the account to find out, which is what the party screen used to do.
     */
    private void openOnTheWholeAccount() {
        try {
            LocalDate earliest = statementService.earliestMovement(employeeId);
            loading = true;
            try {
                comboPeriod.getSelectionModel().select(StatementPeriod.ALL);
                from.setValue(earliest == null ? LocalDate.now().withDayOfMonth(1) : earliest);
                to.setValue(LocalDate.now());
            } finally {
                loading = false;
            }
            loadUsers();
        } catch (Exception e) {
            report(e);
        }
        search(0);
    }

    private void loadUsers() {
        try {
            List<EmployeeStatementUserOption> users = new ArrayList<>();
            users.add(new EmployeeStatementUserOption(0, ""));
            users.addAll(statementService.usersWhoEntered(employeeId));
            comboUser.setItems(FXCollections.observableArrayList(users));
            comboUser.getSelectionModel().selectFirst();
        } catch (Exception e) {
            report(e);
        }
    }

    private void applyPeriod() {
        StatementPeriod period = comboPeriod.getValue();
        if (period == null || loading) {
            return;
        }
        loading = true;
        try {
            LocalDate today = LocalDate.now();
            if (period.needsEarliestMovement()) {
                LocalDate earliest = statementService.earliestMovement(employeeId);
                from.setValue(earliest == null ? today.withDayOfMonth(1) : earliest);
            } else {
                from.setValue(period.from(today));
            }
            to.setValue(period.to(today));
        } catch (Exception e) {
            report(e);
        } finally {
            loading = false;
        }
        search(0);
    }

    private void reload() {
        search(filter.page());
    }

    private void reset() {
        loading = true;
        try {
            kindsMenu.getItems().forEach(item -> {
                if (item instanceof CheckMenuItem checked) {
                    checked.setSelected(false);
                }
            });
            kindsMenu.setText(text("party.statement.filter.all"));
            comboSource.getSelectionModel().selectFirst();
            comboUser.getSelectionModel().selectFirst();
            amountFrom.clear();
            amountTo.clear();
            search.clear();
        } finally {
            loading = false;
        }
        search(0);
    }

    private void search(int page) {
        if (loading) {
            return;
        }
        try {
            filter = readFilter(Math.max(page, 0));
        } catch (IllegalArgumentException contradiction) {
            report(new UserValidationException(text("employee.error.filter.range")));
            return;
        }
        int token = ++generation;
        progress.setVisible(true);
        Task<EmployeeStatementPage> task = new Task<>() {
            @Override
            protected EmployeeStatementPage call() throws Exception {
                return statementService.search(filter);
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
        Thread thread = new Thread(task, "employee-statement-load");
        thread.setDaemon(true);
        thread.start();
    }

    private EmployeeStatementFilter readFilter(int page) {
        EmployeeStatementUserOption user = comboUser.getValue();
        return new EmployeeStatementFilter(employeeId,
                from.getValue() == null ? LocalDate.now().withDayOfMonth(1) : from.getValue(),
                to.getValue() == null ? LocalDate.now() : to.getValue(),
                selectedKinds(),
                comboSource.getValue(),
                user == null || user.id() == 0 ? null : user.id(),
                amount(amountFrom), amount(amountTo),
                search.getText(),
                page, EmployeeStatementFilter.DEFAULT_PAGE_SIZE);
    }

    private void show(EmployeeStatementPage page) {
        summary = page.summary();
        table.setItems(FXCollections.observableArrayList(page.rows()));
        columnSizing.layout(table);

        statOpening.setText(Columns.money(summary.openingBalance()));
        statCredit.setText(Columns.money(summary.totalCredit()));
        statDebit.setText(Columns.money(summary.totalDebit()));
        statClosing.setText(Columns.money(summary.closingBalance()));

        countLabel.setText(LanguageManager.getInstance()
                .getString("employee.statement.count", page.rows().size(), summary.shownCount()));
        pageJump.showing(page.page(), pageCount(summary.shownCount(), filter.pageSize()));
        previous.setDisable(!page.hasPrevious());
        next.setDisable(!page.hasNext());
    }

    /** Rounded up, and never less than one: an empty statement is still page one of one. */
    static int pageCount(int rows, int pageSize) {
        if (pageSize < 1) {
            return 1;
        }
        return Math.max(1, (rows + pageSize - 1) / pageSize);
    }

    // ---- actions -------------------------------------------------------------------------

    private void openPayment() {
        try {
            new AddForAllApplication(0, new EmployeePaymentController(employeeId, employeeName));
            afterWrite();
        } catch (Exception e) {
            report(e);
        }
    }

    private void openLedgerEntry() {
        try {
            new AddForAllApplication(0, new EmployeeLedgerEntryController(employeeId, employeeName));
            afterWrite();
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * Reloads after a dialog that may have written, and tells the rest of the application.
     * <p>
     * The dialogs are modal, so this runs once they close - there is no second window to keep in
     * step, and a reload that finds nothing new costs one query.
     */
    private void afterWrite() {
        if (eventBus != null) {
            eventBus.publish(new EmployeesChanged());
        }
        reload();
    }

    private void print() {
        String title = text("employee.statement.title") + " - " + employeeName;
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        EmployeeStatementFilter printed = filter;
        Task<EmployeeStatementPage> load = new Task<>() {
            @Override
            protected EmployeeStatementPage call() throws Exception {
                return statementService.forPrint(printed);
            }
        };
        load.setOnSucceeded(event -> {
            EmployeeStatementPage extract = load.getValue();
            if (extract.rows().isEmpty()) {
                AllAlerts.alertError(text("party.error.no.data.print"));
                return;
            }
            TablePdfLayout layout = TablePdfLayout.from(table, extract.rows(),
                    Set.of(), TOTALLED_COLUMNS, text("total"));
            TablePdfReport.write(target, title, printSubtitle(printed, extract), layout,
                    () -> warnIfTruncated(extract));
        });
        AllAlerts.handleTaskFailure(text("party.error.export.generic"), load);
        TablePdfReport.start(load, "employee-statement-pdf");
    }

    /**
     * The period and the two balances, on the printed page.
     * <p>
     * A statement handed to an employee without the balance it opens and closes on is a list of
     * movements, not a statement - it is the closing figure they are being asked to agree with.
     */
    private static String printSubtitle(EmployeeStatementFilter printed,
                                        EmployeeStatementPage extract) {
        return text("from") + ": " + printed.from() + "   " + text("to") + ": " + printed.to()
                + "   " + text("employee.statement.opening") + ": "
                + Columns.money(extract.summary().openingBalance())
                + "   " + text("employee.statement.closing") + ": "
                + Columns.money(extract.summary().closingBalance());
    }

    private void warnIfTruncated(EmployeeStatementPage extract) {
        if (extract.truncated()) {
            report(new UserValidationException(LanguageManager.getInstance()
                    .getString("employee.truncated", EmployeeStatementService.PRINT_LIMIT)));
        }
    }

    private void exportExcel() {
        try {
            EmployeeStatementPage extract = statementService.forPrint(filter);
            if (extract.rows().isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(extract.rows(),
                    VisibleColumnsExcelWriter.of(text("employee.statement.title"), table,
                            Set.of(), extract.rows()));
            if (written < 1) {
                return;
            }
            AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            warnIfTruncated(extract);
        } catch (Exception e) {
            report(e);
        }
    }

    // ---- the dialog shell ------------------------------------------------------------------

    /**
     * Loads the shell and returns it, which is what runs {@link #initialize()} and builds the
     * screen. The dialog asks for this once.
     */
    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return text("employee.statement.title") + " - " + employeeName;
    }

    @Override
    public boolean resize() {
        return true;
    }

    /**
     * No dialog buttons: this screen reads, and the close button in its footer is the way out.
     * See {@link #closeButton()} for what returning true did.
     */
    @Override
    public boolean addLastPane() {
        return false;
    }

    @Override
    public double minWidth() {
        return 900;
    }

    @Override
    public double minHeight() {
        return 600;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }

    // ---- plumbing --------------------------------------------------------------------------

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
        Label label = new Label("0.00");
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
        AllAlerts.handleError(text("employee.error.operation"),
                e instanceof Exception exception ? exception : new RuntimeException(e));
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
