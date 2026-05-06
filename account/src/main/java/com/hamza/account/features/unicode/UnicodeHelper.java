package com.hamza.account.features.unicode;

public class UnicodeHelper {
    public static void main(String[] args) {
        // طباعة مجموعة من الرموز
        printUnicodeRange(0x2600, 0x26FF);  // رموز متنوعة
        printUnicodeRange(0x2700, 0x27BF);  // رموز الزخرفة
        printUnicodeRange(0x1F600, 0x1F64F); // إيموجي الوجوه
    }

    public static void printUnicodeRange(int start, int end) {
        System.out.println("\nUnicode Range: U+" +
                Integer.toHexString(start).toUpperCase() +
                " to U+" +
                Integer.toHexString(end).toUpperCase());

        for (int codePoint = start; codePoint <= end; codePoint++) {
            if (Character.isValidCodePoint(codePoint)) {
                char[] chars = Character.toChars(codePoint);
                System.out.printf("U+%04X: %s  ", codePoint, new String(chars));

                if ((codePoint - start + 1) % 5 == 0) {
                    System.out.println();
                }
            }
        }
        System.out.println();
    }
}
