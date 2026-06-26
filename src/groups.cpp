#include "groups.hpp"

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <stdexcept>

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

}  // namespace

GroupStore::GroupStore() : config_path_(expand_home("~/.config/stackSquing/groups.json")) {}

bool GroupStore::load() {
    groups_.clear();
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

    if (!doc.contains("groups") || !doc["groups"].is_array()) {
        return true;
    }

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

    std::sort(groups_.begin(), groups_.end(), [](const ItemGroup& a, const ItemGroup& b) {
        if (a.order != b.order) {
            return a.order < b.order;
        }
        return a.name < b.name;
    });
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

    std::ofstream out(config_path_);
    if (!out) {
        return false;
    }
    out << doc.dump(2) << '\n';
    return true;
}

bool GroupStore::matches(const ItemGroup& group, const std::string& item_name) const {
    const auto lower_name = to_lower(item_name);
    for (const auto& term : group.include) {
        if (lower_name.find(to_lower(term)) == std::string::npos) {
            return false;
        }
    }
    for (const auto& term : group.exclude) {
        if (lower_name.find(to_lower(term)) != std::string::npos) {
            return false;
        }
    }
    return !group.include.empty();
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
