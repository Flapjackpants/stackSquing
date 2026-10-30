#include "overview.hpp"

namespace {

std::string join(const std::vector<std::string>& parts, const std::string& sep) {
    std::string out;
    for (size_t i = 0; i < parts.size(); ++i) {
        if (i > 0) {
            out += sep;
        }
        out += parts[i];
    }
    return out;
}

std::string state_tag(bool enabled) {
    return enabled ? "[on]" : "[off]";
}

void add_filters(std::vector<std::string>& lines,
                 const std::vector<std::string>& include,
                 const std::vector<std::string>& exclude) {
    lines.push_back("      include: " + (include.empty() ? std::string("(none)") : join(include, ", ")));
    if (!exclude.empty()) {
        lines.push_back("      exclude: " + join(exclude, ", "));
    }
}

}  // namespace

std::vector<std::string> build_group_overview(const GroupStore& groups) {
    std::vector<std::string> lines;

    lines.push_back("Supergroups (" + std::to_string(groups.supergroups().size()) + "):");
    if (groups.supergroups().empty()) {
        lines.push_back("  No saved supergroups.");
    }
    for (const auto& sg : groups.supergroups()) {
        lines.push_back("  " + sg.name + " " + state_tag(sg.enabled));
        add_filters(lines, sg.include, sg.exclude);
        std::vector<std::string> members;
        for (const auto& g : groups.groups()) {
            if (groups.matches(sg, g.name)) {
                members.push_back(g.name + " " + state_tag(g.enabled));
            }
        }
        lines.push_back("      groups: " + (members.empty() ? std::string("none") : join(members, ", ")));
    }

    lines.push_back("");
    lines.push_back("Groups (" + std::to_string(groups.groups().size()) + "):");
    if (groups.groups().empty()) {
        lines.push_back("  No saved groups.");
    }
    for (const auto& g : groups.groups()) {
        lines.push_back("  " + g.name + " " + state_tag(g.enabled));
        add_filters(lines, g.include, g.exclude);
        std::vector<std::string> parents;
        for (const auto& sg : groups.supergroups()) {
            if (groups.matches(sg, g.name)) {
                parents.push_back(sg.name);
            }
        }
        if (!parents.empty()) {
            lines.push_back("      in: " + join(parents, ", "));
        }
    }
    return lines;
}
