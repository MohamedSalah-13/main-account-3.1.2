package com.hamza.account.model.dao;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared identity-preserving update for the four invoice-line tables.
 *
 * <p>An existing line keeps its primary key, a line with id {@code 0} is inserted,
 * and only stored ids absent from the submitted invoice are deleted. The lock,
 * validation and writes join the header DAO's transaction through
 * {@link AbstractDao}.</p>
 */
abstract class DocumentLineDao<T extends BasePurchasesAndSales> extends AbstractDao<T> {

    private final DocumentTableSpec spec;

    protected DocumentLineDao(DocumentTableSpec spec) {
        this.spec = spec;
    }

    protected abstract Object[] lineData(T line) throws DaoException;

    final int synchronizeLines(int documentId, List<T> submittedLines) throws DaoException {
        return insertMultiData(() -> {
            Set<Integer> storedIds = lockStoredIds(documentId);
            LinePlan<T> plan = plan(documentId, storedIds, submittedLines);
            updateExisting(documentId, plan.updates());
            if (!plan.inserts().isEmpty()) {
                insertList(plan.inserts());
            }
            deleteRemoved(documentId, plan.deletes());
        });
    }

    private Set<Integer> lockStoredIds(int documentId) throws DaoException {
        return withConnection(connection -> {
            Set<Integer> ids = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(lineIdsForUpdateSql())) {
                statement.setInt(1, documentId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        ids.add(rows.getInt(1));
                    }
                }
            }
            return ids;
        });
    }

    private void updateExisting(int documentId, List<T> lines) throws DaoException {
        if (lines.isEmpty()) return;
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(lineUpdateSql())) {
                for (T line : lines) {
                    Object[] values = lineData(line);
                    if (values.length != spec.lineColumns().size()) {
                        throw new DaoException("تعريف بيانات سطر الفاتورة لا يطابق أعمدة الجدول");
                    }
                    int parameter = 1;
                    // The first insert value is invoice_number. It is immutable here
                    // and is repeated only in the ownership predicate at the end.
                    for (int i = 1; i < values.length; i++) {
                        statement.setObject(parameter++, values[i]);
                    }
                    statement.setInt(parameter++, line.getId());
                    statement.setInt(parameter, documentId);
                    statement.addBatch();
                }
                requireSuccessfulBatch(statement.executeBatch(), "تحديث");
            }
            return null;
        });
    }

    private void deleteRemoved(int documentId, Set<Integer> ids) throws DaoException {
        if (ids.isEmpty()) return;
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(lineDeleteOwnedSql())) {
                for (Integer id : ids) {
                    statement.setInt(1, id);
                    statement.setInt(2, documentId);
                    statement.addBatch();
                }
                requireSuccessfulBatch(statement.executeBatch(), "حذف");
            }
            return null;
        });
    }

    private static void requireSuccessfulBatch(int[] results, String action) throws SQLException {
        for (int result : results) {
            if (result == Statement.EXECUTE_FAILED) {
                throw new SQLException("فشل " + action + " أحد سطور الفاتورة");
            }
        }
    }

    String lineIdsForUpdateSql() {
        return spec.lineIdsForUpdateSql();
    }

    String lineUpdateSql() {
        return spec.lineUpdateSql();
    }

    String lineDeleteOwnedSql() {
        return spec.lineDeleteOwnedSql();
    }

    static <L extends BasePurchasesAndSales> LinePlan<L> plan(
            int documentId, Set<Integer> storedIds, List<L> submittedLines) throws DaoException {
        if (documentId <= 0) {
            throw new UserValidationException("رقم الفاتورة غير صالح");
        }
        if (submittedLines == null || submittedLines.isEmpty()) {
            throw new UserValidationException("لا يمكن حفظ فاتورة بدون أصناف");
        }

        Set<Integer> stored = Set.copyOf(storedIds);
        Set<Integer> retained = new LinkedHashSet<>();
        List<L> inserts = new ArrayList<>();
        List<L> updates = new ArrayList<>();

        for (L line : submittedLines) {
            if (line == null) {
                throw new UserValidationException("يوجد سطر فارغ داخل الفاتورة");
            }
            if (line.getInvoiceNumber() != documentId) {
                throw new BusinessRuleException("تم تغيير سطور الفاتورة؛ أعد فتحها قبل الحفظ");
            }

            int id = line.getId();
            if (id == 0) {
                inserts.add(line);
                continue;
            }
            if (id < 0) {
                throw new UserValidationException("هوية سطر الفاتورة غير صالحة");
            }
            if (!stored.contains(id)) {
                throw new BusinessRuleException("تم تغيير سطور الفاتورة؛ أعد فتحها قبل الحفظ");
            }
            if (!retained.add(id)) {
                throw new UserValidationException("سطر الفاتورة مكرر: " + id);
            }
            updates.add(line);
        }

        Set<Integer> deletes = new LinkedHashSet<>(stored);
        deletes.removeAll(retained);
        return new LinePlan<>(List.copyOf(inserts), List.copyOf(updates), Set.copyOf(deletes));
    }

    record LinePlan<L>(List<L> inserts, List<L> updates, Set<Integer> deletes) {
    }
}
