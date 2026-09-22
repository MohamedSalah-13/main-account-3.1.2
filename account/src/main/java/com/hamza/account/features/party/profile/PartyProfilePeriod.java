package com.hamza.account.features.party.profile;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One year, month or week of a profile: how many documents, and their net less the returns. */
public record PartyProfilePeriod(LocalDate start, String label, int documents, BigDecimal net) {
}
