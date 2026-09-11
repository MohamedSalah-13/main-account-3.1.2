package com.hamza.account.controller.invoice;

import com.hamza.account.controller.others.DialogButtons;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.SelPriceItemService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

import java.util.Optional;

/**
 * The price-tier picker. It used to hold a change calculator as well, shown after a cash sale
 * had already been saved - see {@link InvoicePaymentDialog} for what replaced it and why.
 */
public class DialogCashPaid {

    public static Optional<Double> showPriceSelectionDialog(ItemsModel itemsModel
            , SelPriceItemService selPriceItemService) throws DaoException {
        Dialog<Double> dialog = getDialog();
        dialog.setTitle(LanguageManager.getInstance().getString("invoice.dialog.price.select.title"));
        dialog.setHeaderText(LanguageManager.getInstance().getString(
                "invoice.dialog.price.select.header", itemsModel.getNameItem()));

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
        return dialog;
    }
}
