#include <sle/scene/Registry.hpp>

namespace sle::entity {

Registry Registry::makeEmpty() const
{
    Registry empty;
    empty.nextEntityID = nextEntityID;
    return empty;
}

Entity Registry::createEntity()
{
    const uint32_t id = nextEntityID++;
    aliveEntities.insert(id);
    return Entity(id);
}

void Registry::destroyEntity(Entity entity)
{
    if (aliveEntities.erase(entity.getID()) == 0)
        return;

    parentMap.erase(entity.getID());

    for (auto& [_, pool] : pools)
    {
        pool->erase(entity.getID());
    }
}

bool Registry::hasEntity(Entity entity) const
{
    return aliveEntities.contains(entity.getID());
}

} // namespace sle::entity
