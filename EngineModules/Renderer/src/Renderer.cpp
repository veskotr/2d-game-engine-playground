#include <algorithm>
#include <cstddef>
#include <glad/gl.h>
#include <sle/core/Log.hpp>
#include <sle/renderer/GLDebug.hpp>
#include <sle/renderer/Renderer.hpp>

namespace sle::renderer
{

    namespace {
        constexpr std::size_t kInitialInstanceCapacity = 10000;

        struct DebugVertex
        {
            glm::vec3 pos;
            glm::vec4 color;
        };

        std::size_t nextPow2(std::size_t value)
        {
            std::size_t p = 1;
            while (p < value)
                p <<= 1;
            return p;
        }
    }

    static const float vertices[] = {
        // pos      // uv
        -0.5f, -0.5f, 0.0f, 0.0f, // bottom left
        0.5f, -0.5f, 1.0f, 0.0f,  // bottom right
        0.5f, 0.5f, 1.0f, 1.0f,   // top right
        -0.5f, 0.5f, 0.0f, 1.0f   // top left
    };

    using sle::core::Camera2D;
    using sle::core::Log;
    using sle::core::Result;

    Result<bool> Renderer::init()
    {
        createQuad();
        createDebugPipeline();

        GL_CALL(glEnable(GL_BLEND));
        GL_CALL(glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA));

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

        for (uint32_t& instanceVBO : instanceVBOs)
        {
            if (instanceVBO == 0)
                continue;

            GL_CALL(glDeleteBuffers(1, &instanceVBO));
            instanceVBO = 0;
        }

        instanceBufferCapacity = 0;

        if (VAO != 0)
        {
            GL_CALL(glDeleteVertexArrays(1, &VAO));
            VAO = 0;
        }

        if (debugVBO != 0)
        {
            GL_CALL(glDeleteBuffers(1, &debugVBO));
            debugVBO = 0;
        }

        if (debugVAO != 0)
        {
            GL_CALL(glDeleteVertexArrays(1, &debugVAO));
            debugVAO = 0;
        }

        if (debugProgram != 0)
        {
            GL_CALL(glDeleteProgram(debugProgram));
            debugProgram = 0;
        }

        batches.clear();
        lineCommands.clear();
        pointCommands.clear();
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
        lineCommands.push_back(cmd);
    }

    void Renderer::submit(const PointCommand &cmd)
    {
        pointCommands.push_back(cmd);
    }

    void Renderer::submit(const TextCommand &cmd)
    {
        (void)cmd;
    }

    void Renderer::beginFrame()
    {
        activeInstanceVBOIndex = (activeInstanceVBOIndex + 1) % instanceVBOs.size();

        GL_CALL(glClearColor(0.0f, 0.0f, 0.0f, 1.0f));
        GL_CALL(glClear(GL_COLOR_BUFFER_BIT));
    }

    void Renderer::endFrame()
    {
        orderedBatches.clear();
        orderedBatches.reserve(batches.size());

        for (auto &[key, vec] : batches)
        {
            if (!vec.empty())
                orderedBatches.push_back({key, &vec});
        }

        std::sort(orderedBatches.begin(), orderedBatches.end(),
                  [](auto &a, auto &b)
                  {
                      if (a.first.layer != b.first.layer)
                          return a.first.layer < b.first.layer;

                      if (a.first.shader_id != b.first.shader_id)
                          return a.first.shader_id < b.first.shader_id;

                      return a.first.texture_id < b.first.texture_id;
                  });

        for (auto &[key, cmds] : orderedBatches)
        {
            drawBatch(key, *cmds);
        }

        drawDebugPrimitives();

        for (auto& [_, vec] : batches)
            vec.clear();

        lineCommands.clear();
        pointCommands.clear();
    }

    void Renderer::drawBatch(const BatchKey &key,
                             const std::vector<QuadCommand> &cmds)
    {
        if (cmds.empty())
            return;

        bindShader(key.shader_id);
        bindTexture(key.texture_id);

        instanceStaging.resize(cmds.size());

        for (std::size_t i = 0; i < cmds.size(); ++i)
        {
            const auto& c = cmds[i];
            QuadInstance& inst = instanceStaging[i];
            inst.model = c.modelMatrix;
            inst.color = c.color;
            inst.uvRect = c.uvRect;
        }

        const std::size_t total = instanceStaging.size();
        if (total == 0)
            return;

        if (total > instanceBufferCapacity)
        {
            instanceBufferCapacity = nextPow2(total);
            for (uint32_t instanceVBO : instanceVBOs)
            {
                glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
                glBufferData(
                    GL_ARRAY_BUFFER,
                    static_cast<GLsizeiptr>(instanceBufferCapacity * sizeof(QuadInstance)),
                    nullptr,
                    GL_STREAM_DRAW);
            }
        }

        const uint32_t activeInstanceVBO = instanceVBOs[activeInstanceVBOIndex];
        glBindBuffer(GL_ARRAY_BUFFER, activeInstanceVBO);

        // Orphan old contents so the driver can allocate fresh storage and avoid sync stalls.
        glBufferData(
            GL_ARRAY_BUFFER,
            static_cast<GLsizeiptr>(instanceBufferCapacity * sizeof(QuadInstance)),
            nullptr,
            GL_STREAM_DRAW);

        glBufferSubData(
            GL_ARRAY_BUFFER,
            0,
            static_cast<GLsizeiptr>(total * sizeof(QuadInstance)),
            instanceStaging.data());

        glBindVertexArray(VAO);

        const std::size_t vec4Size = sizeof(glm::vec4);
        for (int i = 0; i < 4; i++)
        {
            glVertexAttribPointer(2 + i, 4, GL_FLOAT, GL_FALSE,
                                  sizeof(QuadInstance), (void *)(i * vec4Size));
        }
        glVertexAttribPointer(6, 4, GL_FLOAT, GL_FALSE,
                              sizeof(QuadInstance), (void *)(sizeof(glm::mat4)));
        glVertexAttribPointer(7, 4, GL_FLOAT, GL_FALSE,
                              sizeof(QuadInstance),
                              (void *)(sizeof(glm::mat4) + sizeof(glm::vec4)));
        glDrawElementsInstanced(
            GL_TRIANGLES,
            6,
            GL_UNSIGNED_INT,
            nullptr,
            static_cast<GLsizei>(total));
    }

    void Renderer::bindShader(uint32_t shader_id)
    {
        GL_CALL(glUseProgram(shader_id));

        if (shader_id == 0)
            return;

        const glm::mat4 vp = camera ? camera->getViewProjection() : glm::mat4(1.0f);

        const GLint vpLoc = glGetUniformLocation(shader_id, "uVP");
        if (vpLoc != -1)
        {
            GL_CALL(glUniformMatrix4fv(vpLoc, 1, GL_FALSE, &vp[0][0]));
        }

        const GLint texLoc = glGetUniformLocation(shader_id, "uTexture");
        if (texLoc != -1)
        {
            GL_CALL(glUniform1i(texLoc, 0));
        }
    }

    void Renderer::bindTexture(uint32_t texture_id)
    {
        GL_CALL(glActiveTexture(GL_TEXTURE0));
        GL_CALL(glBindTexture(GL_TEXTURE_2D, texture_id));
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

        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void *)0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void *)(2 * sizeof(float)));
        glEnableVertexAttribArray(1);

        // index buffer
        glGenBuffers(1, &quadIBO);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, quadIBO);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(indices), indices, GL_STATIC_DRAW);

        // instance buffers (double-buffered)
        glGenBuffers(static_cast<GLsizei>(instanceVBOs.size()), instanceVBOs.data());
        instanceBufferCapacity = kInitialInstanceCapacity;
        activeInstanceVBOIndex = 0;

        for (uint32_t instanceVBO : instanceVBOs)
        {
            glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
            glBufferData(
                GL_ARRAY_BUFFER,
                static_cast<GLsizeiptr>(instanceBufferCapacity * sizeof(QuadInstance)),
                nullptr,
                GL_STREAM_DRAW);
        }

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

    void Renderer::createDebugPipeline()
    {
        // VAO/VBO only — shader program is supplied externally via setDebugShaderID().
        glGenVertexArrays(1, &debugVAO);
        glGenBuffers(1, &debugVBO);

        glBindVertexArray(debugVAO);
        glBindBuffer(GL_ARRAY_BUFFER, debugVBO);
        glBufferData(GL_ARRAY_BUFFER, sizeof(DebugVertex) * 4096, nullptr, GL_STREAM_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, sizeof(DebugVertex), (void*)0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, sizeof(DebugVertex), (void*)offsetof(DebugVertex, color));
        glEnableVertexAttribArray(1);
    }

    void Renderer::drawDebugPrimitives()
    {
        if (debugProgram == 0 || debugVAO == 0 || debugVBO == 0)
            return;

        if (lineCommands.empty() && pointCommands.empty())
            return;

        const glm::mat4 vp = camera ? camera->getViewProjection() : glm::mat4(1.0f);

        glUseProgram(debugProgram);
        const GLint vpLoc = glGetUniformLocation(debugProgram, "uVP");
        if (vpLoc != -1)
            glUniformMatrix4fv(vpLoc, 1, GL_FALSE, &vp[0][0]);

        glBindVertexArray(debugVAO);
        glBindBuffer(GL_ARRAY_BUFFER, debugVBO);

        std::sort(
            lineCommands.begin(),
            lineCommands.end(),
            [](const LineCommand& a, const LineCommand& b)
            {
                return a.layer < b.layer;
            });

        for (const LineCommand& line : lineCommands)
        {
            const float width = std::max(1.0f, line.thickness);
            glLineWidth(width);

            const DebugVertex vertices[2] = {
                {{line.a.x, line.a.y, line.z}, line.color},
                {{line.b.x, line.b.y, line.z}, line.color}
            };

            glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STREAM_DRAW);
            glDrawArrays(GL_LINES, 0, 2);
        }

        std::sort(
            pointCommands.begin(),
            pointCommands.end(),
            [](const PointCommand& a, const PointCommand& b)
            {
                return a.layer < b.layer;
            });

        for (const PointCommand& point : pointCommands)
        {
            const float size = std::max(1.0f, point.size);
            glPointSize(size);

            const DebugVertex vertex = {{point.position.x, point.position.y, point.z}, point.color};
            glBufferData(GL_ARRAY_BUFFER, sizeof(vertex), &vertex, GL_STREAM_DRAW);
            glDrawArrays(GL_POINTS, 0, 1);
        }

        glLineWidth(1.0f);
        glPointSize(1.0f);
    }

    void Renderer::setCamera(const Camera2D *cam)
    {
        camera = cam;
    }

} // namespace sle::renderer
