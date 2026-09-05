package com.hamza.account.features.items;

import java.util.Arrays;

/** The lazily loaded picture bytes for one item, or an explicit empty value. */
public final class ItemImageContent {

    private static final ItemImageContent EMPTY = new ItemImageContent(new byte[0]);

    private final byte[] bytes;

    private ItemImageContent(byte[] bytes) {
        this.bytes = bytes;
    }

    public static ItemImageContent of(byte[] bytes) {
        return bytes == null || bytes.length == 0
                ? EMPTY : new ItemImageContent(Arrays.copyOf(bytes, bytes.length));
    }

    public boolean isAvailable() {
        return bytes.length > 0;
    }

    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
