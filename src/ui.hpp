#pragma once

#include <string>
#include <vector>

#include "display_order.hpp"
#include "groups.hpp"
#include "model.hpp"

class NcursesRenderer {
public:
    NcursesRenderer();
    ~NcursesRenderer();

    NcursesRenderer(const NcursesRenderer&) = delete;
    NcursesRenderer& operator=(const NcursesRenderer&) = delete;

    bool init();
    void shutdown();

    void draw(const MaterialList& list,
              const std::vector<DisplayRow>& rows,
              const AppSettings& settings,
              const GroupStore& groups,
              const std::string& input_line,
              const std::string& status_message,
              int scroll_offset,
              const std::vector<std::string>& help_lines = {});

    std::string read_line();
    bool should_quit() const { return should_quit_; }
    bool needs_redraw() const { return needs_redraw_; }
    void clear_needs_redraw() { needs_redraw_ = false; }

private:
    bool should_quit_ = false;
    bool needs_redraw_ = true;
    bool strikethrough_supported_ = false;

    void handle_resize();
    std::string truncate(const std::string& text, int width) const;
    void draw_box_row(int y, int width, const std::string& content) const;
};
