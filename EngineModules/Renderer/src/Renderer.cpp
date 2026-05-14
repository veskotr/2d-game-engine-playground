#include <algorithm>
#include <glad/gl.h>
#include <sle/core/Log.hpp>
#include <sle/renderer/GLDebug.hpp>
#include <sle/renderer/Renderer.hpp>

namespace sle::renderer
{

    static const float vertices[] = {
        // pos
        -0.5f, -0.5f, // bottom left
        0.5f, -0.5f,  // bottom right
        0.5f, 0.5f,   // top right
        -0.5f, 0.5f   // top left
    };

    using sle::core::Camera2D;
    using sle::core::Log;
    using sle::core::Result;

    Result<bool> Renderer::init()
    {
        createQuad();

        return Result<bool>::success(true);
    }

    void Renderer::shutdown()
    {
        if (quadVBO != 0)
        {
            GL_CALL(glDeleteBuffers(1, &quadVBO));
            quadVBO = 0;
        }

        if (quadIBO != 0)
        {
            GL_CALL(glDeleteBuffers(1, &quadIBO));
            quadIBO = 0;
        }

        if (instanceVBO != 0)
        {
            GL_CALL(glDeleteBuffers(1, &instanceVBO));
            instanceVBO = 0;
        }

        if (VAO != 0)
        {
            GL_CALL(glDeleteVertexArrays(1, &VAO));
            VAO = 0;
        }

        batches.clear();
    }

    void Renderer::submit(const QuadCommand &cmd)
    {
        BatchKey key{
            cmd.layer,
            cmd.shader_id,
            cmd.texture_id};

        batches[key].push_back(cmd);
    }

    void Renderer::submit(const LineCommand &cmd)
    {
        // queue.emplace_back(cmd);
    }

    void Renderer::submit(const TextCommand &cmd)
    {
        // queue.emplace_back(cmd);
    }

    void Renderer::beginFrame()
    {
        GL_CALL(glClearColor(0.0f, 0.0f, 0.0f, 1.0f));
        GL_CALL(glClear(GL_COLOR_BUFFER_BIT));
    }

    void Renderer::endFrame()
    {
        // optional: sort keys by layer first
        std::vector<std::pair<BatchKey, std::vector<QuadCommand> *>> ordered;

        ordered.reserve(batches.size());

        for (auto &[key, vec] : batches)
            ordered.push_back({key, &vec});

        std::sort(ordered.begin(), ordered.end(),
                  [](auto &a, auto &b)
                  {
                      return a.first.layer < b.first.layer;
                  });

        for (auto &[key, cmds] : ordered)
        {
            drawBatch(key, *cmds);
        }

        batches.clear();
    }

    void Renderer::drawBatch(const BatchKey &key,
                             const std::vector<QuadCommand> &cmds)
    {
        if (cmds.empty())
            return;

        bindShader(key.shader_id);
        bindTexture(key.texture_id);

        std::vector<QuadInstance> instances;
        instances.reserve(cmds.size());

        for (const auto &c : cmds)
        {
            QuadInstance inst;
            inst.model = c.modelMatrix;
            inst.color = c.color;
            instances.push_back(inst);
        }

        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferSubData(GL_ARRAY_BUFFER, 0,
                        instances.size() * sizeof(QuadInstance),
                        instances.data());

        glBindVertexArray(VAO);
        glDrawElementsInstanced(
            GL_TRIANGLES,
            6,
            GL_UNSIGNED_INT,
            nullptr,
            instances.size());
    }

    void Renderer::createQuad()
    {

        uint32_t indices[] = {0, 1, 2, 2, 3, 0};

        glGenVertexArrays(1, &VAO);
        glBindVertexArray(VAO);

        // static quad VBO
        glGenBuffers(1, &quadVBO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), (void *)0);
        glEnableVertexAttribArray(0);

        // index buffer
        glGenBuffers(1, &quadIBO);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, quadIBO);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(indices), indices, GL_STATIC_DRAW);

        // instance buffer
        glGenBuffers(1, &instanceVBO);

        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, 10000 * sizeof(QuadInstance), nullptr, GL_DYNAMIC_DRAW);

        std::size_t vec4Size = sizeof(glm::vec4);

        // mat4 = 4 vec4 attributes
        for (int i = 0; i < 4; i++)
        {
            glVertexAttribPointer(2 + i, 4, GL_FLOAT, GL_FALSE,
                                  sizeof(QuadInstance), (void *)(i * vec4Size));
            glEnableVertexAttribArray(2 + i);
            glVertexAttribDivisor(2 + i, 1);
        }

        // color attribute
        glVertexAttribPointer(6, 4, GL_FLOAT, GL_FALSE,
                              sizeof(QuadInstance), (void *)(sizeof(glm::mat4)));
        glEnableVertexAttribArray(6);
        glVertexAttribDivisor(6, 1);

        // uv rect attribute (vec4)
        glVertexAttribPointer(7, 4, GL_FLOAT, GL_FALSE,
                              sizeof(QuadInstance),
                              (void *)(sizeof(glm::mat4) + sizeof(glm::vec4)));
        glEnableVertexAttribArray(7);
        glVertexAttribDivisor(7, 1);
    }

    void Renderer::setCamera(const Camera2D *cam)
    {
        camera = cam;
    }

} // namespace sle::renderer
