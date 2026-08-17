package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * How many returns of one side were entered for each reason over a date range, and
 * what they totalled - the report {@code return_reason} exists to make possible.
 * Thin on purpose: {@link ReturnableRepository#reasonCounts} already answers the
 * question, so this is the validation boundary in front of it, in the shape a
 * service in this codebase is meant to be.
 */
public final class ReturnReasonReportService {

    private final ReturnableRepository repository;

    public ReturnReasonReportService(ReturnableRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<ReturnableRepository.ReasonCount> summarize(
            DocumentType returnType, LocalDate from, LocalDate to) throws DaoException {
        if (!Objects.requireNonNull(returnType, "returnType").isReturn()) {
            throw new IllegalArgumentException(returnType + " is not a return");
        }
        if (from == null || to == null) {
            throw new UserValidationException("من فضلك حدد فترة التقرير");
        }
        if (from.isAfter(to)) {
            throw new UserValidationException("تاريخ البداية يجب أن يسبق تاريخ النهاية");
        }
        return repository.reasonCounts(returnType, from, to);
    }
}
