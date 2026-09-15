package com.hamza.account.controller.invoice;

import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.features.totals.SavedTotalsFilters;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * The named filters of one totals screen: the picker, save and delete.
 * <p>
 * It was sixty lines inside {@code TotalsController}, sharing a "the controls are being changed by
 * code" flag with the search box and the clear button. Those were three different questions
 * answered by one boolean. The picker's own flag lives here now; what the screen does with a
 * chosen filter is the screen's, through {@link Host}.
 */
final class TotalsSavedFiltersPanel {

    /** What the panel needs from the screen it sits on. */
    interface Host {
        /** The filter on screen now, or empty when it is invalid - the screen has said why. */
        Optional<TotalsSearchCriteria> currentCriteria();

        /** Put a saved filter on the controls and search with it. */
        void apply(TotalsSearchCriteria saved);
    }

    private final ComboBox<String> picker;
    private final Button saveButton;
    private final Button deleteButton;
    private final SavedTotalsFilters store;
    private final Host host;
    /** True while code, not the operator, changes the picker - so a reload is not a choice. */
    private boolean reloading;

    TotalsSavedFiltersPanel(ComboBox<String> picker, Button saveButton, Button deleteButton,
                            SavedTotalsFilters store, Host host) {
        this.picker = picker;
        this.saveButton = saveButton;
        this.deleteButton = deleteButton;
        this.store = store;
        this.host = host;
    }

    void install() {
        reload(null);
        picker.valueProperty().addListener((observable, oldName, name) -> {
            deleteButton.setDisable(name == null);
            if (reloading || name == null) return;
            TotalsSearchCriteria saved = store.get(name);
            if (saved != null) host.apply(saved);
        });
        deleteButton.setDisable(picker.getValue() == null);
        saveButton.setOnAction(event -> saveCurrent());
        deleteButton.setOnAction(event -> deleteSelected());
    }

    /** "Clear everything" leaves no named filter looking chosen. */
    void clearSelection() {
        reloading = true;
        try {
            picker.getSelectionModel().clearSelection();
        } finally {
            reloading = false;
        }
        deleteButton.setDisable(true);
    }

    private void reload(String selectedName) {
        reloading = true;
        try {
            picker.setItems(FXCollections.observableArrayList(store.names()));
            if (selectedName != null && picker.getItems().contains(selectedName)) {
                picker.setValue(selectedName);
            } else {
                picker.getSelectionModel().clearSelection();
            }
        } finally {
            reloading = false;
        }
    }

    private void saveCurrent() {
        Optional<TotalsSearchCriteria> criteria = host.currentCriteria();
        if (criteria.isEmpty()) return;

        LanguageManager language = LanguageManager.getInstance();
        TextInputDialog dialog = new TextInputDialog(picker.getValue());
        dialog.setTitle(language.getString("invoice.search.saved.save.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(language.getString("invoice.search.saved.save.prompt"));
        localize(dialog);
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get().isBlank()) return;

        String name = answer.get().trim();
        if (name.length() > Preferences.MAX_KEY_LENGTH) {
            AllAlerts.handleError(language.getString("invoice.error.action.title"), new UserValidationException(
                    language.getString("invoice.search.saved.name.too.long", Preferences.MAX_KEY_LENGTH)));
            return;
        }
        store.save(name, criteria.get());
        reload(name);
        deleteButton.setDisable(false);
    }

    private void deleteSelected() {
        String name = picker.getValue();
        if (name == null) return;
        store.delete(name);
        reload(null);
        deleteButton.setDisable(true);
    }

    /**
     * A dialog built in code, rather than from an FXML loaded with a bundle, carries JavaFX's own
     * English button labels and the platform's left-to-right orientation - while every other window
     * on this screen is Arabic and right-to-left.
     */
    private static void localize(Dialog<?> dialog) {
        LanguageManager language = LanguageManager.getInstance();
        DialogPane pane = dialog.getDialogPane();
        pane.setNodeOrientation(language.getNodeOrientation());
        for (ButtonType type : pane.getButtonTypes()) {
            String key = switch (type.getButtonData()) {
                case OK_DONE -> "ok";
                case CANCEL_CLOSE -> "cancel";
                default -> null;
            };
            if (key != null && pane.lookupButton(type) instanceof Button button) {
                button.setText(language.getString(key));
            }
        }
    }
}
