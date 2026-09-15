package com.hamza.account.features.totals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotalsDeletionPreviewTest {

    private static TotalsDeletionPreview.Document document(int id, String date, String total) {
        return new TotalsDeletionPreview.Document(id, date, "party " + id, 2, new BigDecimal(total));
    }

    @Test
    void addsTheTotalsAndFindsTheRangeWhateverOrderTheRowsAreIn() {
        TotalsDeletionPreview preview = TotalsDeletionPreview.of(List.of(
                document(7, "2026-09-03", "100.50"),
                document(5, "2026-08-28", "20.25"),
                document(9, "2026-09-10", "0.25")), true, 1);

        assertEquals(3, preview.count());
        assertEquals(new BigDecimal("121.00"), preview.total());
        assertEquals(LocalDate.of(2026, 8, 28), preview.earliest());
        assertEquals(LocalDate.of(2026, 9, 10), preview.latest());
        assertFalse(preview.singleDay());
    }

    @Test
    void keepsTheDocumentsInTheOrderTheScreenShowsThem() {
        TotalsDeletionPreview preview = TotalsDeletionPreview.of(List.of(
                document(7, "2026-09-03", "1"), document(5, "2026-08-28", "1")), true, 1);

        assertEquals(List.of(7, 5), preview.documents().stream().map(TotalsDeletionPreview.Document::id).toList());
    }

    @Test
    void tickedRowsOnAPagedResultSayHowManyPagesTheyDoNotReach() {
        List<TotalsDeletionPreview.Document> rows = List.of(document(1, "2026-09-01", "1"));

        assertEquals(23, TotalsDeletionPreview.of(rows, true, 24).unseenPages());
        assertEquals(0, TotalsDeletionPreview.of(rows, true, 1).unseenPages());
        // One row deleted from its own button is that row, whatever page it is on.
        assertEquals(0, TotalsDeletionPreview.of(rows, false, 24).unseenPages());
    }

    @Test
    void aDateThatCannotBeReadIsLeftOutOfTheRangeNotGuessed() {
        TotalsDeletionPreview preview = TotalsDeletionPreview.of(List.of(
                document(1, "not a date", "5"), document(2, null, "5"), document(3, "2026-09-01", "5")), false, 1);

        assertEquals(LocalDate.of(2026, 9, 1), preview.earliest());
        assertTrue(preview.singleDay());
        assertEquals(new BigDecimal("15"), preview.total());
    }

    @Test
    void noReadableDateMeansNoRange() {
        TotalsDeletionPreview preview = TotalsDeletionPreview.of(List.of(document(1, "", "5")), false, 1);

        assertNull(preview.earliest());
        assertNull(preview.latest());
        assertFalse(preview.singleDay());
    }

    @Test
    void aMissingTotalCountsAsZero() {
        var row = new TotalsDeletionPreview.Document(1, "2026-09-01", "x", 0, null);

        assertEquals(BigDecimal.ZERO, TotalsDeletionPreview.of(List.of(row), false, 1).total());
    }
}
