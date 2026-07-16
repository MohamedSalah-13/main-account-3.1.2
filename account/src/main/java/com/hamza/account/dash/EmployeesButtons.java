package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.others.AddEmployeeController;
import com.hamza.account.controller.others.EmployeesController;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.KeyCodeCombinationSetting;
import com.hamza.account.service.EmployeeService;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.Pane;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class EmployeesButtons extends LoadData {

    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);

    public EmployeesButtons(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    public ButtonWithPerm addEmployee() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                AddEmployeeController addEmployeeController = new AddEmployeeController(dataPublisher.getPublisherAddEmployee()
                        , 0, employeeService);
                new AddForAllApplication(0, addEmployeeController);
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.ADD_EMPLOYEE;
            }

            @Override
            public Node imageNode() {
                return new ImageDesign(new Image_Setting().setting);
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return KeyCodeCombinationSetting.ADD_EMPLOYEE;
            }
        };
    }

    public ButtonWithPerm employees() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.EMPLOYEES;
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                EmployeesController employeesController = new EmployeesController(dataPublisher, employeeService);
                Pane pane = new OpenFxmlApplication(employeesController).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().evaluation);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

}
