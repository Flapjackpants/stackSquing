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

struct RowTail {
    bool fulfilled = false;
    int partial = 0;
};

// The line up to and including its last '|'.
std::string row_base(const std::string& line) {
    const auto pos = line.rfind('|');
    if (pos == std::string::npos) {
        const auto end = line.find_last_not_of(" \t\r\n");
        return end == std::string::npos ? std::string() : line.substr(0, end + 1);
    }
    return line.substr(0, pos + 1);
}

bool all_digits(const std::string& s) {
    if (s.empty()) {
        return false;
    }
    for (char c : s) {
        if (!std::isdigit(static_cast<unsigned char>(c))) {
            return false;
        }
    }
    return true;
}

RowTail parse_row_tail(const std::string& line, int missing) {
    RowTail result;
    const auto pos = line.rfind('|');
    if (pos == std::string::npos) {
        return result;
    }
    std::stringstream ss(line.substr(pos + 1));
    std::vector<std::string> tokens;
    std::string token;
    while (ss >> token) {
        tokens.push_back(token);
    }
    if (tokens.empty() || tokens[0] != "a") {
        return result;
    }
    if (tokens.size() < 2 || !all_digits(tokens[1])) {
        result.fulfilled = true;
        return result;
    }
    // More than 9 digits cannot be a sane partial amount: treat as fully gathered.
    const int n = tokens[1].size() > 9 ? missing : std::stoi(tokens[1]);
    if (n == 0) {
        return result;
    }
    if (n >= missing) {
        result.fulfilled = true;
        return result;
    }
    result.partial = n;
    return result;
}

std::string build_row(const std::string& line, bool fulfilled, int partial) {
    std::string row = row_base(line);
    if (fulfilled) {
        row += "a";
    } else if (partial > 0) {
        row += "a " + std::to_string(partial);
    }
    return row;
}

void write_item_line(MaterialList& list, MaterialItem& item, const std::string& updated) {
    if (item.line_index >= 0 && item.line_index < static_cast<int>(list.raw_lines.size())) {
        list.raw_lines[item.line_index] = updated;
    }
    item.original_line = updated;
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
    const auto tail = parse_row_tail(line, parsed.missing);
    parsed.fulfilled = tail.fulfilled;
    parsed.partial = tail.partial;
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
    item.partial = 0;
    write_item_line(list, item, build_row(item.original_line, fulfilled, 0));
}

void MaterialListParser::set_partial(MaterialList& list, int item_index, int amount) {
    if (item_index < 0 || item_index >= static_cast<int>(list.items.size())) {
        throw std::runtime_error("Invalid item index");
    }

    auto& item = list.items[item_index];
    if (amount > 0 && amount >= item.missing) {
        item.fulfilled = true;
        item.partial = 0;
    } else if (amount <= 0) {
        item.fulfilled = false;
        item.partial = 0;
    } else {
        item.fulfilled = false;
        item.partial = amount;
    }
    write_item_line(list, item, build_row(item.original_line, item.fulfilled, item.partial));
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
