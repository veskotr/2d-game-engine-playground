cmake_minimum_required(VERSION 3.20)
project(GameEngine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Export compile commands (useful for VSCode IntelliSense)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

add_library(engine_deps INTERFACE)

target_link_libraries(engine_deps INTERFACE
    glm::glm
    fmt::fmt
    nlohmann_json::nlohmann_json
    glfw
    box2d
    OpenGL::GL
    stb_image
    miniaudio
    glad_gl_core_43
)

# -------------------------
# Dependencies
# -------------------------
include(cmake/Dependencies.cmake)

# -------------------------
# Engine library
# -------------------------
add_subdirectory(engine)

# -------------------------
# Sandbox executable
# -------------------------
add_subdirectory(sandbox)