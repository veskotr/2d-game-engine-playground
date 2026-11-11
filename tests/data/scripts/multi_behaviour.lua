-- Intentionally global-format state; named behaviours must isolate this variable.
counter = 0
return {
 init=function(entity)
    local key=assert(Engine.getBehaviourKey())
    record_behaviour('init:'..key)
    Engine.Events.subscribe('multi.tick',function() record_behaviour('event:'..key) end)
 end,
 update=function(entity,dt)
    counter=counter+1
    record_behaviour('update:'..Engine.getBehaviourKey()..':'..counter)
 end,
 destroy=function(entity) record_behaviour('destroy:'..Engine.getBehaviourKey()) end,
 ping=function(entity) record_behaviour('ping:'..Engine.getBehaviourKey()) end
}
