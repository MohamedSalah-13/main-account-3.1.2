package com.hamza.controlsfx.language;

import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.prefs.Preferences;

@Log4j2
public class LanguageManager {

    private static volatile LanguageManager instance;
    private ResourceBundle resourceBundle;
    private Locale currentLocale;

    public static final String BUNDLE_NAME = "i18n.messages";
    private static final String PREF_LANGUAGE_KEY = "app.language";

    private final Preferences preferences;

    public static final Locale ARABIC = new Locale("ar");
    public static final Locale ENGLISH = Locale.ENGLISH;

    private LanguageManager() {
        preferences = Preferences.userNodeForPackage(LanguageManager.class);
        loadSavedLanguage();
    }

    public static LanguageManager getInstance() {
        if (instance == null) {
            synchronized (LanguageManager.class) {
                if (instance == null) {
                    instance = new LanguageManager();
                }
            }
        }
        return instance;
    }

    private void loadSavedLanguage() {
        String savedLanguage = preferences.get(PREF_LANGUAGE_KEY, ARABIC.getLanguage());
        Locale locale = ENGLISH.getLanguage().equals(savedLanguage) ? ENGLISH : ARABIC;
        setLocale(locale);
    }

    public void setLocale(Locale locale) {
        this.currentLocale = locale;
        this.resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
        preferences.put(PREF_LANGUAGE_KEY, locale.getLanguage());
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public String getString(String key) {
        try {
            return getResourceBundle().getString(key);
        } catch (MissingResourceException e) {
            log.warn("Missing translation key: {}", key);
            return key;
        }
    }

    public String getString(String key, Object... args) {
        try {
            String message = getResourceBundle().getString(key);
            return String.format(message, args);
        } catch (MissingResourceException | IllegalFormatException e) {
            log.warn("Unable to resolve translation key: {}", key, e);
            return key;
        }
    }

    public void toggleLanguage() {
        if (ARABIC.equals(currentLocale)) {
            setLocale(ENGLISH);
        } else {
            setLocale(ARABIC);
        }
    }

    public boolean isArabic() {
        return ARABIC.equals(currentLocale);
    }

    public boolean isEnglish() {
        return ENGLISH.equals(currentLocale);
    }

    public String getTextDirection() {
        return isArabic() ? "rtl" : "ltr";
    }

    public ResourceBundle getResourceBundle() {
        if (resourceBundle == null) {
            resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, Objects.requireNonNullElse(currentLocale, ARABIC));
        }
        return resourceBundle;
    }

    public void reload() {
        ResourceBundle.clearCache();
        this.resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, Objects.requireNonNullElse(currentLocale, ARABIC));
    }
}
