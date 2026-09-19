package com.hamza.account.features.returns;

import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;

import java.math.BigDecimal;

/**
 * What text typed into the "which invoice?" box means.
 * <p>
 * The picker used to be a {@code TextInputDialog} taking an invoice number and nothing else, so
 * returning goods for a customer who has lost their copy meant knowing the number or giving up -
 * and the one thing the person at the counter always has is the customer's name. This is the rule
 * behind the search that replaced it, apart from the dialog so it can be tested: a run of digits
 * is a document number, anything else is a party's name, and both may be given at once.
 * <p>
 * Digits are read through {@link NumberTextConverter}, so the ٠-٩ an Arabic keyboard produces are
 * a number here exactly as they are in the quantity column - the same omission that made
 * {@code ReturnQuantityInput} necessary.
 */
public final class ReturnSourceSearch {

    private ReturnSourceSearch() {
    }

    /**
     * @param text what was typed, or {@code null}
     * @return the document number it names, or {@code 0} when it names none
     */
    public static int documentNumber(String text) {
        String digits = onlyDigits(text);
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            BigDecimal value = NumberTextConverter.parse(digits);
            if (value == null || value.signum() <= 0 || value.scale() > 0) {
                return 0;
            }
            long number = value.longValueExact();
            return number > Integer.MAX_VALUE ? 0 : (int) number;
        } catch (NumberFormatException | ArithmeticException notANumber) {
            return 0;
        }
    }

    /**
     * The part of {@code text} to match a party's name against - everything that is not a digit,
     * trimmed. Empty when nothing is left, which is what "the number alone" looks like, and empty
     * when what is left carries no letter: typing {@code -4} leaves a hyphen behind, and a name
     * search for {@code %-%} narrows the list by a character nobody meant to type.
     */
    public static String partyText(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder letters = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (!isDigit(c)) {
                letters.append(c);
            }
        }
        String name = letters.toString().trim();
        return hasALetter(name) ? name : "";
    }

    private static boolean hasALetter(String text) {
        return text.chars().anyMatch(Character::isLetter);
    }

    /** Whether there is anything to search for at all. */
    public static boolean isEmpty(String text) {
        return documentNumber(text) <= 0 && partyText(text).isEmpty();
    }

    private static String onlyDigits(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (isDigit(c)) {
                digits.append(c);
            }
        }
        return digits.toString();
    }

    private static boolean isDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= '٠' && c <= '٩')
                || (c >= '۰' && c <= '۹');
    }
}
