package com.hamza.account.controller.expense.report;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.report.ExpenseReportService;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.others.DateSetting;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static com.hamza.account.controller.expense.report.ExpenseReportSupport.caption;
import static com.hamza.account.controller.expense.report.ExpenseReportSupport.report;
import static com.hamza.account.controller.expense.report.ExpenseReportSupport.text;

/**
 * The expense reports: by heading, the year by month, the trend, by dimension, and against net sales
 * (docs/expenses-plan.md §4).
 * <p>
 * <b>It opens on the list's own filter and changes only its dates.</b> The heading, till, person, amounts and
 * text the list was narrowed by are carried in and said in plain words under the bar, so a report is
 * always about the set of expenses somebody was looking at - never a second set of filters to keep in
 * step with the first.
 * <p>
 * <b>A line opens the list on exactly that line</b>, through the list's own filter, and closes this window
 * so the list is what is in front of the reader. A line with no filter that says exactly it - a direct line,
 * a payee - offers nothing, rather than a list that does not add up to the figure it was opened from.
 * <p>
 * Nothing on these tabs decides anything: which days make a month, what a main heading adds up to and when a
 * percentage has nothing to divide by are {@code features/expense/report}'s, with a test each.
 */
public class ExpenseReportsController implements AppSettingInterface {

    /** The row actions column's id on every report table - never printed, never exported. */
    static final String ACTIONS_COLUMN = "expense-report-actions";

    private final ExpenseReportService service = new ExpenseReportService();
    private final ExpenseFilter conditions;
    private final String conditionsText;
    private final Consumer<ExpenseFilter> openList;

    private final ComboBox<StatementPeriod> comboPeriod = new ComboBox<>();
    private final DatePicker dateFrom = new DatePicker();
    private final DatePicker dateTo = new DatePicker();
    private final TabPane tabs = new TabPane();
    private final List<ExpenseReportTab> reports = new ArrayList<>();
    /** The tabs already read over the current scope; a tab is read when it is first shown, not before. */
    private final Set<ExpenseReportTab> loaded = new HashSet<>();

    private ExpenseFilter scope;
    private boolean settingPeriod;

    /**
     * @param listFilter     the list's filter as it stood - its dates become the report's starting period
     * @param conditionsText the list's other conditions in words, or blank when there are none
     * @param openList       applies a filter to the list; called after this window has closed
     */
    public ExpenseReportsController(ExpenseFilter listFilter, String conditionsText, Consumer<ExpenseFilter> openList) {
        this.conditions = Objects.requireNonNull(listFilter, "listFilter");
        this.conditionsText = conditionsText == null ? "" : conditionsText;
        this.openList = Objects.requireNonNull(openList, "openList");
    }

    @Override
    public Pane pane() {
        ExpenseReportSupport support = new ExpenseReportSupport(this::openListAndClose, this::describe);
        reports.add(new ExpenseHeadingReportTab(service, support));
        reports.add(new ExpenseYearMatrixTab(service, support));
        reports.add(new ExpenseTrendTab(service, support));
        reports.add(new ExpenseDimensionTab(service, support));
        reports.add(new ExpenseSalesRatioTab(service, support));
        for (ExpenseReportTab report : reports) {
            tabs.getTabs().add(report.tab());
        }
        tabs.getSelectionModel().selectedIndexProperty().addListener((observable, was, index) -> loadSelected());
        tabs.setId("expense-reports-tabs");
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox body = new VBox(8, bar(), tabs);
        body.getStyleClass().add("app-container");
        body.setPadding(new Insets(8));

        StackPane screen = new StackPane(body);
        screen.getStyleClass().addAll("app-root", "screen-expenses");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("expense-reports");
        // Inside 1366x768 with the window's own title bar and the taskbar - the size this ships to.
        screen.setPrefSize(1180, 640);

        Platform.runLater(this::applyScope);
        return screen;
    }

    private VBox bar() {
        comboPeriod.getItems().setAll(StatementPeriod.values());
        comboPeriod.setConverter(new StringConverter<>() {
            @Override
            public String toString(StatementPeriod period) {
                return period == null ? "" : text(period.messageKey());
            }

            @Override
            public StatementPeriod fromString(String string) {
                return null;
            }
        });
        comboPeriod.setId("expense-reports-period");
        comboPeriod.setOnAction(event -> applyPeriod(comboPeriod.getValue()));

        // The list's own format, and no value seeded: opened, it wrote 1/1/2026 beside a list writing
        // 2026/01/01.
        DateSetting.dateFilter(dateFrom);
        DateSetting.dateFilter(dateTo);
        // The list's own dates: the report is about what the list was showing.
        dateFrom.setValue(conditions.from());
        dateTo.setValue(conditions.to());
        dateFrom.setPromptText(text("expense.filter.from"));
        dateTo.setPromptText(text("expense.filter.to"));
        dateFrom.setOnAction(event -> datesChanged());
        dateTo.setOnAction(event -> datesChanged());

        Button refresh = button("refresh", AppIcon.REFRESH, this::applyScope);
        refresh.getStyleClass().setAll("button", "app-primary-button", "party-primary-button");
        FlowPane row = new FlowPane(8, 8, caption("expense.filter.period"), comboPeriod, dateFrom, dateTo, refresh);
        if (AuthorizationGuard.isGranted(AppPermissions.EXPENSES_EXPORT)) {
            row.getChildren().addAll(button("print", AppIcon.PRINT, this::print),
                    button("party.statement.export.excel", AppIcon.SPREADSHEET, this::export));
        }
        row.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(6, row);
        if (!conditionsText.isBlank()) {
            Label narrowed = new Label(text("expense.report.conditions") + ": " + conditionsText);
            narrowed.getStyleClass().add("form-label");
            narrowed.setWrapText(true);
            narrowed.setId("expense-reports-conditions");
            bar.getChildren().add(narrowed);
        }
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    private void applyPeriod(StatementPeriod period) {
        if (period == null || settingPeriod) {
            return;
        }
        settingPeriod = true;
        try {
            LocalDate today = LocalDate.now();
            dateFrom.setValue(period.needsEarliestMovement() ? null : period.from(today));
            dateTo.setValue(period.needsEarliestMovement() ? null : period.to(today));
        } finally {
            settingPeriod = false;
        }
        applyScope();
    }

    /** A date typed by hand is no longer the period the combo names. */
    private void datesChanged() {
        if (settingPeriod) {
            return;
        }
        settingPeriod = true;
        try {
            comboPeriod.setValue(null);
        } finally {
            settingPeriod = false;
        }
        applyScope();
    }

    private void applyScope() {
        try {
            scope = conditions.withPeriod(dateFrom.getValue(), dateTo.getValue());
        } catch (IllegalArgumentException reversed) {
            report(new UserValidationException(text("expense.error.filter.range")));
            return;
        }
        loaded.clear();
        loadSelected();
    }

    private void loadSelected() {
        ExpenseReportTab selected = selected();
        if (selected != null && scope != null && loaded.add(selected)) {
            selected.load(scope);
        }
    }

    private ExpenseReportTab selected() {
        int index = tabs.getSelectionModel().getSelectedIndex();
        return index < 0 || index >= reports.size() ? null : reports.get(index);
    }

    private void print() {
        run(ExpenseReportTab::print);
    }

    private void export() {
        run(ExpenseReportTab::export);
    }

    /** Asks the export permission of the service first - the file, not the screen, leaves the shop. */
    private void run(Consumer<ExpenseReportTab> action) {
        ExpenseReportTab selected = selected();
        if (selected == null) {
            return;
        }
        try {
            service.requireExport();
            action.accept(selected);
        } catch (Exception e) {
            report(e);
        }
    }

    /** The period, then the list's other conditions - what a printed report writes under its title. */
    private String describe(ExpenseFilter printed) {
        String from = printed.from() == null ? "…" : printed.from().toString();
        String to = printed.to() == null ? "…" : printed.to().toString();
        String text = text("expense.print.period") + ": " + from + "  -  " + to;
        return conditionsText.isBlank() ? text : text + "  |  " + conditionsText;
    }

    /** Closes this window first, so the list the line opened is the thing in front of the reader. */
    private void openListAndClose(ExpenseFilter filter) {
        Window window = tabs.getScene() == null ? null : tabs.getScene().getWindow();
        if (window != null) {
            window.hide();
        }
        Platform.runLater(() -> openList.accept(filter));
    }

    private static Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    @Override
    public String title() {
        return text("expense.report.title");
    }

    @Override
    public boolean resize() {
        return true;
    }
}
