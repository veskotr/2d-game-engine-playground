#pragma once
#include "ValueValidation.hpp"
#include <sle/engine/ChunkMapComponent.hpp>

namespace sle::lua::components {
using namespace sle::components;
inline Json snapshot(const ChunkMapComponent& c) {
 return {{"mapId",c.mapId},{"enabled",c.enabled},{"gridConfig",{
 {"chunkWidthTiles",c.gridConfig.chunkWidthTiles},{"chunkHeightTiles",c.gridConfig.chunkHeightTiles},
 {"tileWidth",c.gridConfig.tileWidth},{"tileHeight",c.gridConfig.tileHeight},
 {"activeRadiusX",c.gridConfig.activeRadiusX},{"activeRadiusY",c.gridConfig.activeRadiusY}}}};
}
} // namespace sle::lua::components
