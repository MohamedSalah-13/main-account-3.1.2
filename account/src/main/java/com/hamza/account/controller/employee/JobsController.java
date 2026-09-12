package com.hamza.account.controller.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.EmployeeScope;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.features.employee.Job;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.util.List;

import static com.hamza.controlsfx.others.Utils.setOptionalNumberFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The jobs behind the employees.
 * <p>
 * <b>There was no screen at all, and could not be.</b> {@code jobs} has been a table with a unique
 * name since V1, but Java read it through {@code UsersType} - four constants with ids matched to
 * those four rows by hand - and {@code getUserTypeById} answered {@code null} for anything else. So
 * adding a row broke the employees list when it drew and the form when it saved, and a shop was
 * stuck with the four names somebody seeded in 2019.
 * <p>
 * <b>{@code is_delegate} is the flag that matters here.</b> A delegate used to be {@code job == 4},
 * written into a DAO and two queries; now it is a tick on the job, so a shop can have a delivery
 * delegate and a collections delegate, and V57 sets it on the seeded row 4 alone so nothing changes
 * on upgrade.
 * <p>
 * Not folded into the master-data editor: that one edits a name and one numeric field, and a job
 * carries a flag as well - bending a shared editor to hold employee-specific columns would put them
 * in a query four other screens read.
 */
@Log4j2
@FxmlPath(pathFile = "jobs.fxml")
public class JobsController implements AddInterface {

    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final TableView<Job> table = new TableView<>();
    private final ContentSizedColumns<Job> columnSizing = new ContentSizedColumns<>();
    private final TextField txtName = new TextField();
    private final TextField txtDefaultSalary = new TextField();
    private final TextField txtNotes = new TextField();
    private final CheckBox isDelegate = new CheckBox(text("job.delegate"));
    private final CheckBox isActive = new CheckBox(text("job.active"));

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    /** Which job the entry bar is editing; 0 while it is adding one. */
    private int editing;

    @FXML
    public void initialize() {
        otherSetting();
        selectData();
    }

    @Override
    public void otherSetting() {
        stackPane.getStyleClass().add("screen-employees");

        txtName.setPromptText(text("name"));
        txtNotes.setPromptText(text("column.notes"));
        // A suggestion the employee form may take, so no bound is implied by leaving it empty -
        // setOptionalNumberFormatter rather than the one that seeds 0.0.
        setOptionalNumberFormatter(txtDefaultSalary);
        txtDefaultSalary.setPromptText(text("job.default.salary"));
        isActive.setSelected(true);
        HBox.setHgrow(txtNotes, Priority.ALWAYS);
        whenEnterPressed(txtName, txtDefaultSalary, txtNotes);

        buildTable();
        box.getChildren().setAll(header(), entryBar(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private HBox header() {
        Label title = new Label(text("jobs"));
        title.getStyleClass().add("party-screen-title");
        HBox bar = new HBox(12, AppIcon.EMPLOYEES.graphic(24), title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private VBox entryBar() {
        HBox row = new HBox(8, caption("name"), txtName, caption("job.default.salary"),
                txtDefaultSalary, isDelegate, isActive, txtNotes);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox bar = new VBox(8, row);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    private void buildTable() {
        table.setId("jobs-table");
        table.setPlaceholder(new Label(text("job.empty")));
        table.getColumns().setAll(List.of(
                RowActionsColumn.of("employee.column.actions", RowAction.permitted(List.of(
                        RowAction.of("update", AppIcon.EDIT, "app-primary-button",
                                AppPermissions.JOB_UPDATE, this::edit),
                        RowAction.of("delete", AppIcon.DELETE, "app-neutral-button",
                                AppPermissions.JOB_DELETE, this::remove)))),
                Columns.number("code", Job::id),
                Columns.text("name", Job::name),
                Columns.text("job.delegate", row -> text(row.delegate() ? "yes" : "no")),
                Columns.text("job.active", row -> text(row.active() ? "yes" : "no")),
                Columns.money("job.default.salary", Job::defaultSalary),
                Columns.text("column.notes", Job::notes)));
        columnSizing.install(table);
    }

    @Override
    public int insertData() throws Exception {
        employeeService.saveJob(new Job(editing, txtName.getText(), isDelegate.isSelected(),
                isActive.isSelected(), amount(txtDefaultSalary), blankToNull(txtNotes.getText())));
        editing = 0;
        selectData();
        return 1;
    }

    @Override
    public void afterSaved() {
        resetData();
        if (eventBus != null) {
            // The employees list shows the job name in a column and filters by it, so a rename is
            // news to it as much as an employee is.
            eventBus.publish(new EmployeesChanged());
        }
    }

    @Override
    public void selectData() {
        try {
            table.setItems(FXCollections.observableArrayList(
                    employeeService.jobs(EmployeeScope.EVERYONE)));
            columnSizing.layout(table);
        } catch (Exception e) {
            report(e);
        }
    }

    private void edit(Job job) {
        editing = job.id();
        txtName.setText(job.name());
        txtDefaultSalary.setText(job.defaultSalary() == null ? "" : job.defaultSalary().toPlainString());
        txtNotes.setText(job.notes());
        isDelegate.setSelected(job.delegate());
        isActive.setSelected(job.active());
        txtName.requestFocus();
    }

    /**
     * Deletes a job nobody holds. One that somebody holds is refused by {@code DeletionService}
     * through {@code DeleteRegistry.JOBS}, with the number of employees in the message - and a job
     * that is simply out of use is switched off instead, which is what {@code is_active} is for.
     */
    private void remove(Job job) {
        try {
            if (!AllAlerts.confirmDelete()) {
                return;
            }
            employeeService.deleteJob(job.id());
            selectData();
            afterSaved();
        } catch (Exception e) {
            report(e);
        }
    }

    @Override
    public void resetData() {
        editing = 0;
        txtName.clear();
        txtDefaultSalary.clear();
        txtNotes.clear();
        isDelegate.setSelected(false);
        isActive.setSelected(true);
    }

    @Override
    public @org.jetbrains.annotations.NotNull BooleanBinding checkDataToEnableButton() {
        return Bindings.createBooleanBinding(
                () -> txtName.getText() == null || txtName.getText().isBlank(),
                txtName.textProperty());
    }

    @Override
    public boolean keepDialogOpenAfterSave() {
        return true;
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }

    private void report(Exception e) {
        AllAlerts.handleError(text("employee.error.operation"), e);
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BigDecimal amount(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank()
                ? null
                : BigDecimal.valueOf(com.hamza.controlsfx.others.DoubleSetting
                .parseDoubleOrDefault(value));
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
