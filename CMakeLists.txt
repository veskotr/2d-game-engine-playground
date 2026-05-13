cmake_minimum_required(VERSION 3.20)
project(GameEngine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Export compile commands (useful for VSCode IntelliSense)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# -------------------------
# Dependencies
# -------------------------
include(cmake/Dependencies.cmake)

# -------------------------
# Layered engine modules
# -------------------------
add_subdirectory(EngineModules)

# -------------------------
# Sandbox executable
# -------------------------
add_subdirectory(Sandbox)