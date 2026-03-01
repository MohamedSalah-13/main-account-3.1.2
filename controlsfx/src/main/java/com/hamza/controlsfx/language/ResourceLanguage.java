package com.hamza.controlsfx.language;

import lombok.extern.log4j.Log4j2;

import java.util.Locale;
import java.util.ResourceBundle;


@Log4j2
public class ResourceLanguage {

    public static String getMonths(String key) {
        return getResourceValue("date", key);
    }

    public static String getProvinces(String key) {
        return getResourceValue("provinces", key);
    }

    public static String getWordExtension(String key) {
        return getResourceValue("wordExtension", key);
    }

    public static String getWordData(String key) {
        return getResourceValue("wordData", key);
    }

    public static String getWord(String key) {
        return getResourceValue("words", key);
    }

    public static String getResourceValue(String resourceName, String key) {
        try {
            return getResourceName(resourceName).getString(key);
//            return key;
        } catch (Exception e) {
            log.error(e.getClass().getName(), e.getCause() + " Resource Bundle");
            throw new RuntimeException(Error_Text_Show.NO_DATA_RESOURCE_BUNDLE, e);
        }
    }

    @SuppressWarnings("deprecation")
    private static ResourceBundle getResourceName(String name) {
        return ResourceBundle.getBundle(name, new Locale("ar", "AR"));
    }


}
