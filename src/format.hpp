#pragma once

#include <string>

#include "model.hpp"

struct CssQty {
    int chests = 0;
    int stacks = 0;
    int items = 0;
};

constexpr int kDefaultItemsPerStack = 64;
constexpr int kSlotsPerChest = 27;

CssQty to_css(int n, int stack_size = kDefaultItemsPerStack);
std::string format_css(const CssQty& q);
// item_name selects stack size (1 / 16 / 64) for css formatting.
std::string format_quantity(int value, QuantityFormat format, const std::string& item_name = {});
int quantity_for(const MaterialItem& item, QuantityColumn column);
// Parses "450", "2s", "1c", "2s+3", "2s 3" (components split by '+' and/or whitespace;
// each <digits>[c|s|C|S]; s = stack_size, c = 27 * stack_size; stack_size <= 0 -> 64).
// Returns false on empty text, any other character (including '-'), a bare suffix,
// a component with more than 9 digits, or a total of 0.
bool parse_amount(const std::string& text, int stack_size, int& out);
