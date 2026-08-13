package com.hamza.account.features.invoice;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** JavaFX-free validation for the common purchase/sales invoice editor. */
public final class InvoiceSaveValidator {

    private InvoiceSaveValidator() {
    }

    public static Optional<Problem> firstProblem(
            int lineCount,
            boolean hasInvalidLine,
            LocalDate invoiceDate,
            LocalDate today,
            boolean delegateRequired,
            boolean delegateSelected,
            boolean treasurySelected,
            int accountId) {
        Objects.requireNonNull(today, "today");

        if (lineCount <= 0) {
            return problem(Target.LINES, "لا يمكن حفظ فاتورة بدون أصناف");
        }
        if (hasInvalidLine) {
            return problem(Target.LINES, "لا يمكن حفظ فاتورة بسعر أو كمية صفر أو أقل");
        }
        if (invoiceDate == null) {
            return problem(Target.DATE, "من فضلك حدد تاريخ الفاتورة");
        }
        if (invoiceDate.isAfter(today)) {
            return problem(Target.DATE, "لا يمكن حفظ فاتورة بتاريخ بعد تاريخ اليوم");
        }
        if (delegateRequired && !delegateSelected) {
            return problem(Target.DELEGATE, "من فضلك حدد المندوب");
        }
        if (!treasurySelected) {
            return problem(Target.TREASURY, "من فضلك حدد الخزينة");
        }
        if (accountId <= 0) {
            return problem(Target.ACCOUNT, "لا يوجد بيانات الاسم");
        }
        return Optional.empty();
    }

    private static Optional<Problem> problem(Target target, String message) {
        return Optional.of(new Problem(target, message));
    }

    public enum Target {
        LINES,
        DATE,
        DELEGATE,
        TREASURY,
        ACCOUNT
    }

    public record Problem(Target target, String message) {
        public Problem {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(message, "message");
        }
    }
}
