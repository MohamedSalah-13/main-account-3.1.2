package com.hamza.account.table;

import com.hamza.account.config.AppIcon;

/**
 * The visual and action profile of a screen hosted by the shared table shell.
 *
 * <p>The shell is used by unrelated lists, so its default stays neutral. A feature
 * can opt into a persistent identity without reaching into the shell's FXML or
 * looking up controls by id. The party screens use this seam now; their add and
 * account screens can reuse the same root style class and icon later.</p>
 */
public record TableScreenProfile(
        boolean headerVisible,
        String title,
        String subtitle,
        String searchPrompt,
        String addButtonText,
        AppIcon icon,
        String rootStyleClass,
        boolean updateVisible,
        boolean deleteVisible,
        boolean selectionVisible) {

    public TableScreenProfile {
        title = valueOrEmpty(title);
        subtitle = valueOrEmpty(subtitle);
        searchPrompt = valueOrEmpty(searchPrompt);
        addButtonText = valueOrEmpty(addButtonText);
        rootStyleClass = valueOrEmpty(rootStyleClass);
        if (headerVisible && icon == null) {
            throw new IllegalArgumentException("A visible table header requires an icon");
        }
    }

    public static TableScreenProfile standard(String searchPrompt, String addButtonText) {
        return new TableScreenProfile(false, "", "", searchPrompt, addButtonText,
                null, "", true, true, true);
    }

    public static TableScreenProfile identified(String title, String subtitle,
                                                String searchPrompt, String addButtonText,
                                                AppIcon icon, String rootStyleClass,
                                                boolean updateVisible, boolean deleteVisible,
                                                boolean selectionVisible) {
        return new TableScreenProfile(true, title, subtitle, searchPrompt, addButtonText,
                icon, rootStyleClass, updateVisible, deleteVisible, selectionVisible);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
