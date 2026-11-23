package com.flapjackpants.stacksquing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DisplayOrderTest {
    @TempDir
    Path tmp;

    private GroupStore store() {
        return new GroupStore(tmp.resolve("groups.json"));
    }

    private static MaterialList list(Object[]... specs) {
        MaterialList list = new MaterialList();
        for (Object[] s : specs) {
            MaterialItem item = new MaterialItem();
            item.name = (String) s[0];
            item.total = (Integer) s[1];
            item.missing = (Integer) s[1];
            item.fulfilled = s.length > 2 && (Boolean) s[2];
            list.items.add(item);
        }
        return list;
    }

    private static Object[] spec(String name, int missing, boolean fulfilled) {
        return new Object[] {name, missing, fulfilled};
    }

    private static List<DisplayRow> build(MaterialList list, GroupStore store, QuantityColumn col, boolean hide) {
        return DisplayOrder.build(list, store, col, hide);
    }

    @Test
    void noGroupsListsAllItems() {
        MaterialList list = list(spec("Glow Lichen", 10, false), spec("Deepslate", 100, false),
                spec("Deepslate Bricks", 5, true), spec("Tuff", 50, false));
        List<DisplayRow> rows = build(list, store(), QuantityColumn.MISSING, true);
        assertEquals(4, rows.size());
        for (int i = 0; i < 4; i++) {
            DisplayRow r = rows.get(i);
            assertEquals(DisplayRowKind.ITEM, r.kind());
            assertEquals(i + 1, r.displayNumber());
            assertEquals(list.items.get(i).name, r.label());
            assertEquals(i, r.itemIndex());
        }
        assertTrue(rows.get(2).fulfilled());
    }

    @Test
    void groupBlockAfterUngrouped() {
        MaterialList list = list(spec("Glow Lichen", 10, false), spec("Deepslate", 100, false),
                spec("Deepslate Bricks", 5, true), spec("Tuff", 50, false));
        GroupStore store = store();
        store.groups().add(GroupStoreTest.group("deepslate", List.of("deepslate"), List.of(), true));
        List<DisplayRow> rows = build(list, store, QuantityColumn.MISSING, true);
        assertEquals(6, rows.size());
        assertEquals("Glow Lichen", rows.get(0).label());
        assertEquals(1, rows.get(0).displayNumber());
        assertEquals("Tuff", rows.get(1).label());
        assertEquals(2, rows.get(1).displayNumber());
        DisplayRow sep = rows.get(2);
        assertEquals(DisplayRowKind.SEPARATOR, sep.kind());
        assertEquals(0, sep.displayNumber());
        assertEquals("", sep.label());
        assertEquals(-1, sep.itemIndex());
        assertEquals(null, sep.group());
        assertEquals(null, sep.supergroup());
        DisplayRow header = rows.get(3);
        assertEquals(DisplayRowKind.GROUP_HEADER, header.kind());
        assertEquals(1, header.displayNumber());
        assertEquals("deepslate", header.label());
        assertEquals(100, header.quantity());
        assertEquals(store.groups().get(0), header.group());
        assertEquals("Deepslate", rows.get(4).label());
        assertEquals(3, rows.get(4).displayNumber());
        assertEquals("Deepslate Bricks", rows.get(5).label());
        assertEquals(4, rows.get(5).displayNumber());
        assertTrue(rows.get(5).fulfilled());
        assertEquals(DisplayRowKind.ITEM, rows.get(5).kind());
    }

    @Test
    void hideFulfilledGroups() {
        MaterialList list = list(spec("Glow Lichen", 10, false), spec("Deepslate", 100, true));
        GroupStore store = store();
        store.groups().add(GroupStoreTest.group("deepslate", List.of("deepslate"), List.of(), true));

        List<DisplayRow> hidden = build(list, store, QuantityColumn.MISSING, true);
        assertTrue(hidden.stream().noneMatch(r -> r.kind() == DisplayRowKind.GROUP_HEADER));

        List<DisplayRow> shown = build(list, store, QuantityColumn.MISSING, false);
        DisplayRow header = shown.stream().filter(r -> r.kind() == DisplayRowKind.GROUP_HEADER).findFirst()
                .orElseThrow();
        assertEquals(0, header.quantity());
    }

    @Test
    void supergroupClaimsGroups() {
        MaterialList list = list(spec("Glow Lichen", 10, false), spec("Deepslate", 100, false),
                spec("Tuff", 50, false));
        GroupStore store = store();
        store.groups().add(GroupStoreTest.group("deepslate", List.of("deepslate"), List.of(), true));
        store.groups().add(GroupStoreTest.group("tuff", List.of("tuff"), List.of(), true));
        store.groups().add(GroupStoreTest.group("deep tuff", List.of("deepslate", "tuff"), List.of(), true));
        store.supergroups().add(GroupStoreTest.supergroup("stone", List.of("deep", "tuff"), List.of(), true));

        List<DisplayRow> rows = build(list, store, QuantityColumn.MISSING, true);
        List<DisplayRow> blocks = rows.stream().filter(r -> r.kind() != DisplayRowKind.ITEM).toList();
        assertEquals(List.of(DisplayRowKind.SEPARATOR, DisplayRowKind.SUPERGROUP_HEADER,
                DisplayRowKind.GROUP_HEADER, DisplayRowKind.GROUP_HEADER, DisplayRowKind.GROUP_HEADER),
                blocks.stream().map(DisplayRow::kind).toList());
        DisplayRow superHeader = blocks.get(1);
        assertEquals("stone", superHeader.label());
        assertEquals(1, superHeader.displayNumber());
        assertEquals(150, superHeader.quantity());
        assertEquals(List.of("deep tuff", "deepslate", "tuff"),
                blocks.subList(2, 5).stream().map(DisplayRow::label).toList());
        assertEquals(DisplayRowKind.ITEM, rows.get(rows.size() - 1).kind());
    }

    @Test
    void quantityUsesColumn() {
        MaterialList list = list(spec("Glow Lichen", 10, false), spec("Deepslate", 100, false));
        list.items.get(1).available = 30;
        list.items.get(1).partial = 20;
        GroupStore store = store();
        store.groups().add(GroupStoreTest.group("deepslate", List.of("deepslate"), List.of(), true));

        List<DisplayRow> rows = build(list, store, QuantityColumn.NEED, true);
        for (DisplayRow r : rows) {
            if (r.kind() == DisplayRowKind.ITEM) {
                assertEquals(Format.quantityFor(list.items.get(r.itemIndex()), QuantityColumn.NEED), r.quantity());
            }
        }
        DisplayRow deep = rows.stream().filter(r -> "Deepslate".equals(r.label())).findFirst().orElseThrow();
        assertEquals(50, deep.quantity());
        DisplayRow header = rows.stream().filter(r -> r.kind() == DisplayRowKind.GROUP_HEADER).findFirst()
                .orElseThrow();
        assertEquals(50, header.quantity());
    }
}
