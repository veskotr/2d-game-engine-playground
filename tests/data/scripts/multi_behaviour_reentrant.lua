return {
 ping=function(entity) record_behaviour('ping:'..Engine.getBehaviourKey()) end,
 destroy=function(entity)
    local script=Engine.Components.get(entity,'Script')
    if not script then return end
    for _,entry in ipairs(script.behaviours) do
        if entry.key=='survivor' then entry.order=5 end
    end
    assert(Engine.Components.set(entity,'Script',{behaviours=script.behaviours}))
 end
}
