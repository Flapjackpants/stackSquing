#pragma once

#include <string>
#include <vector>

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
    std::vector<std::string> disk_snapshot_;
    GroupStore groups_;
    AppSettings settings_;
    NcursesRenderer ui_;

    std::string input_line_;
    std::string status_message_;
    std::vector<std::string> help_lines_;
    std::string panel_title_ = "Help";
    std::vector<std::string> command_history_;
    int scroll_offset_ = 0;

    static std::vector<std::string> build_help_text();
    void remember_disk_snapshot();
    bool file_externally_changed() const;
    bool load_list_from_path(const std::string& path, std::string* error_out = nullptr);
    std::string reload_status_message(bool switched_file) const;
    void rebuild_and_draw();
    bool handle_command(const std::string& command);
    bool fulfill_display_range(int lo, int hi, bool fulfilled);
};
