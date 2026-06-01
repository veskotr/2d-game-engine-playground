add_library(sle_engine STATIC
    src/ScriptApiImpl.cpp
    src/SceneManager.cpp
    src/SceneLoader.cpp
    src/Runtime.cpp
    src/AnimationSystem.cpp
    src/TransformSystem.cpp
    src/ScriptSystem.cpp
    src/StateMachineSystem.cpp
    src/PhysicsSystem.cpp
    src/RenderSystem.cpp
    src/AudioSystem.cpp
    ${stb_SOURCE_DIR}/stb_vorbis.c   # compiled as C for OGG/Vorbis support
)

target_include_directories(sle_engine
    PUBLIC
        include
    PRIVATE
        # ogg_decode.c uses stb_vorbis from the project stb dependency.
        ${stb_SOURCE_DIR}
        src
)

target_link_libraries(sle_engine
    PUBLIC
        sle::core
        sle::platform
        sle::renderer
        sle::resources
        sle::scene
        sle::events
        sle::physics
        sle::scripting
        sle::ui
        glm::glm
    miniaudio
)

add_library(sle::engine ALIAS sle_engine)
