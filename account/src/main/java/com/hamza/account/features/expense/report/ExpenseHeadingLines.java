package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseHeading;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The headings as a report lays them out: a main heading, the headings under it, and a "direct" line for
 * what was filed on the main heading itself.
 * <p>
 * <b>A main heading's figure is its own expenses plus its children's</b> (docs/expenses-plan.md ق-٣). The
 * direct line is what makes that visible rather than a sum that does not add up on paper: a main heading
 * of 1,000 over children of 600 and 300 has a direct line of 100, and without it the reader is left to
 * guess where the hundred went. It is written only when the main heading has children on the report -
 * with none, the main line <i>is</i> the direct line and a second one would repeat it.
 * <p>
 * <b>A heading with nothing in either period is left out.</b> A shop keeps headings it stopped using;
 * a report listing every one of them at zero is a list of headings, not a report. The one exception is
 * built in: a heading that had something in the previous period stays, because "fell to nothing" is
 * exactly what a comparison is read for.
 * <p>
 * Mains are ordered by what they came to, the largest first, and so are the children under each - the
 * question a report by heading is opened with is where the money went.
 */
final class ExpenseHeadingLines {

    /** What one heading carries through a report. Mutable while the rows are filed, read only after. */
    static final class Amounts {
        int count;
        BigDecimal total = ExpensePeriods.NONE;
        BigDecimal previous = ExpensePeriods.NONE;
        final BigDecimal[] months = new BigDecimal[12];

        Amounts() {
            java.util.Arrays.fill(months, ExpensePeriods.NONE);
        }

        void add(Amounts other) {
            count += other.count;
            total = total.add(other.total);
            previous = previous.add(other.previous);
            for (int i = 0; i < 12; i++) {
                months[i] = months[i].add(other.months[i]);
            }
        }

        boolean isEmpty() {
            return count == 0 && total.signum() == 0 && previous.signum() == 0;
        }
    }

    /** One line in report order. {@code id} is the heading's; a direct line carries its main heading's. */
    record Line(ExpenseHeadingLineKind kind, int id, String name, Amounts amounts) {
    }

    private ExpenseHeadingLines() {
    }

    /**
     * Lays the headings out.
     *
     * @param headings every heading, stopped ones included - a stopped heading still has history
     * @param amounts  what each heading holds under itself, by id. A heading the list does not name - which
     *                 the foreign key rules out, and is handled anyway rather than dropped - is a main
     *                 heading called by its number, so no amount goes missing from the total
     */
    static List<Line> of(List<ExpenseHeading> headings, Map<Integer, Amounts> amounts) {
        Map<Integer, ExpenseHeading> byId = new LinkedHashMap<>();
        for (ExpenseHeading heading : headings) {
            byId.put(heading.id(), heading);
        }
        for (Integer id : amounts.keySet()) {
            byId.putIfAbsent(id, new ExpenseHeading(id, "#" + id, null, null, false, null, false));
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

        Function<ExpenseHeading, Amounts> own = heading -> amounts.getOrDefault(heading.id(), new Amounts());
        List<Group> groups = new ArrayList<>();
        for (ExpenseHeading main : mains) {
            Amounts direct = own.apply(main);
            List<Map.Entry<ExpenseHeading, Amounts>> shownChildren = new ArrayList<>();
            Amounts rolled = new Amounts();
            rolled.add(direct);
            for (ExpenseHeading child : children.getOrDefault(main.id(), List.of())) {
                Amounts childAmounts = own.apply(child);
                rolled.add(childAmounts);
                if (!childAmounts.isEmpty()) {
                    shownChildren.add(Map.entry(child, childAmounts));
                }
            }
            if (!rolled.isEmpty()) {
                shownChildren.sort(Comparator.<Map.Entry<ExpenseHeading, Amounts>, BigDecimal>comparing(
                        entry -> entry.getValue().total).reversed().thenComparing(entry -> entry.getKey().name()));
                groups.add(new Group(main, rolled, direct, shownChildren));
            }
        }
        groups.sort(Comparator.<Group, BigDecimal>comparing(group -> group.rolled.total).reversed()
                .thenComparing(group -> group.main.name()));

        List<Line> lines = new ArrayList<>();
        for (Group group : groups) {
            lines.add(new Line(ExpenseHeadingLineKind.MAIN, group.main.id(), group.main.name(), group.rolled));
            for (Map.Entry<ExpenseHeading, Amounts> child : group.children) {
                lines.add(new Line(ExpenseHeadingLineKind.SUB, child.getKey().id(), child.getKey().name(),
                        child.getValue()));
            }
            if (!group.children.isEmpty() && !group.direct.isEmpty()) {
                lines.add(new Line(ExpenseHeadingLineKind.DIRECT, group.main.id(), group.main.name(), group.direct));
            }
        }
        return lines;
    }

    /** What the main lines add up to - never the sub or direct lines, which are inside them. */
    static Amounts grandTotal(List<Line> lines) {
        Amounts total = new Amounts();
        for (Line line : lines) {
            if (line.kind() == ExpenseHeadingLineKind.MAIN) {
                total.add(line.amounts());
            }
        }
        return total;
    }

    private record Group(ExpenseHeading main, Amounts rolled, Amounts direct,
                         List<Map.Entry<ExpenseHeading, Amounts>> children) {
    }
}
