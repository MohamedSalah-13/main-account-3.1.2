package com.hamza.account.features.invoice;

import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferEngine;
import com.hamza.account.features.offers.OfferKind;
import com.hamza.account.features.offers.OfferStatus;
import com.hamza.account.features.offers.OfferTarget;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sentences under an invoice's lines: one a hint, the item and its unit named, the same hint said once. */
class InvoiceOfferHintsTest {

    private static final Offer THREE_FOR_100 = new Offer(1, "3 بـ 100", OfferKind.QUANTITY_PRICE,
            OfferStatus.ACTIVE, LocalDate.of(2026, 9, 1), null, null, 0, null, null, new BigDecimal("100"), null,
            new BigDecimal("3"), null, null, null, null, null, List.of(OfferTarget.item(11)), Set.of(), null);

    private static ItemsModel soap() {
        ItemsModel item = new ItemsModel(11, "B11", "صابون");
        item.setUnitsType(new UnitsModel(1, "قطعة", 1));
        return item;
    }

    @Test
    @DisplayName("add one piece of soap to take «3 بـ 100» - said once, however many runs found it")
    void aSentencePerHint() throws Exception {
        OfferEngine.Hint hint = new OfferEngine.Hint(THREE_FOR_100, OfferEngine.HintKind.COMPLETE, 11, null,
                new BigDecimal("1.000"));
        Map<Integer, ItemsModel> items = Map.of(11, soap());
        List<String> sentences = InvoiceOfferHints.sentences(List.of(hint, hint), items::get);

        assertEquals(1, sentences.size());
        String sentence = sentences.get(0);
        assertTrue(sentence.contains("1 قطعة"), sentence);
        assertTrue(sentence.contains("صابون"), sentence);
        assertTrue(sentence.contains("3 بـ 100"), sentence);
    }

    @Test
    @DisplayName("each kind has its sentence, and an item that cannot be found is passed over")
    void eachKind() throws Exception {
        Map<Integer, ItemsModel> items = Map.of(11, soap());
        for (OfferEngine.HintKind kind : OfferEngine.HintKind.values()) {
            List<String> sentences = InvoiceOfferHints.sentences(List.of(
                    new OfferEngine.Hint(THREE_FOR_100, kind, 11, null, new BigDecimal("2"))), items::get);
            assertEquals(1, sentences.size(), kind.name());
            assertTrue(!sentences.get(0).startsWith("invoice.offer.hint"), "the key has a sentence: " + kind);
        }
        assertTrue(InvoiceOfferHints.sentences(List.of(new OfferEngine.Hint(THREE_FOR_100,
                OfferEngine.HintKind.GIFT, 99, null, BigDecimal.ONE)), items::get).isEmpty());
    }

    @Test
    @DisplayName("what is left to spend names no item: the amount, written as money, and the offer")
    void spend() throws Exception {
        List<String> sentences = InvoiceOfferHints.sentences(List.of(new OfferEngine.Hint(THREE_FOR_100,
                OfferEngine.HintKind.SPEND, 0, null, new BigDecimal("1250.5"))), itemId -> {
            throw new AssertionError("no item is looked up for a sum");
        });
        assertEquals(1, sentences.size());
        assertTrue(sentences.get(0).contains("1,250.50"), sentences.get(0));
        assertTrue(sentences.get(0).contains("3 بـ 100"), sentences.get(0));
    }
}
