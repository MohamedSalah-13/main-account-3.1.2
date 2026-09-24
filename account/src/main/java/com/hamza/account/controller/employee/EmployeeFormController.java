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
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

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
 * Built in code, and laid out for a 1366x768 display: a header saying whose record this is, then
 * two cards side by side - the person, with their picture, and the job with its salary - and a short
 * card under them for the address and the notes. It opened as one grid of fourteen fields in two
 * columns with nothing saying which belonged together, date pickers half again the height of the fields
 * beside them, and a picture column that stood empty down the whole height of the form.
 * <p>
 * What the form sends is decided with the service, not only by what is on screen:
 * <ul>
 *   <li><b>The job is a row</b>, read through {@link EmployeeService#jobsForPicker}: whoever may add
 *       or edit an employee may file one under a job. It read the jobs screen's own list, so a role
 *       given this form without {@code job.show} opened on a refusal and an empty combo, under a save
 *       button that could never enable.</li>
 *   <li><b>The salary is on the form only for a reader who may see one.</b> The two rows are left out
 *       for anybody else, and {@link EmployeeService#update} leaves the salary alone for them - the
 *       empty box used to be read as a salary of zero and written.</li>
 *   <li><b>The picture is saved with a new employee</b>, in the same transaction and under the create
 *       key ({@link EmployeeService#create(EmployeeDraft, byte[])}).</li>
 *   <li><b>A column the form has no control for is carried forward</b>: the default treasury, which
 *       the update names and the form used to send as nothing.</li>
 * </ul>
 */
@Log4j2
@FxmlPath(pathFile = "employee-form.fxml")
public class EmployeeFormController implements AddInterface {

    private static final double PHOTO_SIZE = 104;

    /** A picture is stored in a LONGBLOB and read back on a profile; it is not a place for a scan. */
    private static final long PHOTO_MAX_BYTES = 2L * 1024 * 1024;

    /** The width of a card's field column; two cards and the gaps between them decide the dialog's width. */
    private static final double FIELD_WIDTH = 230;

    /** Leaves room for the title bar and the dialog's own buttons on a 768-tall display. */
    private static final double MAX_FORM_HEIGHT = 600;

    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final int employeeId;

    private final TextField txtName = new TextField();
    private final ComboBox<Job> comboJob = new ComboBox<>();
    private final ComboBox<EmploymentType> comboEmployment = new ComboBox<>();
    private final ComboBox<SalaryKind> comboSalaryKind = new ComboBox<>();
    private final TextField txtRate = new TextField();
    private final DatePicker hireDate = new DatePicker();
    private final DatePicker birthDate = new DatePicker();
    private final DatePicker endDate = new DatePicker();
    private final TextField txtNationalId = new TextField();
    private final TextField txtPhone = new TextField();
    private final TextField txtEmail = new TextField();
    private final TextArea txtAddress = new TextArea();
    private final TextArea txtNotes = new TextArea();
    private final ImageView photoView = new ImageView();
    private final Label codeValue = new Label();
    private final Label salaryNote = new Label();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    private byte[] photo;
    private boolean photoChanged;
    private boolean salaryVisible;
    /** Not on the form, and named by the update - so it goes back as it was read. */
    private Integer defaultTreasuryId;

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
        stackPane.getStyleClass().addAll("screen-employees", "employee-form");
        salaryVisible = employeeService.salaryVisible();

        codeValue.setText(text("item.code.generate"));
        txtName.setPromptText(text("name"));
        txtNationalId.setPromptText(text("employee.column.national"));
        txtPhone.setPromptText(text("column.tel"));
        txtEmail.setPromptText(text("column.email"));
        txtAddress.setPromptText(text("column.address"));
        txtAddress.setPrefRowCount(2);
        txtAddress.setWrapText(true);
        txtNotes.setPromptText(text("column.notes"));
        txtNotes.setPrefRowCount(2);
        txtNotes.setWrapText(true);
        // A text area asks for forty columns by default, and two of them side by side decided the
        // dialog's width. They take what the cards above leave them instead.
        txtAddress.setPrefColumnCount(12);
        txtNotes.setPrefColumnCount(12);

        comboJob.setConverter(converter(job -> job == null ? "" : job.name()));
        comboJob.setPromptText(text("employee.column.job"));
        comboEmployment.getItems().setAll(EmploymentType.values());
        comboEmployment.setConverter(converter(type -> type == null ? "" : text(type.messageKey())));
        comboEmployment.getSelectionModel().select(EmploymentType.FULL_TIME);
        comboSalaryKind.getItems().setAll(SalaryKind.values());
        comboSalaryKind.setConverter(converter(kind -> kind == null ? "" : text(kind.messageKey())));
        comboSalaryKind.getSelectionModel().select(SalaryKind.MONTHLY);

        // The format every other screen writes a date in. The hire date is being entered, so it
        // starts on today; a birth date and a leaving date are not known until somebody says so.
        // All three stay typeable: a birth date forty years back is not found by paging a calendar.
        DateSetting.dateAction(hireDate);
        DateSetting.dateFilter(birthDate);
        DateSetting.dateFilter(endDate);
        for (DatePicker picker : List.of(hireDate, birthDate, endDate)) {
            picker.setEditable(true);
            picker.setMaxWidth(Double.MAX_VALUE);
        }

        // An amount being entered, so this one does take the number formatter that seeds 0.0.
        setTextFormatter(txtRate);

        salaryNote.getStyleClass().add("form-hint");
        salaryNote.setWrapText(true);
        salaryNote.setMaxWidth(FIELD_WIDTH + 110);
        showNote(salaryVisible ? null : text("employee.form.salary.hidden"));

        // The picture is only offered to somebody who may write it: on a new employee that is the
        // create key, since the picture is saved with the row; on an existing one, the update key.
        boolean mayEdit = AuthorizationGuard.isGranted(employeeId > 0
                ? AppPermissions.EMPLOYEE_UPDATE : AppPermissions.EMPLOYEE_CREATE);

        ScrollPane scroll = new ScrollPane(body(mayEdit));
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        // A ceiling, not a floor: only a display shorter than the form scrolls it.
        scroll.setMaxHeight(MAX_FORM_HEIGHT);
        box.getChildren().setAll(header(), scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // The Enter order, declared once, in the order the form is read - rule ق-ل9.
        List<Control> order = new ArrayList<>(List.of(txtName, txtNationalId, txtPhone, txtEmail));
        if (salaryVisible) {
            order.add(txtRate);
        }
        whenEnterPressed(order.toArray(Control[]::new));
        Platform.runLater(txtName::requestFocus);
        loadJobs();
    }

    // ---- the layout -----------------------------------------------------------------------------

    /** Whose record this is: a new one, or which code. */
    private HBox header() {
        HBox iconBox = new HBox(AppIcon.EMPLOYEES.graphic(28));
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("party-screen-icon-box");

        Label title = new Label(text(employeeId > 0 ? "employee.form.title.edit" : "employee.form.title.new"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("employee.form.subtitle"));
        subtitle.getStyleClass().add("party-screen-subtitle");
        subtitle.setWrapText(true);
        VBox titles = new VBox(2, title, subtitle);
        titles.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titles, Priority.ALWAYS);

        // The code is a label of its own rather than part of a sentence: a number after an Arabic
        // word is drawn on whichever side the paragraph decides.
        Label codeCaption = new Label(text("code"));
        codeCaption.getStyleClass().add("party-screen-subtitle");
        codeValue.getStyleClass().add("employee-form-code");
        VBox code = new VBox(0, codeCaption, codeValue);
        code.setAlignment(Pos.CENTER);
        code.getStyleClass().add("employee-form-code-box");

        HBox header = new HBox(12, iconBox, titles, code);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getStyleClass().addAll("party-screen-header", "employee-form-header");
        return header;
    }

    private VBox body(boolean mayEdit) {
        GridPane personalFields = grid(
                row("name", txtName, true),
                row("employee.column.national", txtNationalId, false),
                row("employee.column.birth", birthDate, false),
                row("column.tel", txtPhone, false),
                row("column.email", txtEmail, false));
        HBox personalBody = new HBox(14, personalFields, photoBox(mayEdit));
        HBox.setHgrow(personalFields, Priority.ALWAYS);
        VBox personal = card(text("employee.form.section.personal"), personalBody);

        List<Node[]> workRows = new ArrayList<>(List.of(
                row("employee.column.job", comboJob, true),
                row("employee.column.employment", comboEmployment, false),
                row("employee.column.hire", hireDate, true),
                row("employee.column.end", endDate, false)));
        if (salaryVisible) {
            workRows.add(row("employee.column.salary.kind", comboSalaryKind, false));
            workRows.add(row("employee.column.rate", txtRate, false));
        }
        GridPane workFields = grid(workRows.toArray(Node[][]::new));
        workFields.add(salaryNote, 0, workRows.size(), 2, 1);
        VBox work = card(text(salaryVisible ? "employee.form.section.work" : "employee.form.section.job"),
                workFields);

        HBox columns = new HBox(12, personal, work);
        HBox.setHgrow(personal, Priority.ALWAYS);
        HBox.setHgrow(work, Priority.ALWAYS);

        // One row, the captions beside their boxes: the card is two lines of text tall, not four.
        GridPane more = new GridPane();
        more.setHgap(10);
        more.addRow(0, caption("column.address"), txtAddress, caption("column.notes"), txtNotes);
        ColumnConstraints side = new ColumnConstraints();
        side.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints wide = new ColumnConstraints();
        wide.setHgrow(Priority.ALWAYS);
        more.getColumnConstraints().addAll(side, wide, side, wide);

        // No heading of its own: its two captions already say what it holds, and a heading is a
        // line of height the form cannot spare on a 768-tall display.
        VBox moreCard = new VBox(more);
        moreCard.getStyleClass().addAll("app-card", "party-form-card");
        moreCard.setPadding(new Insets(12));

        VBox body = new VBox(10, columns, moreCard);
        body.setPadding(new Insets(10, 2, 2, 2));
        return body;
    }

    private VBox card(String title, Node content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("app-section-title");
        VBox card = new VBox(8, heading, content);
        card.getStyleClass().addAll("app-card", "party-form-card");
        card.setPadding(new Insets(10, 12, 12, 12));
        return card;
    }

    private static GridPane grid(Node[]... rows) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        for (int index = 0; index < rows.length; index++) {
            grid.add(rows[index][0], 0, index);
            grid.add(rows[index][1], 1, index);
        }
        ColumnConstraints captions = new ColumnConstraints();
        captions.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setPrefWidth(FIELD_WIDTH);
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(captions, fields);
        return grid;
    }

    /** A caption and its control, the caption marked when the save cannot do without it. */
    private static Node[] row(String key, Control control, boolean required) {
        control.setMaxWidth(Double.MAX_VALUE);
        Label caption = caption(key);
        if (!required) {
            return new Node[]{caption, control};
        }
        Label mark = new Label("*");
        mark.getStyleClass().add("form-required-mark");
        HBox captioned = new HBox(3, caption, mark);
        captioned.setAlignment(Pos.CENTER_LEFT);
        return new Node[]{captioned, control};
    }

    private static Label caption(String key) {
        Label caption = new Label(text(key));
        caption.getStyleClass().add("form-label");
        return caption;
    }

    private VBox photoBox(boolean mayEdit) {
        photoView.setFitWidth(PHOTO_SIZE);
        photoView.setFitHeight(PHOTO_SIZE);
        photoView.setPreserveRatio(true);
        Rectangle clip = new Rectangle(PHOTO_SIZE, PHOTO_SIZE);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        photoView.setClip(clip);

        Node placeholder = AppIcon.EMPLOYEES.graphic(40);
        placeholder.visibleProperty().bind(photoView.imageProperty().isNull());
        StackPane frame = new StackPane(placeholder, photoView);
        frame.getStyleClass().add("employee-photo-frame");
        frame.setMinSize(PHOTO_SIZE + 8, PHOTO_SIZE + 8);
        frame.setMaxSize(PHOTO_SIZE + 8, PHOTO_SIZE + 8);

        Button choose = new Button(text("employee.photo.choose"), AppIcon.ADD.graphic());
        choose.getStyleClass().add("app-neutral-button");
        choose.setMaxWidth(Double.MAX_VALUE);
        choose.setOnAction(event -> choosePhoto());
        choose.setDisable(!mayEdit);
        Button clear = new Button(text("employee.photo.clear"), AppIcon.DELETE.graphic());
        clear.getStyleClass().add("app-neutral-button");
        clear.setMaxWidth(Double.MAX_VALUE);
        clear.setOnAction(event -> {
            photo = null;
            photoChanged = true;
            photoView.setImage(null);
        });
        clear.disableProperty().bind(photoView.imageProperty().isNull().or(Bindings.createBooleanBinding(() -> !mayEdit)));

        VBox pane = new VBox(8, frame, choose, clear);
        pane.setAlignment(Pos.TOP_CENTER);
        pane.setMinWidth(PHOTO_SIZE + 24);
        pane.setMaxWidth(PHOTO_SIZE + 40);
        return pane;
    }

    /** The one line under the salary: why it cannot be changed here, why it is not shown, or nothing. */
    private void showNote(String note) {
        salaryNote.setText(note == null ? "" : note);
        salaryNote.setVisible(note != null);
        salaryNote.setManaged(note != null);
    }

    // ---- the data -------------------------------------------------------------------------------

    private void loadJobs() {
        try {
            comboJob.setItems(FXCollections.observableArrayList(
                    employeeService.jobsForPicker(EmployeeScope.ACTIVE_ONLY)));
            // The job carries a suggested salary, and it is only ever a suggestion: it fills an
            // empty box on a new employee and never overwrites a figure somebody has typed.
            comboJob.getSelectionModel().selectedItemProperty().addListener((source, was, now) -> {
                if (employeeId == 0 && now != null && now.defaultSalary() != null
                        && isBlankAmount(txtRate.getText())) {
                    txtRate.setText(now.defaultSalary().toPlainString());
                }
            });
        } catch (Exception e) {
            AllAlerts.handleError(text("employee.error.operation"), e);
        }
    }

    private static boolean isBlankAmount(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return DoubleSetting.parseDoubleOrDefault(value) == 0;
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
            byte[] chosen = Files.readAllBytes(file.toPath());
            // A file named .png that is not a picture drew nothing and was stored all the same.
            Image image = new Image(new ByteArrayInputStream(chosen));
            if (image.isError() || image.getWidth() <= 0) {
                throw new UserValidationException(text("employee.error.photo.invalid"));
            }
            photo = chosen;
            photoChanged = true;
            photoView.setImage(image);
        } catch (Exception e) {
            AllAlerts.handleError(text("employee.error.operation"), e);
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
        if (employeeId == 0) {
            employeeService.create(draft, photo);
            return 1;
        }
        employeeService.update(draft);
        if (photoChanged) {
            employeeService.updatePhoto(employeeId, photo);
        }
        return 1;
    }

    private EmployeeDraft readForm() throws UserValidationException {
        Job job = comboJob.getValue();
        return EmployeeDraft.parse(employeeId, txtName.getText(), job == null ? 0 : job.id(),
                birthDate.getValue(), hireDate.getValue(), endDate.getValue(),
                comboEmployment.getValue(), txtNationalId.getText(), txtEmail.getText(),
                txtPhone.getText(), txtAddress.getText(), txtNotes.getText(), defaultTreasuryId,
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
            codeValue.setText(String.valueOf(employee.id()));
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
            defaultTreasuryId = employee.defaultTreasuryId();
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
            AllAlerts.handleError(text("employee.error.operation"), e);
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
        if (!salaryVisible) {
            return;
        }
        try {
            boolean locked = employeeService.salaryHistory(employee.id()).size() > 1;
            comboSalaryKind.setDisable(locked);
            txtRate.setEditable(!locked);
            showNote(locked ? text("employee.salary.locked.note") : null);
        } catch (Exception e) {
            log.warn("Could not read the salary history", e);
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
        codeValue.setText(text("item.code.generate"));
        birthDate.setValue(null);
        endDate.setValue(null);
        hireDate.setValue(LocalDate.now());
        defaultTreasuryId = null;
        photo = null;
        photoChanged = false;
        photoView.setImage(null);
    }

    @Override
    public @NotNull BooleanBinding checkDataToEnableButton() {
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
                : BigDecimal.valueOf(DoubleSetting.parseDoubleOrDefault(value));
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

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
