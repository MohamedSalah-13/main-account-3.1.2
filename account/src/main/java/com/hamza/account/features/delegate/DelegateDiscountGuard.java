package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The question the invoice save asks before it writes: does this document discount more than
 * its delegate may, with nobody entitled to allow it?
 * <p>
 * It is asked in the service and not on the screen - a ceiling the screen enforces is a ceiling
 * for whoever uses that screen. And it is asked of a <b>sale</b> alone: a return gives nothing
 * away, and a purchase has no delegate.
 * <p>
 * {@code sales.discount.override} is read with {@code isGranted} rather than {@code require}, and
 * that is not the hint-versus-enforcement mistake: the permission does not guard the save - the
 * document's own create/update permission has already been required - it decides which of two
 * answers a rule gives, and a refusal here has a sentence of its own to show.
 */
public final class DelegateDiscountGuard {

    /** The refusal, as the key of its sentence. */
    public static final String REFUSAL_KEY = "delegate.ceiling.error.exceeded";

    private final DiscountCeilingRepository ceilings;
    private final BooleanSupplier mayOverride;

    public DelegateDiscountGuard(DiscountCeilingRepository ceilings, BooleanSupplier mayOverride) {
        this.ceilings = Objects.requireNonNull(ceilings, "ceilings");
        this.mayOverride = Objects.requireNonNull(mayOverride, "mayOverride");
    }

    public static DelegateDiscountGuard jdbc() {
        return new DelegateDiscountGuard(new JdbcDiscountCeilingRepository(),
                () -> AuthorizationGuard.isGranted(AppPermissions.SALES_DISCOUNT_OVERRIDE));
    }

    /** For a save path built without a database: no delegate has a ceiling. */
    public static DelegateDiscountGuard none() {
        return new DelegateDiscountGuard(DiscountCeilingRepository.none(), () -> true);
    }

    /**
     * True when the document must be refused. The ceiling is read before the permission, so a
     * shop that has set no ceiling - every shop, on the day it upgrades - costs one indexed read
     * and asks nothing of the session.
     */
    public boolean refuses(DocumentType documentType, int delegateId, BigDecimal gross,
                           BigDecimal lineDiscount, BigDecimal headerDiscount) throws DaoException {
        if (documentType != DocumentType.SALES || delegateId <= 0) {
            return false;
        }
        var ceiling = ceilings.ceilingOf(delegateId);
        if (ceiling.isEmpty() || !ceiling.get().exceededBy(gross, lineDiscount, headerDiscount)) {
            return false;
        }
        return !mayOverride.getAsBoolean();
    }
}
