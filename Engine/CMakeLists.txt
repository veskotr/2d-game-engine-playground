add_library(engine)

target_sources(engine
    PRIVATE
        Engine.cpp
        input/Input.cpp
        renderer/Renderer.cpp
        core/Window.cpp
        core/Camera2D.cpp
        core/Timer.cpp
        renderer/Shader.cpp
        core/Resources.cpp
)

target_include_directories(engine
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(engine PUBLIC engine_deps)