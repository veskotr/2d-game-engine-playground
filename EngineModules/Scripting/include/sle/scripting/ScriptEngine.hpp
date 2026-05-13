#pragma once

namespace sle::scripting {

class ScriptEngine
{
public:
    bool init();
    void shutdown();
    void update(float dt);
};

} // namespace sle::scripting
