#include "app.hpp"

#include <algorithm>
#include <cctype>
#include <fstream>
#include <set>
#include <sstream>
#include <utility>
#include <vector>

#include "display_order.hpp"
#include "overview.hpp"
#include "parser.hpp"
#include "paths.hpp"

namespace {

std::vector<std::string> read_file_lines(const std::string& path) {
    std::ifstream in(path);
    std::vector<std::string> lines;
    if (!in) {
        return lines;
    }
    std::string line;
    while (std::getline(in, line)) {
        lines.push_back(line);
    }
    return lines;
}

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

// Parse include:/exclude: from the raw command so spaces inside comma-separated terms are kept.
bool apply_filter_string(std::vector<std::string>& include, std::vector<std::string>& exclude,
                         const std::string& text, bool append) {
    bool changed = false;
    size_t pos = 0;
    while (pos < text.size()) {
        const size_t inc = text.find("include:", pos);
        const size_t exc = text.find("exclude:", pos);

        size_t key_pos = std::string::npos;
        bool is_include = false;
        if (inc != std::string::npos && (exc == std::string::npos || inc < exc)) {
            key_pos = inc;
            is_include = true;
        } else if (exc != std::string::npos) {
            key_pos = exc;
            is_include = false;
        } else {
            break;
        }

        const size_t value_start = key_pos + 8;
        size_t value_end = text.size();
        const size_t next_inc = text.find("include:", value_start);
        const size_t next_exc = text.find("exclude:", value_start);
        if (next_inc != std::string::npos) {
            value_end = std::min(value_end, next_inc);
        }
        if (next_exc != std::string::npos) {
            value_end = std::min(value_end, next_exc);
        }

        const auto terms = split_csv(text.substr(value_start, value_end - value_start));
        if (is_include) {
            if (append) {
                append_unique_terms(include, terms);
            } else {
                include = terms;
            }
        } else {
            if (append) {
                append_unique_terms(exclude, terms);
            } else {
                exclude = terms;
            }
        }
        changed = true;
        pos = value_end;
    }
    return changed;
}

bool parse_display_range(const std::string& s, int& lo, int& hi) {
    if (s.empty()) {
        return false;
    }
    const size_t dash = s.find('-');
    try {
        if (dash == std::string::npos) {
            lo = hi = std::stoi(s);
            return true;
        }
        if (dash == 0 || dash + 1 >= s.size()) {
            return false;
        }
        lo = std::stoi(s.substr(0, dash));
        hi = std::stoi(s.substr(dash + 1));
        if (lo > hi) {
            std::swap(lo, hi);
        }
        return true;
    } catch (const std::exception&) {
        return false;
    }
}

}  // namespace

std::vector<std::string> App::build_help_text() {
    return {
        "Quantity column:",
        "  total                           Show Total column",
        "  missing                         Show Missing column (default)",
        "  available                       Show Available column",
        "  need                            Show still need (missing − available)",
        "",
        "Quantity format:",
        "  raw                             Plain item counts",
        "  css                             Chests/stacks/items (per-item stack size: 64/16/1)",
        "",
        "Groups:",
        "  groups                          List saved groups and on/off state",
        "  group add <name> include:<term>[,<term>] [exclude:<term>[,<term>]]",
        "                                  (terms may contain spaces; commas separate terms)",
        "  group edit <name> include:<term>[,<term>] [exclude:<term>[,<term>]]",
        "  group on <name>                 Enable a group",
        "  group off <name>                Disable a group",
        "  group remove <name>             Delete a saved group",
        "  group rename <old> <new>        Rename a group",
        "  group order <name> <n>          Set group display order",
        "",
        "Supergroups:",
        "  supergroups                     List saved supergroups and on/off state",
        "  supergroup add <name> include:<term>[,<term>] [exclude:<term>[,<term>]]",
        "                                  (matches group names; terms may contain spaces)",
        "  supergroup edit <name> include:<term>[,<term>] [exclude:<term>[,<term>]]",
        "  supergroup on <name>            Enable a supergroup",
        "  supergroup off <name>           Disable a supergroup",
        "  supergroup remove <name>        Delete a supergroup",
        "  supergroup rename <old> <new>   Rename a supergroup",
        "  supergroup order <name> <n>     Set supergroup display order",
        "",
        "Display:",
        "  overview                        Show all groups and supergroups (no items)",
        "  hidefulfilled on                Hide groups when all items fulfilled (default)",
        "  hidefulfilled off               Keep showing fully fulfilled groups",
        "",
        "Fulfillment:",
        "  fulfill <n>                     Mark item n fulfilled (saves to file)",
        "  fulfill <n>-<m>                 Mark items n through m inclusive",
        "  a <n> / a <n>-<m>               Short for fulfill",
        "  unfulfill <n>                   Remove fulfillment marker",
        "  unfulfill <n>-<m>               Unfulfill a range",
        "  u <n> / u <n>-<m>               Short for unfulfill",
        "",
        "Navigation:",
        "  up                              Scroll up",
        "  down                            Scroll down",
        "  PageUp/PageDown                 Scroll one page (while typing)",
        "  Up arrow (typing)               Previous command",
        "  Down arrow (typing)             Next command",
        "  Left/Right (typing)             Move cursor",
        "  Home/End (typing)               Jump to start/end of input",
        "  Insert (typing)                 Toggle insert/overwrite mode",
        "  Delete (typing)                 Delete character after cursor",
        "  Window resize                   Redraws automatically; typed input is kept",
        "",
        "Other:",
        "  reload                          Re-read current file, or newer material_list_*.txt",
        "                                  in the same folder (Litematica timestamped exports)",
        "  latest                          Load the newest material_list_*.txt in the same folder",
        "  open <path>                     Open a material list file or directory",
        "  help                            Show this help screen",
        "  quit, q, exit                   Exit stackSquing",
    };
}

App::App(std::string file_path) : file_path_(std::move(file_path)) {}

void App::remember_disk_snapshot() {
    disk_snapshot_ = list_.raw_lines;
}

bool App::file_externally_changed() const {
    return read_file_lines(file_path_) != disk_snapshot_;
}

bool App::load_list_from_path(const std::string& path, std::string* error_out) {
    try {
        const std::string resolved = resolve_material_list_path(path);
        list_ = MaterialListParser::load(resolved);
        file_path_ = resolved;
        remember_disk_snapshot();
        scroll_offset_ = 0;
        return true;
    } catch (const std::exception& ex) {
        if (error_out != nullptr) {
            *error_out = ex.what();
        }
        return false;
    }
}

std::string App::reload_status_message(bool switched_file) const {
    std::ostringstream oss;
    if (switched_file) {
        oss << "Loaded newer export: " << path_basename(file_path_);
    } else {
        oss << "Reloaded " << path_basename(file_path_);
    }
    oss << " (" << list_.items.size() << " items";
    if (!list_.placement_name.empty()) {
        oss << ", " << list_.placement_name;
    }
    oss << ").";
    return oss.str();
}

int App::run() {
    try {
        list_ = MaterialListParser::load(file_path_);
        remember_disk_snapshot();
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

    status_message_ = "Loaded " + path_basename(file_path_) + ".";
    rebuild_and_draw();

    std::string pending_input;
    while (!ui_.should_quit()) {
        const LineInput input = ui_.read_line(command_history_, pending_input);
        if (input.resized) {
            pending_input = input.text;
            input_line_ = pending_input;
            rebuild_and_draw();
            continue;
        }
        if (input.scroll_pages != 0) {
            pending_input = input.text;
            input_line_ = pending_input;
            scroll_offset_ = std::max(0, scroll_offset_ + input.scroll_pages * ui_.page_rows());
            rebuild_and_draw();
            continue;
        }
        pending_input.clear();
        input_line_.clear();
        const std::string command = trim(input.text);
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
    if (file_externally_changed()) {
        status_message_ =
            "File changed on disk. Type reload (or latest for a newer export).";
    }
    const auto rows =
        DisplayOrderBuilder::build(list_, groups_, settings_.column, settings_.hide_fulfilled_groups);
    scroll_offset_ = ui_.draw(list_, rows, settings_, groups_, input_line_, status_message_, scroll_offset_, help_lines_, panel_title_);
}

bool App::fulfill_display_range(int lo, int hi, bool fulfilled) {
    if (file_externally_changed()) {
        status_message_ = "File changed on disk. Run reload before fulfilling.";
        return false;
    }

    const auto rows = DisplayOrderBuilder::build(list_, groups_, settings_.column,
                                                 settings_.hide_fulfilled_groups);
    std::set<int> indices;
    for (const auto& r : rows) {
        if (r.kind == DisplayRowKind::Item && r.display_number >= lo && r.display_number <= hi &&
            r.item_index >= 0) {
            indices.insert(r.item_index);
        }
    }
    if (indices.empty()) {
        if (lo == hi) {
            status_message_ = "Invalid item number: " + std::to_string(lo);
        } else {
            status_message_ = "No items in range " + std::to_string(lo) + "-" + std::to_string(hi);
        }
        return false;
    }

    try {
        for (int item_index : indices) {
            MaterialListParser::set_fulfilled(list_, item_index, fulfilled);
        }
        MaterialListParser::save(list_);
        remember_disk_snapshot();
        const std::string count = std::to_string(indices.size());
        if (fulfilled) {
            status_message_ = indices.size() == 1 ? "Marked item fulfilled."
                                                  : "Marked " + count + " items fulfilled.";
        } else {
            status_message_ = indices.size() == 1 ? "Removed fulfillment."
                                                  : "Removed fulfillment from " + count + " items.";
        }
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

    if (cmd != "help" && cmd != "overview" && cmd != "up" && cmd != "down" && !help_lines_.empty()) {
        help_lines_.clear();
    }

    if (cmd == "q" || cmd == "quit" || cmd == "exit") {
        return false;
    }
    if (cmd == "help") {
        help_lines_ = build_help_text();
        panel_title_ = "Help";
        scroll_offset_ = 0;
        status_message_ = "Showing help. Use up/down to scroll.";
        return true;
    }

    if (cmd == "overview") {
        help_lines_ = build_group_overview(groups_);
        panel_title_ = "Group overview";
        scroll_offset_ = 0;
        status_message_ = "Showing group overview. Use up/down to scroll.";
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
    if (cmd == "need") {
        settings_.column = QuantityColumn::Need;
        status_message_ = "Showing need column (missing − available).";
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
    if (cmd == "hidefulfilled" && tokens.size() >= 2) {
        if (tokens[1] == "on") {
            settings_.hide_fulfilled_groups = true;
            status_message_ = "Hiding fully fulfilled groups.";
            return true;
        }
        if (tokens[1] == "off") {
            settings_.hide_fulfilled_groups = false;
            status_message_ = "Showing fully fulfilled groups.";
            return true;
        }
        status_message_ = "Usage: hidefulfilled on|off";
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
        const std::string previous_path = file_path_;
        const std::string dir = parent_directory(file_path_);
        const std::string newest = newest_material_list_in_dir(dir);
        const std::string target =
            (!newest.empty() && newest != file_path_) ? newest : file_path_;

        std::string error;
        if (load_list_from_path(target, &error)) {
            status_message_ = reload_status_message(target != previous_path);
        } else {
            status_message_ = error.empty() ? "Failed to reload material list." : error;
        }
        return true;
    }
    if (cmd == "latest") {
        const std::string dir = parent_directory(file_path_);
        const std::string newest = newest_material_list_in_dir(dir);
        if (newest.empty()) {
            status_message_ = "No material_list_*.txt found in " + dir;
            return true;
        }
        const std::string previous_path = file_path_;
        std::string error;
        if (load_list_from_path(newest, &error)) {
            if (newest == previous_path) {
                status_message_ = reload_status_message(false);
            } else {
                status_message_ = reload_status_message(true);
            }
        } else {
            status_message_ = error.empty() ? "Failed to load latest material list." : error;
        }
        return true;
    }
    if (cmd == "open") {
        if (tokens.size() < 2) {
            status_message_ = "Usage: open <material_list_file_or_directory>";
            return true;
        }
        // Rejoin tokens in case the path contains spaces (unlikely but safe for absolute paths)
        std::string path = tokens[1];
        for (size_t i = 2; i < tokens.size(); ++i) {
            path.push_back(' ');
            path += tokens[i];
        }
        const std::string previous_path = file_path_;
        std::string error;
        if (load_list_from_path(path, &error)) {
            status_message_ = reload_status_message(file_path_ != previous_path);
        } else {
            status_message_ = error.empty() ? "Failed to open material list." : error;
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

            if (!apply_filter_string(group.include, group.exclude, command, false)) {
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
                if (!apply_filter_string(group->include, group->exclude, command, true)) {
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

            if (!apply_filter_string(supergroup.include, supergroup.exclude, command, false) ||
                supergroup.include.empty()) {
                status_message_ = "Supergroup add requires include:term[,term]";
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
                if (!apply_filter_string(supergroup->include, supergroup->exclude, command, true)) {
                    status_message_ = "Supergroup edit requires include:term[,term] and/or exclude:term[,term]";
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
        int lo = 0;
        int hi = 0;
        if (!parse_display_range(tokens[1], lo, hi)) {
            status_message_ = "Usage: fulfill <n> or fulfill <n>-<m>";
            return true;
        }
        fulfill_display_range(lo, hi, true);
        return true;
    }
    if ((cmd == "unfulfill" || cmd == "u") && tokens.size() >= 2) {
        int lo = 0;
        int hi = 0;
        if (!parse_display_range(tokens[1], lo, hi)) {
            status_message_ = "Usage: unfulfill <n> or unfulfill <n>-<m>";
            return true;
        }
        fulfill_display_range(lo, hi, false);
        return true;
    }

    status_message_ = "Unknown command. Type help.";
    return true;
}
