package com.hamza.account.features.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Converts stored JSON snapshots into stable, field-level rows for the audit UI. */
public final class AuditJsonDiff {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROOT = "$";

    private AuditJsonDiff() {
    }

    public static List<AuditDiffRow> compare(String beforeJson, String afterJson) {
        Map<String, String> before = flatten(beforeJson);
        Map<String, String> after = flatten(afterJson);
        LinkedHashSet<String> fields = new LinkedHashSet<>(before.keySet());
        fields.addAll(after.keySet());

        List<AuditDiffRow> rows = new ArrayList<>(fields.size());
        for (String field : fields) {
            boolean existedBefore = before.containsKey(field);
            boolean existsAfter = after.containsKey(field);
            String oldValue = before.getOrDefault(field, "");
            String newValue = after.getOrDefault(field, "");
            AuditDiffKind kind = !existedBefore ? AuditDiffKind.ADDED
                    : !existsAfter ? AuditDiffKind.REMOVED
                    : Objects.equals(oldValue, newValue) ? AuditDiffKind.UNCHANGED
                    : AuditDiffKind.CHANGED;
            rows.add(new AuditDiffRow(field, oldValue, newValue, kind));
        }
        return List.copyOf(rows);
    }

    private static Map<String, String> flatten(String value) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (value == null || value.isBlank()) return result;
        try {
            flatten(JSON.readTree(value), "", result);
        } catch (JsonProcessingException ignored) {
            result.put(ROOT, value);
        }
        return result;
    }

    private static void flatten(JsonNode node, String path, Map<String, String> result) {
        String safePath = path.isEmpty() ? ROOT : path;
        if (node == null || node.isNull() || node.isValueNode() || node.isArray()) {
            result.put(safePath, display(node));
            return;
        }
        if (node.isEmpty()) {
            result.put(safePath, "{}");
            return;
        }
        node.fields().forEachRemaining(field -> flatten(field.getValue(),
                path.isEmpty() ? field.getKey() : path + "." + field.getKey(), result));
    }

    private static String display(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        return node.isTextual() ? node.textValue() : node.toString();
    }
}
