package com.flapjackpants.stacksquing.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Port of src/groups.cpp. */
public final class GroupStore {
    private Path configPath;
    private final List<ItemGroup> groups = new ArrayList<>();
    private final List<Supergroup> supergroups = new ArrayList<>();

    public GroupStore(Path configPath) {
        this.configPath = configPath;
    }

    public Path configPath() {
        return configPath;
    }

    public void setConfigPath(Path p) {
        this.configPath = p;
    }

    public List<ItemGroup> groups() {
        return groups;
    }

    public List<Supergroup> supergroups() {
        return supergroups;
    }

    private static boolean matchesFilter(List<String> include, List<String> exclude, String text) {
        if (include.isEmpty()) {
            return false;
        }
        String lowerName = text.toLowerCase(Locale.ROOT);
        List<String> matchedIncludes = new ArrayList<>();
        for (String term : include) {
            String lowerTerm = term.toLowerCase(Locale.ROOT);
            if (lowerName.contains(lowerTerm)) {
                matchedIncludes.add(lowerTerm);
            }
        }
        if (matchedIncludes.isEmpty()) {
            return false;
        }
        for (String term : exclude) {
            String lowerExc = term.toLowerCase(Locale.ROOT);
            if (!lowerName.contains(lowerExc)) {
                continue;
            }
            boolean overridden = false;
            for (String inc : matchedIncludes) {
                if (inc.length() > lowerExc.length() && inc.contains(lowerExc)) {
                    overridden = true;
                    break;
                }
            }
            if (!overridden) {
                return false;
            }
        }
        return true;
    }

    private static <T> void sortByOrderThenName(List<T> list, java.util.function.ToIntFunction<T> order,
                                                java.util.function.Function<T, String> name) {
        list.sort(Comparator.comparingInt(order).thenComparing(name));
    }

    private static void sortGroups(List<ItemGroup> list) {
        sortByOrderThenName(list, g -> g.order, g -> g.name);
    }

    private static void sortSupergroups(List<Supergroup> list) {
        sortByOrderThenName(list, g -> g.order, g -> g.name);
    }

    private static String stringField(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            return e.getAsString();
        }
        return "";
    }

    private static boolean boolField(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsBoolean();
        }
        return false;
    }

    private static int intField(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            try {
                return e.getAsBigDecimal().intValue();
            } catch (RuntimeException ex) {
                return 0;
            }
        }
        return 0;
    }

    private static void readStrings(JsonObject o, String key, List<String> out) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonArray()) {
            for (JsonElement term : e.getAsJsonArray()) {
                if (term.isJsonPrimitive() && term.getAsJsonPrimitive().isString()) {
                    out.add(term.getAsString());
                }
            }
        }
    }

    public boolean load() {
        groups.clear();
        supergroups.clear();
        if (!Files.exists(configPath)) {
            return true;
        }

        JsonElement doc;
        try {
            doc = JsonParser.parseString(Files.readString(configPath, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return false;
        }
        if (!doc.isJsonObject()) {
            return true;
        }
        JsonObject root = doc.getAsJsonObject();

        JsonElement groupsEl = root.get("groups");
        if (groupsEl != null && groupsEl.isJsonArray()) {
            for (JsonElement el : groupsEl.getAsJsonArray()) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject entry = el.getAsJsonObject();
                ItemGroup group = new ItemGroup();
                group.name = stringField(entry, "name");
                group.enabled = boolField(entry, "enabled");
                group.order = intField(entry, "order");
                readStrings(entry, "include", group.include);
                readStrings(entry, "exclude", group.exclude);
                if (!group.name.isEmpty()) {
                    groups.add(group);
                }
            }
        }

        JsonElement superEl = root.get("supergroups");
        if (superEl != null && superEl.isJsonArray()) {
            for (JsonElement el : superEl.getAsJsonArray()) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject entry = el.getAsJsonObject();
                Supergroup sg = new Supergroup();
                sg.name = stringField(entry, "name");
                sg.enabled = boolField(entry, "enabled");
                sg.order = intField(entry, "order");
                readStrings(entry, "include", sg.include);
                readStrings(entry, "exclude", sg.exclude);
                // Migrate legacy member_groups: exact group names become include terms.
                if (sg.include.isEmpty()) {
                    List<String> members = new ArrayList<>();
                    readStrings(entry, "member_groups", members);
                    for (String m : members) {
                        if (!m.isEmpty()) {
                            sg.include.add(m);
                        }
                    }
                }
                if (!sg.name.isEmpty()) {
                    supergroups.add(sg);
                }
            }
        }

        sortGroups(groups);
        sortSupergroups(supergroups);
        return true;
    }

    private static JsonObject entry(String name, boolean enabled, int order, List<String> include,
                                    List<String> exclude) {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        JsonArray exc = new JsonArray();
        exclude.forEach(exc::add);
        o.add("exclude", exc);
        JsonArray inc = new JsonArray();
        include.forEach(inc::add);
        o.add("include", inc);
        o.addProperty("name", name);
        o.addProperty("order", order);
        return o;
    }

    public boolean save() {
        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject doc = new JsonObject();
            JsonArray g = new JsonArray();
            for (ItemGroup group : groups) {
                g.add(entry(group.name, group.enabled, group.order, group.include, group.exclude));
            }
            doc.add("groups", g);
            JsonArray s = new JsonArray();
            for (Supergroup sg : supergroups) {
                s.add(entry(sg.name, sg.enabled, sg.order, sg.include, sg.exclude));
            }
            doc.add("supergroups", s);
            String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(doc);
            Files.writeString(configPath, text + "\n", StandardCharsets.UTF_8);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public boolean matches(ItemGroup g, String itemName) {
        return matchesFilter(g.include, g.exclude, itemName);
    }

    public boolean matches(Supergroup s, String groupName) {
        return matchesFilter(s.include, s.exclude, groupName);
    }

    public List<ItemGroup> enabledGroupsSorted() {
        List<ItemGroup> result = new ArrayList<>();
        for (ItemGroup g : groups) {
            if (g.enabled) {
                result.add(g);
            }
        }
        sortGroups(result);
        return result;
    }

    public List<Supergroup> enabledSupergroupsSorted() {
        List<Supergroup> result = new ArrayList<>();
        for (Supergroup s : supergroups) {
            if (s.enabled) {
                result.add(s);
            }
        }
        sortSupergroups(result);
        return result;
    }

    public List<ItemGroup> matchingMemberGroups(Supergroup s) {
        List<ItemGroup> result = new ArrayList<>();
        for (ItemGroup g : enabledGroupsSorted()) {
            if (matches(s, g.name)) {
                result.add(g);
            }
        }
        return result;
    }

    public boolean addGroup(ItemGroup g) {
        if (findGroup(g.name) != null) {
            return false;
        }
        groups.add(g);
        return save();
    }

    public boolean removeGroup(String name) {
        if (!groups.removeIf(g -> g.name.equals(name))) {
            return false;
        }
        for (Supergroup s : supergroups) {
            s.include.removeIf(t -> t.equals(name));
            s.exclude.removeIf(t -> t.equals(name));
        }
        return save();
    }

    public boolean renameGroup(String oldName, String newName) {
        if (newName.isEmpty() || oldName.equals(newName)) {
            return false;
        }
        if (findGroup(newName) != null) {
            return false;
        }
        ItemGroup g = findGroup(oldName);
        if (g == null) {
            return false;
        }
        g.name = newName;
        for (Supergroup s : supergroups) {
            s.include.replaceAll(t -> t.equals(oldName) ? newName : t);
            s.exclude.replaceAll(t -> t.equals(oldName) ? newName : t);
        }
        return save();
    }

    /** originalName == null adds a copy of edited; otherwise updates the stored group in place (renaming if needed). */
    public boolean replaceGroup(String originalName, ItemGroup edited) {
        if (edited.name.isBlank()) {
            return false;
        }
        if (originalName == null) {
            if (findGroup(edited.name) != null) {
                return false;
            }
            groups.add(GroupEditing.copy(edited));
            return save();
        }
        ItemGroup g = findGroup(originalName);
        if (g == null) {
            return false;
        }
        String newName = edited.name;
        if (!newName.equals(originalName)) {
            if (findGroup(newName) != null) {
                return false;
            }
            for (Supergroup s : supergroups) {
                s.include.replaceAll(t -> t.equals(originalName) ? newName : t);
                s.exclude.replaceAll(t -> t.equals(originalName) ? newName : t);
            }
        }
        g.name = newName;
        g.include.clear();
        g.include.addAll(edited.include);
        g.exclude.clear();
        g.exclude.addAll(edited.exclude);
        g.enabled = edited.enabled;
        g.order = edited.order;
        return save();
    }

    public ItemGroup findGroup(String name) {
        for (ItemGroup g : groups) {
            if (g.name.equals(name)) {
                return g;
            }
        }
        return null;
    }

    public boolean addSupergroup(Supergroup s) {
        if (findSupergroup(s.name) != null) {
            return false;
        }
        supergroups.add(s);
        return save();
    }

    public boolean removeSupergroup(String name) {
        if (!supergroups.removeIf(s -> s.name.equals(name))) {
            return false;
        }
        return save();
    }

    public boolean renameSupergroup(String oldName, String newName) {
        if (newName.isEmpty() || oldName.equals(newName)) {
            return false;
        }
        if (findSupergroup(newName) != null) {
            return false;
        }
        Supergroup s = findSupergroup(oldName);
        if (s == null) {
            return false;
        }
        s.name = newName;
        return save();
    }

    public Supergroup findSupergroup(String name) {
        for (Supergroup s : supergroups) {
            if (s.name.equals(name)) {
                return s;
            }
        }
        return null;
    }
}
