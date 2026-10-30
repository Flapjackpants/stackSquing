package com.flapjackpants.stacksquing.core;

import java.util.List;

/** Port of src/ui_text.cpp. */
public final class UiText {
    private UiText() {
    }

    public enum StatusKind { NORMAL, WARNING, ERROR }

    public enum PanelLineKind { BLANK, HEADING, ENTRY, DETAIL }

    public record PanelLineParts(PanelLineKind kind, String indent, String key, String rest) {
    }

    private static final List<String> ERROR_PREFIXES = List.of(
            "Usage:", "Unknown command", "Failed", "Invalid", "No items in range", "No material_list");
    private static final List<String> ERROR_SUBSTRINGS = List.of("not found", "already exists", "requires");

    public static StatusKind classifyStatus(String message) {
        if (message.startsWith("File changed on disk") || message.startsWith("Warning")) {
            return StatusKind.WARNING;
        }
        for (String p : ERROR_PREFIXES) {
            if (message.startsWith(p)) {
                return StatusKind.ERROR;
            }
        }
        for (String s : ERROR_SUBSTRINGS) {
            if (message.contains(s)) {
                return StatusKind.ERROR;
            }
        }
        return StatusKind.NORMAL;
    }

    public static PanelLineParts splitPanelLine(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        if (n == line.length()) {
            return new PanelLineParts(PanelLineKind.BLANK, "", "", "");
        }
        if (n == 0) {
            return new PanelLineParts(PanelLineKind.HEADING, "", line, "");
        }
        String indent = line.substring(0, n);
        String remainder = line.substring(n);
        if (n >= 6) {
            int sp = remainder.indexOf(' ');
            String token = sp < 0 ? remainder : remainder.substring(0, sp);
            if (!token.isEmpty() && token.endsWith(":")) {
                return new PanelLineParts(PanelLineKind.DETAIL, indent, token,
                        sp < 0 ? "" : remainder.substring(sp));
            }
            return new PanelLineParts(PanelLineKind.DETAIL, indent, "", remainder);
        }
        int gap = remainder.indexOf("  ");
        if (gap < 0) {
            return new PanelLineParts(PanelLineKind.ENTRY, indent, remainder, "");
        }
        return new PanelLineParts(PanelLineKind.ENTRY, indent, remainder.substring(0, gap),
                remainder.substring(gap));
    }

    public static String scrollIndicator(int first, int last, int total) {
        if (total <= 0) {
            return "";
        }
        return "rows " + first + "-" + last + " of " + total;
    }

    public static int clampScroll(int offset, int total, int pageRows) {
        int max = Math.max(0, total - Math.max(0, pageRows));
        return Math.max(0, Math.min(offset, max));
    }
}
