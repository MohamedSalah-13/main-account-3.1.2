package com.hamza.account.delete;

import com.hamza.account.perm.PermissionGuard;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.database.DaoException;
import org.jetbrains.annotations.NotNull;

/**
 * The one place a delete is decided.
 * <p>
 * It takes the {@link DeleteRule} for the kind of row and the DAO call that
 * actually removes it, and runs the checks in the order that keeps them cheap and
 * the message truthful: permission first (no query at all if the user may not),
 * then the protected ids (in memory), then the reference counts (one query), and
 * only then the delete.
 * <p>
 * It deliberately does not publish the change. The screens already do that, each
 * with the event and the payload it knows about - {@code NameController} publishes
 * {@code NameChanged} and {@code AccountChanged} together, the units screen
 * publishes through its refresh - and announcing it here as well would reload
 * every listening screen twice. Nor does it write an audit row: the triggers in
 * {@code V2__audit_triggers.sql} already record deletes on the main tables, from
 * inside the transaction, which an application-level write cannot match.
 */
public final class DeletionService {

    private final ReferenceScanner referenceScanner;
    private final PermissionChecker permissionChecker;

    /** The wiring the application uses: a real scanner and the signed-in user's permissions. */
    public DeletionService() {
        this(new ReferenceScanner(), PermissionGuard::isGranted);
    }

    /**
     * The instance the services delete through.
     * <p>
     * Held statically rather than registered in {@code ServiceRegistry} because it
     * has no state and no lifecycle: the scanner borrows a pooled connection per
     * call like every DAO, and the permission check reads the signed-in user each
     * time. Nothing here to initialise in the right order, and nothing to go stale
     * at logout.
     */
    public static DeletionService shared() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final DeletionService INSTANCE = new DeletionService();
    }

    /**
     * Both collaborators are taken rather than reached for, so the rules can be
     * exercised without a database and without a signed-in user - the same reason
     * {@code Publisher} and {@code NotificationCenter} take an executor and a clock.
     */
    public DeletionService(@NotNull ReferenceScanner referenceScanner, @NotNull PermissionChecker permissionChecker) {
        this.referenceScanner = referenceScanner;
        this.permissionChecker = permissionChecker;
    }

    /**
     * Applies {@code rule} to {@code id} and, if it passes, deletes the row.
     * <p>
     * Answers rather than throws: refusals are ordinary outcomes here, and a
     * caller that wants the old shape - a row count, an exception on anything else
     * - asks for {@link DeleteOutcome#rowsOrThrow()}.
     */
    public DeleteOutcome delete(@NotNull DeleteRule rule, int id, @NotNull RowDeleter deleter) throws DaoException {
        DeleteOutcome refusal = check(rule, id);
        if (refusal != null) {
            return refusal;
        }

        int rows = deleter.delete(id);
        // Zero rows here is not a refusal - every reason to refuse has been checked
        // and answered above. It means the id was not there to delete.
        return rows > 0 ? new DeleteOutcome.Deleted(rows) : new DeleteOutcome.NotFound(rule.entityLabel());
    }

    /**
     * Why the rule would refuse this row, or null if it would not - for a screen
     * that wants to say so before the user commits to it, and for
     * {@link #delete} to run before it touches anything.
     */
    public DeleteOutcome check(@NotNull DeleteRule rule, int id) throws DaoException {
        if (id <= 0) {
            return new DeleteOutcome.NotFound(rule.entityLabel());
        }
        if (rule.permission() != null && !permissionChecker.isGranted(rule.permission())) {
            return new DeleteOutcome.Denied(rule.entityLabel());
        }

        String protection = rule.protectionFor(id);
        if (protection != null) {
            return new DeleteOutcome.Protected(protection);
        }

        var references = referenceScanner.scan(rule.references(), id);
        if (!references.isEmpty()) {
            return new DeleteOutcome.Blocked(rule.entityLabel(), references);
        }
        return null;
    }

    /**
     * The DAO call that removes the row, once the rule has allowed it. Nothing
     * more than {@code SomeDao::deleteById} in practice - passed in rather than
     * declared on the rule so the rule stays a description of what is allowed and
     * says nothing about how the row is stored.
     */
    @FunctionalInterface
    public interface RowDeleter {
        int delete(int id) throws DaoException;
    }

    /** Whether the signed-in user holds a permission. {@link PermissionGuard} in the application. */
    @FunctionalInterface
    public interface PermissionChecker {
        boolean isGranted(UserPermissionType permissionType);
    }
}
