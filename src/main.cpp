#include <cstdio>
#include <string>

#include "app.hpp"

int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::fprintf(stderr, "Usage: stackSquing <material_list_file>\n");
        return 1;
    }

    App app(argv[1]);
    return app.run();
}
