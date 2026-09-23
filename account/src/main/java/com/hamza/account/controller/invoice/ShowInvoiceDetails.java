package com.hamza.account.controller.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.company.CompanyService;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.invoice.InvoicePrintCurrency;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import com.hamza.account.features.invoice.InvoicePrintDocumentBuilder;
import com.hamza.account.features.party.currency.PartyCurrencies;
import com.hamza.account.features.party.statement.MovementBalance;
import com.hamza.account.features.party.statement.PartyMovementKind;
import com.hamza.account.features.party.statement.PartyStatementService;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.interfaces.api.InvoiceHeaderView;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Company;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.DaoException;

import java.util.HashMap;
import java.util.List;

public class ShowInvoiceDetails {

    /**
     * The header of one document, for printing. Everything the per-family
     * {@link TotalsDataInterface} had to be asked for is already resolved on the view,
     * so no caller has to name the concrete totals type to build this.
     */
    public static HashMap<String, Object> invoiceDetails(InvoiceHeaderView header) {
        BaseTotals t2 = header.totals();
        double paid = t2.getPaid();
        double total = t2.getTotal();
        double discount = t2.getDiscount();
        double rest = MoneyMath.asDouble(MoneyMath.subtract(
                MoneyMath.subtract(MoneyMath.decimal(total), MoneyMath.decimal(discount)),
                MoneyMath.decimal(paid)));
        var type = t2.getInvoiceType().getType();
        if (type == null) type = InvoiceType.CASH.getType();

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put(ShowInvoiceNameData.ID, t2.getId());
        hashMap.put(ShowInvoiceNameData.NAME, header.partyName());
        hashMap.put(ShowInvoiceNameData.DATE, t2.getDate());
        hashMap.put(ShowInvoiceNameData.STOCK, t2.getStockData().getName());
        hashMap.put(ShowInvoiceNameData.PAID, paid);
        hashMap.put(ShowInvoiceNameData.DISCOUNT, discount);
        hashMap.put(ShowInvoiceNameData.TOTAL, total);
        hashMap.put(ShowInvoiceNameData.REST, rest);
        hashMap.put(ShowInvoiceNameData.TYPE, type);
        hashMap.put(ShowInvoiceNameData.DATE_INSERT, header.dateInsert());
        return hashMap;
    }

    /**
     * What the upright invoice page prints, for a saved document: its header as read back from
     * the database, the company's letterhead, and on a deferred document the party's balance
     * either side of it. Both the invoice screen after a save and the saved-invoice screen print
     * through here, so the two papers cannot differ.
     */
    public static InvoicePrintDocument printDocument(InvoiceHeaderView header, DocumentType type,
                                                     List<ModelPrintInvoice> lines, String printedAt)
            throws DaoException {
        PartyStatementService statements = new PartyStatementService();
        InvoicePrintDocumentBuilder builder = new InvoicePrintDocumentBuilder(
                ShowInvoiceDetails::letterhead,
                (kind, partyId, isReturn, number) -> {
                    MovementBalance balance = balanceAfter(statements, kind, partyId, isReturn, number);
                    return balance == null ? null : balance.base();
                },
                (kind, partyId, isReturn, number) -> {
                    MovementBalance balance = balanceAfter(statements, kind, partyId, isReturn, number);
                    return balance == null ? null : balance.own();
                },
                (documentType, number, partyId) -> InvoicePrintCurrency.read(PartyCurrencies.jdbc(),
                        currencyCatalogue(), documentType, number, partyId));
        return builder.build(type, header.totals(), header.partyName(), header.partyId(),
                header.delegateName(), header.sourceInvoiceNumber(), header.returnReason(),
                lines, printedAt);
    }

    private static MovementBalance balanceAfter(PartyStatementService statements, PartyKind kind, int partyId,
                                                boolean isReturn, int number) throws DaoException {
        return statements.balanceAfterMovement(kind, partyId,
                isReturn ? PartyMovementKind.RETURN : PartyMovementKind.INVOICE, number);
    }

    private static InvoicePrintCurrency.Catalogue currencyCatalogue() {
        CurrencyService currencies = new CurrencyService();
        return new InvoicePrintCurrency.Catalogue() {
            @Override
            public Currency find(int currencyId) throws DaoException {
                return currencies.find(currencyId);
            }

            @Override
            public Currency base() throws DaoException {
                return currencies.base();
            }
        };
    }

    private static InvoicePrintDocument.Letterhead letterhead() throws DaoException {
        Company company = new CompanyService(DaoFactory.INSTANCE).load();
        return new InvoicePrintDocument.Letterhead(company.getName(), company.getAddress(),
                company.getTel(), company.getCommercial(), company.getTax(), company.getImage());
    }
}
