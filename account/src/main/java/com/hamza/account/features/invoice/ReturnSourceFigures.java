package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.party.currency.PartyCurrencies;
import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.account.finance.MoneyMath;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * A return's source invoice as the return screen shows it: in the currency the return is typed in
 * (V83, docs/currency-plan.md §15 ق-د٥).
 * <p>
 * A return gives back exactly what its invoice took. When the return is typed in the party's currency, the
 * picker offers the invoice's lines at what they were typed at in that currency, and fills the return's
 * share of the invoice's own discount from the invoice's figures in it:
 * <ul>
 *   <li>an invoice typed in the currency (V83) offers each line's own typed price and discount, and its
 *       header's typed total and discount;</li>
 *   <li>an invoice written in the base and translated (V82) has no typed line - its lines are offered at
 *       their base figures divided by its rate, and its header at its translation;</li>
 *   <li>an invoice in the base, or a return typed in the base, is shown as it is.</li>
 * </ul>
 * Whatever is shown here, the save stores the return's base figures from the invoice's own base figures
 * ({@code ForeignDocumentLines}), so nothing shown here can move a base figure.
 *
 * @param rate     the invoice's rate, or {@code null} when it is shown in the base
 * @param total    its header's total in the party's currency, or {@code null}
 * @param discount its header's discount in the party's currency, or {@code null}
 * @param lines    what each of its lines was typed at, by line id
 */
public record ReturnSourceFigures(BigDecimal rate, BigDecimal total, BigDecimal discount,
                                  Map<Integer, PartyCurrencies.WrittenLine> lines) {

    /** An invoice shown as it is, in the base. */
    public static final ReturnSourceFigures AS_IS = new ReturnSourceFigures(null, null, null, Map.of());

    public ReturnSourceFigures {
        lines = lines == null ? Map.of() : Map.copyOf(lines);
    }

    /**
     * How the invoice a return names is shown on a return typed under {@code returnPricing}.
     */
    public static ReturnSourceFigures read(PartyCurrencies currencies, DocumentType sourceType, int sourceNumber,
                                           DocumentPricing returnPricing) throws DaoException {
        if (currencies == null || sourceNumber <= 0 || returnPricing == null || !returnPricing.foreign()) {
            return AS_IS;
        }
        PartyCurrencies.ForeignHeader header = currencies.foreignHeader(sourceType, sourceNumber);
        if (header == null || header.rate() == null || header.rate().signum() <= 0) {
            return AS_IS;
        }
        Map<Integer, PartyCurrencies.WrittenLine> typed = header.written()
                ? currencies.writtenLines(sourceType, sourceNumber) : Map.of();
        return new ReturnSourceFigures(header.rate(), header.total(), header.discount(), typed);
    }

    /** A source line's price per its unit, in the return's currency. */
    public double price(int lineId, double basePrice) {
        PartyCurrencies.WrittenLine line = lines.get(lineId);
        if (line != null && line.price() != null) {
            return line.price().doubleValue();
        }
        return fromBase(basePrice);
    }

    /** A source line's own discount, in the return's currency. */
    public double discount(int lineId, double baseDiscount) {
        PartyCurrencies.WrittenLine line = lines.get(lineId);
        if (line != null && line.discount() != null) {
            return line.discount().doubleValue();
        }
        return fromBase(baseDiscount);
    }

    /** The invoice's header total and discount, in the return's currency. */
    public ReturnableRepository.SourceAmounts amounts(ReturnableRepository.SourceAmounts base) {
        Objects.requireNonNull(base, "base");
        if (rate == null || total == null || discount == null) {
            return base;
        }
        return new ReturnableRepository.SourceAmounts(total.doubleValue(), discount.doubleValue());
    }

    private double fromBase(double base) {
        if (rate == null) {
            return base;
        }
        return MoneyMath.decimal(base).divide(rate, 2, RoundingMode.HALF_UP).doubleValue();
    }
}
