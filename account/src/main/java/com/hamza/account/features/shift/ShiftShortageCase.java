package com.hamza.account.features.shift;

import java.math.BigDecimal;

/** Locked facts needed to decide whether a shift shortage may be charged. */
public record ShiftShortageCase(int shiftId, int cashierUserId, boolean open,
                                BigDecimal difference, boolean alreadyCharged) {
}
