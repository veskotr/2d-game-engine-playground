#pragma once

#include <cstdint>
#include <string>
#include <unordered_set>

namespace sle::scripting {

class ScriptRuntime
{
public:
    virtual ~ScriptRuntime() = default;

    virtual bool ensureScript(uint32_t entityId, const std::string& scriptAsset) = 0;
    virtual bool executeScriptAsset(const std::string& scriptAsset) = 0;
    virtual void runUpdate(uint32_t entityId, float dt) = 0;
    virtual bool hasScript(uint32_t entityId) const = 0;
    virtual bool callEntityFunctionByName(uint32_t entityId, const std::string& functionName) = 0;
    virtual void syncEntities(const std::unordered_set<uint32_t>& activeEntities) = 0;

    virtual bool callGlobalFunction(
        const std::string& functionName,
        uint32_t entityId = 0,
        const std::string& stringArg = {}) = 0;
    virtual bool callGlobalBoolFunction(
        const std::string& functionName,
        uint32_t entityId = 0,
        const std::string& stringArg = {}) = 0;
};

} // namespace sle::scripting