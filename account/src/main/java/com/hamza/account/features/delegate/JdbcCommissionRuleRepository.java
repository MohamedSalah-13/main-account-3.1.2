package com.hamza.account.features.delegate;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcCommissionRuleRepository extends AbstractDao<CommissionRule>
        implements CommissionRuleRepository {

    @Override
    public List<CommissionRule> history(int employeeId) throws DaoException {
        return queryForObjects(CommissionRuleQuery.HISTORY_SQL, this::map, employeeId);
    }

    @Override
    public Optional<CommissionRule> inForceOn(int employeeId, LocalDate day) throws DaoException {
        List<CommissionRule> found = queryForObjects(CommissionRuleQuery.IN_FORCE_SQL,
                this::map, employeeId, Date.valueOf(day));
        return found.stream().findFirst();
    }

    /**
     * An {@code UPDATE} first, an {@code INSERT} when it moved nothing - not
     * {@code INSERT ... ON DUPLICATE KEY UPDATE}, which reports two affected rows for an update,
     * so a caller could not tell "written" from "written twice". Same reasoning, same shape, as
     * {@code JdbcEmployeeRepository.saveCompensation}.
     *
     * <p>An update that changes no value also reports zero rows in MySQL, and would fall
     * through to an insert that collides with the unique key. The connection reports
     * <em>matched</em> rows ({@code useAffectedRows} is off), so saving a rule unchanged
     * answers 1 rather than a duplicate-key error.
     */
    @Override
    public int save(CommissionRule rule, int userId) throws DaoException {
        Object[] tiers = tierColumns(rule.tiers());
        int updated = executeUpdate(CommissionRuleQuery.UPDATE_SQL,
                rule.basis().name(), rule.tierMode().name(), rule.target(),
                tiers[0], tiers[1], tiers[2], tiers[3], tiers[4], tiers[5], rule.notes(),
                rule.employeeId(), Date.valueOf(rule.effectiveFrom()));
        if (updated > 0) {
            return 1;
        }
        return executeUpdate(CommissionRuleQuery.INSERT_SQL,
                rule.employeeId(), Date.valueOf(rule.effectiveFrom()), rule.basis().name(),
                rule.tierMode().name(), rule.target(),
                tiers[0], tiers[1], tiers[2], tiers[3], tiers[4], tiers[5], rule.notes(), userId);
    }

    @Override
    public int delete(int employeeId, int ruleId) throws DaoException {
        return executeUpdate(CommissionRuleQuery.DELETE_SQL, ruleId, employeeId);
    }

    @Override
    public boolean isDelegate(int employeeId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(CommissionRuleQuery.IS_DELEGATE_SQL)) {
                statement.setInt(1, employeeId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        });
    }

    /** The six tier columns in statement order; an unused tier is two NULLs, as V70's CHECKs require. */
    static Object[] tierColumns(CommissionTiers tiers) {
        Object[] columns = new Object[CommissionTiers.MAX_TIERS * 2];
        for (int i = 0; i < tiers.tiers().size(); i++) {
            columns[i * 2] = tiers.tiers().get(i).fromPercent();
            columns[i * 2 + 1] = tiers.tiers().get(i).ratePercent();
        }
        return columns;
    }

    @Override
    public CommissionRule map(ResultSet rs) throws DaoException {
        try {
            List<CommissionTiers.Tier> tiers = new ArrayList<>();
            for (int i = 1; i <= CommissionTiers.MAX_TIERS; i++) {
                BigDecimal from = rs.getBigDecimal("tier" + i + "_from");
                BigDecimal rate = rs.getBigDecimal("tier" + i + "_rate");
                if (from != null && rate != null) {
                    tiers.add(new CommissionTiers.Tier(from, rate));
                }
            }
            return new CommissionRule(rs.getInt("id"), rs.getInt("employee_id"),
                    rs.getDate("effective_from").toLocalDate(),
                    CommissionBasis.of(rs.getString("basis")), TierMode.of(rs.getString("tier_mode")),
                    rs.getBigDecimal("target"), new CommissionTiers(tiers), rs.getString("notes"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
