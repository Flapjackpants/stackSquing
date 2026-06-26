#pragma once

#include <string>
#include <vector>

enum class QuantityColumn { Total, Missing, Available };
enum class QuantityFormat { Raw, Css };

struct MaterialItem {
    std::string name;
    int total = 0;
    int missing = 0;
    int available = 0;
    bool fulfilled = false;
    int line_index = -1;
    std::string original_line;
};

struct MaterialList {
    std::string placement_name;
    std::string file_path;
    std::vector<std::string> raw_lines;
    std::vector<MaterialItem> items;
};

struct ItemGroup {
    std::string name;
    std::vector<std::string> include;
    std::vector<std::string> exclude;
    bool enabled = false;
    int order = 0;
};

struct Supergroup {
    std::string name;
    std::vector<std::string> member_groups;
    bool enabled = false;
    int order = 0;
};

struct AppSettings {
    QuantityColumn column = QuantityColumn::Missing;
    QuantityFormat format = QuantityFormat::Css;
};
