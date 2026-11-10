#include <sle/engine/Runtime.hpp>
#include <stdexcept>
#include <sle/scene/components/AudioComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/scene/components/Transform.hpp>

namespace sle {
bool ScriptApiImpl::registerScene(const std::string& name, const std::string& builder)
{
    if (name.empty() || builder.empty()) return false;
    return runtime.registerScene(name, [builder](Runtime& rt) {
        if (!rt.getScriptEngine().callGlobalFunction(builder))
            throw std::runtime_error("Lua scene builder failed: " + builder);
    });
}

bool ScriptApiImpl::registerSceneFromFile(const std::string& name, const std::string& path)
{
    return runtime.registerSceneFromFile(name, path);
}

void Runtime::prepareComponentMutation(uint32_t entityId, const std::string& name,
    const nlohmann::json& patch, bool removing)
{
    const sle::entity::Entity entity(entityId);
    auto& registry = scene.getRegistry();
    if (name == "Script") {
        const auto* script = registry.getComponent<components::ScriptComponent>(entity);
        if (removing || (script && patch.contains("scriptAsset") && patch["scriptAsset"] != script->scriptAsset))
            scriptEngine.removeScript(entityId);
    } else if (name == "Audio") {
        const auto* audio = registry.getComponent<components::AudioComponent>(entity);
        if (removing || (audio && patch.contains("assetPath") && patch["assetPath"] != audio->assetPath))
            audioSystem.releaseEntity(scene, entityId);
    } else if (name == "RigidBody") {
        if (removing) {
            physicsSystem.releaseEntity(scene, physicsWorld.get(), entity);
        } else if (physicsWorld) {
            const auto* body = registry.getComponent<components::RigidBodyComponent>(entity);
            if (body && body->box2dBodyId != 0) {
                auto settings = *body;
                if (patch.contains("bodyType")) {
                    const auto type = patch["bodyType"].get<std::string>();
                    settings.bodyType = type == "Static" ? components::BodyType::Static :
                        type == "Kinematic" ? components::BodyType::Kinematic : components::BodyType::Dynamic;
                }
                if (patch.contains("linearDamping")) settings.linearDamping = patch["linearDamping"].get<float>();
                if (patch.contains("angularDamping")) settings.angularDamping = patch["angularDamping"].get<float>();
                if (patch.contains("fixedRotation")) settings.fixedRotation = patch["fixedRotation"].get<bool>();
                physicsWorld->configureBody(body->box2dBodyId, settings);
                if (patch.contains("velocity")) physicsWorld->setBodyVelocity(body->box2dBodyId,
                    {patch["velocity"]["x"].get<float>(), patch["velocity"]["y"].get<float>()});
                if (patch.contains("angularVelocity")) physicsWorld->setBodyAngularVelocity(body->box2dBodyId, patch["angularVelocity"].get<float>());
                if (patch.contains("gravityScale")) physicsWorld->setBodyGravityScale(body->box2dBodyId, patch["gravityScale"].get<float>());
            }
        }
    } else if (name == "BoxCollider" || name == "CircleCollider" || name == "BoxZone" || name == "CircleZone") {
        const auto* body = registry.getComponent<components::RigidBodyComponent>(entity);
        auto release = [&](auto* shape) {
            if (!shape) return;
            if (physicsWorld && body && shape->box2dFixtureId != 0)
                physicsWorld->destroyFixture(body->box2dBodyId, shape->box2dFixtureId);
            shape->box2dFixtureId = 0;
        };
        if (name == "BoxCollider") release(registry.getComponent<components::BoxColliderComponent>(entity));
        if (name == "CircleCollider") release(registry.getComponent<components::CircleColliderComponent>(entity));
        if (name == "BoxZone") release(registry.getComponent<components::BoxZoneComponent>(entity));
        if (name == "CircleZone") release(registry.getComponent<components::CircleZoneComponent>(entity));
    } else if (name == "Transform" && removing) {
        registry.removeComponent<components::WorldTransformComponent>(entity);
        std::vector<sle::entity::Entity> pending = scene.getChildren(entity);
        while (!pending.empty()) {
            auto child = pending.back(); pending.pop_back();
            if (auto* transform = registry.getComponent<components::TransformComponent>(child)) transform->markDirty();
            const auto& children = scene.getChildren(child);
            pending.insert(pending.end(), children.begin(), children.end());
        }
    }
}
} // namespace sle
