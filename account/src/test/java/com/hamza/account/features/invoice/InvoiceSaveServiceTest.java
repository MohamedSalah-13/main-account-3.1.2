package com.hamza.account.features.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.returns.ReturnGuard;
import com.hamza.account.features.returns.ReturnSourceWriter;
import com.hamza.account.features.stockledger.StockMovementDao;
import com.hamza.account.interfaces.api.TotalsAndPurchaseList;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvoiceSaveServiceTest {

    private TotalsAndPurchaseList<Sales, Total_Sales> repository;
    private DaoList<Total_Sales> dao;
    private InvoiceNumberAllocator numberAllocator;
    private InvoiceStockGuard stockGuard;
    private ReturnGuard returnGuard;
    private ReturnSourceWriter returnSourceWriter;
    private StockMovementDao stockMovementDao;
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
        stockMovementDao = mock(StockMovementDao.class);
        when(repository.totalDao()).thenReturn(dao);
        service = new InvoiceSaveService<>(new SalesInvoice(), repository,
                DocumentType.SALES, Clock.fixed(
                Instant.parse("2026-08-13T05:00:00Z"), ZoneOffset.UTC),
                numberAllocator, InvoiceTransactionExecutor.direct(),
                stockGuard, returnGuard, returnSourceWriter,
                name -> new Treasury(1, name, BigDecimal.ZERO),
                name -> new Employees(2, name), stockMovementDao);
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
        clearInvocations(repository, dao, numberAllocator);
    }

    @Test
    void createsAggregateFromLinesAndPersistsItOnce() throws Exception {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        when(numberAllocator.next(DocumentType.SALES)).thenReturn(44);
        when(dao.insert(any())).thenReturn(1);

        InvoiceSaveResult<Sales, Total_Sales> result = service.save(command(0, 5));

        assertEquals(44, result.invoiceNumber());
        assertFalse(result.updated());
        assertEquals(18, result.invoice().getTotal());
        assertEquals(3, result.invoice().getDiscount());
        assertEquals(15, result.invoice().getTotal_after_discount());
        assertEquals(5, result.invoice().getPaid());
        assertEquals(10, result.invoice().getRest());
        assertEquals(44, result.persistedLines().getFirst().getInvoiceNumber());
        verify(dao).insert(result.invoice());
        verify(stockGuard).validate(any());
        verify(returnGuard).validate(eq(DocumentType.SALES), eq(0), eq(0), any());
        verify(dao, never()).update(any());
        verify(stockMovementDao).deleteByReference("SALE", 44);
        verify(stockMovementDao).insertBatch(argThat(movements -> movements.size() == 1));
        // SALES is not a return - nothing here has a source invoice to link.
        verifyNoInteractions(returnSourceWriter);
    }

    @Test
    void updateKeepsExistingNumberAndDoesNotAllocateAnother() throws Exception {
        session.signIn(7, "manager", Set.of(AppPermissions.SALES_UPDATE));
        when(dao.update(any())).thenReturn(1);

        InvoiceSaveResult<Sales, Total_Sales> result = service.save(command(91, 5, 37));

        assertEquals(91, result.invoiceNumber());
        assertTrue(result.updated());
        assertEquals(37, result.persistedLines().getFirst().getId(),
                "an edited line must reach the DAO with its stored identity");
        assertEquals(37, result.invoice().getSalesList().getFirst().getId());
        verifyNoInteractions(numberAllocator);
        verify(stockGuard).validate(any());
        // Updating id 91: excludingReturnId is not this DAO's concern for a sale, but
        // the guard is asked with the existing invoice id all the same.
        verify(returnGuard).validate(eq(DocumentType.SALES), eq(0), eq(91), any());
        verify(dao).update(result.invoice());
        verify(dao, never()).insert(any());
        verify(stockMovementDao).deleteByReference("SALE", 91);
        verify(stockMovementDao).insertBatch(argThat(movements -> movements.size() == 1));
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
        InvoiceSaveCommand<Sales> invalid = command(0, 100);

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
                .when(returnGuard).validate(any(), anyInt(), anyInt(), any());

        assertThrows(BusinessRuleException.class,
                () -> service.save(command(0, 5)));

        verifyNoInteractions(numberAllocator);
        verify(dao, never()).insert(any());
        verify(dao, never()).update(any());
        verifyNoInteractions(stockMovementDao);
        verifyNoInteractions(returnSourceWriter);
    }

    private InvoiceSaveCommand<Sales> command(int existingId, double paid) {
        return command(existingId, paid, 0);
    }

    private InvoiceSaveCommand<Sales> command(int existingId, double paid, int lineId) {
        return new InvoiceSaveCommand<>(existingId, LocalDate.of(2026, 8, 13),
                InvoiceType.DEFER, 3, DiscountType.AMOUNT, paid, " test ",
                8, "عميل", "الرئيسية", "مندوب", List.of(line(lineId)));
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
