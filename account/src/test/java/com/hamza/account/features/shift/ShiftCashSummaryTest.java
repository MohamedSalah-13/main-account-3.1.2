package com.hamza.account.features.shift;

import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.treasury.MovementLabel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ShiftCashSummaryTest {

    private static ShiftCashMovement in(MovementLabel label, double amount) {
        return new ShiftCashMovement(label, bd(amount), BigDecimal.ZERO);
    }

    private static ShiftCashMovement out(MovementLabel label, double amount) {
        return new ShiftCashMovement(label, BigDecimal.ZERO, bd(amount));
    }

    @Test
    void theNamedTotalsAreTakenFromTheirOwnHeadings() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.SALES, 1000),
                out(MovementLabel.SALES_RETURNS, 100),
                out(MovementLabel.EXPENSES, 50),
                in(MovementLabel.DEPOSIT, 200),
                out(MovementLabel.WITHDRAWAL, 30)), bd(500), 7);

        assertMoney(1000, summary.getTotalSales());
        assertMoney(100, summary.getTotalSalesReturns());
        assertMoney(50, summary.getTotalExpenses());
        assertMoney(200, summary.getTotalDeposits());
        assertMoney(30, summary.getTotalWithdrawals());
        assertEquals(7, summary.getInvoicesCount());
        assertMoney(500, summary.getOpenBalance());
    }

    @Test
    @DisplayName("the expected balance counts every heading, not the five with a name")
    void aCustomerCollectionIsPartOfTheExpectedCash() {
        // The defect this class exists for: 5,000 collected against a customer's
        // account reached the till and was counted nowhere, so the cashier finished
        // the day 5,000 over for doing their job.
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.SALES, 1000),
                in(MovementLabel.CUSTOMER_ACCOUNTS, 5000)), BigDecimal.ZERO, 1);

        assertMoney(6000, summary.getExpectedBalance());
        assertMoney(0, summary.calculateDifference(bd(6000)));
    }

    @Test
    void aSupplierPaymentLeavesTheTill() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.SALES, 1000),
                out(MovementLabel.SUPPLIER_ACCOUNTS, 400)), BigDecimal.ZERO, 1);

        assertMoney(600, summary.getExpectedBalance());
    }

    @Test
    void bothSidesOfATransferAreCounted() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.TRANSFER_IN, 300),
                out(MovementLabel.TRANSFER_OUT, 100)), BigDecimal.ZERO, 0);

        assertMoney(200, summary.getExpectedBalance());
        assertMoney(300, summary.getOtherIn());
        assertMoney(100, summary.getOtherOut());
    }

    @Test
    @DisplayName("what the named lines do not carry is shown, not hidden")
    void otherCarriesEveryUnnamedHeading() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.SALES, 1000),
                in(MovementLabel.DEPOSIT, 100),
                in(MovementLabel.CUSTOMER_ACCOUNTS, 500),
                in(MovementLabel.PURCHASE_RETURNS, 50),
                out(MovementLabel.EXPENSES, 20),
                out(MovementLabel.PURCHASES, 200)), BigDecimal.ZERO, 1);

        assertMoney(550, summary.getOtherIn());
        assertMoney(200, summary.getOtherOut());
        assertMoney(1650, summary.getTotalIn());
        assertMoney(220, summary.getTotalOut());
        // Every pound is on one of the six lines.
        assertEquals(summary.getTotalIn(),
                summary.getTotalSales().add(summary.getTotalDeposits()).add(summary.getOtherIn()));
        assertEquals(summary.getTotalOut(), summary.getTotalSalesReturns()
                .add(summary.getTotalExpenses()).add(summary.getTotalWithdrawals()).add(summary.getOtherOut()));
    }

    @Test
    @DisplayName("the opening line belongs to the till, not to the shift")
    void theOpeningBalanceHeadingIsNotAMovement() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.OPENING, 9999),
                in(MovementLabel.SALES, 100)), bd(250), 1);

        assertMoney(100, summary.getTotalIn());
        assertMoney(350, summary.getExpectedBalance());
    }

    @Test
    void aRowTheReaderCouldNotIdentifyIsSkippedRatherThanMiscounted() {
        List<ShiftCashMovement> movements = new ArrayList<>();
        movements.add(null);
        movements.add(in(MovementLabel.SALES, 100));

        assertMoney(100, ShiftCashSummary.summarize(movements, BigDecimal.ZERO, 1).getExpectedBalance());
    }

    @Test
    void aShiftWithNoMovementsExpectsWhatItOpenedWith() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(), bd(750), 0);

        assertMoney(750, summary.getExpectedBalance());
        assertMoney(0, summary.getTotalIn());
        assertMoney(-50, summary.calculateDifference(bd(700)));
    }

    private static BigDecimal bd(double value) { return BigDecimal.valueOf(value); }

    private static void assertMoney(double expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }
}
