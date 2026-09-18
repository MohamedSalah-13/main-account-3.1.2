package com.hamza.account.features.expense.budget;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.report.ExpenseHeadingLineKind;
import com.hamza.account.features.expense.report.ExpensePeriods;
import com.hamza.account.features.expense.report.ExpenseReportRows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The budget against what was actually spent, heading by heading (docs/expenses-plan.md §5.1).
 * <p>
 * <b>A main heading with no budget of its own takes the sum of its children's, and one with a budget of
 * its own does not add them a second time.</b> That is the rule the plan names, and it is the only place
 * a figure could be counted twice: a main heading budgeted at 5,000 over children budgeted at 3,000 and
 * 1,000 means 5,000 for the family, not 9,000. A shop that budgets both is saying the main heading's
 * figure is the ceiling for everything under it - so that is what is compared against the family's
 * actual spend, and each child's own line still shows its own budget beside its own spend.
 * <p>
 * <b>The actual side is the report by heading's.</b> It arrives as the same
 * {@link ExpenseReportRows.HeadingTotal} rows that report reads, so "spent" here and "spent" there are
 * one query and cannot disagree.
 * <p>
 * <b>A heading with neither a budget nor a movement is left out</b>, and one with either is kept: a
 * budget nobody spent against is exactly what this report is opened to find, and so is a heading that
 * was spent on with no budget at all.
 *
 * @param scope the filter the actuals were read over - its period is what the budgets were matched on
 */
public record ExpenseBudgetReport(ExpenseFilter scope, List<Line> lines, BigDecimal budget, BigDecimal actual) {

    /**
     * One heading.
     *
     * @param budget    what it is allowed over the period; zero when it has none
     * @param hasBudget false when nothing was budgeted - which is not the same as a budget of zero
     * @param actual    what was spent under it, a main heading including its children
     */
    public record Line(ExpenseHeadingLineKind kind, int headingId, String name, BigDecimal budget,
                       boolean hasBudget, BigDecimal actual) {

        /** What is left of the budget; negative when it was overspent. */
        public BigDecimal remaining() {
            return budget.subtract(actual);
        }

        /** What share of the budget was spent; absent when there is no budget to divide by. */
        public Optional<BigDecimal> usedPercent() {
            return hasBudget ? ExpensePeriods.percent(actual, budget) : Optional.empty();
        }

        /** Whether the spend has passed the budget. Only ever true where there is one. */
        public boolean overspent() {
            return hasBudget && actual.compareTo(budget) > 0;
        }
    }

    public ExpenseBudgetReport {
        lines = List.copyOf(lines);
    }

    /**
     * Builds the report.
     *
     * @param budgets every budget row of the scope's period - yearly and monthly together; a heading's
     *                budget for the period is the sum of the rows that fall inside it
     * @param actuals what each heading itself holds, as the report by heading reads it
     */
    public static ExpenseBudgetReport build(ExpenseFilter scope, List<ExpenseHeading> headings,
                                            List<ExpenseBudget> budgets,
                                            List<ExpenseReportRows.HeadingTotal> actuals) {
        Map<Integer, BigDecimal> ownBudget = new HashMap<>();
        for (ExpenseBudget budget : budgets) {
            ownBudget.merge(budget.headingId(), budget.amount(), BigDecimal::add);
        }
        Map<Integer, BigDecimal> ownActual = new HashMap<>();
        for (ExpenseReportRows.HeadingTotal total : actuals) {
            ownActual.merge(total.headingId(), total.total(), BigDecimal::add);
        }

        Map<Integer, ExpenseHeading> byId = new LinkedHashMap<>();
        for (ExpenseHeading heading : headings) {
            byId.put(heading.id(), heading);
        }
        Map<Integer, List<ExpenseHeading>> children = new LinkedHashMap<>();
        List<ExpenseHeading> mains = new ArrayList<>();
        for (ExpenseHeading heading : byId.values()) {
            Integer parent = heading.parentId();
            if (parent == null || !byId.containsKey(parent)) {
                mains.add(heading);
            } else {
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(heading);
            }
        }

        List<Line> lines = new ArrayList<>();
        BigDecimal totalBudget = ExpensePeriods.NONE;
        BigDecimal totalActual = ExpensePeriods.NONE;
        for (ExpenseHeading main : mains) {
            List<ExpenseHeading> under = children.getOrDefault(main.id(), List.of());
            BigDecimal familyActual = ownActual.getOrDefault(main.id(), ExpensePeriods.NONE);
            BigDecimal childrenBudget = ExpensePeriods.NONE;
            boolean anyChildBudget = false;
            for (ExpenseHeading child : under) {
                familyActual = familyActual.add(ownActual.getOrDefault(child.id(), ExpensePeriods.NONE));
                BigDecimal childBudget = ownBudget.get(child.id());
                if (childBudget != null) {
                    childrenBudget = childrenBudget.add(childBudget);
                    anyChildBudget = true;
                }
            }
            BigDecimal mainOwn = ownBudget.get(main.id());
            // The rule: its own budget when it has one, its children's when it has not, never both.
            boolean hasBudget = mainOwn != null || anyChildBudget;
            BigDecimal familyBudget = mainOwn != null ? mainOwn : childrenBudget;

            if (!hasBudget && familyActual.signum() == 0) {
                continue;
            }
            lines.add(new Line(ExpenseHeadingLineKind.MAIN, main.id(), main.name(),
                    hasBudget ? familyBudget : ExpensePeriods.NONE, hasBudget, familyActual));
            totalBudget = totalBudget.add(hasBudget ? familyBudget : ExpensePeriods.NONE);
            totalActual = totalActual.add(familyActual);

            for (ExpenseHeading child : under) {
                BigDecimal childBudget = ownBudget.get(child.id());
                BigDecimal childActual = ownActual.getOrDefault(child.id(), ExpensePeriods.NONE);
                if (childBudget == null && childActual.signum() == 0) {
                    continue;
                }
                lines.add(new Line(ExpenseHeadingLineKind.SUB, child.id(), child.name(),
                        childBudget == null ? ExpensePeriods.NONE : childBudget, childBudget != null, childActual));
            }
        }
        return new ExpenseBudgetReport(scope, lines, totalBudget, totalActual);
    }

    /** What is left of every budget together; negative when the shop is over. */
    public BigDecimal remaining() {
        return budget.subtract(actual);
    }

    public Optional<BigDecimal> usedPercent() {
        return ExpensePeriods.percent(actual, budget);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** The list's filter for one line: its heading over the report's own period. */
    public ExpenseFilter listFilter(Line line) {
        return scope.withHeading(line.headingId());
    }

    /** The months of the scope, for matching the budgets that fall inside it. */
    public static boolean inScope(ExpenseBudget budget, LocalDate from, LocalDate to) {
        return from != null && to != null && !budget.to().isBefore(from) && !budget.from().isAfter(to);
    }
}
