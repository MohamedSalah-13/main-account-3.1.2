package com.hamza.account.features.itemreports;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemCatalogFilter.BalanceRule;
import com.hamza.account.features.items.ItemCatalogSql;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every column a report reads off the movement row is one that row has.
 * <p>
 * A statement naming a column its own join does not provide compiles, passes every other
 * test, and fails only when MySQL reads it. That is how every item report came to answer
 * {@code Unknown column 'ip.stock_first_balance'}: {@code ItemCatalogSql.BALANCE} began
 * reading the per-warehouse opening balance, and the reports were joining a copy of the
 * movement subquery that did not have it. There is no database here on purpose - what broke
 * was the text, and the text is what this reads.
 */
class JdbcCatalogFactRepositorySqlTest {

    /** Each report statement, with every balance rule, since each puts BALANCE in the WHERE. */
    private static List<String> statements() {
        List<String> statements = new ArrayList<>();
        for (BalanceRule rule : BalanceRule.values()) {
            ItemCatalogSql.Statement query = ItemCatalogSql.build(ItemCatalogFilter.EMPTY.withBalance(rule));
            statements.add(JdbcCatalogFactRepository.factsSql(query, false));
            statements.add(JdbcCatalogFactRepository.factsSql(query, true));
            statements.add(JdbcCatalogFactRepository.expiringBatchesSql(query));
        }
        return statements;
    }

    /** The subquery joined as {@code ip}, read out of the statement rather than assumed. */
    private static String joinedMovementRow(String sql) {
        int alias = sql.indexOf(" ip ON");
        assertTrue(alias > 0, "no movement row is joined as ip:\n" + sql);
        int close = sql.lastIndexOf(')', alias);
        int depth = 0;
        for (int index = close; index >= 0; index--) {
            char character = sql.charAt(index);
            if (character == ')') {
                depth++;
            } else if (character == '(' && --depth == 0) {
                return sql.substring(index, close + 1);
            }
        }
        throw new AssertionError("the movement row joined as ip has unbalanced brackets:\n" + sql);
    }

    private static Set<String> columnsOf(String subquery) {
        Set<String> columns = new HashSet<>();
        Matcher matcher = Pattern.compile("(?:SELECT|AS)\\s+(\\w+)").matcher(subquery);
        while (matcher.find()) {
            columns.add(matcher.group(1));
        }
        return columns;
    }

    @Test
    void everyColumnReadOffTheMovementRowIsOneItProvides() {
        for (String sql : statements()) {
            Set<String> provided = columnsOf(joinedMovementRow(sql));
            Matcher read = Pattern.compile("\\bip\\.(\\w+)").matcher(sql);
            while (read.find()) {
                assertTrue(provided.contains(read.group(1)),
                        "the report reads ip." + read.group(1) + ", which the row it joins does not provide:\n" + sql);
            }
        }
    }

    @Test
    void theReportsJoinTheSameMovementRowAsTheItemsList() {
        for (String sql : statements()) {
            assertTrue(sql.contains(ItemCatalogSql.MOVEMENTS + " ip ON"),
                    "a report joins its own copy of the movement row instead of ItemCatalogSql.MOVEMENTS:\n" + sql);
        }
    }
}
