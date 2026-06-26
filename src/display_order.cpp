#include "display_order.hpp"

#include <set>
#include <vector>

#include "format.hpp"

namespace {

int sum_quantity(const std::vector<int>& indices, const MaterialList& list, QuantityColumn column) {
    int total = 0;
    for (int idx : indices) {
        const auto& item = list.items[idx];
        if (item.fulfilled) {
            continue;
        }
        total += quantity_for(item, column);
    }
    return total;
}

std::vector<int> matching_indices(const MaterialList& list, const GroupStore& groups, const ItemGroup& group) {
    std::vector<int> indices;
    for (int i = 0; i < static_cast<int>(list.items.size()); ++i) {
        if (groups.matches(group, list.items[i].name)) {
            indices.push_back(i);
        }
    }
    return indices;
}

bool indices_fully_fulfilled(const std::vector<int>& indices, const MaterialList& list) {
    if (indices.empty()) {
        return false;
    }
    for (int idx : indices) {
        if (!list.items[idx].fulfilled) {
            return false;
        }
    }
    return true;
}

bool indices_fully_fulfilled(const std::set<int>& indices, const MaterialList& list) {
    if (indices.empty()) {
        return false;
    }
    for (int idx : indices) {
        if (!list.items[idx].fulfilled) {
            return false;
        }
    }
    return true;
}

std::set<int> unique_indices_for_supergroup(const MaterialList& list, const GroupStore& groups,
                                            const Supergroup& supergroup) {
    std::set<int> unique;
    for (const auto& member_name : supergroup.member_groups) {
        const ItemGroup* member = groups.find_group(member_name);
        if (member == nullptr) {
            continue;
        }
        for (int idx : matching_indices(list, groups, *member)) {
            unique.insert(idx);
        }
    }
    return unique;
}

bool append_group_block(std::vector<DisplayRow>& rows, const MaterialList& list, const GroupStore& groups,
                        const ItemGroup& group, QuantityColumn column, bool hide_fulfilled_groups,
                        int& group_number, int& item_number) {
    const auto member_indices = matching_indices(list, groups, group);
    if (member_indices.empty()) {
        return false;
    }
    if (hide_fulfilled_groups && indices_fully_fulfilled(member_indices, list)) {
        return false;
    }

    DisplayRow header;
    header.kind = DisplayRowKind::GroupHeader;
    header.display_number = group_number++;
    header.label = group.name;
    header.quantity = sum_quantity(member_indices, list, column);
    header.group = &group;
    rows.push_back(header);

    for (int idx : member_indices) {
        const auto& item = list.items[idx];
        DisplayRow row;
        row.kind = DisplayRowKind::Item;
        row.display_number = item_number++;
        row.label = item.name;
        row.quantity = quantity_for(item, column);
        row.item_index = idx;
        row.fulfilled = item.fulfilled;
        row.group = &group;
        rows.push_back(row);
    }
    return true;
}

std::set<std::string> groups_in_enabled_supergroups(const GroupStore& groups) {
    std::set<std::string> claimed;
    for (const Supergroup* supergroup : groups.enabled_supergroups_sorted()) {
        for (const auto& member_name : supergroup->member_groups) {
            claimed.insert(member_name);
        }
    }
    return claimed;
}

bool item_in_enabled_groups(const MaterialList& list, const GroupStore& groups, int item_index) {
    for (const ItemGroup* group : groups.enabled_groups_sorted()) {
        if (groups.matches(*group, list.items[item_index].name)) {
            return true;
        }
    }
    return false;
}

bool item_in_enabled_supergroups(const MaterialList& list, const GroupStore& groups, int item_index) {
    for (const Supergroup* supergroup : groups.enabled_supergroups_sorted()) {
        for (const auto& member_name : supergroup->member_groups) {
            const ItemGroup* member = groups.find_group(member_name);
            if (member != nullptr && groups.matches(*member, list.items[item_index].name)) {
                return true;
            }
        }
    }
    return false;
}

}  // namespace

std::vector<DisplayRow> DisplayOrderBuilder::build(const MaterialList& list,
                                                   const GroupStore& groups,
                                                   QuantityColumn column,
                                                   bool hide_fulfilled_groups) {
    std::vector<DisplayRow> rows;
    int item_number = 1;
    int group_number = 1;
    int supergroup_number = 1;

    const auto enabled_supergroups = groups.enabled_supergroups_sorted();
    const auto enabled_groups = groups.enabled_groups_sorted();
    const auto supergroup_member_names = groups_in_enabled_supergroups(groups);
    const bool any_blocks = !enabled_supergroups.empty() || !enabled_groups.empty();

    for (const Supergroup* supergroup : enabled_supergroups) {
        const auto unique_indices = unique_indices_for_supergroup(list, groups, *supergroup);
        if (unique_indices.empty()) {
            continue;
        }
        if (hide_fulfilled_groups && indices_fully_fulfilled(unique_indices, list)) {
            continue;
        }

        const auto supergroup_start = rows.size();

        DisplayRow super_header;
        super_header.kind = DisplayRowKind::SupergroupHeader;
        super_header.display_number = supergroup_number++;
        super_header.label = supergroup->name;
        super_header.quantity = sum_quantity({unique_indices.begin(), unique_indices.end()}, list, column);
        super_header.supergroup = supergroup;
        rows.push_back(super_header);

        bool any_member_shown = false;
        for (const auto& member_name : supergroup->member_groups) {
            const ItemGroup* member = groups.find_group(member_name);
            if (member == nullptr) {
                continue;
            }
            if (append_group_block(rows, list, groups, *member, column, hide_fulfilled_groups, group_number,
                                   item_number)) {
                any_member_shown = true;
            }
        }

        if (!any_member_shown) {
            rows.resize(supergroup_start);
            --supergroup_number;
            continue;
        }

        if (any_blocks) {
            DisplayRow sep;
            sep.kind = DisplayRowKind::Separator;
            rows.push_back(sep);
        }
    }

    for (const ItemGroup* group : enabled_groups) {
        if (supergroup_member_names.count(group->name)) {
            continue;
        }
        if (!append_group_block(rows, list, groups, *group, column, hide_fulfilled_groups, group_number,
                                item_number)) {
            continue;
        }

        if (any_blocks) {
            DisplayRow sep;
            sep.kind = DisplayRowKind::Separator;
            rows.push_back(sep);
        }
    }

    for (int i = 0; i < static_cast<int>(list.items.size()); ++i) {
        if (item_in_enabled_groups(list, groups, i) || item_in_enabled_supergroups(list, groups, i)) {
            continue;
        }
        const auto& item = list.items[i];
        DisplayRow row;
        row.kind = DisplayRowKind::Item;
        row.display_number = item_number++;
        row.label = item.name;
        row.quantity = quantity_for(item, column);
        row.item_index = i;
        row.fulfilled = item.fulfilled;
        rows.push_back(row);
    }

    if (!rows.empty() && rows.back().kind == DisplayRowKind::Separator) {
        rows.pop_back();
    }

    return rows;
}
