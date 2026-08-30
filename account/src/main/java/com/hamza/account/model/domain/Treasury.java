package com.hamza.account.model.domain;

import com.hamza.account.model.base.DForColumnTable;
import com.hamza.account.treasury.TreasuryType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A treasury: a cash drawer, an e-wallet or a bank account.
 * <p>
 * {@code amount} is the <b>opening balance</b> and nothing else - what the vessel
 * held before this system knew about it. It is not the current balance and must
 * never be presented as one; that answer is {@code treasury_current_balance},
 * read through {@link com.hamza.account.treasury.TreasuryBalanceSummary}. The
 * column carries the same sentence as a COMMENT since
 * {@code V20__treasury_types.sql}, because the two were confused for as long as
 * they were both called "the amount".
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Treasury extends DForColumnTable {

    private int id;
    private String name;
    /** The opening balance - see the class comment. Never the current balance. */
    private BigDecimal amount = BigDecimal.ZERO;
    private TreasuryType type = TreasuryType.CASH;
    private boolean active = true;
    private int sortOrder;
    private BigDecimal feePercent = BigDecimal.ZERO;
    private LocalDate openingDate;
    private int userId;

    public Treasury(int id) {
        this.id = id;
    }

    public Treasury(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Treasury(int id, String name, BigDecimal amount) {
        this.id = id;
        this.name = name;
        this.amount = amount;
    }

    public Treasury(String name, BigDecimal amount, int userId) {
        this.name = name;
        this.amount = amount;
        this.userId = userId;
    }

    @Override
    public String toString() {
        return name;
    }
}
