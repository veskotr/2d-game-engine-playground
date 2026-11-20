#include "EditorLogQueue.hpp"
#include <glad/gl.h>
#include <jni.h>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/GameConfig.hpp>
#include <sle/core/Version.hpp>
#include <map>
#include <sle/engine/EntityAuthoring.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/scene/TransformMath.hpp>
#include <sle/scripting/EditorLuaHost.hpp>

#include <memory>
#include <mutex>
#include <stdexcept>
#include <thread>
#include <vector>
#include <cmath>
#include <chrono>
#include <iostream>

namespace {
// Resources and Input are process-wide today; do not allow concurrent runtimes.
sle::editor::EditorLogQueue editorLogs;
std::mutex sessionMutex;
std::unique_ptr<sle::Runtime> session;
std::thread::id owner;
jlong currentToken = 0;
jlong nextToken = 1;
struct InputEvent { int type, code; double x, y; };
std::vector<InputEvent> pendingInput;
bool previewMode = false;
bool fitRequested = false;
bool scenePlaying = false;

// Lua runs synchronously on the owner thread. Capture the first diagnostic
// independently of the bounded Console queue and ignore other producers.
thread_local std::optional<sle::core::LogEntry>* frameLuaError = nullptr;

struct LuaErrorPause
{
    std::optional<sle::core::LogEntry> firstError;
    std::optional<sle::core::LogEntry>* previous;

    explicit LuaErrorPause(bool enabled)
        : previous(std::exchange(frameLuaError, enabled ? &firstError : nullptr)) {}
    ~LuaErrorPause() { frameLuaError = previous; }

    jbyteArray finish(JNIEnv* env)
    {
        if (!firstError) return nullptr;
        session->setEmbeddedPaused(true);
        pendingInput.clear();
        const nlohmann::json error = {{"message", firstError->message}, {"entity", firstError->lua->entity}};
        const auto text = error.dump(-1, ' ', false, nlohmann::json::error_handler_t::replace);
        const auto result = env->NewByteArray(static_cast<jsize>(text.size()));
        if (result)
            env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
                                    reinterpret_cast<const jbyte*>(text.data()));
        return result;
    }
};

void fail(JNIEnv* env, const char* message)
{
    if (env->ExceptionCheck()) return;
    const auto type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, message);
}

void validate(jlong token)
{
    if (!session || token != currentToken)
        throw std::runtime_error("Invalid or closed engine session");
    if (owner != std::this_thread::get_id())
        throw std::runtime_error("Engine calls must use the viewport context thread");
}

void drainInput()
{
    sle::input::Input::update();
    for (const auto& event : pendingInput)
    {
        switch (event.type)
        {
        case 0: sle::input::Input::setKeyState(event.code, event.x != 0); break;
        case 1: sle::input::Input::setMouseButtonState(event.code, event.x != 0); break;
        case 2: sle::input::Input::setCursorPosition(event.x, event.y); break;
        case 3: sle::input::Input::releaseAll(); break;
        }
    }
    pendingInput.clear();
}

template<class Action>
void guarded(JNIEnv* env, Action action)
{
    try
    {
        std::lock_guard lock(sessionMutex);
        action();
    }
    catch (const std::exception& error) { fail(env, error.what()); }
    catch (...) { fail(env, "Unknown native engine failure"); }
}
}

// Authoring metadata/validation need no viewport, GL context, or Lua VM.
extern "C" JNIEXPORT jstring JNICALL Java_sle_editor_bridge_NativeEngine_componentCatalog(JNIEnv* env, jobject)
{
    jstring result = nullptr;
    guarded(env, [&] { result = env->NewStringUTF(sle::EntityAuthoring::componentCatalog().dump().c_str()); });
    return result;
}

// Version of the engine compiled into this bridge (and of its matching runtime bundle).
extern "C" JNIEXPORT jstring JNICALL Java_sle_editor_bridge_NativeEngine_engineVersion(JNIEnv* env, jobject)
{
    return env->NewStringUTF(sle::core::EngineVersion::text);
}

namespace {
std::string readDiscoveryInput(JNIEnv* env, jbyteArray bytes)
{
    if (!bytes) throw std::invalid_argument("Missing behavior discovery input");
    const auto size = env->GetArrayLength(bytes);
    if (size > 256 * 1024) throw std::invalid_argument("Behavior input exceeds the size limit");
    std::string text(static_cast<std::size_t>(size), '\0');
    if (size != 0)
        env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte*>(text.data()));
    return text;
}

jbyteArray discoveryOutput(JNIEnv* env, const std::string& text)
{
    const auto size = static_cast<jsize>(text.size());
    const auto result = env->NewByteArray(size);
    if (result) env->SetByteArrayRegion(result, 0, size, reinterpret_cast<const jbyte*>(text.data()));
    return result;
}
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_resolveStandaloneAsset(
    JNIEnv* env, jobject, jbyteArray asset)
{
    try
    {
        const auto path = readDiscoveryInput(env, asset);
        if (env->ExceptionCheck()) return nullptr;
        return discoveryOutput(env, sle::EntityAuthoring::resolveStandaloneAsset(path));
    }
    catch (const std::exception& error) { fail(env, error.what()); }
    catch (...) { fail(env, "Unknown asset resolution failure"); }
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_behaviourSchema(
    JNIEnv* env, jobject, jbyteArray source, jbyteArray filename)
{
    try
    {
        const auto bytes = readDiscoveryInput(env, source);
        const auto path = readDiscoveryInput(env, filename);
        if (env->ExceptionCheck()) return nullptr;
        const auto schema = sle::scripting::inspectBehaviourSource(bytes, path);
        const nlohmann::json response = {
            {"fields", schema.fields}, {"defaults", schema.defaults}, {"error", schema.error},
        };
        return discoveryOutput(env, response.dump(-1, ' ', false, nlohmann::json::error_handler_t::replace));
    }
    catch (const std::exception& error) { fail(env, error.what()); }
    catch (...) { fail(env, "Unknown behavior discovery failure"); }
    return nullptr;
}

namespace {
jbyteArray runEditorLua(JNIEnv* env, jbyteArray source, jbyteArray snapshot, jbyteArray filename = nullptr,
    jbyteArray mode = nullptr, jbyteArray commandId = nullptr)
{
    jbyteArray output = nullptr;
    guarded(env, [&] {
        const auto command = readDiscoveryInput(env, source);
        if (env->ExceptionCheck()) return;
        if (!snapshot || env->GetArrayLength(snapshot) > 4 * 1024 * 1024)
            throw std::invalid_argument("Editor Lua snapshot exceeds the size limit");
        std::string text(static_cast<std::size_t>(env->GetArrayLength(snapshot)), '\0');
        env->GetByteArrayRegion(snapshot, 0, static_cast<jsize>(text.size()), reinterpret_cast<jbyte*>(text.data()));
        if (env->ExceptionCheck()) return;
        const auto context = nlohmann::json::parse(text);
        sle::scripting::EditorLuaConsoleResult result;
        if (mode)
        {
            const auto path = readDiscoveryInput(env, filename);
            const auto operation = readDiscoveryInput(env, mode);
            const auto id = readDiscoveryInput(env, commandId);
            if (env->ExceptionCheck()) return;
            result = sle::scripting::runEditorLuaCommands(command, path, context, operation, id);
        }
        else result = sle::scripting::runEditorLuaConsole(command, context);
        const nlohmann::json response = {{"edits", result.edits}, {"output", result.output}, {"error", result.error}};
        output = discoveryOutput(env, response.dump(-1, ' ', false, nlohmann::json::error_handler_t::replace));
    });
    return output;
}
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_runEditorLua(
    JNIEnv* env, jobject, jbyteArray source, jbyteArray snapshot)
{
    return runEditorLua(env, source, snapshot);
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_runProjectEditorLua(
    JNIEnv* env, jobject, jbyteArray source, jbyteArray snapshot, jbyteArray filename, jbyteArray mode, jbyteArray commandId)
{
    return runEditorLua(env, source, snapshot, filename, mode, commandId);
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_validatePrefab(JNIEnv* env, jobject, jbyteArray source)
{
    guarded(env, [&] {
        if (!source) throw std::runtime_error("Missing prefab JSON");
        std::string text(static_cast<std::size_t>(env->GetArrayLength(source)), '\0');
        env->GetByteArrayRegion(source, 0, static_cast<jsize>(text.size()), reinterpret_cast<jbyte*>(text.data()));
        if (env->ExceptionCheck()) return;
        const auto validated = sle::EntityAuthoring::validatePrefab(nlohmann::json::parse(text));
        if (!validated.ok()) throw std::runtime_error(validated.error());
    });
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_canonicalPrefab(JNIEnv* env, jobject, jbyteArray source)
{
    jbyteArray result = nullptr;
    guarded(env, [&] {
        if (!source) throw std::runtime_error("Missing prefab JSON");
        std::string text(static_cast<std::size_t>(env->GetArrayLength(source)), '\0');
        env->GetByteArrayRegion(source, 0, static_cast<jsize>(text.size()), reinterpret_cast<jbyte*>(text.data()));
        if (env->ExceptionCheck()) return;
        const auto canonical = sle::EntityAuthoring::canonicalPrefab(nlohmann::json::parse(text)).dump();
        result = env->NewByteArray(static_cast<jsize>(canonical.size()));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(canonical.size()), reinterpret_cast<const jbyte*>(canonical.data()));
    });
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_canonicalScene(JNIEnv* env, jobject, jbyteArray source)
{
    jbyteArray result = nullptr;
    guarded(env, [&] {
        if (!source) throw std::runtime_error("Missing scene JSON");
        std::string text(static_cast<std::size_t>(env->GetArrayLength(source)), '\0');
        env->GetByteArrayRegion(source, 0, static_cast<jsize>(text.size()), reinterpret_cast<jbyte*>(text.data()));
        if (env->ExceptionCheck()) return;
        const auto canonical = sle::SceneAuthoring::canonicalScene(nlohmann::json::parse(text)).dump();
        result = env->NewByteArray(static_cast<jsize>(canonical.size()));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(canonical.size()), reinterpret_cast<const jbyte*>(canonical.data()));
    });
    return result;
}

extern "C" JNIEXPORT jlong JNICALL Java_sle_editor_bridge_NativeEngine_create(JNIEnv* env, jobject)
{
    jlong token = 0;
    guarded(env, [&] {
        if (session) throw std::runtime_error("Only one embedded engine session is supported");
        if (!gladLoaderLoadGL()) throw std::runtime_error("Cannot load OpenGL from the viewport context");
        sle::core::EngineConfig config;
        config.width = 480;
        config.height = 270;
        const auto started = std::chrono::steady_clock::now();
        sle::core::Log::setSink([](const auto& entry) {
            if (frameLuaError && !*frameLuaError && entry.lua && entry.level == sle::core::LogLevel::Error)
                *frameLuaError = entry;
            editorLogs.push(entry);
        });
        struct CaptureOnSuccess
        {
            jlong& token;
            ~CaptureOnSuccess() { if (!token) sle::core::Log::setSink({}); }
        } capture{token};
        auto candidate = std::make_unique<sle::Runtime>(config);
        const auto initialized = candidate->initEmbedded(false);
        if (!initialized.ok()) throw std::runtime_error(initialized.error());
        candidate->registerSceneFromFile("sandbox", "assets/scenes/npc_zone_demo.json");
        candidate->setPhysicsDebugEnabled(true);
        session = std::move(candidate);
        owner = std::this_thread::get_id();
        currentToken = nextToken++;
        token = currentToken;
        previewMode = false;
        scenePlaying = false;
        std::cerr << ("EDITOR_GRAPHICS_READY_MS=" + std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now()-started).count()) + "\n");
    });
    return token;
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_render(
    JNIEnv* env, jobject, jlong token, jint width, jint height, jfloat dt)
{
    guarded(env, [&] {
        validate(token);
        drainInput();
        const auto rendered = previewMode ? session->renderEntityPreview(width,height,fitRequested)
            : session->tickEmbeddedFrame(width, height, dt);
        fitRequested = false;
        if (!rendered.ok()) throw std::runtime_error(rendered.error());
    });
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_loadPreview(
    JNIEnv* env, jobject, jlong token, jbyteArray source, jboolean preserveView)
{
    guarded(env, [&] {
        validate(token);
        if (!source) throw std::runtime_error("Missing entity preview document");
        std::string text(static_cast<std::size_t>(env->GetArrayLength(source)), '\0');
        env->GetByteArrayRegion(source,0,static_cast<jsize>(text.size()),reinterpret_cast<jbyte*>(text.data()));
        if (env->ExceptionCheck()) return;
        const bool preserve = previewMode && preserveView != JNI_FALSE;
        const auto loaded = session->loadEntityPreview(nlohmann::json::parse(text), preserve);
        if (!loaded.ok()) throw std::runtime_error(loaded.error());
        previewMode = true;
        scenePlaying = false;
        fitRequested = !preserve;
        pendingInput.clear();
    });
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_setEditorProjectSettings(
    JNIEnv* env, jclass, jlong token, jbyteArray source, jbyteArray settings)
{
    guarded(env,[&] {
        validate(token);
        if (!source || !settings) throw std::invalid_argument("Missing editor asset settings");
        std::string root(static_cast<std::size_t>(env->GetArrayLength(source)), '\0');
        if (!root.empty()) env->GetByteArrayRegion(source, 0, static_cast<jsize>(root.size()),
            reinterpret_cast<jbyte*>(root.data()));
        if (env->ExceptionCheck()) return;
        std::string json(static_cast<std::size_t>(env->GetArrayLength(settings)), '\0');
        if (!json.empty()) env->GetByteArrayRegion(settings, 0, static_cast<jsize>(json.size()),
            reinterpret_cast<jbyte*>(json.data()));
        if (env->ExceptionCheck()) return;
        session->setEditorProjectSettings(std::filesystem::u8path(root), nlohmann::json::parse(json));
    });
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_loadScenePreview(
    JNIEnv* env, jobject, jlong token, jbyteArray source, jboolean preserveView)
{
    guarded(env, [&] {
        validate(token);
        if (!source) throw std::runtime_error("Missing entity preview document");
        std::string text(static_cast<std::size_t>(env->GetArrayLength(source)), '\0');
        env->GetByteArrayRegion(source,0,static_cast<jsize>(text.size()),reinterpret_cast<jbyte*>(text.data()));
        if (env->ExceptionCheck()) return;
        const bool preserve = previewMode && preserveView != JNI_FALSE;
        const auto loaded = session->loadScenePreview(nlohmann::json::parse(text), preserve);
        if (!loaded.ok()) throw std::runtime_error(loaded.error());
        previewMode = true;
        scenePlaying = false;
        fitRequested = !preserve;
        pendingInput.clear();
    });
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_loadSandbox(JNIEnv* env, jobject, jlong token)
{
    guarded(env, [&] {
        validate(token);
        const auto started = std::chrono::steady_clock::now();
        const auto loaded = session->loadScene("sandbox");
        if (!loaded.ok()) throw std::runtime_error(loaded.error());
        previewMode = false;
        scenePlaying = false;
        session->setEmbeddedPaused(false);
        pendingInput.clear();
        std::cerr << ("EDITOR_SANDBOX_LOAD_MS=" + std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now()-started).count()) + "\n");
    });
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_previewCamera(
    JNIEnv* env, jobject, jlong token, jfloat zoom, jboolean fit)
{
    guarded(env, [&] {
        validate(token);
        session->zoomEntityPreview(zoom);
        fitRequested = fitRequested || fit != JNI_FALSE;
    });
}

extern "C" JNIEXPORT jfloatArray JNICALL Java_sle_editor_bridge_NativeEngine_previewView(JNIEnv* env, jobject, jlong token)
{
    jfloatArray result = nullptr;
    guarded(env, [&] {
        validate(token);
        const auto position = session->getPreviewPosition();
        const auto size = session->getPreviewViewportSize();
        const jfloat values[] = {position.x,position.y,size.x,size.y};
        result = env->NewFloatArray(4);
        if (result) env->SetFloatArrayRegion(result,0,4,values);
    });
    return result;
}

// Snapshot immediately after a Game draw; presenters must not read mutable globals.
extern "C" JNIEXPORT jfloatArray JNICALL Java_sle_editor_bridge_NativeEngine_gameCamera(JNIEnv* env, jobject, jlong token)
{
    jfloatArray result = nullptr;
    guarded(env, [&] {
        validate(token);
        const auto position = session->getCameraPosition();
        const auto size = session->getCameraViewportSize();
        const jfloat values[] = {position.x,position.y,size.x,size.y,session->getCameraRotation(),session->getCameraZoom()};
        result = env->NewFloatArray(6);
        if (result) env->SetFloatArrayRegion(result,0,6,values);
    });
    return result;
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_enableAudio(JNIEnv* env, jobject, jlong token)
{
    guarded(env, [&] {
        validate(token);
        if (previewMode && !scenePlaying) return;
        const auto started=std::chrono::steady_clock::now();
        session->enableEmbeddedAudio();
        std::cerr << ("EDITOR_AUDIO_READY_MS=" + std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now()-started).count()) + "\n");
    });
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_input(
    JNIEnv* env, jobject, jlong token, jint type, jint code, jdouble x, jdouble y)
{
    guarded(env, [&] {
        validate(token);
        if (type < 0 || type > 3 || !std::isfinite(x) || !std::isfinite(y))
            throw std::runtime_error("Invalid viewport input event");
        pendingInput.push_back({type, code, x, y});
    });
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_pause(
    JNIEnv* env, jobject, jlong token, jboolean paused)
{
    guarded(env, [&] {
        validate(token);
        pendingInput.clear();
        session->setEmbeddedPaused(paused != JNI_FALSE);
    });
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_destroy(JNIEnv* env, jobject, jlong token)
{
    guarded(env, [&] {
        validate(token);
        session.reset(); // Host still has the context current here.
        sle::core::Log::setSink({});
        pendingInput.clear();
        currentToken = 0;
        owner = {};
    });
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_startScenePlay(
    JNIEnv* env,jobject,jlong token,jbyteArray source,jboolean pauseOnError) {
    jbyteArray error = nullptr;
    guarded(env,[&] {
        validate(token);
        if (!source) throw std::runtime_error("Missing scene play document");
        std::string text(static_cast<std::size_t>(env->GetArrayLength(source)),'\0');
        env->GetByteArrayRegion(source,0,static_cast<jsize>(text.size()),reinterpret_cast<jbyte*>(text.data()));
        if (env->ExceptionCheck()) return;
        LuaErrorPause capture(pauseOnError);
        const auto loaded=session->startScenePlay(nlohmann::json::parse(text));
        if (!loaded.ok()) throw std::runtime_error(loaded.error());
        previewMode=false; scenePlaying=true; pendingInput.clear();
        error = capture.finish(env);
    });
    return error;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_advanceFrame(
    JNIEnv* env,jobject,jlong token,jfloat dt,jboolean step,jboolean pauseOnError) {
    jbyteArray error = nullptr;
    guarded(env,[&] {
        validate(token);
        if (!std::isfinite(dt) || dt<0) throw std::runtime_error("Invalid frame dt");
        drainInput();
        if (step && scenePlaying) session->requestEmbeddedStep();
        LuaErrorPause capture(scenePlaying && pauseOnError);
        const auto advanced=session->advanceEmbeddedFrame(dt, !scenePlaying);
        if (!advanced.ok()) throw std::runtime_error(advanced.error());
        error = capture.finish(env);
    });
    return error;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_runPlayLua(
    JNIEnv* env, jobject, jlong token, jbyteArray source, jboolean pauseOnError)
{
    jbyteArray error = nullptr;
    guarded(env, [&] {
        validate(token);
        if (!scenePlaying) throw std::runtime_error("The Play runtime has stopped");
        const auto command = readDiscoveryInput(env, source);
        if (env->ExceptionCheck()) return;
        LuaErrorPause capture(pauseOnError);
        session->getScriptEngine().runConsoleSource(command);
        error = capture.finish(env);
    });
    return error;
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_setLuaProfiling(
    JNIEnv* env, jobject, jlong token, jboolean enabled)
{
    guarded(env, [&] {
        validate(token);
        session->getScriptEngine().setProfiling(enabled != JNI_FALSE);
    });
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_takeLuaProfile(
    JNIEnv* env, jobject, jlong token, jboolean advanced)
{
    jbyteArray result = nullptr;
    guarded(env, [&] {
        validate(token);
        auto& script = session->getScriptEngine();
        if (!scenePlaying || !script.isProfiling() || (!advanced && !script.hasProfileActivity())) return;
        const auto frame = script.finishProfileFrame();
        auto rows = nlohmann::json::array();
        for (const auto& row : frame.rows)
        {
            rows.push_back({{"entity", row.entity}, {"behaviour", row.behaviour}, {"source", row.source},
                {"calls", row.calls}, {"elapsed", row.elapsedNs}, {"allocated", row.allocatedBytes},
                {"freed", row.freedBytes}});
        }
        const nlohmann::json sample = {{"scene", session->getSceneManager().getSceneInstance()},
            {"elapsed", frame.elapsedNs}, {"gc", frame.explicitGcNs}, {"memory", frame.memoryBytes},
            {"rows", std::move(rows)}, {"omittedRows", frame.omittedRows}};
        const auto text = sample.dump(-1, ' ', false, nlohmann::json::error_handler_t::replace);
        result = env->NewByteArray(static_cast<jsize>(text.size()));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
            reinterpret_cast<const jbyte*>(text.data()));
    });
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL Java_sle_editor_bridge_NativeEngine_playEntityPosition(
    JNIEnv* env,jobject,jlong token,jlong id) {
    jfloatArray result = nullptr;
    guarded(env,[&] {
        validate(token);
        if (!scenePlaying || id <= 0 || id > UINT32_MAX) return;
        // Runtime IDs are reserved across scene replacement and are never reused within this session.
        const auto& scene = session->getScene();
        const sle::entity::Entity entity(static_cast<uint32_t>(id));
        if (!scene.getRegistry().getComponent<sle::components::TransformComponent>(entity)) return;
        const auto world = sle::entity::transformMath::currentWorld(scene, entity);
        const jfloat values[] = {world.position.x, world.position.y};
        result = env->NewFloatArray(2);
        if (result) env->SetFloatArrayRegion(result, 0, 2, values);
    });
    return result;
}
extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_drawEmbeddedFrame(
    JNIEnv* env,jobject,jlong token,jint width,jint height) {
    guarded(env,[&] {
        validate(token);
        const auto rendered=session->renderEmbeddedFrame(width,height);
        if (!rendered.ok()) throw std::runtime_error(rendered.error());
    });
}
extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_drawSceneView(
    JNIEnv* env,jobject,jlong token,jint width,jint height,jboolean gameView,jboolean fit) {
    guarded(env,[&] {
        validate(token);
        const bool isGame=gameView!=JNI_FALSE;
        const auto rendered=session->drawSceneView(width,height,isGame,fit!=JNI_FALSE || (!isGame && fitRequested));
        if (!rendered.ok()) throw std::runtime_error(rendered.error());
        if (!isGame) fitRequested=false;
    });
}
extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_renderSceneView(
    JNIEnv* env,jobject,jlong token,jint width,jint height,jfloat dt,jboolean gameView,jboolean playing) {
    guarded(env,[&] {
        validate(token);
        drainInput();
        const auto rendered=session->renderSceneView(width,height,dt,gameView!=JNI_FALSE,playing!=JNI_FALSE,fitRequested);
        fitRequested=false;
        if (!rendered.ok()) throw std::runtime_error(rendered.error());
    });
}
extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_panPreview(
    JNIEnv* env,jobject,jlong token,jfloat dx,jfloat dy) {
    guarded(env,[&] { validate(token); session->panEntityPreview({dx,dy}); });
}
// Editor grid spacing and major-line interval; affects Scene/Entity preview drawing only.
extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_setEditorGrid(
    JNIEnv* env,jobject,jlong token,jfloat spacing,jint majorEvery) {
    guarded(env,[&] {
        validate(token);
        if (!session->setEditorGrid({spacing,majorEvery})) throw std::runtime_error("Invalid editor grid");
    });
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_drainConsole(
    JNIEnv* env, jobject, jlong token)
{
    jbyteArray result = nullptr;
    guarded(env, [&] {
        validate(token);
        const auto batch = editorLogs.drain();
        if (!batch) return;
        const auto text = batch->dump(-1, ' ', false, nlohmann::json::error_handler_t::replace);
        result = env->NewByteArray(static_cast<jsize>(text.size()));
        if (result)
            env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
                                    reinterpret_cast<const jbyte*>(text.data()));
    });
    return result;
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_clearConsole(
    JNIEnv* env, jobject, jlong token)
{
    guarded(env, [&] { validate(token); editorLogs.clear(); });
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_sceneTransforms(JNIEnv* env,jobject,jlong token) {
    jbyteArray result=nullptr;
    guarded(env,[&] {
        validate(token);
        const auto text=session->sceneTransforms().dump();
        result=env->NewByteArray(static_cast<jsize>(text.size()));
        if(result) env->SetByteArrayRegion(result,0,static_cast<jsize>(text.size()),reinterpret_cast<const jbyte*>(text.data()));
    });
    return result;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_reloadScript(
    JNIEnv* env, jobject, jlong token, jlong sceneInstance, jlong entityId, jbyteArray keyBytes,
    jbyteArray assetBytes, jboolean pauseOnError)
{
    jbyteArray error = nullptr;
    guarded(env, [&] {
        validate(token);
        if (!scenePlaying || entityId <= 0 || entityId > UINT32_MAX || !keyBytes || !assetBytes) return;
        const auto read = [&](jbyteArray bytes) {
            std::string value(static_cast<std::size_t>(env->GetArrayLength(bytes)), '\0');
            if (!value.empty())
                env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(value.size()),
                    reinterpret_cast<jbyte*>(value.data()));
            return value;
        };
        const auto key = read(keyBytes);
        const auto asset = read(assetBytes);
        if (env->ExceptionCheck()) return;
        LuaErrorPause capture(pauseOnError);
        session->reloadEmbeddedBehaviour(sceneInstance, static_cast<uint32_t>(entityId), key, asset);
        error = capture.finish(env);
    });
    return error;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_liveSnapshot(
    JNIEnv* env, jobject, jlong token, jlong sceneInstance, jlong entityId)
{
    jbyteArray result = nullptr;
    guarded(env, [&] {
        validate(token);
        nlohmann::json snapshot;
        if (scenePlaying) {
            snapshot = session->liveHierarchy();
            snapshot["run"] = token;
            if (entityId > 0 && entityId <= UINT32_MAX)
                snapshot["details"] = session->liveComponents(sceneInstance, static_cast<uint32_t>(entityId));
        }
        const auto bytes = snapshot.dump(-1, ' ', false, nlohmann::json::error_handler_t::replace);
        result = env->NewByteArray(static_cast<jsize>(bytes.size()));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(bytes.size()),
            reinterpret_cast<const jbyte*>(bytes.data()));
    });
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL Java_sle_editor_bridge_NativeEngine_sceneGameViewport(JNIEnv* env,jobject,jlong token) {
    jfloatArray result=nullptr;
    guarded(env,[&] {
        validate(token); const auto values=session->sceneGameViewport();
        result=env->NewFloatArray(6);
        if(result) env->SetFloatArrayRegion(result,0,6,values.data());
    });
    return result;
}

// Project discovery/canonicalization runs on the editor IO worker without the
// graphics-session lock. Handles transfer a validated snapshot to the GL thread.
namespace {
std::mutex preparedMutex;
std::map<jlong,std::shared_ptr<sle::ProjectRuntime>> preparedProjects;
jlong nextPrepared=1;
template<class Open>
jbyteArray prepare(JNIEnv* env,jbyteArray source,const Open& open) {
    jbyteArray result=nullptr;
    try {
        if(!source) throw std::runtime_error("Missing project descriptor path");
        std::string path(static_cast<std::size_t>(env->GetArrayLength(source)),'\0');
        env->GetByteArrayRegion(source,0,static_cast<jsize>(path.size()),reinterpret_cast<jbyte*>(path.data()));
        if(env->ExceptionCheck()) return nullptr;
        auto opened=open(std::filesystem::u8path(path));
        if(!opened.ok()) throw std::runtime_error(opened.error());
        auto project=std::make_shared<sle::ProjectRuntime>(std::move(opened.value()));
        const auto& config=project->engineConfig();
        std::lock_guard lock(preparedMutex);const auto handle=nextPrepared++;
        const auto text=nlohmann::json{{"handle",handle},{"source",project->sceneDocument(project->startupScene()).dump()},
            {"title",config.title},{"width",config.width},{"height",config.height},{"vsync",config.vsync},{"targetFPS",config.targetFPS}}.dump();
        result=env->NewByteArray(static_cast<jsize>(text.size()));
        if(result) {
            env->SetByteArrayRegion(result,0,static_cast<jsize>(text.size()),reinterpret_cast<const jbyte*>(text.data()));
            if(!env->ExceptionCheck()) preparedProjects.emplace(handle,std::move(project));
        }
    } catch(const std::exception& error) { fail(env,error.what()); }
    return result;
}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_prepareProject(JNIEnv* env,jobject,jbyteArray source) {
    return prepare(env,source,[](const std::filesystem::path& path) { return sle::ProjectRuntime::open(path); });
}
// A built game's config.json and its .slepkg packages, mounted exactly as engine_app does.
extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_prepareGame(JNIEnv* env,jobject,jbyteArray source) {
    return prepare(env,source,[](const std::filesystem::path& path) {
        using Result=sle::core::Result<sle::ProjectRuntime>;
        const auto config=sle::GameConfig::load(path);
        if(!config.ok()) return Result::error(config.error());
        if(config.value().packages.empty()) return Result::error("Game config lists no packages");
        return sle::ProjectRuntime::openPackages(config.value().packages,config.value().engine);
    });
}
extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_releaseProject(JNIEnv* env,jobject,jlong handle) {
    try { std::lock_guard lock(preparedMutex);preparedProjects.erase(handle); }
    catch(const std::exception& error) { fail(env,error.what()); }
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_sle_editor_bridge_NativeEngine_startProject(
    JNIEnv* env,jobject,jlong token,jlong handle,jboolean pauseOnError) {
    jbyteArray error = nullptr;
    guarded(env,[&] {
        validate(token);
        std::shared_ptr<sle::ProjectRuntime> project;
        { std::lock_guard lock(preparedMutex);const auto found=preparedProjects.find(handle);
          if(found==preparedProjects.end()) throw std::runtime_error("Project launch snapshot expired");
          project=std::move(found->second);preparedProjects.erase(found); }
        LuaErrorPause capture(pauseOnError);
        const auto loaded=session->startProject(*project);
        if(!loaded.ok()) throw std::runtime_error(loaded.error());
        previewMode=false;scenePlaying=true;pendingInput.clear();
        error = capture.finish(env);
    });
    return error;
}
