package com.hamza.account.controller.others;

import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.ExpensesDetails;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.service.ExpensesDetailsService;
import com.hamza.account.service.ExpensesService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.session.ShiftContext;
import com.hamza.account.treasury.DefaultTreasury;
import com.hamza.account.type.ExpensesType;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.controlsfx.observer.EventBus;
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
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final ExpensesService expensesService = ServiceRegistry.get(ExpensesService.class);
    private final ExpensesDetailsService expensesDetailsService = ServiceRegistry.get(ExpensesDetailsService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    public TextArea txtNotes;
    @FXML
    private DatePicker date;
    @FXML
    private ComboBox<String> comboType, comboName, comboTreasury;
    @FXML
    private Label labelCode, labelName, labelType, labelDate, labelAmount, labelNotes, labelTreasury;
    @FXML
    private TextField txtCode, txtAmount;

    public AddExpensesController(int codeId) {
        this.codeId = codeId;
    }

    @FXML
    public void initialize() {
        otherSetting();
        resetData();
        selectData();
    }

    @Override
    public void otherSetting() {
        var lm = LanguageManager.getInstance();
        labelCode.setText(lm.getString("code"));
        labelName.setText(lm.getString("name"));
        labelType.setText(lm.getString("type"));
        labelDate.setText(lm.getString("date"));
        labelAmount.setText(lm.getString("column.amount"));
        labelNotes.setText(lm.getString("column.notes"));
        labelTreasury.setText(lm.getString("invoice.treasury"));

        comboName.setPromptText(lm.getString("name"));
        comboType.setPromptText(lm.getString("type"));
        comboTreasury.setPromptText(lm.getString("invoice.treasury"));
        loadTreasuries();

        Utils.setTextFormatter(txtAmount);
        Platform.runLater(() -> txtAmount.requestFocus());

        // date setting
        DateSetting.dateAction(date);

        // The order the form is actually filled: what it is, who it is for, which
        // till it comes out of, how much, and any note. Declared once - a screen
        // without it leaves the operator reaching for the mouse between every two
        // fields (rule ق-ل9).
        Utils.whenEnterPressed(date, comboType, comboName, comboTreasury, txtAmount, txtNotes);

        // combo setting - expensesTypeList / ExpensesType.getType() are not display
        // text to translate: V1__baseline.sql seeds the expenses table with these
        // exact Arabic names as row data, and selectData() below restores the combo
        // selection by matching expensesDetails.getExpenses().getName() (the DB
        // value) against this same list. Translating either side breaks the match.
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
        if (byType == null) throw new UserValidationException(LanguageManager.getInstance().getString("expenses.error.invalid.type"));

        int id = byType.getId();
        expensesDetails.setExpenses(expensesService.fetchExpenseById(id));
        expensesDetails.setLocalDate(date.getValue());
        expensesDetails.setAmount(Double.parseDouble(txtAmount.getText()));
        expensesDetails.setNotes(txtNotes.getText());
        expensesDetails.setTreasuryModel(selectedTreasury());

        // add employee data
        if (byType.equals(ExpensesType.PREDECESSOR) || byType.equals(ExpensesType.SALARIES)) {
            if (comboName.getSelectionModel().isEmpty()) {
                comboName.requestFocus();
                throw new UserValidationException(LanguageManager.getInstance().getString("expenses.error.select.employee"));
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
        if (eventBus != null) eventBus.publish(new ExpensesChanged());
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
                // The row's own treasury, which may since have been closed and left
                // the picker - selectTreasury puts it back for this one row.
                selectTreasury(expensesDetails.getTreasuryModel());
            }
        } catch (DaoException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("expenses.dialog.save.title"), e);
        }
    }

    @Override
    public void resetData() {
        txtCode.setText(LanguageManager.getInstance().getString("item.code.generate"));
        txtNotes.clear();
        Utils.clearAll(txtAmount);
    }

    @NotNull
    @Override
    public BooleanBinding checkDataToEnableButton() {
        BooleanBinding binding = (txtAmount.textProperty().isEmpty())
                .or(txtAmount.textProperty().lessThanOrEqualTo("0.0"))
                .or(comboType.getSelectionModel().selectedItemProperty().isNull())
                .or(comboTreasury.getSelectionModel().selectedItemProperty().isNull());

        // check if name is show
        if (!comboName.isDisable()) binding.or(comboName.getSelectionModel().selectedItemProperty().isNull());
        return binding;
    }

    /**
     * The tills an expense may be paid out of - the open ones, in the order the
     * other screens present them, with the main treasury preselected.
     * <p>
     * There was no picker here at all: every expense was written against
     * {@code new Treasury(1)} whatever it was actually paid from. That is not a
     * missing convenience - {@code treasury_current_balance} derives each till's
     * balance from the rows filed against it, so an expense paid out of the wallet
     * and filed against the drawer leaves both numbers wrong, and no report can
     * tell afterwards which one it really was.
     */
    private void loadTreasuries() {
        try {
            comboTreasury.setItems(FXCollections.observableArrayList(treasuryService.listTreasuryModelNames()));
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            comboTreasury.setItems(FXCollections.observableArrayList());
            return;
        }
        selectDefaultTreasury();
    }

    /**
     * Preselects the main treasury - what every expense was silently charged to
     * before - falling back to the first open one if it has been closed.
     */
    private void selectDefaultTreasury() {
        try {
            Treasury main = treasuryService.getTreasuryById(DefaultTreasury.ID);
            if (main != null && comboTreasury.getItems().contains(main.getName())) {
                comboTreasury.getSelectionModel().select(main.getName());
                return;
            }
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
        }
        comboTreasury.getSelectionModel().selectFirst();
    }

    /**
     * Shows the treasury a saved expense was filed against, adding it to the list
     * if it has since been closed: the picker offers open treasuries only, and
     * without this, opening such an expense would show whichever one happened to
     * be selected and save it back under that one - the same care
     * {@code Add_AccountController.selectTreasury} takes.
     */
    private void selectTreasury(Treasury treasury) {
        if (treasury == null || treasury.getName() == null || treasury.getName().isBlank()) return;
        if (!comboTreasury.getItems().contains(treasury.getName())) {
            comboTreasury.getItems().add(treasury.getName());
        }
        comboTreasury.getSelectionModel().select(treasury.getName());
    }

    private Treasury selectedTreasury() throws DaoException {
        String name = comboTreasury.getSelectionModel().getSelectedItem();
        Treasury treasury = name == null || name.isBlank() ? null : treasuryService.getTreasuryByName(name);
        if (treasury == null) {
            Platform.runLater(() -> comboTreasury.requestFocus());
            throw new UserValidationException(LanguageManager.getInstance().getString("expenses.error.select.treasury"));
        }
        return treasury;
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
