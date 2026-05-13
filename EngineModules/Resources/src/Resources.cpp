#include <sle/resources/Resources.hpp>

namespace sle::core {

std::unordered_map<std::type_index, std::unique_ptr<IResourcePool>> Resources::pools;

void Resources::clear()
{
    for (auto& [_, pool] : pools)
        pool->clear();
}

} // namespace sle::core
