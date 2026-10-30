package com.flapjackpants.stacksquing.core;

import java.util.ArrayList;
import java.util.List;

/** Port of src/overview.cpp. */
public final class Overview {
    private Overview() {
    }

    private static String stateTag(boolean enabled) {
        return enabled ? "[on]" : "[off]";
    }

    private static void addFilters(List<String> lines, List<String> include, List<String> exclude) {
        lines.add("      include: " + (include.isEmpty() ? "(none)" : String.join(", ", include)));
        if (!exclude.isEmpty()) {
            lines.add("      exclude: " + String.join(", ", exclude));
        }
    }

    public static List<String> build(GroupStore groups) {
        List<String> lines = new ArrayList<>();

        lines.add("Supergroups (" + groups.supergroups().size() + "):");
        if (groups.supergroups().isEmpty()) {
            lines.add("  No saved supergroups.");
        }
        for (Supergroup sg : groups.supergroups()) {
            lines.add("  " + sg.name + " " + stateTag(sg.enabled));
            addFilters(lines, sg.include, sg.exclude);
            List<String> members = new ArrayList<>();
            for (ItemGroup g : groups.groups()) {
                if (groups.matches(sg, g.name)) {
                    members.add(g.name + " " + stateTag(g.enabled));
                }
            }
            lines.add("      groups: " + (members.isEmpty() ? "none" : String.join(", ", members)));
        }

        lines.add("");
        lines.add("Groups (" + groups.groups().size() + "):");
        if (groups.groups().isEmpty()) {
            lines.add("  No saved groups.");
        }
        for (ItemGroup g : groups.groups()) {
            lines.add("  " + g.name + " " + stateTag(g.enabled));
            addFilters(lines, g.include, g.exclude);
            List<String> parents = new ArrayList<>();
            for (Supergroup sg : groups.supergroups()) {
                if (groups.matches(sg, g.name)) {
                    parents.add(sg.name);
                }
            }
            if (!parents.isEmpty()) {
                lines.add("      in: " + String.join(", ", parents));
            }
        }
        return lines;
    }
}
