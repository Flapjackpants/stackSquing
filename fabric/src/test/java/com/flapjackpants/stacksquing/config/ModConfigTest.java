package com.flapjackpants.stacksquing.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flapjackpants.stacksquing.core.QuantityColumn;
import com.flapjackpants.stacksquing.core.QuantityFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModConfigTest {
    @TempDir
    Path tmp;

    private static void assertDefaults(ModConfig c) {
        assertEquals("", c.listFolder);
        assertEquals("", c.groupsFile);
        assertEquals(QuantityColumn.MISSING, c.column);
        assertEquals(QuantityFormat.CSS, c.format);
        assertTrue(c.hideFulfilledGroups);
        assertEquals(BookLayout.DOUBLE, c.layout);
    }

    @Test
    void loadAndSave() throws IOException {
        assertDefaults(ModConfig.load(tmp.resolve("missing.json")));

        Path bad = Files.writeString(tmp.resolve("bad.json"), "{bad");
        assertDefaults(ModConfig.load(bad));

        Path partial = Files.writeString(tmp.resolve("partial.json"), "{\"column\":\"NOPE\",\"layout\":\"SINGLE\"}");
        ModConfig p = ModConfig.load(partial);
        assertEquals(QuantityColumn.MISSING, p.column);
        assertEquals(BookLayout.SINGLE, p.layout);
        assertEquals(QuantityFormat.CSS, p.format);

        ModConfig c = new ModConfig();
        c.listFolder = "~/lists";
        c.groupsFile = "/tmp/g.json";
        c.column = QuantityColumn.NEED;
        c.format = QuantityFormat.RAW;
        c.hideFulfilledGroups = false;
        c.layout = BookLayout.SINGLE;
        Path file = tmp.resolve("nested/dir/stacksquing.json");
        c.save(file);
        assertTrue(Files.isRegularFile(file));
        ModConfig r = ModConfig.load(file);
        assertEquals(c.listFolder, r.listFolder);
        assertEquals(c.groupsFile, r.groupsFile);
        assertEquals(c.column, r.column);
        assertEquals(c.format, r.format);
        assertEquals(c.hideFulfilledGroups, r.hideFulfilledGroups);
        assertEquals(c.layout, r.layout);
    }

    @Test
    void resolvesPaths() {
        String home = System.getProperty("user.home");
        ModConfig c = new ModConfig();
        Path cfgDir = tmp.resolve("cfg");
        assertEquals(cfgDir.resolve("litematica"), c.resolvedListFolder(cfgDir));
        c.listFolder = "   ";
        assertEquals(cfgDir.resolve("litematica"), c.resolvedListFolder(cfgDir));
        c.listFolder = "~/x";
        assertEquals(Path.of(home, "x"), c.resolvedListFolder(cfgDir));
        c.listFolder = "/abs/lists";
        assertEquals(Path.of("/abs/lists"), c.resolvedListFolder(cfgDir));

        assertEquals(Path.of(home, ".config", "stackSquing", "groups.json"), c.resolvedGroupsFile());
        c.groupsFile = "~/g.json";
        assertEquals(Path.of(home, "g.json"), c.resolvedGroupsFile());
        assertEquals(Path.of(home), ModConfig.expandHome("~"));
    }
}
