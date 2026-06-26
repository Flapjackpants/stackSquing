#include "display_order.hpp"

#include <set>
#include <vector>

#include "format.hpp"

namespace {

int sum_quantity(const std::vector<int>& indices, const MaterialList& list, QuantityColumn column) {
    int total = 0;
    for (int idx : indices) {
        total += quantity_for(list.items[idx], column);
    }
    return total;
}

}  // namespace

std::vector<DisplayRow> DisplayOrderBuilder::build(const MaterialList& list,
                                                   const GroupStore& groups,
                                                   QuantityColumn column) {
    std::vector<DisplayRow> rows;
    std::set<int> claimed;
    int item_number = 1;
    int group_number = 1;

    const auto enabled = groups.enabled_groups_sorted();
  bool any_group_enabled = !enabled.empty();

    for (const ItemGroup* group : enabled) {
        std::vector<int> member_indices;
        for (int i = 0; i < static_cast<int>(list.items.size()); ++i) {
            if (claimed.count(i)) {
                continue;
            }
            if (groups.matches(*group, list.items[i].name)) {
                member_indices.push_back(i);
                claimed.insert(i);
            }
        }
        if (member_indices.empty()) {
            continue;
        }

        DisplayRow header;
        header.kind = DisplayRowKind::GroupHeader;
        header.display_number = group_number++;
        header.label = group->name;
        header.quantity = sum_quantity(member_indices, list, column);
        header.group = group;
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
            rows.push_back(row);
        }

        if (any_group_enabled) {
            DisplayRow sep;
            sep.kind = DisplayRowKind::Separator;
            rows.push_back(sep);
        }
    }

    for (int i = 0; i < static_cast<int>(list.items.size()); ++i) {
        if (claimed.count(i)) {
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
