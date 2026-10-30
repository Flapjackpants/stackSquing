package com.flapjackpants.stacksquing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaterialListPathsTest {
    @TempDir
    Path tmp;

    @Test
    void listsNewestFirst() throws IOException {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        Path a = Files.writeString(tmp.resolve("material_list_a.txt"), "a");
        Path b = Files.writeString(tmp.resolve("material_list_b.txt"), "b");
        Files.writeString(tmp.resolve("notes.txt"), "n");
        Files.createDirectory(tmp.resolve("material_list_c.txt"));
        Files.setLastModifiedTime(a, FileTime.from(t));
        Files.setLastModifiedTime(b, FileTime.from(t.plusSeconds(10)));

        assertEquals(List.of(b, a), MaterialListPaths.listNewestFirst(tmp));
        assertEquals(Optional.of(b), MaterialListPaths.newestInDir(tmp));
        assertEquals(b, MaterialListPaths.resolve(tmp));
        assertEquals(a, MaterialListPaths.resolve(a));
    }

    @Test
    void errors() throws IOException {
        Path empty = Files.createDirectory(tmp.resolve("empty"));
        IOException e1 = assertThrows(IOException.class, () -> MaterialListPaths.resolve(empty));
        assertTrue(e1.getMessage().startsWith("No material_list_*.txt found in directory:"), e1.getMessage());

        Path missing = tmp.resolve("missing");
        IOException e2 = assertThrows(IOException.class, () -> MaterialListPaths.resolve(missing));
        assertTrue(e2.getMessage().startsWith("Path is not a material list file or directory:"),
                e2.getMessage());

        Path file = Files.writeString(tmp.resolve("material_list_x.txt"), "x");
        assertTrue(MaterialListPaths.listNewestFirst(file).isEmpty());
        assertTrue(MaterialListPaths.listNewestFirst(missing).isEmpty());
        assertTrue(MaterialListPaths.newestInDir(empty).isEmpty());
    }

    @Test
    void nameFilter() {
        assertTrue(MaterialListPaths.isMaterialListName("material_list_2026.txt"));
        assertFalse(MaterialListPaths.isMaterialListName("material_list.txt"));
        assertFalse(MaterialListPaths.isMaterialListName("material_list_x.TXT"));
    }
}
