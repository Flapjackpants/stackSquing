#include "ui_text.hpp"

#include <vector>

namespace {

bool starts_with(const std::string& s, const std::string& prefix) {
    return s.compare(0, prefix.size(), prefix) == 0;
}

bool contains(const std::string& s, const std::string& sub) {
    return s.find(sub) != std::string::npos;
}

}  // namespace

StatusKind classify_status(const std::string& message) {
    if (starts_with(message, "File changed on disk")) {
        return StatusKind::Warning;
    }
    static const std::vector<std::string> error_prefixes = {
        "Usage:", "Unknown command", "Failed", "Invalid", "No items in range", "No material_list"};
    for (const auto& p : error_prefixes) {
        if (starts_with(message, p)) {
            return StatusKind::Error;
        }
    }
    static const std::vector<std::string> error_substrings = {"not found", "already exists", "requires"};
    for (const auto& s : error_substrings) {
        if (contains(message, s)) {
            return StatusKind::Error;
        }
    }
    return StatusKind::Normal;
}

PanelLineParts split_panel_line(const std::string& line) {
    PanelLineParts parts;
    const size_t n = line.find_first_not_of(' ');
    if (n == std::string::npos) {
        return parts;
    }
    if (n == 0) {
        parts.kind = PanelLineKind::Heading;
        parts.key = line;
        return parts;
    }
    parts.indent = line.substr(0, n);
    const std::string remainder = line.substr(n);
    if (n >= 6) {
        parts.kind = PanelLineKind::Detail;
        const size_t sp = remainder.find(' ');
        const std::string token = remainder.substr(0, sp);
        if (!token.empty() && token.back() == ':') {
            parts.key = token;
            parts.rest = sp == std::string::npos ? "" : remainder.substr(sp);
        } else {
            parts.rest = remainder;
        }
        return parts;
    }
    parts.kind = PanelLineKind::Entry;
    const size_t gap = remainder.find("  ");
    if (gap == std::string::npos) {
        parts.key = remainder;
    } else {
        parts.key = remainder.substr(0, gap);
        parts.rest = remainder.substr(gap);
    }
    return parts;
}

std::string scroll_indicator(int first, int last, int total) {
    if (total <= 0) {
        return "";
    }
    return "rows " + std::to_string(first) + "-" + std::to_string(last) + " of " + std::to_string(total);
}
