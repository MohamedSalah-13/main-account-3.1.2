package com.hamza.account.features.shift;

import com.hamza.controlsfx.observer.AppEvent;

public record ShiftOpened(int shiftId, int userId, int treasuryId) implements AppEvent {
}
