package com.hamza.account.controller.invoice;

import com.hamza.account.controller.others.DialogButtons;
import com.hamza.account.features.invoice.ReturnableLineSelection;
import com.hamza.account.features.returns.ReturnReason;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Picks which lines of a source invoice, and how much of each, a return should
 * reverse - the entry point {@code ReturnLineSelectionService} was built for.
 * <p>
 * A plain {@link Dialog}, not FXML, in the shape {@code DialogCashPaid} already
 * established for this kind of one-off picker: no separate view file, no controller
 * class, everything built and torn down with the dialog itself.
 */
public final class DialogReturnFromInvoice {

    private DialogReturnFromInvoice() {
    }

    public static Optional<Result> show(int sourceInvoiceNumber,
                                        List<ReturnableLineSelection> lines) {
        var lang = LanguageManager.getInstance();
        Dialog<Result> dialog = new Dialog<>();
        dialog.setResizable(true);
        dialog.setTitle(lang.getString("return.dialog.title"));
        dialog.setHeaderText(lang.getString("return.dialog.header", sourceInvoiceNumber));
        dialog.getDialogPane().setPrefWidth(Screen.getPrimary().getVisualBounds().getWidth() * 0.5);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogButtons.changeNameAndGraphic(dialog.getDialogPane());

        List<Row> rows = new ArrayList<>();
        for (ReturnableLineSelection line : lines) {
            rows.add(new Row(line));
        }

        TableView<Row> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().setAll(List.of(
                readOnlyColumn(lang.getString("return.dialog.column.item"),
                        row -> row.line().item().getNameItem()),
                readOnlyColumn(lang.getString("return.dialog.column.unit"),
                        row -> row.line().unit().getUnit_name()),
                readOnlyColumn(lang.getString("return.dialog.column.sold"),
                        row -> quantityText(row.line().soldQuantity())),
                readOnlyColumn(lang.getString("return.dialog.column.remaining"),
                        row -> quantityText(row.remainingInUnit())),
                quantityColumn(lang.getString("return.dialog.column.quantity"))));

        ComboBox<ReturnReason> reasonCombo = new ComboBox<>(
                FXCollections.observableArrayList(ReturnReason.values()));
        reasonCombo.setConverter(reasonConverter());
        reasonCombo.setPromptText(lang.getString("return.dialog.reason.prompt"));

        VBox content = new VBox(10, table,
                new HBox(10, new Label(lang.getString("return.dialog.reason.label")), reasonCombo));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            List<Selected> selected = new ArrayList<>();
            for (Row row : rows) {
                double quantityInUnit = parseQuantity(row.quantityText().get());
                if (quantityInUnit > 0) {
                    selected.add(new Selected(row.line(), quantityInUnit));
                }
            }
            return new Result(selected, reasonCombo.getValue());
        });

        return dialog.showAndWait();
    }

    private static TableColumn<Row, String> readOnlyColumn(
            String title, java.util.function.Function<Row, String> value) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private static TableColumn<Row, String> quantityColumn(String title) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> cell.getValue().quantityText());
        column.setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
        column.setEditable(true);
        column.setOnEditCommit(event -> event.getRowValue().quantityText().set(event.getNewValue()));
        return column;
    }

    private static StringConverter<ReturnReason> reasonConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(ReturnReason reason) {
                return reason == null ? "" : reason.label();
            }

            @Override
            public ReturnReason fromString(String string) {
                return null;
            }
        };
    }

    private static double parseQuantity(String text) {
        if (text == null) {
            return 0;
        }
        try {
            double value = Double.parseDouble(text.trim());
            return Math.max(value, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String quantityText(double value) {
        return MoneyMath.text(BigDecimal.valueOf(value));
    }

    /** One table row: the pickable line, plus the quantity the user has typed for it. */
    private record Row(ReturnableLineSelection line, StringProperty quantityText) {
        Row(ReturnableLineSelection line) {
            this(line, new SimpleStringProperty("0"));
        }

        double remainingInUnit() {
            return ItemUnits.fromBase(line.remainingBaseQuantity(), line.unit());
        }
    }

    /** One line the user checked off, with the quantity they entered - in the line's own unit. */
    public record Selected(ReturnableLineSelection line, double quantityInUnit) {
    }

    public record Result(List<Selected> selectedLines, ReturnReason reason) {
    }
}
