package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The items an offer would sell below their cost (docs/pricing-and-offers-plan.md ق-ع٩), asked once when the
 * offer is written or switched on - never at the till. Selling rice at a loss to bring a customer in is a
 * legitimate decision; it is taken once, knowingly, and this is what lets it be.
 * <p>
 * Each item the offer reaches is judged in the unit the offer names, or its base unit, at every tier the
 * offer reaches that prices it: <b>one time of the offer</b> - a unit of a price offer, a whole group of a
 * quantity offer or a "buy and get" - at that tier's price, and what the engine leaves of it, per unit. On a
 * "buy and get" of a gift item the gift is judged beside each item that earns it, the discount shared between
 * them by value as it is on an invoice (ق-ع٢). An item is listed at its worst tier, the gift once. A bundle is
 * judged whole, each component at its share (V87); an invoice offer as the percentage it would take off each
 * item it reaches.
 */
public final class OfferCostCheck {

    private OfferCostCheck() {
    }

    /**
     * An item in one unit, as the check reads it: its groups, how many base units the unit holds, what one
     * of it costs, and what it sells for at each tier - index 0 is tier 1; zero is no price there.
     */
    public record Candidate(int itemId, String name, int unitId, String unitName, int subGroupId,
                            int mainGroupId, BigDecimal factor, BigDecimal cost, List<BigDecimal> tierPrices) {

        public Candidate {
            Objects.requireNonNull(factor, "factor");
            Objects.requireNonNull(cost, "cost");
            tierPrices = List.copyOf(tierPrices);
        }

        BigDecimal priceAt(int tier) {
            if (tier < 1 || tier > tierPrices.size()) {
                return null;
            }
            BigDecimal price = tierPrices.get(tier - 1);
            return price == null || price.signum() <= 0 ? null : price;
        }
    }

    /** One item the offer takes below its cost: at which tier, its price, what the offer leaves, the cost. */
    public record BelowCost(int itemId, String name, String unitName, int tierId, BigDecimal price, BigDecimal net,
                            BigDecimal cost) {
    }

    /** {@link #below(Offer, List, Candidate, Set)} for an offer with no gift. */
    public static List<BelowCost> below(Offer offer, List<Candidate> candidates, Set<Integer> activeTiers) {
        return below(offer, candidates, null, activeTiers);
    }

    /**
     * @param gift        the gift a "buy and get" names, in the gift's unit or base; null for any other offer,
     *                    and for a gift that is not in the catalogue - which is then judged on nothing
     * @param activeTiers the tiers in use, which an offer naming none reaches
     */
    public static List<BelowCost> below(Offer offer, List<Candidate> candidates, Candidate gift,
                                        Set<Integer> activeTiers) {
        if (offer.kind() == OfferKind.BUNDLE) {
            return bundleBelow(offer, candidates, activeTiers);
        }
        if (offer.kind() == OfferKind.INVOICE) {
            return below(asPercent(offer), candidates, null, activeTiers);
        }
        Set<Integer> tiers = offer.priceTierIds().isEmpty() ? activeTiers : offer.priceTierIds();
        List<Integer> sortedTiers = tiers.stream().sorted().toList();
        boolean giftOffer = offer.rewardTarget().isPresent();
        List<BelowCost> found = new ArrayList<>();
        BelowCost giftWorst = null;
        for (Candidate candidate : candidates) {
            if (giftOffer && gift != null && candidate.itemId() == gift.itemId()) {
                continue;
            }
            BelowCost worst = null;
            for (int tier : sortedTiers) {
                BigDecimal price = candidate.priceAt(tier);
                if (price == null) {
                    continue;
                }
                BigDecimal quantity = oneTime(offer);
                OfferEngine.Line line = new OfferEngine.Line(0, candidate.itemId(), candidate.unitId(),
                        candidate.subGroupId(), candidate.mainGroupId(), candidate.factor(), quantity, price);
                if (!offer.targets(line)) {
                    continue;
                }
                List<OfferEngine.Line> lines = new ArrayList<>(List.of(line));
                OfferEngine.Line giftLine = null;
                if (giftOffer) {
                    BigDecimal giftPrice = gift == null ? null : gift.priceAt(tier);
                    if (giftPrice == null) {
                        continue;
                    }
                    giftLine = new OfferEngine.Line(1, gift.itemId(), gift.unitId(), gift.subGroupId(),
                            gift.mainGroupId(), gift.factor(), offer.getQuantity(), giftPrice);
                    lines.add(giftLine);
                }
                Map<Integer, BigDecimal> given = OfferEngine.givenAlone(offer, lines);
                if (!giftOffer && !given.containsKey(0)) {
                    continue;
                }
                BigDecimal net = netPerUnit(line, given.getOrDefault(0, BigDecimal.ZERO));
                if (net.compareTo(candidate.cost()) < 0 && (worst == null || net.compareTo(worst.net()) < 0)) {
                    worst = new BelowCost(candidate.itemId(), candidate.name(), candidate.unitName(), tier, price,
                            net, OfferEngine.money(candidate.cost()));
                }
                if (giftLine != null) {
                    BigDecimal giftNet = netPerUnit(giftLine, given.getOrDefault(1, BigDecimal.ZERO));
                    if (giftNet.compareTo(gift.cost()) < 0
                            && (giftWorst == null || giftNet.compareTo(giftWorst.net()) < 0)) {
                        giftWorst = new BelowCost(gift.itemId(), gift.name(), gift.unitName(), tier,
                                giftLine.price(), giftNet, OfferEngine.money(gift.cost()));
                    }
                }
            }
            if (worst != null) {
                found.add(worst);
            }
        }
        if (giftWorst != null) {
            found.add(giftWorst);
        }
        found.sort(Comparator.comparing(BelowCost::name, Comparator.nullsLast(Comparator.naturalOrder())));
        return found;
    }

    /** One time of the offer, in its unit: a unit, or a whole group - on a gift offer, what is bought. */
    private static BigDecimal oneTime(Offer offer) {
        return switch (offer.kind()) {
            case PERCENT, AMOUNT, PRICE, BUNDLE, INVOICE -> BigDecimal.ONE;
            case QUANTITY_PRICE -> offer.buyQuantity();
            case BUY_GET -> offer.rewardTarget().isPresent() ? offer.buyQuantity() : offer.groupSize();
        };
    }

    /**
     * A bundle judged whole: one bundle at each tier every component has a price at, the discount shared among
     * the components by value as on an invoice (ق-ع٢), and each component that comes out under its cost listed
     * at its worst tier. {@code candidates} holds each component in its unit, or its base unit.
     */
    private static List<BelowCost> bundleBelow(Offer offer, List<Candidate> candidates, Set<Integer> activeTiers) {
        Set<Integer> tiers = offer.priceTierIds().isEmpty() ? activeTiers : offer.priceTierIds();
        List<OfferTarget> components = offer.components();
        List<Candidate> ordered = new ArrayList<>();
        for (OfferTarget component : components) {
            Candidate found = candidates.stream()
                    .filter(candidate -> candidate.itemId() == component.itemId()
                            && (component.unitId() == null || candidate.unitId() == component.unitId()))
                    .findFirst().orElse(null);
            if (found == null) {
                return List.of();
            }
            ordered.add(found);
        }
        Map<Integer, BelowCost> worst = new java.util.LinkedHashMap<>();
        for (int tier : tiers.stream().sorted().toList()) {
            List<OfferEngine.Line> lines = new ArrayList<>();
            for (int index = 0; index < ordered.size(); index++) {
                Candidate candidate = ordered.get(index);
                BigDecimal price = candidate.priceAt(tier);
                if (price == null) {
                    lines = null;
                    break;
                }
                lines.add(new OfferEngine.Line(index, candidate.itemId(), candidate.unitId(), candidate.subGroupId(),
                        candidate.mainGroupId(), candidate.factor(), components.get(index).quantity(), price));
            }
            if (lines == null) {
                continue;
            }
            Map<Integer, BigDecimal> given = OfferEngine.givenAlone(offer, lines);
            for (OfferEngine.Line line : lines) {
                Candidate candidate = ordered.get(line.index());
                BigDecimal net = netPerUnit(line, given.getOrDefault(line.index(), BigDecimal.ZERO));
                BelowCost known = worst.get(line.index());
                if (net.compareTo(candidate.cost()) < 0 && (known == null || net.compareTo(known.net()) < 0)) {
                    worst.put(line.index(), new BelowCost(candidate.itemId(), candidate.name(), candidate.unitName(),
                            tier, line.price(), net, OfferEngine.money(candidate.cost())));
                }
            }
        }
        List<BelowCost> found = new ArrayList<>(worst.values());
        found.sort(Comparator.comparing(BelowCost::name, Comparator.nullsLast(Comparator.naturalOrder())));
        return found;
    }

    /**
     * An invoice offer judged as the percentage it takes off each item it reaches: its own, or its amount over
     * its threshold - the most an amount can take, on an invoice that only just reaches it.
     */
    static Offer asPercent(Offer offer) {
        BigDecimal percent = offer.percent() != null ? offer.percent()
                : offer.amount().multiply(BigDecimal.valueOf(100))
                .divide(offer.threshold(), 3, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100));
        return new Offer(offer.id(), offer.name(), OfferKind.PERCENT, offer.status(), offer.startsOn(),
                offer.endsOn(), offer.weekdays(), offer.priority(), percent, null, null, null, offer.notes(),
                offer.targets(), offer.priceTierIds(), offer.version());
    }

    /** What a unit of the line comes to once the offer's part of its discount is taken off. */
    private static BigDecimal netPerUnit(OfferEngine.Line line, BigDecimal discount) {
        return OfferEngine.money(line.value().subtract(discount)
                .divide(line.quantity(), 10, RoundingMode.HALF_UP));
    }
}
