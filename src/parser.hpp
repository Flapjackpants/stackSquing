#pragma once

#include <string>

#include "model.hpp"

class MaterialListParser {
public:
    static MaterialList load(const std::string& file_path);
    static void set_fulfilled(MaterialList& list, int item_index, bool fulfilled);
    static void save(const MaterialList& list);
};
