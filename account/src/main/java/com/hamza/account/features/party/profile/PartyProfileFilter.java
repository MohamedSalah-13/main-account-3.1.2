package com.hamza.account.features.party.profile;

import com.hamza.account.features.events.PartyKind;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Whose profile, over which days.
 *
 * <p>The checks are in the constructor, as on every filter in {@code features/party}: a filter that
 * exists is one the queries can answer. A person can still pick a start after an end, so
 * {@link #problem} is how a screen asks first and says so in words.</p>
 *
 * @param kind    customers or suppliers - chooses the documents, the permission and the labels
 * @param partyId the one party the profile is about
 * @param from    first day, inclusive
 * @param to      last day, inclusive
 */
public record PartyProfileFilter(PartyKind kind, int partyId, LocalDate from, LocalDate to) {

    /** Why a range cannot be profiled, in an order a screen can translate. */
    public enum Problem {
        NONE,
        MISSING,
        REVERSED
    }

    public PartyProfileFilter {
        if (kind == null) {
            throw new IllegalArgumentException("a profile belongs to a customer or to a supplier");
        }
        if (partyId <= 0) {
            throw new IllegalArgumentException("partyId must identify a party: " + partyId);
        }
        Problem problem = problem(from, to);
        if (problem != Problem.NONE) {
            throw new IllegalArgumentException("cannot profile " + from + " to " + to + ": " + problem);
        }
    }

    public static Problem problem(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return Problem.MISSING;
        }
        return from.isAfter(to) ? Problem.REVERSED : Problem.NONE;
    }

    /** Where a profile opens: the current month and the eleven before it, so a year of seasons shows. */
    public static PartyProfileFilter lastTwelveMonths(PartyKind kind, int partyId, LocalDate today) {
        return new PartyProfileFilter(kind, partyId, today.withDayOfMonth(1).minusMonths(11), today);
    }

    /** The same length of time straight before this one - what "stopped buying" is measured against. */
    public LocalDate previousTo() {
        return from.minusDays(1);
    }

    public LocalDate previousFrom() {
        return previousTo().minusDays(ChronoUnit.DAYS.between(from, to));
    }
}
