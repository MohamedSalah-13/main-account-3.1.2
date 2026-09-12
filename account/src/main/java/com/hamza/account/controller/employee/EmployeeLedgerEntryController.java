package com.hamza.account.controller.employee;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.EmployeeEntryKind;
import com.hamza.account.features.employee.EmployeeLedgerEntry;
import com.hamza.account.features.employee.EmployeeLedgerService;
import com.hamza.account.features.employee.statement.EmployeeStatementService;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Function;

import static com.hamza.controlsfx.others.DateSetting.dateAction;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * Recording what happened to an employee's account without any cash moving: a deduction, a bonus
 * awarded, a month's entitlement, a balance carried in.
 * <p>
 * <b>There is no treasury on this screen, and there cannot be.</b> A row of {@code employee_ledger}
 * carrying cash would be a second writer of money the treasury does not know about — ق-١ of
 * {@code docs/employees-plan.md}, and the reason the table has no treasury column. Cash goes
 * through {@link EmployeePaymentController}, which writes an expense.
 * <p>
 * It follows that <b>this screen does not need an open shift</b>. A deduction takes nothing out of
 * a drawer, and requiring a shift for one would stop a correction being made outside trading
 * hours — which is when corrections are made. The same line {@code CLAUDE.md} draws for a party's
 * debit and credit notes.
 * <p>
 * The direction is the kind's and is shown beside it, so nobody has to remember whether a
 * deduction is typed with a minus: the amount box takes a plain positive figure, and the label
 * under it says which way the balance will move.
 */
@Log4j2
@FxmlPath(pathFile = "employee-ledger-entry.fxml")
public class EmployeeLedgerEntryController implements AddInterface {

    private final EmployeeLedgerService ledgerService =
            ServiceRegistry.get(EmployeeLedgerService.class);
    private final EmployeeStatementService statementService =
            ServiceRegistry.get(EmployeeStatementService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final int employeeId;
    private final String employeeName;

    private final TextField txtEmployee = new TextField();
    private final TextField txtBalance = new TextField();
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final ComboBox<EmployeeEntryKind> comboKind = new ComboBox<>();
    private final TextField txtAmount = new TextField();
    private final TextField txtNotes = new TextField();
    private final Label directionNote = new Label();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    public EmployeeLedgerEntryController(int employeeId, String employeeName) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
    }

    @FXML
    public void initialize() {
        otherSetting();
    }

    @Override
    public void otherSetting() {
        stackPane.getStyleClass().add("screen-employees");

        txtEmployee.setEditable(false);
        txtEmployee.setText(employeeName);
        txtBalance.setEditable(false);
        txtBalance.getStyleClass().add("app-readonly-amount");

        dateAction(date);
        setTextFormatter(txtAmount);
        txtNotes.setPromptText(text("column.notes"));

        // Only what a person may record: a commission is approved by a run, by definition, so
        // offering it here would be offering a figure nothing approved.
        comboKind.getItems().setAll(EmployeeEntryKind.enteredByHand());
        comboKind.setConverter(converter(kind -> kind == null ? "" : text(kind.messageKey())));
        comboKind.getSelectionModel().select(EmployeeEntryKind.DEDUCTION);
        comboKind.valueProperty().addListener((source, was, now) -> showDirection());
        comboKind.setMaxWidth(Double.MAX_VALUE);
        date.setMaxWidth(Double.MAX_VALUE);

        directionNote.getStyleClass().add("form-hint");
        directionNote.setWrapText(true);
        showDirection();

        box.getChildren().setAll(header(), form());
        whenEnterPressed(txtAmount, txtNotes);
        Platform.runLater(txtAmount::requestFocus);
        loadBalance();
    }

    private HBox header() {
        Label title = new Label(text("employee.entry.title"));
        title.getStyleClass().add("party-screen-title");
        HBox bar = new HBox(12, AppIcon.EDIT.graphic(24), title);
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
        values.setPrefWidth(240);
        values.setHgrow(Priority.SOMETIMES);
        grid.getColumnConstraints().addAll(captions, values, captions, values);

        grid.add(label("employees"), 0, 0);
        grid.add(txtEmployee, 1, 0);
        grid.add(label("employee.statement.closing"), 2, 0);
        grid.add(txtBalance, 3, 0);

        grid.add(label("date"), 0, 1);
        grid.add(date, 1, 1);
        grid.add(label("employee.entry.kind"), 2, 1);
        grid.add(comboKind, 3, 1);

        grid.add(label("employee.entry.amount"), 0, 2);
        grid.add(txtAmount, 1, 2);
        grid.add(label("column.notes"), 2, 2);
        grid.add(txtNotes, 3, 2);

        grid.add(directionNote, 1, 3, 3, 1);

        VBox card = new VBox(10, grid);
        card.getStyleClass().addAll("app-card", "party-form-card");
        return card;
    }

    /**
     * Says which way the chosen kind moves the balance, before the save rather than after it.
     * <p>
     * The amount box takes an unsigned figure - the direction is the kind's and the database
     * refuses anything else - so this is the only thing on the screen that answers "will this make
     * the business owe more, or less".
     */
    private void showDirection() {
        EmployeeEntryKind kind = comboKind.getValue();
        directionNote.setText(kind == null ? ""
                : text(kind.isCredit() ? "employee.entry.direction.credit"
                : "employee.entry.direction.debit"));
    }

    private void loadBalance() {
        try {
            txtBalance.setText(Columns.money(statementService.currentBalance(employeeId)));
        } catch (Exception e) {
            AllAlerts.handleError(text("employee.error.operation"),
                    e instanceof Exception exception ? exception : new RuntimeException(e));
        }
    }

    /** Must answer exactly 1, or the dialog reports a saved movement as a failure. */
    @Override
    public int insertData() throws Exception {
        EmployeeLedgerEntry entry = EmployeeLedgerEntry.parse(employeeId, date.getValue(),
                comboKind.getValue(), amount(txtAmount), txtNotes.getText());
        ledgerService.record(entry);
        return 1;
    }

    @Override
    public void afterSaved() {
        if (eventBus != null) {
            // No TreasuriesChanged here, and that is the whole point of this screen: no till moved.
            eventBus.publish(new EmployeesChanged());
        }
    }

    @Override
    public void selectData() {
        // A recorded movement is corrected with an opposing one, never re-opened - §11.
    }

    @Override
    public void resetData() {
        txtAmount.setText("0");
        txtNotes.clear();
        date.setValue(LocalDate.now());
        loadBalance();
    }

    @Override
    public @org.jetbrains.annotations.NotNull BooleanBinding checkDataToEnableButton() {
        return Bindings.createBooleanBinding(
                () -> date.getValue() == null || comboKind.getValue() == null
                        || amount(txtAmount).signum() <= 0,
                date.valueProperty(), comboKind.valueProperty(), txtAmount.textProperty());
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }

    private static Label label(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static BigDecimal amount(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(com.hamza.controlsfx.others.DoubleSetting
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

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
