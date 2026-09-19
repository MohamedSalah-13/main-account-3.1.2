package com.hamza.account.controller.invoice;

import com.hamza.account.features.returns.ReturnSourceSearch;
import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Which document a return reverses.
 * <p>
 * It replaced a {@code TextInputDialog} that took an invoice number and nothing else, so a
 * customer who had lost their copy could not be served at all unless somebody went and found the
 * number first. Here the same box takes the number <em>or</em> the party's name -
 * {@link ReturnSourceSearch} decides which - and the answer is a row you pick rather than a figure
 * you retype, which also removes the typo that used to surface much later as "the invoice does not
 * exist".
 * <p>
 * A document already returned in full is listed and marked rather than hidden: "it has all come
 * back already" is the answer the person is looking for, and an empty list is not.
 */
final class DialogReturnSourcePicker {

    /** Enough to scroll, few enough that the query stays a lookup rather than a report. */
    private static final int LIMIT = 200;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DialogReturnSourcePicker() {
    }

    interface Search {
        List<ReturnableRepository.SourceDocument> find(int number, String partyText, int limit)
                throws Exception;
    }

    /**
     * @return the chosen document's number, or empty when nothing was picked
     */
    static Optional<Integer> show(Search search, ErrorSink errors) {
        var lang = LanguageManager.getInstance();
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle(lang.getString("return.dialog.title"));
        dialog.setHeaderText(lang.getString("return.source.search.header"));
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField searchField = new TextField();
        searchField.setPromptText(lang.getString("return.source.search.prompt"));

        ObservableList<ReturnableRepository.SourceDocument> rows =
                FXCollections.observableArrayList();
        TableView<ReturnableRepository.SourceDocument> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label(lang.getString("return.source.search.none")));
        table.getColumns().setAll(List.of(
                column(lang.getString("return.source.column.number"),
                        row -> String.valueOf(row.number())),
                column(lang.getString("return.source.column.date"),
                        row -> row.date() == null ? "" : DATE.format(row.date())),
                column(lang.getString("return.source.column.party"),
                        row -> row.partyName() == null ? "" : row.partyName()),
                column(lang.getString("return.source.column.net"),
                        row -> Columns.money(BigDecimal.valueOf(row.net()))),
                column(lang.getString("return.source.column.state"),
                        row -> row.fullyReturned()
                                ? lang.getString("return.source.state.returned") : "")));

        Runnable reload = () -> {
            String text = searchField.getText();
            try {
                rows.setAll(search.find(ReturnSourceSearch.documentNumber(text),
                        ReturnSourceSearch.partyText(text), LIMIT));
            } catch (Exception e) {
                rows.clear();
                errors.accept(e);
            }
        };
        searchField.textProperty().addListener((observable, before, now) -> reload.run());
        // Double-click is the shortcut for pick-and-close, as it is in the item picker.
        table.setRowFactory(view -> {
            var row = new javafx.scene.control.TableRow<ReturnableRepository.SourceDocument>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    dialog.setResult(row.getItem().number());
                    dialog.close();
                }
            });
            return row;
        });

        VBox content = new VBox(10, searchField, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        // A dialog takes its size from its root node - three of the commission dialogs opened
        // with their last column behind a scroll bar for want of this.
        content.setPrefSize(720, 460);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            var selected = table.getSelectionModel().getSelectedItem();
            return selected == null ? null : selected.number();
        });

        Platform.runLater(searchField::requestFocus);
        reload.run();
        return dialog.showAndWait();
    }

    private static TableColumn<ReturnableRepository.SourceDocument, String> column(
            String title, Function<ReturnableRepository.SourceDocument, String> value) {
        TableColumn<ReturnableRepository.SourceDocument, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
            }
        });
        return column;
    }

    @FunctionalInterface
    interface ErrorSink {
        void accept(Exception error);
    }
}
