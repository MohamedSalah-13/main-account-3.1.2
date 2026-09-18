package com.hamza.account.controller.expense;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.users.ShiftCorrectionReasonPrompt;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.account.features.events.TreasuriesChanged;
import com.hamza.account.features.expense.ExpenseEntry;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingService;
import com.hamza.account.features.expense.ExpenseRow;
import com.hamza.account.features.expense.ExpenseService;
import com.hamza.account.features.expense.recurring.ExpenseRecurringDue;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.treasury.DefaultTreasury;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.hamza.controlsfx.others.DateSetting.dateAction;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * One expense, entered or corrected.
 * <p>
 * <b>What it replaces offered six headings and knew about employees.</b> {@code AddExpensesController}
 * filled its heading combo from {@code ExpensesType} - six constants, so the wallet-fee heading was not
 * on it and nothing added since would be - and turned a choice of "مرتبات" or "سلف" into an employee
 * picker that proposed the salary the employee was <i>hired</i> at. An advance entered there reached
 * the employee's account as a salary. This screen names no employee: paying one is the employee
 * payment screen's (docs/expenses-plan.md ق-٥).
 * <p>
 * Three things it does on purpose:
 * <ul>
 *   <li><b>A new expense keeps the dialog open</b> with the date, heading and till still chosen, so a
 *       stack of receipts is entered one amount after another. A correction closes.</li>
 *   <li><b>A payment larger than the till is a question, not a refusal</b> (م-١): yesterday's bills are
 *       entered before yesterday's takings every morning, and refusing would stop the entry that makes
 *       the balance right.</li>
 *   <li><b>The balance is written, not formatted.</b> A read-only amount never carries the number
 *       formatter, whose converter round-trips through {@code Double} and prints {@code 1050.0} beside a
 *       screen that says {@code 1,050.00}.</li>
 * </ul>
 */
@Log4j2
@FxmlPath(pathFile = "expense-entry.fxml")
public class ExpenseEntryController implements AddInterface {

    private final ExpenseService expenseService = ServiceRegistry.get(ExpenseService.class);
    private final ExpenseHeadingService headingService = ServiceRegistry.get(ExpenseHeadingService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final int expenseId;

    private final TextField txtCode = new TextField();
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final ComboBox<ExpenseHeading> comboHeading = new ComboBox<>();
    private final ComboBox<TreasuryBalanceSummary> comboTreasury = new ComboBox<>();
    private final Label balance = new Label();
    private final TextField txtAmount = new TextField();
    private final TextField txtPayee = new TextField();
    private final TextField txtReference = new TextField();
    private final TextField txtNotes = new TextField();
    private final Label employeeNote = new Label();

    private final ContextMenu payeeSuggestions = new ContextMenu();
    private final PauseTransition payeeDelay = new PauseTransition(Duration.millis(250));
    private int payeeGeneration;
    private boolean choosingPayee;

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    /** The stored row being corrected, or {@code null} for a new expense. */
    private ExpenseRow stored;

    /** The template this expense is being recorded from, or {@code null} when it is hand-entered. */
    private final ExpenseRecurringDue due;

    public ExpenseEntryController(int expenseId) {
        this(expenseId, null);
    }

    private ExpenseEntryController(int expenseId, ExpenseRecurringDue due) {
        this.expenseId = expenseId;
        this.due = due;
    }

    /**
     * A new expense filled in from a recurring template, for the reminder's "record now".
     * <p>
     * <b>The saved row carries the template's id</b>, which is what stops that period reminding again -
     * "already recorded" is a row of {@code expenses_details} pointing at the template, never a row that
     * merely looks similar. And the date is the day it fell due, not today, because the period the row
     * lands in is decided by its date: a February rent entered on 5 March with March's date would answer
     * March's reminder and leave February's standing.
     */
    public static ExpenseEntryController from(ExpenseRecurringDue due) {
        return new ExpenseEntryController(0, due);
    }

    @FXML
    public void initialize() {
        otherSetting();
        selectData();
    }

    @Override
    public void otherSetting() {
        stackPane.getStyleClass().add("screen-expenses");

        txtCode.setEditable(false);
        txtCode.setText(text("item.code.generate"));
        dateAction(date);
        setTextFormatter(txtAmount);
        balance.getStyleClass().add("app-readonly-amount");
        employeeNote.getStyleClass().add("form-hint");
        employeeNote.setWrapText(true);
        employeeNote.setVisible(false);
        employeeNote.setManaged(false);

        txtPayee.setPromptText(text("expense.column.payee"));
        txtReference.setPromptText(text("expense.column.reference"));
        txtNotes.setPromptText(text("column.notes"));

        comboHeading.setConverter(converter(heading -> heading == null ? "" : heading.path()));
        comboTreasury.setConverter(converter(treasury -> treasury == null ? "" : treasury.name()));
        comboTreasury.valueProperty().addListener((observable, old, treasury) -> showBalance(treasury));
        for (var control : List.of(comboHeading, comboTreasury, date)) {
            control.setMaxWidth(Double.MAX_VALUE);
        }

        payeeDelay.setOnFinished(event -> suggestPayees());
        txtPayee.textProperty().addListener((observable, old, value) -> {
            if (!choosingPayee && txtPayee.isFocused()) {
                payeeDelay.playFromStart();
            }
        });
        txtPayee.focusedProperty().addListener((observable, old, focused) -> {
            if (!focused) {
                payeeSuggestions.hide();
            }
        });

        box.getChildren().setAll(header(), form());

        // The order a receipt is read in: when, what for, which till, how much, to whom, its number, a note.
        whenEnterPressed(date, comboHeading, comboTreasury, txtAmount, txtPayee, txtReference, txtNotes);
        loadPickers();
        Platform.runLater(txtAmount::requestFocus);
    }

    private HBox header() {
        Label title = new Label(text(expenseId > 0 ? "expense.edit.title" : "expense.add.title"));
        title.getStyleClass().add("party-screen-title");
        HBox bar = new HBox(12, AppIcon.TREASURY_CASH.graphic(24), title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private VBox form() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        ColumnConstraints captions = new ColumnConstraints();
        captions.setMinWidth(110);
        ColumnConstraints values = new ColumnConstraints();
        values.setPrefWidth(260);
        values.setHgrow(Priority.SOMETIMES);
        grid.getColumnConstraints().addAll(captions, values, captions, values);

        grid.add(label("code"), 0, 0);
        grid.add(txtCode, 1, 0);
        grid.add(label("date"), 2, 0);
        grid.add(date, 3, 0);

        grid.add(label("expense.column.heading"), 0, 1);
        grid.add(comboHeading, 1, 1);
        grid.add(label("invoice.treasury"), 2, 1);
        grid.add(comboTreasury, 3, 1);

        grid.add(label("column.amount"), 0, 2);
        grid.add(txtAmount, 1, 2);
        grid.add(label("expense.balance"), 2, 2);
        grid.add(balance, 3, 2);

        grid.add(label("expense.column.payee"), 0, 3);
        grid.add(txtPayee, 1, 3);
        grid.add(label("expense.column.reference"), 2, 3);
        grid.add(txtReference, 3, 3);

        grid.add(label("column.notes"), 0, 4);
        grid.add(txtNotes, 1, 4, 3, 1);

        VBox card = new VBox(10, employeeNote, grid);
        card.getStyleClass().addAll("app-card", "party-form-card");
        return card;
    }

    /**
     * The tills with their balances, and the headings the expenses screen may file under. The main
     * treasury is preselected - what every expense was charged to before the till became a choice.
     */
    private void loadPickers() {
        try {
            comboTreasury.setItems(FXCollections.observableArrayList(expenseService.treasuries()));
            comboTreasury.getItems().stream().filter(treasury -> treasury.id() == DefaultTreasury.ID)
                    .findFirst().ifPresentOrElse(comboTreasury.getSelectionModel()::select,
                            () -> comboTreasury.getSelectionModel().selectFirst());
            comboHeading.setItems(FXCollections.observableArrayList(headingService.forExpenses()));
            prefillFromTemplate();
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * What the template already knows, so the person recording it types only what it cannot know - the
     * reference number on the receipt, and the amount when this month's bill differs from the standing one.
     */
    private void prefillFromTemplate() {
        if (due == null) {
            return;
        }
        date.setValue(due.dueOn());
        comboHeading.getItems().stream()
                .filter(heading -> heading.id() == due.template().headingId()).findFirst()
                .ifPresent(comboHeading.getSelectionModel()::select);
        selectTreasury(due.template().treasuryId(), due.template().treasuryName());
        txtAmount.setText(due.template().amount().toPlainString());
        txtPayee.setText(due.template().payee());
        txtNotes.setText(due.template().notes());
    }

    @Override
    public void selectData() {
        if (expenseId <= 0) {
            return;
        }
        try {
            stored = expenseService.find(expenseId);
            if (stored == null) {
                report(new com.hamza.controlsfx.error.UserValidationException("expense.error.not.found"));
                return;
            }
            txtCode.setText(String.valueOf(stored.id()));
            date.setValue(stored.date());
            txtAmount.setText(stored.amount().toPlainString());
            txtPayee.setText(stored.payee());
            txtReference.setText(stored.referenceNo());
            txtNotes.setText(stored.notes());

            // A salary is corrected under a heading employees are paid under, and it keeps its employee -
            // the update does not name that column. The headings on offer follow the row.
            List<ExpenseHeading> offered = new ArrayList<>(stored.paidToEmployee()
                    ? headingService.forEmployeePayments() : headingService.forExpenses());
            if (offered.stream().noneMatch(heading -> heading.id() == stored.headingId())) {
                ExpenseHeading own = headingService.find(stored.headingId());
                if (own != null) {
                    offered.add(0, own);
                }
            }
            comboHeading.setItems(FXCollections.observableArrayList(offered));
            offered.stream().filter(heading -> heading.id() == stored.headingId()).findFirst()
                    .ifPresent(comboHeading.getSelectionModel()::select);

            selectTreasury(stored.treasuryId(), stored.treasuryName());
            if (stored.paidToEmployee()) {
                employeeNote.setText(LanguageManager.getInstance()
                        .getString("expense.edit.employee.note", stored.employeeName()));
                employeeNote.setVisible(true);
                employeeNote.setManaged(true);
            }
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * The row's own till, added back for this one row if it has since been closed: the picker offers open
     * tills only, and without this the correction would silently move the expense to whichever was first.
     */
    private void selectTreasury(int treasuryId, String name) {
        TreasuryBalanceSummary own = comboTreasury.getItems().stream()
                .filter(treasury -> treasury.id() == treasuryId).findFirst().orElse(null);
        if (own == null) {
            own = new TreasuryBalanceSummary(treasuryId, name == null ? "" : name, null, false, 0, null,
                    null, null, null, null);
            comboTreasury.getItems().add(own);
        }
        comboTreasury.getSelectionModel().select(own);
    }

    private void showBalance(TreasuryBalanceSummary treasury) {
        balance.setText(treasury == null || treasury.balance() == null ? "—" : Columns.money(treasury.balance()));
    }

    /** Must answer exactly 1, or the dialog treats a saved expense as a failure and stays open. */
    @Override
    public int insertData() throws Exception {
        ExpenseHeading heading = comboHeading.getValue();
        TreasuryBalanceSummary treasury = comboTreasury.getValue();
        ExpenseEntry entry = ExpenseEntry.parse(expenseId, date.getValue(),
                heading == null ? 0 : heading.id(), treasury == null ? 0 : treasury.id(),
                amount(txtAmount), txtPayee.getText(), txtReference.getText(), txtNotes.getText());

        if (!confirmIfShort(entry, treasury)) {
            return 0;
        }
        if (entry.isNew()) {
            expenseService.create(entry, due == null ? null : due.template().id());
            return 1;
        }
        var reason = ShiftCorrectionReasonPrompt.forUpdate();
        if (reason.isEmpty()) {
            return 0;
        }
        return expenseService.update(entry, reason.get());
    }

    /** Decision م-١: a question, answered by the person paying. Nothing downstream refuses it. */
    private boolean confirmIfShort(ExpenseEntry entry, TreasuryBalanceSummary treasury) throws Exception {
        BigDecimal shortfall = expenseService.shortfall(entry.treasuryId(), entry.amount(), expenseId);
        if (shortfall.signum() <= 0) {
            return true;
        }
        String till = treasury == null ? "" : treasury.name();
        return AllAlerts.confirm_all(text("expenses"), LanguageManager.getInstance()
                .getString("expense.confirm.short", till, Columns.money(shortfall)));
    }

    @Override
    public void afterSaved() {
        if (eventBus != null) {
            eventBus.publish(new ExpensesChanged());
            // The till is lighter by the amount, and the treasury screens read a derived balance.
            eventBus.publish(new TreasuriesChanged());
        }
        resetData();
    }

    /**
     * Ready for the next receipt: the date, the heading and the till stay, because a stack of receipts
     * shares them; the amount and what identifies one receipt go. The balance is read again - the
     * expense just saved has moved it.
     */
    @Override
    public void resetData() {
        txtAmount.clear();
        txtPayee.clear();
        txtReference.clear();
        txtNotes.clear();
        TreasuryBalanceSummary chosen = comboTreasury.getValue();
        try {
            comboTreasury.setItems(FXCollections.observableArrayList(expenseService.treasuries()));
            if (chosen != null) {
                comboTreasury.getItems().stream().filter(treasury -> treasury.id() == chosen.id()).findFirst()
                        .ifPresent(comboTreasury.getSelectionModel()::select);
            }
        } catch (Exception e) {
            log.warn("Could not refresh the treasury balances after a save", e);
        }
        Platform.runLater(txtAmount::requestFocus);
    }

    @Override
    public @NotNull BooleanBinding checkDataToEnableButton() {
        // One binding over every field that must be answered, rather than a chain of or() calls whose
        // result is thrown away - which is what left the old screen's save button ignoring the employee.
        return Bindings.createBooleanBinding(
                () -> date.getValue() == null
                        || comboHeading.getValue() == null
                        || comboTreasury.getValue() == null
                        || amount(txtAmount).signum() <= 0,
                date.valueProperty(), comboHeading.valueProperty(), comboTreasury.valueProperty(),
                txtAmount.textProperty());
    }

    /**
     * A new expense keeps the dialog for the next receipt; a correction closes it - and so does one
     * recorded from a template, which answers one reminder and is not the start of a stack of receipts.
     */
    @Override
    public boolean keepDialogOpenAfterSave() {
        return expenseId <= 0 && due == null;
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-expenses";
    }

    // ---- the payee suggestions -----------------------------------------------------------

    /** Payees already written, starting with what is typed. Off the JavaFX thread, newest query wins. */
    private void suggestPayees() {
        String prefix = txtPayee.getText();
        if (prefix == null || prefix.isBlank()) {
            payeeSuggestions.hide();
            return;
        }
        int token = ++payeeGeneration;
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return expenseService.payeeSuggestions(prefix);
            }
        };
        task.setOnSucceeded(event -> {
            if (token == payeeGeneration && txtPayee.isFocused()) {
                showPayees(task.getValue());
            }
        });
        task.setOnFailed(event -> log.warn("Payee suggestions failed", task.getException()));
        Thread thread = new Thread(task, "expense-payees");
        thread.setDaemon(true);
        thread.start();
    }

    private void showPayees(List<String> payees) {
        List<String> offered = payees.stream().filter(payee -> !payee.equals(txtPayee.getText())).toList();
        if (offered.isEmpty()) {
            payeeSuggestions.hide();
            return;
        }
        List<CustomMenuItem> items = new ArrayList<>();
        for (String payee : offered) {
            Label entry = new Label(payee);
            CustomMenuItem item = new CustomMenuItem(entry, true);
            item.setOnAction(event -> {
                choosingPayee = true;
                try {
                    txtPayee.setText(payee);
                    txtPayee.positionCaret(payee.length());
                } finally {
                    choosingPayee = false;
                }
            });
            items.add(item);
        }
        payeeSuggestions.getItems().setAll(items);
        if (!payeeSuggestions.isShowing()) {
            payeeSuggestions.show(txtPayee, Side.BOTTOM, 0, 0);
        }
    }

    // ---- plumbing ------------------------------------------------------------------------

    private static Label label(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
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

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
