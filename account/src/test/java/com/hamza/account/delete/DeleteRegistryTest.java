package com.hamza.account.delete;

import com.hamza.account.schema.SchemaForeignKeys;
import com.hamza.account.schema.SchemaForeignKeys.ForeignKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Holds {@link DeleteRegistry} to the schema, the way {@code WipeCatalogTest}
 * already holds {@code WipeCatalog} to it.
 * <p>
 * Nothing did, and that is how finding ن-١ survived: {@code EMPLOYEES} declared
 * {@code expense_salary.employee_id}, a table no line of the project has ever
 * written to, and did not declare {@code expenses_details.emp_id}, which is the
 * column the application actually writes. So deleting an employee was refused for
 * the one employee three legacy rows happen to name and allowed for everyone
 * else, with years of salaries behind them. A guard on the wrong row looks
 * present, which is worse than one that is missing.
 * <p>
 * Both directions are checked, because both are wrong in their own way: an
 * undeclared key turns a clean refusal into a raw SQL error on a user's screen,
 * and a declared key the schema does not have refuses a delete for a reason that
 * does not exist.
 */
class DeleteRegistryTest {

    /**
     * The table each rule deletes from. A rule carries a label for the user, not a
     * table name - the table is the schema's word for it and lives here, reviewed
     * once, at the point a rule is added.
     */
    private static final Map<DeleteRule, String> TABLE_BY_RULE = Map.ofEntries(
            Map.entry(DeleteRegistry.ITEMS, "items"),
            Map.entry(DeleteRegistry.CUSTOMERS, "custom"),
            Map.entry(DeleteRegistry.SUPPLIERS, "suppliers"),
            Map.entry(DeleteRegistry.UNITS, "units"),
            Map.entry(DeleteRegistry.TREASURIES, "treasury"),
            Map.entry(DeleteRegistry.MAIN_GROUPS, "main_group"),
            Map.entry(DeleteRegistry.SUB_GROUPS, "sub_group"),
            Map.entry(DeleteRegistry.EMPLOYEES, "employees"),
            Map.entry(DeleteRegistry.EXPENSES_DETAILS, "expenses_details"));

    private record Reference(String child, String column) {
    }

    private static Set<Reference> declaredBy(DeleteRule rule) {
        return rule.references().stream()
                .map(check -> new Reference(check.table(), check.column()))
                .collect(Collectors.toSet());
    }

    private static Set<Reference> inSchemaFor(String parent) {
        return SchemaForeignKeys.all().stream()
                .filter(key -> key.parent().equals(parent))
                .map(key -> new Reference(key.child(), key.column()))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("the migrations really were read - the rest of this class would pass vacuously")
    void theSchemaWasRead() {
        assertFalse(SchemaForeignKeys.all().isEmpty(), "no foreign keys parsed");
        assertFalse(inSchemaFor("employees").isEmpty(), "no keys found pointing at employees");
    }

    @Test
    @DisplayName("every non-cascading key pointing at a guarded table is declared")
    void nothingThatWouldRefuseADeleteIsUndeclared() {
        List<String> missing = new ArrayList<>();

        TABLE_BY_RULE.forEach((rule, table) -> {
            Set<Reference> declared = declaredBy(rule);
            for (Reference reference : inSchemaFor(table)) {
                if (!declared.contains(reference)) {
                    missing.add(table + " is pointed at by " + reference.child() + "."
                            + reference.column() + ", which does not cascade, and its rule does not declare it");
                }
            }
        });

        if (!missing.isEmpty()) {
            fail("Undeclared references:\n" + String.join("\n", missing));
        }
    }

    @Test
    @DisplayName("nothing is declared that the schema does not have")
    void noRuleRefusesADeleteForAKeyThatDoesNotExist() {
        List<String> unknown = new ArrayList<>();

        TABLE_BY_RULE.forEach((rule, table) -> {
            Set<Reference> inSchema = inSchemaFor(table);
            for (Reference reference : declaredBy(rule)) {
                if (!inSchema.contains(reference)) {
                    unknown.add(table + "'s rule declares " + reference.child() + "."
                            + reference.column() + ", which is not a non-cascading foreign key to it");
                }
            }
        });

        if (!unknown.isEmpty()) {
            fail("Declared references the schema does not have:\n" + String.join("\n", unknown));
        }
    }
}
