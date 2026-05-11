package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.TreasuryTransfer;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

public class TreasuryTransferDao extends AbstractDao<TreasuryTransfer> {

    private final TreasuryDao treasuryDao;

    public TreasuryTransferDao(Connection connection) {
        super(connection);
        this.treasuryDao = new TreasuryDao(connection);
    }

    @Override
    public TreasuryTransfer map(ResultSet rs) throws DaoException {
        try {
            TreasuryTransfer transfer = new TreasuryTransfer();

            transfer.setId(rs.getInt("id"));
            transfer.setAmount(rs.getBigDecimal("amount"));

            var transferDate = rs.getDate("transfer_date");
            if (transferDate != null) {
                transfer.setTransferDate(transferDate.toLocalDate());
            }

            transfer.setNotes(rs.getString("notes"));
            transfer.getUsers().setId(rs.getInt("user_id"));

            Treasury from = new Treasury();
            from.setId(rs.getInt("treasury_from"));
            from.setName(rs.getString("from_name"));
            transfer.setTreasuryFrom(from);

            Treasury to = new Treasury();
            to.setId(rs.getInt("treasury_to"));
            to.setName(rs.getString("to_name"));
            transfer.setTreasuryTo(to);

            return transfer;
        } catch (Exception e) {
            throw new DaoException(e);
        }
    }

    @Override
    public List<TreasuryTransfer> loadAll() throws DaoException {
        String query = """
                SELECT tt.id,
                       tt.treasury_from,
                       tt.treasury_to,
                       tt.amount,
                       tt.transfer_date,
                       tt.notes,
                       tt.user_id,
                       tf.t_name AS from_name,
                       tt2.t_name AS to_name
                FROM treasury_transfers tt
                JOIN treasury tf ON tf.id = tt.treasury_from
                JOIN treasury tt2 ON tt2.id = tt.treasury_to
                ORDER BY tt.transfer_date DESC, tt.id DESC
                """;
        return queryForObjects(query, this::map);
    }

    public List<TreasuryTransfer> loadBetweenDates(LocalDate startDate, LocalDate endDate) throws DaoException {
        String query = """
                SELECT tt.id,
                       tt.treasury_from,
                       tt.treasury_to,
                       tt.amount,
                       tt.transfer_date,
                       tt.notes,
                       tt.user_id,
                       tf.t_name AS from_name,
                       tt2.t_name AS to_name
                FROM treasury_transfers tt
                JOIN treasury tf ON tf.id = tt.treasury_from
                JOIN treasury tt2 ON tt2.id = tt.treasury_to
                WHERE tt.transfer_date BETWEEN ? AND ?
                ORDER BY tt.transfer_date DESC, tt.id DESC
                """;
        return queryForObjects(query, this::map, startDate, endDate);
    }

    @Override
    public int insert(TreasuryTransfer transfer) throws DaoException {
        validateTransfer(transfer);

        return insertMultiData(() -> {
            int affectedRows = treasuryDao.decreaseAmount(
                    transfer.getTreasuryFrom().getId(),
                    transfer.getAmount()
            );

            if (affectedRows == 0) {
                throw new DaoException("رصيد الخزينة المحول منها غير كافٍ");
            }

            treasuryDao.increaseAmount(
                    transfer.getTreasuryTo().getId(),
                    transfer.getAmount()
            );

            insertTransferOnly(transfer);
        });
    }

    private int insertTransferOnly(TreasuryTransfer transfer) throws DaoException {
        String query = """
                INSERT INTO treasury_transfers
                    (treasury_from, treasury_to, amount, transfer_date, notes, user_id)
                VALUES
                    (?, ?, ?, ?, ?, ?)
                """;

        return executeUpdate(
                query,
                transfer.getTreasuryFrom().getId(),
                transfer.getTreasuryTo().getId(),
                transfer.getAmount(),
                transfer.getTransferDate(),
                transfer.getNotes(),
                transfer.getUsers().getId()
        );
    }

    private void validateTransfer(TreasuryTransfer transfer) throws DaoException {
        if (transfer.getTreasuryFrom() == null) {
            throw new DaoException("يجب اختيار الخزينة المحول منها");
        }

        if (transfer.getTreasuryTo() == null) {
            throw new DaoException("يجب اختيار الخزينة المحول إليها");
        }

        if (transfer.getTreasuryFrom().getId() == transfer.getTreasuryTo().getId()) {
            throw new DaoException("لا يمكن التحويل إلى نفس الخزينة");
        }

        if (transfer.getAmount() == null || transfer.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DaoException("يجب إدخال مبلغ أكبر من صفر");
        }

        if (transfer.getTransferDate() == null) {
            transfer.setTransferDate(LocalDate.now());
        }
    }
}