package com.hamza.account.type;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * A kind of movement on an item's card.
 * <p>
 * Only ever compared by identity ({@code ==}/{@code .equals} against the constant
 * itself, never by its label), so the label is free to track the active language:
 * each constant's property is refreshed when {@link LanguageManager}'s locale changes.
 * <p>
 * The last three arrived on 2026-09-20 with the transfers and the counts. A transfer is
 * two kinds rather than one because the card reads one warehouse at a time and the two
 * halves go opposite ways there; a posted count is one kind because its quantity is a
 * signed difference and carries its own direction.
 * <p>
 * The four legacy keys are the words they always were. New ones are dotted, and because
 * the label is resolved through a variable here, {@code MessageKeyArchitectureTest}
 * cannot see any of them - {@code ProcessTypeTest} checks them against the three bundles
 * instead, the way {@code PartyStatementTest} does for {@code PartyMovementKind}.
 */
public enum ProcessType {

    PURCHASE("pur"),
    PURCHASE_RETURN("RePur"),
    SALES("sales"),
    SALES_RETURN("ReSal"),
    TRANSFER_IN("item.card.kind.transfer.in"),
    TRANSFER_OUT("item.card.kind.transfer.out"),
    STOCK_COUNT("item.card.kind.stock.count");

    private final String key;
    private final StringProperty type;

    ProcessType(String key) {
        this.key = key;
        this.type = new SimpleStringProperty(LanguageManager.getInstance().getString(key));
    }

    static {
        LanguageManager.getInstance().currentLocaleProperty().addListener((obs, oldLocale, newLocale) -> {
            for (ProcessType value : values()) {
                value.type.set(LanguageManager.getInstance().getString(value.key));
            }
        });
    }

    public String getType() {
        return type.get();
    }

    public StringProperty typeProperty() {
        return type;
    }
}
