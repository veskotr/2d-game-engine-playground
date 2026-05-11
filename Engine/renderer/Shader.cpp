#include "Shader.hpp"
#include <glad/gl.h>
#include <fstream>
#include <sstream>
#include "core/Log.hpp"

Shader::~Shader()
{
    if (program)
        glDeleteProgram(program);
}

void Shader::bind() const
{
    glUseProgram(program);
}

std::string Shader::readFile(const std::string& path)
{
    std::ifstream file(path);
    if (!file.is_open())
    {
        Log::error("Failed to open shader file: {}", path);
        return {};
    }

    std::stringstream ss;
    ss << file.rdbuf();
    return ss.str();
}

bool Shader::loadFromFiles(const std::string& vertexPath,
                           const std::string& fragmentPath)
{
    std::string vs = readFile(vertexPath);
    std::string fs = readFile(fragmentPath);

    if (vs.empty() || fs.empty())
        return false;

    program = compileAndLink(vs, fs);
    return program != 0;
}

unsigned int Shader::compileAndLink(const std::string& vs,
                                    const std::string& fs)
{
    auto compile = [](unsigned int type, const std::string& src)
    {
        unsigned int shader = glCreateShader(type);
        const char* c = src.c_str();

        glShaderSource(shader, 1, &c, nullptr);
        glCompileShader(shader);

        int success;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &success);

        if (!success)
        {
            char log[1024];
            glGetShaderInfoLog(shader, 1024, nullptr, log);
            Log::error("Shader compile error: {}", log);
        }

        return shader;
    };

    unsigned int v = compile(GL_VERTEX_SHADER, vs);
    unsigned int f = compile(GL_FRAGMENT_SHADER, fs);

    unsigned int program = glCreateProgram();
    glAttachShader(program, v);
    glAttachShader(program, f);
    glLinkProgram(program);

    int success;
    glGetProgramiv(program, GL_LINK_STATUS, &success);

    if (!success)
    {
        char log[1024];
        glGetProgramInfoLog(program, 1024, nullptr, log);
        Log::error("Program link error: {}", log);
    }

    glDeleteShader(v);
    glDeleteShader(f);

    return program;
}

// uniforms
int Shader::getUniformLocationCached(const std::string& name) const
{
    // check cache first
    if (auto it = uniformCache.find(name); it != uniformCache.end())
        return it->second;

    // query OpenGL once
    int location = glGetUniformLocation(program, name.c_str());

    // optional debug safety
    if (location == -1)
    {
        // you can log missing uniforms if you want
        Log::warn("Uniform not found: {}", name);
    }

    uniformCache[name] = location;
    return location;
}

void Shader::setMat4(const std::string& name, const glm::mat4& value) const
{
    glUniformMatrix4fv(getUniformLocationCached(name), 1, GL_FALSE, &value[0][0]);
}

void Shader::setVec2(const std::string& name, const glm::vec2& value) const
{
    glUniform2f(getUniformLocationCached(name), value.x, value.y);
}

void Shader::setVec4(const std::string& name, const glm::vec4& value) const
{
    glUniform4f(getUniformLocationCached(name), value.x, value.y, value.z, value.w);
}

void Shader::setFloat(const std::string& name, float value) const
{
    glUniform1f(getUniformLocationCached(name), value);
}