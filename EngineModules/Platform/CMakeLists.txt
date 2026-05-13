add_library(sle_platform STATIC
    src/Window.cpp
    src/Input.cpp
)

target_include_directories(sle_platform
    PUBLIC
        include
)

target_link_libraries(sle_platform
    PUBLIC
        sle::core
        glm::glm
        glfw
        glad_gl_core_43
        OpenGL::GL
)

add_library(sle::platform ALIAS sle_platform)
