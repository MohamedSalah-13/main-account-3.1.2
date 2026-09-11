package com.hamza.account.controller.invoice;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.invoice.InvoicePaymentTerms;
import com.hamza.account.features.invoice.InvoiceTender;
import com.hamza.account.finance.MoneyMath;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The payment step of saving a new sales invoice: the amount due, what the customer handed
 * over, and the change to give back. Optional, and switched on per computer in the settings
 * ({@code settings.checks.showPaidScreen}) - a till wants it, a back-office desk does not.
 * <p>
 * <b>It is asked before the invoice is written, not after.</b> What it replaced was a change
 * calculator shown once the sale had been saved and the "saved" alert dismissed, whose OK and
 * Cancel did the same thing: by the time a cashier found the customer had handed over too
 * little, the sale was already a cash sale in the books. Here, going back writes nothing, and
 * the save button is the confirmation - so this stands in for "do you want to save?" rather than
 * adding a second question after it.
 * <p>
 * The arithmetic and the refusals are {@link InvoiceTender}'s; this class only shows them.
 */
final class InvoicePaymentDialog {

    private static final String FIGURE_STYLE = "-fx-font-size: 20px; -fx-font-weight: bold;";
    private static final String CHANGE_STYLE = "-fx-font-size: 28px; -fx-font-weight: bold;";
    private static final String PROBLEM_STYLE = "-fx-text-fill: #c62828; -fx-font-weight: bold;";

    private InvoicePaymentDialog() {
    }

    /**
     * Asks for the payment of {@code terms}. Empty when the operator went back to the invoice;
     * otherwise a tender {@link InvoiceTender#accepted() accepted} for these terms.
     */
    static Optional<InvoiceTender> ask(Window owner, InvoicePaymentTerms terms, boolean print) {
        var lang = LanguageManager.getInstance();
        boolean deferred = terms.deferred();
        BigDecimal due = terms.netAmount();

        Dialog<InvoiceTender> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(lang.getString("invoice.tender.title"));
        dialog.setHeaderText(deferred
                ? lang.getString("invoice.tender.header.deferred")
                : lang.getString("invoice.tender.header.cash"));
        DialogPane pane = dialog.getDialogPane();
        pane.setNodeOrientation(lang.getNodeOrientation());
        pane.getStylesheets().add(ThemeManager.getStylesheet());
        pane.setPrefWidth(480);

        ButtonType save = new ButtonType(print
                ? lang.getString("invoice.btn.save.print")
                : lang.getString("invoice.btn.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType back = new ButtonType(lang.getString("invoice.tender.back"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(save, back);
        Button saveButton = (Button) pane.lookupButton(save);
        saveButton.setDefaultButton(true);
        saveButton.setGraphic(AppIcon.SAVE.graphic(16));
        ((Button) pane.lookupButton(back)).setGraphic(AppIcon.CLOSE.graphic(16));

        // The formatter first: it writes its own default into the field when it is set.
        TextField tendered = new TextField();
        Utils.setTextFormatter(tendered);
        tendered.setText(MoneyMath.text(deferred ? terms.paidAmount() : due));
        tendered.setStyle(FIGURE_STYLE);

        Label changeValue = figure(CHANGE_STYLE);
        Label remainingValue = figure(FIGURE_STYLE);
        Label problem = new Label();
        problem.setWrapText(true);
        problem.setStyle(PROBLEM_STYLE);

        FlowPane quickAmounts = new FlowPane(8, 8);
        quickAmounts.getChildren().add(quickAmount(lang.getString("invoice.tender.exact"), due, tendered));
        for (BigDecimal amount : InvoiceTender.suggestions(due)) {
            quickAmounts.getChildren().add(
                    quickAmount(amount.stripTrailingZeros().toPlainString(), amount, tendered));
        }

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        ColumnConstraints captions = new ColumnConstraints();
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(captions, values);
        Label dueValue = figure(FIGURE_STYLE);
        dueValue.setText(Columns.money(due));
        grid.addRow(0, caption(lang.getString("invoice.tender.due")), dueValue);
        grid.addRow(1, caption(lang.getString("invoice.tender.tendered")), tendered);
        grid.add(quickAmounts, 1, 2);
        grid.addRow(3, caption(lang.getString("invoice.tender.change")), changeValue);
        if (deferred) {
            grid.addRow(4, caption(lang.getString("invoice.tender.remaining.account")), remainingValue);
        }

        Label hint = new Label(lang.getString("invoice.tender.hint"));
        hint.setStyle("-fx-opacity: 0.7;");
        VBox content = new VBox(14, grid, problem, hint);
        content.setPadding(new Insets(6, 4, 0, 4));
        pane.setContent(content);

        Runnable refresh = () -> {
            InvoiceTender tender = InvoiceTender.of(terms, read(tendered.getText()));
            changeValue.setText(Columns.money(tender.change()));
            remainingValue.setText(Columns.money(tender.remaining()));
            problem.setText(switch (tender.problem()) {
                case NONE -> "";
                case SHORT -> lang.getString("invoice.tender.error.short", Columns.money(tender.shortBy()));
                case NEGATIVE -> lang.getString("invoice.tender.error.negative");
            });
            problem.setVisible(!tender.accepted());
            problem.setManaged(!tender.accepted());
            saveButton.setDisable(!tender.accepted());
        };
        tendered.textProperty().addListener((observable, oldText, newText) -> refresh.run());
        refresh.run();

        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            InvoiceTender tender = InvoiceTender.of(terms, read(tendered.getText()));
            return tender.accepted() ? tender : null;
        });
        // Selected, so the first key the cashier presses replaces the amount rather than
        // appending to it; Enter on the untouched field is "they paid exactly".
        dialog.setOnShown(event -> Platform.runLater(() -> {
            tendered.requestFocus();
            tendered.selectAll();
        }));
        return dialog.showAndWait();
    }

    /** What is in the field, or nothing handed over while it is blank or half typed. */
    private static BigDecimal read(String text) {
        try {
            return NumberTextConverter.parse(text);
        } catch (NumberFormatException halfTyped) {
            return null;
        }
    }

    private static Button quickAmount(String text, BigDecimal amount, TextField field) {
        Button button = new Button(text);
        // Out of the tab order and handing the focus straight back, so Enter after a click
        // still means the save button and not this one again.
        button.setFocusTraversable(false);
        button.setOnAction(event -> {
            field.setText(MoneyMath.text(amount));
            field.requestFocus();
            field.end();
        });
        return button;
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private static Label figure(String style) {
        Label label = new Label();
        label.setStyle(style);
        return label;
    }
}
