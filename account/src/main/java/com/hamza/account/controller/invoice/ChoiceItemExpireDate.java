package com.hamza.account.controller.invoice;

import com.hamza.account.controller.others.DialogButtons;
import com.hamza.account.features.invoice.InvoiceExpiryOptions;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Supplier;

public class ChoiceItemExpireDate extends Dialog<LocalDate> {

    public ChoiceItemExpireDate(InvoiceExpiryOptions options) {
        Objects.requireNonNull(options, "options");
        if (options.mode() == InvoiceExpiryOptions.Mode.NOT_REQUIRED) {
            throw new IllegalArgumentException("Expiry selection is not required");
        }
        LanguageManager language = LanguageManager.getInstance();

        setTitle(language.getString("invoice.expiry.title"));
        setHeaderText(options.mode() == InvoiceExpiryOptions.Mode.EXISTING_BATCH
                ? language.getString("invoice.expiry.header.existing")
                : language.getString("invoice.expiry.header.manual"));

        Selection selection = createSelection(options);

        VBox content = new VBox(10);
        content.getChildren().add(selection.node());
        getDialogPane().setContent(content);

        getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        DialogButtons.changeNameAndGraphic(getDialogPane());
        setResultConverter(button -> {
            var date = selection.selectedDate().get();
            if (button == javafx.scene.control.ButtonType.OK && date != null) {
                return date;
            }
            return null;
        });
    }

    private Selection createSelection(InvoiceExpiryOptions options) {
        if (options.mode() == InvoiceExpiryOptions.Mode.MANUAL_ENTRY) {
            DatePicker datePicker = new DatePicker();
            return new Selection(datePicker, datePicker::getValue);
        }

        ListView<InvoiceExpiryOptions.Batch> batches = new ListView<>();
        batches.getItems().setAll(options.batches());
        batches.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(InvoiceExpiryOptions.Batch batch, boolean empty) {
                super.updateItem(batch, empty);
                setText(empty || batch == null ? null
                        : LanguageManager.getInstance().getString(
                                "invoice.expiry.batch.available",
                                batch.expirationDate(),
                                batch.availableBaseQuantity()));
            }
        });
        if (!batches.getItems().isEmpty()) {
            batches.getSelectionModel().selectFirst();
        }
        return new Selection(batches, () -> {
            InvoiceExpiryOptions.Batch selected =
                    batches.getSelectionModel().getSelectedItem();
            return selected == null ? null : selected.expirationDate();
        });
    }

    private record Selection(Node node, Supplier<LocalDate> selectedDate) {
    }
}
