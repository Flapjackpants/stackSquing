#include "ui.hpp"

#include <csignal>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <iomanip>
#include <sstream>
#include <vector>

#include <ncurses.h>

#include "format.hpp"

namespace {

int prompt_cursor_column(const std::string& line, int width) {
    const std::string content = "> " + line;
    if (static_cast<int>(content.size()) <= width - 2) {
        return 1 + static_cast<int>(content.size());
    }
    const std::string visible = content.substr(content.size() - static_cast<size_t>(width - 2));
    return 1 + static_cast<int>(visible.size());
}

volatile sig_atomic_t g_resize_flag = 0;

void on_sigwinch(int) { g_resize_flag = 1; }

std::string column_label(QuantityColumn column) {
    switch (column) {
        case QuantityColumn::Total:
            return "total";
        case QuantityColumn::Missing:
            return "missing";
        case QuantityColumn::Available:
            return "available";
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
    }
    strikethrough_supported_ = false;
#ifdef A_STRIKETHROUGH
    strikethrough_supported_ = (termattrs() & A_STRIKETHROUGH) != 0;
#endif
    signal(SIGWINCH, on_sigwinch);
    refresh();
    return true;
}

void NcursesRenderer::shutdown() {
    if (isendwin()) {
        return;
    }
    signal(SIGWINCH, SIG_DFL);
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

void NcursesRenderer::draw_box_row(int y, int width, const std::string& content) const {
    mvaddch(y, 0, '|');
    const int inner = width - 2;
    std::string line = truncate(content, inner);
    mvaddstr(y, 1, line.c_str());
    for (int x = 1 + static_cast<int>(line.size()); x < width - 1; ++x) {
        mvaddch(y, x, ' ');
    }
    mvaddch(y, width - 1, '|');
}

void NcursesRenderer::handle_resize() {
    if (g_resize_flag) {
        g_resize_flag = 0;
        endwin();
        refresh();
        clear();
        needs_redraw_ = true;
    }
}

void NcursesRenderer::draw(const MaterialList& list,
                           const std::vector<DisplayRow>& rows,
                           const AppSettings& settings,
                           const GroupStore& groups,
                           const std::string& input_line,
                           const std::string& status_message,
                           int scroll_offset,
                           const std::vector<std::string>& help_lines) {
    handle_resize();
    clear();

    const int width = COLS;
    const int height = LINES;

    if (width < 72) {
        mvaddstr(0, 0, "Terminal too narrow (minimum 72 columns).");
        refresh();
        return;
    }

    const int num_width = 4;
    const int qty_width = 12;
    const int status_width = 11;
    const int name_width = width - num_width - qty_width - status_width - 5;

    auto draw_hline = [&](int y_pos) {
        mvaddch(y_pos, 0, '+');
        for (int x = 1; x < width - 1; ++x) {
            mvaddch(y_pos, x, '-');
        }
        mvaddch(y_pos, width - 1, '+');
    };

    int y = 0;
    draw_hline(y++);

    std::ostringstream title;
    if (!list.placement_name.empty()) {
        title << "Material List for placement '" << list.placement_name << "'";
    } else {
        title << "Material List";
    }
    draw_box_row(y++, width, title.str());

    std::ostringstream meta;
    meta << "Column: " << column_label(settings.column)
         << " | Format: " << format_label(settings.format)
         << " | Groups: " << enabled_group_names(groups);
    draw_box_row(y++, width, meta.str());
    draw_hline(y++);

    draw_hline(y++);

    const int table_top = y;
    const int footer_rows = 4;
    const int visible_rows = height - table_top - footer_rows;
    const bool showing_help = !help_lines.empty();

    if (showing_help) {
        draw_box_row(y++, width, "Help");
        draw_box_row(y++, width, std::string(width - 4, '-'));

        const int total_rows = static_cast<int>(help_lines.size());
        const int max_scroll = std::max(0, total_rows - visible_rows + 2);
        const int effective_scroll = std::min(scroll_offset, max_scroll);

        for (int i = 0; i < visible_rows - 2; ++i) {
            const int row_idx = effective_scroll + i;
            if (row_idx >= total_rows) {
                break;
            }
            draw_box_row(table_top + 2 + i, width, help_lines[row_idx]);
        }
    } else {
        std::ostringstream header;
        header << " " << std::setw(num_width - 1) << "#"
               << " | " << std::left << std::setw(name_width) << "Item"
               << " | " << std::right << std::setw(qty_width) << "Qty"
               << " | " << std::left << std::setw(status_width) << "Status";
        draw_box_row(y++, width, header.str());

        std::string divider;
        divider.append(num_width, '-');
        divider += "-+-";
        divider.append(name_width, '-');
        divider += "-+-";
        divider.append(qty_width, '-');
        divider += "-+-";
        divider.append(status_width, '-');
        draw_box_row(y++, width, divider);

        const int total_rows = static_cast<int>(rows.size());
        const int max_scroll = std::max(0, total_rows - visible_rows + 2);
        const int effective_scroll = std::min(scroll_offset, max_scroll);

        for (int i = 0; i < visible_rows - 2; ++i) {
            const int row_idx = effective_scroll + i;
            if (row_idx >= total_rows) {
                break;
            }
            const auto& row = rows[row_idx];
            const int row_y = table_top + 2 + i;

            if (row.kind == DisplayRowKind::Separator) {
                attron(COLOR_PAIR(4) | A_DIM);
                draw_box_row(row_y, width, std::string(name_width + num_width + qty_width + status_width + 6, '-'));
                attroff(COLOR_PAIR(4) | A_DIM);
                continue;
            }

            std::ostringstream line;
            if (row.kind == DisplayRowKind::SupergroupHeader) {
                line << "S" << std::setw(num_width - 1) << row.display_number
                     << " | <<" << truncate(row.label, name_width - 4) << ">>"
                     << std::string(std::max(0, name_width - 4 - static_cast<int>(row.label.size())), ' ')
                     << " | " << std::right << std::setw(qty_width)
                     << format_quantity(row.quantity, settings.format)
                     << " | ";
                attron(COLOR_PAIR(1) | A_BOLD);
                draw_box_row(row_y, width, line.str());
                attroff(COLOR_PAIR(1) | A_BOLD);
                continue;
            }
            if (row.kind == DisplayRowKind::GroupHeader) {
                line << "G" << std::setw(num_width - 1) << row.display_number
                     << " | [" << truncate(row.label, name_width - 2) << "]"
                     << std::string(std::max(0, name_width - 2 - static_cast<int>(row.label.size())), ' ')
                     << " | " << std::right << std::setw(qty_width)
                     << format_quantity(row.quantity, settings.format)
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
                 << " | " << std::left << std::setw(name_width) << truncate(display_name, name_width)
                 << " | " << std::right << std::setw(qty_width)
                 << format_quantity(row.quantity, settings.format)
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

    draw_hline(height - footer_rows);
    draw_box_row(height - footer_rows + 1, width, "> " + input_line);
    if (!status_message.empty()) {
        attron(COLOR_PAIR(3));
        draw_box_row(height - footer_rows + 2, width, status_message);
        attroff(COLOR_PAIR(3));
    }
    draw_hline(height - 1);

    move(height - footer_rows + 1, prompt_cursor_column(input_line, width));
    refresh();
}

std::string NcursesRenderer::read_line(std::vector<std::string>& command_history) {
    handle_resize();

    std::string line;
    std::string saved_draft;
    size_t history_index = command_history.size();
    const int prompt_row = LINES - 3;

    auto redraw_prompt = [&]() {
        const int width = COLS;
        mvaddch(prompt_row, 0, '|');
        std::string content = "> " + line;
        if (static_cast<int>(content.size()) > width - 2) {
            content = content.substr(content.size() - static_cast<size_t>(width - 2));
        }
        mvaddstr(prompt_row, 1, content.c_str());
        for (int x = 1 + static_cast<int>(content.size()); x < width - 1; ++x) {
            mvaddch(prompt_row, x, ' ');
        }
        mvaddch(prompt_row, width - 1, '|');
        move(prompt_row, prompt_cursor_column(line, width));
        refresh();
    };

    curs_set(1);
    redraw_prompt();

    int ch = 0;
    while ((ch = getch()) != '\n' && ch != KEY_ENTER && ch != 27) {
        if (ch == ERR) {
            if (g_resize_flag) {
                handle_resize();
                redraw_prompt();
            }
            continue;
        }
        if (ch == KEY_UP) {
            if (!command_history.empty() && history_index > 0) {
                if (history_index == command_history.size()) {
                    saved_draft = line;
                }
                --history_index;
                line = command_history[history_index];
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
                redraw_prompt();
            }
            continue;
        }
        if (ch == KEY_BACKSPACE || ch == 127 || ch == 8) {
            if (!line.empty()) {
                line.pop_back();
                history_index = command_history.size();
                saved_draft.clear();
                redraw_prompt();
            }
            continue;
        }
        if (ch >= 32 && ch <= 126) {
            line.push_back(static_cast<char>(ch));
            history_index = command_history.size();
            saved_draft.clear();
            redraw_prompt();
        }
    }

    curs_set(1);
    return line;
}
