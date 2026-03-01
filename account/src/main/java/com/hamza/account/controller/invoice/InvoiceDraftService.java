package com.hamza.account.controller.invoice;

import com.hamza.account.config.Configs;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Log4j2
public class InvoiceDraftService {

    // Legacy single-file draft (kept for backward compatibility and one-time migration)
    private static final String LEGACY_BUY_DRAFT_FILE = "invoice_draft_buy.json";

    // ================= Multi-draft (per invoice code and type) =================
    private static File getDraftsRootDir() {
        // ~/.myapp/invoice_drafts
        File root = Configs.getSecretKeyFile("invoice_drafts");
        if (!root.exists()) {
            root.mkdirs();
            Configs.setSecurePermissions(root);
        }
        return root;
    }

    private static String sanitize(String code) {
        if (code == null || code.isBlank()) return "unknown";
        // remove illegal filename characters and trim size
        String s = code.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (s.length() > 80) s = s.substring(0, 80);
        return s;
    }

    private static File getDraftFile(Type type, String code) {
        File typeDir = new File(getDraftsRootDir(), type.name().toLowerCase());
        if (!typeDir.exists()) {
            typeDir.mkdirs();
            Configs.setSecurePermissions(typeDir);
        }
        String safe = sanitize(code);
        return new File(typeDir, "draft_" + safe + ".json");
    }

    public static boolean exists(Type type, String code) {
        return getDraftFile(type, code).exists();
    }

    public static void clear(Type type, String code) {
        File f = getDraftFile(type, code);
        if (f.exists() && !f.delete()) {
            log.warn("Unable to delete draft file: {}", f.getAbsolutePath());
        }
    }

    public static void saveJson(Type type, @NotNull String code, @NotNull String jsonContent) throws IOException {
        File target = getDraftFile(type, code);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
            Configs.setSecurePermissions(parent);
        }

        // Atomic write: write to temp then move
        File tmp = File.createTempFile("invoice_draft_", ".json", parent);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            writer.write(jsonContent);
            writer.flush();
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static String loadJson(Type type, String code) throws IOException {
        File target = getDraftFile(type, code);
        if (!target.exists()) return null;
        return Files.readString(target.toPath(), StandardCharsets.UTF_8);
    }

    public static java.util.List<DraftSummary> listSummaries(Type type) {
        java.util.List<DraftSummary> list = new java.util.ArrayList<>();
        File typeDir = new File(getDraftsRootDir(), type.name().toLowerCase());
        if (!typeDir.exists()) return list;
        File[] files = typeDir.listFiles((dir, name) -> name.startsWith("draft_") && name.endsWith(".json"));
        if (files == null) return list;
        for (File f : files) {
            try {
                String nameFile = f.getName();
                String code = nameFile.substring("draft_".length(), nameFile.length() - ".json".length());
                String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                // parse minimal fields
                org.json.simple.parser.JSONParser parser = new org.json.simple.parser.JSONParser();
                Object obj = parser.parse(json);
                org.json.simple.JSONObject jo = (org.json.simple.JSONObject) obj;
                String invoiceDate = (String) jo.getOrDefault("invoiceDate", "");
                String nameVal = (String) jo.getOrDefault("name", "");
                double total = 0.0;
                Object tv = jo.get("total");
                if (tv instanceof Number) total = ((Number) tv).doubleValue();
                int itemsCount = 0;
                Object items = jo.get("items");
                if (items instanceof org.json.simple.JSONArray) itemsCount = ((org.json.simple.JSONArray) items).size();
                list.add(new DraftSummary(type, code, f, f.lastModified(), f.length(), invoiceDate, nameVal, total, itemsCount, json));
            } catch (Exception e) {
                log.warn("Failed to read draft summary for file {}: {}", f.getAbsolutePath(), e.getMessage());
            }
        }
        // sort by lastModified desc
        list.sort((a, b) -> Long.compare(b.lastModifiedMillis, a.lastModifiedMillis));
        return list;
    }

    // ================= Legacy helpers (single-draft buy) =================
    private static File getLegacyBuyDraftFile() {
        return Configs.getSecretKeyFile(LEGACY_BUY_DRAFT_FILE);
    }

    public static boolean legacyBuyExists() {
        return getLegacyBuyDraftFile().exists();
    }

    public static String legacyBuyLoad() throws IOException {
        File f = getLegacyBuyDraftFile();
        if (!f.exists()) return null;
        return Files.readString(f.toPath(), StandardCharsets.UTF_8);
    }

    public static void legacyBuyClear() {
        File f = getLegacyBuyDraftFile();
        if (f.exists() && !f.delete()) {
            log.warn("Unable to delete legacy draft file: {}", f.getAbsolutePath());
        }
    }

    public enum Type {BUY, SELL, BUY_RETURN, SELL_RETURN}

    // -------- Listing and summaries --------
    public static class DraftSummary {
        public final Type type;
        public final String code;
        public final File file;
        public final long lastModifiedMillis;
        public final long sizeBytes;
        public final String invoiceDate;
        public final String name;
        public final double total;
        public final int itemsCount;
        public final String json; // raw content for quick restore

        public DraftSummary(Type type, String code, File file, long lastModifiedMillis, long sizeBytes,
                            String invoiceDate, String name, double total, int itemsCount, String json) {
            this.type = type;
            this.code = code;
            this.file = file;
            this.lastModifiedMillis = lastModifiedMillis;
            this.sizeBytes = sizeBytes;
            this.invoiceDate = invoiceDate;
            this.name = name;
            this.total = total;
            this.itemsCount = itemsCount;
            this.json = json;
        }
    }
}
