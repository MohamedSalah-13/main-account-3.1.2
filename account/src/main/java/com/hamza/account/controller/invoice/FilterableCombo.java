package com.hamza.account.controller.invoice;

import com.hamza.account.features.totals.ComboFilter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a plain {@code ComboBox<String>} into one you can type into.
 *
 * <p>A picker over a shop's whole customer list offers no way in but scrolling, which is
 * why every long list on this screen was a scroll. Typing now narrows the popup on
 * {@link ComboFilter}'s rule, so the same name found by its Arabic spelling however it
 * happens to have been entered.</p>
 *
 * <p>Three things this has to get right, and each was a defect in the obvious version:</p>
 * <ul>
 *   <li><b>The selection is what the screen reads.</b> {@code TotalsController} asks the
 *       selection model, not the editor, so text left in the editor that matches nothing
 *       must not silently become a filter. On focus loss the editor is put back to the
 *       selected value unless what was typed names exactly one entry, in which case that
 *       one is selected.</li>
 *   <li><b>Replacing the item list clears the selection.</b> So the selected value is
 *       captured before narrowing and restored after, and the whole cycle is guarded by a
 *       flag - otherwise the listener reacts to its own edit and the caret jumps.</li>
 *   <li><b>Escape means "give up", not "choose nothing".</b> It restores the selection
 *       rather than leaving the field half-typed.</li>
 * </ul>
 */
final class FilterableCombo {

    private final ComboBox<String> combo;
    private final List<String> entries = new ArrayList<>();
    private boolean adjusting;

    private FilterableCombo(ComboBox<String> combo) {
        this.combo = combo;
    }

    /** Makes {@code combo} typable. Its entries are supplied by {@link #setEntries}. */
    static FilterableCombo install(ComboBox<String> combo) {
        FilterableCombo filterable = new FilterableCombo(combo);
        filterable.wire();
        return filterable;
    }

    /**
     * Replaces the whole set the field offers, and selects the first of them.
     * <p>
     * Told rather than observed on purpose: the screen refreshes a picker with
     * {@code setItems}, which swaps the {@code ObservableList} instance itself, so a
     * listener on the old one would go quiet exactly when the names changed - and the
     * field would keep narrowing a list nobody could see any more.
     */
    void setEntries(List<String> values) {
        entries.clear();
        entries.addAll(values);
        adjusting = true;
        try {
            combo.getItems().setAll(values);
            combo.getSelectionModel().selectFirst();
        } finally {
            adjusting = false;
        }
        restoreEditor();
    }

    private void wire() {
        entries.addAll(combo.getItems());
        combo.setEditable(true);

        combo.getEditor().textProperty().addListener((observable, oldText, text) -> {
            if (adjusting) return;
            narrowTo(text);
        });

        combo.getEditor().focusedProperty().addListener((observable, was, focused) -> {
            if (!focused) settle();
        });

        combo.getEditor().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                restoreEditor();
                combo.hide();
            } else if (event.getCode() == KeyCode.ENTER) {
                settle();
            }
        });

        combo.valueProperty().addListener((observable, oldValue, value) -> {
            if (!adjusting && value != null) restoreEditor();
        });
    }

    private void narrowTo(String typed) {
        String selected = combo.getSelectionModel().getSelectedItem();
        List<String> shown = ComboFilter.matching(entries, typed);
        adjusting = true;
        try {
            ObservableList<String> items = combo.getItems();
            items.setAll(shown);
            if (selected != null && shown.contains(selected)) {
                combo.getSelectionModel().select(selected);
            }
            combo.getEditor().setText(typed);
            combo.getEditor().positionCaret(typed == null ? 0 : typed.length());
        } finally {
            adjusting = false;
        }
        if (!combo.isShowing() && !shown.isEmpty()) combo.show();
    }

    /**
     * Decides what the field means once the operator stops typing: the one entry their
     * text names, or - when it names none or several - the selection they already had.
     */
    private void settle() {
        String typed = combo.getEditor().getText();
        String sole = ComboFilter.soleMatch(entries, typed);
        if (sole != null) {
            adjusting = true;
            try {
                combo.getItems().setAll(entries);
            } finally {
                adjusting = false;
            }
            combo.getSelectionModel().select(sole);
        }
        restoreEditor();
    }

    /** Puts the field back to the selection and the popup back to the whole list. */
    private void restoreEditor() {
        String selected = combo.getSelectionModel().getSelectedItem();
        adjusting = true;
        try {
            combo.getItems().setAll(entries);
            if (selected != null) combo.getSelectionModel().select(selected);
            combo.getEditor().setText(selected == null ? "" : selected);
        } finally {
            adjusting = false;
        }
        // After the item list is replaced the skin repositions the caret itself, so the
        // correction has to be queued behind it rather than applied inside this pulse.
        Platform.runLater(() -> combo.getEditor().positionCaret(combo.getEditor().getText().length()));
    }
}
