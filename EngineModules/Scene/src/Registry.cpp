#include <sle/scene/Registry.hpp>

namespace sle::entity {

Entity Registry::createEntity()
{
    const uint32_t id = nextEntityID++;
    aliveEntities.insert(id);
    return Entity(id);
}

void Registry::destroyEntity(Entity entity)
{
    aliveEntities.erase(entity.getID());

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
