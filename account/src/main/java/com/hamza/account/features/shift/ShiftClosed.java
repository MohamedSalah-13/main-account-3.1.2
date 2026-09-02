package com.hamza.account.features.shift;

import com.hamza.controlsfx.observer.AppEvent;

import java.math.BigDecimal;

public record ShiftClosed(int shiftId, int userId, int treasuryId, BigDecimal difference, boolean forced)
        implements AppEvent {
}
