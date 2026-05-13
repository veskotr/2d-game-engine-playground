add_library(sle_core STATIC
    src/Timer.cpp
)

target_include_directories(sle_core
    PUBLIC
        include
)

target_link_libraries(sle_core
    PUBLIC
        fmt::fmt
        nlohmann_json::nlohmann_json
)

add_library(sle::core ALIAS sle_core)
