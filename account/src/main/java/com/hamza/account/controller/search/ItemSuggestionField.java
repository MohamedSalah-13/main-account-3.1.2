package com.hamza.account.controller.search;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * A type-as-you-go item search: one text field that shows matching items in a popup
 * under it and is driven entirely from the keyboard.
 *
 * <p>It replaces the read-only field plus modal table
 * ({@code TextSearchApplication<ItemsModel>} into {@code TableWithTextSearchApplication})
 * that the invoice screens used to search an item by name. That arrangement cost four
 * interactions for every line - click the button, wait for a window, type, pick - and
 * the field itself could not be typed into at all. Here the first keystroke is already
 * the search.
 *
 * <p>Three things make it usable on a real catalogue:
 * <ul>
 *   <li>the query runs on a background thread, never on the JavaFX thread, so a slow
 *       {@code LIKE} cannot freeze the till;</li>
 *   <li>keystrokes are debounced, so typing a ten-letter name is one query and not ten;</li>
 *   <li>every query carries a token and a late answer to a superseded query is dropped,
 *       which is what stops an earlier, slower result from overwriting a later one.</li>
 * </ul>
 *
 * <p>The ranking is the database's: {@code ItemsDao.getFilterItems} already answers
 * exact barcode first, then names starting with the text, then names containing it,
 * over the item's own barcode, its extra barcodes and its units' barcodes alike.
 */
public final class ItemSuggestionField extends TextField {

    /**
     * One shared daemon thread: the searches are serialised on purpose - only the
     * newest answer is ever used, so running them in parallel would just add load.
     */
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "item-suggestion-search");
        thread.setDaemon(true);
        return thread;
    });

    private static final Duration DEBOUNCE = Duration.millis(160);
    private static final int VISIBLE_ROWS = 9;
    /**
     * The popup is never narrower than this, whatever the field is. Sized to the field
     * alone it was too narrow for a row - the balance was cut off the end and the list
     * grew a horizontal scrollbar, which is unusable from the keyboard.
     */
    private static final double MINIMUM_POPUP_WIDTH = 620;

    private final Search search;
    private final Popup popup = new Popup();
    private final ListView<ItemsModel> suggestions = new ListView<>();
    private final PauseTransition debounce = new PauseTransition(DEBOUNCE);
    private final ReadOnlyObjectWrapper<ItemsModel> chosenItem = new ReadOnlyObjectWrapper<>();
    private final StringProperty chosenName = new SimpleStringProperty();
    private final ObjectProperty<ToDoubleFunction<ItemsModel>> price =
            new SimpleObjectProperty<>(ItemsModel::getSelPrice1);

    private Consumer<String> onCreateRequested;
    private long queryToken;
    private boolean settingTextInternally;

    public ItemSuggestionField(Search search) {
        this.search = Objects.requireNonNull(search, "search");
        setPromptText(LanguageManager.getInstance().getString("invoice.quick.item.search.prompt"));
        getStyleClass().add("item-suggestion-field");
        configurePopup();
        configureTyping();
        configureKeys();
        configureChosenName();
    }

    /** The item the user picked, or null while nothing is picked. */
    public ReadOnlyObjectProperty<ItemsModel> chosenItemProperty() {
        return chosenItem.getReadOnlyProperty();
    }

    /**
     * The name of the picked item. It changes <b>only</b> when an item is chosen, or
     * when a caller sets it - never while the user is still typing. That is the contract
     * {@code InvoiceItemEntryCoordinator} listens on: its listener resolves the whole
     * line, so firing it per keystroke would run a lookup for every letter.
     */
    public StringProperty chosenNameProperty() {
        return chosenName;
    }

    /** What to show in the price column of each suggestion - the tier for this screen. */
    public void setPriceResolver(ToDoubleFunction<ItemsModel> resolver) {
        price.set(resolver == null ? ItemsModel::getSelPrice1 : resolver);
    }

    /** Called on F4 with whatever has been typed, so a missing item can be created. */
    public void setOnCreateRequested(Consumer<String> handler) {
        this.onCreateRequested = handler;
    }

    /** Drops the current choice and empties the field without firing a search. */
    public void clearChoice() {
        hideSuggestions();
        chosenItem.set(null);
        chosenName.set(null);
    }

    private void configurePopup() {
        suggestions.setFixedCellSize(30);
        suggestions.setCellFactory(view -> new SuggestionCell(price));
        suggestions.setFocusTraversable(false);
        suggestions.getStyleClass().add("item-suggestion-list");
        suggestions.setOnMouseClicked(event -> choose(suggestions.getSelectionModel().getSelectedItem()));
        // The popup gets its own scene, so the theme file's variables - which are
        // declared on .root - would not resolve inside it. Wearing the class here puts
        // them back in scope for the list and every cell under it.
        suggestions.getStyleClass().add("root");
        popup.getContent().add(suggestions);
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
        debounce.setOnFinished(event -> runSearch(getText()));
        textProperty().addListener((observable, oldText, text) -> {
            if (settingTextInternally) {
                return;
            }
            // Typing away from the picked item means it is no longer what the field says.
            if (chosenItem.get() != null) {
                chosenItem.set(null);
            }
            if (text == null || text.isBlank()) {
                debounce.stop();
                hideSuggestions();
                return;
            }
            debounce.playFromStart();
        });
    }

    private void configureKeys() {
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case DOWN -> {
                    if (popup.isShowing()) {
                        moveSelection(1);
                    } else {
                        debounce.stop();
                        runSearch(getText());
                    }
                    event.consume();
                }
                case UP -> {
                    if (popup.isShowing()) {
                        moveSelection(-1);
                        event.consume();
                    }
                }
                case ENTER -> {
                    if (popup.isShowing()) {
                        // A visible list means Enter is a choice, not "move to the next
                        // field" - otherwise a scanner's trailing Enter would leave the
                        // highlighted row unpicked and carry on.
                        choose(suggestions.getSelectionModel().getSelectedItem());
                        event.consume();
                    }
                }
                case ESCAPE -> {
                    if (popup.isShowing()) {
                        hideSuggestions();
                        event.consume();
                    }
                }
                case F4 -> {
                    if (onCreateRequested != null) {
                        onCreateRequested.accept(getText());
                        event.consume();
                    }
                }
                case TAB -> hideSuggestions();
                default -> {
                }
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
            if (name == null || name.isBlank()) {
                hideSuggestions();
            }
        });
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

    private void runSearch(String text) {
        if (text == null || text.isBlank()) {
            hideSuggestions();
            return;
        }
        final long token = ++queryToken;
        final String query = text.trim();
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return search.find(query);
                    } catch (Exception e) {
                        // A failed lookup is not worth a dialog on every keystroke; an
                        // empty list reads as "nothing matches" and the user types on.
                        return List.<ItemsModel>of();
                    }
                }, SEARCH_EXECUTOR)
                .thenAccept(found -> Platform.runLater(() -> publish(token, found)));
    }

    /** Late answers to superseded queries are dropped - see the class comment. */
    private void publish(long token, List<ItemsModel> found) {
        if (token != queryToken || !isFocused()) {
            return;
        }
        if (found == null || found.isEmpty()) {
            hideSuggestions();
            return;
        }
        suggestions.setItems(FXCollections.observableArrayList(found));
        suggestions.getSelectionModel().selectFirst();
        showSuggestions(found.size());
    }

    private void showSuggestions(int rowCount) {
        if (getScene() == null || getScene().getWindow() == null) {
            return;
        }
        double rows = Math.min(rowCount, VISIBLE_ROWS);
        suggestions.setPrefWidth(Math.max(getWidth(), MINIMUM_POPUP_WIDTH));
        suggestions.setPrefHeight(rows * suggestions.getFixedCellSize() + 4);
        Bounds bounds = localToScreen(getBoundsInLocal());
        if (bounds == null) {
            return;
        }
        if (!popup.isShowing()) {
            popup.show(this, bounds.getMinX(), bounds.getMaxY());
            if (popup.getScene() != null) {
                popup.getScene().getStylesheets().setAll(getScene().getStylesheets());
                popup.getScene().setNodeOrientation(getScene().getNodeOrientation());
            }
        } else {
            popup.setX(bounds.getMinX());
            popup.setY(bounds.getMaxY());
        }
    }

    private void hideSuggestions() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    private void choose(ItemsModel item) {
        if (item == null) {
            return;
        }
        hideSuggestions();
        chosenItem.set(item);
        // Set the text first so the name listener has nothing left to do, then fire the
        // name: listeners on chosenName are what actually put the item on the invoice.
        settingTextInternally = true;
        try {
            setText(item.getNameItem());
            positionCaret(getText() == null ? 0 : getText().length());
        } finally {
            settingTextInternally = false;
        }
        chosenName.set(item.getNameItem());
    }

    /** How a name or a barcode is turned into candidate items. */
    @FunctionalInterface
    public interface Search {
        List<ItemsModel> find(String text) throws Exception;
    }

    /** Name on one side, barcode / price / balance on the other. */
    private static final class SuggestionCell extends ListCell<ItemsModel> {
        private final ObjectProperty<ToDoubleFunction<ItemsModel>> price;
        private final Label name = new Label();
        private final Label barcode = new Label();
        private final Label figures = new Label();
        private final HBox layout;

        private SuggestionCell(ObjectProperty<ToDoubleFunction<ItemsModel>> price) {
            this.price = price;
            name.getStyleClass().add("item-suggestion-name");
            barcode.getStyleClass().add("item-suggestion-barcode");
            figures.getStyleClass().add("item-suggestion-figures");
            // The name is the part that gives way. It grows into whatever is left and
            // ends in an ellipsis; the barcode and the figures keep their natural width,
            // because a half-printed price is worse than a half-printed name.
            name.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            name.setTextOverrun(OverrunStyle.ELLIPSIS);
            HBox.setHgrow(name, Priority.ALWAYS);
            layout = new HBox(10, name, barcode, figures);
            layout.setAlignment(Pos.CENTER_LEFT);
            layout.setMinWidth(0);
            // Without this the cell demands its content's width and the list scrolls
            // sideways instead of the name giving way.
            setMinWidth(0);
            setPrefWidth(1);
        }

        @Override
        protected void updateItem(ItemsModel item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            var language = LanguageManager.getInstance();
            name.setText(item.getNameItem());
            barcode.setText(item.getBarcode() == null ? "" : item.getBarcode());
            figures.setText(String.format("%s %.2f  |  %s %.2f",
                    language.getString("invoice.price"), price.get().applyAsDouble(item),
                    language.getString("invoice.item.balance"), item.getSumAllBalance()));
            setTooltip(new Tooltip(item.getNameItem()));
            setText(null);
            setGraphic(layout);
        }
    }
}
