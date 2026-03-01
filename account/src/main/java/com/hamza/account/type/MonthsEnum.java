package com.hamza.account.type;

import lombok.Getter;

import java.util.List;

@Getter
public enum MonthsEnum {
    JANUARY(1, "يناير"),
    FEBRUARY(2, "فبراير"),
    MARCH(3, "مارس"),
    APRIL(4, "أبريل"),
    MAY(5, "مايو"),
    JUNE(6, "يونيو"),
    JULY(7, "يوليو"),
    AUGUST(8, "أغسطس"),
    SEPTEMBER(9, "سبتمبر"),
    OCTOBER(10, "أكتوبر"),
    NOVEMBER(11, "نوفمبر"),
    DECEMBER(12, "ديسمبر");

    public static final List<MonthsEnum> MONTHS = List.of(JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER);
    private final int number;
    private final String arabicName;

    private MonthsEnum(int number, String arabicName) {
        this.number = number;
        this.arabicName = arabicName;
    }

    public static String getArabicName(int monthNumber) {
        return MONTHS.stream()
                .filter(month -> month.getNumber() == monthNumber)
                .map(MonthsEnum::getArabicName)
                .findFirst()
                .orElse("");
    }
}
