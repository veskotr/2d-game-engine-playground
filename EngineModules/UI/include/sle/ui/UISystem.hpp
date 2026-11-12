#pragma once

#include <sle/core/Result.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scripting/ScriptRuntime.hpp>
#include <sle/ui/UIDocument.hpp>
#include <sle/ui/UIFrameContext.hpp>
#include <sle/ui/UIValue.hpp>

#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace sle::ui {

class UISystem
{
public:
    sle::core::Result<bool> init(sle::scripting::ScriptRuntime* scriptRuntime);
    void shutdown();
    void update(UIFrameContext& ctx);
    void renderSnapshot(UIFrameContext& ctx);
    void setDefaultShaderID(uint32_t shaderID) { defaultShaderID = shaderID; }
    bool setBinding(const std::string& key, const UIValue& value);
    bool setDocumentVisible(std::size_t index, bool visible);

    UIDocument& createDocument(UIDocumentDescriptor descriptor);
    const std::vector<UIDocument>& getDocuments() const { return documents; }

private:
    bool loadDocumentResources(UIDocument& document);
    bool parseDocumentLayout(UIDocument& document);
    void syncEntityDocuments(UIFrameContext& ctx, bool executeBehaviors = true);
    void submitDocument(UIFrameContext& ctx, UIDocument& document, bool interactive = true);
    std::vector<UIDocument> documents;
    std::unordered_map<uint32_t, UIDocument> entityDocuments;
    std::unordered_set<uint32_t> deferredBehaviorEntities;
    sle::scripting::ScriptRuntime* scriptRuntime = nullptr;
    uint32_t defaultShaderID = 0;
    bool initialized = false;
};

} // namespace sle::ui
