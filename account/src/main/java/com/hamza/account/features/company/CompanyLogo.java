package com.hamza.account.features.company;

import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

/**
 * The picture stored in {@code company.comp_image}, as bytes rather than as a
 * {@link Image}.
 *
 * <p>The screen used to keep the logo in its {@code ImageView} and re-encode whatever
 * was on display into a PNG on every save. Two things went wrong with that. The
 * placeholder drawn when there is no logo is an image like any other, so saving after
 * "delete" wrote the placeholder into the column instead of clearing it; and a logo that
 * had never been touched was re-encoded — a JPEG photo turning into a PNG several times
 * its size — every time anyone pressed save. Bytes are the value here: they are read
 * once from the file, kept unchanged while the row is only being edited, and cleared to
 * {@code null} when the user clears the logo.
 *
 * <p>A picture chosen from disk is scaled down to {@link #MAX_DIMENSION} on its longest
 * side, and only then. The column is a {@code LONGBLOB} and would take a 12-megapixel
 * photo happily, but every invoice and every report loads it, so the size is worth
 * capping at the point where it enters. Anything already within that is stored in its
 * original format, untouched.
 */
public final class CompanyLogo {

    /** The longest side a stored logo may have; anything larger is scaled to fit. */
    public static final int MAX_DIMENSION = 512;

    /** What a scaled logo is re-encoded as. PNG keeps transparency, which a shop's logo usually has. */
    private static final String SCALED_FORMAT = "png";

    /** A guard against someone picking a RAW file or a video by mistake, before it is read into memory. */
    private static final long MAX_FILE_BYTES = 12L * 1024 * 1024;

    private final byte[] bytes;
    private final int width;
    private final int height;

    private CompanyLogo(byte[] bytes, int width, int height) {
        this.bytes = bytes;
        this.width = width;
        this.height = height;
    }

    /**
     * Reads a picture the user chose from disk, scaled to fit {@link #MAX_DIMENSION}.
     *
     * @throws IOException if the file is too large, unreadable, or not a picture at all —
     *                     the message is shown to the user, so it is in Arabic
     */
    public static CompanyLogo fromFile(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("لم يتم اختيار ملف صورة");
        }
        if (file.length() > MAX_FILE_BYTES) {
            throw new IOException("حجم الملف أكبر من 12 ميجابايت، اختر صورة أصغر");
        }
        return normalize(Files.readAllBytes(file.toPath()));
    }

    /**
     * The logo as the database holds it — the bytes are kept exactly as stored, so simply
     * opening the screen and saving it again does not rewrite the column.
     *
     * @return {@code null} when the row carries no logo, or carries bytes that no longer
     * decode; a logo nobody can display is not worth failing the whole screen over.
     */
    public static CompanyLogo fromStored(byte[] stored) {
        if (stored == null || stored.length == 0) {
            return null;
        }
        try {
            BufferedImage decoded = decode(stored);
            return new CompanyLogo(stored, decoded.getWidth(), decoded.getHeight());
        } catch (IOException e) {
            return null;
        }
    }

    /** The bytes to write to {@code comp_image}, or {@code null} for a logo that is not set. */
    public static byte[] bytesOf(CompanyLogo logo) {
        return logo == null ? null : logo.bytes;
    }

    /** Whether two logos — either of which may be absent — hold the same picture. */
    public static boolean sameBytes(CompanyLogo one, CompanyLogo other) {
        return Arrays.equals(bytesOf(one), bytesOf(other));
    }

    public byte[] bytes() {
        return bytes;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Image toFxImage() {
        return new Image(new ByteArrayInputStream(bytes));
    }

    /** What the screen prints under the preview: "٥١٢ × ٢٥٦ بكسل · ٤٨ ك.ب". */
    public String summary() {
        return "%d × %d بكسل · %s".formatted(width, height, humanSize(bytes.length));
    }

    private static CompanyLogo normalize(byte[] raw) throws IOException {
        BufferedImage decoded = decode(raw);
        int longest = Math.max(decoded.getWidth(), decoded.getHeight());
        if (longest <= MAX_DIMENSION) {
            return new CompanyLogo(raw, decoded.getWidth(), decoded.getHeight());
        }

        double scale = (double) MAX_DIMENSION / longest;
        int width = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
        BufferedImage scaled = scale(decoded, width, height);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(scaled, SCALED_FORMAT, out)) {
                throw new IOException("تعذر تجهيز الصورة للحفظ");
            }
            return new CompanyLogo(out.toByteArray(), width, height);
        }
    }

    private static BufferedImage decode(byte[] raw) throws IOException {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(raw));
        if (decoded == null) {
            // ImageIO answers null rather than throwing for a file no reader recognises.
            throw new IOException("الملف المختار ليس صورة صالحة");
        }
        return decoded;
    }

    private static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static String humanSize(int length) {
        if (length < 1024) {
            return length + " بايت";
        }
        if (length < 1024 * 1024) {
            return String.format(Locale.US, "%.0f ك.ب", length / 1024.0);
        }
        return String.format(Locale.US, "%.1f م.ب", length / (1024.0 * 1024.0));
    }
}
