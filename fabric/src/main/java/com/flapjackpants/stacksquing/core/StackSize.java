package com.flapjackpants.stacksquing.core;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Port of src/stack_size.cpp. */
public final class StackSize {
    private static final Set<String> STACK_OF_SIXTEEN = Set.of(
            "snowball", "egg", "blue egg", "brown egg",
            "ender pearl", "bucket", "honey bottle", "armor stand",
            "written book", "ominous banner", "straw bed");

    private StackSize() {
    }

    private static boolean equalsAny(String s, String... options) {
        for (String opt : options) {
            if (s.equals(opt)) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithAny(String s, String... suffixes) {
        for (String suffix : suffixes) {
            if (s.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnstackable(String name) {
        if (equalsAny(name, "water bucket", "lava bucket", "milk bucket", "powder snow bucket")) {
            return true;
        }
        if (name.startsWith("bucket of ")) {
            return true;
        }

        if (equalsAny(name, "potion", "splash potion", "lingering potion", "ominous bottle")
                || name.startsWith("potion of ") || name.startsWith("splash potion of ")
                || name.startsWith("lingering potion of ") || name.startsWith("ominous bottle")) {
            return true;
        }

        if (endsWithAny(name, " sword", " pickaxe", " shovel", " hoe", " axe")
                || equalsAny(name, "bow", "crossbow", "trident", "mace", "shield", "brush", "shears",
                        "flint and steel", "fishing rod", "carrot on a stick",
                        "warped fungus on a stick", "elytra")) {
            return true;
        }

        if (endsWithAny(name, " helmet", " chestplate", " leggings", " boots", " horse armor")
                || equalsAny(name, "turtle shell", "wolf armor", "leather horse armor", "iron horse armor",
                        "golden horse armor", "diamond horse armor")
                || name.endsWith(" harness")) {
            return true;
        }

        if (endsWithAny(name, " boat", " raft", " boat with chest", " raft with chest")
                || name.contains("minecart") || (name.endsWith(" bed") && !name.equals("straw bed"))
                || name.endsWith(" shulker box") || name.equals("shulker box")
                || name.endsWith(" bundle") || name.equals("bundle")) {
            return true;
        }

        if (equalsAny(name, "mushroom stew", "rabbit stew", "beetroot soup", "suspicious stew", "cake",
                "saddle", "totem of undying", "book and quill", "goat horn",
                "debug stick", "knowledge book", "writable book")) {
            return true;
        }

        return name.startsWith("music disc") || name.endsWith(" banner pattern")
                || name.startsWith("banner pattern");
    }

    private static boolean isStackOfSixteen(String name) {
        if (STACK_OF_SIXTEEN.contains(name)) {
            return true;
        }
        if (name.endsWith(" hanging sign")
                || (name.endsWith(" sign") && !name.endsWith(" wall sign")
                        && !name.endsWith(" standing sign"))) {
            return true;
        }
        return name.endsWith(" banner");
    }

    public static int maxStackSize(String itemName) {
        String name = itemName == null ? "" : itemName.toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return 64;
        }
        if (isUnstackable(name)) {
            return 1;
        }
        if (isStackOfSixteen(name)) {
            return 16;
        }
        return 64;
    }
}
