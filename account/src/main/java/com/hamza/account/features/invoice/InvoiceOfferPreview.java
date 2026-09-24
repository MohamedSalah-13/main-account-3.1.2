package com.hamza.account.features.invoice;

import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferEngine;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the invoice screen shows of the offers: the engine run over the lines after every change, with the
 * till's snapshot of the offers, and its answer written on the lines (docs/pricing-and-offers-plan.md ق-ع٤).
 * A preview - the save runs the engine again over offers it reads itself, and refuses what disagrees.
 * <p>
 * No JavaFX, so each rule the screen follows is tested without a toolkit: the snapshot is replaced when the
 * offers change on any till, an item's groups are asked of the database once and kept, and a document the
 * engine does not run on - a foreign currency, a return, an edition without the add-on - has its lines'
 * offers taken off rather than left claiming what the save would refuse.
 */
public final class InvoiceOfferPreview {

    private final InvoiceOffers.GroupLookup lookup;
    private final Map<Integer, InvoiceOffers.ItemGroups> groups = new HashMap<>();
    private List<Offer> offers = List.of();
    private Set<Integer> recorded = Set.of();
    private OfferEngine.Result last = OfferEngine.Result.none();

    public InvoiceOfferPreview(InvoiceOffers.GroupLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    /** The offers the till may apply - replaced whole when they change anywhere. */
    public void setOffers(List<Offer> offers) {
        this.offers = List.copyOf(offers);
    }

    /** The offers a reopened sale's own lines carry; empty for a new one. */
    public void setRecorded(Set<Integer> recorded) {
        this.recorded = Set.copyOf(recorded);
    }

    /** The offers a reopened sale's lines carry, read off the lines as loaded. */
    public static Set<Integer> recordedOn(Collection<? extends BasePurchasesAndSales> lines) {
        return lines.stream().map(BasePurchasesAndSales::getOfferId).filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Runs the engine over the lines and writes the answer on them. Answers whether a line moved, which is
     * when the table has to be redrawn - the offer's name is not a property.
     */
    public boolean run(List<? extends BasePurchasesAndSales> lines, LocalDate date, Integer tierId)
            throws DaoException {
        if (offers.isEmpty() && lines.stream().allMatch(line -> line.getOfferId() == null)) {
            last = OfferEngine.Result.none();
            return false;
        }
        OfferEngine.Result result = OfferEngine.apply(InvoiceOffers.engineLines(lines, this::groupsOf, true),
                new OfferEngine.Context(date, tierId, recorded), offers);
        last = result;
        return InvoiceOffers.apply(lines, result);
    }

    /** Takes every offer off the lines: a document the engine does not run on claims none. */
    public boolean clear(List<? extends BasePurchasesAndSales> lines) {
        last = OfferEngine.Result.none();
        return InvoiceOffers.apply(lines, OfferEngine.Result.none());
    }

    /** The last answer - each offer's total, for the footer. */
    public OfferEngine.Result last() {
        return last;
    }

    private Map<Integer, InvoiceOffers.ItemGroups> groupsOf(Collection<Integer> itemIds) throws DaoException {
        List<Integer> missing = itemIds.stream().distinct().filter(id -> !groups.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            groups.putAll(lookup.groupsOf(missing));
            missing.forEach(id -> groups.putIfAbsent(id, new InvoiceOffers.ItemGroups(0, 0)));
        }
        Map<Integer, InvoiceOffers.ItemGroups> answer = new HashMap<>();
        itemIds.forEach(id -> answer.put(id, groups.get(id)));
        return answer;
    }
}
