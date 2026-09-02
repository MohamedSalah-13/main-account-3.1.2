package com.hamza.account.features.shift;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.DaoException;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Captures document cash before a bulk delete, then appends reversals after it succeeds. */
public final class ShiftDocumentDeletionJournal {
    private final DaoFactory daoFactory;

    public ShiftDocumentDeletionJournal(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public Plan capture(DocumentType type, Integer[] ids) throws DaoException {
        int actor = CurrentUser.get().getId();
        ShiftGate gate = ShiftGate.jdbc(daoFactory.userShiftDao());
        JdbcShiftCashEffectReader reader = new JdbcShiftCashEffectReader();
        List<Entry> entries = new ArrayList<>();
        for (Integer id : ids) {
            ShiftCashEffect effect = reader.document(type, id);
            if (effect == null) continue;
            OptionalInt shift = gate.requireCashCorrection(actor, effect.treasuryId(),
                    effect.income().add(effect.output()).abs(), effect.originalShiftId());
            entries.add(new Entry(shift, effect));
        }
        return new Plan(actor, List.copyOf(entries));
    }

    public record Plan(int actorUserId, List<Entry> entries) {
        public void appendReversals(int deletedRows) throws DaoException {
            appendReversals(deletedRows, null);
        }

        public void appendReversals(int deletedRows, String correctionReason) throws DaoException {
            if (deletedRows <= 0) return;
            ShiftCashLedger ledger = ShiftCashLedger.jdbc();
            for (Entry entry : entries) {
                ledger.deleted(entry.shiftId(), actorUserId, entry.effect(), correctionReason);
            }
        }
    }

    public record Entry(OptionalInt shiftId, ShiftCashEffect effect) { }
}
