#include <sle/engine/ScriptApiImpl.hpp>
#include <sle/engine/Runtime.hpp>
#include <algorithm>
#include <map>
#include <functional>
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
namespace sle {
namespace {
using namespace lua::components;
using Ref = scripting::ScriptEntityRef;

struct Descriptor {
 std::function<Json(entity::Registry&,entity::Entity)> get;
 std::function<Json(entity::Registry&,entity::Entity,const Json&)> validate;
 std::function<void(entity::Registry&,entity::Entity,const Json&)> set;
 std::function<void(entity::Registry&,entity::Entity)> remove;
 std::function<void(entity::Registry&,std::vector<uint32_t>&)> find;
};
template<class T, bool Writable=true> Descriptor descriptor() {
 Descriptor d;
 d.get=[](entity::Registry& r,entity::Entity e) { auto* c=r.getComponent<T>(e); return c?snapshot(*c):Json(); };
 d.find=[](entity::Registry& r,std::vector<uint32_t>& out) { r.view<T>([&](entity::Entity e,auto&) {out.push_back(e.getID());}); };
 if constexpr(Writable) {
 d.validate=[](entity::Registry& r,entity::Entity e,const Json& patch) { auto* old=r.getComponent<T>(e); T copy=old?*old:T{}; apply(copy,patch,r); return snapshot(copy); };
 d.set=[](entity::Registry& r,entity::Entity e,const Json& patch) { auto* old=r.getComponent<T>(e); T copy=old?*old:T{}; apply(copy,patch,r); lua::components::invalidate(copy,old); if(old) *old=std::move(copy); else r.addComponent<T>(e,std::move(copy)); };
 d.remove=[](entity::Registry& r,entity::Entity e) { r.removeComponent<T>(e); };
 }
 return d;
}
const std::map<std::string,Descriptor>& descriptors() {
 static const std::map<std::string,Descriptor> table={
  {"Transform",descriptor<TransformComponent>()},
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
} // namespace
nlohmann::json ScriptApiImpl::getComponent(Ref ref,const std::string& name) const {
 auto& r=runtime.getScene().getRegistry(); entity::Entity e(ref.id); const auto it=descriptors().find(name);
 return r.hasEntity(e) && it!=descriptors().end()?it->second.get(r,e):Json();
}
bool ScriptApiImpl::hasComponent(Ref e,const std::string& name) const { return !getComponent(e,name).is_null(); }
std::vector<uint32_t> ScriptApiImpl::findEntitiesWithComponent(const std::string& name) const {
 std::vector<uint32_t> out; const auto it=descriptors().find(name); if(it!=descriptors().end()) it->second.find(runtime.getScene().getRegistry(),out); std::sort(out.begin(),out.end()); return out;
}
bool ScriptApiImpl::setComponent(Ref ref,const std::string& name,const Json& patch,std::string& error) {
 error.clear(); auto& r=runtime.getScene().getRegistry(); entity::Entity e(ref.id);
 try {
  if(!r.hasEntity(e)) throw std::invalid_argument("Entity is not alive");
  if(!patch.is_object()) throw std::invalid_argument("Component patch must be an object");
  const auto it=descriptors().find(name); if(it==descriptors().end() || !it->second.set) throw std::invalid_argument("Unknown or read-only component: "+name);
  const Json candidate=it->second.validate(r,e,patch);
  if(it->second.get(r,e)==candidate) return true;
  runtime.prepareComponentMutation(ref.id,name,patch,false);
  if(!r.hasEntity(e)) throw std::invalid_argument("Entity was destroyed during component teardown");
  it->second.set(r,e,patch);
  return true;
 } catch(const std::exception& ex) { error=ex.what(); return false; }
}
bool ScriptApiImpl::removeComponent(Ref ref,const std::string& name,std::string& error) {
 error.clear(); auto& r=runtime.getScene().getRegistry(); entity::Entity e(ref.id);
 if(!r.hasEntity(e)) { error="Entity is not alive"; return false; }
 const auto it=descriptors().find(name); if(it==descriptors().end() || !it->second.remove) { error="Unknown or read-only component: "+name; return false; }
 if(it->second.get(r,e).is_null()) { error="Entity does not have component: "+name; return false; }
 runtime.prepareComponentMutation(ref.id,name,Json::object(),true);
 if(!r.hasEntity(e)) { error="Entity was destroyed during component teardown"; return false; }
 it->second.remove(r,e); return true;
}
} // namespace sle
