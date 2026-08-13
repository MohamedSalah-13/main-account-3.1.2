package com.hamza.account.controller.setting;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.period.AccountingLock;
import com.hamza.account.period.PeriodLockService;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Where a period is closed and re-opened.
 * <p>
 * It shows the line, moves it, and lists every time it has moved - the history is not
 * decoration: a period that can be quietly re-opened is not closed, and the record of
 * who re-opened it is what makes the close mean anything.
 * <p>
 * The whole tab is disabled without {@code ACCOUNTING_LOCK_MANAGE}. That is a hint, as
 * always - {@link PeriodLockService} checks the permission again where it writes.
 */
@Log4j2
@FxmlPath(pathFile = "include/settingTabPeriodLock.fxml")
public class SettingTabPeriodLockController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PeriodLockService periodLockService = ServiceRegistry.get(PeriodLockService.class);

    @FXML
    private VBox root;
    @FXML
    private Label labelState, labelDetail;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextField textNotes;
    @FXML
    private Button btnClose, btnReopen;
    @FXML
    private TableView<AccountingLock> tableHistory;
    @FXML
    private TableColumn<AccountingLock, String> colLockedUntil, colClosedAt, colNotes;

    @FXML
    public void initialize() {
        buildTable();
        buildActions();

        boolean mayManage = AuthorizationGuard.isGranted(AppPermissions.ACCOUNTING_LOCK_MANAGE);
        datePicker.setDisable(!mayManage);
        textNotes.setDisable(!mayManage);
        btnClose.setDisable(!mayManage);
        btnReopen.setDisable(!mayManage);

        show();
    }

    private void buildTable() {
        colLockedUntil.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(
                f.getValue().isClosed() ? f.getValue().lockedUntil().format(DAY) : "فُتحت الفترة"));
        colClosedAt.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(
                f.getValue().closedAt() == null ? "" : f.getValue().closedAt().format(MOMENT)));
        colNotes.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(f.getValue().notes()));
        tableHistory.setPlaceholder(new Label("لم يتم إغلاق أي فترة بعد"));
    }

    private void buildActions() {
        btnClose.setOnAction(event -> closePeriod());
        btnReopen.setOnAction(event -> reopenPeriod());
    }

    private void closePeriod() {
        LocalDate until = datePicker.getValue();
        if (until == null) {
            AllAlerts.alertError("اختر تاريخ الإغلاق");
            return;
        }
        // Closing is not a keystroke away from being undone, so it is confirmed - and
        // the question names the date, because "هل أنت متأكد" over an unnamed date is
        // how the wrong month gets closed.
        if (!AllAlerts.confirm_all("إغلاق الفترة المحاسبية",
                "سيتم إغلاق كل الحركات حتى " + until.format(DAY)
                + "\nلن يمكن تعديلها أو حذفها بعد ذلك. هل تريد المتابعة؟")) {
            return;
        }

        try {
            periodLockService.close(until, textNotes.getText());
            textNotes.clear();
            AllAlerts.alertSaveWithMessage("تم إغلاق الفترة حتى " + until.format(DAY));
            show();
        } catch (DaoException e) {
            log.error("Failed to close the accounting period", e);
            AllAlerts.handleError("إغلاق الفترة المحاسبية", e);
        }
    }

    private void reopenPeriod() {
        if (!periodLockService.current().isClosed()) {
            AllAlerts.alertError("لا توجد فترة مغلقة");
            return;
        }
        if (!AllAlerts.confirm_all("فتح الفترة المحاسبية",
                "سيصبح كل ما سبق قابلاً للتعديل والحذف مرة أخرى."
                + "\nسيُسجَّل هذا الإجراء باسمك. هل تريد المتابعة؟")) {
            return;
        }

        try {
            periodLockService.reopen(textNotes.getText());
            textNotes.clear();
            AllAlerts.alertSaveWithMessage("تم فتح الفترة");
            show();
        } catch (DaoException e) {
            log.error("Failed to reopen the accounting period", e);
            AllAlerts.handleError("إعادة فتح الفترة المحاسبية", e);
        }
    }

    /** Re-reads the line rather than trusting the cache - another machine may have moved it. */
    private void show() {
        AccountingLock lock = periodLockService.refresh();

        if (lock.isClosed()) {
            labelState.setText("مغلقة حتى " + lock.lockedUntil().format(DAY));
            labelDetail.setText("أول يوم مفتوح: " + lock.firstOpenDay().format(DAY));
            datePicker.setValue(lock.lockedUntil());
        } else {
            labelState.setText("الفترة مفتوحة");
            labelDetail.setText("كل الحركات قابلة للتعديل والحذف حسب صلاحيات المستخدم");
        }
        btnReopen.setDisable(btnReopen.isDisabled() || !lock.isClosed());

        showHistory();
    }

    private void showHistory() {
        try {
            List<AccountingLock> history = periodLockService.history(50);
            tableHistory.setItems(FXCollections.observableArrayList(history));
        } catch (DaoException e) {
            // A user who may not manage the lock cannot read its history either, and
            // that is not worth an alert on a tab they are only looking at.
            log.debug("Could not read the accounting lock history", e);
            tableHistory.setItems(FXCollections.observableArrayList());
        }
    }
}
