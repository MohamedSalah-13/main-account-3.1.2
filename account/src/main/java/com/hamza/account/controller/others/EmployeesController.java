package com.hamza.account.controller.others;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Log4j2
public class EmployeesController implements TableInterface<Employees> {

    private final DataPublisher dataPublisher;
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
                dataPublisher.getPublisherAddEmployee().notifyObservers();
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
                // permission for employee salary
//                tableView.getColumns().get(4).setVisible(!new PermSetting().hasPermission(UserPermissionType.EMPLOYEES_SHOW_SALARY));
            }

            @Override
            public List<Employees> dataList() throws Exception {
                return employeeService.getEmployeesList();
            }

            @Override
            public @NotNull Class<? super Employees> classForColumn() {
                return Employees.class;
            }
        };
    }

    @Override
    public BooleanProperty getColumnSelected(Employees employees) {
        return employees.getSelectedRow();
    }

    @Override
    public Publisher<String> publisherTable() {
        return dataPublisher.getPublisherAddEmployee();
    }

    @Override
    public UserPermissionType permAdd() {
        return UserPermissionType.EMPLOYEE_SHOW;
    }

    @Override
    public UserPermissionType permUpdate() {
        return UserPermissionType.EMPLOYEE_UPDATE;
    }

    @Override
    public UserPermissionType permDelete() {
        return UserPermissionType.EMPLOYEE_DELETE;
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
        AddEmployeeController addEmployeeController = new AddEmployeeController(dataPublisher.getPublisherAddEmployee(), id, employeeService);
        new AddForAllApplication(id, addEmployeeController);
    }

}
