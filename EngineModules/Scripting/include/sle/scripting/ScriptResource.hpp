#pragma once

#include <fstream>
#include <sstream>
#include <string>

namespace sle::scripting {

class ScriptResource
{
public:
    bool loadFromFiles(const std::string& scriptPath)
    {
        std::ifstream file(scriptPath);
        if (!file.is_open())
            return false;

        std::stringstream buffer;
        buffer << file.rdbuf();

        path = scriptPath;
        source = buffer.str();
        return true;
    }

    const std::string& getPath() const { return path; }
    const std::string& getSource() const { return source; }

private:
    std::string path;
    std::string source;
};

} // namespace sle::scripting
