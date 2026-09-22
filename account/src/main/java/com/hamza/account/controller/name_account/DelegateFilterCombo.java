package com.hamza.account.controller.name_account;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.EmployeeRef;
import com.hamza.account.features.employee.EmployeeScope;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.features.party.CustomerDelegateCondition;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * The "which delegate follows these customers" choice on the balances list and the ageing report -
 * everyone, nobody, or one delegate - read as the value {@link CustomerDelegateCondition} takes.
 * <p>
 * One class for both screens so the two combos offer the same choices in the same order. The
 * delegates are listed with {@link EmployeeScope#EVERYONE}: a delegate who has stopped working may
 * still be the default of customers who owe money, and a filter that could not name him could not
 * find them - the rule a history screen follows everywhere else.
 */
final class DelegateFilterCombo {

    /** The first row: no delegate asked about. An id no employee and no condition value has. */
    private static final int EVERYONE = -1;

    private final ComboBox<EmployeeRef> combo = new ComboBox<>();

    DelegateFilterCombo(String id) {
        combo.setId(id);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(EmployeeRef option) {
                if (option == null || option.id() == EVERYONE) {
                    return text("party.statement.filter.all");
                }
                return option.id() == CustomerDelegateCondition.NO_DELEGATE
                        ? text("party.filter.delegate.none") : option.name();
            }

            @Override
            public EmployeeRef fromString(String value) {
                return null;
            }
        });
    }

    ComboBox<EmployeeRef> node() {
        return combo;
    }

    /** Reads the delegates and selects "everyone". Names only - no figure about anybody is read. */
    void load() throws DaoException {
        List<EmployeeRef> options = new ArrayList<>();
        options.add(new EmployeeRef(EVERYONE, ""));
        options.add(new EmployeeRef(CustomerDelegateCondition.NO_DELEGATE, ""));
        options.addAll(ServiceRegistry.get(EmployeeService.class).delegateRefs(EmployeeScope.EVERYONE));
        combo.setItems(FXCollections.observableArrayList(options));
        combo.getSelectionModel().selectFirst();
    }

    /** The filter's value: {@code null} for everyone, {@code 0} for nobody, or the delegate's id. */
    Integer value() {
        EmployeeRef chosen = combo.getValue();
        return chosen == null || chosen.id() == EVERYONE ? null : chosen.id();
    }

    void reset() {
        combo.getSelectionModel().selectFirst();
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
