#include "format.hpp"

#include <sstream>
#include <string>

#include "stack_size.hpp"

CssQty to_css(int n, int stack_size) {
    CssQty q;
    if (n <= 0) {
        return q;
    }
    if (stack_size <= 0) {
        stack_size = kDefaultItemsPerStack;
    }

    if (stack_size == 1) {
        // Unstackable: each chest slot holds 1 item (27 per chest). No "stacks".
        q.chests = n / kSlotsPerChest;
        q.items = n % kSlotsPerChest;
        return q;
    }

    const int items_per_chest = stack_size * kSlotsPerChest;
    q.chests = n / items_per_chest;
    n %= items_per_chest;
    q.stacks = n / stack_size;
    q.items = n % stack_size;
    return q;
}

std::string format_css(const CssQty& q) {
    std::ostringstream oss;
    bool first = true;
    auto append = [&](int value, char suffix) {
        if (value == 0) {
            return;
        }
        if (!first) {
            oss << '+';
        }
        oss << value << suffix;
        first = false;
    };
    append(q.chests, 'c');
    append(q.stacks, 's');
    if (q.items != 0) {
        if (!first) {
            oss << '+';
        }
        oss << q.items;
        first = false;
    }
    if (first) {
        return "0";
    }
    return oss.str();
}

std::string format_quantity(int value, QuantityFormat format, const std::string& item_name) {
    if (format == QuantityFormat::Raw) {
        return std::to_string(value);
    }
    return format_css(to_css(value, max_stack_size(item_name)));
}

int quantity_for(const MaterialItem& item, QuantityColumn column) {
    switch (column) {
        case QuantityColumn::Total:
            return item.total;
        case QuantityColumn::Missing:
            return item.missing;
        case QuantityColumn::Available:
            return item.available;
    }
    return item.missing;
}
