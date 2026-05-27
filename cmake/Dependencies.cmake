include(FetchContent)

# =========================
# GLM
# =========================
FetchContent_Declare(
    glm
    GIT_REPOSITORY https://github.com/g-truc/glm.git
    GIT_TAG 1.0.1
)
FetchContent_MakeAvailable(glm)

# =========================
# fmt
# =========================
FetchContent_Declare(
    fmt
    GIT_REPOSITORY https://github.com/fmtlib/fmt.git
    GIT_TAG 10.2.1
)
FetchContent_MakeAvailable(fmt)

# =========================
# JSON
# =========================
FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG v3.11.3
)
FetchContent_MakeAvailable(nlohmann_json)

# =========================
# GLFW
# =========================
set(GLFW_BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    glfw
    GIT_REPOSITORY https://github.com/glfw/glfw.git
    GIT_TAG 3.4
)
FetchContent_MakeAvailable(glfw)

# =========================
# Box2D
# =========================
set(BOX2D_BUILD_TESTBED OFF CACHE BOOL "" FORCE)
set(BOX2D_BUILD_UNIT_TESTS OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    box2d
    GIT_REPOSITORY https://github.com/erincatto/box2d.git
    GIT_TAG v2.4.1
)
FetchContent_MakeAvailable(box2d)

# =========================
# stb (header-only)
# =========================
FetchContent_Declare(
    stb
    GIT_REPOSITORY https://github.com/nothings/stb.git
    GIT_TAG master
)
FetchContent_Populate(stb)

add_library(stb_image INTERFACE)
target_include_directories(stb_image INTERFACE
    ${stb_SOURCE_DIR}
)

# stb_truetype is provided by the same fetched source tree.
add_library(stb_truetype INTERFACE)
target_include_directories(stb_truetype INTERFACE
    ${stb_SOURCE_DIR}
)

# =========================
# miniaudio (header-only)
# =========================
FetchContent_Declare(
    miniaudio
    GIT_REPOSITORY https://github.com/mackron/miniaudio.git
    GIT_TAG master
)
FetchContent_Populate(miniaudio)

add_library(miniaudio INTERFACE)
target_include_directories(miniaudio INTERFACE
    ${miniaudio_SOURCE_DIR}
)

# =========================
# XML parser
# =========================
FetchContent_Declare(
    pugixml
    GIT_REPOSITORY https://github.com/zeux/pugixml.git
    GIT_TAG master
)
FetchContent_MakeAvailable(pugixml)

# =========================
# OpenGL (system lib)
# =========================
find_package(OpenGL REQUIRED)

# =========================
# GLAD
# =========================

FetchContent_Declare(
    glad
    GIT_REPOSITORY https://github.com/Dav1dde/glad.git
    GIT_TAG v2.0.8
    SOURCE_SUBDIR  cmake
)
FetchContent_MakeAvailable(glad)
glad_add_library(glad_gl_core_43 STATIC REPRODUCIBLE LOADER API gl:core=4.3)

# =========================
# Lua
# =========================

include(FetchContent)

FetchContent_Declare(
    lua
    GIT_REPOSITORY "https://github.com/marovira/lua"
    GIT_TAG "5.4.8"
)

FetchContent_MakeAvailable(lua)

if(MSVC)
    foreach(lua_target IN ITEMS liblua lua)
        if(TARGET ${lua_target})
            set_property(
                TARGET ${lua_target}
                PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL"
            )
        endif()
    endforeach()
endif()
