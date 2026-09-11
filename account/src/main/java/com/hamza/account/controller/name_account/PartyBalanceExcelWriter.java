package com.hamza.account.controller.name_account;

import com.hamza.account.features.party.balances.PartyBalanceRow;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;

/**
 * The balances list as a spreadsheet.
 * <p>
 * The rows are whatever the filter matched - read by {@code forPrint} with the filter the table
 * used - rather than the rows the user happened to tick. Ticking nothing used to produce an empty
 * file and a message saying it had saved.
 */
public record PartyBalanceExcelWriter(List<PartyBalanceRow> rows)
        implements WriteExcelInterface<PartyBalanceRow> {

    @Override
    public Object[] columnHeader() {
        var lm = LanguageManager.getInstance();
        return new Object[]{
                lm.getString("code"), lm.getString("name"), lm.getString("column.tel"),
                lm.getString("party.column.area"),
                lm.getString("party.balances.column.period.debit"),
                lm.getString("party.balances.column.period.credit"),
                lm.getString("party.balances.column.balance"),
                lm.getString("party.balances.column.limit"),
                lm.getString("party.balances.column.last")
        };
    }

    @Override
    public Object[] dataRow(PartyBalanceRow row) {
        return new Object[]{
                row.partyId(), row.name(), row.phone(), row.areaName(),
                row.periodDebit(), row.periodCredit(), row.balance(), row.creditLimit(),
                row.lastMovement() == null ? "" : row.lastMovement().toString()
        };
    }

    @Override
    public List<PartyBalanceRow> itemsList() {
        return rows;
    }

    @Override
    public boolean addDataToFile() {
        return true;
    }

    @Override
    public String sheetName() {
        return LanguageManager.getInstance().getString("party.balances.export.sheet");
    }
}
