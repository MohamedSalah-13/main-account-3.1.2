package com.hamza.account.controller.users;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.ShiftReportService;
import com.hamza.account.service.UsersService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableView;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Log4j2
public class ShiftReportsController {

    private final ShiftReportService shiftReportService = ServiceRegistry.get(ShiftReportService.class);
    private final UsersService usersService = ServiceRegistry.get(UsersService.class);
    private final Print_Reports printReports = new Print_Reports();

    @FXML
    private DatePicker dateFrom;
    @FXML
    private DatePicker dateTo;
    @FXML
    private ComboBox<String> comboUsers;
    @FXML
    private Button btnSearch;
    @FXML
    private Button btnPrint;
    @FXML
    private TableView<UserShift> tableView;


    @FXML
    public void initialize() {
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);

        dateFrom.setValue(LocalDate.now().minusDays(7));
        dateTo.setValue(LocalDate.now());

        setupUsers();
        setupTable();
        setupActions();

        btnSearch.fire();
    }

    private void setupUsers() {
        try {
            List<String> users = usersService.getUsersNames();
            comboUsers.setItems(FXCollections.observableArrayList(users));
            comboUsers.getSelectionModel().selectFirst();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tableView, UserShift.class);
    }

    private void setupActions() {
        btnSearch.setOnAction(e -> refreshData());
        btnPrint.setOnAction(e -> printData());
    }

    private void refreshData() {
        try {
            LocalDate from = dateFrom.getValue();
            LocalDate to = dateTo.getValue();

            if (from == null || to == null) {
                throw new RuntimeException(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
            }

            Integer userId = null;
            String selectedUser = comboUsers.getSelectionModel().getSelectedItem();
            if (selectedUser != null && !selectedUser.isBlank() && comboUsers.getSelectionModel().getSelectedIndex() > 0) {
                userId = usersService.getUsersByName(selectedUser).getId();
            }

            LocalDateTime fromDateTime = from.atStartOfDay();
            LocalDateTime toDateTime = to.plusDays(1).atStartOfDay().minusNanos(1);

            var list = shiftReportService.buildAggregateReport(fromDateTime, toDateTime, userId);
            tableView.setItems(FXCollections.observableArrayList(list));

        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            AllAlerts.alertError(ex.getMessage());
        }
    }

    private void printData() {
        try {
            LocalDate from = dateFrom.getValue();
            LocalDate to = dateTo.getValue();

            if (from == null || to == null) {
                throw new RuntimeException(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
            }

            Integer userId = null;
            String selectedUser = comboUsers.getSelectionModel().getSelectedItem();
            String userName = "الكل";

            if (selectedUser != null && !selectedUser.isBlank() && comboUsers.getSelectionModel().getSelectedIndex() > 0) {
                var user = usersService.getUsersByName(selectedUser);
                userId = user.getId();
                userName = user.getUsername();
            }

            LocalDateTime fromDateTime = from.atStartOfDay();
            LocalDateTime toDateTime = to.plusDays(1).atStartOfDay().minusNanos(1);

            var list = shiftReportService.buildAggregateReport(fromDateTime, toDateTime, userId);
            printReports.printShiftAggregateReport(
                    list,
                    from.toString(),
                    to.toString(),
                    userName
            );
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            AllAlerts.alertError(ex.getMessage());
        }
    }
}
