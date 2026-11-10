return {
    init=function(entity) Engine.destroyEntity(entity) end,
    update=function(entity,dt) error('destroyed script must not update') end
}
