#pragma once
#include <memory>
#include <sle/core/Result.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <sle/renderer/RendererCommand.hpp>
#include <sle/renderer/Shader.hpp>
#include <sle/renderer/Texture.hpp>
#include <vector>

namespace sle::renderer
{

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

    private:
        const sle::core::Camera2D *camera = nullptr;
        uint32_t VAO = 0;
        uint32_t quadVBO = 0;     // static quad vertices
        uint32_t quadIBO = 0;     // static indices
        uint32_t instanceVBO = 0; // dynamic per frame
        std::unordered_map<BatchKey, std::vector<QuadCommand>, BatchKeyHash> batches;

        void createQuad();
        void drawQuad(const QuadCommand &c);
        void drawBatch(const BatchKey &key, const std::vector<QuadCommand> &cmds);
        void bindShader(uint32_t shader_id);
        void bindTexture(uint32_t texture_id);
    };

    struct QuadInstance
    {
        glm::mat4 model;
        glm::vec4 color;
        glm::vec4 uvRect;
    };

} // namespace sle::renderer
