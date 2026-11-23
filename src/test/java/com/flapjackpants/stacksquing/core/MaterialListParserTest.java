package com.flapjackpants.stacksquing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaterialListParserTest {
    private static final String SEP = "+----------------------------+-------+---------+-----------+";

    @TempDir
    Path tmp;

    private List<String> listLines(String... tails) {
        List<String> lines = new ArrayList<>();
        lines.add(SEP);
        lines.add("| Material List for placement 'test'                       |");
        lines.add(SEP);
        lines.add("| Item                       | Total | Missing | Available |");
        lines.add(SEP);
        for (String tail : tails) {
            lines.add("| Glow Lichen                |   985 |     985 |         0 |" + tail);
        }
        lines.add(SEP);
        return lines;
    }

    private Path writeList(String... tails) throws IOException {
        Path file = tmp.resolve("material_list_test.txt");
        Files.writeString(file, String.join("\n", listLines(tails)) + "\n", StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void readsPartialTail() throws IOException {
        MaterialList list = MaterialListParser.load(writeList("", "a", "a 450", "a 2000", "a 0"));
        assertEquals(5, list.items.size());
        boolean[] fulfilled = {false, true, false, true, false};
        int[] partial = {0, 0, 450, 0, 0};
        for (int i = 0; i < 5; i++) {
            assertEquals(fulfilled[i], list.items.get(i).fulfilled, "fulfilled " + i);
            assertEquals(partial[i], list.items.get(i).partial, "partial " + i);
        }
        assertEquals(535, Format.quantityFor(list.items.get(2), QuantityColumn.MISSING));
        assertEquals(985, Format.quantityFor(list.items.get(2), QuantityColumn.TOTAL));
    }

    @Test
    void readsPlacementAndSkipsHeaders() throws IOException {
        MaterialList list = MaterialListParser.load(writeList("", "a"));
        assertEquals("test", list.placementName);
        assertEquals(2, list.items.size());
        assertEquals("Glow Lichen", list.items.get(0).name);
        assertEquals(5, list.items.get(0).lineIndex);
        assertEquals(6, list.items.get(1).lineIndex);
        assertEquals(list.rawLines.get(5), list.items.get(0).originalLine);
        assertEquals(8, list.rawLines.size());
    }

    @Test
    void hugePartialCountsAsFulfilled() throws IOException {
        MaterialList list = MaterialListParser.load(writeList("a 1234567890"));
        assertTrue(list.items.get(0).fulfilled);
        assertEquals(0, list.items.get(0).partial);
    }

    @Test
    void setPartialWritesTail() throws IOException {
        MaterialList list = MaterialListParser.load(writeList(""));
        MaterialItem item = list.items.get(0);
        String row = "| Glow Lichen                |   985 |     985 |         0 |";

        MaterialListParser.setPartial(list, 0, 450);
        assertEquals(row + "a 450", list.rawLines.get(item.lineIndex));
        assertEquals(list.rawLines.get(item.lineIndex), item.originalLine);
        assertEquals(450, item.partial);

        MaterialListParser.setPartial(list, 0, 750);
        assertEquals(row + "a 750", list.rawLines.get(item.lineIndex));

        for (int amount : new int[] {985, 5000}) {
            MaterialListParser.setPartial(list, 0, 450);
            MaterialListParser.setPartial(list, 0, amount);
            assertEquals(row + "a", list.rawLines.get(item.lineIndex));
            assertTrue(item.fulfilled);
            assertEquals(0, item.partial);
        }

        for (int amount : new int[] {0, -5}) {
            MaterialListParser.setPartial(list, 0, 450);
            MaterialListParser.setPartial(list, 0, amount);
            assertEquals(row, list.rawLines.get(item.lineIndex));
            assertFalse(item.fulfilled);
            assertEquals(0, item.partial);
        }
    }

    @Test
    void invalidIndexThrows() throws IOException {
        MaterialList list = MaterialListParser.load(writeList(""));
        for (int index : new int[] {99, -1}) {
            assertThrows(IllegalArgumentException.class, () -> MaterialListParser.setPartial(list, index, 5));
            assertThrows(IllegalArgumentException.class, () -> MaterialListParser.setFulfilled(list, index, true));
        }
    }

    @Test
    void setFulfilledClearsPartial() throws IOException {
        MaterialList list = MaterialListParser.load(writeList("a 450"));
        MaterialItem item = list.items.get(0);
        assertEquals(450, item.partial);

        MaterialListParser.setFulfilled(list, 0, true);
        assertTrue(list.rawLines.get(item.lineIndex).endsWith("|a"));
        assertTrue(item.fulfilled);
        assertEquals(0, item.partial);

        MaterialListParser.setFulfilled(list, 0, false);
        assertTrue(list.rawLines.get(item.lineIndex).endsWith("|"));
        assertFalse(item.fulfilled);
    }

    @Test
    void partialRoundTrip() throws IOException {
        Path file = writeList("");
        MaterialList list = MaterialListParser.load(file);
        MaterialListParser.setPartial(list, 0, 450);
        MaterialListParser.save(list);

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.endsWith("\n"));
        List<String> expected = listLines("a 450");
        expected.set(5, list.rawLines.get(5));
        assertEquals(String.join("\n", expected) + "\n", content);
        assertTrue(content.contains("|a 450\n"));

        MaterialList reloaded = MaterialListParser.load(file);
        assertEquals(1, reloaded.items.size());
        assertEquals(450, reloaded.items.get(0).partial);
        assertFalse(reloaded.items.get(0).fulfilled);
    }

    @Test
    void emptyListThrows() throws IOException {
        Path file = writeList();
        IOException e = assertThrows(IOException.class, () -> MaterialListParser.load(file));
        assertTrue(e.getMessage().startsWith("No material items found in file:"), e.getMessage());
    }

    @Test
    void missingFileThrows() {
        IOException e = assertThrows(IOException.class,
                () -> MaterialListParser.load(tmp.resolve("nope.txt")));
        assertTrue(e.getMessage().startsWith("Could not open file:"), e.getMessage());
    }
}
