local callback = function(event) end
failed_event_weak = setmetatable({callback}, {__mode = "v"})
function failed_event_collected()
    collectgarbage("collect")
    return failed_event_weak[1] == nil
end
Engine.Events.subscribe("failed.tick", callback)
error("intentional initialization failure after subscribing")
