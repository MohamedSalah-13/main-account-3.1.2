package com.hamza.account.features.company;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the company logo does with bytes.
 * <p>
 * Two behaviours here were bugs before the logo became a value rather than whatever was
 * in the {@code ImageView}: a logo read from the database must come back out byte for
 * byte, so opening the settings tab and pressing save does not rewrite the column with a
 * re-encoded copy; and a picture that is not a picture must be refused at the point it is
 * chosen, rather than reaching the database and failing there.
 * <p>
 * No JavaFX toolkit is needed - only {@code toFxImage} touches one, and nothing here
 * calls it.
 */
class CompanyLogoTest {

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }

    private static File write(Path directory, String name, byte[] bytes) throws IOException {
        Path file = directory.resolve(name);
        Files.write(file, bytes);
        return file.toFile();
    }

    @Nested
    @DisplayName("Stored bytes")
    class Stored {

        @Test
        @DisplayName("a row with no logo has none")
        void absent() {
            assertNull(CompanyLogo.fromStored(null));
            assertNull(CompanyLogo.fromStored(new byte[0]));
        }

        @Test
        @DisplayName("bytes nobody can decode are treated as no logo, not as a failure")
        void unreadable() {
            assertNull(CompanyLogo.fromStored("not a picture".getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("a stored logo comes back byte for byte, so saving does not rewrite it")
        void unchanged() throws IOException {
            byte[] stored = png(300, 200);

            CompanyLogo logo = CompanyLogo.fromStored(stored);

            assertNotNull(logo);
            assertArrayEquals(stored, logo.bytes());
            assertEquals(300, logo.width());
            assertEquals(200, logo.height());
        }
    }

    @Nested
    @DisplayName("A picture chosen from disk")
    class FromFile {

        @Test
        @DisplayName("is stored as it is when it already fits")
        void small(@TempDir Path directory) throws IOException {
            byte[] original = png(200, 120);

            CompanyLogo logo = CompanyLogo.fromFile(write(directory, "logo.png", original));

            assertArrayEquals(original, logo.bytes());
            assertEquals(200, logo.width());
        }

        @Test
        @DisplayName("is scaled to the longest side, keeping its proportions")
        void scaled(@TempDir Path directory) throws IOException {
            CompanyLogo logo = CompanyLogo.fromFile(write(directory, "big.png", png(2000, 1000)));

            assertEquals(CompanyLogo.MAX_DIMENSION, logo.width());
            assertEquals(CompanyLogo.MAX_DIMENSION / 2, logo.height());
        }

        @Test
        @DisplayName("a tall picture is capped on its height")
        void tall(@TempDir Path directory) throws IOException {
            CompanyLogo logo = CompanyLogo.fromFile(write(directory, "tall.png", png(600, 1200)));

            assertEquals(CompanyLogo.MAX_DIMENSION, logo.height());
            assertEquals(CompanyLogo.MAX_DIMENSION / 2, logo.width());
        }

        @Test
        @DisplayName("is refused when it is not a picture at all")
        void notAPicture(@TempDir Path directory) throws IOException {
            File file = write(directory, "notes.txt", "just some text".getBytes(StandardCharsets.UTF_8));

            assertThrows(IOException.class, () -> CompanyLogo.fromFile(file));
        }

        @Test
        @DisplayName("is refused when there is no file")
        void missing(@TempDir Path directory) {
            File file = directory.resolve("nothing.png").toFile();

            assertThrows(IOException.class, () -> CompanyLogo.fromFile(file));
        }
    }

    @Nested
    @DisplayName("Comparing")
    class Comparing {

        @Test
        @DisplayName("two absent logos are the same, and an absent one is not a present one")
        void absence() throws IOException {
            CompanyLogo logo = CompanyLogo.fromStored(png(50, 50));

            assertTrue(CompanyLogo.sameBytes(null, null));
            assertFalse(CompanyLogo.sameBytes(logo, null));
            assertFalse(CompanyLogo.sameBytes(null, logo));
        }

        @Test
        @DisplayName("the same picture read twice is the same logo")
        void equal() throws IOException {
            byte[] bytes = png(50, 50);

            assertTrue(CompanyLogo.sameBytes(CompanyLogo.fromStored(bytes), CompanyLogo.fromStored(bytes)));
        }

        @Test
        @DisplayName("different pictures are different logos")
        void different() throws IOException {
            assertFalse(CompanyLogo.sameBytes(CompanyLogo.fromStored(png(50, 50)),
                    CompanyLogo.fromStored(png(60, 60))));
        }

        @Test
        @DisplayName("an absent logo is written to the column as null")
        void bytesOfAbsent() {
            assertNull(CompanyLogo.bytesOf(null));
        }
    }
}
