package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
 * <b>The three that work on one line alone</b> (phase B):
 * <ul>
 *   <li>{@link OfferKind#PERCENT} - that share of the line's value;</li>
 *   <li>{@link OfferKind#AMOUNT} - the amount for each unit sold: of the offer's unit when it names one (and
 *       then a line in another unit is not reached), else of the item's base unit, so a carton of twelve
 *       takes twelve times an amount written for a piece;</li>
 *   <li>{@link OfferKind#PRICE} - down to the offer's price for the unit, reckoned the same way, and
 *       <b>never up</b>: a customer whose tier already sells below the offer takes nothing from it (ق-ع٦).</li>
 * </ul>
 * <b>The two that count a quantity</b> (phase C) count an item's lines together, in the offer's unit or its
 * base units - a carton of twelve and three pieces are fifteen pieces (ق-ع٦). Each item is counted apart: an
 * offer on a group is "3 for 100" on each item of the group, never a mix of them.
 * <ul>
 *   <li>{@link OfferKind#QUANTITY_PRICE} - every whole group of {@code buyQuantity} units for the offer's
 *       price, the dearest units first, and never above what they charge: a group already cheaper than the
 *       offer takes nothing;</li>
 *   <li>{@link OfferKind#BUY_GET} - {@code buyQuantity} bought earns {@code getQuantity} at
 *       {@code getPercent} off. Of the item itself, the cheapest units of each group are the ones given; of a
 *       gift item, the gift has to be on the invoice (ق-ع٣: no line at zero - the gift is a line at its price
 *       with the offer's discount), and a gift earned and not there is a hint.</li>
 * </ul>
 * A pooled offer's discount is shared among the lines that earned it <b>by their value</b>, rounded half up,
 * the remainder on the largest (ق-ع٢) - a return then refunds what was paid for what came back with no rule
 * of its own. What a line records besides is how many of its units the offer covered ({@code offer_quantity}),
 * which is what the global limit counts.
 * <p>
 * <b>The order is fixed</b> (ق-ع٥): the "buy and get" offers, then the quantity offers, then the price
 * offers. A line is given one offer at most, and a pooled offer that covers part of a line takes the whole
 * line - the part it does not cover goes without, since a line names one offer. Within a kind: the highest
 * {@link Offer#priority()}, then the one giving the customer more, then the oldest. The engine is greedy and
 * deterministic and does not search for the best combination of offers across the document.
 * <p>
 * <b>The limits count times</b> - a unit for the three price kinds, a group for the two quantity kinds - on
 * this document ({@link Offer#maxPerInvoice()}) and, what the other documents have not used,
 * over all of them ({@link Context#timesLeft()}). A price offer's limit is shared out line by line in the
 * lines' order.
 * <p>
 * <b>Hints</b> say what would earn an offer: the units that complete a quantity group, the one more unit a
 * "buy and get" would give, a gift earned and not on the invoice.
 */
public final class OfferEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 10;

    /** The pooled kinds, in the order they take lines (ق-ع٥). */
    private static final List<OfferKind> POOLED = List.of(OfferKind.BUY_GET, OfferKind.QUANTITY_PRICE);

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
     * The document the lines are on: its date, the tier it is priced at (null when it names none), the offers
     * its own saved lines already carry - which reach it even once stopped (ق-ع٧) - and, for an offer with a
     * global limit, how many times the other documents have left it. An offer with a limit and no entry here
     * is taken to have all of it left.
     */
    public record Context(LocalDate date, Integer priceTierId, Set<Integer> recordedOfferIds,
                          Map<Integer, BigDecimal> timesLeft) {

        public Context {
            Objects.requireNonNull(date, "date");
            recordedOfferIds = recordedOfferIds == null ? Set.of() : Set.copyOf(recordedOfferIds);
            timesLeft = timesLeft == null ? Map.of() : Map.copyOf(timesLeft);
        }

        public Context(LocalDate date, Integer priceTierId, Set<Integer> recordedOfferIds) {
            this(date, priceTierId, recordedOfferIds, Map.of());
        }

        public Context(LocalDate date, Integer priceTierId) {
            this(date, priceTierId, Set.of(), Map.of());
        }
    }

    /**
     * One line's offer: which, how much of the line's discount is it, and how many of the line's units it
     * covered, in the offer's unit or the item's base. A quantity of null is not asserted - an answer built
     * before the offers counted one.
     */
    public record Applied(int index, Offer offer, BigDecimal discount, BigDecimal quantity) {

        public Applied(int index, Offer offer, BigDecimal discount) {
            this(index, offer, discount, null);
        }
    }

    /** One offer's part of the document, for the footer and the paper: its lines and its discount. */
    public record OfferTotal(Offer offer, int lines, BigDecimal discount) {
    }

    /** What a hint asks for. */
    public enum HintKind {
        /** Units of the item that would complete a quantity group. */
        COMPLETE,
        /** Units of the item a "buy and get" would give - the customer has bought enough for them already. */
        FREE,
        /** The gift item a "buy and get" earned, not on the invoice. */
        GIFT
    }

    /**
     * What would earn an offer: {@code missing} units of {@code itemId} - in {@code unitId}, or its base
     * units when that is null.
     */
    public record Hint(Offer offer, HintKind kind, int itemId, Integer unitId, BigDecimal missing) {
    }

    /** Every line's answer, each offer's total in the order the offers first appear on the lines, and the hints. */
    public record Result(Map<Integer, Applied> byIndex, List<OfferTotal> totals, List<Hint> hints) {

        public Result {
            byIndex = Map.copyOf(byIndex);
            totals = List.copyOf(totals);
            hints = hints == null ? List.of() : List.copyOf(hints);
        }

        public Result(Map<Integer, Applied> byIndex, List<OfferTotal> totals) {
            this(byIndex, totals, List.of());
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
            return new Result(Map.of(), List.of(), List.of());
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
        Map<Integer, Line> available = new LinkedHashMap<>();
        lines.forEach(line -> available.put(line.index(), line));
        Map<Integer, BigDecimal> left = new HashMap<>();
        for (Offer offer : live) {
            BigDecimal cap = cap(offer, context);
            if (cap != null) {
                left.put(offer.id(), cap);
            }
        }
        Map<Integer, Applied> byIndex = new LinkedHashMap<>();
        List<Hint> hints = new ArrayList<>();

        for (OfferKind kind : POOLED) {
            List<Offer> waiting = new ArrayList<>(live.stream().filter(offer -> offer.kind() == kind).toList());
            while (true) {
                Pooled best = null;
                for (Offer offer : waiting) {
                    Pooled pooled = pooled(offer, available, left.get(offer.id()));
                    if (pooled.discount().signum() > 0 && (best == null || better(pooled, best))) {
                        best = pooled;
                    }
                }
                if (best == null) {
                    break;
                }
                Pooled chosen = best;
                for (Applied applied : chosen.applied()) {
                    byIndex.put(applied.index(), applied);
                    available.remove(applied.index());
                }
                left.computeIfPresent(chosen.offer().id(), (id, times) -> times.subtract(chosen.times()));
                hints.addAll(chosen.hints());
                waiting.remove(chosen.offer());
            }
            for (Offer offer : waiting) {
                hints.addAll(pooled(offer, available, left.get(offer.id())).hints());
            }
        }

        List<Offer> single = live.stream().filter(offer -> !offer.kind().pooled()).toList();
        if (!single.isEmpty()) {
            Comparator<Applied> order = Comparator.<Applied>comparingInt(applied -> applied.offer().priority())
                    .reversed()
                    .thenComparing(Applied::discount, Comparator.reverseOrder())
                    .thenComparingInt(applied -> applied.offer().id());
            for (Line line : List.copyOf(available.values())) {
                Applied best = null;
                for (Offer offer : single) {
                    Applied candidate = single(offer, line, left.get(offer.id()));
                    if (candidate != null && candidate.discount().signum() > 0
                            && (best == null || order.compare(candidate, best) < 0)) {
                        best = candidate;
                    }
                }
                if (best != null) {
                    Applied chosen = best;
                    byIndex.put(line.index(), chosen);
                    available.remove(line.index());
                    left.computeIfPresent(chosen.offer().id(), (id, times) -> times.subtract(chosen.quantity()));
                }
            }
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
        return new Result(byIndex, new ArrayList<>(totals.values()), hints);
    }

    /**
     * What one offer would give one line on its own, if its targets reach it - zero when it reaches it and
     * gives nothing, as an offer price above what the line charges does. A quantity offer is counted on the
     * line alone; a "buy and get" of a gift item gives nothing to one line.
     */
    public static Optional<BigDecimal> discountFor(Offer offer, Line line) {
        if (!offer.kind().pooled()) {
            Applied applied = single(offer, line, null);
            return applied == null ? Optional.empty() : Optional.of(applied.discount());
        }
        if (!offer.targets(line) && !offer.rewards(line)) {
            return Optional.empty();
        }
        return Optional.of(pooled(offer, Map.of(line.index(), line), null).discount());
    }

    /**
     * What this one offer alone gives these lines, by index - its dates, days, tiers and limits aside. The
     * below-cost check's question: "one group of this item, at this tier's price - what is left of it?"
     */
    static Map<Integer, BigDecimal> givenAlone(Offer offer, List<Line> lines) {
        Map<Integer, BigDecimal> given = new LinkedHashMap<>();
        if (!offer.kind().pooled()) {
            for (Line line : lines) {
                Applied applied = single(offer, line, null);
                if (applied != null) {
                    given.put(line.index(), applied.discount());
                }
            }
            return given;
        }
        Map<Integer, Line> available = new LinkedHashMap<>();
        lines.forEach(line -> available.put(line.index(), line));
        for (Applied applied : pooled(offer, available, null).applied()) {
            given.put(applied.index(), applied.discount());
        }
        return given;
    }

    // ---- the three price kinds ---------------------------------------------------------------

    /** A price offer on one line: all of it, or as many of its units as the offer has times left. */
    private static Applied single(Offer offer, Line line, BigDecimal timesLeft) {
        if (!offer.targets(line) || !inTheOffersUnit(offer.unitId(), line)) {
            return null;
        }
        BigDecimal count = count(offer.unitId(), line);
        if (count.signum() <= 0) {
            return null;
        }
        BigDecimal perUnit = switch (offer.kind()) {
            case PERCENT -> line.price().multiply(offer.percent()).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
            case AMOUNT -> offer.amount().multiply(unitsOfTheOffer(offer, line));
            case PRICE -> line.price().subtract(offer.offerPrice().multiply(unitsOfTheOffer(offer, line)))
                    .max(BigDecimal.ZERO);
            default -> throw new IllegalStateException("not a price offer: " + offer.kind());
        };
        BigDecimal whole = money(perUnit.multiply(line.quantity())).min(line.value()).max(money(BigDecimal.ZERO));
        BigDecimal covered = timesLeft == null ? count : count.min(timesLeft.max(BigDecimal.ZERO));
        if (covered.signum() <= 0) {
            return null;
        }
        BigDecimal discount = covered.compareTo(count) == 0
                ? whole
                : money(whole.multiply(covered).divide(count, SCALE, RoundingMode.HALF_UP));
        return new Applied(line.index(), offer, discount, quantity(covered));
    }

    /** One of the line's units counted in the offer's: one when the offer names the unit, else its base units. */
    private static BigDecimal unitsOfTheOffer(Offer offer, Line line) {
        return offer.unitId() != null ? BigDecimal.ONE : line.factor();
    }

    // ---- the two quantity kinds --------------------------------------------------------------

    /** One pooled offer's answer on the lines still free: its lines, its discount, the times it used, its hints. */
    private record Pooled(Offer offer, BigDecimal discount, BigDecimal times, List<Applied> applied,
                          List<Hint> hints) {
    }

    private static boolean better(Pooled candidate, Pooled best) {
        if (candidate.offer().priority() != best.offer().priority()) {
            return candidate.offer().priority() > best.offer().priority();
        }
        int more = candidate.discount().compareTo(best.discount());
        if (more != 0) {
            return more > 0;
        }
        return candidate.offer().id() < best.offer().id();
    }

    private static Pooled pooled(Offer offer, Map<Integer, Line> available, BigDecimal timesLeft) {
        if (offer.kind() == OfferKind.BUY_GET && offer.rewardTarget().isPresent()) {
            return buyGetAGift(offer, available, timesLeft);
        }
        Map<Integer, List<Line>> byItem = new LinkedHashMap<>();
        for (Line line : available.values()) {
            if (offer.targets(line) && inTheOffersUnit(offer.unitId(), line)
                    && count(offer.unitId(), line).signum() > 0) {
                byItem.computeIfAbsent(line.itemId(), id -> new ArrayList<>()).add(line);
            }
        }
        // A quantity offer is given in whole groups, so a limit left at two and a half is two.
        BigDecimal remaining = timesLeft == null ? null
                : timesLeft.max(BigDecimal.ZERO).setScale(0, RoundingMode.FLOOR);
        BigDecimal discount = money(BigDecimal.ZERO);
        BigDecimal times = BigDecimal.ZERO;
        List<Applied> applied = new ArrayList<>();
        List<Hint> hints = new ArrayList<>();
        for (List<Line> itemLines : byItem.values()) {
            ItemAnswer answer = offer.kind() == OfferKind.QUANTITY_PRICE
                    ? quantityPrice(offer, itemLines, remaining)
                    : buyGetTheSame(offer, itemLines, remaining);
            hints.addAll(answer.hints());
            if (answer.discount().signum() <= 0) {
                continue;
            }
            applied.addAll(answer.applied());
            discount = discount.add(answer.discount());
            times = times.add(answer.groups());
            if (remaining != null) {
                remaining = remaining.subtract(answer.groups());
            }
        }
        return new Pooled(offer, discount, times, applied, hints);
    }

    private record ItemAnswer(BigDecimal discount, BigDecimal groups, List<Applied> applied, List<Hint> hints) {
    }

    /** "3 for 100" on one item: its whole groups, the dearest units first, never above what they charge. */
    private static ItemAnswer quantityPrice(Offer offer, List<Line> lines, BigDecimal remaining) {
        BigDecimal size = offer.buyQuantity();
        BigDecimal units = total(offer.unitId(), lines);
        BigDecimal whole = units.divide(size, 0, RoundingMode.FLOOR);
        BigDecimal groups = remaining == null ? whole : whole.min(remaining);
        List<Hint> hints = new ArrayList<>();
        BigDecimal rest = units.subtract(whole.multiply(size));
        BigDecimal dearest = lines.stream().map(line -> unitPrice(offer.unitId(), line))
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        boolean aGroupGives = dearest.multiply(size).compareTo(offer.offerPrice()) > 0;
        if (rest.signum() > 0 && aGroupGives && (remaining == null || remaining.compareTo(groups) > 0)) {
            hints.add(new Hint(offer, HintKind.COMPLETE, lines.get(0).itemId(), offer.unitId(),
                    quantity(size.subtract(rest))));
        }
        if (groups.signum() <= 0) {
            return new ItemAnswer(BigDecimal.ZERO, BigDecimal.ZERO, List.of(), hints);
        }
        Map<Line, BigDecimal> covered = take(lines, groups.multiply(size), offer.unitId());
        BigDecimal value = valueOf(covered, offer.unitId());
        BigDecimal discount = money(value.subtract(groups.multiply(offer.offerPrice())).max(BigDecimal.ZERO));
        if (discount.signum() <= 0) {
            return new ItemAnswer(BigDecimal.ZERO, BigDecimal.ZERO, List.of(), hints);
        }
        return new ItemAnswer(discount, groups, share(offer, discount, covered, offer.unitId()),
                hints);
    }

    /** "Buy 2, get 1" of the item itself: its whole groups, the cheapest units of them the ones given. */
    private static ItemAnswer buyGetTheSame(Offer offer, List<Line> lines, BigDecimal remaining) {
        BigDecimal size = offer.groupSize();
        BigDecimal units = total(offer.unitId(), lines);
        BigDecimal whole = units.divide(size, 0, RoundingMode.FLOOR);
        BigDecimal groups = remaining == null ? whole : whole.min(remaining);
        List<Hint> hints = new ArrayList<>();
        BigDecimal rest = units.subtract(whole.multiply(size));
        if (rest.compareTo(offer.buyQuantity()) >= 0 && (remaining == null || remaining.compareTo(groups) > 0)) {
            hints.add(new Hint(offer, HintKind.FREE, lines.get(0).itemId(), offer.unitId(),
                    quantity(size.subtract(rest))));
        }
        if (groups.signum() <= 0) {
            return new ItemAnswer(BigDecimal.ZERO, BigDecimal.ZERO, List.of(), hints);
        }
        Map<Line, BigDecimal> covered = take(lines, groups.multiply(size), offer.unitId());
        List<Map.Entry<Line, BigDecimal>> cheapestFirst = new ArrayList<>(covered.entrySet());
        cheapestFirst.sort(Comparator.<Map.Entry<Line, BigDecimal>, BigDecimal>comparing(
                        entry -> unitPrice(offer.unitId(), entry.getKey()))
                .thenComparing(entry -> entry.getKey().index(), Comparator.reverseOrder()));
        BigDecimal given = groups.multiply(offer.getQuantity());
        BigDecimal givenValue = BigDecimal.ZERO;
        for (Map.Entry<Line, BigDecimal> entry : cheapestFirst) {
            if (given.signum() <= 0) {
                break;
            }
            BigDecimal these = entry.getValue().min(given);
            givenValue = givenValue.add(these.multiply(unitPrice(offer.unitId(), entry.getKey())));
            given = given.subtract(these);
        }
        BigDecimal discount = money(givenValue.multiply(offer.getPercent()).divide(HUNDRED, SCALE, RoundingMode.HALF_UP));
        if (discount.signum() <= 0) {
            return new ItemAnswer(BigDecimal.ZERO, BigDecimal.ZERO, List.of(), hints);
        }
        return new ItemAnswer(discount, groups, share(offer, discount, covered, offer.unitId()),
                hints);
    }

    /**
     * "Buy the shampoo, get the conditioner": what is bought across every line the offer reaches, the gift
     * across the gift's lines, as many groups as both allow - and a gift earned and not on the invoice, a hint.
     */
    private static Pooled buyGetAGift(Offer offer, Map<Integer, Line> available, BigDecimal timesLeft) {
        OfferTarget gift = offer.rewardTarget().orElseThrow();
        List<Line> bought = new ArrayList<>();
        List<Line> gifts = new ArrayList<>();
        for (Line line : available.values()) {
            if (gift.names(line)) {
                if (count(gift.unitId(), line).signum() > 0) {
                    gifts.add(line);
                }
            } else if (offer.targets(line) && inTheOffersUnit(offer.unitId(), line)
                    && count(offer.unitId(), line).signum() > 0) {
                bought.add(line);
            }
        }
        BigDecimal earned = total(offer.unitId(), bought).divide(offer.buyQuantity(), 0, RoundingMode.FLOOR);
        if (timesLeft != null) {
            earned = earned.min(timesLeft.max(BigDecimal.ZERO).setScale(0, RoundingMode.FLOOR));
        }
        BigDecimal giftUnits = total(gift.unitId(), gifts);
        BigDecimal groups = earned.min(giftUnits.divide(offer.getQuantity(), 0, RoundingMode.FLOOR));
        List<Hint> hints = new ArrayList<>();
        BigDecimal missing = earned.multiply(offer.getQuantity()).subtract(giftUnits);
        if (missing.signum() > 0) {
            hints.add(new Hint(offer, HintKind.GIFT, gift.itemId(), gift.unitId(), quantity(missing)));
        }
        if (groups.signum() <= 0) {
            return new Pooled(offer, money(BigDecimal.ZERO), BigDecimal.ZERO, List.of(), hints);
        }
        Map<Line, BigDecimal> coveredBought = take(bought, groups.multiply(offer.buyQuantity()), offer.unitId());
        Map<Line, BigDecimal> coveredGifts = take(gifts, groups.multiply(offer.getQuantity()), gift.unitId());
        BigDecimal giftValue = valueOf(coveredGifts, gift.unitId());
        BigDecimal discount = money(giftValue.multiply(offer.getPercent()).divide(HUNDRED, SCALE, RoundingMode.HALF_UP));
        if (discount.signum() <= 0) {
            return new Pooled(offer, money(BigDecimal.ZERO), BigDecimal.ZERO, List.of(), hints);
        }
        Map<Line, BigDecimal> covered = new LinkedHashMap<>(coveredBought);
        covered.putAll(coveredGifts);
        Map<Line, BigDecimal> weights = new LinkedHashMap<>();
        coveredBought.forEach((line, units) -> weights.put(line, units.multiply(unitPrice(offer.unitId(), line))));
        coveredGifts.forEach((line, units) -> weights.put(line, units.multiply(unitPrice(gift.unitId(), line))));
        return new Pooled(offer, discount, groups, shareByWeight(offer, discount, covered, weights), hints);
    }

    // ---- counting and sharing ----------------------------------------------------------------

    private static boolean inTheOffersUnit(Integer unitId, Line line) {
        return unitId == null || unitId == line.unitId();
    }

    /** The line's units, in {@code unitId} when it is named - the line's own - else in the item's base units. */
    private static BigDecimal count(Integer unitId, Line line) {
        return unitId != null ? line.quantity() : line.quantity().multiply(line.factor());
    }

    /** What one of those units charges. */
    private static BigDecimal unitPrice(Integer unitId, Line line) {
        return unitId != null || line.factor().compareTo(BigDecimal.ONE) == 0
                ? line.price()
                : line.price().divide(line.factor(), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal total(Integer unitId, List<Line> lines) {
        return lines.stream().map(line -> count(unitId, line)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** {@code units} of the lines, the dearest first and then in the lines' order. */
    private static Map<Line, BigDecimal> take(List<Line> lines, BigDecimal units, Integer unitId) {
        List<Line> dearestFirst = new ArrayList<>(lines);
        dearestFirst.sort(Comparator.<Line, BigDecimal>comparing(line -> unitPrice(unitId, line)).reversed()
                .thenComparingInt(Line::index));
        Map<Line, BigDecimal> covered = new LinkedHashMap<>();
        BigDecimal left = units;
        for (Line line : dearestFirst) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal these = count(unitId, line).min(left);
            if (these.signum() > 0) {
                covered.put(line, these);
                left = left.subtract(these);
            }
        }
        return covered;
    }

    private static BigDecimal valueOf(Map<Line, BigDecimal> covered, Integer unitId) {
        return covered.entrySet().stream()
                .map(entry -> entry.getValue().multiply(unitPrice(unitId, entry.getKey())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<Applied> share(Offer offer, BigDecimal discount, Map<Line, BigDecimal> covered,
                                       Integer unitId) {
        Map<Line, BigDecimal> weights = new LinkedHashMap<>();
        covered.forEach((line, units) -> weights.put(line, units.multiply(unitPrice(unitId, line))));
        return shareByWeight(offer, discount, covered, weights);
    }

    /**
     * The discount over the covered lines by their covered value, rounded half up, the piastres left on the
     * line with the most value - the first of them on a tie (ق-ع٢, {@code ReturnHeaderDiscount}'s rule).
     */
    private static List<Applied> shareByWeight(Offer offer, BigDecimal discount, Map<Line, BigDecimal> covered,
                                               Map<Line, BigDecimal> weights) {
        BigDecimal whole = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Line, BigDecimal> shares = new LinkedHashMap<>();
        BigDecimal given = money(BigDecimal.ZERO);
        Line largest = null;
        for (Map.Entry<Line, BigDecimal> entry : weights.entrySet()) {
            BigDecimal share = whole.signum() == 0 ? money(BigDecimal.ZERO)
                    : money(discount.multiply(entry.getValue()).divide(whole, SCALE, RoundingMode.HALF_UP));
            shares.put(entry.getKey(), share);
            given = given.add(share);
            if (largest == null || entry.getValue().compareTo(weights.get(largest)) > 0
                    || (entry.getValue().compareTo(weights.get(largest)) == 0
                    && entry.getKey().index() < largest.index())) {
                largest = entry.getKey();
            }
        }
        if (largest != null) {
            shares.merge(largest, discount.subtract(given), BigDecimal::add);
        }
        List<Applied> applied = new ArrayList<>();
        shares.forEach((line, share) -> applied.add(new Applied(line.index(), offer,
                share.min(line.value()).max(money(BigDecimal.ZERO)), quantity(covered.get(line)))));
        applied.sort(Comparator.comparingInt(Applied::index));
        return applied;
    }

    /** How many more times the offer may be given on this document: the smaller of its two limits, or none. */
    private static BigDecimal cap(Offer offer, Context context) {
        BigDecimal cap = offer.maxPerInvoice();
        if (offer.quantityLimit() != null) {
            BigDecimal global = context.timesLeft().getOrDefault(offer.id(), offer.quantityLimit())
                    .max(BigDecimal.ZERO);
            cap = cap == null ? global : cap.min(global);
        }
        return cap;
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal quantity(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }
}
