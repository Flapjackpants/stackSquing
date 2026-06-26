#pragma once

#include <string>

#include "groups.hpp"
#include "model.hpp"
#include "ui.hpp"

class App {
public:
    explicit App(std::string file_path);

    int run();

private:
    std::string file_path_;
    MaterialList list_;
    GroupStore groups_;
    AppSettings settings_;
    NcursesRenderer ui_;

    std::string input_line_;
    std::string status_message_;
    int scroll_offset_ = 0;

    void rebuild_and_draw();
    bool handle_command(const std::string& command);
    bool fulfill_display_number(int display_number, bool fulfilled);
};
