package com.hamza.account.controller.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.EmployeeCompensation;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.features.employee.SalaryKind;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static com.hamza.controlsfx.others.DateSetting.dateAction;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * What one employee has been paid, and from when.
 * <p>
 * This screen is the road {@code SalaryChangeGuard} points at. Before it, {@code employees.salary}
 * was one number with no date on it: raising a salary in September made every calculation of August
 * read the new figure, today and in a year, with nothing recording that it had moved. The same
 * defect as {@code custom.first_balance}, answered the same way - the figure gets a date, and the
 * old column keeps one fixed meaning.
 * <p>
 * A row dated in the future is legitimate and is the ordinary case: a raise agreed in March to start
 * in April is entered the day it is agreed, and {@code employee_current_compensation} begins reading
 * it on the day. Nobody has to remember.
 */
@Log4j2
@FxmlPath(pathFile = "employee-salary.fxml")
public class EmployeeSalaryController implements AddInterface {

    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final int employeeId;
    private final String employeeName;

    private final TableView<EmployeeCompensation> table = new TableView<>();
    private final ContentSizedColumns<EmployeeCompensation> columnSizing = new ContentSizedColumns<>();
    private final DatePicker effectiveFrom = new DatePicker(LocalDate.now());
    private final ComboBox<SalaryKind> comboKind = new ComboBox<>();
    private final TextField txtRate = new TextField();
    private final TextField txtNotes = new TextField();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    public EmployeeSalaryController(int employeeId, String employeeName) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
    }

    @FXML
    public void initialize() {
        otherSetting();
        selectData();
    }

    @Override
    public void otherSetting() {
        stackPane.getStyleClass().add("screen-employees");

        comboKind.getItems().setAll(SalaryKind.values());
        comboKind.setConverter(converter(kind -> kind == null ? "" : text(kind.messageKey())));
        comboKind.getSelectionModel().select(SalaryKind.MONTHLY);
        comboKind.setMaxWidth(Double.MAX_VALUE);

        dateAction(effectiveFrom);
        setTextFormatter(txtRate);
        txtRate.setPromptText(text("employee.column.rate"));
        txtNotes.setPromptText(text("column.notes"));
        HBox.setHgrow(txtNotes, Priority.ALWAYS);
        whenEnterPressed(txtRate, txtNotes);

        buildTable();
        box.getChildren().setAll(header(), entryBar(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private HBox header() {
        Label title = new Label(text("employee.salary.history") + " - " + employeeName);
        title.getStyleClass().add("party-screen-title");
        HBox bar = new HBox(12, AppIcon.TREASURY_CASH.graphic(24), title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private VBox entryBar() {
        HBox row = new HBox(8, caption("employee.salary.effective.from"), effectiveFrom,
                caption("employee.column.salary.kind"), comboKind,
                caption("employee.column.rate"), txtRate, txtNotes);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(8, row);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    private void buildTable() {
        table.setId("employee-salary-history");
        table.setPlaceholder(new Label(text("employee.salary.empty")));
        table.getColumns().setAll(List.of(
                RowActionsColumn.of("employee.column.actions", RowAction.permitted(List.of(
                        RowAction.of("delete", AppIcon.DELETE, "app-neutral-button",
                                AppPermissions.EMPLOYEE_SALARY_CHANGE, this::remove)))),
                Columns.date("employee.salary.effective.from", EmployeeCompensation::effectiveFrom),
                Columns.text("employee.column.salary.kind",
                        row -> text(row.salaryKind().messageKey())),
                Columns.money("employee.column.rate", EmployeeCompensation::rate),
                Columns.text("column.notes", EmployeeCompensation::notes)));
        columnSizing.install(table);
    }

    @Override
    public int insertData() throws Exception {
        employeeService.changeSalary(employeeId, effectiveFrom.getValue(), comboKind.getValue(),
                amount(txtRate), blankToNull(txtNotes.getText()));
        return 1;
    }

    @Override
    public void afterSaved() {
        if (eventBus != null) {
            eventBus.publish(new EmployeesChanged());
        }
    }

    @Override
    public void selectData() {
        try {
            table.setItems(FXCollections.observableArrayList(
                    employeeService.salaryHistory(employeeId)));
            columnSizing.layout(table);
        } catch (Exception e) {
            AllAlerts.handleError(text("employee.error.operation"),
                    e instanceof Exception exception ? exception : new RuntimeException(e));
        }
    }

    /**
     * Removes one dated change. The earliest is refused by the service, not by this screen: it is
     * what the employee was hired at, and it is the figure {@code employees.salary} mirrors.
     */
    private void remove(EmployeeCompensation row) {
        try {
            if (!AllAlerts.confirmDelete()) {
                return;
            }
            employeeService.removeSalaryChange(employeeId, row.id());
            selectData();
            afterSaved();
        } catch (Exception e) {
            AllAlerts.handleError(text("employee.error.operation"),
                    e instanceof Exception exception ? exception : new RuntimeException(e));
        }
    }

    @Override
    public void resetData() {
        txtRate.setText("0");
        txtNotes.clear();
        effectiveFrom.setValue(LocalDate.now());
    }

    @Override
    public @org.jetbrains.annotations.NotNull BooleanBinding checkDataToEnableButton() {
        return Bindings.createBooleanBinding(
                () -> effectiveFrom.getValue() == null || comboKind.getValue() == null,
                effectiveFrom.valueProperty(), comboKind.valueProperty());
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
        return "screen-employees";
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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
