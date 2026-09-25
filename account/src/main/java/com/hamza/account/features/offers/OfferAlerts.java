package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What the two offer reminders say, and on which day (docs/pricing-and-offers-plan.md phase E) - over plain
 * values, with a test per boundary, so the notification sources only read and announce.
 * <ul>
 *   <li><b>An offer about to end</b>: a switched-on offer whose last day is today or tomorrow. "Tomorrow" is
 *       the reminder the plan asks for; today is said too, since a shop that did not open yesterday still
 *       has the day to extend it.</li>
 *   <li><b>An item on offer running out</b>: an item an offer names by itself - what earns it, its gift, a
 *       bundle's component - whose stock, across every warehouse, is gone or down to its minimum. Named by
 *       itself only: an offer on a whole group or on everything would put the whole low-stock list here a
 *       second time, which {@code LowStockSource} already says.</li>
 * </ul>
 * Both judge an offer by its dates alone, not its days of the week or its tiers: a Friday offer is still on
 * on a Thursday, and the stock it needs is needed tomorrow.
 */
public final class OfferAlerts {

    private OfferAlerts() {
    }

    /** An offer whose last day is {@code daysLeft} away: zero is today, one tomorrow. */
    public record Ending(Offer offer, long daysLeft) {
    }

    /** An item's stock across every warehouse, in its base unit, and its minimum - zero when none is set. */
    public record ItemBalance(int itemId, String name, BigDecimal minimum, BigDecimal balance) {

        public ItemBalance {
            minimum = minimum == null ? BigDecimal.ZERO : minimum;
            balance = balance == null ? BigDecimal.ZERO : balance;
        }

        /** Gone, or at or below a minimum that was set. */
        public boolean isShort() {
            return balance.signum() <= 0 || (minimum.signum() > 0 && balance.compareTo(minimum) <= 0);
        }
    }

    /** An item on offer that is running out, and the offers that name it. */
    public record ShortItem(ItemBalance item, List<Offer> offers) {

        public ShortItem {
            offers = List.copyOf(offers);
        }
    }

    /** The switched-on offers whose last day is today or tomorrow, the soonest first. */
    public static List<Ending> ending(Collection<Offer> offers, LocalDate today) {
        Objects.requireNonNull(today, "today");
        List<Ending> ending = new ArrayList<>();
        for (Offer offer : offers) {
            if (offer.status() != OfferStatus.ACTIVE || offer.endsOn() == null || offer.endsOn().isBefore(today)) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, offer.endsOn());
            if (daysLeft <= 1) {
                ending.add(new Ending(offer, daysLeft));
            }
        }
        ending.sort(Comparator.comparingLong(Ending::daysLeft).thenComparingInt(end -> end.offer().id()));
        return ending;
    }

    /** Whether a switched-on offer's dates hold {@code day} - or will: one that has not started yet still counts. */
    static boolean runningOrAhead(Offer offer, LocalDate day) {
        return offer.status() == OfferStatus.ACTIVE && (offer.endsOn() == null || !offer.endsOn().isBefore(day));
    }

    /** The items the switched-on offers name by themselves - never through a group, and never left out. */
    public static Set<Integer> namedItems(Collection<Offer> offers, LocalDate today) {
        Set<Integer> items = new LinkedHashSet<>();
        for (Offer offer : offers) {
            if (!runningOrAhead(offer, today)) {
                continue;
            }
            for (OfferTarget target : offer.targets()) {
                if (target.scope() == OfferScope.ITEM && !target.excluded()) {
                    items.add(target.itemId());
                }
            }
        }
        return items;
    }

    /** Of the items the offers name by themselves, those running out, each with its offers, the emptiest first. */
    public static List<ShortItem> shortOfStock(Collection<Offer> offers, Collection<ItemBalance> balances,
                                               LocalDate today) {
        Map<Integer, List<Offer>> naming = new LinkedHashMap<>();
        for (Offer offer : offers) {
            if (!runningOrAhead(offer, today)) {
                continue;
            }
            for (OfferTarget target : offer.targets()) {
                if (target.scope() == OfferScope.ITEM && !target.excluded()) {
                    List<Offer> named = naming.computeIfAbsent(target.itemId(), id -> new ArrayList<>());
                    if (!named.contains(offer)) {
                        named.add(offer);
                    }
                }
            }
        }
        List<ShortItem> shortItems = new ArrayList<>();
        for (ItemBalance balance : balances) {
            List<Offer> named = naming.get(balance.itemId());
            if (named != null && balance.isShort()) {
                shortItems.add(new ShortItem(balance, named));
            }
        }
        shortItems.sort(Comparator.comparing((ShortItem item) -> item.item().balance())
                .thenComparingInt(item -> item.item().itemId()));
        return shortItems;
    }
}
