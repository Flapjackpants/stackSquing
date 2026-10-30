package com.flapjackpants.stacksquing.core;

import java.util.ArrayList;
import java.util.List;

/** Port of App::build_help_text, reworded for the book (no column padding, so lines can wrap). */
public final class HelpText {
    private HelpText() {
    }

    private static void entry(List<String> out, String command, String description) {
        out.add("  " + command + "  " + description);
    }

    private static void detail(List<String> out, String text) {
        out.add(" ".repeat(6) + text);
    }

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("Quantity column:");
        entry(out, "total", "Show Total column");
        entry(out, "missing", "Show Missing column (default)");
        entry(out, "available", "Show Available column");
        entry(out, "need", "Show still need (missing − available)");
        out.add("");
        out.add("Quantity format:");
        entry(out, "raw", "Plain item counts");
        entry(out, "css", "Chests/stacks/items (per-item stack size: 64/16/1)");
        out.add("");
        out.add("Groups:");
        entry(out, "groups", "List saved groups and on/off state");
        entry(out, "group add <name> include:<term>[,<term>] [exclude:<term>[,<term>]]",
                "Create a group");
        detail(out, "(terms may contain spaces; commas separate terms)");
        entry(out, "group edit <name> include:<term>[,<term>] [exclude:<term>[,<term>]]",
                "Add terms to a group");
        entry(out, "group on <name>", "Enable a group");
        entry(out, "group off <name>", "Disable a group");
        entry(out, "group remove <name>", "Delete a saved group");
        entry(out, "group rename <old> <new>", "Rename a group");
        entry(out, "group order <name> <n>", "Set group display order");
        out.add("");
        out.add("Supergroups:");
        entry(out, "supergroups", "List saved supergroups and on/off state");
        entry(out, "supergroup add <name> include:<term>[,<term>] [exclude:<term>[,<term>]]",
                "Create a supergroup");
        detail(out, "(matches group names; terms may contain spaces)");
        entry(out, "supergroup edit <name> include:<term>[,<term>] [exclude:<term>[,<term>]]",
                "Add terms to a supergroup");
        entry(out, "supergroup on <name>", "Enable a supergroup");
        entry(out, "supergroup off <name>", "Disable a supergroup");
        entry(out, "supergroup remove <name>", "Delete a supergroup");
        entry(out, "supergroup rename <old> <new>", "Rename a supergroup");
        entry(out, "supergroup order <name> <n>", "Set supergroup display order");
        out.add("");
        out.add("Display:");
        entry(out, "overview", "Show all groups and supergroups (no items)");
        entry(out, "hidefulfilled on", "Hide groups when all items fulfilled (default)");
        entry(out, "hidefulfilled off", "Keep showing fully fulfilled groups");
        out.add("");
        out.add("Fulfillment:");
        entry(out, "fulfill <n>", "Mark item n fulfilled (saves to file)");
        entry(out, "fulfill <n>-<m>", "Mark items n through m inclusive");
        entry(out, "a <n> / a <n>-<m>", "Short for fulfill");
        entry(out, "a <n>, <amount>", "Fulfil part of item n (e.g. a 4, 450 or a 4, 2s+3)");
        entry(out, "unfulfill <n>", "Remove fulfillment marker");
        entry(out, "unfulfill <n>-<m>", "Unfulfill a range");
        entry(out, "u <n> / u <n>-<m>", "Short for unfulfill");
        entry(out, "u <n>, <amount>", "Un-add part of item n's fulfilled amount");
        out.add("");
        out.add("Navigation:");
        entry(out, "up / down", "Scroll one row");
        entry(out, "PageUp / PageDown", "Scroll one page");
        entry(out, "Mouse wheel", "Scroll");
        entry(out, "Up / Down arrow", "Previous / next command");
        entry(out, "Esc", "Close the book");
        out.add("");
        out.add("Other:");
        entry(out, "reload", "Re-read current file, or newer material_list_*.txt");
        detail(out, "in the same folder (Litematica timestamped exports)");
        entry(out, "latest", "Load the newest material_list_*.txt in the list folder");
        entry(out, "files", "List material lists in the list folder, newest first");
        entry(out, "open <n>", "Open file n from the files list");
        entry(out, "open <path>", "Open a file or directory (relative paths use the list folder)");
        entry(out, "help", "Show this help screen");
        entry(out, "quit, q, exit", "Close the book");
        return out;
    }
}
