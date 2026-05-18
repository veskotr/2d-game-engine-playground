#pragma once
#include <memory>
#include <sle/core/Result.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <sle/renderer/RendererCommand.hpp>
#include <sle/renderer/Texture.hpp>
#include <array>
#include <unordered_map>
#include <vector>

namespace sle::renderer
{

    struct QuadInstance
    {
        glm::mat4 model;
        glm::vec4 color;
        glm::vec4 uvRect;
    };

    class Renderer
    {
    public:
        sle::core::Result<bool> init();
        void shutdown();

        void submit(const QuadCommand &cmd);
        void submit(const LineCommand &cmd);
        void submit(const PointCommand &cmd);
        void submit(const TextCommand &cmd);

        void beginFrame();
        void endFrame();

        void setCamera(const sle::core::Camera2D *cam);
        void setDebugShaderID(uint32_t shaderID) { debugProgram = shaderID; }

    private:
        using OrderedBatchEntry = std::pair<BatchKey, std::vector<QuadCommand>*>;

        const sle::core::Camera2D *camera = nullptr;
        uint32_t VAO = 0;
        uint32_t quadVBO = 0;     // static quad vertices
        uint32_t quadIBO = 0;     // static indices
        std::array<uint32_t, 2> instanceVBOs{0, 0}; // dynamic ping-pong buffers
        std::size_t instanceBufferCapacity = 0;
        std::size_t activeInstanceVBOIndex = 0;
        std::unordered_map<BatchKey, std::vector<QuadCommand>, BatchKeyHash> batches;
        std::vector<OrderedBatchEntry> orderedBatches;
        std::vector<QuadInstance> instanceStaging;

        uint32_t debugProgram = 0;
        uint32_t debugVAO = 0;
        uint32_t debugVBO = 0;
        std::vector<LineCommand> lineCommands;
        std::vector<PointCommand> pointCommands;

        void createQuad();
        void createDebugPipeline();
        void drawQuad(const QuadCommand &c);
        void drawBatch(const BatchKey &key, const std::vector<QuadCommand> &cmds);
        void drawDebugPrimitives();
        void bindShader(uint32_t shader_id);
        void bindTexture(uint32_t texture_id);
    };

} // namespace sle::renderer
