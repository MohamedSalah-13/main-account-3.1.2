package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the offers give a sale, line by line (docs/pricing-and-offers-plan.md ق-ع٤). No database and no
 * JavaFX: the screen runs it after every change to the lines to show what the save will accept, and the save
 * runs it again inside its transaction, with the offers read afresh, and refuses a document that says
 * anything else ({@link OfferGuard}).
 * <p>
 * Phase B's three kinds each work on one line alone, so a line's discount is decided by the line:
 * <ul>
 *   <li>{@link OfferKind#PERCENT} - that share of the line's value;</li>
 *   <li>{@link OfferKind#AMOUNT} - the amount for each unit sold: of the offer's unit when it names one (and
 *       then a line in another unit is not reached), else of the item's base unit, so a carton of twelve
 *       takes twelve times an amount written for a piece;</li>
 *   <li>{@link OfferKind#PRICE} - down to the offer's price for the unit, reckoned the same way, and
 *       <b>never up</b>: a customer whose tier already sells below the offer takes nothing from it (ق-ع٦).</li>
 * </ul>
 * <b>The discount is worked on the price the line charges</b>, which is where this departs from the plan's
 * "the list price": a price typed below the list is already a discount, and a percentage of the list on top
 * of it - or an offer price reached from the list - would give the customer the difference twice. On a line
 * charged at its list, which is nearly every line, the two are the same figure.
 * <p>
 * A line is given one offer at most (ق-ع٥): of those that reach it, the highest {@link Offer#priority()}, then
 * the one giving the customer more, then the oldest. Every discount is money rounded half up and never more
 * than the line is worth. The engine is greedy and deterministic and does not search for the best
 * combination of offers across lines - with one offer a line there is none to search for; the quantity and
 * bundle offers of the later phases will say how they are ordered here.
 */
public final class OfferEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private OfferEngine() {
    }

    /**
     * A line as the engine sees it: which item in which unit, the item's groups, how many base units one of
     * the line's unit holds, how many were sold and at what price. {@code index} is the line's place on the
     * document, which is how the answer is read back.
     */
    public record Line(int index, int itemId, int unitId, int subGroupId, int mainGroupId, BigDecimal factor,
                       BigDecimal quantity, BigDecimal price) {

        public Line {
            Objects.requireNonNull(factor, "factor");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(price, "price");
        }

        public BigDecimal value() {
            return money(price.multiply(quantity));
        }
    }

    /**
     * The document the lines are on: its date, the tier it is priced at (null when it names none), and the
     * offers its own saved lines already carry - which reach it even once stopped (ق-ع٧).
     */
    public record Context(LocalDate date, Integer priceTierId, Set<Integer> recordedOfferIds) {

        public Context {
            Objects.requireNonNull(date, "date");
            recordedOfferIds = recordedOfferIds == null ? Set.of() : Set.copyOf(recordedOfferIds);
        }

        public Context(LocalDate date, Integer priceTierId) {
            this(date, priceTierId, Set.of());
        }
    }

    /** One line's offer: which, and how much of the line's discount is it. */
    public record Applied(int index, Offer offer, BigDecimal discount) {
    }

    /** One offer's part of the document, for the footer and the paper: its lines and its discount. */
    public record OfferTotal(Offer offer, int lines, BigDecimal discount) {
    }

    /** Every line's answer, and each offer's total in the order the offers first appear on the lines. */
    public record Result(Map<Integer, Applied> byIndex, List<OfferTotal> totals) {

        public Result {
            byIndex = Map.copyOf(byIndex);
            totals = List.copyOf(totals);
        }

        public Optional<Applied> forLine(int index) {
            return Optional.ofNullable(byIndex.get(index));
        }

        public BigDecimal discount() {
            return totals.stream().map(OfferTotal::discount).reduce(money(BigDecimal.ZERO), BigDecimal::add);
        }

        public boolean isEmpty() {
            return byIndex.isEmpty();
        }

        public static Result none() {
            return new Result(Map.of(), List.of());
        }
    }

    public static Result apply(List<Line> lines, Context context, Collection<Offer> offers) {
        List<Offer> live = offers.stream()
                .filter(offer -> offer.reaches(context.date(), context.priceTierId(),
                        context.recordedOfferIds().contains(offer.id())))
                .toList();
        if (live.isEmpty() || lines.isEmpty()) {
            return Result.none();
        }
        Map<Integer, Applied> byIndex = new LinkedHashMap<>();
        for (Line line : lines) {
            best(line, live).ifPresent(applied -> byIndex.put(line.index(), applied));
        }
        Map<Integer, OfferTotal> totals = new LinkedHashMap<>();
        for (Line line : lines) {
            Applied applied = byIndex.get(line.index());
            if (applied != null) {
                totals.merge(applied.offer().id(), new OfferTotal(applied.offer(), 1, applied.discount()),
                        (sum, one) -> new OfferTotal(sum.offer(), sum.lines() + 1,
                                sum.discount().add(one.discount())));
            }
        }
        return new Result(byIndex, new ArrayList<>(totals.values()));
    }

    /**
     * What one offer would give one line, if its targets and unit reach it - zero when it reaches it and
     * gives nothing, as an offer price above what the line charges does.
     */
    public static Optional<BigDecimal> discountFor(Offer offer, Line line) {
        if (!offer.targets(line)) {
            return Optional.empty();
        }
        if (offer.unitId() != null && offer.unitId() != line.unitId()) {
            return Optional.empty();
        }
        BigDecimal perUnit = switch (offer.kind()) {
            case PERCENT -> line.price().multiply(offer.percent()).divide(HUNDRED, 10, RoundingMode.HALF_UP);
            case AMOUNT -> offer.amount().multiply(unitsOfTheOffer(offer, line));
            case PRICE -> line.price().subtract(offer.offerPrice().multiply(unitsOfTheOffer(offer, line)))
                    .max(BigDecimal.ZERO);
        };
        BigDecimal discount = money(perUnit.multiply(line.quantity()));
        return Optional.of(discount.min(line.value()).max(money(BigDecimal.ZERO)));
    }

    /** One of the line's units counted in the offer's: one when the offer names the unit, else its base units. */
    private static BigDecimal unitsOfTheOffer(Offer offer, Line line) {
        return offer.unitId() != null ? BigDecimal.ONE : line.factor();
    }

    private static Optional<Applied> best(Line line, List<Offer> offers) {
        Comparator<Applied> order = Comparator.<Applied>comparingInt(applied -> applied.offer().priority())
                .reversed()
                .thenComparing(Applied::discount, Comparator.reverseOrder())
                .thenComparingInt(applied -> applied.offer().id());
        return offers.stream()
                .map(offer -> discountFor(offer, line)
                        .filter(discount -> discount.signum() > 0)
                        .map(discount -> new Applied(line.index(), offer, discount)))
                .flatMap(Optional::stream)
                .min(order);
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
