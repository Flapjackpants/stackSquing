package com.flapjackpants.stacksquing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroupStoreTest {
    @TempDir
    Path tmp;

    static ItemGroup group(String name, List<String> include, List<String> exclude, boolean enabled) {
        return new ItemGroup(name, include, exclude, enabled, 0);
    }

    static Supergroup supergroup(String name, List<String> include, List<String> exclude, boolean enabled) {
        return new Supergroup(name, include, exclude, enabled, 0);
    }

    private GroupStore store() {
        return new GroupStore(tmp.resolve("sub").resolve("groups.json"));
    }

    @Test
    void specificIncludeBeatsExclude() {
        GroupStore store = store();
        ItemGroup g = group("Light", List.of("light", "blue", "light blue"), List.of("light", "gray"), true);
        assertTrue(store.matches(g, "Light Blue Wool"));
        assertFalse(store.matches(g, "Light Gray Wool"));
        assertFalse(store.matches(g, "Light Weighted Pressure Plate"));
    }

    @Test
    void excludeStillApplies() {
        GroupStore store = store();
        ItemGroup stone = group("Stone", List.of("stone"), List.of("blackstone"), true);
        assertFalse(store.matches(stone, "Blackstone"));
        assertTrue(store.matches(stone, "Stone Bricks"));
        ItemGroup light = group("Light", List.of("light"), List.of("light"), true);
        assertFalse(store.matches(light, "Light Blue Wool"));
    }

    @Test
    void caseInsensitiveAndSupergroup() {
        GroupStore store = store();
        assertTrue(store.matches(group("g", List.of("Light Blue"), List.of("LIGHT"), true), "light blue wool"));
        assertTrue(store.matches(supergroup("s", List.of("light blue"), List.of("light"), true),
                "light blue wool"));
        assertFalse(store.matches(group("g", List.of(), List.of(), true), "light blue wool"));
    }

    @Test
    void saveLoadRoundTrip() {
        GroupStore store = store();
        store.groups().add(new ItemGroup("a", List.of("x", "y"), List.of("z"), true, 1));
        store.groups().add(new ItemGroup("b", List.of("q"), List.of(), false, 0));
        store.supergroups().add(new Supergroup("s", List.of("a"), List.of("b"), true, 3));
        assertTrue(store.save());

        GroupStore loaded = new GroupStore(store.configPath());
        assertTrue(loaded.load());
        assertEquals(2, loaded.groups().size());
        assertEquals("b", loaded.groups().get(0).name);
        assertEquals("a", loaded.groups().get(1).name);
        ItemGroup a = loaded.groups().get(1);
        assertEquals(List.of("x", "y"), a.include);
        assertEquals(List.of("z"), a.exclude);
        assertTrue(a.enabled);
        assertEquals(1, a.order);
        assertFalse(loaded.groups().get(0).enabled);
        assertEquals(1, loaded.supergroups().size());
        Supergroup s = loaded.supergroups().get(0);
        assertEquals("s", s.name);
        assertEquals(List.of("a"), s.include);
        assertEquals(List.of("b"), s.exclude);
        assertTrue(s.enabled);
        assertEquals(3, s.order);
    }

    @Test
    void saveMatchesCliFormat() throws IOException {
        GroupStore store = store();
        store.groups().add(new ItemGroup("Oak", List.of("oak"), List.of(), true, 0));
        assertTrue(store.save());
        assertEquals("{\n  \"groups\": [\n    {\n      \"enabled\": true,\n      \"exclude\": [],\n"
                + "      \"include\": [\n        \"oak\"\n      ],\n      \"name\": \"Oak\",\n"
                + "      \"order\": 0\n    }\n  ],\n  \"supergroups\": []\n}\n",
                Files.readString(store.configPath(), StandardCharsets.UTF_8));
    }

    @Test
    void loadEdgeCases() throws IOException {
        GroupStore missing = store();
        assertTrue(missing.load());
        assertTrue(missing.groups().isEmpty());
        assertTrue(missing.supergroups().isEmpty());

        Path bad = tmp.resolve("bad.json");
        Files.writeString(bad, "{not json");
        GroupStore broken = new GroupStore(bad);
        assertFalse(broken.load());
        assertTrue(broken.groups().isEmpty());

        Path legacy = tmp.resolve("legacy.json");
        Files.writeString(legacy,
                "{\"groups\":[{\"name\":\"Oak\",\"include\":[\"oak\"],\"enabled\":\"yes\",\"order\":\"x\"},"
                        + "{\"name\":\"\"}],"
                        + "\"supergroups\":[{\"name\":\"s\",\"member_groups\":[\"Oak\"]}]}");
        GroupStore old = new GroupStore(legacy);
        assertTrue(old.load());
        assertEquals(1, old.groups().size());
        assertFalse(old.groups().get(0).enabled);
        assertEquals(0, old.groups().get(0).order);
        assertEquals(1, old.supergroups().size());
        assertEquals(List.of("Oak"), old.supergroups().get(0).include);
    }

    @Test
    void mutatorsUpdateReferences() {
        GroupStore store = store();
        assertTrue(store.addGroup(group("Oak", List.of("oak"), List.of(), true)));
        assertFalse(store.addGroup(group("Oak", List.of("x"), List.of(), true)));
        assertTrue(store.addSupergroup(supergroup("wood", List.of("Oak", "birch"), List.of("Oak"), true)));
        assertFalse(store.addSupergroup(supergroup("wood", List.of(), List.of(), true)));

        assertTrue(store.addGroup(group("Stone", List.of("stone"), List.of(), true)));
        assertFalse(store.renameGroup("Oak", "Stone"));
        assertFalse(store.renameGroup("Oak", ""));
        assertFalse(store.renameGroup("Oak", "Oak"));
        assertFalse(store.renameGroup("Nope", "Other"));
        assertTrue(store.renameGroup("Oak", "Wood"));
        assertNotNull(store.findGroup("Wood"));
        assertEquals(List.of("Wood", "birch"), store.findSupergroup("wood").include);
        assertEquals(List.of("Wood"), store.findSupergroup("wood").exclude);

        assertTrue(store.removeGroup("Wood"));
        assertFalse(store.removeGroup("Wood"));
        assertEquals(List.of("birch"), store.findSupergroup("wood").include);
        assertTrue(store.findSupergroup("wood").exclude.isEmpty());

        assertTrue(store.renameSupergroup("wood", "trees"));
        assertFalse(store.renameSupergroup("trees", "trees"));
        assertTrue(store.removeSupergroup("trees"));
        assertFalse(store.removeSupergroup("trees"));
        assertEquals(null, store.findGroup("Oak"));
    }
}
