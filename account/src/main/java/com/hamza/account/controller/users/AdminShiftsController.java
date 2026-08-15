package com.hamza.account.controller.users;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.UserShiftService;
import com.hamza.account.session.ShiftContext;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;

import java.time.format.DateTimeFormatter;
import java.util.List;

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
            AllAlerts.handleError("تحميل الورديات", e);
        }
    }

    private void forceCloseSelected() {
        try {
            UserShift selected = tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                AllAlerts.handleError("إغلاق وردية إجباري", new UserValidationException("اختر وردية أولاً"));
                return;
            }

//            if (!LogApplication.hasPermission(AppPermissions.SHIFT_MANAGER)) {
//                AllAlerts.alertError("ليس لديك صلاحية إدارة الورديات");
//                return;
//            }

            if (!selected.isOpen()) {
                AllAlerts.handleError("إغلاق وردية إجباري", new BusinessRuleException("هذه الوردية مغلقة بالفعل"));
                return;
            }

            String msg = buildForceCloseMessage(selected);
            if (!AllAlerts.confirm_all("forceClose", msg)) {
                return;
            }

            int result = userShiftService.forceCloseShift(
                    selected.getId(),
                    0.0,
                    "Force closed by admin: " + CurrentUser.get().getUsername()
            );

            if (result > 0) {
                ShiftContext.clear();
                AllAlerts.alertSaveWithMessage("تم غلق الوردية قسريًا بنجاح");
                refreshData();
            }
        } catch (DaoException e) {
            AllAlerts.handleError("الإغلاق الإجباري للوردية", e);
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
