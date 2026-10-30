package com.flapjackpants.stacksquing.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Port of src/display_order.cpp. */
public final class DisplayOrder {
    private DisplayOrder() {
    }

    private static final class Counters {
        int item = 1;
        int group = 1;
        int supergroup = 1;
    }

    private static DisplayRow separator() {
        return new DisplayRow(DisplayRowKind.SEPARATOR, 0, "", 0, -1, false, null, null);
    }

    private static int sumQuantity(Iterable<Integer> indices, MaterialList list, QuantityColumn column) {
        int total = 0;
        for (int idx : indices) {
            MaterialItem item = list.items.get(idx);
            if (item.fulfilled) {
                continue;
            }
            total += Format.quantityFor(item, column);
        }
        return total;
    }

    private static List<Integer> matchingIndices(MaterialList list, GroupStore groups, ItemGroup group) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < list.items.size(); i++) {
            if (groups.matches(group, list.items.get(i).name)) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static boolean fullyFulfilled(Iterable<Integer> indices, MaterialList list) {
        boolean any = false;
        for (int idx : indices) {
            any = true;
            if (!list.items.get(idx).fulfilled) {
                return false;
            }
        }
        return any;
    }

    private static boolean appendGroupBlock(List<DisplayRow> rows, MaterialList list, GroupStore groups,
                                            ItemGroup group, QuantityColumn column, boolean hideFulfilled,
                                            Counters n) {
        List<Integer> members = matchingIndices(list, groups, group);
        if (members.isEmpty()) {
            return false;
        }
        if (hideFulfilled && fullyFulfilled(members, list)) {
            return false;
        }
        rows.add(new DisplayRow(DisplayRowKind.GROUP_HEADER, n.group++, group.name,
                sumQuantity(members, list, column), -1, false, group, null));
        for (int idx : members) {
            MaterialItem item = list.items.get(idx);
            rows.add(new DisplayRow(DisplayRowKind.ITEM, n.item++, item.name,
                    Format.quantityFor(item, column), idx, item.fulfilled, group, null));
        }
        return true;
    }

    private static boolean itemInEnabledGroups(MaterialList list, GroupStore groups, int itemIndex) {
        for (ItemGroup g : groups.enabledGroupsSorted()) {
            if (groups.matches(g, list.items.get(itemIndex).name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean itemInEnabledSupergroups(MaterialList list, GroupStore groups, int itemIndex) {
        for (Supergroup s : groups.enabledSupergroupsSorted()) {
            for (ItemGroup member : groups.matchingMemberGroups(s)) {
                if (groups.matches(member, list.items.get(itemIndex).name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<DisplayRow> build(MaterialList list, GroupStore groups, QuantityColumn column,
                                         boolean hideFulfilledGroups) {
        List<DisplayRow> rows = new ArrayList<>();
        Counters n = new Counters();

        List<Supergroup> enabledSupergroups = groups.enabledSupergroupsSorted();
        List<ItemGroup> enabledGroups = groups.enabledGroupsSorted();
        Set<String> claimed = new HashSet<>();
        for (Supergroup s : enabledSupergroups) {
            for (ItemGroup member : groups.matchingMemberGroups(s)) {
                claimed.add(member.name);
            }
        }
        boolean anyBlocks = !enabledSupergroups.isEmpty() || !enabledGroups.isEmpty();

        boolean anyUngrouped = false;
        for (int i = 0; i < list.items.size(); i++) {
            if (itemInEnabledGroups(list, groups, i) || itemInEnabledSupergroups(list, groups, i)) {
                continue;
            }
            MaterialItem item = list.items.get(i);
            rows.add(new DisplayRow(DisplayRowKind.ITEM, n.item++, item.name,
                    Format.quantityFor(item, column), i, item.fulfilled, null, null));
            anyUngrouped = true;
        }

        if (anyUngrouped && anyBlocks) {
            rows.add(separator());
        }

        for (Supergroup s : enabledSupergroups) {
            List<ItemGroup> members = groups.matchingMemberGroups(s);
            Set<Integer> unique = new TreeSet<>();
            for (ItemGroup member : members) {
                unique.addAll(matchingIndices(list, groups, member));
            }
            if (unique.isEmpty()) {
                continue;
            }
            if (hideFulfilledGroups && fullyFulfilled(unique, list)) {
                continue;
            }

            int start = rows.size();
            rows.add(new DisplayRow(DisplayRowKind.SUPERGROUP_HEADER, n.supergroup++, s.name,
                    sumQuantity(unique, list, column), -1, false, null, s));

            boolean anyMemberShown = false;
            for (ItemGroup member : members) {
                if (appendGroupBlock(rows, list, groups, member, column, hideFulfilledGroups, n)) {
                    anyMemberShown = true;
                }
            }

            if (!anyMemberShown) {
                rows.subList(start, rows.size()).clear();
                n.supergroup--;
                continue;
            }
            if (anyBlocks) {
                rows.add(separator());
            }
        }

        for (ItemGroup g : enabledGroups) {
            if (claimed.contains(g.name)) {
                continue;
            }
            if (!appendGroupBlock(rows, list, groups, g, column, hideFulfilledGroups, n)) {
                continue;
            }
            if (anyBlocks) {
                rows.add(separator());
            }
        }

        if (!rows.isEmpty() && rows.get(rows.size() - 1).kind() == DisplayRowKind.SEPARATOR) {
            rows.remove(rows.size() - 1);
        }
        return rows;
    }
}
