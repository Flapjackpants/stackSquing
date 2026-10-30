package com.flapjackpants.stacksquing.core;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Port of src/parser.cpp. */
public final class MaterialListParser {
    private static final String PLACEMENT_MARKER = "Material List for placement '";

    private MaterialListParser() {
    }

    private record RowTail(boolean fulfilled, int partial) {
    }

    private static boolean isSeparatorLine(String line) {
        String trimmed = line.trim();
        return !trimmed.isEmpty() && trimmed.charAt(0) == '+' && trimmed.indexOf('-') >= 0;
    }

    private static boolean isHeaderRow(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("| item") && lower.contains("| total");
    }

    private static String parsePlacementName(String line) {
        int pos = line.indexOf(PLACEMENT_MARKER);
        if (pos < 0) {
            return null;
        }
        int start = pos + PLACEMENT_MARKER.length();
        int end = line.indexOf('\'', start);
        if (end < 0) {
            return null;
        }
        return line.substring(start, end);
    }

    /** Keeps only the digits of the field; -1 when there are none or the value overflows. */
    private static int parseIntField(String field) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        if (digits.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean allDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** The line up to and including its last '|'. */
    private static String rowBase(String line) {
        int pos = line.lastIndexOf('|');
        if (pos < 0) {
            int end = line.length();
            while (end > 0 && " \t\r\n".indexOf(line.charAt(end - 1)) >= 0) {
                end--;
            }
            return line.substring(0, end);
        }
        return line.substring(0, pos + 1);
    }

    private static RowTail parseRowTail(String line, int missing) {
        int pos = line.lastIndexOf('|');
        if (pos < 0) {
            return new RowTail(false, 0);
        }
        String tail = line.substring(pos + 1).trim();
        String[] tokens = tail.isEmpty() ? new String[0] : tail.split("\\s+");
        if (tokens.length == 0 || !tokens[0].equals("a")) {
            return new RowTail(false, 0);
        }
        if (tokens.length < 2 || !allDigits(tokens[1])) {
            return new RowTail(true, 0);
        }
        // More than 9 digits cannot be a sane partial amount: treat as fully gathered.
        int n = tokens[1].length() > 9 ? missing : Integer.parseInt(tokens[1]);
        if (n == 0) {
            return new RowTail(false, 0);
        }
        if (n >= missing) {
            return new RowTail(true, 0);
        }
        return new RowTail(false, n);
    }

    private static String buildRow(String line, boolean fulfilled, int partial) {
        String row = rowBase(line);
        if (fulfilled) {
            row += "a";
        } else if (partial > 0) {
            row += "a " + partial;
        }
        return row;
    }

    private static void writeItemLine(MaterialList list, MaterialItem item, String updated) {
        if (item.lineIndex >= 0 && item.lineIndex < list.rawLines.size()) {
            list.rawLines.set(item.lineIndex, updated);
        }
        item.originalLine = updated;
    }

    private static MaterialItem parseDataRow(String line) {
        if (line.indexOf('|') < 0 || isSeparatorLine(line) || isHeaderRow(line)) {
            return null;
        }

        // Mirror std::getline: no trailing empty field after the last '|'.
        List<String> fields = new ArrayList<>();
        for (String part : line.split("\\|", -1)) {
            fields.add(part.trim());
        }
        if (fields.get(fields.size() - 1).isEmpty()) {
            fields.remove(fields.size() - 1);
        }
        if (fields.size() < 5) {
            return null;
        }

        String name = fields.get(1);
        if (name.isEmpty() || name.equalsIgnoreCase("item")) {
            return null;
        }

        int total = parseIntField(fields.get(2));
        int missing = parseIntField(fields.get(3));
        int available = parseIntField(fields.get(4));
        if (total < 0 || missing < 0 || available < 0) {
            return null;
        }
        MaterialItem parsed = new MaterialItem();
        parsed.name = name;
        parsed.total = total;
        parsed.missing = missing;
        parsed.available = available;
        RowTail tail = parseRowTail(line, missing);
        parsed.fulfilled = tail.fulfilled();
        parsed.partial = tail.partial();
        return parsed;
    }

    public static MaterialList load(Path file) throws IOException {
        List<String> lines;
        try {
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (CharacterCodingException e) {
                lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            }
        } catch (IOException e) {
            throw new IOException("Could not open file: " + file, e);
        }

        MaterialList list = new MaterialList();
        list.filePath = file;
        int lineIndex = 0;
        for (String line : lines) {
            list.rawLines.add(line);
            if (list.placementName.isEmpty()) {
                String placement = parsePlacementName(line);
                if (placement != null) {
                    list.placementName = placement;
                }
            }
            MaterialItem item = parseDataRow(line);
            if (item != null) {
                item.lineIndex = lineIndex;
                item.originalLine = line;
                list.items.add(item);
            }
            lineIndex++;
        }

        if (list.items.isEmpty()) {
            throw new IOException("No material items found in file: " + file);
        }
        return list;
    }

    private static MaterialItem itemAt(MaterialList list, int itemIndex) {
        if (itemIndex < 0 || itemIndex >= list.items.size()) {
            throw new IllegalArgumentException("Invalid item index");
        }
        return list.items.get(itemIndex);
    }

    public static void setFulfilled(MaterialList list, int itemIndex, boolean fulfilled) {
        MaterialItem item = itemAt(list, itemIndex);
        item.fulfilled = fulfilled;
        item.partial = 0;
        writeItemLine(list, item, buildRow(item.originalLine, fulfilled, 0));
    }

    public static void setPartial(MaterialList list, int itemIndex, int amount) {
        MaterialItem item = itemAt(list, itemIndex);
        if (amount > 0 && amount >= item.missing) {
            item.fulfilled = true;
            item.partial = 0;
        } else if (amount <= 0) {
            item.fulfilled = false;
            item.partial = 0;
        } else {
            item.fulfilled = false;
            item.partial = amount;
        }
        writeItemLine(list, item, buildRow(item.originalLine, item.fulfilled, item.partial));
    }

    public static void save(MaterialList list) throws IOException {
        try {
            Files.writeString(list.filePath, String.join("\n", list.rawLines) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("Could not write file: " + list.filePath, e);
        }
    }
}
