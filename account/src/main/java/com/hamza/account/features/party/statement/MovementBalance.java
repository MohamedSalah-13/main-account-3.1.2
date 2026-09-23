package com.hamza.account.features.party.statement;

import java.math.BigDecimal;

/**
 * A party's running balance on one movement's row of its statement, read both ways from one row
 * (V82, docs/currency-plan.md §14): in the base, and in the party's own currency - the same figure for a
 * party in the base.
 */
public record MovementBalance(BigDecimal base, BigDecimal own) {
}
