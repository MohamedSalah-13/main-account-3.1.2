package com.hamza.account.controller.users;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.UserShiftService;
import com.hamza.account.session.ShiftContext;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import lombok.extern.log4j.Log4j2;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Log4j2
@FxmlPath(pathFile = "admin-shifts-view.fxml")
public class AdminShiftsController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final UserShiftService userShiftService = ServiceRegistry.get(UserShiftService.class);
    @FXML
    private TableView<UserShift> tableView;
    @FXML
    private Button btnRefresh;
    @FXML
    private Button btnForceClose;

    @FXML
    public void initialize() {
        setupTable();
        setupActions();
        refreshData();
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tableView, UserShift.class);
    }

    private void setupActions() {
        btnRefresh.setOnAction(e -> refreshData());
        btnForceClose.setOnAction(e -> forceCloseSelected());
    }

    private void refreshData() {
        try {
            List<UserShift> list = userShiftService.getAllShifts();
            tableView.setItems(FXCollections.observableArrayList(list));
        } catch (DaoException e) {
            log.error("Error loading shifts", e);
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void forceCloseSelected() {
        try {
            UserShift selected = tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                AllAlerts.alertError("اختر وردية أولاً");
                return;
            }

//            if (!LogApplication.hasPermission(UserPermissionType.SHIFT_MANAGER)) {
//                AllAlerts.alertError("ليس لديك صلاحية إدارة الورديات");
//                return;
//            }

            if (!selected.isOpen()) {
                AllAlerts.alertError("هذه الوردية مغلقة بالفعل");
                return;
            }

            String msg = buildForceCloseMessage(selected);
            if (!AllAlerts.confirm_all("forceClose", msg)) {
                return;
            }

            int result = userShiftService.forceCloseShift(
                    selected.getId(),
                    0.0,
                    "Force closed by admin: " + LogApplication.usersVo.getUsername()
            );

            if (result > 0) {
                ShiftContext.clear();
                AllAlerts.alertSaveWithMessage("تم غلق الوردية قسريًا بنجاح");
                refreshData();
            }
        } catch (DaoException e) {
            log.error("Error force closing shift", e);
            AllAlerts.alertError(e.getMessage());
        }
    }

    private String buildForceCloseMessage(UserShift shift) {
        return String.format(
                "هل تريد غلق الوردية قسريًا؟%n%n" +
                        "المستخدم: %s%n" +
                        "رقم الوردية: %d%n" +
                        "وقت الفتح: %s%n" +
                        "الرصيد الافتتاحي: %,.2f%n" +
                        "الملاحظات: %s",
                shift.getUsername(),
                shift.getId(),
                shift.getOpenTime() == null ? "-" : shift.getOpenTime().format(DATE_TIME_FORMATTER),
                shift.getOpenBalance(),
                shift.getNotes() == null ? "" : shift.getNotes()
        );
    }
}
