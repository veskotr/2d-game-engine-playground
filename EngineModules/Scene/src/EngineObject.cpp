#include <sle/scene/EngineObject.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/Scene.hpp>

namespace sle::entity {

EngineObject::EngineObject(Scene* scene, Registry* registry, Entity e)
    : scene(scene), registry(registry), entity(e)
{
}

EngineObject::~EngineObject()
{
}

EngineObject* EngineObject::createChild()
{
    if (!scene)
        return nullptr;

    return scene->createObject(this);
}

void EngineObject::destroy()
{
    if (!scene)
        return;

    scene->destroyObject(this);
}

void EngineObject::setEnabled(bool value)
{
    if (!scene)
    {
        enabled = value;
        return;
    }

    scene->setEnabled(this, value);
}

} // namespace sle::entity
