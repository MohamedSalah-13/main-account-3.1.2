package com.hamza.account.features.delegate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

/**
 * A month's commission, approved. <b>There is no draft</b>: the preview is computed live and
 * writes nothing, and approval creates the run and its lines together - so a run exists only as
 * something somebody decided.
 *
 * @param lines       how many delegates it holds a line for
 * @param total       what it came to
 * @param postedLines how many of its lines have reached an account, by either road
 */
public record CommissionRun(int id, YearMonth period, Status status, String notes, LocalDateTime approvedAt,
                            String approvedBy, String cancelReason, int lines, BigDecimal total, int postedLines) {

    public enum Status {
        APPROVED("commission.run.status.approved"),
        CANCELLED("commission.run.status.cancelled");

        private final String messageKey;

        Status(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }

        public static Status of(String stored) {
            for (Status status : values()) {
                if (status.name().equals(stored)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown commission run status: " + stored);
        }
    }

    public CommissionRun {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(status, "status");
        total = total == null ? BigDecimal.ZERO : total;
    }

    /**
     * Only a run none of whose lines has been posted: a posted commission is an entitlement in
     * somebody's account, and cancelling its run would leave that row belonging to a month that
     * says it was never computed. The trigger on {@code commission_run} refuses the same thing.
     */
    public boolean mayBeCancelled() {
        return status == Status.APPROVED && postedLines == 0;
    }
}
