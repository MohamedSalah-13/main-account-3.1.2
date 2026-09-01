package com.hamza.account.features.shift;

import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.treasury.MovementLabel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShiftCashSummaryTest {

    private static ShiftCashMovement in(MovementLabel label, double amount) {
        return new ShiftCashMovement(label, amount, 0);
    }

    private static ShiftCashMovement out(MovementLabel label, double amount) {
        return new ShiftCashMovement(label, 0, amount);
    }

    @Test
    void theNamedTotalsAreTakenFromTheirOwnHeadings() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.SALES, 1000),
                out(MovementLabel.SALES_RETURNS, 100),
                out(MovementLabel.EXPENSES, 50),
                in(MovementLabel.DEPOSIT, 200),
                out(MovementLabel.WITHDRAWAL, 30)), 500, 7);

        assertEquals(1000, summary.getTotalSales());
        assertEquals(100, summary.getTotalSalesReturns());
        assertEquals(50, summary.getTotalExpenses());
        assertEquals(200, summary.getTotalDeposits());
        assertEquals(30, summary.getTotalWithdrawals());
        assertEquals(7, summary.getInvoicesCount());
        assertEquals(500, summary.getOpenBalance());
    }

    @Test
    @DisplayName("the expected balance counts every heading, not the five with a name")
    void aCustomerCollectionIsPartOfTheExpectedCash() {
        // The defect this class exists for: 5,000 collected against a customer's
        // account reached the till and was counted nowhere, so the cashier finished
        // the day 5,000 over for doing their job.
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.SALES, 1000),
                in(MovementLabel.CUSTOMER_ACCOUNTS, 5000)), 0, 1);

        assertEquals(6000, summary.getExpectedBalance());
        assertEquals(0, summary.calculateDifference(6000));
    }

    @Test
    void aSupplierPaymentLeavesTheTill() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.SALES, 1000),
                out(MovementLabel.SUPPLIER_ACCOUNTS, 400)), 0, 1);

        assertEquals(600, summary.getExpectedBalance());
    }

    @Test
    void bothSidesOfATransferAreCounted() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.TRANSFER_IN, 300),
                out(MovementLabel.TRANSFER_OUT, 100)), 0, 0);

        assertEquals(200, summary.getExpectedBalance());
        assertEquals(300, summary.getOtherIn());
        assertEquals(100, summary.getOtherOut());
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
                out(MovementLabel.PURCHASES, 200)), 0, 1);

        assertEquals(550, summary.getOtherIn());
        assertEquals(200, summary.getOtherOut());
        assertEquals(1650, summary.getTotalIn());
        assertEquals(220, summary.getTotalOut());
        // Every pound is on one of the six lines.
        assertEquals(summary.getTotalIn(),
                summary.getTotalSales() + summary.getTotalDeposits() + summary.getOtherIn());
        assertEquals(summary.getTotalOut(), summary.getTotalSalesReturns()
                + summary.getTotalExpenses() + summary.getTotalWithdrawals() + summary.getOtherOut());
    }

    @Test
    @DisplayName("the opening line belongs to the till, not to the shift")
    void theOpeningBalanceHeadingIsNotAMovement() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(
                in(MovementLabel.OPENING, 9999),
                in(MovementLabel.SALES, 100)), 250, 1);

        assertEquals(100, summary.getTotalIn());
        assertEquals(350, summary.getExpectedBalance());
    }

    @Test
    void aRowTheReaderCouldNotIdentifyIsSkippedRatherThanMiscounted() {
        List<ShiftCashMovement> movements = new ArrayList<>();
        movements.add(null);
        movements.add(in(MovementLabel.SALES, 100));

        assertEquals(100, ShiftCashSummary.summarize(movements, 0, 1).getExpectedBalance());
    }

    @Test
    void aShiftWithNoMovementsExpectsWhatItOpenedWith() {
        ShiftSummary summary = ShiftCashSummary.summarize(List.of(), 750, 0);

        assertEquals(750, summary.getExpectedBalance());
        assertEquals(0, summary.getTotalIn());
        assertEquals(-50, summary.calculateDifference(700));
    }
}
