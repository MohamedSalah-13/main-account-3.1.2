package com.hamza.account.features.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.delegate.DelegateDiscountGuard;
import com.hamza.account.features.delegate.DiscountCeiling;
import com.hamza.account.features.delegate.DiscountCeilingRepository;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.shift.ShiftAttributionWriter;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.returns.ReturnCostResolver;
import com.hamza.account.features.returns.ReturnGuard;
import com.hamza.account.features.returns.ReturnSourceWriter;
import com.hamza.account.features.stockledger.StockMovementDao;
import com.hamza.account.document.TotalsAndPurchaseList;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.model.domain.*;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvoiceSaveServiceTest {

    private static final LocalDateTime VERSION =
            LocalDateTime.of(2026, 8, 13, 8, 30, 0, 123_456_000);

    private TotalsAndPurchaseList<Sales, Total_Sales> repository;
    private DaoList<Total_Sales> dao;
    private InvoiceNumberAllocator numberAllocator;
    private InvoiceStockGuard stockGuard;
    private ReturnGuard returnGuard;
    private ReturnSourceWriter returnSourceWriter;
    private ReturnCostResolver returnCostResolver;
    private StockMovementDao stockMovementDao;
    private ChangeAnnouncer changeAnnouncer;
    private InvoiceSaveService<Sales, Total_Sales, Customers, CustomerAccount> service;
    private UserSessionContext session;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(TotalsAndPurchaseList.class);
        dao = mock(DaoList.class);
        numberAllocator = mock(InvoiceNumberAllocator.class);
        stockGuard = mock(InvoiceStockGuard.class);
        returnGuard = mock(ReturnGuard.class);
        returnSourceWriter = mock(ReturnSourceWriter.class);
        returnCostResolver = mock(ReturnCostResolver.class);
        stockMovementDao = mock(StockMovementDao.class);
        changeAnnouncer = mock(ChangeAnnouncer.class);
        when(repository.totalDao()).thenReturn(dao);
        service = new InvoiceSaveService<>(new SalesInvoice(), repository,
                DocumentType.SALES, Clock.fixed(
                Instant.parse("2026-08-13T05:00:00Z"), ZoneOffset.UTC),
                numberAllocator, InvoiceTransactionExecutor.direct(),
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver,
                name -> new Treasury(1, name, BigDecimal.ZERO),
                name -> new Employees(2, name), stockMovementDao, changeAnnouncer);
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
        clearInvocations(repository, dao, numberAllocator);
    }

    @Test
    void createsAggregateFromLinesAndPersistsItOnce() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        when(numberAllocator.next(DocumentType.SALES)).thenReturn(44);
        when(dao.insert(any())).thenReturn(1);

        InvoiceSaveResult result = service.save(command(0, 5));
        Total_Sales invoice = (Total_Sales) result.invoice();

        assertEquals(44, result.invoiceNumber());
        assertFalse(result.updated());
        assertEquals(18, invoice.getTotal());
        assertEquals(3, invoice.getDiscount());
        assertEquals(15, invoice.getTotal_after_discount());
        assertEquals(5, invoice.getPaid());
        assertEquals(10, invoice.getRest());
        assertEquals(44, result.persistedLines().getFirst().getInvoiceNumber());
        verify(dao).insert(invoice);
        verify(stockGuard).validate(any());
        verify(returnGuard).validate(eq(DocumentType.SALES), eq(0), eq(0), any(), anyInt(), any());
        verify(dao, never()).update(any());
        verify(stockMovementDao).deleteByReference("SALE", 44);
        verify(stockMovementDao).insertBatch(argThat(movements -> movements.size() == 1));
        verify(changeAnnouncer).announce(new InvoiceSaved(DocumentType.SALES.side()));
        // SALES is not a return - nothing here has a source invoice to link.
        verifyNoInteractions(returnSourceWriter);
    }

    @Test
    void updateKeepsExistingNumberAndDoesNotAllocateAnother() throws Exception {
        session.signIn(7, "manager", Set.of(AppPermissions.SALES_UPDATE));
        when(dao.update(any())).thenReturn(1);

        InvoiceSaveResult result = service.save(command(91, 5, 37));
        Total_Sales invoice = (Total_Sales) result.invoice();

        assertEquals(91, result.invoiceNumber());
        assertTrue(result.updated());
        assertEquals(37, result.persistedLines().getFirst().getId(),
                "an edited line must reach the DAO with its stored identity");
        assertEquals(37, invoice.getSalesList().getFirst().getId());
        assertEquals(VERSION, invoice.getUpdated_at());
        verifyNoInteractions(numberAllocator);
        verify(stockGuard).validate(any());
        // Updating id 91: excludingReturnId is not this DAO's concern for a sale, but
        // the guard is asked with the existing invoice id all the same.
        verify(returnGuard).validate(eq(DocumentType.SALES), eq(0), eq(91), any(), anyInt(), any());
        verify(dao).update(invoice);
        verify(dao, never()).insert(any());
        verify(stockMovementDao).deleteByReference("SALE", 91);
        verify(stockMovementDao).insertBatch(argThat(movements -> movements.size() == 1));
        verify(changeAnnouncer).announce(new InvoiceSaved(DocumentType.SALES.side()));
    }

    @Test
    void authorizationRunsBeforeNumberAllocationOrPersistence() {
        session.signOut();

        assertThrows(DaoException.class, () -> service.save(command(0, 0)));

        verifyNoInteractions(repository);
        verifyNoInteractions(dao);
        verifyNoInteractions(numberAllocator);
        verifyNoInteractions(stockGuard);
        verifyNoInteractions(returnGuard);
        verifyNoInteractions(stockMovementDao);
    }

    @Test
    void invalidPaymentDoesNotAllocateNumberOrWrite() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        InvoiceSaveCommand invalid = command(0, 100);

        InvoiceValidationException error = assertThrows(
                InvoiceValidationException.class, () -> service.save(invalid));

        assertEquals(InvoiceSaveValidator.Target.PAID, error.target());
        verifyNoInteractions(numberAllocator);
        verifyNoInteractions(stockGuard);
        verifyNoInteractions(returnGuard);
        verifyNoInteractions(stockMovementDao);
        verify(dao, never()).insert(any());
    }

    @Test
    void stockRefusalHappensBeforeNumberAllocationOrPersistence() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        doThrow(new BusinessRuleException("stock changed"))
                .when(stockGuard).validate(any());

        assertThrows(BusinessRuleException.class,
                () -> service.save(command(0, 5)));

        verifyNoInteractions(numberAllocator);
        verify(dao, never()).insert(any());
        verify(dao, never()).update(any());
        verifyNoInteractions(stockMovementDao);
    }

    @Test
    void returnRefusalHappensBeforeNumberAllocationOrPersistence() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        doThrow(new BusinessRuleException("would exceed the source invoice"))
                .when(returnGuard).validate(any(), anyInt(), anyInt(), any(), anyInt(), any());

        assertThrows(BusinessRuleException.class,
                () -> service.save(command(0, 5)));

        verifyNoInteractions(numberAllocator);
        verify(dao, never()).insert(any());
        verify(dao, never()).update(any());
        verifyNoInteractions(stockMovementDao);
        verifyNoInteractions(returnSourceWriter);
    }

    /**
     * The fixture's document is 20 before discount, 2 off on the line and 3 on the header: 25%.
     * A ceiling on the header alone would read 3 of 18 and miss what the line took.
     */
    @Test
    void aSaleAboveItsDelegatesCeilingIsRefusedBeforeANumberIsTaken() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        var guarded = withCeiling(new BigDecimal("20"), false);

        InvoiceValidationException refusal = assertThrows(InvoiceValidationException.class,
                () -> guarded.save(command(0, 5)));

        assertEquals(InvoiceSaveValidator.Target.DISCOUNT, refusal.target());
        assertEquals(DelegateDiscountGuard.REFUSAL_KEY, refusal.getMessage());
        verifyNoInteractions(numberAllocator);
        verify(dao, never()).insert(any());
        verifyNoInteractions(stockMovementDao);
    }

    @Test
    void theSameSaleIsSavedInsideTheCeilingOrByWhoeverMayOverrideIt() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        when(numberAllocator.next(DocumentType.SALES)).thenReturn(45);
        when(dao.insert(any())).thenReturn(1);

        assertEquals(45, withCeiling(new BigDecimal("25"), false).save(command(0, 5)).invoiceNumber());
        assertEquals(45, withCeiling(new BigDecimal("20"), true).save(command(0, 5)).invoiceNumber());
    }

    /**
     * A customer who deals in dollars (V82, docs/currency-plan.md §14 ق-ج٣): with no rate on the
     * invoice's day the save is refused before a number is taken - the counter does not roll back.
     */
    @Test
    void aDollarCustomersInvoiceWithNoRateIsRefusedBeforeANumberIsTaken() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        var memory = new com.hamza.account.features.party.currency.PartyCurrencyFixtures.Memory()
                .party(com.hamza.account.features.events.PartyKind.CUSTOMER, 8, USD);

        InvoiceValidationException refusal = assertThrows(InvoiceValidationException.class,
                () -> withPartyCurrency(memory).save(inDollars(command(0, 5))));

        assertEquals(InvoiceSaveValidator.Target.DATE, refusal.target());
        verifyNoInteractions(numberAllocator);
        verify(dao, never()).insert(any());
        assertTrue(memory.written.isEmpty());
    }

    /**
     * A dollar customer's invoice is typed in dollars (V83, docs/currency-plan.md §15): the lines and the
     * header are stored in the base at the day's rate - so the cost, the profit and the stock never learn
     * a currency was involved - and exactly what was typed is written beside them.
     */
    @Test
    void aDollarCustomersInvoiceIsTypedInDollarsAndStoredInTheBase() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        when(numberAllocator.next(DocumentType.SALES)).thenReturn(46);
        when(dao.insert(any())).thenReturn(1);
        var memory = dollarCustomer();

        // 2 at 10 dollars less 2 off the line, 3 off the invoice, 5 paid: 18, 15 net, 10 on account.
        assertEquals(46, withPartyCurrency(memory).save(inDollars(command(0, 5))).invoiceNumber());

        var stored = org.mockito.ArgumentCaptor.forClass(Total_Sales.class);
        verify(dao).insert(stored.capture());
        Total_Sales header = stored.getValue();
        assertEquals(900.0, header.getTotal(), 0.0001, "the line at 500 a piece less 100 off, in pounds");
        assertEquals(150.0, header.getDiscount(), 0.0001);
        assertEquals(250.0, header.getPaid(), 0.0001, "the 5 dollars that came in, in pounds");
        Sales line = header.getSalesList().getFirst();
        assertEquals(500.0, line.getPrice(), 0.0001);
        assertEquals(100.0, line.getDiscount(), 0.0001);
        assertEquals(new BigDecimal("10.00"), line.getPriceForeign(), "the price as typed, beside the base");
        assertEquals(new BigDecimal("2.00"), line.getDiscountForeign());

        var written = memory.written.get("SALES:46");
        assertEquals(new BigDecimal("50"), written.rate());
        assertEquals(new BigDecimal("18.00"), written.total());
        assertEquals(new BigDecimal("3.00"), written.discount());
        assertEquals(new BigDecimal("5.00"), written.paid());
        assertEquals(USD.id(), memory.writtenCurrency.get("SALES:46"), "and the currency it was typed in");
    }

    /** A cash invoice paid in full leaves nothing on the account, in pounds as in dollars. */
    @Test
    void aCashDollarInvoiceLeavesNothingOnTheAccountInEitherCurrency() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        when(numberAllocator.next(DocumentType.SALES)).thenReturn(47);
        when(dao.insert(any())).thenReturn(1);
        var memory = dollarCustomer();
        InvoiceSaveCommand cash = inDollars(command(0, 0));
        cash = new InvoiceSaveCommand(cash.existingInvoiceId(), cash.invoiceDate(), InvoiceType.CASH,
                cash.invoiceDiscount(), cash.discountType(), cash.enteredPaid(), cash.notes(), cash.partyId(),
                cash.partyName(), cash.treasuryName(), cash.delegateName(), cash.allowInsufficientStock(),
                cash.sourceInvoiceNumber(), cash.returnReason(), cash.lines(), cash.stockId(),
                cash.correctionReason(), cash.expectedUpdatedAt(), cash.documentCurrencyId());

        withPartyCurrency(memory).save(cash);

        var stored = org.mockito.ArgumentCaptor.forClass(Total_Sales.class);
        verify(dao).insert(stored.capture());
        assertEquals(750.0, stored.getValue().getPaid(), 0.0001, "the net, exactly");
        assertEquals(0, memory.written.get("SALES:47").remainder().signum());
    }

    /**
     * The dollar drawer takes a dollar invoice's cash; a drawer in a third currency does not, and neither
     * does a wallet on it - its fee would be an expense on a treasury in a foreign currency (§15 ق-د٦).
     */
    @Test
    void aTreasuryInTheInvoicesCurrencyTakesItsCash() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        when(numberAllocator.next(DocumentType.SALES)).thenReturn(48);
        when(dao.insert(any())).thenReturn(1);

        Treasury dollarDrawer = new Treasury(4, "درج الدولار", BigDecimal.ZERO);
        dollarDrawer.setCurrencyId(USD.id());
        assertEquals(48, withPartyCurrency(dollarCustomer(), dollarDrawer).save(inDollars(command(0, 5)))
                .invoiceNumber());

        Treasury riyalDrawer = new Treasury(5, "درج الريال", BigDecimal.ZERO);
        riyalDrawer.setCurrencyId(com.hamza.account.features.party.currency.PartyCurrencyFixtures.SAR.id());
        assertEquals(InvoiceSaveValidator.Target.TREASURY, assertThrows(InvoiceValidationException.class,
                () -> withPartyCurrency(dollarCustomer(), riyalDrawer).save(inDollars(command(0, 5)))).target());

        Treasury dollarWallet = new Treasury(6, "محفظة دولار", BigDecimal.ZERO);
        dollarWallet.setCurrencyId(USD.id());
        dollarWallet.setFeePercent(new BigDecimal("1.5"));
        assertEquals(InvoiceSaveValidator.Target.TREASURY, assertThrows(InvoiceValidationException.class,
                () -> withPartyCurrency(dollarCustomer(), dollarWallet).save(inDollars(command(0, 5)))).target());

        var poundCustomer = new com.hamza.account.features.party.currency.PartyCurrencyFixtures.Memory();
        assertEquals(InvoiceSaveValidator.Target.TREASURY, assertThrows(InvoiceValidationException.class,
                () -> withPartyCurrency(poundCustomer, dollarDrawer).save(command(0, 5))).target(),
                "a customer in the base pays into no foreign drawer");
        verify(numberAllocator, times(1)).next(DocumentType.SALES);
    }

    private static final com.hamza.account.features.currency.Currency USD =
            com.hamza.account.features.party.currency.PartyCurrencyFixtures.USD;

    private static com.hamza.account.features.party.currency.PartyCurrencyFixtures.Memory dollarCustomer() {
        return new com.hamza.account.features.party.currency.PartyCurrencyFixtures.Memory()
                .party(com.hamza.account.features.events.PartyKind.CUSTOMER, 8, USD)
                .rate(USD, LocalDate.of(2026, 8, 1), "50");
    }

    /** The same command, its figures typed in dollars. */
    private static InvoiceSaveCommand inDollars(InvoiceSaveCommand c) {
        return new InvoiceSaveCommand(c.existingInvoiceId(), c.invoiceDate(), c.invoiceType(),
                c.invoiceDiscount(), c.discountType(), c.enteredPaid(), c.notes(), c.partyId(), c.partyName(),
                c.treasuryName(), c.delegateName(), c.allowInsufficientStock(), c.sourceInvoiceNumber(),
                c.returnReason(), c.lines(), c.stockId(), c.correctionReason(), c.expectedUpdatedAt(), USD.id());
    }

    private InvoiceSaveService<Sales, Total_Sales, Customers, CustomerAccount> withPartyCurrency(
            com.hamza.account.features.party.currency.PartyCurrencies currencies) {
        return withPartyCurrency(currencies, null);
    }

    private InvoiceSaveService<Sales, Total_Sales, Customers, CustomerAccount> withPartyCurrency(
            com.hamza.account.features.party.currency.PartyCurrencies currencies, Treasury treasury) {
        return new InvoiceSaveService<>(new SalesInvoice(), repository,
                DocumentType.SALES, Clock.fixed(Instant.parse("2026-08-13T05:00:00Z"), ZoneOffset.UTC),
                numberAllocator, InvoiceTransactionExecutor.direct(),
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver,
                name -> treasury != null ? treasury : new Treasury(1, name, BigDecimal.ZERO),
                name -> new Employees(2, name), stockMovementDao,
                ShiftGate.disabled(), ShiftAttributionWriter.disabled(), ShiftCashLedger.disabled(), null,
                changeAnnouncer, InvoiceWalletFee.none(), DelegateDiscountGuard.none(),
                new InvoicePartyCurrency(currencies));
    }

    private InvoiceSaveService<Sales, Total_Sales, Customers, CustomerAccount> withCeiling(
            BigDecimal maxPercent, boolean mayOverride) {
        DiscountCeilingRepository ceilings = new DiscountCeilingRepository() {
            @Override
            public java.util.Optional<DiscountCeiling> ceilingOf(int employeeId) {
                return employeeId == 2 ? DiscountCeiling.ofStored(maxPercent) : java.util.Optional.empty();
            }

            @Override
            public int write(int employeeId, BigDecimal value) {
                return 0;
            }
        };
        return new InvoiceSaveService<>(new SalesInvoice(), repository,
                DocumentType.SALES, Clock.fixed(Instant.parse("2026-08-13T05:00:00Z"), ZoneOffset.UTC),
                numberAllocator, InvoiceTransactionExecutor.direct(),
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver,
                name -> new Treasury(1, name, BigDecimal.ZERO),
                name -> new Employees(2, name), stockMovementDao,
                ShiftGate.disabled(), ShiftAttributionWriter.disabled(), ShiftCashLedger.disabled(), null,
                changeAnnouncer, InvoiceWalletFee.none(),
                new DelegateDiscountGuard(ceilings, () -> mayOverride));
    }

    private InvoiceSaveCommand command(int existingId, double paid) {
        return command(existingId, paid, 0);
    }

    private InvoiceSaveCommand command(int existingId, double paid, int lineId) {
        return new InvoiceSaveCommand(existingId, LocalDate.of(2026, 8, 13),
                InvoiceType.DEFER, BigDecimal.valueOf(3), DiscountType.AMOUNT,
                BigDecimal.valueOf(paid), " test ", 8, "عميل", "الرئيسية", "مندوب",
                false, 0, null, List.of(line(lineId)), 1, null,
                existingId > 0 ? VERSION : null);
    }

    private Sales line(int id) {
        ItemsModel item = new ItemsModel();
        item.setId(12);
        item.setNameItem("صنف");
        item.setBarcode("123");
        item.setBuyPrice(4);
        UnitsModel unit = new UnitsModel(1, "قطعة", 1);
        Sales line = new Sales();
        line.setId(id);
        line.setItems(item);
        line.setUnitsType(unit);
        line.setPrice(10);
        line.setQuantity(2);
        line.setTotal(20);
        line.setDiscount(2);
        return line;
    }
}
