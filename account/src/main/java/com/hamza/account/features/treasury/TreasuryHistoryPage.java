package com.hamza.account.features.treasury;

import java.math.BigDecimal;
import java.util.List;

/**
 * One page of a history list, with the totals of the <b>whole</b> filtered set beside it.
 * <p>
 * The totals are summed in SQL over the same {@code WHERE} the rows were read with - the rule
 * {@code ItemsDao.catalogQuery} and the party statement follow - so a footer can never describe a
 * different set of rows from the table above it, and never only the fifty on screen.
 *
 * @param truncated only ever true for a print extract that met {@link TreasuryHistoryFilter#PRINT_LIMIT}
 */
public record TreasuryHistoryPage<T>(List<T> rows, Totals totals, int page, boolean hasPrevious,
                                     boolean hasNext, boolean truncated) {

    /**
     * @param first  transfers: what was moved. Cash movements: what was deposited.
     * @param second transfers: what moving it cost. Cash movements: what was withdrawn.
     */
    public record Totals(long count, BigDecimal first, BigDecimal second) {
        public Totals {
            first = first == null ? BigDecimal.ZERO : first;
            second = second == null ? BigDecimal.ZERO : second;
        }
    }

    /** Cuts the one extra row the query read, and says whether it was there. */
    public static <T> TreasuryHistoryPage<T> of(List<T> fetched, Totals totals, TreasuryHistoryFilter filter) {
        boolean more = fetched.size() > filter.pageSize();
        List<T> rows = more ? List.copyOf(fetched.subList(0, filter.pageSize())) : List.copyOf(fetched);
        boolean printing = filter.pageSize() == TreasuryHistoryFilter.PRINT_LIMIT;
        return new TreasuryHistoryPage<>(rows, totals, filter.page(), filter.page() > 0,
                more && !printing, more && printing);
    }
}
