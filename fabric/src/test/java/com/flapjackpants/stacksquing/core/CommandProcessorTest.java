package com.flapjackpants.stacksquing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flapjackpants.stacksquing.config.ModConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandProcessorTest {
    private static final String SEP = "+----------------------------+-------+---------+-----------+";

    @TempDir
    Path tmp;

    Path lists;
    Path oldFile;
    Path newFile;
    Instant base;
    ModConfig config;
    AtomicInteger saves;
    GroupStore groups;
    CommandProcessor cp;

    private static String row(String name, int n) {
        return String.format("| %-26s | %5d | %7d | %9d |", name, n, n, 0);
    }

    private static String content() {
        List<String> lines = new ArrayList<>();
        lines.add(SEP);
        lines.add("| Material List for placement 'test'                       |");
        lines.add(SEP);
        lines.add("| Item                       | Total | Missing | Available |");
        lines.add(SEP);
        lines.add(row("Glow Lichen", 985));
        lines.add(row("Deepslate", 837));
        lines.add(row("Deepslate Bricks", 712));
        lines.add(row("Tuff", 750));
        lines.add(SEP);
        return String.join("\n", lines) + "\n";
    }

    private Path writeList(Path file, Instant mtime) throws IOException {
        Files.writeString(file, content(), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.from(mtime));
        return file;
    }

    @BeforeEach
    void setUp() throws IOException {
        lists = Files.createDirectory(tmp.resolve("lists"));
        base = Instant.now().minusSeconds(3600);
        oldFile = writeList(lists.resolve("material_list_old.txt"), base);
        newFile = writeList(lists.resolve("material_list_new.txt"), base.plusSeconds(10));
        config = new ModConfig();
        saves = new AtomicInteger();
        groups = new GroupStore(tmp.resolve("groups.json"));
        cp = new CommandProcessor(config, saves::incrementAndGet, groups, () -> lists);
    }

    private List<String> fileLines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private long itemRows() {
        return cp.rows().stream().filter(r -> r.kind() == DisplayRowKind.ITEM).count();
    }

    @Test
    void onOpenLoadsNewest() {
        cp.onOpen();
        assertEquals("Loaded material_list_new.txt.", cp.status());
        assertEquals(4, itemRows());
        assertEquals("test", cp.title());
        assertEquals(newFile, cp.list().orElseThrow().filePath);
    }

    @Test
    void onOpenEmptyFolder() throws IOException {
        Path empty = Files.createDirectory(tmp.resolve("empty"));
        CommandProcessor c = new CommandProcessor(config, saves::incrementAndGet, groups, () -> empty);
        c.onOpen();
        assertTrue(c.status().startsWith("No material_list_*.txt found in"), c.status());
        assertTrue(c.rows().isEmpty());
        assertTrue(c.list().isEmpty());
        assertEquals("", c.title());
        assertTrue(c.execute("a 1"));
        assertEquals("No material list loaded. Type files or latest.", c.status());
    }

    @Test
    void filesAndOpenByNumber() {
        cp.onOpen();
        assertTrue(cp.execute("files"));
        assertEquals("Files", cp.panelTitle());
        assertEquals(2, cp.panelLines().size());
        assertTrue(cp.panelLines().get(0).endsWith("material_list_new.txt  (open)"), cp.panelLines().get(0));
        assertTrue(cp.panelLines().get(1).endsWith("material_list_old.txt"), cp.panelLines().get(1));
        assertEquals("Showing files. Type open <n> to load one.", cp.status());

        assertTrue(cp.execute("open 2"));
        assertEquals(oldFile, cp.list().orElseThrow().filePath);
        assertTrue(cp.panelLines().isEmpty());

        assertTrue(cp.execute("open 9"));
        assertEquals("Invalid file number: 9. Type files.", cp.status());
        assertTrue(cp.execute("open"));
        assertEquals("Usage: open <n> or open <path>", cp.status());
    }

    @Test
    void filesEmptyFolder() throws IOException {
        Path empty = Files.createDirectory(tmp.resolve("empty"));
        CommandProcessor c = new CommandProcessor(config, saves::incrementAndGet, groups, () -> empty);
        c.execute("files");
        assertTrue(c.status().startsWith("No material_list_*.txt found in"), c.status());
        assertTrue(c.panelLines().isEmpty());
    }

    @Test
    void openRelativePath() {
        cp.onOpen();
        assertTrue(cp.execute("open material_list_old.txt"));
        assertEquals(oldFile, cp.list().orElseThrow().filePath);
        assertEquals("Loaded newer export: material_list_old.txt (4 items, test).", cp.status());
    }

    @Test
    void fulfilRangeWritesFile() throws IOException {
        cp.onOpen();
        assertTrue(cp.execute("a 1"));
        assertTrue(fileLines(newFile).get(5).endsWith("|a"), fileLines(newFile).get(5));
        assertEquals("Marked item fulfilled.", cp.status());
        assertTrue(cp.execute("a 2-3"));
        assertEquals("Marked 2 items fulfilled.", cp.status());
        assertTrue(cp.execute("u 1"));
        assertEquals("Removed fulfillment.", cp.status());
        assertFalse(fileLines(newFile).get(5).endsWith("|a"));
        assertTrue(cp.execute("a 9"));
        assertEquals("Invalid item number: 9", cp.status());
        assertTrue(cp.execute("a 8-9"));
        assertEquals("No items in range 8-9", cp.status());
        assertTrue(cp.execute("a x"));
        assertEquals("Usage: fulfill <n> or fulfill <n>-<m>", cp.status());
        assertTrue(cp.execute("u x"));
        assertEquals("Usage: unfulfill <n> or unfulfill <n>-<m>", cp.status());
    }

    @Test
    void partialFulfil() throws IOException {
        cp.onOpen();
        assertTrue(cp.execute("a 1, 2s+3"));
        assertTrue(fileLines(newFile).get(5).endsWith("|a 131"), fileLines(newFile).get(5));
        assertEquals("Glow Lichen: 13s+22 left.", cp.status());
        assertTrue(cp.execute("u 1, 1"));
        assertTrue(fileLines(newFile).get(5).endsWith("|a 130"), fileLines(newFile).get(5));
        assertTrue(cp.execute("u 1, 5000"));
        assertEquals("Cleared partial fulfillment of Glow Lichen.", cp.status());
        assertTrue(cp.execute("a 1, 5000"));
        assertEquals("Marked Glow Lichen fulfilled.", cp.status());
    }

    @Test
    void partialFulfilBadAmount() {
        cp.onOpen();
        assertTrue(cp.execute("a 1, x"));
        assertEquals("Usage: a <n>, <amount> (e.g. a 4, 450 or a 4, 2s+3)", cp.status());
        assertTrue(cp.execute("u 1, x"));
        assertEquals("Usage: u <n>, <amount> (e.g. u 4, 450 or u 4, 2s+3)", cp.status());
        assertTrue(cp.execute("a 1-2, 5"));
        assertEquals("Usage: a <n>, <amount> (e.g. a 4, 450 or a 4, 2s+3)", cp.status());
    }

    @Test
    void externalChangeBlocksFulfil() throws IOException {
        cp.onOpen();
        String changed = content() + "\n";
        Files.writeString(newFile, changed, StandardCharsets.UTF_8);
        assertTrue(cp.execute("a 1"));
        assertEquals("File changed on disk. Run reload before fulfilling.", cp.status());
        assertEquals(changed, Files.readString(newFile, StandardCharsets.UTF_8));
        assertTrue(cp.execute("missing"));
        assertEquals("File changed on disk. Type reload (or latest for a newer export).", cp.status());
        assertTrue(cp.execute("reload"));
        assertEquals("Reloaded material_list_new.txt (4 items, test).", cp.status());
    }

    @Test
    void onOpenReportsExternalChange() throws IOException {
        cp.onOpen();
        Files.writeString(newFile, content() + "\n", StandardCharsets.UTF_8);
        cp.onOpen();
        assertEquals("File changed on disk. Type reload (or latest for a newer export).", cp.status());
    }

    @Test
    void groupCommands() throws IOException {
        cp.onOpen();
        assertTrue(cp.execute("group add deep include:deepslate"));
        assertEquals("Added group: deep", cp.status());
        assertTrue(Files.isRegularFile(tmp.resolve("groups.json")));
        assertTrue(cp.rows().stream().anyMatch(r -> r.kind() == DisplayRowKind.GROUP_HEADER
                && r.label().equals("deep")), cp.rows().toString());
        assertTrue(cp.execute("group add deep include:x"));
        assertEquals("Group already exists: deep", cp.status());
        assertTrue(cp.execute("group order deep x"));
        assertEquals("Usage: group order <name> <n>", cp.status());
        assertTrue(cp.execute("group order deep 5"));
        assertEquals("Updated group order.", cp.status());
        assertTrue(cp.execute("group off deep"));
        assertEquals("Disabled group: deep", cp.status());
        assertTrue(cp.rows().stream().noneMatch(r -> r.kind() == DisplayRowKind.GROUP_HEADER));
        assertTrue(cp.execute("group"));
        assertEquals("Unknown command. Type help.", cp.status());
        assertTrue(cp.execute("group add nofilter"));
        assertEquals("Group add requires include:term[,term]", cp.status());
        assertTrue(cp.execute("groups"));
        assertEquals("deep [off]  ", cp.status());
        assertTrue(cp.execute("group edit deep exclude:bricks"));
        assertEquals("Updated group: deep", cp.status());
        assertEquals(List.of("bricks"), groups.findGroup("deep").exclude);
        assertTrue(cp.execute("group rename deep dp"));
        assertEquals("Renamed group to: dp", cp.status());
        assertTrue(cp.execute("group remove dp"));
        assertEquals("Removed group: dp", cp.status());
        assertTrue(cp.execute("group on dp"));
        assertEquals("Group not found: dp", cp.status());
    }

    @Test
    void supergroupAndOverview() {
        cp.onOpen();
        cp.execute("group add deep include:deepslate");
        assertTrue(cp.execute("supergroup add stone include:deep"));
        assertEquals("Added supergroup: stone", cp.status());
        assertTrue(cp.execute("supergroup order stone x"));
        assertEquals("Usage: supergroup order <name> <n>", cp.status());
        assertTrue(cp.execute("overview"));
        assertEquals("Group overview", cp.panelTitle());
        assertTrue(cp.panelLines().contains("  stone [on]"), cp.panelLines().toString());
        assertEquals("Showing group overview. Use up/down to scroll.", cp.status());
    }

    @Test
    void settingsCommandsPersist() {
        cp.onOpen();
        assertTrue(cp.execute("total"));
        assertEquals(QuantityColumn.TOTAL, config.column);
        assertTrue(cp.execute("raw"));
        assertEquals(QuantityFormat.RAW, config.format);
        assertTrue(cp.execute("hidefulfilled off"));
        assertFalse(config.hideFulfilledGroups);
        assertEquals(3, saves.get());
        assertTrue(cp.execute("hidefulfilled maybe"));
        assertEquals("Usage: hidefulfilled on|off", cp.status());
        assertEquals(3, saves.get());
    }

    @Test
    void helpPanelAndScroll() {
        cp.onOpen();
        assertTrue(cp.execute("help"));
        assertEquals("Help", cp.panelTitle());
        assertFalse(cp.panelLines().isEmpty());
        assertEquals("Showing help. Use up/down to scroll.", cp.status());
        cp.execute("down");
        cp.execute("down");
        cp.execute("up");
        assertFalse(cp.panelLines().isEmpty());
        assertEquals(1, cp.scroll());
        cp.setScroll(-4);
        assertEquals(0, cp.scroll());
        cp.execute("missing");
        assertTrue(cp.panelLines().isEmpty());
    }

    @Test
    void quitClosesBook() {
        cp.onOpen();
        assertFalse(cp.execute("q"));
        assertFalse(cp.execute("quit"));
        assertFalse(cp.execute("exit"));
        assertTrue(cp.execute(""));
        assertTrue(cp.execute("   "));
        assertTrue(cp.execute("bogus"));
        assertEquals("Unknown command. Type help.", cp.status());
    }

    @Test
    void latestPicksNewestInFolder() throws IOException {
        cp.onOpen();
        writeList(lists.resolve("material_list_newer.txt"), base.plusSeconds(20));
        assertTrue(cp.execute("latest"));
        assertEquals("Loaded newer export: material_list_newer.txt (4 items, test).", cp.status());
        assertTrue(cp.execute("latest"));
        assertEquals("Reloaded material_list_newer.txt (4 items, test).", cp.status());
    }
}
