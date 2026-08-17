package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.controlsfx.database.DaoException;

import java.util.Objects;

/**
 * What the invoice screen shows next to a sale or purchase that has since been
 * (partly) returned - "returned: N of M" needs the same two numbers
 * {@link com.hamza.account.features.returns.ReturnGuard} already computes to decide
 * whether a new return may still be saved, read here the other way around: from the
 * source's own screen, not the return's.
 */
public final class ReturnedStatusService {

    private final ReturnableRepository repository;

    public ReturnedStatusService(ReturnableRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** {@code sourceType} must be {@code SALES} or {@code PURCHASE} - a return has nothing to report on itself. */
    public Status statusOf(DocumentType sourceType, int sourceInvoiceNumber) throws DaoException {
        if (Objects.requireNonNull(sourceType, "sourceType").isReturn()) {
            throw new IllegalArgumentException(sourceType + " is a return, not a source");
        }
        if (sourceInvoiceNumber <= 0) {
            return new Status(0, 0);
        }
        DocumentType returnType = DocumentType.of(sourceType.partyKind(), true);
        double sold = repository.sourceLines(sourceType, sourceInvoiceNumber).stream()
                .mapToDouble(ReturnableRepository.SoldLine::baseQuantity)
                .sum();
        double returned = repository
                .alreadyReturnedBaseQuantities(returnType, sourceInvoiceNumber, 0)
                .values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        return new Status(sold, returned);
    }

    /**
     * @param soldBaseQuantity     everything this document sold or bought, in base units
     * @param returnedBaseQuantity how much of it has been returned so far, in base units
     */
    public record Status(double soldBaseQuantity, double returnedBaseQuantity) {

        public boolean hasAnyReturn() {
            return returnedBaseQuantity > 0.000_001;
        }

        public boolean isFullyReturned() {
            return soldBaseQuantity > 0
                    && returnedBaseQuantity >= soldBaseQuantity - 0.000_001;
        }
    }
}
