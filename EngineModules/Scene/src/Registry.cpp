#include <sle/scene/Registry.hpp>

namespace sle::entity {

Entity Registry::createEntity()
{
    return Entity(nextEntityID++);
}

void Registry::destroyEntity(Entity entity)
{
    for (auto& [_, pool] : pools)
    {
        pool->erase(entity.getID());
    }
}

} // namespace sle::entity
