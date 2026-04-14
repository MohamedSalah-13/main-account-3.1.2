package com.hamza.account.controller.invoice;

import com.hamza.account.controller.pos.DialogButtons;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.SelPriceItemService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

public class DialogCashPaid {

    public static void showCashChangeDialog(double amountDue) {
        Dialog<Void> dialog = getDialog();
        dialog.setResizable(false);
        dialog.setTitle("حساب الباقي");
        dialog.setHeaderText("أدخل المبلغ المدفوع نقداً");
        var content = createPaymentDialog(amountDue);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    public static Optional<Double> showPriceSelectionDialog(ItemsModel itemsModel
            , SelPriceItemService selPriceItemService) throws DaoException {
        Dialog<Double> dialog = getDialog();
        dialog.setTitle("تحديد السعر");
        dialog.setHeaderText("تحديد السعر للصنف : " + itemsModel.getNameItem());

        ToggleGroup group = new ToggleGroup();
        VBox content = new VBox(10);

        var integerStringHashMap = selPriceItemService.getIntegerStringHashMap();
        RadioButton rb1 = new RadioButton(integerStringHashMap.get(1) + ": " + itemsModel.getSelPrice1());
        rb1.setToggleGroup(group);
        rb1.setSelected(true);
        rb1.setUserData(itemsModel.getSelPrice1());
        rb1.setDisable(itemsModel.getSelPrice1() == 0);

        RadioButton rb2 = new RadioButton(integerStringHashMap.get(2) + ": " + itemsModel.getSelPrice2());
        rb2.setToggleGroup(group);
        rb2.setUserData(itemsModel.getSelPrice2());
        rb2.setDisable(itemsModel.getSelPrice2() == 0);

        RadioButton rb3 = new RadioButton(integerStringHashMap.get(3) + ": " + itemsModel.getSelPrice3());
        rb3.setToggleGroup(group);
        rb3.setUserData(itemsModel.getSelPrice3());
        rb3.setDisable(itemsModel.getSelPrice3() == 0);

        content.getChildren().addAll(rb1, rb2, rb3);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                Toggle selectedToggle = group.getSelectedToggle();
                return selectedToggle != null ? (Double) selectedToggle.getUserData() : null;
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private static <T> Dialog<T> getDialog() {
        Dialog<T> dialog = new Dialog<>();
        dialog.setResizable(false);

        double screenWidth = Screen.getPrimary().getVisualBounds().getWidth();
        dialog.getDialogPane().setPrefWidth(screenWidth * 0.25);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogButtons.changeNameAndGraphic(dialog.getDialogPane());
        // إظهار الحوار والانتظار لاختيار المستخدم
        return dialog;
    }

    @NotNull
    private static VBox createPaymentDialog(double amountDue) {
        Label lblTotalText = new Label("إجمالي الفاتورة:");
        Label lblTotal = new Label(String.valueOf(roundToTwoDecimalPlaces(amountDue)));

        Label lblPaidText = new Label("المدفوع:");
        TextField paidField = new TextField();
        paidField.setStyle("-fx-min-width: 20em");
        Utils.setTextFormatter(paidField);
        paidField.setPromptText("0.00");
        Platform.runLater(paidField::requestFocus);

        Label lblChangeText = new Label("الباقي:");
        Label lblChange = new Label("0.00");
        lblChange.setStyle("-fx-font-weight: bold; -fx-text-fill: #6e0a0a");

        paidField.textProperty().addListener((obs, oldV, newV) -> {
            try {
                double paid = (newV == null || newV.isEmpty()) ? 0.0 : Double.parseDouble(newV);
                double change = paid - amountDue;
                if (change < 0) change = 0.0; // الباقي لإرجاعه فقط عند زيادة المدفوع عن إجمالي الفاتورة
                lblChange.setText(String.valueOf(roundToTwoDecimalPlaces(change)));
            } catch (Exception ex) {
                lblChange.setText("0.00");
            }
        });

        HBox row1 = new HBox(10, lblTotalText, lblTotal);
        HBox row2 = new HBox(10, lblPaidText, paidField);
        HBox row3 = new HBox(10, lblChangeText, lblChange);
        VBox content = new VBox(12, row1, row2, row3);
        return content;
    }
}
