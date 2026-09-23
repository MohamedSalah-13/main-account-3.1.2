package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.party.currency.PartyCurrencies;
import com.hamza.account.features.party.currency.PartyCurrencyFixtures;
import com.hamza.account.features.returns.ReturnableRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A return's source invoice as the return screen shows it (docs/currency-plan.md §15 ق-د٥).
 */
class ReturnSourceFiguresTest {

    private static final BigDecimal RATE = new BigDecimal("48.37");
    private static final DocumentPricing DOLLARS = new DocumentPricing(PartyCurrencyFixtures.USD, RATE);
    private static final ReturnableRepository.SourceAmounts BASE_AMOUNTS =
            new ReturnableRepository.SourceAmounts(493.87, 29.51);

    private final PartyCurrencyFixtures.Memory currencies = new PartyCurrencyFixtures.Memory();

    @Test
    @DisplayName("an invoice typed in dollars offers what was typed on each line, and its header in dollars")
    void aWrittenInvoice() throws Exception {
        currencies.foreignHeaders.put("SALES:7", new PartyCurrencies.ForeignHeader(3, RATE,
                new BigDecimal("10.21"), new BigDecimal("0.61"), BigDecimal.ZERO));
        currencies.writtenLines.put("SALES:7", Map.of(
                11, new PartyCurrencies.WrittenLine(new BigDecimal("2.07"), new BigDecimal("0.10"))));

        ReturnSourceFigures shown = ReturnSourceFigures.read(currencies, DocumentType.SALES, 7, DOLLARS);

        assertEquals(2.07, shown.price(11, 100.13), 0.0);
        assertEquals(0.10, shown.discount(11, 4.84), 0.0);
        assertEquals(2.00, shown.price(12, 96.74), 0.0, "a line with nothing typed: its base over the rate");
        assertEquals(10.21, shown.amounts(BASE_AMOUNTS).total(), 0.0);
        assertEquals(0.61, shown.amounts(BASE_AMOUNTS).discount(), 0.0);
    }

    @Test
    @DisplayName("an invoice written in the base and translated offers its base figures over its own rate")
    void aTranslatedInvoice() throws Exception {
        currencies.foreignHeaders.put("SALES:7", new PartyCurrencies.ForeignHeader(null, RATE,
                new BigDecimal("10.21"), new BigDecimal("0.61"), BigDecimal.ZERO));
        currencies.writtenLines.put("SALES:7", Map.of(
                11, new PartyCurrencies.WrittenLine(new BigDecimal("9.99"), BigDecimal.ZERO)));

        ReturnSourceFigures shown = ReturnSourceFigures.read(currencies, DocumentType.SALES, 7, DOLLARS);

        assertEquals(2.07, shown.price(11, 100.13), 0.0, "not asked for typed lines it has none of");
        assertEquals(10.21, shown.amounts(BASE_AMOUNTS).total(), 0.0);
    }

    @Test
    @DisplayName("a return in the base, an invoice in the base, or no invoice shows the invoice as it is")
    void asItIs() throws Exception {
        assertSame(ReturnSourceFigures.AS_IS,
                ReturnSourceFigures.read(currencies, DocumentType.SALES, 7, DocumentPricing.BASE));
        assertSame(ReturnSourceFigures.AS_IS, ReturnSourceFigures.read(currencies, DocumentType.SALES, 7, DOLLARS),
                "an invoice with no foreign header");
        assertSame(ReturnSourceFigures.AS_IS, ReturnSourceFigures.read(currencies, DocumentType.SALES, 0, DOLLARS));
        assertSame(ReturnSourceFigures.AS_IS, ReturnSourceFigures.read(null, DocumentType.SALES, 7, DOLLARS));

        assertEquals(100.13, ReturnSourceFigures.AS_IS.price(11, 100.13), 0.0);
        assertSame(BASE_AMOUNTS, ReturnSourceFigures.AS_IS.amounts(BASE_AMOUNTS));
    }
}
