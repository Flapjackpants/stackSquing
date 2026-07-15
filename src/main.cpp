#include <cstdio>
#include <string>

#include "app.hpp"
#include "paths.hpp"

int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::fprintf(stderr,
                     "Usage: stackSquing <material_list_file_or_directory>\n"
                     "  If a directory is given, loads the newest material_list_*.txt in it.\n");
        return 1;
    }

    try {
        const std::string resolved = resolve_material_list_path(argv[1]);
        App app(resolved);
        return app.run();
    } catch (const std::exception& ex) {
        std::fprintf(stderr, "Error: %s\n", ex.what());
        return 1;
    }
}
