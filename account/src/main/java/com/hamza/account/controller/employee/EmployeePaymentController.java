package com.hamza.account.controller.employee;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.EmployeeCashPurpose;
import com.hamza.account.features.employee.EmployeePayment;
import com.hamza.account.features.employee.EmployeePaymentService;
import com.hamza.account.features.employee.statement.EmployeeStatementService;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.features.events.TreasuriesChanged;
import com.hamza.account.model.domain.Expenses;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.ExpensesService;
import com.hamza.account.service.TreasuryService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
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
 * Paying an employee out of a till.
 * <p>
 * The row it writes is an ordinary {@code expenses_details} row with this employee on it, so it
 * reaches the treasury, the shift journal and the period lock through the paths those rules already
 * live in. What this screen adds is the one thing the expenses screen cannot say: <b>what the
 * payment was for</b> — a salary, an advance, a bonus handed over, a settlement.
 * <p>
 * <b>The balance is shown before the amount is typed, not after it is saved.</b> Paying an advance
 * to somebody already a thousand in debt is a decision, and the figure that informs it belongs on
 * the screen where the decision is made. It is held as a {@code BigDecimal} and written out with
 * {@code Columns.money}: a read-only amount must never carry the number formatter, whose converter
 * round-trips through {@code Double} and prints {@code 1050.0} beside a screen that says
 * {@code 1,050.00}.
 */
@Log4j2
@FxmlPath(pathFile = "employee-payment.fxml")
public class EmployeePaymentController implements AddInterface {

    private final EmployeePaymentService paymentService =
            ServiceRegistry.get(EmployeePaymentService.class);
    private final EmployeeStatementService statementService =
            ServiceRegistry.get(EmployeeStatementService.class);
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    private final ExpensesService expensesService = ServiceRegistry.get(ExpensesService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final int employeeId;
    private final String employeeName;

    private final TextField txtEmployee = new TextField();
    private final TextField txtBalance = new TextField();
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final TextField txtAmount = new TextField();
    private final ComboBox<EmployeeCashPurpose> comboPurpose = new ComboBox<>();
    private final ComboBox<Treasury> comboTreasury = new ComboBox<>();
    private final ComboBox<Expenses> comboHeading = new ComboBox<>();
    private final TextField txtNotes = new TextField();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    public EmployeePaymentController(int employeeId, String employeeName) {
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
        // No number formatter on a box the screen writes into - see this class's javadoc.
        txtBalance.getStyleClass().add("app-readonly-amount");

        dateAction(date);
        setTextFormatter(txtAmount);
        txtNotes.setPromptText(text("column.notes"));

        comboPurpose.getItems().setAll(EmployeeCashPurpose.values());
        comboPurpose.setConverter(converter(purpose -> purpose == null
                ? "" : text(purpose.messageKey())));
        comboPurpose.getSelectionModel().select(EmployeeCashPurpose.SALARY);

        comboTreasury.setConverter(converter(treasury -> treasury == null ? "" : treasury.getName()));
        comboHeading.setConverter(converter(heading -> heading == null ? "" : heading.getName()));

        comboPurpose.setMaxWidth(Double.MAX_VALUE);
        comboTreasury.setMaxWidth(Double.MAX_VALUE);
        comboHeading.setMaxWidth(Double.MAX_VALUE);
        date.setMaxWidth(Double.MAX_VALUE);

        box.getChildren().setAll(header(), form());

        whenEnterPressed(txtAmount, txtNotes);
        Platform.runLater(txtAmount::requestFocus);
        loadPickers();
    }

    private HBox header() {
        Label title = new Label(text("employee.pay.title"));
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
        values.setPrefWidth(240);
        values.setHgrow(Priority.SOMETIMES);
        grid.getColumnConstraints().addAll(captions, values, captions, values);

        grid.add(label("employees"), 0, 0);
        grid.add(txtEmployee, 1, 0);
        grid.add(label("employee.statement.closing"), 2, 0);
        grid.add(txtBalance, 3, 0);

        grid.add(label("date"), 0, 1);
        grid.add(date, 1, 1);
        grid.add(label("employee.pay.amount"), 2, 1);
        grid.add(txtAmount, 3, 1);

        grid.add(label("employee.pay.purpose"), 0, 2);
        grid.add(comboPurpose, 1, 2);
        grid.add(label("employee.pay.treasury"), 2, 2);
        grid.add(comboTreasury, 3, 2);

        grid.add(label("employee.pay.heading"), 0, 3);
        grid.add(comboHeading, 1, 3);
        grid.add(label("column.notes"), 2, 3);
        grid.add(txtNotes, 3, 3);

        VBox card = new VBox(10, grid);
        card.getStyleClass().addAll("app-card", "party-form-card");
        return card;
    }

    private static Label label(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    /**
     * The tills and the headings, and the employee's balance.
     * <p>
     * Only tills still open are offered - a treasury is closed rather than deleted, and paying
     * into one that is closed is not a thing anybody means to do. The headings are offered whole
     * and with <b>no default</b>: mapping a purpose to a heading id in code would be the constant
     * this module spent phase A removing.
     */
    private void loadPickers() {
        try {
            comboTreasury.setItems(FXCollections.observableArrayList(
                    treasuryService.getActiveTreasuryModelList()));
            comboTreasury.getSelectionModel().selectFirst();
            comboHeading.setItems(FXCollections.observableArrayList(expensesService.headings()));
            txtBalance.setText(Columns.money(statementService.currentBalance(employeeId)));
        } catch (Exception e) {
            AllAlerts.handleError(text("employee.error.operation"), asException(e));
        }
    }

    /** Must answer exactly 1, or the dialog treats a saved payment as a failure and stays open. */
    @Override
    public int insertData() throws Exception {
        Treasury treasury = comboTreasury.getValue();
        Expenses heading = comboHeading.getValue();
        EmployeePayment payment = EmployeePayment.parse(employeeId, date.getValue(),
                amount(txtAmount), comboPurpose.getValue(),
                treasury == null ? 0 : treasury.getId(),
                heading == null ? 0 : heading.getId(),
                txtNotes.getText());
        paymentService.pay(payment);
        return 1;
    }

    @Override
    public void afterSaved() {
        if (eventBus != null) {
            eventBus.publish(new EmployeesChanged());
            // The till is lighter by the amount, and the treasury screens read a derived balance -
            // so they are told, exactly as the expenses screen tells them.
            eventBus.publish(new TreasuriesChanged());
        }
    }

    @Override
    public void selectData() {
        // Nothing to open: a payment is recorded, never re-opened. A recorded movement is
        // corrected with an opposing one - docs/employees-plan.md §11.
    }

    @Override
    public void resetData() {
        txtAmount.setText("0");
        txtNotes.clear();
        date.setValue(LocalDate.now());
        comboPurpose.getSelectionModel().select(EmployeeCashPurpose.SALARY);
    }

    @Override
    public @org.jetbrains.annotations.NotNull BooleanBinding checkDataToEnableButton() {
        // One binding built from the four fields that must be answered, rather than a chain of
        // or() calls whose result is thrown away - which is what left AddNameController's save
        // button asking about the name alone.
        return Bindings.createBooleanBinding(
                () -> date.getValue() == null
                        || comboPurpose.getValue() == null
                        || comboTreasury.getValue() == null
                        || comboHeading.getValue() == null
                        || amount(txtAmount).signum() <= 0,
                date.valueProperty(), comboPurpose.valueProperty(), comboTreasury.valueProperty(),
                comboHeading.valueProperty(), txtAmount.textProperty());
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
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

    private static Exception asException(Throwable e) {
        return e instanceof Exception exception ? exception : new RuntimeException(e);
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
