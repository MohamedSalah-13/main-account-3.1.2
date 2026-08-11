package com.hamza.account.delete;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * What happened when a delete was asked for.
 * <p>
 * Deletes used to answer with an {@code int} and, for anything that was not a
 * row count, an exception - {@code IllegalArgumentException} from some DAOs,
 * {@code DaoException} from others, a foreign-key failure translated to one
 * general sentence from the rest. A caller could not tell "no such row" from
 * "this DAO has no delete" from "something still points at it", because all
 * three arrived as 0 or as a message with no structure behind it.
 * <p>
 * Every case is now one of these, and each carries what the screen needs to say.
 */
public sealed interface DeleteOutcome {

    /** The row went. */
    record Deleted(int rows) implements DeleteOutcome {
        @Override
        public String message() {
            return "تم الحذف";
        }
    }

    /** Something still points at the row, and here is what. */
    record Blocked(String entity, List<Reference> references) implements DeleteOutcome {
        @Override
        public String message() {
            String detail = references.stream()
                    .map(Reference::toString)
                    .collect(Collectors.joining("، "));
            return "لا يمكن حذف " + entity + ": مستخدم في " + detail;
        }
    }

    /** The row is one the application will not delete at all - a seeded default. */
    record Protected(String reason) implements DeleteOutcome {
        @Override
        public String message() {
            return reason;
        }
    }

    /** The signed-in user may not delete this kind of row. */
    record Denied(String entity) implements DeleteOutcome {
        @Override
        public String message() {
            return "ليس لديك صلاحية حذف " + entity;
        }
    }

    /** Nothing matched the id - already deleted, or never there. */
    record NotFound(String entity) implements DeleteOutcome {
        @Override
        public String message() {
            return "لا يوجد " + entity + " بهذا الرقم";
        }
    }

    /** A sentence for the user, in Arabic, ready to hand to {@code AllAlerts}. */
    String message();

    default boolean succeeded() {
        return this instanceof Deleted;
    }

    /**
     * The affected row count, or a {@link DaoException} carrying {@link #message()}.
     * <p>
     * The bridge for the screens that still expect a delete to answer with an int
     * and to report failure by throwing. They keep working unchanged while the
     * reason they display becomes the specific one, and a screen that would rather
     * branch on the outcome can take it whole instead.
     */
    default int rowsOrThrow() throws DaoException {
        if (this instanceof Deleted deleted) {
            return deleted.rows();
        }
        throw new DaoException(message());
    }
}
