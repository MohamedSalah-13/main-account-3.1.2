package com.hamza.account.controller.convert_treasury;

import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.List;

/**
 * The treasury picker the two movement screens share.
 * <p>
 * It offers {@link TreasuryBalanceSummary} rather than a name, so the screen has the
 * balance without a second query and a transfer can be refused before it is sent.
 * The two screens are otherwise separate on purpose - a deposit is not a transfer -
 * but a picker written twice is a picker that ends up filtering differently in each.
 */
final class TreasuryCombo {

    private TreasuryCombo() {
    }

    /** Keeps the selection when the list is reloaded after a movement is saved. */
    static void fill(ComboBox<TreasuryBalanceSummary> combo, List<TreasuryBalanceSummary> rows) {
        TreasuryBalanceSummary selected = combo.getValue();
        combo.setItems(FXCollections.observableArrayList(rows));
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TreasuryBalanceSummary row) {
                return row == null ? "" : row.name();
            }

            @Override
            public TreasuryBalanceSummary fromString(String value) {
                return combo.getValue();
            }
        });
        if (selected != null) {
            rows.stream()
                    .filter(row -> row.id() == selected.id())
                    .findFirst()
                    .ifPresentOrElse(combo.getSelectionModel()::select,
                            () -> combo.getSelectionModel().selectFirst());
        } else {
            combo.getSelectionModel().selectFirst();
        }
    }

    /** "Available balance: 1,234.00", or empty when nothing is picked. */
    static String availableText(TreasuryBalanceSummary row) {
        if (row == null) {
            return "";
        }
        return LanguageManager.getInstance().getString("treasury.available.balance")
                + " " + row.balance().toPlainString();
    }

    static BigDecimal amount(String text, String errorKey) throws UserValidationException {
        try {
            BigDecimal amount = new BigDecimal(text == null ? "" : text.trim());
            if (amount.signum() <= 0) {
                throw new UserValidationException(LanguageManager.getInstance().getString(errorKey));
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new UserValidationException(LanguageManager.getInstance().getString(errorKey), e);
        }
    }
}
