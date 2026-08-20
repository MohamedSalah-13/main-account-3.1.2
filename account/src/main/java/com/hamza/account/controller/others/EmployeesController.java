package com.hamza.account.controller.others;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.controlsfx.table.Columns;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.controlsfx.observer.EventBus;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Log4j2
public class EmployeesController implements TableInterface<Employees> {

    private final DataPublisher dataPublisher;
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final EmployeeService employeeService;

    public EmployeesController(DataPublisher dataPublisher, EmployeeService employeeService) throws Exception {
        this.dataPublisher = dataPublisher;
        this.employeeService = employeeService;
    }

    @Override
    public ActionButtonToolBar<Employees> actionButton() {
        return new ActionButtonToolBar<>() {
            @Override
            public void openNew() throws Exception {
                openData(0);
            }

            @Override
            public void print() throws Exception {
                ActionButtonToolBar.super.print();
            }

            @Override
            public void update(Employees employees) throws Exception {
                openData(employees.getId());
            }

            @Override
            public int delete(Employees employees) throws Exception {
                return employeeService.deleteEmployee(employees.getId());
            }

            @Override
            public void afterDelete() {
                if (eventBus != null) eventBus.publish(new EmployeesChanged());
            }
        };
    }

    @Override
    public DataTable<Employees> table_data() {
        return new DataTable<>() {
            @Override
            public void getTable(TableView<Employees> tableView) {
                TableColumn<Employees, String> columnTypeName = new TableColumn<>(Setting_Language.WORD_TYPE);
                columnTypeName.setCellValueFactory(f -> f.getValue().getJob_id().typeProperty());
                tableView.getColumns().add(columnTypeName);
                tableView.getColumns().get(4).setVisible(
                        AuthorizationGuard.isGranted(AppPermissions.EMPLOYEES_SHOW_SALARY));
            }

            @Override
            public List<Employees> dataList() throws Exception {
                return employeeService.getEmployeesList();
            }

            @Override
            public @NotNull List<TableColumn<Employees, ?>> columns() {
                return List.of(
                        Columns.number(NamesTables.CODE, Employees::getId),
                        Columns.text(NamesTables.NAME, Employees::getName),
                        Columns.date(Setting_Language.string_birth, Employees::getBirth_date),
                        Columns.date(Setting_Language.string_hire, Employees::getHire_date),
                        Columns.number(NamesTables.SALARY, Employees::getSalary),
                        Columns.text(NamesTables.EMAIL, Employees::getEmail),
                        Columns.text(NamesTables.TEL, Employees::getTel),
                        Columns.text(NamesTables.ADDRESS, Employees::getAddress)
                );
            }
        };
    }

    @Override
    public BooleanProperty getColumnSelected(Employees employees) {
        return employees.getSelectedRow();
    }

    @Override
    public Class<EmployeesChanged> refreshOn() {
        return EmployeesChanged.class;
    }

    @Override
    public boolean resizeTable() {
        return true;
    }

    @Override
    public PermissionKey permAdd() {
        return AppPermissions.EMPLOYEE_CREATE;
    }

    @Override
    public PermissionKey permUpdate() {
        return AppPermissions.EMPLOYEE_UPDATE;
    }

    @Override
    public PermissionKey permDelete() {
        return AppPermissions.EMPLOYEE_DELETE;
    }

    @Override
    public List<Employees> getProducts(int rowsPerPage, int offset) throws Exception {
        return employeeService.getProducts(rowsPerPage, offset);
    }

    @Override
    public List<Employees> getFilterItems(String newValue) throws Exception {
        return employeeService.getFilterEmployees(newValue);
    }

    @Override
    public int getCountItems() {
        return employeeService.getCountItems();
    }

    private void openData(int id) throws Exception {
        AddEmployeeController addEmployeeController = new AddEmployeeController(id, employeeService);
        new AddForAllApplication(id, addEmployeeController);
    }

}
