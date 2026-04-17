package com.hamza.account.controller.users;

import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.time.format.DateTimeFormatter;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;

@Log4j2
@FxmlPath(pathFile = "user-shift-view.fxml")
public class UserShiftController extends ServiceData {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    private Label labelTitle, labelShiftStatus, labelOpenTime, labelOpenBalance;

    @FXML
    private VBox boxOpenShift, boxCloseShift;

    @FXML
    private TextField txtOpenBalance, txtCloseBalance;

    @FXML
    private TextArea txtOpenNotes, txtCloseNotes;

    @FXML
    private Button btnOpenShift, btnCloseShift;

    @FXML
    private TableView<UserShift> tableShifts;

    @FXML
    private TableColumn<UserShift, Integer> colId;

    @FXML
    private TableColumn<UserShift, String> colOpenTime, colCloseTime, colStatus;

    @FXML
    private TableColumn<UserShift, Double> colOpenBalance, colCloseBalance;

    private final int currentUserId;

    public UserShiftController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
        this.currentUserId = LogApplication.usersVo.getId();
    }

    @FXML
    public void initialize() {
        setupTextFormatters();
        setupTableColumns();
        setupActions();
        refreshView();
    }

    private void setupTextFormatters() {
        setTextFormatter(txtOpenBalance, txtCloseBalance);
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getId()).asObject());

        colOpenTime.setCellValueFactory(cellData -> {
            var openTime = cellData.getValue().getOpenTime();
            return new SimpleStringProperty(openTime != null ? openTime.format(DATE_TIME_FORMATTER) : "-");
        });

        colCloseTime.setCellValueFactory(cellData -> {
            var closeTime = cellData.getValue().getCloseTime();
            return new SimpleStringProperty(closeTime != null ? closeTime.format(DATE_TIME_FORMATTER) : "-");
        });

        colOpenBalance.setCellValueFactory(cellData -> new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().getOpenBalance()).asObject());
        colCloseBalance.setCellValueFactory(cellData -> new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().getCloseBalance()).asObject());
        colStatus.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
    }

    private void setupActions() {
        btnOpenShift.setOnAction(e -> openShift());
        btnCloseShift.setOnAction(e -> closeShift());
    }

    private void refreshView() {
        loadCurrentShiftStatus();
        loadShiftHistory();
    }

    private void loadCurrentShiftStatus() {
        try {
            boolean hasOpen = userShiftService.hasOpenShift(currentUserId);
            if (hasOpen) {
                UserShift openShift = userShiftService.getOpenShift(currentUserId);
                showOpenShiftInfo(openShift);
                boxOpenShift.setDisable(true);
                boxCloseShift.setDisable(false);
                txtCloseBalance.setText(String.valueOf(openShift.getOpenBalance()));
            } else {
                showNoOpenShift();
                boxOpenShift.setDisable(false);
                boxCloseShift.setDisable(true);
                txtCloseBalance.clear();
                txtCloseNotes.clear();
            }
        } catch (DaoException e) {
            log.error("Error loading shift status", e);
            AllAlerts.alertError("خطأ في تحميل حالة الوردية: " + e.getMessage());
        }
    }

    private void showOpenShiftInfo(UserShift shift) {
        labelShiftStatus.setText("مفتوحة");
        labelShiftStatus.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        labelOpenTime.setText(shift.getOpenTime() != null ? shift.getOpenTime().format(DATE_TIME_FORMATTER) : "-");
        labelOpenBalance.setText(String.valueOf(shift.getOpenBalance()));
    }

    private void showNoOpenShift() {
        labelShiftStatus.setText("لا توجد وردية مفتوحة");
        labelShiftStatus.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        labelOpenTime.setText("-");
        labelOpenBalance.setText("0.0");
    }

    private void loadShiftHistory() {
        try {
            var shifts = userShiftService.getUserShifts(currentUserId);
            tableShifts.setItems(FXCollections.observableArrayList(shifts));
        } catch (DaoException e) {
            log.error("Error loading shift history", e);
            AllAlerts.alertError("خطأ في تحميل سجل الورديات: " + e.getMessage());
        }
    }

    private void openShift() {
        try {
            double openBalance = parseBalance(txtOpenBalance.getText());
            String notes = txtOpenNotes.getText();

            int result = userShiftService.openShift(currentUserId, openBalance, notes);
            if (result > 0) {
                AllAlerts.alertSaveWithMessage("تم فتح الوردية بنجاح!");
                clearOpenShiftFields();
                refreshView();
            }
        } catch (DaoException e) {
            log.error("Error opening shift", e);
            AllAlerts.alertError(e.getMessage());
        } catch (NumberFormatException e) {
            AllAlerts.alertError("الرجاء إدخال رصيد صحيح!");
        }
    }

    private void closeShift() {
        try {
            if (!userShiftService.hasOpenShift(currentUserId)) {
                AllAlerts.alertError("لا توجد وردية مفتوحة لإغلاقها!");
                return;
            }

            if (!AllAlerts.confirm_all("هل تريد غلق الوردية؟")) {
                return;
            }

            double closeBalance = parseBalance(txtCloseBalance.getText());
            String notes = txtCloseNotes.getText();

            int result = userShiftService.closeShift(currentUserId, closeBalance, notes);
            if (result > 0) {
                AllAlerts.alertSaveWithMessage("تم غلق الوردية بنجاح!");
                clearCloseShiftFields();
                refreshView();
            }
        } catch (DaoException e) {
            log.error("Error closing shift", e);
            AllAlerts.alertError(e.getMessage());
        } catch (NumberFormatException e) {
            AllAlerts.alertError("الرجاء إدخال رصيد صحيح!");
        }
    }

    private double parseBalance(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        return Double.parseDouble(text.trim());
    }

    private void clearOpenShiftFields() {
        txtOpenBalance.clear();
        txtOpenNotes.clear();
    }

    private void clearCloseShiftFields() {
        txtCloseBalance.clear();
        txtCloseNotes.clear();
    }
}