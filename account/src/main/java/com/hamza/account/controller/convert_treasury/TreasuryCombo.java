package com.hamza.account.controller.convert_treasury;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * Every currency, by id - read once per fill, so a cell naming a treasury's currency is not a query.
     * Empty without a currency service, which leaves every treasury reading as one in the base.
     */
    static Map<Integer, Currency> currencies() {
        CurrencyService service = ServiceRegistry.get(CurrencyService.class);
        if (service == null) {
            return Map.of();
        }
        try {
            return service.all().stream().collect(Collectors.toMap(Currency::id, currency -> currency));
        } catch (DaoException e) {
            return Map.of();
        }
    }

    /** The currency a picked treasury is in (V81); {@code null} for the base. */
    static Currency currencyOf(ComboBox<TreasuryBalanceSummary> combo, TreasuryBalanceSummary row) {
        if (row == null || !row.isForeign()) {
            return null;
        }
        Object known = combo.getProperties().get(CURRENCIES);
        return known instanceof Map<?, ?> map ? (Currency) map.get(row.currencyId()) : null;
    }

    private static final String CURRENCIES = "treasury.combo.currencies";

    /** Keeps the selection when the list is reloaded after a movement is saved. */
    static void fill(ComboBox<TreasuryBalanceSummary> combo, List<TreasuryBalanceSummary> rows) {
        combo.getProperties().put(CURRENCIES, currencies());
        TreasuryBalanceSummary selected = combo.getValue();
        combo.setItems(FXCollections.observableArrayList(rows));
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TreasuryBalanceSummary row) {
                return label(combo, row);
            }

            @Override
            public TreasuryBalanceSummary fromString(String value) {
                return combo.getValue();
            }
        });
        // A drawer, a wallet and a bank account read alike in a list of names; the glyph
        // is what stops a collection landing on the wrong kind of vessel.
        combo.setButtonCell(typedCell(combo));
        combo.setCellFactory(list -> typedCell(combo));

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

    private static ListCell<TreasuryBalanceSummary> typedCell(ComboBox<TreasuryBalanceSummary> combo) {
        return new ListCell<>() {
            @Override
            protected void updateItem(TreasuryBalanceSummary row, boolean empty) {
                super.updateItem(row, empty);
                setText(empty || row == null ? null : label(combo, row));
                setGraphic(empty || row == null ? null : row.type().icon().graphic());
            }
        };
    }

    /** The name, and the currency's code for a treasury in a foreign one - "درج الدولار (USD)". */
    private static String label(ComboBox<TreasuryBalanceSummary> combo, TreasuryBalanceSummary row) {
        if (row == null) {
            return "";
        }
        Currency currency = currencyOf(combo, row);
        return currency == null ? row.name() : row.name() + " (" + currency.code() + ")";
    }

    /**
     * "Available balance: 1,234.00", or empty when nothing is picked. A treasury in a foreign currency
     * says what it holds in that currency - what a withdrawal is checked against (V81) - with its code.
     */
    static String availableText(ComboBox<TreasuryBalanceSummary> combo) {
        TreasuryBalanceSummary row = combo.getValue();
        if (row == null) {
            return "";
        }
        Currency currency = currencyOf(combo, row);
        String figure = currency == null
                ? com.hamza.controlsfx.table.Columns.money(row.balance())
                : CurrencyFormat.amount(row.balanceOwn(), currency) + " " + currency.code();
        return LanguageManager.getInstance().getString("treasury.available.balance") + " " + figure;
    }

    /**
     * Enter on {@code control} goes to {@code next} when it is in the form - shown and enabled - and to
     * {@code otherwise} when it is not: {@code Utils.whenEnterPressed} would request focus on a field
     * that cannot take it, and the caret would stay where it was.
     */
    static void onEnter(javafx.scene.control.Control control, javafx.scene.control.Control next,
                        javafx.scene.control.Control otherwise) {
        control.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                (next.isVisible() && !next.isDisabled() ? next : otherwise).requestFocus();
            }
        });
    }

    /** A figure that may be left out: blank is zero, anything else has to be a number that is not negative. */
    static BigDecimal optionalAmount(String text, String errorKey) throws UserValidationException {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal amount = new BigDecimal(text.trim());
            if (amount.signum() < 0) {
                throw new UserValidationException(LanguageManager.getInstance().getString(errorKey));
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new UserValidationException(LanguageManager.getInstance().getString(errorKey), e);
        }
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
