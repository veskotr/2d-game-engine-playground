#include "ComponentRegistry.hpp"
#include "lua/components/Camera2DBindings.hpp"
#include "ComponentAssets.hpp"
#include "lua/components/TransformBindings.hpp"
#include "lua/components/SpriteRendererBindings.hpp"
#include "lua/components/ScriptBindings.hpp"
#include "lua/components/RigidBodyBindings.hpp"
#include "lua/components/BoxColliderBindings.hpp"
#include "lua/components/CircleColliderBindings.hpp"
#include "lua/components/BoxZoneBindings.hpp"
#include "lua/components/CircleZoneBindings.hpp"
#include "lua/components/AnimatorBindings.hpp"
#include "lua/components/StateMachineBindings.hpp"
#include "lua/components/AudioBindings.hpp"
#include "lua/components/UIBindings.hpp"
#include "lua/components/ChunkActivationBindings.hpp"
#include "lua/components/WorldTransformBindings.hpp"
#include "lua/components/ChunkMapBindings.hpp"

namespace sle::componentdata {
namespace {
using namespace lua::components;

template<class T> void applyFields(T& component, const Json& patch, entity::Registry& registry)
{
 if(!patch.is_object()) throw std::invalid_argument("Component patch must be an object");
 for(const auto& [field,value] : patch.items()) {
  try { apply(component,Json{{field,value}},registry); }
  catch(const std::exception& ex) { throw std::invalid_argument(field+": "+ex.what()); }
 }
}
template<class T, bool Writable=true> Descriptor descriptor() {
 Descriptor d;
 d.get=[](entity::Registry& r,entity::Entity e) { auto* c=r.getComponent<T>(e); return c?snapshot(*c):Json(); };
 d.find=[](entity::Registry& r,std::vector<uint32_t>& out) { r.view<T>([&](entity::Entity e,auto&) {out.push_back(e.getID());}); };
 if constexpr(Writable) {
 d.validate=[](entity::Registry& r,entity::Entity e,const Json& patch) { auto* old=r.getComponent<T>(e); T copy=old?*old:T{}; applyFields(copy,patch,r); return snapshot(copy); };
 d.set=[](entity::Registry& r,entity::Entity e,const Json& patch) { auto* old=r.getComponent<T>(e); T copy=old?*old:T{}; applyFields(copy,patch,r); lua::components::invalidate(copy,old); if(old) *old=std::move(copy); else r.addComponent<T>(e,std::move(copy)); };
 d.remove=[](entity::Registry& r,entity::Entity e) { r.removeComponent<T>(e); };
 }
 return d;
}
} // namespace

const std::map<std::string,Descriptor>& descriptors() {
 static const std::map<std::string,Descriptor> table={
  {"Transform",descriptor<TransformComponent>()},
  {"Camera2D",descriptor<Camera2DComponent>()},
  {"SpriteRenderer",descriptor<SpriteRenderer>()},
  {"Script",descriptor<ScriptComponent>()},
  {"RigidBody",descriptor<RigidBodyComponent>()},
  {"BoxCollider",descriptor<BoxColliderComponent>()},
  {"CircleCollider",descriptor<CircleColliderComponent>()},
  {"BoxZone",descriptor<BoxZoneComponent>()},
  {"CircleZone",descriptor<CircleZoneComponent>()},
  {"Animator",descriptor<AnimatorComponent>()},
  {"StateMachine",descriptor<StateMachineComponent>()},
  {"Audio",descriptor<AudioComponent>()},
  {"UI",descriptor<UIComponent>()},
  {"ChunkActivation",descriptor<ChunkActivationComponent>()},
  {"WorldTransform",descriptor<WorldTransformComponent,false>()},
  {"ChunkMap",descriptor<ChunkMapComponent,false>()},
 }; return table; }

Json normalizePatch(const std::string& component, const Json& patch)
{
    if (!patch.is_object()) throw std::invalid_argument("Component patch must be an object");
    Json normalized = patch;
    normalizeAssetFields(component, normalized);
    return normalized;
}
} // namespace sle::componentdata
