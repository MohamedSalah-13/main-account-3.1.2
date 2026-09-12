package com.hamza.account.dash;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.employee.EmployeeFormController;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.otherSetting.KeyCodeCombinationSetting;
import com.hamza.account.view.EmployeesApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCodeCombination;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

/**
 * The two ways into the employees.
 * <p>
 * The list opens as a tab rather than as a modal window, the way the parties' balances screen does:
 * it is a screen somebody works in beside an invoice, not a dialog that has to be dismissed.
 */
@Log4j2
public class EmployeesButtons extends LoadData {

    public EmployeesButtons(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    public ButtonWithPerm addEmployee() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.EMPLOYEE_CREATE;
            }

            @Override
            public void action() throws Exception {
                new AddForAllApplication(0, new EmployeeFormController(0));
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("nav.add.employee");
            }

            @Override
            public Node imageNode() {
                return AppIcon.EMPLOYEES.graphic(24);
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
            public PermissionKey getPermissionType() {
                return AppPermissions.EMPLOYEE_SHOW;
            }

            @Override
            public void action() {
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                EmployeesApplication screen = new EmployeesApplication(daoFactory, dataPublisher);
                addTape(tabPane, screen.getPane(), textName(), new Image_Setting().account);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("employees");
            }

            @Override
            public Node imageNode() {
                return AppIcon.EMPLOYEES.graphic(24);
            }
        };
    }
}
