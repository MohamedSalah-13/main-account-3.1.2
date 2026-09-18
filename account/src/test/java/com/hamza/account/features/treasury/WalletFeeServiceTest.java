package com.hamza.account.features.treasury;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingRepository;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fee and the movement it was paid for, over a repository in memory.
 * <p>
 * What is pinned here is the defect the link exists to end - a deleted collection leaving its fee
 * behind, and its re-entry charging it twice - and the rules a document's computed fee follows when
 * the document is edited. That the rows land in MySQL is
 * {@code TreasuryBalanceViewAcceptanceTest}'s to say, not this class's.
 */
class WalletFeeServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);
    private static final int WALLET = 3;
    private static final int DRAWER = 1;
    private static final BigDecimal ONE_PERCENT = new BigDecimal("1.00");

    private final InMemoryFees fees = new InMemoryFees();
    private WalletFeeService service;

    @BeforeEach
    void setUp() throws Exception {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", List.of());
        ServiceRegistry.register(UserSessionContext.class, session);

        ExpenseHeadingRepository headings = Mockito.mock(ExpenseHeadingRepository.class);
        Mockito.when(headings.bySystemKey(ExpenseHeading.WALLET_FEE))
                .thenReturn(new ExpenseHeading(7, "عمولات تحويل", null, null, true, ExpenseHeading.WALLET_FEE, false));
        service = new WalletFeeService(fees, headings, ShiftGate.disabled(), ShiftCashLedger.disabled(),
                ChangeAnnouncer.disabled());
    }

    @Test
    @DisplayName("a posted fee carries the movement it was paid for")
    void aPostedFeeNamesItsSource() throws Exception {
        WalletFeeSource collection = WalletFeeSource.party(PartyKind.CUSTOMER, 41);

        assertEquals(1, service.post(collection, WALLET, DAY, new BigDecimal("1000"), new BigDecimal("10"),
                "note", OptionalInt.empty()));

        InMemoryFees.Row row = fees.rows.get(collection);
        assertEquals(new BigDecimal("10.00"), row.amount);
        assertEquals(WALLET, row.treasuryId);
        assertEquals(7, row.headingId);
        assertEquals(9, row.userId);
    }

    @Test
    @DisplayName("deleting the collection takes its fee, so entering it again charges it once")
    void deletingAndReenteringChargesOnce() throws Exception {
        WalletFeeSource first = WalletFeeSource.party(PartyKind.CUSTOMER, 41);
        service.post(first, WALLET, DAY, new BigDecimal("1000"), new BigDecimal("10"), null, OptionalInt.empty());

        assertEquals(1, service.removeFor(first, null));
        WalletFeeSource again = WalletFeeSource.party(PartyKind.CUSTOMER, 42);
        service.post(again, WALLET, DAY, new BigDecimal("1000"), new BigDecimal("10"), null, OptionalInt.empty());

        assertEquals(1, fees.rows.size());
        assertEquals(new BigDecimal("10.00"), fees.total());
    }

    @Test
    @DisplayName("a movement with no fee is deleted without touching anybody else's")
    void removingWhereThereIsNoneTouchesNothing() throws Exception {
        service.post(WalletFeeSource.party(PartyKind.CUSTOMER, 41), WALLET, DAY, new BigDecimal("1000"),
                new BigDecimal("10"), null, OptionalInt.empty());

        // The same number on the other ledger is another movement entirely.
        assertEquals(0, service.removeFor(WalletFeeSource.party(PartyKind.SUPPLIER, 41), null));
        assertEquals(1, fees.rows.size());
    }

    @Test
    @DisplayName("a fee as large as the payment is a typing error, and nothing is written")
    void anImplausibleFeeIsRefused() {
        assertThrows(BusinessRuleException.class, () -> service.post(
                WalletFeeSource.party(PartyKind.CUSTOMER, 41), WALLET, DAY, new BigDecimal("100"),
                new BigDecimal("100"), null, OptionalInt.empty()));
        assertTrue(fees.rows.isEmpty());
    }

    @Test
    @DisplayName("a new cash sale on a wallet posts the percentage; on a drawer it posts nothing")
    void aNewDocumentIsChargedOnAWalletOnly() throws Exception {
        service.syncDocument(sale(7), WALLET, DAY, new BigDecimal("1000"), null, ONE_PERCENT,
                OptionalInt.empty(), null);
        service.syncDocument(sale(8), DRAWER, DAY, new BigDecimal("1000"), null, BigDecimal.ZERO,
                OptionalInt.empty(), null);

        assertEquals(new BigDecimal("10.00"), fees.rows.get(sale(7)).amount);
        assertNull(fees.rows.get(sale(8)));
    }

    @Test
    @DisplayName("editing the cash of a document rewrites its one fee row")
    void editingTheCashRewritesTheFee() throws Exception {
        service.syncDocument(sale(7), WALLET, DAY, new BigDecimal("1000"), null, ONE_PERCENT,
                OptionalInt.empty(), null);

        service.syncDocument(sale(7), WALLET, DAY, new BigDecimal("1500"),
                new WalletFeeService.PreviousCash(new BigDecimal("1000"), WALLET), ONE_PERCENT,
                OptionalInt.empty(), null);

        assertEquals(1, fees.rows.size());
        assertEquals(new BigDecimal("15.00"), fees.rows.get(sale(7)).amount);
    }

    @Test
    @DisplayName("moving a document from the wallet to the drawer removes its fee")
    void movingToADrawerRemovesTheFee() throws Exception {
        service.syncDocument(sale(7), WALLET, DAY, new BigDecimal("1000"), null, ONE_PERCENT,
                OptionalInt.empty(), null);

        service.syncDocument(sale(7), DRAWER, DAY, new BigDecimal("1000"),
                new WalletFeeService.PreviousCash(new BigDecimal("1000"), WALLET), BigDecimal.ZERO,
                OptionalInt.empty(), null);

        assertTrue(fees.rows.isEmpty());
    }

    @Test
    @DisplayName("a fee already posted is not re-rated when only a note changed")
    void anUntouchedDocumentKeepsTheFeeItWasCharged() throws Exception {
        service.syncDocument(sale(7), WALLET, DAY, new BigDecimal("1000"), null, ONE_PERCENT,
                OptionalInt.empty(), null);

        // The treasury's percentage has been raised since, and then set to nothing.
        var unchanged = new WalletFeeService.PreviousCash(new BigDecimal("1000"), WALLET);
        service.syncDocument(sale(7), WALLET, DAY, new BigDecimal("1000"), unchanged, new BigDecimal("2.00"),
                OptionalInt.empty(), null);
        service.syncDocument(sale(7), WALLET, DAY, new BigDecimal("1000"), unchanged, BigDecimal.ZERO,
                OptionalInt.empty(), null);

        assertEquals(new BigDecimal("10.00"), fees.rows.get(sale(7)).amount);
    }

    @Test
    @DisplayName("a document saved before the link existed is not charged for a corrected note")
    void anOldDocumentIsNotChargedRetroactively() throws Exception {
        service.syncDocument(sale(7), WALLET, DAY, new BigDecimal("1000"),
                new WalletFeeService.PreviousCash(new BigDecimal("1000"), WALLET), ONE_PERCENT,
                OptionalInt.empty(), null);

        assertTrue(fees.rows.isEmpty());
    }

    @Test
    @DisplayName("only a document or a party payment can carry a fee")
    void otherSourcesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new WalletFeeSource(ShiftCashSource.EXPENSE, 1));
        assertThrows(IllegalArgumentException.class, () -> new WalletFeeSource(ShiftCashSource.TRANSFER_OUT, 1));
        assertThrows(IllegalArgumentException.class, () -> new WalletFeeSource(ShiftCashSource.SALES, 0));
    }

    private static WalletFeeSource sale(int number) {
        return WalletFeeSource.document(DocumentType.SALES, number);
    }

    /** One fee per source, which is what the unique index in V67 says. */
    private static final class InMemoryFees implements WalletFeeRepository {

        static final class Row {
            int id;
            int headingId;
            LocalDate date;
            BigDecimal amount;
            int treasuryId;
            int userId;
            Integer shiftId;
        }

        final Map<WalletFeeSource, Row> rows = new LinkedHashMap<>();
        private int nextId = 100;

        BigDecimal total() {
            return rows.values().stream().map(row -> row.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public StoredFee lockFor(WalletFeeSource source) {
            Row row = rows.get(source);
            return row == null ? null : new StoredFee(row.id, row.date, row.amount, row.treasuryId, row.shiftId);
        }

        @Override
        public int insert(WalletFeeSource source, int headingId, LocalDate date, BigDecimal amount, String notes,
                          int treasuryId, int userId, Integer shiftId) {
            if (rows.containsKey(source)) {
                throw new IllegalStateException("a second fee for " + source);
            }
            Row row = new Row();
            row.id = nextId++;
            row.headingId = headingId;
            row.date = date;
            row.amount = amount;
            row.treasuryId = treasuryId;
            row.userId = userId;
            row.shiftId = shiftId;
            rows.put(source, row);
            return row.id;
        }

        @Override
        public int update(int expenseId, LocalDate date, BigDecimal amount, int treasuryId) {
            for (Row row : rows.values()) {
                if (row.id == expenseId) {
                    row.date = date;
                    row.amount = amount;
                    row.treasuryId = treasuryId;
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int delete(int expenseId) {
            return rows.values().removeIf(row -> row.id == expenseId) ? 1 : 0;
        }
    }
}
