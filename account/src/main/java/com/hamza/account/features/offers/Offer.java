package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An offer as {@code offer}, {@code offer_target} and {@code offer_price_tier} hold it
 * (docs/pricing-and-offers-plan.md §4.2). A plain value: the engine reads it, the form builds it, the
 * repository writes it, and nothing here reaches a database.
 * <p>
 * Of {@link #percent}, {@link #amount} and {@link #offerPrice} the one its {@link #kind} uses is set - a
 * quantity offer's price is its group's, in {@link #offerPrice} - and a quantity offer and a "buy and get"
 * set {@link #buyQuantity}, a "buy and get" {@link #getQuantity} and {@link #getPercent} too; which is what
 * {@code offer_kind_chk} holds the row to. {@link #unitId} is the unit the offer counts in: a line in another
 * unit is not reached, and with none an item's base units are counted.
 * <p>
 * The two limits count the offer's <b>times</b> ({@link #groupSize()} units each): {@link #maxPerInvoice} on
 * one invoice, {@link #quantityLimit} on every invoice less what came back (V86). No tier id means every tier.
 * <p>
 * A bundle's price is {@link #offerPrice}, its components are its targets of the role {@code COMPONENT}, and it
 * may carry a {@link #barcode}; an invoice offer starts at {@link #threshold} and gives {@link #percent} or
 * {@link #amount} - one of them (V87).
 * {@link #version} is {@code updated_at}, compared when the offer is saved so an edit made on another till is
 * not overwritten.
 */
public record Offer(int id, String name, OfferKind kind, OfferStatus status, LocalDate startsOn,
                    LocalDate endsOn, Integer weekdays, int priority, BigDecimal percent, BigDecimal amount,
                    BigDecimal offerPrice, Integer unitId, BigDecimal buyQuantity, BigDecimal getQuantity,
                    BigDecimal getPercent, BigDecimal maxPerInvoice, BigDecimal quantityLimit, BigDecimal threshold,
                    String barcode, String notes, List<OfferTarget> targets, Set<Integer> priceTierIds,
                    LocalDateTime version) {

    public Offer {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startsOn, "startsOn");
        targets = targets == null ? List.of() : List.copyOf(targets);
        priceTierIds = priceTierIds == null ? Set.of() : Set.copyOf(priceTierIds);
        barcode = barcode == null || barcode.isBlank() ? null : barcode.strip();
    }

    /** An offer of phase B's or phase C's kinds - no threshold and no barcode, which came with V87. */
    public Offer(int id, String name, OfferKind kind, OfferStatus status, LocalDate startsOn, LocalDate endsOn,
                 Integer weekdays, int priority, BigDecimal percent, BigDecimal amount, BigDecimal offerPrice,
                 Integer unitId, BigDecimal buyQuantity, BigDecimal getQuantity, BigDecimal getPercent,
                 BigDecimal maxPerInvoice, BigDecimal quantityLimit, String notes, List<OfferTarget> targets,
                 Set<Integer> priceTierIds, LocalDateTime version) {
        this(id, name, kind, status, startsOn, endsOn, weekdays, priority, percent, amount, offerPrice, unitId,
                buyQuantity, getQuantity, getPercent, maxPerInvoice, quantityLimit, null, null, notes, targets,
                priceTierIds, version);
    }

    /** An offer of phase B's three kinds, with no limit - what every offer was before V86. */
    public Offer(int id, String name, OfferKind kind, OfferStatus status, LocalDate startsOn, LocalDate endsOn,
                 Integer weekdays, int priority, BigDecimal percent, BigDecimal amount, BigDecimal offerPrice,
                 Integer unitId, String notes, List<OfferTarget> targets, Set<Integer> priceTierIds,
                 LocalDateTime version) {
        this(id, name, kind, status, startsOn, endsOn, weekdays, priority, percent, amount, offerPrice, unitId,
                null, null, null, null, null, null, null, notes, targets, priceTierIds, version);
    }

    /** The same offer with these targets and tiers - what the repository reads in a second query. */
    public Offer withTargetsAndTiers(List<OfferTarget> newTargets, Set<Integer> newTiers) {
        return new Offer(id, name, kind, status, startsOn, endsOn, weekdays, priority, percent, amount, offerPrice,
                unitId, buyQuantity, getQuantity, getPercent, maxPerInvoice, quantityLimit, threshold, barcode, notes,
                newTargets, newTiers, version);
    }

    /** The same offer at this status - an edit never moves it; switching on and stopping do. */
    public Offer withStatus(OfferStatus newStatus) {
        return new Offer(id, name, kind, newStatus, startsOn, endsOn, weekdays, priority, percent, amount,
                offerPrice, unitId, buyQuantity, getQuantity, getPercent, maxPerInvoice, quantityLimit, threshold,
                barcode, notes, targets, priceTierIds, version);
    }

    /**
     * How many units one time of the offer is: one for a percentage, an amount or a price; the group for a
     * quantity price; what is bought and what is given together for a "buy and get". The limits count times,
     * and what a line records ({@code offer_quantity}) is units - so the used times are the units over this.
     */
    public BigDecimal groupSize() {
        return switch (kind) {
            case PERCENT, AMOUNT, PRICE, INVOICE -> BigDecimal.ONE;
            case QUANTITY_PRICE -> buyQuantity;
            case BUY_GET -> buyQuantity.add(getQuantity);
            case BUNDLE -> components().stream().map(OfferTarget::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        };
    }

    /** A bundle's components, in the order they were written; none for any other kind. */
    public List<OfferTarget> components() {
        return targets.stream().filter(OfferTarget::component).toList();
    }

    /** The gift a "buy and get" names; none gives more of the item itself. */
    public Optional<OfferTarget> rewardTarget() {
        return targets.stream().filter(OfferTarget::reward).findFirst();
    }

    /**
     * Whether the offer may reach a document dated {@code day}, priced at {@code tierId}. An offer already
     * recorded on the document's own saved lines passes whatever its status is now (ق-ع٧): stopping an offer
     * does not take it back from an invoice it was given on, any more than today's rate re-rates a saved
     * document. Its dates, days and tiers still apply - they do not move once an offer is used.
     */
    public boolean reaches(LocalDate day, Integer tierId, boolean recordedOnTheDocument) {
        boolean live = status == OfferStatus.ACTIVE || (recordedOnTheDocument && status != OfferStatus.DRAFT);
        return live
                && !day.isBefore(startsOn)
                && (endsOn == null || !day.isAfter(endsOn))
                && Weekdays.includes(weekdays, day)
                && (priceTierIds.isEmpty() || (tierId != null && priceTierIds.contains(tierId)));
    }

    /**
     * Whether the offer's targets reach the line: one of the targets that earn it names it and none of the
     * excluded ones does. An offer with only exclusions reaches nothing - the form refuses to save one. A
     * gift is not reached this way: {@link #rewards} says whether a line is the gift. Nor is a bundle's
     * component: a bundle has no targets that earn it, and the engine counts its components itself.
     */
    public boolean targets(OfferEngine.Line line) {
        boolean reached = false;
        for (OfferTarget target : targets) {
            if (target.role() != OfferRole.QUALIFY) {
                continue;
            }
            if (target.names(line)) {
                if (target.excluded()) {
                    return false;
                }
                reached = true;
            }
        }
        return reached;
    }

    /** Whether the line is the gift this "buy and get" names. */
    public boolean rewards(OfferEngine.Line line) {
        return rewardTarget().map(target -> target.names(line)).orElse(false);
    }
}
