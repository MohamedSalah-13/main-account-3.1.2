package com.hamza.account.controller.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingService;
import com.hamza.account.features.expense.budget.ExpenseBudget;
import com.hamza.account.features.expense.budget.ExpenseBudgetDraft;
import com.hamza.account.features.expense.budget.ExpenseBudgetRules;
import com.hamza.account.features.expense.budget.ExpenseBudgetService;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * What each heading is allowed to spend, by year or by month of it (docs/expenses-plan.md §5.1).
 * <p>
 * <b>This screen decides the figures; it does not compare them with anything.</b> The comparison is a tab
 * in the reports window, over the reports' own period and the report by heading's own totals - so "spent"
 * has one definition in the whole expenses area. A second computation here is exactly the defect the
 * reports were built to remove.
 * <p>
 * <b>A budget may be set on a main heading as well as on a sub-heading</b>, and the two do not add up:
 * {@code ExpenseBudgetReport} takes the main heading's own figure as the family's ceiling when it has
 * one, and the sum of its children's when it has not. That rule lives there, with a test, rather than in
 * anything this screen refuses.
 * <p>
 * The year is chosen once, at the top; everything below is that year. A month is optional - "the year as
 * a whole" is the ordinary case for a rent, and a month of its own is for the ones that are not flat.
 */
@Log4j2
@FxmlPath(pathFile = "expense-budgets.fxml")
public class ExpenseBudgetsController implements AddInterface {

    /** January first. Written out because a key built from a number is invisible to every static check. */
    static final List<String> MONTH_KEYS = List.of(
            "expense.report.month.1", "expense.report.month.2", "expense.report.month.3",
            "expense.report.month.4", "expense.report.month.5", "expense.report.month.6",
            "expense.report.month.7", "expense.report.month.8", "expense.report.month.9",
            "expense.report.month.10", "expense.report.month.11", "expense.report.month.12");

    /** How far either side of today the year picker reaches when the table is still empty. */
    private static final int YEARS_BACK = 3;
    private static final int YEARS_FORWARD = 1;

    /** The whole year, as the month combo's first entry. Zero is not a month, which is what V66 relies on. */
    private static final int WHOLE_YEAR = 0;

    private final ExpenseBudgetService budgetService = ServiceRegistry.get(ExpenseBudgetService.class);
    private final ExpenseHeadingService headingService = ServiceRegistry.get(ExpenseHeadingService.class);

    private final TableView<ExpenseBudget> table = new TableView<>();
    private final ContentSizedColumns<ExpenseBudget> columnSizing = new ContentSizedColumns<>();
    private final ComboBox<Integer> comboYear = new ComboBox<>();
    private final ComboBox<ExpenseHeading> comboHeading = new ComboBox<>();
    private final ComboBox<Integer> comboMonth = new ComboBox<>();
    private final TextField txtAmount = new TextField();
    private final TextField txtNotes = new TextField();
    private final Label editingLabel = new Label();
    private final Label totalLabel = new Label();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    /** Which budget the entry bar is correcting; 0 while it is adding one. */
    private int editing;

    @FXML
    public void initialize() {
        otherSetting();
        selectData();
    }

    @Override
    public void otherSetting() {
        stackPane.getStyleClass().add("screen-expenses");

        comboYear.setConverter(converter(year -> year == null ? "" : String.valueOf(year)));
        comboYear.setOnAction(event -> reloadYear());
        comboYear.setPrefWidth(110);

        comboHeading.setConverter(converter(heading -> heading == null ? "" : heading.path()));
        comboHeading.setPrefWidth(240);

        comboMonth.setConverter(converter(month -> month == null || month == WHOLE_YEAR
                ? text("expense.budget.whole.year") : text(MONTH_KEYS.get(month - 1))));
        List<Integer> months = new ArrayList<>();
        months.add(WHOLE_YEAR);
        for (int month = 1; month <= 12; month++) {
            months.add(month);
        }
        comboMonth.setItems(FXCollections.observableArrayList(months));
        comboMonth.getSelectionModel().selectFirst();
        comboMonth.setPrefWidth(150);

        setTextFormatter(txtAmount);
        txtAmount.setPrefWidth(140);
        txtNotes.setPromptText(text("column.notes"));
        txtNotes.setPrefWidth(200);
        editingLabel.getStyleClass().add("form-hint");
        totalLabel.getStyleClass().add("app-readonly-amount");
        HBox.setHgrow(editingLabel, Priority.ALWAYS);
        whenEnterPressed(comboHeading, comboMonth, txtAmount, txtNotes);

        buildTable();
        box.getChildren().setAll(header(), entryBar(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        // The size the headings screen was corrected to, for the same 1366x768 screen with a taskbar.
        table.setPrefHeight(320);
    }

    private HBox header() {
        Label title = new Label(text("expense.budgets.title"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("expense.budgets.subtitle"));
        subtitle.getStyleClass().add("party-screen-subtitle");
        subtitle.setWrapText(true);
        VBox captions = new VBox(3, title, subtitle);
        HBox bar = new HBox(12, AppIcon.REPORT.graphic(24), captions);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    /**
     * The year, then the entry. A {@code FlowPane} rather than an {@code HBox}: seven controls in a row
     * pushed the table off a 1366-point screen on the trend tab, which is the same mistake in a dialog.
     */
    private VBox entryBar() {
        FlowPane row = new FlowPane(8, 8,
                field("expense.budget.year", comboYear),
                field("expense.column.heading", comboHeading),
                field("expense.budget.month", comboMonth),
                field("column.amount", txtAmount),
                txtNotes, editingLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox totals = new HBox(8, caption("expense.budget.year.total"), totalLabel);
        totals.setAlignment(Pos.CENTER_LEFT);
        VBox bar = new VBox(8, row, totals);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        bar.setId("expense-budget-form");
        return bar;
    }

    private void buildTable() {
        table.setId("expense-budgets-table");
        table.setPlaceholder(new Label(text("expense.budgets.empty")));
        List<RowAction<ExpenseBudget>> actions = RowAction.permitted(List.of(
                RowAction.of("update", AppIcon.EDIT, "app-primary-button",
                        AppPermissions.EXPENSES_BUDGET_MANAGE, this::edit),
                RowAction.of("delete", AppIcon.DELETE, "app-neutral-button",
                        AppPermissions.EXPENSES_BUDGET_MANAGE, this::remove)));
        table.getColumns().setAll(List.of(
                RowActionsColumn.of("employee.column.actions", actions),
                Columns.text("expense.column.heading", ExpenseBudget::headingPath),
                Columns.text("expense.budget.month", this::periodOf),
                Columns.money("column.amount", ExpenseBudget::amount),
                Columns.text("column.notes", ExpenseBudget::notes)));
        columnSizing.install(table);
    }

    /** "السنة كلها", or the month's name. */
    private String periodOf(ExpenseBudget budget) {
        return budget.isYearly() ? text("expense.budget.whole.year") : text(MONTH_KEYS.get(budget.month() - 1));
    }

    @Override
    public void selectData() {
        try {
            loadHeadings();
            loadYears();
            reloadYear();
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * Every heading that is still in use, main headings included.
     * <p>
     * Not {@code forExpenses()}: that leaves out the headings employees are paid under, and the wages
     * bill is the largest line a shop budgets. The report by heading counts those expenses, so a budget
     * has to be settable against them or the two sides would describe different money.
     */
    private void loadHeadings() throws Exception {
        List<ExpenseHeading> offered = headingService.all().stream().filter(ExpenseHeading::active).toList();
        ExpenseHeading chosen = comboHeading.getValue();
        comboHeading.setItems(FXCollections.observableArrayList(offered));
        if (chosen != null) {
            offered.stream().filter(heading -> heading.id() == chosen.id()).findFirst()
                    .ifPresent(comboHeading.getSelectionModel()::select);
        }
    }

    /** The years that already carry budgets, and the ones around today so a first budget can be entered. */
    private void loadYears() throws Exception {
        int thisYear = LocalDate.now().getYear();
        Set<Integer> years = new LinkedHashSet<>(budgetService.years());
        for (int year = thisYear + YEARS_FORWARD; year >= thisYear - YEARS_BACK; year--) {
            years.add(year);
        }
        List<Integer> sorted = years.stream()
                .filter(year -> year >= ExpenseBudgetRules.FIRST_YEAR && year <= ExpenseBudgetRules.LAST_YEAR)
                .sorted((left, right) -> Integer.compare(right, left))
                .toList();
        Integer chosen = comboYear.getValue();
        comboYear.setItems(FXCollections.observableArrayList(sorted));
        if (chosen != null && sorted.contains(chosen)) {
            comboYear.getSelectionModel().select(chosen);
        } else if (sorted.contains(thisYear)) {
            comboYear.getSelectionModel().select(Integer.valueOf(thisYear));
        } else {
            comboYear.getSelectionModel().selectFirst();
        }
    }

    private void reloadYear() {
        Integer year = comboYear.getValue();
        if (year == null) {
            return;
        }
        try {
            List<ExpenseBudget> budgets = budgetService.byYear(year);
            table.setItems(FXCollections.observableArrayList(budgets));
            columnSizing.layout(table);
            // What the shop has committed for the year, as entered. It is not the report's total: a main
            // heading's budget and its children's are both rows here, and only one of them is the ceiling.
            BigDecimal total = budgets.stream().map(ExpenseBudget::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalLabel.setText(Columns.money(total));
        } catch (Exception e) {
            report(e);
        }
    }

    /** Must answer exactly 1, or the dialog treats a saved budget as a failure and stays open. */
    @Override
    public int insertData() throws Exception {
        ExpenseHeading heading = comboHeading.getValue();
        Integer month = comboMonth.getValue();
        budgetService.save(new ExpenseBudgetDraft(editing, heading == null ? 0 : heading.id(),
                comboYear.getValue() == null ? 0 : comboYear.getValue(),
                month == null || month == WHOLE_YEAR ? null : month,
                amount(txtAmount), txtNotes.getText()));
        editing = 0;
        return 1;
    }

    @Override
    public void afterSaved() {
        // A budget is read by the reports window when it opens, so nothing else has to be told.
        loadYearsQuietly();
        reloadYear();
        resetData();
    }

    private void loadYearsQuietly() {
        try {
            loadYears();
        } catch (Exception e) {
            log.warn("Could not refresh the budget years", e);
        }
    }

    private void edit(ExpenseBudget budget) {
        editing = budget.id();
        comboHeading.getItems().stream().filter(heading -> heading.id() == budget.headingId()).findFirst()
                .ifPresent(comboHeading.getSelectionModel()::select);
        comboMonth.getSelectionModel().select(Integer.valueOf(budget.month() == null ? WHOLE_YEAR : budget.month()));
        txtAmount.setText(budget.amount().toPlainString());
        txtNotes.setText(budget.notes());
        editingLabel.setText(LanguageManager.getInstance().getString("expense.budget.editing",
                budget.headingPath(), periodOf(budget)));
        txtAmount.requestFocus();
    }

    /** A budget holds no money and nothing points at it, so it is deleted rather than stopped. */
    private void remove(ExpenseBudget budget) {
        try {
            if (!AllAlerts.confirmDelete()) {
                return;
            }
            budgetService.delete(budget.id());
            afterSaved();
        } catch (Exception e) {
            report(e);
        }
    }

    @Override
    public void resetData() {
        editing = 0;
        txtAmount.clear();
        txtNotes.clear();
        comboMonth.getSelectionModel().selectFirst();
        editingLabel.setText("");
    }

    @Override
    public @NotNull BooleanBinding checkDataToEnableButton() {
        // One binding over everything that must be answered - not a chain of or() calls whose result is
        // computed and thrown away, which is what left the party form's save button asking about the name.
        return Bindings.createBooleanBinding(
                () -> comboYear.getValue() == null
                        || comboHeading.getValue() == null
                        || amount(txtAmount).signum() <= 0,
                comboYear.valueProperty(), comboHeading.valueProperty(), txtAmount.textProperty());
    }

    @Override
    public boolean keepDialogOpenAfterSave() {
        return true;
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-expenses";
    }

    private static BigDecimal amount(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(com.hamza.controlsfx.others.DoubleSetting.parseDoubleOrDefault(value));
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

    private void report(Exception e) {
        AllAlerts.handleError(text("expense.error.operation"), e);
    }

    /**
     * A caption and the control it names, as one child of the {@code FlowPane}.
     * <p>
     * A {@code FlowPane} wraps between any two children, so a loose caption gets separated from its
     * control: opened at 1920x1080 this screen wrapped with "الفترة" ending the first line and its combo
     * starting the second, which reads as though it labelled the heading combo before it.
     */
    private static HBox field(String captionKey, javafx.scene.Node control) {
        HBox pair = new HBox(6, caption(captionKey), control);
        pair.setAlignment(Pos.CENTER_LEFT);
        return pair;
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
