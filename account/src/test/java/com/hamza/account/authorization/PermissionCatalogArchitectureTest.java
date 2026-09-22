package com.hamza.account.authorization;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules about the permission catalogue itself, as opposed to who is guarded by it -
 * {@link AuthorizationArchitectureTest} is the other half.
 * <p>
 * Each of these was written for a defect that had shipped. Eight keys nobody read appeared in the
 * roles screen as tick boxes that granted nothing; 108 of the 162 showed their Latin key in an Arabic
 * screen, the owner's capital and the forced close of a shift among them; three screens wrote
 * {@code getId() == 1} themselves, which is the numbered-administrator test this system replaced; and
 * a risk derived from a key's last word answered {@code LOW} for paying an employee.
 */
class PermissionCatalogArchitectureTest {

    private static final Path SOURCE = Path.of("src", "main", "java");
    private static final Path CATALOGUE =
            SOURCE.resolve(Path.of("com", "hamza", "account", "authorization", "AppPermissions.java"));

    /** Read the same way {@code MessageKeyArchitectureTest} reads them: controlsfx does not open its resources. */
    private static final Path BUNDLE_DIR =
            Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
    private static final String[] BUNDLES = {
            "messages.properties", "messages_ar.properties", "messages_en.properties"};

    /**
     * Keys that are declared, granted by {@code V13} to a default role, and read by nothing.
     * <p>
     * They are not exempt because that is acceptable - they are exempt because deleting one takes an
     * ability away from a role a customer may have built on, and wiring one is a decision about what
     * the key should guard. Both answers belong in {@code docs/permissions-plan.md} §3 rather than in
     * a test's allow-list, and the list fails in both directions so it cannot quietly become fiction:
     * a key wired or removed has to leave here in the same change.
     */
    private static final Map<String, String> DECLARED_BUT_UNREAD = new TreeMap<>(Map.of(
            "ITEMS_ADD_EXCEL", "the Excel import asks items.create/items.update and never this key"));

    /**
     * The only files allowed to compare a user id against the recovery administrator's. The session
     * answers {@link UserSessionContext#isSystemAdministrator()} and {@code CurrentUser} passes it on;
     * the users screen protects that account from being edited or having its roles replaced, which is
     * about the row rather than about what its holder may do.
     */
    private static final Set<String> MAY_NAME_THE_ADMINISTRATOR_ID = Set.of(
            "UserSessionContext.java",
            "CurrentUser.java",
            "RbacService.java",
            "UserPermissionController.java",
            "UsersService.java",
            "LogApplication.java");

    /**
     * The receiver has to look like a user, not merely answer {@code getId()}: the first draft matched
     * any {@code getId() == 1} and flagged {@code AddNameController}, where the 1 is the default price
     * tier. The alternation covers both spellings that have been used - {@code usersVo.getId() == 1},
     * which {@link AuthorizationArchitectureTest} already hunted in six named files, and
     * {@code CurrentUser.get().getId() == 1}, which it could not see.
     */
    private static final Pattern ADMINISTRATOR_ID = Pattern.compile(
            "(?:CurrentUser\\.get(?:OrNull)?\\(\\)|currentUser\\(\\)|\\busersVo|\\busers?)\\.getId\\(\\)\\s*[!=]=\\s*1\\b"
                    + "|currentUserId\\(\\)\\s*[!=]=\\s*1\\b"
                    + "|\\buserId\\s*[!=]=\\s*1\\b");

    /** Line and block comments. A comment recording what the old test looked like is not the old test. */
    private static final Pattern COMMENTS = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    @Test
    void everyDeclaredKeyIsReadBySomething() throws IOException {
        Map<String, String> keysByConstant = declaredConstants();
        Set<String> referenced = new LinkedHashSet<>();
        for (Path file : javaSources()) {
            if (file.endsWith(CATALOGUE.getFileName()) && file.toString().contains("authorization")) continue;
            // Comments are stripped: a key named only in a javadoc is documented, not read, and
            // {@link #SOME_KEY} from a neighbouring class would otherwise vouch for it.
            String body = COMMENTS.matcher(Files.readString(file)).replaceAll(" ");
            for (String constant : keysByConstant.keySet()) {
                if (referenced.contains(constant)) continue;
                if (Pattern.compile("\\b" + constant + "\\b").matcher(body).find()) referenced.add(constant);
            }
        }

        List<String> unread = keysByConstant.keySet().stream()
                .filter(constant -> !referenced.contains(constant))
                .filter(constant -> !DECLARED_BUT_UNREAD.containsKey(constant))
                .toList();
        assertTrue(unread.isEmpty(), """
                These permission keys are declared in AppPermissions and read by nothing under \
                src/main/java, so they appear in the roles screen as tick boxes that change nothing \
                a user can do: %s
                Either give the key the guard it was declared for, or delete the constant - \
                synchronizeCatalog disables the row rather than deleting it, so no migration is \
                needed and existing grants stay dormant. If neither can be decided now, add it to \
                DECLARED_BUT_UNREAD with the reason.""".formatted(unread));

        List<String> fixed = DECLARED_BUT_UNREAD.keySet().stream()
                .filter(constant -> referenced.contains(constant) || !keysByConstant.containsKey(constant))
                .toList();
        assertTrue(fixed.isEmpty(),
                "DECLARED_BUT_UNREAD names keys that are now read, or no longer declared. "
                        + "Remove them from the list in the change that fixed them: " + fixed);
    }

    @Test
    void everyDeclaredKeyHasANameInAllThreeBundles() throws IOException {
        Map<String, Properties> bundles = bundles();
        Map<String, List<String>> missing = new LinkedHashMap<>();
        for (PermissionDefinition definition : AppPermissions.definitions()) {
            String bundleKey = PermissionLabels.bundleKey(definition.key().value());
            for (var entry : bundles.entrySet()) {
                String value = entry.getValue().getProperty(bundleKey);
                if (value == null || value.isBlank()) {
                    missing.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(bundleKey);
                }
            }
        }
        assertTrue(missing.isEmpty(), """
                Every permission needs a name in all three bundles, or the roles screen falls back to \
                the stored description - which for most keys is the Latin key itself, because V1 seeds \
                the rows without descriptions. Missing: %s""".formatted(missing));
    }

    /** A name is written for a person to read, so it must not be the key with dots in it. */
    @Test
    void aPermissionNameSaysMoreThanItsKey() throws IOException {
        Map<String, Properties> bundles = bundles();
        List<String> lazy = new ArrayList<>();
        for (PermissionDefinition definition : AppPermissions.definitions()) {
            String key = definition.key().value();
            String bundleKey = PermissionLabels.bundleKey(key);
            for (var entry : bundles.entrySet()) {
                String value = entry.getValue().getProperty(bundleKey, "").trim();
                if (value.equals(key) || value.equals(bundleKey) || value.length() < 4) {
                    lazy.add(entry.getKey() + ": " + bundleKey + " = " + value);
                }
            }
        }
        assertTrue(lazy.isEmpty(), "permission names that only repeat the key: " + lazy);
    }

    /**
     * A declared risk has to disagree with {@link AppPermissions}'s derivation, or it is a second
     * statement of the same thing - and the next person to change the derivation would move only one
     * of them.
     */
    @Test
    void aDeclaredRiskIsOnlyUsedWhereTheDerivationIsWrong() throws IOException {
        String catalogue = Files.readString(CATALOGUE);
        Matcher matcher = Pattern.compile(
                "key\\(\"([a-z][a-z0-9.]*)\", *PermissionRisk\\.([A-Z]+)\\)").matcher(catalogue);

        List<String> redundant = new ArrayList<>();
        int declared = 0;
        while (matcher.find()) {
            declared++;
            String key = matcher.group(1);
            PermissionRisk stated = PermissionRisk.valueOf(matcher.group(2));
            if (derivedRisk(key) == stated) redundant.add(key + " = " + stated);
        }
        assertTrue(declared > 0, "the risk-declaring overload is no longer used - delete it or this test");
        assertTrue(redundant.isEmpty(),
                "these keys declare the risk their last word already derives, so the same fact is "
                        + "written twice: " + redundant);
    }

    /**
     * The rule the section column lacked. It was a module derived from the key's first word against
     * a {@code switch} over eight words chosen in the controller, and nothing could tell the two
     * they disagreed - four of the eight matched nothing, so 134 of 162 keys read "general".
     */
    @Test
    void everyKeyBelongsToExactlyOneGroup() {
        List<String> unowned = new ArrayList<>();
        List<String> contested = new ArrayList<>();
        for (PermissionDefinition definition : AppPermissions.definitions()) {
            String key = definition.key().value();
            List<PermissionGroup> matches = PermissionGroup.allMatching(key);
            if (matches.isEmpty()) unowned.add(key);
            if (matches.size() > 1) contested.add(key + " -> " + matches);
        }
        assertTrue(unowned.isEmpty(),
                "these keys belong to no PermissionGroup, so the roles screen would file them under "
                        + "\"general\" - give one of the groups their prefix: " + unowned);
        assertTrue(contested.isEmpty(), """
                these keys match more than one group. The longest prefix wins at runtime, so the \
                screen is not wrong - but which section a key is under should be read off one \
                declaration, not worked out from prefix lengths: %s""".formatted(contested));
    }

    /**
     * A key a group claims by name has to exist. A typo there does nothing visible - the key falls
     * back to whichever prefix covers it and lands in the neighbouring section, which is the quiet
     * half of the defect this whole class exists for.
     */
    @Test
    void aKeyClaimedByNameIsARealKey() {
        Set<String> declared = AppPermissions.definitions().stream()
                .map(definition -> definition.key().value())
                .collect(java.util.stream.Collectors.toSet());
        List<String> unknown = Arrays.stream(PermissionGroup.values())
                .flatMap(group -> group.keys().stream().map(key -> group + " claims " + key))
                .filter(claim -> !declared.contains(claim.substring(claim.indexOf(" claims ") + 8)))
                .toList();
        assertTrue(unknown.isEmpty(),
                "a group names a key that AppPermissions does not declare, so the claim does nothing "
                        + "and the key is filed by prefix instead: " + unknown);
    }

    /** A group nothing is filed under is a section header for an empty list. */
    @Test
    void everyGroupOwnsAtLeastOneKey() {
        Set<PermissionGroup> used = AppPermissions.definitions().stream()
                .map(definition -> PermissionGroup.of(definition.key().value()).orElseThrow())
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(PermissionGroup.class)));
        List<PermissionGroup> empty = Arrays.stream(PermissionGroup.values())
                .filter(group -> !used.contains(group))
                .toList();
        assertTrue(empty.isEmpty(), "groups that own no permission: " + empty);
    }

    @Test
    void everyGroupHasALabelInAllThreeBundles() throws IOException {
        Map<String, Properties> bundles = bundles();
        Map<String, List<String>> missing = new LinkedHashMap<>();
        List<String> labelKeys = new ArrayList<>(
                Arrays.stream(PermissionGroup.values()).map(PermissionGroup::labelKey).toList());
        labelKeys.add(PermissionGroup.UNKNOWN_LABEL_KEY);
        for (String labelKey : labelKeys) {
            for (var entry : bundles.entrySet()) {
                String value = entry.getValue().getProperty(labelKey);
                if (value == null || value.isBlank()) {
                    missing.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(labelKey);
                }
            }
        }
        assertTrue(missing.isEmpty(), "group labels missing from the bundles: " + missing);
    }

    /**
     * The module written to {@code auth_permission.module_key} has to be a group's name, or the
     * column read back cannot be resolved to a label and the screen says "general" again.
     */
    @Test
    void theStoredModuleIsAlwaysAGroupName() {
        List<String> unresolvable = AppPermissions.definitions().stream()
                .filter(definition -> PermissionGroup.byName(definition.module()).isEmpty())
                .map(definition -> definition.key() + " -> " + definition.module())
                .toList();
        assertTrue(unresolvable.isEmpty(),
                "modules that no group answers to, so the screen would fall back to general: "
                        + unresolvable);
    }

    /** The switch that could not see the derivation must not come back. */
    @Test
    void theSectionLabelIsNotASwitchInAScreen() throws IOException {
        Path controller = SOURCE.resolve(
                Path.of("com", "hamza", "account", "controller", "users", "UserPermissionController.java"));
        String code = COMMENTS.matcher(Files.readString(controller)).replaceAll(" ");
        assertFalse(code.contains("case \"PURCHASES\"") || code.contains("case \"SETTINGS\""), """
                UserPermissionController is matching module names it chose itself again. Four of the \
                eight it used to list could never match what AppPermissions derives - PURCHASE is not \
                PURCHASES and SETTING is not SETTINGS - and 134 of 162 permissions read "general" for \
                as long as nobody opened the screen. The section belongs to PermissionGroup.""");
    }

    @Test
    void theAdministratorIdIsAskedInOnePlace() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaSources()) {
            String name = file.getFileName().toString();
            if (MAY_NAME_THE_ADMINISTRATOR_ID.contains(name)) continue;
            String code = COMMENTS.matcher(Files.readString(file)).replaceAll(" ");
            if (ADMINISTRATOR_ID.matcher(code).find()) offenders.add(name);
        }
        assertTrue(offenders.isEmpty(), """
                "Is this the administrator" is UserSessionContext.isSystemAdministrator(), reached \
                through CurrentUser. Comparing a user id against 1 in a screen is the \
                numbered-administrator test this permission system replaced, and it is wrong on any \
                install where the owner is not user number one: %s""".formatted(offenders));
    }

    /** Restates the derivation so the test can tell a needed declaration from a redundant one. */
    private static PermissionRisk derivedRisk(String key) {
        String action = key.substring(key.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        return switch (action) {
            case "DELETE", "BYPASS", "MANAGE", "POST", "RESTORE" -> PermissionRisk.CRITICAL;
            case "UPDATE", "ADD", "CREATE", "MOVE", "OVERRIDE" -> PermissionRisk.HIGH;
            case "INVOICE", "PRICE", "SALARY", "PROFIT" -> PermissionRisk.MEDIUM;
            default -> PermissionRisk.LOW;
        };
    }

    /** Constant name to key value, for every key {@code AppPermissions} declares apart from the markers. */
    private static Map<String, String> declaredConstants() {
        Map<String, String> result = new TreeMap<>();
        for (Field field : AppPermissions.class.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() != PermissionKey.class) continue;
            try {
                PermissionKey key = (PermissionKey) field.get(null);
                if (key.isMarker()) continue;
                result.put(field.getName(), key.value());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        assertEquals(AppPermissions.definitions().size(), result.size(),
                "definitions() and the declared constants disagree");
        return result;
    }

    private static List<Path> javaSources() throws IOException {
        try (var files = Files.walk(SOURCE)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static Map<String, Properties> bundles() throws IOException {
        Map<String, Properties> loaded = new LinkedHashMap<>();
        for (String bundle : BUNDLES) {
            Properties properties = new Properties();
            try (InputStream stream = Files.newInputStream(BUNDLE_DIR.resolve(bundle))) {
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            loaded.put(bundle, properties);
        }
        assertEquals(Arrays.asList(BUNDLES), new ArrayList<>(loaded.keySet()));
        return loaded;
    }
}
