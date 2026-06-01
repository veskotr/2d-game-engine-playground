#include <sle/ui/UISystem.hpp>

#include <sle/events/EventBus.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/platform/Input.hpp>
#include <sle/renderer/RendererCommand.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/UIComponent.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/ui/UIEvents.hpp>
#include <sle/ui/UILayoutParser.hpp>

#include <glm/gtc/matrix_transform.hpp>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <string_view>
#include <unordered_set>
#include <utility>

namespace sle::ui {
namespace {

constexpr glm::vec4 kDefaultPanelColor{0.15f, 0.15f, 0.15f, 0.85f};
constexpr glm::vec4 kDefaultButtonColor{0.25f, 0.45f, 0.85f, 0.95f};
constexpr glm::vec4 kDefaultLabelColor{1.0f, 1.0f, 1.0f, 1.0f};

struct ResolvedRect
{
    glm::vec2 position{0.0f};
    glm::vec2 size{0.0f};
};

struct HitTarget
{
    uint32_t ownerEntityId = 0;
    std::string elementId;
    std::string handler;
    ResolvedRect rect;
};

void applyAutoEntityBindings(UIFrameContext& ctx, UIDocument& document)
{
    const auto& descriptor = document.getDescriptor();
    if (descriptor.ownerEntityId == 0)
        return;

    const sle::entity::Entity entity(descriptor.ownerEntityId);
    if (!ctx.registry.hasEntity(entity))
        return;

    auto& bindings = document.getBindings();
    const std::string& scope = descriptor.bindingScope;

    auto setBoundValue = [&](const std::string& key, const UIValue& value)
    {
        bindings.set(key, value);
        if (!scope.empty())
            bindings.set(scope + "." + key, value);
    };

    setBoundValue("entity.id", static_cast<int64_t>(descriptor.ownerEntityId));

    if (const auto* transform = ctx.registry.getComponent<sle::components::TransformComponent>(entity))
    {
        const auto position = transform->getPosition();
        const auto scale = transform->getScale();
        setBoundValue("entity.transform.position.x", static_cast<double>(position.x));
        setBoundValue("entity.transform.position.y", static_cast<double>(position.y));
        setBoundValue("entity.transform.rotation", static_cast<double>(transform->getRotation()));
        setBoundValue("entity.transform.scale.x", static_cast<double>(scale.x));
        setBoundValue("entity.transform.scale.y", static_cast<double>(scale.y));
    }

    if (const auto* worldTransform = ctx.registry.getComponent<sle::components::WorldTransformComponent>(entity))
    {
        setBoundValue("entity.world.position.x", static_cast<double>(worldTransform->position.x));
        setBoundValue("entity.world.position.y", static_cast<double>(worldTransform->position.y));
        setBoundValue("entity.world.rotation", static_cast<double>(worldTransform->rotation));
    }

    if (const auto* sprite = ctx.registry.getComponent<sle::components::SpriteRenderer>(entity))
    {
        setBoundValue("entity.sprite.color.r", static_cast<double>(sprite->color.r));
        setBoundValue("entity.sprite.color.g", static_cast<double>(sprite->color.g));
        setBoundValue("entity.sprite.color.b", static_cast<double>(sprite->color.b));
        setBoundValue("entity.sprite.color.a", static_cast<double>(sprite->color.a));
    }

    // Optional target distance bindings derived from Animator targetEntities map:
    // key format: entity.target.<name>.distance
    if (const auto* animator = ctx.registry.getComponent<sle::components::AnimatorComponent>(entity))
    {
        const auto* selfTransform = ctx.registry.getComponent<sle::components::TransformComponent>(entity);
        if (selfTransform)
        {
            const glm::vec2 selfPos = selfTransform->getPosition();

            for (const auto& [targetName, targetId] : animator->targetEntities)
            {
                const sle::entity::Entity target(targetId);
                const auto* targetTransform = ctx.registry.getComponent<sle::components::TransformComponent>(target);
                if (!targetTransform)
                    continue;

                const glm::vec2 targetPos = targetTransform->getPosition();
                const float dx = targetPos.x - selfPos.x;
                const float dy = targetPos.y - selfPos.y;
                const float distance = std::sqrt(dx * dx + dy * dy);
                setBoundValue("entity.target." + targetName + ".distance", static_cast<double>(distance));
            }
        }
    }
}

const UIAttribute* findAttribute(const UIElement& element, std::string_view name)
{
    for (const auto& attribute : element.attributes)
    {
        if (attribute.name == name)
            return &attribute;
    }

    return nullptr;
}

std::string getAttributeValue(const UIElement& element, std::string_view name)
{
    if (const auto* attribute = findAttribute(element, name))
        return attribute->resolvedValue.empty() ? attribute->value : attribute->resolvedValue;

    return {};
}

float parseFloat(const UIElement& element, std::string_view name, float fallback)
{
    const std::string value = getAttributeValue(element, name);
    if (value.empty())
        return fallback;

    try
    {
        return std::stof(value);
    }
    catch (...)
    {
        return fallback;
    }
}

glm::vec4 parseColorValue(std::string value, const glm::vec4& fallback)
{
    if (value.empty())
        return fallback;

    if (value[0] == '#')
        value.erase(value.begin());

    auto hexToFloat = [](const std::string& part) -> float {
        return static_cast<float>(std::stoi(part, nullptr, 16)) / 255.0f;
    };

    try
    {
        if (value.size() == 6)
        {
            return {
                hexToFloat(value.substr(0, 2)),
                hexToFloat(value.substr(2, 2)),
                hexToFloat(value.substr(4, 2)),
                1.0f
            };
        }

        if (value.size() == 8)
        {
            return {
                hexToFloat(value.substr(0, 2)),
                hexToFloat(value.substr(2, 2)),
                hexToFloat(value.substr(4, 2)),
                hexToFloat(value.substr(6, 2))
            };
        }
    }
    catch (...)
    {
    }

    return fallback;
}

glm::vec4 parseColor(const UIElement& element, const glm::vec4& fallback)
{
    return parseColorValue(getAttributeValue(element, "color"), fallback);
}

bool decodeUtf8CodePoint(const std::string& text, std::size_t& index, uint32_t& outCodePoint)
{
    if (index >= text.size())
        return false;

    const std::size_t start = index;
    const auto next = [&]() -> unsigned char { return static_cast<unsigned char>(text[index++]); };

    const unsigned char first = next();
    if (first < 0x80)
    {
        outCodePoint = first;
        return true;
    }

    int continuationCount = 0;
    uint32_t codePoint = 0;

    if ((first & 0xE0) == 0xC0)
    {
        continuationCount = 1;
        codePoint = first & 0x1F;
    }
    else if ((first & 0xF0) == 0xE0)
    {
        continuationCount = 2;
        codePoint = first & 0x0F;
    }
    else if ((first & 0xF8) == 0xF0)
    {
        continuationCount = 3;
        codePoint = first & 0x07;
    }
    else
    {
        outCodePoint = static_cast<uint32_t>('?');
        return true;
    }

    for (int i = 0; i < continuationCount; ++i)
    {
        if (index >= text.size())
        {
            index = start + 1;
            outCodePoint = static_cast<uint32_t>('?');
            return true;
        }

        const unsigned char nextByte = next();
        if ((nextByte & 0xC0) != 0x80)
        {
            index = start + 1;
            outCodePoint = static_cast<uint32_t>('?');
            return true;
        }

        codePoint = (codePoint << 6) | (nextByte & 0x3F);
    }

    if ((continuationCount == 1 && codePoint < 0x80) ||
        (continuationCount == 2 && codePoint < 0x800) ||
        (continuationCount == 3 && codePoint < 0x10000) ||
        codePoint > 0x10FFFF ||
        (codePoint >= 0xD800 && codePoint <= 0xDFFF))
    {
        outCodePoint = static_cast<uint32_t>('?');
        return true;
    }

    outCodePoint = codePoint;
    return true;
}

glm::vec2 rotate2D(const glm::vec2& value, float radians)
{
    const float c = std::cos(radians);
    const float s = std::sin(radians);
    return {
        value.x * c - value.y * s,
        value.x * s + value.y * c
    };
}

glm::mat4 buildQuadModel(const glm::vec2& position, const glm::vec2& size, float rotation)
{
    glm::mat4 model(1.0f);
    model = glm::translate(model, glm::vec3(position.x + size.x * 0.5f, position.y + size.y * 0.5f, 0.0f));
    model = glm::rotate(model, rotation, glm::vec3(0.0f, 0.0f, 1.0f));
    model = glm::scale(model, glm::vec3(size.x, size.y, 1.0f));
    return model;
}

// Measure the pixel width of a single line of text (stops at newline or end).
float measureLineWidth(UIFontResource& font, float textScale, const std::string& line)
{
    float width = 0.0f;
    uint32_t prev = 0;
    std::size_t cursor = 0;
    while (cursor < line.size())
    {
        uint32_t cp = 0;
        if (!decodeUtf8CodePoint(line, cursor, cp))
            break;
        if (cp == '\n')
            break;
        width += font.getKerningAdvance(prev, cp) * textScale;
        UIFontGlyphInfo g;
        if (font.getGlyphInfo(cp, g))
            width += g.advance * textScale;
        prev = cp;
    }
    return width;
}

// Word-wrap `text` into lines that fit within `maxWidth` (world units). Respects explicit newlines.
// Returns one string per line. If maxWidth <= 0 the entire text is returned as-is.
std::vector<std::string> wrapTextToLines(UIFontResource& font, float textScale,
                                         const std::string& text, float maxWidth)
{
    std::vector<std::string> result;

    if (maxWidth <= 0.0f)
    {
        result.push_back(text);
        return result;
    }

    std::string line;
    std::string word;

    const auto commitWord = [&]()
    {
        if (word.empty())
            return;
        const std::string candidate = line.empty() ? word : (line + " " + word);
        if (!line.empty() && measureLineWidth(font, textScale, candidate) > maxWidth)
        {
            result.push_back(line);
            line = word;
        }
        else
        {
            line = candidate;
        }
        word.clear();
    };

    for (const char c : text)
    {
        if (c == ' ' || c == '\t')       { commitWord(); }
        else if (c == '\n')              { commitWord(); result.push_back(line); line.clear(); }
        else                             { word += c; }
    }
    commitWord();
    result.push_back(line);

    return result;
}

glm::vec2 screenAnchorToWorld(const sle::core::Camera2D& camera)
{
    return camera.getPosition();
}

glm::vec2 mouseToWorldUiPoint(const sle::core::Camera2D& camera)
{
    const auto& mouse = sle::input::Input::getMouse();
    const glm::vec2 viewport = camera.getViewportSize();
    const float zoom = std::max(camera.getZoom(), 0.0001f);

    return {
        camera.getPosition().x + static_cast<float>(mouse.position.x) / zoom,
        camera.getPosition().y + (viewport.y - static_cast<float>(mouse.position.y)) / zoom
    };
}

bool contains(const ResolvedRect& rect, const glm::vec2& point)
{
    return point.x >= rect.position.x && point.x <= rect.position.x + rect.size.x
        && point.y >= rect.position.y && point.y <= rect.position.y + rect.size.y;
}

bool descriptorsMatch(const UIDocumentDescriptor& lhs, const UIDocumentDescriptor& rhs)
{
    return lhs.ownerEntityId == rhs.ownerEntityId
        && lhs.layoutAsset == rhs.layoutAsset
        && lhs.fontAsset == rhs.fontAsset
        && lhs.behaviorAsset == rhs.behaviorAsset
        && lhs.spaceMode == rhs.spaceMode
        && lhs.visible == rhs.visible
        && lhs.layer == rhs.layer
        && lhs.bindingScope == rhs.bindingScope;
}

void renderElement(
    UIFrameContext& ctx,
    UIDocument& document,
    const UIElement& element,
    const glm::vec2& parentOrigin,
    float scaleFactor,
    float rotation,
    uint32_t shaderID,
    bool yAxisDown,
    std::vector<HitTarget>& hitTargets)
{
    const glm::vec2 size{
        parseFloat(element, "width", element.type == "Label" ? 0.0f : 100.0f) * scaleFactor,
        parseFloat(element, "height", element.type == "Label" ? 0.0f : 30.0f) * scaleFactor
    };

    const float ySign = yAxisDown ? -1.0f : 1.0f;
    const glm::vec2 localOffset = {
        parseFloat(element, "x", 0.0f) * scaleFactor,
        parseFloat(element, "y", 0.0f) * scaleFactor * ySign
    };
    const glm::vec2 rotatedOffset = rotate2D(localOffset, rotation);
    const glm::vec2 elementOrigin = parentOrigin + rotatedOffset;
    glm::vec2 renderPosition = elementOrigin;
    if (yAxisDown)
        renderPosition.y -= size.y;

    // --- Anchor: shift element so the named corner is placed at (x, y) ---
    // "topleft" (default): no shift.
    // In screen-space (yAxisDown): elementOrigin is the visual top-left.
    //   middle-row anchors shift renderPosition up by size.y/2 (increase world-y).
    //   bottom-row anchors shift by size.y.
    // In world-space (yAxisDown=false): renderPosition is the visual bottom-left.
    //   middle-row anchors shift down by size.y/2 (decrease world-y).
    //   bottom-row anchors shift by size.y.
    glm::vec2 anchorOffset{0.0f};
    if (size.x > 0.0f || size.y > 0.0f)
    {
        const std::string anchor = getAttributeValue(element, "anchor");
        if (!anchor.empty() && anchor != "topleft")
        {
            if (anchor == "topcenter" || anchor == "center" || anchor == "bottomcenter")
                anchorOffset.x = -size.x * 0.5f;
            else if (anchor == "topright" || anchor == "middleright" || anchor == "bottomright")
                anchorOffset.x = -size.x;

            if (anchor == "middleleft" || anchor == "center" || anchor == "middleright")
                anchorOffset.y = yAxisDown ? size.y * 0.5f : -size.y * 0.5f;
            else if (anchor == "bottomleft" || anchor == "bottomcenter" || anchor == "bottomright")
                anchorOffset.y = yAxisDown ? size.y : -size.y;
        }
    }
    const glm::vec2 anchoredOrigin   = elementOrigin   + anchorOffset;
    const glm::vec2 anchoredRenderPos = renderPosition + anchorOffset;

    if (size.x > 0.0f && size.y > 0.0f && element.type != "Label")
    {
        renderer::QuadCommand cmd;
        cmd.modelMatrix = buildQuadModel(anchoredRenderPos, size, rotation);
        cmd.shader_id = shaderID;
        cmd.layer = document.getDescriptor().layer;
        cmd.color = element.type == "Button"
            ? parseColor(element, kDefaultButtonColor)
            : parseColor(element, kDefaultPanelColor);
        ctx.renderer.submit(cmd);
    }

    const std::string text = !element.resolvedText.empty() ? element.resolvedText : element.text;
    if (!text.empty() && document.getFontResource())
    {
        auto& font = *document.getFontResource();
        const float requestedFontSize = parseFloat(element, "fontSize", font.getPixelHeight());
        const float textScale = (requestedFontSize / std::max(font.getPixelHeight(), 1.0f)) * scaleFactor;
        const glm::vec4 textColor = parseColor(element, kDefaultLabelColor);

        // Determine text lines (wrapping or single-line with explicit newlines).
        const std::string alignAttr = getAttributeValue(element, "textAlign"); // left|center|right
        const bool doWrap = getAttributeValue(element, "wrap") == "true";
        const float wrapWidth = doWrap ? size.x : 0.0f;
        const std::vector<std::string> lines = wrapTextToLines(font, textScale, text, wrapWidth);

        float penY = font.getAscent() * textScale;

        for (const std::string& lineText : lines)
        {
            // Compute per-line starting X for alignment.
            float penX = 0.0f;
            if (!alignAttr.empty() && alignAttr != "left" && size.x > 0.0f)
            {
                const float lineW = measureLineWidth(font, textScale, lineText);
                if (alignAttr == "center")
                    penX = (size.x - lineW) * 0.5f;
                else if (alignAttr == "right")
                    penX = size.x - lineW;
            }

            uint32_t previousCodePoint = 0;
            std::size_t cursor = 0;
            while (cursor < lineText.size())
            {
                uint32_t codePoint = 0;
                if (!decodeUtf8CodePoint(lineText, cursor, codePoint))
                    break;

                penX += font.getKerningAdvance(previousCodePoint, codePoint) * textScale;

                UIFontGlyphInfo glyphInfo;
                if (!font.getGlyphInfo(codePoint, glyphInfo) || !glyphInfo.texture)
                {
                    previousCodePoint = codePoint;
                    continue;
                }

                const glm::vec2 glyphOffset{
                    penX + glyphInfo.bearing.x * textScale,
                    penY + glyphInfo.bearing.y * textScale + glyphInfo.size.y * textScale
                };
                const glm::vec2 glyphPosition = anchoredOrigin + rotate2D(glyphOffset, rotation);
                const glm::vec2 glyphSize = glyphInfo.size * textScale;
                if (glyphSize.x <= 0.0f || glyphSize.y <= 0.0f)
                {
                    penX += glyphInfo.advance * textScale;
                    continue;
                }

                renderer::QuadCommand glyphCommand;
                glyphCommand.modelMatrix = buildQuadModel(glyphPosition, glyphSize, rotation);
                glyphCommand.color = textColor;
                glyphCommand.uvRect = {0.0f, 1.0f, 1.0f, 0.0f};
                glyphCommand.texture_id = glyphInfo.texture->getID();
                glyphCommand.shader_id = shaderID;
                glyphCommand.layer = document.getDescriptor().layer;
                ctx.renderer.submit(glyphCommand);

                penX += glyphInfo.advance * textScale;
                previousCodePoint = codePoint;
            }

            penY += font.getLineHeight() * textScale;
        }
    }

    const std::string handler = getAttributeValue(element, "onClick");
    if (!handler.empty() && size.x > 0.0f && size.y > 0.0f)
    {
        hitTargets.push_back(HitTarget{
            document.getDescriptor().ownerEntityId,
            element.id,
            handler,
            {anchoredRenderPos, size}
        });
    }

    for (const auto& child : element.children)
        renderElement(ctx, document, child, anchoredOrigin, scaleFactor, rotation, shaderID, yAxisDown, hitTargets);
}

void dispatchClick(
    UIFrameContext& ctx,
    sle::scripting::ScriptRuntime* scriptRuntime,
    const std::vector<HitTarget>& hitTargets,
    const glm::vec2& mousePoint)
{
    if (!scriptRuntime || !sle::input::Input::getMouse().leftPressed)
        return;

    for (auto it = hitTargets.rbegin(); it != hitTargets.rend(); ++it)
    {
        if (!contains(it->rect, mousePoint))
            continue;

        ctx.eventBus.emit(UIClickEvent{it->ownerEntityId, it->elementId, it->handler});
        scriptRuntime->callGlobalFunction(it->handler, it->ownerEntityId, it->elementId);
        break;
    }
}

} // namespace

sle::core::Result<bool> UISystem::init(sle::scripting::ScriptRuntime* inScriptRuntime)
{
    scriptRuntime = inScriptRuntime;
    initialized = true;
    return sle::core::Result<bool>::success(true);
}

bool UISystem::setBinding(const std::string& key, const UIValue& value)
{
    bool changed = false;

    for (auto& document : documents)
        changed |= document.getBindings().set(key, value);

    for (auto& [_, document] : entityDocuments)
        changed |= document.getBindings().set(key, value);

    return changed;
}

void UISystem::shutdown()
{
    documents.clear();
    entityDocuments.clear();
    scriptRuntime = nullptr;
    initialized = false;
}

void UISystem::update(UIFrameContext& ctx)
{
    if (!initialized)
        return;

    syncEntityDocuments(ctx);

    for (auto& document : documents)
    {
        auto dirtyKeys = document.getBindings().consumeDirtyKeys();
        if (!dirtyKeys.empty())
            document.refreshBindings();

        submitDocument(ctx, document);
    }

    for (auto& [_, document] : entityDocuments)
    {
        applyAutoEntityBindings(ctx, document);

        auto dirtyKeys = document.getBindings().consumeDirtyKeys();
        if (!dirtyKeys.empty())
            document.refreshBindings();

        submitDocument(ctx, document);
    }
}

void UISystem::syncEntityDocuments(UIFrameContext& ctx)
{
    std::unordered_set<uint32_t> seenEntities;

    ctx.registry.view<sle::components::UIComponent>([this, &ctx, &seenEntities](sle::entity::Entity entity, sle::components::UIComponent& component)
    {
        seenEntities.insert(entity.getID());

        UIDocumentDescriptor descriptor;
        descriptor.ownerEntityId = entity.getID();
        descriptor.layoutAsset = component.layoutAsset;
        descriptor.fontAsset = component.fontAsset;
        descriptor.behaviorAsset = component.behaviorAsset;
        descriptor.spaceMode = component.spaceMode;
        descriptor.visible = component.visible;
        descriptor.layer = component.layer;
        descriptor.bindingScope = component.bindingScope;

        auto it = entityDocuments.find(entity.getID());
        if (it != entityDocuments.end() && descriptorsMatch(it->second.getDescriptor(), descriptor))
            return;

        UIDocument document(std::move(descriptor));
        if (!loadDocumentResources(document))
        {
            entityDocuments[entity.getID()] = std::move(document);
            return;
        }

        parseDocumentLayout(document);
        document.refreshBindings();

        if (!document.isBehaviorLoaded() && !document.getDescriptor().behaviorAsset.empty() && scriptRuntime)
            document.setBehaviorLoaded(scriptRuntime->executeScriptAsset(document.getDescriptor().behaviorAsset));

        entityDocuments[entity.getID()] = std::move(document);
    });

    for (auto it = entityDocuments.begin(); it != entityDocuments.end(); )
    {
        if (seenEntities.contains(it->first))
            ++it;
        else
            it = entityDocuments.erase(it);
    }
}

UIDocument& UISystem::createDocument(UIDocumentDescriptor descriptor)
{
    documents.emplace_back(std::move(descriptor));
    if (!loadDocumentResources(documents.back()))
        return documents.back();

    parseDocumentLayout(documents.back());
    documents.back().refreshBindings();

    if (!documents.back().isBehaviorLoaded() && !documents.back().getDescriptor().behaviorAsset.empty() && scriptRuntime)
        documents.back().setBehaviorLoaded(scriptRuntime->executeScriptAsset(documents.back().getDescriptor().behaviorAsset));

    return documents.back();
}

bool UISystem::loadDocumentResources(UIDocument& document)
{
    const auto& descriptor = document.getDescriptor();
    document.clearLastError();

    if (!descriptor.layoutAsset.empty())
    {
        auto layout = sle::core::Resources::create<UILayoutResource>(descriptor.layoutAsset, descriptor.layoutAsset);
        if (!layout)
        {
            document.setLastError("Failed to load UI layout resource: " + descriptor.layoutAsset);
            return false;
        }

        document.setLayoutResource(std::move(layout));
    }

    if (!descriptor.fontAsset.empty())
    {
        auto font = sle::core::Resources::create<UIFontResource>(descriptor.fontAsset, descriptor.fontAsset);
        if (!font)
        {
            document.setLastError("Failed to load UI font resource: " + descriptor.fontAsset);
            return false;
        }

        document.setFontResource(std::move(font));
    }

    if (!descriptor.behaviorAsset.empty())
    {
        auto behavior = sle::core::Resources::create<sle::scripting::ScriptResource>(descriptor.behaviorAsset, descriptor.behaviorAsset);
        if (!behavior)
        {
            document.setLastError("Failed to load UI behavior resource: " + descriptor.behaviorAsset);
            return false;
        }

        document.setBehaviorResource(std::move(behavior));
    }

    return true;
}

bool UISystem::parseDocumentLayout(UIDocument& document)
{
    const auto& layout = document.getLayoutResource();
    if (!layout)
        return true;

    const auto result = UILayoutParser::parse(*layout);
    if (!result.ok())
    {
        document.setLastError("Failed to parse UI layout: " + result.error());
        return false;
    }

    document.setLayoutRoot(result.value());
    return true;
}

void UISystem::submitDocument(UIFrameContext& ctx, UIDocument& document)
{
    if (!document.getDescriptor().visible || !document.getLayoutRoot())
        return;

    glm::vec2 basePosition{0.0f};
    float scaleFactor = 1.0f;
    float rotation = 0.0f;
    bool yAxisDown = false;

    if (document.getDescriptor().spaceMode == sle::components::UISpaceMode::Screen)
    {
        const float zoom = std::max(ctx.camera.getZoom(), 0.0001f);
        const glm::vec2 viewport = ctx.camera.getViewportSize();
        basePosition = screenAnchorToWorld(ctx.camera) + glm::vec2{0.0f, viewport.y / zoom};
        scaleFactor = 1.0f / zoom;
        yAxisDown = true;
    }
    else if (document.getDescriptor().ownerEntityId != 0)
    {
        if (const auto* world = ctx.registry.getComponent<sle::components::WorldTransformComponent>(sle::entity::Entity(document.getDescriptor().ownerEntityId)))
        {
            basePosition = world->position;
            // World-space UI should follow the entity transform position/rotation,
            // but keep UI units stable regardless of sprite/body visual scale.
            scaleFactor = 1.0f;
            rotation = world->rotation;
        }
    }

    std::vector<HitTarget> hitTargets;
    hitTargets.reserve(16);
    renderElement(ctx, document, *document.getLayoutRoot(), basePosition, scaleFactor, rotation, defaultShaderID, yAxisDown, hitTargets);

    dispatchClick(ctx, scriptRuntime, hitTargets, mouseToWorldUiPoint(ctx.camera));
}

} // namespace sle::ui
