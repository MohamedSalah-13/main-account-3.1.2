package com.hamza.account.features.shortcuts;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCombination;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

/** Persists, validates and installs the main-sidebar accelerators. */
public final class SidebarShortcutManager {
    public enum ChangeResult { SAVED, DUPLICATE, RESERVED, INVALID }

    private static final Preferences PREFS = Preferences.userNodeForPackage(SidebarShortcutManager.class);
    private static final String PREFIX = "sidebar-shortcut.";
    private static final Set<String> RESERVED = Set.of(
            "Ctrl+C", "Ctrl+V", "Ctrl+X", "Ctrl+A", "Ctrl+Z", "Ctrl+Y", "Ctrl+N", "Ctrl+O", "Ctrl+P", "Alt+F4", "F10", "Ctrl+F10", "Ctrl+F12");
    private static WeakReference<Scene> sceneReference = new WeakReference<>(null);
    private static final EnumMap<SidebarShortcut, Button> buttons = new EnumMap<>(SidebarShortcut.class);
    private static final Set<KeyCombination> installed = new HashSet<>();

    private SidebarShortcutManager() { }

    public static void install(Scene scene, Map<SidebarShortcut, Button> sidebarButtons) {
        if (scene == null) return;
        sceneReference = new WeakReference<>(scene);
        buttons.clear();
        buttons.putAll(sidebarButtons);
        rebind();
    }

    public static String combination(SidebarShortcut shortcut) {
        return PREFS.get(PREFIX + shortcut.name(), shortcut.defaultCombination());
    }

    public static String displayName(SidebarShortcut shortcut) {
        Button button = buttons.get(shortcut);
        return button == null || button.getText() == null || button.getText().isBlank()
                ? shortcut.name() : button.getText();
    }

    public static ChangeResult change(SidebarShortcut shortcut, String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            PREFS.put(PREFIX + shortcut.name(), "");
            rebind();
            return ChangeResult.SAVED;
        }
        KeyCombination combination;
        try {
            combination = KeyCombination.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return ChangeResult.INVALID;
        }
        if (RESERVED.contains(combination.getName())) return ChangeResult.RESERVED;
        for (SidebarShortcut candidate : SidebarShortcut.values()) {
            String assigned = combination(candidate);
            if (!assigned.isBlank() && candidate != shortcut
                    && combination.getName().equals(KeyCombination.valueOf(assigned).getName())) {
                return ChangeResult.DUPLICATE;
            }
        }
        Scene scene = sceneReference.get();
        if (scene != null && scene.getAccelerators().containsKey(combination) && !installed.contains(combination)) {
            return ChangeResult.RESERVED;
        }
        PREFS.put(PREFIX + shortcut.name(), combination.getName());
        rebind();
        return ChangeResult.SAVED;
    }

    public static void reset(SidebarShortcut shortcut) {
        PREFS.remove(PREFIX + shortcut.name());
        rebind();
    }

    public static void resetAll() {
        for (SidebarShortcut shortcut : SidebarShortcut.values()) PREFS.remove(PREFIX + shortcut.name());
        rebind();
    }

    private static void rebind() {
        Scene scene = sceneReference.get();
        if (scene == null) return;
        installed.forEach(scene.getAccelerators()::remove);
        installed.clear();
        for (var entry : buttons.entrySet()) {
            String value = combination(entry.getKey());
            if (value.isBlank()) continue;
            KeyCombination key = KeyCombination.valueOf(value);
            if (!scene.getAccelerators().containsKey(key)) {
                scene.getAccelerators().put(key, entry.getValue()::fire);
                installed.add(key);
            }
        }
    }
}