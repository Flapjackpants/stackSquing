cmake_minimum_required(VERSION 3.16)
project(stackSquing VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(Curses REQUIRED)

add_library(stackSquing_core STATIC
    src/parser.cpp
    src/format.cpp
    src/stack_size.cpp
    src/groups.cpp
    src/display_order.cpp
    src/paths.cpp
    src/overview.cpp
)
target_include_directories(stackSquing_core PUBLIC
    ${CMAKE_SOURCE_DIR}/src
    ${CMAKE_SOURCE_DIR}/third_party
)

add_executable(stackSquing
    src/main.cpp
    src/app.cpp
    src/ui.cpp
)
target_include_directories(stackSquing PRIVATE ${CURSES_INCLUDE_DIR})
target_link_libraries(stackSquing PRIVATE stackSquing_core ${CURSES_LIBRARIES})

enable_testing()
add_executable(stackSquing_tests tests/test_main.cpp)
target_link_libraries(stackSquing_tests PRIVATE stackSquing_core)
add_test(NAME stackSquing_tests COMMAND stackSquing_tests)

install(TARGETS stackSquing DESTINATION bin)
