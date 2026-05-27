#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <sle/scene/components/UIComponent.hpp>
#include <sle/ui/UIBindingContext.hpp>
#include <sle/ui/UIElement.hpp>
#include <sle/ui/UIResources.hpp>

#include <sle/scripting/ScriptResource.hpp>

#include <string>

namespace sle::ui {

struct UIDocumentDescriptor
{
    uint32_t ownerEntityId = 0;
    std::string layoutAsset;
    std::string fontAsset;
    std::string behaviorAsset;
    sle::components::UISpaceMode spaceMode = sle::components::UISpaceMode::Screen;
    bool visible = true;
    uint32_t layer = 0;
    std::string bindingScope;
};

class UIDocument
{
public:
    UIDocument() = default;
    explicit UIDocument(UIDocumentDescriptor descriptor);

    const UIDocumentDescriptor& getDescriptor() const { return descriptor; }
    const std::shared_ptr<UILayoutResource>& getLayoutResource() const { return layoutResource; }
    const std::shared_ptr<UIFontResource>& getFontResource() const { return fontResource; }
    const std::shared_ptr<sle::scripting::ScriptResource>& getBehaviorResource() const { return behaviorResource; }
    const std::optional<UIElement>& getLayoutRoot() const { return layoutRoot; }
    bool hasParsedLayout() const { return layoutRoot.has_value(); }
    const std::string& getLastError() const { return lastError; }
    UIBindingContext& getBindings() { return bindings; }
    const UIBindingContext& getBindings() const { return bindings; }

    void setLayoutResource(std::shared_ptr<UILayoutResource> resource) { layoutResource = std::move(resource); }
    void setFontResource(std::shared_ptr<UIFontResource> resource) { fontResource = std::move(resource); }
    void setBehaviorResource(std::shared_ptr<sle::scripting::ScriptResource> resource) { behaviorResource = std::move(resource); }
    void setLayoutRoot(UIElement root);
    void setLastError(std::string error);
    void clearLastError();
    void refreshBindings();
    bool isBehaviorLoaded() const { return behaviorLoaded; }
    void setBehaviorLoaded(bool loaded) { behaviorLoaded = loaded; }

private:
    UIDocumentDescriptor descriptor;
    std::shared_ptr<UILayoutResource> layoutResource;
    std::shared_ptr<UIFontResource> fontResource;
    std::shared_ptr<sle::scripting::ScriptResource> behaviorResource;
    std::optional<UIElement> layoutRoot;
    std::string lastError;
    UIBindingContext bindings;
    bool behaviorLoaded = false;
};

} // namespace sle::ui
