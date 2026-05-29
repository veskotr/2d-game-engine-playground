// Integration test for the audio Lua API surface.
// Verifies that Engine.playSound/stopSound/setSoundVolume/setSoundPitch/isSoundPlaying
// route correctly through the ScriptApi interface.
// No audio hardware is required — the spy never initialises miniaudio.

#include <sle/scripting/ScriptApi.hpp>
#include <sle/scripting/ScriptEngine.hpp>

#include <glm/vec2.hpp>

#include <cassert>
#include <cstdint>
#include <string>

namespace {

class AudioSpy final : public sle::scripting::ScriptApi
{
public:
    // ====== spy state ======
    uint32_t playSoundEntity = 0;
    std::string playSoundPath;
    bool playSoundLoop = false;
    bool playSoundCalled = false;

    uint32_t stopSoundEntity = 0;
    bool stopSoundCalled = false;

    uint32_t volumeEntity = 0;
    float volumeValue = -1.0f;

    uint32_t pitchEntity = 0;
    float pitchValue = -1.0f;

    uint32_t isPlayingEntity = 0;
    bool isPlayingResult = true; // return true so Lua can read it

    // ====== ScriptApi ======
    float getDeltaTime() const override { return 0.016f; }
    glm::vec2 getWindowSize() const override { return {320.0f, 180.0f}; }

    sle::scripting::ScriptEntityRef createEntity() override { return {++nextId_}; }
    bool isEntityAlive(sle::scripting::ScriptEntityRef) const override { return true; }
    void destroyEntity(sle::scripting::ScriptEntityRef) override {}
    uint32_t getChildCount(sle::scripting::ScriptEntityRef) const override { return 0; }
    uint32_t destroyChildren(sle::scripting::ScriptEntityRef) override { return 0; }
    bool setParent(sle::scripting::ScriptEntityRef, sle::scripting::ScriptEntityRef) override { return false; }
    sle::scripting::ScriptEntityRef getParent(sle::scripting::ScriptEntityRef) const override { return {}; }

    bool getTransformPosition(sle::scripting::ScriptEntityRef, glm::vec2&) const override { return false; }
    bool setTransformPosition(sle::scripting::ScriptEntityRef, const glm::vec2&) override { return false; }
    bool getTransformScale(sle::scripting::ScriptEntityRef, glm::vec2&) const override { return false; }

    bool isKeyDown(int) const override { return false; }
    bool isKeyPressed(int) const override { return false; }
    bool isKeyReleased(int) const override { return false; }
    glm::dvec2 getMousePosition() const override { return {0.0, 0.0}; }

    glm::vec2 getCameraPosition() const override { return {0.0f, 0.0f}; }
    void setCameraPosition(const glm::vec2&) override {}
    void moveCamera(const glm::vec2&) override {}
    float getCameraZoom() const override { return 1.0f; }
    void setCameraZoom(float) override {}

    uint32_t loadTexture(const std::string&) override { return 0; }
    bool setSpriteTexture(sle::scripting::ScriptEntityRef, const std::string&) override { return false; }

    bool hasScene(const std::string&) const override { return false; }
    bool switchScene(const std::string&) override { return false; }
    std::string getCurrentSceneName() const override { return {}; }

    bool setStateMachineBool(sle::scripting::ScriptEntityRef, const std::string&, bool) override { return false; }
    bool setStateMachineTrigger(sle::scripting::ScriptEntityRef, const std::string&) override { return false; }
    bool getStateMachineCurrentState(sle::scripting::ScriptEntityRef, std::string& out) const override { out = "idle"; return true; }
    bool forceStateMachineState(sle::scripting::ScriptEntityRef, const std::string&) override { return false; }
    bool isStateMachineInState(sle::scripting::ScriptEntityRef, const std::string&) const override { return false; }
    bool sendStateMachineEvent(sle::scripting::ScriptEntityRef, const std::string&) override { return false; }

    void log(const std::string&) override {}
    void warn(const std::string&) override {}
    void error(const std::string&) override {}
    bool setUIBinding(const std::string&, const std::string&) override { return false; }

    bool addForce(sle::scripting::ScriptEntityRef, float, float) override { return false; }
    bool addImpulse(sle::scripting::ScriptEntityRef, float, float) override { return false; }
    bool setVelocity(sle::scripting::ScriptEntityRef, float, float) override { return false; }
    bool getVelocity(sle::scripting::ScriptEntityRef, glm::vec2&) const override { return false; }
    bool setAngularVelocity(sle::scripting::ScriptEntityRef, float) override { return false; }
    float getAngularVelocity(sle::scripting::ScriptEntityRef) const override { return 0.0f; }
    bool setGravityScale(sle::scripting::ScriptEntityRef, float) override { return false; }
    bool isTouching(sle::scripting::ScriptEntityRef) const override { return false; }
    bool raycastFirst(const glm::vec2&, const glm::vec2&, sle::scripting::PhysicsRaycastHit&) const override { return false; }
    uint32_t raycastAll(const glm::vec2&, const glm::vec2&, std::vector<sle::scripting::PhysicsRaycastHit>&) const override { return 0; }
    void setPhysicsDebugEnabled(bool) override {}
    bool isPhysicsDebugEnabled() const override { return false; }

    int subscribeEvent(const std::string&, uint32_t, int) override { return 0; }
    void unsubscribeEvent(int) override {}
    bool emitEvent(const std::string&, uint32_t, const std::string&) override { return false; }

    bool playAnimation(sle::scripting::ScriptEntityRef, const std::string&) override { return false; }
    bool stopAnimation(sle::scripting::ScriptEntityRef) override { return false; }
    bool pauseAnimation(sle::scripting::ScriptEntityRef) override { return false; }
    bool resumeAnimation(sle::scripting::ScriptEntityRef) override { return false; }
    bool setAnimationSpeed(sle::scripting::ScriptEntityRef, float) override { return false; }
    bool setAnimationTime(sle::scripting::ScriptEntityRef, float) override { return false; }
    bool isAnimationPlaying(sle::scripting::ScriptEntityRef) const override { return false; }
    float getAnimationTime(sle::scripting::ScriptEntityRef) const override { return 0.0f; }
    bool setAnimationTarget(sle::scripting::ScriptEntityRef, const std::string&, sle::scripting::ScriptEntityRef) override { return false; }
    bool setAnimatorFloat(sle::scripting::ScriptEntityRef, const std::string&, float) override { return false; }
    bool getAnimatorFloat(sle::scripting::ScriptEntityRef, const std::string&, float&) const override { return false; }

    // ====== AUDIO (spy) ======
    bool playSound(sle::scripting::ScriptEntityRef e, const std::string& path, bool loop) override
    {
        playSoundEntity = e.id;
        playSoundPath = path;
        playSoundLoop = loop;
        playSoundCalled = true;
        return true;
    }
    bool stopSound(sle::scripting::ScriptEntityRef e) override
    {
        stopSoundEntity = e.id;
        stopSoundCalled = true;
        return true;
    }
    bool setSoundVolume(sle::scripting::ScriptEntityRef e, float v) override
    {
        volumeEntity = e.id;
        volumeValue = v;
        return true;
    }
    bool setSoundPitch(sle::scripting::ScriptEntityRef e, float p) override
    {
        pitchEntity = e.id;
        pitchValue = p;
        return true;
    }
    bool isSoundPlaying(sle::scripting::ScriptEntityRef e) const override
    {
        const_cast<AudioSpy*>(this)->isPlayingEntity = e.id;
        return isPlayingResult;
    }

private:
    uint32_t nextId_ = 0;
};

} // namespace

int main()
{
    AudioSpy spy;
    sle::scripting::ScriptEngine engine;

    assert(engine.init(&spy) && "ScriptEngine init failed");

    assert(engine.executeScriptAsset("tests/data/scripts/audio_api_surface.lua") &&
           "Failed to execute audio_api_surface.lua");

    // playSound assertions
    assert(spy.playSoundCalled && "playSound was not called");
    assert(spy.playSoundPath == "assets/audio/coin.wav" && "wrong asset path");
    assert(!spy.playSoundLoop && "loop should be false");

    // stopSound assertions
    assert(spy.stopSoundCalled && "stopSound was not called");
    assert(spy.stopSoundEntity == spy.playSoundEntity && "stopSound entity mismatch");

    // volume
    assert(spy.volumeEntity != 0 && "setSoundVolume entity missing");
    assert(spy.volumeValue > 0.49f && spy.volumeValue < 0.51f && "volume mismatch");

    // pitch
    assert(spy.pitchEntity != 0 && "setSoundPitch entity missing");
    assert(spy.pitchValue > 1.24f && spy.pitchValue < 1.26f && "pitch mismatch");

    // isSoundPlaying
    assert(spy.isPlayingEntity != 0 && "isSoundPlaying entity missing");

    return 0;
}
