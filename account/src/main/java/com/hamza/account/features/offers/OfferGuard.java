package com.hamza.account.features.offers;

import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The save's judgement of the offers on a sale (docs/pricing-and-offers-plan.md ق-ع٤): the engine is run
 * again inside the transaction, over offers read afresh, and a document whose lines say anything else is
 * refused - before its number is allocated, since the counter does not roll back. The screen shows what the
 * save will accept; the save decides, the way {@code DelegateDiscountGuard} and {@code ReturnGuard} do.
 * <p>
 * Three refusals, each naming the offer so the person at the till knows what to look at: an offer the line
 * claims that no longer reaches it (stopped, ended, or no longer for this tier), an offer that reaches a
 * line which does not carry it (started, or reached this customer's tier, since the screen last looked), and
 * an offer whose discount has moved. And a line carrying an offer carries no manual discount beside it -
 * the offer takes its place (ق-ع٨) - so its discount is the offer's, exactly.
 */
public final class OfferGuard {

    /** Half a piastre: two figures rounded to money that agree are within it. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.005");
    /** Half a thousandth: two quantities of three places that agree are within it. */
    private static final BigDecimal QUANTITY_TOLERANCE = new BigDecimal("0.0005");

    private OfferGuard() {
    }

    /**
     * What one line of the document claims: the offer it names, that offer's part of it, its whole discount,
     * and how many of its units the offer covered - which the global limit counts, so it is judged too. A
     * quantity of null is not asserted.
     */
    public record Claim(int index, Integer offerId, BigDecimal offerDiscount, BigDecimal discount,
                        BigDecimal offerQuantity) {

        public Claim {
            offerDiscount = offerDiscount == null ? BigDecimal.ZERO : offerDiscount;
            discount = discount == null ? BigDecimal.ZERO : discount;
        }

        public Claim(int index, Integer offerId, BigDecimal offerDiscount, BigDecimal discount) {
            this(index, offerId, offerDiscount, discount, null);
        }
    }

    /**
     * Refuses the document unless every claim is what {@code expected} gives its line. {@code names} answers
     * an offer's name by id for an offer the engine did not return - one a line claims that no longer reaches
     * anything.
     */
    public static void require(List<Claim> claims, OfferEngine.Result expected, OfferNames names)
            throws BusinessRuleException {
        for (Claim claim : claims) {
            Optional<OfferEngine.Applied> due = expected.forLine(claim.index());
            if (claim.offerId() == null) {
                if (due.isPresent()) {
                    throw refusal("offer.guard.error.appeared", due.get().offer().name());
                }
                continue;
            }
            if (due.isEmpty() || due.get().offer().id() != claim.offerId()) {
                throw refusal("offer.guard.error.gone", names.nameOf(claim.offerId()));
            }
            if (!same(due.get().discount(), claim.offerDiscount())
                    || (due.get().quantity() != null && claim.offerQuantity() != null
                        && due.get().quantity().subtract(claim.offerQuantity()).abs().compareTo(QUANTITY_TOLERANCE) >= 0)) {
                throw refusal("offer.guard.error.changed", due.get().offer().name());
            }
            if (!same(claim.discount(), claim.offerDiscount())) {
                throw refusal("offer.guard.error.manual", due.get().offer().name());
            }
        }
    }

    /** A document the engine does not run on - a return, or one in a foreign currency - claims no offer. */
    public static void requireNone(List<Claim> claims, OfferNames names) throws BusinessRuleException {
        for (Claim claim : claims) {
            if (claim.offerId() != null) {
                throw refusal("offer.guard.error.not.here", names.nameOf(claim.offerId()));
            }
        }
    }

    private static boolean same(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(TOLERANCE) < 0;
    }

    private static BusinessRuleException refusal(String key, String offerName) {
        return new BusinessRuleException(LanguageManager.getInstance().getString(key,
                Objects.requireNonNullElse(offerName, "")));
    }

    /** An offer's name by id. */
    @FunctionalInterface
    public interface OfferNames {
        String nameOf(int offerId);
    }
}
