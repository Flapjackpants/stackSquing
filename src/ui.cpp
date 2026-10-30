#include "ui.hpp"
#include "ui_text.hpp"

#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <iomanip>
#include <sstream>
#include <vector>

#include <ncurses.h>

#include "format.hpp"

namespace {

constexpr size_t kPromptPrefixLen = 2;

struct PromptView {
    std::string visible;
    size_t scroll = 0;
    int cursor_column = 1;
};

PromptView build_prompt_view(const std::string& line, size_t cursor, int width) {
    PromptView view;
    const std::string content = "> " + line;
    const size_t visible_width = static_cast<size_t>(std::max(0, width - 2));

    if (content.size() <= visible_width) {
        view.visible = content;
        view.scroll = 0;
        view.cursor_column = 1 + static_cast<int>(kPromptPrefixLen + cursor);
        return view;
    }

    size_t scroll = 0;
    const size_t cursor_pos = kPromptPrefixLen + cursor;
    if (cursor_pos >= visible_width) {
        scroll = cursor_pos - visible_width + 1;
    }
    const size_t max_scroll = content.size() - visible_width;
    if (scroll > max_scroll) {
        scroll = max_scroll;
    }

    view.visible = content.substr(scroll, visible_width);
    view.scroll = scroll;
    view.cursor_column = 1 + static_cast<int>(cursor_pos - scroll);
    return view;
}

void insert_char(std::string& line, size_t& cursor, char ch, bool insert_mode) {
    if (cursor >= line.size()) {
        line.push_back(ch);
    } else if (insert_mode) {
        line.insert(cursor, 1, ch);
    } else {
        line[cursor] = ch;
    }
    ++cursor;
}

void delete_backward(std::string& line, size_t& cursor) {
    if (cursor == 0) {
        return;
    }
    line.erase(cursor - 1, 1);
    --cursor;
}

void delete_forward(std::string& line, size_t& cursor) {
    if (cursor >= line.size()) {
        return;
    }
    line.erase(cursor, 1);
}

std::string column_label(QuantityColumn column) {
    switch (column) {
        case QuantityColumn::Total:
            return "total";
        case QuantityColumn::Missing:
            return "missing";
        case QuantityColumn::Available:
            return "available";
        case QuantityColumn::Need:
            return "need";
    }
    return "missing";
}

std::string format_label(QuantityFormat format) {
    return format == QuantityFormat::Raw ? "raw" : "css";
}

std::string enabled_group_names(const GroupStore& groups) {
    std::ostringstream oss;
    bool first = true;
    for (const auto& supergroup : groups.supergroups()) {
        if (!supergroup.enabled) {
            continue;
        }
        if (!first) {
            oss << ", ";
        }
        oss << supergroup.name << "*";
        first = false;
    }
    for (const auto& group : groups.groups()) {
        if (!group.enabled) {
            continue;
        }
        if (!first) {
            oss << ", ";
        }
        oss << group.name;
        first = false;
    }
    if (first) {
        return "none";
    }
    return oss.str();
}

// TOC-style leader: "Label ................ 3s+50" (or spaces when use_dots is false)
std::string with_leader_dots(const std::string& label, const std::string& quantity, int width,
                             bool use_dots) {
    if (width <= 0) {
        return "";
    }
    if (static_cast<int>(quantity.size()) >= width) {
        return quantity.substr(quantity.size() - static_cast<size_t>(width));
    }

    const int min_gap = 2;  // " ." or "  " at least
    int label_budget = width - static_cast<int>(quantity.size()) - min_gap;
    if (label_budget < 1) {
        label_budget = 1;
    }

    std::string left = label;
    if (static_cast<int>(left.size()) > label_budget) {
        if (label_budget <= 1) {
            left = left.substr(0, static_cast<size_t>(label_budget));
        } else {
            left = left.substr(0, static_cast<size_t>(label_budget - 3)) + "...";
        }
    }

    const int gap = width - static_cast<int>(left.size()) - static_cast<int>(quantity.size()) - 2;
    if (gap < 1) {
        return left + " " + quantity;
    }
    const char fill = use_dots ? '.' : ' ';
    return left + " " + std::string(static_cast<size_t>(gap), fill) + " " + quantity;
}

}  // namespace

NcursesRenderer::NcursesRenderer() = default;

NcursesRenderer::~NcursesRenderer() { shutdown(); }

bool NcursesRenderer::init() {
    initscr();
    cbreak();
    noecho();
    keypad(stdscr, TRUE);
    curs_set(1);
    if (has_colors()) {
        start_color();
        use_default_colors();
        init_pair(1, COLOR_CYAN, -1);
        init_pair(2, COLOR_YELLOW, -1);
        init_pair(3, COLOR_GREEN, -1);
        init_pair(4, COLOR_BLACK, -1);
        init_pair(5, COLOR_RED, -1);
    }
    strikethrough_supported_ = false;
#ifdef A_STRIKETHROUGH
    strikethrough_supported_ = (termattrs() & A_STRIKETHROUGH) != 0;
#endif
    refresh();
    return true;
}

void NcursesRenderer::shutdown() {
    if (isendwin()) {
        return;
    }
    endwin();
}

std::string NcursesRenderer::truncate(const std::string& text, int width) const {
    if (width <= 0) {
        return "";
    }
    if (static_cast<int>(text.size()) <= width) {
        return text;
    }
    if (width <= 1) {
        return text.substr(0, static_cast<size_t>(width));
    }
    return text.substr(0, static_cast<size_t>(width - 3)) + "...";
}

namespace {

void draw_side_borders(int y, int width) {
    attr_t saved_attrs;
    short saved_pair;
    attr_get(&saved_attrs, &saved_pair, nullptr);
    attrset(A_NORMAL);
    mvaddch(y, 0, ACS_VLINE);
    mvaddch(y, width - 1, ACS_VLINE);
    attr_set(saved_attrs, saved_pair, nullptr);
}

}  // namespace

void NcursesRenderer::draw_box_row(int y, int width, const std::string& content) const {
    const int inner = width - 2;
    std::string line = truncate(content, inner);
    mvaddstr(y, 1, line.c_str());
    for (int x = 1 + static_cast<int>(line.size()); x < width - 1; ++x) {
        mvaddch(y, x, ' ');
    }
    draw_side_borders(y, width);
}

void NcursesRenderer::draw_box_segments(
    int y, int width, const std::vector<std::pair<std::string, int>>& segments) const {
    const int inner = width - 2;
    std::string combined;
    for (const auto& seg : segments) {
        combined += seg.first;
    }
    const std::string shown = truncate(combined, inner);
    size_t pos = 0;
    int x = 1;
    for (const auto& seg : segments) {
        if (pos >= shown.size()) {
            break;
        }
        const std::string piece = shown.substr(pos, seg.first.size());
        pos += piece.size();
        if (piece.empty()) {
            continue;
        }
        if (seg.second != 0) {
            attron(seg.second);
        }
        mvaddstr(y, x, piece.c_str());
        if (seg.second != 0) {
            attroff(seg.second);
        }
        x += static_cast<int>(piece.size());
    }
    for (; x < width - 1; ++x) {
        mvaddch(y, x, ' ');
    }
    draw_side_borders(y, width);
}

int NcursesRenderer::draw(const MaterialList& list,
                           const std::vector<DisplayRow>& rows,
                           const AppSettings& settings,
                           const GroupStore& groups,
                           const std::string& input_line,
                           const std::string& status_message,
                           int scroll_offset,
                           const std::vector<std::string>& help_lines,
                           const std::string& panel_title) {
    clear();

    const int width = COLS;
    const int height = LINES;

    if (width < 72) {
        const std::string msg = "Terminal too narrow (minimum 72 columns).";
        const int len = static_cast<int>(msg.size());
        mvaddstr(height / 2, std::max(0, (width - len) / 2), msg.c_str());
        refresh();
        return scroll_offset;
    }

    const int num_width = 4;
    const int qty_width = 12;
    const int status_width = 11;
    const int name_width = width - num_width - qty_width - status_width - 5;

    auto draw_hline = [&](int y_pos, chtype left, chtype right) {
        mvaddch(y_pos, 0, left);
        for (int x = 1; x < width - 1; ++x) {
            mvaddch(y_pos, x, ACS_HLINE);
        }
        mvaddch(y_pos, width - 1, right);
    };

    int y = 0;
    draw_hline(y++, ACS_ULCORNER, ACS_URCORNER);

    std::ostringstream title;
    if (!list.placement_name.empty()) {
        title << "Material List for placement '" << list.placement_name << "'";
    } else {
        title << "Material List";
    }
    const auto slash = list.file_path.find_last_of("/\\");
    const std::string filename = (slash == std::string::npos)
                                     ? list.file_path
                                     : list.file_path.substr(slash + 1);
    if (!filename.empty()) {
        title << "  [" << filename << "]";
    }
    attron(A_BOLD);
    draw_box_row(y++, width, title.str());
    attroff(A_BOLD);

    std::ostringstream meta;
    meta << "Column: " << column_label(settings.column)
         << " | Format: " << format_label(settings.format)
         << " | HideFul: " << (settings.hide_fulfilled_groups ? "on" : "off")
         << " | Groups: " << enabled_group_names(groups);
    draw_box_row(y++, width, meta.str());
    draw_hline(y++, ACS_LTEE, ACS_RTEE);

    const int table_top = y;
    const int footer_rows = 4;
    const int visible_rows = height - table_top - footer_rows;
    page_rows_ = std::max(1, visible_rows - 2);
    const bool showing_help = !help_lines.empty();
    int total = 0;
    int effective_scroll = 0;

    if (showing_help) {
        attron(A_BOLD | COLOR_PAIR(1));
        draw_box_row(y++, width, panel_title);
        attroff(A_BOLD | COLOR_PAIR(1));
        draw_box_row(y, width, "");
        attron(A_DIM);
        for (int x = 2; x <= width - 3; ++x) {
            mvaddch(y, x, ACS_HLINE);
        }
        attroff(A_DIM);
        ++y;

        total = static_cast<int>(help_lines.size());
        effective_scroll = clamp_scroll(scroll_offset, total, visible_rows - 2);

        for (int i = 0; i < visible_rows - 2; ++i) {
            const int row_idx = effective_scroll + i;
            if (row_idx >= total) {
                break;
            }
            const int line_y = table_top + 2 + i;
            const auto parts = split_panel_line(help_lines[row_idx]);
            switch (parts.kind) {
            case PanelLineKind::Blank:
                draw_box_row(line_y, width, "");
                break;
            case PanelLineKind::Heading:
                draw_box_segments(line_y, width, {{parts.key, COLOR_PAIR(1) | A_BOLD}});
                break;
            case PanelLineKind::Entry:
                draw_box_segments(line_y, width,
                                  {{parts.indent, 0}, {parts.key, COLOR_PAIR(2)}, {parts.rest, 0}});
                break;
            case PanelLineKind::Detail:
                draw_box_segments(line_y, width,
                                  {{parts.indent, 0}, {parts.key, static_cast<int>(A_DIM)}, {parts.rest, 0}});
                break;
            }
        }
    } else {
        const int leader_width = name_width + 3 + qty_width;

        std::ostringstream header;
        header << " " << std::setw(num_width - 1) << "#"
               << " | " << with_leader_dots("Item", "Qty", leader_width, true)
               << " | " << std::left << std::setw(status_width) << "Status";
        attron(A_BOLD);
        draw_box_row(y++, width, header.str());
        attroff(A_BOLD);

        draw_box_row(y, width, "");
        for (int x = 1; x < width - 1; ++x) {
            mvaddch(y, x, ACS_HLINE);
        }
        mvaddch(y, 1 + num_width + 1, ACS_PLUS);
        mvaddch(y, 1 + num_width + 3 + leader_width + 1, ACS_PLUS);
        ++y;

        total = static_cast<int>(rows.size());
        effective_scroll = clamp_scroll(scroll_offset, total, visible_rows - 2);

        for (int i = 0; i < visible_rows - 2; ++i) {
            const int row_idx = effective_scroll + i;
            if (row_idx >= total) {
                break;
            }
            const auto& row = rows[row_idx];
            const int row_y = table_top + 2 + i;

            if (row.kind == DisplayRowKind::Separator) {
                draw_box_row(row_y, width, "");
                attron(A_DIM);
                for (int x = 2; x <= width - 3; ++x) {
                    mvaddch(row_y, x, ACS_HLINE);
                }
                attroff(A_DIM);
                continue;
            }

            const std::string qty = format_quantity(row.quantity, settings.format, row.label);
            const bool use_dots = (row.display_number % 2) != 0;
            std::ostringstream line;

            if (row.kind == DisplayRowKind::SupergroupHeader) {
                const std::string label = "<<" + row.label + ">>";
                line << "S" << std::setw(num_width - 1) << row.display_number
                     << " | " << with_leader_dots(label, qty, leader_width, use_dots)
                     << " | ";
                attron(COLOR_PAIR(1) | A_BOLD);
                draw_box_row(row_y, width, line.str());
                attroff(COLOR_PAIR(1) | A_BOLD);
                continue;
            }
            if (row.kind == DisplayRowKind::GroupHeader) {
                const std::string label = "[" + row.label + "]";
                line << "G" << std::setw(num_width - 1) << row.display_number
                     << " | " << with_leader_dots(label, qty, leader_width, use_dots)
                     << " | ";
                attron(COLOR_PAIR(2) | A_BOLD);
                draw_box_row(row_y, width, line.str());
                attroff(COLOR_PAIR(2) | A_BOLD);
                continue;
            }

            std::string display_name = row.label;
            int attrs = 0;
            if (row.fulfilled) {
#ifdef A_STRIKETHROUGH
                if (strikethrough_supported_) {
                    attrs |= A_STRIKETHROUGH;
                } else {
                    display_name = "[x] " + display_name;
                }
#else
                display_name = "[x] " + display_name;
#endif
                attrs |= A_DIM;
            }

            line << " " << std::setw(num_width - 1) << row.display_number
                 << " | " << with_leader_dots(display_name, qty, leader_width, use_dots)
                 << " | " << std::left << std::setw(status_width)
                 << (row.fulfilled ? "fulfilled" : "");

            if (attrs) {
                attron(attrs);
            }
            draw_box_row(row_y, width, line.str());
            if (attrs) {
                attroff(attrs);
            }
        }
    }

    draw_hline(height - footer_rows, ACS_LTEE, ACS_RTEE);
    draw_box_row(height - footer_rows + 1, width, "> " + input_line);
    if (!status_message.empty()) {
        int status_attrs = COLOR_PAIR(3);
        switch (classify_status(status_message)) {
        case StatusKind::Warning:
            status_attrs = COLOR_PAIR(2) | A_BOLD;
            break;
        case StatusKind::Error:
            status_attrs = COLOR_PAIR(5) | A_BOLD;
            break;
        case StatusKind::Normal:
            break;
        }
        attron(status_attrs);
        draw_box_row(height - footer_rows + 2, width, status_message);
        attroff(status_attrs);
    }
    draw_hline(height - 1, ACS_LLCORNER, ACS_LRCORNER);

    const int first = effective_scroll + 1;
    const int last = std::min(total, effective_scroll + visible_rows - 2);
    const std::string hint = " help | overview | up/down scroll | q quit ";
    const std::string indicator = scroll_indicator(first, last, total);
    const std::string indicator_block = indicator.empty() ? "" : " " + indicator + " ";
    if (2 + static_cast<int>(hint.size() + indicator_block.size()) + 2 <= width) {
        attron(A_DIM);
        mvaddstr(height - 1, 2, hint.c_str());
        attroff(A_DIM);
    }
    if (!indicator_block.empty()) {
        const int ix = width - 2 - static_cast<int>(indicator_block.size());
        if (ix > 1) {
            mvaddstr(height - 1, ix, indicator_block.c_str());
        }
    }

    refresh();
    return effective_scroll;
}

LineInput NcursesRenderer::read_line(std::vector<std::string>& command_history,
                                     const std::string& initial) {
    std::string line = initial;
    size_t cursor = line.size();
    bool insert_mode = true;
    std::string saved_draft;
    size_t history_index = command_history.size();
    const int prompt_row = LINES - 3;

    auto redraw_prompt = [&]() {
        const int width = COLS;
        const auto view = build_prompt_view(line, cursor, width);
        mvaddch(prompt_row, 0, ACS_VLINE);
        mvaddstr(prompt_row, 1, view.visible.c_str());
        for (int x = 1 + static_cast<int>(view.visible.size()); x < width - 1; ++x) {
            mvaddch(prompt_row, x, ' ');
        }
        mvaddch(prompt_row, width - 1, ACS_VLINE);
        move(prompt_row, view.cursor_column);
        refresh();
    };

    curs_set(1);
    redraw_prompt();

    int ch = 0;
    while ((ch = getch()) != '\n' && ch != KEY_ENTER && ch != 27) {
        if (ch == ERR) {
            continue;
        }
        if (ch == KEY_RESIZE) {
            return {line, true};
        }
        if (ch == KEY_NPAGE) {
            return {line, false, 1};
        }
        if (ch == KEY_PPAGE) {
            return {line, false, -1};
        }

        if (ch == KEY_UP) {
            if (!command_history.empty() && history_index > 0) {
                if (history_index == command_history.size()) {
                    saved_draft = line;
                }
                --history_index;
                line = command_history[history_index];
                cursor = line.size();
                redraw_prompt();
            }
            continue;
        }
        if (ch == KEY_DOWN) {
            if (history_index < command_history.size()) {
                ++history_index;
                if (history_index == command_history.size()) {
                    line = saved_draft;
                } else {
                    line = command_history[history_index];
                }
                cursor = line.size();
                redraw_prompt();
            }
            continue;
        }
        if (ch == KEY_LEFT) {
            if (cursor > 0) {
                --cursor;
                redraw_prompt();
            }
            continue;
        }
        if (ch == KEY_RIGHT) {
            if (cursor < line.size()) {
                ++cursor;
                redraw_prompt();
            }
            continue;
        }
        if (ch == KEY_HOME || ch == 1) {  // Home or Ctrl+A
            cursor = 0;
            redraw_prompt();
            continue;
        }
        if (ch == KEY_END || ch == 5) {  // End or Ctrl+E
            cursor = line.size();
            redraw_prompt();
            continue;
        }
        if (ch == KEY_IC) {  // Insert toggles insert/overwrite mode
            insert_mode = !insert_mode;
            redraw_prompt();
            continue;
        }
        if (ch == KEY_BACKSPACE || ch == 127 || ch == 8) {
            if (cursor > 0) {
                delete_backward(line, cursor);
                history_index = command_history.size();
                saved_draft.clear();
                redraw_prompt();
            }
            continue;
        }
        if (ch == KEY_DC || ch == 4) {  // Delete or Ctrl+D
            if (cursor < line.size()) {
                delete_forward(line, cursor);
                history_index = command_history.size();
                saved_draft.clear();
                redraw_prompt();
            }
            continue;
        }
        if (ch >= 32 && ch <= 126) {
            insert_char(line, cursor, static_cast<char>(ch), insert_mode);
            history_index = command_history.size();
            saved_draft.clear();
            redraw_prompt();
        }
    }

    curs_set(1);
    return {line, false};
}
