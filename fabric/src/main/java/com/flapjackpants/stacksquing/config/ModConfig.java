package com.flapjackpants.stacksquing.config;

import com.flapjackpants.stacksquing.core.QuantityColumn;
import com.flapjackpants.stacksquing.core.QuantityFormat;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    public String listFolder = "";            // blank = <game config dir>/litematica
    public String groupsFile = "";            // blank = ~/.config/stackSquing/groups.json
    public QuantityColumn column = QuantityColumn.MISSING;
    public QuantityFormat format = QuantityFormat.CSS;
    public boolean hideFulfilledGroups = true;
    public BookLayout layout = BookLayout.DOUBLE;

    private static String stringField(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            return e.getAsString();
        }
        return fallback;
    }

    private static boolean boolField(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsBoolean();
        }
        return fallback;
    }

    private static <E extends Enum<E>> E enumField(JsonObject o, String key, Class<E> type, E fallback) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            try {
                return Enum.valueOf(type, e.getAsString());
            } catch (IllegalArgumentException ex) {
                return fallback;
            }
        }
        return fallback;
    }

    public static ModConfig load(Path file) {
        ModConfig c = new ModConfig();
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return c;
            }
            JsonObject o = root.getAsJsonObject();
            c.listFolder = stringField(o, "listFolder", c.listFolder);
            c.groupsFile = stringField(o, "groupsFile", c.groupsFile);
            c.column = enumField(o, "column", QuantityColumn.class, c.column);
            c.format = enumField(o, "format", QuantityFormat.class, c.format);
            c.hideFulfilledGroups = boolField(o, "hideFulfilledGroups", c.hideFulfilledGroups);
            c.layout = enumField(o, "layout", BookLayout.class, c.layout);
        } catch (IOException | RuntimeException e) {
            // missing, unreadable or invalid: keep whatever defaults were set
        }
        return c;
    }

    public void save(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonObject o = new JsonObject();
        o.addProperty("listFolder", listFolder);
        o.addProperty("groupsFile", groupsFile);
        o.addProperty("column", column.name());
        o.addProperty("format", format.name());
        o.addProperty("hideFulfilledGroups", hideFulfilledGroups);
        o.addProperty("layout", layout.name());
        String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o);
        Files.writeString(file, text + "\n", StandardCharsets.UTF_8);
    }

    public Path resolvedListFolder(Path gameConfigDir) {
        String trimmed = listFolder == null ? "" : listFolder.trim();
        return trimmed.isEmpty() ? gameConfigDir.resolve("litematica") : expandHome(trimmed);
    }

    public Path resolvedGroupsFile() {
        String trimmed = groupsFile == null ? "" : groupsFile.trim();
        if (trimmed.isEmpty()) {
            return Path.of(System.getProperty("user.home"), ".config", "stackSquing", "groups.json");
        }
        return expandHome(trimmed);
    }

    static Path expandHome(String path) {
        String home = System.getProperty("user.home");
        if (path.equals("~")) {
            return Path.of(home);
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return Path.of(home, path.substring(2));
        }
        return Path.of(path);
    }
}
