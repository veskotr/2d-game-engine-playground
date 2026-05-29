#pragma once

#include <sle/scene/components/AnimationClipResource.hpp>

#include <memory>
#include <string>
#include <unordered_map>
#include <unordered_set>

namespace sle::components {

struct AnimatorComponent
{
    std::string clipAsset;
    bool clipLoadAttempted = false;

    std::shared_ptr<AnimationClipDefinition> clip;

    bool enabled = true;
    bool playing = true;
    float timeSeconds = 0.0f;
    float speed = 1.0f;

    bool overrideLoopMode = false;
    AnimationLoopMode loopMode = AnimationLoopMode::Loop;

    // Optional state-machine-to-clip mapping.
    // When an entity has StateMachineComponent, currentState can select clipAsset via this map.
    std::unordered_map<std::string, std::string> stateClipMap;

    // Named entity targets resolved by name at runtime.
    // Binding paths like "entity:Weapon.Transform.position.x" look up "Weapon" here.
    std::unordered_map<std::string, uint32_t> targetEntities;

    // Script/gameplay-driven animator parameters (Phase 3 bridge).
    std::unordered_map<std::string, float> floatParameters;

    std::unordered_set<std::string> invalidBindingsLogged;
};

} // namespace sle::components
