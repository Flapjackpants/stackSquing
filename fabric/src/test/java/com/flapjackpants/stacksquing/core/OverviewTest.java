package com.flapjackpants.stacksquing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OverviewTest {
    @TempDir
    Path tmp;

    private GroupStore store() {
        return new GroupStore(tmp.resolve("groups.json"));
    }

    @Test
    void empty() {
        assertEquals(List.of("Supergroups (0):", "  No saved supergroups.", "", "Groups (0):",
                "  No saved groups."), Overview.build(store()));
    }

    @Test
    void groupBasic() {
        GroupStore s = store();
        s.groups().add(GroupStoreTest.group("Oak", List.of("oak"), List.of(), true));
        s.groups().add(GroupStoreTest.group("Empty", List.of(), List.of(), false));
        assertEquals(List.of("Supergroups (0):", "  No saved supergroups.", "", "Groups (2):",
                "  Oak [on]", "      include: oak", "  Empty [off]", "      include: (none)"),
                Overview.build(s));
    }

    @Test
    void groupFilters() {
        GroupStore s = store();
        s.groups().add(GroupStoreTest.group("Wood", List.of("oak", "birch"), List.of("dark"), true));
        List<String> out = Overview.build(s);
        assertTrue(out.contains("      include: oak, birch"));
        assertTrue(out.contains("      exclude: dark"));
    }

    @Test
    void supergroupMembers() {
        GroupStore s = store();
        s.groups().add(GroupStoreTest.group("Glass", List.of("glass"), List.of(), true));
        s.groups().add(GroupStoreTest.group("Glazed", List.of("glazed"), List.of(), false));
        s.supergroups().add(GroupStoreTest.supergroup("smelting", List.of("gla"), List.of(), true));
        List<String> out = Overview.build(s);
        assertTrue(out.contains("  smelting [on]"));
        assertTrue(out.contains("      groups: Glass [on], Glazed [off]"));
    }

    @Test
    void supergroupNoMembers() {
        GroupStore s = store();
        s.groups().add(GroupStoreTest.group("Oak", List.of("oak"), List.of(), true));
        s.supergroups().add(GroupStoreTest.supergroup("metals", List.of("iron"), List.of(), true));
        assertTrue(Overview.build(s).contains("      groups: none"));
    }

    @Test
    void groupInSupergroup() {
        GroupStore s = store();
        s.groups().add(GroupStoreTest.group("Glass", List.of("glass"), List.of(), true));
        s.supergroups().add(GroupStoreTest.supergroup("smelting", List.of("glass"), List.of(), true));
        assertTrue(Overview.build(s).contains("      in: smelting"));
    }
}
