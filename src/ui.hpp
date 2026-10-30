#pragma once

#include <string>
#include <utility>
#include <vector>

#include "display_order.hpp"
#include "groups.hpp"
#include "model.hpp"

struct LineInput {
    std::string text;
    bool resized = false;
    int scroll_pages = 0;  // +1 PageDown, -1 PageUp
};

class NcursesRenderer {
public:
    NcursesRenderer();
    ~NcursesRenderer();

    NcursesRenderer(const NcursesRenderer&) = delete;
    NcursesRenderer& operator=(const NcursesRenderer&) = delete;

    bool init();
    void shutdown();

    int draw(const MaterialList& list,
              const std::vector<DisplayRow>& rows,
              const AppSettings& settings,
              const GroupStore& groups,
              const std::string& input_line,
              const std::string& status_message,
              int scroll_offset,
              const std::vector<std::string>& help_lines = {},
              const std::string& panel_title = "Help");

    LineInput read_line(std::vector<std::string>& command_history, const std::string& initial = "");
    bool should_quit() const { return should_quit_; }
    bool needs_redraw() const { return needs_redraw_; }
    int page_rows() const { return page_rows_; }
    void clear_needs_redraw() { needs_redraw_ = false; }

private:
    bool should_quit_ = false;
    bool needs_redraw_ = true;
    bool strikethrough_supported_ = false;
    int page_rows_ = 1;

    std::string truncate(const std::string& text, int width) const;
    void draw_box_segments(int y, int width,
                           const std::vector<std::pair<std::string, int>>& segments) const;
    void draw_box_row(int y, int width, const std::string& content) const;
};
