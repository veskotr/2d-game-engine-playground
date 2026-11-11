#pragma once

#include <cstdint>
#include <string>
#include <unordered_set>
#include <vector>

namespace sle::scripting {

struct ScriptBehaviourSpec
{
    std::string key;
    std::string scriptAsset;
    bool enabled = true;
    int order = 0;
};

class ScriptRuntime
{
public:
    virtual ~ScriptRuntime() = default;

    virtual bool ensureScript(uint32_t entityId, const std::string& scriptAsset) = 0;
    virtual void configureBehaviours(uint32_t, const std::vector<ScriptBehaviourSpec>&) {}
    virtual bool ensureBehaviour(uint32_t entity, const std::string& key, const std::string& asset)
    { return key.empty() && ensureScript(entity, asset); }
    virtual bool hasBehaviour(uint32_t entity, const std::string& key) const
    { return key.empty() && hasScript(entity); }
    virtual void runBehaviourUpdate(uint32_t entity, const std::string& key, float dt)
    { if (key.empty()) runUpdate(entity, dt); }
    virtual void removeBehaviour(uint32_t, const std::string&) {}
    virtual uint64_t getBehaviourGeneration(uint32_t, const std::string&) const { return 0; }
    virtual bool callEntityBoolFunction(uint32_t entity, const std::string& name)
    { return callGlobalBoolFunction(name, entity); }
    virtual bool executeScriptAsset(const std::string& scriptAsset) = 0;
    virtual bool executeScriptAssetForEntity(const std::string& scriptAsset, uint32_t)
    { return executeScriptAsset(scriptAsset); }
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