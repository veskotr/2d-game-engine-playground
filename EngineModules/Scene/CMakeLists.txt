add_library(sle_scene STATIC
    src/Registry.cpp
    src/Scene.cpp
)

target_include_directories(sle_scene
    PUBLIC
        include
)

target_link_libraries(sle_scene
    PUBLIC
        sle::core
        sle::resources
        sle::renderer
)

add_library(sle::scene ALIAS sle_scene)
