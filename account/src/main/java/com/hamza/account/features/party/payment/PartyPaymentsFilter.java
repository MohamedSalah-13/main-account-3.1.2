package com.hamza.account.features.party.payment;

import com.hamza.account.features.events.PartyKind;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Which payments the report lists: one side, a period, and optionally a text and a treasury.
 *
 * <p>The text is a party's name by part, or - when it is a number - a party's code or the invoice a
 * payment was allocated to, exactly. A number typed on an Arabic keyboard arrives as ٠-٩ and is read as
 * the digits it is: that omission once made a quantity typed the ordinary way a silent zero.</p>
 *
 * @param treasuryId a treasury, or 0 for every one
 */
public record PartyPaymentsFilter(PartyKind kind, LocalDate from, LocalDate to, String text, int treasuryId) {

    public PartyPaymentsFilter {
        Objects.requireNonNull(kind, "kind");
        text = latinDigits(text == null ? "" : text.strip());
    }

    public static PartyPaymentsFilter of(PartyKind kind, LocalDate from, LocalDate to) {
        return new PartyPaymentsFilter(kind, from, to, "", 0);
    }

    public boolean hasText() {
        return !text.isEmpty();
    }

    /** Whether the text can name a code or an invoice as well as a name. */
    public boolean textIsNumber() {
        return hasText() && text.length() <= 9 && text.chars().allMatch(Character::isDigit);
    }

    public boolean hasTreasury() {
        return treasuryId > 0;
    }

    private static String latinDigits(String text) {
        StringBuilder latin = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            if (character >= '٠' && character <= '٩') {
                latin.append((char) ('0' + character - '٠'));
            } else if (character >= '۰' && character <= '۹') {
                latin.append((char) ('0' + character - '۰'));
            } else {
                latin.append(character);
            }
        }
        return latin.toString();
    }
}
