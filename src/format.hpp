#pragma once

#include <string>

#include "model.hpp"

struct CssQty {
    int chests = 0;
    int stacks = 0;
    int items = 0;
};

constexpr int kItemsPerStack = 64;
constexpr int kStacksPerChest = 27;
constexpr int kItemsPerChest = kItemsPerStack * kStacksPerChest;

CssQty to_css(int n);
std::string format_css(const CssQty& q);
std::string format_quantity(int value, QuantityFormat format);
int quantity_for(const MaterialItem& item, QuantityColumn column);
