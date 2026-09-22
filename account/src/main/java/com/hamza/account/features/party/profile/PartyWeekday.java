package com.hamza.account.features.party.profile;

import java.math.BigDecimal;
import java.time.DayOfWeek;

/** One day of the week across a whole profile: which day a party comes, and what it is worth. */
public record PartyWeekday(DayOfWeek day, int documents, BigDecimal net) {
}
