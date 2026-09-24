package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An offer as {@code offer}, {@code offer_target} and {@code offer_price_tier} hold it
 * (docs/pricing-and-offers-plan.md §4.2). A plain value: the engine reads it, the form builds it, the
 * repository writes it, and nothing here reaches a database.
 * <p>
 * Of {@link #percent}, {@link #amount} and {@link #offerPrice} exactly the one its {@link #kind} uses is set,
 * which is what {@code offer_kind_chk} holds the row to; {@link #unitId} is the unit an amount or a price is
 * for, and a line in another unit is not reached. No tier id means every tier. {@link #version} is
 * {@code updated_at}, compared when the offer is saved so an edit made on another till is not overwritten.
 */
public record Offer(int id, String name, OfferKind kind, OfferStatus status, LocalDate startsOn,
                    LocalDate endsOn, Integer weekdays, int priority, BigDecimal percent, BigDecimal amount,
                    BigDecimal offerPrice, Integer unitId, String notes, List<OfferTarget> targets,
                    Set<Integer> priceTierIds, LocalDateTime version) {

    public Offer {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startsOn, "startsOn");
        targets = targets == null ? List.of() : List.copyOf(targets);
        priceTierIds = priceTierIds == null ? Set.of() : Set.copyOf(priceTierIds);
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
     * Whether the offer's targets reach the line: one of them names it and none of the excluded ones does.
     * An offer with only exclusions reaches nothing - the form refuses to save one.
     */
    public boolean targets(OfferEngine.Line line) {
        boolean reached = false;
        for (OfferTarget target : targets) {
            if (target.names(line)) {
                if (target.excluded()) {
                    return false;
                }
                reached = true;
            }
        }
        return reached;
    }
}
