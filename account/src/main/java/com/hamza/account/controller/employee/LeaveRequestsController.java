package com.hamza.account.controller.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.Employee;
import com.hamza.account.features.employee.EmployeeFilter;
import com.hamza.account.features.employee.EmployeeRef;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.features.employee.attendance.AttendanceService;
import com.hamza.account.features.employee.attendance.LeaveRequest;
import com.hamza.account.features.employee.attendance.LeaveService;
import com.hamza.account.features.employee.attendance.LeaveStatus;
import com.hamza.account.features.employee.attendance.LeaveType;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Leave: asking for it, and deciding on it.
 *
 * <h2>Two halves, two permissions, and the screen shows each only where it applies</h2>
 * Recording a request grants nothing; approving one writes days onto the attendance grid and
 * so changes what a month costs. A user with only {@code leave.request} sees the form and no
 * decision buttons; a user with only {@code leave.approve} sees the decisions and no form.
 * Both are hints - the services still call {@code require}.
 *
 * <h2>Approving here is what makes an approved day not an absence</h2>
 * The decision writes the days, in the same transaction. A request that said APPROVED while
 * the grid still said nothing would leave the employee marked absent for days the company
 * granted - which is the one outcome this screen exists to prevent.
 */
@Log4j2
@FxmlPath(pathFile = "leave-requests.fxml")
public class LeaveRequestsController implements AppSettingInterface {

    private final LeaveService leaves = ServiceRegistry.get(LeaveService.class);
    private final AttendanceService attendance = ServiceRegistry.get(AttendanceService.class);
    private final EmployeeService employees = ServiceRegistry.get(EmployeeService.class);

    private final ComboBox<LeaveStatus> filterStatus = new ComboBox<>();
    private final TableView<LeaveRequest> table = new TableView<>();
    private final ContentSizedColumns<LeaveRequest> columnSizing = new ContentSizedColumns<>();

    private final ComboBox<EmployeeRef> employee = new ComboBox<>();
    private final ComboBox<LeaveType> type = new ComboBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final TextField reason = new TextField();

    private final Button close = closeButton();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    @FXML
    public void initialize() {
        stackPane.getStyleClass().add("screen-employees");

        buildTable();
        List<Node> children = new ArrayList<>(List.of(identityHeader()));
        if (AuthorizationGuard.isGranted(AppPermissions.LEAVE_REQUEST)) {
            children.add(requestBar());
        }
        children.add(filterBar());
        children.add(tableArea());
        children.add(footer());
        box.getChildren().setAll(children);
        VBox.setVgrow(box, Priority.ALWAYS);
        Platform.runLater(this::load);
    }

    private HBox identityHeader() {
        HBox iconBox = new HBox(AppIcon.EMPLOYEES.graphic(32));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-screen-icon-box");

        Label title = new Label(text("leave.title"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("leave.subtitle"));
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

    private HBox requestBar() {
        employee.setConverter(converter(ref -> ref == null ? "" : ref.name()));
        employee.setMinWidth(180);
        type.setConverter(converter(value -> value == null ? "" : value.name()));
        type.setMinWidth(160);

        // Dates being entered, not filtered: today is the right default for both, and
        // DateSetting.dateAction is the entry helper that seeds it.
        DateSetting.dateAction(from);
        DateSetting.dateAction(to);
        reason.setPromptText(text("leave.reason"));
        HBox.setHgrow(reason, Priority.ALWAYS);

        Button add = button("leave.action.request", AppIcon.ADD, this::request);
        add.getStyleClass().remove("app-neutral-button");
        add.getStyleClass().add("app-primary-button");

        HBox bar = new HBox(8, caption("employees"), employee, caption("leave.type"), type,
                caption("from"), from, caption("to"), to, reason, add);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("filter-bar");
        return bar;
    }

    private HBox filterBar() {
        filterStatus.getItems().add(null);
        filterStatus.getItems().addAll(LeaveStatus.values());
        filterStatus.setConverter(converter(status -> status == null
                ? text("party.statement.filter.all") : text(status.messageKey())));
        filterStatus.getSelectionModel().selectFirst();
        filterStatus.setOnAction(event -> load());

        List<Node> controls = new ArrayList<>(List.of(
                caption("leave.state"), filterStatus,
                button("refresh", AppIcon.REFRESH, this::load)));

        if (AuthorizationGuard.isGranted(AppPermissions.LEAVE_APPROVE)) {
            controls.add(button("leave.action.approve", AppIcon.CONFIRM,
                    () -> decide(LeaveStatus.APPROVED)));
            controls.add(button("leave.action.reject", AppIcon.CLOSE,
                    () -> decide(LeaveStatus.REJECTED)));
        }

        HBox bar = new HBox(8);
        bar.getChildren().addAll(controls);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("filter-bar");
        return bar;
    }

    private VBox tableArea() {
        VBox area = new VBox(8, table);
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
        table.setId("leave-requests");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPlaceholder(new Label(text("leave.empty")));
        table.getColumns().setAll(List.of(
                named("leave-employee", Columns.text("name", LeaveRequest::employeeName)),
                named("leave-type", Columns.text("leave.type", LeaveRequest::leaveTypeName)),
                named("leave-paid", Columns.text("leave.paid",
                        request -> text(request.leaveIsPaid() ? "common.yes" : "common.no"))),
                named("leave-from", Columns.date("from", LeaveRequest::from)),
                named("leave-to", Columns.date("to", LeaveRequest::to)),
                named("leave-days", Columns.text("leave.days",
                        request -> String.valueOf(request.days()))),
                named("leave-status", Columns.text("leave.state",
                        request -> text(request.status().messageKey()))),
                named("leave-reason", Columns.text("leave.reason", LeaveRequest::reason)),
                named("leave-decided-by", Columns.text("payroll.status.approved.by",
                        LeaveRequest::decidedByName))));
    }

    private static <T> TableColumn<LeaveRequest, T> named(String id,
                                                          TableColumn<LeaveRequest, T> column) {
        column.setId(id);
        return column;
    }

    // ---- loading and acting --------------------------------------------------------------

    private void load() {
        try {
            // Everybody, not only the delegates: leave is asked for by whoever works here.
            List<EmployeeRef> refs = new ArrayList<>();
            for (Employee row : employees.search(new EmployeeFilter("", null,
                    com.hamza.account.features.employee.EmployeeState.ALL, null, null, null, null,
                    null, null, false, 0, 2000)).rows()) {
                refs.add(new EmployeeRef(row.id(), row.name()));
            }
            employee.getItems().setAll(refs);
            type.getItems().setAll(attendance.leaveTypes());
            table.getItems().setAll(leaves.requests(filterStatus.getValue(), 500));
            columnSizing.layout(table);
        } catch (Exception e) {
            report(e);
        }
    }

    private void request() {
        try {
            if (employee.getValue() == null || type.getValue() == null) {
                AllAlerts.alertError(text("attendance.error.employee"));
                return;
            }
            leaves.request(employee.getValue().id(), type.getValue().id(),
                    from.getValue(), to.getValue(), emptyToNull(reason.getText()));
            reason.clear();
            load();
            AllAlerts.alertSaveWithMessage(text("leave.requested"));
        } catch (Exception e) {
            report(e);
        }
    }

    private void decide(LeaveStatus decision) {
        LeaveRequest selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.alertError(text("leave.error.select"));
            return;
        }
        if (!AllAlerts.confirmSave()) {
            return;
        }
        try {
            leaves.decide(selected.id(), decision, null);
            load();
            AllAlerts.alertSaveWithMessage(text(decision == LeaveStatus.APPROVED
                    ? "leave.approved" : "leave.rejected"));
        } catch (Exception e) {
            report(e);
        }
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

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
        return text("leave.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public boolean addLastPane() {
        return false;
    }

    @Override
    public double minWidth() {
        return 1000;
    }

    @Override
    public double minHeight() {
        return 560;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }
}
