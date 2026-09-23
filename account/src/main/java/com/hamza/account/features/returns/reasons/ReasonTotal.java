package com.hamza.account.features.returns.reasons;

import com.hamza.account.features.returns.ReturnReason;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * One reason's returns over a period: how many, and what they came to net of their own discounts.
 *
 * @param storedValue what {@code return_reason} holds - null for a return nobody gave a reason for, and
 *                    kept as written when it names no {@link ReturnReason}, so a value this build does not
 *                    know is a row of its own rather than a failed report
 */
public record ReasonTotal(String storedValue, int count, BigDecimal value) {

    public ReasonTotal {
        value = value == null ? BigDecimal.ZERO : value;
    }

    /** The reason, when the stored value is one this build knows. */
    public Optional<ReturnReason> reason() {
        if (storedValue == null || storedValue.isBlank()) {
            return Optional.empty();
        }
        for (ReturnReason reason : ReturnReason.values()) {
            if (reason.storedValue().equals(storedValue)) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }

    /** Whether nobody gave a reason: the old returns, and a free one saved without picking one. */
    public boolean isWithoutReason() {
        return storedValue == null || storedValue.isBlank();
    }

    /** The average return under this reason; absent when there are none. */
    public Optional<BigDecimal> average() {
        return count == 0 ? Optional.empty()
                : Optional.of(value.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP));
    }
}
