#pragma once

#include <string>
#include <vector>

#include "model.hpp"

class GroupStore {
public:
    GroupStore();

    const std::vector<ItemGroup>& groups() const { return groups_; }
    std::vector<ItemGroup>& groups() { return groups_; }

    bool load();
    bool save() const;

    bool matches(const ItemGroup& group, const std::string& item_name) const;
    std::vector<const ItemGroup*> enabled_groups_sorted() const;

    bool add_group(const ItemGroup& group);
    bool remove_group(const std::string& name);
    ItemGroup* find_group(const std::string& name);
    const ItemGroup* find_group(const std::string& name) const;

    std::string config_path() const { return config_path_; }

private:
    std::string config_path_;
    std::vector<ItemGroup> groups_;
};
