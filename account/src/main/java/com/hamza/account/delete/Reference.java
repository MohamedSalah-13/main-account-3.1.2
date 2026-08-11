package com.hamza.account.delete;

/**
 * One kind of thing that still points at a row, and how many of them there are -
 * "12 فاتورة بيع".
 * <p>
 * The count is what makes a refusal answerable: "مستخدمة في فواتير" tells the
 * user to go looking, "مستخدمة في 12 فاتورة بيع" tells them what they are
 * looking for.
 */
public record Reference(String label, int count) {

    @Override
    public String toString() {
        return count + " " + label;
    }
}
