add_library(sle_resources STATIC
    src/Resources.cpp
)

target_include_directories(sle_resources
    PUBLIC
        include
)

target_link_libraries(sle_resources
    PUBLIC
        sle::core
        sle::renderer
        miniaudio
)

add_library(sle::resources ALIAS sle_resources)
