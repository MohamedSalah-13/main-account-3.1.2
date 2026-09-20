package com.hamza.account.authorization;

import com.hamza.controlsfx.language.LanguageManager;

import java.util.Arrays;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.stream.Collectors;

/**
 * What a permission is called on a screen.
 * <p>
 * <b>The name comes from the message bundles, not from the database.</b> It used to come from
 * {@code auth_permission.description}, and for 108 of the 162 keys that column holds the key itself:
 * {@code V1} seeds the permission rows with names and no descriptions, and
 * {@code JdbcRbacRepository.synchronizeCatalog} writes {@code description = permission_key} for a row
 * it inserts and then never updates it, so a migration is the only thing that can put Arabic there
 * and only a couple of dozen ever have. {@code PERMISSIONS_SQL} reads
 * {@code COALESCE(description, permission_key)}, which is the last step that turns a NULL into
 * {@code treasury.capital} on an Arabic screen - and the keys it happened to for include the owner's
 * capital, the opening balance, the forced close of a shift and a merge of two items.
 * <p>
 * So every key has a {@code permission.<key>} entry in all three bundles, pinned by
 * {@code PermissionCatalogArchitectureTest}, and adding a permission is a constant plus three lines
 * of translation - never a migration. The stored description is still read where the bundle has
 * nothing, so a database that does carry a hand-written Arabic sentence keeps showing it.
 */
public final class PermissionLabels {

    /** The prefix a permission's bundle entry carries. Not built anywhere else. */
    public static final String BUNDLE_PREFIX = "permission.";

    private PermissionLabels() {
    }

    /** The bundle entry a key's name lives under. */
    public static String bundleKey(String permissionKey) {
        return BUNDLE_PREFIX + permissionKey;
    }

    public static String describe(PermissionKey key) {
        return key == null ? "" : describe(key.value(), null);
    }

    /**
     * The name to show, most trustworthy source first: the bundle, then a stored description that
     * says more than the key does, then the key's own words. The last of those is a fallback and not
     * a feature - it renders {@code Total · Sales · Re · Show}, which is why the bundle is pinned.
     */
    public static String describe(String permissionKey, String storedDescription) {
        if (permissionKey == null || permissionKey.isBlank()) {
            return storedDescription == null ? "" : storedDescription;
        }
        String translated = translate(permissionKey);
        if (translated != null) return translated;
        if (storedDescription != null && !storedDescription.isBlank()
                && !storedDescription.equals(permissionKey)) {
            return storedDescription;
        }
        return words(permissionKey);
    }

    /**
     * The bundle's answer, or null when it has none. {@code LanguageManager.getString} answers a
     * missing key with the key itself, which would put {@code permission.sales.show} on the screen -
     * worse than the key it is meant to replace - so the bundle is asked whether it holds it.
     */
    private static String translate(String permissionKey) {
        try {
            String bundleKey = bundleKey(permissionKey);
            if (!LanguageManager.getInstance().getResourceBundle().containsKey(bundleKey)) return null;
            String value = LanguageManager.getInstance().getString(bundleKey);
            return value == null || value.isBlank() || value.equals(bundleKey) ? null : value;
        } catch (MissingResourceException e) {
            return null;
        }
    }

    private static String words(String permissionKey) {
        return Arrays.stream(permissionKey.split("\\."))
                .map(part -> Arrays.stream(part.split("_"))
                        .filter(word -> !word.isBlank())
                        .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT)
                                + word.substring(1).toLowerCase(Locale.ROOT))
                        .collect(Collectors.joining(" ")))
                .collect(Collectors.joining(" · "));
    }
}
