package com.hamza.account.features.report;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.productprofile.FeatureKey;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Which reports the hub lists for this reader, and in what groups.
 *
 * <p>A report is listed when every key its road asks is granted and its product feature is present -
 * both asked through the arguments, so this class has no session and no JavaFX. The search
 * matches the translated title and description, ignoring case, Arabic diacritics and the three forms of
 * alef, since a person searching for «الأرباح» types «الارباح».</p>
 */
public final class ReportCatalog {

    private ReportCatalog() {
    }

    /**
     * @param granted   whether the reader holds a permission - {@code AuthorizationGuard::isGranted}
     * @param enabled   whether the edition carries a feature - {@code ProductFeatureAccess::isEnabled}
     * @param search    what the reader typed, or blank for everything
     * @param translate a key to the text the reader sees
     */
    public static Map<ReportSection, List<ReportEntry>> visible(Predicate<PermissionKey> granted,
                                                                Predicate<FeatureKey> enabled, String search,
                                                                Function<String, String> translate) {
        String wanted = normalise(search == null ? "" : search);
        Map<ReportSection, List<ReportEntry>> sections = new LinkedHashMap<>();
        for (ReportSection section : ReportSection.values()) {
            List<ReportEntry> entries = Arrays.stream(ReportEntry.values())
                    .filter(entry -> entry.section() == section)
                    .filter(entry -> entry.permissions().stream()
                            .allMatch(key -> key.isPublicMarker() || granted.test(key)))
                    .filter(entry -> enabled.test(entry.feature()))
                    .filter(entry -> wanted.isEmpty()
                            || normalise(translate.apply(entry.titleKey())).contains(wanted)
                            || normalise(translate.apply(entry.descriptionKey())).contains(wanted))
                    .toList();
            if (!entries.isEmpty()) {
                sections.put(section, entries);
            }
        }
        return sections;
    }

    /** Lower case, no diacritics, one alef - what two spellings of one word have in common. */
    static String normalise(String text) {
        String decomposed = Normalizer.normalize(text.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
        StringBuilder plain = new StringBuilder(decomposed.length());
        for (int index = 0; index < decomposed.length(); index++) {
            char c = decomposed.charAt(index);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            plain.append(switch (c) {
                case 'أ', 'إ', 'آ' -> 'ا';
                case 'ى' -> 'ي';
                case 'ة' -> 'ه';
                default -> c;
            });
        }
        return plain.toString();
    }
}
