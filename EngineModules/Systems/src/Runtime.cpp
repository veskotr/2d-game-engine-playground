#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <glm/glm.hpp>
#include <chrono>
#include <sle/core/Log.hpp>
#include <sle/engine/Runtime.hpp>
#include "ProjectSettings.hpp"
#include <sle/engine/ProjectRuntime.hpp>
#include <thread>
#include <sle/engine/Context.hpp>
#include <sle/engine/CameraSystem.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/ChunkActivity.hpp>
#include <sle/engine/EntityAuthoring.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/events/ScriptEvents.hpp>
#include <sle/resources/Resources.hpp>

#include "ComponentRegistry.hpp"
#include "ScriptBehaviours.hpp"
#include "SceneEntityName.hpp"

#include <algorithm>
#include <cmath>
#include <filesystem>
#include <fstream>
#include <tuple>
#include <string>

namespace sle
{
    namespace
    {
        template<class Action>
        struct ScopeExit {
            Action action;
            ~ScopeExit() { action(); }
        };

        std::string resolveAssetPath(const std::string& relativePath)
        {
            namespace fs = std::filesystem;

            fs::path path(relativePath);
            if (fs::exists(path))
                return path.lexically_normal().generic_string();

            fs::path current = fs::current_path();
            while (!current.empty())
            {
                fs::path candidate = current / relativePath;
                if (fs::exists(candidate))
                    return candidate.lexically_normal().generic_string();

                if (current == current.root_path())
                    break;
                current = current.parent_path();
            }

            return relativePath;
        }
    }

    using namespace sle::core;
    using namespace sle::entity;
    using namespace sle::renderer;
    using namespace sle::input;

    Runtime::Runtime(const sle::core::EngineConfig &config)
        : config(config)
        , chunkRuntimeState(config.chunkActivation)
        , scriptApi(*this)
    {
    }

    bool Runtime::registerScene(const std::string& sceneName, SceneManager::SceneBuilder builder)
    {
        return sceneManager.registerScene(sceneName, std::move(builder));
    }

    bool Runtime::registerSceneFromFile(const std::string& sceneName, const std::string& jsonPath)
    {
        if (projectContext) {
            const auto path=resolveProjectAsset(jsonPath);
            if (path.empty()) return false;
            if (const auto* document=projectContext->findSceneDocument(path)) return sceneManager.registerSceneFromData(sceneName,*document,path);
            return sceneManager.registerSceneFromFile(sceneName,path);
        }
        return sceneManager.registerSceneFromFile(sceneName, jsonPath);
    }

    bool Runtime::registerSceneFromData(const std::string& sceneName, const nlohmann::json& data, const std::string& sourcePath)
    {
        return sceneManager.registerSceneFromData(sceneName, data, sourcePath);
    }

    Result<bool> Runtime::startProject(const ProjectRuntime& project)
    {
        if (!initialized) return Result<bool>::error("Runtime is not initialized");
        try {
            const auto engineAssets=config.engineAssetRoot;
            config=project.engineConfig();config.engineAssetRoot=engineAssets;
            projectContext=std::make_shared<ProjectRuntime>(project);
            projectContext->registerScenes(*this);
            std::vector<std::string> modulePaths;
            for (const auto& root : project.assetRoots()) { modulePaths.push_back((root/"Scripts").generic_string());modulePaths.push_back(root.generic_string()); }
            scriptEngine.prependModulePaths(modulePaths);
            camera.setViewport(static_cast<float>(config.width),static_cast<float>(config.height));
            const auto loaded=loadScene(project.startupScene());
            if (!loaded.ok()) return loaded;
            physicsWorld->setBlockedCollisionPairs(project.collisionMatrix());
            resetInputActions(project.inputActions());
            authoredSceneKeys.clear();
            for (const auto& entity : project.sceneDocument(project.startupScene()).at("entities")) authoredSceneKeys.push_back(entity.at("key").get<std::string>());
            setPhysicsDebugEnabled(false);setEmbeddedPaused(false);
            return loaded;
        } catch (const std::exception& error) { return Result<bool>::error(error.what()); }
    }

    std::string Runtime::resolveProjectAsset(const std::string& value) const {
        if (!projectContext) return value;
        try { return projectContext->resolveAsset(value); }
        catch (const std::exception& error) { Log::warn("Project asset rejected: {}",error.what());return {}; }
    }
    std::string Runtime::resolveTileAsset(const std::string& value, bool editorDocument) const {
        if (!editorDocument && projectContext) return projectContext->resolveAsset(value);
        return ProjectRuntime::resolveEditorAsset(editorAssetRoot, value);
    }

    nlohmann::json Runtime::resolveProjectComponents(const nlohmann::json& value) const {
        return projectContext ? projectContext->resolveComponentAssets(value) : value;
    }

    bool Runtime::hasScene(const std::string& sceneName) const
    {
        return sceneManager.hasScene(sceneName);
    }

    Result<bool> Runtime::loadScene(const std::string& sceneName)
    {
        auto result = sceneManager.loadScene(sceneName, *this, scene);
        if (result.ok())
        {
            embeddedStepRequested = false;
            if (entityPreview)
            {
                camera.setViewport(static_cast<float>(config.width), static_cast<float>(config.height));
                camera.setPosition({0.0f, 0.0f});
                camera.setZoom(1.0f);
                camera.setRotation(0.0f);
                renderer.setClearColor(config.backgroundColor);
            }
            entityPreview = false;
            refreshChunkRuntimeState();
            Log::info("Loaded scene: {}", sceneName);
        }

        return result;
    }

    void Runtime::refreshChunkRuntimeState()
    {
        chunkRuntimeState.refreshFromScene(scene);
    }

    Result<bool> Runtime::requestSceneSwitch(const std::string& sceneName)
    {
        return sceneManager.requestSceneSwitch(sceneName);
    }

    float Runtime::getDeltaTime() const
    {
        return frameDeltaTime;
    }

    glm::vec2 Runtime::getWindowSize() const
    {
        if (embedded || componentCameraActive) return camera.getViewportSize();
        return {
            static_cast<float>(window.getWidth()),
            static_cast<float>(window.getHeight())};
    }

    glm::vec2 Runtime::getCameraPosition() const
    {
        return camera.getPosition();
    }

    void Runtime::setCameraPosition(const glm::vec2& position)
    {
        camera.setPosition(position);
    }

    void Runtime::moveCamera(const glm::vec2& delta)
    {
        camera.move(delta);
    }

    float Runtime::getCameraZoom() const
    {
        return camera.getZoom();
    }

    void Runtime::setCameraZoom(float zoom)
    {
        camera.setZoom(zoom);
    }

    bool Runtime::setUIBinding(const std::string& key, const std::string& value)
    {
        if (key.empty())
            return false;

        return uiSystem.setBinding(key, value);
    }

    bool Runtime::emitScriptEvent(const std::string& eventName, uint32_t sourceEntity, const std::string& payload)
    {
        if (eventName.empty())
            return false;

        scene.getEventBus().queue(sle::events::ScriptEvent{
            eventName,
            sle::entity::Entity(sourceEntity),
            payload,
        });
        return true;
    }

    void Runtime::setPhysicsDebugEnabled(bool enabled)
    {
        renderSystem.setPhysicsDebugEnabled(enabled);
    }

    bool Runtime::isPhysicsDebugEnabled() const
    {
        return renderSystem.isPhysicsDebugEnabled();
    }

    Runtime::~Runtime()
    {
        scriptEngine.shutdown();
        scene.destroy();
        // Release every runtime-owned GPU resource while the GL context is valid.
        // Clearing the shared cache alone cannot release UI and shader references.
        uiSystem.shutdown();
        missingTileTexture.reset();
        defaultQuadShader.reset();
        defaultDebugShader.reset();
        Resources::clear();
        renderer.shutdown();
        window.destroy();
    }

    Result<bool> Runtime::init()
    {
        if (initializationStarted)
            return Result<bool>::error("Runtime initialization already attempted");
        initializationStarted = true;
        auto windowResult = window.create(config);
        if (!windowResult.ok())
            return Result<bool>::error(windowResult.error());

        camera.setViewport(
            static_cast<float>(window.getWidth()),
            static_cast<float>(window.getHeight()));

        return initServices();
    }

    Result<bool> Runtime::initEmbedded(bool initializeAudio)
    {
        if (initializationStarted)
            return Result<bool>::error("Runtime initialization already attempted");
        if (config.width <= 0 || config.height <= 0)
            return Result<bool>::error("Embedded viewport dimensions must be positive");
        if (!glad_glGetString || !glGetString(GL_VERSION) || !GLAD_GL_VERSION_4_3)
            return Result<bool>::error("Embedded runtime requires a current OpenGL 4.3+ context and loaded GLAD");
        initializationStarted = true;
        initializeAudioOnInit = initializeAudio;
        embedded = true;
        camera.setViewport(static_cast<float>(config.width), static_cast<float>(config.height));
        return initServices();
    }

    Result<bool> Runtime::renderEmbeddedFrame(int framebufferWidth, int framebufferHeight)
    {
        if (!embedded || !initialized)
            return Result<bool>::error("Embedded runtime is not initialized");
        if (framebufferWidth <= 0 || framebufferHeight <= 0)
            return Result<bool>::error("Framebuffer dimensions must be positive");
        hostWidth=framebufferWidth; hostHeight=framebufferHeight;
        Context gameCtx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, camera, physicsWorld.get(), 0.0f};
        transformSystem.update(gameCtx);
        componentCameraActive=CameraSystem::update(scene.getRegistry(),camera);
        const bool editorView=entityPreview || sceneViewOverride;
        if (editorView && fitPreview) {
            EntityPreviewSystem::frameCamera(scene, editorCamera, static_cast<float>(framebufferWidth)/framebufferHeight);
            fitPreview=false;
        }
        auto& displayCamera=editorView ? editorCamera : camera;
        if (embeddedPaused && renderSystem.isPhysicsDebugEnabled())
            tileCollisionSystem->update(scene, entityPreview ? displayCamera : camera);
        renderer.setCamera(&displayCamera);
        configureRenderViewport();
        Context ctx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, displayCamera, physicsWorld.get(), 0.0f};
        renderer.beginFrame();
        renderSystem.update(ctx);
        if (editorView) EntityPreviewSystem::submitOverlay(scene,renderer,editorCamera,editorGrid);
        sle::ui::UIFrameContext uiCtx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, displayCamera, scriptEngine, 0.0f};
        uiSystem.renderSnapshot(uiCtx);
        renderer.endFrame();
        return Result<bool>::success(true);
    }

    void Runtime::enableEmbeddedAudio()
    {
        if (!embedded || audioAttempted) return;
        audioAttempted = true;
        audioSystem.init();
        if (embeddedPaused) audioSystem.setPaused(true);
    }

    void Runtime::setEditorAssetRoot(std::filesystem::path root, std::string_view artStyle)
    {
        setEditorProjectSettings(std::move(root), artStyle.empty()
            ? nlohmann::json::object() : nlohmann::json{{"artStyle", artStyle}});
    }

    std::string Runtime::projectTagId(const std::string& name) const
    {
        if (!projectContext) return {};
        const auto& tags = projectContext->tagsByName();
        const auto found = tags.find(name);
        return found == tags.end() ? std::string{} : found->second;
    }

    void Runtime::setEditorProjectSettings(std::filesystem::path root, const nlohmann::json& settings)
    {
        const auto textureDefault = detail::artStyleFilter(settings);
        const auto blockedPairs = detail::blockedCollisionPairs(settings);
        detail::projectTags(settings);
        auto actions = detail::projectInputActions(settings);
        editorInputActions = std::move(actions);
        editorTextureDefault = textureDefault;
        editorBlockedPairs = blockedPairs;
        if (root.empty()) {
            editorAssetRoot.clear();
            return;
        }
        std::error_code error;
        editorAssetRoot = std::filesystem::canonical(root, error);
    }

    renderer::TextureFilter Runtime::spriteTextureFilter(const std::string& path) const
    {
        if (!projectContext && editorAssetRoot.empty()) return renderer::TextureFilter::Default;
        const std::filesystem::path asset(path);
        if (projectContext) return projectContext->textureFilter(asset);
        return ProjectRuntime::previewTextureFilter(editorAssetRoot, asset,
            editorTextureDefault.value_or(renderer::TextureFilter::Default));
    }

    Result<bool> Runtime::loadEntityPreview(const nlohmann::json& document, bool preserveView)
    {
        if (!embedded || !initialized) return Result<bool>::error("Embedded runtime is not initialized");
        const auto valid = EntityAuthoring::validatePrefab(document);
        if (!valid.ok()) return valid;
        return loadScenePreview({{"entities", document.at("entities")}}, preserveView);
    }

    Result<bool> Runtime::loadScenePreview(const nlohmann::json& document, bool preserveView)
    {
        if (!embedded || !initialized) return Result<bool>::error("Embedded runtime is not initialized");
        nlohmann::json canonical;
        try { canonical = SceneAuthoring::canonicalScene(document); }
        catch (const std::exception& error) { return Result<bool>::error(error.what()); }
        const bool keepView = preserveView && entityPreview;
        registerSceneFromData("entity_preview", canonical, "editor.scene.json");
        const auto loaded = sceneManager.loadScene("entity_preview", *this, scene, false, nullptr, keepView);
        if (!loaded.ok()) return loaded;
        authoredSceneKeys.clear();
        for (const auto& e : canonical.at("entities")) authoredSceneKeys.push_back(e.at("key").get<std::string>());
        refreshChunkRuntimeState();
        editorCamera.setZoom(1.0f);
        uiSystem.setDocumentVisible(debugFpsDocumentIndex, false);
        entityPreview = true;
        fitPreview = !keepView;
        embeddedStepRequested = false;
        setEmbeddedPaused(true);
        renderSystem.setPhysicsDebugEnabled(true);
        renderer.setClearColor({0.09f, 0.10f, 0.12f, 1.0f});
        return Result<bool>::success(true);
    }

    Result<bool> Runtime::renderEntityPreview(int width, int height, bool fit)
    {
        if (!entityPreview || width <= 0 || height <= 0)
            return Result<bool>::error("Entity preview is not ready or has invalid dimensions");
        const float aspect = static_cast<float>(width) / height;
        fitPreview = fitPreview || fit || std::abs(aspect - previewAspect) > 0.001f;
        previewAspect = aspect;
        return renderEmbeddedFrame(width, height);
    }

    void Runtime::zoomEntityPreview(float factor)
    {
        if (!std::isfinite(factor) || factor <= 0.0f) return;
        const auto size=editorCamera.getViewportSize();
        const auto next=glm::clamp(size*factor,glm::vec2(0.01f),glm::vec2(1.0e6f));
        const auto center=editorCamera.getPosition()+size*0.5f;
        editorCamera.setViewport(next.x,next.y);
        editorCamera.setPosition(center-next*0.5f);
    }

    bool Runtime::setEditorGrid(const EditorGrid& grid) {
        if (!grid.valid()) return false;
        editorGrid=grid;
        return true;
    }

    void Runtime::panEntityPreview(glm::vec2 delta) {
        if (std::isfinite(delta.x) && std::isfinite(delta.y)) editorCamera.move(delta);
    }

    Result<bool> Runtime::startScenePlay(const nlohmann::json& document) {
        if (!embedded || !initialized) return Result<bool>::error("Embedded runtime is not initialized");
        nlohmann::json canonical;
        try { canonical=SceneAuthoring::canonicalScene(document); }
        catch (const std::exception& e) { return Result<bool>::error(e.what()); }
        if (!registerSceneFromData("editor_play",canonical,"editor.scene.json")) return Result<bool>::error("Cannot register scene play");
        const auto loaded=loadScene("editor_play");
        if (!loaded.ok()) return loaded;
        physicsWorld->setBlockedCollisionPairs(editorBlockedPairs);
        resetInputActions(editorInputActions);
        authoredSceneKeys.clear();
        for (const auto& e : canonical.at("entities")) authoredSceneKeys.push_back(e.at("key").get<std::string>());
        setPhysicsDebugEnabled(false);
        setEmbeddedPaused(false);
        return loaded;
    }

    Result<bool> Runtime::renderSceneView(int width,int height,float dt,bool gameView,bool playing,bool fit) {
        if (width<=0 || height<=0 || !std::isfinite(dt) || dt<0)
            return Result<bool>::error("Invalid scene viewport frame");
        // Preserve the legacy Game wrapper's editor-shortcut policy during advance.
        const bool previousGameView=gameViewOverride;
        ScopeExit restore{[&] { gameViewOverride=previousGameView; }};
        gameViewOverride=gameView;
        if (playing) {
            const auto advanced=advanceEmbeddedFrame(dt);
            if (!advanced.ok()) return advanced;
        }
        return drawSceneView(width,height,gameView,fit);
    }

    Result<bool> Runtime::drawSceneView(int width,int height,bool gameView,bool fit) {
        if (!embedded || !initialized)
            return Result<bool>::error("Embedded runtime is not initialized");
        if (width<=0 || height<=0) return Result<bool>::error("Invalid scene viewport dimensions");
        const bool previousSceneView=sceneViewOverride,previousGameView=gameViewOverride;
        const bool previousDebug=isPhysicsDebugEnabled(),previousPreview=entityPreview;
        const int previousWidth=hostWidth,previousHeight=hostHeight;
        GLint viewport[4]{},drawFramebuffer=0,readFramebuffer=0;
        glGetIntegerv(GL_VIEWPORT,viewport);
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING,&drawFramebuffer);
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING,&readFramebuffer);
        ScopeExit restore{[&] {
            entityPreview=previousPreview;
            setPhysicsDebugEnabled(previousDebug);
            sceneViewOverride=previousSceneView; gameViewOverride=previousGameView;
            // Legacy metrics remember the last Game draw, never the Scene size.
            if (!gameView) { hostWidth=previousWidth; hostHeight=previousHeight; }
            renderer.setCamera(&camera);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,drawFramebuffer);
            glBindFramebuffer(GL_READ_FRAMEBUFFER,readFramebuffer);
            glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
        }};
        sceneViewOverride=!gameView; gameViewOverride=gameView;
        if (!gameView) {
            const float aspect=static_cast<float>(width)/height;
            fitPreview=fitPreview || fit || std::abs(aspect-previewAspect)>0.001f;
            previewAspect=aspect;
        } else {
            entityPreview=false;
            setPhysicsDebugEnabled(false);
        }
        return renderEmbeddedFrame(width,height);
    }

    std::array<float,6> Runtime::sceneGameViewport() const {
        const auto size=camera.getViewportSize();
        const float scale=std::min(hostWidth/size.x,hostHeight/size.y);
        const float width=std::max(1.0f,std::floor(size.x*scale)),height=std::max(1.0f,std::floor(size.y*scale));
        return {std::floor((hostWidth-width)*0.5f),std::floor((hostHeight-height)*0.5f),width,height,size.x,size.y};
    }

    void Runtime::configureRenderViewport() {
        glViewport(0,0,hostWidth,hostHeight);
        if (gameViewOverride || (!embedded && !sceneViewOverride)) {
            const auto rect=sceneGameViewport();
            glViewport(static_cast<int>(rect[0]),static_cast<int>(rect[1]),static_cast<int>(rect[2]),static_cast<int>(rect[3]));
            if (!embedded && componentCameraActive)
                Input::setHostCursorViewport({rect[0],rect[1],rect[2],rect[3]},{rect[4],rect[5]});
            else if (!embedded) Input::clearHostCursorViewport();
        }
    }

    nlohmann::json Runtime::sceneTransforms() {
        nlohmann::json result=nlohmann::json::object();
        auto keys=authoredSceneKeys;
        if (projectContext) if (const auto* document=projectContext->findSceneDocument(sceneManager.getCurrentSceneName())) {
            keys.clear();for (const auto& entity : document->at("entities")) keys.push_back(entity.at("key").get<std::string>());
        }
        for (const auto& key : keys) {
            const auto* t=scene.getRegistry().getComponent<components::TransformComponent>(scene.findEntityByKey(key));
            if (!t) continue;
            const auto p=t->getPosition(),s=t->getScale();
            result[key]={{"position",{{"x",p.x},{"y",p.y}}},{"rotation",t->getRotation()},{"scale",{{"x",s.x},{"y",s.y}}}};
        }
        return result;
    }

    nlohmann::json Runtime::liveHierarchy() const {
        auto rows = nlohmann::json::array();
        std::vector<entity::Entity> pending(scene.getRoots().rbegin(), scene.getRoots().rend());
        const auto& registry = scene.getRegistry();
        const auto& keys = sceneManager.getAuthoredEntityKeys();
        while (!pending.empty()) {
            const auto entity = pending.back();
            pending.pop_back();
            if (!registry.hasEntity(entity)) continue;
            const auto key = keys.find(entity.getID());
            nlohmann::json row = {{"id", entity.getID()}, {"parent", scene.getParent(entity).getID()},
                {"authoredKey", key == keys.end() ? nlohmann::json(nullptr) : nlohmann::json(key->second)}};
            if (const auto* name = registry.getComponent<detail::SceneEntityName>(entity))
                row["name"] = name->value;
            rows.push_back(std::move(row));
            const auto& children = scene.getChildren(entity);
            pending.insert(pending.end(), children.rbegin(), children.rend());
        }
        return {{"sceneInstance", sceneManager.getSceneInstance()}, {"entities", std::move(rows)}};
    }

    nlohmann::json Runtime::liveComponents(uint64_t sceneInstance, uint32_t entityId) const {
        const auto& registry = scene.getRegistry();
        const entity::Entity entity(entityId);
        if (sceneInstance != sceneManager.getSceneInstance() || !registry.hasEntity(entity))
            return nullptr;

        auto result = nlohmann::json::object();
        for (const auto* name : {"Transform", "SpriteRenderer"}) {
            auto snapshot = componentdata::descriptors().at(name).get(registry, entity);
            if (!snapshot.is_null()) result[name] = std::move(snapshot);
        }
        if (const auto* script = registry.getComponent<components::ScriptComponent>(entity)) {
            auto attachments = nlohmann::json::array();
            for (const auto& spec : detail::scriptSpecs(*script)) {
                const char* status = !script->enabled || !spec.enabled ? "Disabled" :
                    scriptEngine.hasBehaviour(entityId, spec.key) ? "Loaded" : "Not loaded";
                attachments.push_back({{"key", spec.key}, {"scriptAsset", spec.scriptAsset},
                    {"order", spec.order}, {"status", status},
                    {"canReload", script->enabled && spec.enabled &&
                        entity::isChunkActive(registry, entity, components::ChunkSubsystem::Scripts) &&
                        (projectContext || [&] {
                            if (editorAssetRoot.empty()) return false;
                            try {
                                (void)ProjectRuntime::resolveEditorSource(editorAssetRoot, std::filesystem::u8path(spec.scriptAsset));
                                return true;
                            } catch (const std::exception&) {
                                return false;
                            }
                        }())}});
            }
            result["Script"] = std::move(attachments);
        }
        return result;
    }

    void Runtime::reloadEmbeddedBehaviour(uint64_t sceneInstance, uint32_t entityId,
        const std::string& key, const std::string& asset)
    {
        if (!embedded || !initialized || entityPreview || (!projectContext && editorAssetRoot.empty()) ||
            sceneInstance != sceneManager.getSceneInstance())
            return;

        auto& registry = scene.getRegistry();
        const auto matches = [&](entity::Entity entity, const std::string& behaviour, const std::string& path) {
            const auto* script = registry.getComponent<components::ScriptComponent>(entity);
            if (!script || !script->enabled ||
                !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Scripts))
                return false;
            const auto specs = detail::scriptSpecs(*script);
            return std::any_of(specs.begin(), specs.end(), [&](const auto& spec) {
                return spec.key == behaviour && spec.enabled && spec.scriptAsset == path;
            });
        };
        if (!matches(entity::Entity(entityId), key, asset)) {
            Log::warn("Script reload skipped because the selected attachment changed");
            return;
        }

        std::filesystem::path sourcePath;
        try {
            sourcePath = projectContext ? std::filesystem::u8path(projectContext->resolveAsset(asset)) :
                ProjectRuntime::resolveEditorSource(editorAssetRoot, std::filesystem::u8path(asset));
        } catch (const std::exception& error) {
            Log::error("Script reload could not resolve {}: {}", asset, error.what());
            return;
        }
        const auto path = sourcePath.generic_string();
        std::ifstream file(sourcePath, std::ios::binary);
        if (!file) {
            Log::error("Could not open script for reload: {}", path);
            return;
        }
        std::string source;
        char buffer[4096];
        while (file.read(buffer, sizeof(buffer))) source.append(buffer, sizeof(buffer));
        source.append(buffer, static_cast<std::size_t>(file.gcount()));
        if (file.bad() || !file.eof()) {
            Log::error("Could not read script for reload: {}", path);
            return;
        }
        struct Target {
            entity::Entity entity;
            scripting::ScriptBehaviourSpec spec;
            uint64_t generation;
        };
        std::vector<Target> targets;
        registry.view<components::ScriptComponent>([&](auto entity, const auto& script) {
            if (!script.enabled || !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Scripts)) return;
            for (const auto& spec : detail::scriptSpecs(script)) {
                if (spec.enabled && (spec.scriptAsset == asset || spec.scriptAsset == path))
                    targets.push_back({entity, spec, scriptEngine.getBehaviourGeneration(entity.getID(), spec.key)});
            }
        });
        std::stable_sort(targets.begin(), targets.end(), [](const auto& a, const auto& b) {
            return std::tuple(a.spec.order, a.entity.getID()) < std::tuple(b.spec.order, b.entity.getID());
        });
        for (const auto& target : targets) {
            const auto id = target.entity.getID();
            const auto& spec = target.spec;
            using Reload = scripting::BehaviourReloadResult;
            Reload result = Reload::AttachmentChanged;
            if (scriptEngine.getBehaviourGeneration(id, spec.key) == target.generation)
                result = scriptEngine.reloadBehaviour(id, spec.key, spec.scriptAsset, source,
                    [&] { return matches(target.entity, spec.key, spec.scriptAsset); }, path);
            const auto label = spec.key.empty() ? "Primary" : spec.key;
            switch (result) {
            case Reload::Restarted:
                scriptSystem.recordBehaviourReload(id, spec.key, spec.scriptAsset, true);
                Log::info("Reloaded {} for entity {} ({})", path, id, label);
                break;
            case Reload::InitFailed:
                if (matches(target.entity, spec.key, spec.scriptAsset) && !scriptEngine.hasBehaviour(id, spec.key))
                    scriptSystem.recordBehaviourReload(id, spec.key, spec.scriptAsset, false);
                Log::warn("Reloaded {} for entity {} ({}) but initialization failed", path, id, label);
                break;
            case Reload::AttachmentChanged:
                Log::warn("Skipped reload of {} for entity {} ({}) because the attachment changed", path, id, label);
                break;
            case Reload::StageFailed:
                // The Lua diagnostic already records the source, entity and attachment.
                break;
            }
        }
    }

    Result<bool> Runtime::advanceEmbeddedFrame(float dt, bool allowDebugInput)
    {
        if (!embedded || !initialized)
            return Result<bool>::error("Embedded runtime is not initialized");
        if (!std::isfinite(dt) || dt < 0.0f)
            return Result<bool>::error("Embedded advance requires finite nonnegative dt");
        if (embeddedPaused) {
            if (!std::exchange(embeddedStepRequested, false)) return Result<bool>::success(true);
            dt = 1.0f / 60.0f;
            allowDebugInput = false;
        }
        processPendingSceneSwitch();
        if (allowDebugInput) handleDebugInput();
        (void)advanceSimulation(dt);
        sle::ui::UIFrameContext uiCtx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, camera, scriptEngine, dt};
        uiSystem.update(uiCtx, false);
        return Result<bool>::success(true);
    }

    Result<bool> Runtime::tickEmbeddedFrame(int framebufferWidth, int framebufferHeight, float dt)
    {
        if (framebufferWidth <= 0 || framebufferHeight <= 0)
            return Result<bool>::error("Embedded frame requires positive dimensions");
        const auto advanced=advanceEmbeddedFrame(dt);
        if (!advanced.ok()) return advanced;
        return renderEmbeddedFrame(framebufferWidth,framebufferHeight);
    }

    void Runtime::setEmbeddedPaused(bool paused)
    {
        if (!embedded || embeddedPaused == paused) return;
        embeddedPaused = paused;
        embeddedStepRequested = false;
        Input::releaseAll();
        audioSystem.setPaused(paused);
    }

    bool Runtime::requestEmbeddedStep()
    {
        if (!embedded || !initialized || !embeddedPaused || entityPreview) return false;
        embeddedStepRequested = true;
        return true;
    }

    void Runtime::processPendingSceneSwitch()
    {
        const std::string previousSceneName = sceneManager.getCurrentSceneName();
        bool errorReported = false;
        auto switchResult = sceneManager.processPendingSwitch(*this, scene, errorReported);
        if (!switchResult.ok())
        {
            if (!errorReported) Log::error("Failed to switch pending scene: {}", switchResult.error());
        }
        else if (previousSceneName != sceneManager.getCurrentSceneName())
        {
            refreshChunkRuntimeState();
        }
    }

    void Runtime::resetInputActions(const InputActionMap& actions)
    {
        activeInputActions = actions;
        inputActionStates.clear();
        for (const auto& [name, keys] : activeInputActions)
            inputActionStates.try_emplace(name);
        Input::releaseAll();
    }

    input::KeyState Runtime::inputAction(std::string_view name) const
    {
        const auto found = inputActionStates.find(name);
        return found == inputActionStates.end() ? input::KeyState{} : found->second;
    }

    bool Runtime::isActionKeyBound(int key) const
    {
        return std::ranges::any_of(activeInputActions, [key](const auto& action) {
            return std::ranges::find(action.second, key) != action.second.end();
        });
    }

    void Runtime::sampleInputActions()
    {
        for (const auto& [name, keys] : activeInputActions)
        {
            auto& state = inputActionStates.at(name);
            const bool down = std::ranges::any_of(keys, Input::isKeyDown);
            state = {down, down && !state.down, !down && state.down};
        }
    }

    void Runtime::handleDebugInput()
    {
        if (gameViewOverride) return;
        if (!isActionKeyBound(GLFW_KEY_F3) && Input::isKeyPressed(GLFW_KEY_F3))
        {
            const bool enabled = !renderSystem.isPhysicsDebugEnabled();
            renderSystem.setPhysicsDebugEnabled(enabled);
            uiSystem.setDocumentVisible(debugFpsDocumentIndex, enabled);
            Log::info("Physics debug rendering: {}", enabled ? "ON" : "OFF");
        }
    }

    Runtime::FrameTimings Runtime::advanceSimulation(float dt)
    {
        using Clock = std::chrono::high_resolution_clock;
        using Ms = std::chrono::duration<double, std::milli>;
        sampleInputActions();
        frameDeltaTime = dt;
        fpsUiAccumulator += dt;
        ++fpsUiFrameCount;

        if (fpsUiAccumulator >= 1.0f)
        {
            const int fpsUi = static_cast<int>(std::lround(static_cast<float>(fpsUiFrameCount) / std::max(fpsUiAccumulator, 0.0001f)));
            uiSystem.setBinding("fpsText", std::to_string(fpsUi));
            fpsUiAccumulator = 0.0f;
            fpsUiFrameCount = 0;
        }
        // Construct per-frame context with all system dependencies.
        Context ctx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, camera, physicsWorld.get(), dt};

        // === FRAME START ===
        // Flush queued events from previous frame.
        ctx.eventBus.flushQueue();
        ctx.globalBus.flushQueue();

        // === LOGIC UPDATE PHASE ===
        // 0. Update chunk activation before simulation-heavy systems so
        // inactive chunk residents can skip work in the same frame.
        chunkRuntimeState.update(scene, camera.getPosition());

        // Advance animation before scripts and physics consume local transforms.
        animationSystem.update(scene, dt);

        // 1b. Process audio play/stop requests.
        audioSystem.update(scene);

        auto t1 = Clock::now();

        // Scripts can edit local transforms before physics synchronization.
        scriptSystem.update(ctx);
        auto t2 = Clock::now();

        // Update state machines after scripts mutate parameters/triggers.
        stateMachineSystem.update(scene, dt, &scriptEngine);

        // Consume dirty local transforms, step physics, and write back results.
        if (!scene.getTileLayers().empty() || !scene.getTilemapPlacements().empty()) {
            auto terrainCamera = camera;
            CameraSystem::updateFromAuthoredTransforms(scene, terrainCamera);
            tileCollisionSystem->update(scene, terrainCamera);
        } else {
            tileCollisionSystem->clear();
        }
        physicsSystem.update(ctx);
        auto t3 = Clock::now();

        // Resolve the final pose after all simulation writes. Doing this earlier
        // clears dirty flags before physics and leaves rendering one frame behind.
        transformSystem.update(ctx);
        componentCameraActive=CameraSystem::update(scene.getRegistry(),camera);
        auto tTransform = Clock::now();
        return {Ms(tTransform - t3).count(), Ms(t2 - t1).count(), Ms(t3 - t2).count(), 0.0, 0.0};
    }

    Runtime::FrameTimings Runtime::advanceAndRender(float dt)
    {
        using Clock = std::chrono::high_resolution_clock;
        using Ms = std::chrono::duration<double, std::milli>;
        auto timings=advanceSimulation(dt);
        const auto tTransform=Clock::now();
        // === RENDER PHASE ===
        auto& displayCamera=sceneViewOverride ? editorCamera : camera;
        renderer.setCamera(&displayCamera);
        configureRenderViewport();
        Context renderCtx{scene,scene.getRegistry(),scene.getEventBus(),globalBus_,renderer,displayCamera,physicsWorld.get(),dt};
        renderer.beginFrame();

        // Submit sprite renders for all entities with transforms.
        renderSystem.update(renderCtx);
        if (sceneViewOverride) EntityPreviewSystem::submitOverlay(scene,renderer,editorCamera,editorGrid);
        sle::ui::UIFrameContext uiCtx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, displayCamera, scriptEngine, dt};
        uiSystem.update(uiCtx);
        auto t4 = Clock::now();

        renderer.endFrame();
        auto t5 = Clock::now();
        timings.renderSubmit=Ms(t4 - tTransform).count();
        timings.renderFlush=Ms(t5 - t4).count();
        return timings;
    }

    Result<bool> Runtime::initServices()
    {
        auto rendererResult = renderer.init();
        if (!rendererResult.ok())
            return Result<bool>::error(rendererResult.error());
        renderer.setClearColor(config.backgroundColor);

        defaultQuadShader = Resources::create<Shader>(
            "default_quad_shader",
            (std::filesystem::path(config.engineAssetRoot)/"shaders/quad.vert").generic_string(),
            (std::filesystem::path(config.engineAssetRoot)/"shaders/quad.frag").generic_string());
        if (!defaultQuadShader)
        {
            return Result<bool>::error("Failed to load default quad shader resource");
        }

        defaultDebugShader = Resources::create<Shader>(
            "default_debug_shader",
            (std::filesystem::path(config.engineAssetRoot)/"shaders/debug.vert").generic_string(),
            (std::filesystem::path(config.engineAssetRoot)/"shaders/debug.frag").generic_string());
        if (!defaultDebugShader)
        {
            return Result<bool>::error("Failed to load default debug shader resource");
        }

        renderSystem.setDefaultShaderID(defaultQuadShader->getID());
        renderSystem.setDefaultTextureID(0);
        if (embedded) {
            constexpr unsigned char pixels[] = {
                255, 0, 255, 255, 32, 0, 32, 255,
                32, 0, 32, 255, 255, 0, 255, 255
            };
            renderer::TextureLoadOptions options;
            options.minFilter = options.magFilter = renderer::TextureFilter::Nearest;
            missingTileTexture = std::make_unique<renderer::Texture>();
            if (!missingTileTexture->loadFromRGBAData(2, 2, pixels, options))
                return Result<bool>::error("Failed to create missing tile texture");
            renderSystem.setMissingTileTextureID(missingTileTexture->getID());
        }
        renderSystem.setPhysicsDebugEnabled(false);
        renderer.setDebugShaderID(defaultDebugShader->getID());

        if (!scriptEngine.init(&scriptApi))
        {
            return Result<bool>::error("Failed to initialize ScriptEngine");
        }
        scriptSystem.setScriptRuntime(&scriptEngine);

        auto uiResult = uiSystem.init(&scriptEngine);
        if (!uiResult.ok())
        {
            return Result<bool>::error(uiResult.error());
        }
        uiSystem.setDefaultShaderID(defaultQuadShader->getID());

        {
            sle::ui::UIDocumentDescriptor descriptor;
            descriptor.layoutAsset = resolveAssetPath((std::filesystem::path(config.engineAssetRoot)/"ui/fps_hud.xml").generic_string());
            descriptor.fontAsset = resolveAssetPath((std::filesystem::path(config.engineAssetRoot)/"fonts/Roboto-Regular.ttf").generic_string());
            descriptor.spaceMode = sle::components::UISpaceMode::Screen;
            descriptor.visible = false;
            descriptor.layer = 1000;
            uiSystem.createDocument(std::move(descriptor));
            debugFpsDocumentIndex = uiSystem.getDocuments().size() - 1;
        }

        Input::init(embedded ? nullptr : window.getNative());

        renderer.setCamera(&camera);

        // Initialize physics world with default gravity
        physicsWorld = std::make_unique<sle::physics::PhysicsWorld>(glm::vec2(0.0f, -9.81f));
        physicsWorld->setFixedTimestep(1.0f / 120.0f);
        tileCollisionSystem = std::make_unique<TileCollisionSystem>(*physicsWorld);

        // Audio init is best-effort; failure is non-fatal (headless CI, no audio device, etc.)
        if (initializeAudioOnInit)
        {
            audioAttempted = true;
            audioSystem.init();
        }
        initialized = true;

        return Result<bool>::success(true);
    }

    void Runtime::run()
    {
        (void)runMainLoop(false, 0);
    }

    std::size_t Runtime::runForFrames(std::size_t maxFrames)
    {
        return runMainLoop(true, maxFrames);
    }

    std::size_t Runtime::runMainLoop(bool bounded, std::size_t maxFrames)
    {
        if (embedded || !initialized) return 0;
        using Clock = std::chrono::high_resolution_clock;
        using Ms = std::chrono::duration<double, std::milli>;

        float fpsLogAccumulator = 0.0f;
        int fpsFrameCount = 0;
        double transformMsAccum = 0.0;
        double scriptMsAccum = 0.0;
        double physicsMsAccum = 0.0;
        double renderSubmitMsAccum = 0.0;
        double renderFlushMsAccum = 0.0;
        double swapMsAccum = 0.0;

        std::size_t executedFrames = 0;

        while (!window.shouldClose() && (!bounded || executedFrames < maxFrames))
        {
            const auto frameStarted=std::chrono::steady_clock::now();
            processPendingSceneSwitch();

            Input::update();

            timer.tick();

            float dt = timer.getDeltaTime();
            fpsLogAccumulator += dt;
            ++fpsFrameCount;
            window.pollEvents();

            if (window.consumeResize() && !componentCameraActive)
            {
                camera.setViewport(
                    static_cast<float>(window.getWidth()),
                    static_cast<float>(window.getHeight()));
            }

            handleDebugInput();

            if (!isActionKeyBound(GLFW_KEY_ESCAPE) && Input::isKeyDown(GLFW_KEY_ESCAPE))
                break;

            hostWidth=window.getWidth(); hostHeight=window.getHeight();
            const auto timings = advanceAndRender(dt);
            auto t5 = Clock::now();

            window.swapBuffers();
            auto t6 = Clock::now();

            transformMsAccum += timings.transform;
            scriptMsAccum += timings.script;
            physicsMsAccum += timings.physics;
            renderSubmitMsAccum += timings.renderSubmit;
            renderFlushMsAccum += timings.renderFlush;
            swapMsAccum += Ms(t6 - t5).count();

            if (fpsLogAccumulator >= 1.0f)
            {
                const float fps = static_cast<float>(fpsFrameCount) / fpsLogAccumulator;
                const double frameCount = static_cast<double>(fpsFrameCount);
                sle::core::Log::info(
                    "FPS: {:.2f} | avg ms: transform {:.3f}, script {:.3f}, physics {:.3f}, render-submit {:.3f}, render-flush {:.3f}, swap {:.3f}",
                    fps,
                    transformMsAccum / frameCount,
                    scriptMsAccum / frameCount,
                    physicsMsAccum / frameCount,
                    renderSubmitMsAccum / frameCount,
                    renderFlushMsAccum / frameCount,
                    swapMsAccum / frameCount);
                fpsLogAccumulator = 0.0f;
                fpsFrameCount = 0;
                transformMsAccum = 0.0;
                scriptMsAccum = 0.0;
                physicsMsAccum = 0.0;
                renderSubmitMsAccum = 0.0;
                renderFlushMsAccum = 0.0;
                swapMsAccum = 0.0;
            }

            if (config.targetFPS>0) std::this_thread::sleep_until(frameStarted+std::chrono::duration<double>(1.0/config.targetFPS));
            ++executedFrames;
        }

        return executedFrames;
    }

} // namespace sle
