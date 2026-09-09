package com.hamza.account.features.productprofile;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

/** Independent of RBAC: even the system administrator cannot use a capability absent from the edition. */
@FunctionalInterface
public interface ProductFeatureAccess {

    boolean isEnabled(FeatureKey feature);

    default void require(FeatureKey feature) throws DaoException {
        if (!isEnabled(feature)) {
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("product.profile.feature.unavailable", feature.value()));
        }
    }

    static ProductFeatureAccess allEnabled() {
        return ignored -> true;
    }
}
