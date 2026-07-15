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
