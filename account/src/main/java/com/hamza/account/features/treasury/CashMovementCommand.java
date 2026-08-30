package com.hamza.account.features.treasury;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A deposit into, or a withdrawal from, one treasury.
 * <p>
 * {@code category} says whose money moved - the business's or the owner's - and
 * must agree with the direction; see {@link CashCategory}.
 */
public record CashMovementCommand(int treasuryId,
                                  CashDirection direction,
                                  CashCategory category,
                                  BigDecimal amount,
                                  LocalDate date,
                                  String statement,
                                  String description,
                                  int userId) {
}
