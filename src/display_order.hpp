#pragma once

#include <string>
#include <vector>

#include "groups.hpp"
#include "model.hpp"

enum class DisplayRowKind { GroupHeader, Item, Separator };

struct DisplayRow {
    DisplayRowKind kind = DisplayRowKind::Item;
    int display_number = 0;
    std::string label;
    int quantity = 0;
    int item_index = -1;
    bool fulfilled = false;
    const ItemGroup* group = nullptr;
};

class DisplayOrderBuilder {
public:
    static std::vector<DisplayRow> build(const MaterialList& list,
                                         const GroupStore& groups,
                                         QuantityColumn column);
};
