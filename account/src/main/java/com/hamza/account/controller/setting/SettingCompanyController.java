package com.hamza.account.controller.setting;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.company.CompanyLogo;
import com.hamza.account.features.company.CompanyService;
import com.hamza.account.features.events.CompanyChanged;
import com.hamza.account.model.domain.Company;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.TreasuryService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.EventBus;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;

/**
 * The company tab of the settings screen — the name, address, phone, tax and commercial
 * numbers, and the logo that every invoice and report prints.
 *
 * <h2>What the screen is holding</h2>
 * {@code company} is the row as it was last read or written, and {@code logo} is the
 * picture currently on display; together they are what "saved" means, and everything on
 * the form is compared against them to decide whether there is anything to save. The
 * logo is deliberately <em>not</em> read back out of the {@code ImageView} at save time:
 * the view shows a placeholder when there is no logo, and reading the view meant pressing
 * "delete" and then "save" wrote the placeholder into the database rather than clearing
 * the column. See {@link CompanyLogo}.
 *
 * <h2>Threads</h2>
 * Reading and writing the row both reach the database, so both run off the JavaFX thread
 * on a {@link Task}, with the buttons held while they do. Decoding and scaling a chosen
 * picture goes the same way — a photo straight off a phone takes long enough to be felt.
 */
@Log4j2
@FxmlPath(pathFile = "include/settingTabCompany.fxml")
public class SettingCompanyController implements Initializable {

    // The lengths the columns actually have, so "Data too long" is prevented at the
    // keyboard rather than recognised by matching on the text of a SQL error.
    private static final int NAME_LIMIT = 50;
    private static final int TEL_LIMIT = 50;
    private static final int ADDRESS_LIMIT = 100;
    private static final int TAX_LIMIT = 100;
    private static final int COMMERCIAL_LIMIT = 50;

    /**
     * A phone field a shop can put two numbers and an extension in — the old filter took
     * digits only, up to fifteen, so a business with a landline and a mobile could not
     * write both. The column's 50 characters are the real limit.
     */
    private static final String TEL_ALLOWED = "[0-9+\\-/ ]*";

    private static final String DROP_ACTIVE = "logo-drop-active";

    private final CompanyService companyService = ServiceRegistry.get(CompanyService.class);
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Image placeholder = placeholder();

    /**
     * The row as last read or saved — the baseline every "is there anything to save" question is asked against.
     */
    private Company company = new Company();
    /**
     * The logo on display, or null for none. Saved as-is; never re-encoded unless it was just chosen.
     */
    private CompanyLogo logo;
    /**
     * The logo as last read or saved, so cancelling a change is possible and dirty-tracking has a baseline.
     */
    private CompanyLogo savedLogo;
    /**
     * Set while the form is being filled in, so filling it does not read as the user typing.
     */
    private boolean filling;

    @FXML
    private Button btnAddImage, btnSave, btnClearImage, btnReset;
    @FXML
    private ImageView imageView;
    @FXML
    private Label labelName, labelAddress, labelTel, labelTax, labelCom, labelLogoInfo, labelStatus;
    @FXML
    private TextField textAddress, textCom, textNameCompany, textTax, textTel;
    @FXML
    private HBox hbox;
    @FXML
    private VBox logoDropZone;
    @FXML
    private ProgressIndicator progress;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        labelsAndPrompts();
        limitsAndKeyboard();
        actions();
        dragAndDrop();
        buttonGraphic();
        load();
    }

    // ------------------------------------------------------------------
    // Setting the screen up
    // ------------------------------------------------------------------

    private void labelsAndPrompts() {
        labelName.setText(Setting_Language.WORD_NAME);
        labelAddress.setText(Setting_Language.WORD_ADDRESS);
        labelTel.setText(Setting_Language.WORD_TEL);
        labelTax.setText(Setting_Language.WORD_TAX);
        labelCom.setText(Setting_Language.WORD_COMM);

        textNameCompany.setPromptText(Setting_Language.WORD_NAME);
        textAddress.setPromptText(Setting_Language.WORD_ADDRESS);
        textTel.setPromptText(Setting_Language.WORD_TEL);
        textTax.setPromptText(Setting_Language.WORD_TAX);
        textCom.setPromptText(Setting_Language.WORD_COMM);

        btnAddImage.setText(Setting_Language.WORD_CHOOSE_FILE);
        btnClearImage.setText(Setting_Language.WORD_DELETE);
        btnSave.setText(Setting_Language.WORD_SAVE);
        btnReset.setText(Setting_Language.WORD_CANCEL);

        imageView.setPreserveRatio(true);
    }

    private void limitsAndKeyboard() {
        textNameCompany.setTextFormatter(limit(NAME_LIMIT, null));
        textAddress.setTextFormatter(limit(ADDRESS_LIMIT, null));
        textTax.setTextFormatter(limit(TAX_LIMIT, null));
        textCom.setTextFormatter(limit(COMMERCIAL_LIMIT, null));
        textTel.setTextFormatter(limit(TEL_LIMIT, TEL_ALLOWED));

        whenEnterPressed(textNameCompany, textAddress, textTel, textTax, textCom);
        fields().forEach(field -> field.textProperty().addListener((observable, old, value) -> refreshDirty()));
    }

    private void actions() {
        btnSave.setOnAction(event -> save());
        btnReset.setOnAction(event -> fill());
        btnAddImage.setOnAction(event -> chooseLogo());
        btnClearImage.setOnAction(event -> setLogo(null));
        // The preview itself is the largest target on the tab, so it opens the chooser too.
        logoDropZone.setOnMouseClicked(event -> chooseLogo());
    }

    /**
     * Dropping a picture onto the preview is the same as choosing one, which is how
     * anyone who has just downloaded their logo expects to get it in.
     */
    private void dragAndDrop() {
        logoDropZone.setOnDragOver(event -> {
            if (droppedFile(event) != null) {
                event.acceptTransferModes(TransferMode.COPY);
                // Drag-over fires continuously while the pointer moves, so the class is
                // added once rather than once per event.
                if (!logoDropZone.getStyleClass().contains(DROP_ACTIVE)) {
                    logoDropZone.getStyleClass().add(DROP_ACTIVE);
                }
            }
            event.consume();
        });
        logoDropZone.setOnDragExited(event -> {
            logoDropZone.getStyleClass().remove(DROP_ACTIVE);
            event.consume();
        });
        logoDropZone.setOnDragDropped(event -> {
            File file = droppedFile(event);
            if (file != null) {
                readLogo(file);
            }
            logoDropZone.getStyleClass().remove(DROP_ACTIVE);
            event.setDropCompleted(file != null);
            event.consume();
        });
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnClearImage.setGraphic(createIcon(images.erase));
        btnAddImage.setGraphic(createIcon(images.folder));
        btnSave.setGraphic(createIcon(images.save));
        btnReset.setGraphic(createIcon(images.cancel));
    }


    // ------------------------------------------------------------------
    // Reading and writing
    // ------------------------------------------------------------------

    private void load() {
        setBusy(true);
        Task<Loaded> task = new Task<>() {
            @Override
            protected Loaded call() throws DaoException {
                Company loaded = companyService.load();
                return new Loaded(loaded, CompanyLogo.fromStored(loaded.getImage()));
            }
        };
        task.setOnSucceeded(event -> {
            company = task.getValue().company();
            savedLogo = task.getValue().logo();
            fill();
            setBusy(false);
        });
        task.setOnFailed(event -> {
            setBusy(false);
            report("Failed to load the company row", task.getException());
        });
        run(task, "company-load");
    }

    /**
     * Puts the last saved state back on the form — used after a load, and by "إلغاء".
     */
    private void fill() {
        filling = true;
        try {
            textNameCompany.setText(company.getName());
            textTel.setText(company.getTel());
            textAddress.setText(company.getAddress());
            textTax.setText(company.getTax());
            textCom.setText(company.getCommercial());
            logo = savedLogo;
            showLogo();
        } finally {
            filling = false;
        }
        refreshDirty();
    }

    private void save() {
        String name = trimmed(textNameCompany);
        if (name.isEmpty()) {
            AllAlerts.alertError("اسم الشركة مطلوب");
            textNameCompany.requestFocus();
            return;
        }
        if (!AllAlerts.confirmSave()) {
            return;
        }

        Company edited = new Company();
        edited.setId(company.getId());
        edited.setName(name);
        edited.setTel(trimmed(textTel));
        edited.setAddress(trimmed(textAddress));
        edited.setTax(trimmed(textTax));
        edited.setCommercial(trimmed(textCom));
        edited.setImage(CompanyLogo.bytesOf(logo));

        CompanyLogo saving = logo;
        setBusy(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws DaoException {
                companyService.save(edited);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            company = edited;
            savedLogo = saving;
            refreshDirty();
            AllAlerts.alertSave();
            if (eventBus != null) {
                eventBus.publish(new CompanyChanged());
            }
        });
        task.setOnFailed(event -> {
            setBusy(false);
            report("Failed to save the company row", task.getException());
        });
        run(task, "company-save");
    }

    // ------------------------------------------------------------------
    // The logo
    // ------------------------------------------------------------------

    private void chooseLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Setting_Language.WORD_CHOOSE_FILE);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("الصور", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = chooser.showOpenDialog(window());
        if (file != null) {
            readLogo(file);
        }
    }

    /**
     * Reading, decoding and scaling a picture off the FX thread — a phone photo is not instant.
     */
    private void readLogo(File file) {
        setBusy(true);
        Task<CompanyLogo> task = new Task<>() {
            @Override
            protected CompanyLogo call() throws IOException {
                return CompanyLogo.fromFile(file);
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            setLogo(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false);
            report("Failed to read the chosen logo: " + file, task.getException());
        });
        run(task, "company-logo");
    }

    private void setLogo(CompanyLogo chosen) {
        logo = chosen;
        showLogo();
        refreshDirty();
    }

    private void showLogo() {
        imageView.setImage(logo == null ? placeholder : logo.toFxImage());
        labelLogoInfo.setText(logo == null
                ? "لا يوجد شعار — اضغط أو أفلت صورة هنا"
                : logo.summary());
        btnClearImage.setDisable(logo == null);
    }

    private Image placeholder() {
        try {
            var stream = new Image_Setting().defaultBlog;
            return stream == null ? null : new Image(stream);
        } catch (Exception e) {
            log.warn("The default logo image could not be loaded", e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    /**
     * Whether the form differs from what was last read or saved. It is what enables the
     * save and cancel buttons, so nobody has to guess whether their change went in.
     */
    private void refreshDirty() {
        if (filling) {
            return;
        }
        boolean dirty = !trimmed(textNameCompany).equals(orEmpty(company.getName()))
                || !trimmed(textTel).equals(orEmpty(company.getTel()))
                || !trimmed(textAddress).equals(orEmpty(company.getAddress()))
                || !trimmed(textTax).equals(orEmpty(company.getTax()))
                || !trimmed(textCom).equals(orEmpty(company.getCommercial()))
                || !CompanyLogo.sameBytes(logo, savedLogo);

        btnSave.setDisable(!dirty);
        btnReset.setDisable(!dirty);
        labelStatus.setText(dirty ? "تغييرات غير محفوظة" : "لا توجد تغييرات");
        labelStatus.getStyleClass().removeAll("status-dirty", "status-clean");
        labelStatus.getStyleClass().add(dirty ? "status-dirty" : "status-clean");
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        btnAddImage.setDisable(busy);
        btnClearImage.setDisable(busy || logo == null);
        if (busy) {
            btnSave.setDisable(true);
            btnReset.setDisable(true);
        } else {
            refreshDirty();
        }
    }

    private List<TextField> fields() {
        return List.of(textNameCompany, textTel, textAddress, textTax, textCom);
    }

    /**
     * A filter that keeps the field within its column's length, and — where a pattern is
     * given — within the characters the column is meant to hold.
     *
     * <p>A formatter sees a programmatic {@code setText} exactly as it sees typing, so a
     * value already in the database that the filter would refuse — a phone number written
     * before this screen restricted the characters — would be rejected on the way in, and
     * the field would show nothing while the user believed they were looking at the row.
     * Filling the form is therefore let through: what is already stored is stored.
     */
    private TextFormatter<String> limit(int max, String allowed) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (filling) {
                return change;
            }
            String text = change.getControlNewText();
            if (text.length() > max) {
                return null;
            }
            return allowed == null || text.matches(allowed) ? change : null;
        };
        return new TextFormatter<>(filter);
    }

    private File droppedFile(DragEvent event) {
        Dragboard board = event.getDragboard();
        if (!board.hasFiles() || board.getFiles().isEmpty()) {
            return null;
        }
        File file = board.getFiles().getFirst();
        String name = file.getName().toLowerCase(Locale.ROOT);
        boolean picture = name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".bmp");
        return picture && file.isFile() ? file : null;
    }

    private Window window() {
        return imageView.getScene() == null ? null : imageView.getScene().getWindow();
    }

    private String trimmed(TextField field) {
        String text = field.getText();
        return text == null ? "" : text.trim();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private void run(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * A failure the user has to see: logged in full, shown as whatever sentence it carries.
     */
    private void report(String context, Throwable error) {
        log.error(context, error);
        String message = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? context
                : error.getMessage();
        AllAlerts.alertError(message);
    }

    /**
     * The row and its logo, read together off the FX thread.
     */
    private record Loaded(Company company, CompanyLogo logo) {
    }
}
