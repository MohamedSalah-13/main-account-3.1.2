package com.hamza.account.controller.reports;

import com.hamza.account.model.domain.CustomerReceivable;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * The receivables report as a spreadsheet.
 * <p>
 * Written because the screen's Excel button had an empty method body, and its PDF button
 * had the export call commented out above a line that told the user it had saved. Modelled
 * on {@code CustomerPurchasedItemsExcelWriter}, which is the one export on the party side
 * that was already done properly.
 * <p>
 * The headings are the same bundle keys the screen's columns use, so the file and the
 * screen cannot end up describing their columns differently.
 */
@RequiredArgsConstructor
public class CustomerReceivableExcelWriter implements WriteExcelInterface<CustomerReceivable> {

    private final List<CustomerReceivable> rows;

    @Override
    public Object[] columnHeader() {
        var lm = LanguageManager.getInstance();
        return new Object[]{
                lm.getString("report.customer.receivables.col.name"),
                lm.getString("column.tel"),
                lm.getString("report.customer.receivables.col.opening"),
                lm.getString("report.customer.receivables.col.invoices"),
                lm.getString("report.customer.receivables.col.payments"),
                lm.getString("report.customer.receivables.col.total")
        };
    }

    @Override
    public Object[] dataRow(CustomerReceivable row) {
        return new Object[]{
                row.getCustomerName(),
                row.getCustomerPhone(),
                row.getOpeningBalance(),
                row.getInvoicesDebt(),
                row.getTotalPayments(),
                row.getTotalReceivable()
        };
    }

    @Override
    public List<CustomerReceivable> itemsList() {
        return rows;
    }

    @Override
    public boolean addDataToFile() {
        return true;
    }

    @Override
    public String sheetName() {
        return "Receivables";
    }
}
