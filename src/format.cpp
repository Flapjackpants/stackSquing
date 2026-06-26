#include "format.hpp"

#include <sstream>
#include <string>

CssQty to_css(int n) {
    CssQty q;
    if (n <= 0) {
        return q;
    }
    q.chests = n / kItemsPerChest;
    n %= kItemsPerChest;
    q.stacks = n / kItemsPerStack;
    q.items = n % kItemsPerStack;
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

std::string format_quantity(int value, QuantityFormat format) {
    if (format == QuantityFormat::Raw) {
        return std::to_string(value);
    }
    return format_css(to_css(value));
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
