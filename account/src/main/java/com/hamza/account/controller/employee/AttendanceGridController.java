package com.hamza.account.controller.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.Employee;
import com.hamza.account.features.employee.EmployeeFilter;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.features.employee.attendance.AttendanceDay;
import com.hamza.account.features.employee.attendance.AttendanceEntry;
import com.hamza.account.features.employee.attendance.AttendanceGridRow;
import com.hamza.account.features.employee.attendance.AttendanceService;
import com.hamza.account.features.employee.attendance.AttendanceStatus;
import com.hamza.account.features.employee.attendance.AttendanceSummary;
import com.hamza.account.features.employee.attendance.LeaveType;
import com.hamza.account.features.employee.attendance.WorkWeek;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The month's attendance, employee by day.
 *
 * <h2>Why it is a grid and not a form</h2>
 * {@code docs/employees-plan.md} §6 says it plainly: a screen that records attendance one row
 * at a time is a screen nobody uses after the first week. Marking a month is twenty-odd
 * decisions per person, and they are made by looking at a row and clicking along it.
 *
 * <h2>The brush</h2>
 * Pick what you are marking, then click the days. It is how a paper register is filled in, and
 * it makes the common case - "everybody present, these three days off" - a handful of clicks
 * rather than a hundred. Two helpers do the bulk: one marks every rest day of the month from
 * the shop's own working week, the other marks everything still blank as present.
 *
 * <h2>Nothing is written until it is saved, and only what changed is written</h2>
 * The grid is a working copy. {@link AttendanceGridRow} records which days a person actually
 * moved, so saving writes those days and leaves the rest of the month exactly as the database
 * has it - a month is not rewritten to correct one cell. The save is one transaction: half a
 * saved month is worse than an unsaved one, because nothing on screen says which half.
 *
 * <h2>A day outside employment cannot be clicked</h2>
 * The service refuses it anyway, and a cell that can be clicked and is then refused teaches
 * nothing. The greying out is the hint; the refusal is still the rule.
 */
@Log4j2
@FxmlPath(pathFile = "attendance-grid.fxml")
public class AttendanceGridController implements AppSettingInterface {

    private final AttendanceService attendance = ServiceRegistry.get(AttendanceService.class);
    private final EmployeeService employees = ServiceRegistry.get(EmployeeService.class);

    private final Spinner<Integer> year = new Spinner<>();
    private final ComboBox<Integer> month = new ComboBox<>();
    private final ComboBox<AttendanceStatus> brush = new ComboBox<>();
    private final ComboBox<LeaveType> brushLeaveType = new ComboBox<>();

    private final TableView<AttendanceGridRow> table = new TableView<>();
    private final Label statusLabel = new Label();
    private final Button close = closeButton();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    private WorkWeek workWeek = new WorkWeek(null, null);
    private List<LeaveType> leaveTypes = new ArrayList<>();
    private YearMonth period = YearMonth.now();

    @FXML
    public void initialize() {
        stackPane.getStyleClass().add("screen-employees");

        buildTable();
        box.getChildren().setAll(identityHeader(), toolbar(), legend(), tableArea(), footer());
        VBox.setVgrow(box, Priority.ALWAYS);
        Platform.runLater(this::load);
    }

    // ---- the screen --------------------------------------------------------------------------

    private HBox identityHeader() {
        HBox iconBox = new HBox(AppIcon.EMPLOYEES.graphic(32));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-screen-icon-box");

        Label title = new Label(text("attendance.title"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("attendance.subtitle"));
        subtitle.getStyleClass().add("party-screen-subtitle");
        subtitle.setWrapText(true);

        VBox textBox = new VBox(3, title, subtitle);
        textBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox bar = new HBox(14, iconBox, textBox);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private HBox toolbar() {
        year.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                2000, 2200, LocalDate.now().getYear()));
        year.setEditable(true);
        year.setMaxWidth(110);

        for (int m = 1; m <= 12; m++) {
            month.getItems().add(m);
        }
        month.getSelectionModel().select(Integer.valueOf(LocalDate.now().getMonthValue()));
        month.setMinWidth(80);

        brush.getItems().setAll(AttendanceStatus.values());
        brush.setConverter(converter(status -> status == null ? "" : text(status.messageKey())));
        brush.getSelectionModel().select(AttendanceStatus.PRESENT);
        brush.setMinWidth(140);
        // A leave type is only a question while the brush is leave - V60 refuses the pairing
        // either way round, so offering the control when it cannot apply is only confusing.
        brush.setOnAction(event -> brushLeaveType.setDisable(
                brush.getValue() == null || !brush.getValue().needsLeaveType()));

        brushLeaveType.setConverter(converter(type -> type == null ? "" : type.name()));
        brushLeaveType.setMinWidth(160);
        brushLeaveType.setDisable(true);

        List<Node> controls = new ArrayList<>(List.of(
                caption("payroll.year"), year,
                caption("payroll.month"), month,
                button("attendance.action.load", AppIcon.REFRESH, this::load),
                new Label("   "),
                caption("attendance.brush"), brush, brushLeaveType));

        if (AuthorizationGuard.isGranted(AppPermissions.ATTENDANCE_RECORD)) {
            controls.add(button("attendance.action.fill.rest", AppIcon.CLEAR, this::fillRestDays));
            controls.add(button("attendance.action.fill.present", AppIcon.SELECT_ALL,
                    this::fillRemainingPresent));
            Button save = button("common.save", AppIcon.SAVE, this::saveGrid);
            save.getStyleClass().remove("app-neutral-button");
            save.getStyleClass().add("app-primary-button");
            controls.add(save);
        }

        HBox bar = new HBox(8);
        bar.getChildren().addAll(controls);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("filter-bar");
        return bar;
    }

    /** What the letters mean, because a grid of single characters explains nothing on its own. */
    private HBox legend() {
        HBox bar = new HBox(14);
        for (AttendanceStatus status : AttendanceStatus.values()) {
            Label label = new Label(shortCode(status) + " = " + text(status.messageKey()));
            label.getStyleClass().add("form-label");
            bar.getChildren().add(label);
        }
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox tableArea() {
        statusLabel.getStyleClass().add("form-label");
        statusLabel.setWrapText(true);
        VBox area = new VBox(8, statusLabel, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    private HBox footer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, close, spacer);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("summary-card", "party-summary-bar");
        return bar;
    }

    private void buildTable() {
        table.setId("attendance-grid");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPlaceholder(new Label(text("attendance.empty")));
    }

    /**
     * Rebuilds the day columns for the month.
     * <p>
     * They are rebuilt rather than hidden because a month has 28 to 31 days: leaving a 31st
     * column in February would offer a cell for a day that does not exist.
     */
    private void buildDayColumns() {
        List<TableColumn<AttendanceGridRow, ?>> columns = new ArrayList<>();

        TableColumn<AttendanceGridRow, String> name = new TableColumn<>(text("name"));
        name.setId("attendance-employee");
        name.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().employeeName()));
        name.setMinWidth(150);
        columns.add(name);

        for (int day = 1; day <= period.lengthOfMonth(); day++) {
            columns.add(dayColumn(day));
        }

        columns.add(totalColumn("attendance.total.worked",
                row -> row.summary().workedDays()));
        columns.add(totalColumn("attendance.total.absence",
                row -> row.summary().absenceDays()));
        columns.add(totalColumn("attendance.total.hours",
                row -> row.summary().workedHours()));

        table.getColumns().setAll(columns);
    }

    private TableColumn<AttendanceGridRow, String> dayColumn(int dayOfMonth) {
        LocalDate date = period.atDay(dayOfMonth);
        TableColumn<AttendanceGridRow, String> column = new TableColumn<>(String.valueOf(dayOfMonth));
        column.setId("attendance-day-" + dayOfMonth);
        column.setSortable(false);
        column.setMinWidth(34);
        column.setPrefWidth(34);
        column.setMaxWidth(34);
        column.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        shortCode(data.getValue().statusOn(dayOfMonth))));
        column.setCellFactory(ignored -> new DayCell(dayOfMonth, date));
        return column;
    }

    private TableColumn<AttendanceGridRow, String> totalColumn(
            String titleKey, Function<AttendanceGridRow, BigDecimal> figure) {
        TableColumn<AttendanceGridRow, String> column = new TableColumn<>(text(titleKey));
        column.setId("attendance-" + titleKey);
        column.setSortable(false);
        column.setMinWidth(90);
        column.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        figure.apply(data.getValue()).stripTrailingZeros().toPlainString()));
        return column;
    }

    /**
     * One day of one employee.
     * <p>
     * Three things a button-like cell gets wrong and this one does not: the graphic is cleared
     * when the cell is empty, because a {@code TableView} recycles cells and would otherwise
     * paint a mark on a blank row; the row is read with {@code getTableRow().getItem()} rather
     * than by index, which throws during a reload; and a day outside employment is disabled
     * rather than clickable and then refused.
     */
    private final class DayCell extends TableCell<AttendanceGridRow, String> {

        private final int dayOfMonth;
        private final LocalDate date;

        private DayCell(int dayOfMonth, LocalDate date) {
            this.dayOfMonth = dayOfMonth;
            this.date = date;
            setAlignment(Pos.CENTER);
            setOnMouseClicked(event -> paint());
        }

        private void paint() {
            AttendanceGridRow row = getTableRow() == null ? null : getTableRow().getItem();
            if (row == null || !row.isEmployedOn(date)
                    || !AuthorizationGuard.isGranted(AppPermissions.ATTENDANCE_RECORD)) {
                return;
            }
            AttendanceStatus status = brush.getValue();
            if (status == null) {
                return;
            }
            if (status.needsLeaveType() && brushLeaveType.getValue() == null) {
                AllAlerts.alertError(text("attendance.error.leave.type.required"));
                return;
            }
            row.set(dayOfMonth, status,
                    status.needsLeaveType() ? brushLeaveType.getValue().id() : null,
                    status.isWorked() ? workWeek.hoursPerDay() : BigDecimal.ZERO);
            table.refresh();
            updateStatus();
        }

        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            AttendanceGridRow row = getTableRow() == null ? null : getTableRow().getItem();
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                setStyle(null);
                setTooltip(null);
                return;
            }
            setText(value);
            if (!row.isEmployedOn(date)) {
                setStyle("-fx-opacity: 0.25;");
                setTooltip(new Tooltip(text("attendance.outside.employment")));
                return;
            }
            setTooltip(null);
            setStyle(row.isChanged(dayOfMonth) ? "-fx-font-weight: bold;" : null);
        }
    }

    /** One letter per status, so a month fits on a screen. The legend above says what each is. */
    private static String shortCode(AttendanceStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PRESENT -> "ح";
            case ABSENT -> "غ";
            case LEAVE -> "إ";
            case WEEKEND -> "ر";
            case HOLIDAY -> "ع";
        };
    }

    // ---- loading and saving ---------------------------------------------------------------

    private void load() {
        try {
            period = YearMonth.of(year.getValue(), month.getValue());
            workWeek = WorkWeek.current();
            leaveTypes = attendance.leaveTypes();
            brushLeaveType.getItems().setAll(leaveTypes);

            Map<Integer, Boolean> paid = new HashMap<>();
            for (LeaveType type : leaveTypes) {
                paid.put(type.id(), type.isPaid());
            }

            Map<Integer, AttendanceGridRow> rows = new java.util.LinkedHashMap<>();
            // Everyone, not only who is active today: somebody who left mid-month still has a
            // month to account for, the same reason the payroll reads them.
            // Everybody, whatever their state: EmployeeState.ALL is what EmployeeFilter.all()
            // already carries, and a large page because a grid shows a whole payroll at once.
            EmployeeFilter everyone = new EmployeeFilter("", null,
                    com.hamza.account.features.employee.EmployeeState.ALL, null, null, null, null,
                    null, null, false, 0, 2000);
            for (Employee employee : employees.search(everyone).rows()) {
                AttendanceGridRow row = new AttendanceGridRow(employee.id(), employee.name(),
                        employee.jobName(), employee.hireDate(), employee.endDate());
                row.knowPaidLeaveTypes(paid);
                rows.put(employee.id(), row);
            }

            for (AttendanceDay day : attendance.daysBetween(period.atDay(1),
                    period.atEndOfMonth())) {
                AttendanceGridRow row = rows.get(day.employeeId());
                if (row != null) {
                    row.load(day.date().getDayOfMonth(), day.status(), day.leaveTypeId(),
                            day.hours());
                }
            }

            buildDayColumns();
            table.getItems().setAll(rows.values());
            updateStatus();
        } catch (Exception e) {
            report(e);
        }
    }

    private void fillRestDays() {
        for (AttendanceGridRow row : table.getItems()) {
            for (int day = 1; day <= period.lengthOfMonth(); day++) {
                LocalDate date = period.atDay(day);
                DayOfWeek weekday = date.getDayOfWeek();
                if (workWeek.isRestDay(weekday) && row.isEmployedOn(date)
                        && row.statusOn(day) == null) {
                    row.set(day, AttendanceStatus.WEEKEND, null, BigDecimal.ZERO);
                }
            }
        }
        table.refresh();
        updateStatus();
    }

    /**
     * Marks every day still blank as present.
     * <p>
     * Blank only - it never overwrites a day somebody has already decided, which is what makes
     * it safe to press on a month that is half filled in.
     */
    private void fillRemainingPresent() {
        for (AttendanceGridRow row : table.getItems()) {
            for (int day = 1; day <= period.lengthOfMonth(); day++) {
                LocalDate date = period.atDay(day);
                if (row.isEmployedOn(date) && row.statusOn(day) == null) {
                    row.set(day, AttendanceStatus.PRESENT, null, workWeek.hoursPerDay());
                }
            }
        }
        table.refresh();
        updateStatus();
    }

    /**
     * Named {@code saveGrid} rather than {@code save}: {@code AppSettingInterface} extends
     * {@code ActionSave}, whose {@code save()} returns an int the dialog reads as success or
     * failure. A method of the same name here would be trying to implement it - the clash the
     * statement screen hit with {@code header()}.
     */
    private void saveGrid() {
        List<AttendanceEntry> entries = new ArrayList<>();
        try {
            for (AttendanceGridRow row : table.getItems()) {
                for (Map.Entry<Integer, AttendanceStatus> change : row.changedDays().entrySet()) {
                    int day = change.getKey();
                    entries.add(AttendanceEntry.parse(row.employeeId(), period.atDay(day),
                            change.getValue(), row.hoursOn(day), row.leaveTypeOn(day), null));
                }
            }
            if (entries.isEmpty()) {
                AllAlerts.alertError(text("attendance.nothing.changed"));
                return;
            }
            attendance.recordDays(entries);
            for (AttendanceGridRow row : table.getItems()) {
                row.clearChanges();
            }
            table.refresh();
            updateStatus();
            AllAlerts.alertSaveWithMessage(text("attendance.saved"));
        } catch (Exception e) {
            report(e);
        }
    }

    private void updateStatus() {
        int changed = 0;
        for (AttendanceGridRow row : table.getItems()) {
            changed += row.changedDays().size();
        }
        statusLabel.setText(period + "  |  " + text("attendance.status.changed") + " " + changed);
    }

    // ---- plumbing ----------------------------------------------------------------------------

    private Button button(String key, AppIcon icon, Runnable action) {
        Button button = new Button(text(key), icon.graphic());
        button.getStyleClass().add("app-neutral-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private Button closeButton() {
        Button button = button("common.close", AppIcon.CLOSE, () -> {
        });
        button.setId("btnClose");
        button.setOnAction(event -> ((Stage) button.getScene().getWindow()).close());
        return button;
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static <T> StringConverter<T> converter(Function<T, String> toText) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return toText.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    private void report(Throwable error) {
        AllAlerts.handleError(text("attendance.error.operation"),
                error instanceof Exception exception ? exception : new RuntimeException(error));
    }

    // ---- the dialog contract -------------------------------------------------------------------

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return text("attendance.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    /** Saving is this screen's own button, over its own rules. See the payroll screen. */
    @Override
    public boolean addLastPane() {
        return false;
    }

    @Override
    public double minWidth() {
        return 1200;
    }

    @Override
    public double minHeight() {
        return 620;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }
}
