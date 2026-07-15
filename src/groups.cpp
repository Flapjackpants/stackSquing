#include "groups.hpp"

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <set>

#include <nlohmann/json.hpp>

namespace {

std::string to_lower(std::string s) {
    for (char& c : s) {
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }
    return s;
}

std::string expand_home(const std::string& path) {
    if (!path.empty() && path[0] == '~') {
        const char* home = std::getenv("HOME");
        if (home) {
            return std::string(home) + path.substr(1);
        }
    }
    return path;
}

void sort_groups(std::vector<ItemGroup>& groups) {
    std::sort(groups.begin(), groups.end(), [](const ItemGroup& a, const ItemGroup& b) {
        if (a.order != b.order) {
            return a.order < b.order;
        }
        return a.name < b.name;
    });
}

void sort_supergroups(std::vector<Supergroup>& supergroups) {
    std::sort(supergroups.begin(), supergroups.end(), [](const Supergroup& a, const Supergroup& b) {
        if (a.order != b.order) {
            return a.order < b.order;
        }
        return a.name < b.name;
    });
}

bool matches_filter(const std::vector<std::string>& include, const std::vector<std::string>& exclude,
                    const std::string& text) {
    if (include.empty()) {
        return false;
    }

    const auto lower_name = to_lower(text);
    bool matched_include = false;
    for (const auto& term : include) {
        if (lower_name.find(to_lower(term)) != std::string::npos) {
            matched_include = true;
            break;
        }
    }
    if (!matched_include) {
        return false;
    }

    for (const auto& term : exclude) {
        if (lower_name.find(to_lower(term)) != std::string::npos) {
            return false;
        }
    }
    return true;
}

}  // namespace

GroupStore::GroupStore() : config_path_(expand_home("~/.config/stackSquing/groups.json")) {}

bool GroupStore::load() {
    groups_.clear();
    supergroups_.clear();
    std::ifstream in(config_path_);
    if (!in) {
        return true;
    }

    nlohmann::json doc;
    try {
        in >> doc;
    } catch (const std::exception&) {
        return false;
    }

    if (doc.contains("groups") && doc["groups"].is_array()) {
        for (const auto& entry : doc["groups"]) {
            ItemGroup group;
            group.name = entry.value("name", "");
            group.enabled = entry.value("enabled", false);
            group.order = entry.value("order", 0);
            if (entry.contains("include") && entry["include"].is_array()) {
                for (const auto& term : entry["include"]) {
                    group.include.push_back(term.get<std::string>());
                }
            }
            if (entry.contains("exclude") && entry["exclude"].is_array()) {
                for (const auto& term : entry["exclude"]) {
                    group.exclude.push_back(term.get<std::string>());
                }
            }
            if (!group.name.empty()) {
                groups_.push_back(group);
            }
        }
    }

    if (doc.contains("supergroups") && doc["supergroups"].is_array()) {
        for (const auto& entry : doc["supergroups"]) {
            Supergroup supergroup;
            supergroup.name = entry.value("name", "");
            supergroup.enabled = entry.value("enabled", false);
            supergroup.order = entry.value("order", 0);
            if (entry.contains("include") && entry["include"].is_array()) {
                for (const auto& term : entry["include"]) {
                    supergroup.include.push_back(term.get<std::string>());
                }
            }
            if (entry.contains("exclude") && entry["exclude"].is_array()) {
                for (const auto& term : entry["exclude"]) {
                    supergroup.exclude.push_back(term.get<std::string>());
                }
            }
            // Migrate legacy member_groups: exact group names become include terms.
            if (supergroup.include.empty() && entry.contains("member_groups") &&
                entry["member_groups"].is_array()) {
                for (const auto& member : entry["member_groups"]) {
                    const auto name = member.get<std::string>();
                    if (!name.empty()) {
                        supergroup.include.push_back(name);
                    }
                }
            }
            if (!supergroup.name.empty()) {
                supergroups_.push_back(supergroup);
            }
        }
    }

    sort_groups(groups_);
    sort_supergroups(supergroups_);
    return true;
}

bool GroupStore::save() const {
    const auto parent = std::filesystem::path(config_path_).parent_path();
    std::error_code ec;
    std::filesystem::create_directories(parent, ec);

    nlohmann::json doc;
    nlohmann::json groups = nlohmann::json::array();
    for (const auto& group : groups_) {
        nlohmann::json entry;
        entry["name"] = group.name;
        entry["enabled"] = group.enabled;
        entry["order"] = group.order;
        entry["include"] = group.include;
        entry["exclude"] = group.exclude;
        groups.push_back(entry);
    }
    doc["groups"] = groups;

    nlohmann::json supergroups = nlohmann::json::array();
    for (const auto& supergroup : supergroups_) {
        nlohmann::json entry;
        entry["name"] = supergroup.name;
        entry["enabled"] = supergroup.enabled;
        entry["order"] = supergroup.order;
        entry["include"] = supergroup.include;
        entry["exclude"] = supergroup.exclude;
        supergroups.push_back(entry);
    }
    doc["supergroups"] = supergroups;

    std::ofstream out(config_path_);
    if (!out) {
        return false;
    }
    out << doc.dump(2) << '\n';
    return true;
}

bool GroupStore::matches(const ItemGroup& group, const std::string& item_name) const {
    return matches_filter(group.include, group.exclude, item_name);
}

bool GroupStore::matches(const Supergroup& supergroup, const std::string& group_name) const {
    return matches_filter(supergroup.include, supergroup.exclude, group_name);
}

std::vector<const ItemGroup*> GroupStore::enabled_groups_sorted() const {
    std::vector<const ItemGroup*> result;
    for (const auto& group : groups_) {
        if (group.enabled) {
            result.push_back(&group);
        }
    }
    std::sort(result.begin(), result.end(), [](const ItemGroup* a, const ItemGroup* b) {
        if (a->order != b->order) {
            return a->order < b->order;
        }
        return a->name < b->name;
    });
    return result;
}

std::vector<const Supergroup*> GroupStore::enabled_supergroups_sorted() const {
    std::vector<const Supergroup*> result;
    for (const auto& supergroup : supergroups_) {
        if (supergroup.enabled) {
            result.push_back(&supergroup);
        }
    }
    std::sort(result.begin(), result.end(), [](const Supergroup* a, const Supergroup* b) {
        if (a->order != b->order) {
            return a->order < b->order;
        }
        return a->name < b->name;
    });
    return result;
}

std::vector<const ItemGroup*> GroupStore::matching_member_groups(const Supergroup& supergroup) const {
    std::vector<const ItemGroup*> result;
    for (const ItemGroup* group : enabled_groups_sorted()) {
        if (matches(supergroup, group->name)) {
            result.push_back(group);
        }
    }
    return result;
}

bool GroupStore::add_group(const ItemGroup& group) {
    if (find_group(group.name) != nullptr) {
        return false;
    }
    groups_.push_back(group);
    return save();
}

bool GroupStore::remove_group(const std::string& name) {
    const auto it = std::remove_if(groups_.begin(), groups_.end(),
                                   [&](const ItemGroup& g) { return g.name == name; });
    if (it == groups_.end()) {
        return false;
    }
    groups_.erase(it, groups_.end());
    remove_group_references(name);
    return save();
}

bool GroupStore::rename_group(const std::string& old_name, const std::string& new_name) {
    if (new_name.empty() || old_name == new_name) {
        return false;
    }
    if (find_group(new_name) != nullptr) {
        return false;
    }
    auto* group = find_group(old_name);
    if (group == nullptr) {
        return false;
    }
    group->name = new_name;
    rename_group_references(old_name, new_name);
    return save();
}

ItemGroup* GroupStore::find_group(const std::string& name) {
    for (auto& group : groups_) {
        if (group.name == name) {
            return &group;
        }
    }
    return nullptr;
}

const ItemGroup* GroupStore::find_group(const std::string& name) const {
    for (const auto& group : groups_) {
        if (group.name == name) {
            return &group;
        }
    }
    return nullptr;
}

bool GroupStore::add_supergroup(const Supergroup& supergroup) {
    if (find_supergroup(supergroup.name) != nullptr) {
        return false;
    }
    supergroups_.push_back(supergroup);
    return save();
}

bool GroupStore::remove_supergroup(const std::string& name) {
    const auto it = std::remove_if(supergroups_.begin(), supergroups_.end(),
                                   [&](const Supergroup& sg) { return sg.name == name; });
    if (it == supergroups_.end()) {
        return false;
    }
    supergroups_.erase(it, supergroups_.end());
    return save();
}

bool GroupStore::rename_supergroup(const std::string& old_name, const std::string& new_name) {
    if (new_name.empty() || old_name == new_name) {
        return false;
    }
    if (find_supergroup(new_name) != nullptr) {
        return false;
    }
    auto* supergroup = find_supergroup(old_name);
    if (supergroup == nullptr) {
        return false;
    }
    supergroup->name = new_name;
    return save();
}

Supergroup* GroupStore::find_supergroup(const std::string& name) {
    for (auto& supergroup : supergroups_) {
        if (supergroup.name == name) {
            return &supergroup;
        }
    }
    return nullptr;
}

const Supergroup* GroupStore::find_supergroup(const std::string& name) const {
    for (const auto& supergroup : supergroups_) {
        if (supergroup.name == name) {
            return &supergroup;
        }
    }
    return nullptr;
}

void GroupStore::remove_group_references(const std::string& name) {
    for (auto& supergroup : supergroups_) {
        const auto rem_inc = std::remove(supergroup.include.begin(), supergroup.include.end(), name);
        supergroup.include.erase(rem_inc, supergroup.include.end());
        const auto rem_exc = std::remove(supergroup.exclude.begin(), supergroup.exclude.end(), name);
        supergroup.exclude.erase(rem_exc, supergroup.exclude.end());
    }
}

void GroupStore::rename_group_references(const std::string& old_name, const std::string& new_name) {
    for (auto& supergroup : supergroups_) {
        for (auto& term : supergroup.include) {
            if (term == old_name) {
                term = new_name;
            }
        }
        for (auto& term : supergroup.exclude) {
            if (term == old_name) {
                term = new_name;
            }
        }
    }
}
