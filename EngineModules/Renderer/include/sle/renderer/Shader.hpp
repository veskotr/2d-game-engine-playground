#pragma once

#include <string>
#include <unordered_map>
#include <glm/glm.hpp>

namespace sle::renderer {

class Shader
{
public:
    Shader() = default;
    ~Shader();

    bool loadFromFiles(const std::string& vertexPath,
                       const std::string& fragmentPath);

    void bind() const;

    void setMat4(const std::string& name, const glm::mat4& value) const;
    void setVec2(const std::string& name, const glm::vec2& value) const;
    void setVec4(const std::string& name, const glm::vec4& value) const;
    void setFloat(const std::string& name, float value) const;
    void setInt(const std::string& name, int value) const;

    unsigned int getID() const { return program; }

private:
    unsigned int program = 0;

    mutable std::unordered_map<std::string, int> uniformCache;

    int getUniformLocationCached(const std::string& name) const;

    static std::string readFile(const std::string& path);
    static unsigned int compileAndLink(const std::string& vs, const std::string& fs);
};

} // namespace sle::renderer
