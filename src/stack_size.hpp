#pragma once

#include <string>

// Max stack size for a Litematica/Java display name (1, 16, or 64).
// Based on Minecraft Wiki item stacking rules for modern Java Edition
// (including 26.x): most items 64; signs/eggs/etc. 16; durability & similar 1.
int max_stack_size(const std::string& item_name);
