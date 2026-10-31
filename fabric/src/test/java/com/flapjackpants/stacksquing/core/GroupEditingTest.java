package com.flapjackpants.stacksquing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroupEditingTest {
    @TempDir
    Path tmp;

    private GroupStore store() {
        return new GroupStore(tmp.resolve("groups.json"));
    }

    private static ItemGroup group(String name, List<String> include, List<String> exclude) {
        return new ItemGroup(name, include, exclude, true, 0);
    }

    @Test
    void parseTermsTrimsDropsBlanksAndDuplicates() {
        assertEquals(List.of("oak", "Birch"), GroupEditing.parseTerms(" oak , Birch,,oak ,  "));
    }

    @Test
    void parseTermsEmpty() {
        assertTrue(GroupEditing.parseTerms(null).isEmpty());
        assertTrue(GroupEditing.parseTerms("").isEmpty());
    }

    @Test
    void joinTermsRoundTrips() {
        List<String> terms = List.of("a", "b c");
        assertEquals("a, b c", GroupEditing.joinTerms(terms));
        assertEquals(terms, GroupEditing.parseTerms(GroupEditing.joinTerms(terms)));
    }

    @Test
    void validateNameRejectsBlank() {
        assertEquals("Name cannot be empty.", GroupEditing.validateName(store(), null, "   "));
        assertEquals("Name cannot be empty.", GroupEditing.validateName(store(), "Oak", ""));
    }

    @Test
    void validateNameRejectsDuplicate() {
        GroupStore store = store();
        store.groups().add(group("Oak", List.of("oak"), List.of()));
        store.groups().add(group("Birch", List.of("birch"), List.of()));
        assertEquals("A group named Oak already exists.", GroupEditing.validateName(store, null, " Oak "));
        assertEquals("A group named Oak already exists.", GroupEditing.validateName(store, "Birch", "Oak"));
    }

    @Test
    void validateNameAcceptsOwnAndUnique() {
        GroupStore store = store();
        store.groups().add(group("Oak", List.of("oak"), List.of()));
        assertNull(GroupEditing.validateName(store, "Oak", "Oak"));
        assertNull(GroupEditing.validateName(store, null, "Spruce"));
        assertNull(GroupEditing.validateName(store, "Oak", "Spruce"));
    }

    @Test
    void togglePickAddsInclude() {
        GroupStore store = store();
        ItemGroup g = group("G", List.of("oak"), List.of());
        assertFalse(store.matches(g, "Birch Planks"));
        GroupEditing.togglePick(store, g, "Birch Planks");
        assertTrue(store.matches(g, "Birch Planks"));
        assertEquals(List.of("oak", "Birch Planks"), g.include);
    }

    @Test
    void togglePickRemovesExactInclude() {
        GroupStore store = store();
        ItemGroup g = group("G", List.of("birch planks", "oak"), List.of("x"));
        assertTrue(store.matches(g, "Birch Planks"));
        GroupEditing.togglePick(store, g, "Birch Planks");
        assertFalse(store.matches(g, "Birch Planks"));
        assertEquals(List.of("oak"), g.include);
        assertEquals(List.of("x"), g.exclude);
    }

    @Test
    void togglePickExcludesWhenBroaderTermMatches() {
        GroupStore store = store();
        ItemGroup g = group("G", List.of("wool"), List.of());
        GroupEditing.togglePick(store, g, "Red Wool");
        assertFalse(store.matches(g, "Red Wool"));
        assertTrue(store.matches(g, "Blue Wool"));
        assertEquals(List.of("wool"), g.include);
        assertEquals(List.of("Red Wool"), g.exclude);
    }

    @Test
    void togglePickRemovesExclude() {
        GroupStore store = store();
        ItemGroup g = group("G", List.of("wool"), List.of("red WOOL", "other"));
        assertFalse(store.matches(g, "Red Wool"));
        GroupEditing.togglePick(store, g, "Red Wool");
        assertTrue(store.matches(g, "Red Wool"));
        assertEquals(List.of("wool"), g.include);
        assertEquals(List.of("other"), g.exclude);
    }

    @Test
    void togglePickTwiceRestoresMembership() {
        GroupStore store = store();
        ItemGroup g = group("G", List.of("wool"), List.of());
        for (String item : List.of("Red Wool", "Oak Planks")) {
            boolean before = store.matches(g, item);
            GroupEditing.togglePick(store, g, item);
            assertEquals(!before, store.matches(g, item));
            GroupEditing.togglePick(store, g, item);
            assertEquals(before, store.matches(g, item));
        }
    }

    @Test
    void itemNamesDistinctInOrder() {
        MaterialList list = new MaterialList();
        for (String n : List.of("B", "A", "B", "C", "A")) {
            MaterialItem it = new MaterialItem();
            it.name = n;
            list.items.add(it);
        }
        assertEquals(List.of("B", "A", "C"), GroupEditing.itemNames(list));
        assertTrue(GroupEditing.itemNames(null).isEmpty());
    }

    @Test
    void copyIsDeep() {
        ItemGroup g = new ItemGroup("G", List.of("a"), List.of("b"), false, 4);
        ItemGroup c = GroupEditing.copy(g);
        assertEquals("G", c.name);
        assertEquals(List.of("a"), c.include);
        assertEquals(List.of("b"), c.exclude);
        assertFalse(c.enabled);
        assertEquals(4, c.order);
        c.include.add("z");
        c.exclude.clear();
        assertEquals(List.of("a"), g.include);
        assertEquals(List.of("b"), g.exclude);
    }
}
