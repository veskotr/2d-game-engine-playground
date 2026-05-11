#pragma once
#include "RendererCommand.hpp"
#include "Shader.hpp"
#include "core/Camera2D.hpp"
#include "core/Result.hpp"
#include <vector>

class Renderer
{
public:
    Result<bool> init();
    void shutdown();

    void submit(const QuadCommand &cmd);
    void submit(const LineCommand &cmd);
    void submit(const TextCommand &cmd);

    void beginFrame();
    void endFrame();

    void setCamera(const Camera2D *cam);
    void setShader(Shader *shader);

private:
    const Camera2D *camera = nullptr;
    uint32_t VAO = 0;
    uint32_t VBO = 0;
    Shader *shader = nullptr;
    std::vector<RenderCommand> queue;

    void createQuad();
    uint32_t compileShader(const char *vs, const char *fs);
    void drawQuad(const QuadCommand &c);
};