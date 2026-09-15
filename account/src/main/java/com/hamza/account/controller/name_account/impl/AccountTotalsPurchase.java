package com.hamza.account.controller.name_account.impl;

import com.hamza.account.controller.model.AccountCard;
import com.hamza.account.controller.name_account.AccountDetailsInterface;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.service.PurchaseReService;
import com.hamza.account.service.PurchaseService;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;

import java.util.List;

/**
 * The supplier side: a document row is a purchase invoice or a purchase return, and its
 * lines come from {@code purchase} or {@code purchase_re}.
 * <p>
 * The two methods that assembled the statement are gone — see {@link AccountDetailsInterface}.
 */
public class AccountTotalsPurchase implements AccountDetailsInterface {

    private final PurchaseService purchaseService = ServiceRegistry.get(PurchaseService.class);
    private final PurchaseReService purchaseReService = ServiceRegistry.get(PurchaseReService.class);

    public static <T extends BasePurchasesAndSales> List<AccountCard> purchaseItems(List<T> lines) {
        String count = LanguageManager.getInstance().getString("party.statement.line.count");
        return lines.stream().map(line -> {
            var card = new AccountCard();
            card.setNotes(count + " ( " + formatQuantity(line.getQuantity()) + " ) "
                    + line.getItems().getNameItem() + " — "
                    + Columns.money(java.math.BigDecimal.valueOf(line.getTotal())));
            return card;
        }).toList();
    }

    private static String formatQuantity(double quantity) {
        if (quantity == Math.floor(quantity)) {
            return String.valueOf((int) quantity);
        }
        return String.valueOf(quantity);
    }

    @Override
    public List<AccountCard> documentLines(AccountCard row) throws Exception {
        return switch (row.getKind()) {
            case INVOICE -> purchaseItems(purchaseService.fetchByInvoiceNumber(row.getId()));
            case RETURN -> purchaseItems(purchaseReService.fetchByInvoiceNumber(row.getId()));
            default -> List.of();
        };
    }
}
