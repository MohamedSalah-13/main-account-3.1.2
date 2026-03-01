package com.hamza.account.controller.others;

import com.hamza.account.model.domain.Employees;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.type.UsersType;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import static com.hamza.account.type.TypeList.usersTypeList;
import static com.hamza.controlsfx.language.Setting_Language.JOP;
import static com.hamza.controlsfx.language.Setting_Language.generate;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;

@Log4j2
@FxmlPath(pathFile = "addEmployee.fxml")
public class AddEmployeeController implements AddInterface {

    private final Publisher<String> publisherAddEmployee;
    private final int updateData;
    private final EmployeeService employeeService;
    @FXML
    private TextField txtCode, txtName, txtSalary, txtEmail, txtTel;
    @FXML
    private DatePicker birthDate, hireDate;
    @FXML
    private TextArea textAreaAddress;
    @FXML
    private ComboBox<String> comboJob;
    @FXML
    private Label labelCode, labelName, labelSalary, labelEmail, labelTel, labelBirthDate, labelHireDate, labelJop, labelAddress;
    @FXML
    private VBox boxImage;

    public AddEmployeeController(Publisher<String> publisherAddEmployee, int updateData, EmployeeService employeeService) {
        this.employeeService = employeeService;
        this.publisherAddEmployee = publisherAddEmployee;
        this.updateData = updateData;
    }

    @FXML
    public void initialize() {
        otherSetting();
        if (updateData > 0) selectData();
    }

    @Override
    public void otherSetting() {
        // prompt text
        txtCode.setPromptText(Setting_Language.WORD_CODE);
        txtName.setPromptText(Setting_Language.WORD_NAME);
        txtSalary.setPromptText(Setting_Language.salary);
        txtEmail.setPromptText(Setting_Language.E_MAIL);
        txtTel.setPromptText(Setting_Language.WORD_TEL);
        textAreaAddress.setPromptText(Setting_Language.WORD_ADDRESS);
        comboJob.setPromptText(JOP);

        DateSetting.dateAction(birthDate);
        DateSetting.dateAction(hireDate);

        labelCode.setText(Setting_Language.WORD_CODE);
        labelName.setText(Setting_Language.WORD_NAME);
        labelSalary.setText(Setting_Language.salary);
        labelEmail.setText(Setting_Language.E_MAIL);
        labelTel.setText(Setting_Language.WORD_TEL);
        labelBirthDate.setText(Setting_Language.string_birth);
        labelHireDate.setText(Setting_Language.string_hire);
        labelJop.setText(JOP);
        labelAddress.setText(Setting_Language.WORD_ADDRESS);
        txtCode.setText(generate);

        comboJob.getItems().addAll(usersTypeList);

        Platform.runLater(() -> txtName.requestFocus());
        setTextFormatter(txtSalary);
    }

    @Override
    public int insertData() throws Exception {
        double salary = Double.parseDouble(txtSalary.getText());
        UsersType userTypeByType = UsersType.getUserTypeByType(comboJob.getSelectionModel().getSelectedItem());
        return employeeService.updateEmployee(updateData, txtName.getText(), birthDate.getValue(), hireDate.getValue()
                , salary, txtEmail.getText(), txtTel.getText(), textAreaAddress.getText(), userTypeByType);

    }

    @Override
    public void afterSaved() {
        publisherAddEmployee.notifyObservers();
//        paneImage.getImageController().clearImage();
        Utils.clearAll(txtName, txtSalary, txtEmail, txtTel);
    }

    @Override
    public void selectData() {
        if (updateData > 0) {
            try {
                Employees dataById = employeeService.getDelegateById(updateData);
                txtCode.setText(String.valueOf(dataById.getId()));
                txtName.setText(dataById.getName());
                birthDate.setValue(dataById.getBirth_date());
                hireDate.setValue(dataById.getHire_date());
                txtSalary.setText(String.valueOf(dataById.getSalary()));
                txtTel.setText(dataById.getTel());
                txtEmail.setText(dataById.getEmail());
                textAreaAddress.setText(dataById.getAddress());
                comboJob.getSelectionModel().select(dataById.getJob_id().getType());
            } catch (DaoException e) {
                log.error("Failed to get employee data by id: {}", e.getMessage());
                AllAlerts.showExceptionDialog(e);
            }
        }
    }

    @Override
    public void resetData() {
        txtCode.setText(generate);
        txtSalary.setText("0");
        textAreaAddress.clear();
        Utils.clearAll(txtName, txtSalary, txtEmail, txtTel);
    }

    @NotNull
    @Override
    public BooleanBinding checkDataToEnableButton() {
        return (txtName.textProperty().isEmpty())
                .or(comboJob.getSelectionModel().selectedItemProperty().isNull());
    }

}
