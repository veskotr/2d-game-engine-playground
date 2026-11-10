global_event_hits = 0
local callback = function(event) global_event_hits = global_event_hits + 1 end
global_event_weak = setmetatable({callback}, {__mode = "v"})
Engine.Events.subscribe("owned.tick", callback)
function global_event_received() return global_event_hits == 1 end
function global_event_collected()
    collectgarbage("collect")
    return global_event_weak[1] == nil
end
