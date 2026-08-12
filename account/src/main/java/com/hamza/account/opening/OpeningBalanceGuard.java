package com.hamza.account.opening;

import com.hamza.account.delete.Reference;
import com.hamza.account.delete.ReferenceScanner;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The one place an opening balance is decided.
 * <p>
 * It takes the {@link OpeningBalanceRule} for the kind of row and answers two
 * questions: has this row moved, and may this value be written. The screens ask the
 * first so they can grey the field and say why; the DAOs ask the second where the row
 * is actually written, because a disabled field is a hint and not a rule - the same
 * save is reachable from the Excel import, from a bulk-edit screen, and from any
 * caller that never looked at a form.
 * <p>
 * The scan is {@link ReferenceScanner}, the same one the delete rules use, so the
 * refusal can name what is in the way: "12 فاتورة بيع" rather than "يوجد حركات".
 */
public final class OpeningBalanceGuard {

    /**
     * {@code DECIMAL(14,2)} for the parties and {@code DECIMAL(14,3)} for the items, so
     * half a thousandth is below anything either column can hold. Comparing doubles
     * more finely than the column stores would refuse a save nobody asked for.
     */
    private static final double TOLERANCE = 0.0005;

    private static final OpeningBalanceGuard SHARED =
            new OpeningBalanceGuard(new ReferenceScanner(), OpeningBalanceGuard::readFromDatabase);

    private final ReferenceScanner referenceScanner;
    private final BalanceReader balanceReader;

    /** Reads the balance a row currently holds. */
    @FunctionalInterface
    public interface BalanceReader {
        double read(OpeningBalanceRule rule, int id) throws DaoException;
    }

    /** The wiring the application uses: a real scanner reading real rows. */
    public OpeningBalanceGuard(@NotNull ReferenceScanner referenceScanner) {
        this(referenceScanner, OpeningBalanceGuard::readFromDatabase);
    }

    /**
     * Both collaborators are taken rather than reached for, so the rules can be
     * exercised without a database - the same reason {@code DeletionService} takes its
     * scanner and its permission check, and {@code Publisher} its executor.
     */
    public OpeningBalanceGuard(@NotNull ReferenceScanner referenceScanner,
                               @NotNull BalanceReader balanceReader) {
        this.referenceScanner = referenceScanner;
        this.balanceReader = balanceReader;
    }

    /**
     * Held statically for the same reason {@code DeletionService.shared()} is: no state
     * and no lifecycle, since the scanner borrows a pooled connection per call.
     */
    public static OpeningBalanceGuard shared() {
        return SHARED;
    }

    /**
     * What has moved this row, empty if nothing has. A row that does not exist yet -
     * id zero from a form that has not been saved - has moved nothing.
     */
    public List<Reference> movements(@NotNull OpeningBalanceRule rule, int id) throws DaoException {
        if (id <= 0) {
            return List.of();
        }
        return referenceScanner.scan(rule.movements(), id);
    }

    /** Whether the opening balance is closed to editing. */
    public boolean isLocked(@NotNull OpeningBalanceRule rule, int id) throws DaoException {
        return !movements(rule, id).isEmpty();
    }

    /**
     * Refuses the write when the row has moved and the value is not the one already
     * stored.
     * <p>
     * A changed value is refused rather than quietly dropped: the user typed a number
     * and is entitled to know it was not saved. An unchanged one passes, so saving a
     * name or a phone number on a row that has moved is not turned into an error about
     * a field nobody touched.
     *
     * @return whether the balance may be written at all. False means the caller must
     *         leave the column out of its statement - the value is unchanged, but
     *         writing it back is still a write of a closed entry
     */
    public boolean mayWrite(@NotNull OpeningBalanceRule rule, int id, double incoming) throws DaoException {
        List<Reference> movements = movements(rule, id);
        if (movements.isEmpty()) {
            return true;
        }

        double stored = storedBalance(rule, id);
        if (Math.abs(stored - incoming) >= TOLERANCE) {
            throw new DaoException(refusal(rule, movements, stored));
        }
        return false;
    }

    private String refusal(OpeningBalanceRule rule, List<Reference> movements, double stored) {
        String moved = movements.stream().map(Reference::toString).collect(Collectors.joining("، "));
        return """
                لا يمكن تعديل رصيد أول المدة لـ%s بعد وجود حركات عليه.
                الحركات: %s
                الرصيد المسجل: %s
                %s"""
                .formatted(rule.entity(), moved, stored, rule.correction());
    }

    /** The balance as stored, which is the one every balance is computed from. */
    public double storedBalance(@NotNull OpeningBalanceRule rule, int id) throws DaoException {
        return balanceReader.read(rule, id);
    }

    private static double readFromDatabase(OpeningBalanceRule rule, int id) throws DaoException {
        // The table is an identifier from the registry, checked at construction; the id
        // is bound.
        String query = "SELECT " + OpeningBalanceRule.BALANCE_COLUMN
                       + " FROM " + rule.table() + " WHERE id = ?";
        return new SingleNumberDao().read(query, id);
    }

    /**
     * {@code values} without the one at {@code index}, everything after it shifted down.
     * <p>
     * Its own method because getting it wrong is silent. A DAO's update parameters are
     * positional, so dropping the opening balance from the column list means dropping
     * it from the values too, and an off-by-one here writes the area id into the user
     * column - or, worse, shifts the id that is the {@code WHERE} clause. Pinned down by
     * {@code OpeningBalanceGuardTest}.
     */
    public static Object[] without(Object[] values, int index) {
        if (index < 0 || index >= values.length) {
            throw new IllegalArgumentException("No value at " + index + " of " + values.length);
        }
        Object[] kept = new Object[values.length - 1];
        System.arraycopy(values, 0, kept, 0, index);
        System.arraycopy(values, index + 1, kept, index, values.length - index - 1);
        return kept;
    }

    /** Reads one number. A DAO because that is what borrows a pooled connection. */
    private static final class SingleNumberDao extends AbstractDao<Double> {

        double read(String query, int id) throws DaoException {
            return withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setInt(1, id);
                    try (ResultSet rs = statement.executeQuery()) {
                        return rs.next() ? rs.getDouble(1) : 0d;
                    }
                }
            });
        }
    }
}
