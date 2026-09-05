package com.hamza.account.controller.search;

import com.hamza.account.model.base.BaseNames;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.PopupControl;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Type-as-you-go customer or supplier search for an invoice header.
 *
 * <p>The invoice reacts to {@link #chosenNameProperty()}, not this field's text:
 * typing must not select a party or recalculate its price tier. A party is selected
 * only by clicking a suggestion or confirming the highlighted row with Enter.
 */
public final class PartySuggestionField<T extends BaseNames> extends TextField {

    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "party-suggestion-search");
        thread.setDaemon(true);
        return thread;
    });
    private static final Duration DEBOUNCE = Duration.millis(160);
    private static final int VISIBLE_ROWS = 8;
    private static final double MINIMUM_POPUP_WIDTH = 500;

    private final SearchInterface<T> search;
    private final PopupControl popup = new PopupControl();
    private final ListView<T> suggestions = new ListView<>();
    private final PauseTransition debounce = new PauseTransition(DEBOUNCE);
    private final ReadOnlyObjectWrapper<T> chosenParty = new ReadOnlyObjectWrapper<>();
    private final StringProperty chosenName = new SimpleStringProperty();
    private long queryToken;
    private boolean settingTextInternally;

    public PartySuggestionField(SearchInterface<T> search) {
        this.search = Objects.requireNonNull(search, "search");
        setPromptText(LanguageManager.getInstance().getString("invoice.quick.party.search.prompt"));
        getStyleClass().add("party-suggestion-field");
        configurePopup();
        configureTyping();
        configureKeys();
        configureChosenName();
    }

    public ReadOnlyObjectProperty<T> chosenPartyProperty() {
        return chosenParty.getReadOnlyProperty();
    }

    public StringProperty chosenNameProperty() {
        return chosenName;
    }

    private void configurePopup() {
        suggestions.setFixedCellSize(30);
        suggestions.setCellFactory(view -> new PartySuggestionCell<>(search));
        suggestions.setFocusTraversable(false);
        suggestions.getStyleClass().addAll("party-suggestion-list", "root");
        suggestions.setOnMouseClicked(event -> choose(suggestions.getSelectionModel().getSelectedItem()));
        popup.getScene().setRoot(suggestions);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(true);
        focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (!focused) {
                hideSuggestions();
            }
        });
    }

    private void configureTyping() {
        textProperty().addListener((observable, oldText, text) -> {
            if (settingTextInternally) {
                return;
            }
            if (chosenParty.get() != null) {
                chosenParty.set(null);
            }
            if (text == null || text.isBlank()) {
                debounce.stop();
                hideSuggestions();
                return;
            }
            debounce.playFromStart();
        });
        debounce.setOnFinished(event -> runSearch(getText()));
    }

    private void configureKeys() {
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.DOWN) {
                if (popup.isShowing()) {
                    moveSelection(1);
                } else {
                    debounce.stop();
                    runSearch(getText());
                }
                event.consume();
            } else if (event.getCode() == KeyCode.UP && popup.isShowing()) {
                moveSelection(-1);
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER && popup.isShowing()) {
                choose(suggestions.getSelectionModel().getSelectedItem());
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && popup.isShowing()) {
                hideSuggestions();
                event.consume();
            } else if (event.getCode() == KeyCode.TAB) {
                hideSuggestions();
            }
        });
    }

    private void configureChosenName() {
        chosenName.addListener((observable, oldName, name) -> {
            String text = name == null ? "" : name;
            if (!text.equals(getText())) {
                settingTextInternally = true;
                try {
                    setText(text);
                } finally {
                    settingTextInternally = false;
                }
            }
            if (text.isBlank()) {
                chosenParty.set(null);
                hideSuggestions();
            }
        });
    }

    private void runSearch(String text) {
        if (text == null || text.isBlank()) {
            hideSuggestions();
            return;
        }
        long token = ++queryToken;
        String query = text.trim();
        CompletableFuture.supplyAsync(() -> {
            try {
                return search.getFilterItems(query);
            } catch (Exception ignored) {
                return List.<T>of();
            }
        }, SEARCH_EXECUTOR).thenAccept(found -> Platform.runLater(() -> publish(token, found)));
    }

    private void publish(long token, List<T> found) {
        if (token != queryToken || !isFocused() || found == null || found.isEmpty()) {
            hideSuggestions();
            return;
        }
        suggestions.setItems(FXCollections.observableArrayList(found));
        suggestions.getSelectionModel().selectFirst();
        showSuggestions(found.size());
    }

    private void showSuggestions(int count) {
        if (getScene() == null || getScene().getWindow() == null) {
            return;
        }
        suggestions.setPrefWidth(Math.max(getWidth(), MINIMUM_POPUP_WIDTH));
        suggestions.setPrefHeight(Math.min(count, VISIBLE_ROWS) * suggestions.getFixedCellSize() + 4);
        Bounds bounds = localToScreen(getBoundsInLocal());
        if (bounds == null) {
            return;
        }
        if (!popup.isShowing()) {
            popup.show(this, bounds.getMinX(), bounds.getMaxY());
            popup.getScene().getStylesheets().setAll(getScene().getStylesheets());
            popup.getScene().setNodeOrientation(getScene().getNodeOrientation());
        } else {
            popup.setX(bounds.getMinX());
            popup.setY(bounds.getMaxY());
        }
    }

    private void moveSelection(int step) {
        int size = suggestions.getItems().size();
        if (size == 0) {
            return;
        }
        int index = Math.floorMod(suggestions.getSelectionModel().getSelectedIndex() + step, size);
        suggestions.getSelectionModel().select(index);
        suggestions.scrollTo(index);
    }

    private void choose(T party) {
        if (party == null) {
            return;
        }
        hideSuggestions();
        chosenParty.set(party);
        settingTextInternally = true;
        try {
            setText(search.getName(party));
            positionCaret(getText().length());
        } finally {
            settingTextInternally = false;
        }
        chosenName.set(search.getName(party));
    }

    private void hideSuggestions() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    private static final class PartySuggestionCell<T extends BaseNames> extends ListCell<T> {
        private final SearchInterface<T> search;
        private final Label name = new Label();
        private final Label contact = new Label();
        private final HBox layout;

        private PartySuggestionCell(SearchInterface<T> search) {
            this.search = search;
            name.getStyleClass().add("party-suggestion-name");
            contact.getStyleClass().add("party-suggestion-contact");
            name.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            name.setTextOverrun(OverrunStyle.ELLIPSIS);
            HBox.setHgrow(name, Priority.ALWAYS);
            layout = new HBox(10, name, contact);
            layout.setAlignment(Pos.CENTER_LEFT);
            layout.setMinWidth(0);
            setMinWidth(0);
            setPrefWidth(1);
        }

        @Override
        protected void updateItem(T party, boolean empty) {
            super.updateItem(party, empty);
            if (empty || party == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            name.setText(search.getName(party));
            String telephone = party.getTel() == null ? "" : party.getTel();
            contact.setText(telephone.isBlank() ? String.valueOf(party.getId())
                    : party.getId() + " | " + telephone);
            setText(null);
            setGraphic(layout);
        }
    }
}
