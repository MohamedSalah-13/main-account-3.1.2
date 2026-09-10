package com.hamza.account.controller.productprofile;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.features.productprofile.FeatureKey;
import com.hamza.account.features.productprofile.JdbcProductProfileRepository;
import com.hamza.account.features.productprofile.ProductFeatureDefinition;
import com.hamza.account.features.productprofile.ProductFeatureCatalog;
import com.hamza.account.features.productprofile.ProductProfile;
import com.hamza.account.features.productprofile.ProductProfileCodec;
import com.hamza.account.features.productprofile.ProductProfileDraft;
import com.hamza.account.features.productprofile.ProductProfileException;
import com.hamza.account.features.productprofile.ProductProfileService;
import com.hamza.account.features.productprofile.ProductProfileSigner;
import com.hamza.account.features.productprofile.ProductEditionPreset;
import com.hamza.account.features.productprofile.ProductEditionPresets;
import com.hamza.account.features.productprofile.ProductProfileSummaryFormatter;
import com.hamza.account.service.version.DatabaseMigrationService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/** Thin JavaFX boundary; signing, validation and persistence live in productprofile services. */
public final class ProductProfileSetupController {

    private static final String APPLIED_BY = "AccountK-Product-Setup";

    @FXML private ScrollPane root;
    @FXML private TextField customerField;
    @FXML private TextField profileNameField;
    @FXML private ComboBox<ProductEditionPreset> presetBox;
    @FXML private Label presetDescriptionLabel;
    @FXML private TextField featureSearchField;
    @FXML private VBox featureList;
    @FXML private Label privateKeyPathLabel;
    @FXML private Label profilePathLabel;
    @FXML private Label profileSummaryLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator progress;
    @FXML private Button choosePrivateKeyButton;
    @FXML private Button exportButton;
    @FXML private Button exportSummaryButton;
    @FXML private Button chooseProfileButton;
    @FXML private Button applyButton;

    private final ProductFeatureCatalog catalog = ProductFeatureCatalog.standard();
    private final ProductProfileCodec codec = ProductProfileCodec.trustedReleaseKey(catalog);
    private final ProductProfileSigner signer = new ProductProfileSigner(codec);
    private final Map<FeatureKey, CheckBox> featureBoxes = new LinkedHashMap<>();
    private final Map<FeatureKey, FeatureRow> featureRows = new LinkedHashMap<>();
    private final Map<String, VBox> categoryPanels = new LinkedHashMap<>();
    private final Map<String, Label> categoryCountLabels = new LinkedHashMap<>();
    private boolean applyingPreset;

    private Path privateKeyFile;
    private Path selectedProfileFile;
    private String selectedEnvelope;

    @FXML
    private void initialize() {
        renderFeatureCatalog();
        configurePresets();
        choosePrivateKeyButton.setGraphic(AppIcon.SECURITY.graphic());
        exportButton.setGraphic(AppIcon.EXPORT.graphic());
        exportSummaryButton.setGraphic(AppIcon.SPREADSHEET.graphic());
        chooseProfileButton.setGraphic(AppIcon.SEARCH.graphic());
        applyButton.setGraphic(AppIcon.CONFIRM.graphic());
        featureSearchField.textProperty().addListener((observable, oldValue, query) -> filterFeatures(query));
        whenEnterPressed(presetBox, customerField, profileNameField, exportButton);
    }

    @FXML
    private void choosePrivateKey() {
        FileChooser chooser = chooser("product.profile.setup.key.chooser",
                "product.profile.setup.key.file.type", "*.pem");
        File chosen = chooser.showOpenDialog(root.getScene().getWindow());
        if (chosen == null) return;
        privateKeyFile = chosen.toPath();
        privateKeyPathLabel.setText(privateKeyFile.toAbsolutePath().toString());
        clearStatus();
    }

    @FXML
    private void exportProfile() {
        if (privateKeyFile == null) {
            showStatus("product.profile.error.private.key.required", "status-error");
            return;
        }
        ProductProfileDraft draft = new ProductProfileDraft(
                customerField.getText(), profileNameField.getText(), selectedFeatures(), Instant.now());
        Path signingKey = privateKeyFile;
        FileChooser chooser = chooser("product.profile.setup.export.chooser",
                "product.profile.setup.profile.file.type", "*.akprofile");
        chooser.setInitialFileName(safeFileName(customerField.getText()) + ".akprofile");
        File chosen = chooser.showSaveDialog(root.getScene().getWindow());
        if (chosen == null) return;

        run(() -> {
            String envelope = signer.sign(draft, signingKey);
            Files.writeString(chosen.toPath(), envelope, StandardCharsets.US_ASCII);
            return new SelectedProfile(chosen.toPath(), envelope, codec.decode(envelope));
        }, profile -> {
            select(profile);
            showStatus("product.profile.setup.export.success", "status-success", profile.path().toAbsolutePath());
        });
    }

    @FXML
    private void chooseProfile() {
        FileChooser chooser = chooser("product.profile.setup.import.chooser",
                "product.profile.setup.profile.file.type", "*.akprofile");
        File chosen = chooser.showOpenDialog(root.getScene().getWindow());
        if (chosen == null) return;

        run(() -> {
            String envelope = Files.readString(chosen.toPath(), StandardCharsets.US_ASCII).strip();
            return new SelectedProfile(chosen.toPath(), envelope, codec.decode(envelope));
        }, profile -> {
            select(profile);
            showStatus("product.profile.setup.import.ready", "status-success");
        });
    }

    @FXML
    private void applyProfile() {
        if (selectedEnvelope == null || selectedProfileFile == null) {
            showStatus("product.profile.error.profile.required", "status-error");
            return;
        }
        String envelope = selectedEnvelope;
        run(() -> {
            try {
                ConnectionToDatabase connection = new ConnectionToDatabase();
                new DatabaseMigrationService(connection).updateDatabaseIfNeeded();
                ProductProfileService service = new ProductProfileService(
                        new JdbcProductProfileRepository(), codec, catalog);
                return service.apply(envelope, APPLIED_BY);
            } finally {
                DataSourceProvider.shutdown();
            }
        }, profile -> showStatus("product.profile.setup.apply.success", "status-success",
                profile.customerName(), profile.profileName()));
    }

    @FXML
    private void exportSummary() {
        Instant generatedAt = Instant.now();
        Set<FeatureKey> enabled = selectedFeatures();
        try {
            // Reuse the signed-profile validation rules so the delivery record can
            // never describe a profile that the signing path would reject.
            codec.payload(new ProductProfileDraft(customerField.getText(), profileNameField.getText(),
                    enabled, generatedAt));
        } catch (ProductProfileException failure) {
            showStatus(failure.messageKey(), "status-error", failure.arguments());
            return;
        }

        FileChooser chooser = chooser("product.profile.summary.chooser",
                "product.profile.summary.file.type", "*.txt");
        chooser.setInitialFileName(safeFileName(customerField.getText()) + "-profile-summary.txt");
        File chosen = chooser.showSaveDialog(root.getScene().getWindow());
        if (chosen == null) return;

        LanguageManager language = LanguageManager.getInstance();
        String summary = ProductProfileSummaryFormatter.format(
                customerField.getText(), profileNameField.getText(), generatedAt, enabled, catalog,
                language::getString, language.getCurrentLocale(), ZoneId.systemDefault());
        run(() -> {
            Files.writeString(chosen.toPath(), summary, StandardCharsets.UTF_8);
            return chosen.toPath();
        }, path -> showStatus("product.profile.summary.export.success", "status-success",
                path.toAbsolutePath()));
    }

    private Set<FeatureKey> selectedFeatures() {
        Set<FeatureKey> selected = new LinkedHashSet<>();
        featureBoxes.forEach((key, checkBox) -> {
            if (checkBox.isSelected()) selected.add(key);
        });
        return selected;
    }

    private void renderFeatureCatalog() {
        for (ProductFeatureDefinition definition : catalog.definitions()) {
            VBox categoryPanel = categoryPanels.computeIfAbsent(
                    definition.categoryKey(), this::createCategoryPanel);

            CheckBox feature = new CheckBox(
                    LanguageManager.getInstance().getString(definition.titleKey()));
            feature.setSelected(true);
            feature.selectedProperty().addListener((observable, oldValue, newValue) -> {
                markCustomPreset();
                refreshCategoryCount(definition.categoryKey());
            });
            Label explanation = new Label(
                    LanguageManager.getInstance().getString(definition.descriptionKey()));
            explanation.setWrapText(true);
            explanation.getStyleClass().add("text-explain");
            VBox row = new VBox(4, feature, explanation);
            row.getStyleClass().add("feature-row");
            categoryPanel.getChildren().add(row);
            featureBoxes.put(definition.key(), feature);
            featureRows.put(definition.key(), new FeatureRow(definition, row));
        }
        categoryPanels.keySet().forEach(this::refreshCategoryCount);
    }

    private VBox createCategoryPanel(String categoryKey) {
        Label title = new Label(LanguageManager.getInstance().getString(categoryKey));
        title.getStyleClass().add("feature-category");
        Label count = new Label();
        count.getStyleClass().add("feature-count");
        categoryCountLabels.put(categoryKey, count);

        Button selectAll = new Button(LanguageManager.getInstance().getString("product.profile.category.select.all"));
        selectAll.setGraphic(AppIcon.SELECT_ALL.graphic());
        selectAll.getStyleClass().add("secondary-button");
        selectAll.setOnAction(event -> setCategorySelected(categoryKey, true));
        Button clearAll = new Button(LanguageManager.getInstance().getString("product.profile.category.clear.all"));
        clearAll.setGraphic(AppIcon.CLEAR.graphic());
        clearAll.getStyleClass().add("secondary-button");
        clearAll.setOnAction(event -> setCategorySelected(categoryKey, false));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, count, spacer, selectAll, clearAll);
        header.getStyleClass().add("feature-category-header");
        VBox panel = new VBox(8, header);
        panel.getStyleClass().add("feature-category-panel");
        featureList.getChildren().add(panel);
        return panel;
    }

    private void setCategorySelected(String categoryKey, boolean selected) {
        applyingPreset = true;
        try {
            featureRows.values().stream()
                    .filter(row -> row.definition().categoryKey().equals(categoryKey))
                    .forEach(row -> featureBoxes.get(row.definition().key()).setSelected(selected));
        } finally {
            applyingPreset = false;
        }
        markCustomPreset();
        refreshCategoryCount(categoryKey);
    }

    private void refreshCategoryCount(String categoryKey) {
        Label count = categoryCountLabels.get(categoryKey);
        if (count == null) return;
        long selected = featureRows.values().stream()
                .filter(row -> row.definition().categoryKey().equals(categoryKey))
                .filter(row -> featureBoxes.get(row.definition().key()).isSelected())
                .count();
        long total = featureRows.values().stream()
                .filter(row -> row.definition().categoryKey().equals(categoryKey))
                .count();
        count.setText(LanguageManager.getInstance().getString(
                "product.profile.category.count", selected, total));
    }

    private void filterFeatures(String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        LanguageManager language = LanguageManager.getInstance();
        featureRows.values().forEach(row -> {
            String searchable = (language.getString(row.definition().titleKey()) + " "
                    + language.getString(row.definition().categoryKey())).toLowerCase(Locale.ROOT);
            boolean matches = needle.isEmpty() || searchable.contains(needle);
            row.node().setVisible(matches);
            row.node().setManaged(matches);
        });
        categoryPanels.forEach((category, panel) -> {
            boolean hasMatch = featureRows.values().stream()
                    .filter(row -> row.definition().categoryKey().equals(category))
                    .anyMatch(row -> row.node().isManaged());
            panel.setVisible(hasMatch);
            panel.setManaged(hasMatch);
        });
    }

    private void configurePresets() {
        StringConverter<ProductEditionPreset> converter = new StringConverter<>() {
            @Override
            public String toString(ProductEditionPreset preset) {
                return preset == null ? "" : LanguageManager.getInstance().getString(preset.nameKey());
            }

            @Override
            public ProductEditionPreset fromString(String value) {
                return null;
            }
        };
        presetBox.setConverter(converter);
        presetBox.getItems().setAll(ProductEditionPresets.standard(catalog));
        presetBox.valueProperty().addListener((observable, oldPreset, preset) -> applyPreset(preset));
        presetBox.getSelectionModel().selectFirst();
    }

    private void applyPreset(ProductEditionPreset preset) {
        if (preset == null) return;
        presetDescriptionLabel.setText(LanguageManager.getInstance().getString(preset.descriptionKey()));
        if ("custom".equals(preset.id())) return;

        applyingPreset = true;
        try {
            featureBoxes.forEach((key, box) -> box.setSelected(preset.enabledFeatures().contains(key)));
            profileNameField.setText(LanguageManager.getInstance().getString(preset.nameKey()));
        } finally {
            applyingPreset = false;
        }
    }

    private void markCustomPreset() {
        if (applyingPreset || presetBox.getItems().isEmpty()) return;
        presetBox.getItems().stream()
                .filter(preset -> "custom".equals(preset.id()))
                .findFirst()
                .ifPresent(preset -> presetBox.getSelectionModel().select(preset));
    }

    private void select(SelectedProfile selected) {
        selectedProfileFile = selected.path();
        selectedEnvelope = selected.envelope();
        ProductProfile profile = selected.profile();
        profilePathLabel.setText(selectedProfileFile.toAbsolutePath().toString());
        profileSummaryLabel.setText(LanguageManager.getInstance().getString(
                "product.profile.setup.summary", profile.customerName(), profile.profileName(),
                profile.enabledFeatures().size(), catalog.definitions().size()));
        applyButton.setDisable(false);
    }

    private <T> void run(Callable<T> operation, Consumer<T> succeeded) {
        setBusy(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            succeeded.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false);
            Throwable failure = task.getException();
            if (failure instanceof ProductProfileException profileFailure) {
                showStatus(profileFailure.messageKey(), "status-error", profileFailure.arguments());
            } else {
                showStatus("product.profile.setup.unexpected.error", "status-error");
                AllAlerts.handleError(LanguageManager.getInstance().getString(
                        "product.profile.setup.operation"), failure);
            }
        });
        Thread worker = new Thread(task, "product-profile-setup-operation");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        progress.setManaged(busy);
        choosePrivateKeyButton.setDisable(busy);
        exportButton.setDisable(busy);
        exportSummaryButton.setDisable(busy);
        chooseProfileButton.setDisable(busy);
        applyButton.setDisable(busy || selectedEnvelope == null);
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.getStyleClass().removeAll("status-success", "status-warning", "status-error");
    }

    private void showStatus(String key, String styleClass, Object... arguments) {
        statusLabel.setText(LanguageManager.getInstance().getString(key, arguments));
        statusLabel.getStyleClass().removeAll("status-success", "status-warning", "status-error");
        statusLabel.getStyleClass().add(styleClass);
    }

    private FileChooser chooser(String titleKey, String descriptionKey, String pattern) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(LanguageManager.getInstance().getString(titleKey));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                LanguageManager.getInstance().getString(descriptionKey), pattern));
        return chooser;
    }

    private static String safeFileName(String value) {
        String safe = value == null ? "" : value.strip().replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        return safe.isBlank() ? "accountk-client" : safe;
    }

    private record SelectedProfile(Path path, String envelope, ProductProfile profile) {
    }

    private record FeatureRow(ProductFeatureDefinition definition, VBox node) {
    }
}
