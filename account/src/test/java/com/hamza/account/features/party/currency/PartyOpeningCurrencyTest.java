package com.hamza.account.features.party.currency;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.model.domain.Customers;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.DAY;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.EGP;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.SAR;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.USD;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Settling a party's currency before it is saved (docs/currency-plan.md §14 ق-ج١ and ق-ج٢): valued
 * openings, the currency fixed by the first movement, and an unchanged opening that keeps its value.
 */
class PartyOpeningCurrencyTest {

    private final Map<Integer, Currency> currencies = new HashMap<>(Map.of(1, EGP, 2, SAR, 3, USD));
    private final Map<String, BigDecimal> rates = new HashMap<>();
    private final Set<Integer> moved = new HashSet<>();
    private PartyOpeningCurrency preparer;

    @BeforeEach
    void setUp() {
        rates.put("3@" + DAY.minusDays(3), bd("48"));
        rates.put("3@" + DAY, bd("48.5"));
        preparer = new PartyOpeningCurrency(new PartyOpeningCurrency.Lookup() {
            @Override
            public Currency find(Integer currencyId) {
                return currencyId == null ? null : currencies.get(currencyId);
            }

            @Override
            public BigDecimal rateOn(int currencyId, LocalDate day) {
                BigDecimal found = null;
                for (LocalDate d = day; !d.isBefore(DAY.minusDays(10)) && found == null; d = d.minusDays(1)) {
                    found = rates.get(currencyId + "@" + d);
                }
                return found;
            }

            @Override
            public boolean hasMoved(PartyKind kind, int partyId) {
                return moved.contains(partyId);
            }
        });
    }

    private static Customers party(int id, Integer currency, String openingForeign, double firstBalance) {
        Customers customer = new Customers();
        customer.setId(id);
        customer.setName("عميل " + id);
        customer.setCurrency_id(currency);
        customer.setOpening_foreign(openingForeign == null ? null : new BigDecimal(openingForeign));
        customer.setFirst_balance(firstBalance);
        return customer;
    }

    @Test
    @DisplayName("a new dollar customer: the opening in dollars is valued at today's rate into first_balance")
    void aNewDollarCustomer() throws Exception {
        Customers customer = party(0, 3, "100", 0);
        preparer.prepare(PartyKind.CUSTOMER, customer, null, DAY);

        assertEquals(3, customer.getCurrency_id());
        assertEquals(bd("100"), customer.getOpening_foreign());
        assertEquals(bd("48.5"), customer.getOpening_rate());
        assertEquals(4850.0, customer.getFirst_balance());
    }

    @Test
    @DisplayName("an opening dated in the past is valued at that day's rate")
    void anOpeningHasItsOwnDay() throws Exception {
        Customers customer = party(0, 3, "100", 0);
        customer.setOpening_balance_date(DAY.minusDays(2));
        preparer.prepare(PartyKind.CUSTOMER, customer, null, DAY);
        assertEquals(bd("48"), customer.getOpening_rate());
        assertEquals(4800.0, customer.getFirst_balance());
    }

    @Test
    @DisplayName("a customer in the base names no currency and stores nothing foreign - the base itself included")
    void theBaseIsStoredAsNothing() throws Exception {
        Customers customer = party(0, 1, "100", 750);
        preparer.prepare(PartyKind.CUSTOMER, customer, null, DAY);
        assertNull(customer.getCurrency_id());
        assertNull(customer.getOpening_foreign());
        assertNull(customer.getOpening_rate());
        assertEquals(750.0, customer.getFirst_balance(), "the base opening is what was typed");
    }

    @Test
    @DisplayName("no rate for a dollar opening is a refusal")
    void noRateIsARefusal() {
        rates.clear();
        assertThrows(UserValidationException.class,
                () -> preparer.prepare(PartyKind.CUSTOMER, party(0, 3, "100", 0), null, DAY));
    }

    @Test
    @DisplayName("an opening that did not change keeps its value and rate, though a rate was recorded since")
    void anUnchangedOpeningKeepsItsValue() throws Exception {
        Customers stored = party(7, 3, "100", 4800);
        stored.setOpening_rate(bd("48"));
        Customers edited = party(7, 3, "100", 0);
        edited.setTel("0100");

        preparer.prepare(PartyKind.CUSTOMER, edited, stored, DAY);

        assertEquals(bd("48"), edited.getOpening_rate());
        assertEquals(4800.0, edited.getFirst_balance());
    }

    @Test
    @DisplayName("once the party has moved, its currency is fixed")
    void theCurrencyIsFixedByTheFirstMovement() {
        moved.add(7);
        Customers stored = party(7, 3, "100", 4800);
        stored.setOpening_rate(bd("48"));

        assertThrows(BusinessRuleException.class,
                () -> preparer.prepare(PartyKind.CUSTOMER, party(7, 2, "100", 0), stored, DAY));
        assertThrows(BusinessRuleException.class,
                () -> preparer.prepare(PartyKind.CUSTOMER, party(7, null, null, 4800), stored, DAY));
    }

    @Test
    @DisplayName("a moved party saved unchanged keeps what is stored; a base one is left alone")
    void aMovedPartySavedUnchanged() throws Exception {
        moved.add(7);
        Customers stored = party(7, 3, "100", 4800);
        stored.setOpening_rate(bd("48"));
        Customers edited = party(7, 3, "100", 0);
        preparer.prepare(PartyKind.CUSTOMER, edited, stored, DAY);
        assertEquals(4800.0, edited.getFirst_balance());
        assertEquals(bd("48"), edited.getOpening_rate());

        moved.add(8);
        Customers base = party(8, null, null, 300);
        preparer.prepare(PartyKind.CUSTOMER, base, party(8, null, null, 300), DAY);
        assertNull(base.getCurrency_id());
        assertEquals(300.0, base.getFirst_balance());
    }

    @Test
    @DisplayName("a moved party's changed dollar opening is valued, so the opening guard can refuse it")
    void aChangedOpeningReachesTheGuard() throws Exception {
        moved.add(7);
        Customers stored = party(7, 3, "100", 4800);
        stored.setOpening_rate(bd("48"));
        Customers edited = party(7, 3, "150", 4800);
        preparer.prepare(PartyKind.CUSTOMER, edited, stored, DAY);
        assertEquals(7275.0, edited.getFirst_balance(), "150 at today's 48.5 - a changed figure the guard refuses");
    }
}
