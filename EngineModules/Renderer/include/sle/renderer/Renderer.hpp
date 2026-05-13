#pragma once
#include <sle/renderer/RendererCommand.hpp>
#include <sle/renderer/Shader.hpp>
#include <sle/renderer/Texture.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <sle/core/Result.hpp>
#include <memory>
#include <vector>
#include <BatchKey.hpp>

namespace sle::renderer {

class Renderer
{
public:
    sle::core::Result<bool> init();
    void shutdown();

    void submit(const QuadCommand &cmd);
    void submit(const LineCommand &cmd);
    void submit(const TextCommand &cmd);

    void beginFrame();
    void endFrame();

    void setCamera(const sle::core::Camera2D *cam);
    void setShader(std::shared_ptr<Shader> shader);

private:
    const sle::core::Camera2D *camera = nullptr;
    uint32_t VAO = 0;
    uint32_t VBO = 0;
    std::shared_ptr<Shader> defaultShader;
    std::shared_ptr<Texture> defaultWhiteTexture;
    std::unordered_map<BatchKey, std::vector<QuadCommand>, BatchKeyHash> batches;

    void createQuad();
    void createDefaultTexture();
    uint32_t compileShader(const char *vs, const char *fs);
    void drawQuad(const QuadCommand &c);
    void bindDefaultTexture(int slot);
};

} // namespace sle::renderer
