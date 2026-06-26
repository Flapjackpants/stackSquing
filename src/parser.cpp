#include "parser.hpp"

#include <cctype>
#include <fstream>
#include <sstream>
#include <stdexcept>

namespace {

std::string trim(const std::string& s) {
    size_t start = 0;
    while (start < s.size() && std::isspace(static_cast<unsigned char>(s[start]))) {
        ++start;
    }
    size_t end = s.size();
    while (end > start && std::isspace(static_cast<unsigned char>(s[end - 1]))) {
        --end;
    }
    return s.substr(start, end - start);
}

std::string to_lower(std::string s) {
    for (char& c : s) {
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }
    return s;
}

bool is_separator_line(const std::string& line) {
    const auto trimmed = trim(line);
    return !trimmed.empty() && trimmed.front() == '+' && trimmed.find('-') != std::string::npos;
}

bool is_header_row(const std::string& line) {
    const auto lower = to_lower(line);
    return lower.find("| item") != std::string::npos &&
           lower.find("| total") != std::string::npos;
}

bool parse_placement_name(const std::string& line, std::string& placement) {
    const auto marker = std::string("Material List for placement '");
    const auto pos = line.find(marker);
    if (pos == std::string::npos) {
        return false;
    }
    const auto start = pos + marker.size();
    const auto end = line.find('\'', start);
    if (end == std::string::npos) {
        return false;
    }
    placement = line.substr(start, end - start);
    return true;
}

bool parse_int_field(const std::string& field, int& value) {
    std::string digits;
    for (char c : field) {
        if (std::isdigit(static_cast<unsigned char>(c))) {
            digits.push_back(c);
        }
    }
    if (digits.empty()) {
        return false;
    }
    value = std::stoi(digits);
    return true;
}

bool line_has_fulfilled_marker(const std::string& line) {
    const auto trimmed = trim(line);
    if (trimmed.size() >= 2 && trimmed.substr(trimmed.size() - 2) == "|a") {
        return true;
    }
    const auto pos = line.rfind('|');
    if (pos == std::string::npos) {
        return false;
    }
    const auto tail = trim(line.substr(pos + 1));
    return tail == "a";
}

std::string strip_fulfilled_marker(const std::string& line) {
    const auto trimmed = trim(line);
    if (trimmed.size() >= 2 && trimmed.substr(trimmed.size() - 2) == "|a") {
        return trim(line.substr(0, line.find_last_of('|') + 1)) + " ";
    }
    const auto pos = line.rfind('|');
    if (pos == std::string::npos) {
        return line;
    }
    const auto tail = trim(line.substr(pos + 1));
    if (tail == "a") {
        return line.substr(0, pos + 1) + " ";
    }
    return line;
}

std::string apply_fulfilled_marker(const std::string& line) {
    if (line_has_fulfilled_marker(line)) {
        return line;
    }
    const auto trimmed = trim(line);
    if (!trimmed.empty() && trimmed.back() == '|') {
        return trim(line) + "a";
    }
    return trim(line) + "|a";
}

bool parse_data_row(const std::string& line, MaterialItem& item) {
    if (line.find('|') == std::string::npos || is_separator_line(line) || is_header_row(line)) {
        return false;
    }

    std::vector<std::string> fields;
    std::stringstream ss(line);
    std::string part;
    while (std::getline(ss, part, '|')) {
        fields.push_back(trim(part));
    }
    if (fields.size() < 5) {
        return false;
    }

    const std::string& name = fields[1];
    if (name.empty() || to_lower(name) == "item") {
        return false;
    }

    MaterialItem parsed;
    parsed.name = name;
    if (!parse_int_field(fields[2], parsed.total) ||
        !parse_int_field(fields[3], parsed.missing) ||
        !parse_int_field(fields[4], parsed.available)) {
        return false;
    }
    parsed.fulfilled = line_has_fulfilled_marker(line) ||
                       (fields.size() > 5 && trim(fields[5]) == "a");
    item = parsed;
    return true;
}

}  // namespace

MaterialList MaterialListParser::load(const std::string& file_path) {
    std::ifstream in(file_path);
    if (!in) {
        throw std::runtime_error("Could not open file: " + file_path);
    }

    MaterialList list;
    list.file_path = file_path;
    std::string line;
    int line_index = 0;

    while (std::getline(in, line)) {
        list.raw_lines.push_back(line);
        if (list.placement_name.empty()) {
            parse_placement_name(line, list.placement_name);
        }

        MaterialItem item;
        if (parse_data_row(line, item)) {
            item.line_index = line_index;
            item.original_line = line;
            list.items.push_back(item);
        }
        ++line_index;
    }

    if (list.items.empty()) {
        throw std::runtime_error("No material items found in file: " + file_path);
    }

    return list;
}

void MaterialListParser::set_fulfilled(MaterialList& list, int item_index, bool fulfilled) {
    if (item_index < 0 || item_index >= static_cast<int>(list.items.size())) {
        throw std::runtime_error("Invalid item index");
    }

    auto& item = list.items[item_index];
    item.fulfilled = fulfilled;

    std::string updated = item.original_line;
    if (fulfilled) {
        updated = apply_fulfilled_marker(updated);
    } else {
        updated = strip_fulfilled_marker(updated);
    }

    if (item.line_index >= 0 && item.line_index < static_cast<int>(list.raw_lines.size())) {
        list.raw_lines[item.line_index] = updated;
    }
    item.original_line = updated;
}

void MaterialListParser::save(const MaterialList& list) {
    std::ofstream out(list.file_path);
    if (!out) {
        throw std::runtime_error("Could not write file: " + list.file_path);
    }
    for (const auto& line : list.raw_lines) {
        out << line << '\n';
    }
}
