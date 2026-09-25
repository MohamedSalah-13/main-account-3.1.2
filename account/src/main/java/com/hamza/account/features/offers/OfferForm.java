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
 * of three places up to a hundred, an amount and a price in {@code DECIMAL(14, 2)}, a quantity and a limit
 * in {@code DECIMAL(14, 3)}.
 */
public final class OfferForm {

    public static final int NAME_MAX = 100;
    public static final int NOTES_MAX = 255;
    public static final int PRIORITY_MAX = 999;
    /** {@code offer.barcode} is {@code VARCHAR(50)}; digits alone, as the invoice's barcode box takes nothing else. */
    public static final int BARCODE_MAX = 50;
    static final BigDecimal MONEY_MAX = new BigDecimal("999999999999.99");
    static final BigDecimal QUANTITY_MAX = new BigDecimal("99999999999.999");
    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private OfferForm() {
    }

    /**
     * What the form's value boxes say, whichever the kind uses: the value (a percentage, an amount, a price -
     * the group's for a quantity offer, the bundle's for a bundle), the quantity bought, the quantity given and
     * its discount, the two limits, an invoice offer's threshold and whether its value is an amount rather than
     * a percentage, and a bundle's barcode - any of them blank.
     */
    public record Terms(BigDecimal value, BigDecimal buyQuantity, BigDecimal getQuantity, BigDecimal getPercent,
                        BigDecimal maxPerInvoice, BigDecimal quantityLimit, BigDecimal threshold,
                        boolean amountOff, String barcode) {

        /** The terms of phase B's and phase C's kinds. */
        public Terms(BigDecimal value, BigDecimal buyQuantity, BigDecimal getQuantity, BigDecimal getPercent,
                     BigDecimal maxPerInvoice, BigDecimal quantityLimit) {
            this(value, buyQuantity, getQuantity, getPercent, maxPerInvoice, quantityLimit, null, false, null);
        }

        public static Terms of(BigDecimal value) {
            return new Terms(value, null, null, null, null, null);
        }
    }

    /** {@link #build(int, String, OfferKind, OfferStatus, LocalDate, LocalDate, Set, int, Terms, Integer, String, List, Set, LocalDateTime)} with a value alone. */
    public static Offer build(int id, String name, OfferKind kind, OfferStatus status, LocalDate startsOn,
                              LocalDate endsOn, Set<DayOfWeek> days, int priority, BigDecimal value,
                              Integer unitId, String notes, List<OfferTarget> targets, Set<Integer> tierIds,
                              LocalDateTime version) throws UserValidationException {
        return build(id, name, kind, status, startsOn, endsOn, days, priority, Terms.of(value), unitId, notes,
                targets, tierIds, version);
    }

    /**
     * The offer the form says: each value goes to the one column its kind uses, a unit with anything but a
     * percentage, the days as {@code offer.weekdays} stores them - where no day ticked is a refusal and not
     * "every day", which is what a null mask means.
     */
    public static Offer build(int id, String name, OfferKind kind, OfferStatus status, LocalDate startsOn,
                              LocalDate endsOn, Set<DayOfWeek> days, int priority, Terms terms, Integer unitId,
                              String notes, List<OfferTarget> targets, Set<Integer> tierIds, LocalDateTime version)
            throws UserValidationException {
        if (kind == null) {
            throw new UserValidationException("offer.error.kind");
        }
        if (startsOn == null) {
            throw new UserValidationException("offer.error.starts");
        }
        if (days == null || days.isEmpty()) {
            throw new UserValidationException("offer.error.weekdays");
        }
        Terms given = terms == null ? Terms.of(null) : terms;
        BigDecimal value = given.value();
        boolean invoice = kind == OfferKind.INVOICE;
        boolean countedInAUnit = kind == OfferKind.AMOUNT || kind == OfferKind.PRICE || kind.countsAQuantity();
        Offer offer = new Offer(id, name == null ? "" : name.strip(), kind,
                status == null ? OfferStatus.DRAFT : status, startsOn, endsOn, Weekdays.of(days), priority,
                kind == OfferKind.PERCENT || (invoice && !given.amountOff()) ? value : null,
                kind == OfferKind.AMOUNT || (invoice && given.amountOff()) ? value : null,
                kind == OfferKind.PRICE || kind == OfferKind.QUANTITY_PRICE || kind == OfferKind.BUNDLE ? value : null,
                countedInAUnit ? unitId : null,
                kind.countsAQuantity() ? given.buyQuantity() : null,
                kind == OfferKind.BUY_GET ? given.getQuantity() : null,
                kind == OfferKind.BUY_GET ? given.getPercent() : null,
                invoice ? null : given.maxPerInvoice(), given.quantityLimit(),
                invoice ? given.threshold() : null,
                kind == OfferKind.BUNDLE ? given.barcode() : null,
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
                requirePercent(offer.percent(), "offer.error.percent");
                if (offer.unitId() != null) {
                    throw new UserValidationException("offer.error.unit.percent");
                }
            }
            case AMOUNT -> requireMoney(offer.amount(), "offer.error.amount");
            case PRICE -> requireMoney(offer.offerPrice(), "offer.error.price");
            case QUANTITY_PRICE -> {
                requireQuantity(offer.buyQuantity(), "offer.error.buy.quantity");
                requireMoney(offer.offerPrice(), "offer.error.price");
            }
            case BUY_GET -> {
                requireQuantity(offer.buyQuantity(), "offer.error.buy.quantity");
                requireQuantity(offer.getQuantity(), "offer.error.get.quantity");
                requirePercent(offer.getPercent(), "offer.error.get.percent");
            }
            case BUNDLE -> {
                requireMoney(offer.offerPrice(), "offer.error.bundle.price");
                requireBundle(offer);
            }
            case INVOICE -> {
                requireMoney(offer.threshold(), "offer.error.threshold");
                if (offer.amount() != null) {
                    requireMoney(offer.amount(), "offer.error.amount");
                    if (offer.amount().compareTo(offer.threshold()) >= 0) {
                        throw new UserValidationException("offer.error.invoice.amount");
                    }
                } else {
                    requirePercent(offer.percent(), "offer.error.percent");
                }
                if (offer.maxPerInvoice() != null) {
                    throw new UserValidationException("offer.error.invoice.limit");
                }
            }
        }
        requireOnlyWhatTheKindUses(offer);
        if (offer.maxPerInvoice() != null) {
            requireQuantity(offer.maxPerInvoice(), "offer.error.limit");
        }
        if (offer.quantityLimit() != null) {
            requireQuantity(offer.quantityLimit(), "offer.error.limit");
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
        boolean components = offer.targets().stream().anyMatch(OfferTarget::component);
        if (components != (offer.kind() == OfferKind.BUNDLE)) {
            throw new UserValidationException("offer.error.bundle.targets");
        }
        if (offer.kind() != OfferKind.BUNDLE && offer.targets().stream()
                .noneMatch(target -> !target.excluded() && target.role() == OfferRole.QUALIFY)) {
            throw new UserValidationException("offer.error.targets");
        }
        long gifts = offer.targets().stream().filter(OfferTarget::reward).count();
        if (gifts > 1 || (gifts == 1 && offer.kind() != OfferKind.BUY_GET)) {
            throw new UserValidationException("offer.error.reward");
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

    /** What the kind does not use is not there - which is what {@code offer_kind_chk} holds the row to. */
    private static void requireOnlyWhatTheKindUses(Offer offer) throws UserValidationException {
        OfferKind kind = offer.kind();
        boolean invoice = kind == OfferKind.INVOICE;
        boolean stray = (kind != OfferKind.PERCENT && !invoice && offer.percent() != null)
                || (kind != OfferKind.AMOUNT && !invoice && offer.amount() != null)
                || (invoice && offer.percent() != null && offer.amount() != null)
                || (kind != OfferKind.PRICE && kind != OfferKind.QUANTITY_PRICE && kind != OfferKind.BUNDLE
                    && offer.offerPrice() != null)
                || (!kind.countsAQuantity() && offer.buyQuantity() != null)
                || (kind != OfferKind.BUY_GET && (offer.getQuantity() != null || offer.getPercent() != null))
                || (!invoice && offer.threshold() != null)
                || ((kind == OfferKind.BUNDLE || invoice) && offer.unitId() != null)
                || (kind != OfferKind.BUNDLE && offer.barcode() != null);
        if (stray) {
            throw new UserValidationException("offer.error.kind");
        }
    }

    /**
     * A bundle is two items or more, each once, each in a quantity - and nothing that earns or is given: its
     * components are the whole of what it reaches (ق-ع١٢). Its barcode, when it has one, is digits alone.
     */
    private static void requireBundle(Offer offer) throws UserValidationException {
        List<OfferTarget> components = offer.components();
        if (components.size() != offer.targets().size()) {
            throw new UserValidationException("offer.error.bundle.targets");
        }
        if (components.size() < 2) {
            throw new UserValidationException("offer.error.bundle.components");
        }
        if (components.stream().map(OfferTarget::itemId).distinct().count() != components.size()) {
            throw new UserValidationException("offer.error.bundle.duplicate");
        }
        for (OfferTarget component : components) {
            requireQuantity(component.quantity(), "offer.error.bundle.quantity");
        }
        String barcode = offer.barcode();
        if (barcode != null && (barcode.length() > BARCODE_MAX
                || !barcode.chars().allMatch(c -> c >= '0' && c <= '9'))) {
            throw new UserValidationException("offer.error.barcode");
        }
    }

    private static void requirePercent(BigDecimal value, String key) throws UserValidationException {
        if (!positive(value) || value.compareTo(HUNDRED) > 0 || value.stripTrailingZeros().scale() > 3) {
            throw new UserValidationException(key);
        }
    }

    private static void requireMoney(BigDecimal value, String key) throws UserValidationException {
        if (!positive(value) || value.compareTo(MONEY_MAX) > 0 || value.stripTrailingZeros().scale() > 2) {
            throw new UserValidationException(key);
        }
    }

    private static void requireQuantity(BigDecimal value, String key) throws UserValidationException {
        if (!positive(value) || value.compareTo(QUANTITY_MAX) > 0 || value.stripTrailingZeros().scale() > 3) {
            throw new UserValidationException(key);
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /**
     * Whether two versions of an offer say the same terms - everything but its name, its notes, its end and a
     * bundle's barcode. Once a line names the offer, those are all that may change (ق-ع٧): stop it and write
     * another. The limits are terms too, and an invoice offer's threshold, and a bundle's components with their
     * quantities: an edit of a saved invoice is judged by them. The barcode is how a bundle is scanned, not what
     * it gives.
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
                && same(left.buyQuantity(), right.buyQuantity())
                && same(left.getQuantity(), right.getQuantity())
                && same(left.getPercent(), right.getPercent())
                && same(left.maxPerInvoice(), right.maxPerInvoice())
                && same(left.quantityLimit(), right.quantityLimit())
                && same(left.threshold(), right.threshold())
                && Set.copyOf(left.targets()).equals(Set.copyOf(right.targets()))
                && left.priceTierIds().equals(right.priceTierIds());
    }

    private static boolean same(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
