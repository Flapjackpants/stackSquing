package com.flapjackpants.stacksquing.core;

import com.flapjackpants.stacksquing.config.ModConfig;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;
import java.util.function.Supplier;

/** Port of App::handle_command and its helpers (src/app.cpp), without any terminal code. */
public final class CommandProcessor {
    private static final String FILE_CHANGED_WARNING =
            "File changed on disk. Type reload (or latest for a newer export).";
    private static final String FILE_CHANGED_BLOCK = "File changed on disk. Run reload before fulfilling.";

    private final ModConfig config;
    private final Runnable saveConfig;
    private final GroupStore groups;
    private final Supplier<Path> listFolder;

    private MaterialList list;
    private List<String> diskSnapshot = List.of();
    private String status = "";
    private List<String> panelLines = List.of();
    private String panelTitle = "";
    private int scroll;
    private List<DisplayRow> rows = List.of();

    public CommandProcessor(ModConfig config, Runnable saveConfig, GroupStore groups, Supplier<Path> listFolder) {
        this.config = config;
        this.saveConfig = saveConfig;
        this.groups = groups;
        this.listFolder = listFolder;
    }

    // ---- accessors ----

    public String status() {
        return status;
    }

    public String title() {
        if (list == null) {
            return "";
        }
        if (!list.placementName.isEmpty()) {
            return list.placementName;
        }
        return fileName(list.filePath);
    }

    public Optional<MaterialList> list() {
        return Optional.ofNullable(list);
    }

    public List<DisplayRow> rows() {
        return rows;
    }

    public List<String> panelLines() {
        return panelLines;
    }

    public String panelTitle() {
        return panelTitle;
    }

    public int scroll() {
        return scroll;
    }

    public void setScroll(int offset) {
        scroll = Math.max(0, offset);
    }

    // ---- lifecycle ----

    public void onOpen() {
        if (!groups.load()) {
            status = "Failed to parse groups config at " + groups.configPath();
        }
        if (list == null) {
            Path dir = listFolder.get();
            Optional<Path> newest = MaterialListPaths.newestInDir(dir);
            if (newest.isEmpty()) {
                status = "No material_list_*.txt found in " + dir;
            } else {
                StringBuilder error = new StringBuilder();
                if (loadListFromPath(newest.get(), error)) {
                    status = "Loaded " + fileName(list.filePath) + ".";
                } else {
                    status = error.toString();
                }
            }
        } else if (fileExternallyChanged()) {
            status = FILE_CHANGED_WARNING;
        }
        refresh();
    }

    public void refresh() {
        rows = list == null ? List.of()
                : DisplayOrder.build(list, groups, config.column, config.hideFulfilledGroups);
    }

    // ---- helpers ----

    private static String fileName(Path p) {
        Path n = p == null ? null : p.getFileName();
        return n == null ? "" : n.toString();
    }

    private static List<String> readLines(Path file) throws IOException {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        }
    }

    private void rememberDiskSnapshot() {
        diskSnapshot = new ArrayList<>(list.rawLines);
    }

    private boolean fileExternallyChanged() {
        if (list == null) {
            return false;
        }
        try {
            return !readLines(list.filePath).equals(diskSnapshot);
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    private boolean loadListFromPath(Path path, StringBuilder error) {
        try {
            Path resolved = MaterialListPaths.resolve(path);
            MaterialList loaded = MaterialListParser.load(resolved);
            list = loaded;
            rememberDiskSnapshot();
            scroll = 0;
            return true;
        } catch (IOException | RuntimeException e) {
            error.append(e.getMessage() == null ? e.toString() : e.getMessage());
            return false;
        }
    }

    private String reloadStatusMessage(boolean switched) {
        StringBuilder sb = new StringBuilder();
        sb.append(switched ? "Loaded newer export: " : "Reloaded ").append(fileName(list.filePath));
        sb.append(" (").append(list.items.size()).append(" items");
        if (!list.placementName.isEmpty()) {
            sb.append(", ").append(list.placementName);
        }
        return sb.append(").").toString();
    }

    private void setPanel(String title, List<String> lines) {
        panelTitle = title;
        panelLines = lines;
        scroll = 0;
    }

    private void clearPanel() {
        panelLines = List.of();
        panelTitle = "";
    }

    private List<DisplayRow> buildRows() {
        return DisplayOrder.build(list, groups, config.column, config.hideFulfilledGroups);
    }

    private boolean fulfillDisplayRange(int lo, int hi, boolean fulfilled) {
        if (fileExternallyChanged()) {
            status = FILE_CHANGED_BLOCK;
            return false;
        }
        TreeSet<Integer> indices = new TreeSet<>();
        for (DisplayRow r : buildRows()) {
            if (r.kind() == DisplayRowKind.ITEM && r.displayNumber() >= lo && r.displayNumber() <= hi
                    && r.itemIndex() >= 0) {
                indices.add(r.itemIndex());
            }
        }
        if (indices.isEmpty()) {
            status = lo == hi ? "Invalid item number: " + lo : "No items in range " + lo + "-" + hi;
            return false;
        }
        try {
            for (int index : indices) {
                MaterialListParser.setFulfilled(list, index, fulfilled);
            }
            MaterialListParser.save(list);
            rememberDiskSnapshot();
            String count = Integer.toString(indices.size());
            if (fulfilled) {
                status = indices.size() == 1 ? "Marked item fulfilled." : "Marked " + count + " items fulfilled.";
            } else {
                status = indices.size() == 1 ? "Removed fulfillment."
                        : "Removed fulfillment from " + count + " items.";
            }
            return true;
        } catch (IOException | RuntimeException e) {
            status = e.getMessage();
            return false;
        }
    }

    private boolean partialFulfillDisplayItem(int displayNumber, String amountText, boolean add) {
        if (fileExternallyChanged()) {
            status = FILE_CHANGED_BLOCK;
            return false;
        }
        int idx = -1;
        for (DisplayRow r : buildRows()) {
            if (r.kind() == DisplayRowKind.ITEM && r.displayNumber() == displayNumber && r.itemIndex() >= 0) {
                idx = r.itemIndex();
                break;
            }
        }
        if (idx < 0) {
            status = "Invalid item number: " + displayNumber;
            return false;
        }
        MaterialItem before = list.items.get(idx);
        OptionalInt amount = Format.parseAmount(amountText, StackSize.maxStackSize(before.name));
        if (amount.isEmpty()) {
            status = add ? "Usage: a <n>, <amount> (e.g. a 4, 450 or a 4, 2s+3)"
                    : "Usage: u <n>, <amount> (e.g. u 4, 450 or u 4, 2s+3)";
            return false;
        }
        try {
            long current = before.fulfilled ? before.missing : before.partial;
            long next = add ? current + amount.getAsInt() : current - amount.getAsInt();
            next = Math.max(0, Math.min(next, before.missing));
            MaterialListParser.setPartial(list, idx, (int) next);
            MaterialListParser.save(list);
            rememberDiskSnapshot();

            MaterialItem item = list.items.get(idx);
            if (item.fulfilled) {
                status = "Marked " + item.name + " fulfilled.";
            } else if (item.partial == 0) {
                status = "Cleared partial fulfillment of " + item.name + ".";
            } else {
                status = item.name + ": "
                        + Format.formatQuantity(item.missing - item.partial, config.format, item.name) + " left.";
            }
            return true;
        } catch (IOException | RuntimeException e) {
            status = e.getMessage();
            return false;
        }
    }

    private static List<String> splitCsv(String s) {
        List<String> parts = new ArrayList<>();
        for (String piece : s.split(",", -1)) {
            String t = piece.trim();
            if (!t.isEmpty()) {
                parts.add(t);
            }
        }
        return parts;
    }

    private static void appendUnique(List<String> dest, List<String> terms) {
        for (String t : terms) {
            if (!dest.contains(t)) {
                dest.add(t);
            }
        }
    }

    private static boolean applyFilterString(List<String> include, List<String> exclude, String text,
                                             boolean append) {
        boolean changed = false;
        int pos = 0;
        while (pos < text.length()) {
            int inc = text.indexOf("include:", pos);
            int exc = text.indexOf("exclude:", pos);
            int keyPos;
            boolean isInclude;
            if (inc >= 0 && (exc < 0 || inc < exc)) {
                keyPos = inc;
                isInclude = true;
            } else if (exc >= 0) {
                keyPos = exc;
                isInclude = false;
            } else {
                break;
            }
            int valueStart = keyPos + 8;
            int valueEnd = text.length();
            int nextInc = text.indexOf("include:", valueStart);
            int nextExc = text.indexOf("exclude:", valueStart);
            if (nextInc >= 0) {
                valueEnd = Math.min(valueEnd, nextInc);
            }
            if (nextExc >= 0) {
                valueEnd = Math.min(valueEnd, nextExc);
            }
            List<String> terms = splitCsv(text.substring(valueStart, valueEnd));
            List<String> target = isInclude ? include : exclude;
            if (append) {
                appendUnique(target, terms);
            } else {
                target.clear();
                target.addAll(terms);
            }
            changed = true;
            pos = valueEnd;
        }
        return changed;
    }

    /** Returns {lo, hi} or null when the text is not a number or range. */
    private static int[] parseDisplayRange(String s) {
        if (s.isEmpty()) {
            return null;
        }
        try {
            int dash = s.indexOf('-');
            if (dash < 0) {
                int v = Integer.parseInt(s);
                return new int[] {v, v};
            }
            if (dash == 0 || dash + 1 >= s.length()) {
                return null;
            }
            int lo = Integer.parseInt(s.substring(0, dash));
            int hi = Integer.parseInt(s.substring(dash + 1));
            return lo > hi ? new int[] {hi, lo} : new int[] {lo, hi};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean allDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static Path expandHome(String path) {
        String home = System.getProperty("user.home");
        if (path.equals("~")) {
            return Path.of(home);
        }
        if (path.startsWith("~/")) {
            return Path.of(home, path.substring(2));
        }
        return Path.of(path);
    }

    private void saveSettings() {
        try {
            saveConfig.run();
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            status = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        }
    }

    // ---- commands ----

    /** @return false when the book should close */
    public boolean execute(String command) {
        command = command.trim();
        if (command.isEmpty()) {
            return true;
        }
        String[] tokens = command.split("\\s+");
        String cmd = tokens[0];
        if (!cmd.equals("help") && !cmd.equals("overview") && !cmd.equals("files")
                && !cmd.equals("up") && !cmd.equals("down")) {
            clearPanel();
        }
        if (cmd.equals("q") || cmd.equals("quit") || cmd.equals("exit")) {
            return false;
        }
        handle(command, tokens);
        if (fileExternallyChanged() && !status.startsWith("File changed on disk")) {
            status = FILE_CHANGED_WARNING;
        }
        refresh();
        return true;
    }

    private void handle(String command, String[] tokens) {
        String cmd = tokens[0];
        int n = tokens.length;
        switch (cmd) {
            case "help" -> {
                setPanel("Help", HelpText.lines());
                status = "Showing help. Use up/down to scroll.";
                return;
            }
            case "overview" -> {
                setPanel("Group overview", Overview.build(groups));
                status = "Showing group overview. Use up/down to scroll.";
                return;
            }
            case "files" -> {
                showFiles();
                return;
            }
            case "total" -> {
                config.column = QuantityColumn.TOTAL;
                status = "Showing total column.";
                saveSettings();
                return;
            }
            case "missing" -> {
                config.column = QuantityColumn.MISSING;
                status = "Showing missing column.";
                saveSettings();
                return;
            }
            case "available" -> {
                config.column = QuantityColumn.AVAILABLE;
                status = "Showing available column.";
                saveSettings();
                return;
            }
            case "need" -> {
                config.column = QuantityColumn.NEED;
                status = "Showing need column (missing − available).";
                saveSettings();
                return;
            }
            case "raw" -> {
                config.format = QuantityFormat.RAW;
                status = "Showing raw quantities.";
                saveSettings();
                return;
            }
            case "css" -> {
                config.format = QuantityFormat.CSS;
                status = "Showing chest/stack/item quantities.";
                saveSettings();
                return;
            }
            case "up" -> {
                scroll = Math.max(0, scroll - 1);
                status = "";
                return;
            }
            case "down" -> {
                scroll += 1;
                status = "";
                return;
            }
            case "reload" -> {
                reload();
                return;
            }
            case "latest" -> {
                latest();
                return;
            }
            case "open" -> {
                open(tokens);
                return;
            }
            case "groups" -> {
                StringBuilder sb = new StringBuilder();
                for (ItemGroup g : groups.groups()) {
                    sb.append(g.name).append(g.enabled ? " [on]" : " [off]").append("  ");
                }
                if (groups.groups().isEmpty()) {
                    sb.append("No saved groups.");
                }
                status = sb.toString();
                return;
            }
            case "supergroups" -> {
                StringBuilder sb = new StringBuilder();
                for (Supergroup s : groups.supergroups()) {
                    sb.append(s.name).append(s.enabled ? " [on]" : " [off]").append("  ");
                }
                if (groups.supergroups().isEmpty()) {
                    sb.append("No saved supergroups.");
                }
                status = sb.toString();
                return;
            }
            default -> {
            }
        }
        if (cmd.equals("hidefulfilled") && n >= 2) {
            if (tokens[1].equals("on")) {
                config.hideFulfilledGroups = true;
                status = "Hiding fully fulfilled groups.";
                saveSettings();
            } else if (tokens[1].equals("off")) {
                config.hideFulfilledGroups = false;
                status = "Showing fully fulfilled groups.";
                saveSettings();
            } else {
                status = "Usage: hidefulfilled on|off";
            }
            return;
        }
        if (cmd.equals("group") && n >= 2) {
            groupCommand(command, tokens);
            return;
        }
        if (cmd.equals("supergroup") && n >= 2) {
            supergroupCommand(command, tokens);
            return;
        }
        boolean add = cmd.equals("fulfill") || cmd.equals("a");
        boolean remove = cmd.equals("unfulfill") || cmd.equals("u");
        if ((add || remove) && n >= 2) {
            fulfillCommand(tokens, add);
            return;
        }
        status = "Unknown command. Type help.";
    }

    private void showFiles() {
        Path dir = listFolder.get();
        List<Path> files = MaterialListPaths.listNewestFirst(dir);
        if (files.isEmpty()) {
            clearPanel();
            status = "No material_list_*.txt found in " + dir;
            return;
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            String line = "  " + (i + 1) + ") " + fileName(files.get(i));
            if (list != null && files.get(i).equals(list.filePath)) {
                line += "  (open)";
            }
            lines.add(line);
        }
        setPanel("Files", lines);
        status = "Showing files. Type open <n> to load one.";
    }

    private void reload() {
        if (list == null) {
            latest();
            return;
        }
        Path previous = list.filePath;
        Path dir = previous.toAbsolutePath().getParent();
        Optional<Path> newest = dir == null ? Optional.empty() : MaterialListPaths.newestInDir(dir);
        Path target = newest.isPresent() && !newest.get().equals(previous) ? newest.get() : previous;
        StringBuilder error = new StringBuilder();
        if (loadListFromPath(target, error)) {
            status = reloadStatusMessage(!target.equals(previous));
        } else {
            status = error.length() == 0 ? "Failed to reload material list." : error.toString();
        }
    }

    private void latest() {
        Path dir = listFolder.get();
        Optional<Path> newest = MaterialListPaths.newestInDir(dir);
        if (newest.isEmpty()) {
            status = "No material_list_*.txt found in " + dir;
            return;
        }
        Path previous = list == null ? null : list.filePath;
        StringBuilder error = new StringBuilder();
        if (loadListFromPath(newest.get(), error)) {
            status = reloadStatusMessage(!list.filePath.equals(previous));
        } else {
            status = error.length() == 0 ? "Failed to load latest material list." : error.toString();
        }
    }

    private void open(String[] tokens) {
        if (tokens.length < 2) {
            status = "Usage: open <n> or open <path>";
            return;
        }
        Path target;
        if (tokens.length == 2 && allDigits(tokens[1])) {
            List<Path> files = MaterialListPaths.listNewestFirst(listFolder.get());
            long number;
            try {
                number = Long.parseLong(tokens[1]);
            } catch (NumberFormatException e) {
                number = -1;
            }
            if (number < 1 || number > files.size()) {
                status = "Invalid file number: " + tokens[1] + ". Type files.";
                return;
            }
            target = files.get((int) number - 1);
        } else {
            String joined = String.join(" ", java.util.Arrays.copyOfRange(tokens, 1, tokens.length));
            Path p = expandHome(joined);
            target = p.isAbsolute() ? p : listFolder.get().resolve(p);
        }
        Path previous = list == null ? null : list.filePath;
        StringBuilder error = new StringBuilder();
        if (loadListFromPath(target, error)) {
            status = reloadStatusMessage(!list.filePath.equals(previous));
        } else {
            status = error.length() == 0 ? "Failed to open material list." : error.toString();
        }
    }

    private void fulfillCommand(String[] tokens, boolean add) {
        String verb = add ? "a" : "u";
        String longVerb = add ? "fulfill" : "unfulfill";
        String partialUsage = "Usage: " + verb + " <n>, <amount> (e.g. " + verb + " 4, 450 or " + verb + " 4, 2s+3)";
        String rest = String.join(" ", java.util.Arrays.copyOfRange(tokens, 1, tokens.length));
        int comma = rest.indexOf(',');
        if (comma >= 0) {
            String left = rest.substring(0, comma).trim();
            String right = rest.substring(comma + 1).trim();
            int[] range = parseDisplayRange(left);
            if (left.indexOf('-') >= 0 || range == null || right.isEmpty()) {
                status = partialUsage;
                return;
            }
            if (list == null) {
                status = "No material list loaded. Type files or latest.";
                return;
            }
            partialFulfillDisplayItem(range[0], right, add);
            return;
        }
        int[] range = parseDisplayRange(tokens[1]);
        if (range == null) {
            status = "Usage: " + longVerb + " <n> or " + longVerb + " <n>-<m>";
            return;
        }
        if (list == null) {
            status = "No material list loaded. Type files or latest.";
            return;
        }
        fulfillDisplayRange(range[0], range[1], add);
    }

    private void groupCommand(String command, String[] tokens) {
        int n = tokens.length;
        String sub = tokens[1];
        if ((sub.equals("on") || sub.equals("off")) && n >= 3) {
            ItemGroup g = groups.findGroup(tokens[2]);
            if (g == null) {
                status = "Group not found: " + tokens[2];
                return;
            }
            g.enabled = sub.equals("on");
            groups.save();
            status = (g.enabled ? "Enabled group: " : "Disabled group: ") + g.name;
        } else if (sub.equals("remove") && n >= 3) {
            status = groups.removeGroup(tokens[2]) ? "Removed group: " + tokens[2]
                    : "Group not found: " + tokens[2];
        } else if (sub.equals("order") && n >= 4) {
            ItemGroup g = groups.findGroup(tokens[2]);
            if (g == null) {
                status = "Group not found: " + tokens[2];
                return;
            }
            try {
                g.order = Integer.parseInt(tokens[3]);
            } catch (NumberFormatException e) {
                status = "Usage: group order <name> <n>";
                return;
            }
            groups.save();
            groups.load();
            status = "Updated group order.";
        } else if (sub.equals("add") && n >= 3) {
            ItemGroup g = new ItemGroup();
            g.name = tokens[2];
            g.enabled = true;
            g.order = groups.groups().size();
            if (!applyFilterString(g.include, g.exclude, command, false) || g.include.isEmpty()) {
                status = "Group add requires include:term[,term]";
                return;
            }
            if (groups.findGroup(g.name) != null) {
                status = "Group already exists: " + g.name;
                return;
            }
            status = groups.addGroup(g) ? "Added group: " + g.name : "Failed to save group.";
        } else if (sub.equals("edit") && n >= 4) {
            ItemGroup g = groups.findGroup(tokens[2]);
            if (g == null) {
                status = "Group not found: " + tokens[2];
                return;
            }
            if (!applyFilterString(g.include, g.exclude, command, true)) {
                status = "Group edit requires include:term[,term] and/or exclude:term[,term]";
                return;
            }
            status = groups.save() ? "Updated group: " + g.name : "Failed to save group.";
        } else if (sub.equals("rename") && n >= 4) {
            String oldName = tokens[2];
            String newName = tokens[3];
            if (groups.findGroup(oldName) == null) {
                status = "Group not found: " + oldName;
            } else if (groups.findGroup(newName) != null) {
                status = "Group already exists: " + newName;
            } else {
                status = groups.renameGroup(oldName, newName) ? "Renamed group to: " + newName
                        : "Failed to rename group.";
            }
        } else {
            status = "Usage: group on|off|remove|rename|order|add|edit ...";
        }
    }

    private void supergroupCommand(String command, String[] tokens) {
        int n = tokens.length;
        String sub = tokens[1];
        if ((sub.equals("on") || sub.equals("off")) && n >= 3) {
            Supergroup s = groups.findSupergroup(tokens[2]);
            if (s == null) {
                status = "Supergroup not found: " + tokens[2];
                return;
            }
            s.enabled = sub.equals("on");
            groups.save();
            status = (s.enabled ? "Enabled supergroup: " : "Disabled supergroup: ") + s.name;
        } else if (sub.equals("remove") && n >= 3) {
            status = groups.removeSupergroup(tokens[2]) ? "Removed supergroup: " + tokens[2]
                    : "Supergroup not found: " + tokens[2];
        } else if (sub.equals("order") && n >= 4) {
            Supergroup s = groups.findSupergroup(tokens[2]);
            if (s == null) {
                status = "Supergroup not found: " + tokens[2];
                return;
            }
            try {
                s.order = Integer.parseInt(tokens[3]);
            } catch (NumberFormatException e) {
                status = "Usage: supergroup order <name> <n>";
                return;
            }
            groups.save();
            groups.load();
            status = "Updated supergroup order.";
        } else if (sub.equals("add") && n >= 3) {
            Supergroup s = new Supergroup();
            s.name = tokens[2];
            s.enabled = true;
            s.order = groups.supergroups().size();
            if (!applyFilterString(s.include, s.exclude, command, false) || s.include.isEmpty()) {
                status = "Supergroup add requires include:term[,term]";
                return;
            }
            if (groups.findSupergroup(s.name) != null) {
                status = "Supergroup already exists: " + s.name;
                return;
            }
            status = groups.addSupergroup(s) ? "Added supergroup: " + s.name : "Failed to save supergroup.";
        } else if (sub.equals("edit") && n >= 4) {
            Supergroup s = groups.findSupergroup(tokens[2]);
            if (s == null) {
                status = "Supergroup not found: " + tokens[2];
                return;
            }
            if (!applyFilterString(s.include, s.exclude, command, true)) {
                status = "Supergroup edit requires include:term[,term] and/or exclude:term[,term]";
                return;
            }
            status = groups.save() ? "Updated supergroup: " + s.name : "Failed to save supergroup.";
        } else if (sub.equals("rename") && n >= 4) {
            String oldName = tokens[2];
            String newName = tokens[3];
            if (groups.findSupergroup(oldName) == null) {
                status = "Supergroup not found: " + oldName;
            } else if (groups.findSupergroup(newName) != null) {
                status = "Supergroup already exists: " + newName;
            } else {
                status = groups.renameSupergroup(oldName, newName) ? "Renamed supergroup to: " + newName
                        : "Failed to rename supergroup.";
            }
        } else {
            status = "Usage: supergroup on|off|remove|rename|order|add|edit ...";
        }
    }
}
