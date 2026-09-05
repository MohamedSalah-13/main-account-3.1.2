package com.hamza.account.features.items;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemImageContentTest {

    @Test
    void nullAndEmptyBytesMeanThereIsNoPicture() {
        assertFalse(ItemImageContent.of(null).isAvailable());
        assertFalse(ItemImageContent.of(new byte[0]).isAvailable());
    }

    @Test
    void storedPictureIsAvailableAndCannotBeMutatedThroughEitherArray() {
        byte[] source = {1, 2, 3};
        ItemImageContent content = ItemImageContent.of(source);
        source[0] = 9;
        byte[] read = content.bytes();
        read[1] = 9;

        assertTrue(content.isAvailable());
        assertArrayEquals(new byte[]{1, 2, 3}, content.bytes());
    }
}
