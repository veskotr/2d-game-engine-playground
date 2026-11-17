add_library(sle_core STATIC
    src/Timer.cpp
)

# sle/core/Version.hpp is generated from the top-level project(VERSION).
configure_file(include/sle/core/Version.hpp.in
    "${CMAKE_CURRENT_BINARY_DIR}/generated/include/sle/core/Version.hpp" @ONLY)

target_include_directories(sle_core
    PUBLIC
        include
        "${CMAKE_CURRENT_BINARY_DIR}/generated/include"
)

target_link_libraries(sle_core
    PUBLIC
        fmt::fmt
        nlohmann_json::nlohmann_json
)

add_library(sle::core ALIAS sle_core)
