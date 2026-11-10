#include <sle/engine/Runtime.hpp>
#include <sle/events/SceneLifecycleEvents.hpp>
#include <sle/scene/components/Transform.hpp>

#include <iostream>
#include <string>
#include <vector>

namespace {
struct ScenePing {};
}

int main()
{
    // No graphics initialization: these scene loads only construct ECS data.
    sle::Runtime runtime(sle::core::EngineConfig{});
    auto& scene = runtime.getScene();
    auto& registry = scene.getRegistry();
    std::vector<std::string> lifecycle;
    runtime.getGlobalBus().subscribe<sle::events::SceneLoadedEvent>(
        [&](const auto& event) { lifecycle.push_back("load:" + event.sceneName); });
    runtime.getGlobalBus().subscribe<sle::events::SceneUnloadedEvent>(
        [&](const auto& event) { lifecycle.push_back("unload:" + event.sceneName); });

    sle::entity::Entity parent;
    sle::entity::Entity child;
    if (!runtime.registerScene("original", [&](sle::Runtime& rt)
        {
            parent = rt.getScene().createEntity();
            rt.getScene().getRegistry().addComponent<sle::components::TransformComponent>(parent)
                .setPosition({17.0f, 23.0f});
            child = rt.getScene().createEntity();
            rt.getScene().setParent(child, parent);
        }) || !runtime.loadScene("original").ok())
    {
        std::cerr << "Failed to construct original scene\n";
        return 1;
    }
    runtime.getGlobalBus().flushQueue();
    lifecycle.clear();
    auto* const originalTransform = registry.getComponent<sle::components::TransformComponent>(parent);
    auto* const originalBus = &scene.getEventBus();
    int pings = 0;
    const auto oldSubscription = scene.getEventBus().subscribe<ScenePing>([&](const auto&) { ++pings; });

    struct FailureCase { const char* name; const char* path; bool deferred; };
    const FailureCase failures[] = {
        {"malformed", "tests/data/scenes/test_malformed_scene.json", false},
        {"missing", "tests/data/scenes/does_not_exist.json", true},
        {"typed", "tests/data/scenes/test_partial_invalid_scene.json", false},
        {"map", "tests/data/scenes/test_missing_tiled_scene.json", true},
    };
    bool passed = true;
    for (const auto& failure : failures)
    {
        if (!runtime.registerSceneFromFile(failure.name, failure.path))
            return 1;
        scene.getEventBus().queue(ScenePing{});
        const auto result = [&]()
        {
            if (!failure.deferred)
                return runtime.loadScene(failure.name);
            const auto request = runtime.requestSceneSwitch(failure.name);
            if (!request.ok())
                return request;
            return runtime.getSceneManager().processPendingSwitch(runtime, scene);
        }();
        if (result.ok() || result.error().find(failure.path) == std::string::npos)
        {
            std::cerr << failure.name << ": expected a load error identifying the scene file\n";
            passed = false;
        }
        runtime.getGlobalBus().flushQueue();
        if (runtime.getCurrentSceneName() != "original" || !lifecycle.empty())
        {
            std::cerr << failure.name << ": failed load changed the active scene or emitted lifecycle events\n";
            passed = false;
        }
        if (!registry.hasEntity(parent) || !registry.hasEntity(child) ||
            registry.getComponent<sle::components::TransformComponent>(parent) != originalTransform ||
            scene.getParent(child) != parent || scene.getRoots().size() != 1)
        {
            std::cerr << failure.name << ": failed load changed ECS data/hierarchy or left partial entities\n";
            passed = false;
        }
        if (&scene.getEventBus() != originalBus)
        {
            std::cerr << "Failed load replaced the live event bus\n";
            passed = false;
        }
        const int before = pings;
        scene.getEventBus().flushQueue();
        if (pings != before + 1)
        {
            std::cerr << failure.name << ": failed load lost the old scene's queued event/subscription\n";
            passed = false;
        }
        // A rejected pending switch must be consumed, not retried forever.
        if (!runtime.getSceneManager().processPendingSwitch(runtime, scene).ok())
            passed = false;
    }
    if (!passed)
        return 1;

    scene.getEventBus().queue(ScenePing{});
    if (!runtime.registerSceneFromFile("valid", "tests/data/scenes/test_replacement_scene.json") ||
        !runtime.loadScene("valid").ok())
    {
        std::cerr << "A valid scene must load after rejected replacements\n";
        return 1;
    }
    if (!lifecycle.empty())
    {
        std::cerr << "Successful lifecycle events must still be deferred\n";
        return 1;
    }
    runtime.getGlobalBus().flushQueue();
    if (lifecycle != std::vector<std::string>{"unload:original", "load:valid"} ||
        runtime.getCurrentSceneName() != "valid" || registry.hasEntity(parent) || registry.hasEntity(child) ||
        scene.getRoots().size() != 1 || scene.getRoots().front().getID() <= child.getID())
    {
        std::cerr << "Successful replacement must preserve lifecycle order and never reuse old entity IDs\n";
        return 1;
    }
    const auto* replacement = registry.getComponent<sle::components::TransformComponent>(scene.getRoots().front());
    if (!replacement || replacement->getPosition() != glm::vec2(50.0f, 60.0f))
    {
        std::cerr << "Replacement scene data was not committed\n";
        return 1;
    }
    const int before = pings;
    int freshPings = 0;
    scene.getEventBus().subscribe<ScenePing>([&](const auto&) { ++freshPings; });
    scene.getEventBus().flushQueue();
    if (freshPings != 0)
    {
        std::cerr << "Old queued events must not reach the replacement scene\n";
        return 1;
    }
    // A surviving ScopedSubscription from the old scene may reset later.
    // Its old token must never unsubscribe a new scene's listener.
    scene.getEventBus().unsubscribe(oldSubscription);
    scene.getEventBus().emit(ScenePing{});
    if (pings != before || freshPings != 1 || &scene.getEventBus() != originalBus)
    {
        std::cerr << "Successful replacement must clear old subscriptions without moving the event bus\n";
        return 1;
    }
    return 0;
}
