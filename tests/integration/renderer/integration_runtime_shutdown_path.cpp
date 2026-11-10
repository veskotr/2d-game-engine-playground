#include <glad/gl.h>
#include <GLFW/glfw3.h>

#include <sle/engine/Runtime.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/ui/UIResources.hpp>

#include <array>
#include <iostream>
#include <memory>

namespace {
PFNGLDELETEPROGRAMPROC originalDeleteProgram = nullptr;
PFNGLDELETETEXTURESPROC originalDeleteTextures = nullptr;
PFNGLGETERRORPROC originalGetError = nullptr;
std::array<GLuint, 2> runtimePrograms{};
GLuint glyphTexture = 0;
std::array<unsigned int, 2> programDeleteCounts{};
bool glyphDeleted = false;
bool deletionWithoutContext = false;

void GLAD_API_PTR observeDeleteProgram(GLuint program)
{
    const bool hasContext = glfwGetCurrentContext() != nullptr;
    deletionWithoutContext |= !hasContext;
    for (std::size_t i = 0; i < runtimePrograms.size(); ++i)
        if (runtimePrograms[i] == program)
            ++programDeleteCounts[i];
    // Avoid invoking a driver entry point without a context on the failing path.
    if (hasContext)
        originalDeleteProgram(program);
}

void GLAD_API_PTR observeDeleteTextures(GLsizei count, const GLuint* textures)
{
    const bool hasContext = glfwGetCurrentContext() != nullptr;
    deletionWithoutContext |= !hasContext;
    for (GLsizei i = 0; i < count; ++i)
        glyphDeleted |= textures[i] == glyphTexture;
    if (hasContext)
        originalDeleteTextures(count, textures);
}

GLenum GLAD_API_PTR observeGetError()
{
    return glfwGetCurrentContext() ? originalGetError() : GL_NO_ERROR;
}

struct DeletionObserver
{
    DeletionObserver()
    {
        originalDeleteProgram = glad_glDeleteProgram;
        originalDeleteTextures = glad_glDeleteTextures;
        originalGetError = glad_glGetError;
        glad_glDeleteProgram = observeDeleteProgram;
        glad_glDeleteTextures = observeDeleteTextures;
        glad_glGetError = observeGetError;
    }
    ~DeletionObserver()
    {
        glad_glDeleteProgram = originalDeleteProgram;
        glad_glDeleteTextures = originalDeleteTextures;
        glad_glGetError = originalGetError;
    }
};
}

int main()
{
    sle::core::EngineConfig config;
    config.width = 320;
    config.height = 180;
    config.title = "Runtime GPU shutdown regression";
    config.vsync = false;
    auto runtime = std::make_unique<sle::Runtime>(config);
    const auto init = runtime->init();
    if (!init.ok())
    {
        if (init.error().find("Failed to create GLFW window") != std::string::npos)
        {
            std::cout << "SKIP: runtime shutdown requires a graphics environment\n";
            return 125;
        }
        std::cerr << init.error() << '\n';
        return 1;
    }

    // Borrow runtime resources only within this scope: no test-owned reference
    // may defer their destruction past Runtime's own release of its resources.
    {
        const auto quad = sle::core::Resources::get<sle::renderer::Shader>("default_quad_shader");
        const auto debug = sle::core::Resources::get<sle::renderer::Shader>("default_debug_shader");
        const auto font = sle::core::Resources::get<sle::ui::UIFontResource>("assets/fonts/Roboto-Regular.ttf");
        if (!quad || !debug || !font)
        {
            std::cerr << "Runtime default shaders and HUD font must load\n";
            return 1;
        }
        runtimePrograms = {quad->getID(), debug->getID()};
        sle::ui::UIFontGlyphInfo glyph;
        if (!font->getGlyphInfo('X', glyph) || !glyph.texture)
        {
            std::cerr << "HUD font must create a glyph texture\n";
            return 1;
        }
        glyphTexture = glyph.texture->getID();
    }

    DeletionObserver observer;
    runtime.reset();
    if (programDeleteCounts[0] != 1 || programDeleteCounts[1] != 1 || !glyphDeleted || deletionWithoutContext)
    {
        std::cerr << "Runtime must delete each default program exactly once and HUD glyph textures before destroying its GL context"
                  << " (quad=" << programDeleteCounts[0] << ", debug=" << programDeleteCounts[1]
                  << ", glyph=" << glyphDeleted << ", missing context=" << deletionWithoutContext << ")\n";
        return 1;
    }
    return 0;
}
