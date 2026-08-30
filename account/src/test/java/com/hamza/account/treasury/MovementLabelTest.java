package com.hamza.account.treasury;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds {@link MovementLabel} against the view that produces the values.
 * <p>
 * {@code treasury_balance} writes an Arabic literal into {@code information} on
 * every branch, and the statement screen compares that column with
 * {@code equals()}. So the two sides are one contract written in two files, and it
 * has already drifted once: the view emitted a deposit and a withdrawal that the
 * screen's list of constants did not know about.
 * <p>
 * Reading the literals out of {@code R__views.sql} rather than repeating them here
 * is the whole point - a test that restates the strings would drift with the code
 * it is meant to hold still. No database: the migration is on the classpath, and
 * that is where the view is defined.
 */
class MovementLabelTest {

    /** The literal in the {@code information} position of each UNION branch. */
    private static final Pattern LITERAL = Pattern.compile("'([^']+)'");

    private static final Set<String> VIEW_LABELS = readViewLabels();

    private static Set<String> readViewLabels() {
        String views = read("db/migration/R__views.sql");
        int start = views.indexOf("CREATE VIEW treasury_balance AS");
        int end = views.indexOf("treasury_transfers_and_names", start);
        assertTrue(start >= 0 && end > start, "treasury_balance not found in R__views.sql");

        Set<String> labels = new LinkedHashSet<>();
        Matcher matcher = LITERAL.matcher(views.substring(start, end));
        while (matcher.find()) {
            labels.add(matcher.group(1));
        }
        return labels;
    }

    private static String read(String resource) {
        try (InputStream in = MovementLabelTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("the view really was read - the rest of this class would pass vacuously otherwise")
    void theViewWasRead() {
        assertFalse(VIEW_LABELS.isEmpty(), "no literals parsed out of treasury_balance");
        assertTrue(VIEW_LABELS.contains("المبيعات"), "expected the sales branch among the parsed literals");
    }

    @Test
    @DisplayName("every value the view writes has a constant")
    void everyViewLabelIsDeclared() {
        for (String label : VIEW_LABELS) {
            assertTrue(MovementLabel.isKnown(label),
                    "treasury_balance writes '" + label + "' and MovementLabel does not declare it - "
                            + "the statement screen cannot filter or total it");
        }
    }

    @Test
    @DisplayName("no constant describes a movement the view never produces")
    void noLabelIsInvented() {
        for (MovementLabel label : MovementLabel.values()) {
            assertTrue(VIEW_LABELS.contains(label.text()),
                    "MovementLabel." + label.name() + " is '" + label.text()
                            + "', which treasury_balance never writes - it would filter to nothing");
        }
    }

    @Test
    @DisplayName("the opening balance and both halves of a transfer are among them")
    void theNewBranchesAreCovered() {
        assertTrue(VIEW_LABELS.contains(MovementLabel.OPENING.text()));
        assertTrue(VIEW_LABELS.contains(MovementLabel.TRANSFER_IN.text()));
        assertTrue(VIEW_LABELS.contains(MovementLabel.TRANSFER_OUT.text()));
    }

    @Test
    @DisplayName("the balance view excludes exactly the opening label, by the same literal")
    void theBalanceViewFiltersTheOpeningLabel() {
        String views = read("db/migration/R__views.sql");
        int start = views.indexOf("CREATE VIEW treasury_current_balance AS");
        assertTrue(start >= 0, "treasury_current_balance not found in R__views.sql");

        assertTrue(views.substring(start).contains("information <> '" + MovementLabel.OPENING.text() + "'"),
                "treasury_current_balance must exclude the opening line by MovementLabel.OPENING's own text, "
                        + "or the opening balance is counted twice");
    }

    @Test
    @DisplayName("the labels are distinct - two branches sharing one would merge in every filter")
    void labelsAreDistinct() {
        assertEquals(MovementLabel.values().length,
                Set.copyOf(List.of(MovementLabel.allTexts().toArray(new String[0]))).size());
    }
}
