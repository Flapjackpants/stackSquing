#include "format.hpp"

#include <algorithm>
#include <cctype>
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
            return std::max(0, item.missing - item.partial);
        case QuantityColumn::Available:
            return item.available;
        case QuantityColumn::Need:
            return std::max(0, item.missing - item.partial - item.available);
    }
    return std::max(0, item.missing - item.partial);
}

bool parse_amount(const std::string& text, int stack_size, int& out) {
    if (stack_size <= 0) {
        stack_size = kDefaultItemsPerStack;
    }
    long long total = 0;
    size_t i = 0;
    bool any = false;
    bool plus_pending = false;  // a '+' still waiting for its component
    while (i < text.size()) {
        const unsigned char ch = static_cast<unsigned char>(text[i]);
        if (ch == '+') {
            if (!any || plus_pending) {
                return false;
            }
            plus_pending = true;
            ++i;
            continue;
        }
        if (std::isspace(ch)) {
            ++i;
            continue;
        }
        if (!std::isdigit(ch)) {
            return false;
        }
        long long value = 0;
        int digits = 0;
        while (i < text.size() && std::isdigit(static_cast<unsigned char>(text[i]))) {
            if (++digits > 9) {
                return false;
            }
            value = value * 10 + (text[i] - '0');
            ++i;
        }
        if (i < text.size()) {
            const char suffix = static_cast<char>(std::tolower(static_cast<unsigned char>(text[i])));
            if (suffix == 's') {
                value *= stack_size;
                ++i;
            } else if (suffix == 'c') {
                value *= static_cast<long long>(kSlotsPerChest) * stack_size;
                ++i;
            }
        }
        total += value;
        any = true;
        plus_pending = false;
        if (total > 2000000000LL) {
            return false;
        }
    }
    if (!any || plus_pending || total <= 0) {
        return false;
    }
    out = static_cast<int>(total);
    return true;
}
