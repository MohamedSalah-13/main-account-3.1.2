package com.hamza.account.features.invoice;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.document.DocumentType;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.InvoiceBuy;
import com.hamza.account.interfaces.api.TotalsAndPurchaseList;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.type.DiscountType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Application service that owns invoice validation, calculation, construction and persistence. */
public final class InvoiceSaveService<
        T1 extends BasePurchasesAndSales,
        T2 extends BaseTotals,
        T3 extends BaseNames,
        T4 extends BaseAccount> {

    private final InvoiceBuy<T1, T2, T3, T4> invoiceFactory;
    private final TotalsAndPurchaseList<T1, T2> repository;
    private final DocumentType documentType;
    private final Clock clock;
    private final InvoiceNumberAllocator numberAllocator;
    private final InvoiceTransactionExecutor transactions;
    private final InvoiceLookup<Treasury> treasuryLookup;
    private final InvoiceLookup<Employees> delegateLookup;

    public InvoiceSaveService(DataInterface<T1, T2, T3, T4> dataInterface,
                              InvoiceLookup<Treasury> treasuryLookup,
                              InvoiceLookup<Employees> delegateLookup) {
        this(dataInterface.invoiceBuy(), dataInterface.totalsAndPurchaseList(),
                dataInterface.designInterface().documentType(), Clock.systemDefaultZone(),
                new JdbcInvoiceNumberAllocator(), InvoiceTransactionExecutor.jdbc(),
                treasuryLookup, delegateLookup);
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup) {
        this.invoiceFactory = invoiceFactory;
        this.repository = repository;
        this.documentType = documentType;
        this.clock = clock;
        this.numberAllocator = numberAllocator;
        this.transactions = transactions;
        this.treasuryLookup = treasuryLookup;
        this.delegateLookup = delegateLookup;
    }

    public InvoiceSaveResult<T1, T2> save(InvoiceSaveCommand<T1> command) throws DaoException {
        if (command == null) {
            throw new DaoException("بيانات الفاتورة مفقودة");
        }

        // The permission boundary comes first: a denied request must not even read
        // the next invoice number or inspect database-backed collaborators.
        AuthorizationGuard.require(command.updating()
                ? documentType.updatePermission()
                : documentType.createPermission());

        InvoiceLineTotals lineTotals = InvoiceLineTotals.from(command.lines());
        InvoiceSaveValidator.Problem problem = InvoiceSaveValidator.firstProblem(
                lineTotals.lineCount(), lineTotals.hasInvalidLine(), command.invoiceDate(),
                LocalDate.now(clock), documentType.hasDelegate(), !command.delegateName().isBlank(),
                !command.treasuryName().isBlank(), command.partyId()).orElse(null);
        if (problem != null) {
            throw new InvoiceValidationException(problem.target(), problem.message());
        }

        InvoicePaymentTerms payment = InvoicePaymentTerms.resolve(
                command.invoiceType(), lineTotals.net(), command.invoiceDiscount(),
                command.enteredPaid());
        return transactions.execute(() -> persist(command, payment));
    }

    private InvoiceSaveResult<T1, T2> persist(InvoiceSaveCommand<T1> command,
                                              InvoicePaymentTerms payment)
            throws DaoException {
        int invoiceNumber = command.updating()
                ? command.existingInvoiceId()
                : numberAllocator.next(documentType);
        List<T1> persistedLines = InvoiceLineAssembler.assemble(
                command.lines(), invoiceNumber, invoiceFactory::object_TableData);
        T3 party = invoiceFactory.objectName(command.partyId(), command.partyName());
        Treasury treasury = treasuryLookup.find(command.treasuryName());
        Employees delegate = documentType.hasDelegate()
                ? delegateLookup.find(command.delegateName())
                : null;
        if (treasury == null) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.TREASURY,
                    "الخزينة المحددة غير موجودة");
        }
        if (documentType.hasDelegate() && delegate == null) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.DELEGATE,
                    "المندوب المحدد غير موجود");
        }
        DiscountType discountType = command.discountType() == null
                ? DiscountType.AMOUNT
                : command.discountType();
        T2 invoice = invoiceFactory.object_Totals(
                invoiceNumber, payment.invoiceType(), command.invoiceDate().toString(),
                payment.subtotal(), payment.discount(), discountType, payment.net(),
                payment.paid(), payment.remaining(), command.notes(), party,
                new Stock(DefaultStock.ID), delegate, persistedLines, treasury);

        DaoList<T2> dao = repository.totalDao();
        int affected = command.updating() ? dao.update(invoice) : dao.insert(invoice);
        if (affected != 1) {
            throw new DaoException("لم يتم حفظ الفاتورة؛ لم تؤثر العملية في سجل واحد");
        }
        return new InvoiceSaveResult<>(invoiceNumber, command.updating(), invoice,
                payment, persistedLines);
    }
}
