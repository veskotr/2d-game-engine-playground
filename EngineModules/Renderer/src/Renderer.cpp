#include <glad/gl.h>
#include <sle/core/Log.hpp>
#include <sle/renderer/GLDebug.hpp>
#include <sle/renderer/Renderer.hpp>

namespace sle::renderer
{

    using sle::core::Camera2D;
    using sle::core::Log;
    using sle::core::Result;

    Result<bool> Renderer::init()
    {
        createQuad();
        createDefaultTexture();

        return Result<bool>::success(true);
    }

    void Renderer::shutdown()
    {
        shader.reset();

        if (VBO != 0)
        {
            GL_CALL(glDeleteBuffers(1, &VBO));
            VBO = 0;
        }

        if (VAO != 0)
        {
            GL_CALL(glDeleteVertexArrays(1, &VAO));
            VAO = 0;
        }

        queue.clear();
    }

    void Renderer::setShader(std::shared_ptr<Shader> s)
    {
        shader = s;
    }

    void Renderer::submit(const QuadCommand &cmd)
    {
        queue.emplace_back(cmd);
    }

    void Renderer::submit(const LineCommand &cmd)
    {
        queue.emplace_back(cmd);
    }

    void Renderer::submit(const TextCommand &cmd)
    {
        queue.emplace_back(cmd);
    }

    void Renderer::beginFrame()
    {
        GL_CALL(glClearColor(0.1f, 0.1f, 0.15f, 1.0f));
        GL_CALL(glClear(GL_COLOR_BUFFER_BIT));
    }

    void Renderer::endFrame()
    {
        for (auto &cmd : queue)
        {
            std::visit([this](auto &&c)
                       {
            using T = std::decay_t<decltype(c)>;

            if constexpr (std::is_same_v<T, QuadCommand>)
            {
                drawQuad(c);
            }
            else if constexpr (std::is_same_v<T, LineCommand>)
            {
                // drawLine(c);
            }
            else if constexpr (std::is_same_v<T, TextCommand>)
            {
                // drawText(c);
            } }, cmd);
        }

        queue.clear();
    }

    void Renderer::drawQuad(const QuadCommand &c)
    {
        if (!shader)
            return;

        shader->bind();

        if (camera)
            shader->setMat4("uVP", camera->getViewProjection());

        shader->setVec2("uPos", c.position);
        shader->setVec2("uSize", c.size);
        shader->setVec4("uColor", c.color);

        int slot = 0;
        GL_CALL(glActiveTexture(GL_TEXTURE0 + slot));

        uint32_t tex = (c.texture != 0)
                           ? c.texture
                           : defaultWhiteTexture->getID();

        GL_CALL(glBindTexture(GL_TEXTURE_2D, tex));
        shader->setInt("uTexture", slot);

        GL_CALL(glBindVertexArray(VAO));
        GL_CALL(glDrawArrays(GL_TRIANGLES, 0, 6));
    }

    void Renderer::createQuad()
    {
        float vertices[] = {
            // positions        // UVs
            -1.0f, -1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f,

            1.0f, 1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f, 0.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f};

        GL_CALL(glGenVertexArrays(1, &VAO));
        GL_CALL(glGenBuffers(1, &VBO));

        GL_CALL(glBindVertexArray(VAO));

        GL_CALL(glBindBuffer(GL_ARRAY_BUFFER, VBO));
        GL_CALL(glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW));

        // Position attribute
        GL_CALL(glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void *)0));
        GL_CALL(glEnableVertexAttribArray(0));

        // UV attribute
        GL_CALL(glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void *)(2 * sizeof(float))));
        GL_CALL(glEnableVertexAttribArray(1));
    }

    void Renderer::createDefaultTexture()
    {
        defaultWhiteTexture = std::make_shared<Texture>();

        uint32_t textureID;
        GL_CALL(glGenTextures(1, &textureID));
        GL_CALL(glBindTexture(GL_TEXTURE_2D, textureID));

        uint8_t white[] = {255, 255, 255, 255};

        GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT));
        GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT));
        GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
        GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));

        GL_CALL(glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, white));

        defaultWhiteTexture->setID(textureID);
    }

    uint32_t Renderer::compileShader(const char *vs, const char *fs)
    {
        uint32_t vertex = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertex, 1, &vs, nullptr);
        glCompileShader(vertex);

        int success;
        glGetShaderiv(vertex, GL_COMPILE_STATUS, &success);
        if (!success)
        {
            char log[1024];
            glGetShaderInfoLog(vertex, 1024, nullptr, log);
            Log::error("Vertex shader error: {}", log);
            glDeleteShader(vertex);
            return 0;
        }

        uint32_t fragment = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragment, 1, &fs, nullptr);
        glCompileShader(fragment);

        glGetShaderiv(fragment, GL_COMPILE_STATUS, &success);
        if (!success)
        {
            char log[1024];
            glGetShaderInfoLog(fragment, 1024, nullptr, log);
            Log::error("Fragment shader error: {}", log);
            glDeleteShader(vertex);
            glDeleteShader(fragment);
            return 0;
        }

        uint32_t program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);

        glGetProgramiv(program, GL_LINK_STATUS, &success);
        if (!success)
        {
            char log[1024];
            glGetProgramInfoLog(program, 1024, nullptr, log);
            Log::error("Program link error: {}", log);
            glDeleteProgram(program);
            glDeleteShader(vertex);
            glDeleteShader(fragment);
            return 0;
        }

        glDeleteShader(vertex);
        glDeleteShader(fragment);

        return program;
    }

    void Renderer::setCamera(const Camera2D *cam)
    {
        camera = cam;
    }

} // namespace sle::renderer
