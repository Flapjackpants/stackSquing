#pragma once

#include <string>

enum class StatusKind { Normal, Warning, Error };
StatusKind classify_status(const std::string& message);

enum class PanelLineKind { Blank, Heading, Entry, Detail };
struct PanelLineParts {
    PanelLineKind kind = PanelLineKind::Blank;
    std::string indent;
    std::string key;
    std::string rest;
};
PanelLineParts split_panel_line(const std::string& line);

std::string scroll_indicator(int first, int last, int total);
int clamp_scroll(int offset, int total, int page_rows);
