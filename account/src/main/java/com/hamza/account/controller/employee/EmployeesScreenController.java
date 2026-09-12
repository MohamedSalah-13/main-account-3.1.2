package com.hamza.account.controller.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.Employee;
import com.hamza.account.features.employee.EmployeeFilter;
import com.hamza.account.features.employee.EmployeePage;
import com.hamza.account.features.employee.EmployeeScope;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.features.employee.EmployeeState;
import com.hamza.account.features.employee.EmployeeSummary;
import com.hamza.account.features.employee.EmploymentType;
import com.hamza.account.features.employee.Job;
import com.hamza.account.features.employee.SalaryKind;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.PageJumpBox;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.TableColumnViews;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.account.openFxml.AddForAllApplication;
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
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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

import static com.hamza.controlsfx.others.DateSetting.dateAction;
import static com.hamza.controlsfx.others.Utils.setOptionalNumberFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The employees list.
 * <p>
 * <b>What it replaces read the whole table.</b> {@code getEmployeesList()} was
 * {@code SELECT * FROM employees} with no order and no limit, and the search beside it was a
 * hand-rolled three-phase {@code LIKE} capped at fifty rows and merged in a {@code LinkedHashMap} -
 * so a search had no second page, because its order existed only inside that map. There was no
 * filter of any other kind: not by job, not by whether somebody still works here, not by how they
 * are paid.
 * <p>
 * And the salary column was hidden by <b>index</b>: {@code getColumns().get(4).setVisible(...)}.
 * Reordering one line in the column list would have shown it, and the figures were fetched and
 * handed to the screen either way. {@link EmployeeService} decides that once now, and what it
 * decides is whether the columns are selected at all.
 * <p>
 * Everything here follows the parties' balances screen, class for class: SQL-side filtering and
 * paging, four figures that are filters as much as facts, row buttons rather than a toolbar acting
 * on "the selected row", the "العرض" menu over columns that all carry ids, content-sized columns,
 * a typed page number, and a PDF and a spreadsheet of the columns actually on screen.
 */
@Log4j2
@FxmlPath(pathFile = "employees.fxml")
public class EmployeesScreenController extends LoadData {

    /** Set on the row of somebody who has left. Styled in {@code app-theme.css}. */
    private static final PseudoClass STOPPED = PseudoClass.getPseudoClass("stopped");

    /** The row buttons: fixed width, never offered in the view menu, never printed. */
    private static final String ACTIONS_COLUMN = "employee-actions";

    /** The amounts a printed totals line adds up. A sum of hire salaries answers nothing. */
    private static final Set<String> TOTALLED_COLUMNS = Set.of("employee-rate");

    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);

    private final TableView<Employee> table = new TableView<>();
    private final ComboBox<EmployeeState> comboState = new ComboBox<>();
    private final ComboBox<Job> comboJob = new ComboBox<>();
    private final ComboBox<SalaryKind> comboSalaryKind = new ComboBox<>();
    private final ComboBox<EmploymentType> comboEmployment = new ComboBox<>();
    private final DatePicker hiredFrom = new DatePicker();
    private final DatePicker hiredTo = new DatePicker();
    private final TextField rateFrom = new TextField();
    private final TextField rateTo = new TextField();
    private final TextField search = new TextField();
    private final CheckBox delegates = new CheckBox(text("employee.filter.delegates"));

    private final Label statEmployees = statValue("stat-employees");
    private final Label statActive = statValue("stat-active");
    private final Label statDelegates = statValue("stat-delegates");
    private final Label statPayroll = statValue("stat-payroll");

    private final PageJumpBox pageJump = new PageJumpBox(this::search);
    private final ContentSizedColumns<Employee> columnSizing = new ContentSizedColumns<>();
    private final MenuButton viewMenu = TableColumnViews.menuButton();

    private final Label countLabel = new Label();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final ProgressIndicator progress = new ProgressIndicator();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    private EmployeeFilter filter = EmployeeFilter.all();
    private EmployeeSummary summary = EmployeeSummary.EMPTY;
    private int generation;
    private boolean loading;

    public EmployeesScreenController(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    @FXML
    public void initialize() {
        stackPane.getStyleClass().add("screen-employees");

        buildTable();
        columnViews().install(viewMenu, table);
        box.getChildren().setAll(header(), statCards(), filterBar(), tableArea(), footer());
        VBox.setVgrow(box, Priority.ALWAYS);

        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(EmployeesChanged.class, event -> reload()));
            subscriptions.disposeWith(stackPane);
        }
        Platform.runLater(this::loadJobsAndRows);
    }

    // ---- the screen ----------------------------------------------------------------------

    private HBox header() {
        HBox iconBox = new HBox(AppIcon.EMPLOYEES.graphic(32));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-screen-icon-box");

        Label title = new Label(text("employees"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("employee.screen.subtitle"));
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
     * The four figures, three of which narrow the list when clicked.
     * <p>
     * The wage bill does not: it is the one card that is only a fact, and it is blank rather than
     * zero for a reader without {@code employees.show.salary} - a zero there would be a statement
     * about the payroll instead of a refusal to make one.
     */
    private FlowPane statCards() {
        FlowPane cards = new FlowPane(12, 10);
        cards.setId("employee-stats");
        cards.getChildren().addAll(
                card("employee.stat.all", statEmployees, () -> narrow(EmployeeState.ALL, false), true),
                card("employee.stat.active", statActive, () -> narrow(EmployeeState.ACTIVE, false), true),
                card("employee.stat.delegates", statDelegates, () -> narrow(EmployeeState.ALL, true), true),
                card("employee.stat.payroll", statPayroll, () -> {
                }, false));
        return cards;
    }

    private VBox card(String titleKey, Label value, Runnable onClick, boolean clickable) {
        Label caption = new Label(text(titleKey));
        caption.getStyleClass().add("stat-title");
        VBox card = new VBox(4, caption, value);
        card.getStyleClass().addAll("dashboard-tile", "party-stat-card");
        if (clickable) {
            card.getStyleClass().add("clickable-card");
            card.setOnMouseClicked(event -> onClick.run());
        }
        card.setMinWidth(150);
        return card;
    }

    private void narrow(EmployeeState state, boolean delegatesOnly) {
        loading = true;
        try {
            comboState.getSelectionModel().select(state);
            delegates.setSelected(delegatesOnly);
        } finally {
            loading = false;
        }
        search(0);
    }

    private VBox filterBar() {
        comboState.getItems().setAll(EmployeeState.values());
        comboState.setConverter(converter(state -> text(stateKey(state))));
        comboState.getSelectionModel().select(EmployeeState.ALL);
        comboState.setId("employee-state");

        comboJob.setConverter(converter(job -> job == null || job.id() == 0
                ? text("party.statement.filter.all") : job.name()));
        comboJob.setId("employee-job");

        comboSalaryKind.getItems().add(null);
        comboSalaryKind.getItems().addAll(SalaryKind.values());
        comboSalaryKind.setConverter(converter(kind -> kind == null
                ? text("party.statement.filter.all") : text(kind.messageKey())));
        comboSalaryKind.getSelectionModel().selectFirst();

        comboEmployment.getItems().add(null);
        comboEmployment.getItems().addAll(EmploymentType.values());
        comboEmployment.setConverter(converter(type -> type == null
                ? text("party.statement.filter.all") : text(type.messageKey())));
        comboEmployment.getSelectionModel().selectFirst();

        dateAction(hiredFrom);
        dateAction(hiredTo);
        hiredFrom.setPromptText(text("employee.filter.hired.from"));
        hiredTo.setPromptText(text("employee.filter.hired.to"));

        // setOptionalNumberFormatter, not setTextFormatter: the latter seeds "0.0", which would
        // open this screen filtering on rate >= 0 AND rate <= 0 with nobody having typed anything.
        setOptionalNumberFormatter(rateFrom, rateTo);
        rateFrom.setPromptText(text("employee.filter.rate.from"));
        rateTo.setPromptText(text("employee.filter.rate.to"));
        search.setPromptText(text("employee.filter.text"));
        search.setId("employee-search");
        HBox.setHgrow(search, Priority.ALWAYS);

        // The two salary filters are only offered to somebody who may read a salary. A filter you
        // can move is a way of reading the figure it filters on, so the service refuses them too -
        // this only saves the user a refusal they could not have understood.
        boolean salary = employeeService.salaryVisible();
        rateFrom.setVisible(salary);
        rateFrom.setManaged(salary);
        rateTo.setVisible(salary);
        rateTo.setManaged(salary);

        Button apply = new Button(text("search"), AppIcon.SEARCH.graphic());
        apply.getStyleClass().addAll("app-primary-button", "party-primary-button");
        apply.setMinWidth(Region.USE_PREF_SIZE);
        apply.setOnAction(event -> search(0));

        Button clear = new Button(text("party.statement.filter.reset"), AppIcon.CLEAR.graphic());
        clear.getStyleClass().add("app-neutral-button");
        clear.setMinWidth(Region.USE_PREF_SIZE);
        clear.setOnAction(event -> reset());

        comboState.setOnAction(event -> search(0));
        comboJob.setOnAction(event -> search(0));
        comboSalaryKind.setOnAction(event -> search(0));
        comboEmployment.setOnAction(event -> search(0));
        hiredFrom.setOnAction(event -> search(0));
        hiredTo.setOnAction(event -> search(0));
        delegates.setOnAction(event -> search(0));

        // The Enter order, declared once, in the order the bar is filled - rule ق-ل9.
        whenEnterPressed(rateFrom, rateTo, search, apply);

        HBox first = new HBox(8, caption("employee.filter.state"), comboState,
                caption("employee.filter.job"), comboJob,
                caption("employee.filter.salary.kind"), comboSalaryKind,
                caption("employee.filter.employment"), comboEmployment, delegates);
        first.setAlignment(Pos.CENTER_LEFT);

        HBox second = new HBox(8, hiredFrom, hiredTo, rateFrom, rateTo, search, apply, clear);
        second.getChildren().addAll(listActions());
        second.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(8, first, second);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        bar.setId("employee-filters");
        return bar;
    }

    /**
     * The things that act on the <b>list</b>, after the filters that decide what the list is.
     * Editing and stopping one employee are buttons in that employee's own row.
     */
    private Node[] listActions() {
        Button add = button("employee.action.add", AppIcon.ADD, this::openNew);
        add.getStyleClass().setAll("button", "app-primary-button", "party-primary-button");
        Button jobs = button("employee.action.jobs", AppIcon.SETTINGS, this::openJobs);
        Button refresh = button("refresh", AppIcon.REFRESH, this::reload);
        Button print = button("print", AppIcon.PRINT, this::print);
        Button excel = button("party.statement.export.excel", AppIcon.SPREADSHEET, this::exportExcel);

        Separator divider = new Separator(Orientation.VERTICAL);
        divider.getStyleClass().add("modern-separator");
        return new Node[]{divider, add, jobs, refresh, print, excel, viewMenu};
    }

    private TableColumnViews<Employee> columnViews() {
        Preferences preferences = Preferences.userNodeForPackage(EmployeesScreenController.class)
                .node("employees");
        return new TableColumnViews<>(preferences, "view.mode", TableColumnViews.Preset.COMPACT,
                Set.of("employee-code", "employee-name", "employee-job", "employee-phone",
                        "employee-rate"),
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

    /**
     * The columns, with every amount through {@code Columns.money} and every column carrying an id -
     * the view menu names its compact set by id, and {@code TableSetting} would otherwise key a
     * column's saved width and visibility by its position.
     */
    private void buildTable() {
        table.setId("employees-table");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPlaceholder(new Label(text("employee.empty")));
        table.getColumns().setAll(List.of(
                named(ACTIONS_COLUMN, actionsColumn()),
                named("employee-code", Columns.number("code", Employee::id)),
                named("employee-name", Columns.text("name", Employee::name)),
                named("employee-job", Columns.text("employee.column.job", Employee::jobName)),
                named("employee-status", Columns.text("employee.column.status",
                        row -> text(row.active() ? "employee.status.active" : "employee.status.stopped"))),
                named("employee-phone", Columns.text("column.tel", Employee::phone)),
                named("employee-salary-kind", Columns.text("employee.column.salary.kind",
                        row -> row.salaryKind() == null ? "" : text(row.salaryKind().messageKey()))),
                named("employee-rate", Columns.money("employee.column.rate", Employee::rate)),
                named("employee-employment", Columns.text("employee.column.employment",
                        row -> text(row.employmentType().messageKey()))),
                named("employee-hire", Columns.date("employee.column.hire", Employee::hireDate)),
                named("employee-end", Columns.date("employee.column.end", Employee::endDate)),
                named("employee-national", Columns.text("employee.column.national", Employee::nationalId)),
                named("employee-email", Columns.text("column.email", Employee::email)),
                named("employee-address", Columns.text("column.address", Employee::address))));

        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(Employee row, boolean empty) {
                super.updateItem(row, empty);
                pseudoClassStateChanged(STOPPED, !empty && row != null && !row.active());
            }
        });
        table.setOnMouseClicked(event -> {
            Employee selected = table.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                openEdit(selected);
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
     * The three things done to one employee, in that employee's own row.
     * <p>
     * The permissions are a hint in the {@code isGranted} sense - the action is left out rather than
     * offered and refused - and every service behind them still calls {@code require}.
     */
    private TableColumn<Employee, Void> actionsColumn() {
        List<RowAction<Employee>> actions = List.of(
                RowAction.of("employee.action.edit", AppIcon.EDIT, "app-primary-button",
                        AppPermissions.EMPLOYEE_UPDATE, this::openEdit),
                RowAction.of("employee.action.salary", AppIcon.TREASURY_CASH, "app-neutral-button",
                        AppPermissions.EMPLOYEE_SALARY_CHANGE, this::openSalary),
                RowAction.of("employee.action.toggle", AppIcon.SECURITY, "app-neutral-button",
                        AppPermissions.EMPLOYEE_UPDATE, this::toggleActive));
        return RowActionsColumn.of("employee.column.actions", RowAction.permitted(actions));
    }

    // ---- loading -------------------------------------------------------------------------

    private void loadJobsAndRows() {
        try {
            List<Job> options = new ArrayList<>();
            options.add(Job.of(0, ""));
            if (AuthorizationGuard.isGranted(AppPermissions.JOB_SHOW)) {
                options.addAll(employeeService.jobs(EmployeeScope.EVERYONE));
            }
            comboJob.setItems(FXCollections.observableArrayList(options));
            comboJob.getSelectionModel().selectFirst();
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
            comboState.getSelectionModel().select(EmployeeState.ALL);
            comboJob.getSelectionModel().selectFirst();
            comboSalaryKind.getSelectionModel().selectFirst();
            comboEmployment.getSelectionModel().selectFirst();
            hiredFrom.setValue(null);
            hiredTo.setValue(null);
            rateFrom.clear();
            rateTo.clear();
            search.clear();
            delegates.setSelected(false);
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
            report(new UserValidationException(text("employee.error.filter.range")));
            return;
        }
        int token = ++generation;
        progress.setVisible(true);
        Task<EmployeePage> task = new Task<>() {
            @Override
            protected EmployeePage call() throws Exception {
                return employeeService.search(filter);
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
        Thread thread = new Thread(task, "employees-load");
        thread.setDaemon(true);
        thread.start();
    }

    private EmployeeFilter readFilter(int page) {
        Job job = comboJob.getValue();
        return new EmployeeFilter(search.getText(),
                job == null || job.id() == 0 ? null : job.id(),
                comboState.getValue() == null ? EmployeeState.ALL : comboState.getValue(),
                comboSalaryKind.getValue(),
                comboEmployment.getValue(),
                hiredFrom.getValue(), hiredTo.getValue(),
                amount(rateFrom), amount(rateTo),
                delegates.isSelected(),
                page, EmployeeFilter.DEFAULT_PAGE_SIZE);
    }

    private void show(EmployeePage page) {
        summary = page.summary();
        table.setItems(FXCollections.observableArrayList(page.rows()));
        columnSizing.layout(table);

        statEmployees.setText(String.valueOf(summary.employees()));
        statActive.setText(String.valueOf(summary.active()));
        statDelegates.setText(String.valueOf(summary.delegates()));
        statPayroll.setText(summary.monthlyPayroll() == null
                ? "—" : Columns.money(summary.monthlyPayroll()));

        pageJump.showing(page.page(), pageCount(summary.employees(), filter.pageSize()));
        countLabel.setText(LanguageManager.getInstance()
                .getString("employee.count", page.rows().size(), summary.employees()));
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

    private void openEdit(Employee employee) {
        open(employee.id());
    }

    private void open(int employeeId) {
        try {
            new AddForAllApplication(employeeId, new EmployeeFormController(employeeId));
        } catch (Exception e) {
            report(e);
        }
    }

    private void openSalary(Employee employee) {
        try {
            new AddForAllApplication(employee.id(),
                    new EmployeeSalaryController(employee.id(), employee.name()));
        } catch (Exception e) {
            report(e);
        }
    }

    private void openJobs() {
        try {
            new AddForAllApplication(0, new JobsController());
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * Stops an employee, or starts them again.
     * <p>
     * Both directions from the same button, and the stopped are shown here rather than hidden -
     * this screen is the only place the flag can be turned back, so hiding them would make it a
     * one-way door. The same reasoning as {@code PartySearchScope} on the parties list.
     */
    private void toggleActive(Employee employee) {
        try {
            if (!AllAlerts.confirm_all(text("employees"), text(employee.active()
                    ? "employee.confirm.stop" : "employee.confirm.start"))) {
                return;
            }
            employeeService.setActive(employee.id(), !employee.active());
            if (eventBus != null) {
                eventBus.publish(new EmployeesChanged());
            } else {
                reload();
            }
        } catch (Exception e) {
            report(e);
        }
    }

    /** Prints every employee the filter matched, as a PDF of the columns on screen. */
    private void print() {
        String title = text("employees");
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        EmployeeFilter printed = filter;
        Task<EmployeePage> load = new Task<>() {
            @Override
            protected EmployeePage call() throws Exception {
                return employeeService.forPrint(printed);
            }
        };
        load.setOnSucceeded(event -> {
            EmployeePage extract = load.getValue();
            if (extract.rows().isEmpty()) {
                AllAlerts.alertError(text("party.error.no.data.print"));
                return;
            }
            TablePdfLayout layout = TablePdfLayout.from(table, extract.rows(),
                    Set.of(ACTIONS_COLUMN), TOTALLED_COLUMNS, text("total"));
            TablePdfReport.write(target, title, printSubtitle(printed), layout,
                    () -> warnIfTruncated(extract));
        });
        AllAlerts.handleTaskFailure(text("party.error.export.generic"), load);
        TablePdfReport.start(load, "employees-pdf-load");
    }

    private static String printSubtitle(EmployeeFilter printed) {
        String subtitle = text("employee.print.subtitle") + ": " + LocalDate.now();
        if (printed.hasText()) {
            subtitle += "  -  " + text("search") + ": " + printed.text();
        }
        return subtitle;
    }

    private void warnIfTruncated(EmployeePage extract) {
        if (extract.truncated()) {
            report(new UserValidationException(LanguageManager.getInstance()
                    .getString("employee.truncated", EmployeeService.PRINT_LIMIT)));
        }
    }

    private void exportExcel() {
        try {
            EmployeePage extract = employeeService.forPrint(filter);
            if (extract.rows().isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(extract.rows(),
                    VisibleColumnsExcelWriter.of(text("employees"), table,
                            Set.of(ACTIONS_COLUMN), extract.rows()));
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

    private static String stateKey(EmployeeState state) {
        return switch (state) {
            case ALL -> "party.statement.filter.all";
            case ACTIVE -> "employee.status.active";
            case INACTIVE -> "employee.status.stopped";
        };
    }

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
