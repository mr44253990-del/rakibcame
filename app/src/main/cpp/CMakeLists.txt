cmake_minimum_required(VERSION 3.22.1)
project(uinput_bridge)

add_library(
    uinput_bridge
    SHARED
    uinput_bridge.cpp
)

find_library(log-lib log)

target_link_libraries(
    uinput_bridge
    ${log-lib}
)
