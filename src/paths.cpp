#include "paths.hpp"

#include <filesystem>
#include <stdexcept>
#include <string>

namespace fs = std::filesystem;

namespace {

bool starts_with(const std::string& s, const std::string& prefix) {
    return s.size() >= prefix.size() && s.compare(0, prefix.size(), prefix) == 0;
}

bool ends_with(const std::string& s, const std::string& suffix) {
    return s.size() >= suffix.size() &&
           s.compare(s.size() - suffix.size(), suffix.size(), suffix) == 0;
}

bool is_material_list_filename(const std::string& name) {
    return starts_with(name, "material_list_") && ends_with(name, ".txt");
}

}  // namespace

std::string path_basename(const std::string& path) {
    return fs::path(path).filename().string();
}

std::string parent_directory(const std::string& path) {
    const fs::path p(path);
    if (p.has_parent_path()) {
        const auto parent = p.parent_path();
        if (!parent.empty()) {
            return parent.string();
        }
    }
    return ".";
}

std::string newest_material_list_in_dir(const std::string& dir) {
    std::error_code ec;
    if (!fs::is_directory(dir, ec)) {
        return {};
    }

    std::string best_path;
    fs::file_time_type best_time{};
    bool found = false;

    for (const auto& entry : fs::directory_iterator(dir, ec)) {
        if (ec) {
            break;
        }
        std::error_code entry_ec;
        if (!entry.is_regular_file(entry_ec) || entry_ec) {
            continue;
        }
        const auto name = entry.path().filename().string();
        if (!is_material_list_filename(name)) {
            continue;
        }
        const auto write_time = entry.last_write_time(entry_ec);
        if (entry_ec) {
            continue;
        }
        if (!found || write_time > best_time) {
            best_time = write_time;
            best_path = entry.path().string();
            found = true;
        }
    }

    return best_path;
}

std::string resolve_material_list_path(const std::string& path_or_dir) {
    std::error_code ec;
    const fs::path path(path_or_dir);

    if (fs::is_directory(path, ec) && !ec) {
        const auto newest = newest_material_list_in_dir(path.string());
        if (newest.empty()) {
            throw std::runtime_error("No material_list_*.txt found in directory: " + path_or_dir);
        }
        return newest;
    }

    if (fs::is_regular_file(path, ec) && !ec) {
        return path.string();
    }

    throw std::runtime_error("Path is not a material list file or directory: " + path_or_dir);
}
