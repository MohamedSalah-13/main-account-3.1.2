package com.hamza.account.controller.setting;

import com.hamza.account.features.shortcuts.SidebarShortcut;
import com.hamza.account.features.shortcuts.SidebarShortcutManager;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;

@FxmlPath(pathFile = "include/settingTabShortcuts.fxml")
public final class SettingTabShortcutsController implements Initializable {
    @FXML private Label labelTitle, labelDescription, labelCommand, labelShortcut, labelMessage;
    @FXML private TableView<ShortcutRow> table;
    @FXML private TableColumn<ShortcutRow, String> columnCommand, columnShortcut;
    @FXML private TextField txtShortcut;
    @FXML private Button btnReset, btnResetAll;

    private final ObservableList<ShortcutRow> rows = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        var lm = LanguageManager.getInstance();
        labelTitle.setText(lm.getString("settings.shortcuts.title"));
        labelDescription.setText(lm.getString("settings.shortcuts.description"));
        labelCommand.setText(lm.getString("settings.shortcuts.command"));
        labelShortcut.setText(lm.getString("settings.shortcuts.key"));
        columnCommand.setText(lm.getString("settings.shortcuts.command"));
        columnShortcut.setText(lm.getString("settings.shortcuts.key"));
        txtShortcut.setPromptText(lm.getString("settings.shortcuts.capture"));
        btnReset.setText(lm.getString("settings.shortcuts.reset"));
        btnResetAll.setText(lm.getString("settings.shortcuts.resetAll"));

        columnCommand.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().name()));
        columnShortcut.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().combination()));
        table.setItems(rows);
        refreshRows(null);
        txtShortcut.setDisable(true);
        btnReset.setDisable(true);

        table.getSelectionModel().selectedItemProperty().addListener((observable, oldRow, row) -> select(row));
        txtShortcut.addEventFilter(KeyEvent.KEY_PRESSED, this::captureShortcut);
        btnReset.setOnAction(event -> resetSelected());
        btnResetAll.setOnAction(event -> {
            SidebarShortcutManager.resetAll();
            refreshRows(null);
            labelMessage.setText(lm.getString("settings.shortcuts.resetAll.done"));
        });
    }

    private void select(ShortcutRow row) {
        boolean selected = row != null;
        txtShortcut.setDisable(!selected);
        btnReset.setDisable(!selected);
        txtShortcut.setText(selected ? row.combination() : "");
        labelMessage.setText("");
    }

    private void captureShortcut(KeyEvent event) {
        ShortcutRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        event.consume();
        // Ctrl on its own is the first half of a chord, not a shortcut - and asking
        // KeyCodeCombination for one throws: "Key code must not match modifier key!".
        // Thrown from an event filter that reaches the global handler, so pressing Ctrl
        // in this field put an error dialog with a reference code on the screen.
        if (event.getCode().isModifierKey()) {
            return;
        }
        if (event.getCode() == KeyCode.BACK_SPACE || event.getCode() == KeyCode.DELETE) {
            save(selected.shortcut(), "");
            return;
        }
        if (!event.isControlDown() && !event.isAltDown() && !event.isShiftDown() && !event.isMetaDown()) {
            labelMessage.setText(LanguageManager.getInstance().getString("settings.shortcuts.modifier.required"));
            return;
        }
        KeyCombination combination = new KeyCodeCombination(event.getCode(), modifiers(event));
        save(selected.shortcut(), combination.getName());
    }

    private KeyCombination.Modifier[] modifiers(KeyEvent event) {
        var result = FXCollections.<KeyCombination.Modifier>observableArrayList();
        if (event.isControlDown()) result.add(KeyCombination.CONTROL_DOWN);
        if (event.isAltDown()) result.add(KeyCombination.ALT_DOWN);
        if (event.isShiftDown()) result.add(KeyCombination.SHIFT_DOWN);
        if (event.isMetaDown()) result.add(KeyCombination.META_DOWN);
        return result.toArray(KeyCombination.Modifier[]::new);
    }

    private void save(SidebarShortcut shortcut, String combination) {
        var lm = LanguageManager.getInstance();
        var result = SidebarShortcutManager.change(shortcut, combination);
        if (result == SidebarShortcutManager.ChangeResult.SAVED) {
            refreshRows(shortcut);
            labelMessage.setText(lm.getString("settings.shortcuts.saved"));
        } else if (result == SidebarShortcutManager.ChangeResult.DUPLICATE) {
            labelMessage.setText(lm.getString("settings.shortcuts.duplicate"));
        } else if (result == SidebarShortcutManager.ChangeResult.RESERVED) {
            labelMessage.setText(lm.getString("settings.shortcuts.reserved"));
        } else {
            labelMessage.setText(lm.getString("settings.shortcuts.invalid"));
        }
    }

    private void resetSelected() {
        ShortcutRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        SidebarShortcutManager.reset(selected.shortcut());
        refreshRows(selected.shortcut());
        labelMessage.setText(LanguageManager.getInstance().getString("settings.shortcuts.saved"));
    }

    private void refreshRows(SidebarShortcut selectedShortcut) {
        rows.setAll(Arrays.stream(SidebarShortcut.values())
                .map(shortcut -> new ShortcutRow(shortcut, SidebarShortcutManager.displayName(shortcut), SidebarShortcutManager.combination(shortcut)))
                .toList());
        if (selectedShortcut != null) {
            rows.stream().filter(row -> row.shortcut() == selectedShortcut).findFirst()
                    .ifPresent(row -> table.getSelectionModel().select(row));
        }
    }

    private record ShortcutRow(SidebarShortcut shortcut, String name, String combination) { }
}