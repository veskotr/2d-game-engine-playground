saved_on_destroy = 0
function saved_destroy_entity_alive() return Engine.isEntityAlive(saved_on_destroy) end
return { destroy = function(entity) saved_on_destroy = Engine.createEntity() end }
