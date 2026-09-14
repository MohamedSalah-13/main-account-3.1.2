package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvoicePrintDocumentBuilderTest {

    private static final InvoicePrintDocument.Letterhead COMPANY = new InvoicePrintDocument.Letterhead(
            "شركة", "", "", "", "", null);

    /** A header of 1000 less 100, with 300 paid: 600 went onto the account. */
    private static Total_Sales header(InvoiceType type) {
        Total_Sales totals = new Total_Sales();
        totals.setId(77);
        totals.setDate("2026-09-14");
        totals.setTotal(1000);
        totals.setDiscount(100);
        totals.setPaid(300);
        totals.setInvoiceType(type);
        totals.setNotes("ملاحظة");
        totals.setStockData(new Stock(1));
        return totals;
    }

    @Test
    void aDeferredDocumentPrintsTheBalanceOnItsOwnRowAndTheBalanceBeforeIt() throws Exception {
        List<String> asked = new ArrayList<>();
        InvoicePrintDocumentBuilder builder = new InvoicePrintDocumentBuilder(() -> COMPANY,
                (kind, partyId, isReturn, number) -> {
                    asked.add(kind + "/" + partyId + "/" + isReturn + "/" + number);
                    return new BigDecimal("1600");
                });

        InvoicePrintDocument document = builder.build(DocumentType.SALES, header(InvoiceType.DEFER),
                "عميل", 5, "مندوب", 0, "", List.of(), "now");

        assertEquals(List.of(PartyKind.CUSTOMER + "/5/false/77"), asked);
        assertEquals(0, document.balance().after().compareTo(new BigDecimal("1600")));
        assertEquals(0, document.balance().before().compareTo(new BigDecimal("1000")),
                "before = after less the 600 this invoice put on the account");
        assertEquals(0, document.rest().compareTo(new BigDecimal("600")));
        assertEquals("مندوب", document.delegateName());
        assertSame(COMPANY, document.letterhead());
    }

    /** A return takes its amount off the account, so the balance before it was higher. */
    @Test
    void aDeferredReturnsBalanceBeforeIsAboveTheBalanceAfter() throws Exception {
        InvoicePrintDocumentBuilder builder = new InvoicePrintDocumentBuilder(() -> COMPANY,
                (kind, partyId, isReturn, number) -> {
                    assertEquals(PartyKind.SUPPLIER, kind);
                    assertTrue(isReturn);
                    return new BigDecimal("400");
                });

        InvoicePrintDocument document = builder.build(DocumentType.PURCHASE_RETURN, header(InvoiceType.DEFER),
                "مورد", 9, "مندوب", 55, "تالف", List.of(), "now");

        assertEquals(0, document.balance().before().compareTo(new BigDecimal("1000")));
        assertEquals("", document.delegateName(), "a purchase carries no delegate");
        assertEquals(55, document.sourceInvoiceNumber());
        assertEquals("تالف", document.returnReason());
    }

    @Test
    void aCashDocumentDoesNotAskForTheBalance() throws Exception {
        InvoicePrintDocumentBuilder builder = new InvoicePrintDocumentBuilder(() -> COMPANY,
                (kind, partyId, isReturn, number) -> fail("a cash invoice prints no balance"));

        InvoicePrintDocument document = builder.build(DocumentType.SALES, header(InvoiceType.CASH),
                "عميل", 5, "", 12, "سبب", List.of(), "now");

        assertNull(document.balance());
        assertEquals(0, document.sourceInvoiceNumber(), "a sale reverses nothing");
        assertEquals("", document.returnReason());
    }

    @Test
    void aReaderWhoMayNotSeeTheAccountGetsTheInvoiceWithoutTheBalance() throws Exception {
        InvoicePrintDocumentBuilder builder = new InvoicePrintDocumentBuilder(() -> COMPANY,
                (kind, partyId, isReturn, number) -> {
                    throw new BusinessRuleException("لا تملك صلاحية");
                });

        InvoicePrintDocument document = builder.build(DocumentType.SALES, header(InvoiceType.DEFER),
                "عميل", 5, "", 0, "", List.of(), "now");

        assertNull(document.balance());
        assertEquals(0, document.net().compareTo(new BigDecimal("900")));
    }

    /** A paper silently missing a figure it should carry is worse than an error. */
    @Test
    void aFailureToReadTheBalanceIsNotSwallowed() {
        InvoicePrintDocumentBuilder builder = new InvoicePrintDocumentBuilder(() -> COMPANY,
                (kind, partyId, isReturn, number) -> {
                    throw new DaoException("connection lost");
                });

        assertThrows(DaoException.class, () -> builder.build(DocumentType.SALES, header(InvoiceType.DEFER),
                "عميل", 5, "", 0, "", List.of(), "now"));
    }

    @Test
    void aDocumentNotInTheLedgerPrintsWithoutTheBalance() throws Exception {
        InvoicePrintDocumentBuilder builder = new InvoicePrintDocumentBuilder(() -> COMPANY,
                (kind, partyId, isReturn, number) -> null);

        assertNull(builder.build(DocumentType.SALES, header(InvoiceType.DEFER),
                "عميل", 5, "", 0, "", List.of(), "now").balance());
    }
}
