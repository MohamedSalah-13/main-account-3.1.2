package com.hamza.account.manual;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the demo seed script into statements.
 * <p>
 * Small on purpose, and separate from {@link ManualDemoDatabase} so it can be tested without a
 * database. It understands only what the seed file uses: {@code --} line comments, statements
 * ending in {@code ;}, and semicolons inside quoted strings - an Arabic product name with a
 * semicolon in it would otherwise be cut in half and the halves executed as two broken statements.
 */
public final class ManualDemoScript {

    private ManualDemoScript() {
    }

    public static List<String> statements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inComment = false;

        for (int at = 0; at < script.length(); at++) {
            char character = script.charAt(at);

            if (inComment) {
                if (character == '\n') {
                    inComment = false;
                    current.append(character);
                }
                continue;
            }
            if (!inSingle && !inDouble && character == '-' && at + 1 < script.length()
                    && script.charAt(at + 1) == '-') {
                inComment = true;
                at++;
                continue;
            }
            // An escaped quote is part of the string, not the end of it.
            if ((inSingle || inDouble) && character == '\\' && at + 1 < script.length()) {
                current.append(character).append(script.charAt(at + 1));
                at++;
                continue;
            }
            if (character == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (character == '"' && !inSingle) {
                inDouble = !inDouble;
            }
            if (character == ';' && !inSingle && !inDouble) {
                add(statements, current);
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        add(statements, current);
        return statements;
    }

    private static void add(List<String> statements, CharSequence candidate) {
        String statement = candidate.toString().strip();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
    }
}
