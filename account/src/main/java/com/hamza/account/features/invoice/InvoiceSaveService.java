package com.hamza.account.features.invoice;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.document.DocumentType;
import com.hamza.account.document.DocumentWriteGuard;
import com.hamza.account.features.delegate.DelegateDiscountGuard;
import com.hamza.account.features.returns.JdbcReturnableRepository;
import com.hamza.account.features.returns.ReturnCostResolver;
import com.hamza.account.features.returns.ReturnGuard;
import com.hamza.account.features.returns.ReturnPolicy;
import com.hamza.account.features.returns.ReturnSourceWriter;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.stockledger.StockMovementAssembler;
import com.hamza.account.features.stockledger.StockMovementDao;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.account.features.shift.ShiftAttributionWriter;
import com.hamza.account.features.shift.JdbcShiftCashEffectReader;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.document.InvoiceBuy;
import com.hamza.account.document.TotalsAndPurchaseList;
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
    private final InvoiceStockGuard stockGuard;
    private final ReturnGuard returnGuard;
    private final ReturnSourceWriter returnSourceWriter;
    private final ReturnCostResolver returnCostResolver;
    private final InvoiceLookup<Treasury> treasuryLookup;
    private final InvoiceLookup<Employees> delegateLookup;
    private final StockMovementDao stockMovementDao;
    private final ShiftGate shiftGate;
    private final ShiftAttributionWriter shiftAttribution;
    private final ShiftCashLedger shiftCashLedger;
    private final JdbcShiftCashEffectReader shiftEffectReader;
    private final ChangeAnnouncer changeAnnouncer;
    private final InvoiceWalletFee walletFee;
    private final DelegateDiscountGuard discountGuard;
    private final InvoicePartyCurrency partyCurrency;
    private final InvoicePriceTier priceTier;

    /**
     * Built by the {@link com.hamza.account.interfaces.api.DataInterface} implementation
     * itself, from its own concrete fields - not from the interface, which the screens
     * above it now hold through wildcards. Taking the two collaborators directly is
     * what keeps this class fully generic while its callers name nothing.
     */
    public InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                              TotalsAndPurchaseList<T1, T2> repository,
                              DocumentType documentType,
                              InvoiceLookup<Treasury> treasuryLookup,
                              InvoiceLookup<Employees> delegateLookup) {
        this(invoiceFactory, repository,
                documentType, Clock.systemDefaultZone(),
                new JdbcInvoiceNumberAllocator(), InvoiceTransactionExecutor.jdbc(),
                new InvoiceStockGuard(documentType,
                        new JdbcInvoiceStockRepository()),
                // The policy is read here, at the composition root, rather than inside
                // ReturnGuard - the guard stays a pure decision over an injected policy,
                // which is what lets ReturnGuardTest drive both settings without touching
                // machine-wide Preferences.
                new ReturnGuard(new JdbcReturnableRepository(), InvoiceSaveService::returnPolicy),
                new ReturnSourceWriter(),
                new ReturnCostResolver(new JdbcReturnableRepository()),
                treasuryLookup, delegateLookup, new StockMovementDao(), ShiftGate.jdbc(),
                ShiftAttributionWriter.jdbc(), ShiftCashLedger.jdbc(), new JdbcShiftCashEffectReader(),
                ChangeAnnouncer.jdbc(), InvoiceWalletFee.jdbc(), DelegateDiscountGuard.jdbc(),
                InvoicePartyCurrency.jdbc(), InvoicePriceTier.jdbc());
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceStockGuard stockGuard,
                       ReturnGuard returnGuard,
                       ReturnSourceWriter returnSourceWriter,
                       ReturnCostResolver returnCostResolver,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup,
                       StockMovementDao stockMovementDao) {
        this(invoiceFactory, repository, documentType, clock, numberAllocator, transactions,
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver,
                treasuryLookup, delegateLookup, stockMovementDao, ChangeAnnouncer.disabled());
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceStockGuard stockGuard,
                       ReturnGuard returnGuard,
                       ReturnSourceWriter returnSourceWriter,
                       ReturnCostResolver returnCostResolver,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup,
                       StockMovementDao stockMovementDao,
                       ChangeAnnouncer changeAnnouncer) {
        this(invoiceFactory, repository, documentType, clock, numberAllocator, transactions,
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver,
                treasuryLookup, delegateLookup, stockMovementDao, ShiftGate.disabled(),
                ShiftAttributionWriter.disabled(), ShiftCashLedger.disabled(), null,
                changeAnnouncer);
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceStockGuard stockGuard,
                       ReturnGuard returnGuard,
                       ReturnSourceWriter returnSourceWriter,
                       ReturnCostResolver returnCostResolver,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup,
                       StockMovementDao stockMovementDao,
                       ShiftGate shiftGate) {
        this(invoiceFactory, repository, documentType, clock, numberAllocator, transactions,
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver,
                treasuryLookup, delegateLookup, stockMovementDao, shiftGate,
                ShiftAttributionWriter.disabled(), ShiftCashLedger.disabled(), null);
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceStockGuard stockGuard,
                       ReturnGuard returnGuard,
                       ReturnSourceWriter returnSourceWriter,
                       ReturnCostResolver returnCostResolver,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup,
                       StockMovementDao stockMovementDao,
                       ShiftGate shiftGate,
                       ShiftAttributionWriter shiftAttribution) {
        this(invoiceFactory, repository, documentType, clock, numberAllocator, transactions,
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver, treasuryLookup,
                delegateLookup, stockMovementDao, shiftGate, shiftAttribution,
                ShiftCashLedger.disabled(), null);
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceStockGuard stockGuard,
                       ReturnGuard returnGuard,
                       ReturnSourceWriter returnSourceWriter,
                       ReturnCostResolver returnCostResolver,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup,
                       StockMovementDao stockMovementDao,
                       ShiftGate shiftGate,
                       ShiftAttributionWriter shiftAttribution,
                       ShiftCashLedger shiftCashLedger,
                       JdbcShiftCashEffectReader shiftEffectReader) {
        this(invoiceFactory, repository, documentType, clock, numberAllocator, transactions,
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver, treasuryLookup,
                delegateLookup, stockMovementDao, shiftGate, shiftAttribution, shiftCashLedger,
                shiftEffectReader, ChangeAnnouncer.disabled());
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceStockGuard stockGuard,
                       ReturnGuard returnGuard,
                       ReturnSourceWriter returnSourceWriter,
                       ReturnCostResolver returnCostResolver,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup,
                       StockMovementDao stockMovementDao,
                       ShiftGate shiftGate,
                       ShiftAttributionWriter shiftAttribution,
                       ShiftCashLedger shiftCashLedger,
                       JdbcShiftCashEffectReader shiftEffectReader,
                       ChangeAnnouncer changeAnnouncer) {
        this(invoiceFactory, repository, documentType, clock, numberAllocator, transactions,
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver, treasuryLookup,
                delegateLookup, stockMovementDao, shiftGate, shiftAttribution, shiftCashLedger,
                shiftEffectReader, changeAnnouncer, InvoiceWalletFee.none());
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceStockGuard stockGuard,
                       ReturnGuard returnGuard,
                       ReturnSourceWriter returnSourceWriter,
                       ReturnCostResolver returnCostResolver,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup,
                       StockMovementDao stockMovementDao,
                       ShiftGate shiftGate,
                       ShiftAttributionWriter shiftAttribution,
                       ShiftCashLedger shiftCashLedger,
                       JdbcShiftCashEffectReader shiftEffectReader,
                       ChangeAnnouncer changeAnnouncer,
                       InvoiceWalletFee walletFee) {
        this(invoiceFactory, repository, documentType, clock, numberAllocator, transactions,
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver, treasuryLookup,
                delegateLookup, stockMovementDao, shiftGate, shiftAttribution, shiftCashLedger,
                shiftEffectReader, changeAnnouncer, walletFee, DelegateDiscountGuard.none());
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceStockGuard stockGuard,
                       ReturnGuard returnGuard,
                       ReturnSourceWriter returnSourceWriter,
                       ReturnCostResolver returnCostResolver,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup,
                       StockMovementDao stockMovementDao,
                       ShiftGate shiftGate,
                       ShiftAttributionWriter shiftAttribution,
                       ShiftCashLedger shiftCashLedger,
                       JdbcShiftCashEffectReader shiftEffectReader,
                       ChangeAnnouncer changeAnnouncer,
                       InvoiceWalletFee walletFee,
                       DelegateDiscountGuard discountGuard) {
        this(invoiceFactory, repository, documentType, clock, numberAllocator, transactions,
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver, treasuryLookup,
                delegateLookup, stockMovementDao, shiftGate, shiftAttribution, shiftCashLedger,
                shiftEffectReader, changeAnnouncer, walletFee, discountGuard, InvoicePartyCurrency.none());
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceStockGuard stockGuard,
                       ReturnGuard returnGuard,
                       ReturnSourceWriter returnSourceWriter,
                       ReturnCostResolver returnCostResolver,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup,
                       StockMovementDao stockMovementDao,
                       ShiftGate shiftGate,
                       ShiftAttributionWriter shiftAttribution,
                       ShiftCashLedger shiftCashLedger,
                       JdbcShiftCashEffectReader shiftEffectReader,
                       ChangeAnnouncer changeAnnouncer,
                       InvoiceWalletFee walletFee,
                       DelegateDiscountGuard discountGuard,
                       InvoicePartyCurrency partyCurrency) {
        this(invoiceFactory, repository, documentType, clock, numberAllocator, transactions,
                stockGuard, returnGuard, returnSourceWriter, returnCostResolver, treasuryLookup,
                delegateLookup, stockMovementDao, shiftGate, shiftAttribution, shiftCashLedger,
                shiftEffectReader, changeAnnouncer, walletFee, discountGuard, partyCurrency,
                InvoicePriceTier.none());
    }

    InvoiceSaveService(InvoiceBuy<T1, T2, T3, T4> invoiceFactory,
                       TotalsAndPurchaseList<T1, T2> repository,
                       DocumentType documentType, Clock clock,
                       InvoiceNumberAllocator numberAllocator,
                       InvoiceTransactionExecutor transactions,
                       InvoiceStockGuard stockGuard,
                       ReturnGuard returnGuard,
                       ReturnSourceWriter returnSourceWriter,
                       ReturnCostResolver returnCostResolver,
                       InvoiceLookup<Treasury> treasuryLookup,
                       InvoiceLookup<Employees> delegateLookup,
                       StockMovementDao stockMovementDao,
                       ShiftGate shiftGate,
                       ShiftAttributionWriter shiftAttribution,
                       ShiftCashLedger shiftCashLedger,
                       JdbcShiftCashEffectReader shiftEffectReader,
                       ChangeAnnouncer changeAnnouncer,
                       InvoiceWalletFee walletFee,
                       DelegateDiscountGuard discountGuard,
                       InvoicePartyCurrency partyCurrency,
                       InvoicePriceTier priceTier) {
        this.priceTier = priceTier;
        this.invoiceFactory = invoiceFactory;
        this.repository = repository;
        this.documentType = documentType;
        this.clock = clock;
        this.numberAllocator = numberAllocator;
        this.transactions = transactions;
        this.stockGuard = stockGuard;
        this.returnGuard = returnGuard;
        this.returnSourceWriter = returnSourceWriter;
        this.returnCostResolver = returnCostResolver;
        this.treasuryLookup = treasuryLookup;
        this.delegateLookup = delegateLookup;
        this.stockMovementDao = stockMovementDao;
        this.shiftGate = shiftGate;
        this.shiftAttribution = shiftAttribution;
        this.shiftCashLedger = shiftCashLedger;
        this.shiftEffectReader = shiftEffectReader;
        this.changeAnnouncer = changeAnnouncer;
        this.walletFee = walletFee;
        this.discountGuard = discountGuard;
        this.partyCurrency = partyCurrency;
    }

    /**
     * The two return settings, read here rather than inside the guard - see the constructor - and
     * read at every save rather than once: a screen stays open across a change of them, so reading
     * once meant turning "a return must name an invoice" on had no effect until every open invoice
     * window had been closed and reopened.
     */
    /** The sentence a refusal reads, from the bundles - never a literal written here. */
    private static String text(String key, Object... args) {
        return com.hamza.controlsfx.language.LanguageManager.getInstance().getString(key, args);
    }

    private static ReturnPolicy returnPolicy() {
        if (PropertiesName.getReturnRequireSourceInvoice()) {
            return ReturnPolicy.requiringSource();
        }
        double limit = PropertiesName.getReturnFreeLimit();
        return limit > 0 ? ReturnPolicy.cappingFreeReturns(limit) : ReturnPolicy.DEFAULT;
    }

    public InvoiceSaveResult save(InvoiceSaveCommand command) throws DaoException {
        if (command == null) {
            throw new DaoException("The invoice save was given no command");
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
                command.invoiceType(), lineTotals.netAmount(),
                command.invoiceDiscount(), command.enteredPaid());
        return transactions.execute(() -> persist(command, payment, lineTotals));
    }

    private InvoiceSaveResult persist(InvoiceSaveCommand command,
                                      InvoicePaymentTerms typedPayment,
                                      InvoiceLineTotals typedTotals)
            throws DaoException {
        int existingNumber = command.updating() ? command.existingInvoiceId() : 0;
        // The document's currency and its rate come first (V82, V83 - docs/currency-plan.md §14, §15):
        // everything after this line reads base figures, and a document typed in its party's currency
        // has none until they are worked out here. Before the number is allocated - no rate is a
        // refusal, and the counter does not roll back.
        InvoicePartyCurrency.Rate currency = partyCurrency.rateFor(documentType, command.partyId(),
                command.invoiceDate(), existingNumber, command.sourceInvoiceNumber(),
                command.documentCurrencyId());
        List<? extends BasePurchasesAndSales> rows = command.lines();
        InvoiceLineTotals lineTotals = typedTotals;
        InvoicePaymentTerms payment = typedPayment;
        if (currency.written()) {
            List<T1> baseRows = ForeignDocumentLines.toBase(rows, currency.rate(),
                    partyCurrency.sourceLines(documentType, command.sourceInvoiceNumber()),
                    invoiceFactory::object_TableData);
            lineTotals = InvoiceLineTotals.from(baseRows);
            payment = ForeignDocumentFigures.header(typedPayment, lineTotals.netAmount(), currency.rate(),
                    partyCurrency.returnShare(documentType, command.sourceInvoiceNumber(),
                            lineTotals.netAmount()));
            rows = baseRows;
        }

        stockGuard.validate(command);
        returnGuard.validate(documentType, command.sourceInvoiceNumber(), existingNumber,
                payment.invoiceType(), command.partyId(), rows);
        returnGuard.validateDiscount(documentType, command.sourceInvoiceNumber(),
                payment.subtotal(), payment.discount());
        Employees delegate = documentType.hasDelegate()
                ? delegateLookup.find(command.delegateName())
                : null;
        // A delegate's discount ceiling (V73) judges the lines' discounts and the header's
        // together: a ceiling on the header alone is walked round through a line's discount
        // column. Asked before the number is allocated - the counter does not roll back, so a
        // refusal after it would leave a hole in the numbering for every refused attempt.
        if (delegate != null && discountGuard.refuses(documentType, delegate.getId(),
                lineTotals.grossAmount(), lineTotals.discountAmount(), payment.discountAmount())) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.DISCOUNT,
                    DelegateDiscountGuard.REFUSAL_KEY);
        }
        // The price tier the document is priced at, and every line's list price (V84,
        // docs/pricing-and-offers-plan.md ق-س٢ and ق-س٤) - judged on the lines as they will be
        // stored, and before the number is allocated, as the discount above is.
        Integer tierToStore = priceTier.decide(documentType, command.partyId(), existingNumber,
                command.priceTierId());
        priceTier.requireListPrices(documentType, existingNumber, rows);
        // Before the number is allocated - the counter does not roll back - as the discount above is.
        Treasury treasury = treasuryLookup.find(command.treasuryName());
        if (treasury == null) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.TREASURY,
                    text("invoice.save.error.treasury"));
        }
        requireTreasuryTakes(treasury, currency);
        int invoiceNumber = command.updating()
                ? command.existingInvoiceId()
                : numberAllocator.next(documentType);
        List<T1> persistedLines = InvoiceLineAssembler.assemble(
                rows, invoiceNumber, invoiceFactory::object_TableData);
        returnCostResolver.apply(documentType, command.sourceInvoiceNumber(), existingNumber,
                rows, persistedLines);
        T3 party = invoiceFactory.objectName(command.partyId(), command.partyName());
        if (documentType.hasDelegate() && delegate == null) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.DELEGATE,
                    text("invoice.save.error.delegate"));
        }
        DiscountType discountType = command.discountType() == null
                ? DiscountType.AMOUNT
                : command.discountType();
        T2 invoice = invoiceFactory.object_Totals(
                invoiceNumber, payment.invoiceType(), command.invoiceDate().toString(),
                payment.subtotal(), payment.discount(), discountType, payment.net(),
                payment.paid(), payment.remaining(), command.notes(), party,
                new Stock(command.stockId()), delegate, persistedLines, treasury);
        if (command.updating()) {
            invoice.setUpdated_at(DocumentWriteGuard.requireEditVersion(
                    command.expectedUpdatedAt(), documentType));
        }

        int userId = invoice.getUsers() == null ? 0 : invoice.getUsers().getId();
        ShiftCashEffect previous = command.updating() && shiftEffectReader != null
                ? shiftEffectReader.document(documentType, invoiceNumber) : null;
        var previousShiftId = previous == null ? java.util.OptionalInt.empty()
                : shiftGate.requireCashCorrection(userId, previous.treasuryId(),
                    previous.income().add(previous.output()).abs(), previous.originalShiftId());
        var shiftId = previous == null
                ? shiftGate.requireCashAction(userId, treasury.getId(),
                    com.hamza.account.finance.MoneyMath.decimal(payment.paid()))
                : shiftGate.requireCashCorrection(userId, treasury.getId(),
                    com.hamza.account.finance.MoneyMath.decimal(payment.paid()), previous.originalShiftId());

        DaoList<T2> dao = repository.totalDao();
        int affected = command.updating() ? dao.update(invoice) : dao.insert(invoice);
        if (affected != 1) {
            throw new DaoException("The invoice was not saved: the write did not affect exactly one row");
        }
        partyCurrency.write(documentType, invoiceNumber, currency, typedPayment);
        priceTier.write(documentType, invoiceNumber, tierToStore);
        if (!command.updating()) {
            shiftAttribution.assignDocument(documentType, invoiceNumber, shiftId);
        }
        ShiftCashSource cashSource = ShiftCashSource.document(documentType);
        var paid = com.hamza.account.finance.MoneyMath.decimal(payment.paid());
        ShiftCashEffect current = documentType.cashSign() > 0
                ? ShiftCashEffect.incoming(cashSource, invoiceNumber, treasury.getId(),
                    shiftId.isPresent() ? shiftId.getAsInt() : null, paid)
                : ShiftCashEffect.outgoing(cashSource, invoiceNumber, treasury.getId(),
                    shiftId.isPresent() ? shiftId.getAsInt() : null, paid);
        if (command.updating() && previous != null) {
            shiftCashLedger.updated(previousShiftId, shiftId, userId, previous, current,
                    command.correctionReason());
        } else {
            shiftCashLedger.created(shiftId, userId, current);
        }
        // The wallet keeps its percentage of whatever cash this document moved, and that is an
        // expense on the same treasury - written, rewritten or removed here so it commits with
        // the document. See InvoiceWalletFee.
        walletFee.sync(documentType, invoiceNumber, treasury.getId(), treasury.getFeePercent(),
                command.invoiceDate(), paid, previous, shiftId, command.correctionReason());
        if (documentType.isReturn()) {
            returnSourceWriter.writeSource(documentType, invoiceNumber,
                    command.sourceInvoiceNumber(), command.returnReason());
        }
        writeStockMovements(invoiceNumber, command, persistedLines,
                invoice.getUsers() == null ? null : invoice.getUsers().getId());
        changeAnnouncer.announce(new InvoiceSaved(documentType.side()));
        return new InvoiceSaveResult(invoiceNumber, command.updating(), invoice,
                payment, persistedLines);
    }

    /**
     * Where a document's cash may go (docs/currency-plan.md §11 ق-ب٦, §15 ق-د٦): a treasury in the base
     * takes any document; one in a foreign currency takes only a document written in that currency -
     * the amount it holds is then exactly what was typed - and never a wallet fee, which is an expense,
     * and an expense on a treasury in a foreign currency is refused.
     */
    private static void requireTreasuryTakes(Treasury treasury, InvoicePartyCurrency.Rate currency)
            throws InvoiceValidationException {
        Integer treasuryCurrency = treasury.getCurrencyId();
        if (treasuryCurrency == null) {
            return;
        }
        if (!currency.written()) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.TREASURY,
                    com.hamza.account.features.treasury.TreasuryCurrencyGuard.refusal(treasury.getName()));
        }
        var language = com.hamza.controlsfx.language.LanguageManager.getInstance();
        if (treasuryCurrency != currency.currency().id()) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.TREASURY,
                    language.getString("party.currency.error.treasury", treasury.getName()));
        }
        if (treasury.getFeePercent() != null && treasury.getFeePercent().signum() > 0) {
            throw new InvoiceValidationException(InvoiceSaveValidator.Target.TREASURY,
                    language.getString("party.currency.error.fee", treasury.getName()));
        }
    }

    /**
     * The dual-write half of a save: the invoice itself is already committed to its own
     * table by the time this runs, still inside the same transaction (see
     * {@code docs/erp-roadmap.md} §8.3-8.4). Deleting this document's existing movements
     * before writing the current set is what makes an edit correct without recomputing
     * anything - see {@link StockMovementDao#deleteByReference} for why that is a
     * deliberate, temporary exception to the ledger otherwise being append-only.
     */
    private void writeStockMovements(int invoiceNumber, InvoiceSaveCommand command,
                                     List<T1> persistedLines, Integer userId) throws DaoException {
        String referenceType = StockMovementAssembler.referenceTypeFor(documentType);
        stockMovementDao.deleteByReference(referenceType, invoiceNumber);
        stockMovementDao.insertBatch(StockMovementAssembler.assemble(
                documentType, command.stockId(), invoiceNumber, command.invoiceDate(),
                persistedLines, userId));
    }
}
