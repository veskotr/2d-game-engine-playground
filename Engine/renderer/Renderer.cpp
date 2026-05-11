#include "Renderer.hpp"
#include <glad/gl.h>
#include "core/GLDebug.hpp"
#include "core/Log.hpp"

static bool checkShader(uint32_t shader);

Result<bool> Renderer::init()
{
    createQuad();

    const char *vs = R"(
    #version 460 core
    layout(location = 0) in vec2 aPos;

    uniform mat4 uVP;
    uniform vec2 uPos;
    uniform vec2 uSize;

    void main()
    {
        vec2 world = aPos * uSize + uPos;
        gl_Position = uVP * vec4(world, 0.0, 1.0);
    }
)";

    const char *fs = R"(
        #version 460 core
        out vec4 FragColor;

        void main()
        {
            FragColor = vec4(0.2, 0.8, 1.0, 1.0);
        }
    )";

    shader = compileShader(vs, fs);
    if (!checkShader(shader))
    {
        return Result<bool>::error("Failed to compile shader");
    }

    return Result<bool>::success(true);
}

void Renderer::submit(const QuadCommand& cmd)
{
    queue.emplace_back(cmd);
}

void Renderer::submit(const LineCommand& cmd)
{
    queue.emplace_back(cmd);
}

void Renderer::submit(const TextCommand& cmd)
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
    for (auto& cmd : queue)
    {
        std::visit([this](auto&& c)
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
            }
        }, cmd);
    }

    queue.clear();
}

void Renderer::drawQuad(const QuadCommand& c)
{
    GL_CALL(glUseProgram(shader));

    if (camera)
    {
        glm::mat4 vp = camera->getViewProjection();
        GL_CALL(glUniformMatrix4fv(
            glGetUniformLocation(shader, "uVP"),
            1,
            GL_FALSE,
            &vp[0][0]
        ));
    }

    GL_CALL(glUniform2f(glGetUniformLocation(shader, "uPos"), c.position.x, c.position.y));
    GL_CALL(glUniform2f(glGetUniformLocation(shader, "uSize"), c.size.x, c.size.y));

    GL_CALL(glBindVertexArray(VAO));
    GL_CALL(glDrawArrays(GL_TRIANGLES, 0, 6));
}

void Renderer::createQuad()
{
    float vertices[] = {
        // positions
        -1.0f, -1.0f,
        1.0f, -1.0f,
        1.0f, 1.0f,

        1.0f, 1.0f,
        -1.0f, 1.0f,
        -1.0f, -1.0f};

    GL_CALL(glGenVertexArrays(1, &VAO));
    GL_CALL(glGenBuffers(1, &VBO));

    GL_CALL(glBindVertexArray(VAO));

    GL_CALL(glBindBuffer(GL_ARRAY_BUFFER, VBO));
    GL_CALL(glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW));

    GL_CALL(glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), (void *)0));
    GL_CALL(glEnableVertexAttribArray(0));
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
    }

    glDeleteShader(vertex);
    glDeleteShader(fragment);

    return program;
}

void Renderer::setCamera(const Camera2D* cam)
{
    camera = cam;
}

static bool checkShader(uint32_t shader)
{
    int success;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);

    if (!success)
    {
        char log[1024];
        glGetShaderInfoLog(shader, 1024, nullptr, log);
        Log::error("Shader compile error: {}", log);
        return false;
    }

    return true;
}