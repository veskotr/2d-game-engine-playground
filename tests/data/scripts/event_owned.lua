function event_fallback_subscribe(entity)
    local fn = function(event) end
    fallback_weak = setmetatable({fn}, {__mode = "v"})
    Engine.Events.subscribe("fallback.tick", fn)
end
function event_fallback_collected()
    collectgarbage("collect")
    return fallback_weak[1] == nil
end
event_weak = setmetatable({}, {__mode = "v"})
function event_callbacks_collected()
    collectgarbage("collect")
    return event_weak[1] == nil and event_weak[2] == nil and event_weak[3] == nil
end
local ids = {}
return {
    init = function(entity)
        for i, increment in ipairs({1, 10, 100}) do
            local callback = function(event)
                local pos = Engine.getTransformPosition(entity)
                if pos then Engine.setTransformPosition(entity, pos.x + increment, pos.y) end
            end
            event_weak[i] = callback
            ids[i] = Engine.Events.subscribe("owned.tick", callback)
        end
        Engine.Events.unsubscribe(ids[1])
        Engine.Events.unsubscribe(ids[3]) -- removal after a previous swap/erase
    end,
    unsubscribe_all = function(entity)
        Engine.Events.unsubscribe(ids[2])
    end
}
