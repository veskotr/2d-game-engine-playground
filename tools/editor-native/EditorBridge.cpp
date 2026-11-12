#include <glad/gl.h>
#include <jni.h>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/EntityAuthoring.hpp>
#include <sle/engine/SceneAuthoring.hpp>

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
        pendingInput.clear();
        currentToken = 0;
        owner = {};
    });
}

extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_startScenePlay(
    JNIEnv* env,jobject,jlong token,jbyteArray source) {
    guarded(env,[&] {
        validate(token);
        if (!source) throw std::runtime_error("Missing scene play document");
        std::string text(static_cast<std::size_t>(env->GetArrayLength(source)),'\0');
        env->GetByteArrayRegion(source,0,static_cast<jsize>(text.size()),reinterpret_cast<jbyte*>(text.data()));
        if (env->ExceptionCheck()) return;
        const auto loaded=session->startScenePlay(nlohmann::json::parse(text));
        if (!loaded.ok()) throw std::runtime_error(loaded.error());
        previewMode=false; scenePlaying=true; pendingInput.clear();
    });
}
extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_renderSceneView(
    JNIEnv* env,jobject,jlong token,jint width,jint height,jfloat dt,jboolean gameView,jboolean playing) {
    guarded(env,[&] {
        validate(token);
        sle::input::Input::update();
        for (const auto& event:pendingInput) {
            switch(event.type) {
            case 0: sle::input::Input::setKeyState(event.code,event.x!=0); break;
            case 1: sle::input::Input::setMouseButtonState(event.code,event.x!=0); break;
            case 2: sle::input::Input::setCursorPosition(event.x,event.y); break;
            case 3: sle::input::Input::releaseAll(); break;
            }
        }
        pendingInput.clear();
        const auto rendered=session->renderSceneView(width,height,dt,gameView!=JNI_FALSE,playing!=JNI_FALSE,fitRequested);
        fitRequested=false;
        if (!rendered.ok()) throw std::runtime_error(rendered.error());
    });
}
extern "C" JNIEXPORT void JNICALL Java_sle_editor_bridge_NativeEngine_panPreview(
    JNIEnv* env,jobject,jlong token,jfloat dx,jfloat dy) {
    guarded(env,[&] { validate(token); session->panEntityPreview({dx,dy}); });
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
extern "C" JNIEXPORT jfloatArray JNICALL Java_sle_editor_bridge_NativeEngine_sceneGameViewport(JNIEnv* env,jobject,jlong token) {
    jfloatArray result=nullptr;
    guarded(env,[&] {
        validate(token); const auto values=session->sceneGameViewport();
        result=env->NewFloatArray(6);
        if(result) env->SetFloatArrayRegion(result,0,6,values.data());
    });
    return result;
}
