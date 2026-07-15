#pragma once

#include <string>

// If path is a directory, returns the newest material_list_*.txt inside it.
// If path is a file, returns it unchanged (must exist).
// Throws std::runtime_error on failure.
std::string resolve_material_list_path(const std::string& path_or_dir);

// Newest material_list_*.txt in dir by mtime. Empty string if none.
std::string newest_material_list_in_dir(const std::string& dir);

std::string parent_directory(const std::string& path);
std::string path_basename(const std::string& path);
