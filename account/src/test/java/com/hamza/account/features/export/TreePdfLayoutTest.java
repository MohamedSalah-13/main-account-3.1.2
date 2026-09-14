package com.hamza.account.features.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A short row would shift every following cell a column over in iText, so it is refused up front. */
class TreePdfLayoutTest {

    private static final String[] HEADERS = {"1", "2", "3"};
    private static final float[] WIDTHS = {1, 1, 1};

    @Test
    void aRowWithTheWrongNumberOfCellsIsRefused() {
        var branch = new TreePdfLayout.Branch("t", List.<String[]>of(new String[]{"a", "b"}), null);
        assertThrows(IllegalArgumentException.class,
                () -> new TreePdfLayout(HEADERS, WIDTHS, List.of(branch), null));
    }

    @Test
    void aSummaryOrTotalsLineWithTheWrongNumberOfCellsIsRefused() {
        var shortSummary = new TreePdfLayout.Branch("t", List.<String[]>of(new String[]{"a", "b", "c"}),
                new String[]{"x"});
        assertThrows(IllegalArgumentException.class,
                () -> new TreePdfLayout(HEADERS, WIDTHS, List.of(shortSummary), null));
        assertThrows(IllegalArgumentException.class,
                () -> new TreePdfLayout(HEADERS, WIDTHS, List.of(), new String[]{"x", "y"}));
    }

    @Test
    void oneWidthPerHeadingIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new TreePdfLayout(HEADERS, new float[]{1, 1}, List.of(), null));
        assertDoesNotThrow(() -> new TreePdfLayout(HEADERS, WIDTHS, List.of(), null));
    }
}
