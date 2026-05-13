add_library(sle_renderer STATIC
    src/Camera2D.cpp
    src/Renderer.cpp
    src/Shader.cpp
    src/Texture.cpp
    src/RenderSystem.cpp
)

target_include_directories(sle_renderer
    PUBLIC
        include
)

target_link_libraries(sle_renderer
    PUBLIC
        sle::core
        sle::platform
        glm::glm
        glad_gl_core_43
        OpenGL::GL
        stb_image
)

add_library(sle::renderer ALIAS sle_renderer)
