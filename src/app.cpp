#include "app.hpp"

#include <algorithm>
#include <cctype>
#include <sstream>
#include <vector>

#include "display_order.hpp"
#include "parser.hpp"

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

std::vector<std::string> split_tokens(const std::string& s) {
    std::istringstream iss(s);
    std::vector<std::string> tokens;
    std::string token;
    while (iss >> token) {
        tokens.push_back(token);
    }
    return tokens;
}

std::vector<std::string> split_csv(const std::string& s) {
    std::vector<std::string> parts;
    std::string current;
    for (char c : s) {
        if (c == ',') {
            if (!trim(current).empty()) {
                parts.push_back(trim(current));
            }
            current.clear();
        } else {
            current.push_back(c);
        }
    }
    if (!trim(current).empty()) {
        parts.push_back(trim(current));
    }
    return parts;
}

void append_unique_terms(std::vector<std::string>& dest, const std::vector<std::string>& terms) {
    for (const auto& term : terms) {
        if (std::find(dest.begin(), dest.end(), term) == dest.end()) {
            dest.push_back(term);
        }
    }
}

bool apply_filter_tokens(ItemGroup& group, size_t start_index, const std::vector<std::string>& tokens,
                         bool append) {
    bool changed = false;
    for (size_t i = start_index; i < tokens.size(); ++i) {
        const auto& token = tokens[i];
        const auto include_pos = token.find("include:");
        const auto exclude_pos = token.find("exclude:");
        if (include_pos != std::string::npos) {
            const auto terms = split_csv(token.substr(include_pos + 8));
            if (append) {
                append_unique_terms(group.include, terms);
            } else {
                group.include = terms;
            }
            changed = true;
        }
        if (exclude_pos != std::string::npos) {
            const auto terms = split_csv(token.substr(exclude_pos + 8));
            if (append) {
                append_unique_terms(group.exclude, terms);
            } else {
                group.exclude = terms;
            }
            changed = true;
        }
    }
    return changed;
}

bool apply_member_group_tokens(Supergroup& supergroup, size_t start_index,
                               const std::vector<std::string>& tokens, bool append) {
    bool changed = false;
    for (size_t i = start_index; i < tokens.size(); ++i) {
        const auto& token = tokens[i];
        const auto groups_pos = token.find("groups:");
        if (groups_pos != std::string::npos) {
            const auto terms = split_csv(token.substr(groups_pos + 7));
            if (append) {
                append_unique_terms(supergroup.member_groups, terms);
            } else {
                supergroup.member_groups = terms;
            }
            changed = true;
        }
    }
    return changed;
}

}  // namespace

std::vector<std::string> App::build_help_text() {
    return {
        "Quantity column:",
        "  total                 Show Total column",
        "  missing               Show Missing column (default)",
        "  available             Show Available column",
        "",
        "Quantity format:",
        "  raw                   Plain item counts",
        "  css                   Chests/stacks/items (e.g. 5c+5s+10)",
        "",
        "Groups:",
        "  groups                List saved groups and on/off state",
        "  group add <name> include:<term>[,<term>] [exclude:<term>[,<term>]]",
        "  group edit <name> include:<term>[,<term>] [exclude:<term>[,<term>]]",
        "  group on <name>       Enable a group",
        "  group off <name>      Disable a group",
        "  group remove <name>   Delete a saved group",
        "  group rename <old> <new> Rename a group",
        "  group order <name> <n> Set group display order",
        "",
        "Supergroups:",
        "  supergroups             List saved supergroups and on/off state",
        "  supergroup add <name> groups:<group>[,<group>]",
        "  supergroup edit <name> groups:<group>[,<group>]",
        "  supergroup on <name>    Enable a supergroup",
        "  supergroup off <name>   Disable a supergroup",
        "  supergroup remove <name> Delete a supergroup",
        "  supergroup rename <old> <new> Rename a supergroup",
        "  supergroup order <name> <n> Set supergroup display order",
        "",
        "Fulfillment:",
        "  fulfill <n>           Mark item n fulfilled (saves to file)",
        "  a <n>                 Short for fulfill",
        "  unfulfill <n>         Remove fulfillment marker",
        "  u <n>                 Short for unfulfill",
        "",
        "Navigation:",
        "  up                    Scroll up",
        "  down                  Scroll down",
        "  Up arrow (typing)     Previous command",
        "  Down arrow (typing)   Next command",
        "",
        "Other:",
        "  reload                Re-read the material list from disk",
        "  help                  Show this help screen",
        "  quit, q, exit         Exit stackSquing",
    };
}

App::App(std::string file_path) : file_path_(std::move(file_path)) {}

int App::run() {
    try {
        list_ = MaterialListParser::load(file_path_);
    } catch (const std::exception& ex) {
        fprintf(stderr, "Error: %s\n", ex.what());
        return 1;
    }

    if (!groups_.load()) {
        fprintf(stderr, "Warning: could not parse groups config at %s\n", groups_.config_path().c_str());
    }

    if (!ui_.init()) {
        fprintf(stderr, "Error: failed to initialize terminal UI\n");
        return 1;
    }

    rebuild_and_draw();

    while (!ui_.should_quit()) {
        const std::string command = trim(ui_.read_line(command_history_));
        if (command.empty()) {
            rebuild_and_draw();
            continue;
        }

        if (!handle_command(command)) {
            ui_.shutdown();
            return 0;
        }
        if (!command.empty() &&
            (command_history_.empty() || command_history_.back() != command)) {
            command_history_.push_back(command);
        }
        rebuild_and_draw();
    }

    ui_.shutdown();
    return 0;
}

void App::rebuild_and_draw() {
    const auto rows = DisplayOrderBuilder::build(list_, groups_, settings_.column);
    ui_.draw(list_, rows, settings_, groups_, input_line_, status_message_, scroll_offset_, help_lines_);
}

bool App::fulfill_display_number(int display_number, bool fulfilled) {
    const auto rows = DisplayOrderBuilder::build(list_, groups_, settings_.column);
    const DisplayRow* row = nullptr;
    for (const auto& r : rows) {
        if (r.kind == DisplayRowKind::Item && r.display_number == display_number) {
            row = &r;
            break;
        }
    }
    if (!row || row->item_index < 0) {
        status_message_ = "Invalid item number: " + std::to_string(display_number);
        return false;
    }

    try {
        MaterialListParser::set_fulfilled(list_, row->item_index, fulfilled);
        MaterialListParser::save(list_);
        status_message_ = fulfilled ? "Marked item fulfilled." : "Removed fulfillment.";
        return true;
    } catch (const std::exception& ex) {
        status_message_ = ex.what();
        return false;
    }
}

bool App::handle_command(const std::string& command) {
    const auto tokens = split_tokens(command);
    if (tokens.empty()) {
        return true;
    }

    const std::string& cmd = tokens[0];

    if (cmd != "help" && cmd != "up" && cmd != "down" && !help_lines_.empty()) {
        help_lines_.clear();
    }

    if (cmd == "q" || cmd == "quit" || cmd == "exit") {
        return false;
    }
    if (cmd == "help") {
        help_lines_ = build_help_text();
        scroll_offset_ = 0;
        status_message_ = "Showing help. Use up/down to scroll.";
        return true;
    }
    if (cmd == "total") {
        settings_.column = QuantityColumn::Total;
        status_message_ = "Showing total column.";
        return true;
    }
    if (cmd == "missing") {
        settings_.column = QuantityColumn::Missing;
        status_message_ = "Showing missing column.";
        return true;
    }
    if (cmd == "available") {
        settings_.column = QuantityColumn::Available;
        status_message_ = "Showing available column.";
        return true;
    }
    if (cmd == "raw") {
        settings_.format = QuantityFormat::Raw;
        status_message_ = "Showing raw quantities.";
        return true;
    }
    if (cmd == "css") {
        settings_.format = QuantityFormat::Css;
        status_message_ = "Showing chest/stack/item quantities.";
        return true;
    }
    if (cmd == "up") {
        scroll_offset_ = std::max(0, scroll_offset_ - 1);
        status_message_.clear();
        return true;
    }
    if (cmd == "down") {
        scroll_offset_ += 1;
        status_message_.clear();
        return true;
    }
    if (cmd == "reload") {
        try {
            list_ = MaterialListParser::load(file_path_);
            status_message_ = "Reloaded material list.";
        } catch (const std::exception& ex) {
            status_message_ = ex.what();
        }
        return true;
    }
    if (cmd == "groups") {
        std::ostringstream oss;
        for (const auto& group : groups_.groups()) {
            oss << group.name << (group.enabled ? " [on]" : " [off]") << "  ";
        }
        if (groups_.groups().empty()) {
            oss << "No saved groups.";
        }
        status_message_ = oss.str();
        return true;
    }
    if (cmd == "supergroups") {
        std::ostringstream oss;
        for (const auto& supergroup : groups_.supergroups()) {
            oss << supergroup.name << (supergroup.enabled ? " [on]" : " [off]") << "  ";
        }
        if (groups_.supergroups().empty()) {
            oss << "No saved supergroups.";
        }
        status_message_ = oss.str();
        return true;
    }
    if (cmd == "group" && tokens.size() >= 2) {
        const std::string& sub = tokens[1];
        if (sub == "on" && tokens.size() >= 3) {
            if (auto* group = groups_.find_group(tokens[2])) {
                group->enabled = true;
                groups_.save();
                status_message_ = "Enabled group: " + group->name;
            } else {
                status_message_ = "Group not found: " + tokens[2];
            }
            return true;
        }
        if (sub == "off" && tokens.size() >= 3) {
            if (auto* group = groups_.find_group(tokens[2])) {
                group->enabled = false;
                groups_.save();
                status_message_ = "Disabled group: " + group->name;
            } else {
                status_message_ = "Group not found: " + tokens[2];
            }
            return true;
        }
        if (sub == "remove" && tokens.size() >= 3) {
            if (groups_.remove_group(tokens[2])) {
                status_message_ = "Removed group: " + tokens[2];
            } else {
                status_message_ = "Group not found: " + tokens[2];
            }
            return true;
        }
        if (sub == "order" && tokens.size() >= 4) {
            if (auto* group = groups_.find_group(tokens[2])) {
                group->order = std::stoi(tokens[3]);
                groups_.save();
                groups_.load();
                status_message_ = "Updated group order.";
            } else {
                status_message_ = "Group not found: " + tokens[2];
            }
            return true;
        }
        if (sub == "add" && tokens.size() >= 3) {
            ItemGroup group;
            group.name = tokens[2];
            group.enabled = true;
            group.order = static_cast<int>(groups_.groups().size());

            if (!apply_filter_tokens(group, 3, tokens, false)) {
                status_message_ = "Group add requires include:term[,term]";
                return true;
            }

            if (group.include.empty()) {
                status_message_ = "Group add requires include:term[,term]";
                return true;
            }

            if (groups_.find_group(group.name)) {
                status_message_ = "Group already exists: " + group.name;
                return true;
            }

            if (groups_.add_group(group)) {
                status_message_ = "Added group: " + group.name;
            } else {
                status_message_ = "Failed to save group.";
            }
            return true;
        }
        if (sub == "edit" && tokens.size() >= 4) {
            if (auto* group = groups_.find_group(tokens[2])) {
                if (!apply_filter_tokens(*group, 3, tokens, true)) {
                    status_message_ = "Group edit requires include:term[,term] and/or exclude:term[,term]";
                    return true;
                }
                if (groups_.save()) {
                    status_message_ = "Updated group: " + group->name;
                } else {
                    status_message_ = "Failed to save group.";
                }
            } else {
                status_message_ = "Group not found: " + tokens[2];
            }
            return true;
        }
        if (sub == "rename" && tokens.size() >= 4) {
            const std::string& old_name = tokens[2];
            const std::string& new_name = tokens[3];
            if (groups_.find_group(old_name) == nullptr) {
                status_message_ = "Group not found: " + old_name;
                return true;
            }
            if (groups_.find_group(new_name) != nullptr) {
                status_message_ = "Group already exists: " + new_name;
                return true;
            }
            if (groups_.rename_group(old_name, new_name)) {
                status_message_ = "Renamed group to: " + new_name;
            } else {
                status_message_ = "Failed to rename group.";
            }
            return true;
        }
        status_message_ = "Usage: group on|off|remove|rename|order|add|edit ...";
        return true;
    }
    if (cmd == "supergroup" && tokens.size() >= 2) {
        const std::string& sub = tokens[1];
        if (sub == "on" && tokens.size() >= 3) {
            if (auto* supergroup = groups_.find_supergroup(tokens[2])) {
                supergroup->enabled = true;
                groups_.save();
                status_message_ = "Enabled supergroup: " + supergroup->name;
            } else {
                status_message_ = "Supergroup not found: " + tokens[2];
            }
            return true;
        }
        if (sub == "off" && tokens.size() >= 3) {
            if (auto* supergroup = groups_.find_supergroup(tokens[2])) {
                supergroup->enabled = false;
                groups_.save();
                status_message_ = "Disabled supergroup: " + supergroup->name;
            } else {
                status_message_ = "Supergroup not found: " + tokens[2];
            }
            return true;
        }
        if (sub == "remove" && tokens.size() >= 3) {
            if (groups_.remove_supergroup(tokens[2])) {
                status_message_ = "Removed supergroup: " + tokens[2];
            } else {
                status_message_ = "Supergroup not found: " + tokens[2];
            }
            return true;
        }
        if (sub == "order" && tokens.size() >= 4) {
            if (auto* supergroup = groups_.find_supergroup(tokens[2])) {
                supergroup->order = std::stoi(tokens[3]);
                groups_.save();
                groups_.load();
                status_message_ = "Updated supergroup order.";
            } else {
                status_message_ = "Supergroup not found: " + tokens[2];
            }
            return true;
        }
        if (sub == "add" && tokens.size() >= 3) {
            Supergroup supergroup;
            supergroup.name = tokens[2];
            supergroup.enabled = true;
            supergroup.order = static_cast<int>(groups_.supergroups().size());

            if (!apply_member_group_tokens(supergroup, 3, tokens, false) ||
                supergroup.member_groups.empty()) {
                status_message_ = "Supergroup add requires groups:Group1[,Group2]";
                return true;
            }

            if (groups_.find_supergroup(supergroup.name)) {
                status_message_ = "Supergroup already exists: " + supergroup.name;
                return true;
            }

            if (groups_.add_supergroup(supergroup)) {
                status_message_ = "Added supergroup: " + supergroup.name;
            } else {
                status_message_ = "Failed to save supergroup.";
            }
            return true;
        }
        if (sub == "edit" && tokens.size() >= 4) {
            if (auto* supergroup = groups_.find_supergroup(tokens[2])) {
                if (!apply_member_group_tokens(*supergroup, 3, tokens, true)) {
                    status_message_ = "Supergroup edit requires groups:Group1[,Group2]";
                    return true;
                }
                if (groups_.save()) {
                    status_message_ = "Updated supergroup: " + supergroup->name;
                } else {
                    status_message_ = "Failed to save supergroup.";
                }
            } else {
                status_message_ = "Supergroup not found: " + tokens[2];
            }
            return true;
        }
        if (sub == "rename" && tokens.size() >= 4) {
            const std::string& old_name = tokens[2];
            const std::string& new_name = tokens[3];
            if (groups_.find_supergroup(old_name) == nullptr) {
                status_message_ = "Supergroup not found: " + old_name;
                return true;
            }
            if (groups_.find_supergroup(new_name) != nullptr) {
                status_message_ = "Supergroup already exists: " + new_name;
                return true;
            }
            if (groups_.rename_supergroup(old_name, new_name)) {
                status_message_ = "Renamed supergroup to: " + new_name;
            } else {
                status_message_ = "Failed to rename supergroup.";
            }
            return true;
        }
        status_message_ = "Usage: supergroup on|off|remove|rename|order|add|edit ...";
        return true;
    }
    if ((cmd == "fulfill" || cmd == "a") && tokens.size() >= 2) {
        fulfill_display_number(std::stoi(tokens[1]), true);
        return true;
    }
    if ((cmd == "unfulfill" || cmd == "u") && tokens.size() >= 2) {
        fulfill_display_number(std::stoi(tokens[1]), false);
        return true;
    }

    status_message_ = "Unknown command. Type help.";
    return true;
}
