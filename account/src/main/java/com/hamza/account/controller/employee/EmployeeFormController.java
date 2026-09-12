package com.hamza.account.controller.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.Employee;
import com.hamza.account.features.employee.EmployeeDraft;
import com.hamza.account.features.employee.EmployeeScope;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.features.employee.EmploymentType;
import com.hamza.account.features.employee.Job;
import com.hamza.account.features.employee.SalaryKind;
import com.hamza.account.features.events.EmployeesChanged;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * Adding an employee, and editing one.
 * <p>
 * Built in code. The file it replaces declared nine labels reading "Code", "Name", "Salary" in
 * English and let the controller overwrite each one at startup - two files that have to agree about
 * one screen, where a caption the controller forgets ships in the wrong language. It also declared
 * a box for the employee's picture that nothing ever filled, and the one line that would have
 * cleared it was commented out.
 * <p>
 * Three things it does that the old form could not:
 * <ul>
 *   <li><b>The job is a row</b>, read from {@code jobs}. It used to be a combo over four constants
 *       whose ids were matched to that table by hand, so a fifth row broke the screen.</li>
 *   <li><b>The salary is dated.</b> On a new employee this box writes the first
 *       {@code employee_compensation} row. On an existing one it may still correct that row while
 *       it is the only one there is - after that {@code SalaryChangeGuard} refuses it and the
 *       message points at the salary screen, which exists.</li>
 *   <li><b>The picture is wired.</b></li>
 * </ul>
 */
@Log4j2
@FxmlPath(pathFile = "employee-form.fxml")
public class EmployeeFormController implements AddInterface {

    private static final int PHOTO_SIZE = 120;

    /** A picture is stored in a LONGBLOB and read back on a profile; it is not a place for a scan. */
    private static final long PHOTO_MAX_BYTES = 2L * 1024 * 1024;

    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final int employeeId;

    private final TextField txtCode = new TextField();
    private final TextField txtName = new TextField();
    private final ComboBox<Job> comboJob = new ComboBox<>();
    private final ComboBox<EmploymentType> comboEmployment = new ComboBox<>();
    private final ComboBox<SalaryKind> comboSalaryKind = new ComboBox<>();
    private final TextField txtRate = new TextField();
    private final DatePicker hireDate = new DatePicker(LocalDate.now());
    private final DatePicker birthDate = new DatePicker();
    private final DatePicker endDate = new DatePicker();
    private final TextField txtNationalId = new TextField();
    private final TextField txtPhone = new TextField();
    private final TextField txtEmail = new TextField();
    private final TextArea txtAddress = new TextArea();
    private final TextArea txtNotes = new TextArea();
    private final ImageView photoView = new ImageView();
    private final Label salaryLockNote = new Label();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    private byte[] photo;
    private boolean photoChanged;

    public EmployeeFormController(int employeeId) {
        this.employeeId = employeeId;
    }

    @FXML
    public void initialize() {
        otherSetting();
        if (employeeId > 0) {
            selectData();
        }
    }

    @Override
    public void otherSetting() {
        stackPane.getStyleClass().add("screen-employees");

        txtCode.setEditable(false);
        txtCode.setText(LanguageManager.getInstance().getString("item.code.generate"));
        txtName.setPromptText(text("name"));
        txtNationalId.setPromptText(text("employee.column.national"));
        txtPhone.setPromptText(text("column.tel"));
        txtEmail.setPromptText(text("column.email"));
        txtAddress.setPromptText(text("column.address"));
        txtAddress.setPrefRowCount(2);
        txtNotes.setPromptText(text("column.notes"));
        txtNotes.setPrefRowCount(2);

        comboJob.setConverter(converter(job -> job == null ? "" : job.name()));
        comboEmployment.getItems().setAll(EmploymentType.values());
        comboEmployment.setConverter(converter(type -> type == null ? "" : text(type.messageKey())));
        comboEmployment.getSelectionModel().select(EmploymentType.FULL_TIME);
        comboSalaryKind.getItems().setAll(SalaryKind.values());
        comboSalaryKind.setConverter(converter(kind -> kind == null ? "" : text(kind.messageKey())));
        comboSalaryKind.getSelectionModel().select(SalaryKind.MONTHLY);

        // An amount being entered, so this one does take the number formatter that seeds 0.0.
        setTextFormatter(txtRate);

        salaryLockNote.getStyleClass().add("form-hint");
        salaryLockNote.setWrapText(true);
        salaryLockNote.setVisible(false);
        salaryLockNote.setManaged(false);

        // The picture is only offered to somebody who may edit the employee; on a new one it is
        // kept until the row exists, because a photo needs an id to belong to.
        boolean mayEdit = AuthorizationGuard.isGranted(employeeId > 0
                ? AppPermissions.EMPLOYEE_UPDATE : AppPermissions.EMPLOYEE_CREATE);

        boolean salaryVisible = employeeService.salaryVisible();
        comboSalaryKind.setDisable(!salaryVisible);
        txtRate.setDisable(!salaryVisible);

        box.getChildren().setAll(form(mayEdit));

        // The Enter order, declared once, in the order the form is actually filled - rule ق-ل9.
        whenEnterPressed(txtName, txtRate, txtNationalId, txtPhone, txtEmail);
        Platform.runLater(txtName::requestFocus);
        loadJobs();
    }

    private HBox form(boolean mayEdit) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        ColumnConstraints captions = new ColumnConstraints();
        captions.setMinWidth(110);
        ColumnConstraints values = new ColumnConstraints();
        values.setPrefWidth(260);
        values.setHgrow(Priority.SOMETIMES);
        grid.getColumnConstraints().addAll(captions, values, captions, values);

        int row = 0;
        addRow(grid, row, "code", txtCode, "name", txtName);
        row++;
        addRow(grid, row, "employee.column.job", comboJob, "employee.column.employment", comboEmployment);
        row++;
        addRow(grid, row, "employee.column.salary.kind", comboSalaryKind, "employee.column.rate", txtRate);
        row++;
        addRow(grid, row, "employee.column.hire", hireDate, "employee.column.birth", birthDate);
        row++;
        addRow(grid, row, "employee.column.end", endDate, "employee.column.national", txtNationalId);
        row++;
        addRow(grid, row, "column.tel", txtPhone, "column.email", txtEmail);
        row++;
        addRow(grid, row, "column.address", txtAddress, "column.notes", txtNotes);
        row++;
        grid.add(salaryLockNote, 1, row, 3, 1);

        comboJob.setMaxWidth(Double.MAX_VALUE);
        comboEmployment.setMaxWidth(Double.MAX_VALUE);
        comboSalaryKind.setMaxWidth(Double.MAX_VALUE);
        hireDate.setMaxWidth(Double.MAX_VALUE);
        birthDate.setMaxWidth(Double.MAX_VALUE);
        endDate.setMaxWidth(Double.MAX_VALUE);

        VBox fields = new VBox(10, grid);
        fields.getStyleClass().addAll("app-card", "party-form-card");
        HBox.setHgrow(fields, Priority.ALWAYS);

        HBox shell = new HBox(12, fields, photoBox(mayEdit));
        shell.setAlignment(Pos.TOP_CENTER);
        return shell;
    }

    private VBox photoBox(boolean mayEdit) {
        photoView.setFitWidth(PHOTO_SIZE);
        photoView.setFitHeight(PHOTO_SIZE);
        photoView.setPreserveRatio(true);

        Button choose = new Button(text("employee.photo.choose"), AppIcon.ADD.graphic());
        choose.getStyleClass().add("app-neutral-button");
        choose.setOnAction(event -> choosePhoto());
        Button clear = new Button(text("employee.photo.clear"), AppIcon.DELETE.graphic());
        clear.getStyleClass().add("app-neutral-button");
        clear.setOnAction(event -> {
            photo = null;
            photoChanged = true;
            photoView.setImage(null);
        });
        choose.setDisable(!mayEdit);
        clear.setDisable(!mayEdit);

        Label caption = new Label(text("employee.photo"));
        caption.getStyleClass().add("form-label");
        VBox pane = new VBox(8, caption, photoView, choose, clear);
        pane.setAlignment(Pos.TOP_CENTER);
        pane.getStyleClass().addAll("app-card", "party-form-card");
        pane.setMinWidth(PHOTO_SIZE + 40);
        return pane;
    }

    private void addRow(GridPane grid, int row, String firstKey, javafx.scene.Node first,
                        String secondKey, javafx.scene.Node second) {
        grid.add(label(firstKey), 0, row);
        grid.add(first, 1, row);
        grid.add(label(secondKey), 2, row);
        grid.add(second, 3, row);
    }

    private static Label label(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private void loadJobs() {
        try {
            List<Job> options = new ArrayList<>(employeeService.jobs(EmployeeScope.ACTIVE_ONLY));
            comboJob.setItems(FXCollections.observableArrayList(options));
            // The job carries a suggested salary, and it is only ever a suggestion: it fills an
            // empty box on a new employee and never overwrites a figure somebody has typed.
            comboJob.getSelectionModel().selectedItemProperty().addListener((source, was, now) -> {
                if (employeeId == 0 && now != null && now.defaultSalary() != null
                        && isBlankAmount(txtRate.getText())) {
                    txtRate.setText(now.defaultSalary().toPlainString());
                }
            });
        } catch (Exception e) {
            AllAlerts.handleError(text("employee.error.operation"), asException(e));
        }
    }

    private static boolean isBlankAmount(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return com.hamza.controlsfx.others.DoubleSetting.parseDoubleOrDefault(value) == 0;
    }

    private void choosePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(text("employee.photo.choose"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                text("employee.photo.filter"), "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(box.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            if (file.length() > PHOTO_MAX_BYTES) {
                throw new UserValidationException(text("employee.error.photo.size"));
            }
            photo = Files.readAllBytes(file.toPath());
            photoChanged = true;
            photoView.setImage(new Image(new ByteArrayInputStream(photo)));
        } catch (Exception e) {
            AllAlerts.handleError(text("employee.error.operation"), asException(e));
        }
    }

    /**
     * Must answer exactly 1 for the dialog to treat the save as done.
     * <p>
     * Anything else leaves the dialog open and {@code afterSaved()} unrun - which is how
     * {@code AddUserController} reported every user it created as a failure, by answering the
     * generated id.
     */
    @Override
    public int insertData() throws Exception {
        EmployeeDraft draft = readForm();
        int id = employeeId;
        if (employeeId == 0) {
            id = employeeService.create(draft);
        } else {
            employeeService.update(draft);
        }
        if (photoChanged && id > 0) {
            employeeService.updatePhoto(id, photo);
        }
        return 1;
    }

    private EmployeeDraft readForm() throws UserValidationException {
        Job job = comboJob.getValue();
        return EmployeeDraft.parse(employeeId, txtName.getText(), job == null ? 0 : job.id(),
                birthDate.getValue(), hireDate.getValue(), endDate.getValue(),
                comboEmployment.getValue(), txtNationalId.getText(), txtEmail.getText(),
                txtPhone.getText(), txtAddress.getText(), txtNotes.getText(), null,
                comboSalaryKind.getValue(), amount(txtRate));
    }

    @Override
    public void afterSaved() {
        if (eventBus != null) {
            eventBus.publish(new EmployeesChanged());
        }
    }

    @Override
    public void selectData() {
        try {
            Employee employee = employeeService.find(employeeId);
            if (employee == null) {
                return;
            }
            txtCode.setText(String.valueOf(employee.id()));
            txtName.setText(employee.name());
            comboEmployment.getSelectionModel().select(employee.employmentType());
            hireDate.setValue(employee.hireDate());
            birthDate.setValue(employee.birthDate());
            endDate.setValue(employee.endDate());
            txtNationalId.setText(employee.nationalId());
            txtPhone.setText(employee.phone());
            txtEmail.setText(employee.email());
            txtAddress.setText(employee.address());
            txtNotes.setText(employee.notes());
            if (employee.salaryKind() != null) {
                comboSalaryKind.getSelectionModel().select(employee.salaryKind());
            }
            if (employee.rate() != null) {
                txtRate.setText(employee.rate().toPlainString());
            }
            selectJob(employee);
            showSalaryLock(employee);
            loadPhoto();
        } catch (Exception e) {
            AllAlerts.handleError(text("employee.error.operation"), asException(e));
        }
    }

    /**
     * The employee's own job, even when it has been stopped.
     * <p>
     * The combo offers the active ones, because a new employee should not be filed under a job
     * nobody holds any more - but an employee already filed under one must not silently lose it
     * when their telephone number is corrected. Same rule as the delegate on a saved invoice.
     */
    private void selectJob(Employee employee) {
        boolean listed = comboJob.getItems().stream().anyMatch(job -> job.id() == employee.jobId());
        if (!listed) {
            comboJob.getItems().add(Job.of(employee.jobId(), employee.jobName()));
        }
        comboJob.getItems().stream()
                .filter(job -> job.id() == employee.jobId())
                .findFirst()
                .ifPresent(job -> comboJob.getSelectionModel().select(job));
    }

    /**
     * Says so, before the save, when the salary box can no longer write.
     * <p>
     * A refusal that arrives only when Save is pressed is a refusal the user has already typed
     * around. And the note names the road that exists - the salary screen - rather than the road
     * {@code opening.correction.customers} named for months and did not.
     */
    private void showSalaryLock(Employee employee) {
        try {
            boolean locked = employeeService.salaryVisible()
                    && employeeService.salaryHistory(employee.id()).size() > 1;
            salaryLockNote.setText(text("employee.salary.locked.note"));
            salaryLockNote.setVisible(locked);
            salaryLockNote.setManaged(locked);
            comboSalaryKind.setDisable(comboSalaryKind.isDisable() || locked);
            txtRate.setEditable(!locked);
        } catch (Exception ignored) {
            // A reader without the salary permission cannot count the history, and does not need
            // to: their salary controls are already disabled.
        }
    }

    private void loadPhoto() {
        try {
            byte[] stored = employeeService.photo(employeeId);
            photo = stored;
            if (stored != null && stored.length > 0) {
                photoView.setImage(new Image(new ByteArrayInputStream(stored)));
            }
        } catch (Exception e) {
            log.warn("Could not read the employee photo", e);
        }
    }

    @Override
    public void resetData() {
        Utils.clearAll(txtName, txtNationalId, txtPhone, txtEmail, txtRate);
        txtAddress.clear();
        txtNotes.clear();
        txtCode.setText(text("item.code.generate"));
        birthDate.setValue(null);
        endDate.setValue(null);
        hireDate.setValue(LocalDate.now());
        photo = null;
        photoChanged = false;
        photoView.setImage(null);
    }

    @Override
    public @org.jetbrains.annotations.NotNull BooleanBinding checkDataToEnableButton() {
        // Bindings.createBooleanBinding, not a chain of or() calls whose result is thrown away -
        // which is what AddNameController did, leaving the save button asking about the name alone.
        return Bindings.createBooleanBinding(
                () -> txtName.getText() == null || txtName.getText().isBlank()
                        || comboJob.getValue() == null || hireDate.getValue() == null,
                txtName.textProperty(), comboJob.valueProperty(), hireDate.valueProperty());
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }

    private static BigDecimal amount(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(com.hamza.controlsfx.others.DoubleSetting
                .parseDoubleOrDefault(value));
    }

    private static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private static Exception asException(Throwable e) {
        return e instanceof Exception exception ? exception : new RuntimeException(e);
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
