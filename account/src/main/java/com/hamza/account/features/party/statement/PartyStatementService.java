package com.hamza.account.features.party.statement;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The one place a party statement comes from.
 * <p>
 * <b>What this replaced, and why it is the whole point of the package.</b>
 * {@code AccountDetailsWithItemsController} assembled a statement itself, from four
 * queries: the party's opening balance off their own row, the invoice totals, the return
 * totals, and the payments. So the system held two definitions of what a customer owes —
 * this assembly, and {@code account_customer_table}, the view that already performs exactly
 * that union — and they disagreed. The assembly carried
 * <pre>{@code if (invoiceType == CASH) total = totalAfterDiscount;}</pre>
 * which is the branch {@code V15__return_cash_split.sql} removed from the views and which
 * {@link com.hamza.account.document.DocumentLedgerEffect} exists to state once: a
 * <em>deferred</em> sales return contributed nothing at all to the statement, so a customer
 * who returned 1000 of goods on account still appeared to owe the 1000 — while the totals
 * screen, reading the view, knew they did not. Two screens, two balances.
 * <p>
 * Everything here reads {@code account_customer_table} or
 * {@code account_suppliers_table}. There is no second definition left to disagree with, and
 * {@code PartyStatementAgreesWithLedgerEffectTest} is what keeps it that way.
 * <p>
 * <b>The permission is the statement's own.</b> {@code customer.account.show} or
 * {@code suppliers.account.show}, resolved through {@link PermAccountAndNameInt#forParty},
 * and required on every method including the read ones — what a party owes is not public
 * inside a shop.
 */
public final class PartyStatementService {

    /** As on the treasury side: an extract wide enough to print, narrow enough to hold. */
    public static final int PRINT_LIMIT = 10_000;

    private final PartyStatementRepository repository;

    public PartyStatementService() {
        this(new JdbcPartyStatementRepository());
    }

    public PartyStatementService(PartyStatementRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** What the filter combos offer. Read once per screen opening. */
    public PartyStatementOptions options(PartyKind kind) throws DaoException {
        requireShow(kind);
        return repository.options();
    }

    /**
     * Where a statement opens: the party's first movement, or today if they have none.
     * <p>
     * Today rather than a far-past default, because a date picker showing 1970 is a
     * picker the user has to correct before every search.
     */
    public LocalDate earliestMovement(PartyKind kind, int partyId) throws DaoException {
        requireShow(kind);
        LocalDate earliest = repository.earliestMovement(kind, partyId);
        return earliest == null ? LocalDate.now() : earliest;
    }

    /**
     * What the party owes today.
     * <p>
     * For the screens that want the one number: the collection dialog's "balance" field, and the
     * balance column the parties list is to gain. Reading it through here rather than off a
     * loaded list is the whole of the performance fix in phase ب.
     */
    public BigDecimal currentBalance(PartyKind kind, int partyId) throws DaoException {
        requireShow(kind);
        return repository.currentBalance(kind, partyId);
    }

    /**
     * One page of the statement, with the period's figures.
     * <p>
     * The page is asked for one row more than it holds, and that extra row is what answers
     * "is there a next page" — no second {@code COUNT} to fall out of step with the page's
     * own {@code WHERE}.
     */
    public PartyStatementPage search(PartyStatementFilter filter) throws DaoException {
        requireShow(filter.partyKind());
        List<PartyStatementRow> fetched = repository.search(filter);
        boolean hasNext = fetched.size() > filter.pageSize();
        List<PartyStatementRow> rows = hasNext
                ? List.copyOf(fetched.subList(0, filter.pageSize()))
                : List.copyOf(fetched);
        return new PartyStatementPage(rows, repository.summarize(filter), filter.page(),
                filter.page() > 0, hasNext);
    }

    /**
     * The whole filtered statement, for printing and for export.
     * <p>
     * A query of its own, with the same filter — so a printed or exported statement is the
     * statement the user is looking at, all of it, and not the hundred rows that happened
     * to be on screen. {@code AccountController2} exports the ticked rows while asking the
     * table whether it is empty, which is how ticking nothing produces an empty file and a
     * message saying it saved.
     */
    public PartyStatementPrintData forPrint(PartyStatementFilter filter) throws DaoException {
        requireShow(filter.partyKind());
        PartyStatementFilter printable = filter.firstPageWithSize(PRINT_LIMIT);
        List<PartyStatementRow> fetched = repository.search(printable);
        boolean truncated = fetched.size() > PRINT_LIMIT;
        List<PartyStatementRow> rows = truncated
                ? List.copyOf(fetched.subList(0, PRINT_LIMIT))
                : List.copyOf(fetched);
        return new PartyStatementPrintData(rows, repository.summarize(printable), truncated);
    }

    private static void requireShow(PartyKind kind) throws DaoException {
        AuthorizationGuard.require(PermAccountAndNameInt.forParty(kind).showAccounts());
    }
}
