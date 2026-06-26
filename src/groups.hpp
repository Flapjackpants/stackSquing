#pragma once

#include <string>
#include <vector>

#include "model.hpp"

class GroupStore {
public:
    GroupStore();

    const std::vector<ItemGroup>& groups() const { return groups_; }
    std::vector<ItemGroup>& groups() { return groups_; }
    const std::vector<Supergroup>& supergroups() const { return supergroups_; }
    std::vector<Supergroup>& supergroups() { return supergroups_; }

    bool load();
    bool save() const;

    bool matches(const ItemGroup& group, const std::string& item_name) const;
    std::vector<const ItemGroup*> enabled_groups_sorted() const;
    std::vector<const Supergroup*> enabled_supergroups_sorted() const;

    bool add_group(const ItemGroup& group);
    bool remove_group(const std::string& name);
    bool rename_group(const std::string& old_name, const std::string& new_name);
    ItemGroup* find_group(const std::string& name);
    const ItemGroup* find_group(const std::string& name) const;

    bool add_supergroup(const Supergroup& supergroup);
    bool remove_supergroup(const std::string& name);
    bool rename_supergroup(const std::string& old_name, const std::string& new_name);
    Supergroup* find_supergroup(const std::string& name);
    const Supergroup* find_supergroup(const std::string& name) const;

    void remove_group_references(const std::string& name);
    void rename_group_references(const std::string& old_name, const std::string& new_name);

    std::string config_path() const { return config_path_; }

private:
    std::string config_path_;
    std::vector<ItemGroup> groups_;
    std::vector<Supergroup> supergroups_;
};
