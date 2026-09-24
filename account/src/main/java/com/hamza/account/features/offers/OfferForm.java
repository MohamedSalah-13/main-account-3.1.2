package com.hamza.account.features.offers;

import com.hamza.account.features.pricing.PriceTiers;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * An offer as the form builds it, and every rule it is held to - each refusing with a message key, never a
 * sentence (docs/new-code-rules.md). The limits are the schema's: a name of 100, a note of 255, a percentage
 * of three places up to a hundred, an amount and a price in {@code DECIMAL(14, 2)}.
 */
public final class OfferForm {

    public static final int NAME_MAX = 100;
    public static final int NOTES_MAX = 255;
    public static final int PRIORITY_MAX = 999;
    static final BigDecimal MONEY_MAX = new BigDecimal("999999999999.99");
    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private OfferForm() {
    }

    /**
     * The offer the form says: the value goes to the one column its kind uses, a unit only with an amount or
     * a price, the days as {@code offer.weekdays} stores them - where no day ticked is a refusal and not
     * "every day", which is what a null mask means.
     */
    public static Offer build(int id, String name, OfferKind kind, OfferStatus status, LocalDate startsOn,
                              LocalDate endsOn, Set<DayOfWeek> days, int priority, BigDecimal value,
                              Integer unitId, String notes, List<OfferTarget> targets, Set<Integer> tierIds,
                              LocalDateTime version) throws UserValidationException {
        if (kind == null) {
            throw new UserValidationException("offer.error.kind");
        }
        if (startsOn == null) {
            throw new UserValidationException("offer.error.starts");
        }
        if (days == null || days.isEmpty()) {
            throw new UserValidationException("offer.error.weekdays");
        }
        Offer offer = new Offer(id, name == null ? "" : name.strip(), kind,
                status == null ? OfferStatus.DRAFT : status, startsOn, endsOn, Weekdays.of(days), priority,
                kind == OfferKind.PERCENT ? value : null, kind == OfferKind.AMOUNT ? value : null,
                kind == OfferKind.PRICE ? value : null, kind == OfferKind.PERCENT ? null : unitId,
                notes == null || notes.isBlank() ? null : notes.strip(), targets, tierIds, version);
        requireValid(offer);
        return offer;
    }

    public static void requireValid(Offer offer) throws UserValidationException {
        if (offer.name().isBlank()) {
            throw new UserValidationException("offer.error.name.required");
        }
        if (offer.name().length() > NAME_MAX) {
            throw new UserValidationException("offer.error.name.long");
        }
        switch (offer.kind()) {
            case PERCENT -> {
                if (!positive(offer.percent()) || offer.percent().compareTo(HUNDRED) > 0
                        || offer.percent().stripTrailingZeros().scale() > 3) {
                    throw new UserValidationException("offer.error.percent");
                }
                if (offer.unitId() != null) {
                    throw new UserValidationException("offer.error.unit.percent");
                }
            }
            case AMOUNT -> requireMoney(offer.amount(), "offer.error.amount");
            case PRICE -> requireMoney(offer.offerPrice(), "offer.error.price");
        }
        if (offer.endsOn() != null && offer.endsOn().isBefore(offer.startsOn())) {
            throw new UserValidationException("offer.error.dates");
        }
        if (offer.weekdays() != null && (offer.weekdays() < 1 || offer.weekdays() > Weekdays.EVERY_DAY)) {
            throw new UserValidationException("offer.error.weekdays");
        }
        if (Math.abs(offer.priority()) > PRIORITY_MAX) {
            throw new UserValidationException("offer.error.priority");
        }
        if (offer.targets().stream().noneMatch(target -> !target.excluded())) {
            throw new UserValidationException("offer.error.targets");
        }
        if (offer.notes() != null && offer.notes().length() > NOTES_MAX) {
            throw new UserValidationException("offer.error.notes.long");
        }
        for (int tierId : offer.priceTierIds()) {
            if (!PriceTiers.exists(tierId)) {
                throw new UserValidationException("offer.error.tier");
            }
        }
    }

    private static void requireMoney(BigDecimal value, String key) throws UserValidationException {
        if (!positive(value) || value.compareTo(MONEY_MAX) > 0 || value.stripTrailingZeros().scale() > 2) {
            throw new UserValidationException(key);
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /**
     * Whether two versions of an offer say the same terms - everything but its name, its notes and its end.
     * Once a line names the offer, those are all that may change (ق-ع٧): stop it and write another.
     */
    public static boolean sameTerms(Offer left, Offer right) {
        return left.kind() == right.kind()
                && left.startsOn().equals(right.startsOn())
                && java.util.Objects.equals(left.weekdays(), right.weekdays())
                && left.priority() == right.priority()
                && same(left.percent(), right.percent())
                && same(left.amount(), right.amount())
                && same(left.offerPrice(), right.offerPrice())
                && java.util.Objects.equals(left.unitId(), right.unitId())
                && Set.copyOf(left.targets()).equals(Set.copyOf(right.targets()))
                && left.priceTierIds().equals(right.priceTierIds());
    }

    private static boolean same(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
