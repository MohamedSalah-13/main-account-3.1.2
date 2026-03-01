package com.hamza.controlsfx.others;

import java.time.Month;
import java.util.EnumMap;
import java.util.List;

public class MonthsWithArabic {

    /**
     * Retrieves a list of month names in Arabic.
     *
     * @return a list of Arabic month names in the order from January to December.
     */
    public static List<String> retrieveArabicMonths() {
        EnumMap<Month, String> monthStringEnumMap = new EnumMap<>(Month.class);
        monthStringEnumMap.put(Month.JANUARY, "يناير");
        monthStringEnumMap.put(Month.FEBRUARY, "فبراير");
        monthStringEnumMap.put(Month.MARCH, "مارس");
        monthStringEnumMap.put(Month.APRIL, "ابريل");
        monthStringEnumMap.put(Month.MAY, "مايو");
        monthStringEnumMap.put(Month.JUNE, "يونيو");
        monthStringEnumMap.put(Month.JULY, "يوليو");
        monthStringEnumMap.put(Month.AUGUST, "أغسطس");
        monthStringEnumMap.put(Month.SEPTEMBER, "سبتمبر");
        monthStringEnumMap.put(Month.OCTOBER, "أكتوبر");
        monthStringEnumMap.put(Month.NOVEMBER, "نوفمبر");
        monthStringEnumMap.put(Month.DECEMBER, "ديسمبر");
        return monthStringEnumMap.values().stream().toList();
    }
}
