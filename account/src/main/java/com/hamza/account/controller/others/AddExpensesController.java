package com.hamza.account.controller.others;

import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.ExpensesDetails;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.service.ExpensesDetailsService;
import com.hamza.account.service.ExpensesService;
import com.hamza.account.session.ShiftContext;
import com.hamza.account.type.ExpensesType;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.hamza.account.type.TypeList.expensesTypeList;

@Log4j2
@FxmlPath(pathFile = "addExpenses.fxml")
public class AddExpensesController implements AddInterface {

    private final int codeId;
    private final Publisher<String> publisher;
    private final ExpensesService expensesService = ServiceRegistry.get(ExpensesService.class);
    private final ExpensesDetailsService expensesDetailsService = ServiceRegistry.get(ExpensesDetailsService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    public TextArea txtNotes;
    @FXML
    private DatePicker date;
    @FXML
    private ComboBox<String> comboType, comboName;
    @FXML
    private Label labelCode, labelName, labelType, labelDate, labelAmount, labelNotes;
    @FXML
    private TextField txtCode, txtAmount;

    public AddExpensesController(int codeId, Publisher<String> publisher) {
        this.codeId = codeId;
        this.publisher = publisher;
    }

    @FXML
    public void initialize() {
        otherSetting();
        resetData();
        selectData();
    }

    @Override
    public void otherSetting() {
        labelCode.setText(Setting_Language.WORD_CODE);
        labelName.setText(Setting_Language.WORD_NAME);
        labelType.setText(Setting_Language.WORD_TYPE);
        labelDate.setText(Setting_Language.WORD_DATE);
        labelAmount.setText(Setting_Language.THE_AMOUNT);
        labelNotes.setText(Setting_Language.NOTES);

        comboName.setPromptText(Setting_Language.WORD_NAME);
        comboType.setPromptText(Setting_Language.WORD_TYPE);

        Utils.setTextFormatter(txtAmount);
        Platform.runLater(() -> txtAmount.requestFocus());

        // date setting
        DateSetting.dateAction(date);

        // combo setting
        comboType.setItems(FXCollections.observableArrayList(expensesTypeList));

        // txt disable for salary
        BooleanBinding equalTo = comboType.valueProperty().isEqualTo(ExpensesType.SALARIES.getType())
                .or(comboType.valueProperty().isEqualTo(ExpensesType.PREDECESSOR.getType()));

        comboName.disableProperty().bind(equalTo.not());
        comboName.setItems(FXCollections.observableArrayList(employeesList()));
        comboName.valueProperty().addListener((observableValue, s, t1) -> {
            Employees dataByString = getDataByString();
            txtAmount.setText(String.valueOf(dataByString.getSalary()));
        });

//        comboType.valueProperty().addListener((observableValue, s, t1) -> txtAmount.clear());
    }

    @Override
    public int insertData() throws DaoException {
        if (!ShiftContext.requireOpenShift()) {
            return 0;
        }

        ExpensesDetails expensesDetails = new ExpensesDetails();
        ExpensesType byType = ExpensesType.fromType(comboType.getSelectionModel().getSelectedItem());
        if (byType == null) throw new DaoException("خطا فى نوع المصروف");

        int id = byType.getId();
        expensesDetails.setExpenses(expensesService.fetchExpenseById(id));
        expensesDetails.setLocalDate(date.getValue());
        expensesDetails.setAmount(Double.parseDouble(txtAmount.getText()));
        expensesDetails.setNotes(txtNotes.getText());
        expensesDetails.setTreasuryModel(new Treasury(1));

        // add employee data
        if (byType.equals(ExpensesType.PREDECESSOR) || byType.equals(ExpensesType.SALARIES)) {
            if (comboName.getSelectionModel().isEmpty()) {
                comboName.requestFocus();
                throw new DaoException("من فضلك حدد اسم الموظف");
            }
        }

        Employees employees = new Employees(0);
        expensesDetails.setEmployees(employees);
        if (!comboName.isDisable())
            employees = getDataByString();

        expensesDetails.setEmployees(employees);
        if (codeId > 0) {
            expensesDetails.setId(codeId);
            return expensesDetailsService.update(expensesDetails);
        }
        return expensesDetailsService.insert(expensesDetails);
    }

    @Override
    public void afterSaved() {
        publisher.notifyObservers();
        resetData();
    }

    @Override
    public void selectData() {
        try {
            if (codeId > 0) {
                ExpensesDetails expensesDetails = expensesDetailsService.getExpensesDetailsById(codeId);
                txtCode.setText(String.valueOf(expensesDetails.getId()));
                txtAmount.setText(String.valueOf(expensesDetails.getAmount()));
                txtNotes.setText(expensesDetails.getNotes());
                comboType.getSelectionModel().select(expensesDetails.getExpenses().getName());
                if (expensesDetails.getEmployees().getId() != 0) {
                    comboName.getSelectionModel().select(expensesDetails.getEmployees().getName());
                }
            }
        } catch (DaoException e) {
            AllAlerts.showExceptionDialog(e);
            log.error(e.getMessage(), e.getCause());
        }
    }

    @Override
    public void resetData() {
        txtCode.setText(Setting_Language.generate);
        txtNotes.clear();
        Utils.clearAll(txtAmount);
    }

    @NotNull
    @Override
    public BooleanBinding checkDataToEnableButton() {
        BooleanBinding binding = (txtAmount.textProperty().isEmpty())
                .or(txtAmount.textProperty().lessThanOrEqualTo("0.0"))
                .or(comboType.getSelectionModel().selectedItemProperty().isNull());

        // check if name is show
        if (!comboName.isDisable()) binding.or(comboName.getSelectionModel().selectedItemProperty().isNull());
        return binding;
    }

    private List<String> employeesList() {
        try {
            return employeeService.getEmployeeNames();
        } catch (DaoException e) {
            log.error(e.getMessage());
            return List.of();
        }
    }

    private Employees getDataByString() {
        try {
            return employeeService.getDelegateByName(comboName.getSelectionModel().getSelectedItem());
        } catch (DaoException e) {
            log.error(e.getMessage());
            return new Employees(1);
        }
    }
}
