package com.flapjackpants.stacksquing.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure helpers behind the group editor screen. */
public final class GroupEditing {
    private GroupEditing() {}

    /** Split on ',', trim each, drop blanks, drop case-insensitive duplicates keeping the first. null -> empty list. */
    public static List<String> parseTerms(String csv) {
        List<String> result = new ArrayList<>();
        if (csv == null) {
            return result;
        }
        for (String part : csv.split(",")) {
            String term = part.trim();
            if (term.isEmpty() || containsIgnoreCase(result, term)) {
                continue;
            }
            result.add(term);
        }
        return result;
    }

    /** String.join(", ", terms). */
    public static String joinTerms(List<String> terms) {
        return String.join(", ", terms);
    }

    /** Returns null when valid, else a message. originalName == null means a new group. */
    public static String validateName(GroupStore store, String originalName, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "Name cannot be empty.";
        }
        if (!trimmed.equals(originalName) && store.findGroup(trimmed) != null) {
            return "A group named " + trimmed + " already exists.";
        }
        return null;
    }

    /** Picker click; mutates g.include / g.exclude so that store.matches(g, itemName) flips. */
    public static void togglePick(GroupStore store, ItemGroup g, String itemName) {
        if (store.matches(g, itemName)) {
            g.include.removeIf(t -> t.equalsIgnoreCase(itemName));
            if (store.matches(g, itemName) && !containsIgnoreCase(g.exclude, itemName)) {
                g.exclude.add(itemName);
            }
        } else {
            g.exclude.removeIf(t -> t.equalsIgnoreCase(itemName));
            if (!store.matches(g, itemName) && !containsIgnoreCase(g.include, itemName)) {
                g.include.add(itemName);
            }
        }
    }

    /** Distinct item names (exact equals) from list.items in list order; empty list for null list. */
    public static List<String> itemNames(MaterialList list) {
        if (list == null) {
            return new ArrayList<>();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (MaterialItem item : list.items) {
            seen.add(item.name);
        }
        return new ArrayList<>(seen);
    }

    /** Deep copy (name, include, exclude, enabled, order). */
    public static ItemGroup copy(ItemGroup g) {
        return new ItemGroup(g.name, g.include, g.exclude, g.enabled, g.order);
    }

    private static boolean containsIgnoreCase(List<String> terms, String term) {
        for (String t : terms) {
            if (t.equalsIgnoreCase(term)) {
                return true;
            }
        }
        return false;
    }
}
