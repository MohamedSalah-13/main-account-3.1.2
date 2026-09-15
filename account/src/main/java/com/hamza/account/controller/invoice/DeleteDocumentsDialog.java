package com.hamza.account.controller.invoice;

import com.hamza.account.features.totals.TotalsDeletionPreview;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

/**
 * The confirmation before documents are deleted: the list of them, what they come to, and whether
 * the ticks reach beyond this page.
 * <p>
 * Built in code rather than from an FXML - it is one table and three lines - and its buttons are
 * the application's own words and orientation, not JavaFX's English defaults. <b>Cancel is the
 * default button</b>: an Enter pressed out of habit must not delete fifty invoices.
 */
final class DeleteDocumentsDialog {

    private static final double TABLE_HEIGHT = 260;

    private DeleteDocumentsDialog() {
    }

    /** True only when the operator pressed Delete. */
    static boolean confirm(Window owner, String documentName, TotalsDeletionPreview preview) {
        LanguageManager language = LanguageManager.getInstance();

        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(language.getString("invoice.delete.confirm.title"));
        dialog.setResizable(true);

        ButtonType delete = new ButtonType(language.getString("delete"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(language.getString("cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        DialogPane pane = dialog.getDialogPane();
        pane.setNodeOrientation(language.getNodeOrientation());
        pane.getButtonTypes().setAll(delete, cancel);
        pane.lookupButton(delete).getStyleClass().add("danger-button");
        // ButtonType's own default flag goes to OK_DONE; take it away so Enter cancels.
        if (pane.lookupButton(delete) instanceof Button deleteButton) {
            deleteButton.setDefaultButton(false);
        }
        if (pane.lookupButton(cancel) instanceof Button cancelButton) {
            cancelButton.setDefaultButton(true);
        }

        Label header = new Label(language.getString("invoice.delete.confirm.header",
                preview.count(), documentName));
        header.getStyleClass().add("app-section-title");
        header.setWrapText(true);

        VBox content = new VBox(10, header, table(preview), new Label(summary(preview)));
        if (preview.unseenPages() > 0) {
            Label pages = new Label(language.getString("invoice.delete.confirm.page.only", preview.unseenPages()));
            pages.getStyleClass().add("totals-filtered-badge");
            pages.setWrapText(true);
            content.getChildren().add(pages);
        }
        content.setPrefWidth(640);
        pane.setContent(content);

        Optional<ButtonType> answer = dialog.showAndWait();
        return answer.isPresent() && answer.get() == delete;
    }

    private static TableView<TotalsDeletionPreview.Document> table(TotalsDeletionPreview preview) {
        TableView<TotalsDeletionPreview.Document> table = new TableView<>();
        TableColumn<TotalsDeletionPreview.Document, Number> number =
                Columns.number("code", TotalsDeletionPreview.Document::id);
        TableColumn<TotalsDeletionPreview.Document, String> date =
                Columns.text("date", TotalsDeletionPreview.Document::date);
        TableColumn<TotalsDeletionPreview.Document, String> name =
                Columns.text("name", TotalsDeletionPreview.Document::partyName);
        TableColumn<TotalsDeletionPreview.Document, Number> items =
                Columns.number("invoice.column.item.count", TotalsDeletionPreview.Document::itemCount);
        var total = Columns.money("total", TotalsDeletionPreview.Document::total);
        table.getColumns().addAll(List.of(number, date, name, items, total));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getItems().setAll(preview.documents());
        table.setPrefHeight(TABLE_HEIGHT);
        return table;
    }

    private static String summary(TotalsDeletionPreview preview) {
        LanguageManager language = LanguageManager.getInstance();
        String total = Columns.money(preview.total());
        if (preview.earliest() == null) {
            return language.getString("invoice.delete.confirm.summary.total", total);
        }
        if (preview.singleDay()) {
            return language.getString("invoice.delete.confirm.summary.day", total, preview.earliest());
        }
        return language.getString("invoice.delete.confirm.summary.range", total, preview.earliest(), preview.latest());
    }
}
