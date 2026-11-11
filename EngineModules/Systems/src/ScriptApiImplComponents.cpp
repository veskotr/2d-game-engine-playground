#include <sle/engine/ScriptApiImpl.hpp>
#include <sle/engine/Runtime.hpp>
#include <algorithm>
#include <map>
#include <functional>
#include "ComponentRegistry.hpp"
namespace sle {
namespace {
using namespace componentdata;
using Ref = scripting::ScriptEntityRef;

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
  const auto normalized=normalizePatch(name,patch);
  const Json candidate=it->second.validate(r,e,normalized);
  if(it->second.get(r,e)==candidate) return true;
  runtime.prepareComponentMutation(ref.id,name,normalized,false);
  if(!r.hasEntity(e)) throw std::invalid_argument("Entity was destroyed during component teardown");
  it->second.set(r,e,normalized);
  // Teardown may recursively edit Script before this outer write commits.
  // Reconcile against committed ECS data so removed siblings cannot survive.
  if(name=="Script") runtime.synchronizeScriptBehaviours(ref.id);
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
 it->second.remove(r,e); if(name=="Script") runtime.synchronizeScriptBehaviours(ref.id); return true;
}
} // namespace sle
