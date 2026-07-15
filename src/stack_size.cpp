#include "stack_size.hpp"

#include <algorithm>
#include <cctype>
#include <string>
#include <unordered_set>
#include <vector>

namespace {

std::string to_lower(std::string s) {
    for (char& c : s) {
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }
    return s;
}

bool ends_with(const std::string& s, const std::string& suffix) {
    return s.size() >= suffix.size() &&
           s.compare(s.size() - suffix.size(), suffix.size(), suffix) == 0;
}

bool starts_with(const std::string& s, const std::string& prefix) {
    return s.size() >= prefix.size() && s.compare(0, prefix.size(), prefix) == 0;
}

bool equals_any(const std::string& s, const std::vector<std::string>& options) {
    for (const auto& opt : options) {
        if (s == opt) {
            return true;
        }
    }
    return false;
}

bool ends_with_any(const std::string& s, const std::vector<std::string>& suffixes) {
    for (const auto& suffix : suffixes) {
        if (ends_with(s, suffix)) {
            return true;
        }
    }
    return false;
}

bool contains(const std::string& s, const std::string& needle) {
    return s.find(needle) != std::string::npos;
}

// Unstackable (max stack 1) — durability, filled buckets, boats, etc.
bool is_unstackable(const std::string& name) {
    // Filled buckets (check before empty "bucket")
    if (equals_any(name, {"water bucket", "lava bucket", "milk bucket", "powder snow bucket"})) {
        return true;
    }
    if (starts_with(name, "bucket of ")) {
        return true;
    }

    // Potions / ominous bottles
    if (equals_any(name, {"potion", "splash potion", "lingering potion", "ominous bottle"}) ||
        starts_with(name, "potion of ") || starts_with(name, "splash potion of ") ||
        starts_with(name, "lingering potion of ") || starts_with(name, "ominous bottle")) {
        return true;
    }

    // Tools & weapons (pickaxe before axe)
    if (ends_with_any(name, {" sword", " pickaxe", " shovel", " hoe", " axe"}) ||
        equals_any(name, {"bow", "crossbow", "trident", "mace", "shield", "brush", "shears",
                           "flint and steel", "fishing rod", "carrot on a stick",
                           "warped fungus on a stick", "elytra"})) {
        return true;
    }

    // Armor
    if (ends_with_any(name, {" helmet", " chestplate", " leggings", " boots", " horse armor"}) ||
        equals_any(name, {"turtle shell", "wolf armor", "leather horse armor", "iron horse armor",
                           "golden horse armor", "diamond horse armor"}) ||
        ends_with(name, " harness")) {
        return true;
    }

    // Vehicles & storage that don't stack
    if (ends_with_any(name, {" boat", " raft", " boat with chest", " raft with chest"}) ||
        contains(name, "minecart") || ends_with(name, " bed") || ends_with(name, " shulker box") ||
        name == "shulker box" || ends_with(name, " bundle") || name == "bundle") {
        return true;
    }

    // Food bowls / unique singles
    if (equals_any(name, {"mushroom stew", "rabbit stew", "beetroot soup", "suspicious stew", "cake",
                           "saddle", "totem of undying", "book and quill", "goat horn",
                           "debug stick", "knowledge book", "writable book"})) {
        return true;
    }

    if (starts_with(name, "music disc") || ends_with(name, " banner pattern") ||
        starts_with(name, "banner pattern")) {
        return true;
    }

    return false;
}

// Stack-to-16 items per Minecraft Wiki (Java Edition).
bool is_stack_of_sixteen(const std::string& name) {
    static const std::unordered_set<std::string> exact = {
        "snowball",       "egg",           "blue egg",     "brown egg",
        "ender pearl",    "bucket",        "honey bottle", "armor stand",
        "written book",   "ominous banner",
    };
    if (exact.count(name) != 0) {
        return true;
    }

    // Standing/wall signs are block forms; item forms are "* Sign" / "* Hanging Sign".
    if (ends_with(name, " hanging sign") ||
        (ends_with(name, " sign") && !ends_with(name, " wall sign") &&
         !ends_with(name, " standing sign"))) {
        return true;
    }

    if (ends_with(name, " banner")) {
        return true;
    }

    return false;
}

}  // namespace

int max_stack_size(const std::string& item_name) {
    const auto name = to_lower(item_name);
    if (name.empty()) {
        return 64;
    }
    if (is_unstackable(name)) {
        return 1;
    }
    if (is_stack_of_sixteen(name)) {
        return 16;
    }
    return 64;
}
