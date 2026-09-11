package com.hamza.account.features.party.payment;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Putting a payment against a particular invoice.
 * <p>
 * Two things only: offering the invoices that still owe something, and refusing an allocation
 * larger than what one of them owes. The payment itself is written by
 * {@code AccountCustomerService} / {@code AccountSupplierService} as it always was — this adds
 * a value for {@code numberInv}, not a second way to save a payment.
 * <p>
 * <b>Over-allocation is refused rather than clamped.</b> A user who types 1000 against an
 * invoice owing 600 has either picked the wrong invoice or means to pay on account, and both are
 * answered by saying so. Silently allocating 600 and leaving 400 unexplained is how a ledger
 * ends up with a figure nobody can trace; silently writing 1000 against a 600 invoice makes the
 * ageing report show a negative remainder for ever.
 * <p>
 * An unallocated payment is still perfectly ordinary — {@code numberInv = 0} means "on account",
 * which is what every payment in every existing install says. Nothing here is compulsory.
 */
public final class PartyPaymentAllocationService extends AbstractDao<OpenInvoice> {

    /** {@code numberInv} for a payment that settles no particular invoice. */
    public static final long ON_ACCOUNT = 0;

    /**
     * The party's unsettled invoices.
     *
     * @param kind      which ledger
     * @param partyId   whose invoices
     * @param editingMovement the movement being edited, so its own allocation is not counted
     *                        against the invoice it is allocated to; {@code 0} for a new payment
     */
    public List<OpenInvoice> openInvoices(PartyKind kind, int partyId, long editingMovement)
            throws DaoException {
        requireShow(kind);
        return queryForObjects(OpenInvoiceQuery.openInvoicesSql(kind), this::map,
                partyId, editingMovement, partyId);
    }

    /**
     * Refuses an allocation the invoice cannot absorb.
     * <p>
     * Read inside the caller's transaction, not from the list the screen is holding: a second
     * till can settle the same invoice while a dialog is open. {@code AbstractDao} helpers join
     * the transaction already open on this thread, so calling this from inside
     * {@code TransactionTemplate.execute} is all that is needed.
     *
     * @throws UserValidationException if the invoice no longer owes that much — a message for the
     *                                user, not a reference code: they chose the number and they
     *                                can choose another
     */
    public void requireAllocationFits(PartyKind kind, int partyId, long invoiceNumber,
                                      BigDecimal amount, long editingMovement) throws DaoException {
        if (invoiceNumber == ON_ACCOUNT || amount == null || amount.signum() <= 0) {
            return;
        }
        BigDecimal remaining = remainingOn(kind, partyId, invoiceNumber, editingMovement);
        if (remaining == null) {
            throw new UserValidationException(LanguageManager.getInstance()
                    .getString("party.payment.allocation.invoice.missing", invoiceNumber));
        }
        if (amount.compareTo(remaining) > 0) {
            throw new UserValidationException(LanguageManager.getInstance()
                    .getString("party.payment.allocation.too.large", invoiceNumber, remaining));
        }
    }

    /** What one invoice still owes, or null if the party has no such invoice. */
    public BigDecimal remainingOn(PartyKind kind, int partyId, long invoiceNumber,
                                  long editingMovement) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    OpenInvoiceQuery.remainingOnInvoiceSql(kind))) {
                statement.setInt(1, partyId);
                statement.setLong(2, editingMovement);
                statement.setInt(3, partyId);
                statement.setLong(4, invoiceNumber);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getBigDecimal("remaining") : null;
                }
            }
        });
    }

    private static void requireShow(PartyKind kind) throws DaoException {
        AuthorizationGuard.require(PermAccountAndNameInt.forParty(kind).showAccounts());
    }

    @Override
    public OpenInvoice map(ResultSet rs) throws DaoException {
        try {
            return new OpenInvoice(rs.getLong("invoice_number"),
                    rs.getDate("invoice_date").toLocalDate(),
                    rs.getBigDecimal("net"), rs.getBigDecimal("settled"), rs.getString("notes"));
        } catch (SQLException e) {
            throw new DaoException("Could not map an open invoice", e);
        }
    }

    @Override
    public List<OpenInvoice> loadAll() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int insert(OpenInvoice value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(OpenInvoice value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteById(int id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public OpenInvoice getDataById(int id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] getData(OpenInvoice value) {
        throw new UnsupportedOperationException();
    }
}
