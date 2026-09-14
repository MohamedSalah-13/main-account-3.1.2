package com.hamza.account.features.shift;

/**
 * What the cashier's "counted cash" field holds after the shift screen reloads.
 * <p>
 * The screen reloads on its own - on every {@code ShiftsChanged} from another workstation, and
 * again inside the close confirmation - so a reload must never take away a figure the cashier
 * typed. Under blind close it used to: the field was cleared on every reload, so another till
 * opening a shift wiped a drawer count half entered, and cancelling the confirmation left the
 * cashier counting again. Only text the screen wrote itself, or text typed against a different
 * shift, may be replaced.
 */
public final class CloseBalanceEntry {

    private CloseBalanceEntry() {
    }

    /**
     * @param reconciles  the till reconciles cash, so the cashier enters a count
     * @param blindClose  the expected figure is hidden, so nothing may be suggested
     * @param openBalance the shift's opening balance, the suggestion when one is allowed
     * @param currentText what the field holds now
     * @param lastWritten what the screen itself last put there, or null
     * @param sameShift   the field was last filled for this same shift
     * @return the text the field should hold
     */
    public static String textAfterReload(boolean reconciles, boolean blindClose, String openBalance,
                                         String currentText, String lastWritten, boolean sameShift) {
        if (!reconciles) return openBalance;
        boolean typedByCashier = sameShift && currentText != null && !currentText.isBlank()
                && !currentText.equals(lastWritten);
        if (typedByCashier) return currentText;
        return blindClose ? "" : openBalance;
    }
}
