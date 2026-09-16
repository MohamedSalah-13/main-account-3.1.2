package com.hamza.account.features.itemmerge;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Narrows the merge candidates to the text typed above them, <b>a whole group at a time</b>.
 * <p>
 * The rows exist to be compared with their neighbours: a group is the items that look like one
 * item, and the merge picks a target and sources from inside it. Filtering row by row would show
 * the flavour that matched "لافيستا" and hide the one written "لافسيتا" - which is exactly the
 * duplicate the screen was opened to find. So a group stays whole when any of its rows matches.
 */
public final class ItemMergeSearch {

    private ItemMergeSearch() {
    }

    /** The groups to keep, or {@code null} when there is no text and nothing is narrowed. */
    public static Set<String> groupsMatching(Collection<ItemMergeCandidate> candidates, String text) {
        String needle = normalise(text);
        if (needle.isEmpty()) {
            return null;
        }
        Set<String> groups = new HashSet<>();
        for (ItemMergeCandidate candidate : candidates) {
            if (contains(candidate.name(), needle) || contains(candidate.barcode(), needle)) {
                groups.add(candidate.groupKey());
            }
        }
        return groups;
    }

    private static boolean contains(String value, String needle) {
        return value != null && normalise(value).contains(needle);
    }

    private static String normalise(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
