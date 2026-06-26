cmake_minimum_required(VERSION 3.16)
project(stackSquing VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(Curses REQUIRED)

add_executable(stackSquing
    src/main.cpp
    src/parser.cpp
    src/format.cpp
    src/groups.cpp
    src/display_order.cpp
    src/app.cpp
    src/ui.cpp
)

target_include_directories(stackSquing PRIVATE
    ${CMAKE_SOURCE_DIR}/src
    ${CMAKE_SOURCE_DIR}/third_party
    ${CURSES_INCLUDE_DIR}
)

target_link_libraries(stackSquing PRIVATE ${CURSES_LIBRARIES})

install(TARGETS stackSquing DESTINATION bin)
