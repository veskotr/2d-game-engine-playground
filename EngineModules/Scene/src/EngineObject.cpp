#include <sle/scene/EngineObject.hpp>

namespace sle::entity {

EngineObject::EngineObject(Scene* scene, Entity e)
    : scene(scene), entity(e)
{
}

EngineObject::~EngineObject()
{
}

EngineObject* EngineObject::createChild()
{
    return nullptr;
}

void EngineObject::destroy()
{
}

void EngineObject::setEnabled(bool value)
{
    enabled = value;
}

} // namespace sle::entity
