local callback = function(event) error("failed initialization callback must not execute") end
ui_failed_weak = setmetatable({callback}, {__mode = "v"})
function ui_failed_collected()
    collectgarbage("collect")
    return ui_failed_weak[1] == nil
end
Engine.Events.subscribe("ui.owned", callback)
error("intentional UI initialization failure")
