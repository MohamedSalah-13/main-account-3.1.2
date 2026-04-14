package com.hamza.account.features.key_setting;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import javafx.event.EventHandler;
import javafx.scene.control.TablePosition;
import javafx.scene.input.*;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class UpdateQuantity {

    private final UpdateInterface updateInterface;

    public EventHandler<KeyEvent> tableKeyPressed() {
        return new EventHandler<>() {
            final KeyCombination charCombo = new KeyCharacterCombination("+", KeyCombination.CONTROL_DOWN);
            final KeyCombination codeCombo = new KeyCodeCombination(KeyCode.ADD);
            final KeyCombination charComboMinus = new KeyCharacterCombination("-", KeyCombination.CONTROL_DOWN);
            final KeyCombination codeComboMinus = new KeyCodeCombination(KeyCode.SUBTRACT);

            @Override
            public void handle(KeyEvent event) {
                try {
                    if (charCombo.match(event) || codeCombo.match(event)) {
                        updateQuantity(true);
                    } else if (charComboMinus.match(event) || codeComboMinus.match(event)) {
                        updateQuantity(false);
                    }
                } catch (DaoException e) {
                    AllAlerts.alertError(e.getMessage());
                }
                updateInterface.sum();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void updateQuantity(boolean plus) throws DaoException {
        TablePosition<BasePurchasesAndSales, String> focusedCell = updateInterface.getTable().getFocusModel().getFocusedCell();
//        double quantity = invoiceBuy.getQuantity(focusedCell);
        double quantity = focusedCell.getTableView().getItems().get(focusedCell.getRow()).getQuantity();
        double quantity1 = 1;
        if (plus) quantity1 = quantity + 1;
        else {
            if (quantity > 1)
                quantity1 = quantity - 1;
        }
//        invoiceBuy.setQuantityAndUpdateRow(focusedCell, quantity1);
        focusedCell.getTableView().getItems().get(focusedCell.getRow()).setQuantity(quantity1);
        updateInterface.update(focusedCell.getTableView().getItems().get(focusedCell.getRow()));
    }

}

